import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Skeleton, Typography } from '@mui/material';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { DataTable, PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView, ErrorView, SystemStateView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { formatDate } from '../../../../shared/utils/format';
import { QuestionEditorSheet, QuestionForm, difficultyLabel, emptyQuestionDraft, questionKindLabel, useSkillLookup } from '../components/QuestionForm';
import type { SkillSource } from '../components/QuestionForm';
import type { QuestionDraft, SkillOption } from '../types/courseManagementTypes';
import type { B3Question, B3QuizOwned, B5OwnedAssignment, QuizKind } from '../api/assessmentAuthoringApi';
import type { OwnedCourse } from '../api/instructorCourseApi';
import { useQuestionBank } from '../hooks/useQuestionBank';
import { failureStatus, useOwnedAssignments, useOwnedQuizzes } from '../hooks/useAssessmentEditor';
import { useCourseEditorContext } from '../hooks/useCourseEditor';

/*
 * S19 Assessments page (DESIGN.md r12, D12-2): `/teach/courses/:courseId/assessments`.
 * Segmented switch `Questions | Quizzes | Assignments` with `?view=questions|quizzes|assignments`.
 * Each view is its own paginated owner read shown in API order, with no client-side filtering,
 * sorting or filter controls. Note: G7 owner confirmation on Issues #200/#201 remains tracked as
 * a governance record gate while both FE15 and FE16 views are integrated here under Leader authorization.
 */

export type AssessmentsView = 'questions' | 'quizzes' | 'assignments';
export const assessmentsViewFrom = (value: string | null): AssessmentsView =>
    value === 'quizzes' ? 'quizzes' : value === 'assignments' ? 'assignments' : 'questions';
export const assessmentsPath = (courseId: string, view: AssessmentsView = 'questions') =>
    `/teach/courses/${encodeURIComponent(courseId)}/assessments${view === 'questions' ? '' : `?view=${view}`}`;
export const quizPath = (courseId: string, quizId: string) =>
    `/teach/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`;
export const assignmentPath = (courseId: string, assignmentId: string) =>
    `/teach/courses/${encodeURIComponent(courseId)}/assignments/${encodeURIComponent(assignmentId)}`;
export const skillsPath = (courseId: string) =>
    `/teach/courses/${encodeURIComponent(courseId)}/skills`;
export const quizKindLabel: Record<QuizKind, string> = { diagnostic: 'Placement diagnostic', practice: 'Practice', test: 'Test' };
/** Notices handed over by child editors through navigation state; only these exact values are shown. */
const handoverNotices = ['Quiz deleted.'] as const;

/** "1 min 35 s", from exact seconds. */
export function formatTimeLimit(seconds: number) {
    const minutes = Math.floor(seconds / 60), rest = seconds % 60;
    return [minutes ? `${minutes} min` : '', rest ? `${rest} s` : ''].filter(Boolean).join(' ') || '0 s';
}
export function PublicationPill({ published }: { published: boolean }) {
    return published ? <StatusPill tone="mint" icon="eye" size="tag">Published</StatusPill> : <StatusPill tone="neutral" icon="eye-off" size="tag">Hidden</StatusPill>;
}
const excerpt = (text: string) => text.length > 48 ? `${text.slice(0, 48).trimEnd()}…` : text;

export function localTimeZone(): string {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    } catch {
        return 'UTC';
    }
}

export function formatDeadlineDisplay(isoString: string): { absolute: string; relative: string } {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return { absolute: isoString, relative: '' };
    const timeZone = localTimeZone();
    const absolute = formatDate(date, {
        locale: 'en-US',
        options: { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZone, timeZoneName: 'short' },
    }) || isoString;
    const diffMs = date.getTime() - Date.now();
    const absMs = Math.abs(diffMs);
    const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    let span: string;
    if (absMs >= 86_400_000) span = rtf.format(Math.round(diffMs / 86_400_000), 'day');
    else if (absMs >= 3_600_000) span = rtf.format(Math.round(diffMs / 3_600_000), 'hour');
    else span = rtf.format(Math.round(diffMs / 60_000), 'minute');
    return { absolute, relative: diffMs <= 0 ? `Past due (${span})` : `Due ${span}` };
}

