import { useState } from 'react';
import type { ReactNode } from 'react';
import { IconButton, ListItemIcon, Menu, MenuItem, Typography } from '@mui/material';
import { DataTable, PaginationBar } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import type { UserRowView } from '../types/userManagementTypes';
import { control } from '../../../../app/theme/theme';

export interface UserTableProps { rows: UserRowView[]; onChangeRole: (user: UserRowView) => void; onToggleLock: (user: UserRowView) => void; page: number; pageSize: number; total: number; onPage: (page: number) => void; showEmail: boolean; emptyAction?: ReactNode }

export function UserTable({ rows, onChangeRole, onToggleLock, page, pageSize, total, onPage, showEmail, emptyAction }: UserTableProps) {
    return <DataTable label="Users" rows={rows} getRowId={(row) => row.id}
        empty={<EmptyView icon="users" title="No users match" message="Try another name, role or status." action={emptyAction} />}
        columns={[
            { key: 'name', header: 'Display name', primary: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.displayName}</Typography> },
            ...(showEmail ? [{ key: 'email', header: 'Email', render: (row: UserRowView) => row.email ?? '—' }] : []),
            { key: 'role', header: 'Role', render: (row) => row.role },
            { key: 'status', header: 'Status', render: (row) => row.status === 'active' ? <StatusPill size="tag" tone="mint" icon="check-circle">Active</StatusPill> : <StatusPill size="tag" tone="rose" icon="lock">Locked</StatusPill> },
        ]}
        actions={(row) => <RowMenu user={row} onChangeRole={onChangeRole} onToggleLock={onToggleLock} />}
        footer={<PaginationBar page={page} totalPages={Math.ceil(total / pageSize)} totalItems={total} pageSize={pageSize} itemsOnPage={rows.length} onChange={onPage} noun="users" />} />;
}

function RowMenu({ user, onChangeRole, onToggleLock }: { user: UserRowView; onChangeRole: (user: UserRowView) => void; onToggleLock: (user: UserRowView) => void }) {
    const [anchor, setAnchor] = useState<HTMLElement | null>(null);
    return <>
        <IconButton aria-label={`Actions for ${user.displayName}`} aria-haspopup="menu" onClick={(event) => setAnchor(event.currentTarget)} sx={control.secondary}><PresentationIcon name="more" /></IconButton>
        <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
            <MenuItem onClick={() => { setAnchor(null); onChangeRole(user); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name="user" sx={{ fontSize: 20 }} /></ListItemIcon>Change role</MenuItem>
            <MenuItem onClick={() => { setAnchor(null); onToggleLock(user); }}><ListItemIcon sx={{ minWidth: 0 }}><PresentationIcon name={user.status === 'active' ? 'lock' : 'unlock'} sx={{ fontSize: 20 }} /></ListItemIcon>{user.status === 'active' ? 'Lock account' : 'Unlock account'}</MenuItem>
        </Menu>
    </>;
}
