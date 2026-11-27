import { useState } from 'react';
import { Box, Button, MenuItem, TextField, Typography } from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { DataTable, PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView, ErrorView, LoadingView } from '../../../../shared/components/StatusViews';
import { useAuth } from '../../../auth/hooks/useAuth';
import { useOwnedCourses } from '../../course-management/hooks/useCourseEditor';
import { useOwnerRead } from '../../course-management/hooks/useAssessmentEditor';
import { getOwnedCourse } from '../../course-management/api/instructorCourseApi';
import type { AuthoringRequestOptions } from '../../course-management/api/assessmentAuthoringApi';
import { listOwnedAssignments, readOwnedAssignment } from '../../course-management/api/assessmentAuthoringApi';
import { CourseUnavailable, courseRefused, errorMessage, formatDeadlineDisplay } from '../../course-management/pages/QuestionBankPage';
import { failureStatus } from '../../course-management/hooks/useAssessmentEditor';
import { gradingId } from '../api/gradingApi';
import { gradingPath, reviewedState, useSubmissions } from '../hooks/useSubmissions';
import { SubmissionTable } from '../components/SubmissionTable';
import type { SubmissionRow, SubmissionSummary } from '../types/gradingTypes';

export function toSubmissionRow(row: SubmissionSummary, title: string): SubmissionRow {
    return { id: row.id, learnerName: row.learnerDisplayName ?? 'Name unavailable', assignmentTitle: title, courseTitle: '',
        submittedAtLabel: formatDeadlineDisplay(row.submittedAt).absolute, fileName: row.originalFilename,
        ...(row.graded ? { graded: { grade: row.gradePercentage!, feedback: row.feedback!, gradedAtLabel: formatDeadlineDisplay(row.gradedAt!).absolute } } : {}) };
}
/** Presentation-only entry for the isolated preview. All production reads live below. */
export function SubmissionListPage({ rows, onOpen }: { rows: SubmissionRow[]; onOpen: (id: string) => void }) {
    return <Page><PageHeader title="Grading" subtitle="File submissions from learners in your courses." /><SubmissionTable rows={rows} onOpen={onOpen} emptyTitle="No submissions yet" /></Page>;
}
export function ProductionSubmissionListPage() {
    const auth = useAuth(), [params, setParams] = useSearchParams();
    const course = params.get('courseId'), assignment = params.get('assignmentId');
    const invalid = params.getAll('courseId').length > 1 || params.getAll('assignmentId').length > 1
        || course !== null && !gradingId(course) || assignment !== null && (!gradingId(assignment) || !course);
    if (invalid) return <Page><ErrorView title="We can't open grading from this link." message="Choose a course to continue." onRetry={() => setParams({})} retryLabel="Back to grading" /></Page>;
    return <GradingSelection key={auth.generation} courseId={course?.toLowerCase() ?? null} assignmentId={assignment?.toLowerCase() ?? null}
        choose={(courseId, assignmentId) => setParams(new URLSearchParams({ ...(courseId ? { courseId } : {}), ...(assignmentId ? { assignmentId } : {}) }))} />;
}
/** One selected summary per resource and session, independent of the visible page window.
 * Reuse an admitted page row; only a missing summary needs one bounded owner read.
 * useOwnerRead suppresses obsolete settlements and handles explicit read-only retries.
 */
