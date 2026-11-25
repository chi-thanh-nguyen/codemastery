import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Link, useLocation, useNavigate } from 'react-router';
import { useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { Tile, CircleAction } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { EmptyView, ErrorView, LoadingView, SystemStateView } from '../../../../shared/components/StatusViews';
import { PaginationBar } from '../../../../shared/components/DataTable';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import type { QuestionDraft, QuizDraft, SkillOption } from '../types/courseManagementTypes';
import { difficultyLabel } from '../types/courseManagementTypes';
import { radius, roles } from '../../../../app/theme/theme';
import { ApiError } from '../../../../shared/types/apiTypes';
import { MAX_TIME_LIMIT_SECONDS } from '../api/assessmentAuthoringApi';
import type { B3Question, B3QuizOwned, B3QuizWrite, QuizKind } from '../api/assessmentAuthoringApi';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown, useQuizEditor } from '../hooks/useAssessmentEditor';
import { useQuestionBank } from '../hooks/useQuestionBank';
import { difficultyLabel as levelLabel, questionKindLabel, useSkillLookup } from '../components/QuestionForm';
import type { SkillSource } from '../components/QuestionForm';
import { CourseUnavailable, PublicationPill, assessmentsPath, errorMessage, quizKindLabel, quizPath } from './QuestionBankPage';

export interface QuizEditorPageProps { initial: QuizDraft; questions: QuestionDraft[]; skills: SkillOption[]; onSave: (quiz: QuizDraft) => void; onCancel: () => void }

