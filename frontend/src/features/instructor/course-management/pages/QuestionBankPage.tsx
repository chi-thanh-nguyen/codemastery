import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Skeleton, Typography } from '@mui/material';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { DataTable, PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView, ErrorView, SystemStateView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { QuestionEditorSheet, QuestionForm, difficultyLabel, emptyQuestionDraft, questionKindLabel, useSkillLookup } from '../components/QuestionForm';
import type { SkillSource } from '../components/QuestionForm';
import type { QuestionDraft, SkillOption } from '../types/courseManagementTypes';
import type { B3Question, B3QuizOwned, QuizKind } from '../api/assessmentAuthoringApi';
import { useQuestionBank } from '../hooks/useQuestionBank';
import { failureStatus, useOwnedQuizzes } from '../hooks/useAssessmentEditor';

/*
 * S19 Assessments page (DESIGN.md r12, D12-2): `/teach/courses/:courseId/assessments`. Each view is its
 * own paginated owner read shown in API order, with no client-side filtering, sorting or filter
 * controls. The view is kept in `?view=` (recommended by DESIGN.md; FE15 owner confirmation pending,
 * G7). The Assignments view is FE16's deliverable and is not routed until G7 is confirmed and FE16
 * delivers it, so `?view=assignments` opens Questions like any unknown value.
 */

export type AssessmentsView = 'questions' | 'quizzes';
export const assessmentsViewFrom = (value: string | null): AssessmentsView => value === 'quizzes' ? 'quizzes' : 'questions';
export const assessmentsPath = (courseId: string, view: AssessmentsView = 'questions') =>
    `/teach/courses/${encodeURIComponent(courseId)}/assessments${view === 'quizzes' ? '?view=quizzes' : ''}`;
export const quizPath = (courseId: string, quizId: string) => `/teach/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`;
export const quizKindLabel: Record<QuizKind, string> = { diagnostic: 'Placement diagnostic', practice: 'Practice', test: 'Test' };
/** Notices handed over by the quiz editor through navigation state; only these exact values are shown. */
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
    /** The course's skill graph read, held by the composing route (D12-3). */
    skills: SkillSource;
    /** `h1` while the page is routed on its own; `h2` once a course editor header provides the page title. */
    headingLevel?: 'h1' | 'h2';
}

export function AssessmentsPage({ courseId, skills, headingLevel = 'h1' }: AssessmentsPageProps) {
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
    useEffect(() => {
        // Consume the handover once so a reload or history visit does not repeat it.
        if ((location.state as { notice?: unknown } | null)?.notice !== undefined) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null });
    }, [location, navigate]);
    const [refusal, setRefusal] = useState<number | undefined>(undefined);
    // "New question" sits in the view toolbar; each press asks the Questions view to open an empty form.
    const [newQuestion, setNewQuestion] = useState(0);
    const onRefused = useCallback((status: number | undefined) => setRefusal(status), []);
    if (refusal !== undefined) return <CourseUnavailable status={refusal} />;
    return <Box component="section" aria-labelledby="assessments-title" sx={{ display: 'grid', gap: 2.5 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
            <Typography id="assessments-title" component={headingLevel} variant={headingLevel === 'h1' ? 'h1' : 'h2'}>Assessments</Typography>
        </Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
            <SegmentedControl label="Assessment views" value={view} onChange={(next) => { setNotice(null); setView(next); }} controls="assessments-view"
                options={[{ value: 'questions', label: 'Questions', icon: 'quiz' }, { value: 'quizzes', label: 'Quizzes', icon: 'list' }]} />
            {view === 'questions'
                ? <Button variant="contained" onClick={() => setNewQuestion((value) => value + 1)} startIcon={<PresentationIcon name="plus" />}>New question</Button>
                : <Button component={Link} to={quizPath(courseId, 'new')} variant="contained" startIcon={<PresentationIcon name="plus" />}>New quiz</Button>}
        </Box>
        {notice && <Typography role="status" sx={{ color: 'text.secondary' }}>{notice}</Typography>}
        <Box id="assessments-view">
            {view === 'questions'
                ? <QuestionsView key={courseId} courseId={courseId} page={page} createRequest={newQuestion} onPage={(next) => setPaging({ view, page: next })} skills={skills} onNotice={setNotice} onRefused={onRefused} />
                : <QuizzesView key={courseId} courseId={courseId} page={page} onPage={(next) => setPaging({ view, page: next })} onRefused={onRefused} />}
        </Box>
    </Box>;
}

function QuestionsView({ courseId, page, createRequest, onPage, skills, onNotice, onRefused }: { courseId: string; page: number; createRequest: number; onPage: (page: number) => void; skills: SkillSource;
    onNotice: (notice: string | null) => void; onRefused: (status: number | undefined) => void }) {
    const bank = useQuestionBank(courseId, page);
    const lookup = useSkillLookup(skills);
    const [editing, setEditing] = useState<{ key: number; question: B3Question | null } | null>(null);
    const { data, error } = bank, { ensure } = lookup;
    useEffect(() => { if (data) ensure(data.content.map((question) => question.skillId)); }, [data, ensure]);
    useEffect(() => { if (courseRefused(error)) onRefused(failureStatus(error)); }, [error, onRefused]);
    const open = useCallback((question: B3Question | null) => { onNotice(null); setEditing({ key: Date.now(), question }); }, [onNotice]);
    const handled = useRef(createRequest);
    useEffect(() => { if (createRequest !== handled.current) { handled.current = createRequest; open(null); } }, [createRequest, open]);
    const skillCell = (question: B3Question): ReactNode => lookup.name(question.skillId) ?? <Skeleton width={96} aria-label="Loading the skill name" />;
    return <Box sx={{ display: 'grid', gap: 2 }}>
        {skills.status === 'error' && <Alert severity="error" action={<Button size="small" variant="text" onClick={skills.reload}>Try again</Button>}>Skills couldn't be loaded.</Alert>}
        {bank.error && !courseRefused(bank.error)
            ? <ErrorView title="Questions couldn't be loaded." message={errorMessage(bank.error)} onRetry={bank.reload} />
            : <DataTable label="Questions" loading={!data} rows={data?.content ?? []} getRowId={(question) => question.id}
                // "Go to Skills" waits for the Skills route (FE18): no link to an unimplemented page (CT16.routes activation rule).
                empty={<EmptyView icon="quiz" title="No questions yet" message="Each question is tagged with a skill and a difficulty." />}
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
