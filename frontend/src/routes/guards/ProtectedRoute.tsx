import React from 'react';
import { Box, Button, Typography, Paper } from '@mui/material';
import { useAuth } from '../../features/auth/hooks/useAuth';
import { ROUTE_PATHS } from '../routePaths';

export interface ProtectedRouteProps {
    children: React.ReactNode;
    onNavigate?: (path: string) => void;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
    children,
    onNavigate,
}) => {
    const { isAuthenticated } = useAuth();

    if (!isAuthenticated) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    minHeight: '60vh',
                    p: 3,
                }}
            >
                <Paper
                    sx={{
                        p: 4,
                        maxWidth: 400,
                        textAlign: 'center',
                        borderRadius: 2,
                        border: '1px solid #e2e8f0',
                    }}
                >
                    <Typography variant="h6" sx={{ fontWeight: 'bold' }} gutterBottom>
                        Authentication Required
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                        You need to be logged in to view this protected page.
                    </Typography>
                    {onNavigate && (
                        <Button
                            variant="contained"
                            fullWidth
                            onClick={() => onNavigate(ROUTE_PATHS.LOGIN)}
                        >
                            Go to Login
                        </Button>
                    )}
                </Paper>
            </Box>
        );
    }

    return <>{children}</>;
};