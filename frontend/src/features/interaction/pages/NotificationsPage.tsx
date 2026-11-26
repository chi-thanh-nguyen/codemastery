import { useState } from 'react';
import { useOutletContext } from 'react-router';
import { Alert, Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { EmptyView, ErrorView, LoadingView } from '../../../shared/components/StatusViews';
import { PaginationBar } from '../../../shared/components/DataTable';
import { NotificationItem } from '../components/NotificationItem';
import type { NotificationRow } from '../components/NotificationItem';
import type { NotificationsState } from '../hooks/useNotifications';
import { radius, roles } from '../../../app/theme/theme';
import type { NotificationView } from '../types/interactionTypes';

function NotificationList({ notifications, onMarkRead, pending }: { notifications: NotificationRow[]; onMarkRead?: (id: string) => void; pending?: boolean }) {
    const groups = [...new Set(notifications.map((item) => item.dayGroup))];
    return <Box sx={{ bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
        {groups.map((group, index) => <Box component="section" key={group} aria-label={group}>
            <Typography component="h2" variant="h4" sx={{ px: { xs: 2, sm: 3 }, pt: index ? 3 : 2, pb: 1 }}>{group}</Typography>
            <Box component="ul" aria-label={`${group} notifications`} sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {notifications.filter((item) => item.dayGroup === group).map((item) => <Box component="li" key={item.id}
                    sx={{ borderBottom: `1px solid ${roles.line.hairline}`, '&:last-child': { borderBottom: 0 } }}>
                    <NotificationItem notification={item} onMarkRead={onMarkRead} pending={pending} />
                </Box>)}
            </Box>
        </Box>)}
    </Box>;
}
function Filter({ value, onChange }: { value: 'all' | 'unread'; onChange: (value: 'all' | 'unread') => void }) {
    return <Box sx={{ mb: 3 }} onKeyDown={(event) => {
        if (!(event.target instanceof HTMLButtonElement)) return;
        const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End'];
        if (!keys.includes(event.key)) return;
        event.preventDefault();
        const next = event.key === 'Home' ? 'all' : event.key === 'End' ? 'unread' : value === 'all' ? 'unread' : 'all';
        onChange(next);
        event.currentTarget.querySelector<HTMLButtonElement>(`button[value="${next}"]`)?.focus();
    }}><SegmentedControl label="Show notifications" value={value} onChange={onChange}
        controls="notification-results" options={[{ value: 'all', label: 'All' }, { value: 'unread', label: 'Unread' }]} /></Box>;
}
const empty = <EmptyView icon="bell" title="You're all caught up" message="New updates about your courses appear here." />;

/** Network-free preview adapter. Sample state remains owned by /preview. */
export function NotificationsPage({ notifications, filter, onFilterChange, onMarkRead, onMarkAllRead }: {
    notifications: NotificationView[]; filter: 'all' | 'unread'; onFilterChange: (filter: 'all' | 'unread') => void;
    onMarkRead?: (id: string) => void; onMarkAllRead?: () => void;
}) {
    const [message, setMessage] = useState('');
    const unread = notifications.filter((item) => !item.read).length;
    const visible = notifications.filter((item) => filter === 'all' || !item.read);
    return <Page width="reading">
        <PageHeader title="Notifications" actions={<Button variant="outlined" disabled={!onMarkAllRead || unread === 0}
            onClick={() => { onMarkAllRead?.(); setMessage(`Marked ${unread} notifications as read.`); }}>Mark all as read</Button>} />
        <Filter value={filter} onChange={onFilterChange} />
        {message && <Alert severity="success" role="status" sx={{ mb: 3 }}>{message}</Alert>}
        <Box id="notification-results">{visible.length ? <NotificationList notifications={visible} onMarkRead={onMarkRead} /> : empty}</Box>
        {visible.length > 0 && <Box sx={{ mt: 3 }}><PaginationBar page={0} totalPages={1} totalItems={visible.length} itemsOnPage={visible.length} pageSize={20} noun="notifications" nounOne="notification" onChange={() => undefined} /></Box>}
    </Page>;
}

/** Local calendar days, including DST boundaries; the API instant remains unchanged. */
export function notificationDay(createdAt: string, now = new Date()) {
    const date = new Date(createdAt.toUpperCase()), yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === now.toDateString()) return 'Today';
    if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return date.toLocaleDateString('en-US', { dateStyle: 'medium' });
}

/** Receives the single Workspace-owned FE28 state through the existing router outlet. */
export function LiveNotificationsPage() {
    const state = useOutletContext<NotificationsState>();
    const rows: NotificationRow[] = state.notifications.map((item) => ({ ...item, dayGroup: notificationDay(item.createdAt),
        createdAtLabel: new Date(item.createdAt.toUpperCase()).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) }));
    return <Page width="reading">
        <PageHeader title="Notifications" actions={<Button variant="outlined" disabled={state.unreadCount === null || state.unreadCount === 0 || !!state.pending}
            aria-busy={state.pending === 'all' || undefined} onClick={() => { void state.markAllRead(); }}>Mark all as read</Button>} />
        <Filter value={state.filter} onChange={state.setFilter} />
        {state.feedback && <Alert severity={state.feedback.severity} role={state.feedback.severity === 'error' ? 'alert' : 'status'} sx={{ mb: 3 }}>{state.feedback.message}</Alert>}
        {state.pending && <Typography role="status" sx={{ mb: 2 }}>Marking notifications as read…</Typography>}
        <Box id="notification-results" role={state.error ? 'alert' : undefined} aria-busy={state.loading || !!state.pending}>
            {state.loading ? <LoadingView message="Loading notifications…" /> : state.error
                ? <ErrorView title="Couldn't load notifications" message={state.error} onRetry={state.reload} />
                : rows.length ? <NotificationList notifications={rows} onMarkRead={(id) => { void state.markRead(id); }} pending={!!state.pending} /> : empty}
        </Box>
        {!state.loading && !state.error && <Box sx={{ mt: 3 }}><PaginationBar page={state.page} totalPages={state.totalPages} totalItems={state.totalElements}
            pageSize={20} itemsOnPage={rows.length} onChange={state.setPage} noun="notifications" nounOne="notification" /></Box>}
    </Page>;
}
