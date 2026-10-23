import type { ReactNode } from 'react';
import { Container, Box, Paper, Typography } from '@mui/material';
import { presentationTokens } from '../app/theme/theme';
import { PresentationIcon } from '../shared/components/PresentationIcon';

export interface AuthLayoutProps { title?: string; subtitle?: string; children: ReactNode }
export function AuthLayout({ title = 'CodeMastery', subtitle = 'Mastery-Based Programming Learning Platform', children }: AuthLayoutProps) {
    return <Box sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', p: { xs: 2, sm: 3 }, background: presentationTokens.outerGradient }}>
        <Container maxWidth="xs" disableGutters>
            <Paper sx={{ p: { xs: 2.5, sm: 4 }, boxShadow: presentationTokens.frameShadow }}>
                <Box sx={{ width: 44, height: 44, display: 'grid', placeItems: 'center', bgcolor: presentationTokens.softBlue,
                    color: 'primary.main', borderRadius: '10px', mb: 3 }}><PresentationIcon name="code" /></Box>
                <Typography variant="h4" component="h1">{title}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 3 }}>{subtitle}</Typography>
                {children}
            </Paper>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block', textAlign: 'center' }}>CodeMastery · Presentation preview</Typography>
        </Container>
    </Box>;
}
