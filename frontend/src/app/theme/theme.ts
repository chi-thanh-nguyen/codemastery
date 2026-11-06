import { createTheme } from '@mui/material/styles';
import type { CSSProperties } from 'react';
import figtreeUrl from './figtree-latin-wght.woff2';

/**
 * "The Lit Path" design tokens — DESIGN.md revision DESIGN-2026-11-06-r1.
 * Values are normative in the DESIGN.md frontmatter; this file is their single
 * frontend source. Visual reference studied (not copied): "E-learning Platform —
 * Learning Plan Dashboard" by Bogdan Nikitin for Nixtio (Dribbble shot 23956256).
 * No logo, artwork, avatar, copy or screen composition from it is reused.
 */
export const color = {
    ink950: '#0A0B0F', ink900: '#15171C', ink700: '#3A3E46', ink600: '#50545C', ink500: '#6B7079', ink400: '#80858E',
    line200: '#E4E4E7', fill150: '#EEEEF0', canvas100: '#F6F6F7', surface: '#FFFFFF',
    chromeRaised: '#1C1E24', chromeMuted: '#A3A7B0', focusOnChrome: '#7DB7FF',
    brand700: '#0652C4', brand600: '#0866F2', brand400: '#3B9CFA', brand100: '#E3EEFF',
    sky100: '#DFF6FB', sky50: '#EEFAFD',
    mint100: '#D3F7D9', mint50: '#E9FBEC', mint700: '#1F7A3A',
    butter100: '#FCEBB8', butter50: '#FEF5DB', butter800: '#7A5200',
    lilac100: '#F1D9FB', lilac50: '#F8ECFD', lilac700: '#7A2FB8',
    rose100: '#FDE2E1', rose50: '#FEF0EF', rose700: '#B42318',
    dataMastered: '#2E9E4F', dataLearning: '#0866F2', dataWeak: '#B07A00', dataUnknown: '#80858E',
} as const;

/** Semantic families. Text on a tint is always ink-900; `strong` is for glyphs and small text. */
export type Tone = 'neutral' | 'sky' | 'mint' | 'butter' | 'lilac' | 'rose';
export const tones: Record<Tone, { tint: string; soft: string; strong: string; text: string }> = {
    neutral: { tint: color.fill150, soft: color.surface, strong: color.ink700, text: color.ink900 },
    sky: { tint: color.sky100, soft: color.sky50, strong: color.brand600, text: color.brand700 },
    mint: { tint: color.mint100, soft: color.mint50, strong: color.mint700, text: color.mint700 },
    butter: { tint: color.butter100, soft: color.butter50, strong: color.butter800, text: color.butter800 },
    lilac: { tint: color.lilac100, soft: color.lilac50, strong: color.lilac700, text: color.lilac700 },
    rose: { tint: color.rose100, soft: color.rose50, strong: color.rose700, text: color.rose700 },
};

export const radius = { xs: 8, sm: 12, md: 16, lg: 24, xl: 32, pill: 999 } as const;

export const shadow = {
    hover: '0 6px 20px rgba(10, 11, 15, 0.08)',
    raised: '0 12px 32px rgba(10, 11, 15, 0.12)',
    overlay: '0 24px 64px rgba(10, 11, 15, 0.20)',
    scrim: 'rgba(10, 11, 15, 0.48)',
} as const;

export const motion = {
    fast: 120, standard: 200, panel: 280, reach: 240, signature: 400,
    easeIn: 'cubic-bezier(0.2, 0, 0, 1)', easeOut: 'cubic-bezier(0.4, 0, 1, 1)',
} as const;

/** Shell geometry. Kept under the historical export name used by layouts. */
export const presentationTokens = {
    topBarHeight: 80, topBarHeightCompact: 64, canvasInset: 8,
    asideWidth: 360, asideWidthWide: 400, outlineWidth: 280, readingWidth: 720, contentMax: 1440,
    drawerWidth: 304, reachWidth: 56, reachHeight: 12,
    pagePadding: { xs: 2, sm: 3, md: 4, lg: 5 },
} as const;

