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
/** A press: focus moves only as the default action of one of these, so every pointer-caused blur happens during one. */
const pressEvents = ['pointerdown', 'mousedown', 'touchstart'] as const;
/** The end of a press (or its abandonment); the click that a release completes is dispatched in the same task. */
const releaseEvents = ['pointerup', 'mouseup', 'touchend', 'pointercancel', 'touchcancel', 'dragend'] as const;
/** Interruptions after which no click can follow: a press whose release never arrives applies its re-validation here. */
const documentInterrupts = ['keydown', 'contextmenu', 'visibilitychange'] as const;
/**
 * F1 blur re-validation that cannot swallow a click or a tap. Re-validation can remove an error or the summary,
 * which moves "Reset password" or "Send reset link"; if that happens while the press that caused the blur is still
 * in progress, the release lands on whatever is now under the pointer and the click is lost. With a mouse, focus
 * moves on `mousedown` before `pointerup`; with touch, browsers release the pointer first and move focus on the
 * compatibility `mousedown` that follows. So no single event order is assumed: while any press is in progress
 * (pointer, mouse or touch), re-validation is queued, and the layout changes only at the first task boundary after a
 * release when no press remains, which is after that release's click. A keyboard blur applies at once. Keyboard
 * input, a context menu, the page or window losing focus, or pointer cancellation applies the queue, so an
 * interrupted press never postpones validation indefinitely. Queued work must read current values, not render-time ones.
 */
export function useBlurRevalidation() {
    const pressing = useRef(false), queue = useRef<Array<() => void>>([]), flushTimer = useRef<number | undefined>(undefined);
    useEffect(() => {
        const flush = () => {
            flushTimer.current = undefined;
            if (pressing.current) return; // a new press began first; its release flushes
            const work = queue.current;
            queue.current = [];
            work.forEach((run) => run());
        };
        const press = () => { pressing.current = true; };
        const release = () => {
            pressing.current = false;
            flushTimer.current ??= window.setTimeout(flush, 0); // a task boundary, not a delay: after this release's click
        };
        const interrupt = () => { pressing.current = false; window.clearTimeout(flushTimer.current); flush(); };
        for (const type of pressEvents) document.addEventListener(type, press, true);
        for (const type of releaseEvents) document.addEventListener(type, release, true);
        for (const type of documentInterrupts) document.addEventListener(type, interrupt, true);
        window.addEventListener('blur', interrupt);
        return () => {
            for (const type of pressEvents) document.removeEventListener(type, press, true);
            for (const type of releaseEvents) document.removeEventListener(type, release, true);
            for (const type of documentInterrupts) document.removeEventListener(type, interrupt, true);
            window.removeEventListener('blur', interrupt);
            window.clearTimeout(flushTimer.current);
            queue.current = [];
        };
    }, []);
    return useCallback((revalidate: () => void) => {
        if (pressing.current) queue.current.push(revalidate); else revalidate();
    }, []);
}
/**
 * Polite, visually hidden announcement of a pending request; empty otherwise, so each request is announced once.
 * Render it outside the `aria-busy` form: assistive technology may hold back updates inside a busy subtree.
 */
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
    /** Set when a later submit fails the local check: the F14 alert then describes an earlier attempt and is hidden. */
    const [failureSuperseded, setFailureSuperseded] = useState(false);
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
        </Box> : <><Box component="form" aria-label="Forgot password" aria-busy={state === 'submitting'} noValidate onSubmit={(event) => {
            event.preventDefault();
            if (pending) return;
            const problem = emailProblem(email);
            setError(problem);
            setFailureSuperseded(!!problem);
            if (problem) { input.current?.focus(); return; }
            // "Try again" leaves with its alert: keep keyboard focus on the (busy) submit button instead.
            if (document.activeElement !== input.current) submit.current?.focus();
            onSubmit(email);
        }} sx={{ display: 'grid', gap: 2.5 }}>
            {state === 'error' && !failureSuperseded && <RecoveryFailureAlert failure={failure} alertRef={alert} />}
            {error && <RecoveryErrorSummary />}
            <TextField inputRef={input} name="email" label="Email address" type="email" autoComplete="email" required fullWidth value={email}
                onChange={(event) => setEmail(event.target.value)} error={!!error} helperText={error}
                // F1: a field already in error is re-validated when it loses focus (reading the field's current value).
                onBlur={() => afterBlur(() => setError((current) => current && emailProblem(input.current?.value ?? '')))} />
            <Button ref={submit} type="submit" variant="contained" fullWidth {...pendingSubmitProps(pending)}>{pending ? 'Sending…' : 'Send reset link'}</Button>
            <Typography sx={{ color: 'text.secondary' }}>Remembered it? {signInLink}</Typography>
        </Box>
        <PendingStatus message={pending ? 'Sending the reset link…' : undefined} /></>}
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
