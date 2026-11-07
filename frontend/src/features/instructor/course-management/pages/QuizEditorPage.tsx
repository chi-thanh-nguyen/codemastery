import { useState } from 'react';
import { Alert, Box, Button, Checkbox, MenuItem, TextField, Typography } from '@mui/material';
import { Tile, CircleAction } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { StatusPill } from '../../../../shared/components/StatusPill';
import type { QuestionDraft, QuizDraft, SkillOption } from '../types/courseManagementTypes';
import { difficultyLabel } from '../types/courseManagementTypes';
import { color, radius } from '../../../../app/theme/theme';

export interface QuizEditorPageProps { initial: QuizDraft; questions: QuestionDraft[]; skills: SkillOption[]; onSave: (quiz: QuizDraft) => void; onCancel: () => void }

/** S19 Quiz editor: kind, time limit (stored as seconds), ordered questions, visibility (US-12). */
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
                    {draft.questionIds.map((id, index) => { const question = byId.get(id); return question && <Box component="li" key={id} sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.25, borderRadius: `${radius.md}px`, bgcolor: color.canvas100 }}>
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
                        <Box component="label" sx={{ display: 'flex', alignItems: 'center', gap: 1, px: .5, borderRadius: `${radius.sm}px`, cursor: 'pointer', '&:hover': { bgcolor: color.canvas100 } }}>
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
