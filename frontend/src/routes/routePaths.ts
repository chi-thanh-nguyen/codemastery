import { matchRoutes } from 'react-router';
import type { RouteObject } from 'react-router';
import type { UserRole } from '../features/auth/types/authTypes';
import type { PresentationIconName } from '../shared/components/PresentationIcon';

export const ROUTE_PATHS = {
    HOME: '/',
    LEARNER_HOME: '/home',
    LOGIN: '/login',
    REGISTER: '/register',
    COURSES: '/courses',
    COURSE_DETAIL: '/courses/:courseId',
    PROFILE: '/profile',
    MY_COURSES: '/my-courses',
    TEACH: '/teach',
    TEACH_COURSE_NEW: '/teach/courses/new',
    TEACH_COURSE: '/teach/courses/:courseId',
    TEACH_COURSE_CONTENT: '/teach/courses/:courseId/content',
    TEACH_COURSE_SKILLS: '/teach/courses/:courseId/skills',
    TEACH_COURSE_ASSESSMENTS: '/teach/courses/:courseId/assessments',
    TEACH_COURSE_SETTINGS: '/teach/courses/:courseId/settings',
    TEACH_QUIZ: '/teach/courses/:courseId/quizzes/:quizId',
    TEACH_ASSIGNMENT: '/teach/courses/:courseId/assignments/:assignmentId',
    COURSE_LEARN: '/courses/:courseId/learn',
    COURSE_LESSON: '/courses/:courseId/lessons/:lessonId',
    COURSE_DIAGNOSTIC: '/courses/:courseId/diagnostic',
    COURSE_MASTERY: '/courses/:courseId/mastery',
    COURSE_ASSESSMENTS: '/courses/:courseId/assessments',
    COURSE_QUIZ: '/courses/:courseId/quizzes/:quizId',
    COURSE_QUIZ_ATTEMPT: '/courses/:courseId/quizzes/:quizId/attempt',
    COURSE_QUIZ_HISTORY: '/courses/:courseId/quizzes/:quizId/history',
    COURSE_ATTEMPT: '/courses/:courseId/attempts/:attemptId',
    COURSE_ASSIGNMENT: '/courses/:courseId/assignments/:assignmentId',
    TEACH_LESSON: '/teach/courses/:courseId/lessons/:lessonId',
    TEACH_QA: '/teach/courses/:courseId/qa',
    TEACH_COURSE_LEARNERS: '/teach/courses/:courseId/learners',
    TEACH_COURSE_LEARNER: '/teach/courses/:courseId/learners/:learnerId',
    TEACH_GRADING: '/teach/grading',
    TEACH_SUBMISSION: '/teach/grading/:submissionId',
    TEACH_LEARNERS: '/teach/learners',
    ADMIN: '/admin',
    ADMIN_USERS: '/admin/users',
    ADMIN_COURSES: '/admin/courses',
    ADMIN_REPORTS: '/admin/reports',
    ADMIN_AUDIT: '/admin/audit',
    NOTIFICATIONS: '/notifications',
    PREVIEW: '/preview',
} as const;

/**
 * The production workspace's React Router tree. AppRouter binds these IDs to the delivered
 * elements; navigation and start pages inspect this same tree, never a second live-path list.
 * Screen owners add their route here only after all three CT16.routes activation gates hold.
 * Approved constants and preview screens above do not register a production destination.
 */
