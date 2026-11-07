import { useEffect, useRef, useState } from 'react';
import type { ComponentProps, Dispatch, ReactNode, SetStateAction } from 'react';
import { Box, Button, Typography, useMediaQuery, useTheme } from '@mui/material';
import { SegmentedControl } from '../../shared/components/ViewRail';
import type { MasteryState } from '../../shared/components/StatusPill';
import { GuidanceEntry } from '../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../shared/components/GuidanceStack';
import type { UploadState } from '../../shared/components/FileUploadField';
import { LearnerHomePage } from '../../features/learning/pages/LearnerHomePage';
import { MembershipCourseTile, MembershipTileMenu } from '../../features/learning/pages/MyCoursesPage';
import { PageHeader } from '../../shared/components/PageHeader';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { EmptyView } from '../../shared/components/StatusViews';
import { TableToolbar } from '../../shared/components/DataTable';
import { Page } from '../../layouts/DashboardLayout';
import { CourseLearningPage } from '../../features/learning/pages/CourseLearningPage';
import { LessonPage } from '../../features/learning/pages/LessonPage';
import type { OutlineChapterView } from '../../features/learning/types/learningTypes';
import { MasteryMapPage } from '../../features/mastery/pages/MasteryMapPage';
import type { MasteryView } from '../../features/mastery/pages/MasteryMapPage';
import { PlacementPrompt } from '../../features/mastery/components/PlacementPrompt';
import { recommendationEntry } from '../../features/mastery/components/NextRecommendedCard';
import type { RecommendationView, SkillView } from '../../features/mastery/types/masteryTypes';
import { CourseAssessmentsPage } from '../../features/assessment/pages/CourseAssessmentsPage';
import type { AssessmentFilter } from '../../features/assessment/pages/CourseAssessmentsPage';
import { QuizAttemptPage, QuizIntroView } from '../../features/assessment/pages/QuizAttemptPage';
import { QuizResultPage } from '../../features/assessment/pages/QuizResultPage';
import { AttemptHistoryPage } from '../../features/assessment/pages/AttemptHistoryPage';
import { AssignmentDetailPage } from '../../features/assessment/pages/AssignmentDetailPage';
import { quizKindLabel } from '../../features/assessment/types/assessmentTypes';
import type { AttemptView, QuestionView, QuizSummaryView, ReviewItemView, SubmissionView } from '../../features/assessment/types/assessmentTypes';
import { NotificationsPage } from '../../features/interaction/pages/NotificationsPage';
import { DiscussionPanel } from '../../features/interaction/components/DiscussionPanel';
import { ReportContentDialog } from '../../features/interaction/components/ReportContentDialog';
import type { DiscussionPostView, NotificationView } from '../../features/interaction/types/interactionTypes';
import * as fx from './fixtures';
import { ContractNote, later, nowLabel, usePreview } from './previewKit';

export interface LearnerState {
    chapters: OutlineChapterView[];
    skills: SkillView[];
    recommendation: RecommendationView;
    notifications: NotificationView[];
    posts: DiscussionPostView[];
    diagnosticOffered: boolean;
    attempts: AttemptView[];
    submissions: Record<string, SubmissionView | undefined>;
    memberships: fx.SampleMembership[];
    lastResult?: { quiz: QuizSummaryView; review: ReviewItemView[]; score: number; correct: number; attemptNumber: number; diagnostic: boolean };
}
export const initialLearnerState = (): LearnerState => structuredClone({
    chapters: fx.learnerChapters, skills: fx.learnerSkills, recommendation: fx.sampleRecommendation, notifications: fx.notifications, posts: fx.discussionPosts,
    diagnosticOffered: true, attempts: fx.loopsAttempts, submissions: { 'as-vars': fx.varsSubmission }, memberships: fx.learnerMemberships,
});

type Go = (screen: string, extra?: Record<string, string | undefined>) => void;
interface Props { state: LearnerState; setState: Dispatch<SetStateAction<LearnerState>>; screen: string; item?: string; go: Go }

const lessons = (state: LearnerState) => state.chapters.flatMap((chapter) => chapter.lessons.map((lesson) => ({ ...lesson, chapter })));
const skillCounts = (skills: SkillView[]) => skills.reduce((counts, skill) => ({ ...counts, [skill.state]: counts[skill.state] + 1 }), { unknown: 0, learning: 0, mastered: 0, weak: 0 } as Record<MasteryState, number>);
const progressOf = (state: LearnerState) => {
    const all = lessons(state), completed = all.filter((lesson) => lesson.progress !== 'not-started').length;
    return { completed, total: all.length, percent: Math.round((completed / all.length) * 100) };
};

