import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Drawer, IconButton, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { MasteryTag } from '../../../shared/components/StatusPill';
import { GuidanceEntry } from '../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../shared/components/GuidanceStack';
import { CourseOutline, LessonProgressPill, MarkerTag } from '../components/CourseOutline';
import { LessonContent, MaterialList } from '../components/LessonContent';
import type { LessonBlock, LessonMaterialView, LessonProgressState, OutlineChapterView, SkillTagView, LessonAdaptiveMarker, LearnerLesson } from '../types/learningTypes';
import { control, presentationTokens, radius, shadow, roles } from '../../../app/theme/theme';
import { getLearnerLesson } from '../api/learningApi';
import { useCourseLearning } from '../hooks/useCourseLearning';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../../shared/types/apiTypes';
export interface PrerequisiteWarningView { skillName: string; reviewLessonTitle?: string; onReview?: () => void }
export interface LessonPageProps {
    courseTitle: string;
    chapterTitle: string;
    lesson: { id: string; title: string; position: number; progress: LessonProgressState; markers?: LessonAdaptiveMarker[]; blocks: LessonBlock[]; materials: LessonMaterialView[]; skills: SkillTagView[]; video?: { title: string; url: string } };
    chapters: OutlineChapterView[];
    onOpenLesson: (lessonId: string) => void;
    onMarkComplete: () => void;
    completion: 'idle' | 'saving' | 'error';
    onPrevious?: () => void;
    onNext?: () => void;
    hasPrevious?: boolean;
    hasNext?: boolean;
    completing?: boolean;
    completionError?: string | null;
    /** US-06: an unmastered prerequisite warns but never blocks. */
    prerequisiteWarning?: PrerequisiteWarningView;
    /** Shown when this lesson is the target of a remedial recommendation. */
    remedialReason?: string;
    recommendation?: GuidanceEntryData;
    onDownload?: (materialId: string) => void;
    /** Lesson Q&A (Interaction-owned), composed by the caller. */
    discussion?: ReactNode;
    back?: Crumb;
}

