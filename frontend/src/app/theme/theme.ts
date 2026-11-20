import { createTheme } from '@mui/material/styles';
import type { TypographyStyle } from '@mui/material/styles';
import { createContext, useContext } from 'react';
import type { CSSProperties } from 'react';
import uiLatin from './google-sans-flex-latin-wght.woff2';
import uiLatinExt from './google-sans-flex-latin-ext-wght.woff2';
import uiVietnamese from './google-sans-flex-vietnamese-wght.woff2';
import codeLatin from './jetbrains-mono-latin-wght.woff2';
import codeLatinExt from './jetbrains-mono-latin-ext-wght.woff2';
import codeVietnamese from './jetbrains-mono-vietnamese-wght.woff2';
import brandLatin from './poppins-latin-800.woff2';

/**
 * "The Lit Path" design tokens — DESIGN.md revision DESIGN-2026-11-19-r12 (FE34: light and dark
 * colour schemes). Values are normative in the DESIGN.md frontmatter; this file is their single
 * frontend source. Visual reference studied (not copied): "E-learning Platform —
 * Learning Plan Dashboard" by Bogdan Nikitin for Nixtio (Dribbble shot 23956256).
 * No logo, artwork, avatar, copy or screen composition from it is reused.
 *
 * `color` and `darkColor` are the raw palettes, read only inside this file (and by tests). Themed
 * components consume `roles` (CSS variables of the active scheme), never raw values (Role-Not-Value Rule).
 */
export const color = {
    ink950: '#0A0B0F', ink900: '#15171C', ink700: '#3A3E46', ink600: '#50545C', ink500: '#6B7079', ink400: '#80858E',
    line200: '#E4E4E7', fill150: '#EEEEF0', canvas100: '#F6F6F7', surface: '#FFFFFF',
    chromeRaised: '#1C1E24', chromeHover: '#2A2D35', chromeMuted: '#A3A7B0', focusOnChrome: '#7DB7FF',
    brand700: '#0652C4', brand600: '#0866F2', brand400: '#3B9CFA', brand100: '#E3EEFF',
    sky100: '#DFF6FB', sky50: '#EEFAFD',
    mint100: '#D3F7D9', mint50: '#E9FBEC', mint700: '#1F7A3A',
    butter100: '#FCEBB8', butter50: '#FEF5DB', butter800: '#7A5200',
    lilac100: '#F1D9FB', lilac50: '#F8ECFD', lilac700: '#7A2FB8',
    rose100: '#FDE2E1', rose50: '#FEF0EF', rose700: '#B42318', rose800: '#951C13',
    dataMastered: '#2E9E4F', dataLearning: '#0866F2', dataWeak: '#B07A00', dataUnknown: '#80858E',
} as const;

const darkColor = {
    graphite980: '#0E0F13', graphite900: '#15171C', graphite850: '#1D2025', graphite800: '#23262D',
    graphite750: '#2A2D35', graphite700: '#2C3038', graphite600: '#363A43', graphite400: '#7B818B',
    mist50: '#ECEDEF', mist100: '#E6E8EB', mist200: '#B4B8C0', mist300: '#9AA0AA', mist500: '#6E737C',
    brand200: '#8DBDFF', brand300: '#6AA8FF', brand800: '#2A4A7F', brand900: '#1B2E4D',
    sky900: '#113445', sky950: '#0D2230', mint300: '#5DD283', mint900: '#143A25', mint950: '#0F2619',
    butter300: '#EBBB4E', butter900: '#3F310E', butter950: '#2C230B', lilac300: '#C9A2F7',
    lilac900: '#35244D', lilac950: '#251A35', rose300: '#FF8F85', rose500: '#D23A2E',
    rose700: '#951C13', rose900: '#43201E', rose950: '#2F1514',
    dataMastered: '#45BE6C', dataLearning: '#4D94FF', dataWeak: '#D9A21F', dataUnknown: '#80868F',
} as const;

const roleVar = (path: string) => `var(--mui-palette-roles-${path.replace(/\./g, '-')})`;
export const roles = {
    bg: {
        frame: roleVar('bg.frame'), canvas: roleVar('bg.canvas'), surface: roleVar('bg.surface'), fill: roleVar('bg.fill'),
        raised: roleVar('bg.raised'), hover: roleVar('bg.hover'), rowHover: roleVar('bg.rowHover'), field: roleVar('bg.field'), fieldDisabled: roleVar('bg.fieldDisabled'),
        selected: roleVar('bg.selected'), inverse: roleVar('bg.inverse'), code: roleVar('bg.code'), courseMark: roleVar('bg.courseMark'),
    },
    text: {
        primary: roleVar('text.primary'), strongSecondary: roleVar('text.strongSecondary'), secondary: roleVar('text.secondary'),
        tertiary: roleVar('text.tertiary'), disabled: roleVar('text.disabled'), onChrome: roleVar('text.onChrome'),
        onChromeMuted: roleVar('text.onChromeMuted'), inverse: roleVar('text.inverse'), code: roleVar('text.code'), onStrong: roleVar('text.onStrong'),
    },
    line: { hairline: roleVar('line.hairline'), strong: roleVar('line.strong'), code: roleVar('line.code'), floating: roleVar('line.floating') },
    accent: {
        link: roleVar('accent.link'), linkHover: roleVar('accent.linkHover'), linkOnTint: roleVar('accent.linkOnTint'),
        path: roleVar('accent.path'), progress: roleVar('accent.progress'), caret: roleVar('accent.caret'),
    },
    focus: { ring: roleVar('focus.ring'), onChrome: roleVar('focus.onChrome') },
    control: {
        primary: { background: roleVar('control.primary.background'), hover: roleVar('control.primary.hover'), text: roleVar('control.primary.text') },
        secondary: { background: roleVar('control.secondary.background'), border: roleVar('control.secondary.border'), hover: roleVar('control.secondary.hover') },
        onChrome: { background: roleVar('control.onChrome.background'), hover: roleVar('control.onChrome.hover'), text: roleVar('control.onChrome.text') },
        danger: { background: roleVar('control.danger.background'), hover: roleVar('control.danger.hover'), text: roleVar('control.danger.text') },
        disabled: { background: roleVar('control.disabled.background'), text: roleVar('control.disabled.text') },
    },
    tone: {
        neutral: { tint: roleVar('tone.neutral.tint'), soft: roleVar('tone.neutral.soft'), strong: roleVar('tone.neutral.strong'), text: roleVar('tone.neutral.text') },
        sky: { tint: roleVar('tone.sky.tint'), soft: roleVar('tone.sky.soft'), strong: roleVar('tone.sky.strong'), text: roleVar('tone.sky.text') },
        mint: { tint: roleVar('tone.mint.tint'), soft: roleVar('tone.mint.soft'), strong: roleVar('tone.mint.strong'), text: roleVar('tone.mint.text') },
        butter: { tint: roleVar('tone.butter.tint'), soft: roleVar('tone.butter.soft'), strong: roleVar('tone.butter.strong'), text: roleVar('tone.butter.text') },
        lilac: { tint: roleVar('tone.lilac.tint'), soft: roleVar('tone.lilac.soft'), strong: roleVar('tone.lilac.strong'), text: roleVar('tone.lilac.text') },
        rose: { tint: roleVar('tone.rose.tint'), soft: roleVar('tone.rose.soft'), strong: roleVar('tone.rose.strong'), text: roleVar('tone.rose.text') },
    },
    data: {
        mastered: roleVar('data.mastered'), learning: roleVar('data.learning'), weak: roleVar('data.weak'), unknown: roleVar('data.unknown'),
    },
    chrome: { raised: roleVar('chrome.raised'), hover: roleVar('chrome.hover'), muted: roleVar('chrome.muted') },
    brand: { identity: roleVar('brand.identity'), courseMark: roleVar('brand.courseMark'), badge: roleVar('brand.badge') },
    selection: { background: roleVar('selection.background'), text: roleVar('selection.text') },
    skeleton: { base: roleVar('skeleton.base'), highlight: roleVar('skeleton.highlight') },
    scrim: roleVar('scrim'),
} as const;

