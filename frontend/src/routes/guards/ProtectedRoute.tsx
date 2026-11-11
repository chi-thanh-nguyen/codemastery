import { useLayoutEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { Button } from '@mui/material';
import { useAuth } from '../../features/auth/hooks/useAuth';
import type { UserRole } from '../../features/auth/types/authTypes';
import { Page } from '../../layouts/DashboardLayout';
import { SystemStateView } from '../../shared/components/StatusViews';

const audience: Record<UserRole, string> = { Learner: 'learners', Instructor: 'instructors', Administrator: 'administrators' };
const article = (role: UserRole) => role === 'Learner' ? 'a Learner' : role === 'Instructor' ? 'an Instructor' : 'an Administrator';

/** Frontend guard only; the backend independently authorizes every effect. */
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
    if (roles && !roles.includes(auth.user!.role)) return <Page><SystemStateView icon="shield" title="You don't have access to this page"
        message={`You're signed in as ${article(auth.user!.role)}. This page is for ${roles.map((role) => audience[role]).join(' and ')}.`}
        actions={<><Button component={Link} to="/courses" variant="contained">Go to catalogue</Button><Button component={Link} to="/profile" variant="outlined">Profile</Button></>} /></Page>;
    return children;
}
