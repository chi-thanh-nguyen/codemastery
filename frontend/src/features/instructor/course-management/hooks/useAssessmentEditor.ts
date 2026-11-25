import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError, cancelled } from '../../../../shared/types/apiTypes';
import {
    createAssignment, createQuiz, deleteUnusedHiddenQuiz, DEFAULT_PAGE_SIZE, listOwnedAssignments, listOwnedQuizzes,
    readOwnedAssignment, readOwnedQuiz, replaceAssignment, replaceUnusedHiddenQuiz, setAssignmentPublication, setQuizPublication,
} from '../api/assessmentAuthoringApi';
import type {
    AssignmentPublicationStatus, AuthoringRequestOptions, B3QuizOwnedPage, B3QuizWrite, B5AssignmentWrite, B5OwnedAssignmentPage,
} from '../api/assessmentAuthoringApi';

/*
 * Assessment authoring state (CT16.final-state F1): transient state stays in these hooks and the
 * pages that use them. Every request carries the session generation and an abort signal; a late
 * response of a superseded read, an unmounted page or another session never reaches the view, and
 * nothing is retried or replayed.
 */

export const isCancelled = (error: unknown) => error instanceof ApiError && error.kind === 'cancelled';
/** The HTTP status of a decoded API failure, if any. */
export const failureStatus = (error: unknown) => error instanceof ApiError && error.kind === 'http' ? error.status : undefined;
/** No HTTP outcome was confirmed: a network failure, a 5xx or an invalid response (DESIGN.md S19 "unknown outcome"). */
export const outcomeUnknown = (error: unknown) => error instanceof ApiError &&
    (error.kind === 'network' || error.kind === 'protocol' || error.kind === 'http' && (error.status ?? 0) >= 500);

/**
 * Extracts field-level 400 validation messages from a decoded ProblemDetail (DESIGN.md F14).
 * Unknown field names stay at the form level rather than being silently dropped.
 */
export function fieldErrorsFrom(error: unknown, mapping: Readonly<Record<string, string>>): Record<string, string> {
    if (!(error instanceof ApiError) || error.kind !== 'http' || error.status !== 400 || !error.fields) return {};
    const out: Record<string, string> = {};
    for (const [serverKey, message] of Object.entries(error.fields)) {
        const localKey = mapping[serverKey];
        if (localKey && message) out[localKey] = message;
    }
    return out;
}

export interface OwnerRead<T> {
    /** The decoded body for the current key and session, or null. */
    data: T | null;
    loading: boolean;
    error: unknown;
    /** A new read of the same key; never a mutation replay. */
    reload: () => void;
}

/**
 * One owner read identified by `key` (null disables it). A key or session change discards the
 * previous body before the new read starts, so no other page, resource or session is shown.
 */
export function useOwnerRead<T>(key: string | null, load: (options: AuthoringRequestOptions) => Promise<T>): OwnerRead<T> {
    const auth = useAuth(), generation = auth.generation, authenticated = auth.isAuthenticated, isCurrent = auth.isCurrent;
    const identity = key === null || !authenticated ? null : `${generation}|${key}`;
    const loader = useRef(load);
    loader.current = load;
    const [revision, setRevision] = useState(0);
    const [state, setState] = useState<{ identity: string | null; data: T | null; error: unknown; loading: boolean }>({ identity: null, data: null, error: null, loading: false });
    useEffect(() => {
        if (identity === null) return;
        const controller = new AbortController();
        let live = true;
        const current = () => live && isCurrent(generation);
        setState((previous) => ({ identity, data: previous.identity === identity ? previous.data : null, error: null, loading: true }));
        loader.current({ signal: controller.signal, isCurrent: current }).then(
            (data) => { if (current()) setState({ identity, data, error: null, loading: false }); },
            (error: unknown) => { if (current() && !isCancelled(error)) setState({ identity, data: null, error, loading: false }); });
        return () => { live = false; controller.abort(); };
    }, [identity, generation, isCurrent, revision]);
    const reload = useCallback(() => setRevision((value) => value + 1), []);
    const matches = identity !== null && state.identity === identity;
    const data = matches ? state.data : null, loading = identity !== null && (!matches || state.loading), error = matches ? state.error : null;
    // A stable identity per observable state, so consumers' effects and memoized contexts do not re-run on every render.
    return useMemo(() => ({ data, loading, error, reload }), [data, loading, error, reload]);
}

