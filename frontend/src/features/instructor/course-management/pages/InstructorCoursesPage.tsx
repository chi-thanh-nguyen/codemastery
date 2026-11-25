import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Skeleton, TextField } from '@mui/material';
import { Link, useNavigate } from 'react-router';
import { Page, useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { GuidanceStack } from '../../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../../shared/components/GuidanceStack';
import { CourseTile, Tile } from '../../../../shared/components/Tile';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView, ErrorView, SystemStateView } from '../../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { PublicationPill } from '../components/PublishToggle';
import type { InstructorCourseView } from '../types/courseManagementTypes';
import { ApiError } from '../../../../shared/types/apiTypes';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown } from '../hooks/useAssessmentEditor';
import { useCourseCreation, useOwnedCourses } from '../hooks/useCourseEditor';
import { errorMessage } from './QuestionBankPage';

export const courseEditorPath = (courseId: string) => `/teach/courses/${encodeURIComponent(courseId)}`;

/** S17 Teaching home (production): owned courses with publication pills, create action and pagination. */
export function TeachingHomePage() {
    const navigate = useNavigate();
    const [page, setPage] = useState(0);
    const courses = useOwnedCourses(page);
    const status = failureStatus(courses.error);

    if (status === 403) {
        return <Page><SystemStateView icon="shield" title="You don't have access to this page"
            message="This page is for instructors."
            actions={<Button component={Link} to="/courses" variant="contained">Go to catalogue</Button>} /></Page>;
    }

    const data = courses.data;
    return <Page>
        <PageHeader title="Teaching"
            actions={<Button component={Link} to="/teach/courses/new" variant="contained" startIcon={<PresentationIcon name="plus" />}>Create course</Button>} />
        {courses.error
            ? <ErrorView title="We couldn't load your courses." message={errorMessage(courses.error)} onRetry={courses.reload} />
            : !data
                ? <Box role="status" aria-label="Loading your courses" sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
                    {[0, 1].map((item) => <Tile key={item} sx={{ display: 'grid', gap: 1.5 }}><Skeleton width="60%" height={28} /><Skeleton width="90%" /><Skeleton width="40%" /></Tile>)}
                </Box>
                : data.content.length === 0
                    ? <EmptyView icon="book" title="You haven't created a course yet"
                        message="Create your first course to start adding lessons."
                        action={<Button component={Link} to="/teach/courses/new" variant="outlined">Create course</Button>} />
                    : <Box sx={{ display: 'grid', gap: 3 }}>
                        <Box component="ul" aria-label="Your courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
                            {data.content.map((course) => <Box component="li" key={course.id} sx={{ minWidth: 0 }}>
                                <CourseTile
                                    title={course.title}
                                    description={course.description}
                                    href={courseEditorPath(course.id)}
                                    onOpen={() => navigate(courseEditorPath(course.id))}
                                    status={<PublicationPill publication={course.publicationStatus} />} />
                            </Box>)}
                        </Box>
                        <PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements}
                            pageSize={data.size} itemsOnPage={data.content.length} onChange={setPage} noun="courses" nounOne="course" />
                    </Box>}
    </Page>;
}

