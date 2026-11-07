import { Box } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { SubmissionTable } from '../components/SubmissionTable';
import type { SubmissionRow } from '../types/gradingTypes';

export type GradingFilter = 'ungraded' | 'graded' | 'all';
/** S21 Grading list: submissions in owned courses only (US-13). */
export function SubmissionListPage({ rows, filter, onFilterChange, onOpen }: { rows: SubmissionRow[]; filter: GradingFilter; onFilterChange: (filter: GradingFilter) => void; onOpen: (id: string) => void }) {
    const visible = rows.filter((row) => filter === 'all' || (filter === 'graded') === !!row.graded);
    return <Page>
        <PageHeader title="Grading" subtitle="File submissions from learners in your courses." />
        <Box sx={{ mb: 2 }}><SegmentedControl label="Submission status" value={filter} onChange={onFilterChange}
            options={[{ value: 'ungraded', label: 'Ungraded', count: rows.filter((row) => !row.graded).length }, { value: 'graded', label: 'Graded', count: rows.filter((row) => row.graded).length }, { value: 'all', label: 'All', count: rows.length }]} /></Box>
        <SubmissionTable rows={visible} onOpen={onOpen} emptyTitle={filter === 'ungraded' ? 'Nothing to grade' : 'No submissions yet'} />
    </Page>;
}
