import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import { readQuizAttempt, saveQuizAnswers, submitQuizAttempt } from '../api/quizApi';
import type {
    B3AnswerEntry,
    B3Attempt,
    B3AttemptQuestion,
    B3AttemptResult,
    SaveMachineState,
} from '../types/assessmentTypes';

// In-memory retention for the life of the document only (never browser storage, per F6 / ADR011 §5)
const sessionMemoryAnswers = new Map<
    string,
    { generation: number; answers: Record<string, boolean[]>; dirty: boolean }
>();

function answersToRecord(questions: B3AttemptQuestion[]): Record<string, boolean[]> {
    const rec: Record<string, boolean[]> = {};
    for (const q of questions) rec[q.questionId] = [...q.selectedOptions];
    return rec;
}

function areAnswersEqual(a: Record<string, boolean[]>, b: Record<string, boolean[]>): boolean {
    const keysA = Object.keys(a);
    const keysB = Object.keys(b);
    if (keysA.length !== keysB.length) return false;
    for (const k of keysA) {
        const arrA = a[k];
        const arrB = b[k];
        if (!arrB || arrA.length !== arrB.length) return false;
        for (let i = 0; i < arrA.length; i += 1) {
            if (arrA[i] !== arrB[i]) return false;
        }
    }
    return true;
}

function findDifferingQuestionIds(
    local: Record<string, boolean[]>,
    server: Record<string, boolean[]>
): string[] {
    const diffs: string[] = [];
    for (const k of Object.keys(local)) {
        const a = local[k] ?? [];
        const b = server[k] ?? [];
        if (a.length !== b.length || a.some((v, idx) => v !== b[idx])) {
            diffs.push(k);
        }
    }
    return diffs;
}

function toPayload(
    questions: B3AttemptQuestion[],
    answers: Record<string, boolean[]>
): B3AnswerEntry[] {
    return questions.map((q) => ({
        questionId: q.questionId,
        selectedOptions: answers[q.questionId] ?? q.selectedOptions.map(() => false),
    }));
}

