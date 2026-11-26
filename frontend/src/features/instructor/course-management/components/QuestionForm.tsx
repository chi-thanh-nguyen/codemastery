import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Checkbox, Drawer, FormControlLabel, IconButton, ListItemText, MenuItem, Radio, TextField, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Link } from 'react-router';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { radius, roles, shadow } from '../../../../app/theme/theme';
import { ApiError } from '../../../../shared/types/apiTypes';
import type { QuestionDraft, SkillOption } from '../types/courseManagementTypes';
import type { B3Question, B3QuestionWrite, QuestionDifficulty, QuestionKind } from '../api/assessmentAuthoringApi';
import { useQuestionEditor } from '../hooks/useQuestionBank';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown } from '../hooks/useAssessmentEditor';

/*
 * S19 question form (DESIGN.md r12, D10-4; ADR007 §4): kind, plain-text prompt, options with
 * correct toggles, a required skill from the course's skill graph and a required difficulty. There
 * is no code-snippet field. Usage is not observable in advance, so the form opens editable and the
 * server decides: a refusal keeps the draft and offers an explicit "Save as a new question".
 */

export const questionKindLabel: Record<QuestionKind, string> = { single_choice: 'Single choice', multiple_choice: 'Multiple choice', true_false: 'True/false' };
export const difficultyLabel: Record<QuestionDifficulty, string> = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };

/** A skill as the composing route passes it from `getOwnedSkillGraph` (D12-3). */
export interface SkillChoice { id: string; name: string; description: string | null }
/** The course's skill graph read, held by the composing route and passed down (CT16.final-state F1). */
export interface SkillSource { status: 'loading' | 'ready' | 'error'; skills: SkillChoice[]; reload: () => void }

/** Alphabetical by name, ties by UUID (S19 skill select). */
export const sortSkills = (skills: readonly SkillChoice[]) =>
    [...skills].sort((a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }) || a.id.toLowerCase().localeCompare(b.id.toLowerCase()));

/**
 * Skill names for question rows. A `skillId` missing from a loaded graph means the copy is stale
 * (a referenced skill cannot be deleted), so the graph is re-read once for each newly missing skill.
 */
export function useSkillLookup(skills: SkillSource) {
    const reread = useRef(new Set<string>());
    const { status, skills: list, reload } = skills;
    const name = useCallback((skillId: string): string | null => {
        if (status !== 'ready') return status === 'error' ? 'Skill unavailable' : null;
        return list.find((skill) => skill.id.toLowerCase() === skillId.toLowerCase())?.name ?? 'Skill unavailable';
    }, [status, list]);
    const ensure = useCallback((skillIds: readonly string[]) => {
        if (status !== 'ready') return;
        const known = new Set(list.map((skill) => skill.id.toLowerCase()));
        const missing = skillIds.map((skillId) => skillId.toLowerCase()).filter((skillId) => !known.has(skillId) && !reread.current.has(skillId));
        if (!missing.length) return;
        missing.forEach((skillId) => reread.current.add(skillId));
        reload();
    }, [status, list, reload]);
    return { name, ensure };
}

export interface QuestionOptionDraft { key: string; label: string; correct: boolean }
export interface QuestionFormDraft { kind: QuestionKind; prompt: string; options: QuestionOptionDraft[]; skillId: string; difficulty: QuestionDifficulty | '' }

let sequence = 0;
const option = (label = '', correct = false): QuestionOptionDraft => ({ key: `option-${(sequence += 1)}`, label, correct });
const trueFalse = () => [option('True'), option('False')];
export const emptyQuestionDraft = (skillId = ''): QuestionFormDraft => ({ kind: 'single_choice', prompt: '', options: [option(), option()], skillId, difficulty: '' });
export const draftFromQuestion = (question: B3Question): QuestionFormDraft => ({
    kind: question.kind, prompt: question.prompt, skillId: question.skillId, difficulty: question.difficulty,
    options: question.options.map((label, index) => option(label, question.correctOptions[index])),
});
/** The exact write body; prompt and option text are sent as typed (line breaks kept). */
export const questionWrite = (draft: QuestionFormDraft): B3QuestionWrite => ({
    skillId: draft.skillId, prompt: draft.prompt, kind: draft.kind, difficulty: draft.difficulty as QuestionDifficulty,
    options: draft.options.map((item) => item.label), correctOptions: draft.options.map((item) => item.correct),
});
export const sameQuestion = (draft: QuestionFormDraft, question: B3Question) => {
    const write = questionWrite(draft);
    return write.skillId.toLowerCase() === question.skillId.toLowerCase() && write.prompt === question.prompt && write.kind === question.kind &&
        write.difficulty === question.difficulty && write.options.length === question.options.length &&
        write.options.every((label, index) => label === question.options[index] && write.correctOptions[index] === question.correctOptions[index]);
};
/** Kind changes keep the option rules: True/False are fixed, single choice keeps no more than one key. */
export function withKind(draft: QuestionFormDraft, kind: QuestionKind): QuestionFormDraft {
    if (kind === draft.kind) return draft;
    if (kind === 'true_false') return { ...draft, kind, options: trueFalse() };
    if (draft.kind === 'true_false') return { ...draft, kind, options: [option(), option()] };
    return { ...draft, kind, options: kind === 'single_choice' ? draft.options.map((item) => ({ ...item, correct: false })) : draft.options };
}

