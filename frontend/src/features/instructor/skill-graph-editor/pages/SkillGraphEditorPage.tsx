import type { ReactNode } from 'react';
import { Box, Button } from '@mui/material';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { Tile } from '../../../../shared/components/Tile';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { SkillGraphCanvas } from '../components/SkillGraphCanvas';
import { SkillForm } from '../components/SkillForm';
import { LessonSkillMappingTable } from '../components/LessonSkillMappingTable';
import { AdaptiveSettingsPanel } from '../components/AdaptiveSettingsPanel';
import type { EditableSkill, MappingLesson, ReadinessCheck } from '../types/skillGraphTypes';

export type SkillEditorTab = 'graph' | 'mapping' | 'adaptive';
export interface SkillGraphEditorPageProps {
    courseTitle: string;
    tab: SkillEditorTab;
    onTabChange: (tab: SkillEditorTab) => void;
    skills: EditableSkill[];
    selectedId: string | null;
    onSelect: (id: string | null) => void;
    adding: boolean;
    onAdd: () => void;
    onSaveSkill: (skill: { id?: string; name: string; description: string; prerequisiteIds: string[] }) => void;
    serverError?: string | null;
    lessons: MappingLesson[];
    onMappingChange: (lessonId: string, skillIds: string[]) => void;
    readiness: ReadinessCheck[];
    adaptiveEnabled: boolean;
    onAdaptiveToggle: (next: boolean) => void;
    status?: ReactNode;
}

/** S20 Skill graph editor: same-course skills and prerequisites, lesson mapping and adaptive readiness (US-11). */
export function SkillGraphEditorPage(props: SkillGraphEditorPageProps) {
    const { courseTitle, tab, onTabChange, skills, selectedId, onSelect, adding, onAdd, onSaveSkill, serverError, lessons, onMappingChange, readiness, adaptiveEnabled, onAdaptiveToggle, status } = props;
    const selected = skills.find((skill) => skill.id === selectedId) ?? null;
    return <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 3 }}>
            <SegmentedControl label="Skill editor sections" value={tab} onChange={onTabChange} options={[{ value: 'graph', label: 'Skill graph' }, { value: 'mapping', label: 'Lesson mapping' }, { value: 'adaptive', label: 'Adaptive mode' }]} />
            {tab === 'graph' && <Button variant="contained" onClick={onAdd} startIcon={<PresentationIcon name="plus" />}>Add skill</Button>}
        </Box>
        {status}
        {tab === 'graph' && (skills.length === 0 && !adding ? <EmptyView icon="graph" title="No skills yet" message="Add the skills this course teaches, then link prerequisites." action={<Button variant="contained" onClick={onAdd}>Add skill</Button>} />
            : <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1fr) 360px' }, alignItems: 'start' }}>
                <SkillGraphCanvas skills={skills} selectedId={selectedId} onSelect={onSelect} label={`Skill graph for ${courseTitle}`} />
                {adding || selected ? <Tile><SkillForm key={adding ? 'new' : selected!.id} skill={adding ? null : selected} skills={skills} onSave={onSaveSkill} serverError={serverError} onCancel={() => onSelect(null)} /></Tile>
                    : <EmptyView compact icon="graph" headingLevel="h3" title="Select a skill" message="Select a skill on the graph to edit it and its prerequisites, or add a new skill." />}
            </Box>)}
        {tab === 'mapping' && <LessonSkillMappingTable lessons={lessons} skills={skills} onChange={onMappingChange} />}
        {tab === 'adaptive' && <AdaptiveSettingsPanel checks={readiness} enabled={adaptiveEnabled} onToggle={onAdaptiveToggle} />}
    </Box>;
}
