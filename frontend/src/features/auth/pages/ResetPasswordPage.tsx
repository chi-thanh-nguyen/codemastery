import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link, useLocation, useNavigate } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { PasswordChecklist } from '../components/RegisterForm';
import { PasswordVisibility } from './LoginPage';
import { RecoveryFailureAlert, recoveryFailureOf } from './ForgotPasswordPage';
import type { RecoveryFailure } from './ForgotPasswordPage';
import { confirmPasswordReset } from '../api/authApi';
import { passwordResetCompletedMessage } from '../types/authTypes';
import { ApiError } from '../../../shared/types/apiTypes';

const passwordRule = 'Use at least 15 Unicode characters and at most 72 UTF-8 bytes.';

export interface ResetPasswordPageProps {
    onSubmit: (password: string) => void;
    state: 'idle' | 'submitting' | 'done' | 'expired' | 'error';
    signInLink: ReactNode;
    requestNewLink: ReactNode;
    notice?: ReactNode;
    /** The decoded CT02 completion message, shown verbatim; the preview falls back to the approved text. */
    successMessage?: string;
    /** Which F14 failure the error state reports (default: network). */
    failure?: RecoveryFailure;
    /** A password error returned by the request (the API's field error or the local CT01 check). */
    passwordError?: string;
}

/** F1 step 5: new password with the live CT01 checklist; success creates no session. */
export function ResetPasswordPage({ onSubmit, state, signInLink, requestNewLink, notice, successMessage, failure = 'network', passwordError }: ResetPasswordPageProps) {
    const [password, setPassword] = useState(''), [confirm, setConfirm] = useState(''), [visible, setVisible] = useState(false), [errors, setErrors] = useState<Record<string, string>>({});
    const passwordInput = useRef<HTMLInputElement>(null), confirmInput = useRef<HTMLInputElement>(null), alert = useRef<HTMLDivElement>(null);
    useEffect(() => { if (passwordError) { setErrors((current) => ({ ...current, password: passwordError })); passwordInput.current?.focus(); } }, [passwordError]);
    useEffect(() => {
        if (state === 'error') alert.current?.focus();
        // The page changes in place, so move focus to its new title.
        if (state === 'done' || state === 'expired') document.querySelector<HTMLElement>('main h1')?.focus();
    }, [state]);
    if (state === 'expired') {
        return <AuthLayout title="This reset link is invalid or has expired." subtitle="Request a new link from the sign-in page." notice={notice}>
            <Box sx={{ display: 'grid', gap: 2 }}>{requestNewLink}</Box>
        </AuthLayout>;
    }
    if (state === 'done') {
        return <AuthLayout title="Password updated" subtitle="You can now use your new password." notice={notice}>
            <Box sx={{ display: 'grid', gap: 3 }}>
                <Alert severity="success" role="status">
                    <Box>{successMessage ?? passwordResetCompletedMessage}</Box>
                    <Box sx={{ mt: 0.5 }}>Other signed-in sessions on your account have been signed out.</Box>
                </Alert>
                {signInLink}
            </Box>
        </AuthLayout>;
    }
    return <AuthLayout title="Reset password" subtitle="Choose a password you don't use anywhere else." notice={notice}>
        <Box component="form" aria-label="Reset password" aria-busy={state === 'submitting'} noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={(event) => {
            event.preventDefault();
            if (state === 'submitting') return;
            const next: Record<string, string> = {};
            if ([...password].length < 15 || new TextEncoder().encode(password).length > 72) next.password = passwordRule;
            if (password !== confirm) next.confirm = 'Passwords must match.';
            setErrors(next);
            if (next.password) passwordInput.current?.focus(); else if (next.confirm) confirmInput.current?.focus(); else onSubmit(password);
        }}>
            {state === 'error' && <RecoveryFailureAlert failure={failure} alertRef={alert} />}
            <Box><TextField inputRef={passwordInput} name="password" label="New password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth value={password} onChange={(event) => setPassword(event.target.value)}
                error={!!errors.password} helperText={errors.password ?? 'At least 15 Unicode characters, at most 72 UTF-8 bytes.'}
                slotProps={{ htmlInput: { 'aria-describedby': 'reset-password-helper-text reset-password-rules' }, input: { endAdornment: <PasswordVisibility visible={visible} onToggle={() => setVisible((value) => !value)} /> } }} id="reset-password" />
                <PasswordChecklist password={password} id="reset-password-rules" /></Box>
            <TextField inputRef={confirmInput} name="confirmPassword" label="Confirm new password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth value={confirm} onChange={(event) => setConfirm(event.target.value)} error={!!errors.confirm} helperText={errors.confirm} />
            <Button type="submit" variant="contained" fullWidth disabled={state === 'submitting'}>{state === 'submitting' ? 'Resetting…' : 'Reset password'}</Button>
        </Box>
    </AuthLayout>;
}