/** S19 Quiz editor preview component: kind, time limit (stored as seconds), ordered questions, visibility (US-12). */
export function QuizEditorPage({ initial, questions, skills, onSave, onCancel }: QuizEditorPageProps) {
    const [draft, setDraft] = useState(initial), [errors, setErrors] = useState<Record<string, string>>({}), [saved, setSaved] = useState(false);
    const set = (patch: Partial<QuizDraft>) => { setDraft((current) => ({ ...current, ...patch })); setSaved(false); };
    const byId = new Map(questions.map((question) => [question.id, question]));
    const move = (index: number, direction: -1 | 1) => { const ids = [...draft.questionIds]; [ids[index], ids[index + direction]] = [ids[index + direction], ids[index]]; set({ questionIds: ids }); };
    const save = () => {
        const next: Record<string, string> = {};
        if (!draft.title.trim()) next.title = 'Enter a quiz title.';
        if (!Number.isInteger(draft.timeLimitMinutes) || draft.timeLimitMinutes < 1) next.time = 'Enter a whole number of minutes, at least 1.';
        if (draft.questionIds.length === 0) next.questions = 'Add at least one question.';
        setErrors(next); if (!Object.keys(next).length) { onSave(draft); setSaved(true); }
    };
    return <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); save(); }} sx={{ display: 'grid', gap: 3, maxWidth: 900 }}>
        <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
            <Typography variant="h3" component="h2">{initial.title ? 'Edit quiz' : 'New quiz'}</Typography>
            <TextField label="Quiz title" value={draft.title} onChange={(event) => set({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required />
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <TextField select label="Quiz kind" value={draft.kind} onChange={(event) => set({ kind: event.target.value as QuizDraft['kind'] })}>
                    <MenuItem value="diagnostic">Placement diagnostic</MenuItem><MenuItem value="practice">Practice quiz</MenuItem><MenuItem value="test">Test</MenuItem>
                </TextField>
                <TextField label="Time limit (minutes)" type="number" value={Number.isNaN(draft.timeLimitMinutes) ? '' : draft.timeLimitMinutes} onChange={(event) => set({ timeLimitMinutes: Number(event.target.value) })}
                    error={!!errors.time} helperText={errors.time ?? 'Answers are submitted automatically when time runs out.'} slotProps={{ htmlInput: { min: 1, step: 1, inputMode: 'numeric' } }} required />
            </Box>
            <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Visibility</Typography>
                <SegmentedControl label="Quiz visibility" value={draft.publication} onChange={(publication) => set({ publication })} options={[{ value: 'hidden', label: 'Hidden', icon: 'eye-off' }, { value: 'published', label: 'Published', icon: 'eye' }]} /></Box>
        </Tile>
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: '1fr 1fr' } }}>
            <Tile component="section" aria-labelledby="quiz-order">
                <Typography id="quiz-order" variant="h4" component="h3" sx={{ mb: 1.5 }}>In this quiz ({draft.questionIds.length})</Typography>
                {errors.questions && <Alert severity="error" sx={{ mb: 1.5 }}>{errors.questions}</Alert>}
                <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                    {draft.questionIds.map((id, index) => { const question = byId.get(id); return question && <Box component="li" key={id} sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.25, borderRadius: `${radius.md}px`, bgcolor: roles.bg.canvas }}>
                        <Typography className="cm-tabular" sx={{ fontWeight: 600, width: 24, color: 'text.secondary' }}>{index + 1}</Typography>
                        <Typography sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{question.prompt}</Typography>
                        <CircleAction size="sm" variant="plain" icon="move-up" label={`Move question ${index + 1} up`} disabled={index === 0} onClick={() => move(index, -1)} />
                        <CircleAction size="sm" variant="plain" icon="move-down" label={`Move question ${index + 1} down`} disabled={index === draft.questionIds.length - 1} onClick={() => move(index, 1)} />
                    </Box>; })}
                </Box>
            </Tile>
            <Tile component="section" aria-labelledby="quiz-bank">
                <Typography id="quiz-bank" variant="h4" component="h3" sx={{ mb: 1.5 }}>Question bank</Typography>
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: .5, maxHeight: 360, overflowY: 'auto' }}>
                    {questions.map((question) => <Box component="li" key={question.id}>
                        <Box component="label" sx={{ display: 'flex', alignItems: 'center', gap: 1, px: .5, borderRadius: `${radius.sm}px`, cursor: 'pointer', '&:hover': { bgcolor: roles.bg.canvas } }}>
                            <Checkbox checked={draft.questionIds.includes(question.id)} onChange={(event) => set({ questionIds: event.target.checked ? [...draft.questionIds, question.id] : draft.questionIds.filter((item) => item !== question.id) })} />
                            <Box sx={{ minWidth: 0, flex: 1 }}><Typography sx={{ fontSize: '0.9375rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{question.prompt}</Typography>
                                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{skills.find((skill) => skill.id === question.skillId)?.name ?? 'Unmapped'} · {difficultyLabel[question.difficulty]}</Typography></Box>
                        </Box>
                    </Box>)}
                </Box>
            </Tile>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button type="submit" variant="contained">Save quiz</Button><Button variant="outlined" onClick={onCancel}>Cancel</Button>
            {saved && <StatusPill tone="mint" icon="check-circle" size="tag">Saved</StatusPill>}
        </Box>
    </Box>;
}

// --- Production S19 quiz editor (DESIGN.md r12; CT23.composition/publication) ---

export interface QuizFormDraft { title: string; kind: QuizKind | ''; minutes: string; seconds: string; questions: B3Question[] }
export const emptyQuizDraft = (): QuizFormDraft => ({ title: '', kind: '', minutes: '', seconds: '', questions: [] });
/** Exact seconds shown as minutes and seconds; nothing is rounded. */
export const draftFromQuiz = (quiz: B3QuizOwned): QuizFormDraft => ({ title: quiz.title, kind: quiz.kind, minutes: String(Math.floor(quiz.timeLimitSeconds / 60)),
    seconds: String(quiz.timeLimitSeconds % 60), questions: quiz.questions.map((item) => item.question) });

/** Minutes and seconds (0–59) as whole numbers, stored as seconds within the contract bounds (1 … 2147483647); otherwise null. */
export function timeLimitSeconds(minutes: string, seconds: string): number | null {
    const m = minutes.trim(), s = seconds.trim();
    if (!m && !s || !/^\d*$/.test(m) || !/^\d*$/.test(s)) return null;
    const whole = (m ? Number(m) : 0) * 60 + (s ? Number(s) : 0);
    if (s && Number(s) > 59 || !Number.isSafeInteger(whole) || whole < 1 || whole > MAX_TIME_LIMIT_SECONDS) return null;
    return whole;
}
export function validateQuiz(draft: QuizFormDraft): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!/\S/.test(draft.title)) errors.title = 'Enter a quiz title.';
    if (!draft.kind) errors.kind = 'Choose a quiz kind.';
    if (timeLimitSeconds(draft.minutes, draft.seconds) === null) errors.time = 'Enter a time limit of at least 1 second, with seconds from 0 to 59.';
    return errors;
}
export const quizWrite = (draft: QuizFormDraft): B3QuizWrite => ({ title: draft.title, kind: draft.kind as QuizKind,
    timeLimitSeconds: timeLimitSeconds(draft.minutes, draft.seconds) as number, questionIds: draft.questions.map((question) => question.id) });
