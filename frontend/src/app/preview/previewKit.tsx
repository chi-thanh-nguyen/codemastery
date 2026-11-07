import { createContext, useCallback, useContext } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { Box, Button, Typography } from '@mui/material';
import { PresentationIcon } from '../../shared/components/PresentationIcon';
import { color, radius } from '../theme/theme';

export type Workspace = 'reference' | 'public' | 'learner' | 'instructor' | 'admin';

/** Query-parameter navigation inside the single approved `/preview` route (no new route paths). */
export function usePreviewNav() {
    const [params, setParams] = useSearchParams();
    const screen = params.get('screen') ?? 'overview';
    const item = params.get('item') ?? undefined;
    const tab = params.get('tab') ?? undefined;
    const go = useCallback((next: string, extra: Record<string, string | undefined> = {}) => {
        const search: Record<string, string> = { screen: next };
        Object.entries(extra).forEach(([key, value]) => { if (value) search[key] = value; });
        setParams(search);
    }, [setParams]);
    return { screen, item, tab, go };
}

/** Href for a preview screen (real link semantics; clicks are intercepted by callers). */
export const previewHref = (screen: string, extra: Record<string, string> = {}) => `/preview?${new URLSearchParams({ screen, ...extra }).toString()}`;

interface PreviewContextValue { notify: (message: string) => void; reset: () => void }
export const PreviewContext = createContext<PreviewContextValue>({ notify: () => undefined, reset: () => undefined });
export const usePreview = () => useContext(PreviewContext);

/** Persistent label: every preview screen says it shows sample data and sends nothing. */
export function SampleDataBanner({ tools, onIndex, onReset, compact }: { tools?: ReactNode; onIndex?: () => void; onReset?: () => void; compact?: boolean }) {
    return <Box role="note" aria-label="Design preview notice" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: { xs: 1, sm: 1.5 }, px: { xs: 2, sm: 3, md: 4, lg: 5 }, py: 1.25,
        bgcolor: color.sky100, color: color.ink900, borderBottom: `1px solid ${color.line200}`, ...(compact ? {} : { borderRadius: { xs: `${radius.lg}px ${radius.lg}px 0 0`, sm: `${radius.xl}px ${radius.xl}px 0 0` } }) }}>
        <PresentationIcon name="info" sx={{ fontSize: 20, color: color.brand700 }} />
        <Typography sx={{ fontSize: '0.9375rem', flex: '1 1 260px', minWidth: 0 }}><strong>Design preview · Sample data.</strong> Nothing here is saved or sent to CodeMastery.</Typography>
        {tools}
        {onIndex && <Button variant="text" size="small" onClick={onIndex} sx={{ color: color.brand700 }}>Preview index</Button>}
        {onReset && <Button variant="outlined" size="small" onClick={onReset} startIcon={<PresentationIcon name="refresh" />} sx={{ bgcolor: color.surface }}>Reset sample data</Button>}
    </Box>;
}

/** Note inside a screen naming the contract a preview interaction stands in for. */
export function ContractNote({ children }: { children: ReactNode }) {
    // In Google Sans Flex the "0" that defines 1ch is wider than an average glyph: 54ch measured
    // 68–77 characters per line on the preview notes (6 November 2026 typography change).
    return <Box role="note" sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', mb: 3 }}>
        <PresentationIcon name="info" sx={{ fontSize: 18, color: color.brand700, mt: .25, flexShrink: 0 }} />
        <Typography variant="body2" sx={{ color: color.ink700, maxWidth: '54ch' }}>{children}</Typography>
    </Box>;
}

/** Local delay standing in for a server round trip in preview interactions only. */
export const later = (fn: () => void, ms = 700) => window.setTimeout(fn, ms);
export const nowLabel = 'Just now';
