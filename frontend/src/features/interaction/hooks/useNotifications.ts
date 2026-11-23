import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import {
    countUnreadNotifications,
    listMyNotifications,
    markAllNotificationsRead,
    markNotificationRead,
    type NotificationItemModel,
} from '../api/notificationApi';

export function useNotifications(pageSize = 20) {
    const auth = useAuth();
    const [notifications, setNotifications] = useState<NotificationItemModel[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
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

    const refreshUnreadCount = useCallback(async () => {
        if (!auth.isAuthenticated) {
            setUnreadCount(0);
            return;
        }
        const gen = auth.generation;
        try {
            const res = await countUnreadNotifications({
                isCurrent: () => auth.generation === gen,
            });
            if (alive.current && auth.generation === gen) {
                setUnreadCount(res.unreadCount);
            }
        } catch {

        }
    }, [auth.isAuthenticated, auth.generation]);

    const loadNotifications = useCallback(
        async (unreadOnly = false, targetPage = 0) => {
            if (!auth.isAuthenticated) {
                setNotifications([]);
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
                const result = await listMyNotifications(unreadOnly, targetPage, pageSize, {
                    signal: controller.signal,
                    isCurrent: () => auth.generation === gen,
                });
                if (!alive.current || fetchWork.current !== controller || controller.signal.aborted) return;

                setNotifications(result.content);
                setPage(result.page);
                setTotalPages(result.totalPages);
                setTotalElements(result.totalElements);
                await refreshUnreadCount();
            } catch (err: unknown) {
                if (
                    !alive.current ||
                    fetchWork.current !== controller ||
                    controller.signal.aborted ||
                    (err instanceof ApiError && err.kind === 'cancelled')
                ) {
                    return;
                }
                setError(err instanceof Error ? err.message : "Couldn't load notifications. Try again.");
            } finally {
                if (alive.current && fetchWork.current === controller) {
                    setLoading(false);
                }
            }
        },
        [pageSize, auth.isAuthenticated, auth.generation, refreshUnreadCount]
    );

    useEffect(() => {
        loadNotifications(false, 0);
    }, [loadNotifications]);

    const markRead = useCallback(
        async (notificationId: string) => {
            const gen = auth.generation;
            await markNotificationRead(notificationId, {
                isCurrent: () => auth.generation === gen,
            });
            if (alive.current && auth.generation === gen) {
                setNotifications((prev) =>
                    prev.map((n) => (n.id === notificationId ? { ...n, read: true } : n))
                );
                await refreshUnreadCount();
            }
        },
        [auth.generation, refreshUnreadCount]
    );

    const markAllRead = useCallback(async () => {
        const gen = auth.generation;
        const res = await markAllNotificationsRead({
            isCurrent: () => auth.generation === gen,
        });
        if (alive.current && auth.generation === gen) {
            setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
            setUnreadCount(0);
        }
        return res.markedCount;
    }, [auth.generation]);

    return {
        notifications,
        unreadCount,
        page,
        totalPages,
        totalElements,
        loading,
        error,
        loadPage: (unreadOnly: boolean, p: number) => loadNotifications(unreadOnly, p),
        refreshUnreadCount,
        markRead,
        markAllRead,
    };
}