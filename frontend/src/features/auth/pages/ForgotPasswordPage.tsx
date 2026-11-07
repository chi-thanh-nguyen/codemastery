import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { AuthLayout } from '../../../layouts/AuthLayout';

export interface ForgotPasswordPageProps {
    /** Called with the entered email when the recovery contract (CT02) exists. */
    onSubmit: (email: string) => void;
    state: 'idle' | 'submitting' | 'sent' | 'error';
    signInLink: ReactNode;
    notice?: ReactNode;
}

/**
 * Password recovery request (US-01). Presentation only: CT02 (reset policy, link
 * delivery) is DEFERRED, so this page is not routed in production. The confirmation
 * never reveals whether an account exists.
 */
export function ForgotPasswordPage({ onSubmit, state, signInLink, notice }: ForgotPasswordPageProps) {
    const [email, setEmail] = useState(''), [error, setError] = useState<string | null>(null);
    return <AuthLayout title="Reset your password" subtitle="Enter the email address you use for CodeMastery. We'll issue a link that lets you set a new password." notice={notice}>
        {state === 'sent' ? <Box sx={{ display: 'grid', gap: 3 }}>
            <Alert severity="success" role="status">Request received. If an account matches this email address, a password reset link will be issued. The link expires after a short time.</Alert>
            <Typography>{signInLink}</Typography>
        </Box> : <Box component="form" aria-label="Reset your password" noValidate onSubmit={(event) => { event.preventDefault(); if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError('Enter a valid email address.'); return; } setError(null); onSubmit(email.trim()); }} sx={{ display: 'grid', gap: 2.5 }}>
            {state === 'error' && <Alert severity="error">We couldn't send the request. Check your connection and try again.</Alert>}
            <TextField name="email" label="Email address" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} error={!!error} helperText={error} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Sending…' : 'Send reset link'}</Button>
            <Typography sx={{ color: 'text.secondary' }}>Remembered it? {signInLink}</Typography>
        </Box>}
    </AuthLayout>;
}
