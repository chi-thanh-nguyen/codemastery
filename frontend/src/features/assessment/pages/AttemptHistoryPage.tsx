import { Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { DataTable } from '../../../shared/components/DataTable';
import { EmptyView } from '../../../shared/components/StatusViews';
import type { AttemptView } from '../types/assessmentTypes';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { StatusPill } from '../../../shared/components/StatusPill';
import { useAuth } from '../../auth/hooks/useAuth';
import { listAttemptHistory } from '../api/quizApi';
import type { B3AttemptSummary } from '../types/assessmentTypes';
import { ApiError } from '../../../shared/types/apiTypes';

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

/** Live S12 Attempt History: re-reads on mount, shows Active/Being submitted/Submitted rows, newest first. */
export function LiveAttemptHistoryPage({ courseId, quizId, quizTitle = 'Quiz' }: { courseId: string; quizId: string; quizTitle?: string }) {
    const auth = useAuth();
    const navigate = useNavigate();
    const [attempts, setAttempts] = useState<B3AttemptSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [revision, retry] = useState(0);

    useEffect(() => {
        let alive = true;
        const controller = new AbortController();
        const gen = auth.generation;
        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        listAttemptHistory(courseId, quizId, 0, 50, {
            signal: controller.signal,
            isCurrent: () => alive && auth.generation === gen,
        })
            .then((res) => {
                if (alive && auth.generation === gen) setAttempts(res.content);
            })
            .catch((err: unknown) => {
                if (!alive || auth.generation !== gen || (err instanceof ApiError && err.kind === 'cancelled')) return;
                if (err instanceof ApiError) {
                    setErrorStatus(err.status ?? null);
                    setErrorMessage(err.message);
                } else {
                    setErrorMessage("Couldn't load attempt history.");
                }
            })
            .finally(() => {
                if (alive && auth.generation === gen) setLoading(false);
            });

        return () => {
            alive = false;
            controller.abort();
        };
    }, [courseId, quizId, auth.generation, revision]);

    if (loading) return <Page><LoadingView message="Loading attempt history…" /></Page>;
    if (errorStatus === 404) {
        return <Page><SystemStateView icon="search" title="Quiz not available" message="This quiz may have been hidden." actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/assessments`} variant="contained">Back to assessments</Button>} /></Page>;
    }
    if (errorMessage) {
        return <Page><ErrorView title="Couldn't load attempt history" message={errorMessage} onRetry={() => retry((r) => r + 1)} /></Page>;
    }

    return (
        <Page width="reading">
            <PageHeader
                title="Attempt history"
                subtitle={quizTitle}
                back={{ label: quizTitle, href: `/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}` }}
            />
            <DataTable
                label={`Attempts for ${quizTitle}`}
                rows={attempts}
                getRowId={(a) => a.attemptId}
                empty={<EmptyView icon="quiz" title="No attempts yet" message="Your submitted attempts and their scores will appear here." />}
                columns={[
                    { key: 'attempt', header: 'Attempt', primary: true, render: (a) => `Attempt ${a.attemptNumber}` },
                    { key: 'started', header: 'Started', render: (a) => new Date(a.startedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) },
                    { key: 'submitted', header: 'Submitted', render: (a) => a.submittedAt ? new Date(a.submittedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : '—' },
                    {
                        key: 'status',
                        header: 'Status',
                        render: (a) => a.status === 'submitted'
                            ? <StatusPill size="tag" tone="mint">Submitted</StatusPill>
                            : a.status === 'overdue'
                                ? <StatusPill size="tag" tone="butter">Being submitted</StatusPill>
                                : <StatusPill size="tag" tone="sky">Active</StatusPill>,
                    },
                    {
                        key: 'score',
                        header: 'Score',
                        align: 'right',
                        render: (a) => <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{a.scorePercentage !== null ? `${a.scorePercentage}%` : '—'}</Typography>,
                    },
                ]}
                actions={(a) => a.status === 'submitted' ? (
                    <Button variant="text" size="small" onClick={() => navigate(`/courses/${encodeURIComponent(courseId)}/attempts/${encodeURIComponent(a.attemptId)}?quizId=${encodeURIComponent(quizId)}`)}>
                        View result
                    </Button>
                ) : a.status === 'active' ? (
                    <Button variant="contained" size="small" onClick={() => navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempt?attemptId=${encodeURIComponent(a.attemptId)}`)}>
                        Resume attempt
                    </Button>
                ) : (
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>Being submitted</Typography>
                )}
            />
        </Page>
    );
}