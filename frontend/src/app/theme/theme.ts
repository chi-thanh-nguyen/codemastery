import { createTheme } from '@mui/material/styles';
import variableFontUrl from './plus-jakarta-sans-latin-wght.woff2';

/**
 * Presentation-only tokens selected under the Leader's 2026-10-23 UI delegation.
 * Visual inspiration: LMS Admin Dashboard UI/UX Design for EdTech SaaS Platforms
 * — Rakibul Ism / Panze UX Design Studio:
 * https://dribbble.com/shots/27183809-LMS-Admin-Dashboard-UI-UX-Design-for-EdTech-SaaS-Platforms
 * General dashboard principles only; independently developed for CodeMastery.
 * No proprietary graphics, logos or complete screen compositions are reused.
 */
export const presentationTokens = {
    sidebarWidth: 248,
    headerHeight: 72,
    cardRadius: 12,
    controlRadius: 10,
    frameRadius: 12,
    pageBackground: '#F8F9FA',
    surfaceBorder: '#E6E8EC',
    searchSurface: '#F5F6F8',
    sidebarBackground: '#FFFFFE',
    chartBarFill: '#52A5F7',
    chartBarFade: '#EFF8FF',
    chartBarEdge: '#3988CE',
    chartBarActiveEdge: '#287CC9',
    chartBarActive: '#2589DE',
    chartGrid: '#E8EBEF',
    softBlue: '#E3EEFF',
    softGreen: '#D7F9E8',
    softOrange: '#FFEDD5',
    softViolet: '#EDE5FF',
    softPink: '#FCEFF4',
    muted: '#A8A6AF', // Decorative only; never ordinary interface text.
    warm: '#996F1F',
    activeNav: '#EDF4FF',
    hoverNav: '#DDE9FC',
    frameShadow: '0 16px 64px rgba(38, 52, 84, 0.09)',
    outerGradient: 'linear-gradient(135deg, #EAF2FF 0%, #F0EDFA 55%, #FBEFF3 100%)',
} as const;

