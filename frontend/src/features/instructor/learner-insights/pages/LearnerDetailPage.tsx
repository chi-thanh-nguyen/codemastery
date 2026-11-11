import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { DataTable } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { ProgressLine, StatGroup, StatTile, Tile } from '../../../../shared/components/Tile';
import { RecommendationLogTable } from '../components/RecommendationLogTable';
import type { LearnerRow, RecommendationLogRow, ResultRow } from '../types/learnerInsightsTypes';

export type LearnerDetailView = 'progress' | 'results' | 'mastery' | 'recommendations';
export interface LearnerDetailPageProps { learner: LearnerRow; view: LearnerDetailView; onViewChange: (view: LearnerDetailView) => void; results: ResultRow[]; log: RecommendationLogRow[]; masteryMap: ReactNode; back?: Crumb; progressDetail?: ReactNode }

/** S22 Learner detail: progress, results, read-only Mastery Map and recommendation log (US-14). */
export function LearnerDetailPage({ learner, view, onViewChange, results, log, masteryMap, back, progressDetail }: LearnerDetailPageProps) {
    return <Page>
        <PageHeader title={learner.name} subtitle={learner.courseTitle} back={back} />
        <Box sx={{ mb: 4 }}><SegmentedControl label="Learner detail sections" value={view} onChange={onViewChange}
            options={[{ value: 'progress', label: 'Progress' }, { value: 'results', label: 'Results' }, { value: 'mastery', label: 'Mastery Map' }, { value: 'recommendations', label: 'Recommendation log' }]} /></Box>
        {view === 'progress' && <Box sx={{ display: 'grid', gap: 3 }}>
            <StatGroup label="Learner progress"><StatTile value={learner.progress.total} label="Lessons" tone="sky" /><StatTile value={learner.progress.completed} label="Completed" tone="mint" /><StatTile value={learner.progress.total - learner.progress.completed} label="Remaining" tone="dashed" /></StatGroup>
            <Tile><Typography variant="h4" component="h2" sx={{ mb: 1.5 }}>Course progress</Typography><ProgressLine {...learner.progress} label={`${learner.name} course progress`} /></Tile>
            {progressDetail}
        </Box>}
        {view === 'results' && <DataTable label="Quiz results" rows={results} getRowId={(row) => row.id}
            empty={<EmptyView icon="quiz" title="No quiz results yet" message="Submitted quizzes appear here." />}
            columns={[{ key: 'title', header: 'Quiz', primary: true, render: (row) => <Typography sx={{ fontWeight: 600 }}>{row.title}</Typography> }, { key: 'kind', header: 'Kind', render: (row) => row.kindLabel },
                { key: 'attempt', header: 'Attempt', align: 'right', render: (row) => row.attempt }, { key: 'submitted', header: 'Submitted', render: (row) => row.submittedAtLabel },
                { key: 'score', header: 'Score', align: 'right', render: (row) => <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{row.score}%</Typography> }]} />}
        {view === 'mastery' && masteryMap}
        {view === 'recommendations' && <RecommendationLogTable rows={log} />}
    </Page>;
}
