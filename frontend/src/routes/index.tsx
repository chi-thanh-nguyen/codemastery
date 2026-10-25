import { useLayoutEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router';
import { Box, Button } from '@mui/material';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { ProtectedRoute } from './guards/ProtectedRoute';
import { useAuth } from '../features/auth/hooks/useAuth';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { PageHeader } from '../shared/components/PageHeader';

export function SessionNavigation() {
    const auth = useAuth(), navigate = useNavigate();
    return <Box component="nav" aria-label="Account navigation" sx={{ display: 'flex', flexWrap: 'wrap', gap: .5, maxWidth: { xs: 144, sm: 'none' }, '& .MuiButton-root': { minWidth: 0, px: 1 }, '& :focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } }}>
        <Button component={Link} to="/courses" size="small">Courses</Button>
        {auth.isAuthenticated ? <>
            <Button component={Link} to="/profile" size="small">Profile</Button>
            <Button size="small" onClick={() => { auth.logout(); navigate('/login', { replace: true }); }}>Sign out</Button>
        </> : <><Button component={Link} to="/login" size="small">Sign in</Button><Button component={Link} to="/register" size="small">Register</Button></>}
    </Box>;
}
function Workspace() {
    const auth = useAuth(), navigate = useNavigate(), location = useLocation();
    return <DashboardLayout headerTitle="CodeMastery"
        navItems={[{ id: '/courses', label: 'Courses' }, ...(auth.isAuthenticated ? [{ id: '/profile', label: 'Profile' }] : [])]}
        selectedNavId={location.pathname} onNavSelect={(path) => navigate(path)} headerActions={<SessionNavigation />}>
        <Outlet />
    </DashboardLayout>;
}
function RouteFocus() {
    const { pathname } = useLocation();
    useLayoutEffect(() => {
        const heading = document.querySelector<HTMLElement>('main h1');
        if (heading) { heading.tabIndex = -1; heading.focus(); }
    }, [pathname]);
    return null;
}
/** FE07 owns real catalogue/detail rendering; these public routes supply composition only. */
export function AppRouter({ catalogue }: { catalogue?: ReactNode }) {
    return <><Routes>
        <Route path="/" element={<Navigate to="/courses" replace />} />
        <Route path="/login" element={<Box component="main" id="main-content"><LoginPage /></Box>} />
        <Route path="/register" element={<Box component="main" id="main-content"><RegisterPage /></Box>} />
        <Route path="/courses" element={catalogue ?? <Workspace />}>
            {!catalogue && <Route index element={<PageHeader title="Courses" subtitle="Course browsing is not available yet." />} />}
        </Route>
        <Route element={<Workspace />}>
            <Route path="/courses/:courseId" element={<PageHeader title="Course details" subtitle="Course details are not available yet." />} />
            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
            <Route path="*" element={<PageHeader title="Page not found" subtitle="Choose an available page from navigation." />} />
        </Route>
    </Routes><RouteFocus /></>;
}
