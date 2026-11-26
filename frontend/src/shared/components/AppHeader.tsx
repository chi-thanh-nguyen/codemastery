import { useCallback, useContext, useId, useLayoutEffect, useRef, useState } from 'react';
import type { KeyboardEvent, MouseEvent, ReactNode } from 'react';
import { Box, ButtonBase, ClickAwayListener, Divider, IconButton, Menu, MenuItem, MenuList, Paper, Popper, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { BrandWordmark } from './BrandLogo';
import { isPlainClick } from './SidebarNav';
import type { NavItem } from './SidebarNav';
import { focusRingOnChrome, motion, presentationTokens, radius, roles, shadow, ThemePreferenceContext, themeAnnouncement, themeButtonLabel, themeSystemCaption } from '../../app/theme/theme';
import type { ThemePreference } from '../../app/theme/theme';

export interface AppHeaderProps {
    /** Retained for API compatibility; the wordmark names the product. */
    title?: string;
    onMenuToggle?: () => void;
    menuOpen?: boolean;
    navItems?: NavItem[];
    selectedNavId?: string;
    onNavSelect?: (id: string) => void;
    /** Page utilities left of the Theme button (for example the notification bell). */
    utilities?: ReactNode;
    /** Session actions right of the Theme button: the account menu, or Sign in and Create account. */
    actions?: ReactNode;
    search?: ReactNode;
    brandHref?: string;
    onBrandSelect?: () => void;
}

const themeOptions: { value: ThemePreference; label: string; icon: PresentationIconName }[] = [
    { value: 'system', label: 'System', icon: 'device' },
    { value: 'light', label: 'Light', icon: 'sun' },
    { value: 'dark', label: 'Dark', icon: 'moon' },
];

/** Arrow keys (with wrap), Home and End over the three options; returns the target index or null. */
function optionIndexForKey(key: string, index: number) {
    const last = themeOptions.length - 1;
    if (key === 'ArrowDown' || key === 'ArrowRight') return index === last ? 0 : index + 1;
    if (key === 'ArrowUp' || key === 'ArrowLeft') return index === 0 ? last : index - 1;
    if (key === 'Home') return 0;
    if (key === 'End') return last;
    return null;
}

/** Whether focus on `element` is keyboard focus, by the browser's own `:focus-visible` heuristic. */
function isKeyboardFocus(element: Element) {
    try { return element.matches(':focus-visible'); } catch { return false; }
}

/**
 * The Theme control (DESIGN.md › Theme selector, D9-1/D10-1): one presentational control reused by
 * every shell. `button` is the 44 px on-frame button with its menu (top bar, landing header, auth
 * layout, Focus bar); `drawer` is the on-chrome segmented control of the mobile drawer footer. Both
 * read and change the one preference above the router, never the session, and each announces its
 * own change once through a polite status message that stays outside any modal's hidden siblings.
 */
export function ThemeControl({ variant = 'button' }: { variant?: 'button' | 'drawer' }) {
    const context = useContext(ThemePreferenceContext);
    const [open, setOpen] = useState(false);
    // The tooltip is controlled so that opening the menu also hides a tooltip that hover or focus had
    // already shown; it sits above the menu and would otherwise cover the menu's "Theme" caption.
    const [tooltip, setTooltip] = useState(false);
    // MUI calls onClose only while it considers the tooltip open, and it is held closed while the menu is open,
    // so its state is reset whenever the menu opens or closes (D13-5). After a close, two separate suppressions
    // apply, one per source (AUD-02/F-01): `staleHover` while the pointer still rests on the button, until it
    // genuinely enters again; `restoredFocus` for the focus that closing returns to the button, until the button
    // loses focus. Escape dismisses the tooltip until the same releases happen (`escaped`, N-01: hover when the pointer
    // genuinely enters again, focus when it leaves the button), so no pending timer brings it back. Refs, not state:
    // Tooltip calls onOpen from a timer started in an earlier render, so a stale closure must read the current values.
    const hovering = useRef(false), staleHover = useRef(false), restoredFocus = useRef(false), menuOpen = useRef(false), escaped = useRef(false);
    const [announcement, setAnnouncement] = useState('');
    const button = useRef<HTMLButtonElement>(null);
    const optionRefs = useRef<Array<HTMLElement | null>>([]);
    const menuId = useId();
    const checkedIndex = Math.max(0, themeOptions.findIndex((option) => option.value === context?.preference));
    if (!context) return null;
    const { preference, systemScheme, setPreference } = context;
    const selected = themeOptions[checkedIndex];
    const choose = (next: ThemePreference) => {
        if (next !== preference) setAnnouncement(themeAnnouncement(next, systemScheme));
        setPreference(next);
    };
    const status = <Box component="span" role="status" aria-live="polite" aria-atomic="true" className="cm-visually-hidden">{announcement}</Box>;
    const caption = themeSystemCaption(systemScheme);

    if (variant === 'drawer') {
        const onRadioKeyDown = (event: KeyboardEvent<HTMLElement>, index: number) => {
            const next = optionIndexForKey(event.key, index);
            if (next === null) return;
            event.preventDefault();
            optionRefs.current[next]?.focus();
            choose(themeOptions[next].value);
        };
        return <Box data-theme-control="drawer" sx={{ minWidth: 0, containerType: 'inline-size' }}>
            <Typography id={`${menuId}-label`} component="div" sx={{ mb: 1, color: roles.chrome.muted, fontSize: '0.8125rem', fontWeight: 600 }}>Theme</Typography>
            {/* At normal text size: a 44 px chrome-raised track, with three equal full-height hit areas. */}
            <Box role="radiogroup" aria-labelledby={`${menuId}-label`} sx={{ display: 'flex', height: 44, borderRadius: `${radius.pill}px`, bgcolor: roles.chrome.raised,
                // Keep the normal 44 px track. When enlarged text cannot fit three segments, retain the glyphs and
                // labels in full-width rows instead of shrinking or clipping them (G-02). rem follows the text size.
                '@container (max-width: 14rem)': { height: 'auto', flexDirection: 'column', borderRadius: `${radius.lg}px`,
                    '& > [role="radio"]': { flex: '0 0 auto', width: '100%', height: 'auto', minHeight: 44, py: .5, lineHeight: 1.2 } } }}>
                {themeOptions.map((option, index) => {
                    const active = index === checkedIndex;
                    return <ButtonBase key={option.value} ref={(element: HTMLButtonElement | null) => { optionRefs.current[index] = element; }}
                        role="radio" aria-checked={active} tabIndex={active ? 0 : -1} onClick={() => choose(option.value)}
                        onKeyDown={(event) => onRadioKeyDown(event, index)}
                        sx={{ position: 'relative', zIndex: 0, flex: '1 1 0', minWidth: 0, height: 44, gap: .5, borderRadius: `${radius.pill}px`,
                            color: active ? roles.control.onChrome.text : roles.chrome.muted, fontSize: '0.8125rem', fontWeight: active ? 600 : 500,
                            // The visible pill is 36 px tall, inset by the 4 px track padding.
                            '&::before': { content: '""', position: 'absolute', zIndex: -1, inset: 4, borderRadius: `${radius.pill}px`,
                                bgcolor: active ? roles.control.onChrome.background : 'transparent' },
                            '&:hover': active ? {} : { color: roles.text.onChrome, '&::before': { bgcolor: roles.chrome.hover } },
                            '&.Mui-focusVisible, &:focus-visible': { ...focusRingOnChrome, outlineOffset: -2 }, '& svg': { fontSize: 16, flexShrink: 0 } }}>
                        <PresentationIcon name={option.icon} />{option.label}
                    </ButtonBase>;
                })}
            </Box>
            {preference === 'system' && <Typography variant="caption" sx={{ display: 'block', mt: 1, color: roles.chrome.muted }}>{caption}</Typography>}
            {status}
        </Box>;
    }

    const openMenu = () => { menuOpen.current = true; setTooltip(false); setOpen(true); };
    const close = (returnFocus: boolean) => {
        menuOpen.current = false;
        setOpen(false);
        setTooltip(false);
        staleHover.current = hovering.current;
        restoredFocus.current = returnFocus;
        if (returnFocus) button.current?.focus();
    };
    // Shows the tooltip for a genuine hover (Tooltip's delayed onOpen) or keyboard focus, never over the open menu.
    const showTooltip = (event: { type: string }) => {
        if (menuOpen.current || escaped.current || (event.type === 'focus' ? restoredFocus.current : staleHover.current)) return;
        setTooltip(true);
    };
    // Keyboard focus is handled once, at once, by the button's own onFocus. Tooltip's focus listener also starts its
    // delayed open (400 ms, or 200 ms within its hysteresis) and Escape does not cancel that timer, so its focus-driven
    // onOpen is ignored here (N-01). Its focus and blur listeners stay attached: blur still closes the tooltip through
    // Tooltip's own close, which also restarts its hysteresis timer (`disableFocusListener` would remove both).
    // That ignored focus timer still runs inside MUI: when it fires, `handleOpen` sets MUI's module-level hysteresis flag
    // and cancels the reset timer that Escape's close had started, so the flag stays set while focus stays on the button
    // and the next pointer hover would open after `enterNextDelay` (200 ms by default) instead of 400 ms (N-04). The
    // Theme tooltip therefore takes the specified 400 ms for every pointer hover (DESIGN.md › Tooltip, D13-5).
    const onTooltipOpen = (event: { type: string }) => { if (event.type !== 'focus') showTooltip(event); };
    // Tooltip closes itself on Escape (a document keydown listener while it is open); that dismissal persists (N-01).
    const onTooltipClose = (event: { type: string; key?: string }) => {
        if (event.type === 'keydown' && event.key === 'Escape') escaped.current = true;
        setTooltip(false);
    };
    // MenuList owns arrow keys (with wrap), Home, End and type-ahead; Escape returns focus to the button.
    // Tab and Shift+Tab also close the menu: focus goes back to the button before the key's default
    // action runs, so the browser continues the tab sequence from the button. The menu is portalled to
    // the end of the document, and a Tab from inside it would otherwise leave the page or wrap to its start.
    const onMenuKeyDown = (event: KeyboardEvent<HTMLElement>) => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); }
        else if (event.key === 'Tab') close(true);
    };
    return <>
        {/* While the menu is open the tooltip exits without its fade, so it never overlaps the menu's caption. */}
        <Tooltip title="Theme" enterNextDelay={400} open={tooltip && !open} onOpen={onTooltipOpen} onClose={onTooltipClose}
            slotProps={{ transition: open ? { timeout: 0 } : {} }}>
            <IconButton ref={button} aria-label={themeButtonLabel(preference, systemScheme)} data-theme-control="button"
                aria-haspopup="menu" aria-expanded={open} aria-controls={open ? menuId : undefined} onClick={() => (open ? close(false) : openMenu())}
                // Keyboard focus shows the tooltip at once (DESIGN.md › Tooltip, F-03); only hover keeps the 400 ms delay.
                // Tooltip runs this handler before its own, which would open it only after that delay.
                onFocus={(event) => { if (isKeyboardFocus(event.currentTarget)) showTooltip(event); }}
                onMouseEnter={() => { hovering.current = true; staleHover.current = false; escaped.current = false; }}
                onMouseLeave={() => { hovering.current = false; staleHover.current = false; }}
                onBlur={() => { restoredFocus.current = false; escaped.current = false; }}
                sx={{ flexShrink: 0, width: 44, height: 44, bgcolor: 'transparent', color: roles.chrome.muted,
                    '&:hover': { bgcolor: roles.chrome.raised, color: roles.text.onChrome }, '&.Mui-focusVisible': focusRingOnChrome }}>
                <PresentationIcon name={selected.icon} sx={{ fontSize: 20 }} />
            </IconButton>
        </Tooltip>
        {status}
        {/* A non-modal popup: the rest of the page (and the Focus bar's attempt) stays exposed and unchanged. */}
        {/* role="presentation" replaces the Popper's default role="tooltip", which would wrap the menu. */}
        <Popper open={open} anchorEl={button.current} placement="bottom-start" role="presentation" sx={{ zIndex: 'modal' }}
            modifiers={[{ name: 'offset', options: { offset: [0, 8] } }, { name: 'preventOverflow', options: { padding: 8 } }]}>
            <ClickAwayListener onClickAway={(event) => { if (!button.current?.contains(event.target as Node)) close(false); }}>
                <Paper sx={{ width: 240, maxWidth: 'calc(100vw - 16px)', boxSizing: 'border-box', p: 1, bgcolor: roles.bg.raised, color: roles.text.primary,
                    borderRadius: `${radius.lg}px`, boxShadow: `${shadow.raised}, inset 0 0 0 1px ${roles.line.floating}` }}>
                    {/* Opens with focus on the chosen item; the menu stays open after a selection. */}
                    <MenuList id={menuId} component="div" aria-label="Theme" autoFocusItem={open} onKeyDown={onMenuKeyDown} sx={{ p: 0 }}>
                        <Typography aria-hidden component="div" sx={{ px: 1.5, pt: .5, pb: 1, color: roles.text.secondary, fontSize: '0.8125rem', fontWeight: 600 }}>Theme</Typography>
                        <Box role="group" aria-label="Theme">
                            {themeOptions.map((option, index) => {
                                const active = index === checkedIndex;
                                return <MenuItem key={option.value} component="div" role="menuitemradio" aria-checked={active} selected={active}
                                    onClick={(event) => { choose(option.value); event.currentTarget.focus(); }}
                                    sx={{ minHeight: 44, color: roles.text.primary }}>
                                    <PresentationIcon name={option.icon} sx={{ fontSize: 20, color: roles.text.secondary, flexShrink: 0 }} />
                                    <Box component="span" sx={{ flex: 1, minWidth: 0 }}>
                                        <Box component="span" sx={{ display: 'block', fontSize: '0.9375rem', fontWeight: 'inherit' }}>{option.label}</Box>
                                        {option.value === 'system' && <Box component="span" sx={{ display: 'block', fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4,
                                            color: roles.text.secondary, whiteSpace: 'normal' }}>{caption}</Box>}
                                    </Box>
                                    {active && <PresentationIcon name="check" sx={{ fontSize: 20, flexShrink: 0 }} />}
                                </MenuItem>;
                            })}
                        </Box>
                    </MenuList>
                </Paper>
            </ClickAwayListener>
        </Popper>
    </>;
}

