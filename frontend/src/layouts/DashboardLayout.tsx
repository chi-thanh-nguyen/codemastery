import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { AppHeader } from '../shared/components/AppHeader';
import { SidebarNav } from '../shared/components/SidebarNav';
import type { NavItem } from '../shared/components/SidebarNav';
import { presentationTokens } from '../app/theme/theme';

export interface DashboardLayoutProps {
    headerTitle?: string;
    userDisplayName?: string;
    navItems: NavItem[];
    selectedNavId?: string;
    onNavSelect: (id: string) => void;
    headerActions?: ReactNode;
    headerSearch?: ReactNode;
    children: ReactNode;
}

export function DashboardLayout({ headerTitle, userDisplayName, navItems, selectedNavId, onNavSelect, headerActions, headerSearch, children }: DashboardLayoutProps) {
    const [mobileOpen, setMobileOpen] = useState(false);
    const desktop = useMediaQuery(useTheme().breakpoints.up('md'));
    useEffect(() => {
        if (!desktop || !mobileOpen) return;
        setMobileOpen(false);
        // The mobile trigger becomes hidden at this breakpoint; restore focus to visible content.
        const target = document.querySelector<HTMLElement>('.MuiDrawer-docked [aria-current="true"]')
            ?? document.getElementById('main-content');
        target?.focus();
    }, [desktop, mobileOpen]);
    return (
        <Box sx={{ minHeight: '100dvh', bgcolor: 'background.paper' }}>
            <Box sx={{ display: 'flex', maxWidth: 1600, mx: 'auto', minHeight: '100dvh',
                bgcolor: 'background.paper', overflow: 'clip' }}>
                <SidebarNav items={navItems} selectedId={selectedNavId} onSelect={onNavSelect}
                    mobileOpen={mobileOpen && !desktop} onMobileClose={() => setMobileOpen(false)} width={presentationTokens.sidebarWidth} />
                <Box sx={{ minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column' }}>
                    <AppHeader title={headerTitle} userDisplayName={userDisplayName} onMenuToggle={() => setMobileOpen((open) => !open)}
                        menuOpen={mobileOpen && !desktop} actions={headerActions} search={headerSearch} />
                    <Box component="main" id="main-content" tabIndex={-1} sx={{ flex: 1, minWidth: 0, p: { xs: 2, md: 3 }, bgcolor: 'background.paper' }}>
                        {children}
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}
