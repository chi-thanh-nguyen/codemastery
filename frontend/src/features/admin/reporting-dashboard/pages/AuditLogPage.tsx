import { useState } from 'react';
import { Box, MenuItem, TextField } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { TableToolbar } from '../../../../shared/components/DataTable';
import { AuditLogTable } from '../components/AuditLogTable';
import type { AuditEntry } from '../types/reportingTypes';

const PAGE = 8;
/** S24 Audit log: filter by actor, action and date range; read-only (US-17). Action codes are shown as stored. */
export function AuditLogPage({ entries }: { entries: AuditEntry[] }) {
    const [actor, setActor] = useState('all'), [action, setAction] = useState('all'), [from, setFrom] = useState(''), [to, setTo] = useState(''), [page, setPage] = useState(0);
    const actors = [...new Set(entries.map((entry) => entry.actor))], actions = [...new Set(entries.map((entry) => entry.action))];
    const filtered = entries.filter((entry) => (actor === 'all' || entry.actor === actor) && (action === 'all' || entry.action === action)
        && (!from || entry.atSort.slice(0, 10) >= from) && (!to || entry.atSort.slice(0, 10) <= to)).sort((a, b) => b.atSort.localeCompare(a.atSort));
    const reset = (fn: () => void) => { fn(); setPage(0); };
    return <Page>
        <PageHeader title="Audit log" subtitle="Who did what, to which record, and when." />
        <TableToolbar count={`${filtered.length} ${filtered.length === 1 ? 'entry' : 'entries'}`}>
            <TextField select label="Actor" value={actor} onChange={(event) => reset(() => setActor(event.target.value))} sx={{ minWidth: 180 }}><MenuItem value="all">All actors</MenuItem>{actors.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField>
            <TextField select label="Action" value={action} onChange={(event) => reset(() => setAction(event.target.value))} sx={{ minWidth: 200 }}><MenuItem value="all">All actions</MenuItem>{actions.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField>
            <TextField label="From" type="date" value={from} onChange={(event) => reset(() => setFrom(event.target.value))} sx={{ width: 180 }} />
            <TextField label="To" type="date" value={to} onChange={(event) => reset(() => setTo(event.target.value))} sx={{ width: 180 }} />
        </TableToolbar>
        <Box><AuditLogTable rows={filtered.slice(page * PAGE, page * PAGE + PAGE)} page={page} pageSize={PAGE} total={filtered.length} onPage={setPage} /></Box>
    </Page>;
}
