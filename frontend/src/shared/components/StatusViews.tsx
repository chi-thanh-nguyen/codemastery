import React from 'react';
import { Box, Typography, CircularProgress, Button, Paper } from '@mui/material';

export interface LoadingViewProps {
    message?: string;
}

export const LoadingView: React.FC<LoadingViewProps> = ({
    message = 'Loading...',
}) => {
    const messageId = React.useId();
    const label = message.trim() ? message : 'Loading...';
    return (
    <Box
        sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 240,
            p: 3,
        }}
    >
        <CircularProgress size={40} aria-labelledby={messageId} />
        <Typography id={messageId} role="status" variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            {label}
        </Typography>
    </Box>
);
};

export interface EmptyViewProps {
    title?: string;
    message?: string;
    action?: React.ReactNode;
}

export const EmptyView: React.FC<EmptyViewProps> = ({
    title = 'No data available',
    message = 'There are no items to display at this moment.',
    action,
}) => (
    <Paper
        sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            p: 4,
            textAlign: 'center',
            minHeight: 200,
        }}
    >
        <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
            {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: action ? 2 : 0 }}>
            {message}
        </Typography>
        {action}
    </Paper>
);

export interface ErrorViewProps {
    title?: string;
    message?: string;
    onRetry?: () => void;
    retryLabel?: string;
}

export const ErrorView: React.FC<ErrorViewProps> = ({
    title = 'Something went wrong',
    message = 'An unexpected error occurred. Please try again.',
    onRetry,
    retryLabel = 'Retry',
}) => (
    <Paper
        sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            p: 4,
            textAlign: 'center',
            minHeight: 200,
            borderColor: 'error.light',
        }}
    >
        <Typography variant="subtitle1" color="error.main" sx={{ fontWeight: 'bold' }}>
            {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: onRetry ? 2 : 0 }}>
            {message}
        </Typography>
        {onRetry && (
            <Button variant="outlined" color="primary" onClick={onRetry}>
                {retryLabel}
            </Button>
        )}
    </Paper>
);