export const sameQuiz = (draft: QuizFormDraft, quiz: B3QuizOwned) => draft.title === quiz.title && draft.kind === quiz.kind &&
    timeLimitSeconds(draft.minutes, draft.seconds) === quiz.timeLimitSeconds && draft.questions.length === quiz.questions.length &&
    draft.questions.every((question, index) => question.id.toLowerCase() === quiz.questions[index].question.id.toLowerCase());
/** ADR013 §19.4: only a published practice quiz with one difficulty can be recommended as practice. */
export function difficultySummary(kind: QuizKind | '', questions: readonly B3Question[]): string | null {
    if (kind !== 'practice' || !questions.length) return null;
    if (new Set(questions.map((question) => question.difficulty)).size > 1) return 'Mixed difficulty. This quiz is never recommended as practice, but learners can still take it.';
    const level = levelLabel[questions[0].difficulty];
    return `All questions are ${level}. Learners can be recommended this quiz as ${level} practice.`;
}

const CREATED = 'Quiz created. It stays hidden until you publish it.';
const QUIZ_FIELD_MAP = {
    title: 'title',
    kind: 'kind',
    timeLimitSeconds: 'time',
    questionIds: 'questions',
} as const;
type EditorAlert =
    | { type: 'conflict-save' | 'conflict-delete' | 'conflict-publication' | 'error'; message: string }
    | { type: 'not-found' | 'unknown-create' | 'no-access' }
    /** An unknown outcome: the re-read version, or null until a re-read succeeds. */
    | { type: 'unknown'; current: B3QuizOwned | null; retry: () => void };
const messageOf = (error: unknown) => error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.';

export interface QuizEditorProps {
    courseId: string;
    /** A quiz UUID, or the literal `new` creation segment (CT16.routes). Mount with a key per value. */
    quizId: string;
    /** The course's skill graph read, held by the composing route (D12-3). */
    skills: SkillSource;
}