/** Field rules mirror the approved question constraints (OpenAPI B3QuestionWrite, V004). The backend validates again. */
export function validateQuestion(draft: QuestionFormDraft): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!/\S/.test(draft.prompt)) errors.prompt = 'Enter the question.';
    if (draft.options.length < 2) errors.options = 'Add at least two options.';
    else if (draft.options.some((item) => !/\S/.test(item.label))) errors.options = 'Every option needs text.';
    const correct = draft.options.filter((item) => item.correct).length;
    if (draft.kind === 'multiple_choice' ? correct < 1 : correct !== 1) errors.correct = draft.kind === 'multiple_choice' ? 'Mark at least one correct option.' : 'Mark exactly one correct option.';
    if (!draft.skillId) errors.skillId = 'Choose the skill this question checks.';
    if (!draft.difficulty) errors.difficulty = 'Choose a difficulty.';
    return errors;
}

/** The form fields shared by the production sheet and the design preview. */
export function QuestionFields({ draft, onChange, errors, skills, disabled = false, skillsHref, onGoToSkills }: {
    draft: QuestionFormDraft;
    onChange: (next: QuestionFormDraft) => void;
    errors: Record<string, string>;
    skills: SkillSource;
    disabled?: boolean;
    skillsHref?: string;
    onGoToSkills?: () => void;
}) {
    const set = (patch: Partial<QuestionFormDraft>) => onChange({ ...draft, ...patch });
    const mark = (key: string, value: boolean) => set({ options: draft.options.map((item) => draft.kind === 'multiple_choice' ? (item.key === key ? { ...item, correct: value } : item) : { ...item, correct: item.key === key }) });
    const sorted = sortSkills(skills.skills);
    const known = sorted.some((skill) => skill.id.toLowerCase() === draft.skillId.toLowerCase());
    // minmax(0, 1fr): the segmented control scrolls inside the sheet instead of widening it at 360 px.
    return <Box component="fieldset" disabled={disabled} sx={{ border: 0, p: 0, m: 0, minWidth: 0, display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
        <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Question type</Typography>
            <SegmentedControl label="Question type" value={draft.kind} onChange={(kind) => onChange(withKind(draft, kind))}
                options={[{ value: 'single_choice', label: 'Single choice' }, { value: 'multiple_choice', label: 'Multiple choice' }, { value: 'true_false', label: 'True/false' }]} /></Box>
        <TextField label="Question" multiline minRows={3} value={draft.prompt} onChange={(event) => set({ prompt: event.target.value })} error={!!errors.prompt}
            helperText={errors.prompt ?? 'Plain text. Line breaks are kept.'} required disabled={disabled} />
        <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
            <Typography component="legend" sx={{ fontWeight: 600, mb: 1 }}>Options and correct answer{draft.kind === 'multiple_choice' ? 's' : ''}</Typography>
            <Box sx={{ display: 'grid', gap: 1.5 }}>
                {draft.options.map((item, index) => <Box key={item.key} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <FormControlLabel sx={{ m: 0 }} label={<span className="cm-visually-hidden">Option {index + 1} is correct</span>} disabled={disabled}
                        control={draft.kind === 'multiple_choice' ? <Checkbox checked={item.correct} onChange={(event) => mark(item.key, event.target.checked)} />
                            : <Radio checked={item.correct} onChange={() => mark(item.key, true)} name="correct-option" />} />
                    <TextField value={item.label} disabled={disabled || draft.kind === 'true_false'} fullWidth placeholder={`Option ${index + 1}`} slotProps={{ htmlInput: { 'aria-label': `Option ${index + 1}` } }}
                        onChange={(event) => set({ options: draft.options.map((entry) => entry.key === item.key ? { ...entry, label: event.target.value } : entry) })} />
                    {draft.kind !== 'true_false' && <IconButton aria-label={`Remove option ${index + 1}`} disabled={disabled || draft.options.length <= 2}
                        onClick={() => set({ options: draft.options.filter((entry) => entry.key !== item.key) })}><PresentationIcon name="trash" sx={{ fontSize: 20 }} /></IconButton>}
                </Box>)}
            </Box>
            {(errors.options || errors.correct) && <Typography role="alert" variant="caption" sx={{ display: 'block', mt: 1, color: 'error.main' }}>{errors.options ?? errors.correct}</Typography>}
            {draft.kind !== 'true_false' && <Button variant="text" sx={{ mt: 1 }} disabled={disabled} onClick={() => set({ options: [...draft.options, option()] })} startIcon={<PresentationIcon name="plus" />}>Add option</Button>}
        </Box>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, alignItems: 'start' }}>
            {skills.status === 'error' ? <Alert severity="error" action={<Button size="small" variant="text" onClick={skills.reload}>Try again</Button>}>Skills couldn&apos;t be loaded.</Alert>
                : skills.status === 'ready' && sorted.length === 0 ? <Alert severity="info"
                    action={skillsHref ? <Button component={Link} to={skillsHref} onClick={onGoToSkills} size="small" variant="text">Go to Skills</Button> : undefined}>
                    This course has no skills yet. Add a skill in the Skills tab first.
                </Alert>
                    : <TextField select label="Skill" value={skills.status === 'ready' ? draft.skillId : ''} onChange={(event) => set({ skillId: event.target.value })} required disabled={disabled || skills.status !== 'ready'}
                        error={!!errors.skillId} helperText={errors.skillId ?? (skills.status === 'loading' ? 'Loading skills…' : 'Each question checks one skill.')}
                        slotProps={{ select: { renderValue: (value) => sorted.find((skill) => skill.id === value)?.name ?? 'Skill unavailable' } }}>
                        {!known && draft.skillId && <MenuItem value={draft.skillId} disabled>Skill unavailable</MenuItem>}
                        {sorted.map((skill) => <MenuItem key={skill.id} value={skill.id}><ListItemText primary={skill.name} secondary={skill.description ?? undefined} /></MenuItem>)}
                    </TextField>}
            <TextField select label="Difficulty" value={draft.difficulty} onChange={(event) => set({ difficulty: event.target.value as QuestionDifficulty })} required disabled={disabled}
                error={!!errors.difficulty} helperText={errors.difficulty}>
                <MenuItem value="easy">Easy</MenuItem><MenuItem value="medium">Medium</MenuItem><MenuItem value="hard">Hard</MenuItem>
            </TextField>
        </Box>
    </Box>;
}

