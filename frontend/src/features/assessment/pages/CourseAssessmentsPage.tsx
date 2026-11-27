import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import type { PageResponse } from '../../../shared/types/apiTypes';
import { listLearnerAssignments, assignmentIdValid } from '../api/assignmentApi';
import type { B5LearnerAssignmentSummary } from '../api/assignmentApi';
import { listLearnerQuizzes } from '../api/quizApi';
import type { B3QuizSummaryPage } from '../types/assessmentTypes';
import { assignmentDeadline } from './AssignmentDetailPage';
import { isDestinationLive } from '../../../routes/routePaths';
import type { ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { DataTable, PaginationBar } from '../../../shared/components/DataTable';
import { EmptyView, ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { StatusPill } from '../../../shared/components/StatusPill';
import { CircleAction } from '../../../shared/components/Tile';
import { quizKindLabel } from '../types/assessmentTypes';
import type { AssessmentItemView } from '../types/assessmentTypes';
import { formatClock } from '../components/QuizTimer';

export type AssessmentFilter = 'all' | 'quiz' | 'assignment';
export interface CourseAssessmentsPageProps {
    courseTitle: string;
    items: AssessmentItemView[];
    filter: AssessmentFilter;
    onFilterChange: (filter: AssessmentFilter) => void;
    onOpen: (item: AssessmentItemView) => void;
    sectionNav?: ReactNode;
    back?: Crumb;
}

const deadlineTone = { open: 'neutral', soon: 'butter', closed: 'rose' } as const;

/** S10 Course assessments: published quizzes and assignments for enrolled learners (US-12). */
export function CourseAssessmentsPage({ courseTitle, items, filter, onFilterChange, onOpen, sectionNav, back }: CourseAssessmentsPageProps) {
    const rows = items.filter((item) => filter === 'all' || item.type === filter);
    return <Page>
        <PageHeader title="Assessments" subtitle={`${courseTitle} · Published quizzes and assignments.`} back={back} />
        {sectionNav && <Box sx={{ mb: 4 }}>{sectionNav}</Box>}
        <Box sx={{ mb: 2 }}><SegmentedControl label="Assessment type" value={filter} onChange={onFilterChange}
            options={[{ value: 'all', label: 'All', count: items.length }, { value: 'quiz', label: 'Quizzes', count: items.filter((i) => i.type === 'quiz').length }, { value: 'assignment', label: 'Assignments', count: items.filter((i) => i.type === 'assignment').length }]} /></Box>
        <DataTable label="Assessments" rows={rows} getRowId={(item) => item.id}
            empty={<EmptyView icon="quiz" title="No assessments here yet" message="Published quizzes and assignments for this course will appear here." />}
            columns={[
                { key: 'name', header: 'Name', primary: true, render: (item) => <Box component="a" href="#" onClick={(event) => { event.preventDefault(); onOpen(item); }} sx={{ color: 'text.primary', fontWeight: 600, '&:hover': { textDecoration: 'underline' } }}>{item.title}</Box> },
                { key: 'type', header: 'Type', render: (item) => item.type === 'quiz' ? quizKindLabel[item.kind] : 'Assignment' },
                { key: 'limit', header: 'Time limit or deadline', render: (item) => item.type === 'quiz' ? <span className="cm-tabular">{formatClock(item.timeLimitSeconds)}</span>
                    : <StatusPill size="tag" tone={deadlineTone[item.deadlineState]} icon="calendar">{item.deadlineState === 'closed' ? 'Closed' : item.deadlineLabel}</StatusPill> },
                { key: 'status', header: 'Attempts or submission', render: (item) => item.type === 'quiz' ? item.attemptsLabel : item.submissionLabel },
                { key: 'score', header: 'Last score or grade', align: 'right', render: (item) => { const value = item.type === 'quiz' ? item.lastScore : item.grade;
                    return <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{value === undefined || value === null ? '—' : item.type === 'quiz' ? `${value}%` : `${value} / 100`}</Typography>; } },
            ]}
            actions={(item) => <CircleAction label={item.type === 'quiz' ? `Open ${item.title}` : `Open assignment ${item.title}`} icon="arrow-right" variant="ink" onClick={() => onOpen(item)} />} />
    </Page>;
}

/** S10 composition seam: FE11 owns Assignments; the quiz summary slice is shared with FE10. */
export function LiveCourseAssessmentsPage({ courseId: providedCourseId, courseTitle }: { courseId?: string; courseTitle?: string }) {
    const params = useParams(), auth = useAuth(), courseId = providedCourseId ?? params.courseId;
    return <AssessmentsSession key={`${auth.generation}|${courseId}`} courseId={courseId} courseTitle={courseTitle} />;
}
function AssessmentsSession({ courseId, courseTitle }: { courseId?: string; courseTitle?: string }) {
    const auth = useAuth(), generation = auth.generation;
    const [tab, setTab] = useState<'quizzes' | 'assignments'>('assignments');
    // Each operation retains its own server page, without filtering or merging incomplete pages.
    const [pages, setPages] = useState({ quizzes: 0, assignments: 0 });
    const [revision, retry] = useState(0), [now, setNow] = useState(Date.now);
    const [read, setRead] = useState<{ key: string; data: B3QuizSummaryPage | PageResponse<B5LearnerAssignmentSummary> | null; error: ApiError | null }>({ key: '', data: null, error: null });
    const key = `${tab}|${pages[tab]}|${revision}`;
    const latest = useRef(key); latest.current = key;
    useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer); }, []);
    useEffect(() => {
        if (!assignmentIdValid(courseId) || !auth.isAuthenticated || auth.user?.role !== 'Learner') return;
        const controller = new AbortController(); let active = true;
        const current = () => active && latest.current === key && !controller.signal.aborted && auth.isCurrent(generation);
        const options = { signal: controller.signal, isCurrent: current };
        const request = tab === 'assignments' ? listLearnerAssignments(courseId, pages.assignments, 20, options) : listLearnerQuizzes(courseId, pages.quizzes, 20, options);
        request.then(data => {
            if (!current()) return;
            if (data.totalPages > 0 && data.page >= data.totalPages) { setPages(p => ({ ...p, [tab]: data.totalPages - 1 })); return; }
            if (data.totalElements === 0 && pages[tab] !== 0) { setPages(p => ({ ...p, [tab]: 0 })); return; }
            setRead({ key, data, error: null });
        }).catch(error => { if (current()) setRead({ key, data: null, error: error instanceof ApiError ? error : new ApiError('network', "Couldn't load assessments. Try again.") }); });
        return () => { active = false; controller.abort(); };
    }, [key, courseId, generation]);
    const data = read.key === key ? read.data : null, error = read.key === key ? read.error : null;
    const coursePath = `/courses/${encodeURIComponent(courseId ?? '')}`;
    const footer = data ? <PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length}
        onChange={page => setPages(p => ({ ...p, [tab]: page }))} noun={tab} nounOne={tab === 'assignments' ? 'assignment' : 'quiz'} /> : undefined;
    return <Page><PageHeader title="Assessments" subtitle={courseTitle} back={{ label: 'Course details', href: coursePath }} />
        <Box sx={{ mb: 3 }}><SegmentedControl label="Assessment type" value={tab} onChange={setTab} options={[{ value: 'quizzes', label: 'Quizzes' }, { value: 'assignments', label: 'Assignments' }]} /></Box>
        {!assignmentIdValid(courseId) ? <SystemStateView icon="search" title="Invalid course link" message="Open assessments from the course details." />
            : error?.status === 403 ? <SystemStateView icon="lock" title="Rejoin the course to see assessments" message="Your membership is required." actions={<Button component={Link} to={coursePath}>View course details</Button>} />
                : error?.status === 404 ? <SystemStateView icon="search" title="Course not available" message="It may have been hidden." />
                    : error ? <ErrorView title={`Couldn't load ${tab}`} message={error.message} onRetry={() => retry(n => n + 1)} />
                        : !data ? <LoadingView message={`Loading ${tab}…`} variant="rows" />
                            : tab === 'assignments' ? <DataTable label="Assignments" rows={(data as PageResponse<B5LearnerAssignmentSummary>).content} getRowId={r => r.id}
                                empty={<EmptyView icon="assignment" title="No assignments published yet" message="Published assignments for this course will appear here." />} footer={footer}
                                columns={[
                                    { key: 'name', header: 'Name', primary: true, render: r => r.title },
                                    { key: 'deadline', header: 'Deadline', render: r => { const d = assignmentDeadline(r.deadlineAt, r.open, now); return <Box><Typography component="time" dateTime={r.deadlineAt}>{d.label}</Typography><Typography variant="body2" sx={{ color: 'text.secondary' }}>{d.relative}</Typography></Box>; } },
                                    { key: 'status', header: 'Status', render: r => <StatusPill tone={r.submissionStatus === 'graded' ? 'mint' : r.submissionStatus === 'submitted' ? 'sky' : !r.open ? 'neutral' : 'dashed'}>{r.submissionStatus === 'graded' ? 'Graded' : r.submissionStatus === 'submitted' ? 'Submitted' : !r.open ? 'Closed' : 'Not submitted'}</StatusPill> },
                                ]} actions={r => <Button component={Link} to={`${coursePath}/assignments/${encodeURIComponent(r.id)}`} aria-label={`Open assignment ${r.title}`}>Open</Button>} />
                                : <><Typography variant="body2" sx={{ mb: 2 }}>Quiz attempts become available when their screens are delivered.</Typography><DataTable label="Quizzes" rows={(data as B3QuizSummaryPage).content} getRowId={r => r.id} footer={footer}
                                    empty={<EmptyView icon="quiz" title="No quizzes published yet" message="Published quizzes for this course will appear here." />} columns={[
                                        { key: 'name', header: 'Name', primary: true, render: r => r.title },
                                        { key: 'kind', header: 'Kind', render: r => quizKindLabel[r.kind] },
                                        { key: 'questions', header: 'Questions', render: r => r.questionCount },
                                        { key: 'limit', header: 'Time limit', render: r => formatClock(r.timeLimitSeconds) },
                                    ]} actions={r => { const path = r.kind === 'diagnostic' ? `${coursePath}/diagnostic` : `${coursePath}/quizzes/${encodeURIComponent(r.id)}`;
                                        return isDestinationLive(path, 'Learner') ? <Button component={Link} to={path}>Open</Button> : <Typography variant="body2">Not available yet</Typography>; }} /></>}
    </Page>;
}
