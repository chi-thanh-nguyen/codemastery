import React from 'react';
import { Box, Paper, Typography, Button, Chip, Divider } from '@mui/material';
import { useAuth } from '../hooks/useAuth';
import { PageHeader } from '../../../shared/components/PageHeader';

export interface ProfilePageProps {
    onNavigate: (path: string) => void;
}

export const ProfilePage: React.FC<ProfilePageProps> = ({ onNavigate }) => {
    const { user, logout } = useAuth();

    const handleLogout = () => {
        logout();
        onNavigate('/login');
    };

    return (
        <Box sx={{ maxWidth: 600, mx: 'auto', py: 3 }}>
            <PageHeader
                title="User Profile"
                subtitle="Current authenticated account information"
                actions={
                    <Button variant="outlined" color="error" onClick={handleLogout}>
                        Log Out
                    </Button>
                }
            />

            <Paper sx={{ p: 3, borderRadius: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                    <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                        {user?.displayName}
                    </Typography>
                    <Chip label={user?.role} color="primary" size="small" />
                </Box>

                <Divider sx={{ my: 2 }} />

                <Box sx={{ mb: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                        User ID (UUID)
                    </Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {user?.id}
                    </Typography>
                </Box>

                <Box sx={{ mb: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                        Role Permission
                    </Typography>
                    <Typography variant="body2">
                        {user?.role} Access Rights
                    </Typography>
                </Box>

                <Box sx={{ mt: 3 }}>
                    <Button variant="contained" onClick={() => onNavigate('/')}>
                        Back to Home
                    </Button>
                </Box>
            </Paper>
        </Box>
    );
};