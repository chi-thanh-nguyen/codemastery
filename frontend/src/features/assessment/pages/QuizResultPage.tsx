import type { ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { StatGroup, StatTile, Tile } from '../../../shared/components/Tile';
import { MasteryTag, StatusPill } from '../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { CodeBlock } from '../../../shared/components/CodeBlock';
import type { ReviewItemView, SkillChangeView } from '../types/assessmentTypes';
import { radius, roles } from '../../../app/theme/theme';
import { useEffect, useState } from 'react';
import { Alert } from '@mui/material';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { useAuth } from '../../auth/hooks/useAuth';
import { readAttemptResult } from '../api/quizApi';
import { quizKindLabel } from '../types/assessmentTypes';
import type { B3AttemptResult } from '../types/assessmentTypes';
import { ApiError } from '../../../shared/types/apiTypes';

export interface QuizResultPageProps {
    quizTitle: string;
    kindLabel: string;
    score: number;
    correct: number;
    total: number;
    attemptNumber: number;
    submittedAtLabel: string;
    review: ReviewItemView[];
    /** Only when the owning contract returns them; never computed in the client. */
    skillChanges?: SkillChangeView[];
    recommendation?: ReactNode;
    onBack: () => void;
    onHistory?: () => void;
    onOpenMap?: () => void;
    back?: Crumb;
    notice?: ReactNode;
}

/** S12 Quiz result: score, per-question correctness, skill changes and the next step (US-05/US-07). */
export function QuizResultPage({ quizTitle, kindLabel, score, correct, total, attemptNumber, submittedAtLabel, review, skillChanges, recommendation, onBack, onHistory, onOpenMap, back, notice }: QuizResultPageProps) {
    return <Page width="reading">
        <PageHeader title={quizTitle} subtitle={`${kindLabel} · Submitted ${submittedAtLabel}`} back={back}
            actions={<>{onHistory && <Button variant="outlined" onClick={onHistory}>Attempt history</Button>}<Button variant="contained" onClick={onBack}>Back to course</Button></>} />
        {notice}
        <Box sx={{ mb: 5 }}><StatGroup label="Result">
            <StatTile value={<>{score}<Box component="span" sx={{ fontSize: '1.5rem' }}>%</Box></>} label="Score" tone="sky" />
            <StatTile value={`${correct}/${total}`} label="Correct" tone="mint" />
            <StatTile value={attemptNumber} label="Attempt" tone="white" />
        </StatGroup></Box>
        {skillChanges && skillChanges.length > 0 && <Box component="section" aria-labelledby="result-skills" sx={{ mb: 5 }}>
            <Typography id="result-skills" variant="h2" sx={{ mb: 2 }}>Skills updated</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                {skillChanges.map((change) => <Box component="li" key={change.skillId} sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, px: 3, py: 2, borderBottom: `1px solid ${roles.line.hairline}`, '&:last-child': { borderBottom: 0 } }}>
                    <Typography sx={{ fontWeight: 600, flex: '1 1 160px' }}>{change.name}</Typography>
                    <MasteryTag state={change.before} /><PresentationIcon name="arrow-right" aria-label="changed to" sx={{ fontSize: 18, color: 'text.secondary' }} /><MasteryTag state={change.after} />
                </Box>)}
            </Box>
            {onOpenMap && <Button variant="text" sx={{ mt: 1.5 }} onClick={onOpenMap} endIcon={<PresentationIcon name="arrow-right" />}>Open Mastery Map</Button>}
        </Box>}
        {recommendation && <Box component="section" aria-labelledby="result-next" sx={{ mb: 5, maxWidth: 560 }}>
            <Typography id="result-next" variant="h2" sx={{ mb: 2 }}>Next step</Typography>{recommendation}
        </Box>}
        <Box component="section" aria-labelledby="result-review">
            <Typography id="result-review" variant="h2" sx={{ mb: 2 }}>Question review</Typography>
            <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
                {review.map((item) => <Tile key={item.questionId} component="li">
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', justifyContent: 'space-between' }}>
                        <Box sx={{ minWidth: 0, flex: '1 1 320px' }}>
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Question {item.position}</Typography>
                            <Typography variant="h4" component="h3" sx={{ mt: .5 }}>{item.prompt}</Typography>
                        </Box>
                        <StatusPill size="tag" tone={item.correct ? 'mint' : 'rose'} icon={item.correct ? 'check-circle' : 'alert-circle'}>{item.correct ? 'Correct' : 'Incorrect'}</StatusPill>
                    </Box>
                    {item.code && <CodeBlock code={item.code} />}
                    <Typography variant="body2" sx={{ mt: 1.5, maxWidth: '70ch' }}><Box component="span" sx={{ color: 'text.secondary' }}>Your answer: </Box>{item.selectedLabels.length ? item.selectedLabels.join(', ') : 'Not answered'}</Typography>
                    {item.correctLabels && !item.correct && <Typography variant="body2" sx={{ mt: .5, maxWidth: '70ch' }}><Box component="span" sx={{ color: 'text.secondary' }}>Correct answer: </Box>{item.correctLabels.join(', ')}</Typography>}
                </Tile>)}
            </Box>
        </Box>
    </Page>;
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Live S12 Quiz Result: reads `?quizId=`, displays own selections + correctness only (answer keys withheld per ADR007 §11). */
export function LiveQuizResultPage({ courseId, attemptId }: { courseId: string; attemptId: string }) {
    const auth = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();
    const quizId = searchParams.get('quizId');
    const navState = location.state as { discardedNotice?: boolean; quizTitle?: string } | null;

    const [result, setResult] = useState<B3AttemptResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [revision, retry] = useState(0);

    const validQuizId = quizId && uuidPattern.test(quizId) ? quizId : null;

    useEffect(() => {
        if (!validQuizId) {
            setLoading(false);
            return;
        }
        let alive = true;
        const controller = new AbortController();
        const gen = auth.generation;
        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        readAttemptResult(courseId, validQuizId, attemptId, {
            signal: controller.signal,
            isCurrent: () => alive && auth.generation === gen,
        })
            .then((res) => {
                if (alive && auth.generation === gen) setResult(res);
            })
            .catch((err: unknown) => {
                if (!alive || auth.generation !== gen || (err instanceof ApiError && err.kind === 'cancelled')) return;
                if (err instanceof ApiError) {
                    setErrorStatus(err.status ?? null);
                    setErrorMessage(err.message);
                } else {
                    setErrorMessage("Couldn't load this result.");
                }
            })
            .finally(() => {
                if (alive && auth.generation === gen) setLoading(false);
            });

        return () => {
            alive = false;
            controller.abort();
        };
    }, [courseId, validQuizId, attemptId, auth.generation, revision]);

    if (!validQuizId) {
        return (
            <Page>
                <SystemStateView
                    icon="search"
                    title="We can't show this result from this link"
                    message="This link is missing the quiz identifier."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/assessments`} variant="contained">
                            Go to assessments
                        </Button>
                    }
                />
            </Page>
        );
    }

    if (loading) return <Page><LoadingView message="Loading quiz result…" /></Page>;

    if (errorStatus === 409) {
        return (
            <Page>
                <SystemStateView
                    icon="clock"
                    title="Result not ready yet"
                    message="Your result isn't ready yet. Try again in a moment."
                    actions={
                        <Box sx={{ display: 'flex', gap: 1.5 }}>
                            <Button variant="outlined" onClick={() => retry((r) => r + 1)}>Try again</Button>
                            <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(validQuizId)}/attempt?attemptId=${encodeURIComponent(attemptId)}`} variant="contained">
                                Back to attempt
                            </Button>
                        </Box>
                    }
                />
            </Page>
        );
    }

    if (errorStatus === 404) {
        return (
            <Page>
                <SystemStateView
                    icon="search"
                    title="Result not available"
                    message="This attempt doesn't exist or isn't available."
                    actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/assessments`} variant="contained">Go to assessments</Button>}
                />
            </Page>
        );
    }

    if (errorMessage || !result) {
        return <Page><ErrorView title="Couldn't load result" message={errorMessage ?? 'Please try again.'} onRetry={() => retry((r) => r + 1)} /></Page>;
    }

    const autoSubmitted = result.submittedAt === result.expiresAt;
    const correctCount = result.answers.filter((a) => a.correct).length;
    const reviewItems: ReviewItemView[] = result.answers
        .slice()
        .sort((a, b) => a.position - b.position)
        .map((ans) => ({
            questionId: ans.questionId,
            position: ans.position,
            prompt: ans.prompt,
            selectedLabels: ans.options.filter((_, idx) => ans.selectedOptions[idx]),
            correct: ans.correct,
        }));

    const notice = (
        <>
            {navState?.discardedNotice && (
                <Alert severity="info" sx={{ mb: 2 }}>
                    This attempt was already submitted, so answers on this page that weren't saved couldn't be included.
                </Alert>
            )}
            {autoSubmitted && (
                <Alert severity="info" sx={{ mb: 2 }}>
                    Submitted automatically when time ran out.
                </Alert>
            )}
            <Typography variant="caption" sx={{ display: 'block', mb: 3, color: 'text.secondary' }}>
                Mastery states reflect the latest submitted quiz evidence for each skill.
            </Typography>
        </>
    );

    return (
        <QuizResultPage
            quizTitle={navState?.quizTitle ?? 'Quiz result'}
            kindLabel={quizKindLabel.test}
            score={result.scorePercentage}
            correct={correctCount}
            total={result.answers.length}
            attemptNumber={result.attemptNumber}
            submittedAtLabel={new Date(result.submittedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })}
            review={reviewItems}
            notice={notice}
            onBack={() => navigate(`/courses/${encodeURIComponent(courseId)}/learn`)}
            onHistory={() => navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(validQuizId)}/history`)}
            onOpenMap={() => navigate(`/courses/${encodeURIComponent(courseId)}/mastery`)}
            back={{ label: 'Assessments', href: `/courses/${encodeURIComponent(courseId)}/assessments` }}
        />
    );
}