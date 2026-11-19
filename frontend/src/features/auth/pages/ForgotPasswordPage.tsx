import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { requestPasswordReset } from '../api/authApi';
import { ApiError } from '../../../shared/types/apiTypes';

export interface ForgotPasswordPageProps {
    onSubmit: (email: string) => void;
    state: 'idle' | 'submitting' | 'sent' | 'error';
    signInLink: ReactNode;
    notice?: ReactNode;
    acknowledgement?: string;
}
export function ForgotPasswordPage({ onSubmit, state, signInLink, notice, acknowledgement }: ForgotPasswordPageProps) {
    const [email, setEmail] = useState(''), [error, setError] = useState<string | null>(null);
    return <AuthLayout title="Forgot password" subtitle="Enter the email address you use for CodeMastery. We'll send a link that lets you set a new password." notice={notice}>
        {state === 'sent' ? <Box sx={{ display: 'grid', gap: 3 }}>
            <Alert severity="success" role="status">
                {acknowledgement ?? 'If an account matches, a password reset link will be sent.'}
            </Alert>
            <Box>{signInLink}</Box>
        </Box> : <Box component="form" aria-label="Forgot password" noValidate onSubmit={(event) => {
            event.preventDefault();
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
                setError('Enter a valid email address.');
                return;
            }
            setError(null);
            onSubmit(email.trim());
        }} sx={{ display: 'grid', gap: 2.5 }}>
            {state === 'error' && <Alert severity="error">We couldn't send the request. Check your connection and try again.</Alert>}
            <TextField name="email" label="Email address" type="email" autoComplete="email" required fullWidth value={email} onChange={(event) => setEmail(event.target.value)} error={!!error} helperText={error} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Sending…' : 'Send reset link'}</Button>
            <Typography sx={{ color: 'text.secondary' }}>Remembered it? {signInLink}</Typography>
        </Box>}
    </AuthLayout>;
}

export function ForgotPasswordRoute() {
    const [state, setState] = useState<'idle' | 'submitting' | 'sent' | 'error'>('idle');
    const [acknowledgement, setAcknowledgement] = useState<string | undefined>(undefined);
    const work = useRef<AbortController | null>(null);
    const alive = useRef(true);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            work.current?.abort();
        };
    }, []);

    return (
        <ForgotPasswordPage
            state={state}
            acknowledgement={acknowledgement}
            signInLink={
                state === 'sent' ? (
                    <Button component={Link} to="/login" variant="outlined" fullWidth>
                        Back to sign in
                    </Button>
                ) : (
                    <TextLink component={Link} to="/login" sx={{ fontWeight: 600 }}>
                        Back to sign in
                    </TextLink>
                )
            }
            onSubmit={async (email) => {
                if (state === 'submitting') return;
                const controller = new AbortController();
                work.current?.abort();
                work.current = controller;
                setState('submitting');
                try {
                    const res = await requestPasswordReset({ email }, controller.signal);
                    if (alive.current && work.current === controller && !controller.signal.aborted) {
                        setAcknowledgement(res.message);
                        setState('sent');
                    }
                } catch (error) {
                    if (!alive.current || work.current !== controller || controller.signal.aborted || (error instanceof ApiError && error.kind === 'cancelled')) {
                        return;
                    }
                    setState('error');
                }
            }}
        />
    );
}