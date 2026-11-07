import { useState } from 'react';
import type { ReactNode } from 'react';
import { Box } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader, SearchPill } from '../../../../shared/components/PageHeader';
import { TableToolbar } from '../../../../shared/components/DataTable';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { AdminCourseTable } from '../components/AdminCourseTable';
import type { AdminCourseRow } from '../types/adminCourseTypes';

/** S24 Course administration: inspect courses and change publication (exact operations per CT12). */
export function AdminCoursesPage({ courses, onPublicationChange, notice }: { courses: AdminCourseRow[]; onPublicationChange: (id: string, publication: AdminCourseRow['publication']) => void; notice?: ReactNode }) {
    const [query, setQuery] = useState(''), [status, setStatus] = useState<'all' | AdminCourseRow['publication']>('all'), [target, setTarget] = useState<AdminCourseRow | null>(null);
    const rows = courses.filter((course) => (status === 'all' || course.publication === status) && course.title.toLowerCase().includes(query.trim().toLowerCase()));
    const hiding = target?.publication === 'published';
    return <Page>
        <PageHeader title="Courses" subtitle="All courses on the platform and their publication state." />
        {notice}
        <TableToolbar count={`${rows.length} ${rows.length === 1 ? 'course' : 'courses'}`}>
            <Box sx={{ flex: '1 1 240px', maxWidth: 360 }}><SearchPill label="Search courses" value={query} onChange={setQuery} onClear={() => setQuery('')} /></Box>
            <SegmentedControl label="Publication" value={status} onChange={setStatus} options={[{ value: 'all', label: 'All' }, { value: 'published', label: 'Published' }, { value: 'hidden', label: 'Hidden' }]} />
        </TableToolbar>
        <AdminCourseTable rows={rows} onTogglePublication={setTarget} />
        <ConfirmDialog open={!!target} isDestructive={hiding} title={hiding ? `Hide ${target?.title}?` : `Publish ${target?.title ?? ''}?`}
            message={hiding ? 'Learners will no longer see this course. Memberships and history are kept. The change is recorded in the audit log.' : 'Learners will see this course in the catalogue. The change is recorded in the audit log.'}
            confirmLabel={hiding ? 'Hide course' : 'Publish course'} onCancel={() => setTarget(null)} onConfirm={() => { if (target) onPublicationChange(target.id, hiding ? 'hidden' : 'published'); setTarget(null); }} />
    </Page>;
}