const fontStack = 'Figtree, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif';
export const codeFontStack = 'ui-monospace, "SF Mono", SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace';
const compact = '@media (max-width:599.95px)';
export const focusRing = { outline: `2px solid ${color.brand600}`, outlineOffset: 2 } as const;
export const focusRingOnChrome = { outline: `2px solid ${color.focusOnChrome}`, outlineOffset: 2 } as const;

declare module '@mui/material/styles' {
    interface Palette { ink: Palette['primary'] }
    interface PaletteOptions { ink?: PaletteOptions['primary'] }
    interface TypographyVariants { stat: CSSProperties; reading: CSSProperties; code: CSSProperties }
    interface TypographyVariantsOptions { stat?: CSSProperties; reading?: CSSProperties; code?: CSSProperties }
}
declare module '@mui/material/Typography' {
    interface TypographyPropsVariantOverrides { stat: true; reading: true; code: true }
}
declare module '@mui/material/Button' { interface ButtonPropsColorOverrides { ink: true } }
declare module '@mui/material/IconButton' { interface IconButtonPropsColorOverrides { ink: true } }
declare module '@mui/material/LinearProgress' { interface LinearProgressPropsColorOverrides { ink: true } }

export const appTheme = createTheme({
    spacing: 8,
    // Covers MUI's JavaScript transition lifecycles and scrolling as well as CSS.
    motion: { reducedMotion: 'system' },
    palette: {
        mode: 'light',
        primary: { main: color.brand600, dark: color.brand700, light: color.brand400, contrastText: color.surface },
        ink: { main: color.ink950, dark: color.chromeRaised, light: color.ink700, contrastText: color.surface },
        secondary: { main: color.lilac700, light: color.lilac100, dark: color.lilac700, contrastText: color.surface },
        success: { main: color.mint700, light: color.mint100, dark: color.mint700, contrastText: color.surface },
        warning: { main: color.butter800, light: color.butter100, dark: color.butter800, contrastText: color.surface },
        error: { main: color.rose700, light: color.rose100, dark: color.rose700, contrastText: color.surface },
        info: { main: color.brand600, light: color.sky100, dark: color.brand700, contrastText: color.surface },
        background: { default: color.canvas100, paper: color.surface },
        text: { primary: color.ink900, secondary: color.ink600, disabled: color.ink500 },
        divider: color.line200,
        action: { hover: color.fill150, selected: color.brand100, focus: color.brand100, disabledBackground: color.fill150, disabled: color.ink500 },
    },
    shape: { borderRadius: radius.md },
    typography: {
        // Self-hosted Figtree Latin variable WOFF2 (SIL OFL 1.1, public/figtree-OFL.txt),
        // from @fontsource-variable/figtree 5.2.10; upstream google/fonts ofl/figtree.
        fontFamily: fontStack,
        htmlFontSize: 16,
        fontSize: 16,
        fontWeightRegular: 400, fontWeightMedium: 500, fontWeightBold: 600,
        h1: { fontSize: '2.5rem', fontWeight: 600, lineHeight: 1.1, letterSpacing: '-0.025em', textWrap: 'balance', [compact]: { fontSize: '2rem' } },
        h2: { fontSize: '1.75rem', fontWeight: 600, lineHeight: 1.2, letterSpacing: '-0.02em', textWrap: 'balance', [compact]: { fontSize: '1.5rem' } },
        h3: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.2, letterSpacing: '-0.015em', textWrap: 'balance', [compact]: { fontSize: '1.25rem' } },
        h4: { fontSize: '1.125rem', fontWeight: 600, lineHeight: 1.35, letterSpacing: '-0.005em', textWrap: 'balance' },
        h5: { fontSize: '1.125rem', fontWeight: 600, lineHeight: 1.35 },
        h6: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
        subtitle1: { fontSize: '1.125rem', fontWeight: 600, lineHeight: 1.35 },
        subtitle2: { fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2 },
        body1: { fontSize: '1rem', fontWeight: 400, lineHeight: 1.55, textWrap: 'pretty' },
        body2: { fontSize: '0.9375rem', fontWeight: 400, lineHeight: 1.5, textWrap: 'pretty' },
        caption: { fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4 },
        overline: { fontSize: '0.8125rem', fontWeight: 600, lineHeight: 1.4, letterSpacing: 0, textTransform: 'none' },
        button: { fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2, letterSpacing: 0, textTransform: 'none' },
        stat: { fontFamily: fontStack, fontSize: '2.5rem', fontWeight: 600, lineHeight: 1, letterSpacing: '-0.03em', fontVariantNumeric: 'tabular-nums lining-nums' },
        reading: { fontFamily: fontStack, fontSize: '1.0625rem', fontWeight: 400, lineHeight: 1.7, textWrap: 'pretty' },
        code: { fontFamily: codeFontStack, fontSize: '0.9375rem', fontWeight: 400, lineHeight: 1.6 },
    },
    components: {
        MuiCssBaseline: { styleOverrides: {
            '@font-face': {
                fontFamily: 'Figtree', fontStyle: 'normal', fontWeight: '300 900', fontDisplay: 'swap',
                src: `url("${figtreeUrl}") format("woff2")`,
                unicodeRange: 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD',
            },
            // The page scrollbar sits on the ink frame; keep it in the frame's palette where the platform shows one.
            html: { WebkitTextSizeAdjust: '100%', scrollbarColor: `${color.ink600} ${color.ink950}` },
            body: { margin: 0, backgroundColor: color.ink950, color: color.ink900, caretColor: color.brand600 },
            '::selection': { backgroundColor: color.brand100, color: color.ink900 },
            ':where(:focus-visible)': focusRing,
            // Inputs show focus through their outline border.
            ':where(.MuiInputBase-input:focus-visible)': { outline: 'none' },
            // Programmatic heading focus announces the route without framing static text.
            ':where(h1[tabindex="-1"], main[tabindex="-1"], [data-focus-target][tabindex="-1"]):focus': { outline: 'none' },
            'code, kbd, samp, pre': { fontFamily: codeFontStack },
            '.cm-tabular': { fontVariantNumeric: 'tabular-nums lining-nums' },
            '.cm-visually-hidden': { position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap', border: 0 },
            '@keyframes cm-shimmer': { from: { backgroundPosition: '100% 0' }, to: { backgroundPosition: '-100% 0' } },
            '@media (prefers-reduced-motion: reduce)': {
                '*, *::before, *::after': {
                    animationDuration: '0.01ms !important', animationIterationCount: '1 !important',
                    transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important',
                },
            },
        } },
        MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { backgroundImage: 'none' } } },
        // ButtonBase resets outline to 0, which beats the zero-specificity global ring; restore it for keyboard focus.
        MuiButtonBase: { defaultProps: { disableRipple: true }, styleOverrides: { root: { '&.Mui-focusVisible': focusRing } } },
        MuiButton: {
            defaultProps: { disableElevation: true, color: 'ink' },
            styleOverrides: {
                root: {
                    borderRadius: radius.pill, minHeight: 48, paddingInline: 24, gap: 8, whiteSpace: 'nowrap',
                    transition: `background-color ${motion.fast}ms ${motion.easeIn}, color ${motion.fast}ms, transform ${motion.fast}ms`,
                    '&:active': { transform: 'scale(0.98)' },
                    '&.Mui-disabled': { backgroundColor: color.fill150, color: color.ink500 },
                    variants: [
                        { props: { variant: 'outlined' }, style: { border: 0, backgroundColor: color.fill150, color: color.ink900, paddingInline: 20,
                            '&:hover': { border: 0, backgroundColor: color.line200 } } },
                        { props: { variant: 'text' }, style: { color: color.brand600, paddingInline: 12, '&:hover': { backgroundColor: 'transparent', textDecoration: 'underline', textUnderlineOffset: 3 } } },
                        { props: { variant: 'text', color: 'inherit' }, style: { color: 'inherit' } },
                        { props: { variant: 'text', color: 'error' }, style: { color: color.rose700 } },
                        { props: { variant: 'text' }, style: { '&.Mui-disabled': { backgroundColor: 'transparent' } } },
                        { props: { variant: 'contained', color: 'ink' }, style: { '&:hover': { backgroundColor: color.chromeRaised } } },
                        { props: { variant: 'contained', color: 'error' }, style: { backgroundColor: color.rose700, '&:hover': { backgroundColor: '#951C13' } } },
                    ],
                },
                sizeSmall: { minHeight: 40, paddingInline: 16, fontSize: '0.9375rem' },
                startIcon: { marginLeft: -4, marginRight: 0 },
                endIcon: { marginRight: -4, marginLeft: 0 },
            },
        },
        MuiIconButton: { styleOverrides: { root: {
            width: 44, height: 44, borderRadius: radius.pill, color: 'inherit',
            '&:hover': { backgroundColor: color.fill150 },
            '&.Mui-disabled': { color: color.ink500 },
        } } },
        MuiTextField: { defaultProps: { slotProps: { inputLabel: { shrink: true } } } },
        MuiFormControl: { styleOverrides: { root: { '& > .MuiInputLabel-root + .MuiInputBase-root': { marginTop: 0 } } } },
        MuiInputLabel: { defaultProps: { shrink: true }, styleOverrides: {
            root: { position: 'relative', transform: 'none', marginBottom: 8, color: color.ink900, fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2,
                whiteSpace: 'normal', overflow: 'visible', maxWidth: '100%',
                '&.Mui-focused': { color: color.ink900 }, '&.Mui-error': { color: color.ink900 }, '&.Mui-disabled': { color: color.ink600 } },
            // DESIGN.md › Inputs: required fields say "(required)", not an asterisk alone. The input keeps its required attribute.
            asterisk: { fontSize: 0, '&::after': { content: '" (required)"', fontSize: '0.8125rem', fontWeight: 500, color: color.ink600 } },
        } },
        MuiOutlinedInput: {
            defaultProps: { notched: false },
            styleOverrides: {
                root: {
                    borderRadius: radius.md, backgroundColor: color.surface, fontSize: '1rem', lineHeight: 1.5, minHeight: 52,
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: color.ink400, borderWidth: 1.5, top: 0, '& legend': { display: 'none' } },
                    '&:hover:not(.Mui-disabled):not(.Mui-focused):not(.Mui-error) .MuiOutlinedInput-notchedOutline': { borderColor: color.ink700 },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: color.brand600, borderWidth: 2 },
                    '&.Mui-error .MuiOutlinedInput-notchedOutline': { borderColor: color.rose700, borderWidth: 2 },
                    '&.Mui-disabled': { backgroundColor: color.fill150 },
                    variants: [{ props: { hiddenLabel: true }, style: {
                        borderRadius: radius.pill, backgroundColor: color.fill150,
                        '& .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent', borderWidth: 2 },
                        '&:hover:not(.Mui-disabled):not(.Mui-focused) .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
                        '&.Mui-focused': { backgroundColor: color.surface },
                        '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: color.brand600 },
                    } }],
                },
                input: { padding: '14px 16px', '&::placeholder': { color: color.ink600, opacity: 1 } },
                multiline: { padding: 0 },
            },
        },
        MuiFormHelperText: { styleOverrides: { root: { marginInline: 0, marginTop: 8, fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4, color: color.ink600,
            '&.Mui-error': { color: color.rose700 } } } },
        MuiSelect: { styleOverrides: { icon: { color: color.ink700, right: 14 } } },
        MuiMenu: { styleOverrides: { paper: { borderRadius: radius.lg, boxShadow: shadow.raised, marginTop: 8 }, list: { padding: 8 } } },
        MuiMenuItem: { styleOverrides: { root: { minHeight: 44, borderRadius: radius.sm, fontSize: '0.9375rem', gap: 12,
            '&.Mui-focusVisible, &:hover': { backgroundColor: color.brand100 }, '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: color.brand100, fontWeight: 600 } } } },
        MuiPopover: { styleOverrides: { paper: { borderRadius: radius.lg, boxShadow: shadow.raised } } },
        MuiTooltip: { defaultProps: { enterDelay: 400, enterNextDelay: 200 }, styleOverrides: {
            tooltip: { backgroundColor: color.ink950, color: color.surface, borderRadius: radius.sm, padding: '8px 12px', fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4, maxWidth: 280 },
            arrow: { color: color.ink950 },
        } },
        MuiDialog: { styleOverrides: {
            paper: { borderRadius: radius.xl, margin: 16, width: 'calc(100% - 32px)', boxShadow: shadow.overlay, padding: 8 },
            paperFullScreen: { borderRadius: 0, margin: 0, width: '100%' },
        } },
        MuiBackdrop: { styleOverrides: { root: { '&:not(.MuiBackdrop-invisible)': { backgroundColor: shadow.scrim } } } },
        MuiDialogTitle: { styleOverrides: { root: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.2, letterSpacing: '-0.015em', padding: '24px 24px 8px' } } },
        MuiDialogContent: { styleOverrides: { root: { padding: '8px 24px 16px' } } },
        MuiDialogContentText: { styleOverrides: { root: { color: color.ink600, fontSize: '1rem', lineHeight: 1.55 } } },
        MuiDialogActions: { styleOverrides: { root: { padding: '8px 24px 24px', gap: 8, flexWrap: 'wrap', '& > :not(style) ~ :not(style)': { marginLeft: 0 } } } },
        MuiDrawer: { styleOverrides: { paper: { backgroundColor: color.ink950, color: color.surface, boxShadow: shadow.overlay } } },
        MuiTableCell: { styleOverrides: {
            root: { borderBottom: `1px solid ${color.line200}`, padding: '14px 16px', fontSize: '0.9375rem', lineHeight: 1.5, color: color.ink900 },
            head: { color: color.ink600, fontSize: '0.8125rem', fontWeight: 600, lineHeight: 1.4, height: 48, paddingBlock: 8, backgroundColor: color.surface },
        } },
        MuiTableRow: { styleOverrides: { root: { '&:last-child td': { borderBottom: 0 }, '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: color.brand100 } } } },
        MuiTableSortLabel: { styleOverrides: { root: { '&.Mui-active': { color: color.ink900 }, '&:focus-visible': focusRing }, icon: { opacity: 0.6 } } },
        MuiLinearProgress: { styleOverrides: { root: { height: 8, borderRadius: radius.pill, backgroundColor: color.line200 }, bar: { borderRadius: radius.pill, backgroundColor: color.brand600 } } },
        // Pulse, not a horizontal sweep: loading motion conveys state without marquee-like movement.
        // DESIGN.md › Skeletons: fill-150 blocks with a 1.2 s shimmer, a static fill with reduced motion.
        // The highlight moves through background-position inside each block; no element travels across the page.
        MuiSkeleton: { defaultProps: { animation: false }, styleOverrides: { root: {
            backgroundColor: color.fill150, borderRadius: radius.sm, transform: 'none',
            backgroundImage: `linear-gradient(90deg, ${color.fill150} 0%, ${color.canvas100} 50%, ${color.fill150} 100%)`,
            backgroundSize: '200% 100%', backgroundRepeat: 'no-repeat', animation: 'cm-shimmer 1.2s ease-in-out infinite',
            '@media (prefers-reduced-motion: reduce)': { animation: 'none', backgroundImage: 'none' },
        } } },
        MuiAlert: {
            defaultProps: { variant: 'standard' },
            styleOverrides: {
                root: { borderRadius: radius.md, padding: '12px 16px', fontSize: '0.9375rem', lineHeight: 1.5, color: color.ink900, alignItems: 'flex-start',
                    variants: [
                        { props: { severity: 'success' }, style: { backgroundColor: color.mint100, '& .MuiAlert-icon': { color: color.mint700 } } },
                        { props: { severity: 'warning' }, style: { backgroundColor: color.butter100, '& .MuiAlert-icon': { color: color.butter800 } } },
                        { props: { severity: 'error' }, style: { backgroundColor: color.rose100, '& .MuiAlert-icon': { color: color.rose700 } } },
                        { props: { severity: 'info' }, style: { backgroundColor: color.sky100, '& .MuiAlert-icon': { color: color.brand700 } } },
                    ] },
                icon: { padding: '2px 0', marginRight: 12, opacity: 1 },
                message: { padding: '2px 0', overflowWrap: 'anywhere' },
                action: { paddingTop: 0, marginRight: -4, alignItems: 'center' },
            },
        },
        MuiAlertTitle: { styleOverrides: { root: { fontSize: '0.9375rem', fontWeight: 600, marginBottom: 2, marginTop: 0 } } },
        MuiSnackbarContent: { styleOverrides: { root: { backgroundColor: color.ink950, color: color.surface, borderRadius: radius.pill, padding: '6px 20px', fontSize: '1rem', boxShadow: shadow.raised } } },
        MuiToggleButtonGroup: { styleOverrides: { root: { backgroundColor: color.fill150, borderRadius: radius.pill, padding: 4, gap: 4, flexWrap: 'wrap' },
            grouped: { border: 0, borderRadius: `${radius.pill}px !important`, margin: '0 !important' } } },
        MuiToggleButton: { styleOverrides: { root: {
            border: 0, minHeight: 36, paddingInline: 16, fontSize: '0.9375rem', fontWeight: 600, color: color.ink600, textTransform: 'none', whiteSpace: 'nowrap',
            '&:hover': { backgroundColor: color.line200 },
            '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: color.surface, color: color.ink900, boxShadow: '0 2px 8px rgba(10, 11, 15, 0.10)' },
        } } },
        MuiChip: { styleOverrides: { root: { borderRadius: radius.pill, height: 32, fontSize: '0.9375rem', fontWeight: 600, backgroundColor: color.fill150, color: color.ink900 },
            deleteIcon: { color: color.ink600, '&:hover': { color: color.ink900 } } } },
        MuiPagination: { styleOverrides: { ul: { gap: 4 } } },
        MuiPaginationItem: { styleOverrides: { root: { minWidth: 44, height: 44, borderRadius: radius.pill, fontSize: '0.9375rem', fontWeight: 600, color: color.ink900,
            '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: color.ink950, color: color.surface } } } },
        MuiBreadcrumbs: { styleOverrides: { root: { fontSize: '0.8125rem', fontWeight: 500, color: color.ink600 }, separator: { marginInline: 6 } } },
        MuiLink: { defaultProps: { underline: 'hover' }, styleOverrides: { root: { color: color.brand600, textUnderlineOffset: 3, '&:hover': { color: color.brand700 } } } },
        MuiCheckbox: { styleOverrides: { root: { color: color.ink400, '&.Mui-checked': { color: color.brand600 } } } },
        MuiRadio: { styleOverrides: { root: { color: color.ink400, '&.Mui-checked': { color: color.brand600 } } } },
        MuiSwitch: { styleOverrides: { switchBase: { '&.Mui-checked': { color: color.surface, '& + .MuiSwitch-track': { backgroundColor: color.brand600, opacity: 1 } } },
            track: { backgroundColor: color.ink400, opacity: 1 } } },
        MuiAccordion: { defaultProps: { disableGutters: true }, styleOverrides: { root: { backgroundColor: 'transparent', '&::before': { display: 'none' } } } },
        MuiDivider: { styleOverrides: { root: { borderColor: color.line200 } } },
    },
});
