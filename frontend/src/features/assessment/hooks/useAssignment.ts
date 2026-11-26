import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import { readLearnerAssignment, submitAssignmentFile } from '../api/assignmentApi';
import type { B5LearnerAssignment, B5LearnerSubmission } from '../api/assignmentApi';

export function useAssignment(courseId: string | undefined, assignmentId: string | undefined) {
    const auth = useAuth();
    const [assignment, setAssignment] = useState<B5LearnerAssignment | null>(null);
    const [loading, setLoading] = useState(true);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);

    const alive = useRef(true);
    const work = useRef<AbortController | null>(null);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            work.current?.abort();
        };
    }, []);

    const loadAssignment = useCallback(async () => {
        if (!courseId || !assignmentId || !auth.isAuthenticated) {
            setLoading(false);
            return;
        }

        const controller = new AbortController();
        work.current?.abort();
        work.current = controller;
        const gen = auth.generation;

        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        try {
            const res = await readLearnerAssignment(courseId, assignmentId, {
                signal: controller.signal,
                isCurrent: () => auth.generation === gen,
            });
            if (!alive.current || work.current !== controller || controller.signal.aborted) return;
            setAssignment(res);
        } catch (err: unknown) {
            if (
                !alive.current ||
                work.current !== controller ||
                controller.signal.aborted ||
                (err instanceof ApiError && err.kind === 'cancelled')
            ) {
                return;
            }
            if (err instanceof ApiError) {
                setErrorStatus(err.status ?? null);
                setErrorMessage(err.message);
            } else {
                setErrorMessage("Couldn't load assignment details.");
            }
        } finally {
            if (alive.current && work.current === controller) {
                setLoading(false);
            }
        }
    }, [courseId, assignmentId, auth.isAuthenticated, auth.generation]);

    useEffect(() => {
        void loadAssignment();
    }, [loadAssignment]);

    const submitFile = useCallback(
        async (file: File): Promise<B5LearnerSubmission> => {
            if (!courseId || !assignmentId || submitting) {
                throw new Error('Assignment context missing.');
            }
            const gen = auth.generation;
            setSubmitting(true);
            setSubmitError(null);

            try {
                const sub = await submitAssignmentFile(courseId, assignmentId, file, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    setAssignment((prev) => (prev ? { ...prev, submission: sub } : prev));
                }
                return sub;
            } catch (err: unknown) {
                const msg = err instanceof Error ? err.message : "Couldn't submit file. Try again.";
                if (alive.current) setSubmitError(msg);
                throw err;
            } finally {
                if (alive.current) setSubmitting(false);
            }
        },
        [courseId, assignmentId, submitting, auth.generation]
    );

    return {
        assignment,
        loading,
        errorStatus,
        errorMessage,
        submitting,
        submitError,
        refresh: loadAssignment,
        submitFile,
    };
}
