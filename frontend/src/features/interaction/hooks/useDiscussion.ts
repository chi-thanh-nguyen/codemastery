import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import {
    createLessonDiscussionPost,
    listLessonDiscussion,
    reportDiscussionPost,
    reportLesson,
} from '../api/discussionApi';
import type { DiscussionThread } from '../types/interactionTypes';

export function useDiscussion(lessonId: string | undefined, pageSize = 20) {
    const auth = useAuth();
    const [threads, setThreads] = useState<DiscussionThread[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [posting, setPosting] = useState(false);
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

    const loadPage = useCallback(
        async (targetPage: number, append: boolean) => {
            if (!lessonId || !auth.isAuthenticated) {
                setThreads([]);
                setLoading(false);
                return;
            }

            const controller = new AbortController();
            fetchWork.current?.abort();
            fetchWork.current = controller;
            const gen = auth.generation;

            if (append) {
                setLoadingMore(true);
            } else {
                setLoading(true);
            }
            setError(null);

            try {
                const result = await listLessonDiscussion(lessonId, targetPage, pageSize, {
                    signal: controller.signal,
                    isCurrent: () => auth.generation === gen,
                });
                if (!alive.current || fetchWork.current !== controller || controller.signal.aborted) return;

                setThreads((prev) => (append ? [...prev, ...result.content] : result.content));
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
                    setLoadingMore(false);
                }
            }
        },
        [lessonId, pageSize, auth.isAuthenticated, auth.generation]
    );

    useEffect(() => {
        loadPage(0, false);
    }, [loadPage]);

    const askQuestion = useCallback(
        async (content: string) => {
            if (!lessonId) throw new Error('Lesson ID is required.');
            const gen = auth.generation;
            setPosting(true);
            try {
                const created = await createLessonDiscussionPost(
                    lessonId,
                    { content },
                    { isCurrent: () => auth.generation === gen }
                );
                const newThread: DiscussionThread = {
                    id: created.id,
                    authorId: created.authorId,
                    authorDisplayName: created.authorDisplayName,
                    authorRole: created.authorRole,
                    mine: created.mine,
                    content: created.content,
                    createdAt: created.createdAt,
                    replies: [],
                };
                if (alive.current && auth.generation === gen) {
                    // Newest questions appear at the top (DESIGN.md S8a / F10)
                    setThreads((prev) => [newThread, ...prev]);
                    setTotalElements((prev) => prev + 1);
                }
                return created;
            } finally {
                if (alive.current) setPosting(false);
            }
        },
        [lessonId, auth.generation]
    );

    const replyToQuestion = useCallback(
        async (parentPostId: string, content: string) => {
            if (!lessonId) throw new Error('Lesson ID is required.');
            const gen = auth.generation;
            setPosting(true);
            try {
                const created = await createLessonDiscussionPost(
                    lessonId,
                    { content, parentPostId },
                    { isCurrent: () => auth.generation === gen }
                );
                if (alive.current && auth.generation === gen) {
                    // Replies are ordered oldest first (append to bottom of thread replies)
                    setThreads((prev) =>
                        prev.map((thread) =>
                            thread.id === parentPostId
                                ? {
                                    ...thread,
                                    replies: [
                                        ...thread.replies,
                                        {
                                            id: created.id,
                                            parentPostId,
                                            authorId: created.authorId,
                                            authorDisplayName: created.authorDisplayName,
                                            authorRole: created.authorRole,
                                            mine: created.mine,
                                            content: created.content,
                                            createdAt: created.createdAt,
                                        },
                                    ],
                                }
                                : thread
                        )
                    );
                }
                return created;
            } finally {
                if (alive.current) setPosting(false);
            }
        },
        [lessonId, auth.generation]
    );

    const submitPostReport = useCallback(
        async (postId: string, reason: string) => {
            if (!lessonId) throw new Error('Lesson ID is required.');
            const gen = auth.generation;
            return reportDiscussionPost(
                lessonId,
                postId,
                { reason },
                { isCurrent: () => auth.generation === gen }
            );
        },
        [lessonId, auth.generation]
    );

    const submitLessonReport = useCallback(
        async (reason: string) => {
            if (!lessonId) throw new Error('Lesson ID is required.');
            const gen = auth.generation;
            return reportLesson(lessonId, { reason }, { isCurrent: () => auth.generation === gen });
        },
        [lessonId, auth.generation]
    );

    const hasMore = page + 1 < totalPages;

    return {
        threads,
        totalElements,
        loading,
        loadingMore,
        posting,
        error,
        hasMore,
        loadMore: () => hasMore && loadPage(page + 1, true),
        refresh: () => loadPage(0, false),
        askQuestion,
        replyToQuestion,
        submitPostReport,
        submitLessonReport,
    };
}