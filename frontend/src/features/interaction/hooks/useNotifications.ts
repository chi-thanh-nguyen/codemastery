import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import { countUnreadNotifications, listMyNotifications, markAllNotificationsRead, markNotificationRead } from '../api/notificationApi';
import type { NotificationPage } from '../api/notificationApi';

interface Query { unreadOnly: boolean; page: number }
interface Feedback { severity: 'success' | 'error'; message: string; generation: number; routeKey: string }
interface ListState { generation: number; routeKey: string; query: Query; revision: number; data?: NotificationPage; error?: string }
const firstPage: Query = { unreadOnly: false, page: 0 };
const failure = (error: unknown) => error instanceof ApiError ? error.message : 'The request could not be completed. Please try again.';
const isCancelled = (error: unknown) => error instanceof ApiError && error.kind === 'cancelled';

/**
 * One feature hook at Workspace, the bell/page's closest composing owner (CT16 F1).
 * Only an open production page reads the list. Reads and feedback belong to both the
 * current session and operation; no optimistic count, cache, polling or mutation replay.
 */
export function useNotifications({ enabled, open, routeKey }: { enabled: boolean; open: boolean; routeKey: string }) {
    const auth = useAuth(), generation = auth.generation;
    const [selection, setSelection] = useState({ generation, query: firstPage });
    const query = selection.generation === generation ? selection.query : firstPage;
    const setQuery = useCallback((next: Query | ((value: Query) => Query)) => {
        setSelection((previous) => ({ generation, query: typeof next === 'function'
            ? next(previous.generation === generation ? previous.query : firstPage) : next }));
    }, [generation]);
    const [revision, setRevision] = useState(0);
    const [list, setList] = useState<ListState | null>(null);
    const [count, setCount] = useState<{ generation: number; value: number | null } | null>(null);
    const [feedback, setFeedback] = useState<Feedback | null>(null);
    const [pending, setPending] = useState<{ generation: number; id: string } | null>(null);
    const alive = useRef(false), countWork = useRef<AbortController | null>(null), listWork = useRef<AbortController | null>(null);
    const mutation = useRef<AbortController | null>(null);
    const latest = useRef({ enabled, open, routeKey, generation, query, revision });
    latest.current = { enabled, open, routeKey, generation, query, revision };
    const current = useCallback(() => alive.current && enabled && latest.current.enabled &&
        latest.current.generation === generation && auth.isCurrent(generation), [auth.isCurrent, enabled, generation]);

    useEffect(() => {
        alive.current = true;
        setPending(null); setCount(null); setList(null);
        return () => {
            alive.current = false;
            countWork.current?.abort(); listWork.current?.abort(); mutation.current?.abort(); mutation.current = null;
        };
    }, [generation, enabled]);

    const refreshUnreadCount = useCallback(async () => {
        countWork.current?.abort();
        if (!current()) return;
        const work = new AbortController(); countWork.current = work;
        const valid = () => current() && countWork.current === work && !work.signal.aborted;
        try {
            const result = await countUnreadNotifications({ signal: work.signal, isCurrent: valid });
            if (valid()) setCount({ generation, value: result.unreadCount });
        } catch {
            // Unknown is distinct from zero: hide a previously visible badge on any current failure.
            if (valid()) setCount({ generation, value: null });
        }
    }, [current, generation]);
    useEffect(() => { void refreshUnreadCount(); return () => countWork.current?.abort(); }, [refreshUnreadCount, routeKey]);

    useEffect(() => {
        listWork.current?.abort();
        if (!open || !current()) { setQuery(firstPage); setList(null); return; }
        const work = new AbortController(); listWork.current = work;
        const valid = () => current() && latest.current.open && latest.current.routeKey === routeKey &&
            latest.current.query === query && latest.current.revision === revision && listWork.current === work && !work.signal.aborted;
        // A mutation invalidates all earlier reads; reconcile only after its outcome settles.
        if (mutation.current) return () => work.abort();
        setList(null);
        void listMyNotifications(query.unreadOnly, query.page, 20, { signal: work.signal, isCurrent: valid }).then((data) => {
            if (!valid()) return;
            if (data.page > 0 && data.content.length === 0) {
                // Concurrent read/remove-from-Unread can shrink the last page. The next approved
                // server read targets the last extant page, or page zero when nothing remains.
                setQuery({ ...query, page: Math.max(0, data.totalPages - 1) });
                return;
            }
            setList({ generation, routeKey, query, revision, data });
        }, (error: unknown) => {
            if (valid() && !isCancelled(error)) setList({ generation, routeKey, query, revision, error: failure(error) });
        });
        return () => work.abort();
    }, [open, routeKey, current, generation, query, revision, setQuery]);

    // Leaving the page or replacing its session cancels owned callbacks, including writes whose
    // server outcome may already have committed. Reopening re-reads; it never replays that write.
    useEffect(() => {
        setFeedback(null);
        return () => { mutation.current?.abort(); };
    }, [open, routeKey, generation]);

    const mutate = useCallback(async (id?: string) => {
        if (!current() || !latest.current.open || mutation.current) return;
        const work = new AbortController(); mutation.current = work;
        const startedRoute = latest.current.routeKey;
        const valid = () => current() && latest.current.open && latest.current.routeKey === startedRoute &&
            mutation.current === work && !work.signal.aborted;
        countWork.current?.abort(); listWork.current?.abort();
        setPending({ generation, id: id ?? 'all' }); setFeedback(null);
        try {
            const options = { signal: work.signal, isCurrent: valid };
            if (id) {
                await markNotificationRead(id, options);
                if (valid()) setFeedback({ generation, routeKey: startedRoute, severity: 'success', message: 'Marked notification as read.' });
            } else {
                const result = await markAllNotificationsRead(options);
                if (valid()) setFeedback({ generation, routeKey: startedRoute, severity: 'success', message: `Marked ${result.markedCount} notifications as read.` });
            }
        } catch (error) {
            if (valid() && !isCancelled(error)) setFeedback({ generation, routeKey: startedRoute, severity: 'error', message: failure(error) });
        } finally {
            if (mutation.current === work) {
                mutation.current = null;
                if (current()) {
                    setPending(null);
                    // Even a lost mutation response can hide a committed change. Reconcile via GETs,
                    // and count concurrent newly-created rows rather than forcing the badge to zero.
                    setRevision((value) => value + 1);
                    void refreshUnreadCount();
                }
            }
        }
    }, [current, generation, refreshUnreadCount]);

    const validList = current() && open && list?.generation === generation && list.routeKey === routeKey && list.query === query && list.revision === revision ? list : null;
    const validFeedback = current() && open && feedback?.generation === generation && feedback.routeKey === routeKey ? feedback : null;
    return {
        notifications: validList?.data?.content ?? [],
        unreadCount: current() && count?.generation === generation ? count.value : null,
        filter: query.unreadOnly ? 'unread' as const : 'all' as const,
        page: query.page, totalPages: validList?.data?.totalPages ?? 0, totalElements: validList?.data?.totalElements ?? 0,
        loading: open && enabled && !validList, error: validList?.error ?? null,
        pending: current() && pending?.generation === generation ? pending.id : null,
        feedback: validFeedback,
        setFilter: (filter: 'all' | 'unread') => setQuery({ unreadOnly: filter === 'unread', page: 0 }),
        setPage: (page: number) => setQuery((value) => ({ ...value, page })),
        reload: () => setRevision((value) => value + 1),
        markRead: (id: string) => mutate(id), markAllRead: () => mutate(),
    };
}
export type NotificationsState = ReturnType<typeof useNotifications>;
