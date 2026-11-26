// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../shared/api/httpClient';
import { countUnreadNotifications, listMyNotifications, markAllNotificationsRead, markNotificationRead } from './notificationApi';

const id = '00000000-0000-3000-8000-000000000001';
const row = { id, message: 'New content in "Course".\n<script>plain text</script>', read: false, createdAt: '2026-11-26T08:01:02.123456Z' };
const page = (content: unknown[] = [row], extra = {}) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra });
const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });
let fetcher: ReturnType<typeof vi.fn>, unbind: () => void, generation: number, invalidate: ReturnType<typeof vi.fn<(generation: number) => void>>;
beforeEach(() => {
    generation = 1; invalidate = vi.fn(); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
    unbind = bindAuthSession({ capture: () => ({ generation, accessToken: 'synthetic', expiresAt: Date.now() + 60000 }), isCurrent: (value) => value === generation, invalidate: (value) => { invalidate(value); } });
});
afterEach(() => { unbind(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe('CT11 consumption', () => {
    it('uses exactly the four authenticated operations without bodies or mutation replay', async () => {
        fetcher.mockResolvedValueOnce(json(page())).mockResolvedValueOnce(json({ unreadCount: 3 }))
            .mockResolvedValueOnce(json({ ...row, read: true })).mockResolvedValueOnce(json({ markedCount: 2 }));
        expect((await listMyNotifications()).content).toEqual([row]);
        expect(await countUnreadNotifications()).toEqual({ unreadCount: 3 });
        expect((await markNotificationRead(id)).read).toBe(true);
        expect(await markAllNotificationsRead()).toEqual({ markedCount: 2 });
        expect(fetcher.mock.calls.map(([url, init]) => [new URL(url).pathname + new URL(url).search, init.method])).toEqual([
            ['/api/me/notifications?unreadOnly=false&page=0&size=20', 'GET'], ['/api/me/notifications/unread-count', 'GET'],
            [`/api/me/notifications/${id}/read`, 'PUT'], ['/api/me/notifications/read-all', 'PUT'],
        ]);
        for (const [, init] of fetcher.mock.calls) {
            expect(init.headers.get('Authorization')).toBe('Bearer synthetic'); expect(init.body).toBeUndefined();
            expect(init.cache).toBe('no-store'); expect(init.credentials).toBe('omit'); expect(init.redirect).toBe('error');
        }
    });
    it.each(['2026-02-29T00:00:00Z', '2026-04-31T00:00:00Z', '2026-11-26', 'yesterday', '2026-11-26T24:00:00Z', '2026-11-26T12:00:00+24:00', '2026-11-26T12:00:00'])('rejects invalid timestamp %s', async (createdAt) => {
        fetcher.mockResolvedValue(json(page([{ ...row, createdAt }]))); await expect(listMyNotifications()).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each(['2024-02-29t23:59:59.123456z', '2026-11-26T08:00:00+07:00', '2026-11-26T08:00:00Z'])('accepts an approved instant %s', async (createdAt) => {
        fetcher.mockResolvedValue(json(page([{ ...row, createdAt }]))); expect((await listMyNotifications()).content[0].createdAt).toBe(createdAt);
    });
    it.each([{ ...row, id: 'bad' }, { ...row, message: ' \n' }, { ...row, type: 'deadline' }, { ...row, read: 1 }, { ...row, createdAt: null }])('rejects malformed/extended rows', async (value) => {
        fetcher.mockResolvedValue(json(page([value]))); await expect(listMyNotifications()).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([page([row], { page: 1 }), page([row], { size: 10 }), page([row], { totalPages: 3 }), page([row], { totalElements: -1 }), page([row], { totalElements: 20 }), page([row, row]), { ...page(), sort: 'id' }])('rejects incoherent pages', async (value) => {
        fetcher.mockResolvedValue(json(value)); await expect(listMyNotifications()).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('accepts an empty out-of-range page and binds unread filtering to server data', async () => {
        fetcher.mockResolvedValueOnce(json(page([], { page: 4, totalElements: 1, totalPages: 1 })));
        expect((await listMyNotifications(false, 4)).content).toEqual([]);
        fetcher.mockResolvedValueOnce(json(page([{ ...row, read: true }])));
        await expect(listMyNotifications(true)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([-1, 1.5, Number.MAX_SAFE_INTEGER, NaN])('rejects invalid page %s before fetch', async (index) => {
        await expect(listMyNotifications(false, index)).rejects.toMatchObject({ kind: 'validation' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it.each([0, 101, 1.5])('rejects invalid size %s before fetch', async (size) => {
        await expect(listMyNotifications(false, 0, size)).rejects.toMatchObject({ kind: 'validation' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it.each([-1, 1.5, '2', Number.MAX_SAFE_INTEGER + 1])('rejects invalid count %s', async (value) => {
        fetcher.mockResolvedValueOnce(json({ unreadCount: value })).mockResolvedValueOnce(json({ markedCount: value }));
        await expect(countUnreadNotifications()).rejects.toMatchObject({ kind: 'protocol' }); await expect(markAllNotificationsRead()).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('rejects wrong-identity/read-state mutation responses and invalid input', async () => {
        fetcher.mockResolvedValueOnce(json(row)).mockResolvedValueOnce(json({ ...row, id: '00000000-0000-3000-8000-000000000002', read: true }));
        await expect(markNotificationRead(id)).rejects.toMatchObject({ kind: 'protocol' }); await expect(markNotificationRead(id)).rejects.toMatchObject({ kind: 'protocol' });
        await expect(markNotificationRead('../read-all')).rejects.toMatchObject({ kind: 'validation' }); expect(fetcher).toHaveBeenCalledTimes(2);
    });
    it('cancels an obsolete session response and does not replay a lost write', async () => {
        fetcher.mockImplementationOnce(async () => { generation++; return json(page()); });
        await expect(listMyNotifications()).rejects.toMatchObject({ kind: 'cancelled' });
        fetcher.mockRejectedValueOnce(new TypeError('offline')); await expect(markAllNotificationsRead()).rejects.toMatchObject({ kind: 'network' }); expect(fetcher).toHaveBeenCalledTimes(2);
    });
    it('honors explicit cancellation and current-session 401 invalidation', async () => {
        const controller = new AbortController(); controller.abort(); await expect(countUnreadNotifications({ signal: controller.signal })).rejects.toMatchObject({ kind: 'cancelled' });
        expect(fetcher).not.toHaveBeenCalled(); fetcher.mockResolvedValueOnce(new Response('', { status: 401 }));
        await expect(markAllNotificationsRead()).rejects.toMatchObject({ status: 401 }); expect(invalidate).toHaveBeenCalledWith(1); expect(fetcher).toHaveBeenCalledTimes(1);
    });
});