export const WORKSPACE_ROUTES: RouteObject[] = [
    { id: 'catalogue', path: ROUTE_PATHS.COURSES, children: [{ id: 'catalogue-index', index: true }] },
    { id: 'course-detail', path: ROUTE_PATHS.COURSE_DETAIL },
    { id: 'learner', handle: { roles: ['Learner'] }, children: [
        { id: 'learner-home', path: ROUTE_PATHS.LEARNER_HOME },
        { id: 'my-courses', path: ROUTE_PATHS.MY_COURSES },
    ] },
    { id: 'notifications', path: ROUTE_PATHS.NOTIFICATIONS, handle: { roles: ['Learner', 'Instructor'] } },
    { id: 'profile', path: ROUTE_PATHS.PROFILE, handle: { protected: true } },
    { id: 'instructor', handle: { roles: ['Instructor'] }, children: [
        { id: 'teaching', path: ROUTE_PATHS.TEACH },
        { id: 'grading', path: ROUTE_PATHS.TEACH_GRADING },
        { id: 'grade-submission', path: ROUTE_PATHS.TEACH_SUBMISSION },
        { id: 'create-course', path: ROUTE_PATHS.TEACH_COURSE_NEW },
        { id: 'course-editor', path: ROUTE_PATHS.TEACH_COURSE, children: [
            { id: 'course-frame', children: [
                { id: 'course-content-index', index: true },
                { id: 'course-content', path: ROUTE_PATHS.TEACH_COURSE_CONTENT },
                { id: 'course-skills', path: ROUTE_PATHS.TEACH_COURSE_SKILLS },
                { id: 'course-assessments', path: ROUTE_PATHS.TEACH_COURSE_ASSESSMENTS },
                { id: 'course-settings', path: ROUTE_PATHS.TEACH_COURSE_SETTINGS },
            ] },
            { id: 'course-lesson', path: ROUTE_PATHS.TEACH_LESSON },
            { id: 'course-quiz', path: ROUTE_PATHS.TEACH_QUIZ },
            { id: 'course-assignment', path: ROUTE_PATHS.TEACH_ASSIGNMENT },
        ] },
    ] },
];

/** Native route metadata shared by production guard composition and availability checks. */
export interface WorkspaceHandle { roles?: readonly UserRole[]; protected?: boolean }
function accessible(matches: ReturnType<typeof matchRoutes>, role?: UserRole) {
    return matches !== null && matches.every(({ route }) => {
        const handle = route.handle as WorkspaceHandle | undefined;
        return (!handle?.protected || !!role) && (!handle?.roles || !!role && handle.roles.includes(role));
    });
}
export function isDestinationLive(path: string, role?: UserRole, routes: RouteObject[] = WORKSPACE_ROUTES) {
    return accessible(matchRoutes(routes, path), role);
}

export type RoleStartPath = '/home' | '/teach' | '/admin' | '/courses';
/** Only approved start destinations; there is no caller-supplied redirect target. */
export function roleStartPath(role?: UserRole, routes: RouteObject[] = WORKSPACE_ROUTES): RoleStartPath {
    if (role === 'Learner') return ROUTE_PATHS.LEARNER_HOME;
    if (role === 'Instructor' && isDestinationLive(ROUTE_PATHS.TEACH, role, routes)) return ROUTE_PATHS.TEACH;
    if (role === 'Administrator' && isDestinationLive(ROUTE_PATHS.ADMIN, role, routes)) return ROUTE_PATHS.ADMIN;
    return ROUTE_PATHS.COURSES;
}

interface Destination { id: string; href: string; label: string; icon: PresentationIconName }
const destination = (href: string, label: string, icon: PresentationIconName): Destination => ({ id: href, href, label, icon });
const catalogue = destination(ROUTE_PATHS.COURSES, 'Catalogue', 'catalogue');
/** CT16.routes order. The bell is a separate FE28 utility, never a primary-navigation row. */
const ROLE_NAVIGATION: Record<UserRole, Destination[]> = {
    Learner: [destination(ROUTE_PATHS.LEARNER_HOME, 'Home', 'home'), catalogue,
        destination(ROUTE_PATHS.MY_COURSES, 'My courses', 'my-courses')],
    Instructor: [destination(ROUTE_PATHS.TEACH, 'Teaching', 'book'), destination(ROUTE_PATHS.TEACH_GRADING, 'Grading', 'assignment'),
        destination(ROUTE_PATHS.TEACH_LEARNERS, 'Learners', 'users'), catalogue],
    Administrator: [destination(ROUTE_PATHS.ADMIN, 'Overview', 'chart'), destination(ROUTE_PATHS.ADMIN_USERS, 'Users', 'users'),
        destination(ROUTE_PATHS.ADMIN_COURSES, 'Courses', 'catalogue'), destination(ROUTE_PATHS.ADMIN_REPORTS, 'Reports', 'flag'),
        destination(ROUTE_PATHS.ADMIN_AUDIT, 'Audit log', 'audit'), catalogue],
};
export function roleNavigation(role?: UserRole, routes: RouteObject[] = WORKSPACE_ROUTES) {
    return (role ? ROLE_NAVIGATION[role] : [catalogue]).filter(({ href }) => isDestinationLive(href, role, routes));
}
/** FE28 owns the delivered bell/page. This predicate is its activation and current-state handoff. */
export function notificationsAvailable(role?: UserRole, routes: RouteObject[] = WORKSPACE_ROUTES) {
    return (role === 'Learner' || role === 'Instructor') && isDestinationLive(ROUTE_PATHS.NOTIFICATIONS, role, routes);
}

