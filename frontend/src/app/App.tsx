import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { BrowserRouter } from 'react-router';
import { ThemeProvider, CssBaseline, Box } from '@mui/material';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter } from '../routes/index';
import { appTheme, applyThemeScheme, isThemePreference, resolveThemeScheme, roles, ThemePreferenceContext } from './theme/theme';
import type { ThemePreference, ThemeScheme } from './theme/theme';
import { LoadingView } from '../shared/components/StatusViews';

// The design preview is a separate chunk: production journeys never load its
// fixtures, preview-only screens or the React Flow graph code.
const PreviewApp = lazy(() => import('./preview/PreviewApp'));

function PreviewFallback() {
    return <Box sx={{ minHeight: '100dvh', bgcolor: roles.bg.canvas, p: 5 }}><LoadingView message="Loading design preview" variant="tiles" /></Box>;
}

/** The isolated design preview sends no request (CT14), so a document opened there restores no session. */
const openedAtPreview = () => /^\/preview(\/|$)/.test(globalThis.location?.pathname ?? '');
const THEME_STORAGE_KEY = 'codemastery.theme';
const systemScheme = (): ThemeScheme => {
    try {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    } catch {
        return 'light';
    }
};
const storedPreference = (): ThemePreference => {
    try {
        const value = window.localStorage.getItem(THEME_STORAGE_KEY);
        return isThemePreference(value) ? value : 'system';
    } catch {
        return 'system';
    }
};

export default function App() {
    const [preference, setPreferenceState] = useState(storedPreference);
    const [deviceScheme, setDeviceScheme] = useState(systemScheme);
    const [announcement, setAnnouncement] = useState('');
    const setPreference = useCallback((next: ThemePreference) => {
        applyThemeScheme(resolveThemeScheme(next, deviceScheme));
        setPreferenceState(next);
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, next);
        } catch {
            // The selection remains active in memory when browser storage is unavailable.
        }
    }, [deviceScheme]);
    const announce = useCallback((message: string) => setAnnouncement(message), []);
    const themeContext = useMemo(() => ({ preference, systemScheme: deviceScheme, setPreference, announce }),
        [preference, deviceScheme, setPreference, announce]);

    useEffect(() => {
        applyThemeScheme(resolveThemeScheme(preference, deviceScheme));
    }, [preference, deviceScheme]);

    useEffect(() => {
        let query: MediaQueryList | undefined;
        try {
            query = window.matchMedia('(prefers-color-scheme: dark)');
        } catch {
            query = undefined;
        }
        const onSystemChange = (event: MediaQueryListEvent) => setDeviceScheme(event.matches ? 'dark' : 'light');
        if (query?.addEventListener) query.addEventListener('change', onSystemChange);
        else query?.addListener?.(onSystemChange);
        const onStorage = (event: StorageEvent) => {
            if (event.key !== THEME_STORAGE_KEY && event.key !== null) return;
            setPreferenceState(isThemePreference(event.newValue) ? event.newValue : 'system');
        };
        const onBeforePrint = () => applyThemeScheme('light');
        const onAfterPrint = () => applyThemeScheme(resolveThemeScheme(preference, deviceScheme));
        window.addEventListener('storage', onStorage);
        window.addEventListener('beforeprint', onBeforePrint);
        window.addEventListener('afterprint', onAfterPrint);
        return () => {
            if (query?.removeEventListener) query.removeEventListener('change', onSystemChange);
            else query?.removeListener?.(onSystemChange);
            window.removeEventListener('storage', onStorage);
            window.removeEventListener('beforeprint', onBeforePrint);
            window.removeEventListener('afterprint', onAfterPrint);
        };
    }, [preference, deviceScheme]);

    return <ThemePreferenceContext.Provider value={themeContext}>
        <ThemeProvider theme={appTheme} defaultMode="light" storageManager={null} colorSchemeNode={null}>
            <CssBaseline />
            <Box role="status" aria-live="polite" aria-atomic="true" className="cm-visually-hidden">{announcement}</Box>
            <BrowserRouter><AuthProvider refreshSessions restoreOnLoad={!openedAtPreview()}>
                <AppRouter preview={<Suspense fallback={<PreviewFallback />}><PreviewApp /></Suspense>} />
            </AuthProvider></BrowserRouter>
        </ThemeProvider>
    </ThemePreferenceContext.Provider>;
}