export interface CourseLessonSource {
    status: 'loading' | 'ready' | 'error';
    data: OwnedCourse | null;
    reload: () => void;
}

export function useLessonLookup(course: CourseLessonSource | undefined) {
    const reread = useRef(new Set<string>());
    const status = course?.status ?? 'ready';
    const data = course?.data ?? null;
    const reload = course?.reload;

    const byId = useMemo(() => {
        const map = new Map<string, { title: string; publicationStatus: 'hidden' | 'published' }>();
        for (const chapter of data?.chapters ?? []) {
            for (const lesson of chapter.lessons) {
                map.set(lesson.id.toLowerCase(), { title: lesson.title, publicationStatus: lesson.publicationStatus });
            }
        }
        return map;
    }, [data]);

    const ensure = useCallback((lessonIds: readonly (string | null)[]) => {
        if (!course || status !== 'ready' || !reload) return;
        const missing = lessonIds
            .filter((value): value is string => typeof value === 'string' && value.length > 0)
            .map((value) => value.toLowerCase())
            .filter((lessonId) => !byId.has(lessonId) && !reread.current.has(lessonId));
        if (!missing.length) return;
        missing.forEach((lessonId) => reread.current.add(lessonId));
        reload();
    }, [course, status, reload, byId]);

    const label = useCallback((lessonId: string | null): string | null => {
        if (lessonId === null) return '—';
        if (!course) return 'Lesson unavailable';
        if (status === 'loading' && !data) return null;
        if (status === 'error' && !data) return 'Lesson unavailable';
        const found = byId.get(lessonId.toLowerCase());
        if (!found) return 'Lesson unavailable';
        return found.publicationStatus === 'hidden' ? `${found.title} (hidden)` : found.title;
    }, [course, status, data, byId]);

    return { label, ensure };
}

/** F14 course states for a course read refused before any row is shown. */
export function CourseUnavailable({ status }: { status: number | undefined }) {
    const back = <Button component={Link} to="/courses" variant="contained">Back to catalogue</Button>;
    if (status === 400) return <SystemStateView icon="search" title="We can't open this course from this link." message="Check the address, or continue from the catalogue." actions={back} />;
    if (status === 403) return <SystemStateView icon="shield" title="You don't have access to this course" message="Only the course's instructor can manage its assessments." actions={back} />;
    return <SystemStateView icon="search" title="Course not available" message="This course doesn't exist or isn't published." actions={back} />;
}
export const courseRefused = (error: unknown) => [400, 403, 404].includes(failureStatus(error) ?? 0);

export interface AssessmentsPageProps {
    courseId: string;
    /** The course's skill graph read, held by the composing route/shell (D12-3). */
    skills: SkillSource;
    /** `h1` while standalone; `h2` inside the S18 Course Editor shell header. */
    headingLevel?: 'h1' | 'h2';
    /** Optional course lesson tree for the Assignments view (defaults to CourseEditorContext when mounted in shell). */
    courseContext?: CourseLessonSource;
}

