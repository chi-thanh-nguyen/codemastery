import React, { useState } from 'react';
import { Box } from '@mui/material';
import { AppHeader } from '../shared/components/AppHeader';
import { SidebarNav, type NavItem } from '../shared/components/SidebarNav';
export interface DashboardLayoutProps {
    headerTitle?: string;
    userDisplayName?: string;
    navItems: NavItem[];
    selectedNavId?: string;
    onNavSelect: (id: string) => void;
    headerActions?: React.ReactNode;
    children: React.ReactNode;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
    headerTitle,
    userDisplayName,
    navItems,
    selectedNavId,
    onNavSelect,
    headerActions,
    children,
}) => {
    const [mobileOpen, setMobileOpen] = useState(false);

    const handleDrawerToggle = () => {
        setMobileOpen((prev) => !prev);
    };

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: 'background.default' }}>
            <AppHeader
                title={headerTitle}
                userDisplayName={userDisplayName}
                onMenuToggle={handleDrawerToggle}
                actions={headerActions}
            />
            <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
                <SidebarNav
                    items={navItems}
                    selectedId={selectedNavId}
                    onSelect={onNavSelect}
                    mobileOpen={mobileOpen}
                    onMobileClose={() => setMobileOpen(false)}
                />
                <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, sm: 3 }, overflowY: 'auto' }}>
                    {children}
                </Box>
            </Box>
        </Box>
    );
};