// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { appTheme } from '../../../app/theme/theme';
import { NotificationBell } from '../components/NotificationBell';
import { LiveNotificationsPage, NotificationsPage, notificationDay } from './NotificationsPage';
import type { NotificationsState } from '../hooks/useNotifications';
const id = '00000000-0000-3000-8000-000000000001';
const row = { id, message: '<img src=x>\nA long notification', read: false, createdAt: new Date().toISOString() };
let root: Root, host: HTMLDivElement, state: NotificationsState;
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    state = { notifications: [row], unreadCount: 1, filter: 'all', page: 0, totalPages: 2, totalElements: 21, loading: false, error: null, pending: null, feedback: null,
        setFilter: vi.fn(), setPage: vi.fn(), reload: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() };
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); });
async function render() {
    await act(async () => root.render(<ThemeProvider theme={appTheme}><MemoryRouter><Routes><Route element={<Outlet context={state} />}>
        <Route path="/" element={<><NotificationBell unread={state.unreadCount ?? 0} current href="/notifications" /><LiveNotificationsPage /></>} />
    </Route></Routes></MemoryRouter></ThemeProvider>));
}
async function click(text: string) { const button = [...host.querySelectorAll('button')].find((node) => node.textContent === text)!; expect(button).toBeTruthy(); await act(async () => button.click()); }

describe('S15 and F10 production presentation', () => {
    it('renders plain text, calendar groups, explicit actions and one list surface with no inferred destinations', async () => {
        await render(); expect(host.querySelector('h1')?.textContent).toBe('Notifications'); expect(host.querySelector('h2')?.textContent).toBe('Today');
        expect(host.querySelector('li')?.textContent).toContain(row.message); expect(host.querySelector('li a, li img')).toBeNull(); expect(host.querySelector('li time')?.getAttribute('datetime')).toBe(row.createdAt);
        expect(host.querySelector('li')?.textContent).toContain('Unread'); expect(state.markRead).not.toHaveBeenCalled(); expect(state.markAllRead).not.toHaveBeenCalled();
        await click('Mark as read'); expect(state.markRead).toHaveBeenCalledWith(id);
    });
    it('presents the accessible bell count and current state, and silently hides unknown count', async () => {
        await render(); expect(host.querySelector('[aria-label="Notifications, 1 unread"]')?.getAttribute('aria-current')).toBe('page');
        state = { ...state, unreadCount: null }; await render(); expect(host.querySelector('[aria-label="Notifications"]')).not.toBeNull(); expect(host.querySelector('[aria-label*="unread"]')).toBeNull();
    });
    it('uses server pagination and segmented selection callbacks', async () => {
        await render(); await click('Unread'); expect(state.setFilter).toHaveBeenCalledWith('unread');
        const second = host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!; await act(async () => second.click()); expect(state.setPage).toHaveBeenCalledWith(1);
        expect(host.textContent).toContain('of 21 notifications'); expect(host.querySelector('[aria-controls="notification-results"]')).not.toBeNull();
    });
    it.each([0, null])('keeps Mark all visible and disabled for count %s', async (unreadCount) => {
        state = { ...state, unreadCount }; await render(); const button = [...host.querySelectorAll('button')].find((node) => node.textContent === 'Mark all as read')!;
        expect(button.disabled).toBe(true); await act(async () => button.click()); expect(state.markAllRead).not.toHaveBeenCalled();
    });
    it('disables duplicate mutation actions and announces pending work', async () => {
        state = { ...state, pending: 'all' }; await render(); expect(host.querySelector('[aria-busy="true"]')).not.toBeNull();
        expect([...host.querySelectorAll('button')].filter((button) => button.textContent?.startsWith('Mark')).every((button) => button.disabled)).toBe(true);
        expect(host.textContent).toContain('Marking notifications as read…');
    });
    it('renders success and error feedback with their appropriate semantics', async () => {
        state = { ...state, feedback: { severity: 'success', message: 'Marked 7 notifications as read.', generation: 1, routeKey: 'a' } }; await render();
        expect(host.querySelector('[role="status"]')?.textContent).toContain('Marked 7');
        state = { ...state, feedback: { severity: 'error', message: 'Unable to connect. Please try again.', generation: 1, routeKey: 'a' } }; await render();
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Unable to connect'); expect(host.textContent).not.toContain('Marked 7');
    });
    it('renders loading, empty and list failure with an explicit retry', async () => {
        state = { ...state, loading: true, notifications: [] }; await render(); expect(host.textContent).toContain('Loading notifications');
        state = { ...state, loading: false }; await render(); expect(host.textContent).toContain("You're all caught up");
        state = { ...state, error: 'Unable to connect.' }; await render(); expect(host.querySelector('[role="alert"]')).not.toBeNull(); await click('Try again'); expect(state.reload).toHaveBeenCalledOnce();
    });
    it('hides Mark as read for read rows', async () => { state.notifications = [{ ...row, read: true }]; await render(); expect(host.querySelector('ul[aria-label$="notifications"] li button')).toBeNull(); expect(host.querySelector('ul[aria-label$="notifications"] li')?.textContent).not.toContain('Unread'); });
    it('uses Today, Yesterday and absolute date by local calendar rather than elapsed hours', () => {
        const now = new Date(2026, 10, 26, 0, 5); expect(notificationDay(new Date(2026, 10, 26, 0, 1).toISOString(), now)).toBe('Today');
        expect(notificationDay(new Date(2026, 10, 25, 23, 59).toISOString(), now)).toBe('Yesterday'); expect(notificationDay(new Date(2026, 10, 20).toISOString(), now)).toBe('Nov 20, 2026');
    });
    it('keeps the preview message-only and uses explicit read actions without navigation', async () => {
        const mark = vi.fn(); await act(async () => root.render(<ThemeProvider theme={appTheme}><NotificationsPage
            notifications={[{ ...row, kind: 'deadline', createdAtLabel: '9:00 AM', dayGroup: 'Today', targetLabel: 'Must not render' }]}
            filter="all" onFilterChange={vi.fn()} onMarkRead={mark} onMarkAllRead={vi.fn()} /></ThemeProvider>));
        expect(host.querySelector('a')).toBeNull(); expect(host.textContent).not.toContain('Must not render'); expect(host.textContent).not.toContain('Deadline'); await click('Mark as read'); expect(mark).toHaveBeenCalledWith(id);
    });
});

