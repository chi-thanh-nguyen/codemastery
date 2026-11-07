import { useState } from 'react';
import { Alert, Button } from '@mui/material';
import { Link } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { RegisterForm } from '../components/RegisterForm';

/** Learner self-registration; no auto-login (ADR002 CT01.core). Nothing is passed to the sign-in page. */
export function RegisterPage() {
    const [registered, setRegistered] = useState(false);
    return <AuthLayout title="Create account" subtitle="Start learning with CodeMastery. Accounts you create here are Learner accounts.">
        {registered ? <>
            <Alert severity="success" role="status" sx={{ mb: 3 }}>Account created. Sign in to continue.</Alert>
            <Button variant="contained" fullWidth component={Link} to="/login">Sign in</Button>
        </> : <RegisterForm onSuccess={() => setRegistered(true)} />}
    </AuthLayout>;
}