const REACH = presentationTokens.reachWidth, REACH_RISE = presentationTokens.reachHeight;

/**
 * Concave curved bottom-edge indicator ("canvas reach", DESIGN.md › Top bar): the
 * navbar's lower edge bends up under the active item in one symmetric bell. The
 * profile was traced from the reference (width ≈ 6.2 × height): long concave
 * shoulders that leave the edge tangentially, an inflection near mid-height and a
 * rounded convex crest. y(u) = ((1 − cos πu) / 2)^1.2 for each half, drawn as a
 * Catmull-Rom spline converted to cubic Béziers, so it is C1-smooth with no corners.
 * The shape is mirrored point for point, so it is exactly symmetric.
 */
export function canvasReachPath(width: number, rise: number, samples = 12): string {
    const half = Array.from({ length: samples + 1 }, (_, i) => {
        const u = i / samples;
        return [u * width / 2, rise * (1 - ((1 - Math.cos(Math.PI * u)) / 2) ** 1.2)] as const;
    });
    const points = [...half, ...half.slice(0, -1).reverse().map(([x, y]) => [width - x, y] as const)];
    const f = (n: number) => Number(n.toFixed(3));
    let d = `M${f(points[0][0])} ${f(points[0][1])}`;
    for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[Math.max(0, i - 1)], p1 = points[i], p2 = points[i + 1], p3 = points[Math.min(points.length - 1, i + 2)];
        // Ends keep a horizontal tangent so the curve meets the straight edge without a kink.
        const t1 = i === 0 ? [p2[0] - p1[0], 0] : [(p2[0] - p0[0]) / 2, (p2[1] - p0[1]) / 2];
        const t2 = i === points.length - 2 ? [p2[0] - p1[0], 0] : [(p3[0] - p1[0]) / 2, (p3[1] - p1[1]) / 2];
        d += ` C${f(p1[0] + t1[0] / 3)} ${f(p1[1] + t1[1] / 3)} ${f(p2[0] - t2[0] / 3)} ${f(p2[1] - t2[1] / 3)} ${f(p2[0])} ${f(p2[1])}`;
    }
    // Close below the edge (1 px overlap with the canvas) so no seam shows.
    return `${d} V${rise + 1} H0 Z`;
}
const REACH_PATH = canvasReachPath(REACH, REACH_RISE);
/**
 * The bell as a mask layer (88 × 15: the curve plus 1 px below the bar edge). It is subtracted from the
 * header, so the bar itself is cut and the surface underneath (the canvas, or content scrolling under the
 * sticky bar) shows through. Nothing is painted to fake the cut-out.
 */
