import { Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { EmptyView } from '../../../shared/components/StatusViews';
import { NotificationItem } from '../components/NotificationItem';
import type { NotificationView } from '../types/interactionTypes';
import { color, radius } from '../../../app/theme/theme';

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
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {visible.filter((item) => item.dayGroup === group).map((item) => <Box component="li" key={item.id} sx={{ borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 } }}>
                        <NotificationItem notification={item} onOpen={onOpen} />
                    </Box>)}
                </Box>
            </Box>)}
    </Page>;
}