/** Learner workspace preview: every interaction changes sample state only. */
export function LearnerPreview({ state, setState, screen, item, go }: Props) {
    const { notify } = usePreview();
    const openLesson = (id: string) => go('lesson', { item: id });
    const rec = recommendationEntry(state.recommendation, openLesson);
    const guidance: GuidanceEntryData[] = [rec,
        { id: 'g-deadline', tone: 'butter', icon: 'calendar', type: 'Deadline', meta: 'Fri 13 Nov, 23:59', body: <Typography>Trace and fix three short programs</Typography>, action: { label: 'Open assignment', onClick: () => go('assignment', { item: 'as-trace' }) } },
        { id: 'g-result', tone: 'mint', icon: 'check-circle', type: 'Result available', meta: 'Thu 5 Nov', body: <Typography>Loops practice quiz · Attempt 2</Typography>, action: { label: 'View result', onClick: () => go('quiz-result') } }];
    const sectionNav = (value: 'course' | 'mastery' | 'assessments') => <SegmentedControl label="Course sections" value={value} onChange={(next) => go(next)}
        options={[{ value: 'course', label: 'Path', icon: 'path' }, { value: 'mastery', label: 'Mastery Map', icon: 'map' }, { value: 'assessments', label: 'Assessments', icon: 'quiz' }]} />;
    const back = (label: string, target: string, extra?: Record<string, string>) => ({ label, href: '#', onClick: () => go(target, extra) });

    if (screen === 'learner-home') return <LearnerHomePage learnerName={fx.sampleLearner.name} guidance={guidance} onBrowse={() => notify('In production this opens the live catalogue at /courses.')}
        continueLesson={{ courseTitle: fx.COURSE_TITLE, chapterTitle: 'Repetition', lessonTitle: 'For Loops', position: 7, onResume: () => openLesson('l7') }}
        courses={[{ id: 'c-intro', title: fx.COURSE_TITLE, description: fx.COURSE_DESCRIPTIONS['c-intro'], progress: progressOf(state), nextLessonTitle: 'For Loops', onOpen: () => go('course') },
            { id: 'c-practice', title: 'Programming Practice', description: fx.COURSE_DESCRIPTIONS['c-practice'], progress: { completed: 1, total: 4, percent: 25 }, nextLessonTitle: 'Week 2 exercises', onOpen: () => go('course') }]} />;

    if (screen === 'my-courses') return <MyCoursesScreen state={state} setState={setState} go={go} />;

    if (screen === 'course') return <CoursePath state={state} setState={setState} go={go} guidance={guidance} sectionNav={sectionNav('course')} />;

    if (screen === 'lesson') {
        const all = lessons(state), index = Math.max(0, all.findIndex((lesson) => lesson.id === (item ?? 'l7'))), lesson = all[index];
        const gap = lesson.markers?.includes('prerequisite-gap');
        return <LessonScreen key={lesson.id} state={state} setState={setState} lessonId={lesson.id} go={go}
            prerequisiteWarning={gap ? { skillName: lesson.id === 'l8' ? 'For loops' : 'Conditionals', reviewLessonTitle: lesson.id === 'l8' ? 'For Loops' : 'If Statements', onReview: () => openLesson(lesson.id === 'l8' ? 'l7' : 'l5') } : undefined}
            remedialReason={lesson.id === state.recommendation.targetLessonId ? state.recommendation.reason : undefined}
            recommendation={lesson.id === state.recommendation.targetLessonId ? undefined : rec}
            onPrevious={index > 0 ? () => openLesson(all[index - 1].id) : undefined} onNext={index < all.length - 1 ? () => openLesson(all[index + 1].id) : undefined}
            back={back(fx.COURSE_TITLE, 'course')} />;
    }

    if (screen === 'mastery') return <MasteryScreen state={state} go={go} sectionNav={sectionNav('mastery')} />;

    if (screen === 'assessments') return <AssessmentsScreen go={go} sectionNav={sectionNav('assessments')} />;

    if (screen === 'diagnostic') return <QuizIntroView quiz={fx.diagnosticQuiz} title="Placement diagnostic" back={back(fx.COURSE_TITLE, 'course')} onStart={() => go('quiz-attempt', { item: 'diagnostic' })}
        note={<Box sx={{ mt: 2 }}><Button variant="text" onClick={() => { setState((current) => ({ ...current, diagnosticOffered: false })); notify('Diagnostic skipped. You start on the default path.'); go('course'); }}>Skip and start from the beginning</Button></Box>}>
        <Typography sx={{ mb: 2 }}>The diagnostic checks these skills so you can skip what you already know:</Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 3 }}>{['Variables', 'Expressions', 'Conditionals', 'For loops'].map((name) => <Box key={name} component="span" sx={{ px: 1.5, py: .5, borderRadius: 999, bgcolor: 'action.hover', fontWeight: 600, fontSize: '0.9375rem' }}>{name}</Box>)}</Box>
    </QuizIntroView>;

    if (screen === 'quiz') return <QuizIntroView quiz={{ ...fx.loopsQuiz, nextAttemptNumber: state.attempts.length + 1 }} back={back('Assessments', 'assessments')} onStart={() => go('quiz-attempt')}
        note={<ContractNote>Sample quiz. In production the timer counts down to the expiry the server records when you start (Assessment contracts pending).</ContractNote>} />;

    if (screen === 'quiz-attempt') return <QuizAttemptController diagnostic={item === 'diagnostic'}
        onExit={() => go(item === 'diagnostic' ? 'course' : 'assessments')}
        onDone={(result) => { setState((current) => ({ ...current, lastResult: result, diagnosticOffered: result.diagnostic ? false : current.diagnosticOffered,
            attempts: result.diagnostic ? current.attempts : [...current.attempts, { id: `att-${current.attempts.length + 1}`, attemptNumber: result.attemptNumber, submittedAtLabel: nowLabel, score: result.score, correct: result.correct, total: result.review.length }] }));
            go('quiz-result'); }} nextAttempt={state.attempts.length + 1} />;

    if (screen === 'quiz-result') {
        const result = state.lastResult ?? { quiz: fx.loopsQuiz, score: 66.67, correct: 4, attemptNumber: 2, diagnostic: false,
            review: fx.loopsQuestions.map((question, index) => ({ questionId: question.id, position: question.position, prompt: question.prompt, code: question.code, correct: index !== 1 && index !== 2,
                selectedLabels: question.options.filter((option) => (index === 1 ? ['a', 'b'] : index === 2 ? ['t'] : fx.loopsAnswerKey[question.id]).includes(option.id)).map((option) => option.label),
                correctLabels: question.options.filter((option) => fx.loopsAnswerKey[question.id].includes(option.id)).map((option) => option.label) })) };
        return <QuizResultPage quizTitle={result.quiz.title} kindLabel={quizKindLabel[result.quiz.kind]} score={result.score} correct={result.correct} total={result.review.length}
            attemptNumber={result.attemptNumber} submittedAtLabel={state.lastResult ? nowLabel : 'Thu 5 Nov, 21:10'} review={result.review}
            skillChanges={result.diagnostic ? [{ skillId: 'variables', name: 'Variables', before: 'unknown', after: 'mastered' }, { skillId: 'conditionals', name: 'Conditionals', before: 'unknown', after: 'learning' }, { skillId: 'for-loops', name: 'For loops', before: 'unknown', after: 'weak' }] : fx.sampleSkillChanges}
            recommendation={<RecommendationBlock entry={rec} />} onBack={() => go('course')} onHistory={result.diagnostic ? undefined : () => go('attempt-history')} onOpenMap={() => go('mastery', { item: 'updated' })}
            back={back('Assessments', 'assessments')}
            notice={<ContractNote>Sample result calculated in your browser with the approved equal-weight rule (unanswered = 0) for display only. CodeMastery's server is the scoring authority; skill updates shown here are fixed sample data, not a calculation.</ContractNote>} />;
    }

    if (screen === 'attempt-history') return <AttemptHistoryPage quizTitle={fx.loopsQuiz.title} attempts={state.attempts} onView={() => go('quiz-result')} onStart={() => go('quiz')} back={back('Assessments', 'assessments')} />;

    if (screen === 'assignment') return <AssignmentScreen key={item} id={item ?? 'as-trace'} state={state} setState={setState} back={back('Assessments', 'assessments')} />;

    if (screen === 'notifications') return <NotificationsScreen state={state} setState={setState} go={go} />;
    return null;
}

