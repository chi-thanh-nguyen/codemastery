import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, matchRoutes, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, ListItemIcon, MenuItem } from '@mui/material';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { ForgotPasswordRoute } from '../features/auth/pages/ForgotPasswordPage';
import { ResetPasswordRoute } from '../features/auth/pages/ResetPasswordPage';
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
import { LandingPage, LandingRestoringView } from '../features/catalogue/pages/LandingPage';
import { LearnerHome } from '../features/learning/pages/LearnerHomePage';
import type { UserRole } from '../features/auth/types/authTypes';
import { CreateCoursePage, TeachingHomePage } from '../features/instructor/course-management/pages/InstructorCoursesPage';
import { CourseContentTab, CourseEditorFrame, CourseEditorShell, CourseSettingsTab } from '../features/instructor/course-management/pages/CourseEditorPage';
import { AssessmentsPage } from '../features/instructor/course-management/pages/QuestionBankPage';
import { QuizEditor } from '../features/instructor/course-management/pages/QuizEditorPage';
import { AssignmentEditor } from '../features/instructor/course-management/pages/AssignmentEditorPage';
import { useCourseEditorContext } from '../features/instructor/course-management/hooks/useCourseEditor';
import { CourseSkillsSection } from '../features/instructor/skill-graph-editor/pages/SkillGraphEditorPage';

// The landing page's example map pulls in the graph library, so it loads on demand.
const ExampleMasteryMap = lazy(() => import('../features/mastery/components/ExampleMasteryMap'));

/**
 * F2 session-ended notice. This only observes that a session present in this document ended
 * without the user signing out here (refresh refused, a current-session 401, or sign-out in
 * another tab), and the sign-in page says so once. A wake-up that the refresh cookie restores
 * clears the notice again.
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

/**
 * CT16.routes start pages: Learner `/home`, Instructor `/teach` (now routed in production),
 * Administrator `/courses` until `/admin` is routed. Sign-in uses the same rule (`consumeReturn`).
 */
const startFor = (role?: UserRole) => role === 'Learner' ? '/home' : role === 'Instructor' ? '/teach' : '/courses';

/** Header utilities: account menu for a session, sign-in actions otherwise. Hiding links is not authorization. */
export function SessionNavigation() {
    const auth = useAuth(), navigate = useNavigate(), { signOut } = useContext(SessionEndContext);
    // No sign-in actions flash while a reload restores the session from the refresh cookie.
    if (!auth.isAuthenticated && auth.restoring) return null;
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
        <Button component={Link} to="/register" variant="contained" color="onChrome" size="small" sx={{ display: { xs: 'none', md: 'inline-flex' } }}>Create account</Button>
    </>;
}

const LEARNER_WORKSPACE_SUBPATH = /^\/courses\/[^/]+\/(learn|lessons|diagnostic|mastery|assessments|quizzes|attempts|assignments)(\/|$)/;

/** Parent-aware current destination (DESIGN.md › Information architecture, CT16.routes). */
export function currentSection(pathname: string) {
    if (pathname === '/home') return '/home';
    if (pathname === '/teach' || pathname.startsWith('/teach/courses')) return '/teach';
    if (pathname === '/my-courses' || LEARNER_WORKSPACE_SUBPATH.test(pathname)) return '/my-courses';
    if (pathname === '/courses' || pathname.startsWith('/courses/')) return '/courses';
    return undefined;
}

function Workspace() {
    const auth = useAuth(), navigate = useNavigate(), location = useLocation();
    const role = auth.isAuthenticated ? auth.user?.role : undefined;
    // DESIGN.md › Primary navigation (FE32): only implemented destinations for the active role are shown.
    const navItems: NavItem[] = role === 'Learner'
        ? [
            { id: '/home', href: '/home', label: 'Home', icon: <PresentationIcon name="home" /> },
            { id: '/courses', href: '/courses', label: 'Catalogue', icon: <PresentationIcon name="catalogue" /> },
            { id: '/my-courses', href: '/my-courses', label: 'My courses', icon: <PresentationIcon name="my-courses" /> },
        ]
        : role === 'Instructor'
            ? [
                { id: '/teach', href: '/teach', label: 'Teaching', icon: <PresentationIcon name="book" /> },
                { id: '/courses', href: '/courses', label: 'Catalogue', icon: <PresentationIcon name="catalogue" /> },
            ]
            : [{ id: '/courses', href: '/courses', label: 'Catalogue', icon: <PresentationIcon name="catalogue" /> }];
    const brand = auth.isAuthenticated ? startFor(auth.user?.role) : '/';
    return <DashboardLayout navItems={navItems} selectedNavId={currentSection(location.pathname)} onNavSelect={(path) => navigate(path)}
        headerActions={<SessionNavigation />} brandHref={brand} onBrandSelect={() => navigate(brand)}
        // Like the header, the drawer shows no sign-in action while the restore runs (F2).
        drawerFooter={auth.isAuthenticated || auth.restoring ? undefined : <Button component={Link} to="/register" variant="contained" color="onChrome" fullWidth>Create account</Button>}>
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
    // The landing shell keeps its Theme button while the restore runs (D9-1; FE34 with FE03).
    if (auth.restoring) return <LandingRestoringView />;
    return <LandingPage exampleMap={<Suspense fallback={<Box role="status" aria-label="Loading the example map" sx={{ minHeight: 360 }} />}><ExampleMasteryMap /></Suspense>} />;
}
function NotFound() {
    return <Page><SystemStateView icon="search" title="Page not found" message="Check the address, or continue from the catalogue."
        actions={<Button component={Link} to="/courses" variant="contained">Browse courses</Button>} /></Page>;
}

