import { Box, Typography } from '@mui/material';
import { useAuth } from '../hooks/useAuth';
import { PageHeader } from '../../../shared/components/PageHeader';
import { Page } from '../../../layouts/DashboardLayout';
import { Tile } from '../../../shared/components/Tile';
import { StatusPill } from '../../../shared/components/StatusPill';
import { Monogram } from '../../../shared/components/AppHeader';
import { color, codeFontStack } from '../../../app/theme/theme';

/** Login-time presentation only: no Profile resource, request, or permission claim (CT16-PROFILE P2). */
export function ProfilePage() {
    const { user } = useAuth();
    if (!user) return null;
    return <Page width="form">
        <Box key={user.id} sx={{ maxWidth: 720, overflowWrap: 'anywhere' }}>
            <PageHeader title="Profile" subtitle="Details from your current sign-in." />
            <Tile padding={{ xs: 3, sm: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2.5, mb: 4, flexWrap: 'wrap' }}>
                    <Monogram name={user.displayName} size={64} tone="brand" />
                    <Box sx={{ minWidth: 0 }}>
                        <Typography component="p" variant="h2" sx={{ overflowWrap: 'anywhere' }}>{user.displayName}</Typography>
                        <Box sx={{ mt: 1 }}><StatusPill icon="user" size="tag">{user.role}</StatusPill></Box>
                    </Box>
                </Box>
                <Box component="dl" sx={{ m: 0, display: 'grid', rowGap: 2.5, '& dt': { fontSize: '0.8125rem', fontWeight: 600, color: 'text.secondary', mb: .5 }, '& dd': { m: 0 } }}>
                    <Box><Typography component="dt">Display name</Typography><Typography component="dd">{user.displayName}</Typography></Box>
                    <Box><Typography component="dt">Role</Typography><Typography component="dd">{user.role}</Typography></Box>
                    <Box><Typography component="dt">User ID</Typography><Typography component="dd" sx={{ fontFamily: codeFontStack, fontSize: '0.9375rem', color: color.ink700 }}>{user.id}</Typography></Box>
                </Box>
            </Tile>
        </Box>
    </Page>;
}
