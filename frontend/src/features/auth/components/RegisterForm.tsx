import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { registrationInput } from '../types/authTypes';
import { ApiError, formFailure } from '../../../shared/types/apiTypes';

export interface RegisterFormProps { onSuccess: () => void }
export function RegisterForm({ onSuccess }: RegisterFormProps) {
    const auth = useAuth();
    const [displayName, setName] = useState(''), [email, setEmail] = useState(''), [password, setPassword] = useState('');
    const [confirmPassword, setConfirm] = useState(''), [loading, setLoading] = useState(false);
    const [failure, setFailure] = useState<{ message: string; fields: Record<string, string> } | null>(null);
    const form = useRef<HTMLFormElement>(null), summary = useRef<HTMLDivElement>(null);
    const work = useRef<AbortController | null>(null), alive = useRef(true), focusError = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; work.current?.abort(); }; }, []);
    useLayoutEffect(() => {
        if (!failure || !focusError.current) return;
        focusError.current = false;
        const field = Object.keys(failure.fields)[0];
        const input = field ? form.current?.elements.namedItem(field) : null;
        if (input instanceof HTMLElement) input.focus(); else summary.current?.focus();
    }, [failure]);
    return <Box component="form" ref={form} sx={{ '& :focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } }} aria-label="Create account" aria-busy={loading} noValidate onSubmit={async (event) => {
        event.preventDefault(); if (loading) return;
        setFailure(null);
        try {
            registrationInput({ email, password, displayName });
            if (password !== confirmPassword) throw new ApiError('validation', 'Please check the supplied information.', undefined, undefined, { confirmPassword: 'Passwords must match.' });
        } catch (error) { focusError.current = true; setFailure(formFailure(error, ['email', 'password', 'displayName', 'confirmPassword'])); return; }
        const controller = new AbortController(), origin = document.activeElement;
        work.current?.abort(); work.current = controller; setLoading(true);
        try {
            await auth.register({ email, password, displayName }, controller.signal);
            if (alive.current && work.current === controller && !controller.signal.aborted) onSuccess();
        } catch (error) {
            if (!alive.current || work.current !== controller || controller.signal.aborted || error instanceof ApiError && error.kind === 'cancelled') return;
            focusError.current = document.activeElement === origin || document.activeElement === document.body;
            setFailure(formFailure(error, ['email', 'password', 'displayName']));
        } finally { if (alive.current && work.current === controller) setLoading(false); }
    }}>
        {failure && <Alert ref={summary} tabIndex={-1} severity="error" sx={{ mb: 2 }}>{failure.message}</Alert>}
        <TextField id="register-displayName" name="displayName" label="Display name" autoComplete="name" required fullWidth margin="normal"
            value={displayName} onChange={(event) => setName(event.target.value)} error={!!failure?.fields.displayName} helperText={failure?.fields.displayName} />
        <TextField id="register-email" name="email" label="Email address" type="email" autoComplete="email" required fullWidth margin="normal"
            value={email} onChange={(event) => setEmail(event.target.value)} error={!!failure?.fields.email} helperText={failure?.fields.email} />
        <TextField id="register-password" name="password" label="Password" type="password" autoComplete="new-password" required fullWidth margin="normal"
            value={password} onChange={(event) => setPassword(event.target.value)} error={!!failure?.fields.password}
            helperText={failure?.fields.password ?? 'At least 15 Unicode characters, at most 72 UTF-8 bytes.'} />
        <TextField id="register-confirm" name="confirmPassword" label="Confirm password" type="password" autoComplete="new-password" required fullWidth margin="normal"
            value={confirmPassword} onChange={(event) => setConfirm(event.target.value)} error={!!failure?.fields.confirmPassword} helperText={failure?.fields.confirmPassword} />
        <Button type="submit" variant="contained" fullWidth disabled={loading} sx={{ my: 2 }}>Register</Button>
        {loading && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={20} aria-labelledby="register-status" /><Typography id="register-status" role="status">Registering account…</Typography>
        </Box>}
        <Button component={Link} to="/login">Already registered? Sign in</Button>
    </Box>;
}
