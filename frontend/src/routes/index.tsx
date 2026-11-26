import { ProductionLessonEditorPage } from '../features/instructor/course-management/pages/LessonEditorPage';
import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { WORKSPACE_ROUTES, ROUTE_PATHS, currentSection, roleNavigation, notificationsAvailable } from './routePaths';
import type { WorkspaceHandle } from './routePaths';
import { Link, matchRoutes, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { Alert, Box, Button, ListItemIcon, MenuItem } from '@mui/material';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { ForgotPasswordRoute } from '../features/auth/pages/ForgotPasswordPage';
import { ResetPasswordRoute } from '../features/auth/pages/ResetPasswordPage';
import { ProtectedRoute } from './guards/ProtectedRoute';
import { useAuth } from '../features/auth/hooks/useAuth';
import { DashboardLayout, Page, ShellNavigationContext, useShellNavigation, useShellNavigationRegistry } from '../layouts/DashboardLayout';
import { AccountMenu, AccountMenuDivider } from '../shared/components/AppHeader';
import { PresentationIcon } from '../shared/components/PresentationIcon';
import { SystemStateView } from '../shared/components/StatusViews';
import { isPlainClick } from '../shared/components/SidebarNav';
import { CourseCataloguePage } from '../features/catalogue/pages/CourseCataloguePage';
import { CourseMembershipActions, MyCoursesPage } from '../features/learning/pages/MyCoursesPage';
import { CourseDetailPage } from '../features/catalogue/pages/CourseDetailPage';
import { LandingPage, LandingRestoringView } from '../features/catalogue/pages/LandingPage';
import { LearnerHome } from '../features/learning/pages/LearnerHomePage';
import { CreateCoursePage, TeachingHomePage } from '../features/instructor/course-management/pages/InstructorCoursesPage';
import { CourseContentTab, CourseEditorFrame, CourseEditorShell, CourseRefreshAlert, CourseSettingsTab } from '../features/instructor/course-management/pages/CourseEditorPage';
import { AssessmentsPage } from '../features/instructor/course-management/pages/QuestionBankPage';
import { QuizEditor } from '../features/instructor/course-management/pages/QuizEditorPage';
import { AssignmentEditor } from '../features/instructor/course-management/pages/AssignmentEditorPage';
import { useCourseEditorContext } from '../features/instructor/course-management/hooks/useCourseEditor';
import { CourseSkillsSection } from '../features/instructor/skill-graph-editor/pages/SkillGraphEditorPage';

import { NotificationBell } from '../features/interaction/components/NotificationBell';
import { LiveNotificationsPage } from '../features/interaction/pages/NotificationsPage';
import { useNotifications } from '../features/interaction/hooks/useNotifications';

export { currentSection } from './routePaths';

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

/** Header utilities: account menu for a session, sign-in actions otherwise. Hiding links is not authorization. */
export function SessionNavigation() {
    const auth = useAuth(), navigate = useNavigate(), { signOut } = useContext(SessionEndContext), shell = useShellNavigation();
    // No sign-in actions flash while a reload restores the session from the refresh cookie.
    if (!auth.isAuthenticated && auth.restoring) return null;
    if (auth.isAuthenticated && auth.user) return <AccountMenu displayName={auth.user.displayName} role={auth.user.role} renderItems={(close) => [
        <MenuItem key="profile" component={Link} to="/profile" onClick={(event: MouseEvent) => {
            close();
            // A plain click asks a mounted editor with unsaved edits first; Cmd/Ctrl-click still opens a new tab.
            if (shell && isPlainClick(event)) { event.preventDefault(); shell.attempt(() => navigate('/profile')); }
        }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="user" sx={{ fontSize: 20 }} /></ListItemIcon>Profile</MenuItem>,
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

function Workspace() {
    const auth = useAuth(), navigate = useNavigate(), location = useLocation();
    const role = auth.isAuthenticated ? auth.user?.role : undefined;
    const navItems = roleNavigation(role).map((item) => ({ ...item, icon: <PresentationIcon name={item.icon} /> }));
    const notifications = useNotifications({ enabled: notificationsAvailable(role), open: currentSection(location.pathname, role) === ROUTE_PATHS.NOTIFICATIONS, routeKey: location.key });
    const brand = auth.isAuthenticated ? auth.startPath : ROUTE_PATHS.HOME;
    // Primary navigation and the home link ask a mounted editor with unsaved edits before leaving (S19).
    const shell = useShellNavigationRegistry();
    return <ShellNavigationContext.Provider value={shell}><DashboardLayout navItems={navItems} selectedNavId={currentSection(location.pathname, role)}
        onNavSelect={(path) => shell.attempt(() => navigate(path))}
        headerUtilities={notificationsAvailable(role) ? <NotificationBell unread={notifications.unreadCount ?? 0} href={ROUTE_PATHS.NOTIFICATIONS}
            current={currentSection(location.pathname, role) === ROUTE_PATHS.NOTIFICATIONS} onClick={(event) => { if (isPlainClick(event)) { event.preventDefault(); shell.attempt(() => navigate(ROUTE_PATHS.NOTIFICATIONS)); } }} /> : undefined}
        headerActions={<SessionNavigation />} brandHref={brand} onBrandSelect={() => shell.attempt(() => navigate(auth.isAuthenticated ? auth.consumeReturn() : brand))}
        // Like the header, the drawer shows no sign-in action while the restore runs (F2).
        drawerFooter={auth.isAuthenticated || auth.restoring ? undefined : <Button component={Link} to="/register" variant="contained" color="onChrome" fullWidth>Create account</Button>}>
        <Outlet context={notifications} />
    </DashboardLayout></ShellNavigationContext.Provider>;
}
function RouteFocus() {
    const { pathname, key } = useLocation(), auth = useAuth();
    const latest = useRef(auth);
    latest.current = auth;
    const pending = useRef<{ cancel: () => void; ready: () => void } | null>(null);
    // A same-path/query-only navigation cancels an old intent without creating a new one.
    useLayoutEffect(() => { pending.current?.cancel(); }, [key]);
    useLayoutEffect(() => {
        const main = document.querySelector('main');
        if (!main) return;
        let generation = latest.current.generation;
        let awaitingRestore = !latest.current.isAuthenticated && latest.current.restoring;
        let active = true;
        let observer: MutationObserver | undefined;
        const cancel = () => {
            if (!active) return;
            active = false;
            observer?.disconnect();
            for (const event of ['pointerdown', 'keydown', 'input', 'focusin'] as const) document.removeEventListener(event, cancel, true);
            window.removeEventListener('blur', cancel);
            document.removeEventListener('visibilitychange', cancel);
            if (pending.current?.cancel === cancel) pending.current = null;
        };
        const ready = () => {
            if (!active) return;
            const current = latest.current;
            // A load-time restore may supply this navigation's first session. A replacement of an
            // installed session cancels its intent; it must never focus a discarded session's view.
            if (awaitingRestore) {
                if (current.restoring) return;
                awaitingRestore = false;
                generation = current.generation;
            } else if (current.generation !== generation) { cancel(); return; }
            const heading = main.querySelector<HTMLElement>('h1');
            if (!heading || !main.isConnected || document.visibilityState === 'hidden') return;
            cancel();
            heading.tabIndex = -1;
            heading.focus();
        };
        pending.current = { cancel, ready };
        // Listeners begin after the navigation's initiating event. Any subsequent interaction,
        // including a dialog taking focus, wins over a delayed heading.
        for (const event of ['pointerdown', 'keydown', 'input', 'focusin'] as const) document.addEventListener(event, cancel, true);
        window.addEventListener('blur', cancel);
        document.addEventListener('visibilitychange', cancel);
        ready();
        if (active) {
            // Only the pending route's main subtree, only until its first heading or cancellation.
            observer = new MutationObserver(ready);
            observer.observe(main, { childList: true, subtree: true });
        }
        return cancel;
    }, [pathname]);
    useLayoutEffect(() => { pending.current?.ready(); }, [auth.generation, auth.restoring]);
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
/** Consume only after commit, so a render (including StrictMode) cannot discard the one-use return. */
function StartPage() {
    const auth = useAuth(), navigate = useNavigate();
    const redirected = useRef(false);
    useLayoutEffect(() => {
        if (!auth.isAuthenticated || redirected.current) return;
        redirected.current = true;
        void navigate(auth.consumeReturn(), { replace: true });
    }, [auth, navigate]);
    return null;
}
/** `/`: the public landing page; a signed-in visitor goes to their start page instead. */
function Landing() {
    const auth = useAuth();
    if (auth.isAuthenticated) return <StartPage />;
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
    return <Page width="reading"><CourseRefreshAlert course={course} /><AssignmentEditor key={`${courseId}:${assignmentId}`} courseId={courseId} assignmentId={assignmentId!} courseContext={course} /></Page>;
}

/** The isolated design preview's route (CT14, CT16). */
export const PREVIEW_PATH = '/preview';
/**
 * Whether `<AppRouter>` renders the design preview at `pathname`. It asks React Router itself, with the same
 * route path, so it accepts exactly what the route does: any letter case, trailing slashes and percent-encoded
 * characters (the router decodes each segment and keeps a malformed one as typed, which then matches nothing).
 */
export const isPreviewPath = (pathname: string) => matchRoutes([{ path: PREVIEW_PATH }], pathname) !== null;

/** Screen owners reuse native pathless role groups; unmatched descendants still reach NotFound. */
function workspaceRouteElements(routes: RouteObject[], elements: Record<string, ReactNode>): ReactNode {
    return routes.map(({ id, path, index, children, handle }) => {
        if (!id || (!children && !(id in elements))) throw new Error(`Missing delivered workspace screen: ${id}`);
        const policy = handle as WorkspaceHandle | undefined;
        const screen = elements[id] ?? <Outlet />;
        const element = policy?.protected || policy?.roles
            ? <ProtectedRoute roles={policy.roles}>{screen}</ProtectedRoute> : screen;
        return index ? <Route key={id} index element={element} />
            : <Route key={id} path={path} element={element}>{children && workspaceRouteElements(children, elements)}</Route>;
    });
}

/** Public catalogue routes share the current Auth/Profile workspace. */
export function AppRouter({ catalogue, preview }: { catalogue?: ReactNode; preview?: ReactNode }) {
    const auth = useAuth();
    // Bind the one production route tree to real screens. No preview or placeholder enters this map.
    const elements: Record<string, ReactNode> = {
        catalogue: catalogue ?? <Outlet />,
        'catalogue-index': <Catalogue />,
        'course-detail': <CourseDetail />,
        'learner-home': <LearnerHome key={auth.generation} />,
        'my-courses': <MyCoursesPage key={auth.generation} />,
        profile: <ProfilePage />,
        notifications: <LiveNotificationsPage key={auth.generation} />,
        teaching: <TeachingHomePage key={auth.generation} />,
        'create-course': <CreateCoursePage key={auth.generation} />,
        'course-editor': <CourseEditorShell key={auth.generation} />,
        'course-frame': <CourseEditorFrame />,
        'course-content-index': <CourseContentTab />,
        'course-content': <CourseContentTab />,
        'course-skills': <CourseSkillsSection />,
        'course-assessments': <CourseAssessmentsTab />,
        'course-settings': <CourseSettingsTab />,
        'course-lesson': <ProductionLessonEditorPage />,
        'course-quiz': <CourseQuizEditor />,
        'course-assignment': <CourseAssignmentEditor />,
    };
    return <SessionEndProvider><Routes>
        <Route path="/" element={<Landing />} />
        <Route path={PREVIEW_PATH} element={preview ?? <Navigate to="/courses" replace />} />
        <Route path="/login" element={<SignIn />} />
        <Route path="/register" element={<RegisterPage />} />
        {/* FE06 recovery (CT16.routes, anonymous; CT02). Delivery stays inactive until a deployment binds SMTP. */}
        <Route path="/forgot-password" element={<ForgotPasswordRoute />} />
        <Route path="/reset-password" element={<ResetPasswordRoute />} />
        <Route element={<Workspace />}>
            {workspaceRouteElements(WORKSPACE_ROUTES, elements)}
            <Route path="*" element={<NotFound />} />
        </Route>
    </Routes><RouteFocus /></SessionEndProvider>;
}
