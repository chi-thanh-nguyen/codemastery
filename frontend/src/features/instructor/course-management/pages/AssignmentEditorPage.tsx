import { useState } from 'react';
import { Box, Button, MenuItem, TextField, Typography } from '@mui/material';
import { Tile } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { StatusPill } from '../../../../shared/components/StatusPill';
import type { AssignmentDraft } from '../types/courseManagementTypes';

export interface AssignmentEditorPageProps { initial: AssignmentDraft; lessons: { id: string; title: string }[]; timeZoneLabel: string; onSave: (assignment: AssignmentDraft) => void; onCancel: () => void }

/** S19 Assignment editor: instructions, deadline with explicit timezone, optional lesson link (US-12). */
export function AssignmentEditorPage({ initial, lessons, timeZoneLabel, onSave, onCancel }: AssignmentEditorPageProps) {
    const [draft, setDraft] = useState(initial), [errors, setErrors] = useState<Record<string, string>>({}), [saved, setSaved] = useState(false);
    const set = (patch: Partial<AssignmentDraft>) => { setDraft((current) => ({ ...current, ...patch })); setSaved(false); };
    return <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 760 }}>
        <Box component="form" noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={(event) => {
            event.preventDefault(); const next: Record<string, string> = {};
            if (!draft.title.trim()) next.title = 'Enter an assignment title.';
            if (!draft.instructions.trim()) next.instructions = 'Write the instructions.';
            if (!draft.deadline) next.deadline = 'Choose a deadline.';
            setErrors(next); if (!Object.keys(next).length) { onSave(draft); setSaved(true); }
        }}>
            <Typography variant="h3" component="h2">{initial.title ? 'Edit assignment' : 'New assignment'}</Typography>
            <TextField label="Assignment title" value={draft.title} onChange={(event) => set({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required />
            <TextField label="Instructions" multiline minRows={5} value={draft.instructions} onChange={(event) => set({ instructions: event.target.value })} error={!!errors.instructions} helperText={errors.instructions} required />
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <TextField label="Deadline" type="datetime-local" value={draft.deadline} onChange={(event) => set({ deadline: event.target.value })} error={!!errors.deadline} helperText={errors.deadline ?? `Time zone: ${timeZoneLabel}. Files are refused after the deadline.`} required />
                <TextField select label="Linked lesson (optional)" value={draft.lessonId ?? ''} onChange={(event) => set({ lessonId: event.target.value || undefined })}>
                    <MenuItem value="">No linked lesson</MenuItem>{lessons.map((lesson) => <MenuItem key={lesson.id} value={lesson.id}>{lesson.title}</MenuItem>)}
                </TextField>
            </Box>
            <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Visibility</Typography>
                <SegmentedControl label="Assignment visibility" value={draft.publication} onChange={(publication) => set({ publication })} options={[{ value: 'hidden', label: 'Hidden', icon: 'eye-off' }, { value: 'published', label: 'Published', icon: 'eye' }]} /></Box>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Assignment grades count toward the course but don't update skill mastery.</Typography>
            <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', flexWrap: 'wrap' }}>
                <Button type="submit" variant="contained">Save assignment</Button><Button variant="outlined" onClick={onCancel}>Cancel</Button>
                {saved && <StatusPill tone="mint" icon="check-circle" size="tag">Saved</StatusPill>}
            </Box>
        </Box>
    </Tile>;
}