export const appTheme = createTheme({
    spacing: 8,
    // Cover MUI's JavaScript transition lifecycles and scrolling as well as CSS.
    motion: { reducedMotion: 'system' },
    palette: {
        mode: 'light',
        primary: { main: '#1F6BE8', light: '#52A5F7', dark: '#1753B5', contrastText: '#FFFFFF' },
        secondary: { main: '#996F1F', light: '#FFF4E5', dark: '#715014' },
        success: { main: '#287448', light: '#EDF7F0', dark: '#205D3A' },
        warning: { main: '#996F1F', light: '#FFF4E5', dark: '#715014' },
        error: { main: '#B6324B', light: '#FCEFF4', dark: '#8D243A' },
        background: { default: presentationTokens.pageBackground, paper: '#FFFFFF' },
        text: { primary: '#101418', secondary: '#4A5458' },
        divider: presentationTokens.surfaceBorder,
        action: { hover: '#F5F6F8', selected: presentationTokens.activeNav, focus: presentationTokens.hoverNav },
    },
    shape: { borderRadius: presentationTokens.cardRadius },
    typography: {
        // Self-hosted, unmodified Google Fonts Latin variable asset (200–800).
        // SIL OFL 1.1 and copyright ship with the build: public/plus-jakarta-sans-OFL.txt
        // Source: https://github.com/google/fonts/tree/main/ofl/plusjakartasans
        fontFamily: '"Plus Jakarta Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontSize: 14,
        fontWeightRegular: 400,
        fontWeightMedium: 500,
        fontWeightBold: 700,
        h3: { fontSize: '2rem', fontWeight: 700, letterSpacing: '-.025em', lineHeight: 1.25 },
        h4: { fontSize: '1.875rem', fontWeight: 500, letterSpacing: '-.025em', lineHeight: 1.25 },
        h5: { fontSize: '1.375rem', fontWeight: 600, letterSpacing: '-.02em', lineHeight: 1.35 },
        h6: { fontSize: '1.25rem', fontWeight: 600, letterSpacing: '-.015em', lineHeight: 1.4 },
        subtitle1: { fontSize: '.9375rem', fontWeight: 500, letterSpacing: 0, lineHeight: 1.5 },
        subtitle2: { fontSize: '.875rem', fontWeight: 600, letterSpacing: 0, lineHeight: 1.5 },
        body1: { fontSize: '.9375rem', fontWeight: 400, letterSpacing: 0, lineHeight: 1.6 },
        body2: { fontSize: '.875rem', fontWeight: 400, letterSpacing: 0, lineHeight: 1.55 },
        caption: { fontSize: '.8125rem', fontWeight: 400, letterSpacing: 0, lineHeight: 1.5 },
        overline: { fontSize: '.75rem', fontWeight: 500, letterSpacing: '.07em', lineHeight: 1.5 },
        button: { textTransform: 'none', fontSize: '.875rem', fontWeight: 500, letterSpacing: 0, lineHeight: 1.5 },
    },
    components: {
        MuiCssBaseline: { styleOverrides: {
            '@font-face': {
                fontFamily: 'Plus Jakarta Sans', fontStyle: 'normal', fontWeight: '200 800',
                fontDisplay: 'swap', src: `url("${variableFontUrl}") format("woff2")`,
                unicodeRange: 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD',
            },
            body: { margin: 0 },
            ':focus-visible': { outline: '2px solid #1F6BE8', outlineOffset: 3 },
            '@media (prefers-reduced-motion: reduce)': {
                '*, *::before, *::after': {
                    animationDuration: '0.01ms !important', animationIterationCount: '1 !important',
                    transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important',
                },
            },
        } },
        MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: `1px solid ${presentationTokens.surfaceBorder}`, backgroundImage: 'none' } } },
        MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: {
            root: { borderRadius: presentationTokens.controlRadius, minHeight: 44, paddingInline: 16,
                '&.MuiButton-contained.MuiButton-colorPrimary:not(.Mui-disabled)': { backgroundColor: '#1F6BE8', '&:hover': { backgroundColor: '#1753B5' } },
                '&.MuiButton-outlined.MuiButton-colorPrimary:not(.Mui-disabled), &.MuiButton-text.MuiButton-colorPrimary:not(.Mui-disabled)': { color: '#1753B5' } },
            outlined: { borderColor: '#87919C' },
            sizeSmall: { fontSize: '.875rem' },
        } },
        MuiIconButton: { styleOverrides: { root: { borderRadius: 8, width: 44, height: 44 } } },
        MuiOutlinedInput: { styleOverrides: {
            root: { borderRadius: presentationTokens.controlRadius, backgroundColor: '#FFFFFF', fontSize: '.875rem', lineHeight: 1.5,
                '& .MuiOutlinedInput-notchedOutline': { borderColor: '#87919C' },
                variants: [{ props: { hiddenLabel: true }, style: {
                    minHeight: 44, backgroundColor: presentationTokens.searchSurface,
                    '& .MuiOutlinedInput-notchedOutline, &:hover .MuiOutlinedInput-notchedOutline': { borderWidth: 0 },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderWidth: 2, borderColor: '#1F6BE8' },
                } }],
            },
            input: { '&::placeholder': { color: '#4A5458', opacity: 1 } },
            // The opaque label masks the border while moving; animating the notch separately
            // leaves a trailing gap after the label descends. Only the label needs to animate.
            notchedOutline: { '& legend': { transition: 'none' } },
        } },
        // MUI's legend uses .75em of the input font; its scaled label must use the same base size.
        MuiInputLabel: { styleOverrides: {
            root: { color: '#4A5458', fontSize: '.875rem', lineHeight: 1.5 },
            // Keep the border behind the label throughout its motion, including interrupted blur.
            outlined: { backgroundColor: '#FFFFFF' },
        } },
        MuiChip: { styleOverrides: { root: { borderRadius: 6, fontWeight: 500, fontSize: '.8125rem' } } },
        MuiListItemButton: { styleOverrides: { root: {
            minHeight: 44, borderRadius: 8,
            '&.Mui-selected': { backgroundColor: presentationTokens.activeNav, color: '#1753B5' },
            '&.Mui-selected:hover, &.Mui-selected.Mui-focusVisible': { backgroundColor: presentationTokens.hoverNav },
            '&.Mui-focusVisible': { outline: '2px solid #1F6BE8', outlineOffset: -2 },
        } } },
        MuiTableCell: { styleOverrides: {
            root: { borderBottomColor: presentationTokens.surfaceBorder, padding: '14px 16px', fontSize: '.875rem' },
            head: { color: '#4A5458', backgroundColor: presentationTokens.pageBackground, fontWeight: 500, fontSize: '.8125rem', lineHeight: 1.5 },
        } },
        MuiLinearProgress: { styleOverrides: { root: { height: 7, borderRadius: 8, backgroundColor: '#E3EBF8' }, bar: { borderRadius: 8 } } },
        MuiDialog: { styleOverrides: { paper: { borderRadius: 12, margin: 16, width: 'calc(100% - 32px)' } } },
        MuiDialogTitle: { styleOverrides: { root: { fontSize: '1.125rem', fontWeight: 600, lineHeight: 1.4 } } },
        MuiTab: { styleOverrides: { root: { textTransform: 'none', fontSize: '.875rem', fontWeight: 500, lineHeight: 1.5, '&.Mui-selected': { fontWeight: 600 } } } },
    },
});