/**
 * Theme preference (DESIGN.md › Themes). The preference is what the person chose; the effective
 * scheme is what renders. One preference state lives above the router (`App.tsx`) and feeds every
 * Theme control through this context. It is a device setting: never sent to the backend and
 * independent of the session.
 */
export type ThemePreference = 'system' | 'light' | 'dark';
export type ThemeScheme = 'light' | 'dark';
/** The only browser-storage key this feature uses (DESIGN.md › Persistence and precedence). */
export const THEME_STORAGE_KEY = 'codemastery.theme';
export interface ThemePreferenceContextValue {
    /** The preference the Theme controls in this subtree show and change. */
    preference: ThemePreference;
    /** What `prefers-color-scheme` reports now (`light` when it cannot be queried). */
    systemScheme: ThemeScheme;
    setPreference: (preference: ThemePreference) => void;
    /**
     * The `/preview` document's memory-only override (S26). `null` follows the stored preference.
     * Only the preview sets it; it never writes storage and is gone on reload.
     */
    previewPreference: ThemePreference | null;
    setPreviewPreference: (preference: ThemePreference | null) => void;
}

export const ThemePreferenceContext = createContext<ThemePreferenceContextValue | null>(null);

export function useThemePreference() {
    const context = useContext(ThemePreferenceContext);
    if (!context) throw new Error('Theme preference context is unavailable.');
    return context;
}

export const isThemePreference = (value: unknown): value is ThemePreference =>
    value === 'system' || value === 'light' || value === 'dark';

export const resolveThemeScheme = (preference: ThemePreference, systemScheme: ThemeScheme): ThemeScheme =>
    preference === 'system' ? systemScheme : preference;

/** Missing, empty, unknown or unreadable values mean System; an invalid value is left in place. */
export function readStoredThemePreference(): ThemePreference {
    try {
        const value = globalThis.localStorage?.getItem(THEME_STORAGE_KEY);
        return isThemePreference(value) ? value : 'system';
    } catch {
        return 'system';
    }
}

/** Writes the choice; storage that throws keeps the choice in memory only, with no error shown. */
export function writeStoredThemePreference(preference: ThemePreference) {
    try {
        globalThis.localStorage?.setItem(THEME_STORAGE_KEY, preference);
    } catch {
        // Private modes, blocked storage or a full quota: the in-memory choice stays active.
    }
}

const SYSTEM_DARK_QUERY = '(prefers-color-scheme: dark)';
/** `matchMedia` or `prefers-color-scheme` unsupported → light. */
export function systemDarkQuery(): MediaQueryList | undefined {
    try {
        return typeof globalThis.matchMedia === 'function' ? globalThis.matchMedia(SYSTEM_DARK_QUERY) : undefined;
    } catch {
        return undefined;
    }
}
export const readSystemScheme = (): ThemeScheme => (systemDarkQuery()?.matches ? 'dark' : 'light');

const themeLabel = (value: ThemePreference | ThemeScheme) => `${value[0].toUpperCase()}${value.slice(1)}`;
/** Accessible name of the Theme button: "Theme: System (currently Dark)", "Theme: Light", "Theme: Dark". */
export const themeButtonLabel = (preference: ThemePreference, systemScheme: ThemeScheme) =>
    `Theme: ${themeLabel(preference)}${preference === 'system' ? ` (currently ${themeLabel(systemScheme)})` : ''}`;
/** The single polite announcement after a change: "Theme set to Dark." / "Theme set to System (Dark)." */
export const themeAnnouncement = (preference: ThemePreference, systemScheme: ThemeScheme) =>
    `Theme set to ${themeLabel(preference)}${preference === 'system' ? ` (${themeLabel(systemScheme)})` : ''}.`;
/** The System item's caption: "Matches your device (Dark now)". */
export const themeSystemCaption = (systemScheme: ThemeScheme) => `Matches your device (${themeLabel(systemScheme)} now)`;

/**
 * Applies the effective scheme to the root element, the same attribute and `color-scheme` the
 * first-paint script in `index.html` sets. Colour transitions are suspended for the switch so
 * nothing animates or fades (DESIGN.md › Rendering, reloads and other tabs).
 */