export const REACH_MASK = `url("data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${REACH}" height="${REACH_RISE + 1}" viewBox="0 0 ${REACH} ${REACH_RISE + 1}"><path d="${REACH_PATH}"/></svg>`)}")`;

/** Ink top bar with the "canvas reach" under the active destination (DESIGN.md › Top bar). */
export function AppHeader({ onMenuToggle, menuOpen, navItems = [], selectedNavId, onNavSelect, utilities, actions, search, brandHref, onBrandSelect }: AppHeaderProps) {
    const theme = useTheme();
    const wide = useMediaQuery(theme.breakpoints.up('lg'));
    const desktop = useMediaQuery(theme.breakpoints.up('md'));
    // Full role sets use the existing compact spacing; brand, labels and utilities retain their sizes.
    const denseNavigation = navItems.length >= 4;
    const bar = useRef<HTMLDivElement>(null), header = useRef<HTMLElement>(null);
    const [reach, setReach] = useState<number | null>(null);
    const measure = useCallback(() => {
        const host = bar.current, frame = header.current, active = host?.querySelector<HTMLElement>('[data-nav-item][aria-current="page"]');
        if (!host || !frame || !active || active.offsetParent === null) { setReach(null); return; }
        // Relative to the header element, which carries the mask (the bar is centred inside it).
        const a = active.getBoundingClientRect(), h = frame.getBoundingClientRect();
        setReach(Math.round(a.left - h.left + a.width / 2 - REACH / 2));
    }, []);
    useLayoutEffect(() => {
        measure();
        const host = bar.current;
        // Web fonts swap in after first paint and change item widths, so remeasure when they finish loading.
        const fonts = typeof document !== 'undefined' ? document.fonts : undefined;
        fonts?.addEventListener?.('loadingdone', measure);
        void fonts?.ready?.then(measure);
        if (!host || typeof ResizeObserver === 'undefined') {
            window.addEventListener('resize', measure);
            return () => { window.removeEventListener('resize', measure); fonts?.removeEventListener?.('loadingdone', measure); };
        }
        // The item list moves without resizing when a sibling changes width (for example when the
        // wordmark face swaps in), so observe the bar, every slot in it and the list itself.
        const observer = new ResizeObserver(measure); observer.observe(host);
        if (header.current) observer.observe(header.current);
        for (const slot of [...host.children, ...host.querySelectorAll('nav ul')]) observer.observe(slot);
        return () => { observer.disconnect(); fonts?.removeEventListener?.('loadingdone', measure); };
    }, [measure, selectedNavId, navItems, wide]);
    const brand = <BrandWordmark size={presentationTokens.brandMark} hideTextBelow={360} />;
    // Canvas reach: the bar's own ink is cut by the bell (mask-composite: exclude), so the shape is a real
    // opening in the header, not a painted patch. Below 900 px there is no reach (the drawer marks the page).
    const cut = reach !== null ? {
        maskImage: `linear-gradient(#000, #000), ${REACH_MASK}`, WebkitMaskImage: `linear-gradient(#000, #000), ${REACH_MASK}`,
        maskSize: `100% 100%, ${REACH}px ${REACH_RISE + 1}px`, WebkitMaskSize: `100% 100%, ${REACH}px ${REACH_RISE + 1}px`,
        maskRepeat: 'no-repeat', WebkitMaskRepeat: 'no-repeat',
        maskPosition: `0 0, ${reach}px calc(100% + 1px)`, WebkitMaskPosition: `0 0, ${reach}px calc(100% + 1px)`,
        WebkitMaskComposite: 'xor', maskComposite: 'exclude',
        transition: `mask-position ${motion.reach}ms ${motion.easeIn}, -webkit-mask-position ${motion.reach}ms ${motion.easeIn}`,
    } : {};
    return <Box component="header" ref={header} data-canvas-reach={reach !== null ? '' : undefined} data-reach-for={reach !== null ? selectedNavId : undefined}
        data-reach-x={reach ?? undefined}
        sx={{ position: 'sticky', top: 0, zIndex: 'appBar', bgcolor: roles.bg.frame, color: 'common.white', [theme.breakpoints.up('md')]: cut }}>
        <Box ref={bar} sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: { xs: 1, md: denseNavigation ? 1 : 2 },
            height: { xs: presentationTokens.topBarHeightCompact, sm: presentationTokens.topBarHeight }, px: { xs: 1.5, sm: 2.5, md: denseNavigation ? 1.5 : 3 },
            maxWidth: 1600, mx: 'auto', '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>
            {onMenuToggle && <IconButton aria-label="Toggle navigation" aria-expanded={!!menuOpen} onClick={onMenuToggle}
                sx={{ display: { md: 'none' }, color: 'common.white', '&:hover': { bgcolor: roles.chrome.raised } }}><PresentationIcon name="menu" /></IconButton>}
            <Box sx={{ flexShrink: 0, minWidth: 0, display: 'flex' }}>
                {brandHref ? <ButtonBase component="a" href={brandHref} aria-label="CodeMastery home"
                    onClick={(event: MouseEvent) => { if (!onBrandSelect || !isPlainClick(event)) return; event.preventDefault(); onBrandSelect(); }}
                    sx={{ borderRadius: `${radius.sm}px`, p: .5 }}>{brand}</ButtonBase> : brand}
            </Box>
            {navItems.length > 0 && <Box component="nav" aria-label="Main navigation" sx={{ display: { xs: 'none', md: 'flex' }, flex: '1 0 auto', justifyContent: 'center', minWidth: 'max-content' }}>
                <Box component="ul" sx={{ display: 'flex', gap: denseNavigation ? 0 : .5, m: 0, p: 0, listStyle: 'none' }}>
                    {navItems.map((item) => <Box component="li" key={item.id}>
                        <TopNavItem item={item} current={item.id === selectedNavId} iconOnly={!wide && item.id !== selectedNavId && !!item.icon} onSelect={onNavSelect} />
                    </Box>)}
                </Box>
            </Box>}
            {navItems.length === 0 && <Box sx={{ flex: 1 }} />}
            {search && <Box sx={{ flex: '0 1 320px', minWidth: 0, display: { xs: 'none', md: 'block' } }}>{search}</Box>}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: .5, sm: denseNavigation ? .5 : 1 }, flex: '0 1 auto', minWidth: 0, ml: { xs: 'auto', md: 0 },
                ...(denseNavigation ? { '& [data-account-menu]': { gap: .5, pr: 1 } } : {}) }}>
                {/* Brand → navigation → [utilities] → Theme → session actions (DESIGN.md › Top bar, D13-3): the Theme
                    button sits right before the account menu, or before Sign in and Create account for visitors. */}
                {utilities}{desktop && <ThemeControl />}{actions}
            </Box>
        </Box>
    </Box>;
}

