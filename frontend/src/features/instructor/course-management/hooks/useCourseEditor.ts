import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { DEFAULT_PAGE_SIZE } from '../api/assessmentAuthoringApi';
import {
    createCourseChapter, createCourseLesson, createOwnedCourse, getOwnedCourse, listOwnedCourses,
    orderChapterLessons, orderCourseChapters, setCourseLessonPublication, setOwnedCoursePublication,
    updateCourseChapter, updateOwnedCourse,
} from '../api/instructorCourseApi';
import type {
    CoursePublicationStatus, OwnedCourse, OwnedCourseSummaryPage, OwnedCourseWrite, OwnedLessonWrite,
} from '../api/instructorCourseApi';
import type { SkillSource } from '../components/QuestionForm';
import { ApiError, cancelled } from '../../../../shared/types/apiTypes';
import type { AuthoringRequestOptions } from '../api/assessmentAuthoringApi';
import type { AuthoringOperationOwner } from './useAssessmentEditor';
import { isCancelled, outcomeUnknown, useAuthoringOperation, useOwnerRead } from './useAssessmentEditor';
import type { OwnerRead } from './useAssessmentEditor';
import { useOwnedSkillGraph } from '../../skill-graph-editor/hooks/useSkillGraph';
import type { OwnedSkillGraphRead } from '../../skill-graph-editor/hooks/useSkillGraph';

/*
 * Instructor course authoring state & shared course editor context (DESIGN.md S17, S18, D12-3;
 * CT21.authoring, CT05.representation). One `getOwnedCourse` read and one `getOwnedSkillGraph`
 * read are held by the course editor shell for the active `courseId` and session generation, so
 * Content, Skills, Assessments, Settings and the quiz/assignment editors share them without
 * duplicate requests and discard them cleanly when the course or session changes.
 */

export function useOwnedCourses(page: number, size = DEFAULT_PAGE_SIZE): OwnerRead<OwnedCourseSummaryPage> {
    return useOwnerRead(`owned-courses|${page}|${size}`, (options) => listOwnedCourses(page, size, options));
}

export function useCourseCreation() {
    const { busy, run } = useAuthoringOperation();
    const create = useCallback((write: OwnedCourseWrite) => run((options) => createOwnedCourse(write, options)), [run]);
    return { busy, create };
}

export function useCourseOperations(courseId: string) {
    const ctx = useCourseEditorContext();
    const { busy, pending, run } = useAuthoringOperation(ctx?.courseMutations);
    const actions = useMemo(() => ({
        updateMetadata: (write: OwnedCourseWrite) => run(async (options) => {
            const saved = await updateOwnedCourse(courseId, write, options);
            if (options.isCurrent?.()) ctx?.acknowledgeSettings(saved);
            return saved;
        }),
        setPublication: (status: CoursePublicationStatus) => run((options) => setOwnedCoursePublication(courseId, status, options)),
        createChapter: (title: string) => run((options) => createCourseChapter(courseId, title, options)),
        updateChapter: (chapterId: string, title: string) => run((options) => updateCourseChapter(courseId, chapterId, title, options)),
        orderChapters: (orderedIds: readonly string[]) => run((options) => orderCourseChapters(courseId, orderedIds, options)),
        createLesson: (chapterId: string, write: OwnedLessonWrite) => run((options) => createCourseLesson(courseId, chapterId, write, options)),
        orderLessons: (chapterId: string, orderedIds: readonly string[]) => run((options) => orderChapterLessons(courseId, chapterId, orderedIds, options)),
        setLessonPublication: (lessonId: string, status: CoursePublicationStatus) => run((options) => setCourseLessonPublication(courseId, lessonId, status, options)),
    }), [courseId, run, ctx?.acknowledgeSettings]);
    return { busy, pending, ...actions };
}

export interface SettingsAcknowledgment {
    metadata: OwnedCourseWrite;
    reconcileAfter: number;
}

