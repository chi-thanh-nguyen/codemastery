import { useState } from 'react';
import { Alert, Box, Button, Typography, Snackbar } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { EmptyView, ErrorView, LoadingView } from '../../../shared/components/StatusViews';
import { LiveNotificationItem, NotificationItem } from '../components/NotificationItem';
import { useNotifications } from '../hooks/useNotifications';
import { radius, roles } from '../../../app/theme/theme';
import type { NotificationView } from '../types/interactionTypes';

export interface NotificationsPageProps {
    notifications: NotificationView[];
    filter: 'all' | 'unread';
    onFilterChange: (filter: 'all' | 'unread') => void;
    onOpen: (id: string) => void;
    /** Only when the owning contract provides a mark-all operation. */
    onMarkAllRead?: () => void;
}

/** S15 Notifications: in-app updates grouped by day (US-09). */
export function NotificationsPage({ notifications, filter, onFilterChange, onOpen, onMarkAllRead }: NotificationsPageProps) {
    const visible = notifications.filter((item) => filter === 'all' || !item.read);
    const groups = [...new Set(visible.map((item) => item.dayGroup))];
    const unread = notifications.filter((item) => !item.read).length;
    return <Page width="reading">
        <PageHeader title="Notifications" subtitle="New content, deadlines, results and replies in your courses."
            actions={onMarkAllRead && unread > 0 && <Button variant="outlined" onClick={onMarkAllRead}>Mark all as read</Button>} />
        <Box sx={{ mb: 3 }}><SegmentedControl label="Show" value={filter} onChange={onFilterChange} options={[{ value: 'all', label: 'All', count: notifications.length }, { value: 'unread', label: 'Unread', count: unread }]} /></Box>
        {visible.length === 0 ? <EmptyView icon="bell" title={filter === 'unread' ? "You're all caught up" : 'No notifications yet'}
            message={filter === 'unread' ? 'New updates will appear here.' : 'Updates about new content, deadlines, results and replies will appear here.'} />
            : groups.map((group) => <Box component="section" key={group} aria-labelledby={`group-${group}`} sx={{ mb: 4 }}>
                <Typography id={`group-${group}`} component="h2" variant="h4" sx={{ mb: 1.5 }}>{group}</Typography>
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {visible.filter((item) => item.dayGroup === group).map((item) => <Box component="li" key={item.id} sx={{ borderBottom: `1px solid ${roles.line.hairline}`, '&:last-child': { borderBottom: 0 } }}>
                        <NotificationItem notification={item} onOpen={onOpen} />
                    </Box>)}
                </Box>
            </Box>)}
    </Page>;
}

export function LiveNotificationsPage() {
    const [filter, setFilter] = useState<'all' | 'unread'>('all');
    const [markedMessage, setMarkedMessage] = useState<string | null>(null);

    const {
        notifications,
        unreadCount,
        loading,
        error,
        loadPage,
        refreshUnreadCount,
        markRead,
        markAllRead,
    } = useNotifications(20);

    const unreadOnly = filter === 'unread';
    const visible = notifications.filter((item) => !unreadOnly || !item.read);

    // Group by day label
    const grouped: Record<string, typeof visible> = {};
    for (const item of visible) {
        const day = new Date(item.createdAt).toLocaleDateString('en-US', {
            dateStyle: 'medium',
        });
        if (!grouped[day]) grouped[day] = [];
        grouped[day].push(item);
    }
    const groups = Object.keys(grouped);

    const handleMarkAll = async () => {
        try {
            const count = await markAllRead();
            setMarkedMessage(`Marked ${count} notifications as read.`);
        } catch {
            setMarkedMessage("Couldn't mark notifications as read.");
        }
    };

    return (
        <Page width="reading">
            <PageHeader
                title="Notifications"
                subtitle="New content, deadlines, results and replies in your courses."
                actions={
                    unreadCount > 0 && (
                        <Button variant="outlined" onClick={handleMarkAll}>
                            Mark all as read
                        </Button>
                    )
                }
            />

            <Box sx={{ mb: 3 }}>
                <SegmentedControl
                    label="Show"
                    value={filter}
                    onChange={(val) => {
                        setFilter(val as 'all' | 'unread');
                        loadPage(val === 'unread', 0);
                    }}
                    options={[
                        { value: 'all', label: 'All', count: notifications.length },
                        { value: 'unread', label: 'Unread', count: unreadCount },
                    ]}
                />
            </Box>

            {loading ? (
                <LoadingView message="Loading notifications…" />
            ) : error ? (
                <ErrorView title="Couldn't load notifications" message={error} onRetry={() => loadPage(unreadOnly, 0)} />
            ) : visible.length === 0 ? (
                <EmptyView
                    icon="bell"
                    title={unreadOnly ? "You're all caught up" : 'No notifications yet'}
                    message={
                        unreadOnly
                            ? 'New unread updates will appear here.'
                            : 'Updates about new content, deadlines, results and replies will appear here.'
                    }
                />
            ) : (
                groups.map((group) => (
                    <Box component="section" key={group} aria-labelledby={`group-${group}`} sx={{ mb: 4 }}>
                        <Typography id={`group-${group}`} component="h2" variant="h4" sx={{ mb: 1.5 }}>
                            {group}
                        </Typography>
                        <Box
                            component="ul"
                            aria-label="Notification list"
                            sx={{
                                listStyle: 'none',
                                m: 0,
                                p: 0,
                                bgcolor: roles.bg.surface,
                                borderRadius: `${radius.lg}px`,
                                overflow: 'hidden',
                            }}
                        >
                            {grouped[group].map((item) => (
                                <Box
                                    component="li"
                                    key={item.id}
                                    sx={{
                                        borderBottom: `1px solid ${roles.line.hairline}`,
                                        '&:last-child': { borderBottom: 0 },
                                    }}
                                >
                                    <LiveNotificationItem
                                        notification={item}
                                        onMarkRead={async (id) => {
                                            await markRead(id);
                                            await refreshUnreadCount();
                                        }}
                                    />
                                </Box>
                            ))}
                        </Box>
                    </Box>
                ))
            )}

            <Snackbar
                open={Boolean(markedMessage)}
                autoHideDuration={6000}
                onClose={() => setMarkedMessage(null)}
            >
                <Alert severity="success" role="status" onClose={() => setMarkedMessage(null)}>
                    {markedMessage}
                </Alert>
            </Snackbar>
        </Page>
    );
}
