import { useEffect, useRef, useState } from 'react';
import type { ReactNode, Ref } from 'react';
import { Alert, Box, Button, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { requestPasswordReset } from '../api/authApi';
import { canonicalEmail, passwordResetRequestedMessage } from '../types/authTypes';
import { ApiError } from '../../../shared/types/apiTypes';

/** F14 failure kinds a recovery form reports; server diagnostics are never shown. */
export type RecoveryFailure = 'network' | 'server';
export const recoveryFailureOf = (error: unknown): RecoveryFailure =>
    error instanceof ApiError && error.kind === 'network' ? 'network' : 'server';
const recoveryFailureText: Record<RecoveryFailure, string> = {
    network: "We couldn't reach CodeMastery. Check your connection and try again.",
    server: 'Something went wrong on our side. Try again in a moment.',
};
/** Inline F14 alert inside a recovery form; "Try again" resubmits that form. */
export function RecoveryFailureAlert({ failure, alertRef }: { failure: RecoveryFailure; alertRef?: Ref<HTMLDivElement> }) {
    return <Alert ref={alertRef} tabIndex={-1} severity="error" action={<Button type="submit" color="inherit" size="small">Try again</Button>}>
        {recoveryFailureText[failure]}
    </Alert>;
}
const invalidEmail = 'Enter a valid ASCII email address.';

export interface ForgotPasswordPageProps {
    onSubmit: (email: string) => void;
    state: 'idle' | 'submitting' | 'sent' | 'error';
    signInLink: ReactNode;
    notice?: ReactNode;
    /** The decoded CT02 acknowledgement, shown verbatim; the preview falls back to the approved text. */
    acknowledgement?: string;
    /** Which F14 failure the error state reports (default: network). */
    failure?: RecoveryFailure;
    /** An email error returned by the request (the API's 400 or the local CT01 check). */
    emailError?: string;
}

/** F1 step 4: one email field; the acknowledgement is identical for every input (no account enumeration). */
export function ForgotPasswordPage({ onSubmit, state, signInLink, notice, acknowledgement, failure = 'network', emailError }: ForgotPasswordPageProps) {
    const [email, setEmail] = useState(''), [error, setError] = useState<string | null>(null);
    const input = useRef<HTMLInputElement>(null), alert = useRef<HTMLDivElement>(null), sent = useRef<HTMLDivElement>(null);
    useEffect(() => { if (emailError) { setError(emailError); input.current?.focus(); } }, [emailError]);
    useEffect(() => {
        if (state === 'error') alert.current?.focus();
        if (state === 'sent') sent.current?.focus();
    }, [state]);
    return <AuthLayout title="Forgot password" subtitle="Enter the email address you use for CodeMastery. We'll send a link that lets you set a new password." notice={notice}>
        {state === 'sent' ? <Box sx={{ display: 'grid', gap: 3 }}>
            <Alert ref={sent} tabIndex={-1} severity="success" role="status">{acknowledgement ?? passwordResetRequestedMessage}</Alert>
            <Box>{signInLink}</Box>
        </Box> : <Box component="form" aria-label="Forgot password" aria-busy={state === 'submitting'} noValidate onSubmit={(event) => {
            event.preventDefault();
            if (state === 'submitting') return;
            // The same CT01 canonical-email rule the API client applies; nothing invalid is sent.
            try { canonicalEmail(email); } catch { setError(invalidEmail); input.current?.focus(); return; }
            setError(null);
            onSubmit(email);
        }} sx={{ display: 'grid', gap: 2.5 }}>
            {state === 'error' && <RecoveryFailureAlert failure={failure} alertRef={alert} />}
            <TextField inputRef={input} name="email" label="Email address" type="email" autoComplete="email" required fullWidth value={email}
                onChange={(event) => setEmail(event.target.value)} error={!!error} helperText={error} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Sending…' : 'Send reset link'}</Button>
            <Typography sx={{ color: 'text.secondary' }}>Remembered it? {signInLink}</Typography>
        </Box>}
    </AuthLayout>;
}

/** `/forgot-password` (CT16.routes, anonymous): requests a reset through the CT02 client. */
export function ForgotPasswordRoute() {
    const [state, setState] = useState<ForgotPasswordPageProps['state']>('idle');
    const [acknowledgement, setAcknowledgement] = useState<string>(), [failure, setFailure] = useState<RecoveryFailure>('network');
    const [emailError, setEmailError] = useState<string>();
    const work = useRef<AbortController | null>(null);
    useEffect(() => () => work.current?.abort(), []);
    return <ForgotPasswordPage state={state} acknowledgement={acknowledgement} failure={failure} emailError={emailError}
        signInLink={state === 'sent'
            ? <Button component={Link} to="/login" variant="outlined" fullWidth>Back to sign in</Button>
            : <TextLink component={Link} to="/login" sx={{ fontWeight: 600 }}>Back to sign in</TextLink>}
        onSubmit={async (email) => {
            if (work.current) return; // one request at a time; no automatic replay
            const controller = new AbortController();
            work.current = controller;
            setState('submitting'); setEmailError(undefined);
            try {
                const result = await requestPasswordReset({ email }, controller.signal);
                setAcknowledgement(result.message); setState('sent');
            } catch (error) {
                if (controller.signal.aborted || error instanceof ApiError && error.kind === 'cancelled') return;
                if (error instanceof ApiError && (error.kind === 'validation' || error.status === 400)) {
                    setEmailError(invalidEmail); setState('idle');
                } else {
                    setFailure(recoveryFailureOf(error)); setState('error');
                }
            } finally {
                if (work.current === controller) work.current = null;
            }
        }} />;
}