const learnerWorkspace = [ROUTE_PATHS.COURSE_LEARN, ROUTE_PATHS.COURSE_LESSON, ROUTE_PATHS.COURSE_DIAGNOSTIC,
    ROUTE_PATHS.COURSE_MASTERY, ROUTE_PATHS.COURSE_ASSESSMENTS, ROUTE_PATHS.COURSE_QUIZ, ROUTE_PATHS.COURSE_QUIZ_ATTEMPT,
    ROUTE_PATHS.COURSE_QUIZ_HISTORY, ROUTE_PATHS.COURSE_ATTEMPT, ROUTE_PATHS.COURSE_ASSIGNMENT];
const teachingWorkspace = [ROUTE_PATHS.TEACH_COURSE_NEW, ROUTE_PATHS.TEACH_COURSE, ROUTE_PATHS.TEACH_COURSE_CONTENT,
    ROUTE_PATHS.TEACH_COURSE_SETTINGS, ROUTE_PATHS.TEACH_LESSON, ROUTE_PATHS.TEACH_COURSE_SKILLS,
    ROUTE_PATHS.TEACH_COURSE_ASSESSMENTS, ROUTE_PATHS.TEACH_QUIZ, ROUTE_PATHS.TEACH_ASSIGNMENT, ROUTE_PATHS.TEACH_QA,
    ROUTE_PATHS.TEACH_COURSE_LEARNERS, ROUTE_PATHS.TEACH_COURSE_LEARNER];
const SECTION_ROUTES: RouteObject[] = [
    { path: ROUTE_PATHS.COURSE_DETAIL, handle: ROUTE_PATHS.COURSES },
    ...Object.values(ROLE_NAVIGATION).flat().map(({ href }) => ({ path: href, handle: href })),
    ...learnerWorkspace.map((path) => ({ path, handle: ROUTE_PATHS.MY_COURSES })),
    ...teachingWorkspace.map((path) => ({ path, handle: ROUTE_PATHS.TEACH })),
    { path: ROUTE_PATHS.TEACH_SUBMISSION, handle: ROUTE_PATHS.TEACH_GRADING },
    { path: ROUTE_PATHS.NOTIFICATIONS, handle: ROUTE_PATHS.NOTIFICATIONS },
];
/**
 * Match exactly as the router does (ranking, segment decoding, case, trailing slash). A route
 * must also be delivered and eligible, and its parent item live for this role. Unknown paths
 * have no current item. Query/fragment are ignored by matchRoutes itself.
 */
export function currentSection(pathname: string, role?: UserRole, routes: RouteObject[] = WORKSPACE_ROUTES): string | undefined {
    if (!isDestinationLive(pathname, role, routes)) return undefined;
    const matches = matchRoutes(SECTION_ROUTES, pathname);
    const section = matches?.at(-1)?.route.handle as string | undefined;
    if (section === ROUTE_PATHS.NOTIFICATIONS) return notificationsAvailable(role, routes) ? section : undefined;
    return roleNavigation(role, routes).some(({ id }) => id === section) ? section : undefined;
}
