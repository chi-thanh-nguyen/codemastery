import { Box, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { GuidanceStack, GuidanceEntry } from '../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../shared/components/GuidanceStack';
import { ProgressLine, Tile, TileTitleLink, CircleAffordance } from '../../../shared/components/Tile';
import { StatusPill } from '../../../shared/components/StatusPill';
import { EmptyView } from '../../../shared/components/StatusViews';
import type { CourseProgressView } from '../types/learningTypes';
import { color, radius } from '../../../app/theme/theme';

export interface ContinueLessonView { courseTitle: string; chapterTitle: string; lessonTitle: string; position: number; onResume: () => void }
export interface HomeCourseView { id: string; title: string; progress: CourseProgressView; nextLessonTitle?: string; onOpen: () => void }
export interface LearnerHomePageProps {
    learnerName: string;
    continueLesson?: ContinueLessonView;
    courses: HomeCourseView[];
    guidance: GuidanceEntryData[];
    onBrowse: () => void;
}

/** S6 Learner home (Proposed route): the next step across active courses. No streaks or time charts. */
export function LearnerHomePage({ learnerName, continueLesson, courses, guidance, onBrowse }: LearnerHomePageProps) {
    const firstRecommendation = guidance.find((entry) => entry.tone === 'lilac');
    // Below 1200 px the aside stacks under the main column, so the recommendation moves to the top instead of appearing twice.
    const stacked = !useMediaQuery(useTheme().breakpoints.up('lg'));
    const promoted = stacked ? firstRecommendation : undefined;
    return <Page aside={<GuidanceStack title="Up next" entries={promoted ? guidance.filter((entry) => entry !== promoted) : guidance} />} asideLabel="Up next">
        <PageHeader title={`Welcome back, ${learnerName}`} subtitle="Pick up where you left off, or follow your next recommendation." />
        {promoted && <Box sx={{ mb: 4 }}><GuidanceEntry component="div" entry={promoted} /></Box>}
        <Box component="section" aria-labelledby="home-continue" sx={{ mb: 6 }}>
            <Typography id="home-continue" variant="h2" sx={{ mb: 2 }}>Continue learning</Typography>
            {continueLesson ? <Tile interactive component="article" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 1.5 }}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{continueLesson.courseTitle} · {continueLesson.chapterTitle} · Lesson {continueLesson.position}</Typography>
                <TileTitleLink component="h3" onClick={continueLesson.onResume}>{continueLesson.lessonTitle}</TileTitleLink>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><Box sx={{ flex: 1 }}><StatusPill tone="sky" icon="play" size="tag">Resume</StatusPill></Box><CircleAffordance icon="play" /></Box>
            </Tile> : <EmptyView compact icon="book" headingLevel="h3" title="Nothing in progress" message="Open a course to start its first lesson." />}
        </Box>
        <Box component="section" aria-labelledby="home-courses">
            <Typography id="home-courses" variant="h2" sx={{ mb: 2 }}>Your courses</Typography>
            {courses.length === 0 ? <EmptyView icon="catalogue" headingLevel="h3" title="You haven't joined a course yet" message="Browse the catalogue to find one." /> :
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {courses.map((course) => <Box component="li" key={course.id} sx={{ position: 'relative', p: { xs: 2.5, sm: 3 }, borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 },
                        display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1.2fr) minmax(0, 1fr) auto' }, alignItems: 'center', '&:hover': { bgcolor: color.canvas100 } }}>
                        <TileTitleLink component="h3" variant="h4" onClick={course.onOpen}>{course.title}</TileTitleLink>
                        <ProgressLine completed={course.progress.completed} total={course.progress.total} percent={course.progress.percent} label={`${course.title} progress`} />
                        <Typography variant="body2" sx={{ color: 'text.secondary', minWidth: 0 }}>{course.nextLessonTitle ? `Next: ${course.nextLessonTitle}` : 'All lessons completed'}</Typography>
                    </Box>)}
                </Box>}
            <Box sx={{ mt: 2 }}><Box component="a" href="/courses" onClick={(event) => { event.preventDefault(); onBrowse(); }} sx={{ color: color.brand600, fontWeight: 600 }}>Browse the catalogue</Box></Box>
        </Box>
    </Page>;
}
