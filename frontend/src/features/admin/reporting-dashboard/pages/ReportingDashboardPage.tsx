import { Box, MenuItem, TextField } from '@mui/material';
import type { ReactNode } from 'react';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { EnrolmentChart } from '../components/EnrolmentChart';
import { MasteryDistributionChart } from '../components/MasteryDistributionChart';
import type { CourseMetricRow, SkillDistributionRow } from '../types/reportingTypes';

export interface ReportingDashboardPageProps { courses: CourseMetricRow[]; selectedCourseId: string; onCourseChange: (courseId: string) => void; distribution: SkillDistributionRow[]; notice?: ReactNode }

/** S23 Platform overview: enrolments, completion, average quiz score and mastery distribution (US-17). No KPI cards or trends. */
export function ReportingDashboardPage({ courses, selectedCourseId, onCourseChange, distribution, notice }: ReportingDashboardPageProps) {
    const percent = (value: number) => `${Math.round(value)}%`;
    return <Page>
        <PageHeader title="Platform overview" subtitle="Learning outcomes across published courses. Each metric states its definition." />
        {notice}
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', xl: '1fr 1fr' } }}>
            <EnrolmentChart headingId="metric-enrolments" title="Enrolments by course" definition="Learners with an active or completed membership in each published course." valueHeader="Enrolments"
                data={courses.map((course) => ({ id: course.courseId, label: course.courseTitle, value: course.enrolments }))} format={(value) => String(value)} />
            <EnrolmentChart headingId="metric-completion" title="Completion rate by course" definition="Share of enrolled learners whose course status is Completed." valueHeader="Completion rate" max={100}
                data={courses.map((course) => ({ id: course.courseId, label: course.courseTitle, value: course.completionRate }))} format={percent} />
            <EnrolmentChart headingId="metric-score" title="Average quiz score by course" definition="Mean score of submitted quiz attempts in each course." valueHeader="Average score" max={100}
                data={courses.filter((course) => course.averageQuizScore !== null).map((course) => ({ id: course.courseId, label: course.courseTitle, value: course.averageQuizScore! }))} format={percent} />
            <Box sx={{ display: 'grid', gap: 2, alignContent: 'start' }}>
                <TextField select label="Course for mastery distribution" value={selectedCourseId} onChange={(event) => onCourseChange(event.target.value)} sx={{ maxWidth: 360 }}>
                    {courses.map((course) => <MenuItem key={course.courseId} value={course.courseId}>{course.courseTitle}</MenuItem>)}
                </TextField>
                <MasteryDistributionChart rows={distribution} definition="Number of enrolled learners in each skill state for the selected course." />
            </Box>
        </Box>
    </Page>;
}
