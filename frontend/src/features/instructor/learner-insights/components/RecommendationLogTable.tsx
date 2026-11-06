import { Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { RecommendationTag } from '../../../../shared/components/StatusPill';
import type { RecommendationLogRow } from '../types/learnerInsightsTypes';

/** Recommendation history: time, type, target lesson and reason, newest first (US-14). */
export function RecommendationLogTable({ rows }: { rows: RecommendationLogRow[] }) {
    return <DataTable label="Recommendation log" rows={rows} getRowId={(row) => row.id}
        empty={<EmptyView icon="route" title="No recommendations yet" message="Recommendations appear after the learner's first skill-tagged quiz." />}
        columns={[
            { key: 'time', header: 'Time', primary: true, width: 160, render: (row) => row.atLabel },
            { key: 'type', header: 'Type', width: 140, render: (row) => <RecommendationTag type={row.type} /> },
            { key: 'lesson', header: 'Target lesson', render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.targetLesson}</Typography> },
            { key: 'reason', header: 'Reason', width: '40%', render: (row) => row.reason },
        ]} />;
}
