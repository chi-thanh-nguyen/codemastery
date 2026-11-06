import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import { Box, ButtonBase, Divider, IconButton, Menu, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { BrandWordmark } from './BrandLogo';
import type { NavItem } from './SidebarNav';
import { color, motion, presentationTokens, radius, focusRingOnChrome } from '../../app/theme/theme';

export interface AppHeaderProps {
    /** Retained for API compatibility; the wordmark names the product. */
    title?: string;
    onMenuToggle?: () => void;
    menuOpen?: boolean;
    navItems?: NavItem[];
    selectedNavId?: string;
    onNavSelect?: (id: string) => void;
    /** Utilities on the right (notifications, account menu, sign-in actions). */
    actions?: ReactNode;
    search?: ReactNode;
    brandHref?: string;
    onBrandSelect?: () => void;
}

const REACH = presentationTokens.reachWidth + 24;

/** Ink top bar with the "canvas reach" under the active destination (DESIGN.md › Top bar). */
export function AppHeader({ onMenuToggle, menuOpen, navItems = [], selectedNavId, onNavSelect, actions, search, brandHref, onBrandSelect }: AppHeaderProps) {
    const theme = useTheme();
    const wide = useMediaQuery(theme.breakpoints.up('lg'));
    const bar = useRef<HTMLDivElement>(null);
    const [reach, setReach] = useState<number | null>(null);
    const measure = useCallback(() => {
        const host = bar.current, active = host?.querySelector<HTMLElement>('[data-nav-item][aria-current="page"]');
        if (!host || !active || active.offsetParent === null) { setReach(null); return; }
        const a = active.getBoundingClientRect(), h = host.getBoundingClientRect();
        setReach(Math.round(a.left - h.left + a.width / 2 - REACH / 2));
    }, []);
    useLayoutEffect(() => {
        measure();
        const host = bar.current;
        if (!host || typeof ResizeObserver === 'undefined') { window.addEventListener('resize', measure); return () => window.removeEventListener('resize', measure); }
        const observer = new ResizeObserver(measure); observer.observe(host);
        return () => observer.disconnect();
    }, [measure, selectedNavId, navItems, wide]);
    const brand = <BrandWordmark size={32} hideTextBelow={360} />;
    return <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 'appBar', bgcolor: color.ink950, color: 'common.white' }}>
        <Box ref={bar} sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: { xs: 1, md: 2 },
            height: { xs: presentationTokens.topBarHeightCompact, sm: presentationTokens.topBarHeight }, px: { xs: 1.5, sm: 2.5, md: 3 },
            maxWidth: 1600, mx: 'auto', '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>
            {onMenuToggle && <IconButton aria-label="Toggle navigation" aria-expanded={!!menuOpen} onClick={onMenuToggle}
                sx={{ display: { md: 'none' }, color: 'common.white', '&:hover': { bgcolor: color.chromeRaised } }}><PresentationIcon name="menu" /></IconButton>}
            <Box sx={{ flexShrink: 0, minWidth: 0, display: 'flex' }}>
                {brandHref ? <ButtonBase component="a" href={brandHref} aria-label="CodeMastery home"
                    onClick={(event: MouseEvent) => { if (onBrandSelect) { event.preventDefault(); onBrandSelect(); } }}
                    sx={{ borderRadius: `${radius.sm}px`, p: .5 }}>{brand}</ButtonBase> : brand}
            </Box>
            {navItems.length > 0 && <Box component="nav" aria-label="Main navigation" sx={{ display: { xs: 'none', md: 'flex' }, flex: 1, justifyContent: 'center', minWidth: 0 }}>
                <Box component="ul" sx={{ display: 'flex', gap: .5, m: 0, p: 0, listStyle: 'none' }}>
                    {navItems.map((item) => <Box component="li" key={item.id}>
                        <TopNavItem item={item} current={item.id === selectedNavId} iconOnly={!wide && item.id !== selectedNavId && !!item.icon} onSelect={onNavSelect} />
                    </Box>)}
                </Box>
            </Box>}
            {navItems.length === 0 && <Box sx={{ flex: 1 }} />}
            {search && <Box sx={{ flex: '0 1 320px', minWidth: 0, display: { xs: 'none', md: 'block' } }}>{search}</Box>}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: .5, sm: 1 }, flexShrink: 0, ml: { xs: 'auto', md: 0 } }}>{actions}</Box>
            {reach !== null && <Box aria-hidden data-canvas-reach sx={{ display: { xs: 'none', md: 'block' }, position: 'absolute', bottom: -1, left: 0, width: REACH, height: presentationTokens.reachHeight + 1,
                transform: `translateX(${reach}px)`, transition: `transform ${motion.reach}ms ${motion.easeIn}`, pointerEvents: 'none', color: color.canvas100 }}>
                <svg width={REACH} height={presentationTokens.reachHeight + 1} viewBox="0 0 80 13" fill="currentColor" style={{ display: 'block' }}>
                    <path d="M0 13C8 13 12 11 14 7 17 2 20 1 26 1h28c6 0 9 1 12 6 2 4 6 6 14 6Z" />
                </svg>
            </Box>}
        </Box>
    </Box>;
}

