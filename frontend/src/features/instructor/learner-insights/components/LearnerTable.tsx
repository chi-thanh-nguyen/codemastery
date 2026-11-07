import { Box, Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import type { TableSort } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { ProgressLine, CircleAction } from '../../../../shared/components/Tile';
import { MasteryTag, RecommendationTag, masteryOrder } from '../../../../shared/components/StatusPill';
import type { LearnerRow } from '../types/learnerInsightsTypes';

export function LearnerTable({ rows, onOpen, sort }: { rows: LearnerRow[]; onOpen: (id: string) => void; sort?: TableSort }) {
    return <DataTable label="Learners" rows={rows} getRowId={(row) => row.id} sort={sort}
        empty={<EmptyView icon="users" title="No enrolled learners yet" message="Learners appear here after they enroll in this course." />}
        columns={[
            { key: 'name', header: 'Learner', primary: true, sortable: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.name}</Typography> },
            { key: 'progress', header: 'Progress', sortable: true, width: 260, render: (row) => <ProgressLine completed={row.progress.completed} total={row.progress.total} percent={row.progress.percent} label={`${row.name} progress`} /> },
            { key: 'score', header: 'Latest score', align: 'right', sortable: true, render: (row) => <span className="cm-tabular">{row.latestScore === null ? '—' : `${row.latestScore}%`}</span> },
            { key: 'mastery', header: 'Skills by state', render: (row) => <Box sx={{ display: 'flex', gap: .75, flexWrap: 'wrap' }}>
                {masteryOrder.filter((state) => row.mastery[state] > 0).map((state) => <Box key={state} sx={{ display: 'inline-flex', alignItems: 'center', gap: .5 }}><MasteryTag state={state} /><span className="cm-tabular">{row.mastery[state]}</span></Box>)}
            </Box> },
            { key: 'recommendation', header: 'Last recommendation', render: (row) => row.lastRecommendation ? <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}><RecommendationTag type={row.lastRecommendation.type} /><Typography variant="caption" sx={{ color: 'text.secondary' }}>{row.lastRecommendation.atLabel}</Typography></Box> : '—' },
        ]}
        actions={(row) => <CircleAction label={`Open ${row.name}`} icon="arrow-right" variant="ink" onClick={() => onOpen(row.id)} />} />;
}
