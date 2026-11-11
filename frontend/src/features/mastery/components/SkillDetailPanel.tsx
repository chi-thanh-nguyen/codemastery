import { Box, ButtonBase, Typography } from '@mui/material';
import { MasteryStateChip } from './MasteryStateChip';
import { masteryMeta, StatusPill } from '../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import type { SkillView } from '../types/masteryTypes';
import { color, radius } from '../../../app/theme/theme';

export interface SkillDetailPanelProps {
    skill: SkillView | null;
    skillsById: Map<string, SkillView>;
    onOpenLesson?: (lessonId: string) => void;
    onSelectSkill: (skillId: string) => void;
    headingLevel?: 'h2' | 'h3';
}

const progressTone = { 'Not started': 'dashed', Completed: 'mint', Mastered: 'mint' } as const;

/** Selected skill: state and meaning, latest score, prerequisites, related lessons (US-07). */
export function SkillDetailPanel({ skill, skillsById, onOpenLesson, onSelectSkill, headingLevel = 'h2' }: SkillDetailPanelProps) {
    if (!skill) return <Box sx={{ border: `1.5px dashed ${color.ink400}`, borderRadius: `${radius.lg}px`, p: 3 }}>
        <Typography component={headingLevel} variant="h4">Selected skill</Typography>
        <Typography sx={{ mt: .5, color: 'text.secondary' }}>Select a skill to see related lessons and your latest score.</Typography>
    </Box>;
    const prerequisites = skill.prerequisiteIds.map((id) => skillsById.get(id)).filter((value): value is SkillView => !!value);
    const dependents = [...skillsById.values()].filter((candidate) => candidate.prerequisiteIds.includes(skill.id));
    return <Box component="section" aria-labelledby={`skill-${skill.id}-title`} sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: 3 }}>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>Selected skill</Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mt: .5 }}>
            <Typography component={headingLevel} variant="h3" id={`skill-${skill.id}-title`} sx={{ overflowWrap: 'anywhere' }}>{skill.name}</Typography>
            <MasteryStateChip state={skill.state} />
        </Box>
        <Typography variant="body2" sx={{ mt: 1, color: 'text.secondary' }}>{masteryMeta[skill.state].meaning}</Typography>
        {skill.description && <Typography sx={{ mt: 1.5 }}>{skill.description}</Typography>}
        <Box component="dl" sx={{ m: 0, mt: 2.5, display: 'grid', rowGap: 2.5, '& dt': { fontSize: '0.8125rem', fontWeight: 600, color: 'text.secondary', mb: .75 }, '& dd': { m: 0 } }}>
            <Box><dt>Latest score</dt><dd className="cm-tabular">{skill.latestScore === undefined || skill.latestScore === null ? 'No quiz result yet' : `${skill.latestScore}%`}</dd></Box>
            <Box><dt>Prerequisites</dt><dd>{prerequisites.length === 0 ? 'None — this skill starts a path.' : <SkillLinks skills={prerequisites} onSelect={onSelectSkill} />}</dd></Box>
            {dependents.length > 0 && <Box><dt>Builds toward</dt><dd><SkillLinks skills={dependents} onSelect={onSelectSkill} /></dd></Box>}
            <Box><dt>Related lessons</dt><dd>
                {skill.relatedLessons.length === 0 ? 'No published lessons are mapped to this skill yet.'
                    : <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                        {skill.relatedLessons.map((lesson) => <Box component="li" key={lesson.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', justifyContent: 'space-between' }}>
                            {onOpenLesson ? <ButtonBase onClick={() => onOpenLesson(lesson.id)} sx={{ fontWeight: 600, color: color.brand600, textAlign: 'left', minHeight: 36, borderRadius: `${radius.xs}px`, '&:hover': { textDecoration: 'underline' } }}>{lesson.title}</ButtonBase>
                                : <Typography sx={{ fontWeight: 600 }}>{lesson.title}</Typography>}
                            <StatusPill size="tag" tone={progressTone[lesson.progress]} icon={lesson.progress === 'Not started' ? 'dashed-circle' : lesson.progress === 'Mastered' ? 'skip' : 'check-circle'}>{lesson.progress}</StatusPill>
                        </Box>)}
                    </Box>}
            </dd></Box>
        </Box>
    </Box>;
}

function SkillLinks({ skills, onSelect }: { skills: SkillView[]; onSelect: (id: string) => void }) {
    return <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
        {skills.map((item) => <Box component="li" key={item.id}>
            <ButtonBase onClick={() => onSelect(item.id)} aria-label={`${item.name}, ${masteryMeta[item.state].label}. Select skill`}
                sx={{ gap: .75, px: 1.25, height: 32, borderRadius: `${radius.pill}px`, bgcolor: color.fill150, fontWeight: 600, fontSize: '0.9375rem', '&:hover': { bgcolor: color.line200 } }}>
                <PresentationIcon name={masteryMeta[item.state].icon} sx={{ fontSize: 16 }} />{item.name}
            </ButtonBase>
        </Box>)}
    </Box>;
}
