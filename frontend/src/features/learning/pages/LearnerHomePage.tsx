import { useEffect, useState } from 'react';
import { Box, Typography, useMediaQuery, useTheme } from '@mui/material';
import { useNavigate } from 'react-router';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { GuidanceStack, GuidanceEntry } from '../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../shared/components/GuidanceStack';
import { ProgressLine, Tile, TileTitleLink, CircleAffordance } from '../../../shared/components/Tile';
import { CourseMark } from '../../../shared/components/CourseMark';
import { StatusPill } from '../../../shared/components/StatusPill';
import { EmptyView, ErrorView, LoadingView } from '../../../shared/components/StatusViews';
import { ApiError } from '../../../shared/types/apiTypes';
import { useAuth } from '../../auth/hooks/useAuth';
import { getMyEnrolledCourses } from '../api/learningApi';
import type { MembershipPage } from '../types/learningTypes';
import type { CourseProgressView } from '../types/learningTypes';
import { color, radius } from '../../../app/theme/theme';

export interface ContinueLessonView { courseTitle: string; chapterTitle: string; lessonTitle: string; position: number; onResume: () => void }
export interface HomeCourseView {
    id: string;
    title: string;
    /** Read by the course mark (the course's own text names its language). */
    description?: string;
    /** Course progress (CT22 progress, pending). Without it the row shows `detail` instead. */
    progress?: CourseProgressView;
    nextLessonTitle?: string;
    /** Secondary line when no progress is available, for example the instructor. */
    detail?: string;
    href?: string;
    onOpen: () => void;
}
export interface LearnerHomePageProps {
    learnerName: string;
    subtitle?: string;
    /** Resume target (CT22 resume, pending). The section is omitted unless `showContinue` is set. */
    continueLesson?: ContinueLessonView;
    showContinue?: boolean;
    courses: HomeCourseView[];
    /** Up next entries (Adaptive/Assessment/Interaction, pending). The aside is omitted when absent. */
    guidance?: GuidanceEntryData[];
    onBrowse: () => void;
    /** Shown under the list when more active courses exist than are listed. */
    moreCourses?: { label: string; href: string; onOpen: () => void };
    /** Replaces the course list while it loads or fails. */
    coursesState?: { loading?: boolean; error?: string | null; onRetry?: () => void };
}

