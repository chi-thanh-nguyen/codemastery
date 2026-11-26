import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError, cancelled } from '../../../../shared/types/apiTypes';
import {
    addCoursePrerequisite, createCourseSkill, deleteCourseSkill, getOwnedSkillGraph,
    mapCourseLessonSkill, removeCoursePrerequisite, unmapCourseLessonSkill, updateCourseSkill,
} from '../api/skillGraphApi';
import type { OwnedSkillGraph, SkillGraphRequestOptions, SkillWritePayload } from '../api/skillGraphApi';
import { useAuthoringOperation } from '../../course-management/hooks/useAssessmentEditor';
import type { AuthoringOperationOwner } from '../../course-management/hooks/useAssessmentEditor';
import { keepsLastBody } from '../../course-management/hooks/useAssessmentEditor';

export interface OwnedSkillGraphRead {
    /**
     * The decoded graph of the current course and session, or null. After a failed re-read whose
     * outcome is unknown it is the last graph read for the same course and session, with `error` set.
     */
    graph: OwnedSkillGraph | null;
    loading: boolean;
    error: unknown;
    /** A fresh read, for "Try again", after mutations, and for a stale copy. */
    reload: () => number;
    successfulRead: number;
}

/**
 * `getOwnedSkillGraph` state (CT05.representation; DESIGN.md D12-3). The composing owner
 * (the course editor shell) holds one read per course and session and passes the graph to the
 * views that need it (CT16.final-state F1); there is no shared module cache. A course or session
 * change discards the previous graph before the new read starts.
 */
export function useOwnedSkillGraph(courseId: string): OwnedSkillGraphRead {
    const auth = useAuth(), generation = auth.generation, authenticated = auth.isAuthenticated, isCurrent = auth.isCurrent;
    const identity = authenticated ? `${generation}|${courseId}` : null;
    const [revision, setRevision] = useState(0);
    const readSequence = useRef(0);
    const [state, setState] = useState<{ identity: string | null; graph: OwnedSkillGraph | null; error: unknown; loading: boolean; sequence: number }>({ identity: null, graph: null, error: null, loading: false, sequence: 0 });
    useEffect(() => {
        if (identity === null) return;
        const controller = new AbortController();
        let live = true;
        const current = () => live && isCurrent(generation);
        setState((previous) => ({ identity, sequence: previous.sequence, graph: previous.identity === identity ? previous.graph : null, error: null, loading: true }));
        const sequence = ++readSequence.current;
        getOwnedSkillGraph(courseId, { signal: controller.signal, isCurrent: current }).then(
            (graph) => { if (current()) setState({ identity, graph, error: null, loading: false, sequence }); },
            (error: unknown) => {
                if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) {
                    // Same rule as useOwnerRead: only an unknown outcome keeps the last graph of this identity.
                    setState((previous) => ({
                        identity,
                        sequence: previous.sequence,
                        graph: keepsLastBody(error) && previous.identity === identity ? previous.graph : null,
                        error,
                        loading: false,
                    }));
                }
            });
        return () => { live = false; controller.abort(); };
    }, [identity, courseId, generation, isCurrent, revision]);
    const reload = useCallback(() => { const floor = readSequence.current + 1; setRevision((value) => value + 1); return floor; }, []);
    const matches = identity !== null && state.identity === identity;
    const graph = matches ? state.graph : null, loading = identity !== null && (!matches || state.loading), error = matches ? state.error : null;
    // Stable per observable state: the course editor context and its consumers' effects must not change identity on every render.
    const successfulRead = matches ? state.sequence : 0;
    return useMemo(() => ({ graph, loading, error, reload, successfulRead }), [graph, loading, error, reload, successfulRead]);
}

/** Single-flight CT05 skill, prerequisite and lesson-mapping mutations bound to the session generation. */
export function useSkillGraphMutations(courseId: string, mappingOwner?: AuthoringOperationOwner, blocked?: () => boolean) {
    const mapping = useAuthoringOperation(mappingOwner);
    const auth = useAuth(), generation = auth.generation, isCurrent = auth.isCurrent;
    const alive = useRef(false), work = useRef<AbortController | null>(null);
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; work.current?.abort(); work.current = null; };
    }, []);
    const run = useCallback(<T,>(operation: (options: SkillGraphRequestOptions) => Promise<T>): Promise<T> => {
        if (work.current || !alive.current || blocked?.()) return Promise.reject(cancelled());
        const controller = new AbortController();
        work.current = controller;
        const current = () => alive.current && work.current === controller && isCurrent(generation);
        setBusy(true);
        return operation({ signal: controller.signal, isCurrent: current })
            .then((value) => { if (!current()) throw cancelled(); return value; },
                (error: unknown) => { throw current() ? error : cancelled(); })
            .finally(() => { if (work.current === controller) { work.current = null; if (alive.current) setBusy(false); } });
    }, [generation, isCurrent, blocked]);

    const actions = useMemo(() => ({
        createSkill: (payload: SkillWritePayload) => run((options) => createCourseSkill(courseId, payload, options)),
        updateSkill: (skillId: string, payload: SkillWritePayload) => run((options) => updateCourseSkill(courseId, skillId, payload, options)),
        deleteSkill: (skillId: string) => run((options) => deleteCourseSkill(courseId, skillId, options)),
        addPrerequisite: (skillId: string, prerequisiteSkillId: string) => run((options) => addCoursePrerequisite(courseId, skillId, prerequisiteSkillId, options)),
        removePrerequisite: (skillId: string, prerequisiteSkillId: string) => run((options) => removeCoursePrerequisite(courseId, skillId, prerequisiteSkillId, options)),
        mapLesson: (lessonId: string, skillId: string) => mapping.run((options) => mapCourseLessonSkill(courseId, lessonId, skillId, options)),
        unmapLesson: (lessonId: string, skillId: string) => mapping.run((options) => unmapCourseLessonSkill(courseId, lessonId, skillId, options)),
    }), [courseId, run, mapping.run]);
    return { busy: busy || mapping.busy, run, ...actions };
}
