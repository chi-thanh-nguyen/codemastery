import { useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { MasteryLegend, masteryMeta, masteryOrder } from '../../../../shared/components/StatusPill';
import type { SkillDistributionRow } from '../types/reportingTypes';
import { color, radius } from '../../../../app/theme/theme';

const hatch = `repeating-linear-gradient(135deg, ${color.dataUnknown} 0 4px, #9EA2AA 4px 8px)`;

/** 100% stacked bars of the four states per skill, with hatched Unknown, legend and table equivalent (US-17). */
export function MasteryDistributionChart({ rows, title = 'Mastery distribution by skill', definition }: { rows: SkillDistributionRow[]; title?: string; definition: string }) {
    const [table, setTable] = useState(false);
    return <Box component="section" aria-labelledby="mastery-distribution" sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: { xs: 2.5, sm: 3 } }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Box><Typography id="mastery-distribution" variant="h4" component="h2">{title}</Typography><Typography variant="body2" sx={{ color: 'text.secondary', mt: .5, maxWidth: '56ch' }}>{definition}</Typography></Box>
            <Button variant="text" size="small" aria-pressed={table} onClick={() => setTable((value) => !value)}>{table ? 'Show as chart' : 'Show as table'}</Button>
        </Box>
        <Box sx={{ mb: 2.5 }}><MasteryLegend dense /></Box>
        {table ? <DataTable label={title} rows={rows} getRowId={(row) => row.skillId} columns={[{ key: 'skill', header: 'Skill', primary: true, render: (row) => row.skillName },
            ...masteryOrder.map((state) => ({ key: state, header: masteryMeta[state].label, align: 'right' as const, render: (row: SkillDistributionRow) => <span className="cm-tabular">{row.counts[state]}</span> }))]} />
            : <Box component="ul" aria-label={title} sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1.75 }}>
                {rows.map((row) => { const total = masteryOrder.reduce((sum, state) => sum + row.counts[state], 0) || 1;
                    const summary = masteryOrder.map((state) => `${masteryMeta[state].label} ${row.counts[state]}`).join(', ');
                    return <Box component="li" key={row.skillId} aria-label={`${row.skillName}: ${summary}`} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(120px, 28%) minmax(0, 1fr)' }, gap: { xs: .5, sm: 2 }, alignItems: 'center' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{row.skillName}</Typography>
                        <Box aria-hidden sx={{ display: 'flex', height: 24, gap: '2px', borderRadius: `${radius.xs}px`, overflow: 'hidden' }}>
                            {masteryOrder.filter((state) => row.counts[state] > 0).map((state) => <Box key={state} title={`${masteryMeta[state].label}: ${row.counts[state]}`}
                                sx={{ width: `${(row.counts[state] / total) * 100}%`, background: state === 'unknown' ? hatch : masteryMeta[state].data }} />)}
                        </Box>
                    </Box>; })}
            </Box>}
    </Box>;
}
