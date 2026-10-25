import { useId } from 'react';
import type { ReactNode } from 'react';
import { Box, Typography, CircularProgress, Button, Paper } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { presentationTokens } from '../../app/theme/theme';

export interface LoadingViewProps { message?: string }
export function LoadingView({ message = 'Loading...' }: LoadingViewProps) {
    const messageId = useId();
    const label = message.trim() ? message : 'Loading...';
    return <Box sx={{ minHeight: 190, p: 2.5, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
        <CircularProgress size={30} thickness={3} aria-labelledby={messageId} />
        <Typography id={messageId} role="status" variant="body2" color="text.secondary" sx={{ mt: 2 }}>{label}</Typography>
    </Box>;
}

export interface EmptyViewProps { title?: string; message?: string; action?: ReactNode }
export function EmptyView({ title = 'No data available', message = 'There are no items to display at this moment.', action }: EmptyViewProps) {
    return <Paper sx={{ minHeight: 190, p: 2.5, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
        <Box sx={{ width: 36, height: 36, display: 'grid', placeItems: 'center', borderRadius: '10px', bgcolor: presentationTokens.softBlue, color: 'primary.dark', mb: 1.5 }}>
            <PresentationIcon name="layers" sx={{ fontSize: 20 }} />
        </Box>
        <Typography variant="subtitle2" component="h3">{title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: .5, mb: action ? 1.5 : 0 }}>{message}</Typography>
        {action}
    </Paper>;
}

export interface ErrorViewProps { title?: string; message?: string; onRetry?: () => void; retryLabel?: string }
export function ErrorView({ title = 'Something went wrong', message = 'An unexpected error occurred. Please try again.', onRetry, retryLabel = 'Retry' }: ErrorViewProps) {
    return <Paper sx={{ minHeight: 190, p: 2.5, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
        <Box sx={{ width: 36, height: 36, display: 'grid', placeItems: 'center', borderRadius: '10px', bgcolor: 'error.light', color: 'error.main', mb: 1.5 }}>
            <PresentationIcon name="alert" sx={{ fontSize: 20 }} />
        </Box>
        <Typography variant="subtitle2" component="h3">{title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: .5, mb: onRetry ? 1.5 : 0 }}>{message}</Typography>
        {onRetry && <Button variant="text" size="small" onClick={onRetry}>{retryLabel}</Button>}
    </Paper>;
}
