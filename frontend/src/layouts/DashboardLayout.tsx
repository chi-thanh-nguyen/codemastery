import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { AppHeader } from '../shared/components/AppHeader';
import { SidebarNav } from '../shared/components/SidebarNav';
import type { NavItem } from '../shared/components/SidebarNav';
import { color, presentationTokens, radius } from '../app/theme/theme';

export interface DashboardLayoutProps {
    /** Retained for API compatibility; the wordmark names the product. */
    headerTitle?: string;
    userDisplayName?: string;
    navItems: NavItem[];
    selectedNavId?: string;
    onNavSelect: (id: string) => void;
    headerActions?: ReactNode;
    headerSearch?: ReactNode;
    /** Account and utility content repeated at the bottom of the mobile drawer. */
    drawerFooter?: ReactNode;
    /** Optional strip rendered at the top of the canvas (for example the preview's sample-data notice). */
    banner?: ReactNode;
    brandHref?: string;
    onBrandSelect?: () => void;
    children: ReactNode;
}

const RISE = presentationTokens.reachHeight;

/**
 * Outline of the canvas, which starts `rise` px under the top bar: a band across the top (hidden by the bar
 * except where the bar is cut by the canvas reach), then the visible rounded rectangle whose top corners
 * start exactly at the bar's bottom edge, so they meet it tangentially with no notch.
 */
export function canvasOutlinePath(width: number, height: number, rise: number, top: number, bottom: number): string {
    const w = Math.max(width, top * 2), h = Math.max(height, rise + top + bottom);
    const arc = (r: number, x: number, y: number) => (r > 0 ? `A${r} ${r} 0 0 1 ${x} ${y}` : `L${x} ${y}`);
    return `M${top} 0 H${w - top} V${rise} ${arc(top, w, rise + top)} V${h - bottom} ${arc(bottom, w - bottom, h)} H${bottom} ${arc(bottom, 0, h - bottom)} V${rise + top} ${arc(top, top, rise)} Z`;
}

/** Ink frame + sticky top bar + rounded canvas (DESIGN.md › Layout › Shell). */
export function DashboardLayout({ navItems, selectedNavId, onNavSelect, headerActions, headerSearch, drawerFooter, banner, brandHref, onBrandSelect, children }: DashboardLayoutProps) {
    const [mobileOpen, setMobileOpen] = useState(false);
    const theme = useTheme();
    const desktop = useMediaQuery(theme.breakpoints.up('md')), inset = useMediaQuery(theme.breakpoints.up('sm'));
    const canvas = useRef<HTMLElement>(null);
    const [outline, setOutline] = useState<string | undefined>();
    // The canvas reaches up under the bar; its own outline (clip-path) shapes the visible corners.
    useLayoutEffect(() => {
        const element = canvas.current;
        if (!element) return;
        const top = inset ? radius.xl : radius.lg, bottom = inset ? radius.xl : 0;
        const update = () => setOutline(`path('${canvasOutlinePath(element.offsetWidth, element.offsetHeight, RISE, top, bottom)}')`);
        update();
        if (typeof ResizeObserver === 'undefined') return;
        const observer = new ResizeObserver(update); observer.observe(element);
        return () => observer.disconnect();
    }, [inset]);
    useEffect(() => {
        if (!desktop || !mobileOpen) return;
        setMobileOpen(false);
        // The mobile trigger becomes hidden at this breakpoint; restore focus to visible content.
        const target = document.querySelector<HTMLElement>('header nav [aria-current="page"]')
            ?? document.getElementById('main-content');
        target?.focus();
    }, [desktop, mobileOpen]);
    return <Box sx={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', bgcolor: color.ink950 }}>
        <Box component="a" href="#main-content" onClick={(event) => { event.preventDefault(); document.getElementById('main-content')?.focus(); }}
            sx={{ position: 'absolute', left: 16, top: -80, zIndex: 'tooltip', px: 2.5, py: 1.5, borderRadius: `${radius.pill}px`, bgcolor: 'common.white', color: color.ink950,
                fontWeight: 600, textDecoration: 'none', '&:focus': { top: 12 } }}>
            Skip to main content
        </Box>
        <AppHeader navItems={navItems} selectedNavId={selectedNavId} onNavSelect={onNavSelect} actions={headerActions} search={headerSearch}
            brandHref={brandHref} onBrandSelect={onBrandSelect} onMenuToggle={() => setMobileOpen((open) => !open)} menuOpen={mobileOpen && !desktop} />
        <SidebarNav items={navItems} selectedId={selectedNavId} onSelect={onNavSelect} footer={drawerFooter}
            mobileOpen={mobileOpen && !desktop} onMobileClose={() => setMobileOpen(false)} />
        <Box component="main" id="main-content" tabIndex={-1} ref={canvas} sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', bgcolor: color.canvas100,
            mx: { xs: 0, sm: `${presentationTokens.canvasInset}px` }, mb: { xs: 0, sm: `${presentationTokens.canvasInset}px` },
            // Starts RISE px under the sticky bar (padding keeps content where it was); the bar's reach opening shows it.
            mt: `-${RISE}px`, pt: `${RISE}px`, clipPath: outline,
            borderRadius: { xs: `${radius.lg}px ${radius.lg}px 0 0`, sm: `${radius.xl}px` }, overflow: 'clip', width: { xs: '100%', sm: `calc(100% - ${presentationTokens.canvasInset * 2}px)` },
            maxWidth: { xl: 1600 - presentationTokens.canvasInset * 2 }, alignSelf: 'center', boxSizing: 'border-box' }}>
            {banner}
            {children}
        </Box>
    </Box>;
}