/**
 * Single-flight operations for one editor. A second operation while one runs is refused locally;
 * unmounting aborts the running request and its late result is discarded (rejected as cancelled).
 */
export function useAuthoringOperation() {
    const auth = useAuth(), generation = auth.generation, isCurrent = auth.isCurrent;
    const alive = useRef(false), work = useRef<AbortController | null>(null);
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; work.current?.abort(); work.current = null; };
    }, []);
    const run = useCallback(<T,>(operation: (options: AuthoringRequestOptions) => Promise<T>): Promise<T> => {
        if (work.current || !alive.current) return Promise.reject(cancelled());
        const controller = new AbortController();
        work.current = controller;
        const current = () => alive.current && work.current === controller && isCurrent(generation);
        setBusy(true);
        return operation({ signal: controller.signal, isCurrent: current })
            .then((value) => { if (!current()) throw cancelled(); return value; },
                (error: unknown) => { throw current() ? error : cancelled(); })
            .finally(() => { if (work.current === controller) { work.current = null; if (alive.current) setBusy(false); } });
    }, [generation, isCurrent]);
    return { busy, run };
}

/** `b3ListOwnedQuizzes` for the Quizzes view: one page in API order. */
export function useOwnedQuizzes(courseId: string, page: number, size = DEFAULT_PAGE_SIZE): OwnerRead<B3QuizOwnedPage> {
    return useOwnerRead(`owned-quizzes|${courseId}|${page}|${size}`, (options) => listOwnedQuizzes(courseId, page, size, options));
}

/** `b5ListOwnedAssignments` for the Assignments view: one page in API order. */
export function useOwnedAssignments(courseId: string, page: number, size = DEFAULT_PAGE_SIZE): OwnerRead<B5OwnedAssignmentPage> {
    return useOwnerRead(`owned-assignments|${courseId}|${page}|${size}`, (options) => listOwnedAssignments(courseId, page, size, options));
}

/** The quiz editor's operations (CT23.composition/publication). Each call resolves only with a decoded, current result. */
export function useQuizEditor(courseId: string) {
    const { busy, run } = useAuthoringOperation();
    // Stable per course and session, so effects that read on entry run once.
    const operations = useMemo(() => ({
        read: (quizId: string) => run((options) => readOwnedQuiz(courseId, quizId, options)),
        create: (write: B3QuizWrite) => run((options) => createQuiz(courseId, write, options)),
        replace: (quizId: string, write: B3QuizWrite) => run((options) => replaceUnusedHiddenQuiz(courseId, quizId, write, options)),
        remove: (quizId: string) => run((options) => deleteUnusedHiddenQuiz(courseId, quizId, options)),
        setPublication: (quizId: string, published: boolean) => run((options) => setQuizPublication(courseId, quizId, published, options)),
    }), [courseId, run]);
    return { busy, ...operations };
}
export type QuizEditorOperations = ReturnType<typeof useQuizEditor>;

/** The assignment editor's operations (CT10.rest). Each call resolves only with a decoded, current result. */
export function useAssignmentEditor(courseId: string) {
    const { busy, run } = useAuthoringOperation();
    const operations = useMemo(() => ({
        read: (assignmentId: string) => run((options) => readOwnedAssignment(courseId, assignmentId, options)),
        create: (write: B5AssignmentWrite) => run((options) => createAssignment(courseId, write, options)),
        replace: (assignmentId: string, write: B5AssignmentWrite) => run((options) => replaceAssignment(courseId, assignmentId, write, options)),
        setPublication: (assignmentId: string, status: AssignmentPublicationStatus) =>
            run((options) => setAssignmentPublication(courseId, assignmentId, status, options)),
    }), [courseId, run]);
    return { busy, ...operations };
}
export type AssignmentEditorOperations = ReturnType<typeof useAssignmentEditor>;