/** The opaque CT02 capability: 32 random bytes as 43 Base64url characters (OpenAPI `PasswordResetConfirm.token`). */
const capability = /^[A-Za-z0-9_-]{43}$/;
function capabilityFrom(hash: string): string | null {
    const token = new URLSearchParams(hash.replace(/^#/, '')).get('token');
    return token && capability.test(token) ? token : null;
}

/**
 * `/reset-password#token=…` (CT16.routes, anonymous; ADR006 §9.3). The capability is read once into
 * component memory and the history entry is replaced with `/reset-password` before paint and before
 * any request, so it never stays in the address bar, the router location, the DOM, storage or logs.
 * A reload therefore has no capability and shows the invalid-link state.
 *
 * ADR006 §9.3 Referrer-Policy: React hoists the `no-referrer` meta into the document head on the first
 * commit, before any request. Browsers apply it to the rest of this document, also after the meta is
 * removed; that only drops `Referer`. `fetch` requests (mode `cors`) still send the real `Origin`, so
 * ADR014's trusted-Origin checks on refresh and logout are unaffected.
 */
export function ResetPasswordRoute() {
    const location = useLocation(), navigate = useNavigate();
    const [token] = useState(() => capabilityFrom(location.hash));
    const [state, setState] = useState<ResetPasswordPageProps['state']>(token ? 'idle' : 'expired');
    const [successMessage, setSuccessMessage] = useState<string>(), [failure, setFailure] = useState<RecoveryFailure>('network');
    const [passwordError, setPasswordError] = useState<string>();
    const work = useRef<AbortController | null>(null);
    useLayoutEffect(() => {
        if (location.hash || location.search) navigate('/reset-password', { replace: true });
    }, [location.hash, location.search, navigate]);
    useEffect(() => () => work.current?.abort(), []);
    return <><meta name="referrer" content="no-referrer" /><ResetPasswordPage state={state} successMessage={successMessage} failure={failure} passwordError={passwordError}
        signInLink={<Button component={Link} to="/login" variant="contained" fullWidth>Sign in</Button>}
        requestNewLink={<Box sx={{ display: 'grid', gap: 1.5 }}>
            <Button component={Link} to="/forgot-password" variant="contained" fullWidth>Request a new link</Button>
            <Typography variant="body2" sx={{ textAlign: 'center', color: 'text.secondary' }}>
                <TextLink component={Link} to="/login" sx={{ fontWeight: 600 }}>Back to sign in</TextLink>
            </Typography>
        </Box>}
        onSubmit={async (password) => {
            if (!token || work.current) return; // one request at a time; no automatic replay
            const controller = new AbortController();
            work.current = controller;
            setState('submitting'); setPasswordError(undefined);
            try {
                const result = await confirmPasswordReset({ token, password }, controller.signal);
                setSuccessMessage(result.message); setState('done');
            } catch (error) {
                if (controller.signal.aborted || error instanceof ApiError && error.kind === 'cancelled') return;
                if (error instanceof ApiError && (error.kind === 'validation' || error.status === 400) && error.fields.password) {
                    // A password-rule refusal keeps the capability (the server does not consume it).
                    setPasswordError(passwordRule); setState('idle');
                } else if (error instanceof ApiError && error.status === 400) {
                    // CT02's generic 400: invalid, used, replaced or expired capability.
                    setState('expired');
                } else {
                    setFailure(recoveryFailureOf(error)); setState('error');
                }
            } finally {
                if (work.current === controller) work.current = null;
            }
        }} /></>;
}
