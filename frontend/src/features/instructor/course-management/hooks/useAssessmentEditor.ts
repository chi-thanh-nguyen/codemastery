import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError } from '../../../../shared/types/apiTypes';
import {
    createQuiz,
    deleteUnusedHiddenQuiz,
    listOwnedQuizzes,
    readOwnedQuiz,
    replaceUnusedHiddenQuiz,
    setQuizPublication,
    type B3QuizOwned,
    type B3QuizWrite,
} from '../api/assessmentAuthoringApi';

export function useAssessmentEditor(courseId: string | undefined, pageSize = 20) {
    const auth = useAuth();
    const [quizzes, setQuizzes] = useState<B3QuizOwned[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const alive = useRef(true);
    const fetchWork = useRef<AbortController | null>(null);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            fetchWork.current?.abort();
        };
    }, []);

    const loadQuizzes = useCallback(
        async (targetPage: number) => {
            if (!courseId || !auth.isAuthenticated) {
                setQuizzes([]);
                setLoading(false);
                return;
            }

            const controller = new AbortController();
            fetchWork.current?.abort();
            fetchWork.current = controller;
            const gen = auth.generation;

            setLoading(true);
            setError(null);

            try {
                const result = await listOwnedQuizzes(courseId, targetPage, pageSize, {
                    signal: controller.signal,
                    isCurrent: () => auth.generation === gen,
                });
                if (!alive.current || fetchWork.current !== controller || controller.signal.aborted) return;

                setQuizzes(result.content);
                setPage(result.page);
                setTotalPages(result.totalPages);
                setTotalElements(result.totalElements);
            } catch (err: unknown) {
                if (
                    !alive.current ||
                    fetchWork.current !== controller ||
                    controller.signal.aborted ||
                    (err instanceof ApiError && err.kind === 'cancelled')
                ) {
                    return;
                }
                setError(err instanceof Error ? err.message : "Couldn't load quizzes. Try again.");
            } finally {
                if (alive.current && fetchWork.current === controller) {
                    setLoading(false);
                }
            }
        },
        [courseId, pageSize, auth.isAuthenticated, auth.generation]
    );

    useEffect(() => {
        loadQuizzes(0);
    }, [loadQuizzes]);

    const saveQuiz = useCallback(
        async (payload: B3QuizWrite, quizId?: string) => {
            if (!courseId) throw new Error('Course ID is required.');
            const gen = auth.generation;
            setSaving(true);
            try {
                let saved: B3QuizOwned;
                if (quizId) {
                    saved = await replaceUnusedHiddenQuiz(courseId, quizId, payload, {
                        isCurrent: () => auth.generation === gen,
                    });
                } else {
                    saved = await createQuiz(courseId, payload, {
                        isCurrent: () => auth.generation === gen,
                    });
                }
                if (alive.current && auth.generation === gen) {
                    await loadQuizzes(page);
                }
                return saved;
            } finally {
                if (alive.current) setSaving(false);
            }
        },
        [courseId, page, auth.generation, loadQuizzes]
    );

    const togglePublication = useCallback(
        async (quizId: string, published: boolean) => {
            if (!courseId) throw new Error('Course ID is required.');
            const gen = auth.generation;
            setSaving(true);
            try {
                const updated = await setQuizPublication(courseId, quizId, published, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    await loadQuizzes(page);
                }
                return updated;
            } finally {
                if (alive.current) setSaving(false);
            }
        },
        [courseId, page, auth.generation, loadQuizzes]
    );

    const removeQuiz = useCallback(
        async (quizId: string) => {
            if (!courseId) throw new Error('Course ID is required.');
            const gen = auth.generation;
            setSaving(true);
            try {
                const deleted = await deleteUnusedHiddenQuiz(courseId, quizId, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    await loadQuizzes(page);
                }
                return deleted;
            } finally {
                if (alive.current) setSaving(false);
            }
        },
        [courseId, page, auth.generation, loadQuizzes]
    );

    return {
        quizzes,
        page,
        totalPages,
        totalElements,
        loading,
        saving,
        error,
        loadPage: (p: number) => loadQuizzes(p),
        refresh: () => loadQuizzes(page),
        saveQuiz,
        togglePublication,
        removeQuiz,
        getQuiz: (quizId: string) => (courseId ? readOwnedQuiz(courseId, quizId) : Promise.reject()),
    };
}