export interface SharedCourseContextState {
    courseId: string;
    courseMutations: AuthoringOperationOwner;
    graphMutations: AuthoringOperationOwner;
    writesBlocked: () => boolean;
    mutationPending: boolean;
    mutationUncertain: boolean;
    cancelMutation: () => void;
    course: {
        status: 'loading' | 'ready' | 'error';
        data: OwnedCourse | null;
        error: unknown;
        /** Pending owner read, including refresh of a retained tree; optional for legacy overrides. */
        loading?: boolean;
        /** Sequence of the GET that supplied data; retained bodies keep their old sequence. */
        successfulRead: number;
        /** Schedules the shared read and returns its minimum eligible initiation sequence. */
        reload: () => number;
        /** Mutation reconciliation survives child editor unmount/remount. */
        revalidate: () => number;
        revalidating: boolean;
    };
    skillGraph: OwnedSkillGraphRead;
    skillsRevalidating: boolean;
    skills: SkillSource;
    /** Confirmed metadata awaiting a causally eligible GET; never a draft or shared-read evidence. */
    settingsAcknowledgment: SettingsAcknowledgment | null;
    acknowledgeSettings: (metadata: OwnedCourseWrite) => void;
    /** Marks that S20 changed skills in this document, and triggers a fresh graph read. */
    notifySkillsChanged: () => void;
    /** Re-reads the skill graph if an earlier S20 skill mutation has not been freshly read yet (D12-3). */
    refreshSkillsIfStale: () => void;
    /** Registers whether the active tab currently holds unsaved changes for shell tab/back navigation. */
    setTabDirty: (dirty: boolean) => void;
    isTabDirty: () => boolean;
}

export const CourseEditorContext = createContext<SharedCourseContextState | null>(null);

export function useCourseEditorContext(): SharedCourseContextState | null {
    return useContext(CourseEditorContext);
}