export function AssessmentsPage({ courseId, skills, headingLevel = 'h1', courseContext }: AssessmentsPageProps) {
    const sharedCtx = useCourseEditorContext();
    const resolvedCourse = courseContext ?? sharedCtx?.course;
    const refreshSkillsIfStale = sharedCtx?.refreshSkillsIfStale;
    const [params, setParams] = useSearchParams();
    const view = assessmentsViewFrom(params.get('view'));
    const location = useLocation(), navigate = useNavigate();
    // Each view opens on its first page: a view change resets the page during render, before any read.
    const [paging, setPaging] = useState<{ view: AssessmentsView; page: number }>({ view, page: 0 });
    if (paging.view !== view) setPaging({ view, page: 0 });
    const page = paging.view === view ? paging.page : 0;
    const setView = (next: AssessmentsView) => setParams((current) => {
        const updated = new URLSearchParams(current);
        if (next === 'questions') updated.delete('view'); else updated.set('view', next);
        return updated;
    }, { replace: true });
    const [notice, setNotice] = useState<string | null>(() => {
        const value = (location.state as { notice?: unknown } | null)?.notice;
        return handoverNotices.find((item) => item === value) ?? null;
    });
    // Opening the Questions view re-reads a graph that S20 changed (D12-3). The callback is stable and never
    // reloads after a failed read on its own, so this runs once per view change, not once per render.
    useEffect(() => {
        if (view === 'questions') refreshSkillsIfStale?.();
    }, [view, refreshSkillsIfStale]);
    useEffect(() => {
        // Consume the handover once so a reload or history visit does not repeat it.
        if ((location.state as { notice?: unknown } | null)?.notice !== undefined) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null });
    }, [location, navigate]);
    const [refusal, setRefusal] = useState<number | undefined>(undefined);
    // "New question" sits in the view toolbar; each press asks the Questions view to open an empty form.
    const [newQuestion, setNewQuestion] = useState(0);
    const onRefused = useCallback((status: number | undefined) => setRefusal(status), []);
    if (refusal !== undefined) return <CourseUnavailable status={refusal} />;
    // minmax(0, 1fr): the segmented switch and the stacked rows must not widen the section past a 320–390 px
    // viewport, where the canvas clips overflow (overflow-x: clip) and the clipped part cannot be scrolled to.
    return <Box component="section" aria-labelledby="assessments-title" sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5, minWidth: 0 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
            <Typography id="assessments-title" component={headingLevel} variant={headingLevel === 'h1' ? 'h1' : 'h2'}>Assessments</Typography>
        </Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2, minWidth: 0, '& > *': { minWidth: 0, maxWidth: '100%' } }}>
            <SegmentedControl label="Assessment views" value={view} onChange={(next) => { setNotice(null); setView(next); }} controls="assessments-view"
                options={[
                    { value: 'questions', label: 'Questions', icon: 'quiz' },
                    { value: 'quizzes', label: 'Quizzes', icon: 'list' },
                    { value: 'assignments', label: 'Assignments', icon: 'assignment' },
                ]} />
            {view === 'questions'
                ? <Button variant="contained" onClick={() => setNewQuestion((value) => value + 1)} startIcon={<PresentationIcon name="plus" />}>New question</Button>
                : view === 'quizzes'
                    ? <Button component={Link} to={quizPath(courseId, 'new')} variant="contained" startIcon={<PresentationIcon name="plus" />}>New quiz</Button>
                    : <Button component={Link} to={assignmentPath(courseId, 'new')} variant="contained" startIcon={<PresentationIcon name="plus" />}>New assignment</Button>}
        </Box>
        {notice && <Typography role="status" sx={{ color: 'text.secondary' }}>{notice}</Typography>}
        <Box id="assessments-view" sx={{ minWidth: 0 }}>
            {view === 'questions'
                ? <QuestionsView key={courseId} courseId={courseId} page={page} createRequest={newQuestion} onPage={(next) => setPaging({ view, page: next })} skills={skills} onNotice={setNotice} onRefused={onRefused} />
                : view === 'quizzes'
                    ? <QuizzesView key={courseId} courseId={courseId} page={page} onPage={(next) => setPaging({ view, page: next })} onRefused={onRefused} />
                    : <AssignmentsView key={courseId} courseId={courseId} page={page} onPage={(next) => setPaging({ view, page: next })} courseContext={resolvedCourse} onRefused={onRefused} />}
        </Box>
    </Box>;
}

