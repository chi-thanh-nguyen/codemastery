import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { PasswordChecklist } from '../components/RegisterForm';
import { PasswordVisibility } from './LoginPage';

export interface ResetPasswordPageProps {
    onSubmit: (password: string) => void;
    state: 'idle' | 'submitting' | 'done' | 'expired' | 'error';
    signInLink: ReactNode;
    requestNewLink: ReactNode;
    notice?: ReactNode;
}

/** Set a new password from an expiring link (US-01). Presentation only until CT02 is approved. Uses the approved CT01 password rules. */
export function ResetPasswordPage({ onSubmit, state, signInLink, requestNewLink, notice }: ResetPasswordPageProps) {
    const [password, setPassword] = useState(''), [confirm, setConfirm] = useState(''), [visible, setVisible] = useState(false), [errors, setErrors] = useState<Record<string, string>>({});
    if (state === 'expired') return <AuthLayout title="This link has expired" subtitle="Reset links work for a short time. Request a new one to continue." notice={notice}><Typography>{requestNewLink}</Typography></AuthLayout>;
    if (state === 'done') return <AuthLayout title="Password updated" subtitle="Sign in with your new password." notice={notice}><Alert severity="success" role="status" sx={{ mb: 3 }}>Your password was changed.</Alert><Typography>{signInLink}</Typography></AuthLayout>;
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
            <TextField name="confirmPassword" label="Confirm new password" type={visible ? 'text' : 'password'} autoComplete="new-password" required value={confirm} onChange={(event) => setConfirm(event.target.value)} error={!!errors.confirm} helperText={errors.confirm} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Saving…' : 'Set new password'}</Button>
        </Box>
    </AuthLayout>;
}
