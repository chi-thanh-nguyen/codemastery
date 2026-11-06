import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, ButtonBase, IconButton, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { FactList, Tile } from '../../../shared/components/Tile';
import { QuestionRenderer } from '../components/QuestionRenderer';
import { QuizTimer, formatClock } from '../components/QuizTimer';
import { quizKindLabel } from '../types/assessmentTypes';
import type { QuestionView, QuizSummaryView } from '../types/assessmentTypes';
import { color, focusRingOnChrome, radius } from '../../../app/theme/theme';

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
                        sx={{ width: 44, height: 44, borderRadius: '50%', fontWeight: 600, fontSize: '0.9375rem', bgcolor: done ? color.ink950 : color.surface, color: done ? color.surface : color.ink900,
                            border: done ? 0 : `1.5px solid ${color.ink400}`, boxShadow: current ? `0 0 0 3px ${color.canvas100}, 0 0 0 5px ${color.brand600}` : 'none' }}>{index + 1}</ButtonBase>
                </Box>;
            })}
        </Box>
        <Typography variant="body2" sx={{ mt: 1.5, color: 'text.secondary' }} role="status">{answered} of {questions.length} answered</Typography>
    </Box>;
    return <Box sx={{ minHeight: '100dvh', bgcolor: color.ink950, display: 'flex', flexDirection: 'column' }}>
        {banner}
        <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 'appBar', bgcolor: color.ink950, color: color.surface, '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': focusRingOnChrome }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 }, minHeight: { xs: 64, sm: 72 }, px: { xs: 1.5, sm: 3 }, flexWrap: 'wrap', py: { xs: 1, sm: 0 } }}>
                <IconButton aria-label="Leave quiz" onClick={() => setDialog('leave')} disabled={locked} sx={{ color: color.surface, '&:hover': { bgcolor: color.chromeRaised } }}><PresentationIcon name="close" /></IconButton>
                <Typography component="p" sx={{ fontWeight: 600, fontSize: '1rem', minWidth: 0, flex: { xs: '1 1 auto', md: '0 1 auto' }, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{quizTitle}</Typography>
                <Typography component="p" className="cm-tabular" sx={{ color: color.chromeMuted, flex: 1, textAlign: 'center', display: { xs: 'none', md: 'block' } }}>Question {currentIndex + 1} of {questions.length}</Typography>
                <QuizTimer remainingSeconds={remainingSeconds} totalSeconds={totalSeconds} />
                <Button variant="contained" size="small" onClick={() => setDialog('submit')} disabled={locked} sx={{ bgcolor: color.surface, color: color.ink950, '&:hover': { bgcolor: color.fill150 } }}>Submit quiz</Button>
            </Box>
            <Box sx={{ display: { md: 'none' }, px: 1.5, pb: 1.5, bgcolor: color.canvas100, borderRadius: `${radius.lg}px ${radius.lg}px 0 0`, pt: 1.5, color: color.ink900 }}>{navigator}</Box>
        </Box>
        <Box component="main" id="main-content" tabIndex={-1} sx={{ flex: 1, bgcolor: color.canvas100, mx: { md: 1 }, mb: { md: 1 }, borderRadius: { md: `${radius.xl}px` }, display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) 300px' } }}>
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
            <Box component="aside" aria-label="Question navigator" sx={{ display: { xs: 'none', md: 'block' }, bgcolor: color.fill150, px: 3, py: 6, borderRadius: { md: `0 ${radius.xl}px ${radius.xl}px 0` } }}>
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
