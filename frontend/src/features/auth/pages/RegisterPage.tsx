import React, { useState } from 'react';
import { Alert, Box, Button } from '@mui/material';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { RegisterForm } from '../components/RegisterForm';

export interface RegisterPageProps {
    onNavigate: (path: string) => void;
}

export const RegisterPage: React.FC<RegisterPageProps> = ({ onNavigate }) => {
    const [registered, setRegistered] = useState(false);

    return (
        <AuthLayout
            title="Create Account"
            subtitle="Join CodeMastery to start adaptive learning"
        >
            {registered ? (
                <Box sx={{ textAlign: 'center' }}>
                    <Alert severity="success" sx={{ mb: 3 }}>
                        Account registered successfully! You can now sign in.
                    </Alert>
                    <Button
                        variant="contained"
                        fullWidth
                        onClick={() => onNavigate('/login')}
                    >
                        Proceed to Login
                    </Button>
                </Box>
            ) : (
                <RegisterForm
                    onSuccess={() => setRegistered(true)}
                    onGoToLogin={() => onNavigate('/login')}
                />
            )}
        </AuthLayout>
    );
};