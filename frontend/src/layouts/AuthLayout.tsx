import type { ReactNode } from 'react';
import { useLayoutEffect, useRef } from 'react';
import { Box, Typography } from '@mui/material';
import { Link, useInRouterContext } from 'react-router';
import { color, radius } from '../app/theme/theme';
import { BrandLogo } from '../shared/components/BrandLogo';

export interface AuthLayoutProps { title?: string; subtitle?: string; notice?: ReactNode; children: ReactNode }

/** Split auth layout: ink brand panel + white form panel (DESIGN.md › S2). */
export function AuthLayout({ title = 'CodeMastery', subtitle = 'Mastery-based programming learning platform', notice, children }: AuthLayoutProps) {
    const surface = useRef<HTMLDivElement>(null);
    // A router link keeps the in-memory session; a plain anchor would reload the document.
    const routed = useInRouterContext();
    useLayoutEffect(() => {
        const element = surface.current;
        const blurInput = () => {
            const active = element?.ownerDocument.activeElement;
            if ((active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement) && element?.contains(active)) active.blur();
        };
        window.addEventListener('pagehide', blurInput);
        return () => {
            // Dismiss native AutoFill while its input is still connected.
            blurInput();
            window.removeEventListener('pagehide', blurInput);
        };
    }, []);
    return <Box ref={surface} sx={{ minHeight: '100dvh', display: 'grid', bgcolor: color.ink950, gridTemplateColumns: { xs: '1fr', md: '5fr 7fr' } }}>
        <Box sx={{ color: 'common.white', px: { xs: 3, sm: 5, lg: 8 }, py: { xs: 3, md: 6 }, display: 'flex', flexDirection: 'column', gap: 4, minHeight: { xs: 120, md: 'auto' } }}>
            <Box {...(routed ? { component: Link, to: '/' } : { component: 'a', href: import.meta.env.BASE_URL })} aria-label="CodeMastery home" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.25, color: 'inherit', textDecoration: 'none', alignSelf: 'flex-start',
                borderRadius: `${radius.sm}px`, '&:focus-visible': { outline: `2px solid ${color.focusOnChrome}`, outlineOffset: 4 } }}>
                <BrandLogo size={40} />
                <Typography component="span" aria-hidden sx={{ fontSize: '1.375rem', fontWeight: 600, letterSpacing: '-0.02em' }}>CodeMastery</Typography>
            </Box>
            <Box sx={{ display: { xs: 'none', md: 'flex' }, flexDirection: 'column', flex: 1, justifyContent: 'center', gap: 5, maxWidth: 440 }}>
                <Typography component="p" sx={{ fontSize: '2.5rem', fontWeight: 600, lineHeight: 1.1, letterSpacing: '-0.025em', textWrap: 'balance' }}>
                    Learn programming at the pace of what you actually know.
                </Typography>
                <BrandPath />
                <Typography sx={{ color: color.chromeMuted, maxWidth: '40ch' }}>
                    Courses follow one shared path. Quizzes show which skills you have mastered, and CodeMastery suggests the next step with a reason.
                </Typography>
            </Box>
        </Box>
        <Box component="main" id="main-content" tabIndex={-1} sx={{ bgcolor: color.surface, borderRadius: { xs: `${radius.lg}px ${radius.lg}px 0 0`, md: `${radius.xl}px 0 0 ${radius.xl}px` },
            display: 'flex', alignItems: 'center', justifyContent: 'center', px: { xs: 3, sm: 5 }, py: { xs: 5, md: 8 } }}>
            <Box sx={{ width: '100%', maxWidth: 440 }}>
                <Typography variant="h1">{title}</Typography>
                {subtitle && <Typography sx={{ mt: 1.5, mb: 4, color: 'text.secondary' }}>{subtitle}</Typography>}
                {notice && <Box sx={{ mb: 3 }}>{notice}</Box>}
                {children}
            </Box>
        </Box>
    </Box>;
}

/** Crisp vector echo of the logo's three linked skill nodes. Decorative. */
function BrandPath() {
    return <Box component="svg" aria-hidden viewBox="0 0 320 120" sx={{ width: '100%', maxWidth: 320, height: 'auto', display: 'block' }}>
        <path d="M24 92 H120 Q148 92 160 64 Q172 36 200 36 H296" fill="none" stroke={color.brand400} strokeWidth="2" strokeDasharray="4 6" strokeLinecap="round" />
        <circle cx="24" cy="92" r="12" fill={color.mint700} />
        <path d="m18 92 4.5 4.5L31 88" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="160" cy="64" r="14" fill={color.brand600} />
        <circle cx="160" cy="64" r="20" fill="none" stroke={color.brand600} strokeOpacity=".35" strokeWidth="4" />
        <circle cx="296" cy="36" r="11" fill="none" stroke={color.chromeMuted} strokeWidth="2" />
    </Box>;
}