export interface PageProps {
    children: ReactNode;
    /** Contextual aside: a fill-150 column at >= 1200 px; stacked after the content below that. */
    aside?: ReactNode;
    asideLabel?: string;
    /** Constrain the content column for reading or forms. */
    width?: 'wide' | 'reading' | 'form';
    /** Keep the aside content in view while a long main column scrolls (>= 1200 px). */
    stickyAside?: boolean;
}

/** Page body with the standard padding rhythm and an optional contextual aside. */
export function Page({ children, aside, asideLabel, width = 'wide', stickyAside = false }: PageProps) {
    const maxWidth = width === 'reading' ? 960 : width === 'form' ? 760 : presentationTokens.contentMax;
    const padding = { px: presentationTokens.pagePadding, pt: { xs: 3, md: 5 }, pb: { xs: 4, md: 6 } };
    return <Box sx={{ flex: 1, display: 'grid', alignItems: 'stretch',
        gridTemplateColumns: aside ? { xs: 'minmax(0, 1fr)', lg: `minmax(0, 1fr) ${presentationTokens.asideWidth}px`, xl: `minmax(0, 1fr) ${presentationTokens.asideWidthWide}px` } : 'minmax(0, 1fr)' }}>
        <Box sx={{ ...padding, minWidth: 0, width: '100%', maxWidth: aside ? undefined : maxWidth + 80, mx: aside ? 0 : 'auto', boxSizing: 'border-box',
            ...(aside && width !== 'wide' ? { '& > *': { maxWidth } } : {}) }}>{children}</Box>
        {aside && <Box component="aside" aria-label={asideLabel} sx={{ minWidth: 0, bgcolor: color.fill150, boxSizing: 'border-box',
            px: { xs: 2, sm: 3 }, py: { xs: 3, lg: 5 }, mx: { xs: 2, sm: 3, md: 4, lg: 0 }, mb: { xs: 4, lg: 0 },
            borderRadius: { xs: `${radius.lg}px`, lg: 0 } }}>
            {stickyAside ? <Box sx={{ position: { lg: 'sticky' }, top: { lg: presentationTokens.topBarHeight + 24 } }}>{aside}</Box> : aside}
        </Box>}
    </Box>;
}
