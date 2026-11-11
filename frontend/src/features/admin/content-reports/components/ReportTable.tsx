import { Typography } from '@mui/material';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import type { ReportRow } from '../types/contentReportTypes';

export const reportStatus = { open: { label: 'Open', tone: 'butter', icon: 'flag' }, resolved: { label: 'Resolved', tone: 'mint', icon: 'eye-off' }, dismissed: { label: 'Dismissed', tone: 'neutral', icon: 'close' } } as const;

export function ReportTable({ rows, onOpen, selectedId }: { rows: ReportRow[]; onOpen: (id: string) => void; selectedId?: string }) {
    return <DataTable label="Content reports" rows={rows} getRowId={(row) => row.id} selectedId={selectedId}
        empty={<EmptyView icon="flag" title="No reports here" message="Reports from learners and instructors appear in this queue." />}
        columns={[
            { key: 'item', header: 'Reported item', primary: true, render: (row) => <><Typography sx={{ fontWeight: 600 }}>{row.targetLabel}</Typography><Typography variant="caption" sx={{ color: 'text.secondary' }}>{row.courseTitle}</Typography></> },
            { key: 'type', header: 'Type', render: (row) => row.targetType },
            { key: 'reporter', header: 'Reporter', render: (row) => row.reporter },
            { key: 'reported', header: 'Reported', render: (row) => row.reportedAtLabel },
            { key: 'status', header: 'Status', render: (row) => <StatusPill size="tag" tone={reportStatus[row.status].tone} icon={reportStatus[row.status].icon}>{reportStatus[row.status].label}</StatusPill> },
        ]}
        actions={(row) => <CircleAction label={`Review report on ${row.targetLabel}`} icon="arrow-right" variant="ink" onClick={() => onOpen(row.id)} />} />;
}
