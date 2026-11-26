import { Box, Button, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { roles } from '../../../app/theme/theme';
import type { NotificationItemModel } from '../api/notificationApi';

export interface NotificationRow extends Pick<NotificationItemModel, 'id' | 'message' | 'read'> {
    createdAtLabel: string;
    createdAt?: string;
    dayGroup: string;
}

/** Message-only S15 row. Preview and production share presentation, never destinations or kinds. */
export function NotificationItem({ notification, onMarkRead, pending = false }: {
    notification: NotificationRow; onMarkRead?: (id: string) => void; pending?: boolean;
}) {
    return <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '40px minmax(0, 1fr) 10px', sm: '40px minmax(0, 1fr) auto 10px' },
        alignItems: 'start', columnGap: 2, rowGap: 1, px: { xs: 2, sm: 3 }, py: 2 }}>
        <Box aria-hidden sx={{ width: 40, height: 40, borderRadius: '50%', bgcolor: roles.bg.fill, color: roles.text.secondary,
            display: 'grid', placeItems: 'center', '& svg': { fontSize: 20 } }}><PresentationIcon name="bell" /></Box>
        <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: notification.read ? 400 : 600, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{notification.message}</Typography>
            <Typography component="time" dateTime={notification.createdAt} variant="caption" sx={{ display: 'block', mt: .5, color: roles.text.secondary }}>{notification.createdAtLabel}</Typography>
        </Box>
        {!notification.read && <>
            {onMarkRead && <Button variant="text" size="small" disabled={pending} aria-busy={pending || undefined}
                sx={{ minHeight: 44, gridColumn: { xs: 2, sm: 3 }, gridRow: { xs: 2, sm: 1 }, justifySelf: 'start' }} onClick={() => onMarkRead(notification.id)}>Mark as read</Button>}
            <Box sx={{ gridColumn: { xs: 3, sm: 4 }, gridRow: 1, pt: 1 }}>
                <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: roles.accent.link }} />
                <span className="cm-visually-hidden">Unread</span>
            </Box>
        </>}
    </Box>;
}
