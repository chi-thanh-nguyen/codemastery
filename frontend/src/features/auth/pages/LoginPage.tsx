import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, TextField, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { useAuth } from '../hooks/useAuth';
import { ApiError, formFailure } from '../../../shared/types/apiTypes';
import { loginInput } from '../types/authTypes';

export function LoginPage() {
    const auth = useAuth(), navigate = useNavigate();
    const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [loading, setLoading] = useState(false);
    const [failure, setFailure] = useState<{ message: string; fields: Record<string, string> } | null>(null);
    const form = useRef<HTMLFormElement>(null), summary = useRef<HTMLDivElement>(null);
    const work = useRef<AbortController | null>(null), alive = useRef(true), focusError = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; work.current?.abort(); }; }, []);
    useLayoutEffect(() => {
        if (!failure || !focusError.current) return;
        focusError.current = false;
        const field = Object.keys(failure.fields)[0], input = field ? form.current?.elements.namedItem(field) : null;
        if (input instanceof HTMLElement) input.focus(); else summary.current?.focus();
    }, [failure]);
    return <AuthLayout title="Welcome back" subtitle="Sign in to CodeMastery">
        <Box component="form" ref={form} sx={{ '& :focus-visible:not(.MuiOutlinedInput-input)': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } }} aria-label="Sign in" aria-busy={loading} noValidate onSubmit={async (event) => {
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
        }}>
            {failure && <Alert ref={summary} tabIndex={-1} severity="error" sx={{ mb: 2 }}>{failure.message}</Alert>}
            <TextField id="login-email" name="email" label="Email address" type="email" autoComplete="email" required fullWidth margin="normal"
                value={email} onChange={(event) => setEmail(event.target.value)} error={!!failure?.fields.email} helperText={failure?.fields.email} />
            <TextField id="login-password" name="password" label="Password" type="password" autoComplete="current-password" required fullWidth margin="normal"
                value={password} onChange={(event) => setPassword(event.target.value)} error={!!failure?.fields.password} helperText={failure?.fields.password} />
            <Button type="submit" fullWidth variant="contained" disabled={loading} sx={{ my: 2 }}>Sign in</Button>
            {loading && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CircularProgress size={20} aria-labelledby="login-status" /><Typography id="login-status" role="status">Signing in…</Typography>
            </Box>}
            <Button component={Link} to="/register">Create an account</Button>
            <Button component={Link} to="/courses">Browse courses</Button>
        </Box>
    </AuthLayout>;
}
