import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, IconButton, ListItemIcon, Menu, MenuItem, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../auth/hooks/useAuth';
import { getCourseMembership, getMyEnrolledCourses, enrollInCourse, leaveCourse } from '../api/learningApi';
import type { CourseMembership, MembershipCourse, MembershipFilter, MembershipPage, MembershipStatus } from '../types/learningTypes';
import { ApiError } from '../../../shared/types/apiTypes';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { LoadingView, ErrorView, EmptyView } from '../../../shared/components/StatusViews';
import { StatusPill } from '../../../shared/components/StatusPill';
import type { PillTone } from '../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import type { PresentationIconName } from '../../../shared/components/PresentationIcon';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { PaginationBar, TableToolbar } from '../../../shared/components/DataTable';
import { Page } from '../../../layouts/DashboardLayout';
import { CourseTile } from '../../../shared/components/Tile';
import { color } from '../../../app/theme/theme';

const failure = (error: unknown) => error instanceof ApiError ? error.message : 'The request could not be completed. Please try again.';

/** Mounted with a course/session key by the router; no token leaves the Auth provider. */
export function CourseMembershipActions({ courseId }: { courseId: string }) {
    const auth = useAuth();
    const [membership, setMembership] = useState<CourseMembership | null>(null), [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false), [confirm, setConfirm] = useState(false), [revision, retry] = useState(0);
    const work = useRef<AbortController | null>(null), alive = useRef(false), pending = useRef(false);
    const primary = useRef<HTMLButtonElement>(null), restoreFocus = useRef(false);
    const generation = auth.generation;
    const eligible = auth.isAuthenticated && auth.user?.role === 'Learner';
    useEffect(() => {
        alive.current = true;
        if (!eligible) return () => { alive.current = false; };
        const controller = new AbortController(); work.current = controller;
        const current = () => alive.current && work.current === controller && auth.isCurrent(generation);
        setBusy(true); setError(null);
        getCourseMembership(courseId, { signal: controller.signal, isCurrent: current }).then((value) => { if (current()) setMembership(value); })
            .catch((error: unknown) => { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setError(failure(error)); })
            .finally(() => { if (current()) setBusy(false); });
        return () => { alive.current = false; work.current?.abort(); };
    }, [courseId, generation, eligible, revision]);
    async function mutate(joining: boolean) {
        if (pending.current || busy || !eligible) return;
        pending.current = true; setConfirm(false); setBusy(true); setError(null);
        work.current?.abort(); const controller = new AbortController(); work.current = controller;
        const current = () => alive.current && work.current === controller && auth.isCurrent(generation);
        try { const value = await (joining ? enrollInCourse : leaveCourse)(courseId, { signal: controller.signal, isCurrent: current }); if (current()) { restoreFocus.current = true; setMembership(value); } }
        catch (error) { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setError(failure(error)); }
        finally { pending.current = false; if (current()) setBusy(false); }
    }
    useEffect(() => {
        // The action button changes with the new state; return focus to it only if focus was lost, never stealing it.
        if (!busy && restoreFocus.current) { restoreFocus.current = false; if (document.activeElement === document.body) primary.current?.focus(); }
    }, [busy, membership]);
    if (!auth.isAuthenticated) return <Button component={Link} to="/login" variant="contained">Sign in to enroll</Button>;
    if (!eligible) return <Alert severity="info" sx={{ maxWidth: 420 }}>Course membership is available to learners.</Alert>;
    const joined = membership?.status === 'active' || membership?.status === 'completed';
    return <Box aria-busy={busy} sx={{ display: 'grid', gap: 1.5, justifyItems: { xs: 'stretch', md: 'end' } }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5 }}>
            {membership && <Box component="span" role="status"><span className="cm-visually-hidden">Membership: </span><MembershipPill status={membership.status} /></Box>}
            {membership && (joined
                ? <Button ref={primary} variant="outlined" disabled={busy} onClick={() => setConfirm(true)}>Leave course</Button>
                : <Button ref={primary} variant="contained" disabled={busy} onClick={() => void mutate(true)}>{membership.status === 'left' ? 'Re-enroll' : 'Enroll in course'}</Button>)}
            <Button component={Link} to="/my-courses" variant={joined ? 'contained' : 'outlined'}>My courses</Button>
        </Box>
        {busy && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>{membership ? 'Updating membership…' : 'Checking membership…'}</Typography>}
        {error && <Alert severity="error" sx={{ maxWidth: 480 }} action={<Button variant="text" size="small" onClick={() => retry((value) => value + 1)} disabled={busy}>Try again</Button>}>{error}</Alert>}
        <ConfirmDialog open={confirm} title="Leave course?" message="Your learning history will be preserved. You can re-enroll while the course is published."
            confirmLabel="Leave course" isDestructive onCancel={() => setConfirm(false)} onConfirm={() => void mutate(false)} />
    </Box>;
}