function QuestionsView({ courseId, page, createRequest, onPage, skills, onNotice, onRefused }: { courseId: string; page: number; createRequest: number; onPage: (page: number) => void; skills: SkillSource;
    onNotice: (notice: string | null) => void; onRefused: (status: number | undefined) => void }) {
    const refreshSkillsIfStale = useCourseEditorContext()?.refreshSkillsIfStale;
    const bank = useQuestionBank(courseId, page);
    const lookup = useSkillLookup(skills);
    const [editing, setEditing] = useState<{ key: number; question: B3Question | null } | null>(null);
    const { data, error } = bank, { ensure } = lookup;
    useEffect(() => { if (data) ensure(data.content.map((question) => question.skillId)); }, [data, ensure]);
    useEffect(() => { if (courseRefused(error)) onRefused(failureStatus(error)); }, [error, onRefused]);
    // U4: a deletion can leave the current page past the last one (the server answers an empty page with the
    // remaining total). Move to the last page the server reports, once per such answer; at page 0 an empty answer is
    // the real empty state. Only a current, successful read of this page counts, so a stale copy, a failed re-read or
    // a page the user has just left never moves the view, and each step strictly lowers the page.
    useEffect(() => {
        if (!data || error || data.page !== page || page === 0) return;
        if (data.content.length === 0 && data.totalElements > 0 && data.page >= data.totalPages) onPage(Math.max(0, data.totalPages - 1));
    }, [data, error, page, onPage]);
    const open = useCallback((question: B3Question | null) => {
        refreshSkillsIfStale?.();
        onNotice(null);
        setEditing({ key: Date.now(), question });
    }, [onNotice, refreshSkillsIfStale]);
    const handled = useRef(createRequest);
    useEffect(() => { if (createRequest !== handled.current) { handled.current = createRequest; open(null); } }, [createRequest, open]);
    const skillCell = (question: B3Question): ReactNode => lookup.name(question.skillId) ?? <Skeleton width={96} aria-label="Loading the skill name" />;
    const noSkillsYet = skills.status === 'ready' && skills.skills.length === 0;
    return <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2 }}>
        {skills.status === 'error' && <Alert severity="error" action={<Button size="small" variant="text" onClick={skills.reload}>Try again</Button>}>Skills couldn&apos;t be loaded.</Alert>}
        {bank.error && !courseRefused(bank.error)
            ? <ErrorView title="Questions couldn't be loaded." message={errorMessage(bank.error)} onRetry={bank.reload} />
            : <DataTable label="Questions" loading={!data} rows={data?.content ?? []} getRowId={(question) => question.id}
                empty={<EmptyView icon="quiz" title="No questions yet" message="Each question is tagged with a skill and a difficulty."
                    action={noSkillsYet ? <Button component={Link} to={skillsPath(courseId)} variant="outlined">Go to Skills</Button> : undefined} />}
                columns={[
                    { key: 'prompt', header: 'Question', primary: true, render: (question) => <Typography sx={{ fontWeight: 600, whiteSpace: 'pre-line', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{question.prompt}</Typography> },
                    { key: 'kind', header: 'Kind', render: (question) => questionKindLabel[question.kind] },
                    { key: 'skill', header: 'Skill', render: skillCell },
                    // D8-7: difficulty tags are neutral.
                    { key: 'difficulty', header: 'Difficulty', render: (question) => <StatusPill size="tag" tone="neutral">{difficultyLabel[question.difficulty]}</StatusPill> },
                ]}
                actions={(question) => <CircleAction label={`Edit question: ${excerpt(question.prompt)}`} icon="edit" variant="fill" onClick={() => open(question)} />}
                footer={data && <PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length} onChange={onPage} noun="questions" nounOne="question" />} />}
        {editing && <QuestionEditorSheet key={editing.key} courseId={courseId} question={editing.question} skills={skills} ensureSkills={ensure}
            skillsHref={skillsPath(courseId)}
            onClose={() => setEditing(null)}
            onDone={(outcome) => { setEditing(null); onNotice(outcome === 'saved' ? 'Question saved.' : 'Question deleted.'); bank.reload(); }} />}
    </Box>;
}

