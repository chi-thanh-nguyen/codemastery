// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../app/providers/AuthProvider';
import { ApiError } from '../../../shared/types/apiTypes';
import { useNotifications } from './useNotifications';
import type { NotificationsState } from './useNotifications';
import { countUnreadNotifications, listMyNotifications, markAllNotificationsRead, markNotificationRead } from '../api/notificationApi';
import type { NotificationPage } from '../api/notificationApi';
vi.mock('../api/notificationApi', () => ({ countUnreadNotifications: vi.fn(), listMyNotifications: vi.fn(), markAllNotificationsRead: vi.fn(), markNotificationRead: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const id = '00000000-0000-3000-8000-000000000001';
const row = { id, message: 'Current session', read: false, createdAt: '2026-11-26T00:00:00Z' };
const page = (message = 'Current session'): NotificationPage => ({ content: [{ ...row, message }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
const deferred = <T,>() => { let resolve!: (value: T) => void, reject!: (error: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; };
let root: Root, host: HTMLDivElement, generation: number, auth: AuthContextValue, output: NotificationsState;
let options: { enabled: boolean; open: boolean; routeKey: string };
function Probe() { output = useNotifications(options); return <div>{output.notifications.map((item) => item.message)}{output.feedback?.message}</div>; }
async function render(strict = false) { await act(async () => root.render(<AuthContext.Provider value={auth}>{strict ? <StrictMode><Probe /></StrictMode> : <Probe />}</AuthContext.Provider>)); }
beforeEach(() => {
    generation = 1; options = { enabled: true, open: true, routeKey: 'a' };
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id, displayName: 'Learner', role: 'Learner' }, get generation() { return generation; }, isAuthenticated: true, restoring: false,
        isCurrent: (value) => value === generation, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), startPath: '/home', consumeReturn: () => '/home' };
    vi.mocked(listMyNotifications).mockResolvedValue(page()); vi.mocked(countUnreadNotifications).mockResolvedValue({ unreadCount: 1 });
    vi.mocked(markNotificationRead).mockResolvedValue({ ...row, read: true }); vi.mocked(markAllNotificationsRead).mockResolvedValue({ markedCount: 1 });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.resetAllMocks(); });

describe('Workspace-owned FE28 state', () => {
    it('reads only the count with the bell mounted and rereads it on each navigation', async () => {
        options.open = false; await render(); expect(listMyNotifications).not.toHaveBeenCalled(); expect(output.unreadCount).toBe(1);
        options = { ...options, routeKey: 'b' }; await render(); expect(countUnreadNotifications).toHaveBeenCalledTimes(2); expect(listMyNotifications).not.toHaveBeenCalled();
        options = { ...options, open: true, routeKey: 'c' }; await render(); expect(listMyNotifications).toHaveBeenCalledTimes(1); expect(markNotificationRead).not.toHaveBeenCalled(); expect(markAllNotificationsRead).not.toHaveBeenCalled();
    });
    it('does not read for excluded roles or anonymous sessions', async () => {
        options.enabled = false; await render(); expect(countUnreadNotifications).not.toHaveBeenCalled(); expect(listMyNotifications).not.toHaveBeenCalled(); expect(output.unreadCount).toBeNull();
    });
    it('hides a previously visible badge silently after a count-read failure', async () => {
        await render(); vi.mocked(countUnreadNotifications).mockRejectedValueOnce(new ApiError('network', 'Offline'));
        options = { ...options, routeKey: 'b' }; await render(); expect(output.unreadCount).toBeNull(); expect(output.error).toBeNull();
    });
    it('cancels rapid filters/pages and ignores an out-of-order success', async () => {
        const pending = deferred<NotificationPage>(); vi.mocked(listMyNotifications).mockReturnValueOnce(pending.promise);
        await render(); const old = vi.mocked(listMyNotifications).mock.calls[0][3]!;
        await act(async () => output.setFilter('unread')); expect(old.signal?.aborted).toBe(true); expect(old.isCurrent?.()).toBe(false);
        expect(vi.mocked(listMyNotifications).mock.calls[1].slice(0, 3)).toEqual([true, 0, 20]);
        await act(async () => pending.resolve(page('Stale'))); expect(host.textContent).not.toContain('Stale'); expect(output.filter).toBe('unread');
    });
    it('discards a superseded error and permits an explicit read retry', async () => {
        const pending = deferred<NotificationPage>(); vi.mocked(listMyNotifications).mockReturnValueOnce(pending.promise);
        await render(); await act(async () => output.reload()); await act(async () => pending.reject(new ApiError('network', 'Stale failure')));
        expect(output.error).toBeNull(); vi.mocked(listMyNotifications).mockRejectedValueOnce(new ApiError('network', 'Try again'));
        await act(async () => output.reload()); expect(output.error).toBe('Try again'); await act(async () => output.reload()); expect(output.error).toBeNull();
    });
    it('never lets an older count overwrite a navigation refresh', async () => {
        const old = deferred<{ unreadCount: number }>(); vi.mocked(countUnreadNotifications).mockReturnValueOnce(old.promise);
        await render(); options = { ...options, routeKey: 'b' }; await render(); await act(async () => old.resolve({ unreadCount: 99 })); expect(output.unreadCount).toBe(1);
    });
    it('serializes duplicate/overlapping mutations and reconciles mark-all with concurrently created notifications', async () => {
        await render(); const pending = deferred<{ markedCount: number }>(); vi.mocked(markAllNotificationsRead).mockReturnValueOnce(pending.promise);
        let write!: Promise<void>;
        await act(async () => { write = output.markAllRead(); void output.markAllRead(); void output.markRead(id); });
        expect(markAllNotificationsRead).toHaveBeenCalledTimes(1); expect(markNotificationRead).not.toHaveBeenCalled(); expect(output.pending).toBe('all');
        vi.mocked(countUnreadNotifications).mockResolvedValueOnce({ unreadCount: 2 }); vi.mocked(listMyNotifications).mockResolvedValueOnce(page('New after mark-all'));
        await act(async () => { pending.resolve({ markedCount: 7 }); await write; });
        expect(output.unreadCount).toBe(2); expect(output.notifications[0].message).toBe('New after mark-all'); expect(output.feedback?.message).toBe('Marked 7 notifications as read.'); expect(output.pending).toBeNull();
    });
    it('reconciles the Unread filter after a single mutation and recovers a shrunken last page', async () => {
        await render(); await act(async () => output.setFilter('unread'));
        vi.mocked(listMyNotifications).mockResolvedValueOnce({ content: [], page: 2, size: 20, totalElements: 21, totalPages: 2 })
            .mockResolvedValueOnce({ ...page(), page: 1, totalElements: 21, totalPages: 2 });
        await act(async () => output.setPage(2)); expect(output.page).toBe(1);
        vi.mocked(listMyNotifications).mockResolvedValueOnce({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 })
            .mockResolvedValueOnce({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
        await act(async () => output.markRead(id)); expect(output.page).toBe(0); expect(output.notifications).toEqual([]); expect(output.filter).toBe('unread');
    });
    it('shows safe mutation errors and reconciles an uncertain outcome without replay', async () => {
        await render(); vi.mocked(markNotificationRead).mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.'));
        await act(async () => output.markRead(id)); expect(output.feedback?.severity).toBe('error'); expect(markNotificationRead).toHaveBeenCalledTimes(1); expect(listMyNotifications).toHaveBeenCalledTimes(2); expect(output.pending).toBeNull();
    });
    it('removes all old-session data/feedback and ignores late results on same-account session replacement', async () => {
        await render(); await act(async () => output.markAllRead());
        const old = deferred<NotificationPage>(); vi.mocked(listMyNotifications).mockReturnValueOnce(old.promise); await act(async () => output.reload());
        generation = 2; vi.mocked(listMyNotifications).mockResolvedValueOnce(page('Replacement')); await render();
        expect(host.textContent).not.toContain('Marked'); await act(async () => old.resolve(page('Private A'))); expect(host.textContent).toContain('Replacement'); expect(host.textContent).not.toContain('Private A');
    });
    it('suppresses stale success on page close and cancels owned operations on unmount', async () => {
        await render(); const pending = deferred<{ markedCount: number }>(); vi.mocked(markAllNotificationsRead).mockReturnValueOnce(pending.promise);
        let write!: Promise<void>; await act(async () => { write = output.markAllRead(); }); const request = vi.mocked(markAllNotificationsRead).mock.calls[0][0]!;
        options = { ...options, open: false, routeKey: 'b' }; await render(); expect(request.signal?.aborted).toBe(true); expect(request.isCurrent?.()).toBe(false);
        await act(async () => { pending.resolve({ markedCount: 88 }); await write; }); expect(output.feedback).toBeNull();
    });
    it('settles under StrictMode with only one live read per resource', async () => {
        await render(true); expect(output.notifications).toHaveLength(1);
        expect(vi.mocked(listMyNotifications).mock.calls.filter(([, , , request]) => !request?.signal?.aborted)).toHaveLength(1);
        expect(vi.mocked(countUnreadNotifications).mock.calls.filter(([request]) => !request?.signal?.aborted)).toHaveLength(1);
    });
});

it('isolates a pending write, count and filter when the same UUID gets a replacement session', async () => {
    await render(); await act(async () => output.setFilter('unread'));
    const pending = deferred<{ markedCount: number }>(); vi.mocked(markAllNotificationsRead).mockReturnValueOnce(pending.promise);
    let write!: Promise<void>; await act(async () => { write = output.markAllRead(); });
    const request = vi.mocked(markAllNotificationsRead).mock.calls[0][0]!;
    generation = 2; vi.mocked(countUnreadNotifications).mockResolvedValueOnce({ unreadCount: 6 }); vi.mocked(listMyNotifications).mockResolvedValueOnce(page('Session B'));
    await render(); expect(request.signal?.aborted).toBe(true); expect(request.isCurrent?.()).toBe(false);
    await act(async () => { pending.resolve({ markedCount: 88 }); await write; });
    expect(output.unreadCount).toBe(6); expect(output.filter).toBe('all'); expect(output.notifications[0].message).toBe('Session B'); expect(output.feedback).toBeNull(); expect(output.pending).toBeNull();
});
it('aborts list/count/write callbacks on owner unmount and never starts a reconciliation afterward', async () => {
    const list = deferred<NotificationPage>(), count = deferred<{ unreadCount: number }>(), write = deferred<{ markedCount: number }>();
    vi.mocked(listMyNotifications).mockReturnValueOnce(list.promise); vi.mocked(countUnreadNotifications).mockReturnValueOnce(count.promise);
    await render(); const listOptions = vi.mocked(listMyNotifications).mock.calls[0][3]!, countOptions = vi.mocked(countUnreadNotifications).mock.calls[0][0]!;
    vi.mocked(markAllNotificationsRead).mockReturnValueOnce(write.promise); let operation!: Promise<void>; await act(async () => { operation = output.markAllRead(); });
    const writeOptions = vi.mocked(markAllNotificationsRead).mock.calls[0][0]!;
    await act(async () => root.render(null));
    for (const request of [listOptions, countOptions, writeOptions]) { expect(request.signal?.aborted).toBe(true); expect(request.isCurrent?.()).toBe(false); }
    await act(async () => { list.resolve(page('Late private data')); count.resolve({ unreadCount: 99 }); write.resolve({ markedCount: 99 }); await operation; });
    expect(host.textContent).toBe(''); expect(countUnreadNotifications).toHaveBeenCalledTimes(1); expect(listMyNotifications).toHaveBeenCalledTimes(1);
});

it('releases an aborted mutation lock when shell eligibility changes within one session generation', async () => {
    await render(); const write = deferred<{ markedCount: number }>(); vi.mocked(markAllNotificationsRead).mockReturnValueOnce(write.promise);
    let operation!: Promise<void>; await act(async () => { operation = output.markAllRead(); }); expect(output.pending).toBe('all');
    options = { enabled: false, open: false, routeKey: 'b' }; await render();
    options = { enabled: true, open: true, routeKey: 'c' }; await render(); expect(output.pending).toBeNull();
    await act(async () => { write.resolve({ markedCount: 42 }); await operation; }); expect(output.feedback).toBeNull();
    await act(async () => output.markRead(id)); expect(markNotificationRead).toHaveBeenCalledOnce();
});