/** S19 side sheet frame: right-anchored 480 px panel, full screen below 900 px (DESIGN.md › Dialogs, side sheets and menus). */
function QuestionSheet({ open, title, onClose, closeDisabled, children, footer }: { open: boolean; title: string; onClose: () => void; closeDisabled?: boolean; children: ReactNode; footer: ReactNode }) {
    const full = useMediaQuery(useTheme().breakpoints.down('md'));
    return <Drawer anchor="right" open={open} onClose={() => { if (!closeDisabled) onClose(); }} slotProps={{ paper: { role: 'dialog', 'aria-modal': true, 'aria-labelledby': 'question-sheet-title',
        sx: { width: full ? '100%' : 480, bgcolor: roles.bg.raised, color: roles.text.primary, boxShadow: `${shadow.overlay}, inset 0 0 0 1px ${roles.line.floating}`,
            borderRadius: full ? 0 : `${radius.lg}px 0 0 ${radius.lg}px`, display: 'flex', flexDirection: 'column' } } as object }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, px: 3, pt: 3, pb: 2 }}>
            <Typography id="question-sheet-title" variant="h3" component="h2">{title}</Typography>
            <IconButton aria-label="Close the question form" onClick={onClose} disabled={closeDisabled}><PresentationIcon name="close" /></IconButton>
        </Box>
        <Box sx={{ flex: '1 1 auto', overflowY: 'auto', px: 3, pb: 2 }}>{children}</Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center', px: 3, py: 2, borderTop: `1px solid ${roles.line.hairline}` }}>{footer}</Box>
    </Drawer>;
}