it('supports arrow keys and Home/End within the notification segmented filter', async () => {
    await render(); const all = [...host.querySelectorAll<HTMLButtonElement>('button')].find((button) => button.textContent === 'All')!;
    all.focus(); await act(async () => all.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true })));
    expect(state.setFilter).toHaveBeenCalledWith('unread'); expect(document.activeElement?.textContent).toBe('Unread');
    const unread = document.activeElement!;
    await act(async () => unread.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true, cancelable: true })));
    expect(state.setFilter).toHaveBeenCalledWith('all'); expect(document.activeElement).toBe(all);
});

// FE28-R01: focus belongs to the stable results owner, never an async callback.
it.each(['Mark as read', 'Mark all as read', 'Go to page 2'])('keeps meaningful focus through keyboard %s and loading', async (action) => {
    await render();
    const button = [...host.querySelectorAll<HTMLButtonElement>('button')].find((node) => node.textContent === action || node.getAttribute('aria-label') === action)!;
    button.focus(); await act(async () => button.click());
    const results = host.querySelector<HTMLElement>('#notification-updates')!;
    state = { ...state, loading: true, notifications: [], totalPages: 0 }; await render();
    expect(document.activeElement).toBe(results); expect(results.getAttribute('aria-busy')).toBe('true');
    expect(results.getAttribute('role')).toBe('region'); expect(results.getAttribute('aria-label')).toBe('Notifications');
    state = { ...state, loading: false, notifications: [], unreadCount: 0 }; await render();
    expect(document.activeElement).toBe(results); expect(host.textContent).toContain("You're all caught up");
    expect(results.tabIndex).toBe(-1);
});

it('does not reclaim focus after a user chooses another control during reconciliation', async () => {
    await render(); const action = [...host.querySelectorAll<HTMLButtonElement>('button')].find((node) => node.textContent === 'Mark as read')!;
    action.focus(); await act(async () => action.click());
    state = { ...state, loading: true, notifications: [] }; await render();
    const filter = host.querySelector<HTMLButtonElement>('button[value="all"]')!; filter.focus();
    state = { ...state, loading: false, notifications: [{ ...row, read: true }] }; await render();
    expect(document.activeElement).toBe(filter);
});

it('keeps pointer activation native instead of moving focus to the results region', async () => {
    await render(); const action = [...host.querySelectorAll<HTMLButtonElement>('button')].find((node) => node.textContent === 'Mark as read')!;
    action.focus(); await act(async () => action.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 })));
    expect(document.activeElement).toBe(action); expect(state.markRead).toHaveBeenCalledWith(id);
});

it('gives keyboard retry the same stable owner and preserves the error announcement', async () => {
    state = { ...state, error: 'Unable to connect.' }; await render();
    const retry = [...host.querySelectorAll<HTMLButtonElement>('button')].find((node) => node.textContent === 'Try again')!;
    retry.focus(); await act(async () => retry.click()); expect(document.activeElement?.id).toBe('notification-updates');
    expect(state.reload).toHaveBeenCalledOnce(); expect(host.querySelector('[role="alert"]')?.textContent).toContain('Unable to connect.');
});
