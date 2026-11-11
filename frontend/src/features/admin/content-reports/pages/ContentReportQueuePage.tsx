import { useState } from 'react';
import type { ReactNode } from 'react';
import { Box } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { ReportTable } from '../components/ReportTable';
import { ReportDetailDialog } from '../components/ReportDetailDialog';
import type { ReportRow } from '../types/contentReportTypes';

/** S24 Content reports: queue with status; hide resolves, dismiss closes (US-16). */
export function ContentReportQueuePage({ reports, onHide, onDismiss, notice }: { reports: ReportRow[]; onHide: (id: string) => void; onDismiss: (id: string) => void; notice?: ReactNode }) {
    const [status, setStatus] = useState<ReportRow['status']>('open'), [selected, setSelected] = useState<string | null>(null);
    const rows = reports.filter((report) => report.status === status);
    return <Page>
        <PageHeader title="Content reports" subtitle="Review reported posts and lessons. Hiding content resolves the report; dismissing closes it." />
        {notice}
        <Box sx={{ mb: 2 }}><SegmentedControl label="Report status" value={status} onChange={setStatus}
            options={(['open', 'resolved', 'dismissed'] as const).map((value) => ({ value, label: value[0].toUpperCase() + value.slice(1), count: reports.filter((report) => report.status === value).length }))} /></Box>
        <ReportTable rows={rows} onOpen={setSelected} selectedId={selected ?? undefined} />
        <ReportDetailDialog report={reports.find((report) => report.id === selected) ?? null} onClose={() => setSelected(null)} onHide={onHide} onDismiss={onDismiss} />
    </Page>;
}
