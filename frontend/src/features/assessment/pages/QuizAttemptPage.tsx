import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, ButtonBase, IconButton, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { ThemeControl } from '../../../shared/components/AppHeader';
import { FactList, Tile } from '../../../shared/components/Tile';
import { QuestionRenderer } from '../components/QuestionRenderer';
import { QuizTimer, formatClock } from '../components/QuizTimer';
import { quizKindLabel } from '../types/assessmentTypes';
import type { QuestionView, QuizSummaryView } from '../types/assessmentTypes';
import { control, focusRingOnChrome, radius, roles } from '../../../app/theme/theme';
import { useRef } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { CircularProgress } from '@mui/material';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { useAuth } from '../../auth/hooks/useAuth';
import { listAttemptHistory, listLearnerQuizzes, startQuizAttempt } from '../api/quizApi';
import { useQuizAttempt } from '../hooks/useQuizAttempt';
import { CURRENT_DOC_INSTANCE } from './CourseAssessmentsPage';
import type { B3AttemptSummary, B3QuizSummary, SaveMachineState } from '../types/assessmentTypes'; import { ApiError } from '../../../shared/types/apiTypes';

export interface QuizIntroViewProps { quiz: QuizSummaryView; onStart: () => void; starting?: boolean; error?: string; back?: Crumb; note?: ReactNode; title?: string; children?: ReactNode }
/** Before the timer starts: kind, length, time limit and attempt number (US-05). */
export function QuizIntroView({ quiz, onStart, starting, error, back, note, title, children }: QuizIntroViewProps) {
    return <Page width="form">
        <PageHeader title={title ?? quiz.title} subtitle={quizKindLabel[quiz.kind]} back={back} />
        <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 720 }}>
            {children}
            <FactList columns={2} items={[
                { label: 'Questions', value: quiz.questionCount }, { label: 'Time limit', value: <span className="cm-tabular">{formatClock(quiz.timeLimitSeconds)}</span> },
                { label: 'Attempt', value: `Attempt ${quiz.nextAttemptNumber}` }, { label: 'Scoring', value: 'Each question counts equally. Unanswered questions score zero.' },
            ]} />
            <Alert severity="info" sx={{ mt: 3 }}>The timer starts when you begin. When time runs out, your answers are submitted automatically.</Alert>
            {note}
            {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            <Button variant="contained" sx={{ mt: 3 }} onClick={onStart} disabled={starting}>{starting ? 'Starting…' : 'Start quiz'}</Button>
        </Tile>
    </Page>;
}

export interface QuizAttemptPageProps {
    quizTitle: string;
    questions: QuestionView[];
    answers: Record<string, string[]>;
    currentIndex: number;
    onAnswer: (questionId: string, optionIds: string[]) => void;
    onNavigate: (index: number) => void;
    remainingSeconds: number;
    totalSeconds: number;
    phase: 'answering' | 'submitting' | 'expired';
    onSubmit: () => void;
    onExit: () => void;
    submitError?: string;
    /** Prompt before the document unloads while answering (browser Back / tab close). */
    guardUnload?: boolean;
    /** Strip rendered above the frame bar (for example a preview notice). */
    banner?: ReactNode;
}

