import React from 'react';
import { AppBar, Toolbar, Typography, IconButton, Box } from '@mui/material';

export interface AppHeaderProps {
    title?: string;
    onMenuToggle?: () => void;
    userDisplayName?: string;
    actions?: React.ReactNode;
}

export const AppHeader: React.FC<AppHeaderProps> = ({
    title = 'CodeMastery',
    onMenuToggle,
    userDisplayName,
    actions,
}) => {
    return (
        <AppBar position="sticky" color="inherit" sx={{ borderBottom: '1px solid #e2e8f0', zIndex: 1201 }}>
            <Toolbar>
                {onMenuToggle && (
                    <IconButton
                        edge="start"
                        color="inherit"
                        aria-label="toggle menu"
                        onClick={onMenuToggle}
                        sx={{ mr: 2, display: { md: 'none' } }}
                    >
                        {/* SVG Menu Icon without external icon dependency */}
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z" />
                        </svg>
                    </IconButton>
                )}

                <Typography variant="h6" component="div" sx={{ fontWeight: 'bold', color: 'primary.main', flexGrow: 1 }}>
                    {title}
                </Typography>

                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    {userDisplayName && (
                        <Typography variant="body2" color="text.secondary">
                            {userDisplayName}
                        </Typography>
                    )}
                    {actions}
                </Box>
            </Toolbar>
        </AppBar>
    );
};