import { useState } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { color, radius } from '../../../../app/theme/theme';

export interface BarDatum { id: string; label: string; value: number }
export interface BarChartProps { title: string; definition: string; data: BarDatum[]; max?: number; format: (value: number) => string; valueHeader: string; headingId: string }

/**
 * Horizontal bar chart in semantic HTML (no chart dependency): sorted bars, value
 * labels at bar ends, one series in Logo Blue, and a "Show as table" equivalent.
 * Used for enrolments, completion rate and average quiz score by course.
 */
export function EnrolmentChart({ title, definition, data, max, format, valueHeader, headingId }: BarChartProps) {
    const [table, setTable] = useState(false);
    const sorted = [...data].sort((a, b) => b.value - a.value), top = max ?? Math.max(1, ...data.map((item) => item.value));
    return <Box component="section" aria-labelledby={headingId} sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: { xs: 2.5, sm: 3 } }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', mb: 2.5 }}>
            <Box sx={{ minWidth: 0 }}><Typography id={headingId} variant="h4" component="h2">{title}</Typography><Typography variant="body2" sx={{ color: 'text.secondary', mt: .5, maxWidth: '56ch' }}>{definition}</Typography></Box>
            <Button variant="text" size="small" aria-pressed={table} onClick={() => setTable((value) => !value)}>{table ? 'Show as chart' : 'Show as table'}</Button>
        </Box>
        {data.length === 0 ? <Typography sx={{ color: 'text.secondary' }}>No data for this selection.</Typography>
            : table ? <DataTable label={title} rows={sorted} getRowId={(item) => item.id} columns={[{ key: 'label', header: 'Course', primary: true, render: (item) => item.label },
                { key: 'value', header: valueHeader, align: 'right', render: (item) => <span className="cm-tabular">{format(item.value)}</span> }]} />
                : <Box component="ul" aria-label={title} sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1.75 }}>
                    {sorted.map((item) => <Box component="li" key={item.id} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(120px, 34%) minmax(0, 1fr)' }, gap: { xs: .5, sm: 2 }, alignItems: 'center' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{item.label}</Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                            <Box aria-hidden sx={{ height: 20, width: item.value === 0 ? 0 : `${Math.max(2, (item.value / top) * 100)}%`, maxWidth: 'calc(100% - 72px)', bgcolor: color.brand600, borderRadius: `0 ${radius.xs}px ${radius.xs}px 0` }} />
                            <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600, fontSize: '0.9375rem', whiteSpace: 'nowrap' }}>{format(item.value)}</Typography>
                        </Box>
                    </Box>)}
                </Box>}
    </Box>;
}
