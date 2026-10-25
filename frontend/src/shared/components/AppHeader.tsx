import type { ReactNode } from 'react';
import { AppBar, Toolbar, Typography, IconButton, Box, Avatar } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { presentationTokens } from '../../app/theme/theme';

export interface AppHeaderProps {
    title?: string;
    onMenuToggle?: () => void;
    userDisplayName?: string;
    actions?: ReactNode;
    menuOpen?: boolean;
}

export function AppHeader({ title = 'CodeMastery', onMenuToggle, userDisplayName, actions, menuOpen }: AppHeaderProps) {
    return (
        <AppBar position="sticky" color="inherit" elevation={0} sx={{ border: 0, borderBottom: 1, borderColor: 'divider', zIndex: 'appBar', bgcolor: 'background.paper' }}>
            <Toolbar sx={{ minHeight: `${presentationTokens.headerHeight}px !important`, px: { xs: 1.5, sm: 2.5 }, gap: 1 }}>
                {onMenuToggle && <IconButton aria-label="Toggle navigation" aria-expanded={menuOpen} onClick={onMenuToggle}
                    sx={{ display: { md: 'none' }, flexShrink: 0 }}><PresentationIcon name="menu" /></IconButton>}
                <Typography variant="subtitle2" sx={{ flex: 1, minWidth: 0 }}>{title}</Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexShrink: 0 }}>
                    {actions}
                    {userDisplayName && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar aria-hidden sx={{ width: 32, height: 32, bgcolor: presentationTokens.softBlue, color: 'primary.dark', fontSize: '.75rem', fontWeight: 700 }}>
                            {userDisplayName.charAt(0).toUpperCase()}
                        </Avatar>
                        <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'block' } }}>{userDisplayName}</Typography>
                    </Box>}
                </Box>
            </Toolbar>
        </AppBar>
    );
}
