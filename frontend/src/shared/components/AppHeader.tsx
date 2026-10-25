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
    search?: ReactNode;
}

export function AppHeader({ title = 'CodeMastery', onMenuToggle, userDisplayName, actions, menuOpen, search }: AppHeaderProps) {
    return (
        <AppBar position="sticky" color="inherit" elevation={0} sx={{ border: 0, borderBottom: 1, borderColor: 'divider', zIndex: 'appBar', bgcolor: 'background.paper' }}>
            <Toolbar sx={{ minHeight: `${presentationTokens.headerHeight}px !important`, px: { xs: 2, md: 3 }, py: 1.5,
                columnGap: { xs: 1, md: 3 }, rowGap: 1.5, flexWrap: 'wrap' }}>
                {onMenuToggle && <IconButton aria-label="Toggle navigation" aria-expanded={menuOpen} onClick={onMenuToggle}
                    sx={{ display: { md: 'none' }, flexShrink: 0 }}><PresentationIcon name="menu" /></IconButton>}
                <Box sx={{ flex: { xs: 1, lg: '0 1 auto' }, minWidth: 0 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', lg: 'block' } }}>Your workspace</Typography>
                    <Typography variant="h5" component="p" sx={{ typography: { xs: 'subtitle1', sm: 'h5' }, overflowWrap: 'anywhere' }}>{title}</Typography>
                </Box>
                {search && <Box sx={{ order: { xs: 3, lg: 0 }, flex: { xs: '1 0 100%', lg: '1 1 240px' }, minWidth: 0, maxWidth: { lg: 420 }, mx: { lg: 'auto' } }}>{search}</Box>}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexShrink: 0, ml: 'auto' }}>
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
