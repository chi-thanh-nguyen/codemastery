import React from 'react';
import { Container, Box, Paper, Typography } from '@mui/material';

export interface AuthLayoutProps {
    title?: string;
    subtitle?: string;
    children: React.ReactNode;
}

export const AuthLayout: React.FC<AuthLayoutProps> = ({
    title = 'CodeMastery',
    subtitle = 'Mastery-Based Programming Learning Platform',
    children,
}) => {
    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                bgcolor: 'background.default',
                py: 4,
            }}
        >
            <Container maxWidth="xs">
                <Box sx={{ mb: 3, textAlign: 'center' }}>
                    <Typography variant="h4" color="primary.main" sx={{ fontWeight: 'bold' }}>
                        {title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                        {subtitle}
                    </Typography>
                </Box>

                <Paper sx={{ p: 4, borderRadius: 2, boxShadow: 1 }}>
                    {children}
                </Paper>

                <Box sx={{ mt: 4, textAlign: 'center' }}>
                    <Typography variant="caption" color="text.secondary">
                        © {new Date().getFullYear()} CodeMastery. All rights reserved.
                    </Typography>
                </Box>
            </Container>
        </Box>
    );
};
