import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Autocomplete, Box, Button, Chip, TextField, Typography } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { Tile } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { MaterialForm } from '../components/MaterialForm';
import type { LessonDraft, SkillOption } from '../types/courseManagementTypes';
import { color, radius, shadow } from '../../../../app/theme/theme';

const materialIcon = { text: 'text', file: 'file', external_video: 'video' } as const;
const materialLabel = { text: 'Text', file: 'File', external_video: 'Video link' } as const;

export interface LessonEditorPageProps { courseTitle: string; initial: LessonDraft; skills: SkillOption[]; onSave: (draft: LessonDraft) => void; onLeave: () => void; back?: Crumb; notice?: ReactNode }

/** S18 Lesson editor (Form archetype): title, text, materials, skills and publication; unsaved-change guard. */
export function LessonEditorPage({ courseTitle, initial, skills, onSave, onLeave, back, notice }: LessonEditorPageProps) {
    const [draft, setDraft] = useState(initial), [saved, setSaved] = useState(initial), [materialOpen, setMaterialOpen] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({}), [leaving, setLeaving] = useState(false), [status, setStatus] = useState<string | null>(null);
    const dirty = JSON.stringify(draft) !== JSON.stringify(saved);
    const set = (patch: Partial<LessonDraft>) => { setDraft((current) => ({ ...current, ...patch })); setStatus(null); };
    const save = () => {
        const next: Record<string, string> = {};
        if (!draft.title.trim()) next.title = 'Enter a lesson title.';
        if (draft.skillIds.length === 0) next.skills = 'Map at least one skill to this lesson.';
        setErrors(next);
        if (Object.keys(next).length) { setStatus(null); return; }
        onSave(draft); setSaved(draft); setStatus('Saved');
    };
    return <Page width="form">
        <PageHeader title={saved.title || 'New lesson'} subtitle={courseTitle} back={back ? { ...back, onClick: () => (dirty ? setLeaving(true) : back.onClick?.()) } : undefined} />
        {notice}
        <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); save(); }} sx={{ display: 'grid', gap: 3, maxWidth: 760, pb: 12 }}>
            <Tile component="section" aria-labelledby="lesson-basics" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
                <Typography id="lesson-basics" variant="h3" component="h2">Lesson</Typography>
                <TextField label="Lesson title" value={draft.title} onChange={(event) => set({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required />
                <TextField label="Lesson text" multiline minRows={8} value={draft.content} onChange={(event) => set({ content: event.target.value })} helperText="Optional when materials carry the content. Formatting preview arrives with approved Markdown rendering." />
            </Tile>
            <Tile component="section" aria-labelledby="lesson-materials-edit" padding={{ xs: 3, sm: 4 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
                    <Typography id="lesson-materials-edit" variant="h3" component="h2">Materials</Typography>
                    <Button variant="outlined" onClick={() => setMaterialOpen(true)} startIcon={<PresentationIcon name="plus" />}>Add material</Button>
                </Box>
                {draft.materials.length === 0 ? <Typography sx={{ color: 'text.secondary' }}>No materials yet. Courses need at least two material types across their lessons.</Typography>
                    : <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                        {draft.materials.map((material) => <Box component="li" key={material.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: `${radius.md}px`, bgcolor: color.canvas100 }}>
                            <PresentationIcon name={materialIcon[material.kind]} sx={{ fontSize: 20, color: 'text.secondary' }} />
                            <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{material.title}</Typography>
                                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{materialLabel[material.kind]}{material.kind === 'file' ? ` · ${material.fileName}` : material.kind === 'external_video' ? ` · ${material.url}` : ''}</Typography></Box>
                            <CircleAction label={`Remove ${material.title}`} icon="trash" variant="plain" onClick={() => set({ materials: draft.materials.filter((item) => item.id !== material.id) })} />
                        </Box>)}
                    </Box>}
            </Tile>
            <Tile component="section" aria-labelledby="lesson-skills-edit" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
                <Typography id="lesson-skills-edit" variant="h3" component="h2">Skills and publication</Typography>
                <Autocomplete multiple options={skills} getOptionLabel={(option) => option.name} value={skills.filter((skill) => draft.skillIds.includes(skill.id))}
                    onChange={(_, value) => set({ skillIds: value.map((skill) => skill.id) })} isOptionEqualToValue={(a, b) => a.id === b.id}
                    renderValue={(value, getItemProps) => value.map((option, index) => <Chip {...getItemProps({ index })} key={option.id} label={option.name} />)}
                    renderInput={(params) => <TextField {...params} label="Skills covered" error={!!errors.skills} helperText={errors.skills ?? 'Every published lesson needs at least one skill for adaptive mode.'} required />} />
                <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Visibility</Typography>
                    <SegmentedControl label="Lesson visibility" value={draft.publication} onChange={(publication) => set({ publication })} options={[{ value: 'hidden', label: 'Hidden', icon: 'eye-off' }, { value: 'published', label: 'Published', icon: 'eye' }]} /></Box>
            </Tile>
            {Object.keys(errors).length > 0 && <Alert severity="error">Fix the highlighted fields, then save again.</Alert>}
            <Box role="region" aria-label="Save lesson" sx={{ position: 'sticky', bottom: 16, zIndex: 5, bgcolor: color.surface, borderRadius: `${radius.pill}px`, boxShadow: shadow.raised, p: 1, pl: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Typography role="status" sx={{ flex: 1, color: dirty ? color.butter800 : 'text.secondary', fontWeight: 600 }}>{dirty ? 'Unsaved changes' : status ?? 'All changes saved'}</Typography>
                <Button type="submit" variant="contained" disabled={!dirty}>Save changes</Button>
            </Box>
        </Box>
        <MaterialForm open={materialOpen} onCancel={() => setMaterialOpen(false)} onSave={(material) => { set({ materials: [...draft.materials, { ...material, id: `mat-${Date.now().toString(36)}` }] }); setMaterialOpen(false); }} />
        <ConfirmDialog open={leaving} title="Leave without saving?" message="Your unsaved changes to this lesson will be lost." confirmLabel="Leave without saving" cancelLabel="Keep editing" isDestructive
            onCancel={() => setLeaving(false)} onConfirm={() => { setLeaving(false); onLeave(); }} />
    </Page>;
}
