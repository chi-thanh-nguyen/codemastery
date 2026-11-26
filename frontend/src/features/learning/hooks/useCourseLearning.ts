import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import {
    completeCourseLesson,
    getCourseProgress,
    getCourseResume,
    getCourseSyllabusForLearning,
    getLearnerLesson,
    recordCourseVisit,
} from '../api/learningApi';
import type {
    CourseProgress,
    CourseResume,
    LearnerLesson,
    LearningCourseStructure,
} from '../types/learningTypes';

export function useCourseLearning(courseId: string | undefined) {
    const auth = useAuth();
    const [course, setCourse] = useState<LearningCourseStructure | null>(null);
    const [progress, setProgress] = useState<CourseProgress | null>(null);
    const [resume, setResume] = useState<CourseResume | null>(null);
    const [loading, setLoading] = useState(false);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const [completing, setCompleting] = useState(false);
    const [completionError, setCompletionError] = useState<string | null>(null);

    const alive = useRef(true);
    const work = useRef<AbortController | null>(null);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            work.current?.abort();
        };
    }, []);

    const loadWorkspace = useCallback(async () => {
        if (!courseId || !auth.isAuthenticated) {
            setLoading(false);
            return;
        }

        const controller = new AbortController();
        work.current?.abort();
        work.current = controller;
        const gen = auth.generation;
        const opts = { signal: controller.signal, isCurrent: () => auth.generation === gen };

        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        try {
            const [syllabusRes, progressRes, resumeRes] = await Promise.all([
                getCourseSyllabusForLearning(courseId, opts),
                getCourseProgress(courseId, opts),
                getCourseResume(courseId, opts),
            ]);
            if (!alive.current || work.current !== controller || controller.signal.aborted) return;
            setCourse(syllabusRes);
            setProgress(progressRes);
            setResume(resumeRes);
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
                setErrorMessage("Couldn't load this course. Try again.");
            }
        } finally {
            if (alive.current && work.current === controller) {
                setLoading(false);
            }
        }
    }, [courseId, auth.isAuthenticated, auth.generation]);

    useEffect(() => {
        loadWorkspace();
    }, [loadWorkspace]);

    const markComplete = useCallback(
        async (lessonId: string) => {
            if (!courseId || completing) return;
            const gen = auth.generation;
            setCompleting(true);
            setCompletionError(null);
            try {
                const updated = await completeCourseLesson(courseId, lessonId, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    setProgress(updated);
                }
                return updated;
            } catch (err: unknown) {
                if (alive.current && auth.generation === gen) {
                    setCompletionError(
                        err instanceof Error ? err.message : "Couldn't mark lesson as complete. Try again."
                    );
                }
                throw err;
            } finally {
                if (alive.current) setCompleting(false);
            }
        },
        [courseId, completing, auth.generation]
    );

    // Silent post-render visit recording (no retry, no error message on failure per F4)
    const recordVisit = useCallback(
        async (lessonId: string) => {
            if (!courseId || !auth.isAuthenticated) return;
            const gen = auth.generation;
            try {
                const updated = await recordCourseVisit(courseId, lessonId, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    setResume(updated);
                }
            } catch {
                // Silent ignore per CT22.resume / F4
            }
        },
        [courseId, auth.isAuthenticated, auth.generation]
    );

    return {
        course,
        progress,
        resume,
        loading,
        errorStatus,
        errorMessage,
        completing,
        completionError,
        refresh: loadWorkspace,
        markComplete,
        recordVisit,
        fetchLesson: (lessonId: string, signal?: AbortSignal): Promise<LearnerLesson> =>
            courseId
                ? getLearnerLesson(courseId, lessonId, { signal })
                : Promise.reject(new Error('Course ID is required.')),
    };
}