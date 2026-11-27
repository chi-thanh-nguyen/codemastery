import { Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import type { SubmissionRow } from '../types/gradingTypes';

export function SubmissionTable({ rows, onOpen, emptyTitle, loading }: { rows: SubmissionRow[]; onOpen: (id: string) => void; emptyTitle: string; loading?: boolean }) {
    return <DataTable label="Submissions" rows={rows} loading={loading} getRowId={(row) => row.id}
        empty={<EmptyView icon="assignment" title={emptyTitle} message="Submissions from learners in your courses appear here." />}
        columns={[
            { key: 'learner', header: 'Learner', primary: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.learnerName}</Typography> },
            { key: 'file', header: 'File', render: (row) => <Typography sx={{ overflowWrap: 'anywhere' }}>{row.fileName}</Typography> },
            { key: 'submitted', header: 'Submitted', render: (row) => row.submittedAtLabel },
            { key: 'status', header: 'Status', render: (row) => row.graded ? <StatusPill size="tag" tone="mint" icon="check-circle">Graded</StatusPill> : <StatusPill size="tag" tone="butter" icon="clock">To grade</StatusPill> },
            { key: 'grade', header: 'Grade', render: row => row.graded ? `${row.graded.grade} / 100` : '—' },
        ]}
        actions={(row) => <CircleAction label={`${row.graded ? 'Update grade for' : 'Grade'} ${row.learnerName}'s submission`} icon="arrow-right" variant="ink" onClick={() => onOpen(row.id)} />} />;
}
