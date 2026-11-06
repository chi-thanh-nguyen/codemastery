import { useCallback, useLayoutEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { Box, ListItemIcon, MenuItem, Snackbar } from '@mui/material';
import { DashboardLayout } from '../../layouts/DashboardLayout';
import { AccountMenu } from '../../shared/components/AppHeader';
import { PresentationIcon } from '../../shared/components/PresentationIcon';
import type { NavItem } from '../../shared/components/SidebarNav';
import { SegmentedControl } from '../../shared/components/ViewRail';
import { NotificationBell } from '../../features/interaction/components/NotificationBell';
import { DesignReference } from './DesignReference';
import type { DirectoryGroup } from './DesignReference';
import { LearnerPreview, initialLearnerState } from './LearnerPreview';
import { InstructorPreview, initialInstructorState } from './InstructorPreview';
import { AdminPreview, initialAdminState } from './AdminPreview';
import { PublicPreview } from './PublicPreview';
import * as fx from './fixtures';
import { PreviewContext, SampleDataBanner, previewHref, usePreviewNav } from './previewKit';
import type { Workspace } from './previewKit';

interface ScreenDef { id: string; workspace: Workspace; title: string; spec: string; status: 'Live' | 'Preview' | 'Proposed'; note: string; bare?: boolean; nav?: string }
/** Every DESIGN.md screen group. `nav` names the top-bar item that stays current (parent-aware). */
const SCREENS: ScreenDef[] = [
    { id: 'overview', workspace: 'reference', title: 'Design system and screen index', spec: 'S26', status: 'Live', note: 'This page', nav: 'overview' },
    { id: 'landing', workspace: 'public', title: 'Landing page', spec: 'S25', status: 'Live', note: 'Live at / for visitors without a session; the example map is an illustration', bare: true },
    { id: 'sign-in', workspace: 'public', title: 'Sign in (sample states)', spec: 'S2', status: 'Preview', note: 'Presentation of the live page without a request', bare: true },
    { id: 'session-ended', workspace: 'public', title: 'Session-ended notice', spec: 'F2', status: 'Live', note: 'Live on /login after an expiry or session 401 in the same tab', bare: true },
    { id: 'forgot-password', workspace: 'public', title: 'Forgot password', spec: 'F1', status: 'Proposed', note: 'CT02 recovery contract pending', bare: true },
    { id: 'reset-password', workspace: 'public', title: 'Reset password', spec: 'F1', status: 'Proposed', note: 'CT02 recovery contract pending', bare: true },
    { id: 'reset-expired', workspace: 'public', title: 'Reset link expired', spec: 'F1', status: 'Proposed', note: 'CT02 recovery contract pending', bare: true },
    { id: 'learner-home', workspace: 'learner', title: 'Learner home (full design)', spec: 'S6', status: 'Preview', note: 'Live at /home with active courses only; resume, progress and Up next wait for CT22 and Adaptive', nav: 'learner-home' },
    { id: 'my-courses', workspace: 'learner', title: 'My courses (sample memberships)', spec: 'S5', status: 'Preview', note: 'Live page uses the real API; tiles here open the sample workspace', nav: 'my-courses' },
    { id: 'course', workspace: 'learner', title: 'Course workspace — Path', spec: 'S7', status: 'Preview', note: 'CT21/CT22 progress and resume pending', nav: 'my-courses' },
    { id: 'lesson', workspace: 'learner', title: 'Lesson reader with Q&A', spec: 'S8', status: 'Preview', note: 'Lesson content, CT09 files, Interaction pending', nav: 'my-courses' },
    { id: 'diagnostic', workspace: 'learner', title: 'Placement diagnostic', spec: 'S9', status: 'Preview', note: 'Assessment + Adaptive contracts pending', nav: 'my-courses' },
    { id: 'assessments', workspace: 'learner', title: 'Course assessments', spec: 'S10', status: 'Preview', note: 'Assessment contracts pending', nav: 'my-courses' },
    { id: 'quiz', workspace: 'learner', title: 'Quiz introduction', spec: 'S11', status: 'Preview', note: 'Assessment contracts pending', nav: 'my-courses' },
    { id: 'quiz-attempt', workspace: 'learner', title: 'Quiz attempt (Focus)', spec: 'S11', status: 'Preview', note: 'Server timing and auto-submit pending', bare: true },
    { id: 'quiz-result', workspace: 'learner', title: 'Quiz result', spec: 'S12', status: 'Preview', note: 'Assessment + Adaptive pending', nav: 'my-courses' },
    { id: 'attempt-history', workspace: 'learner', title: 'Attempt history', spec: 'S12', status: 'Preview', note: 'Assessment contracts pending', nav: 'my-courses' },
    { id: 'assignment', workspace: 'learner', title: 'Assignment', spec: 'S13', status: 'Preview', note: 'Assessment + CT09 pending', nav: 'my-courses' },
    { id: 'mastery', workspace: 'learner', title: 'Mastery Map', spec: 'S14', status: 'Preview', note: 'Adaptive reads pending', nav: 'my-courses' },
    { id: 'notifications', workspace: 'learner', title: 'Notifications', spec: 'S15', status: 'Preview', note: 'Interaction contracts pending' },
    { id: 'teaching', workspace: 'instructor', title: 'Teaching home', spec: 'S17', status: 'Proposed', note: 'Route and authoring contracts pending', nav: 'teaching' },
    { id: 'course-editor', workspace: 'instructor', title: 'Course editor (content, skills, assessments, learners, Q&A, settings)', spec: 'S18', status: 'Preview', note: 'CT21, CT40, Assessment pending', nav: 'teaching' },
    { id: 'lesson-editor', workspace: 'instructor', title: 'Lesson editor', spec: 'S18', status: 'Preview', note: 'CT21 + CT09 pending', nav: 'teaching' },
    { id: 'grading', workspace: 'instructor', title: 'Grading list', spec: 'S21', status: 'Preview', note: 'Assessment + CT09 pending', nav: 'grading' },
    { id: 'grade', workspace: 'instructor', title: 'Grade a submission', spec: 'S21', status: 'Preview', note: 'Assessment + CT09 pending', nav: 'grading' },
    { id: 'learners', workspace: 'instructor', title: 'Learner insights', spec: 'S22', status: 'Preview', note: 'Scoped insight reads pending', nav: 'learners' },
    { id: 'learner', workspace: 'instructor', title: 'Learner detail', spec: 'S22', status: 'Preview', note: 'Scoped insight reads pending', nav: 'learners' },
    { id: 'admin-overview', workspace: 'admin', title: 'Platform overview', spec: 'S23', status: 'Preview', note: 'Reporting definitions pending', nav: 'admin-overview' },
    { id: 'admin-users', workspace: 'admin', title: 'Users', spec: 'S24', status: 'Preview', note: 'CT12 + CT31 pending', nav: 'admin-users' },
    { id: 'admin-courses', workspace: 'admin', title: 'Courses', spec: 'S24', status: 'Preview', note: 'CT12 pending', nav: 'admin-courses' },
    { id: 'admin-reports', workspace: 'admin', title: 'Content reports', spec: 'S24', status: 'Preview', note: 'CT12 + Interaction pending', nav: 'admin-reports' },
    { id: 'admin-audit', workspace: 'admin', title: 'Audit log', spec: 'S24', status: 'Preview', note: 'CT12 + CT31 pending', nav: 'admin-audit' },
];
const LIVE: { title: string; spec: string; href: string; note: string }[] = [
    { title: 'Design preview (this page)', spec: 'S26', href: '/preview', note: 'Living reference with sample data' },
    { title: 'Landing page', spec: 'S25', href: '/', note: 'Public; signed-in visitors go to their start page' },
    { title: 'Course catalogue', spec: 'S3', href: '/courses', note: 'Real API: search, filter, paging' },
    { title: 'Sign in', spec: 'S2', href: '/login', note: 'Real API' }, { title: 'Create account', spec: 'S2', href: '/register', note: 'Real API' },
    { title: 'Learner home', spec: 'S6', href: '/home', note: 'Real API (active memberships); Learner sign-in required' },
    { title: 'My courses', spec: 'S5', href: '/my-courses', note: 'Real API; Learner sign-in required' }, { title: 'Profile', spec: 'S16', href: '/profile', note: 'Session snapshot; sign-in required' },
    { title: 'Page not found', spec: 'F14', href: '/missing-page', note: 'System state' },
];

const icon = (name: Parameters<typeof PresentationIcon>[0]['name']) => <PresentationIcon name={name} />;
const NAV: Record<Workspace, (NavItem & { screen: string; live?: string })[]> = {
    reference: [{ id: 'overview', screen: 'overview', label: 'Design system', icon: icon('layers') }],
    public: [{ id: 'landing', screen: 'landing', label: 'Landing', icon: icon('home') }],
    // DESIGN.md › Primary navigation, per role. "Catalogue" is the live public route, so it leaves the preview.
    learner: [{ id: 'learner-home', screen: 'learner-home', label: 'Home', icon: icon('home') }, { id: 'catalogue', screen: 'catalogue', live: '/courses', label: 'Catalogue', icon: icon('catalogue') },
        { id: 'my-courses', screen: 'my-courses', label: 'My courses', icon: icon('my-courses') }],
    instructor: [{ id: 'teaching', screen: 'teaching', label: 'Teaching', icon: icon('book') }, { id: 'grading', screen: 'grading', label: 'Grading', icon: icon('assignment') }, { id: 'learners', screen: 'learners', label: 'Learners', icon: icon('users') },
        { id: 'catalogue', screen: 'catalogue', live: '/courses', label: 'Catalogue', icon: icon('catalogue') }],
    admin: [{ id: 'admin-overview', screen: 'admin-overview', label: 'Overview', icon: icon('chart') }, { id: 'admin-users', screen: 'admin-users', label: 'Users', icon: icon('users') },
        { id: 'admin-courses', screen: 'admin-courses', label: 'Courses', icon: icon('catalogue') }, { id: 'admin-reports', screen: 'admin-reports', label: 'Reports', icon: icon('flag') }, { id: 'admin-audit', screen: 'admin-audit', label: 'Audit log', icon: icon('audit') }],
};
const PERSONA: Record<Workspace, { name: string; role: string }> = { reference: { name: 'Design preview', role: 'Sample data' }, public: { name: 'Visitor', role: 'Sample data' },
    learner: fx.sampleLearner, instructor: fx.sampleInstructor, admin: fx.sampleAdmin };
const WORKSPACE_HOME: Record<Workspace, string> = { reference: 'overview', public: 'landing', learner: 'learner-home', instructor: 'teaching', admin: 'admin-overview' };

/** Isolated design preview at the approved `/preview` route. It never reads AuthProvider or calls the API. */
export default function PreviewApp() {
    const { screen, item, tab, go } = usePreviewNav();
    const navigate = useNavigate(), location = useLocation();
    const [learner, setLearner] = useState(initialLearnerState), [instructor, setInstructor] = useState(initialInstructorState), [admin, setAdmin] = useState(initialAdminState);
    const [toast, setToast] = useState<string | null>(null), [resetKey, setResetKey] = useState(0);
    const reset = useCallback(() => { setLearner(initialLearnerState()); setInstructor(initialInstructorState()); setAdmin(initialAdminState()); setResetKey((value) => value + 1); setToast('Sample data reset.'); }, []);
    const context = useMemo(() => ({ notify: setToast, reset }), [reset]);
    const def = SCREENS.find((entry) => entry.id === screen) ?? SCREENS[0];
    // Preview screens change by query string; move focus to the new screen heading like route focus does.
    useLayoutEffect(() => {
        const heading = document.querySelector<HTMLElement>('main h1');
        if (heading) { heading.tabIndex = -1; heading.focus(); }
    }, [location.search]);
    const toIndex = () => go('overview');
    const groups: DirectoryGroup[] = [
        { title: 'Live screens', description: 'Production routes with the approved API. Opening one leaves the preview.', entries: LIVE.map((entry) => ({ id: entry.href, title: entry.title, spec: entry.spec, status: 'Live', note: entry.note, href: entry.href, onOpen: () => (entry.href === '/preview' ? go('overview') : navigate(entry.href)) })) },
        ...(['learner', 'instructor', 'admin', 'public'] as Workspace[]).map((workspace) => ({
            title: { learner: 'Learner', instructor: 'Instructor', admin: 'Administrator', public: 'Public and account', reference: '' }[workspace],
            description: { learner: 'Learning journey, assessment and Mastery Map with sample data.', instructor: 'Authoring, skills, grading and insights with sample data.', admin: 'Governance and reporting with sample data.', public: 'Group C proposals: landing, sign-in states and password recovery.', reference: '' }[workspace],
            entries: SCREENS.filter((entry) => entry.workspace === workspace).map((entry) => ({ id: entry.id, title: entry.title, spec: entry.spec, status: entry.status, note: entry.note,
                href: previewHref(entry.id), onOpen: () => go(entry.id) })),
        })),
    ];
    const workspace = def.workspace;
    let content: ReactNode;
    if (workspace === 'reference') content = <DesignReference groups={groups} />;
    else if (workspace === 'public') content = <PublicPreview screen={def.id} go={go} />;
    else if (workspace === 'learner') content = <LearnerPreview state={learner} setState={setLearner} screen={def.id} item={item} go={go} onIndex={toIndex} onReset={reset} />;
    else if (workspace === 'instructor') content = <InstructorPreview state={instructor} setState={setInstructor} screen={def.id} item={item} tab={tab} go={go} />;
    else content = <AdminPreview state={admin} setState={setAdmin} screen={def.id} />;
    const switcher = <SegmentedControl label="Preview workspace" value={workspace} onChange={(next) => go(WORKSPACE_HOME[next])}
        options={[{ value: 'reference', label: 'Index' }, { value: 'public', label: 'Public' }, { value: 'learner', label: 'Learner' }, { value: 'instructor', label: 'Instructor' }, { value: 'admin', label: 'Administrator' }]} />;
    const persona = PERSONA[workspace];
    const unread = learner.notifications.filter((entry) => !entry.read).length;
    return <PreviewContext.Provider value={context}>
        <Box key={resetKey}>
            {def.bare ? (def.id === 'quiz-attempt' ? content : <Box sx={{ position: 'relative' }}>
                <SampleDataBanner compact onIndex={toIndex} onReset={reset} tools={switcher} />
                {content}
            </Box>)
                : <DashboardLayout navItems={NAV[workspace].map(({ screen: target, live, ...entry }) => ({ ...entry, href: live ?? previewHref(target) }))} selectedNavId={def.nav}
                    onNavSelect={(id) => { const entry = NAV[workspace].find((candidate) => candidate.id === id); if (entry?.live) navigate(entry.live); else go(entry?.screen ?? 'overview'); }}
                    brandHref={previewHref('overview')} onBrandSelect={toIndex}
                    banner={<SampleDataBanner onIndex={workspace === 'reference' ? undefined : toIndex} onReset={reset} tools={switcher} />}
                    headerActions={<>
                        {workspace === 'learner' && <NotificationBell unread={unread} href={previewHref('notifications')} onClick={() => go('notifications')} />}
                        <AccountMenu displayName={persona.name} role={persona.role} renderItems={(close) => [
                            <MenuItem key="index" onClick={() => { close(); toIndex(); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="layers" sx={{ fontSize: 20 }} /></ListItemIcon>Preview index</MenuItem>,
                            <MenuItem key="reset" onClick={() => { close(); reset(); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="refresh" sx={{ fontSize: 20 }} /></ListItemIcon>Reset sample data</MenuItem>,
                        ]} />
                    </>}>
                    {content}
                </DashboardLayout>}
        </Box>
        <Snackbar open={!!toast} autoHideDuration={6000} onClose={(_, reason) => { if (reason !== 'clickaway') setToast(null); }} message={toast ?? ''} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} />
    </PreviewContext.Provider>;
}