function TopNavItem({ item, current, iconOnly, onSelect }: { item: NavItem; current: boolean; iconOnly: boolean; onSelect?: (id: string) => void }) {
    const content = <ButtonBase component={item.href ? 'a' : 'button'} href={item.href} data-nav-item aria-current={current ? 'page' : undefined}
        onClick={(event: MouseEvent) => { if (item.href) event.preventDefault(); onSelect?.(item.id); }}
        sx={{ position: 'relative', height: 44, px: iconOnly ? 1.25 : 2, gap: 1, borderRadius: `${radius.pill}px`, fontSize: '0.9375rem', fontWeight: current ? 600 : 500,
            color: current ? 'common.white' : color.chromeMuted, whiteSpace: 'nowrap', transition: `background-color ${motion.fast}ms, color ${motion.fast}ms`,
            '&:hover': { bgcolor: color.chromeRaised, color: 'common.white' }, '& svg': { fontSize: 20 } }}>
        {item.icon}
        <Box component="span" className={iconOnly ? 'cm-visually-hidden' : undefined}>{item.label}</Box>
        {item.badge !== undefined && item.badge !== '' && item.badge !== 0 && <Box component="span" sx={{ minWidth: 20, px: .625, borderRadius: `${radius.pill}px`,
            bgcolor: color.brand600, color: 'common.white', fontSize: '0.8125rem', fontWeight: 600, lineHeight: '20px', textAlign: 'center' }}>{item.badge}</Box>}
    </ButtonBase>;
    return iconOnly ? <Tooltip title={item.label}>{content}</Tooltip> : content;
}

export interface AccountMenuProps {
    displayName: string;
    role: string;
    /** Menu entries (rendered as MenuItem children). Receives a close callback. */
    renderItems: (close: () => void) => ReactNode;
}
/** Monogram account button with a menu. Shows only display name and role (CT16-PROFILE P2). */
export function AccountMenu({ displayName, role, renderItems }: AccountMenuProps) {
    const [anchor, setAnchor] = useState<HTMLElement | null>(null);
    const initial = Array.from(displayName.trim())[0]?.toUpperCase() ?? '?';
    return <>
        <ButtonBase aria-haspopup="menu" aria-expanded={!!anchor} aria-label={`Account menu, ${displayName}, ${role}`}
            onClick={(event) => setAnchor(event.currentTarget)}
            sx={{ display: 'flex', alignItems: 'center', gap: 1.25, height: 52, pl: .75, pr: { xs: .75, md: 1.5 }, borderRadius: `${radius.pill}px`, color: 'common.white',
                '&:hover': { bgcolor: color.chromeRaised } }}>
            <Monogram name={displayName} initial={initial} />
            <Box sx={{ display: { xs: 'none', md: 'block' }, textAlign: 'left', minWidth: 0, maxWidth: 180 }}>
                <Typography component="span" sx={{ display: 'block', fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayName}</Typography>
                <Typography component="span" sx={{ display: 'block', fontSize: '0.8125rem', fontWeight: 500, color: color.chromeMuted, lineHeight: 1.3 }}>{role}</Typography>
            </Box>
            <PresentationIcon name="chevron-down" sx={{ fontSize: 18, display: { xs: 'none', md: 'block' } }} />
        </ButtonBase>
        <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            slotProps={{ paper: { sx: { minWidth: 220 } } }}>
            {renderItems(() => setAnchor(null))}
        </Menu>
    </>;
}

export function Monogram({ name, initial, size = 40, tone = 'chrome' }: { name: string; initial?: string; size?: number; tone?: 'chrome' | 'ink' }) {
    const letter = initial ?? Array.from(name.trim())[0]?.toUpperCase() ?? '?';
    return <Box component="span" aria-hidden sx={{ width: size, height: size, borderRadius: '50%', display: 'grid', placeItems: 'center', flexShrink: 0,
        bgcolor: tone === 'chrome' ? color.chromeRaised : color.ink950, color: 'common.white', fontSize: size * 0.42, fontWeight: 600 }}>{letter}</Box>;
}

export function AccountMenuDivider() { return <Divider sx={{ my: .5 }} />; }

export function HeaderIconButton({ label, icon, badge, onClick, href }: { label: string; icon: PresentationIconName; badge?: number; onClick?: () => void; href?: string }) {
    const name = badge ? `${label}, ${badge} unread` : label;
    return <Tooltip title={label}>
        <IconButton aria-label={name} onClick={onClick} {...(href ? { component: 'a', href } : {})} sx={{ position: 'relative', color: 'common.white', '&:hover': { bgcolor: color.chromeRaised } }}>
            <PresentationIcon name={icon} />
            {!!badge && <Box component="span" aria-hidden sx={{ position: 'absolute', top: 0, right: -2, minWidth: 20, height: 20, px: .5, borderRadius: `${radius.pill}px`,
                bgcolor: color.brand600, color: 'common.white', fontSize: '0.8125rem', fontWeight: 600, lineHeight: '20px', textAlign: 'center', border: `2px solid ${color.ink950}`, boxSizing: 'content-box' }}>{badge}</Box>}
        </IconButton>
    </Tooltip>;
}