function TopNavItem({ item, current, iconOnly, onSelect }: { item: NavItem; current: boolean; iconOnly: boolean; onSelect?: (id: string) => void }) {
    const content = <ButtonBase component={item.href ? 'a' : 'button'} href={item.href} data-nav-item aria-current={current ? 'page' : undefined}
        onClick={(event: MouseEvent) => {
            // A link is routed in-app only when a handler exists and the click is plain; otherwise the
            // browser follows the href natively (including Cmd/Ctrl-click into a new tab).
            if (!onSelect || (item.href && !isPlainClick(event))) return;
            if (item.href) event.preventDefault();
            onSelect(item.id);
        }}
        sx={{ position: 'relative', height: 44, minWidth: 44, px: iconOnly ? 1.5 : 2, gap: 1, borderRadius: `${radius.pill}px`, fontSize: '0.9375rem', fontWeight: current ? 600 : 500,
            color: current ? 'common.white' : roles.chrome.muted, whiteSpace: 'nowrap', transition: `background-color ${motion.fast}ms, color ${motion.fast}ms`,
            '&:hover': { bgcolor: roles.chrome.raised, color: 'common.white' }, '& svg': { fontSize: 20 } }}>
        {item.icon}
        <Box component="span" className={iconOnly ? 'cm-visually-hidden' : undefined}>{item.label}</Box>
        {item.badge !== undefined && item.badge !== '' && item.badge !== 0 && <Box component="span" sx={{ minWidth: 20, px: .625, borderRadius: `${radius.pill}px`,
            bgcolor: roles.brand.badge, color: 'common.white', fontSize: '0.8125rem', fontWeight: 600, lineHeight: '20px', textAlign: 'center' }}>{item.badge}</Box>}
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
        <ButtonBase data-account-menu aria-haspopup="menu" aria-expanded={!!anchor} aria-label={`Account menu, ${displayName}, ${role}`}
            onClick={(event) => setAnchor(event.currentTarget)}
            sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0, maxWidth: '100%', height: 52, pl: .75, pr: { xs: .75, md: 1.5 }, borderRadius: `${radius.pill}px`, color: 'common.white',
                '&:hover': { bgcolor: roles.chrome.raised } }}>
            <Monogram name={displayName} initial={initial} tone="brand" />
            <Box sx={{ display: { xs: 'none', md: 'block' }, textAlign: 'left', width: 180, minWidth: 'min-content', maxWidth: 180, flex: '1 1 auto' }}>
                {/* The role supplies the minimum width; a long name may use the existing ellipsis. */}
                <Typography component="span" sx={{ display: 'block', width: 0, minWidth: '100%', fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayName}</Typography>
                <Typography component="span" sx={{ display: 'block', fontSize: '0.8125rem', fontWeight: 500, color: roles.chrome.muted, lineHeight: 1.3, whiteSpace: 'nowrap' }}>{role}</Typography>
            </Box>
            <PresentationIcon name="chevron-down" sx={{ fontSize: 18, display: { xs: 'none', md: 'block' } }} />
        </ButtonBase>
        <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            slotProps={{ paper: { sx: { minWidth: 220 } } }}>
            {renderItems(() => setAnchor(null))}
        </Menu>
    </>;
}