function QuizzesView({ courseId, page, onPage, onRefused }: { courseId: string; page: number; onPage: (page: number) => void; onRefused: (status: number | undefined) => void }) {
    const quizzes = useOwnedQuizzes(courseId, page);
    const { error } = quizzes;
    useEffect(() => { if (courseRefused(error)) onRefused(failureStatus(error)); }, [error, onRefused]);
    const data = quizzes.data;
    if (quizzes.error && !courseRefused(quizzes.error)) return <ErrorView title="Quizzes couldn't be loaded." message={errorMessage(quizzes.error)} onRetry={quizzes.reload} />;
    return <DataTable label="Quizzes" loading={!data} rows={data?.content ?? []} getRowId={(quiz) => quiz.id}
        empty={<EmptyView icon="list" title="No quizzes yet" message="Build a quiz from questions in the question bank." />}
        columns={[
            { key: 'title', header: 'Title', primary: true, render: (quiz: B3QuizOwned) => <Typography component={Link} to={quizPath(courseId, quiz.id)} sx={{ fontWeight: 600, color: 'inherit' }}>{quiz.title}</Typography> },
            { key: 'kind', header: 'Kind', render: (quiz) => <StatusPill size="tag" tone="neutral">{quizKindLabel[quiz.kind]}</StatusPill> },
            { key: 'questions', header: 'Questions', render: (quiz) => <span className="cm-tabular">{quiz.questionCount}</span> },
            { key: 'time', header: 'Time limit', render: (quiz) => <span className="cm-tabular">{formatTimeLimit(quiz.timeLimitSeconds)}</span> },
            { key: 'publication', header: 'Publication', render: (quiz) => <PublicationPill published={quiz.published} /> },
        ]}
        actions={(quiz) => <Button component={Link} to={quizPath(courseId, quiz.id)} variant="outlined" size="small" aria-label={`Open quiz: ${quiz.title}`}>Open</Button>}
        footer={data && <PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length} onChange={onPage} noun="quizzes" nounOne="quiz" />} />;
}

function AssignmentsView({ courseId, page, onPage, courseContext, onRefused }: {
    courseId: string;
    page: number;
    onPage: (page: number) => void;
    courseContext: CourseLessonSource | undefined;
    onRefused: (status: number | undefined) => void;
}) {
    const assignments = useOwnedAssignments(courseId, page);
    const lessonLookup = useLessonLookup(courseContext);
    const { data, error } = assignments;
    const { ensure } = lessonLookup;
    useEffect(() => { if (data) ensure(data.content.map((item) => item.lessonId)); }, [data, ensure]);
    useEffect(() => { if (courseRefused(error)) onRefused(failureStatus(error)); }, [error, onRefused]);

    if (assignments.error && !courseRefused(assignments.error)) {
        return <ErrorView title="Assignments couldn't be loaded." message={errorMessage(assignments.error)} onRetry={assignments.reload} />;
    }

    return <DataTable label="Assignments" loading={!data} rows={data?.content ?? []} getRowId={(item) => item.id}
        empty={<EmptyView icon="assignment" title="No assignments yet" message="Create an assignment with instructions and a deadline." />}
        columns={[
            {
                key: 'title',
                header: 'Title',
                primary: true,
                render: (assignment: B5OwnedAssignment) => <Typography component={Link} to={assignmentPath(courseId, assignment.id)} sx={{ fontWeight: 600, color: 'inherit' }}>{assignment.title}</Typography>,
            },
            {
                key: 'deadline',
                header: 'Deadline',
                render: (assignment) => {
                    const formatted = formatDeadlineDisplay(assignment.deadlineAt);
                    return <Box>
                        <Typography variant="body2" className="cm-tabular">{formatted.absolute}</Typography>
                        {formatted.relative && <Typography variant="caption" sx={{ color: 'text.secondary' }}>{formatted.relative}</Typography>}
                    </Box>;
                },
            },
            {
                key: 'lesson',
                header: 'Related lesson',
                render: (assignment) => lessonLookup.label(assignment.lessonId) ?? <Skeleton width={112} aria-label="Loading the related lesson" />,
            },
            {
                key: 'publication',
                header: 'Publication',
                render: (assignment) => <PublicationPill published={assignment.publicationStatus === 'published'} />,
            },
            {
                key: 'submitted',
                header: 'Submitted',
                render: (assignment) => <span className="cm-tabular">{assignment.submissionCount}</span>,
            },
            {
                key: 'graded',
                header: 'Graded',
                render: (assignment) => <span className="cm-tabular">{assignment.gradedCount}</span>,
            },
        ]}
        actions={(assignment) => <Button component={Link} to={assignmentPath(courseId, assignment.id)} variant="outlined" size="small" aria-label={`Open assignment: ${assignment.title}`}>Open</Button>}
        footer={data && <PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length} onChange={onPage} noun="assignments" nounOne="assignment" />} />;
}

