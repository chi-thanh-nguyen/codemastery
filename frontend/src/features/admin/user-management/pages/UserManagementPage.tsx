import { useState } from 'react';
import type { ReactNode } from 'react';
import { Box, Button, FormControlLabel, Radio, RadioGroup } from '@mui/material';
import { useDebounce } from '../../../../shared/hooks/useDebounce';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader, SearchPill } from '../../../../shared/components/PageHeader';
import { TableToolbar } from '../../../../shared/components/DataTable';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { UserTable } from '../components/UserTable';
import type { Role, UserRowView } from '../types/userManagementTypes';

const PAGE = 8;
/** Live filter delay so the table and count settle after typing pauses. Presentation only. */
export const USER_SEARCH_DELAY_MS = 300;
const roleText: Record<Role, string> = { Learner: 'can enroll in courses and learn.', Instructor: 'can create and manage the courses they own.', Administrator: 'can manage users, courses, reports and the audit log.' };

export interface UserManagementPageProps { users: UserRowView[]; onRoleChange: (userId: string, role: Role) => void; onLockChange: (userId: string, locked: boolean) => void; showEmail: boolean; notice?: ReactNode }

/** S24 Users: search, change role, lock/unlock. Each action is audited by the backend (US-15). */
export function UserManagementPage({ users, onRoleChange, onLockChange, showEmail, notice }: UserManagementPageProps) {
    const [query, setQuery] = useState(''), [role, setRole] = useState<'all' | Role>('all'), [page, setPage] = useState(0);
    const [roleTarget, setRoleTarget] = useState<UserRowView | null>(null), [nextRole, setNextRole] = useState<Role>('Learner'), [lockTarget, setLockTarget] = useState<UserRowView | null>(null);
    const settled = useDebounce(query, USER_SEARCH_DELAY_MS);
    const filtered = users.filter((user) => (role === 'all' || user.role === role) && `${user.displayName} ${user.email ?? ''}`.toLowerCase().includes(settled.trim().toLowerCase()));
    const clearFilters = () => { setQuery(''); setRole('all'); setPage(0); };
    return <Page>
        <PageHeader title="Users" subtitle="Find accounts, change roles, and lock or unlock access. Roles never inherit each other's rights." />
        {notice}
        <TableToolbar count={`${filtered.length} ${filtered.length === 1 ? 'user' : 'users'}`}>
            <Box sx={{ flex: '1 1 240px', maxWidth: 360 }}><SearchPill label="Search users" value={query} onChange={(value) => { setQuery(value); setPage(0); }} onClear={() => { setQuery(''); setPage(0); }} /></Box>
            <SegmentedControl label="Role" value={role} onChange={(value) => { setRole(value); setPage(0); }} options={[{ value: 'all', label: 'All' }, { value: 'Learner', label: 'Learners' }, { value: 'Instructor', label: 'Instructors' }, { value: 'Administrator', label: 'Administrators' }]} />
        </TableToolbar>
        <UserTable rows={filtered.slice(page * PAGE, page * PAGE + PAGE)} page={page} pageSize={PAGE} total={filtered.length} onPage={setPage} showEmail={showEmail}
            emptyAction={settled || role !== 'all' ? <Button variant="outlined" onClick={clearFilters}>Clear filters</Button> : undefined}
            onChangeRole={(user) => { setRoleTarget(user); setNextRole(user.role); }} onToggleLock={setLockTarget} />
        <ConfirmDialog open={!!roleTarget} title={`Change role for ${roleTarget?.displayName ?? ''}?`} message={`A ${nextRole} ${roleText[nextRole]} The change is recorded in the audit log.`}
            confirmLabel="Change role" onCancel={() => setRoleTarget(null)} onConfirm={() => { if (roleTarget) onRoleChange(roleTarget.id, nextRole); setRoleTarget(null); }}>
            <RadioGroup aria-label="New role" value={nextRole} onChange={(event) => setNextRole(event.target.value as Role)} sx={{ mt: 2 }}>
                {(['Learner', 'Instructor', 'Administrator'] as Role[]).map((item) => <FormControlLabel key={item} value={item} control={<Radio />} label={item} />)}
            </RadioGroup>
        </ConfirmDialog>
        <ConfirmDialog open={!!lockTarget} isDestructive={lockTarget?.status === 'active'}
            title={lockTarget?.status === 'active' ? `Lock ${lockTarget?.displayName}'s account?` : `Unlock ${lockTarget?.displayName ?? ''}'s account?`}
            message={lockTarget?.status === 'active' ? "They won't be able to sign in or use protected features while locked. The change is recorded in the audit log." : 'They can sign in again. The change is recorded in the audit log.'}
            confirmLabel={lockTarget?.status === 'active' ? 'Lock account' : 'Unlock account'} onCancel={() => setLockTarget(null)}
            onConfirm={() => { if (lockTarget) onLockChange(lockTarget.id, lockTarget.status === 'active'); setLockTarget(null); }} />
    </Page>;
}