/** S17 `/teach/courses/new`: creates a hidden course and replaces the URL with its editor route. */
export function CreateCoursePage() {
    const navigate = useNavigate();
    const creation = useCourseCreation();
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [prerequisites, setPrerequisites] = useState('');
    const [dirty, setDirty] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [alert, setAlert] = useState<{ kind: 'unknown' | 'error'; message?: string } | null>(null);
    // The pending navigation that "Leave" completes. Back, Cancel and the shell's own destinations (primary
    // navigation, the home link and account-menu links, U3) share this one confirmation; sign-out and session end
    // never ask, and a successful creation clears the draft before it replaces the URL.
    const [leaveTarget, setLeaveTarget] = useState<{ go: () => void } | null>(null);
    const dirtyRef = useRef(dirty);
    dirtyRef.current = dirty;
    useNavigationGuard((proceed) => {
        if (!dirtyRef.current) return false;
        setLeaveTarget({ go: proceed });
        return true;
    });

    useEffect(() => {
        if (!dirty) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const goBack = () => {
        if (dirtyRef.current) setLeaveTarget({ go: () => navigate('/teach') });
        else navigate('/teach');
    };

    async function submit() {
        const next: Record<string, string> = {};
        if (!/\S/.test(title)) next.title = 'Enter a course title.';
        if (!/\S/.test(description)) next.description = 'Enter a course description.';
        setErrors(next);
        if (Object.keys(next).length) return;
        setAlert(null);
        try {
            const created = await creation.create({
                title: title.trim(),
                description: description.trim(),
                prerequisiteDescription: /\S/.test(prerequisites) ? prerequisites.trim() : null,
            });
            setDirty(false);
            dirtyRef.current = false;
            navigate(courseEditorPath(created.id), { replace: true });
        } catch (error) {
            if (isCancelled(error)) return;
            const fields = fieldErrorsFrom(error, {
                title: 'title',
                description: 'description',
                prerequisiteDescription: 'prerequisites',
            });
            if (Object.keys(fields).length) {
                setErrors(fields);
                return;
            }
            if (outcomeUnknown(error)) {
                setAlert({ kind: 'unknown' });
                return;
            }
            setAlert({ kind: 'error', message: error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.' });
        }
    }

    return <Page width="reading">
        <PageHeader title="Create course" back={{ label: 'Teaching', href: '/teach', onClick: goBack }} />
        {alert?.kind === 'unknown' && <Alert severity="warning" sx={{ mb: 3 }}
            action={<Button size="small" variant="text" onClick={goBack}>Go to Teaching</Button>}>
            We couldn&apos;t confirm whether the course was created. Check Teaching before creating it again.
        </Alert>}
        {alert?.kind === 'error' && <Alert severity="error" sx={{ mb: 3 }}>{alert.message}</Alert>}
        <Tile padding={{ xs: 3, sm: 4 }}>
            <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); void submit(); }}
                sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
                <TextField label="Course title" value={title} required disabled={creation.busy}
                    onChange={(event) => { setTitle(event.target.value); setDirty(true); setErrors({}); }}
                    error={!!errors.title} helperText={errors.title} />
                <TextField label="Description" multiline minRows={4} value={description} required disabled={creation.busy}
                    onChange={(event) => { setDescription(event.target.value); setDirty(true); setErrors({}); }}
                    error={!!errors.description} helperText={errors.description ?? 'Shown on the course detail page.'} />
                <TextField label="Prerequisites (optional)" multiline minRows={2} value={prerequisites} disabled={creation.busy}
                    onChange={(event) => { setPrerequisites(event.target.value); setDirty(true); setErrors({}); }}
                    error={!!errors.prerequisites} helperText={errors.prerequisites ?? 'A learner-facing description. It never blocks enrolment.'} />
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
                    <Button type="submit" variant="contained" disabled={creation.busy} aria-busy={creation.busy || undefined}>Create course</Button>
                    <Button variant="outlined" onClick={goBack} disabled={creation.busy}>Cancel</Button>
                </Box>
            </Box>
        </Tile>
        <ConfirmDialog open={leaveTarget !== null} isDestructive title="Leave without saving?" message="Your changes will be lost."
            cancelLabel="Stay" confirmLabel="Leave" onCancel={() => setLeaveTarget(null)}
            onConfirm={() => { const next = leaveTarget; setLeaveTarget(null); setDirty(false); dirtyRef.current = false; next?.go(); }} />
    </Page>;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

export interface InstructorCoursesPageProps { instructorName: string; courses: InstructorCourseView[]; attention: GuidanceEntryData[]; onOpen: (courseId: string) => void; onCreate: () => void }

/** S17 Teaching home preview component. */
export function InstructorCoursesPage({ instructorName, courses, attention, onOpen, onCreate }: InstructorCoursesPageProps) {
    return <Page aside={<GuidanceStack title="Needs attention" entries={attention} emptyText="Nothing else needs your attention." />} asideLabel="Needs attention">
        <PageHeader title="Teaching" subtitle={`Courses you own, ${instructorName}. Learners see only published content.`}
            actions={<Button variant="contained" onClick={onCreate} startIcon={<PresentationIcon name="plus" />}>Create course</Button>} />
        {courses.length === 0 ? <EmptyView icon="book" title="You don't own a course yet" message="Create a course, add chapters and lessons, then publish it when it's ready." action={<Button variant="outlined" onClick={onCreate}>Create course</Button>} />
            : <Box component="ul" aria-label="Your courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
                {courses.map((course) => <Box component="li" key={course.id} sx={{ minWidth: 0 }}>
                    <CourseTile title={course.title} description={course.description} href={`#course-${course.id}`} onOpen={() => onOpen(course.id)}
                        byline={`${course.lessonCount} lessons${course.learnerCount !== undefined ? ` · ${course.learnerCount} enrolled learners` : ''}`}
                        status={<PublicationPill publication={course.publication} />} />
                </Box>)}
            </Box>}
    </Page>;
}