export function applyThemeScheme(scheme: ThemeScheme) {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    if (root.dataset.colorScheme === scheme) {
        root.style.colorScheme = scheme;
        return;
    }
    root.classList.add('cm-theme-transition');
    root.dataset.colorScheme = scheme;
    root.style.colorScheme = scheme;
    const nextFrame = globalThis.requestAnimationFrame ?? ((callback: FrameRequestCallback) => globalThis.setTimeout(callback, 0));
    nextFrame(() => nextFrame(() => root.classList.remove('cm-theme-transition')));
}

/** Semantic families. Text on a tint is always primary; `strong` is for glyphs and small text. */
export type Tone = 'neutral' | 'sky' | 'mint' | 'butter' | 'lilac' | 'rose';
export const tones: Record<Tone, { tint: string; soft: string; strong: string; text: string }> = {
    neutral: roles.tone.neutral,
    sky: roles.tone.sky,
    mint: roles.tone.mint,
    butter: roles.tone.butter,
    lilac: roles.tone.lilac,
    rose: roles.tone.rose,
};

export const radius = { xs: 8, sm: 12, md: 16, lg: 24, xl: 32, pill: 999 } as const;

export const shadow = {
    hover: roleVar('shadow.hover'),
    raised: roleVar('shadow.raised'),
    /** Selected segment inside a segmented control: lifts the white pill off its fill-150 track. */
    control: roleVar('shadow.control'),
    overlay: roleVar('shadow.overlay'),
    scrim: roles.scrim,
} as const;

/**
 * Button and control roles (DESIGN.md › Buttons). Graphite (the existing chrome-raised value) is the
 * one fill for primary actions and chosen controls, so controls read apart from the ink frame.
 * Secondary controls are neutral outlined surfaces; no new colour is introduced.
 */
export const control = {
    primary: roles.control.primary.background,
    primaryHover: roles.control.primary.hover,
    /** Neutral outlined surface for secondary buttons, circles and overflow actions. */
    secondary: {
        backgroundColor: roles.control.secondary.background, color: roles.text.primary, border: `1px solid ${roles.control.secondary.border}`, boxSizing: 'border-box',
        '&:hover': { backgroundColor: roles.control.secondary.hover, borderColor: roles.control.secondary.border },
        '&.Mui-disabled': { backgroundColor: roles.control.secondary.background, color: roles.control.disabled.text, borderColor: roles.control.secondary.border },
    },
} as const;

/** The dashed path line (Path Spine, prerequisite edges, brand path): 2 px, 4 px dash, 6 px gap. */
export const pathLine = { width: 2, dash: 4, gap: 6, dasharray: '4 6' } as const;

export const motion = {
    fast: 120, standard: 200, panel: 280, reach: 240, signature: 400,
    easeIn: 'cubic-bezier(0.2, 0, 0, 1)', easeOut: 'cubic-bezier(0.4, 0, 1, 1)',
} as const;

/** Shell geometry. Kept under the historical export name used by layouts. */
export const presentationTokens = {
    topBarHeight: 80, topBarHeightCompact: 64, canvasInset: 8,
    asideWidth: 360, asideWidthWide: 400, outlineWidth: 280, readingWidth: 720, contentMax: 1440,
    drawerWidth: 304, reachWidth: 88, reachHeight: 14,
    /**
     * Logo mark height in the top bars (64 px bar below 600 px, 80 px from 600 px); the wordmark is
     * 0.625 × this. One fixed step below 390 px (`narrow`) keeps the narrowest bars from scrolling.
     */
    brandMark: { narrow: 32, xs: 36, sm: 40 },
    pagePadding: { xs: 2, sm: 3, md: 4, lg: 5 },
} as const;

/**
 * Typography roles (DESIGN.md › Typography). Exactly one UI typeface, one code
 * typeface and one wordmark typeface are bundled; everything else is a generic fallback.
 * - UI: Google Sans Flex (variable wght, Latin + Latin Extended + Vietnamese).
 * - Code: JetBrains Mono (variable wght) for code, programming examples and technical output.
 * - Brand: Poppins ExtraBold 800, only for the "CodeMastery" wordmark.
 */
export const fontStack = '"Google Sans Flex", ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif';
export const codeFontStack = '"JetBrains Mono", ui-monospace, monospace';
export const brandFontStack = '"CodeMastery Wordmark", "Google Sans Flex", ui-sans-serif, system-ui, sans-serif';
const compact = '@media (max-width:599.95px)';
// Google Fonts subset ranges (identical for both variable families).
const LATIN = 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD';
const LATIN_EXT = 'U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C4,U+2113,U+2C60-2C7F,U+A720-A7FF';
const VIETNAMESE = 'U+0102-0103,U+0110-0111,U+0128-0129,U+0168-0169,U+01A0-01A1,U+01AF-01B0,U+0300-0301,U+0303-0304,U+0308-0309,U+0323,U+0329,U+1EA0-1EF9,U+20AB';
export const focusRing = { outline: `2px solid ${roles.focus.ring}`, outlineOffset: 2 } as const;
export const focusRingOnChrome = { outline: `2px solid ${roles.focus.onChrome}`, outlineOffset: 2 } as const;

interface SemanticPalette {
    bg: Record<'frame' | 'canvas' | 'surface' | 'fill' | 'raised' | 'hover' | 'rowHover' | 'field' | 'fieldDisabled' | 'selected' | 'inverse' | 'code' | 'courseMark', string>;
    text: Record<'primary' | 'strongSecondary' | 'secondary' | 'tertiary' | 'disabled' | 'onChrome' | 'onChromeMuted' | 'inverse' | 'code' | 'onStrong', string>;
    line: Record<'hairline' | 'strong' | 'code' | 'floating', string>;
    accent: Record<'link' | 'linkHover' | 'linkOnTint' | 'path' | 'progress' | 'caret', string>;
    focus: Record<'ring' | 'onChrome', string>;
    control: {
        primary: Record<'background' | 'hover' | 'text', string>;
        secondary: Record<'background' | 'border' | 'hover', string>;
        onChrome: Record<'background' | 'hover' | 'text', string>;
        danger: Record<'background' | 'hover' | 'text', string>;
        disabled: Record<'background' | 'text', string>;
    };
    tone: Record<Tone, Record<'tint' | 'soft' | 'strong' | 'text', string>>;
    data: Record<'mastered' | 'learning' | 'weak' | 'unknown', string>;
    chrome: Record<'raised' | 'hover' | 'muted', string>;
    brand: Record<'identity' | 'courseMark' | 'badge', string>;
    selection: Record<'background' | 'text', string>;
    scrim: string;
    shadow: Record<'hover' | 'raised' | 'control' | 'overlay', string>;
    skeleton: Record<'base' | 'highlight', string>;
}