type SheetAlert =
    | { type: 'conflict-save' | 'conflict-delete' | 'error'; message: string }
    | { type: 'not-found' | 'unknown-create' | 'load-failed' | 'no-access' }
    /** An unknown outcome: `current` is the re-read version (null until a re-read succeeds). */
    | { type: 'unknown'; current: B3Question | null; expected: QuestionFormDraft | null; deleting: boolean };
const messageOf = (error: unknown) => error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.';
const QUESTION_FIELD_MAP = {
    prompt: 'prompt',
    options: 'options',
    correctOptions: 'correct',
    skillId: 'skillId',
    difficulty: 'difficulty',
} as const;

export interface QuestionEditorSheetProps {
    courseId: string;
    /** The list row of an existing question (shown while it is read), or null for a new question. */
    question: B3Question | null;
    skills: SkillSource;
    /** Re-read the skill graph once when a skill is missing from it. */
    ensureSkills: (skillIds: readonly string[]) => void;
    /** Optional route to the course's Skills tab when the course has no skills yet (S19). */
    skillsHref?: string;
    /** Leave without a change. */
    onClose: () => void;
    /** A confirmed save or delete; the bank page is re-read. */
    onDone: (outcome: 'saved' | 'deleted') => void;
}

/** Production question form: mount it with a key per opened question so each opening starts from a fresh read. */
export function QuestionEditorSheet({ courseId, question, skills, ensureSkills, skillsHref, onClose, onDone }: QuestionEditorSheetProps) {
    const editor = useQuestionEditor(courseId);
    const questionId = question?.id ?? null;
    const [snapshot, setSnapshot] = useState<B3Question | null>(null);
    const [reading, setReading] = useState(questionId !== null);
    const [gone, setGone] = useState(false);
    const [noAccess, setNoAccess] = useState(false);
    const [draft, setDraft] = useState<QuestionFormDraft>(() => question ? draftFromQuestion(question) : emptyQuestionDraft());
    const [dirty, setDirty] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [alert, setAlert] = useState<SheetAlert | null>(null);
    const [confirm, setConfirm] = useState<'discard' | 'delete' | null>(null);
    // Callbacks from the page may change identity on each render; reads use their latest value.
    const latest = useRef({ ensureSkills, onDone, dirty });
    latest.current = { ensureSkills, onDone, dirty };
    const { read } = editor;

    /**
     * `b3ReadQuestion` on opening, after "Discard my edits" (F14: re-read before another attempt) and after
     * an unknown outcome. The draft is replaced only on opening without edits or on an explicit discard.
     */
    const reread = useCallback(async (mode: 'open' | 'discard' | 'unknown', expected: QuestionFormDraft | null = null, deleting = false) => {
        if (questionId === null) return;
        setReading(true);
        try {
            const current = await read(questionId);
            setSnapshot(current); setReading(false); latest.current.ensureSkills([current.skillId]);
            if (mode === 'unknown') {
                // A version equal to the draft confirms that the save committed.
                if (expected && sameQuestion(expected, current)) { latest.current.onDone('saved'); return; }
                setAlert({ type: 'unknown', current, expected, deleting });
            } else if (mode === 'discard' || !latest.current.dirty) { setDraft(draftFromQuestion(current)); setDirty(false); }
        } catch (error) {
            if (isCancelled(error)) return;
            setReading(false);
            const status = failureStatus(error);
            // After an unknown deletion, a missing definition confirms that the deletion committed.
            if (mode === 'unknown' && deleting && status === 404) { latest.current.onDone('deleted'); return; }
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else setAlert(mode === 'unknown' ? { type: 'unknown', current: null, expected, deleting } : { type: 'load-failed' });
        }
    }, [questionId, read]);
    useEffect(() => { void reread('open'); }, [reread]);

    const change = (next: QuestionFormDraft) => { setDraft(next); setDirty(true); setErrors({}); };
    const skillsReady = skills.status === 'ready' && skills.skills.length > 0;
    // Another save is offered only after the question was read (on opening and after an unknown outcome).
    const awaitingRead = reading || alert?.type === 'load-failed' || alert?.type === 'unknown' && alert.current === null;
    const canSave = !editor.busy && !noAccess && skillsReady && !awaitingRead;

    async function save(asNew: boolean) {
        const next = validateQuestion(draft);
        setErrors(next);
        if (Object.keys(next).length || !skillsReady || noAccess) return;
        const target = !asNew && questionId !== null && !gone ? questionId : null;
        const sent = draft;
        setAlert(null);
        try {
            if (target) await editor.replace(target, questionWrite(sent)); else await editor.create(questionWrite(sent));
            onDone('saved');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) {
                setNoAccess(true);
                setAlert({ type: 'no-access' });
                return;
            }
            if (status === 400) {
                const mapped = fieldErrorsFrom(error, QUESTION_FIELD_MAP);
                if (Object.keys(mapped).length) {
                    setErrors(mapped);
                    return;
                }
            }
            if (target && status === 409) {
                setAlert({ type: 'conflict-save', message: messageOf(error) });
                return;
            }
            if (status === 404) {
                if (!target) {
                    // S19: a rejected skill on create is surfaced at the Skill field, and the graph is re-read.
                    skills.reload();
                    setErrors({ skillId: 'This skill is no longer available. Choose another skill.' });
                    return;
                }
                if (snapshot && sent.skillId.toLowerCase() !== snapshot.skillId.toLowerCase()) {
                    skills.reload();
                    try {
                        const current = await read(target);
                        setSnapshot(current);
                        setErrors({ skillId: 'This skill is no longer available. Choose another skill.' });
                        return;
                    } catch (readError) {
                        if (isCancelled(readError)) return;
                        if (failureStatus(readError) === 403) {
                            setNoAccess(true);
                            setAlert({ type: 'no-access' });
                            return;
                        }
                    }
                }
                setGone(true);
                setAlert({ type: 'not-found' });
                return;
            }
            if (outcomeUnknown(error)) {
                if (target) { setAlert({ type: 'unknown', current: null, expected: sent, deleting: false }); await reread('unknown', sent); }
                else setAlert({ type: 'unknown-create' });
            } else setAlert({ type: 'error', message: messageOf(error) });
        }
    }
    async function remove() {
        setConfirm(null);
        if (questionId === null || noAccess) return;
        setAlert(null);
        try {
            await editor.remove(questionId);
            onDone('deleted');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); }
            else if (status === 409) setAlert({ type: 'conflict-delete', message: messageOf(error) });
            else if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); }
            else if (outcomeUnknown(error)) { setAlert({ type: 'unknown', current: null, expected: null, deleting: true }); await reread('unknown', null, true); }
            else setAlert({ type: 'error', message: messageOf(error) });
        }
    }
    const discard = () => { setAlert(null); setErrors({}); if (snapshot) { setDraft(draftFromQuestion(snapshot)); setDirty(false); } void reread('discard'); };
    const requestClose = () => { if (editor.busy) return; if (dirty && !noAccess) setConfirm('discard'); else onClose(); };

    const editing = questionId !== null && !gone && !noAccess;
    const actions = (...buttons: ReactNode[]) => <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>{buttons}</Box>;
    const alertView = (() => {
        if (!alert) return null;
        switch (alert.type) {
            case 'conflict-save': return <Alert severity="warning" action={actions(
                <Button key="new" size="small" variant="text" onClick={() => void save(true)} disabled={!canSave}>Save as a new question</Button>,
                <Button key="discard" size="small" variant="text" onClick={discard} disabled={editor.busy}>Discard my edits</Button>)}>
                {alert.message} This question can&apos;t be changed, for example because learners have answered it. Your edits are still here.</Alert>;
            case 'conflict-delete': return <Alert severity="warning">This question can&apos;t be deleted, for example because it is in a quiz or has been answered. {alert.message}</Alert>;
            case 'not-found': return <Alert severity="warning" action={actions(
                <Button key="back" size="small" variant="text" onClick={onClose} disabled={editor.busy}>Back to the question bank</Button>,
                dirty && <Button key="new" size="small" variant="text" onClick={() => void save(true)} disabled={!canSave}>Save as a new question</Button>)}>This question is no longer available.</Alert>;
            case 'no-access': return <Alert severity="error" action={<Button size="small" variant="text" onClick={onClose}>Close</Button>}>
                You don&apos;t have access to this course. Only the course&apos;s instructor can manage its assessments.</Alert>;
            case 'unknown-create': return <Alert severity="warning">We couldn&apos;t confirm whether the question was created. Check the question bank before creating it again.</Alert>;
            case 'unknown': return <Alert severity="warning" action={alert.current === null && !reading ? <Button size="small" variant="text" onClick={() => void reread('unknown', alert.expected, alert.deleting)} disabled={editor.busy}>Try again</Button> : undefined}>
                We couldn&apos;t confirm whether your changes were saved.
                {alert.current && <Box component="span" sx={{ display: 'block', mt: 1 }}>Current version: <Box component="span" sx={{ whiteSpace: 'pre-line' }}>{alert.current.prompt}</Box> · {questionKindLabel[alert.current.kind]} · {difficultyLabel[alert.current.difficulty]}</Box>}</Alert>;
            case 'load-failed': return <Alert severity="error" action={<Button size="small" variant="text" onClick={() => void reread(snapshot ? 'discard' : 'open')} disabled={editor.busy || reading}>Try again</Button>}>The question couldn&apos;t be loaded.</Alert>;
            case 'error': return <Alert severity="error">{alert.message}</Alert>;
        }
    })();
    const status = reading ? 'Loading the question…' : editor.busy ? 'Saving…' : null;
    return <QuestionSheet open title={questionId === null ? 'New question' : 'Edit question'} onClose={requestClose} closeDisabled={editor.busy}
        footer={<>
            {editing && <Button variant="outlined" color="error" onClick={() => setConfirm('delete')} disabled={editor.busy || awaitingRead} startIcon={<PresentationIcon name="trash" />}>Delete question</Button>}
            <Box sx={{ flex: 1 }} />
            {status && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>{status}</Typography>}
            <Button variant="outlined" onClick={requestClose} disabled={editor.busy}>Cancel</Button>
            <Button variant="contained" onClick={() => void save(gone)} disabled={!canSave} aria-busy={editor.busy || undefined}>{gone ? 'Save as a new question' : 'Save question'}</Button>
        </>}>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
            {alertView}
            <QuestionFields draft={draft} onChange={change} errors={errors} skills={skills} disabled={editor.busy || reading || noAccess}
                skillsHref={skillsHref} onGoToSkills={() => { setDirty(false); onClose(); }} />
        </Box>
        <ConfirmDialog open={confirm === 'discard'} isDestructive title="Discard your changes?" message="Your changes will be lost." cancelLabel="Keep editing" confirmLabel="Discard"
            onCancel={() => setConfirm(null)} onConfirm={() => { setConfirm(null); onClose(); }} />
        <ConfirmDialog open={confirm === 'delete'} isDestructive title="Delete this question?" message="This can't be undone." confirmLabel="Delete question"
            onCancel={() => setConfirm(null)} onConfirm={() => void remove()} />
    </QuestionSheet>;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