/** `brand` (solid Deep Logo Blue) is the signed-in account's own avatar (top bar, profile); `ink` marks other people (Q&A authors). */
export function Monogram({ name, initial, size = 40, tone = 'chrome' }: { name: string; initial?: string; size?: number; tone?: 'chrome' | 'ink' | 'brand' }) {
    const letter = initial ?? Array.from(name.trim())[0]?.toUpperCase() ?? '?';
    return <Box component="span" aria-hidden data-monogram={tone} sx={{ width: size, height: size, borderRadius: '50%', display: 'grid', placeItems: 'center', flexShrink: 0,
        bgcolor: tone === 'brand' ? roles.brand.identity : tone === 'chrome' ? roles.chrome.raised : roles.bg.frame, color: 'common.white', fontSize: size * 0.42, fontWeight: 600 }}>{letter}</Box>;
}

export function AccountMenuDivider() { return <Divider sx={{ my: .5 }} />; }

/** Utility button on the frame. `current` marks the page it opens (as the drawer marks its active row). */
export function HeaderIconButton({ label, icon, badge, onClick, href, current = false }: { label: string; icon: PresentationIconName; badge?: number; onClick?: () => void; href?: string; current?: boolean }) {
    const name = badge ? `${label}, ${badge} unread` : label;
    return <Tooltip title={label}>
        <IconButton aria-label={name} aria-current={current ? 'page' : undefined} onClick={onClick} {...(href ? { component: 'a', href } : {})}
            sx={{ position: 'relative', color: 'common.white', bgcolor: current ? roles.chrome.raised : undefined, '&:hover': { bgcolor: roles.chrome.raised } }}>
            <PresentationIcon name={icon} />
            {!!badge && <Box component="span" aria-hidden sx={{ position: 'absolute', top: 0, right: -2, minWidth: 20, height: 20, px: .5, borderRadius: `${radius.pill}px`,
                bgcolor: roles.brand.badge, color: 'common.white', fontSize: '0.8125rem', fontWeight: 600, lineHeight: '20px', textAlign: 'center', border: `2px solid ${roles.bg.frame}`, boxSizing: 'content-box' }}>{badge}</Box>}
        </IconButton>
    </Tooltip>;
}