declare module '@mui/material/styles' {
    interface CssThemeVariables { enabled: true }
    interface Palette { ink: Palette['primary']; onChrome: Palette['primary']; roles: SemanticPalette }
    interface PaletteOptions { ink?: PaletteOptions['primary']; onChrome?: PaletteOptions['primary']; roles?: SemanticPalette }
    interface TypographyVariants { display: CSSProperties; stat: CSSProperties; reading: CSSProperties; code: CSSProperties }
    // `display` changes size at 600 px, so its options take MUI's style type (media keys allowed).
    interface TypographyVariantsOptions { display?: TypographyStyle; stat?: CSSProperties; reading?: CSSProperties; code?: CSSProperties }
    interface Theme { motion: { reducedMotion: 'system' | 'always' | 'never' } }
    interface ThemeOptions { motion?: { reducedMotion?: 'system' | 'always' | 'never' } }
}
declare module '@mui/material/Typography' {
    interface TypographyPropsVariantOverrides { display: true; stat: true; reading: true; code: true }
}
declare module '@mui/material/Button' { interface ButtonPropsColorOverrides { ink: true; onChrome: true } }
declare module '@mui/material/IconButton' { interface IconButtonPropsColorOverrides { ink: true } }
declare module '@mui/material/LinearProgress' { interface LinearProgressPropsColorOverrides { ink: true } }

/** Self-hosted faces (Fontsource builds of the Google Fonts releases), subset like the Google Fonts API so a
 * browser fetches only the files its text needs. Licences: public/*-OFL.txt (SIL OFL 1.1). */
const fontFaces = [
    ...([[uiLatin, LATIN], [uiLatinExt, LATIN_EXT], [uiVietnamese, VIETNAMESE]] as const).map(([url, unicodeRange]) => ({
        fontFamily: 'Google Sans Flex', fontStyle: 'normal', fontWeight: '100 900', fontDisplay: 'swap', src: `url("${url}") format("woff2")`, unicodeRange })),
    ...([[codeLatin, LATIN], [codeLatinExt, LATIN_EXT], [codeVietnamese, VIETNAMESE]] as const).map(([url, unicodeRange]) => ({
        fontFamily: 'JetBrains Mono', fontStyle: 'normal', fontWeight: '100 800', fontDisplay: 'swap', src: `url("${url}") format("woff2")`, unicodeRange })),
    // The wordmark is Latin only: one 800 file under its own family name, so no other text can pick it up.
    { fontFamily: 'CodeMastery Wordmark', fontStyle: 'normal', fontWeight: 800, fontDisplay: 'swap', src: `url("${brandLatin}") format("woff2")`, unicodeRange: LATIN },
];

