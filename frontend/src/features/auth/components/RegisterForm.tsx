import React, { useState } from 'react';
import {
    Box,
    TextField,
    Button,
    Alert,
    CircularProgress,
    Typography,
} from '@mui/material';
import { useAuth } from '../hooks/useAuth';

export interface RegisterFormProps {
    onSuccess: () => void;
    onGoToLogin: () => void;
}

export const RegisterForm: React.FC<RegisterFormProps> = ({
    onSuccess,
    onGoToLogin,
}) => {
    const { register } = useAuth();
    const [displayName, setDisplayName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        // Validation rules per OpenAPI specification
        const trimmedName = displayName.trim();
        if (!trimmedName) {
            setError('Display name is required.');
            return;
        }
        if (trimmedName.length > 100) {
            setError('Display name must not exceed 100 characters.');
            return;
        }

        const trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail || !/^\S+@\S+\.\S+$/.test(trimmedEmail)) {
            setError('Please enter a valid email address.');
            return;
        }

        // Exact contract: minimum 15 Unicode code points
        if (password.length < 15) {
            setError('Password must be at least 15 characters long.');
            return;
        }

        if (password !== confirmPassword) {
            setError('Passwords do not match.');
            return;
        }

        try {
            setLoading(true);
            await register({
                displayName: trimmedName,
                email: trimmedEmail,
                password, // Untrimmed
            });
            onSuccess();
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Registration failed. Please try again.';
            setError(message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 1 }}>
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            <TextField
                margin="normal"
                required
                fullWidth
                id="displayName"
                label="Display Name"
                name="displayName"
                autoComplete="name"
                autoFocus
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={loading}
            />

            <TextField
                margin="normal"
                required
                fullWidth
                id="email"
                label="Email Address"
                name="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
            />

            <TextField
                margin="normal"
                required
                fullWidth
                name="password"
                label="Password (min. 15 characters)"
                type="password"
                id="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
            />

            <TextField
                margin="normal"
                required
                fullWidth
                name="confirmPassword"
                label="Confirm Password"
                type="password"
                id="confirmPassword"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={loading}
            />

            <Button
                type="submit"
                fullWidth
                variant="contained"
                disabled={loading}
                sx={{ mt: 3, mb: 2, py: 1.2 }}
            >
                {loading ? <CircularProgress size={24} color="inherit" /> : 'Register'}
            </Button>

            <Box sx={{ textAlign: 'center', mt: 1 }}>
                <Typography variant="body2" color="text.secondary">
                    Already have an account?{' '}
                    <Button
                        variant="text"
                        size="small"
                        onClick={onGoToLogin}
                        disabled={loading}
                        sx={{ p: 0, minWidth: 'auto', verticalAlign: 'baseline', fontWeight: 'bold' }}
                    >
                        Sign in
                    </Button>
                </Typography>
            </Box>
        </Box>
    );
};