import { useState } from 'react';
import { Box, MenuItem, TextField } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader, SearchPill } from '../../../../shared/components/PageHeader';
import { TableToolbar } from '../../../../shared/components/DataTable';
import { LearnerTable } from '../components/LearnerTable';
import type { LearnerRow } from '../types/learnerInsightsTypes';

/** S22 Learner insights: enrolled learners in owned courses with progress, scores, states and recommendations. */
export function LearnerInsightsPage({ rows, courses, onOpen }: { rows: LearnerRow[]; courses: string[]; onOpen: (id: string) => void }) {
    const [course, setCourse] = useState(courses[0] ?? ''), [query, setQuery] = useState('');
    const [sort, setSort] = useState<{ key: string; direction: 'asc' | 'desc' }>({ key: 'name', direction: 'asc' });
    const value = (row: LearnerRow) => sort.key === 'progress' ? row.progress.percent : sort.key === 'score' ? row.latestScore ?? -1 : row.name.toLowerCase();
    const visible = rows.filter((row) => row.courseTitle === course && row.name.toLowerCase().includes(query.trim().toLowerCase()))
        .sort((a, b) => { const x = value(a), y = value(b); return (x < y ? -1 : x > y ? 1 : 0) * (sort.direction === 'asc' ? 1 : -1); });
    return <Page>
        <PageHeader title="Learners" subtitle="Progress, results, skill states and recommendation history for learners enrolled in your courses." />
        <TableToolbar count={`${visible.length} ${visible.length === 1 ? 'learner' : 'learners'}`}>
            <TextField select label="Course" value={course} onChange={(event) => setCourse(event.target.value)} sx={{ minWidth: 240 }}>{courses.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField>
            <Box sx={{ flex: '1 1 220px', maxWidth: 340, alignSelf: 'flex-end' }}><SearchPill label="Search learners" value={query} onChange={setQuery} onClear={() => setQuery('')} /></Box>
        </TableToolbar>
        <LearnerTable rows={visible} onOpen={onOpen} sort={{ ...sort, onChange: (key) => setSort((current) => ({ key, direction: current.key === key && current.direction === 'asc' ? 'desc' : 'asc' })) }} />
    </Page>;
}