const paletteFor = (scheme: ThemeScheme) => {
    const dark = scheme === 'dark';
    const tone = dark ? {
        neutral: { tint: darkColor.graphite850, soft: darkColor.graphite800, strong: darkColor.mist200, text: darkColor.mist50 },
        // `text` is the family's coloured text (light: the strong text step); on dark grounds that is the strong colour.
        sky: { tint: darkColor.sky900, soft: darkColor.sky950, strong: darkColor.brand300, text: darkColor.brand300 },
        mint: { tint: darkColor.mint900, soft: darkColor.mint950, strong: darkColor.mint300, text: darkColor.mint300 },
        butter: { tint: darkColor.butter900, soft: darkColor.butter950, strong: darkColor.butter300, text: darkColor.butter300 },
        lilac: { tint: darkColor.lilac900, soft: darkColor.lilac950, strong: darkColor.lilac300, text: darkColor.lilac300 },
        rose: { tint: darkColor.rose900, soft: darkColor.rose950, strong: darkColor.rose300, text: darkColor.rose300 },
    } : {
        neutral: { tint: color.fill150, soft: color.surface, strong: color.ink700, text: color.ink900 },
        sky: { tint: color.sky100, soft: color.sky50, strong: color.brand600, text: color.brand700 },
        mint: { tint: color.mint100, soft: color.mint50, strong: color.mint700, text: color.mint700 },
        butter: { tint: color.butter100, soft: color.butter50, strong: color.butter800, text: color.butter800 },
        lilac: { tint: color.lilac100, soft: color.lilac50, strong: color.lilac700, text: color.lilac700 },
        rose: { tint: color.rose100, soft: color.rose50, strong: color.rose700, text: color.rose700 },
    };
    const roles: SemanticPalette = {
        bg: {
            frame: color.ink950,
            canvas: dark ? darkColor.graphite900 : color.canvas100,
            surface: dark ? darkColor.graphite800 : color.surface,
            fill: dark ? darkColor.graphite850 : color.fill150,
            raised: dark ? darkColor.graphite700 : color.surface,
            hover: dark ? darkColor.graphite750 : color.fill150,
            // Table and list rows hover on the canvas value in light (DESIGN.md › bg.hover).
            rowHover: dark ? darkColor.graphite750 : color.canvas100,
            field: dark ? darkColor.graphite850 : color.surface,
            fieldDisabled: dark ? darkColor.graphite900 : color.fill150,
            selected: dark ? darkColor.brand900 : color.brand100,
            inverse: dark ? darkColor.mist100 : color.ink950,
            code: dark ? darkColor.graphite980 : color.ink950,
            // Course-mark container: today's canvas value in light, bg.fill in dark (Dark-scheme surface rules).
            courseMark: dark ? darkColor.graphite850 : color.canvas100,
        },
        text: {
            primary: dark ? darkColor.mist50 : color.ink900,
            strongSecondary: dark ? darkColor.mist200 : color.ink700,
            secondary: dark ? darkColor.mist200 : color.ink600,
            tertiary: dark ? darkColor.mist300 : color.ink500,
            disabled: dark ? darkColor.mist500 : color.ink500,
            onChrome: color.surface,
            onChromeMuted: color.chromeMuted,
            inverse: dark ? color.ink900 : color.surface,
            code: dark ? darkColor.mist100 : color.fill150,
            // Glyphs and letters on a strong family or accent fill (check in a completed node, selected option letter).
            onStrong: dark ? color.ink900 : color.surface,
        },
        line: {
            hairline: dark ? darkColor.graphite600 : color.line200,
            strong: dark ? darkColor.graphite400 : color.ink400,
            code: dark ? darkColor.graphite600 : 'transparent',
            // Hairline outline of floating surfaces: the documented dark exception to Flat-At-Rest; none in light.
            floating: dark ? darkColor.graphite600 : 'transparent',
        },
        accent: {
            link: dark ? darkColor.brand300 : color.brand600,
            linkHover: dark ? darkColor.brand200 : color.brand700,
            linkOnTint: dark ? darkColor.brand300 : color.brand700,
            path: color.brand400,
            progress: dark ? darkColor.brand300 : color.brand600,
            caret: dark ? darkColor.brand300 : color.brand600,
        },
        focus: { ring: dark ? color.focusOnChrome : color.brand600, onChrome: color.focusOnChrome },
        control: {
            primary: {
                background: dark ? darkColor.mist50 : color.chromeRaised,
                hover: dark ? color.surface : color.chromeHover,
                text: dark ? color.ink900 : color.surface,
            },
            secondary: {
                background: dark ? darkColor.graphite800 : color.surface,
                border: dark ? darkColor.graphite600 : color.line200,
                hover: dark ? darkColor.graphite750 : color.fill150,
            },
            onChrome: { background: color.surface, hover: color.fill150, text: color.ink950 },
            danger: {
                background: dark ? darkColor.rose500 : color.rose700,
                hover: dark ? darkColor.rose700 : color.rose800,
                text: color.surface,
            },
            disabled: {
                background: dark ? darkColor.graphite850 : color.fill150,
                text: dark ? darkColor.mist500 : color.ink500,
            },
        },
        tone,
        data: dark ? {
            mastered: darkColor.dataMastered, learning: darkColor.dataLearning, weak: darkColor.dataWeak, unknown: darkColor.dataUnknown,
        } : {
            mastered: color.dataMastered, learning: color.dataLearning, weak: color.dataWeak, unknown: color.dataUnknown,
        },
        chrome: { raised: color.chromeRaised, hover: color.chromeHover, muted: color.chromeMuted },
        // Identity blue and the notification count sit on the frame, so they are shared by both schemes.
        brand: { identity: color.brand700, courseMark: dark ? darkColor.brand300 : color.brand700, badge: color.brand600 },
        selection: {
            background: dark ? darkColor.brand800 : color.brand100,
            text: dark ? darkColor.mist50 : color.ink900,
        },
        scrim: dark ? 'rgba(0, 0, 0, 0.64)' : 'rgba(10, 11, 15, 0.48)',
        shadow: dark ? {
            hover: '0 6px 20px rgba(0, 0, 0, 0.40)',
            raised: '0 12px 32px rgba(0, 0, 0, 0.55)',
            control: '0 2px 8px rgba(0, 0, 0, 0.45)',
            overlay: '0 24px 64px rgba(0, 0, 0, 0.65)',
        } : {
            hover: '0 6px 20px rgba(10, 11, 15, 0.08)',
            raised: '0 12px 32px rgba(10, 11, 15, 0.12)',
            control: '0 2px 8px rgba(10, 11, 15, 0.10)',
            overlay: '0 24px 64px rgba(10, 11, 15, 0.20)',
        },
        skeleton: dark ? { base: darkColor.graphite850, highlight: darkColor.graphite750 } : { base: color.fill150, highlight: color.canvas100 },
    };
    // MUI palette families (r12 Theme mapping): `main` = strong, `light` = tint, `dark` = strong text.
    // Labels on a strong fill stay white in light (unchanged) and take ink-900 on the lighter dark-scheme fills.
    const family = (name: Exclude<Tone, 'neutral'>) => ({
        main: tone[name].strong, light: tone[name].tint, dark: tone[name].text, contrastText: roles.text.onStrong,
    });
    return {
        mode: scheme,
        primary: {
            main: roles.accent.link, dark: roles.accent.linkHover, light: color.brand400, contrastText: roles.text.onStrong,
        },
        ink: {
            main: roles.control.primary.background, dark: roles.control.primary.hover,
            light: dark ? darkColor.mist50 : color.ink700, contrastText: roles.control.primary.text,
        },
        onChrome: { main: color.surface, dark: color.fill150, light: color.surface, contrastText: color.ink950 },
        secondary: family('lilac'),
        success: family('mint'),
        warning: family('butter'),
        // Dark `error.main` is rose-300 for text; the destructive fill (rose-500) comes from control.danger.
        error: family('rose'),
        info: family('sky'),
        background: { default: roles.bg.canvas, paper: roles.bg.surface },
        text: { primary: roles.text.primary, secondary: roles.text.secondary, disabled: roles.text.disabled },
        divider: roles.line.hairline,
        action: {
            hover: roles.bg.hover, selected: roles.bg.selected, focus: roles.bg.selected,
            disabledBackground: roles.control.disabled.background, disabled: roles.control.disabled.text,
        },
        roles,
    };
};

export const themeRoles = {
    light: paletteFor('light').roles,
    dark: paletteFor('dark').roles,
};

/** The light scheme's CSS variables as MUI emits them for `:root, [data-color-scheme="light"]`. */
function lightSchemeVariables(theme: unknown): Record<string, string> {
    const sheets = (theme as { generateStyleSheets?: () => Array<Record<string, Record<string, string>>> }).generateStyleSheets?.() ?? [];
    const light = sheets.find((sheet) => Object.keys(sheet).some((selector) => selector.includes('[data-color-scheme="light"]')));
    return light ? Object.values(light)[0] : {};
}

