import { Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import type { SubmissionRow } from '../types/gradingTypes';

export function SubmissionTable({ rows, onOpen, emptyTitle }: { rows: SubmissionRow[]; onOpen: (id: string) => void; emptyTitle: string }) {
    return <DataTable label="Submissions" rows={rows} getRowId={(row) => row.id}
        empty={<EmptyView icon="assignment" title={emptyTitle} message="Submissions from learners in your courses appear here." />}
        columns={[
            { key: 'learner', header: 'Learner', primary: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.learnerName}</Typography> },
            { key: 'assignment', header: 'Assignment', render: (row) => row.assignmentTitle },
            { key: 'course', header: 'Course', render: (row) => row.courseTitle },
            { key: 'submitted', header: 'Submitted', render: (row) => row.submittedAtLabel },
            { key: 'status', header: 'Status', render: (row) => row.graded ? <StatusPill size="tag" tone="mint" icon="check-circle">Graded · {row.graded.grade}</StatusPill> : <StatusPill size="tag" tone="butter" icon="clock">Ungraded</StatusPill> },
        ]}
        actions={(row) => <CircleAction label={`${row.graded ? 'View' : 'Grade'} ${row.learnerName}'s submission`} icon="arrow-right" variant="ink" onClick={() => onOpen(row.id)} />} />;
}
