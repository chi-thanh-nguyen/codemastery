import { useState } from 'react';
import { Alert, Button } from '@mui/material';
import { Link } from 'react-router';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { RegisterForm } from '../components/RegisterForm';
export function RegisterPage() {
    const [registered, setRegistered] = useState(false);
    return <AuthLayout title="Create account" subtitle="Start learning with CodeMastery">
        {registered ? <>
            <Alert severity="success" role="status" sx={{ mb: 2 }}>Your account was registered. Sign in to continue.</Alert>
            <Button variant="contained" sx={{ '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } }} component={Link} to="/login">Proceed to sign in</Button>
        </> : <RegisterForm onSuccess={() => setRegistered(true)} />}
    </AuthLayout>;
}