/** S6 Learner home: the next step across active courses. Sections whose contract is pending are omitted. No streaks or time charts. */
export function LearnerHomePage({ learnerName, subtitle = 'Pick up where you left off, or follow your next recommendation.', continueLesson, showContinue = !!continueLesson,
    courses, guidance = [], onBrowse, moreCourses, coursesState }: LearnerHomePageProps) {
    const firstRecommendation = guidance.find((entry) => entry.tone === 'lilac');
    // Below 1200 px the aside stacks under the main column, so the recommendation moves to the top instead of appearing twice.
    const stacked = !useMediaQuery(useTheme().breakpoints.up('lg'));
    const promoted = stacked ? firstRecommendation : undefined;
    const aside = guidance.length > 0 ? <GuidanceStack title="Up next" entries={promoted ? guidance.filter((entry) => entry !== promoted) : guidance} /> : undefined;
    return <Page aside={aside} asideLabel="Up next">
        <PageHeader title={`Welcome back, ${learnerName}`} subtitle={subtitle} />
        {promoted && <Box sx={{ mb: 4 }}><GuidanceEntry component="div" entry={promoted} /></Box>}
        {showContinue && <Box component="section" aria-labelledby="home-continue" sx={{ mb: 6 }}>
            <Typography id="home-continue" variant="h2" sx={{ mb: 2 }}>Continue learning</Typography>
            {continueLesson ? <Tile interactive component="article" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 1.5 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{continueLesson.courseTitle} · {continueLesson.chapterTitle} · Lesson {continueLesson.position}</Typography>
                <TileTitleLink component="h3" onClick={continueLesson.onResume}>{continueLesson.lessonTitle}</TileTitleLink>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><Box sx={{ flex: 1 }}><StatusPill tone="sky" icon="play" size="tag">Resume</StatusPill></Box><CircleAffordance icon="play" emphasis="primary" /></Box>
            </Tile> : <EmptyView compact icon="book" headingLevel="h3" title="Nothing in progress" message="Open a course to start its first lesson." />}
        </Box>}
        <Box component="section" aria-labelledby="home-courses">
            <Typography id="home-courses" variant="h2" sx={{ mb: 2 }}>Your courses</Typography>
            {coursesState?.loading ? <LoadingView message="Loading your courses…" variant="rows" count={2} />
                : coursesState?.error ? <ErrorView headingLevel="h3" title="Couldn't load your courses" message={coursesState.error} onRetry={coursesState.onRetry} />
                : courses.length === 0 ? <EmptyView icon="catalogue" headingLevel="h3" title="You haven't joined a course yet" message="Browse the catalogue to find one." /> :
                <Box component="ul" aria-label="Your active courses" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {courses.map((course) => <Box component="li" key={course.id} sx={{ position: 'relative', p: { xs: 2.5, sm: 3 }, borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 },
                        display: 'grid', columnGap: 2, rowGap: 1.5, alignItems: 'center', '&:hover': { bgcolor: color.canvas100 },
                        gridTemplateColumns: { xs: '32px minmax(0, 1fr)', md: course.progress ? '32px minmax(0, 1.2fr) minmax(0, 1fr) auto' : '32px minmax(0, 1.2fr) minmax(0, 1fr)' },
                        // Below 900 px the secondary cells sit under the title, aligned with it.
                        '& > :nth-child(n+3)': { gridColumn: { xs: '2', md: 'auto' } } }}>
                        <CourseMark title={course.title} description={course.description} size={32} />
                        <TileTitleLink component="h3" variant="h4" href={course.href} onClick={course.onOpen}>{course.title}</TileTitleLink>
                        {course.progress ? <>
                            <ProgressLine completed={course.progress.completed} total={course.progress.total} percent={course.progress.percent} label={`${course.title} progress`} />
                            <Typography variant="body2" sx={{ color: 'text.secondary', minWidth: 0 }}>{course.nextLessonTitle ? `Next: ${course.nextLessonTitle}` : 'All lessons completed'}</Typography>
                        </> : course.detail && <Typography variant="body2" sx={{ color: 'text.secondary', minWidth: 0, overflowWrap: 'anywhere' }}>{course.detail}</Typography>}
                    </Box>)}
                </Box>}
            <Box sx={{ mt: 2, display: 'flex', flexWrap: 'wrap', columnGap: 3, rowGap: 1 }}>
                {moreCourses && <Box component="a" href={moreCourses.href} onClick={(event) => { event.preventDefault(); moreCourses.onOpen(); }} sx={{ color: color.brand700, fontWeight: 600 }}>{moreCourses.label}</Box>}
                <Box component="a" href="/courses" onClick={(event) => { event.preventDefault(); onBrowse(); }} sx={{ color: color.brand700, fontWeight: 600 }}>Browse the catalogue</Box>
            </Box>
        </Box>
    </Page>;
}

const failure = (error: unknown) => error instanceof ApiError ? error.message : 'The request could not be completed. Please try again.';

/**
 * Production Learner home at `/home`. It reads only the approved CT22.membership
 * list (active courses). Resume, progress and recommendations stay out until their
 * contracts exist, so those sections are not rendered.
 */
export function LearnerHome() {
    const auth = useAuth(), generation = auth.generation, navigate = useNavigate();
    const [data, setData] = useState<MembershipPage | null>(null), [error, setError] = useState<string | null>(null), [revision, retry] = useState(0);
    useEffect(() => {
        let alive = true; const controller = new AbortController();
        const current = () => alive && auth.isCurrent(generation);
        setData(null); setError(null);
        getMyEnrolledCourses(0, 'active', { signal: controller.signal, isCurrent: current })
            .then((value) => { if (current()) setData(value); })
            .catch((reason: unknown) => { if (current() && !(reason instanceof ApiError && reason.kind === 'cancelled')) setError(failure(reason)); });
        return () => { alive = false; controller.abort(); };
    }, [generation, revision]);
    const courses: HomeCourseView[] = (data?.content ?? []).map((course) => {
        const href = `/courses/${encodeURIComponent(course.id)}`;
        return { id: course.id, title: course.title, description: course.description, href, onOpen: () => navigate(href), detail: `Instructor · ${course.instructorDisplayName ?? 'Instructor name unavailable'}` };
    });
    const more = data && data.totalElements > data.content.length ? data.totalElements : 0;
    return <LearnerHomePage learnerName={auth.user?.displayName ?? ''} subtitle="Your active courses, one click from their syllabus." courses={courses}
        coursesState={{ loading: !data && !error, error, onRetry: () => retry((value) => value + 1) }} onBrowse={() => navigate('/courses')}
        moreCourses={more ? { label: `See all ${more} active courses in My courses`, href: '/my-courses', onOpen: () => navigate('/my-courses') } : undefined} />;
}