type SampleFilter = 'joined' | 'active' | 'completed' | 'left' | 'all';
/** S5 with sample memberships: the live tile and overflow menu, with the course workspace as the tile destination. */
function MyCoursesScreen({ state, setState, go }: { state: LearnerState; setState: Props['setState']; go: Go }) {
    const { notify } = usePreview();
    const [filter, setFilter] = useState<SampleFilter>('joined'), [leaving, setLeaving] = useState<fx.SampleMembership | null>(null);
    const visible = state.memberships.filter((course) => filter === 'all' || (filter === 'joined' ? course.status !== 'left' : course.status === filter));
    const setStatus = (id: string, status: fx.SampleMembership['status']) => setState((current) => ({ ...current, memberships: current.memberships.map((course) => course.id === id ? { ...course, status } : course) }));
    const open = (course: fx.SampleMembership) => (course.id === 'c-intro' ? go('course') : notify(`${course.title} has no sample course workspace. In production the tile opens course details.`));
    return <Page>
        <PageHeader title="My courses" subtitle="Published courses you have joined. Your learning history stays here if you leave a course." />
        <ContractNote>The live page reads and changes membership through CT22.membership. Here the tile opens the sample course workspace, which waits for CT21 and CT22 progress.</ContractNote>
        <TableToolbar count={`${visible.length} ${visible.length === 1 ? 'course' : 'courses'}`}>
            <SegmentedControl label="Membership status" value={filter} onChange={setFilter}
                options={[{ value: 'joined', label: 'Joined' }, { value: 'active', label: 'Active' }, { value: 'completed', label: 'Completed' }, { value: 'left', label: 'Left' }, { value: 'all', label: 'All' }]} />
        </TableToolbar>
        {visible.length === 0
            ? <EmptyView icon="my-courses" title={filter === 'left' ? 'No courses left' : "You haven't joined a course yet"} message={filter === 'left' ? 'Courses you leave stay here so you can re-enroll.' : 'Browse the catalogue to find one.'} />
            : <Box component="ul" aria-label="Your courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))' } }}>
                {visible.map((course) => <Box component="li" key={course.id} sx={{ minWidth: 0 }}>
                    <MembershipCourseTile course={course} href={course.id === 'c-intro' ? '/preview?screen=course' : '#'} onOpen={() => open(course)}
                        menu={<MembershipTileMenu title={course.title} status={course.status} detailsHref="/preview?screen=my-courses"
                            onDetails={() => notify('In production this opens the live course detail page.')} onLeave={() => setLeaving(course)}
                            onReenroll={() => { setStatus(course.id, 'active'); notify(`You re-enrolled in ${course.title}.`); }} />} />
                </Box>)}
            </Box>}
        <ConfirmDialog open={!!leaving} title="Leave course?" message="Your learning history will be preserved. You can re-enroll while the course is published."
            confirmLabel="Leave course" isDestructive onCancel={() => setLeaving(null)}
            onConfirm={() => { if (leaving) { setStatus(leaving.id, 'left'); notify(`You left ${leaving.title}. It's listed under Left, where you can re-enroll.`); } setLeaving(null); }} />
    </Page>;
}

