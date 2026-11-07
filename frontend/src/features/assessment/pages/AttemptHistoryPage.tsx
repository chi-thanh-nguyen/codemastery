import { Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { DataTable } from '../../../shared/components/DataTable';
import { EmptyView } from '../../../shared/components/StatusViews';
import type { AttemptView } from '../types/assessmentTypes';

/** S12 Attempt history: attempt number, submitted time and score, newest first (US-05). */
export function AttemptHistoryPage({ quizTitle, attempts, onView, onStart, back }: { quizTitle: string; attempts: AttemptView[]; onView: (attemptId: string) => void; onStart?: () => void; back?: Crumb }) {
    return <Page width="reading">
        <PageHeader title="Attempt history" subtitle={quizTitle} back={back} actions={onStart && <Button variant="contained" onClick={onStart}>Start new attempt</Button>} />
        <DataTable label={`Attempts for ${quizTitle}`} rows={[...attempts].sort((a, b) => b.attemptNumber - a.attemptNumber)} getRowId={(attempt) => attempt.id}
            empty={<EmptyView icon="quiz" title="No attempts yet" message="Your submitted attempts and their scores will appear here." />}
            columns={[
                { key: 'attempt', header: 'Attempt', primary: true, render: (attempt) => `Attempt ${attempt.attemptNumber}` },
                { key: 'submitted', header: 'Submitted', render: (attempt) => attempt.submittedAtLabel },
                { key: 'correct', header: 'Correct', align: 'right', render: (attempt) => <span className="cm-tabular">{attempt.correct} of {attempt.total}</span> },
                { key: 'score', header: 'Score', align: 'right', render: (attempt) => <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{attempt.score}%</Typography> },
            ]}
            actions={(attempt) => <Button variant="text" size="small" onClick={() => onView(attempt.id)}>View result</Button>} />
    </Page>;
}
