import { Box, Paper, Typography } from '@mui/material';
import { useAuth } from '../hooks/useAuth';
import { PageHeader } from '../../../shared/components/PageHeader';
/** Login-time presentation only: no Profile resource, request, or permission claim. */
export function ProfilePage() {
    const { user } = useAuth();
    if (!user) return null;
    return <Box key={user.id} sx={{ maxWidth: 680, mx: 'auto', overflowWrap: 'anywhere' }}>
        <PageHeader title="Profile" subtitle="Account information from your current sign-in." />
        <Paper component="dl" sx={{ p: 3, m: 0, '& dt': { color: 'text.secondary', mt: 2 }, '& dd': { m: 0, mt: .5 } }}>
            <Typography component="dt">User ID</Typography><Typography component="dd">{user.id}</Typography>
            <Typography component="dt">Display name</Typography><Typography component="dd">{user.displayName}</Typography>
            <Typography component="dt">Role</Typography><Typography component="dd">{user.role}</Typography>
        </Paper>
    </Box>;
}
