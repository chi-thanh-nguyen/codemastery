import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, MenuItem, Pagination, Paper, TextField, Typography } from '@mui/material';
import { Link } from 'react-router';
import { useAuth } from '../../auth/hooks/useAuth';
import { getCourseMembership, getMyEnrolledCourses, enrollInCourse, leaveCourse } from '../api/learningApi';
import type { CourseMembership, MembershipFilter, MembershipPage } from '../types/learningTypes';
import { ApiError } from '../../../shared/types/apiTypes';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { LoadingView, ErrorView, EmptyView } from '../../../shared/components/StatusViews';

const failure = (error: unknown) => error instanceof ApiError ? error.message : 'The request could not be completed. Please try again.';

/** Mounted with a course/session key by the router; no token leaves the Auth provider. */
export function CourseMembershipActions({ courseId }: { courseId: string }) {
    const auth = useAuth();
    const [membership, setMembership] = useState<CourseMembership | null>(null), [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false), [confirm, setConfirm] = useState(false), [revision, retry] = useState(0);
    const work = useRef<AbortController | null>(null), alive = useRef(false), pending = useRef(false);
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
        try { const value = await (joining ? enrollInCourse : leaveCourse)(courseId, { signal: controller.signal, isCurrent: current }); if (current()) setMembership(value); }
        catch (error) { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setError(failure(error)); }
        finally { pending.current = false; if (current()) setBusy(false); }
    }
    if (!auth.isAuthenticated) return <Button component={Link} to="/login" variant="contained" sx={{ mb: 2 }}>Sign in to enroll</Button>;
    if (!eligible) return <Alert severity="info" sx={{ mb: 2 }}>Course membership is available to learners.</Alert>;
    const joined = membership?.status === 'active' || membership?.status === 'completed';
    return <Box sx={{ mb: 3 }} aria-busy={busy}>
        {busy && <Typography role="status">Updating membership…</Typography>}
        {error && <Alert severity="error" action={<Button onClick={() => retry((value) => value + 1)} disabled={busy}>Retry</Button>}>{error}</Alert>}
        {membership && <Typography role="status" sx={{ mb: 1 }}>Membership: {membership.status}</Typography>}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {membership && <Button variant="contained" disabled={busy} onClick={() => joined ? setConfirm(true) : void mutate(true)}>
                {joined ? 'Leave course' : membership.status === 'left' ? 'Re-enroll' : 'Enroll in course'}
            </Button>}
            <Button component={Link} to="/my-courses">My Courses</Button>
        </Box>
        <ConfirmDialog open={confirm} title="Leave course?" message="Your learning history will be preserved. You can re-enroll while the course is published."
            confirmLabel="Leave course" isDestructive onCancel={() => setConfirm(false)} onConfirm={() => void mutate(false)} />
    </Box>;
}

export function MyCoursesPage() {
    const auth = useAuth(), generation = auth.generation;
    const [data, setData] = useState<MembershipPage | null>(null), [page, setPage] = useState(0);
    const [filter, setFilter] = useState<MembershipFilter>('joined'), [revision, retry] = useState(0);
    const [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null);
    useEffect(() => {
        let alive = true; const controller = new AbortController();
        const current = () => alive && auth.isCurrent(generation);
        setLoading(true); setError(null); setData(null);
        getMyEnrolledCourses(page, filter, { signal: controller.signal, isCurrent: current }).then((value) => { if (current()) setData(value); })
            .catch((error: unknown) => { if (current() && !(error instanceof ApiError && error.kind === 'cancelled')) setError(failure(error)); })
            .finally(() => { if (current()) setLoading(false); });
        return () => { alive = false; controller.abort(); };
    }, [page, filter, generation, revision]);
    return <Box><PageHeader title="My Courses" subtitle="Your published course memberships" actions={<Button onClick={() => retry((value) => value + 1)}>Refresh</Button>} />
        <TextField select label="Membership status" value={filter} sx={{ minWidth: 200, mb: 3 }} onChange={(event) => { setPage(0); setFilter(event.target.value as MembershipFilter); }}>
            <MenuItem value="joined">Joined</MenuItem><MenuItem value="active">Active</MenuItem><MenuItem value="completed">Completed</MenuItem><MenuItem value="left">Left</MenuItem><MenuItem value="all">All memberships</MenuItem>
        </TextField>
        {loading && <LoadingView message="Loading your courses…" />}
        {error && <ErrorView message={error} onRetry={() => retry((value) => value + 1)} />}
        {!loading && !error && data?.content.length === 0 && <EmptyView title="No courses found" message="Choose a different status or browse the catalogue." action={<Button component={Link} to="/courses">Browse courses</Button>} />}
        {data && <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' } }}>
            {data.content.map((course) => <Paper key={course.id} sx={{ p: 3, overflowWrap: 'anywhere' }}>
                <Typography variant="h6" component="h2">{course.title}</Typography><Typography>{course.description}</Typography>
                <Typography>Instructor: {course.instructorDisplayName ?? 'Instructor name unavailable'}</Typography>
                <Typography>Membership: {course.status}</Typography>
                <Button component={Link} to={`/courses/${encodeURIComponent(course.id)}`}>View course and membership</Button>
            </Paper>)}
        </Box>}
        {data && data.totalPages > 1 && <Pagination count={data.totalPages} page={page + 1} onChange={(_, next) => setPage(next - 1)} sx={{ mt: 3 }} />}
    </Box>;
}