function RecommendationBlock({ entry }: { entry: GuidanceEntryData }) {
    return <Box sx={{ maxWidth: 520 }}><GuidanceEntry component="div" entry={entry} /></Box>;
}

function CoursePath({ state, setState, go, guidance, sectionNav }: { state: LearnerState; setState: Props['setState']; go: Go; guidance: GuidanceEntryData[]; sectionNav: ReactNode }) {
    const [view, setView] = useState<'path' | 'outline'>('path');
    const { notify } = usePreview();
    return <CourseLearningPage courseTitle={fx.COURSE_TITLE} chapters={state.chapters} progress={progressOf(state)} view={view} onViewChange={setView}
        onOpenLesson={(id) => go('lesson', { item: id })} onResume={() => go('lesson', { item: 'l7' })} hasResumePoint sectionNav={sectionNav} guidance={guidance}
        skillCounts={skillCounts(state.skills)} onOpenMap={() => go('mastery')} back={{ label: 'My courses', href: '/preview?screen=my-courses', onClick: () => go('my-courses') }}
        diagnostic={state.diagnosticOffered ? <PlacementPrompt onStart={() => go('diagnostic')} onSkip={() => { setState((current) => ({ ...current, diagnosticOffered: false })); notify('Diagnostic skipped. You start on the default path.'); }} /> : undefined} />;
}