const previewDraft = (question: QuestionDraft): QuestionFormDraft => ({ kind: question.kind, prompt: question.prompt, skillId: question.skillId, difficulty: question.difficulty,
    options: question.options.map((item) => option(item.label, item.correct)) });
export interface QuestionFormProps { open: boolean; initial: QuestionDraft; skills: SkillOption[]; onCancel: () => void; onSave: (question: QuestionDraft) => void }
/** The preview's question form: the r12 fields with sample skills; saving hands the draft back to the preview. */
export function QuestionForm({ open, initial, skills, onCancel, onSave }: QuestionFormProps) {
    const [draft, setDraft] = useState<QuestionFormDraft>(() => previewDraft(initial)), [errors, setErrors] = useState<Record<string, string>>({});
    const source: SkillSource = { status: 'ready', skills: skills.map((skill) => ({ id: skill.id, name: skill.name, description: null })), reload: () => undefined };
    const save = () => {
        const next = validateQuestion(draft); setErrors(next);
        if (Object.keys(next).length) return;
        const { code: _code, ...rest } = initial;
        onSave({ ...rest, kind: draft.kind, prompt: draft.prompt, skillId: draft.skillId, difficulty: draft.difficulty as QuestionDifficulty,
            options: draft.options.map((item) => ({ id: item.key, label: item.label, correct: item.correct })) });
    };
    return <QuestionSheet open={open} title={initial.id ? 'Edit question' : 'New question'} onClose={onCancel}
        footer={<><Box sx={{ flex: 1 }} /><Button variant="outlined" onClick={onCancel}>Cancel</Button><Button variant="contained" onClick={save}>Save question</Button></>}>
        <QuestionFields draft={draft} onChange={(next) => { setDraft(next); setErrors({}); }} errors={errors} skills={source} />
    </QuestionSheet>;
}
