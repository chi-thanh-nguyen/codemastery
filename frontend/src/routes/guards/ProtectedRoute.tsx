import { useLayoutEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../../features/auth/hooks/useAuth';
import type { UserRole } from '../../features/auth/types/authTypes';
import { Typography } from '@mui/material';
export function ProtectedRoute({ children, roles }: { children: ReactNode; roles?: readonly UserRole[] }) {
    const auth = useAuth(), location = useLocation();
    // Eligibility is read before the protected subtree can mount, including throttled timers.
    const eligible = auth.isAuthenticated;
    const previouslyEligible = useRef(eligible);
    useLayoutEffect(() => {
        if (!eligible && location.pathname === '/profile') auth.rememberProfile(previouslyEligible.current);
        previouslyEligible.current = eligible;
    }, [eligible, location.pathname, auth]);
    if (!eligible) return <Navigate to="/login" replace />;
    if (roles && !roles.includes(auth.user!.role)) return <Typography component="h1">Access is denied.</Typography>;
    return children;
}
