import { useState } from 'react';
import { Alert, Autocomplete, Box, Button, Chip, TextField, Typography } from '@mui/material';
import type { EditableSkill } from '../types/skillGraphTypes';

export interface SkillFormProps {
    skill: EditableSkill | null;
    skills: EditableSkill[];
    onSave: (skill: { id?: string; name: string; description: string; prerequisiteIds: string[] }) => void;
    /** Server rejection message (for example a cycle), shown verbatim; the graph stays unchanged. */
    serverError?: string | null;
    onCancel?: () => void;
}

/** Add or edit a skill and its prerequisites: the accessible way to create edges (US-11). */
export function SkillForm({ skill, skills, onSave, serverError, onCancel }: SkillFormProps) {
    const [name, setName] = useState(skill?.name ?? ''), [description, setDescription] = useState(skill?.description ?? ''), [prerequisites, setPrerequisites] = useState<string[]>(skill?.prerequisiteIds ?? []);
    const [error, setError] = useState<string | null>(null);
    const options = skills.filter((item) => item.id !== skill?.id);
    return <Box component="form" noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={(event) => { event.preventDefault(); if (!name.trim()) { setError('Enter a skill name.'); return; } setError(null); onSave({ id: skill?.id, name: name.trim(), description, prerequisiteIds: prerequisites }); }}>
        <Typography variant="h4" component="h3">{skill ? `Edit ${skill.name}` : 'Add skill'}</Typography>
        {serverError && <Alert severity="error" role="alert">{serverError}</Alert>}
        <TextField label="Skill name" value={name} onChange={(event) => setName(event.target.value)} error={!!error} helperText={error} required />
        <TextField label="Description (optional)" multiline minRows={2} value={description} onChange={(event) => setDescription(event.target.value)} />
        <Autocomplete multiple options={options} getOptionLabel={(option) => option.name} value={options.filter((item) => prerequisites.includes(item.id))} isOptionEqualToValue={(a, b) => a.id === b.id}
            onChange={(_, value) => setPrerequisites(value.map((item) => item.id))}
            renderValue={(value, getItemProps) => value.map((option, index) => <Chip {...getItemProps({ index })} key={option.id} label={option.name} />)}
            renderInput={(params) => <TextField {...params} label="Prerequisites" helperText="Skills a learner should master first. Prerequisites guide; they never lock lessons." />} />
        <Box sx={{ display: 'flex', gap: 1 }}><Button type="submit" variant="contained">{skill ? 'Save skill' : 'Add skill'}</Button>{onCancel && <Button variant="outlined" onClick={onCancel}>Cancel</Button>}</Box>
    </Box>;
}