/** S11 Quiz attempt (Focus archetype): no primary navigation; timer and navigator always visible. */
export function QuizAttemptPage({ quizTitle, questions, answers, currentIndex, onAnswer, onNavigate, remainingSeconds, totalSeconds, phase, onSubmit, onExit, submitError, guardUnload, banner }: QuizAttemptPageProps) {
    const [dialog, setDialog] = useState<'submit' | 'leave' | null>(null);
    const question = questions[currentIndex];
    const answered = questions.filter((item) => (answers[item.id] ?? []).length > 0).length;
    const locked = phase !== 'answering';
    useEffect(() => {
        if (!guardUnload || phase !== 'answering') return;
        const guard = (event: BeforeUnloadEvent) => { event.preventDefault(); };
        window.addEventListener('beforeunload', guard);
        return () => window.removeEventListener('beforeunload', guard);
    }, [guardUnload, phase]);
    const navigator = <Box component="nav" aria-label="Questions" sx={{ minWidth: 0 }}>
        <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: { xs: 'nowrap', md: 'wrap' }, gap: 1, overflowX: { xs: 'auto', md: 'visible' }, py: .5, px: { xs: .5, md: 0 } }}>
            {questions.map((item, index) => {
                const done = (answers[item.id] ?? []).length > 0, current = index === currentIndex;
                return <Box component="li" key={item.id} sx={{ flexShrink: 0 }}>
                    <ButtonBase onClick={() => onNavigate(index)} aria-current={current ? 'step' : undefined} aria-label={`Question ${index + 1}, ${done ? 'answered' : 'not answered'}`}
                        sx={{
                            width: 44, height: 44, borderRadius: '50%', fontWeight: 600, fontSize: '0.9375rem', bgcolor: done ? control.primary : roles.bg.surface, color: done ? roles.control.primary.text : roles.text.primary,
                            border: done ? 0 : `1.5px solid ${roles.line.strong}`, boxShadow: current ? `0 0 0 3px ${roles.bg.canvas}, 0 0 0 5px ${roles.accent.progress}` : 'none'
                        }}>{index + 1}</ButtonBase>
                </Box>;
            })}
        </Box>
        <Typography variant="body2" sx={{ mt: 1.5, color: 'text.secondary' }} role="status">{answered} of {questions.length} answered</Typography>
    </Box>;
    return <Box sx={{ minHeight: '100dvh', bgcolor: roles.bg.frame, display: 'flex', flexDirection: 'column' }}>
        {banner}
        <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 'appBar', bgcolor: roles.bg.frame, color: roles.text.onChrome, '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 }, minHeight: { xs: 64, sm: 72 }, px: { xs: 1.5, sm: 3 }, flexWrap: 'wrap', py: { xs: 1, sm: 0 } }}>
                <IconButton aria-label="Leave quiz" onClick={() => setDialog('leave')} disabled={locked} sx={{ color: roles.text.onChrome, '&:hover': { bgcolor: roles.chrome.raised } }}><PresentationIcon name="close" /></IconButton>
                <ThemeControl />
                {/* Below 600 px the bar keeps exit, Theme, timer and Submit; title and counter move into the navigator strip (D10-1). */}
                <Typography component="p" sx={{ display: { xs: 'none', sm: 'block' }, fontWeight: 600, fontSize: '1rem', minWidth: 0, flex: { sm: '1 1 auto', md: '0 1 auto' }, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{quizTitle}</Typography>
                <Typography component="p" className="cm-tabular" sx={{ color: roles.chrome.muted, flex: 1, textAlign: 'center', display: { xs: 'none', md: 'block' } }}>Question {currentIndex + 1} of {questions.length}</Typography>
                <Box sx={{ display: 'contents', '& > *': { ml: { xs: 'auto', sm: 0 } } }}><QuizTimer remainingSeconds={remainingSeconds} totalSeconds={totalSeconds} /></Box>
                <Button variant="contained" color="onChrome" size="small" onClick={() => setDialog('submit')} disabled={locked}>Submit quiz</Button>
            </Box>
            <Box sx={{ display: { md: 'none' }, px: 1.5, pb: 1.5, bgcolor: roles.bg.canvas, borderRadius: `${radius.lg}px ${radius.lg}px 0 0`, pt: 1.5, color: roles.text.primary }}>
                <Box sx={{ display: { xs: 'flex', sm: 'none' }, alignItems: 'baseline', gap: 1, px: .5, pb: 1, minWidth: 0 }}>
                    <Typography component="p" sx={{ fontWeight: 600, fontSize: '1rem', minWidth: 0, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{quizTitle}</Typography>
                    <Typography component="p" variant="body2" className="cm-tabular" sx={{ color: 'text.secondary', flexShrink: 0 }}>Question {currentIndex + 1} of {questions.length}</Typography>
                </Box>
                {navigator}
            </Box>
        </Box>
        <Box component="main" id="main-content" tabIndex={-1} sx={{
            flex: 1, bgcolor: roles.bg.canvas, mx: { md: 1 }, mb: { md: 1 }, borderRadius: { md: `${radius.xl}px` }, display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) 300px' }
        }}>
            <Box sx={{ px: { xs: 2, sm: 4, lg: 6 }, py: { xs: 3, md: 6 }, maxWidth: 820, width: '100%', mx: 'auto', boxSizing: 'border-box' }}>
                <Typography variant="h1" className="cm-visually-hidden">{quizTitle}</Typography>
                {phase === 'expired' && <Alert severity="warning" role="status" sx={{ mb: 3 }}>Time is up. Submitting your answers…</Alert>}
                {phase === 'submitting' && <Alert severity="info" role="status" sx={{ mb: 3 }}>Submitting your answers…</Alert>}
                {submitError && <Alert severity="error" sx={{ mb: 3 }} action={<Button variant="text" size="small" color="inherit" onClick={onSubmit}>Try again</Button>}>{submitError}</Alert>}
                {question && <QuestionRenderer key={question.id} question={question} total={questions.length} selected={answers[question.id] ?? []} onChange={(ids) => onAnswer(question.id, ids)} disabled={locked} />}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1.5, mt: 4 }}>
                    <Button variant="outlined" onClick={() => onNavigate(currentIndex - 1)} disabled={currentIndex === 0 || locked} startIcon={<PresentationIcon name="arrow-left" />}>Previous</Button>
                    {currentIndex < questions.length - 1
                        ? <Button variant="contained" onClick={() => onNavigate(currentIndex + 1)} disabled={locked} endIcon={<PresentationIcon name="arrow-right" />}>Next</Button>
                        : <Button variant="contained" onClick={() => setDialog('submit')} disabled={locked}>Review and submit</Button>}
                </Box>
            </Box>
            <Box component="aside" aria-label="Question navigator" sx={{ display: { xs: 'none', md: 'block' }, bgcolor: roles.bg.fill, px: 3, py: 6, borderRadius: { md: `0 ${radius.xl}px ${radius.xl}px 0` } }}>
                <Typography component="h2" variant="h4" sx={{ mb: 2 }}>Questions</Typography>{navigator}
                <Typography variant="caption" sx={{ display: 'block', mt: 3, color: 'text.secondary' }}>Filled circles are answered. You can change answers until you submit.</Typography>
            </Box>
        </Box>
        <ConfirmDialog open={dialog === 'submit'} title="Submit quiz?" message={`You answered ${answered} of ${questions.length}. Unanswered questions score zero.`}
            confirmLabel="Submit quiz" cancelLabel="Keep answering" onCancel={() => setDialog(null)} onConfirm={() => { setDialog(null); onSubmit(); }} />
        <ConfirmDialog open={dialog === 'leave'} title="Leave the quiz?" message="The timer keeps running while you are away. Answers you have chosen are not submitted until you submit the quiz."
            confirmLabel="Leave quiz" cancelLabel="Stay" isDestructive onCancel={() => setDialog(null)} onConfirm={() => { setDialog(null); onExit(); }} />
    </Box>;
}

