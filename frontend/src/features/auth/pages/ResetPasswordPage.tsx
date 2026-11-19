import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { PasswordChecklist } from '../components/RegisterForm';
import { PasswordVisibility } from './LoginPage';
import { confirmPasswordReset } from '../api/authApi';
import { ApiError } from '../../../shared/types/apiTypes';

export interface ResetPasswordPageProps {
    onSubmit: (password: string) => void;
    state: 'idle' | 'submitting' | 'done' | 'expired' | 'error';
    signInLink: ReactNode;
    requestNewLink: ReactNode;
    notice?: ReactNode;
    successMessage?: string;
}

export function ResetPasswordPage({ onSubmit, state, signInLink, requestNewLink, notice, successMessage }: ResetPasswordPageProps) {
    const [password, setPassword] = useState(''), [confirm, setConfirm] = useState(''), [visible, setVisible] = useState(false), [errors, setErrors] = useState<Record<string, string>>({});
    if (state === 'expired') {
        return <AuthLayout title="This reset link is invalid or has expired." subtitle="Request a new link from the sign-in page." notice={notice}>
            <Box sx={{ display: 'grid', gap: 2 }}>{requestNewLink}</Box>
        </AuthLayout>;
    }
    if (state === 'done') {
        return <AuthLayout title="Password updated" subtitle="You can now use your new password." notice={notice}>
            <Box sx={{ display: 'grid', gap: 3 }}>
                <Alert severity="success" role="status">
                    <Box>{successMessage ?? 'Password reset. Sign in with your new password.'}</Box>
                    <Box sx={{ mt: 0.5 }}>Other signed-in sessions on your account have been signed out.</Box>
                </Alert>
                {signInLink}
            </Box>
        </AuthLayout>;
    }
    return <AuthLayout title="Set a new password" subtitle="Choose a password you don't use anywhere else." notice={notice}>
        <Box component="form" aria-label="Set a new password" noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={(event) => {
            event.preventDefault(); const next: Record<string, string> = {};
            if ([...password].length < 15 || new TextEncoder().encode(password).length > 72) next.password = 'Use at least 15 Unicode characters and at most 72 UTF-8 bytes.';
            if (password !== confirm) next.confirm = 'Passwords must match.';
            setErrors(next); if (!Object.keys(next).length) onSubmit(password);
        }}>
            {state === 'error' && <Alert severity="error">We couldn't update your password. Try again.</Alert>}
            <Box><TextField name="password" label="New password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth value={password} onChange={(event) => setPassword(event.target.value)}
                error={!!errors.password} helperText={errors.password ?? 'At least 15 Unicode characters, at most 72 UTF-8 bytes.'}
                slotProps={{ htmlInput: { 'aria-describedby': 'reset-password-helper-text reset-password-rules' }, input: { endAdornment: <PasswordVisibility visible={visible} onToggle={() => setVisible((value) => !value)} /> } }} id="reset-password" />
                <PasswordChecklist password={password} id="reset-password-rules" /></Box>
            <TextField name="confirmPassword" label="Confirm new password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth value={confirm} onChange={(event) => setConfirm(event.target.value)} error={!!errors.confirm} helperText={errors.confirm} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Saving…' : 'Reset password'}</Button>
        </Box>
    </AuthLayout>;
}

function readFragmentToken(): string | null {
    if (typeof window === 'undefined' || !window.location.hash) return null;
    const params = new URLSearchParams(window.location.hash.slice(1));
    const token = params.get('token');
    return token && token.trim().length > 0 ? token : null;
}

export function ResetPasswordRoute() {
    const [token] = useState<string | null>(() => readFragmentToken());
    const [state, setState] = useState<'idle' | 'submitting' | 'done' | 'expired' | 'error'>(() =>
        token ? 'idle' : 'expired'
    );
    const [successMessage, setSuccessMessage] = useState<string | undefined>(undefined);
    const work = useRef<AbortController | null>(null);
    const alive = useRef(true);

    // Replace history entry before any request so token is scrubbed from URL
    useEffect(() => {
        if (typeof window !== 'undefined' && window.location.hash) {
            window.history.replaceState(null, '', '/reset-password');
        }
        alive.current = true;
        return () => {
            alive.current = false;
            work.current?.abort();
        };
    }, []);

    return (
        <ResetPasswordPage
            state={state}
            successMessage={successMessage}
            signInLink={
                <Button component={Link} to="/login" variant="contained" fullWidth>
                    Sign in
                </Button>
            }
            requestNewLink={
                <Box sx={{ display: 'grid', gap: 1.5 }}>
                    <Button component={Link} to="/forgot-password" variant="contained" fullWidth>
                        Request a new link
                    </Button>
                    <Typography variant="body2" sx={{ textAlign: 'center', color: 'text.secondary' }}>
                        <TextLink component={Link} to="/login" sx={{ fontWeight: 600 }}>
                            Back to sign in
                        </TextLink>
                    </Typography>
                </Box>
            }
            onSubmit={async (password) => {
                if (!token || state === 'submitting') return;
                const controller = new AbortController();
                work.current?.abort();
                work.current = controller;
                setState('submitting');
                try {
                    const res = await confirmPasswordReset({ token, password }, controller.signal);
                    if (alive.current && work.current === controller && !controller.signal.aborted) {
                        setSuccessMessage(res.message);
                        setState('done');
                    }
                } catch (error) {
                    if (!alive.current || work.current !== controller || controller.signal.aborted || (error instanceof ApiError && error.kind === 'cancelled')) {
                        return;
                    }
                    if (error instanceof ApiError && error.status === 400) {
                        setState('expired');
                    } else {
                        setState('error');
                    }
                }
            }}
        />
    );
}