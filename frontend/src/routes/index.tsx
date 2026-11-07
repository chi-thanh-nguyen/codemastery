import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, ListItemIcon, MenuItem } from '@mui/material';
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
import { LandingPage } from '../features/catalogue/pages/LandingPage';
import { LearnerHome } from '../features/learning/pages/LearnerHomePage';
import type { UserRole } from '../features/auth/types/authTypes';
import { color } from '../app/theme/theme';

// The landing page's example map pulls in the graph library, so it loads on demand.
const ExampleMasteryMap = lazy(() => import('../features/mastery/components/ExampleMasteryMap'));

/**
 * F2 session-ended notice. The provider's lifecycle is unchanged: this only observes
 * that a session present in this document ended without the user signing out
 * (15-minute expiry or a current-session 401), and the sign-in page says so once.
 */
interface SessionEnd { ended: boolean; dismiss: () => void; signOut: () => void }
const SessionEndContext = createContext<SessionEnd>({ ended: false, dismiss: () => undefined, signOut: () => undefined });
function SessionEndProvider({ children }: { children: ReactNode }) {
    const auth = useAuth(), authenticated = auth.isAuthenticated;
    const [ended, setEnded] = useState(false);
    const was = useRef(authenticated), explicit = useRef(false);
    useEffect(() => {
        if (was.current && !authenticated) { if (!explicit.current) setEnded(true); explicit.current = false; }
        if (authenticated) setEnded(false);
        was.current = authenticated;
    }, [authenticated]);
    const signOut = useCallback(() => { explicit.current = true; auth.logout(); }, [auth]);
    const dismiss = useCallback(() => setEnded(false), []);
    const value = useMemo(() => ({ ended, dismiss, signOut }), [ended, dismiss, signOut]);
    return <SessionEndContext.Provider value={value}>{children}</SessionEndContext.Provider>;
}
function SignIn() {
    const { ended, dismiss } = useContext(SessionEndContext);
    // Latch the notice for this visit and consume it, so it is shown once.
    const [shown, setShown] = useState(ended);
    useEffect(() => { if (ended) { setShown(true); dismiss(); } }, [ended, dismiss]);
    return <LoginPage notice={shown ? <Alert severity="info" role="status">Your session ended. Sign in again to continue.</Alert> : undefined} />;
}

/** Where a signed-in role starts: Learner home, otherwise the catalogue. Sign-in uses the same rule (AuthProvider `consumeReturn`). */
const startFor = (role?: UserRole) => role === 'Learner' ? '/home' : '/courses';

/** Header utilities: account menu for a session, sign-in actions otherwise. Hiding links is not authorization. */
export function SessionNavigation() {
    const auth = useAuth(), navigate = useNavigate(), { signOut } = useContext(SessionEndContext);
    if (auth.isAuthenticated && auth.user) return <AccountMenu displayName={auth.user.displayName} role={auth.user.role} renderItems={(close) => [
        <MenuItem key="profile" component={Link} to="/profile" onClick={close}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="user" sx={{ fontSize: 20 }} /></ListItemIcon>Profile</MenuItem>,
        <AccountMenuDivider key="divider" />,
        <MenuItem key="sign-out" onClick={() => { close(); signOut(); navigate('/login', { replace: true }); }}>
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
    if (pathname === '/home') return '/home';
    if (pathname === '/courses' || pathname.startsWith('/courses/')) return '/courses';
    if (pathname === '/my-courses') return '/my-courses';
    return undefined;
}

function Workspace() {
    const auth = useAuth(), navigate = useNavigate(), location = useLocation();
    const learner = auth.isAuthenticated && auth.user?.role === 'Learner';
    // DESIGN.md › Primary navigation: only implemented destinations are shown.
    const navItems: NavItem[] = [
        ...(learner ? [{ id: '/home', href: '/home', label: 'Home', icon: <PresentationIcon name="home" /> }] : []),
        { id: '/courses', href: '/courses', label: 'Catalogue', icon: <PresentationIcon name="catalogue" /> },
        ...(learner ? [{ id: '/my-courses', href: '/my-courses', label: 'My courses', icon: <PresentationIcon name="my-courses" /> }] : [])];
    const brand = auth.isAuthenticated ? startFor(auth.user?.role) : '/';
    return <DashboardLayout navItems={navItems} selectedNavId={currentSection(location.pathname)} onNavSelect={(path) => navigate(path)}
        headerActions={<SessionNavigation />} brandHref={brand} onBrandSelect={() => navigate(brand)}
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
/** `/`: the public landing page; a signed-in visitor goes to their start page instead. */
function Landing() {
    const auth = useAuth();
    if (auth.isAuthenticated) return <Navigate to={startFor(auth.user?.role)} replace />;
    return <LandingPage exampleMap={<Suspense fallback={<Box role="status" aria-label="Loading the example map" sx={{ minHeight: 360 }} />}><ExampleMasteryMap /></Suspense>} />;
}
function NotFound() {
    return <Page><SystemStateView icon="search" title="Page not found" message="Check the address, or continue from the catalogue."
        actions={<Button component={Link} to="/courses" variant="contained">Browse courses</Button>} /></Page>;
}
/** Public catalogue routes share the current Auth/Profile workspace. */
export function AppRouter({ catalogue, preview }: { catalogue?: ReactNode; preview?: ReactNode }) {
    const auth = useAuth();
    return <SessionEndProvider><Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/preview" element={preview ?? <Navigate to="/courses" replace />} />
        <Route path="/login" element={<SignIn />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/courses" element={catalogue ?? <Workspace />}>
            {!catalogue && <Route index element={<Catalogue />} />}
        </Route>
        <Route element={<Workspace />}>
            <Route path="/courses/:courseId" element={<CourseDetail />} />
            <Route path="/home" element={<ProtectedRoute roles={['Learner']}><LearnerHome key={auth.generation} /></ProtectedRoute>} />
            <Route path="/my-courses" element={<ProtectedRoute roles={['Learner']}><MyCoursesPage key={auth.generation} /></ProtectedRoute>} />
            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
            <Route path="*" element={<NotFound />} />
        </Route>
    </Routes><RouteFocus /></SessionEndProvider>;
}
