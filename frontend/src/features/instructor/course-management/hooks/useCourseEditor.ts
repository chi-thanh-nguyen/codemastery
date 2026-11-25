import { createContext, useCallback, useContext, useEffect, useMemo, useRef } from 'react';
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
import { useAuthoringOperation, useOwnerRead } from './useAssessmentEditor';
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
    const { busy, run } = useAuthoringOperation();
    const actions = useMemo(() => ({
        updateMetadata: (write: OwnedCourseWrite) => run((options) => updateOwnedCourse(courseId, write, options)),
        setPublication: (status: CoursePublicationStatus) => run((options) => setOwnedCoursePublication(courseId, status, options)),
        createChapter: (title: string) => run((options) => createCourseChapter(courseId, title, options)),
        updateChapter: (chapterId: string, title: string) => run((options) => updateCourseChapter(courseId, chapterId, title, options)),
        orderChapters: (orderedIds: readonly string[]) => run((options) => orderCourseChapters(courseId, orderedIds, options)),
        createLesson: (chapterId: string, write: OwnedLessonWrite) => run((options) => createCourseLesson(courseId, chapterId, write, options)),
        orderLessons: (chapterId: string, orderedIds: readonly string[]) => run((options) => orderChapterLessons(courseId, chapterId, orderedIds, options)),
        setLessonPublication: (lessonId: string, status: CoursePublicationStatus) => run((options) => setCourseLessonPublication(courseId, lessonId, status, options)),
    }), [courseId, run]);
    return { busy, ...actions };
}

export interface SharedCourseContextState {
    courseId: string;
    course: {
        status: 'loading' | 'ready' | 'error';
        data: OwnedCourse | null;
        error: unknown;
        reload: () => void;
    };
    skillGraph: OwnedSkillGraphRead;
    skills: SkillSource;
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
    const courseRead = useOwnerRead(`owned-course|${courseId}`, (options) => getOwnedCourse(courseId, options));
    const skillGraph = useOwnedSkillGraph(courseId);
    const { reload: reloadGraph, graph, loading: graphLoading } = skillGraph;
    // True from an S20 skill change until a later graph read succeeds (D12-3). A failed read keeps it set,
    // so the next deliberate opening of the Questions view or the question form reads once more. It is
    // never cleared during render and never triggers a read on its own: a failed read is retried only by
    // such an opening or by "Try again" (CT16.minimum: no automatic GET retry).
    const skillsStaleRef = useRef(false);
    const tabDirtyRef = useRef(false);

    const graphLoadingRef = useRef(graphLoading);
    useEffect(() => {
        graphLoadingRef.current = graphLoading;
        if (graph && !graphLoading) skillsStaleRef.current = false;
    }, [graph, graphLoading]);

    const notifySkillsChanged = useCallback(() => {
        skillsStaleRef.current = true;
        reloadGraph();
    }, [reloadGraph]);

    const refreshSkillsIfStale = useCallback(() => {
        // A read already in flight (for example the one notifySkillsChanged started) answers the staleness.
        if (skillsStaleRef.current && !graphLoadingRef.current) reloadGraph();
    }, [reloadGraph]);

    const setTabDirty = useCallback((dirty: boolean) => {
        tabDirtyRef.current = dirty;
    }, []);
    const isTabDirty = useCallback(() => tabDirtyRef.current, []);

    const courseData = courseRead.data;
    const courseStatus: 'loading' | 'ready' | 'error' = courseRead.error && !courseData
        ? 'error'
        : courseData
            ? 'ready'
            : 'loading';
    const course = useMemo(() => ({
        status: courseStatus,
        data: courseData,
        error: courseRead.error,
        reload: courseRead.reload,
    }), [courseStatus, courseData, courseRead.error, courseRead.reload]);

    const skills = useMemo<SkillSource>(() => ({
        status: skillGraph.error ? 'error' : skillGraph.graph ? 'ready' : 'loading',
        skills: skillGraph.graph?.skills ?? [],
        reload: skillGraph.reload,
    }), [skillGraph.error, skillGraph.graph, skillGraph.reload]);

    return useMemo(() => ({
        courseId,
        course,
        skillGraph,
        skills,
        notifySkillsChanged,
        refreshSkillsIfStale,
        setTabDirty,
        isTabDirty,
    }), [courseId, course, skillGraph, skills, notifySkillsChanged, refreshSkillsIfStale, setTabDirty, isTabDirty]);
}
