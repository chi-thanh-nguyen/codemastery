import { useId } from 'react';
import type { ReactNode } from 'react';
import { Box, Button, Skeleton, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { color, radius } from '../../app/theme/theme';

type Heading = 'h2' | 'h3' | 'h4';

export interface LoadingViewProps {
    message?: string;
    /** Skeleton shaped like the content that is loading. */
    variant?: 'tiles' | 'rows' | 'block' | 'none';
    count?: number;
}
/** Named loading status with a content-shaped skeleton (never a centred spinner alone). */
export function LoadingView({ message = 'Loading...', variant = 'block', count = 3 }: LoadingViewProps) {
    const messageId = useId();
    const label = message.trim() ? message : 'Loading...';
    return <Box sx={{ mb: 3 }}>
        {/* The named status line plus a content-shaped skeleton is the whole loading state; no spinner (DESIGN.md › Feedback). */}
        <Typography id={messageId} role="status" variant="body2" sx={{ mb: variant === 'none' ? 0 : 2, color: 'text.secondary' }}>{label}</Typography>
        {variant === 'tiles' && <Box aria-hidden sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' } }}>
            {Array.from({ length: count }, (_, i) => <Box key={i} sx={{ bgcolor: 'background.paper', borderRadius: `${radius.lg}px`, p: 3 }}>
                <Skeleton variant="rounded" width={48} height={48} sx={{ mb: 2 }} /><Skeleton height={28} width="80%" /><Skeleton height={18} sx={{ mt: 1.5 }} /><Skeleton height={18} width="60%" sx={{ mt: 1 }} />
            </Box>)}
        </Box>}
        {variant === 'rows' && <Box aria-hidden sx={{ bgcolor: 'background.paper', borderRadius: `${radius.lg}px`, p: 2 }}>
            {Array.from({ length: count }, (_, i) => <Box key={i} sx={{ display: 'flex', gap: 2, alignItems: 'center', py: 1.75, borderBottom: i < count - 1 ? `1px solid ${color.line200}` : 0 }}>
                <Skeleton height={20} sx={{ flex: 2 }} /><Skeleton height={20} sx={{ flex: 1 }} /><Skeleton height={20} sx={{ flex: 1 }} />
            </Box>)}
        </Box>}
        {variant === 'block' && <Box aria-hidden sx={{ bgcolor: 'background.paper', borderRadius: `${radius.lg}px`, p: 3 }}>
            <Skeleton height={28} width="45%" /><Skeleton height={18} sx={{ mt: 2 }} /><Skeleton height={18} width="85%" sx={{ mt: 1 }} /><Skeleton height={18} width="70%" sx={{ mt: 1 }} />
        </Box>}
    </Box>;
}

export interface EmptyViewProps { title?: string; message?: string; action?: ReactNode; icon?: PresentationIconName; headingLevel?: Heading; compact?: boolean }
/** Dashed "not yet" slot that teaches the next step (DESIGN.md › Feedback › Empty state). */
export function EmptyView({ title = 'Nothing here yet', message = 'Items will appear here when they are available.', action, icon = 'layers', headingLevel = 'h2', compact = false }: EmptyViewProps) {
    return <Box sx={{ border: `1.5px dashed ${color.ink400}`, borderRadius: `${radius.lg}px`, p: compact ? 2.5 : { xs: 3, sm: 5 }, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', textAlign: 'center', minHeight: compact ? 0 : 200 }}>
        <PresentationIcon name={icon} sx={{ fontSize: 24, color: color.ink600, mb: 1.5 }} />
        <Typography component={headingLevel} variant="h4">{title}</Typography>
        <Typography sx={{ mt: .75, color: 'text.secondary', maxWidth: '48ch' }}>{message}</Typography>
        {action && <Box sx={{ mt: 2.5, display: 'flex', flexWrap: 'wrap', gap: 1, justifyContent: 'center' }}>{action}</Box>}
    </Box>;
}

export interface ErrorViewProps { title?: string; message?: string; onRetry?: () => void; retryLabel?: string; headingLevel?: Heading; action?: ReactNode }
/** Names the problem and the recovery; never shows server diagnostics. */
export function ErrorView({ title = 'Something went wrong', message = 'Something went wrong on our side. Try again in a moment.', onRetry, retryLabel = 'Try again', headingLevel = 'h2', action }: ErrorViewProps) {
    return <Box sx={{ bgcolor: color.rose100, borderRadius: `${radius.lg}px`, p: { xs: 3, sm: 4 }, display: 'flex', gap: 2, alignItems: 'flex-start', mb: 3 }}>
        <PresentationIcon name="alert-circle" sx={{ fontSize: 24, color: color.rose700, flexShrink: 0, mt: .25 }} />
        <Box sx={{ minWidth: 0 }}>
            <Typography component={headingLevel} variant="h4">{title}</Typography>
            <Typography sx={{ mt: .5, overflowWrap: 'anywhere' }}>{message}</Typography>
            {(onRetry || action) && <Box sx={{ mt: 2, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {onRetry && <Button variant="contained" size="small" onClick={onRetry} startIcon={<PresentationIcon name="refresh" />}>{retryLabel}</Button>}
                {action}
            </Box>}
        </Box>
    </Box>;
}

export interface SystemStateViewProps { title: string; message: string; icon: PresentationIconName; actions?: ReactNode }
/** Full-page system state inside the shell (not found, access denied, unavailable). The h1 receives route focus. */
export function SystemStateView({ title, message, icon, actions }: SystemStateViewProps) {
    return <Box sx={{ maxWidth: 640, mx: 'auto', py: { xs: 6, md: 10 }, px: 3, textAlign: 'center' }}>
        <Box aria-hidden sx={{ width: 64, height: 64, mx: 'auto', mb: 3, borderRadius: '50%', bgcolor: color.surface, display: 'grid', placeItems: 'center', color: color.ink700 }}>
            <PresentationIcon name={icon} sx={{ fontSize: 28 }} />
        </Box>
        <Typography variant="h1" sx={{ textWrap: 'balance' }}>{title}</Typography>
        <Typography sx={{ mt: 1.5, color: 'text.secondary' }}>{message}</Typography>
        {actions && <Box sx={{ mt: 4, display: 'flex', flexWrap: 'wrap', gap: 1.5, justifyContent: 'center' }}>{actions}</Box>}
    </Box>;
}