export function LiveQuizIntroPage({ courseId, quizId, courseTitle }: { courseId: string; quizId: string; courseTitle: string }) {
    const auth = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const navState = location.state as { docInstance?: string; quizSummary?: B3QuizSummary; provisionalTitle?: string } | null;
    const validDocSummary =
        navState?.docInstance === CURRENT_DOC_INSTANCE &&
            navState.quizSummary?.id === quizId &&
            navState.quizSummary?.courseId === courseId
            ? navState.quizSummary
            : null;

    const [summary, setSummary] = useState<B3QuizSummary | null>(validDocSummary);
    const [lookupState, setLookupState] = useState<'idle' | 'loading' | 'done' | 'missing'>(
        validDocSummary ? 'done' : 'loading'
    );
    const [history, setHistory] = useState<B3AttemptSummary[] | null>(null);
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [accessStatus, setAccessStatus] = useState<number | null>(null);
    const [starting, setStarting] = useState(false);
    const [startError, setStartError] = useState<string | null>(null);
    const [revision, retry] = useState(0);

    // Authoritative history & access check on mount (D12-1)
    useEffect(() => {
        let alive = true;
        const controller = new AbortController();
        const gen = auth.generation;
        setAccessStatus(null);
        setHistoryError(null);

        listAttemptHistory(courseId, quizId, 0, 20, {
            signal: controller.signal,
            isCurrent: () => alive && auth.generation === gen,
        })
            .then((res) => {
                if (alive && auth.generation === gen) setHistory(res.content);
            })
            .catch((err: unknown) => {
                if (!alive || auth.generation !== gen || (err instanceof ApiError && err.kind === 'cancelled')) return;
                if (err instanceof ApiError && (err.status === 403 || err.status === 404)) {
                    setAccessStatus(err.status);
                } else {
                    setHistoryError("We couldn't check your earlier attempts.");
                }
            });

        return () => {
            alive = false;
            controller.abort();
        };
    }, [courseId, quizId, auth.generation, revision]);

    // Bounded list lookup when no complete summary exists (G2 / D11-3)
    useEffect(() => {
        if (summary) return;
        let alive = true;
        const controller = new AbortController();
        const gen = auth.generation;
        setLookupState('loading');

        (async () => {
            try {
                const first = await listLearnerQuizzes(courseId, 0, 100, {
                    signal: controller.signal,
                    isCurrent: () => alive && auth.generation === gen,
                });
                const hit = first.content.find((q) => q.id === quizId);
                if (hit) {
                    if (alive && auth.generation === gen) {
                        setSummary(hit);
                        setLookupState('done');
                    }
                    return;
                }
                for (let p = 1; p < first.totalPages; p += 1) {
                    const next = await listLearnerQuizzes(courseId, p, 100, {
                        signal: controller.signal,
                        isCurrent: () => alive && auth.generation === gen,
                    });
                    const found = next.content.find((q) => q.id === quizId);
                    if (found) {
                        if (alive && auth.generation === gen) {
                            setSummary(found);
                            setLookupState('done');
                        }
                        return;
                    }
                }
                if (alive && auth.generation === gen) setLookupState('missing');
            } catch {
                if (alive && auth.generation === gen) setLookupState('missing');
            }
        })();

        return () => {
            alive = false;
            controller.abort();
        };
    }, [courseId, quizId, summary, auth.generation, revision]);

    if (accessStatus === 404) {
        return (
            <Page>
                <SystemStateView
                    icon="search"
                    title="Quiz not available"
                    message="It may have been hidden by the instructor."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/assessments`} variant="contained">
                            Back to assessments
                        </Button>
                    }
                />
            </Page>
        );
    }

    if (accessStatus === 403) {
        return (
            <Page>
                <SystemStateView
                    icon="lock"
                    title="Join this course to take this quiz"
                    message="Your course membership is required."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">
                            View course details
                        </Button>
                    }
                />
            </Page>
        );
    }

    const latest = history?.[0];
    const activeAttempt = latest?.status === 'active' ? latest : null;
    const overdueAttempt = latest?.status === 'overdue' ? latest : null;
    const nextNumber = latest ? latest.attemptNumber + 1 : 1;

    if (summary?.kind === 'diagnostic') {
        return <Navigate to={`/courses/${encodeURIComponent(courseId)}/diagnostic`} replace />;
    }

    const handleStart = async () => {
        if (starting) return;
        setStarting(true);
        setStartError(null);
        try {
            const started = await startQuizAttempt(courseId, quizId);
            navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempt?attemptId=${encodeURIComponent(started.attemptId)}`);
        } catch (err: unknown) {
            if (err instanceof ApiError && err.status === 409) {
                retry((r) => r + 1);
                setStartError("This quiz can't be started right now.");
            } else {
                retry((r) => r + 1);
                setStartError("We couldn't confirm whether the quiz started. Check your attempt history or try again.");
            }
        } finally {
            setStarting(false);
        }
    };

    const handleResume = (attId: string) => {
        navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempt?attemptId=${encodeURIComponent(attId)}`);
    };

    const displayTitle = summary?.title ?? navState?.provisionalTitle ?? 'Quiz';

    return (
        <Page width="form">
            <PageHeader
                title={displayTitle}
                subtitle={summary ? quizKindLabel[summary.kind] : undefined}
                back={{ label: 'Assessments', href: `/courses/${encodeURIComponent(courseId)}/assessments` }}
                actions={
                    history && history.length > 0 && (
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/history`} variant="outlined">
                            Attempt history
                        </Button>
                    )
                }
            />
            <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 720 }}>
                {lookupState === 'loading' && <Typography role="status" variant="body2" sx={{ mb: 2, color: 'text.secondary' }}>Loading quiz details…</Typography>}
                {lookupState === 'missing' && (
                    <Alert severity="info" sx={{ mb: 3 }} action={<Button size="small" onClick={() => retry((r) => r + 1)}>Try again</Button>}>
                        Some quiz details couldn't be loaded. The time limit will appear when the quiz starts.
                    </Alert>
                )}
                {summary && (
                    <FactList
                        columns={2}
                        items={[
                            { label: 'Questions', value: summary.questionCount },
                            { label: 'Time limit', value: <span className="cm-tabular">{formatClock(summary.timeLimitSeconds)}</span> },
                            { label: 'Course', value: courseTitle },
                            { label: 'Attempt', value: activeAttempt ? `Attempt ${activeAttempt.attemptNumber} (Active)` : `Attempt ${nextNumber}` },
                        ]}
                    />
                )}
                <Alert severity="info" sx={{ mt: 3 }}>
                    The timer starts when you begin. Your saved answers are submitted automatically when time runs out.
                </Alert>
                {historyError && (
                    <Alert severity="warning" sx={{ mt: 2 }} action={<Button size="small" onClick={() => retry((r) => r + 1)}>Try again</Button>}>
                        {historyError} If an attempt is in progress, starting reopens it with its timer still running; otherwise a new attempt starts.
                    </Alert>
                )}
                {startError && <Alert severity="error" sx={{ mt: 2 }}>{startError}</Alert>}
                {overdueAttempt ? (
                    <Alert severity="warning" sx={{ mt: 3 }} action={<Button size="small" onClick={() => retry((r) => r + 1)}>Check again</Button>}>
                        Your last attempt is being submitted because time ran out.
                    </Alert>
                ) : activeAttempt ? (
                    <Button variant="contained" sx={{ mt: 3 }} onClick={() => handleResume(activeAttempt.attemptId)}>
                        Resume attempt
                    </Button>
                ) : (
                    <Button variant="contained" sx={{ mt: 3 }} onClick={() => void handleStart()} disabled={starting}>
                        {starting ? 'Starting…' : history && history.length > 0 ? 'Start another attempt' : 'Start quiz'}
                    </Button>
                )}
            </Tile>
        </Page>
    );
}

