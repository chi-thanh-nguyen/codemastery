import { Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import type { AdminCourseRow } from '../types/adminCourseTypes';

export function AdminCourseTable({ rows, onTogglePublication }: { rows: AdminCourseRow[]; onTogglePublication: (row: AdminCourseRow) => void }) {
    return <DataTable label="Courses" rows={rows} getRowId={(row) => row.id}
        empty={<EmptyView icon="catalogue" title="No courses match" message="Try another search or status." />}
        columns={[
            { key: 'title', header: 'Course', primary: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.title}</Typography> },
            { key: 'instructor', header: 'Instructor', render: (row) => row.instructorName ?? 'Instructor name unavailable' },
            { key: 'publication', header: 'Publication', render: (row) => row.publication === 'published' ? <StatusPill size="tag" tone="mint" icon="eye">Published</StatusPill> : <StatusPill size="tag" tone="neutral" icon="eye-off">Hidden</StatusPill> },
            { key: 'enrolments', header: 'Enrolments', align: 'right', render: (row) => <span className="cm-tabular">{row.enrolments}</span> },
        ]}
        actions={(row) => <CircleAction label={row.publication === 'published' ? `Hide ${row.title}` : `Publish ${row.title}`} icon={row.publication === 'published' ? 'eye-off' : 'eye'} onClick={() => onTogglePublication(row)} />} />;
}
