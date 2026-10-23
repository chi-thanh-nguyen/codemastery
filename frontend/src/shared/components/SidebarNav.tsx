import { Typography } from '@mui/material';
import React from 'react';
import {
    Drawer,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    Box,
    Divider,
} from '@mui/material';

export interface NavItem {
    id: string;
    label: string;
    badge?: string | number;
}

export interface SidebarNavProps {
    items: NavItem[];
    selectedId?: string;
    onSelect: (id: string) => void;
    mobileOpen: boolean;
    onMobileClose: () => void;
    width?: number;
}

export const SidebarNav: React.FC<SidebarNavProps> = ({
    items,
    selectedId,
    onSelect,
    mobileOpen,
    onMobileClose,
    width = 240,
}) => {
    const navContent = (
        <Box component="nav" aria-label="Main navigation" sx={{ width, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ p: 2 }}>
                <Typography variant="subtitle2" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 1 }}>
                    Navigation
                </Typography>
            </Box>
            <Divider />
            <List sx={{ px: 1, py: 2 }}>
                {items.map((item) => {
                    const isSelected = item.id === selectedId;
                    return (
                        <ListItem key={item.id} disablePadding sx={{ mb: 0.5 }}>
                            <ListItemButton
                                selected={isSelected}
                                aria-current={isSelected ? 'true' : undefined}
                                onClick={() => {
                                    onSelect(item.id);
                                    onMobileClose();
                                }}
                                sx={{
                                    borderRadius: 1,
                                    '&.Mui-selected': {
                                        bgcolor: 'grey.100',
                                        color: 'text.primary',
                                        '&.Mui-focusVisible': { bgcolor: 'grey.200', outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 },
                                        '&:hover': {
                                            bgcolor: 'grey.200',
                                        },
                                    },
                                }}
                            >
                                <ListItemText primary={item.label} />
                                {item.badge !== undefined && item.badge !== '' && (
                                    <Box
                                        sx={{
                                            ml: 1,
                                            px: 1,
                                            py: 0.25,
                                            borderRadius: 1,
                                            fontSize: '0.75rem',
                                            bgcolor: 'grey.200',
                                            color: 'text.primary',
                                        }}
                                    >
                                        {item.badge}
                                    </Box>
                                )}
                            </ListItemButton>
                        </ListItem>
                    );
                })}
            </List>
        </Box>
    );

    return (
        <>
            {/* Mobile Drawer */}
            <Drawer
                variant="temporary"
                open={mobileOpen}
                onClose={onMobileClose}
                ModalProps={{ keepMounted: true }}
                sx={{
                    display: { xs: 'block', md: 'none' },
                    '& .MuiDrawer-paper': { boxSizing: 'border-box', width },
                }}
            >
                {navContent}
            </Drawer>

            {/* Desktop Persistent Drawer */}
            <Drawer
                variant="permanent"
                sx={{
                    display: { xs: 'none', md: 'block' },
                    '& .MuiDrawer-paper': { boxSizing: 'border-box', width, position: 'relative', height: '100%' },
                }}
                open
            >
                {navContent}
            </Drawer>
        </>
    );
};