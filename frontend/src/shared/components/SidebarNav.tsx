import type { ReactNode } from 'react';
import { Box, Drawer, List, ListItem, ListItemButton, Typography, useMediaQuery, useTheme } from '@mui/material';
import { color, presentationTokens, radius, focusRingOnChrome } from '../../app/theme/theme';
import { BrandWordmark } from './BrandLogo';

export interface NavItem { id: string; label: string; badge?: string | number; icon?: ReactNode; group?: string; href?: string }

/**
 * True for a plain primary click that the app should route itself. Modified clicks (Cmd/Ctrl/Shift/Alt),
 * other buttons and already-handled events keep the browser's native link behavior (new tab, new window).
 */
export function isPlainClick(event: { button: number; metaKey: boolean; ctrlKey: boolean; shiftKey: boolean; altKey: boolean; defaultPrevented: boolean }) {
    return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey && !event.defaultPrevented;
}
export interface SidebarNavProps {
    items: NavItem[];
    selectedId?: string;
    onSelect: (id: string) => void;
    mobileOpen: boolean;
    onMobileClose: () => void;
    width?: number;
    footer?: ReactNode;
}

/** Mobile navigation drawer (< 900 px). Desktop navigation lives in the top bar. */
export function SidebarNav({ items, selectedId, onSelect, mobileOpen, onMobileClose, width = presentationTokens.drawerWidth, footer }: SidebarNavProps) {
    const theme = useTheme();
    const desktop = useMediaQuery(theme.breakpoints.up('md'));
    const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
    const reducedMotion = theme.motion.reducedMotion === 'always'
        || (theme.motion.reducedMotion === 'system' && prefersReducedMotion);
    if (desktop) return null;
    let lastGroup: string | undefined;
    return <Drawer variant="temporary" open={mobileOpen} onClose={onMobileClose}
        // A fresh reduced-motion panel avoids focusing retained, hidden Slide content.
        ModalProps={{ keepMounted: !reducedMotion }}
        sx={{ '& .MuiDrawer-paper': { width: `min(${width}px, calc(100vw - 48px))`, boxSizing: 'border-box', borderRadius: '0 24px 24px 0' } }}>
        <Box component="nav" aria-label="Main navigation" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', px: 3, height: presentationTokens.topBarHeightCompact, flexShrink: 0 }}>
                <BrandWordmark size={presentationTokens.brandMark.xs} />
            </Box>
            <List sx={{ px: 1.5, py: 1 }}>
                {items.map((item) => {
                    const current = item.id === selectedId;
                    const heading = item.group && item.group !== lastGroup ? item.group : undefined;
                    lastGroup = item.group;
                    return <ListItem key={item.id} disablePadding sx={{ display: 'block', mb: .5 }}>
                        {heading && <Typography component="div" sx={{ px: 1.5, mt: 2, mb: 1, color: color.chromeMuted, fontSize: '0.8125rem', fontWeight: 600 }}>{heading}</Typography>}
                        <ListItemButton {...(item.href ? { component: 'a', href: item.href } : {})} selected={current} aria-current={current ? 'page' : undefined}
                            onClick={(event) => { if (item.href && !isPlainClick(event)) return; if (item.href) event.preventDefault(); onSelect(item.id); onMobileClose(); }}
                            sx={{ minHeight: 52, px: 1.5, gap: 1.5, borderRadius: `${radius.md}px`, color: current ? 'common.white' : color.chromeMuted,
                                '&:hover': { bgcolor: color.chromeRaised, color: 'common.white' },
                                '&.Mui-selected, &.Mui-selected:hover': { bgcolor: color.chromeRaised, color: 'common.white' },
                                '&.Mui-focusVisible': { bgcolor: color.chromeRaised, ...focusRingOnChrome, outlineOffset: -2 } }}>
                            <Box component="span" aria-hidden sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: current ? color.brand600 : 'transparent', flexShrink: 0 }} />
                            {item.icon && <Box component="span" sx={{ display: 'grid', '& svg': { fontSize: 20 } }}>{item.icon}</Box>}
                            <Box component="span" sx={{ flex: 1, fontSize: '1rem', fontWeight: current ? 600 : 500 }}>{item.label}</Box>
                            {item.badge !== undefined && item.badge !== '' && <Box component="span" sx={{ minWidth: 22, px: .75, py: .25, textAlign: 'center',
                                borderRadius: `${radius.pill}px`, bgcolor: color.brand600, color: 'common.white', fontSize: '0.8125rem', fontWeight: 600, lineHeight: 1.2 }}>
                                {item.badge}
                            </Box>}
                        </ListItemButton>
                    </ListItem>;
                })}
            </List>
            {footer && <Box sx={{ mt: 'auto', px: 2, pb: 3, pt: 2, borderTop: `1px solid ${color.chromeRaised}`, '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>{footer}</Box>}
        </Box>
    </Drawer>;
}
