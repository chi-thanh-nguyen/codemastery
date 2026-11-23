import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError } from '../../../../shared/types/apiTypes';
import {
    createQuestion,
    deleteUnusedQuestion,
    listQuestions,
    replaceUnusedQuestion,
    type B3Question,
    type B3QuestionWrite,
} from '../api/assessmentAuthoringApi';

export function useQuestionBank(courseId: string | undefined, pageSize = 20) {
    const auth = useAuth();
    const [questions, setQuestions] = useState<B3Question[]>([]);
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

    const loadQuestions = useCallback(
        async (targetPage: number) => {
            if (!courseId || !auth.isAuthenticated) {
                setQuestions([]);
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
                const result = await listQuestions(courseId, targetPage, pageSize, {
                    signal: controller.signal,
                    isCurrent: () => auth.generation === gen,
                });
                if (!alive.current || fetchWork.current !== controller || controller.signal.aborted) return;

                setQuestions(result.content);
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
                setError(err instanceof Error ? err.message : "Couldn't load questions. Try again.");
            } finally {
                if (alive.current && fetchWork.current === controller) {
                    setLoading(false);
                }
            }
        },
        [courseId, pageSize, auth.isAuthenticated, auth.generation]
    );

    useEffect(() => {
        loadQuestions(0);
    }, [loadQuestions]);

    const saveQuestion = useCallback(
        async (payload: B3QuestionWrite, questionId?: string) => {
            if (!courseId) throw new Error('Course ID is required.');
            const gen = auth.generation;
            setSaving(true);
            try {
                let saved: B3Question;
                if (questionId) {
                    saved = await replaceUnusedQuestion(courseId, questionId, payload, {
                        isCurrent: () => auth.generation === gen,
                    });
                } else {
                    saved = await createQuestion(courseId, payload, {
                        isCurrent: () => auth.generation === gen,
                    });
                }
                if (alive.current && auth.generation === gen) {
                    await loadQuestions(page);
                }
                return saved;
            } finally {
                if (alive.current) setSaving(false);
            }
        },
        [courseId, page, auth.generation, loadQuestions]
    );

    const removeQuestion = useCallback(
        async (questionId: string) => {
            if (!courseId) throw new Error('Course ID is required.');
            const gen = auth.generation;
            setSaving(true);
            try {
                const deleted = await deleteUnusedQuestion(courseId, questionId, {
                    isCurrent: () => auth.generation === gen,
                });
                if (alive.current && auth.generation === gen) {
                    await loadQuestions(page);
                }
                return deleted;
            } finally {
                if (alive.current) setSaving(false);
            }
        },
        [courseId, page, auth.generation, loadQuestions]
    );

    return {
        questions,
        page,
        totalPages,
        totalElements,
        loading,
        saving,
        error,
        loadPage: (p: number) => loadQuestions(p),
        refresh: () => loadQuestions(page),
        saveQuestion,
        removeQuestion,
    };
}