/** S8 Lesson reader: outline, reading column, lesson aside and a sticky completion bar. */
export function LessonPage({ courseTitle, chapterTitle, lesson, chapters, onOpenLesson, onMarkComplete, completion, onPrevious, onNext, prerequisiteWarning, remedialReason, recommendation, onDownload, discussion, back }: LessonPageProps) {
    const theme = useTheme();
    const wide = useMediaQuery(theme.breakpoints.up('xl'));
    const [outlineOpen, setOutlineOpen] = useState(false);
    const done = lesson.progress !== 'not-started';
    const outline = <CourseOutline compact chapters={chapters} view="outline" currentLessonId={lesson.id} onOpenLesson={(id) => { setOutlineOpen(false); onOpenLesson(id); }} />;
    const aside = <Box sx={{ display: 'grid', gap: 3 }}>
        <Box component="section" aria-labelledby="lesson-skills">
            <Typography id="lesson-skills" component="h2" variant="h4" sx={{ mb: 1.5 }}>Skills in this lesson</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                {lesson.skills.map((skill) => <Box component="li" key={skill.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>{skill.name}</Typography><MasteryTag state={skill.state} />
                </Box>)}
            </Box>
            <Typography variant="caption" sx={{ display: 'block', mt: 1.5, color: 'text.secondary' }}>Completing a lesson doesn't change skill mastery. Quizzes do.</Typography>
        </Box>
        {recommendation && <Box><Typography component="h2" variant="h4" sx={{ mb: 1.5 }}>Recommended next</Typography><GuidanceEntry component="div" entry={recommendation} /></Box>}
        <Box component="section" aria-labelledby="lesson-materials">
            <Typography id="lesson-materials" component="h2" variant="h4" sx={{ mb: 1.5 }}>Materials</Typography>
            <MaterialList materials={lesson.materials} onDownload={onDownload} />
        </Box>
    </Box>;
    return <Page aside={aside} asideLabel="Lesson details" stickyAside>
        <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: 'minmax(0, 1fr)', xl: `${presentationTokens.outlineWidth}px minmax(0, 1fr)` }, alignItems: 'start' }}>
            {wide && <Box sx={{ position: 'sticky', top: 104, maxHeight: 'calc(100dvh - 140px)', overflowY: 'auto', pr: 1 }}>{outline}</Box>}
            <Box component="article" aria-labelledby="lesson-title" sx={{ minWidth: 0, maxWidth: 720 }}>
                <PageHeader title={lesson.title} titleId="lesson-title" back={back}
                    meta={<><Typography variant="body2" sx={{ color: 'text.secondary' }}>{courseTitle} · {chapterTitle} · Lesson {lesson.position}</Typography>
                        <LessonProgressPill progress={lesson.progress} />{lesson.markers?.map((marker) => <MarkerTag key={marker} marker={marker} />)}</>}
                    actions={!wide && <Button variant="outlined" onClick={() => setOutlineOpen(true)} startIcon={<PresentationIcon name="list" />}>Course outline</Button>} />
                {remedialReason && <Alert severity="info" icon={<PresentationIcon name="route" />} sx={{ mb: 3, bgcolor: roles.tone.lilac.tint, '& .MuiAlert-icon': { color: roles.tone.lilac.strong } }}>
                    <strong>Recommended review.</strong> {remedialReason}
                </Alert>}
                {prerequisiteWarning && <Alert severity="warning" sx={{ mb: 3 }} action={prerequisiteWarning.onReview && prerequisiteWarning.reviewLessonTitle
                    ? <Button variant="text" size="small" color="inherit" onClick={prerequisiteWarning.onReview}>Review {prerequisiteWarning.reviewLessonTitle}</Button> : undefined}>
                    You haven't mastered {prerequisiteWarning.skillName} yet. This lesson builds on it. You can continue anyway.
                </Alert>}
                <LessonContent blocks={lesson.blocks} />
                {lesson.video && <Box component="figure" sx={{ m: 0, my: 4 }}>
                    <Box sx={{ aspectRatio: '16 / 9', bgcolor: roles.bg.frame, borderRadius: `${radius.md}px`, display: 'grid', placeItems: 'center', color: roles.text.onChrome, textAlign: 'center', p: 3 }}>
                        <Box><PresentationIcon name="video" sx={{ fontSize: 32, mb: 1 }} /><Typography>External video embed</Typography>
                            <Typography variant="caption" sx={{ color: roles.chrome.muted }}>Videos play from the instructor's external link. CodeMastery doesn't host video.</Typography></Box>
                    </Box>
                    <Typography component="figcaption" variant="caption" sx={{ display: 'block', mt: 1, color: 'text.secondary' }}>
                        {lesson.video.title} · <Box component="a" href={lesson.video.url} target="_blank" rel="noreferrer noopener" sx={{ color: roles.accent.link }}>Open video in a new tab</Box>
                    </Typography>
                </Box>}
                {discussion && <Box sx={{ mt: 6 }}>{discussion}</Box>}
                <Box role="region" aria-label="Lesson actions" sx={{
                    position: 'sticky', bottom: { xs: 8, sm: 16 }, zIndex: 5, mt: 6, maxWidth: 720, bgcolor: roles.bg.raised, borderRadius: `${radius.pill}px`,
                    boxShadow: `${shadow.raised}, inset 0 0 0 1px ${roles.line.floating}`, p: 1, display: 'flex', alignItems: 'center', gap: 1
                }}>
                    <IconButton aria-label="Previous lesson" onClick={onPrevious} disabled={!onPrevious} sx={control.secondary}><PresentationIcon name="arrow-left" /></IconButton>
                    <Box sx={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                        {completion === 'error' && <Typography role="alert" variant="body2" sx={{ color: roles.tone.rose.strong, display: { xs: 'none', sm: 'block' } }}>Couldn't save. Try again.</Typography>}
                        {done ? <Typography role="status" sx={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: 1 }}><PresentationIcon name="check-circle" sx={{ color: roles.tone.mint.strong }} />{lesson.progress === 'completed' ? 'Lesson completed' : 'Counted as complete'}</Typography>
                            : <Button variant="contained" onClick={onMarkComplete} disabled={completion === 'saving'} aria-busy={completion === 'saving' || undefined}>{completion === 'saving' ? 'Saving…' : completion === 'error' ? 'Try again' : 'Mark as complete'}</Button>}
                    </Box>
                    {onNext ? <Button variant={done ? 'contained' : 'outlined'} onClick={onNext} endIcon={<PresentationIcon name="arrow-right" />} sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>Next lesson</Button> : null}
                    <IconButton aria-label="Next lesson" onClick={onNext} disabled={!onNext} sx={{ ...control.secondary, display: { sm: 'none' } }}><PresentationIcon name="arrow-right" /></IconButton>
                </Box>
            </Box>
        </Box>
        <Drawer anchor="left" open={outlineOpen && !wide} onClose={() => setOutlineOpen(false)}
            slotProps={{ paper: { sx: { bgcolor: roles.bg.canvas, color: roles.text.primary, width: 'min(340px, calc(100vw - 48px))', p: 2.5, borderRadius: '0 24px 24px 0' } } }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant="h4" component="p">Course outline</Typography>
                <IconButton aria-label="Close course outline" onClick={() => setOutlineOpen(false)}><PresentationIcon name="close" /></IconButton>
            </Box>
            {outline}
        </Drawer>
    </Page>;
}

/** Live S8 Lesson Reader: renders CT21 lesson, records silent post-render visit, handles monotonic completion. */
export function LiveLessonPage({ courseId, lessonId }: { courseId: string; lessonId: string }) {
    const navigate = useNavigate();
    const { course, progress, loading: workspaceLoading, errorStatus: workspaceErrorStatus, errorMessage: workspaceErrorMessage, completing, completionError, refresh, markComplete, recordVisit } = useCourseLearning(courseId);

    const [lesson, setLesson] = useState<LearnerLesson | null>(null);
    const [lessonLoading, setLessonLoading] = useState(true);
    const [lessonErrorStatus, setLessonErrorStatus] = useState<number | null>(null);
    const [lessonErrorMessage, setLessonErrorMessage] = useState<string | null>(null);
    const visitedRef = useRef<string | null>(null);

    useEffect(() => {
        let alive = true;
        const controller = new AbortController();
        setLessonLoading(true); setLessonErrorStatus(null); setLessonErrorMessage(null);

        getLearnerLesson(courseId, lessonId, { signal: controller.signal })
            .then((data) => {
                if (!alive) return;
                setLesson(data);
                if (visitedRef.current !== data.id) {
                    visitedRef.current = data.id;
                    void recordVisit(data.id);
                }
            })
            .catch((err: unknown) => {
                if (!alive || (err instanceof ApiError && err.kind === 'cancelled')) return;
                if (err instanceof ApiError) { setLessonErrorStatus(err.status ?? null); setLessonErrorMessage(err.message); }
                else { setLessonErrorMessage("Couldn't load this lesson."); }
            })
            .finally(() => { if (alive) setLessonLoading(false); });

        return () => { alive = false; controller.abort(); };
    }, [courseId, lessonId, recordVisit]);

    if (workspaceLoading || lessonLoading) return <Page><LoadingView message="Loading lesson…" /></Page>;

    const errStatus = lessonErrorStatus ?? workspaceErrorStatus;
    if (errStatus === 403) return <Page><SystemStateView icon="lock" title="Rejoin the course to continue" message="Your membership is required to access this lesson." actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">View course details</Button>} /></Page>;
    if (errStatus === 404) return <Page><SystemStateView icon="search" title="Lesson not available" message="It may have been hidden by the instructor." actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">Back to course</Button>} /></Page>;
    if (lessonErrorMessage || workspaceErrorMessage || !lesson || !course || !progress) {
        return <Page><ErrorView title="Couldn't load lesson" message={lessonErrorMessage ?? workspaceErrorMessage ?? 'Please try again.'} onRetry={refresh} /></Page>;
    }

    const progressByLesson = new Map(progress.lessons.map((l) => [l.lessonId, l]));
    const flatLessons: { id: string; position: number; chapterTitle: string; title: string }[] = [];
    const chapters: OutlineChapterView[] = course.syllabus.slice().sort((a, b) => a.position - b.position).map((ch) => ({
        id: ch.id, title: ch.title, position: ch.position,
        lessons: ch.lessons.slice().sort((a, b) => a.position - b.position).map((l) => {
            flatLessons.push({ id: l.id, position: l.position, chapterTitle: ch.title, title: l.title });
            const entry = progressByLesson.get(l.id);
            return { id: l.id, title: l.title, position: l.position, progress: entry?.masterySkipped ? 'mastered-skip' : entry?.completed ? 'completed' : 'not-started' };
        }),
    }));

    const currentIndex = flatLessons.findIndex((l) => l.id === lesson.id);
    const currentMeta = currentIndex >= 0 ? flatLessons[currentIndex] : { position: 1, chapterTitle: '', title: lesson.title };
    const prevLesson = currentIndex > 0 ? flatLessons[currentIndex - 1] : null;
    const nextLesson = currentIndex >= 0 && currentIndex < flatLessons.length - 1 ? flatLessons[currentIndex + 1] : null;
    const currentProgress = progressByLesson.get(lesson.id);

    const goToLesson = (id: string) => navigate(`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(id)}`);
    const relationalMaterials = lesson.materials.map(m => m.materialType === 'file' ? { id: m.id, kind: 'file' as const, name: m.title, description: 'PDF file' } : m.materialType === 'external_video' ? { id: m.id, kind: 'external_video' as const, title: m.title, url: m.externalVideoUrl } : { id: m.id, kind: 'file' as const, name: m.title, description: 'Text material' });

    return <LessonPage
        courseTitle={course.title} chapterTitle={currentMeta.chapterTitle}
        lesson={{ id: lesson.id, title: lesson.title, position: currentMeta.position, progress: currentProgress?.masterySkipped ? 'mastered-skip' : currentProgress?.completed ? 'completed' : 'not-started', blocks: [], materials: relationalMaterials, skills: [] }}
        chapters={chapters} hasPrevious={Boolean(prevLesson)} hasNext={Boolean(nextLesson)}
        onPrevious={() => { if (prevLesson) goToLesson(prevLesson.id); }}
        onNext={() => { if (nextLesson) goToLesson(nextLesson.id); }}
        onMarkComplete={() => void markComplete(lesson.id)}
        completion={completing ? 'saving' : completionError ? 'error' : 'idle'}
        onOpenLesson={goToLesson}
        back={{ label: course.title, href: `/courses/${encodeURIComponent(courseId)}` }}
    />;
}