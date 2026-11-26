import { useState, type ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { ViewRail } from '../../../shared/components/ViewRail';
import { GuidanceStack } from '../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../shared/components/GuidanceStack';
import { ProgressLine, StatGroup, StatTile } from '../../../shared/components/Tile';
import { MasteryTag, masteryOrder } from '../../../shared/components/StatusPill';
import type { MasteryState } from '../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { EmptyView } from '../../../shared/components/StatusViews';
import { CourseOutline } from '../components/CourseOutline';
import type { CourseProgressView, OutlineChapterView } from '../types/learningTypes';
import { Link, useNavigate } from 'react-router';
import { useCourseLearning } from '../hooks/useCourseLearning';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';

export interface CourseLearningPageProps {
    courseTitle: string;
    chapters: OutlineChapterView[];
    progress?: CourseProgressView;
    view: 'path' | 'outline';
    onViewChange: (view: 'path' | 'outline') => void;
    onOpenLesson: (lessonId: string) => void;
    onResume?: () => void;
    hasResumePoint?: boolean;
    sectionNav?: ReactNode;
    /** Placement diagnostic offer (US-03), when applicable. */
    diagnostic?: ReactNode;
    guidance: GuidanceEntryData[];
    skillCounts?: Record<MasteryState, number>;
    onOpenMap?: () => void;
    back?: Crumb;
}

/** S7 Course workspace — canonical path with adaptive markers; recommendations never reorder it. */
export function CourseLearningPage({ courseTitle, chapters, progress, view, onViewChange, onOpenLesson, onResume, hasResumePoint, sectionNav, diagnostic, guidance, skillCounts, onOpenMap, back }: CourseLearningPageProps) {
    const aside = <Box sx={{ display: 'grid', gap: 4 }}>
        <GuidanceStack title="Up next" entries={guidance} titleId="course-up-next" />
        {skillCounts && <Box component="section" aria-labelledby="course-skills-summary">
            <Typography id="course-skills-summary" component="h2" variant="h4" sx={{ mb: 1.5 }}>Your skills</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1, mb: 2 }}>
                {masteryOrder.map((state) => <Box component="li" key={state} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <MasteryTag state={state} /><Typography className="cm-tabular" sx={{ fontWeight: 600 }}>{skillCounts[state]}</Typography>
                </Box>)}
            </Box>
            {onOpenMap && <Button variant="outlined" fullWidth onClick={onOpenMap} startIcon={<PresentationIcon name="map" />}>Open Mastery Map</Button>}
        </Box>}
    </Box>;
    return <Page aside={aside} asideLabel="Up next and skills">
        <PageHeader title={courseTitle} back={back}
            actions={onResume && <Button variant="contained" onClick={onResume} startIcon={<PresentationIcon name="play" />}>{hasResumePoint ? 'Resume course' : 'Start course'}</Button>} />
        {sectionNav && <Box sx={{ mb: 4 }}>{sectionNav}</Box>}
        {diagnostic}
        {progress && <Box sx={{ mb: 5 }}>
            <Box sx={{ display: { xs: 'none', sm: 'block' }, mb: 2 }}>
                <StatGroup label="Course progress">
                    <StatTile value={progress.total} label="Lessons" tone="sky" />
                    <StatTile value={progress.completed} label="Completed" tone="mint" />
                    <StatTile value={progress.total - progress.completed} label="Remaining" tone="dashed" />
                </StatGroup>
            </Box>
            <ProgressLine completed={progress.completed} total={progress.total} percent={progress.percent} />
        </Box>}
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: '52px minmax(0, 1fr)' }, alignItems: 'start' }}>
            <ViewRail label="Course view" value={view} onChange={onViewChange}
                options={[{ value: 'path', label: 'Path', icon: 'path' }, { value: 'outline', label: 'Outline', icon: 'list' }]} />
            <Box sx={{ minWidth: 0 }}>
                {chapters.length === 0 ? <EmptyView icon="book" title="No lessons published yet" message="Lessons will appear here in course order when the instructor publishes them." />
                    : <CourseOutline chapters={chapters} view={view} onOpenLesson={onOpenLesson} />}
            </Box>
        </Box>
    </Page>;
}

/** Live S7 Course Workspace (Path): connects CT22 progress, resume, and canonical syllabus. */
export function LiveCourseLearningPage({ courseId }: { courseId: string }) {
    const navigate = useNavigate();
    const [view, setView] = useState<'path' | 'outline'>('path');
    const { course, progress, resume, loading, errorStatus, errorMessage, refresh } = useCourseLearning(courseId);

    if (loading) {
        return <Page><LoadingView message="Loading course path…" variant="tiles" count={3} /></Page>;
    }

    if (errorStatus === 403) {
        return <Page><SystemStateView icon="lock" title="Join this course to see your path" message="You need an active or completed membership to view this course workspace." actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">View course details</Button>} /></Page>;
    }

    if (errorStatus === 404) {
        return <Page><SystemStateView icon="search" title="Course not available" message="This course doesn't exist or isn't published." actions={<Button component={Link} to="/courses" variant="contained">Back to catalogue</Button>} /></Page>;
    }

    if (errorMessage || !course || !progress || !resume) {
        return <Page><ErrorView title="Couldn't load course" message={errorMessage ?? 'Please try again.'} onRetry={refresh} /></Page>;
    }

    const progressByLesson = new Map(progress.lessons.map((l) => [l.lessonId, l]));
    const chapters: OutlineChapterView[] = course.syllabus.slice().sort((a, b) => a.position - b.position).map((ch) => ({
        id: ch.id, title: ch.title, position: ch.position,
        lessons: ch.lessons.slice().sort((a, b) => a.position - b.position).map((l) => {
            const entry = progressByLesson.get(l.id);
            const lessonState = entry?.masterySkipped ? 'mastered-skip' : entry?.completed ? 'completed' : 'not-started';
            return { id: l.id, title: l.title, position: l.position, progress: lessonState, isResume: resume.resumeLessonId === l.id };
        }),
    }));

    const targetLessonId = resume.resumeLessonId ?? resume.nextLessonId;
    const openLesson = (lessonId: string) => navigate(`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(lessonId)}`);

    return <CourseLearningPage
        courseTitle={course.title}
        chapters={chapters}
        progress={{ completed: progress.completedLessons, total: progress.totalLessons, percent: progress.progressPercentage }}
        view={view}
        onViewChange={setView}
        onOpenLesson={openLesson}
        hasResumePoint={Boolean(resume.resumeLessonId)}
        onResume={() => { if (targetLessonId) openLesson(targetLessonId); }}
        back={{ label: 'My courses', href: '/my-courses' }}
        guidance={[]}
    />;
}