/**
 * Course-scoped Instructor authoring views inside `CourseEditorShell` (`D12-3`):
 * `getOwnedCourse` and `getOwnedSkillGraph` are held once by the shell and shared across tabs and editors.
 */
function CourseAssessmentsTab() {
    const { courseId, skills, course } = useCourseEditorContext()!;
    return <AssessmentsPage key={courseId} courseId={courseId} skills={skills} headingLevel="h2" courseContext={course} />;
}
function CourseQuizEditor() {
    const { quizId } = useParams();
    const { courseId, skills } = useCourseEditorContext()!;
    return <Page width="reading"><QuizEditor key={`${courseId}:${quizId}`} courseId={courseId} quizId={quizId!} skills={skills} /></Page>;
}
function CourseAssignmentEditor() {
    const { assignmentId } = useParams();
    const { courseId, course } = useCourseEditorContext()!;
    return <Page width="reading"><AssignmentEditor key={`${courseId}:${assignmentId}`} courseId={courseId} assignmentId={assignmentId!} courseContext={course} /></Page>;
}

/** The isolated design preview's route (CT14, CT16). */
export const PREVIEW_PATH = '/preview';
/**
 * Whether `<AppRouter>` renders the design preview at `pathname`. It asks React Router itself, with the same
 * route path, so it accepts exactly what the route does: any letter case, trailing slashes and percent-encoded
 * characters (the router decodes each segment and keeps a malformed one as typed, which then matches nothing).
 */
export const isPreviewPath = (pathname: string) => matchRoutes([{ path: PREVIEW_PATH }], pathname) !== null;

/** Public catalogue routes share the current Auth/Profile workspace. */
export function AppRouter({ catalogue, preview }: { catalogue?: ReactNode; preview?: ReactNode }) {
    const auth = useAuth();
    return <SessionEndProvider><Routes>
        <Route path="/" element={<Landing />} />
        <Route path={PREVIEW_PATH} element={preview ?? <Navigate to="/courses" replace />} />
        <Route path="/login" element={<SignIn />} />
        <Route path="/register" element={<RegisterPage />} />
        {/* FE06 recovery (CT16.routes, anonymous; CT02). Delivery stays inactive until a deployment binds SMTP. */}
        <Route path="/forgot-password" element={<ForgotPasswordRoute />} />
        <Route path="/reset-password" element={<ResetPasswordRoute />} />
        <Route path="/courses" element={catalogue ?? <Workspace />}>
            {!catalogue && <Route index element={<Catalogue />} />}
        </Route>
        <Route element={<Workspace />}>
            <Route path="/courses/:courseId" element={<CourseDetail />} />
            <Route path="/home" element={<ProtectedRoute roles={['Learner']}><LearnerHome key={auth.generation} /></ProtectedRoute>} />
            <Route path="/my-courses" element={<ProtectedRoute roles={['Learner']}><MyCoursesPage key={auth.generation} /></ProtectedRoute>} />
            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
            <Route path="/teach" element={<ProtectedRoute roles={['Instructor']}><TeachingHomePage key={auth.generation} /></ProtectedRoute>} />
            <Route path="/teach/courses/new" element={<ProtectedRoute roles={['Instructor']}><CreateCoursePage key={auth.generation} /></ProtectedRoute>} />
            <Route path="/teach/courses/:courseId" element={<ProtectedRoute roles={['Instructor']}><CourseEditorShell key={auth.generation} /></ProtectedRoute>}>
                <Route element={<CourseEditorFrame />}>
                    <Route index element={<CourseContentTab />} />
                    <Route path="content" element={<CourseContentTab />} />
                    <Route path="skills" element={<CourseSkillsSection />} />
                    <Route path="assessments" element={<CourseAssessmentsTab />} />
                    <Route path="settings" element={<CourseSettingsTab />} />
                </Route>
                <Route path="quizzes/:quizId" element={<CourseQuizEditor />} />
                <Route path="assignments/:assignmentId" element={<CourseAssignmentEditor />} />
            </Route>
            <Route path="*" element={<NotFound />} />
        </Route>
    </Routes><RouteFocus /></SessionEndProvider>;
}
