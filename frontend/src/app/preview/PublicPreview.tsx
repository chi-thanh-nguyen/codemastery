import { useState } from 'react';
import { Alert, Link as TextLink } from '@mui/material';
import { LoginView } from '../../features/auth/pages/LoginPage';
import type { FormFailure } from '../../features/auth/pages/LoginPage';
import { ForgotPasswordPage } from '../../features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '../../features/auth/pages/ResetPasswordPage';
import { LandingPage } from '../../features/catalogue/pages/LandingPage';
import ExampleMasteryMap from '../../features/mastery/components/ExampleMasteryMap';
import { later, usePreview } from './previewKit';

type Go = (screen: string, extra?: Record<string, string | undefined>) => void;

const link = (label: string, onClick: () => void) => <TextLink href="#" sx={{ fontWeight: 600 }} onClick={(event) => { event.preventDefault(); onClick(); }}>{label}</TextLink>;

/** Group C presentation: landing, sign-in states and password recovery. No request leaves the browser. */
export function PublicPreview({ screen, go }: { screen: string; go: Go }) {
    if (screen === 'landing') return <LandingPage exampleMap={<ExampleMasteryMap />} />;
    if (screen === 'sign-in' || screen === 'session-ended') return <SignInPreview key={screen} sessionEnded={screen === 'session-ended'} go={go} />;
    if (screen === 'forgot-password') return <ForgotPreview go={go} />;
    if (screen === 'reset-password' || screen === 'reset-expired') return <ResetPreview key={screen} expired={screen === 'reset-expired'} go={go} />;
    return null;
}


function SignInPreview({ sessionEnded, go }: { sessionEnded: boolean; go: Go }) {
    const { notify } = usePreview();
    const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [loading, setLoading] = useState(false), [failure, setFailure] = useState<FormFailure | null>(null);
    return <LoginView email={email} password={password} onEmailChange={setEmail} onPasswordChange={setPassword} loading={loading} failure={failure}
        notice={sessionEnded ? <Alert severity="info">Your session ended. Sign in again to continue.</Alert> : <Alert severity="info">Sample sign-in: submitting shows the generic failure state. Nothing is sent.</Alert>}
        recovery={link('Forgot password?', () => go('forgot-password'))}
        registerLink={link('Create an account', () => notify('In production this opens the live Create account page at /register.'))}
        onSubmit={(event) => { event.preventDefault(); setLoading(true); setFailure(null); later(() => { setLoading(false); setFailure({ message: 'Email or password is incorrect.', fields: {} }); }, 800); }} />;
}

function ForgotPreview({ go }: { go: Go }) {
    const [state, setState] = useState<'idle' | 'submitting' | 'sent' | 'error'>('idle');
    return <ForgotPasswordPage state={state} signInLink={link('Back to sign in', () => go('sign-in'))}
        notice={<Alert severity="info">Preview only. Password recovery (CT02: reset policy and link delivery) is not approved yet, so this page is not available in the product.</Alert>}
        onSubmit={() => { setState('submitting'); later(() => setState('sent')); }} />;
}

function ResetPreview({ expired, go }: { expired: boolean; go: Go }) {
    const [state, setState] = useState<'idle' | 'submitting' | 'done' | 'expired' | 'error'>(expired ? 'expired' : 'idle');
    return <ResetPasswordPage state={state} signInLink={link('Sign in', () => go('sign-in'))} requestNewLink={link('Request a new link', () => go('forgot-password'))}
        notice={<Alert severity="info">Preview only (CT02 pending). Nothing is changed.</Alert>}
        onSubmit={() => { setState('submitting'); later(() => setState('done')); }} />;
}
