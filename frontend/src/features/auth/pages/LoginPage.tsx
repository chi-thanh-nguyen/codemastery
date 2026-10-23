import React, { useState } from 'react';
import {
    Box,
    TextField,
    Button,
    Alert,
    CircularProgress,
    Typography,
} from '@mui/material';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { useAuth } from '../hooks/useAuth';

export interface LoginPageProps {
    onNavigate: (path: string) => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onNavigate }) => {
    const { login } = useAuth();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        const trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail) {
            setError('Email is required.');
            return;
        }
        if (!password) {
            setError('Password is required.');
            return;
        }

        try {
            setLoading(true);
            await login({
                email: trimmedEmail,
                password, // Untrimmed
            });
            onNavigate('/profile');
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Invalid email or password.';
            setError(message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            title="Welcome Back"
            subtitle="Sign in with your CodeMastery credentials"
        >
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            <Box component="form" onSubmit={handleSubmit} noValidate>
                <TextField
                    margin="normal"
                    required
                    fullWidth
                    id="email"
                    label="Email Address"
                    name="email"
                    autoComplete="email"
                    autoFocus
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={loading}
                />

                <TextField
                    margin="normal"
                    required
                    fullWidth
                    name="password"
                    label="Password"
                    type="password"
                    id="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={loading}
                />

                <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    disabled={loading}
                    sx={{ mt: 3, mb: 2, py: 1.2 }}
                >
                    {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign In'}
                </Button>

                <Box sx={{ textAlign: 'center', mt: 1 }}>
                    <Typography variant="body2" color="text.secondary">
                        Don't have an account?{' '}
                        <Button
                            variant="text"
                            size="small"
                            onClick={() => onNavigate('/register')}
                            disabled={loading}
                            sx={{ p: 0, minWidth: 'auto', verticalAlign: 'baseline', fontWeight: 'bold' }}
                        >
                            Register here
                        </Button>
                    </Typography>
                </Box>
            </Box>
        </AuthLayout>
    );
};