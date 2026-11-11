import { Box, ButtonBase, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import type { PresentationIconName } from '../../../shared/components/PresentationIcon';
import type { NotificationKind, NotificationView } from '../types/interactionTypes';
import { color, tones } from '../../../app/theme/theme';
import type { Tone } from '../../../app/theme/theme';

export const notificationMeta: Record<NotificationKind, { label: string; icon: PresentationIconName; tone: Tone }> = {
    content: { label: 'New content', icon: 'book', tone: 'sky' },
    deadline: { label: 'Deadline', icon: 'calendar', tone: 'butter' },
    result: { label: 'Result available', icon: 'check-circle', tone: 'mint' },
    reply: { label: 'Q&A reply', icon: 'message', tone: 'sky' },
    recommendation: { label: 'Recommendation', icon: 'route', tone: 'lilac' },
};

export function NotificationItem({ notification, onOpen }: { notification: NotificationView; onOpen: (id: string) => void }) {
    const meta = notificationMeta[notification.kind];
    return <ButtonBase component="a" href="#" onClick={(event) => { event.preventDefault(); onOpen(notification.id); }}
        sx={{ width: '100%', display: 'flex', alignItems: 'flex-start', justifyContent: 'flex-start', textAlign: 'left', gap: 2, px: { xs: 2, sm: 3 }, py: 2, '&:hover': { bgcolor: color.canvas100 } }}>
        <Box aria-hidden sx={{ width: 40, height: 40, borderRadius: '50%', bgcolor: tones[meta.tone].tint, color: tones[meta.tone].strong, display: 'grid', placeItems: 'center', flexShrink: 0, '& svg': { fontSize: 20 } }}>
            <PresentationIcon name={meta.icon} />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography component="span" variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{meta.label} · {notification.targetLabel}</Typography>
            <Typography component="span" sx={{ display: 'block', mt: .25, fontWeight: notification.read ? 400 : 600, overflowWrap: 'anywhere' }}>{notification.message}</Typography>
            <Typography component="span" variant="caption" sx={{ display: 'block', mt: .5, color: 'text.secondary' }}>{notification.createdAtLabel}</Typography>
        </Box>
        {!notification.read && <Box sx={{ display: 'flex', alignItems: 'center', gap: .75, mt: .5 }}>
            <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: color.brand600 }} /><span className="cm-visually-hidden">Unread</span>
        </Box>}
    </ButtonBase>;
}
