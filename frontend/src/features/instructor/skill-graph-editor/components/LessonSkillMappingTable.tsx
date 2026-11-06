import { Autocomplete, Chip, TextField, Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { StatusPill } from '../../../../shared/components/StatusPill';
import type { EditableSkill, MappingLesson } from '../types/skillGraphTypes';

/** Lesson → skill mapping. Every published lesson needs at least one skill before adaptive mode (US-11). */
export function LessonSkillMappingTable({ lessons, skills, onChange }: { lessons: MappingLesson[]; skills: EditableSkill[]; onChange: (lessonId: string, skillIds: string[]) => void }) {
    return <DataTable label="Lesson to skill mapping" rows={lessons} getRowId={(lesson) => lesson.id}
        columns={[
            { key: 'lesson', header: 'Lesson', primary: true, width: '32%', render: (lesson) => <><Typography sx={{ fontWeight: 600 }}>{lesson.title}</Typography><Typography variant="caption" sx={{ color: 'text.secondary' }}>{lesson.chapterTitle}</Typography></> },
            { key: 'state', header: 'Status', render: (lesson) => !lesson.published ? <StatusPill size="tag" tone="neutral" icon="eye-off">Hidden</StatusPill>
                : lesson.skillIds.length ? <StatusPill size="tag" tone="mint" icon="check-circle">Mapped</StatusPill> : <StatusPill size="tag" tone="butter" icon="alert-triangle">Needs a skill</StatusPill> },
            { key: 'skills', header: 'Skills', width: '46%', render: (lesson) => <Autocomplete multiple size="small" options={skills} getOptionLabel={(option) => option.name} value={skills.filter((skill) => lesson.skillIds.includes(skill.id))}
                isOptionEqualToValue={(a, b) => a.id === b.id} onChange={(_, value) => onChange(lesson.id, value.map((skill) => skill.id))}
                renderValue={(value, getItemProps) => value.map((option, index) => <Chip {...getItemProps({ index })} key={option.id} label={option.name} size="small" />)}
                renderInput={(params) => <TextField {...params} placeholder="Add skill" slotProps={{ ...params.slotProps, htmlInput: { ...params.slotProps.htmlInput, 'aria-label': `Skills for ${lesson.title}` } }} />} /> },
        ]} />;
}
