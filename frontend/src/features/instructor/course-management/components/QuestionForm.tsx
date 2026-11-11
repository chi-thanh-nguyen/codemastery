import { useState } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem, Radio, TextField, Typography } from '@mui/material';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { codeFontStack } from '../../../../app/theme/theme';
import type { Difficulty, QuestionDraft, SkillOption } from '../types/courseManagementTypes';

let sequence = 0;
const optionId = () => `opt-${Date.now().toString(36)}-${(sequence += 1)}`;
export const emptyQuestion = (skillId = ''): QuestionDraft => ({ id: '', kind: 'single_choice', prompt: '', options: [{ id: optionId(), label: '', correct: false }, { id: optionId(), label: '', correct: false }], skillId, difficulty: 'easy', usedInQuizzes: 0, usedInAttempts: false });

/** Field rules mirror the approved question constraints (data-model §11). The backend validates again. */
export function validateQuestion(question: QuestionDraft): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!question.prompt.trim()) errors.prompt = 'Enter the question.';
    if (question.options.length < 2) errors.options = 'Add at least two options.';
    if (question.options.some((option) => !option.label.trim())) errors.options = 'Every option needs text.';
    const correct = question.options.filter((option) => option.correct).length;
    if (question.kind === 'multiple_choice' ? correct < 1 : correct !== 1) errors.correct = question.kind === 'multiple_choice' ? 'Mark at least one correct option.' : 'Mark exactly one correct option.';
    if (!question.skillId) errors.skillId = 'Choose the skill this question checks.';
    return errors;
}

export interface QuestionFormProps { open: boolean; initial: QuestionDraft; skills: SkillOption[]; onCancel: () => void; onSave: (question: QuestionDraft) => void }

export function QuestionForm({ open, initial, skills, onCancel, onSave }: QuestionFormProps) {
    const [draft, setDraft] = useState<QuestionDraft>(initial), [errors, setErrors] = useState<Record<string, string>>({});
    const set = (patch: Partial<QuestionDraft>) => setDraft((current) => ({ ...current, ...patch }));
    const setKind = (kind: QuestionDraft['kind']) => set({ kind, options: kind === 'true_false' ? [{ id: optionId(), label: 'True', correct: false }, { id: optionId(), label: 'False', correct: false }]
        : draft.kind === 'true_false' ? [{ id: optionId(), label: '', correct: false }, { id: optionId(), label: '', correct: false }] : draft.options.map((option) => ({ ...option, correct: kind === 'single_choice' ? false : option.correct })) });
    const mark = (id: string, value: boolean) => set({ options: draft.options.map((option) => draft.kind === 'multiple_choice' ? (option.id === id ? { ...option, correct: value } : option) : { ...option, correct: option.id === id }) });
    const editing = !!initial.id;
    return <Dialog open={open} onClose={onCancel} fullWidth maxWidth="md" aria-labelledby="question-form-title" slotProps={{ paper: { sx: { maxWidth: 760 } } }}>
        <DialogTitle id="question-form-title">{editing ? 'Edit question' : 'New question'}</DialogTitle>
        <DialogContent>
            {draft.usedInAttempts && <Alert severity="info" sx={{ mb: 2 }}>This question is used in past attempts. Saving creates a new version; past attempts keep the original.</Alert>}
            <Box component="form" id="question-form" noValidate onSubmit={(event) => { event.preventDefault(); const next = validateQuestion(draft); setErrors(next); if (!Object.keys(next).length) onSave(draft); }} sx={{ display: 'grid', gap: 2.5, pt: 1 }}>
                <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Question type</Typography>
                    <SegmentedControl label="Question type" value={draft.kind} onChange={setKind} options={[{ value: 'single_choice', label: 'Single choice' }, { value: 'multiple_choice', label: 'Multiple choice' }, { value: 'true_false', label: 'True / false' }]} /></Box>
                <TextField label="Question" multiline minRows={2} value={draft.prompt} onChange={(event) => set({ prompt: event.target.value })} error={!!errors.prompt} helperText={errors.prompt} required />
                <TextField label="Code snippet (optional)" multiline minRows={3} value={draft.code ?? ''} onChange={(event) => set({ code: event.target.value || undefined })}
                    helperText="For “predict the output” questions. Shown as code; never run." slotProps={{ htmlInput: { style: { fontFamily: codeFontStack }, spellCheck: false } }} />
                <Box component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
                    <Typography component="legend" sx={{ fontWeight: 600, mb: 1 }}>Options and correct answer{draft.kind === 'multiple_choice' ? 's' : ''}</Typography>
                    <Box sx={{ display: 'grid', gap: 1.5 }}>
                        {draft.options.map((option, index) => <Box key={option.id} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                            <FormControlLabel sx={{ m: 0 }} label={<span className="cm-visually-hidden">Option {index + 1} is correct</span>}
                                control={draft.kind === 'multiple_choice' ? <Checkbox checked={option.correct} onChange={(event) => mark(option.id, event.target.checked)} />
                                    : <Radio checked={option.correct} onChange={() => mark(option.id, true)} name="correct-option" />} />
                            <TextField value={option.label} disabled={draft.kind === 'true_false'} fullWidth placeholder={`Option ${index + 1}`} slotProps={{ htmlInput: { 'aria-label': `Option ${index + 1}` } }}
                                onChange={(event) => set({ options: draft.options.map((item) => item.id === option.id ? { ...item, label: event.target.value } : item) })} />
                            {draft.kind !== 'true_false' && <IconButton aria-label={`Remove option ${index + 1}`} disabled={draft.options.length <= 2} onClick={() => set({ options: draft.options.filter((item) => item.id !== option.id) })}><PresentationIcon name="trash" sx={{ fontSize: 20 }} /></IconButton>}
                        </Box>)}
                    </Box>
                    {(errors.options || errors.correct) && <Typography role="alert" variant="caption" sx={{ display: 'block', mt: 1, color: 'error.main' }}>{errors.options ?? errors.correct}</Typography>}
                    {draft.kind !== 'true_false' && <Button variant="text" sx={{ mt: 1 }} onClick={() => set({ options: [...draft.options, { id: optionId(), label: '', correct: false }] })} startIcon={<PresentationIcon name="plus" />}>Add option</Button>}
                </Box>
                <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                    <TextField select label="Skill" value={draft.skillId} onChange={(event) => set({ skillId: event.target.value })} error={!!errors.skillId} helperText={errors.skillId ?? 'Each question checks one skill.'} required>
                        {skills.map((skill) => <MenuItem key={skill.id} value={skill.id}>{skill.name}</MenuItem>)}
                    </TextField>
                    <TextField select label="Difficulty" value={draft.difficulty} onChange={(event) => set({ difficulty: event.target.value as Difficulty })} required>
                        <MenuItem value="easy">Easy</MenuItem><MenuItem value="medium">Medium</MenuItem><MenuItem value="hard">Hard</MenuItem>
                    </TextField>
                </Box>
            </Box>
        </DialogContent>
        <DialogActions><Button variant="outlined" onClick={onCancel}>Cancel</Button><Button type="submit" form="question-form" variant="contained">Save question</Button></DialogActions>
    </Dialog>;
}