export function useSharedCourseState(courseId: string): SharedCourseContextState {
    const auth = useAuth();
    const identity = auth.isAuthenticated ? `${auth.generation}|${courseId}` : null;
    const [mutationFloor, setMutationFloor] = useState<{ identity: string; sequence: number } | null>(null);
    const [settingsSave, setSettingsSave] = useState<(SettingsAcknowledgment & { identity: string }) | null>(null);
    // Count actual GET initiations, not renders, reload scheduling or response arrival. The existing owner-read
    // lifecycle accepts only current, non-superseded results and retains this envelope unchanged on read failure.
    const readSequence = useRef(0);
    const courseRead = useOwnerRead(`owned-course|${courseId}`, async (options) => {
        const sequence = ++readSequence.current;
        const body = await getOwnedCourse(courseId, options);
        return { body, sequence };
    });
    const reloadCourse = useCallback(() => {
        const minimumRead = readSequence.current + 1;
        courseRead.reload();
        return minimumRead;
    }, [courseRead.reload]);
    const revalidateCourse = useCallback(() => {
        const sequence = reloadCourse();
        if (identity !== null) setMutationFloor({ identity, sequence });
        return sequence;
    }, [identity, reloadCourse]);
    const skillGraph = useOwnedSkillGraph(courseId);
    const { reload: reloadGraph, graph, loading: graphLoading, error: graphError } = skillGraph;
    // True from an S20 skill change until a later graph read succeeds (D12-3). A failed read keeps it set,
    // so the next deliberate opening of the Questions view or the question form reads once more. It is
    // never cleared during render and never triggers a read on its own: a failed read is retried only by
    // such an opening or by "Try again" (CT16.minimum: no automatic GET retry).
    const skillsStaleRef = useRef(false);
    const [skillsRefreshIdentity, setSkillsRefreshIdentity] = useState<string | null>(null);
    const graphReloadStarted = useRef(false);
    const tabDirtyRef = useRef(false);

    const graphLoadingRef = useRef(graphLoading);
    useEffect(() => {
        graphLoadingRef.current = graphLoading;
        if (graphLoading && skillsRefreshIdentity === identity) graphReloadStarted.current = true;
        if (graphReloadStarted.current && graph && !graphLoading && !graphError && skillsRefreshIdentity === identity) setSkillsRefreshIdentity(null);
        // Only a successful read answers the staleness: after a failed re-read the last graph stays on screen
        // with an error (U2), and it is still the stale copy.
        if (graph && !graphLoading && !graphError && !(skillsRefreshIdentity === identity && !graphReloadStarted.current)) skillsStaleRef.current = false;
    }, [graph, graphLoading, graphError, skillsRefreshIdentity, identity]);

    const notifySkillsChanged = useCallback(() => {
        skillsStaleRef.current = true;
        graphReloadStarted.current = false;
        setSkillsRefreshIdentity(identity);
        reloadGraph();
    }, [reloadGraph, identity]);

    const refreshSkillsIfStale = useCallback(() => {
        // A read already in flight (for example the one notifySkillsChanged started) answers the staleness.
        if (skillsStaleRef.current && !graphLoadingRef.current) reloadGraph();
    }, [reloadGraph]);

    const setTabDirty = useCallback((dirty: boolean) => {
        tabDirtyRef.current = dirty;
    }, []);
    const isTabDirty = useCallback(() => tabDirtyRef.current, []);

    const courseData = courseRead.data?.body ?? null;
    const successfulRead = courseRead.data?.sequence ?? 0;
    // The provider outlives Settings. Retain only confirmed metadata while its read is unresolved, so a remounted
    // form cannot use the pre-save body for another full replacement. Resolve here even with Settings absent.
    // A refusal drops courseData; erase the acknowledgment before recovery can make it visible again.
    const settingsAcknowledgment = settingsSave && settingsSave.identity === identity && courseData
        && successfulRead < settingsSave.reconcileAfter ? settingsSave : null;
    if (settingsSave && !settingsAcknowledgment) setSettingsSave(null);
    const acknowledgeSettings = useCallback((metadata: OwnedCourseWrite) => {
        if (identity === null) return;
        const reconcileAfter = revalidateCourse();
        if (obligation.current?.identity === identity) obligation.current.courseFloor = reconcileAfter;
        setSettingsSave({
            identity,
            metadata: { title: metadata.title, description: metadata.description, prerequisiteDescription: metadata.prerequisiteDescription },
            reconcileAfter,
        });
    }, [identity, revalidateCourse]);
    // The composing owner registers before dispatch. Its controller and settlement outlive child views;
    // the child runner separately suppresses callbacks. One flight serializes dependent Course/graph writes.
    const obligation = useRef<{ identity: string; controller: AbortController; pending: boolean;
        affected: 'course' | 'graph'; courseFloor: number; graphFloor: number } | null>(null);
    const [, renderMutation] = useState(0);
    const [uncertainIdentity, setUncertainIdentity] = useState<string | null>(null);
    const ownerLive = useRef(false);
    const ownerIdentity = useRef(identity);
    ownerIdentity.current = identity;
    useEffect(() => {
        ownerLive.current = true;
        return () => { ownerLive.current = false; obligation.current?.controller.abort(); obligation.current = null; };
    }, [identity]);
    const active = obligation.current?.identity === identity ? obligation.current : null;
    if (active && !active.pending && successfulRead >= active.courseFloor
        && skillGraph.successfulRead >= active.graphFloor) obligation.current = null;
    const blockedRef = useRef(false);
    blockedRef.current = !!obligation.current || !!courseRead.error
        || mutationFloor?.identity === identity && successfulRead < mutationFloor.sequence
        || skillsRefreshIdentity !== null && skillsRefreshIdentity === identity;
    const writesBlocked = useCallback(() => blockedRef.current || !!obligation.current, []);
    const runMutation = useCallback(async <T,>(affected: 'course' | 'graph',
        operation: (options: AuthoringRequestOptions) => Promise<T>, signal: AbortSignal): Promise<T> => {
        if (identity === null || writesBlocked() || !ownerLive.current || !auth.isCurrent(auth.generation)) throw cancelled();
        const record = { identity, controller: new AbortController(), pending: true, affected, courseFloor: 0, graphFloor: 0 };
        obligation.current = record;
        renderMutation(n => n + 1);
        const cancel = () => record.controller.abort();
        signal.addEventListener('abort', cancel, { once: true });
        if (signal.aborted) cancel();
        const current = () => ownerLive.current && ownerIdentity.current === identity
            && obligation.current === record && auth.isCurrent(auth.generation);
        const recover = () => {
            record.pending = false;
            if (affected === 'course' && !record.courseFloor) record.courseFloor = revalidateCourse();
            else if (affected === 'graph') record.graphFloor = reloadGraph();
            renderMutation(n => n + 1);
        };
        try {
            const value = await operation({ signal: record.controller.signal, isCurrent: current });
            if (!current()) throw cancelled();
            recover();
            return value;
        } catch (error) {
            if (!current()) throw cancelled();
            if (isCancelled(error) || outcomeUnknown(error) || error instanceof ApiError && error.kind === 'unknown-outcome') {
                // An inspection after transport loss permits a deliberate next action under CT16/CT37;
                // it does not prove that an unobserved server transaction has terminated.
                setUncertainIdentity(identity);
                recover();
            } else { obligation.current = null; renderMutation(n => n + 1); }
            throw error;
        } finally { signal.removeEventListener('abort', cancel); }
    }, [identity, auth.generation, auth.isCurrent, writesBlocked, revalidateCourse, reloadGraph]);
    const mutationPending = !!active?.pending;
    const mutationBusy = writesBlocked();
    const runCourse = useCallback<AuthoringOperationOwner['run']>((operation, signal) => runMutation('course', operation, signal), [runMutation]);
    const runGraph = useCallback<AuthoringOperationOwner['run']>((operation, signal) => runMutation('graph', operation, signal), [runMutation]);
    const courseMutations = useMemo(() => ({ busy: mutationBusy, pending: mutationPending, run: runCourse }), [mutationBusy, mutationPending, runCourse]);
    const graphMutations = useMemo(() => ({ busy: mutationBusy, pending: mutationPending, run: runGraph }), [mutationBusy, mutationPending, runGraph]);
    const cancelMutation = useCallback(() => obligation.current?.controller.abort(), []);
    const courseStatus: 'loading' | 'ready' | 'error' = courseRead.error && !courseData
        ? 'error'
        : courseData
            ? 'ready'
            : 'loading';
    const revalidating = mutationBusy;
    const course = useMemo(() => ({
        status: courseStatus,
        data: courseData,
        error: courseRead.error,
        loading: courseRead.loading,
        successfulRead,
        reload: reloadCourse,
        revalidate: revalidateCourse,
        revalidating,
    }), [courseStatus, courseData, courseRead.error, courseRead.loading, successfulRead, reloadCourse, revalidateCourse, revalidating]);

    const skills = useMemo<SkillSource>(() => ({
        status: skillGraph.error ? 'error' : skillGraph.graph ? 'ready' : 'loading',
        skills: skillGraph.graph?.skills ?? [],
        reload: skillGraph.reload,
    }), [skillGraph.error, skillGraph.graph, skillGraph.reload]);

    return useMemo(() => ({
        courseId,
        courseMutations, graphMutations, writesBlocked, mutationPending, mutationUncertain: uncertainIdentity === identity && identity !== null, cancelMutation,
        course,
        skillGraph,
        skillsRevalidating: identity !== null && skillsRefreshIdentity === identity,
        skills,
        settingsAcknowledgment,
        acknowledgeSettings,
        notifySkillsChanged,
        refreshSkillsIfStale,
        setTabDirty,
        isTabDirty,
    }), [courseId, courseMutations, graphMutations, writesBlocked, mutationPending, uncertainIdentity, cancelMutation, course, skillGraph, skills, identity, skillsRefreshIdentity, settingsAcknowledgment, acknowledgeSettings, notifySkillsChanged, refreshSkillsIfStale, setTabDirty, isTabDirty]);
}
