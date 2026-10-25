import type { ReactNode } from 'react';
import { Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography, useMediaQuery, useTheme } from '@mui/material';
import { presentationTokens } from '../../app/theme/theme';
import { BrandLogo } from './BrandLogo';

export interface NavItem { id: string; label: string; badge?: string | number; icon?: ReactNode; group?: string }
export interface SidebarNavProps {
    items: NavItem[];
    selectedId?: string;
    onSelect: (id: string) => void;
    mobileOpen: boolean;
    onMobileClose: () => void;
    width?: number;
}

export function SidebarNav({ items, selectedId, onSelect, mobileOpen, onMobileClose, width = presentationTokens.sidebarWidth }: SidebarNavProps) {
    const theme = useTheme();
    const desktop = useMediaQuery(theme.breakpoints.up('md'));
    const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
    const reducedMotion = theme.motion.reducedMotion === 'always'
        || (theme.motion.reducedMotion === 'system' && prefersReducedMotion);
    const content = (
        <Box component="nav" aria-label="Main navigation" sx={{ width, height: '100%', display: 'flex', flexDirection: 'column', bgcolor: presentationTokens.sidebarBackground }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, px: 3, height: presentationTokens.headerHeight, flexShrink: 0 }}>
                <BrandLogo size={34} decorative />
                <Typography variant="h6" component="p" sx={{ fontWeight: 700 }}>CodeMastery</Typography>
            </Box>
            {!items.some((item) => item.group) && <Typography variant="overline" color="text.secondary" sx={{ px: 3, mt: 3, mb: 1 }}>Workspace</Typography>}
            <List sx={{ px: 2, py: 1 }}>
                {items.map((item) => (
                    <ListItem key={item.id} disablePadding sx={{ mb: .5, display: 'block' }}>
                        {item.group && <Typography component="div" variant="overline" color="text.secondary" sx={{ px: 1, mt: 2, mb: 1 }}>{item.group}</Typography>}
                        <ListItemButton selected={item.id === selectedId} aria-current={item.id === selectedId ? 'true' : undefined}
                            onClick={() => { onSelect(item.id); onMobileClose(); }} sx={{ px: 1.5, py: 1, gap: 1.5,
                                '&.Mui-selected': { bgcolor: 'action.selected', color: 'primary.dark' },
                                '&.Mui-selected:hover, &.Mui-selected.Mui-focusVisible': { bgcolor: 'action.focus' } }}>
                            {item.icon && <ListItemIcon sx={{ minWidth: 0, color: item.id === selectedId ? 'primary.dark' : 'text.secondary', '& svg': { fontSize: 20 } }}>{item.icon}</ListItemIcon>}
                            <ListItemText primary={item.label} slotProps={{ primary: { variant: 'subtitle1', sx: { fontWeight: item.id === selectedId ? 500 : 400 } } }} />
                            {item.badge !== undefined && item.badge !== '' && <Box component="span" sx={{ minWidth: 22, px: .75, py: .125,
                                textAlign: 'center', borderRadius: '5px', bgcolor: 'action.focus', color: 'text.primary', typography: 'caption', fontWeight: 500 }}>
                                {item.badge}
                            </Box>}
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
            <Box sx={{ mt: 'auto', mx: 2.5, mb: 3, pt: 4 }}>
                {items.some((item) => item.group) && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: .5, mb: 2 }}>Local demonstration views</Typography>}
                <Box sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}>
                <Typography variant="subtitle2">One step at a time.</Typography>
                <Typography variant="caption" color="text.secondary">Small progress. Lasting knowledge.</Typography>
                </Box>
            </Box>
        </Box>
    );
    return <>
        {/* A fresh reduced-motion panel avoids focusing retained, hidden Slide content. */}
        {!desktop && <Drawer variant="temporary" open={mobileOpen} onClose={onMobileClose} ModalProps={{ keepMounted: !reducedMotion }}
            sx={{ '& .MuiDrawer-paper': { width, boxSizing: 'border-box' } }}>{content}</Drawer>}
        <Drawer variant="permanent" open sx={{ display: { xs: 'none', md: 'block' }, flexShrink: 0, width,
            '& .MuiDrawer-paper': { position: 'relative', height: '100%', width, boxSizing: 'border-box', border: 0, borderRight: 1, borderColor: 'divider' } }}>{content}</Drawer>
    </>;
}
