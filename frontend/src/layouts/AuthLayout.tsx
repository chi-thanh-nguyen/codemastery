import type { ReactNode } from 'react';
import { useLayoutEffect, useRef } from 'react';
import { Container, Box, Paper, Typography } from '@mui/material';
import { presentationTokens } from '../app/theme/theme';
import { BrandLogo } from '../shared/components/BrandLogo';

export interface AuthLayoutProps { title?: string; subtitle?: string; children: ReactNode }
export function AuthLayout({ title = 'CodeMastery', subtitle = 'Mastery-Based Programming Learning Platform', children }: AuthLayoutProps) {
    const surface = useRef<HTMLDivElement>(null);
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
    return <Box ref={surface} sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', p: { xs: 2, sm: 3 }, background: presentationTokens.outerGradient }}>
        <Container maxWidth="xs" disableGutters>
            <Paper sx={{ p: { xs: 2.5, sm: 4 }, boxShadow: presentationTokens.frameShadow }}>
                <Box sx={{ mb: 3 }}><BrandLogo size={64} /></Box>
                <Typography variant="h4" component="h1">{title}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 3 }}>{subtitle}</Typography>
                {children}
            </Paper>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block', textAlign: 'center' }}>CodeMastery · Learn at your own pace</Typography>
        </Container>
    </Box>;
}
