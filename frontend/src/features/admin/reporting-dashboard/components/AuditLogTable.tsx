import { Box, Typography } from '@mui/material';
import { DataTable, PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import type { AuditEntry } from '../types/reportingTypes';
import { codeFontStack } from '../../../../app/theme/theme';

export function AuditLogTable({ rows, page, pageSize, total, onPage }: { rows: AuditEntry[]; page: number; pageSize: number; total: number; onPage: (page: number) => void }) {
    return <DataTable label="Audit log" rows={rows} getRowId={(row) => row.id}
        empty={<EmptyView icon="audit" title="No entries match these filters" message="Try a different actor, action or date range." />}
        columns={[
            { key: 'time', header: 'Time', primary: true, width: 170, render: (row) => row.atLabel },
            { key: 'actor', header: 'Actor', render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.actor}</Typography> },
            { key: 'action', header: 'Action', render: (row) => <Box component="code" sx={{ fontFamily: codeFontStack, fontSize: '0.875rem' }}>{row.action}</Box> },
            { key: 'target', header: 'Target', render: (row) => row.target },
        ]}
        footer={<PaginationBar page={page} totalPages={Math.ceil(total / pageSize)} totalItems={total} pageSize={pageSize} itemsOnPage={rows.length} onChange={onPage} noun="entries" />} />;
}