export function LiveQuizAttemptPage({ courseId, quizId }: { courseId: string; quizId: string }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const attemptId = searchParams.get('attemptId') ?? undefined;
    const [resolvingId, setResolvingId] = useState(!attemptId);

    // D12-1: Link without attemptId resolves newest history row (never starts)
    useEffect(() => {
        if (attemptId) {
            setResolvingId(false);
            return;
        }
        let alive = true;
        listAttemptHistory(courseId, quizId, 0, 1)
            .then((res) => {
                if (!alive) return;
                const latest = res.content[0];
                if (latest && (latest.status === 'active' || latest.status === 'overdue')) {
                    setSearchParams({ attemptId: latest.attemptId }, { replace: true });
                } else {
                    navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`, {
                        replace: true,
                        state: { notice: "You don't have an attempt in progress for this quiz." },
                    });
                }
            })
            .catch(() => {
                if (alive) navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`, { replace: true });
            })
            .finally(() => {
                if (alive) setResolvingId(false);
            });
        return () => {
            alive = false;
        };
    }, [attemptId, courseId, quizId, navigate, setSearchParams]);

    const {
        attempt,
        submittedResult,
        discardedNotice,
        loading,
        loadErrorStatus,
        loadErrorMessage,
        localAnswers,
        differingIds,
        saveState,
        submitting,
        submitError,
        setAnswer,
        flushSavePoint,
        checkAgain,
        reconcileUseLocal,
        reconcileUseServer,
        submit,
        retryLoad,
    } = useQuizAttempt(courseId, quizId, attemptId);

    const [currentIndex, setCurrentIndex] = useState(0);
    const [dialog, setDialog] = useState<'submit' | 'leave' | null>(null);
    const [remainingSeconds, setRemainingSeconds] = useState(0);
    const leavePendingRef = useRef(false);

    // Live countdown ticker synced strictly to server expiresAt
    useEffect(() => {
        if (!attempt) return;
        const calc = () => Math.max(0, Math.floor((new Date(attempt.expiresAt).getTime() - Date.now()) / 1000));
        setRemainingSeconds(calc());
        const id = setInterval(() => setRemainingSeconds(calc()), 1000);
        return () => clearInterval(id);
    }, [attempt]);

    // Redirect to result route as soon as attempt is submitted
    useEffect(() => {
        if (submittedResult) {
            navigate(
                `/courses/${encodeURIComponent(courseId)}/attempts/${encodeURIComponent(submittedResult.attemptId)}?quizId=${encodeURIComponent(quizId)}`,
                { replace: true, state: { discardedNotice } }
            );
        }
    }, [submittedResult, courseId, quizId, discardedNotice, navigate]);

    // Warn on tab close/reload while unsaved changes exist
    useEffect(() => {
        if (saveState !== 'Pending' && saveState !== 'Saving' && saveState !== 'Reconcile') return;
        const guard = (event: BeforeUnloadEvent) => event.preventDefault();
        window.addEventListener('beforeunload', guard);
        return () => window.removeEventListener('beforeunload', guard);
    }, [saveState]);

    // Save point on unmount / Back navigation
    useEffect(() => {
        return () => {
            if (!leavePendingRef.current) flushSavePoint();
        };
    }, [flushSavePoint]);

    if (resolvingId || loading) {
        return <Page><LoadingView message="Loading quiz attempt…" /></Page>;
    }

    if (loadErrorStatus === 400) {
        return (
            <Page>
                <SystemStateView icon="alert" title="Invalid attempt link" message="We can't open this attempt from this link."
                    actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`} variant="contained">Back to quiz</Button>} />
            </Page>
        );
    }

    if (loadErrorStatus === 403 || saveState === 'No access') {
        return (
            <Page>
                <SystemStateView icon="lock" title="Access denied" message="You no longer have access to this quiz."
                    actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">Back to course</Button>} />
            </Page>
        );
    }

    if (loadErrorStatus === 404) {
        return (
            <Page>
                <SystemStateView icon="search" title="Attempt not available" message="This attempt isn't available. The quiz may have been hidden, or the link may be wrong."
                    actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/assessments`} variant="contained">Back to assessments</Button>} />
            </Page>
        );
    }

    if (loadErrorMessage || !attempt) {
        return <Page><ErrorView title="We couldn't load this attempt" message={loadErrorMessage ?? 'Please try again.'} onRetry={retryLoad} /></Page>;
    }

    const questions = attempt.questions.slice().sort((a, b) => a.position - b.position);
    const currentQ = questions[currentIndex];
    const totalSeconds = Math.max(1, Math.floor((new Date(attempt.expiresAt).getTime() - new Date(attempt.startedAt).getTime()) / 1000));
    const locked = saveState === 'Time is up' || submitting;
    const canSubmit = (saveState === 'Saved' || saveState === 'Pending' || saveState === 'Saving') && !submitting;

    const answeredCount = questions.filter((q) => (localAnswers[q.questionId] ?? []).some(Boolean)).length;

    const saveLineMap: Record<SaveMachineState, string> = {
        Saved: 'All answers saved',
        Pending: 'Unsaved changes',
        Saving: 'Saving…',
        Checking: 'Checking your saved answers…',
        'Check failed': "We couldn't check your saved answers.",
        Reconcile: 'Your answers differ from the server.',
        'Time is up': 'Time is up.',
        'No access': 'No access.',
    };

    const toQuestionView = (q: typeof currentQ): QuestionView => ({
        id: q.questionId,
        position: q.position,
        kind: q.kind,
        prompt: q.prompt,
        options: q.options.map((label, idx) => ({ id: String(idx), label })),
    });

    const selectedIndices = (localAnswers[currentQ.questionId] ?? [])
        .map((val, idx) => (val ? String(idx) : null))
        .filter((v): v is string => v !== null);

    const handleSelectOption = (optionIds: string[]) => {
        const mask = currentQ.options.map((_, idx) => optionIds.includes(String(idx)));
        setAnswer(currentQ.questionId, mask);
    };

    const goQuestion = (idx: number) => {
        flushSavePoint();
        setCurrentIndex(idx);
    };

    return (
        <Box sx={{ minHeight: '100dvh', bgcolor: roles.bg.frame, display: 'flex', flexDirection: 'column' }}>
            <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 'appBar', bgcolor: roles.bg.frame, color: roles.text.onChrome, '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 }, minHeight: { xs: 64, sm: 72 }, px: { xs: 1.5, sm: 3 }, flexWrap: 'wrap', py: { xs: 1, sm: 0 } }}>
                    <IconButton aria-label="Leave quiz" onClick={() => setDialog('leave')} sx={{ color: roles.text.onChrome, '&:hover': { bgcolor: roles.chrome.raised } }}>
                        <PresentationIcon name="close" />
                    </IconButton>
                    <ThemeControl />
                    <Typography component="p" sx={{ display: { xs: 'none', sm: 'block' }, fontWeight: 600, fontSize: '1rem', flex: { sm: '1 1 auto', md: '0 1 auto' } }}>
                        Attempt {attempt.attemptNumber}
                    </Typography>
                    <Typography component="p" className="cm-tabular" sx={{ color: roles.chrome.muted, flex: 1, textAlign: 'center', display: { xs: 'none', md: 'block' } }}>
                        Question {currentIndex + 1} of {questions.length}
                    </Typography>
                    <Box sx={{ display: 'contents', '& > *': { ml: { xs: 'auto', sm: 0 } } }}>
                        <QuizTimer remainingSeconds={remainingSeconds} totalSeconds={totalSeconds} />
                    </Box>
                    <Button variant="contained" color="onChrome" size="small" onClick={() => setDialog('submit')} disabled={!canSubmit}>
                        {submitting ? 'Submitting…' : 'Submit quiz'}
                    </Button>
                </Box>
            </Box>

            <Box component="main" id="main-content" tabIndex={-1} sx={{ flex: 1, bgcolor: roles.bg.canvas, mx: { md: 1 }, mb: { md: 1 }, borderRadius: { md: `${radius.xl}px` }, display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) 300px' } }}>
                <Box sx={{ px: { xs: 2, sm: 4, lg: 6 }, py: { xs: 3, md: 6 }, maxWidth: 820, width: '100%', mx: 'auto', boxSizing: 'border-box' }}>
                    <Typography variant="h1" className="cm-visually-hidden">Quiz Attempt {attempt.attemptNumber}</Typography>

                    {saveState === 'Time is up' && (
                        <Alert severity="warning" role="status" sx={{ mb: 3 }} action={
                            <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/attempts/${encodeURIComponent(attempt.attemptId)}?quizId=${encodeURIComponent(quizId)}`} size="small" color="inherit">
                                View result
                            </Button>
                        }>
                            Time is up. Your last saved answers will be submitted automatically.
                        </Alert>
                    )}

                    {saveState === 'Check failed' && (
                        <Alert severity="error" sx={{ mb: 3 }} action={<Button size="small" color="inherit" onClick={() => void checkAgain()}>Check again</Button>}>
                            We couldn't check your saved answers. Your answers are still on this page, but they haven't been saved.
                        </Alert>
                    )}

                    {saveState === 'Reconcile' && (
                        <Alert severity="info" sx={{ mb: 3 }} action={
                            <Box sx={{ display: 'flex', gap: 1 }}>
                                <Button size="small" onClick={reconcileUseServer}>Use saved answers</Button>
                                <Button size="small" variant="contained" onClick={reconcileUseLocal}>Save my answers</Button>
                            </Box>
                        }>
                            Your answers on this page differ from the answers saved for this attempt.
                        </Alert>
                    )}

                    {submitError && <Alert severity="error" sx={{ mb: 3 }}>{submitError}</Alert>}

                    {currentQ && (
                        <QuestionRenderer
                            key={currentQ.questionId}
                            question={toQuestionView(currentQ)}
                            total={questions.length}
                            selected={selectedIndices}
                            onChange={handleSelectOption}
                            disabled={locked}
                        />
                    )}

                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 4 }}>
                        <Button variant="outlined" onClick={() => goQuestion(currentIndex - 1)} disabled={currentIndex === 0 || locked} startIcon={<PresentationIcon name="arrow-left" />}>
                            Previous
                        </Button>
                        <Typography role="status" variant="caption" sx={{ color: 'text.secondary' }}>
                            {saveLineMap[saveState]}
                        </Typography>
                        {currentIndex < questions.length - 1 ? (
                            <Button variant="contained" onClick={() => goQuestion(currentIndex + 1)} disabled={locked} endIcon={<PresentationIcon name="arrow-right" />}>
                                Next
                            </Button>
                        ) : (
                            <Button variant="contained" onClick={() => setDialog('submit')} disabled={!canSubmit}>
                                Submit quiz
                            </Button>
                        )}
                    </Box>
                </Box>

                <Box component="aside" aria-label="Question navigator" sx={{ display: { xs: 'none', md: 'block' }, bgcolor: roles.bg.fill, px: 3, py: 6, borderRadius: { md: `0 ${radius.xl}px ${radius.xl}px 0` } }}>
                    <Typography component="h2" variant="h4" sx={{ mb: 2 }}>Questions</Typography>
                    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {questions.map((q, idx) => {
                            const done = (localAnswers[q.questionId] ?? []).some(Boolean);
                            const isCur = idx === currentIndex;
                            const diff = differingIds.includes(q.questionId);
                            return (
                                <Box component="li" key={q.questionId}>
                                    <ButtonBase
                                        onClick={() => goQuestion(idx)}
                                        aria-current={isCur ? 'step' : undefined}
                                        sx={{
                                            width: 44, height: 44, borderRadius: '50%', fontWeight: 600, fontSize: '0.9375rem',
                                            bgcolor: done ? control.primary : roles.bg.surface,
                                            color: done ? roles.control.primary.text : roles.text.primary,
                                            border: diff ? `2px solid ${roles.tone.butter.strong}` : done ? 0 : `1.5px solid ${roles.line.strong}`,
                                            boxShadow: isCur ? `0 0 0 3px ${roles.bg.canvas}, 0 0 0 5px ${roles.accent.progress}` : 'none',
                                        }}
                                    >
                                        {idx + 1}
                                    </ButtonBase>
                                </Box>
                            );
                        })}
                    </Box>
                    <Typography variant="body2" sx={{ mt: 2, color: 'text.secondary' }}>{answeredCount} of {questions.length} answered</Typography>
                    <Typography role="status" variant="caption" sx={{ display: 'block', mt: 1, color: 'text.secondary' }}>
                        {submitting && <CircularProgress size={12} sx={{ mr: 1 }} />}
                        {saveLineMap[saveState]}
                    </Typography>
                </Box>
            </Box>

            <ConfirmDialog
                open={dialog === 'submit'}
                title="Submit quiz?"
                message={`You answered ${answeredCount} of ${questions.length}. Unanswered questions score zero.`}
                confirmLabel="Submit quiz"
                cancelLabel="Keep answering"
                onCancel={() => setDialog(null)}
                onConfirm={() => {
                    setDialog(null);
                    void submit();
                }}
            />
            <ConfirmDialog
                open={dialog === 'leave'}
                title="Leave the quiz?"
                message="The timer keeps running. Answers that aren't saved stay in this tab until you return or close it."
                confirmLabel="Leave quiz"
                cancelLabel="Stay"
                isDestructive
                onCancel={() => setDialog(null)}
                onConfirm={() => {
                    leavePendingRef.current = true;
                    flushSavePoint();
                    setDialog(null);
                    navigate(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`);
                }}
            />
        </Box>
    );
}