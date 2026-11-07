import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { DataTable } from '../../../shared/components/DataTable';
import { EmptyView } from '../../../shared/components/StatusViews';
import { StatusPill } from '../../../shared/components/StatusPill';
import { CircleAction } from '../../../shared/components/Tile';
import { quizKindLabel } from '../types/assessmentTypes';
import type { AssessmentItemView } from '../types/assessmentTypes';
import { formatClock } from '../components/QuizTimer';

export type AssessmentFilter = 'all' | 'quiz' | 'assignment';
export interface CourseAssessmentsPageProps {
    courseTitle: string;
    items: AssessmentItemView[];
    filter: AssessmentFilter;
    onFilterChange: (filter: AssessmentFilter) => void;
    onOpen: (item: AssessmentItemView) => void;
    sectionNav?: ReactNode;
    back?: Crumb;
}

const deadlineTone = { open: 'neutral', soon: 'butter', closed: 'rose' } as const;

/** S10 Course assessments: published quizzes and assignments for enrolled learners (US-12). */
export function CourseAssessmentsPage({ courseTitle, items, filter, onFilterChange, onOpen, sectionNav, back }: CourseAssessmentsPageProps) {
    const rows = items.filter((item) => filter === 'all' || item.type === filter);
    return <Page>
        <PageHeader title="Assessments" subtitle={`${courseTitle} · Published quizzes and assignments.`} back={back} />
        {sectionNav && <Box sx={{ mb: 4 }}>{sectionNav}</Box>}
        <Box sx={{ mb: 2 }}><SegmentedControl label="Assessment type" value={filter} onChange={onFilterChange}
            options={[{ value: 'all', label: 'All', count: items.length }, { value: 'quiz', label: 'Quizzes', count: items.filter((i) => i.type === 'quiz').length }, { value: 'assignment', label: 'Assignments', count: items.filter((i) => i.type === 'assignment').length }]} /></Box>
        <DataTable label="Assessments" rows={rows} getRowId={(item) => item.id}
            empty={<EmptyView icon="quiz" title="No assessments here yet" message="Published quizzes and assignments for this course will appear here." />}
            columns={[
                { key: 'name', header: 'Name', primary: true, render: (item) => <Box component="a" href="#" onClick={(event) => { event.preventDefault(); onOpen(item); }} sx={{ color: 'text.primary', fontWeight: 600, '&:hover': { textDecoration: 'underline' } }}>{item.title}</Box> },
                { key: 'type', header: 'Type', render: (item) => item.type === 'quiz' ? quizKindLabel[item.kind] : 'Assignment' },
                { key: 'limit', header: 'Time limit or deadline', render: (item) => item.type === 'quiz' ? <span className="cm-tabular">{formatClock(item.timeLimitSeconds)}</span>
                    : <StatusPill size="tag" tone={deadlineTone[item.deadlineState]} icon="calendar">{item.deadlineState === 'closed' ? 'Closed' : item.deadlineLabel}</StatusPill> },
                { key: 'status', header: 'Attempts or submission', render: (item) => item.type === 'quiz' ? item.attemptsLabel : item.submissionLabel },
                { key: 'score', header: 'Last score or grade', align: 'right', render: (item) => { const value = item.type === 'quiz' ? item.lastScore : item.grade;
                    return <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{value === undefined || value === null ? '—' : item.type === 'quiz' ? `${value}%` : `${value} / 100`}</Typography>; } },
            ]}
            actions={(item) => <CircleAction label={item.type === 'quiz' ? `Open ${item.title}` : `Open assignment ${item.title}`} icon="arrow-right" variant="ink" onClick={() => onOpen(item)} />} />
    </Page>;
}
