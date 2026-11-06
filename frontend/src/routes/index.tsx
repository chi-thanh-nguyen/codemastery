import { useLayoutEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { Button, ListItemIcon, MenuItem } from '@mui/material';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { ProtectedRoute } from './guards/ProtectedRoute';
import { useAuth } from '../features/auth/hooks/useAuth';
import { DashboardLayout, Page } from '../layouts/DashboardLayout';
import { AccountMenu, AccountMenuDivider } from '../shared/components/AppHeader';
import { PresentationIcon } from '../shared/components/PresentationIcon';
import { SystemStateView } from '../shared/components/StatusViews';
import type { NavItem } from '../shared/components/SidebarNav';
import { CourseCataloguePage } from '../features/catalogue/pages/CourseCataloguePage';
import { CourseMembershipActions, MyCoursesPage } from '../features/learning/pages/MyCoursesPage';
import { CourseDetailPage } from '../features/catalogue/pages/CourseDetailPage';
import { color } from '../app/theme/theme';

/** Header utilities: account menu for a session, sign-in actions otherwise. Hiding links is not authorization. */
export function SessionNavigation() {
    const auth = useAuth(), navigate = useNavigate();
    if (auth.isAuthenticated && auth.user) return <AccountMenu displayName={auth.user.displayName} role={auth.user.role} renderItems={(close) => [
        <MenuItem key="profile" component={Link} to="/profile" onClick={close}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="user" sx={{ fontSize: 20 }} /></ListItemIcon>Profile</MenuItem>,
        <AccountMenuDivider key="divider" />,
        <MenuItem key="sign-out" onClick={() => { close(); auth.logout(); navigate('/login', { replace: true }); }}>
            <ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="sign-out" sx={{ fontSize: 20 }} /></ListItemIcon>Sign out
        </MenuItem>,
    ]} />;
    return <>
        <Button component={Link} to="/login" variant="text" color="inherit" size="small" sx={{ color: 'common.white' }}>Sign in</Button>
        {/* Below 900 px "Create account" lives in the navigation drawer so the bar never overflows at large text sizes. */}
        <Button component={Link} to="/register" variant="contained" size="small" sx={{ display: { xs: 'none', md: 'inline-flex' }, bgcolor: 'common.white', color: color.ink950, '&:hover': { bgcolor: color.fill150 } }}>Create account</Button>
    </>;
}

/** Parent-aware current destination: child routes keep their section active. */
function currentSection(pathname: string) {
    if (pathname === '/courses' || pathname.startsWith('/courses/')) return '/courses';
    if (pathname === '/my-courses') return '/my-courses';
    return undefined;
}

function Workspace() {
    const auth = useAuth(), navigate = useNavigate(), location = useLocation();
    const navItems: NavItem[] = [{ id: '/courses', href: '/courses', label: 'Catalogue', icon: <PresentationIcon name="catalogue" /> },
        ...(auth.isAuthenticated && auth.user?.role === 'Learner' ? [{ id: '/my-courses', href: '/my-courses', label: 'My courses', icon: <PresentationIcon name="my-courses" /> }] : [])];
    return <DashboardLayout navItems={navItems} selectedNavId={currentSection(location.pathname)} onNavSelect={(path) => navigate(path)}
        headerActions={<SessionNavigation />} brandHref="/courses" onBrandSelect={() => navigate('/courses')}
        drawerFooter={auth.isAuthenticated ? undefined : <Button component={Link} to="/register" variant="contained" fullWidth sx={{ bgcolor: 'common.white', color: color.ink950, '&:hover': { bgcolor: color.fill150 } }}>Create account</Button>}>
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
function Catalogue() {
    const navigate = useNavigate();
    return <CourseCataloguePage onSelectCourse={(id) => navigate(`/courses/${encodeURIComponent(id)}`)} />;
}
function CourseDetail() {
    const { courseId } = useParams(), navigate = useNavigate(), auth = useAuth();
    return <CourseDetailPage key={courseId} courseId={courseId!} onBack={() => navigate('/courses')} membershipActions={<CourseMembershipActions key={`${courseId}:${auth.generation}`} courseId={courseId!} />} />;
}
function NotFound() {
    return <Page><SystemStateView icon="search" title="Page not found" message="Check the address, or continue from the catalogue."
        actions={<Button component={Link} to="/courses" variant="contained">Browse courses</Button>} /></Page>;
}
/** Public catalogue routes share the current Auth/Profile workspace. */
export function AppRouter({ catalogue, preview }: { catalogue?: ReactNode; preview?: ReactNode }) {
    const auth = useAuth();
    return <><Routes>
        <Route path="/" element={<Navigate to="/courses" replace />} />
        <Route path="/preview" element={preview ?? <Navigate to="/courses" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/courses" element={catalogue ?? <Workspace />}>
            {!catalogue && <Route index element={<Catalogue />} />}
        </Route>
        <Route element={<Workspace />}>
            <Route path="/courses/:courseId" element={<CourseDetail />} />
            <Route path="/my-courses" element={<ProtectedRoute roles={['Learner']}><MyCoursesPage key={auth.generation} /></ProtectedRoute>} />
            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
            <Route path="*" element={<NotFound />} />
        </Route>
    </Routes><RouteFocus /></>;
}