const membershipLabels: Record<CourseMembership['status'], { label: string; tone: PillTone; icon: PresentationIconName }> = {
    none: { label: 'Not enrolled', tone: 'dashed', icon: 'dashed-circle' },
    active: { label: 'Active', tone: 'neutral', icon: 'my-courses' },
    completed: { label: 'Completed', tone: 'mint', icon: 'check-circle' },
    left: { label: 'Left', tone: 'dashed', icon: 'sign-out' },
};
/** Membership is not progress or mastery: its own wording and pill (DESIGN.md › Domain Presentation Rules). */
export function MembershipPill({ status }: { status: CourseMembership['status'] }) {
    const meta = membershipLabels[status];
    return <StatusPill tone={meta.tone} icon={meta.icon}>{meta.label}</StatusPill>;
}

/** Tile overflow menu (DESIGN.md › S5): course details plus Leave course, or Re-enroll for a left course. */
export function MembershipTileMenu({ title, status, detailsHref, onDetails, onLeave, onReenroll, disabled }: {
    title: string; status: MembershipStatus; detailsHref: string; onDetails?: () => void; onLeave: () => void; onReenroll: () => void; disabled?: boolean;
}) {
    const [anchor, setAnchor] = useState<HTMLElement | null>(null);
    const close = () => setAnchor(null);
    return <>
        <IconButton aria-label={`More actions for ${title}`} aria-haspopup="menu" aria-expanded={anchor ? true : undefined} disabled={disabled}
            onClick={(event) => setAnchor(event.currentTarget)} sx={{ width: 44, height: 44, bgcolor: color.fill150, color: color.ink900, '&:hover': { bgcolor: color.line200 } }}>
            <PresentationIcon name="more" />
        </IconButton>
        <Menu anchorEl={anchor} open={!!anchor} onClose={close} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
            <MenuItem component={Link} to={detailsHref} onClick={(event) => { close(); if (onDetails) { event.preventDefault(); onDetails(); } }}>
                <ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="eye" sx={{ fontSize: 20 }} /></ListItemIcon>View course details
            </MenuItem>
            {status === 'left'
                ? <MenuItem onClick={() => { close(); onReenroll(); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="refresh" sx={{ fontSize: 20 }} /></ListItemIcon>Re-enroll</MenuItem>
                : <MenuItem onClick={() => { close(); onLeave(); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="sign-out" sx={{ fontSize: 20 }} /></ListItemIcon>Leave course</MenuItem>}
        </Menu>
    </>;
}

/** One joined course: the tile opens course details; the overflow menu changes membership. */
export function MembershipCourseTile({ course, href, onOpen, menu }: { course: Pick<MembershipCourse, 'title' | 'description' | 'instructorDisplayName' | 'status'>; href: string; onOpen: () => void; menu?: ReactNode }) {
    return <CourseTile title={course.title} description={course.description} byline={`Instructor · ${course.instructorDisplayName ?? 'Instructor name unavailable'}`}
        href={href} onOpen={onOpen} extraActions={menu}
        status={<><span className="cm-visually-hidden">Membership: </span><MembershipPill status={course.status} /></>} />;
}

export function MyCoursesPage() {
    const auth = useAuth(), generation = auth.generation;
    const [data, setData] = useState<MembershipPage | null>(null), [page, setPage] = useState(0);
    const [filter, setFilter] = useState<MembershipFilter>('joined'), [revision, retry] = useState(0);
    const [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null);
    useEffect(() => {
        let alive = true, recovering = false; const controller = new AbortController();
        const current = () => alive && auth.isCurrent(generation);
        setLoading(true); setError(null); setData(null);
        getMyEnrolledCourses(page, filter, { signal: controller.signal, isCurrent: current }).then((value) => {
            if (!current()) return;
            if (value.totalPages > 0 && page >= value.totalPages) {
                recovering = true;
                setPage(value.totalPages - 1);
                return;
            }
            setData(value);
        })
            .catch((error: unknown) => { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setError(failure(error)); })
            .finally(() => { if (current() && !recovering) setLoading(false); });
        return () => { alive = false; controller.abort(); };
    }, [page, filter, generation, revision]);
    const navigate = useNavigate();
    // Tile membership changes reuse the approved CT22.membership PUT/DELETE calls; the list is then re-read from the server.
    const [leaving, setLeaving] = useState<MembershipCourse | null>(null), [mutating, setMutating] = useState<string | null>(null);
    const [notice, setNotice] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
    const noticeRef = useRef<HTMLDivElement>(null), mounted = useRef(true), pending = useRef(false), focusNotice = useRef(false), mutation = useRef<AbortController | null>(null);
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; mutation.current?.abort(); }; }, []);
    async function changeMembership(course: MembershipCourse, joining: boolean) {
        if (pending.current) return;
        pending.current = true; setLeaving(null); setMutating(course.id); setNotice(null);
        const controller = new AbortController(); mutation.current = controller;
        const current = () => mounted.current && mutation.current === controller && auth.isCurrent(generation);
        try {
            await (joining ? enrollInCourse : leaveCourse)(course.id, { signal: controller.signal, isCurrent: current });
            if (!current()) return;
            setNotice({ severity: 'success', text: joining ? `You re-enrolled in ${course.title}.` : `You left ${course.title}. It's listed under Left, where you can re-enroll.` });
            focusNotice.current = true; retry((value) => value + 1);
        } catch (error) {
            if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) { setNotice({ severity: 'error', text: failure(error) }); focusNotice.current = true; }
        } finally { pending.current = false; if (mounted.current) setMutating(null); }
    }
    useEffect(() => {
        // The changed tile may leave this filter; if focus was lost with it, move focus to the outcome message.
        if (!loading && focusNotice.current && notice) {
            focusNotice.current = false;
            if (!document.activeElement || document.activeElement === document.body) noticeRef.current?.focus();
        }
    }, [loading, notice, data]);
    const empty: Record<MembershipFilter, { title: string; message: string }> = {
        joined: { title: "You haven't joined a course yet", message: 'Browse the catalogue to find one.' },
        active: { title: 'No active courses', message: 'Courses you are taking appear here.' },
        completed: { title: 'No completed courses yet', message: 'Courses you complete appear here.' },
        left: { title: 'No courses left', message: 'Courses you leave stay here so you can re-enroll.' },
        all: { title: "You haven't joined a course yet", message: 'Browse the catalogue to find one.' },
    };
    return <Page>
        <PageHeader title="My courses" subtitle="Published courses you have joined. Your learning history stays here if you leave a course."
            actions={<Button variant="outlined" onClick={() => retry((value) => value + 1)} startIcon={<PresentationIcon name="refresh" />}>Refresh</Button>} />
        <TableToolbar count={data ? `${data.totalElements} ${data.totalElements === 1 ? 'course' : 'courses'}` : undefined}>
            <SegmentedControl label="Membership status" value={filter} onChange={(next) => { setPage(0); setFilter(next); }}
                options={[{ value: 'joined', label: 'Joined' }, { value: 'active', label: 'Active' }, { value: 'completed', label: 'Completed' }, { value: 'left', label: 'Left' }, { value: 'all', label: 'All' }]} />
        </TableToolbar>
        <Box ref={noticeRef} tabIndex={-1} data-focus-target role={notice?.severity === 'error' ? 'alert' : 'status'} sx={{ '&:empty': { display: 'none' }, mb: notice ? 3 : 0 }}>
            {notice && <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert>}
        </Box>
        {loading && <LoadingView message="Loading your courses…" variant="tiles" count={2} />}
        {error && <ErrorView title="Couldn't load your courses" message={error} onRetry={() => retry((value) => value + 1)} />}
        {!loading && !error && data?.totalElements === 0 && <EmptyView icon="my-courses" title={empty[filter].title} message={empty[filter].message}
            action={<Button component={Link} to="/courses" variant="contained">Browse courses</Button>} />}
        {data && data.content.length > 0 && <Box component="ul" aria-label="Your courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, mb: 4, gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))' } }}>
            {data.content.map((course) => {
                const href = `/courses/${encodeURIComponent(course.id)}`;
                return <Box component="li" key={course.id} sx={{ minWidth: 0 }} aria-busy={mutating === course.id || undefined}>
                    <MembershipCourseTile course={course} href={href} onOpen={() => navigate(href)}
                        menu={<MembershipTileMenu title={course.title} status={course.status} detailsHref={href} onDetails={() => navigate(href)} disabled={mutating !== null}
                            onLeave={() => setLeaving(course)} onReenroll={() => void changeMembership(course, true)} />} />
                </Box>;
            })}
        </Box>}
        {data && data.totalElements > 0 && <PaginationBar page={page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length} onChange={setPage} noun="courses" nounOne="course" />}
        {mutating && <Typography role="status" className="cm-visually-hidden">Updating membership…</Typography>}
        <ConfirmDialog open={!!leaving} title="Leave course?" message="Your learning history will be preserved. You can re-enroll while the course is published."
            confirmLabel="Leave course" isDestructive busy={mutating !== null} onCancel={() => setLeaving(null)} onConfirm={() => { if (leaving) void changeMembership(leaving, false); }} />
    </Page>;
}
