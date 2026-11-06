import { useState } from 'react';
import { Box, Typography, useMediaQuery, useTheme } from '@mui/material';
import { MasteryGraph } from './MasteryGraph';
import type { GraphSkill } from './MasteryGraph';
import { MasteryLegend, MasteryTag } from '../../../shared/components/StatusPill';
import { color, radius } from '../../../app/theme/theme';

/**
 * Fixed explanatory illustration for the public landing page (DESIGN.md › S25
 * "Example map"). It is static content, not learner data: no request, no storage,
 * and it is labelled as an illustration wherever it appears. Loaded lazily so the
 * graph library stays out of the main bundle.
 */
const illustration: GraphSkill[] = [
    { id: 'variables', name: 'Variables', state: 'mastered', prerequisiteIds: [] },
    { id: 'expressions', name: 'Expressions', state: 'mastered', prerequisiteIds: ['variables'] },
    { id: 'output', name: 'Output', state: 'mastered', prerequisiteIds: ['variables'] },
    { id: 'conditionals', name: 'Conditionals', state: 'learning', prerequisiteIds: ['expressions'] },
    { id: 'loops', name: 'Loops', state: 'weak', prerequisiteIds: ['expressions'] },
    { id: 'functions', name: 'Functions', state: 'unknown', prerequisiteIds: ['output'] },
];

export default function ExampleMasteryMap() {
    const [selected, setSelected] = useState<string | null>(null);
    const narrow = useMediaQuery(useTheme().breakpoints.down('sm'));
    return <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2, flexWrap: 'wrap', mb: 1.5, px: .5 }}>
            <Typography component="h2" variant="h4">Example map</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Illustration · not learner data</Typography>
        </Box>
        {narrow
            // The list equivalent (DESIGN.md › S14) reads better than a clipped graph on a phone.
            ? <Box component="ul" aria-label="Example skills (illustration, not learner data)" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px` }}>
                {illustration.map((skill) => <Box component="li" key={skill.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5, px: 2, py: 1.5, borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 } }}>
                    <Box sx={{ minWidth: 0 }}>
                        <Typography sx={{ fontWeight: 600 }}>{skill.name}</Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>{skill.prerequisiteIds.length ? `Builds on ${illustration.filter((other) => skill.prerequisiteIds.includes(other.id)).map((other) => other.name).join(', ')}` : 'No prerequisite'}</Typography>
                    </Box>
                    <MasteryTag state={skill.state} />
                </Box>)}
            </Box>
            : <MasteryGraph label="Example skill map (illustration, not learner data)" skills={illustration} selectedId={selected} onSelect={setSelected} recommendedSkillId="conditionals" zoom={0.6} />}
        <Box sx={{ mt: 1.5 }}><MasteryLegend dense /></Box>
    </Box>;
}