export const appTheme = createTheme({
    cssVariables: { colorSchemeSelector: '[data-color-scheme="%s"]' },
    defaultColorScheme: 'light',
    colorSchemes: {
        light: { palette: paletteFor('light') },
        dark: { palette: paletteFor('dark') },
    },
    spacing: 8,
    // Covers MUI's JavaScript transition lifecycles and scrolling as well as CSS.
    motion: { reducedMotion: 'system' },
    shape: { borderRadius: radius.md },
    typography: {
        // Google Sans Flex 4.005 (Google Fonts v23 via @fontsource-variable/google-sans-flex 5.3.2), SIL OFL 1.1.
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
        // Display: the landing hero only (Persuade surface). Changes at 600 px like every other step.
        display: { fontFamily: fontStack, fontSize: '3.5rem', fontWeight: 600, lineHeight: 1.05, letterSpacing: '-0.03em', textWrap: 'balance', [compact]: { fontSize: '2.5rem' } },
        stat: { fontFamily: fontStack, fontSize: '2.5rem', fontWeight: 600, lineHeight: 1, letterSpacing: '-0.03em', fontVariantNumeric: 'tabular-nums lining-nums' },
        reading: { fontFamily: fontStack, fontSize: '1.0625rem', fontWeight: 400, lineHeight: 1.7, textWrap: 'pretty' },
        code: { fontFamily: codeFontStack, fontSize: '0.9375rem', fontWeight: 400, lineHeight: 1.6 },
    },
    components: {
        // An array: emotion keeps only the last value of a repeated '@font-face' key, so each face is its own entry.
        MuiCssBaseline: { styleOverrides: (theme) => [...fontFaces.map((face) => ({ '@font-face': face })), {
            // The page scrollbar sits on the ink frame; it keeps the frame palette in both schemes.
            html: { WebkitTextSizeAdjust: '100%', colorScheme: 'light', scrollbarColor: `${color.ink600} ${color.ink950}` },
            ':root[data-color-scheme="dark"]': { colorScheme: 'dark' },
            body: { margin: 0, backgroundColor: roles.bg.frame, color: roles.text.primary, caretColor: roles.accent.caret },
            '::selection': { backgroundColor: roles.selection.background, color: roles.selection.text },
            ':where(:focus-visible)': focusRing,
            'html.cm-theme-transition, html.cm-theme-transition *, html.cm-theme-transition *::before, html.cm-theme-transition *::after': { transition: 'none !important' },
            // Inputs show focus through their outline border.
            ':where(.MuiInputBase-input:focus-visible)': { outline: 'none' },
            // Programmatic heading focus announces the route without framing static text.
            ':where(h1[tabindex="-1"], main[tabindex="-1"], [data-focus-target][tabindex="-1"]):focus': { outline: 'none' },
            'code, kbd, samp, pre': { fontFamily: codeFontStack },
            '.cm-tabular': { fontVariantNumeric: 'tabular-nums lining-nums' },
            '.cm-visually-hidden': { position: 'absolute', width: 1, height: 1, padding: 0, margin: -1, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap', border: 0 },
            '@keyframes cm-shimmer': { from: { backgroundPosition: '100% 0' }, to: { backgroundPosition: '-100% 0' } },
            // Hairlines take line.strong and tertiary text takes text.secondary, in both schemes (Fallbacks and special modes).
            '@media (prefers-contrast: more)': {
                ':root[data-color-scheme="light"]': { '--mui-palette-roles-line-hairline': color.ink400, '--mui-palette-divider': color.ink400, '--mui-palette-roles-text-tertiary': color.ink600 },
                ':root[data-color-scheme="dark"]': { '--mui-palette-roles-line-hairline': darkColor.graphite400, '--mui-palette-divider': darkColor.graphite400, '--mui-palette-roles-text-tertiary': darkColor.mist200 },
            },
            // System colours stay in charge: tint-drawn boundaries get a real border, focus uses Highlight, glyphs currentColor.
            '@media (forced-colors: active)': {
                ':where(:focus-visible), .Mui-focusVisible': { outlineColor: 'Highlight !important' },
                '.MuiAlert-root, .MuiChip-root, .MuiTooltip-tooltip, [data-tone]': { border: '1px solid CanvasText' },
                // Floating surfaces (menus, popovers, dialogs, the Theme menu) draw their edge with a shadow, which
                // forced colours removes; an outline gives them a real edge without changing their layout.
                '.MuiPopover-paper, .MuiDialog-paper, .MuiDrawer-paper, .MuiPopper-root > .MuiPaper-root': { outline: '1px solid CanvasText' },
                '[role="radio"][aria-checked="true"]::before': { border: '2px solid Highlight' },
                svg: { color: 'currentColor' },
            },
            // Printed output always uses the light scheme: the light variables win over the dark selector, and
            // `!important` lets color-scheme beat the inline value the first-paint script and applyThemeScheme set.
            '@media print': {
                ':root[data-color-scheme]': { ...lightSchemeVariables(theme), colorScheme: 'light !important' },
            },
            '@media (prefers-reduced-motion: reduce)': {
                '*, *::before, *::after': {
                    animationDuration: '0.01ms !important', animationIterationCount: '1 !important',
                    transitionDuration: '0.01ms !important', scrollBehavior: 'auto !important',
                },
            },
        }] },
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
                    '&.Mui-disabled': { backgroundColor: roles.control.disabled.background, color: roles.control.disabled.text },
                    variants: [
                        // Secondary: neutral outlined surface (white, line-200 border, ink-900 label).
                        { props: { variant: 'outlined' }, style: { ...control.secondary, paddingInline: 20 } },
                        // Tertiary: no fill, no border; Logo Blue label like a link, underline on hover.
                        { props: { variant: 'text' }, style: { color: roles.accent.link, paddingInline: 12, '&:hover': { backgroundColor: 'transparent', color: roles.accent.linkHover, textDecoration: 'underline', textUnderlineOffset: 3 } } },
                        { props: { variant: 'text', color: 'inherit' }, style: { color: 'inherit' } },
                        { props: { variant: 'text', color: 'error' }, style: { color: roles.tone.rose.strong } },
                        { props: { variant: 'text' }, style: { '&.Mui-disabled': { backgroundColor: 'transparent' } } },
                        // Primary: graphite fill, white label; one per region.
                        { props: { variant: 'contained', color: 'ink' }, style: { backgroundColor: control.primary, '&:hover': { backgroundColor: control.primaryHover } } },
                        { props: { variant: 'contained', color: 'onChrome' }, style: { backgroundColor: roles.control.onChrome.background, color: roles.control.onChrome.text, '&:hover': { backgroundColor: roles.control.onChrome.hover } } },
                        { props: { variant: 'outlined', color: 'onChrome' }, style: { border: 0, backgroundColor: roles.chrome.raised, color: roles.text.onChrome, '&:hover': { border: 0, backgroundColor: roles.chrome.hover } } },
                        { props: { variant: 'contained', color: 'error' }, style: { backgroundColor: roles.control.danger.background, color: roles.control.danger.text, '&:hover': { backgroundColor: roles.control.danger.hover } } },
                    ],
                },
                sizeSmall: { minHeight: 40, paddingInline: 16, fontSize: '0.9375rem' },
                startIcon: { marginLeft: -4, marginRight: 0 },
                endIcon: { marginRight: -4, marginLeft: 0 },
            },
        },
        MuiIconButton: { styleOverrides: { root: {
            width: 44, height: 44, borderRadius: radius.pill, color: 'inherit',
            '&:hover': { backgroundColor: roles.bg.hover },
            '&.Mui-disabled': { color: roles.text.disabled },
        } } },
        MuiTextField: { defaultProps: { slotProps: { inputLabel: { shrink: true } } } },
        MuiFormControl: { styleOverrides: { root: { '& > .MuiInputLabel-root + .MuiInputBase-root': { marginTop: 0 } } } },
        MuiInputLabel: { defaultProps: { shrink: true }, styleOverrides: {
            root: { position: 'relative', transform: 'none', marginBottom: 8, color: roles.text.primary, fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.2,
                whiteSpace: 'normal', overflow: 'visible', maxWidth: '100%',
                '&.Mui-focused': { color: roles.text.primary }, '&.Mui-error': { color: roles.text.primary }, '&.Mui-disabled': { color: roles.text.secondary } },
            // DESIGN.md › Inputs: required fields say "(required)", not an asterisk alone. The input keeps its required attribute.
            asterisk: { fontSize: 0, '&::after': { content: '" (required)"', fontSize: '0.8125rem', fontWeight: 500, color: roles.text.secondary } },
        } },
        MuiOutlinedInput: {
            defaultProps: { notched: false },
            styleOverrides: {
                root: {
                    borderRadius: radius.md, backgroundColor: roles.bg.field, fontSize: '1rem', lineHeight: 1.5, minHeight: 52,
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: roles.line.strong, borderWidth: 1.5, top: 0, '& legend': { display: 'none' } },
                    '&:hover:not(.Mui-disabled):not(.Mui-focused):not(.Mui-error) .MuiOutlinedInput-notchedOutline': { borderColor: roles.text.strongSecondary },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: roles.focus.ring, borderWidth: 2 },
                    '&.Mui-error .MuiOutlinedInput-notchedOutline': { borderColor: roles.tone.rose.strong, borderWidth: 2 },
                    '&.Mui-disabled': { backgroundColor: roles.bg.fieldDisabled },
                    variants: [{ props: { hiddenLabel: true }, style: {
                        borderRadius: radius.pill, backgroundColor: roles.bg.fill,
                        '& .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent', borderWidth: 2 },
                        '&:hover:not(.Mui-disabled):not(.Mui-focused) .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
                        '&.Mui-focused': { backgroundColor: roles.bg.field },
                        '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: roles.focus.ring },
                    } }],
                },
                input: { padding: '14px 16px', '&::placeholder': { color: roles.text.secondary, opacity: 1 } },
                multiline: { padding: 0 },
            },
        },
        MuiFormHelperText: { styleOverrides: { root: { marginInline: 0, marginTop: 8, fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4, color: roles.text.secondary,
            '&.Mui-error': { color: roles.tone.rose.strong } } } },
        MuiSelect: { styleOverrides: { icon: { color: roles.text.strongSecondary, right: 14 } } },
        MuiMenu: { styleOverrides: { paper: { backgroundColor: roles.bg.raised, borderRadius: radius.lg, boxShadow: shadow.raised, marginTop: 8,
            '[data-color-scheme="dark"] &': { border: `1px solid ${roles.line.hairline}` } }, list: { padding: 8 } } },
        MuiMenuItem: { styleOverrides: { root: { minHeight: 44, borderRadius: radius.sm, fontSize: '0.9375rem', gap: 12,
            '&.Mui-focusVisible, &:hover': { backgroundColor: roles.bg.selected }, '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: roles.bg.selected, fontWeight: 600 } } } },
        MuiPopover: { styleOverrides: { paper: { backgroundColor: roles.bg.raised, borderRadius: radius.lg, boxShadow: shadow.raised,
            '[data-color-scheme="dark"] &': { border: `1px solid ${roles.line.hairline}` } } } },
        MuiTooltip: { defaultProps: { enterDelay: 400, enterNextDelay: 200 }, styleOverrides: {
            tooltip: { backgroundColor: roles.bg.inverse, color: roles.text.inverse, borderRadius: radius.sm, padding: '8px 12px', fontSize: '0.8125rem', fontWeight: 500, lineHeight: 1.4, maxWidth: 280 },
            arrow: { color: roles.bg.inverse },
        } },
        MuiDialog: { styleOverrides: {
            paper: { backgroundColor: roles.bg.raised, borderRadius: radius.xl, margin: 16, width: 'calc(100% - 32px)', boxShadow: shadow.overlay, padding: 8,
                '[data-color-scheme="dark"] &': { border: `1px solid ${roles.line.hairline}` } },
            paperFullScreen: { borderRadius: 0, margin: 0, width: '100%' },
        } },
        MuiBackdrop: { styleOverrides: { root: { '&:not(.MuiBackdrop-invisible)': { backgroundColor: shadow.scrim } } } },
        MuiDialogTitle: { styleOverrides: { root: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.2, letterSpacing: '-0.015em', padding: '24px 24px 8px' } } },
        MuiDialogContent: { styleOverrides: { root: { padding: '8px 24px 16px' } } },
        MuiDialogContentText: { styleOverrides: { root: { color: roles.text.secondary, fontSize: '1rem', lineHeight: 1.55 } } },
        MuiDialogActions: { styleOverrides: { root: { padding: '8px 24px 24px', gap: 8, flexWrap: 'wrap', '& > :not(style) ~ :not(style)': { marginLeft: 0 } } } },
        MuiDrawer: { styleOverrides: { paper: { backgroundColor: roles.bg.frame, color: roles.text.onChrome, boxShadow: shadow.overlay } } },
        MuiTableCell: { styleOverrides: {
            root: { borderBottom: `1px solid ${roles.line.hairline}`, padding: '14px 16px', fontSize: '0.9375rem', lineHeight: 1.5, color: roles.text.primary },
            head: { color: roles.text.secondary, fontSize: '0.8125rem', fontWeight: 600, lineHeight: 1.4, height: 48, paddingBlock: 8, backgroundColor: roles.bg.surface },
        } },
        MuiTableRow: { styleOverrides: { root: { '&:last-child td': { borderBottom: 0 }, '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: roles.bg.selected } } } },
        MuiTableSortLabel: { styleOverrides: { root: { '&.Mui-active': { color: roles.text.primary }, '&:focus-visible': focusRing }, icon: { opacity: 0.6 } } },
        MuiLinearProgress: { styleOverrides: { root: { height: 8, borderRadius: radius.pill, backgroundColor: roles.line.hairline }, bar: { borderRadius: radius.pill, backgroundColor: roles.accent.progress } } },
        // Pulse, not a horizontal sweep: loading motion conveys state without marquee-like movement.
        // DESIGN.md › Skeletons: fill-150 blocks with a 1.2 s shimmer, a static fill with reduced motion.
        // The highlight moves through background-position inside each block; no element travels across the page.
        MuiSkeleton: { defaultProps: { animation: false }, styleOverrides: { root: {
            backgroundColor: roles.skeleton.base, borderRadius: radius.sm, transform: 'none',
            backgroundImage: `linear-gradient(90deg, ${roles.skeleton.base} 0%, ${roles.skeleton.highlight} 50%, ${roles.skeleton.base} 100%)`,
            backgroundSize: '200% 100%', backgroundRepeat: 'no-repeat', animation: 'cm-shimmer 1.2s ease-in-out infinite',
            '@media (prefers-reduced-motion: reduce)': { animation: 'none', backgroundImage: 'none' },
        } } },
        MuiAlert: {
            defaultProps: { variant: 'standard' },
            styleOverrides: {
                root: { borderRadius: radius.md, padding: '12px 16px', fontSize: '0.9375rem', lineHeight: 1.5, color: roles.text.primary, alignItems: 'flex-start',
                    variants: [
                        { props: { severity: 'success' }, style: { backgroundColor: tones.mint.tint, '& .MuiAlert-icon': { color: tones.mint.strong } } },
                        { props: { severity: 'warning' }, style: { backgroundColor: tones.butter.tint, '& .MuiAlert-icon': { color: tones.butter.strong } } },
                        { props: { severity: 'error' }, style: { backgroundColor: tones.rose.tint, '& .MuiAlert-icon': { color: tones.rose.strong } } },
                        // Information glyphs use the sky text step (brand-700 in light, brand-300 in dark), as before.
                        { props: { severity: 'info' }, style: { backgroundColor: tones.sky.tint, '& .MuiAlert-icon': { color: tones.sky.text } } },
                    ] },
                icon: { padding: '2px 0', marginRight: 12, opacity: 1 },
                message: { padding: '2px 0', overflowWrap: 'anywhere' },
                action: { paddingTop: 0, marginRight: -4, alignItems: 'center' },
            },
        },
        MuiAlertTitle: { styleOverrides: { root: { fontSize: '0.9375rem', fontWeight: 600, marginBottom: 2, marginTop: 0 } } },
        MuiSnackbarContent: { styleOverrides: { root: { backgroundColor: roles.bg.inverse, color: roles.text.inverse, borderRadius: radius.pill, padding: '6px 20px', fontSize: '1rem', boxShadow: shadow.raised } } },
        MuiToggleButtonGroup: { styleOverrides: { root: { backgroundColor: roles.bg.fill, borderRadius: radius.pill, padding: 4, gap: 4, flexWrap: 'wrap' },
            grouped: { border: 0, borderRadius: `${radius.pill}px !important`, margin: '0 !important' } } },
        MuiToggleButton: { styleOverrides: { root: {
            border: 0, minHeight: 36, paddingInline: 16, fontSize: '0.9375rem', fontWeight: 600, color: roles.text.secondary, textTransform: 'none', whiteSpace: 'nowrap',
            '&:hover': { backgroundColor: roles.line.hairline },
            '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: roles.bg.raised, color: roles.text.primary, boxShadow: shadow.control },
        } } },
        MuiChip: { styleOverrides: { root: { borderRadius: radius.pill, height: 32, fontSize: '0.9375rem', fontWeight: 600, backgroundColor: roles.bg.fill, color: roles.text.primary },
            deleteIcon: { color: roles.text.secondary, '&:hover': { color: roles.text.primary } } } },
        MuiPagination: { styleOverrides: { ul: { gap: 4 } } },
        MuiPaginationItem: { styleOverrides: { root: { minWidth: 44, height: 44, borderRadius: radius.pill, fontSize: '0.9375rem', fontWeight: 600, color: roles.text.primary,
            '&.Mui-selected, &.Mui-selected:hover': { backgroundColor: control.primary, color: roles.control.primary.text } } } },
        MuiBreadcrumbs: { styleOverrides: { root: { fontSize: '0.8125rem', fontWeight: 500, color: roles.text.secondary }, separator: { marginInline: 6 } } },
        MuiLink: { defaultProps: { underline: 'hover' }, styleOverrides: { root: { color: roles.accent.link, textUnderlineOffset: 3, '&:hover': { color: roles.accent.linkHover } } } },
        MuiCheckbox: { styleOverrides: { root: { color: roles.line.strong, '&.Mui-checked': { color: roles.accent.link } } } },
        MuiRadio: { styleOverrides: { root: { color: roles.line.strong, '&.Mui-checked': { color: roles.accent.link } } } },
        MuiSwitch: { styleOverrides: { switchBase: { '&.Mui-checked': { color: roles.control.onChrome.background, '& + .MuiSwitch-track': { backgroundColor: roles.accent.link, opacity: 1 } } },
            track: { backgroundColor: roles.line.strong, opacity: 1 } } },
        MuiAccordion: { defaultProps: { disableGutters: true }, styleOverrides: { root: { backgroundColor: 'transparent', '&::before': { display: 'none' } } } },
        MuiDivider: { styleOverrides: { root: { borderColor: roles.line.hairline } } },
    },
});
