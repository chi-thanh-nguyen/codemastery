import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, Link as TextLink, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { registrationInput } from '../types/authTypes';
import { ApiError, formFailure } from '../../../shared/types/apiTypes';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { PasswordVisibility } from '../pages/LoginPage';
import { color } from '../../../app/theme/theme';

/** Live checklist mirroring the approved CT01 predicates (code points and UTF-8 bytes, no trimming). */
export function PasswordChecklist({ password, id }: { password: string; id: string }) {
    const checks = [
        { label: 'At least 15 Unicode characters', met: [...password].length >= 15 },
        { label: 'At most 72 UTF-8 bytes', met: password.length > 0 && new TextEncoder().encode(password).length <= 72 },
    ];
    return <Box component="ul" id={id} aria-label="Password rules" sx={{ listStyle: 'none', m: 0, mt: 1, p: 0, display: 'grid', gap: .5 }}>
        {checks.map((check) => <Box component="li" key={check.label} sx={{ display: 'flex', alignItems: 'center', gap: 1, fontSize: '0.8125rem', fontWeight: 500, color: check.met ? color.mint700 : 'text.secondary' }}>
            <PresentationIcon name={check.met ? 'check-circle' : 'dashed-circle'} sx={{ fontSize: 16 }} />
            {check.label}<span className="cm-visually-hidden">{check.met ? ', met' : ', not met yet'}</span>
        </Box>)}
    </Box>;
}

export interface RegisterFormProps { onSuccess: () => void }
export function RegisterForm({ onSuccess }: RegisterFormProps) {
    const auth = useAuth();
    const [displayName, setName] = useState(''), [email, setEmail] = useState(''), [password, setPassword] = useState('');
    const [confirmPassword, setConfirm] = useState(''), [loading, setLoading] = useState(false), [visible, setVisible] = useState(false);
    const [failure, setFailure] = useState<{ message: string; fields: Record<string, string> } | null>(null);
    const form = useRef<HTMLFormElement>(null), summary = useRef<HTMLDivElement>(null);
    const work = useRef<AbortController | null>(null), alive = useRef(true), focusError = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; work.current?.abort(); }; }, []);
    useLayoutEffect(() => {
        const element = form.current;
        return () => {
            // Registration success removes this form while AuthLayout stays mounted.
            const active = element?.ownerDocument.activeElement;
            if (active instanceof HTMLInputElement && element?.contains(active)) active.blur();
        };
    }, []);
    useLayoutEffect(() => {
        if (!failure || !focusError.current) return;
        focusError.current = false;
        const field = Object.keys(failure.fields)[0];
        const input = field ? form.current?.elements.namedItem(field) : null;
        if (input instanceof HTMLElement) input.focus(); else summary.current?.focus();
    }, [failure]);
    return <Box component="form" ref={form} aria-label="Create account" aria-busy={loading} noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={async (event) => {
        event.preventDefault(); if (loading) return;
        setFailure(null);
        try {
            registrationInput({ email, password, displayName });
            if (password !== confirmPassword) throw new ApiError('validation', 'Please check the supplied information.', undefined, undefined, { confirmPassword: 'Passwords must match.' });
        } catch (error) { focusError.current = true; setFailure(formFailure(error, ['email', 'password', 'displayName', 'confirmPassword'])); return; }
        const controller = new AbortController(), origin = document.activeElement;
        work.current?.abort(); work.current = controller; setLoading(true);
        let generation = auth.generation;
        try {
            const result = auth.register({ email, password, displayName }, controller.signal);
            generation = auth.generation;
            await result;
            if (alive.current && work.current === controller && !controller.signal.aborted && auth.generation === generation) onSuccess();
        } catch (error) {
            if (!alive.current || work.current !== controller || controller.signal.aborted || auth.generation !== generation || error instanceof ApiError && error.kind === 'cancelled') return;
            focusError.current = document.activeElement === origin || document.activeElement === document.body;
            setFailure(formFailure(error, ['email', 'password', 'displayName']));
        } finally { if (alive.current && work.current === controller) setLoading(false); }
    }}>
        {failure && <Alert ref={summary} tabIndex={-1} severity="error">{failure.message}</Alert>}
        <TextField id="register-displayName" name="displayName" label="Display name" autoComplete="name" required fullWidth
            value={displayName} onChange={(event) => setName(event.target.value)} error={!!failure?.fields.displayName} helperText={failure?.fields.displayName ?? 'Shown to instructors and in your profile.'} />
        <TextField id="register-email" name="email" label="Email address" type="email" autoComplete="email" required fullWidth
            value={email} onChange={(event) => setEmail(event.target.value)} error={!!failure?.fields.email} helperText={failure?.fields.email} />
        <Box>
            <TextField id="register-password" name="password" label="Password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth
                value={password} onChange={(event) => setPassword(event.target.value)} error={!!failure?.fields.password}
                helperText={failure?.fields.password ?? 'At least 15 Unicode characters, at most 72 UTF-8 bytes.'}
                slotProps={{ htmlInput: { 'aria-describedby': 'register-password-helper-text register-password-rules' }, input: { endAdornment: <PasswordVisibility visible={visible} onToggle={() => setVisible((value) => !value)} /> } }} />
            <PasswordChecklist password={password} id="register-password-rules" />
        </Box>
        <TextField id="register-confirm" name="confirmPassword" label="Confirm password" type={visible ? 'text' : 'password'} autoComplete="new-password" required fullWidth
            value={confirmPassword} onChange={(event) => setConfirm(event.target.value)} error={!!failure?.fields.confirmPassword} helperText={failure?.fields.confirmPassword} />
        <Button type="submit" variant="contained" fullWidth disabled={loading} sx={{ mt: .5 }}>Create account</Button>
        {loading && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={18} aria-labelledby="register-status" /><Typography id="register-status" role="status" variant="body2">Creating your account…</Typography>
        </Box>}
        <Typography sx={{ color: 'text.secondary' }}>Already have an account? <TextLink component={Link} to="/login" sx={{ fontWeight: 600 }}>Sign in</TextLink></Typography>
    </Box>;
}