function LessonScreen({ state, setState, lessonId, go, ...rest }: { state: LearnerState; setState: Props['setState']; lessonId: string; go: Go } & Pick<ComponentProps<typeof LessonPage>, 'prerequisiteWarning' | 'remedialReason' | 'recommendation' | 'onPrevious' | 'onNext' | 'back'>) {
    const { notify } = usePreview();
    const [completion, setCompletion] = useState<'idle' | 'saving' | 'error'>('idle'), [report, setReport] = useState<string | null>(null);
    const entry = lessons(state).find((lesson) => lesson.id === lessonId)!;
    const blocks = fx.lessonBlocks[lessonId] ?? [{ kind: 'paragraph' as const, text: entry.summary ?? '' }, { kind: 'note' as const, text: 'Sample lesson text. Full lesson content arrives with the approved lesson-content contract.' }];
    const markComplete = () => {
        setCompletion('saving');
        later(() => { setState((current) => ({ ...current, chapters: current.chapters.map((chapter) => ({ ...chapter, lessons: chapter.lessons.map((lesson) => lesson.id === lessonId ? { ...lesson, progress: 'completed' as const } : lesson) })) })); setCompletion('idle'); notify('Lesson marked as complete (sample).'); });
    };
    return <>
        <LessonPage courseTitle={fx.COURSE_TITLE} chapterTitle={entry.chapter.title} chapters={state.chapters} onOpenLesson={(id) => go('lesson', { item: id })} onMarkComplete={markComplete} completion={completion}
            onDownload={() => notify('File download is pending the storage contract (CT09).')}
            lesson={{ id: entry.id, title: entry.title, position: entry.position, progress: entry.progress, markers: entry.markers, blocks, materials: fx.lessonMaterials[lessonId] ?? [], skills: entry.skills ?? [],
                video: lessonId === 'l7' ? { title: 'Loops walkthrough (external link)', url: 'https://example.com/video/loops' } : undefined }}
            discussion={<DiscussionPanel lessonTitle={entry.title} posts={lessonId === 'l7' ? state.posts : []} canReply={false}
                onAsk={(text) => { setState((current) => ({ ...current, posts: [...current.posts, { id: `p${Date.now()}`, author: fx.sampleLearner.name, role: 'Learner', createdAtLabel: nowLabel, body: text, replies: [] }] })); notify('Question posted (sample).'); }}
                onReport={setReport} />}
            {...rest} />
        <ReportContentDialog open={!!report} targetLabel="this post" onCancel={() => setReport(null)} onSubmit={() => { setReport(null); notify('Thanks. An administrator will review this post.'); }} />
    </>;
}

function MasteryScreen({ state, go, sectionNav }: { state: LearnerState; go: Go; sectionNav: ReactNode }) {
    const narrow = useMediaQuery(useTheme().breakpoints.down('md'));
    const [view, setView] = useState<MasteryView>(narrow ? 'list' : 'graph'), [selected, setSelected] = useState<string | null>('for-loops');
    const [updated, setUpdated] = useState<string[]>(() => (new URLSearchParams(window.location.search).get('item') === 'updated' ? ['for-loops', 'while-loops'] : []));
    useEffect(() => { if (!updated.length) return; const timer = window.setTimeout(() => setUpdated([]), 6000); return () => window.clearTimeout(timer); }, [updated]);
    return <MasteryMapPage courseTitle={fx.COURSE_TITLE} skills={state.skills} view={view} onViewChange={setView} selectedId={selected} onSelect={setSelected} recommendation={state.recommendation}
        updatedIds={updated} onOpenLesson={(id) => go('lesson', { item: id })} sectionNav={sectionNav} back={{ label: fx.COURSE_TITLE, onClick: () => go('course') }}
        notice={<ContractNote>Sample skill states. In production the map shows the states the Adaptive module returns for you; thresholds and calculations are never shown or computed here.</ContractNote>} />;
}

function AssessmentsScreen({ go, sectionNav }: { go: Go; sectionNav: ReactNode }) {
    const [filter, setFilter] = useState<AssessmentFilter>('all');
    return <CourseAssessmentsPage courseTitle={fx.COURSE_TITLE} items={fx.assessmentItems} filter={filter} onFilterChange={setFilter} sectionNav={sectionNav} back={{ label: fx.COURSE_TITLE, href: '#', onClick: () => go('course') }}
        onOpen={(entry) => entry.type === 'assignment' ? go('assignment', { item: entry.id }) : entry.id === 'q-diag' ? go('diagnostic') : go('quiz')} />;
}

