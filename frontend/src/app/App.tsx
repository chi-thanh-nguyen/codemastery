import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { BrowserRouter } from 'react-router';
import { ThemeProvider, CssBaseline, Box } from '@mui/material';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter, isPreviewPath } from '../routes/index';
import {
    appTheme, applyThemeScheme, isThemePreference, readStoredThemePreference, readSystemScheme, resolveThemeScheme, roles,
    systemDarkQuery, THEME_STORAGE_KEY, ThemePreferenceContext, writeStoredThemePreference,
} from './theme/theme';
import type { ThemePreference, ThemeScheme } from './theme/theme';
import { LoadingView } from '../shared/components/StatusViews';

// The design preview is a separate chunk: production journeys never load its
// fixtures, preview-only screens or the React Flow graph code.
const PreviewApp = lazy(() => import('./preview/PreviewApp'));

function PreviewFallback() {
    return <Box sx={{ minHeight: '100dvh', bgcolor: roles.bg.canvas, p: 5 }}><LoadingView message="Loading design preview" variant="tiles" /></Box>;
}

/**
 * The isolated design preview sends no request (CT14), so a document opened there restores no session. The
 * decision uses the router's own match (AUD-01): `/PREVIEW` or `/pre%76iew` render the preview too, and a
 * separate pattern that missed them let those documents restore a session.
 */
const openedAtPreview = () => isPreviewPath(globalThis.location?.pathname ?? '/');

/**
 * The device-scoped theme preference (FE34, DESIGN.md › Themes). It lives above the router and the
 * session, so signing in or out, a session ending elsewhere or another account never changes it, and
 * it involves no request. `index.html` applied the same effective scheme before the first paint.
 */
function useThemePreferenceState() {
    const [preference, setStoredPreference] = useState<ThemePreference>(readStoredThemePreference);
    const [systemScheme, setSystemScheme] = useState<ThemeScheme>(readSystemScheme);
    // The /preview document's memory-only override; null follows the stored preference.
    const [previewPreference, setPreviewPreference] = useState<ThemePreference | null>(null);
    const setPreference = useCallback((next: ThemePreference) => {
        setStoredPreference(next);
        writeStoredThemePreference(next);
    }, []);
    const effectiveScheme = resolveThemeScheme(previewPreference ?? preference, systemScheme);
    // Before paint, so a selection applies immediately without navigation, reload or remount.
    useLayoutEffect(() => { applyThemeScheme(effectiveScheme); }, [effectiveScheme]);
    useEffect(() => {
        // System follows the operating system live; Light and Dark simply ignore the value.
        const query = systemDarkQuery();
        const onSystemChange = (event: MediaQueryListEvent) => setSystemScheme(event.matches ? 'dark' : 'light');
        if (query?.addEventListener) query.addEventListener('change', onSystemChange);
        else query?.addListener?.(onSystemChange);
        // Another tab's choice applies here; a cleared or invalid value means System.
        const onStorage = (event: StorageEvent) => {
            if (event.key !== THEME_STORAGE_KEY && event.key !== null) return;
            setStoredPreference(isThemePreference(event.newValue) ? event.newValue : 'system');
        };
        window.addEventListener('storage', onStorage);
        return () => {
            if (query?.removeEventListener) query.removeEventListener('change', onSystemChange);
            else query?.removeListener?.(onSystemChange);
            window.removeEventListener('storage', onStorage);
        };
    }, []);
    return useMemo(() => ({ preference, systemScheme, setPreference, previewPreference, setPreviewPreference }),
        [preference, systemScheme, setPreference, previewPreference]);
}

export default function App() {
    const theme = useThemePreferenceState();
    // Decided once per document: App re-renders (for example on a theme change) after in-app
    // navigation must not turn a document opened at /preview into one that restores (A13).
    const [isolatedPreview] = useState(openedAtPreview);
    return <ThemePreferenceContext.Provider value={theme}>
        {/* MUI's built-in colour schemes; the preference above owns the root `data-color-scheme` and storage. */}
        <ThemeProvider theme={appTheme} defaultMode="light" storageManager={null} colorSchemeNode={null}>
            <CssBaseline />
            <BrowserRouter><AuthProvider refreshSessions restoreOnLoad={!isolatedPreview}>
                <AppRouter preview={<Suspense fallback={<PreviewFallback />}><PreviewApp /></Suspense>} />
            </AuthProvider></BrowserRouter>
        </ThemeProvider>
    </ThemePreferenceContext.Provider>;
}