export function QuizEditor({ courseId, quizId, skills }: QuizEditorProps) {
    const navigate = useNavigate(), location = useLocation();
    const editor = useQuizEditor(courseId);
    const lookup = useSkillLookup(skills);
    const creating = quizId === 'new';
    const listPath = assessmentsPath(courseId, 'quizzes');
    const [quiz, setQuiz] = useState<B3QuizOwned | null>(null);
    const [entry, setEntry] = useState<'loading' | 'ready' | 'failed'>(creating ? 'ready' : 'loading');
    const [refusal, setRefusal] = useState<number | undefined>(undefined);
    const [revision, setRevision] = useState(0);
    const [draft, setDraft] = useState<QuizFormDraft>(emptyQuizDraft);
    const [dirty, setDirty] = useState(false);
    const [gone, setGone] = useState(false);
    const [noAccess, setNoAccess] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [alert, setAlert] = useState<EditorAlert | null>(null);
    const [notice, setNotice] = useState<string | null>(() => (location.state as { notice?: unknown } | null)?.notice === CREATED ? CREATED : null);
    const [confirm, setConfirm] = useState<'publish' | 'hide' | 'delete' | 'leave' | null>(null);
    const [leaveTo, setLeaveTo] = useState<{ go: () => void } | null>(null);
    const [picking, setPicking] = useState(false);
    const [moved, setMoved] = useState('');
    const dirtyRef = useRef(dirty);
    dirtyRef.current = dirty;
    const { read } = editor, { ensure } = lookup;

    useEffect(() => {
        // Consume the creation notice once, so a reload does not repeat it.
        if ((location.state as { notice?: unknown } | null)?.notice !== undefined) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null });
    }, [location, navigate]);
    // Entry read: a deep link, reload or creation always reads the quiz (S19 recovery table).
    useEffect(() => {
        if (creating) return;
        let live = true;
        setEntry('loading');
        read(quizId).then((current) => {
            if (!live) return;
            setQuiz(current); setDraft(draftFromQuiz(current)); setDirty(false); setEntry('ready');
        }, (error: unknown) => {
            if (!live || isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 400 || status === 403 || status === 404) setRefusal(status);
            else { setEntry('failed'); setAlert({ type: 'error', message: errorMessage(error) }); }
        });
        return () => { live = false; };
    }, [creating, quizId, read, revision]);
    useEffect(() => { if (draft.questions.length) ensure(draft.questions.map((question) => question.skillId)); }, [draft.questions, ensure]);
    useEffect(() => {
        if (!dirty || noAccess) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty, noAccess]);

    /** In-app leaving with unsaved edits asks first; browser Back cannot be intercepted (A15). */
    const go = (to: string) => { if (dirtyRef.current && !noAccess) { setLeaveTo({ go: () => navigate(to) }); setConfirm('leave'); } else navigate(to); };
    // Shell navigation (Teaching, Catalogue, the home link, Profile) asks through the same confirmation (S19).
    useNavigationGuard((proceed) => {
        if (!dirtyRef.current || noAccess) return false;
        setLeaveTo({ go: proceed }); setConfirm('leave');
        return true;
    });
    const change = (patch: Partial<QuizFormDraft>) => { setDraft((current) => ({ ...current, ...patch })); setDirty(true); setErrors({}); setNotice(null); };

    /** Re-read after an unknown outcome or a refusal; `confirmed` decides whether the read shows that the change committed. */
    const recheck = useCallback(async (confirmed: ((current: B3QuizOwned) => boolean) | null, onConfirmed: () => void, deleting = false) => {
        if (!quiz) return;
        const retry = () => void recheck(confirmed, onConfirmed, deleting);
        try {
            const current = await read(quiz.id);
            setQuiz(current);
            if (!dirtyRef.current) setDraft(draftFromQuiz(current));
            if (confirmed?.(current)) { setAlert(null); onConfirmed(); } else setAlert({ type: 'unknown', current, retry });
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 404) {
                if (deleting) { navigate(listPath, { replace: true, state: { notice: 'Quiz deleted.' } }); return; }
                setGone(true); setAlert({ type: 'not-found' }); return;
            }
            setAlert({ type: 'unknown', current: null, retry });
        }
    }, [quiz, read, navigate, listPath]);

    async function create() {
        if (noAccess) return;
        const next = validateQuiz(draft);
        setErrors(next);
        if (Object.keys(next).length) return;
        setAlert(null); setNotice(null);
        try {
            const created = await editor.create(quizWrite(draft));
            setDirty(false); dirtyRef.current = false;
            navigate(quizPath(courseId, created.id), { replace: true, state: { notice: CREATED } });
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 400) {
                const mapped = fieldErrorsFrom(error, QUIZ_FIELD_MAP);
                if (Object.keys(mapped).length) { setErrors(mapped); return; }
            }
            setAlert(outcomeUnknown(error) ? { type: 'unknown-create' } : { type: 'error', message: messageOf(error) });
        }
    }
    async function save() {
        if (!quiz || noAccess) return;
        const next = validateQuiz(draft);
        setErrors(next);
        if (Object.keys(next).length) return;
        const sent = draft;
        setAlert(null); setNotice(null);
        try {
            const saved = await editor.replace(quiz.id, quizWrite(sent));
            setQuiz(saved); setDraft(draftFromQuiz(saved)); setDirty(false); setNotice('Saved');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 400) {
                const mapped = fieldErrorsFrom(error, QUIZ_FIELD_MAP);
                if (Object.keys(mapped).length) { setErrors(mapped); return; }
            }
            if (status === 409) setAlert({ type: 'conflict-save', message: messageOf(error) });
            else if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else if (outcomeUnknown(error)) {
                setAlert({ type: 'unknown', current: null, retry: () => undefined });
                await recheck((current) => sameQuiz(sent, current), () => { setDraft(sent); setDirty(false); setNotice('Saved'); });
            } else setAlert({ type: 'error', message: messageOf(error) });
        }
    }
    async function publication(published: boolean) {
        setConfirm(null);
        if (!quiz || noAccess) return;
        setAlert(null); setNotice(null);
        try {
            const updated = await editor.setPublication(quiz.id, published);
            setQuiz(updated); if (!dirtyRef.current) setDraft(draftFromQuiz(updated));
            setNotice(published ? 'Quiz published.' : 'Quiz hidden.');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 409) {
                setAlert({ type: 'conflict-publication', message: messageOf(error) });
                // F14: the view re-reads the resource before another attempt.
                try { const current = await read(quiz.id); setQuiz(current); if (!dirtyRef.current) setDraft(draftFromQuiz(current)); } catch { /* the refusal stays shown */ }
            } else if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else if (outcomeUnknown(error)) {
                setAlert({ type: 'unknown', current: null, retry: () => undefined });
                await recheck((current) => current.published === published, () => setNotice(published ? 'Quiz published.' : 'Quiz hidden.'));
            } else setAlert({ type: 'error', message: messageOf(error) });
        }
    }
    async function remove() {
        setConfirm(null);
        if (!quiz || noAccess) return;
        setAlert(null); setNotice(null);
        try {
            await editor.remove(quiz.id);
            setDirty(false); dirtyRef.current = false;
            navigate(listPath, { replace: true, state: { notice: 'Quiz deleted.' } });
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 409) setAlert({ type: 'conflict-delete', message: messageOf(error) });
            else if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else if (outcomeUnknown(error)) { setAlert({ type: 'unknown', current: null, retry: () => undefined }); await recheck(null, () => undefined, true); }
            else setAlert({ type: 'error', message: messageOf(error) });
        }
    }
    const discard = async () => {
        setAlert(null); setErrors({});
        if (!quiz) return;
        try { const current = await read(quiz.id); setQuiz(current); setDraft(draftFromQuiz(current)); setDirty(false); }
        catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); }
            else if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else setAlert({ type: 'error', message: messageOf(error) });
        }
    };
    const move = (index: number, direction: -1 | 1) => {
        const questions = [...draft.questions];
        [questions[index], questions[index + direction]] = [questions[index + direction], questions[index]];
        change({ questions });
        setMoved(`Moved question ${index + 1} to position ${index + direction + 1}.`);
    };

    if (refusal !== undefined) {
        const back = <Button component={Link} to={listPath} variant="contained">Back to quizzes</Button>;
        if (refusal === 403) return <CourseUnavailable status={403} />;
        return refusal === 400
            ? <SystemStateView icon="search" title="We can't open this quiz from this link." message="Check the address, or go back to the quizzes." actions={back} />
            : <SystemStateView icon="search" title="This quiz isn't available. It may have been deleted." message="Go back to the quizzes to see the current list." actions={back} />;
    }
    if (entry === 'loading') return <LoadingView message="Loading the quiz…" variant="block" />;
    if (entry === 'failed') return <Box sx={{ display: 'grid', gap: 2 }}>
        <PageHeader title="Quiz" back={{ label: 'Back to quizzes', href: listPath, onClick: () => go(listPath) }} />
        <ErrorView title="The quiz couldn't be loaded." message={alert?.type === 'error' ? alert.message : undefined} onRetry={() => { setAlert(null); setRevision((value) => value + 1); }} />
    </Box>;

    const published = quiz?.published ?? false;
    const readOnly = published || gone || noAccess || editor.busy;
    const summary = difficultySummary(draft.kind, draft.questions);
    const inQuiz = new Set(draft.questions.map((question) => question.id.toLowerCase()));
    const alertView = (() => {
        if (!alert) return null;
        const row = (...items: ReactNode[]) => <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>{items}</Box>;
        switch (alert.type) {
            case 'conflict-save': return <Alert severity="warning" action={row(
                <Button key="new" size="small" variant="text" onClick={() => void create()} disabled={editor.busy}>Create a new quiz from these settings</Button>,
                <Button key="discard" size="small" variant="text" onClick={() => void discard()} disabled={editor.busy}>Discard my edits</Button>)}>
                {alert.message} This quiz can&apos;t be changed, for example because learners have started it. Your edits are still here.</Alert>;
            case 'conflict-delete': return <Alert severity="warning">This quiz can&apos;t be deleted because learners have started it. {alert.message}</Alert>;
            case 'conflict-publication': return <Alert severity="warning">{alert.message}{quiz?.questionCount === 0 ? ' Add at least one question and save before publishing.' : ''}</Alert>;
            case 'not-found': return <Alert severity="warning" action={row(
                <Button key="back" size="small" variant="text" onClick={() => go(listPath)}>Back to quizzes</Button>,
                <Button key="new" size="small" variant="text" onClick={() => void create()} disabled={editor.busy || noAccess}>Create a new quiz from these settings</Button>)}>
                This quiz isn&apos;t available. It may have been deleted.</Alert>;
            case 'no-access': return <Alert severity="error" action={<Button component={Link} to="/courses" size="small" variant="text">Back to catalogue</Button>}>
                You don&apos;t have access to this course. Only the course&apos;s instructor can manage its assessments.</Alert>;
            case 'unknown-create': return <Alert severity="warning" action={<Button size="small" variant="text" onClick={() => go(listPath)}>Go to quizzes</Button>}>
                We couldn&apos;t confirm whether the quiz was created. Check the quiz list before creating it again.</Alert>;
            case 'unknown': return <Alert severity="warning" action={alert.current === null && !editor.busy ? <Button size="small" variant="text" onClick={alert.retry}>Try again</Button> : undefined}>
                We couldn&apos;t confirm whether your changes were saved.
                {alert.current && <Box component="span" sx={{ display: 'block', mt: 1 }}>Current version: {alert.current.title} · {quizKindLabel[alert.current.kind]} · {alert.current.questionCount} {alert.current.questionCount === 1 ? 'question' : 'questions'} · {alert.current.published ? 'Published' : 'Hidden'}</Box>}</Alert>;
            case 'error': return <Alert severity="error">{alert.message}</Alert>;
        }
    })();
    const status = editor.busy ? 'Saving…' : notice ?? (dirty && !noAccess ? 'Unsaved changes' : null);
    // minmax(0, 1fr) columns let long prompts ellipsize instead of widening the page at 360 px.
    return <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 3, maxWidth: 960 }}>
        <PageHeader title={creating ? 'New quiz' : quiz?.title ?? 'Quiz'} back={{ label: 'Back to quizzes', href: listPath, onClick: () => go(listPath) }}
            meta={quiz && <PublicationPill published={published} />}
            actions={quiz && !gone && !noAccess && (published
                ? <Button variant="outlined" onClick={() => setConfirm('hide')} disabled={editor.busy} startIcon={<PresentationIcon name="eye-off" />}>Hide quiz</Button>
                : <Button variant="contained" onClick={() => setConfirm('publish')} disabled={editor.busy || dirty} startIcon={<PresentationIcon name="eye" />}>Publish quiz</Button>)} />
        {quiz && !published && !gone && !noAccess && dirty && <Typography variant="body2" sx={{ color: 'text.secondary', mt: -2 }}>Save your changes before publishing.</Typography>}
        {published && !gone && !noAccess && <Alert severity="info">Hide this quiz to edit it. Quizzes that learners have already started can&apos;t be edited, even when hidden.</Alert>}
        {alertView}
        <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
            <Typography variant="h3" component="h2">Quiz settings</Typography>
            <TextField label="Quiz title" value={draft.title} onChange={(event) => change({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required disabled={readOnly} />
            <TextField select label="Quiz kind" value={draft.kind} onChange={(event) => change({ kind: event.target.value as QuizKind })} error={!!errors.kind} helperText={errors.kind} required disabled={readOnly}>
                <MenuItem value="diagnostic">{quizKindLabel.diagnostic}</MenuItem><MenuItem value="practice">{quizKindLabel.practice}</MenuItem><MenuItem value="test">{quizKindLabel.test}</MenuItem>
            </TextField>
            <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
                <Typography component="legend" sx={{ fontWeight: 600, mb: 1 }}>Time limit</Typography>
                <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', sm: '160px 160px' } }}>
                    <TextField label="Minutes" value={draft.minutes} onChange={(event) => change({ minutes: event.target.value })} disabled={readOnly} error={!!errors.time}
                        slotProps={{ htmlInput: { inputMode: 'numeric', pattern: '[0-9]*' } }} />
                    <TextField label="Seconds" value={draft.seconds} onChange={(event) => change({ seconds: event.target.value })} disabled={readOnly} error={!!errors.time}
                        slotProps={{ htmlInput: { inputMode: 'numeric', pattern: '[0-9]*' } }} />
                </Box>
                <Typography variant="caption" role={errors.time ? 'alert' : undefined} sx={{ display: 'block', mt: 1, color: errors.time ? 'error.main' : 'text.secondary' }}>
                    {errors.time ?? 'Answers are submitted automatically when time runs out.'}</Typography>
            </Box>
        </Tile>
        <Tile component="section" aria-labelledby="quiz-questions" sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 1.5 }}>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 1.5 }}>
                <Typography id="quiz-questions" variant="h3" component="h2">Questions ({draft.questions.length})</Typography>
                {!readOnly && <Button variant="outlined" onClick={() => setPicking(true)} startIcon={<PresentationIcon name="plus" />}>Add questions</Button>}
            </Box>
            {errors.questions && <Alert severity="error">{errors.questions}</Alert>}
            {draft.questions.length === 0
                ? <EmptyView compact icon="quiz" headingLevel="h3" title="No questions yet" message="Add questions from the question bank." />
                : <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 1 }}>
                    {draft.questions.map((question, index) => <Box component="li" key={question.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.25, borderRadius: `${radius.md}px`, bgcolor: roles.bg.canvas }}>
                        <Typography className="cm-tabular" sx={{ fontWeight: 600, width: 28, flexShrink: 0, color: 'text.secondary' }}>{index + 1}</Typography>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{question.prompt}</Typography>
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{questionKindLabel[question.kind]} · {levelLabel[question.difficulty]} · {lookup.name(question.skillId) ?? 'Loading skill…'}</Typography>
                        </Box>
                        {!readOnly && <>
                            <CircleAction size="sm" variant="plain" icon="move-up" label={`Move question ${index + 1} up`} disabled={index === 0} onClick={() => move(index, -1)} />
                            <CircleAction size="sm" variant="plain" icon="move-down" label={`Move question ${index + 1} down`} disabled={index === draft.questions.length - 1} onClick={() => move(index, 1)} />
                            <CircleAction size="sm" variant="plain" icon="trash" label={`Remove question ${index + 1} from the quiz`} onClick={() => change({ questions: draft.questions.filter((_, position) => position !== index) })} />
                        </>}
                    </Box>)}
                </Box>}
            <Typography role="status" className="cm-visually-hidden">{moved}</Typography>
            {summary && <Typography variant="body2" sx={{ color: 'text.secondary' }}>{summary}</Typography>}
        </Tile>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
            {!noAccess && (creating
                ? <Button variant="contained" onClick={() => void create()} disabled={editor.busy} aria-busy={editor.busy || undefined}>Create quiz</Button>
                : gone ? <Button variant="contained" onClick={() => void create()} disabled={editor.busy}>Create a new quiz from these settings</Button>
                    : !published && <Button variant="contained" onClick={() => void save()} disabled={editor.busy || !dirty} aria-busy={editor.busy || undefined}>Save changes</Button>)}
            {quiz && !published && !gone && !noAccess && <Button variant="outlined" color="error" onClick={() => setConfirm('delete')} disabled={editor.busy} startIcon={<PresentationIcon name="trash" />}>Delete quiz</Button>}
            {status && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>{status}</Typography>}
        </Box>
        {picking && <QuestionPicker courseId={courseId} inQuiz={inQuiz} skillName={lookup.name} ensureSkills={ensure} onCancel={() => setPicking(false)}
            onAdd={(added) => { setPicking(false); change({ questions: [...draft.questions, ...added] }); }} />}
        <ConfirmDialog open={confirm === 'publish'} title="Publish quiz?" message="Learners in this course will see this quiz and can start it." confirmLabel="Publish quiz"
            onCancel={() => setConfirm(null)} onConfirm={() => void publication(true)} />
        <ConfirmDialog open={confirm === 'hide'} title="Hide quiz?" message="Learners can no longer start this quiz. Attempts already in progress still finish when their time runs out." confirmLabel="Hide quiz"
            onCancel={() => setConfirm(null)} onConfirm={() => void publication(false)} />
        <ConfirmDialog open={confirm === 'delete'} isDestructive title="Delete this quiz?" message="This can't be undone." confirmLabel="Delete quiz"
            onCancel={() => setConfirm(null)} onConfirm={() => void remove()} />
        <ConfirmDialog open={confirm === 'leave'} isDestructive title="Leave without saving?" message="Your changes will be lost." cancelLabel="Stay" confirmLabel="Leave"
            onCancel={() => { setConfirm(null); setLeaveTo(null); }} onConfirm={() => { setConfirm(null); setLeaveTo(null); setDirty(false); dirtyRef.current = false; leaveTo?.go(); }} />
    </Box>;
}