/** Local attempt state machine. Preview timer only; production counts down to the server's expiry. */
function QuizAttemptController({ diagnostic, onExit, onDone, nextAttempt }: { diagnostic: boolean; onExit: () => void; onDone: (result: NonNullable<LearnerState['lastResult']>) => void; nextAttempt: number }) {
    const quiz = diagnostic ? fx.diagnosticQuiz : { ...fx.loopsQuiz, nextAttemptNumber: nextAttempt };
    const questions: QuestionView[] = diagnostic ? fx.loopsQuestions.slice(0, 4) : fx.loopsQuestions;
    const [answers, setAnswers] = useState<Record<string, string[]>>({}), [index, setIndex] = useState(0), [remaining, setRemaining] = useState(quiz.timeLimitSeconds);
    const [phase, setPhase] = useState<'answering' | 'submitting' | 'expired'>('answering');
    const answersRef = useRef(answers); answersRef.current = answers;
    const finish = (expired: boolean) => {
        setPhase(expired ? 'expired' : 'submitting');
        later(() => {
            // Approved initial grading baseline (data-model §11): equal weight, exact selection match, unanswered = 0,
            // 0–100 rounded half-up to two decimals. Display only; the server is the scoring authority.
            const review = questions.map((question) => {
                const selected = answersRef.current[question.id] ?? [], key = fx.loopsAnswerKey[question.id];
                const correct = selected.length === key.length && key.every((id) => selected.includes(id));
                return { questionId: question.id, position: question.position, prompt: question.prompt, code: question.code, correct,
                    selectedLabels: question.options.filter((option) => selected.includes(option.id)).map((option) => option.label),
                    correctLabels: question.options.filter((option) => key.includes(option.id)).map((option) => option.label) };
            });
            const correct = review.filter((entry) => entry.correct).length;
            onDone({ quiz, review, correct, score: Math.round((correct / review.length) * 10000) / 100, attemptNumber: quiz.nextAttemptNumber, diagnostic });
        }, expired ? 1400 : 800);
    };
    useEffect(() => {
        if (phase !== 'answering') return;
        const timer = window.setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000);
        return () => window.clearInterval(timer);
    }, [phase]);
    useEffect(() => { if (remaining === 0 && phase === 'answering') finish(true); }); // eslint-disable-line react-hooks/exhaustive-deps
    return <QuizAttemptPage quizTitle={quiz.title} questions={questions} answers={answers} currentIndex={index} onAnswer={(id, ids) => setAnswers((current) => ({ ...current, [id]: ids }))}
        onNavigate={(next) => setIndex(Math.max(0, Math.min(questions.length - 1, next)))} remainingSeconds={remaining} totalSeconds={quiz.timeLimitSeconds} phase={phase}
        onSubmit={() => finish(false)} onExit={onExit} guardUnload />;
}

function AssignmentScreen({ id, state, setState, back }: { id: string; state: LearnerState; setState: Props['setState']; back: { label: string; href: string; onClick: () => void } }) {
    const [upload, setUpload] = useState<UploadState>({ kind: 'idle' });
    const assignment = fx.assignments[id] ?? fx.assignments['as-trace'];
    return <AssignmentDetailPage assignment={assignment} submission={state.submissions[assignment.id]} upload={upload} back={back}
        notice={<ContractNote>Sample upload: the file stays in your browser. Accepted file types, size limits and storage are defined by the pending file-storage contract (CT09).</ContractNote>}
        onSelectFile={(file) => { setUpload({ kind: 'uploading', fileName: file.name }); later(() => { setUpload({ kind: 'accepted', fileName: file.name, detail: 'Sample: not uploaded.' }); setState((current) => ({ ...current, submissions: { ...current.submissions, [assignment.id]: { fileName: file.name, submittedAtLabel: nowLabel } } })); }, 900); }} />;
}

function NotificationsScreen({ state, setState, go }: { state: LearnerState; setState: Props['setState']; go: Go }) {
    const [filter, setFilter] = useState<'all' | 'unread'>('all');
    const targets: Record<NotificationView['kind'], () => void> = { recommendation: () => go('lesson', { item: 'l5' }), result: () => go('quiz-result'), deadline: () => go('assignment', { item: 'as-trace' }), reply: () => go('lesson', { item: 'l7' }), content: () => go('course') };
    return <NotificationsPage notifications={state.notifications} filter={filter} onFilterChange={setFilter}
        onMarkAllRead={() => setState((current) => ({ ...current, notifications: current.notifications.map((entry) => ({ ...entry, read: true })) }))}
        onOpen={(id) => { const entry = state.notifications.find((item) => item.id === id)!; setState((current) => ({ ...current, notifications: current.notifications.map((item) => item.id === id ? { ...item, read: true } : item) })); targets[entry.kind](); }} />;
}