function useSelectedSummary<T extends { id: string; title: string }>(identity: string | null, visible: T | undefined,
    loadingPage: boolean, load: (options: AuthoringRequestOptions) => Promise<T>) {
    const [retained, setRetained] = useState<{ identity: string | null; data: T | null }>({ identity, data: null });
    const previous = retained.identity === identity ? retained.data : null;
    const read = useOwnerRead(identity && !visible && !previous && !loadingPage ? identity : null, load);
    const data = visible ?? previous ?? read.data;
    if (retained.identity !== identity || data && retained.data !== data) setRetained({ identity, data });
    return { ...read, data, loading: !!identity && !data && (loadingPage || read.loading) };
}
function GradingSelection({ courseId, assignmentId, choose }: { courseId: string | null; assignmentId: string | null; choose: (courseId: string | null, assignmentId?: string) => void }) {
    const [coursePage, setCoursePage] = useState(0);
    const [paging, setPaging] = useState({ courseId, page: 0 });
    if (paging.courseId !== courseId) setPaging({ courseId, page: 0 });
    const assignmentPage = paging.courseId === courseId ? paging.page : 0;
    const setAssignmentPage = (page: number) => setPaging({ courseId, page });
    const courses = useOwnedCourses(coursePage), assignments = useOwnerRead(courseId ? `grading-assignments|${courseId}|${assignmentPage}` : null,
        options => listOwnedAssignments(courseId!, assignmentPage, 20, options));
    const selectedCourse = useSelectedSummary(courseId ? `grading-course|${courseId}` : null,
        courses.data?.content.find(c => c.id.toLowerCase() === courseId), courses.loading,
        options => getOwnedCourse(courseId!, options));
    const assignment = useSelectedSummary(courseId && assignmentId ? `grading-assignment|${courseId}|${assignmentId}` : null,
        assignments.data?.content.find(a => a.id.toLowerCase() === assignmentId), assignments.loading,
        options => readOwnedAssignment(courseId!, assignmentId!, options));
    const courseOptions = [...(courses.data?.content ?? [])];
    if (selectedCourse.data && !courseOptions.some(c => c.id.toLowerCase() === courseId)) courseOptions.push(selectedCourse.data);
    const selectionRefusal = selectedCourse.error || assignment.error;
    if (courseRefused(selectionRefusal)) return <Page><PageHeader title="Grading" /><CourseUnavailable status={failureStatus(selectionRefusal)} /></Page>;
    const error = assignments.error || courses.error;
    return <Page><PageHeader title="Grading" subtitle="File submissions from learners in your courses." />
        <Box sx={{ display: 'grid', gap: 3, minWidth: 0 }}>
            {courses.loading && <LoadingView message="Loading courses…" />}
            {!!courses.error && <ErrorView title="Courses couldn't be loaded." message={errorMessage(courses.error)} onRetry={courses.reload} />}
            {selectedCourse.loading && <LoadingView message="Loading selected course…" />}
            {!!selectedCourse.error && <ErrorView title="Selected course couldn't be loaded." message={errorMessage(selectedCourse.error)} onRetry={selectedCourse.reload} />}
            {(courses.data || selectedCourse.data) && <>
                {!courseId && !courses.data?.totalElements ? <EmptyView icon="book" title="You haven't created a course yet" message="Create a course in Teaching to start adding assignments." /> : <>
                    <TextField select label="Course" value={selectedCourse.data?.id ?? ''} onChange={e => { setAssignmentPage(0); choose(e.target.value); }} sx={{ maxWidth: 560 }}>
                        {courseOptions.map(c => <MenuItem key={c.id} value={c.id}>{c.title}</MenuItem>)}
                    </TextField>
                    {courseId && !selectedCourse.data && <Typography>Selected course: {courseId}</Typography>}
                    {courses.data && <PaginationBar page={coursePage} totalPages={courses.data.totalPages} totalItems={courses.data.totalElements} pageSize={courses.data.size} itemsOnPage={courses.data.content.length} onChange={setCoursePage} noun="courses" nounOne="course" />}
                </>}
            </>}
            {courseId && (courseRefused(assignments.error) ? <CourseUnavailable status={failureStatus(assignments.error)} /> : <>
                {!!assignments.error && <ErrorView title="Assignments couldn't be loaded." message={errorMessage(error)} onRetry={assignments.reload} />}
                {(assignments.data || assignments.loading || !assignments.error) && <DataTable label="Assignments for grading" rows={assignments.data?.content ?? []} loading={assignments.loading} getRowId={a => a.id}
                    empty={<EmptyView icon="assignment" title="No assignments yet" message="Assignments from this course appear here." />}
                    columns={[
                        { key: 'title', header: 'Assignment', primary: true, render: a => a.title },
                        { key: 'deadline', header: 'Deadline', render: a => formatDeadlineDisplay(a.deadlineAt).absolute },
                        { key: 'submitted', header: 'Submitted', render: a => a.submissionCount },
                        { key: 'graded', header: 'Graded', render: a => a.gradedCount },
                        { key: 'to-grade', header: 'To grade', render: a => a.submissionCount - a.gradedCount },
                    ]} actions={a => <Button variant="outlined" onClick={() => choose(courseId, a.id)} aria-label={`View submissions: ${a.title}`}>Submissions</Button>}
                    footer={assignments.data && <PaginationBar page={assignmentPage} totalPages={assignments.data.totalPages} totalItems={assignments.data.totalElements} pageSize={assignments.data.size} itemsOnPage={assignments.data.content.length} onChange={setAssignmentPage} noun="assignments" nounOne="assignment" />} />}
            </>)}
            {assignment.loading && <LoadingView message="Loading selected assignment…" />}
            {!!assignment.error && <ErrorView title="Selected assignment couldn't be loaded." message={errorMessage(assignment.error)} onRetry={assignment.reload} />}
            {courseId && assignmentId && assignment.data && !courseRefused(assignments.error) && <AssignmentSubmissions key={`${courseId}|${assignmentId}`} courseId={courseId} assignmentId={assignmentId} title={assignment.data.title} />}
        </Box>
    </Page>;
}
function AssignmentSubmissions({ courseId, assignmentId, title }: { courseId: string; assignmentId: string; title: string }) {
    const auth = useAuth(), navigate = useNavigate(), [page, setPage] = useState(0);
    const read = useSubmissions(courseId, assignmentId, page);
    if (courseRefused(read.error)) return <CourseUnavailable status={failureStatus(read.error)} />;
    return <Box component="section" aria-label="Assignment submissions" sx={{ minWidth: 0 }}>
        <Typography variant="h2" sx={{ mb: 2 }}>{title}</Typography>
        {!!read.error && <ErrorView title="Submissions couldn't be loaded." message={errorMessage(read.error)} onRetry={read.reload} />}
        {(read.data || read.loading || !read.error) && <SubmissionTable rows={(read.data?.content ?? []).map(r => toSubmissionRow(r, title))} loading={read.loading} emptyTitle="No submissions yet"
            onOpen={id => { const row = read.data?.content.find(r => r.id === id); if (row && !read.loading && !read.error) navigate(gradingPath(courseId, assignmentId, id), { state: reviewedState({ generation: auth.generation, courseId, assignmentId, row, title }) }); }} />}
        {read.data && <PaginationBar page={page} totalPages={read.data.totalPages} totalItems={read.data.totalElements} pageSize={read.data.size} itemsOnPage={read.data.content.length} onChange={setPage} noun="submissions" nounOne="submission" />}
    </Box>;
}
