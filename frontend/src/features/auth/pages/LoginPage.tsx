import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode, RefObject } from 'react';
import { Alert, Box, Button, CircularProgress, IconButton, InputAdornment, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { useAuth } from '../hooks/useAuth';
import { ApiError, formFailure } from '../../../shared/types/apiTypes';
import { loginInput } from '../types/authTypes';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';

export interface FormFailure { message: string; fields: Record<string, string> }

/** Show/hide toggle for password fields. */
export function PasswordVisibility({ visible, onToggle }: { visible: boolean; onToggle: () => void }) {
    return <InputAdornment position="end"><IconButton aria-label={visible ? 'Hide password' : 'Show password'} aria-pressed={visible} onClick={onToggle} edge="end" sx={{ mr: -.5 }}>
        <PresentationIcon name={visible ? 'eye-off' : 'eye'} sx={{ fontSize: 20 }} />
    </IconButton></InputAdornment>;
}

export interface LoginViewProps {
    email: string;
    password: string;
    onEmailChange: (value: string) => void;
    onPasswordChange: (value: string) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    loading: boolean;
    failure: FormFailure | null;
    formRef?: RefObject<HTMLFormElement | null>;
    summaryRef?: RefObject<HTMLDivElement | null>;
    /** Optional notice above the form (for example "Your session ended"). */
    notice?: ReactNode;
    /** Password recovery entry, rendered only when a recovery flow exists (CT02 pending). */
    recovery?: ReactNode;
    registerLink?: ReactNode;
    browseLink?: ReactNode;
}

/** Sign-in presentation (DESIGN.md › S2). Behavior lives in LoginPage. */
export function LoginView({ email, password, onEmailChange, onPasswordChange, onSubmit, loading, failure, formRef, summaryRef, notice, recovery, registerLink, browseLink }: LoginViewProps) {
    const [visible, setVisible] = useState(false);
    return <AuthLayout title="Sign in" subtitle="Welcome back. Continue where you left off." notice={notice}>
        <Box component="form" ref={formRef} aria-label="Sign in" aria-busy={loading} noValidate onSubmit={onSubmit} sx={{ display: 'grid', gap: 2.5 }}>
            {failure && <Alert ref={summaryRef} tabIndex={-1} severity="error">{failure.message}</Alert>}
            <TextField id="login-email" name="email" label="Email address" type="email" autoComplete="email" required fullWidth
                value={email} onChange={(event) => onEmailChange(event.target.value)} error={!!failure?.fields.email} helperText={failure?.fields.email} />
            <TextField id="login-password" name="password" label="Password" type={visible ? 'text' : 'password'} autoComplete="current-password" required fullWidth
                value={password} onChange={(event) => onPasswordChange(event.target.value)} error={!!failure?.fields.password} helperText={failure?.fields.password}
                slotProps={{ input: { endAdornment: <PasswordVisibility visible={visible} onToggle={() => setVisible((value) => !value)} /> } }} />
            {recovery && <Box sx={{ mt: -1 }}>{recovery}</Box>}
            <Button type="submit" fullWidth variant="contained" disabled={loading} sx={{ mt: .5 }}>Sign in</Button>
            {loading && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CircularProgress size={18} aria-labelledby="login-status" /><Typography id="login-status" role="status" variant="body2">Signing in…</Typography>
            </Box>}
            <Typography sx={{ color: 'text.secondary' }}>New to CodeMastery? {registerLink}</Typography>
            {browseLink && <Box>{browseLink}</Box>}
        </Box>
    </AuthLayout>;
}

export function LoginPage() {
    const auth = useAuth(), navigate = useNavigate();
    const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [loading, setLoading] = useState(false);
    const [failure, setFailure] = useState<FormFailure | null>(null);
    const form = useRef<HTMLFormElement>(null), summary = useRef<HTMLDivElement>(null);
    const work = useRef<AbortController | null>(null), alive = useRef(true), focusError = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; work.current?.abort(); }; }, []);
    useLayoutEffect(() => {
        if (!failure || !focusError.current) return;
        focusError.current = false;
        const field = Object.keys(failure.fields)[0], input = field ? form.current?.elements.namedItem(field) : null;
        if (input instanceof HTMLElement) input.focus(); else summary.current?.focus();
    }, [failure]);
    return <LoginView email={email} password={password} onEmailChange={setEmail} onPasswordChange={setPassword} loading={loading} failure={failure} formRef={form} summaryRef={summary}
        registerLink={<TextLink component={Link} to="/register" sx={{ fontWeight: 600 }}>Create an account</TextLink>}
        browseLink={<TextLink component={Link} to="/courses" sx={{ fontWeight: 600 }}>Browse courses</TextLink>}
        onSubmit={async (event) => {
            event.preventDefault(); if (loading) return;
            setFailure(null);
            try { loginInput({ email, password }); }
            catch (error) { focusError.current = true; setFailure(formFailure(error, ['email', 'password'])); return; }
            const controller = new AbortController(), origin = document.activeElement;
            work.current?.abort(); work.current = controller; setLoading(true);
            let generation = auth.generation;
            try {
                const result = auth.login({ email, password }, controller.signal);
                generation = auth.generation;
                const installed = await result;
                if (alive.current && work.current === controller && !controller.signal.aborted && auth.isCurrent(installed)) navigate(auth.consumeReturn(), { replace: true });
            } catch (error) {
                if (!alive.current || work.current !== controller || controller.signal.aborted || auth.generation !== generation || error instanceof ApiError && error.kind === 'cancelled') return;
                focusError.current = document.activeElement === origin || document.activeElement === document.body;
                setFailure(formFailure(error, ['email', 'password']));
            } finally { if (alive.current && work.current === controller) setLoading(false); }
        }} />;
}
