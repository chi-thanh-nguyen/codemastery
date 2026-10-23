import { createTheme } from '@mui/material/styles';

/**
 * Presentation-only tokens selected under the Leader's 2026-10-23 UI delegation.
 * Visual inspiration: LMS Admin Dashboard UI/UX Design for EdTech SaaS Platforms
 * — Rakibul Ism / Panze UX Design Studio:
 * https://dribbble.com/shots/27183809-LMS-Admin-Dashboard-UI-UX-Design-for-EdTech-SaaS-Platforms
 * General dashboard principles only; independently developed for CodeMastery.
 * No proprietary graphics, logos or complete screen compositions are reused.
 */
export const presentationTokens = {
    sidebarWidth: 224,
    headerHeight: 64,
    cardRadius: 12,
    controlRadius: 8,
    frameRadius: 12,
    softBlue: '#EDF4FF',
    softGreen: '#EDF7F0',
    softOrange: '#FFF4E5',
    softPink: '#FCEFF4',
    muted: '#A8A6AF', // Decorative only; never ordinary interface text.
    warm: '#996F1F',
    activeNav: '#F0F2F5',
    hoverNav: '#E6E9EE',
    frameShadow: '0 16px 64px rgba(38, 52, 84, 0.09)',
    outerGradient: 'linear-gradient(135deg, #EAF2FF 0%, #F0EDFA 55%, #FBEFF3 100%)',
} as const;

export const appTheme = createTheme({
    spacing: 8,
    palette: {
        mode: 'light',
        primary: { main: '#1F6BE8', light: '#52A5F7', dark: '#1753B5', contrastText: '#FFFFFF' },
        secondary: { main: '#996F1F', light: '#FFF4E5', dark: '#715014' },
        success: { main: '#287448', light: '#EDF7F0', dark: '#205D3A' },
        warning: { main: '#996F1F', light: '#FFF4E5', dark: '#715014' },
        error: { main: '#B6324B', light: '#FCEFF4', dark: '#8D243A' },
        background: { default: '#F9FAFC', paper: '#FFFFFF' },
        text: { primary: '#101418', secondary: '#4A5458' },
        divider: '#E5E9EF',
        action: { hover: '#F5F6F8', selected: presentationTokens.activeNav, focus: presentationTokens.hoverNav },
    },
    shape: { borderRadius: presentationTokens.cardRadius },
    typography: {
        // Inter is used only if installed locally; no external font request.
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontSize: 14,
        h4: { fontSize: '1.75rem', fontWeight: 700, letterSpacing: '-0.045em', lineHeight: 1.2 },
        h5: { fontSize: '1.25rem', fontWeight: 700, letterSpacing: '-0.025em' },
        h6: { fontSize: '1rem', fontWeight: 650, letterSpacing: '-0.02em' },
        subtitle1: { fontSize: '.9375rem', fontWeight: 600 },
        subtitle2: { fontSize: '.8125rem', fontWeight: 600 },
        body1: { fontSize: '.9375rem', lineHeight: 1.6 },
        body2: { fontSize: '.8125rem', lineHeight: 1.6 },
        caption: { fontSize: '.75rem', lineHeight: 1.5 },
        overline: { fontSize: '.6875rem', fontWeight: 650, letterSpacing: '.09em', lineHeight: 1.5 },
        button: { textTransform: 'none', fontSize: '.8125rem', fontWeight: 600 },
    },
    components: {
        MuiCssBaseline: { styleOverrides: {
            body: { margin: 0 },
            ':focus-visible': { outline: '2px solid #1F6BE8', outlineOffset: 3 },
            '@media (prefers-reduced-motion: reduce)': {
                '*, *::before, *::after': {
                    animationDuration: '0.01ms !important', animationIterationCount: '1 !important',
                    transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important',
                },
            },
        } },
        MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: '1px solid #E5E9EF', backgroundImage: 'none' } } },
        MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: {
            root: { borderRadius: 8, minHeight: 44, paddingInline: 16,
                '&.MuiButton-contained.MuiButton-colorPrimary:not(.Mui-disabled)': { backgroundColor: '#101418', '&:hover': { backgroundColor: '#29333D' } },
                '&.MuiButton-outlined.MuiButton-colorPrimary:not(.Mui-disabled), &.MuiButton-text.MuiButton-colorPrimary:not(.Mui-disabled)': { color: '#1753B5' } },
            outlined: { borderColor: '#87919C' },
        } },
        MuiIconButton: { styleOverrides: { root: { borderRadius: 8, width: 44, height: 44 } } },
        MuiOutlinedInput: { styleOverrides: {
            root: { borderRadius: 8, backgroundColor: '#FFFFFF', fontSize: '.8125rem', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#87919C' } },
            input: { '&::placeholder': { color: '#4A5458', opacity: 1 } },
        } },
        MuiInputLabel: { styleOverrides: { root: { color: '#4A5458' } } },
        MuiChip: { styleOverrides: { root: { borderRadius: 6, fontWeight: 500, fontSize: '.6875rem' } } },
        MuiListItemButton: { styleOverrides: { root: {
            minHeight: 44, borderRadius: 8,
            '&.Mui-selected': { backgroundColor: presentationTokens.activeNav, color: '#101418' },
            '&.Mui-selected:hover, &.Mui-selected.Mui-focusVisible': { backgroundColor: presentationTokens.hoverNav },
            '&.Mui-focusVisible': { outline: '2px solid #1F6BE8', outlineOffset: -2 },
        } } },
        MuiTableCell: { styleOverrides: {
            root: { borderBottomColor: '#E5E9EF', padding: '14px 16px', fontSize: '.8125rem' },
            head: { color: '#4A5458', backgroundColor: '#F9FAFC', fontWeight: 600, fontSize: '.75rem' },
        } },
        MuiLinearProgress: { styleOverrides: { root: { height: 7, borderRadius: 8, backgroundColor: '#E3EBF8' }, bar: { borderRadius: 8 } } },
        MuiDialog: { styleOverrides: { paper: { borderRadius: 12, margin: 16, width: 'calc(100% - 32px)' } } },
        MuiDialogTitle: { styleOverrides: { root: { fontSize: '1.125rem', fontWeight: 650 } } },
    },
});
