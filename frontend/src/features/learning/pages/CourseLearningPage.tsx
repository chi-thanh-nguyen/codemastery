import type { ReactNode } from 'react';
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
