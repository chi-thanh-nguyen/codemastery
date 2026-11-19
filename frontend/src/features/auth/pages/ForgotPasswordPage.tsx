import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode, Ref } from 'react';
import { Alert, Box, Button, buttonClasses, Link as TextLink, TextField, Typography } from '@mui/material';
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
/**
 * Invisible extension centred on a control (DESIGN.md › Accessibility › Targets): the hit area becomes at least
 * 44 × 44 px while the visible shape is unchanged.
 */
const targetExtension = {
    position: 'relative',
    '&::before': { content: '""', position: 'absolute', top: '50%', left: '50%', width: 'max(100%, 44px)', height: 'max(100%, 44px)', transform: 'translate(-50%, -50%)' },
} as const;
/** Inline F14 alert inside a recovery form; "Try again" resubmits that form. */
export function RecoveryFailureAlert({ failure, alertRef }: { failure: RecoveryFailure; alertRef?: Ref<HTMLDivElement> }) {
    return <Alert ref={alertRef} tabIndex={-1} severity="error" action={<Button type="submit" color="inherit" size="small" sx={targetExtension}>Try again</Button>}>
        {recoveryFailureText[failure]}
    </Alert>;
}
/**
 * The F1 error summary shown on submit while any field is in error (DESIGN.md › Accessibility › Forms). It is
 * not a live region: focus moves to the first invalid field, which announces its own linked error.
 */
export function RecoveryErrorSummary() {
    return <Alert severity="error" role="none">Please check the supplied information.</Alert>;
}
/**
 * Props for a recovery submit button. While a request is pending the button looks and acts disabled but stays
 * focusable (`aria-disabled`), so keyboard focus is not dropped to the page; activation is ignored.
 */
export function pendingSubmitProps(pending: boolean) {
    return pending ? {
        'aria-disabled': true as const, className: buttonClasses.disabled,
        onClick: (event: { preventDefault(): void }) => event.preventDefault(),
    } : {};
}
/**
 * F1 blur re-validation that cannot swallow a click. A pointer press on another control blurs the field before
 * that press completes; applying the re-validation at once (an error or the summary disappearing) would move
 * "Reset password" or "Send reset link" out from under the pointer, so the click would be lost. A blur caused by
 * a pointer is therefore applied just after the press is released (after its click); a keyboard blur at once.
 */
export function useBlurRevalidation() {
    const pressed = useRef(false);
    useEffect(() => {
        const down = () => { pressed.current = true; }, up = () => { pressed.current = false; };
        document.addEventListener('pointerdown', down, true);
        document.addEventListener('pointerup', up, true);
        document.addEventListener('pointercancel', up, true);
        return () => {
            document.removeEventListener('pointerdown', down, true);
            document.removeEventListener('pointerup', up, true);
            document.removeEventListener('pointercancel', up, true);
        };
    }, []);
    return useCallback((revalidate: () => void) => {
        if (!pressed.current) { revalidate(); return; }
        const release = () => {
            document.removeEventListener('pointerup', release, true);
            document.removeEventListener('pointercancel', release, true);
            window.setTimeout(revalidate, 0); // the click that this release completes is dispatched first
        };
        document.addEventListener('pointerup', release, true);
        document.addEventListener('pointercancel', release, true);
    }, []);
}
/** Polite, visually hidden announcement of a pending request; empty otherwise, so each request is announced once. */
export function PendingStatus({ message }: { message?: string }) {
    return <span role="status" className="cm-visually-hidden">{message}</span>;
}
const invalidEmail = 'Enter a valid ASCII email address.';
/** The CT01 canonical-email check the API client applies; nothing invalid is sent. */
const emailProblem = (value: string) => { try { canonicalEmail(value); return null; } catch { return invalidEmail; } };
/**
 * Text link of the recovery flow (DESIGN.md › Accessibility › Targets): the visible shape stays inline and
 * unchanged; an invisible 44 px tall extension centred on it makes the hit area at least 44 × 44 px
 * (each link text is wider than 44 px). It extends only vertically, so it never covers text it does not own.
 */
export const recoveryLinkSx = {
    fontWeight: 600, position: 'relative',
    '&::before': { content: '""', position: 'absolute', left: 0, right: 0, top: '50%', height: 44, transform: 'translateY(-50%)' },
} as const;

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
    const input = useRef<HTMLInputElement>(null), alert = useRef<HTMLDivElement>(null), sent = useRef<HTMLDivElement>(null), submit = useRef<HTMLButtonElement>(null);
    const pending = state === 'submitting', afterBlur = useBlurRevalidation();
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
            if (pending) return;
            const problem = emailProblem(email);
            setError(problem);
            if (problem) { input.current?.focus(); return; }
            // "Try again" leaves with its alert: keep keyboard focus on the (busy) submit button instead.
            if (document.activeElement !== input.current) submit.current?.focus();
            onSubmit(email);
        }} sx={{ display: 'grid', gap: 2.5 }}>
            {state === 'error' && <RecoveryFailureAlert failure={failure} alertRef={alert} />}
            {error && <RecoveryErrorSummary />}
            <TextField inputRef={input} name="email" label="Email address" type="email" autoComplete="email" required fullWidth value={email}
                onChange={(event) => setEmail(event.target.value)} error={!!error} helperText={error}
                // F1: a field already in error is re-validated when it loses focus.
                onBlur={() => afterBlur(() => setError((current) => current && emailProblem(email)))} />
            <Button ref={submit} type="submit" variant="contained" fullWidth {...pendingSubmitProps(pending)}>{pending ? 'Sending…' : 'Send reset link'}</Button>
            <PendingStatus message={pending ? 'Sending the reset link…' : undefined} />
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
            : <TextLink component={Link} to="/login" sx={recoveryLinkSx}>Back to sign in</TextLink>}
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