/** "Add questions": the bank's pages with checkboxes; questions already in the quiz cannot be selected; picks are appended in the order chosen. */
function QuestionPicker({ courseId, inQuiz, skillName, ensureSkills, onCancel, onAdd }: { courseId: string; inQuiz: ReadonlySet<string>; skillName: (skillId: string) => string | null;
    ensureSkills: (skillIds: readonly string[]) => void; onCancel: () => void; onAdd: (questions: B3Question[]) => void }) {
    const [page, setPage] = useState(0);
    const [picked, setPicked] = useState<B3Question[]>([]);
    const bank = useQuestionBank(courseId, page);
    const sheet = useMediaQuery(useTheme().breakpoints.down('sm'));
    const { data } = bank;
    useEffect(() => { if (data) ensureSkills(data.content.map((question) => question.skillId)); }, [data, ensureSkills]);
    const toggle = (question: B3Question, on: boolean) => setPicked((current) => on ? [...current, question] : current.filter((item) => item.id !== question.id));
    return <Dialog open onClose={onCancel} fullWidth maxWidth="md" fullScreen={sheet} aria-labelledby="question-picker-title">
        <DialogTitle id="question-picker-title">Add questions</DialogTitle>
        <DialogContent>
            {bank.error ? <ErrorView title="Questions couldn't be loaded." message={errorMessage(bank.error)} onRetry={bank.reload} headingLevel="h3" />
                : !data ? <LoadingView message="Loading questions…" variant="block" />
                    : data.content.length === 0 ? <EmptyView compact icon="quiz" headingLevel="h3" title="No questions yet" message="Each question is tagged with a skill and a difficulty." />
                        : <Box component="ul" aria-label="Question bank" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: .5 }}>
                            {data.content.map((question) => {
                                const already = inQuiz.has(question.id.toLowerCase()), checked = already || picked.some((item) => item.id === question.id);
                                return <Box component="li" key={question.id}>
                                    <Box component="label" sx={{ display: 'flex', alignItems: 'center', gap: 1, px: .5, py: .5, borderRadius: `${radius.sm}px`, cursor: already ? 'default' : 'pointer', '&:hover': already ? undefined : { bgcolor: roles.bg.hover } }}>
                                        <Checkbox checked={checked} disabled={already} onChange={(event) => toggle(question, event.target.checked)} />
                                        <Box sx={{ minWidth: 0, flex: 1 }}>
                                            <Typography sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{question.prompt}</Typography>
                                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{questionKindLabel[question.kind]} · {skillName(question.skillId) ?? 'Loading skill…'} · {levelLabel[question.difficulty]}</Typography>
                                        </Box>
                                        {already && <StatusPill size="tag" tone="neutral">In this quiz</StatusPill>}
                                    </Box>
                                </Box>;
                            })}
                        </Box>}
            {data && data.totalElements > 0 && <Box sx={{ mt: 2 }}><PaginationBar page={data.page} totalPages={data.totalPages} totalItems={data.totalElements} pageSize={data.size} itemsOnPage={data.content.length} onChange={setPage} noun="questions" nounOne="question" /></Box>}
        </DialogContent>
        <DialogActions>
            <Typography role="status" variant="body2" sx={{ color: 'text.secondary', mr: 'auto', pl: 1 }}>{picked.length ? `${picked.length} selected` : ''}</Typography>
            <Button variant="outlined" onClick={onCancel}>Cancel</Button>
            <Button variant="contained" onClick={() => onAdd(picked)} disabled={!picked.length}>Add questions</Button>
        </DialogActions>
    </Dialog>;
}