/** F14 network and server wording; other refusals keep the safe client message. */
export function errorMessage(error: unknown) {
    const status = failureStatus(error);
    if (status === undefined || status >= 500) {
        return (error as { kind?: string } | null)?.kind === 'network' ? "We couldn't reach CodeMastery. Check your connection and try again." : 'Something went wrong on our side. Try again in a moment.';
    }
    return (error as Error).message;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

export interface QuestionBankPageProps { questions: QuestionDraft[]; skills: SkillOption[]; onSave: (question: QuestionDraft) => void; headingLevel?: 'h1' | 'h2' }

/** The preview's Questions view with sample data: r12 columns in the given order, no filters, the r12 question form. */
export function QuestionBankPage({ questions, skills, onSave, headingLevel = 'h2' }: QuestionBankPageProps) {
    const [editing, setEditing] = useState<QuestionDraft | null>(null);
    const skillName = (id: string) => skills.find((item) => item.id === id)?.name ?? 'Skill unavailable';
    const fresh = (): QuestionDraft => {
        const draft = emptyQuestionDraft();
        return { id: '', kind: draft.kind, prompt: '', options: draft.options.map((item) => ({ id: item.key, label: '', correct: false })), skillId: '', difficulty: 'easy', usedInQuizzes: 0, usedInAttempts: false };
    };
    return <Box component="section" aria-labelledby="question-bank-title">
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Typography id="question-bank-title" component={headingLevel} variant="h2">Questions</Typography>
            <Button variant="contained" onClick={() => setEditing(fresh())} startIcon={<PresentationIcon name="plus" />}>New question</Button>
        </Box>
        <DataTable label="Questions" rows={questions} getRowId={(question) => question.id}
            empty={<EmptyView icon="quiz" title="No questions yet" message="Each question is tagged with a skill and a difficulty." />}
            columns={[
                { key: 'prompt', header: 'Question', primary: true, render: (question) => <Typography sx={{ fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{question.prompt}</Typography> },
                { key: 'kind', header: 'Kind', render: (question) => questionKindLabel[question.kind] },
                { key: 'skill', header: 'Skill', render: (question) => skillName(question.skillId) },
                { key: 'difficulty', header: 'Difficulty', render: (question) => <StatusPill size="tag" tone="neutral">{difficultyLabel[question.difficulty]}</StatusPill> },
            ]}
            actions={(question) => <CircleAction label={`Edit question: ${excerpt(question.prompt)}`} icon="edit" variant="fill" onClick={() => setEditing(question)} />} />
        {editing && <QuestionForm key={editing.id || 'new'} open initial={editing} skills={skills} onCancel={() => setEditing(null)} onSave={(question) => { onSave(question); setEditing(null); }} />}
    </Box>;
}
