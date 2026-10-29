import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

export interface PageHeaderProps { title: string; subtitle?: string; actions?: ReactNode }
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
    return <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between',
        alignItems: { xs: 'flex-start', sm: 'center' }, gap: 2, mb: 2 }}>
        <Box sx={{ minWidth: 0 }}>
            <Typography variant="h4" component="h1">{title}</Typography>
            {subtitle && <Typography variant="body2" color="text.secondary" sx={{ mt: .5, maxWidth: 560 }}>{subtitle}</Typography>}
        </Box>
        {actions && <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, flexShrink: 0 }}>{actions}</Box>}
    </Box>;
}