export function useQuizAttempt(
    courseId: string | undefined,
    quizId: string | undefined,
    attemptId: string | undefined
) {
    const auth = useAuth();
    const [attempt, setAttempt] = useState<B3Attempt | null>(null);
    const [submittedResult, setSubmittedResult] = useState<B3AttemptResult | null>(null);
    const [discardedNotice, setDiscardedNotice] = useState(false);

    const [loading, setLoading] = useState(true);
    const [loadErrorStatus, setLoadErrorStatus] = useState<number | null>(null);
    const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null);

    const [localAnswers, setLocalAnswers] = useState<Record<string, boolean[]>>({});
    const [serverAnswers, setServerAnswers] = useState<Record<string, boolean[]>>({});
    const [differingIds, setDifferingIds] = useState<string[]>([]);
    const [saveState, setSaveState] = useState<SaveMachineState>('Saved');
    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);

    const alive = useRef(true);
    const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const inFlightSave = useRef<Promise<boolean> | null>(null);
    const saveSeq = useRef(0);
    const localRef = useRef<Record<string, boolean[]>>({});
    const attemptRef = useRef<B3Attempt | null>(null);
    const saveStateRef = useRef<SaveMachineState>('Saved');

    const updateSaveState = useCallback((next: SaveMachineState) => {
        saveStateRef.current = next;
        if (alive.current) setSaveState(next);
    }, []);

    const clearDebounce = useCallback(() => {
        if (debounceTimer.current) {
            clearTimeout(debounceTimer.current);
            debounceTimer.current = null;
        }
    }, []);

    const isExpired = useCallback(() => {
        const cur = attemptRef.current;
        if (!cur) return false;
        return Date.now() >= new Date(cur.expiresAt).getTime();
    }, []);

    useEffect(() => {
        for (const [k, entry] of sessionMemoryAnswers.entries()) {
            if (entry.generation !== auth.generation) {
                sessionMemoryAnswers.delete(k);
            }
        }
    }, [auth.generation]);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            clearDebounce();
        };
    }, [clearDebounce]);

    const runCheck = useCallback(async () => {
        if (!courseId || !quizId || !attemptId || !auth.isAuthenticated) return;
        clearDebounce();
        updateSaveState('Checking');
        const gen = auth.generation;

        try {
            const read = await readQuizAttempt(courseId, quizId, attemptId, {
                isCurrent: () => auth.generation === gen,
            });
            if (!alive.current || auth.generation !== gen) return;

            if (read.status === 'submitted') {
                sessionMemoryAnswers.delete(attemptId);
                setSubmittedResult(read);
                return;
            }

            attemptRef.current = read;
            setAttempt(read);
            const srvRec = answersToRecord(read.questions);
            setServerAnswers(srvRec);

            if (read.status === 'overdue' || Date.now() >= new Date(read.expiresAt).getTime()) {
                updateSaveState('Time is up');
                return;
            }

            if (areAnswersEqual(localRef.current, srvRec)) {
                setDifferingIds([]);
                sessionMemoryAnswers.set(attemptId, {
                    generation: gen,
                    answers: localRef.current,
                    dirty: false,
                });
                updateSaveState('Saved');
            } else {
                setDifferingIds(findDifferingQuestionIds(localRef.current, srvRec));
                updateSaveState('Reconcile');
            }
        } catch (err: unknown) {
            if (!alive.current || auth.generation !== gen) return;
            if (err instanceof ApiError && (err.status === 403 || err.status === 404)) {
                sessionMemoryAnswers.delete(attemptId);
                updateSaveState('No access');
            } else {
                updateSaveState('Check failed');
            }
        }
    }, [courseId, quizId, attemptId, auth.isAuthenticated, auth.generation, clearDebounce, updateSaveState]);

    const loadAttempt = useCallback(async () => {
        if (!courseId || !quizId || !attemptId || !auth.isAuthenticated) {
            setLoading(false);
            return;
        }
        const gen = auth.generation;
        setLoading(true);
        setLoadErrorStatus(null);
        setLoadErrorMessage(null);

        try {
            const read = await readQuizAttempt(courseId, quizId, attemptId, {
                isCurrent: () => auth.generation === gen,
            });
            if (!alive.current || auth.generation !== gen) return;

            if (read.status === 'submitted') {
                const mem = sessionMemoryAnswers.get(attemptId);
                if (mem && mem.generation === gen && mem.dirty) {
                    setDiscardedNotice(true);
                }
                sessionMemoryAnswers.delete(attemptId);
                setSubmittedResult(read);
                return;
            }

            attemptRef.current = read;
            setAttempt(read);
            const srvRec = answersToRecord(read.questions);
            setServerAnswers(srvRec);

            if (read.status === 'overdue' || Date.now() >= new Date(read.expiresAt).getTime()) {
                localRef.current = srvRec;
                setLocalAnswers(srvRec);
                updateSaveState('Time is up');
                return;
            }

            const mem = sessionMemoryAnswers.get(attemptId);
            if (mem && mem.generation === gen && !areAnswersEqual(mem.answers, srvRec)) {
                localRef.current = mem.answers;
                setLocalAnswers(mem.answers);
                setDifferingIds(findDifferingQuestionIds(mem.answers, srvRec));
                updateSaveState('Reconcile');
            } else {
                localRef.current = srvRec;
                setLocalAnswers(srvRec);
                sessionMemoryAnswers.set(attemptId, { generation: gen, answers: srvRec, dirty: false });
                updateSaveState('Saved');
            }
        } catch (err: unknown) {
            if (!alive.current || auth.generation !== gen) return;
            if (err instanceof ApiError) {
                setLoadErrorStatus(err.status ?? null);
                setLoadErrorMessage(err.message);
            } else {
                setLoadErrorMessage("We couldn't load this attempt.");
            }
        } finally {
            if (alive.current) setLoading(false);
        }
    }, [courseId, quizId, attemptId, auth.isAuthenticated, auth.generation, updateSaveState]);

    useEffect(() => {
        void loadAttempt();
    }, [loadAttempt]);

    const executeSave = useCallback(async (): Promise<boolean> => {
        const cur = attemptRef.current;
        if (!courseId || !quizId || !attemptId || !cur || !auth.isAuthenticated) return false;

        if (isExpired()) {
            clearDebounce();
            updateSaveState('Time is up');
            return false;
        }

        if (inFlightSave.current) return inFlightSave.current;

        clearDebounce();
        const seq = (saveSeq.current += 1);
        const gen = auth.generation;
        const snapshot = { ...localRef.current };
        updateSaveState('Saving');

        const promise = (async () => {
            try {
                const updated = await saveQuizAnswers(
                    courseId,
                    quizId,
                    attemptId,
                    { answers: toPayload(cur.questions, snapshot) },
                    { isCurrent: () => auth.generation === gen }
                );
                if (!alive.current || auth.generation !== gen || saveSeq.current !== seq) return false;

                attemptRef.current = updated;
                setAttempt(updated);
                const srvRec = answersToRecord(updated.questions);
                setServerAnswers(srvRec);

                if (areAnswersEqual(localRef.current, srvRec)) {
                    setDifferingIds([]);
                    sessionMemoryAnswers.set(attemptId, { generation: gen, answers: localRef.current, dirty: false });
                    updateSaveState('Saved');
                    return true;
                } else {
                    updateSaveState('Pending');
                    inFlightSave.current = null;
                    return executeSave();
                }
            } catch (err: unknown) {
                if (!alive.current || auth.generation !== gen) return false;
                if (err instanceof ApiError && (err.status === 403 || err.status === 404)) {
                    sessionMemoryAnswers.delete(attemptId);
                    updateSaveState('No access');
                    return false;
                }
                await runCheck();
                return false;
            } finally {
                if (saveSeq.current === seq) inFlightSave.current = null;
            }
        })();

        inFlightSave.current = promise;
        return promise;
    }, [courseId, quizId, attemptId, auth.isAuthenticated, auth.generation, isExpired, clearDebounce, updateSaveState, runCheck]);

    useEffect(() => {
        if (!attempt || attempt.status === 'overdue') return;
        const msLeft = new Date(attempt.expiresAt).getTime() - Date.now();
        if (msLeft <= 0) {
            clearDebounce();
            updateSaveState('Time is up');
            return;
        }
        const timer = setTimeout(() => {
            clearDebounce();
            updateSaveState('Time is up');
        }, msLeft);
        return () => clearTimeout(timer);
    }, [attempt, clearDebounce, updateSaveState]);

    const setAnswer = useCallback(
        (questionId: string, selectedOptions: boolean[]) => {
            const state = saveStateRef.current;
            if (state === 'Time is up' || state === 'No access') return;

            const nextLocal = { ...localRef.current, [questionId]: selectedOptions };
            localRef.current = nextLocal;
            setLocalAnswers(nextLocal);

            if (attemptId) {
                sessionMemoryAnswers.set(attemptId, {
                    generation: auth.generation,
                    answers: nextLocal,
                    dirty: true,
                });
            }

            if (state === 'Checking' || state === 'Check failed' || state === 'Reconcile' || state === 'Saving') {
                return;
            }

            updateSaveState('Pending');
            clearDebounce();
            debounceTimer.current = setTimeout(() => {
                void executeSave();
            }, 1000);
        },
        [attemptId, auth.generation, updateSaveState, clearDebounce, executeSave]
    );

    const flushSavePoint = useCallback(() => {
        if (saveStateRef.current === 'Pending') {
            void executeSave();
        }
    }, [executeSave]);

    const reconcileUseLocal = useCallback(() => {
        updateSaveState('Pending');
        void executeSave();
    }, [updateSaveState, executeSave]);

    const reconcileUseServer = useCallback(() => {
        localRef.current = serverAnswers;
        setLocalAnswers(serverAnswers);
        setDifferingIds([]);
        if (attemptId) {
            sessionMemoryAnswers.set(attemptId, {
                generation: auth.generation,
                answers: serverAnswers,
                dirty: false,
            });
        }
        updateSaveState('Saved');
    }, [serverAnswers, attemptId, auth.generation, updateSaveState]);

    const submit = useCallback(async (): Promise<B3AttemptResult | null> => {
        const cur = attemptRef.current;
        if (!courseId || !quizId || !attemptId || !cur || submitting) return null;
        const state = saveStateRef.current;
        if (state !== 'Saved' && state !== 'Pending' && state !== 'Saving') return null;

        setSubmitError(null);
        setSubmitting(true);
        const gen = auth.generation;

        try {
            if (inFlightSave.current) {
                const ok = await inFlightSave.current;
                if (!ok) {
                    setSubmitError("We couldn't save your answers, so the quiz wasn't submitted. Your answers are still on this page.");
                    return null;
                }
            }

            if (saveStateRef.current === 'Pending' || !areAnswersEqual(localRef.current, serverAnswers)) {
                const savedOk = await executeSave();
                if (!savedOk) {
                    setSubmitError("We couldn't save your answers, so the quiz wasn't submitted. Your answers are still on this page.");
                    return null;
                }
            }

            if (isExpired()) {
                updateSaveState('Time is up');
                return null;
            }

            const result = await submitQuizAttempt(
                courseId,
                quizId,
                attemptId,
                { answers: toPayload(cur.questions, localRef.current) },
                { isCurrent: () => auth.generation === gen }
            );
            if (!alive.current || auth.generation !== gen) return null;

            sessionMemoryAnswers.delete(attemptId);
            setSubmittedResult(result);
            return result;
        } catch (err: unknown) {
            if (!alive.current || auth.generation !== gen) return null;
            if (err instanceof ApiError && err.status === 409) {
                await runCheck();
            } else {
                setSubmitError('Something went wrong on our side. Your saved answers are kept. Try submitting again.');
            }
            return null;
        } finally {
            if (alive.current) setSubmitting(false);
        }
    }, [courseId, quizId, attemptId, submitting, auth.generation, serverAnswers, executeSave, isExpired, updateSaveState, runCheck]);

    return {
        attempt,
        submittedResult,
        discardedNotice,
        loading,
        loadErrorStatus,
        loadErrorMessage,
        localAnswers,
        differingIds,
        saveState,
        submitting,
        submitError,
        setAnswer,
        flushSavePoint,
        checkAgain: runCheck,
        reconcileUseLocal,
        reconcileUseServer,
        submit,
        retryLoad: loadAttempt,
    };
}