import type { ReactNode } from 'react';
import { Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { presentationTokens } from '../../app/theme/theme';
import { PresentationIcon } from './PresentationIcon';

export interface NavItem { id: string; label: string; badge?: string | number; icon?: ReactNode }
export interface SidebarNavProps {
    items: NavItem[];
    selectedId?: string;
    onSelect: (id: string) => void;
    mobileOpen: boolean;
    onMobileClose: () => void;
    width?: number;
}

export function SidebarNav({ items, selectedId, onSelect, mobileOpen, onMobileClose, width = presentationTokens.sidebarWidth }: SidebarNavProps) {
    const content = (
        <Box component="nav" aria-label="Main navigation" sx={{ width, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2.5, height: presentationTokens.headerHeight, flexShrink: 0 }}>
                <Box sx={{ width: 29, height: 29, display: 'grid', placeItems: 'center', bgcolor: 'primary.main', color: 'primary.contrastText', borderRadius: '8px' }}>
                    <PresentationIcon name="code" sx={{ fontSize: 19 }} />
                </Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 750, letterSpacing: '-.04em' }}>CodeMastery</Typography>
            </Box>
            <Typography variant="overline" color="text.secondary" sx={{ px: 2.5, mt: 3, mb: 1 }}>Workspace</Typography>
            <List sx={{ px: 1.5, py: 0 }}>
                {items.map((item) => (
                    <ListItem key={item.id} disablePadding sx={{ mb: .5 }}>
                        <ListItemButton selected={item.id === selectedId} aria-current={item.id === selectedId ? 'true' : undefined}
                            onClick={() => { onSelect(item.id); onMobileClose(); }} sx={{ px: 1.5, gap: 1.25,
                                '&.Mui-selected': { bgcolor: 'action.selected', color: 'text.primary' },
                                '&.Mui-selected:hover, &.Mui-selected.Mui-focusVisible': { bgcolor: 'action.focus' } }}>
                            {item.icon && <ListItemIcon sx={{ minWidth: 0, color: 'text.secondary', '& svg': { fontSize: 19 } }}>{item.icon}</ListItemIcon>}
                            <ListItemText primary={item.label} slotProps={{ primary: { variant: 'body2', sx: { fontWeight: item.id === selectedId ? 650 : 500 } } }} />
                            {item.badge !== undefined && item.badge !== '' && <Box component="span" sx={{ minWidth: 22, px: .75, py: .125,
                                textAlign: 'center', borderRadius: '5px', bgcolor: 'action.focus', color: 'text.primary', fontSize: '.6875rem', fontWeight: 600 }}>
                                {item.badge}
                            </Box>}
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
            <Box sx={{ mt: 'auto', mx: 2, my: 3, p: 1.5, borderRadius: '10px', bgcolor: presentationTokens.softBlue }}>
                <PresentationIcon name="spark" sx={{ color: 'primary.dark', fontSize: 20, mb: .75 }} />
                <Typography variant="subtitle2">One step at a time.</Typography>
                <Typography variant="caption" color="text.secondary">Small progress. Lasting knowledge.</Typography>
            </Box>
        </Box>
    );
    return <>
        <Drawer variant="temporary" open={mobileOpen} onClose={onMobileClose} ModalProps={{ keepMounted: true }}
            sx={{ display: { xs: 'block', md: 'none' }, '& .MuiDrawer-paper': { width, boxSizing: 'border-box' } }}>{content}</Drawer>
        <Drawer variant="permanent" open sx={{ display: { xs: 'none', md: 'block' }, flexShrink: 0, width,
            '& .MuiDrawer-paper': { position: 'relative', height: '100%', width, boxSizing: 'border-box', border: 0, borderRight: 1, borderColor: 'divider' } }}>{content}</Drawer>
    </>;
}
