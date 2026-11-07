import { Box, ButtonBase, Typography } from '@mui/material';
import { PathSpine } from '../../../shared/components/PathSpine';
import type { SpineNode } from '../../../shared/components/PathSpine';
import { StatusPill, MasteryTag } from '../../../shared/components/StatusPill';
import { Tile, TileTitleLink, CornerBadge, CircleAffordance } from '../../../shared/components/Tile';
import type { LessonAdaptiveMarker, LessonProgressState, OutlineChapterView, OutlineLessonView } from '../types/learningTypes';
import { color, radius } from '../../../app/theme/theme';

const markerMeta: Record<LessonAdaptiveMarker, { label: string; tone: 'lilac' | 'butter'; icon: 'route' | 'skip' | 'alert-triangle' }> = {
    recommended: { label: 'Recommended', tone: 'lilac', icon: 'route' },
    skippable: { label: 'Skippable (Test-out)', tone: 'lilac', icon: 'skip' },
    remedial: { label: 'Remedial', tone: 'lilac', icon: 'route' },
    'prerequisite-gap': { label: 'Prerequisite gap', tone: 'butter', icon: 'alert-triangle' },
};

/** Lesson completion wording (LessonProgress). Never a mastery claim, except the approved mastery skip. */
export function LessonProgressPill({ progress, size = 'tag' }: { progress: LessonProgressState; size?: 'pill' | 'tag' }) {
    if (progress === 'completed') return <StatusPill tone="mint" icon="check-circle" size={size}>Completed</StatusPill>;
    if (progress === 'mastered-skip') return <StatusPill tone="mint" icon="skip" size={size}>Mastered · Counted as complete</StatusPill>;
    return <StatusPill tone="dashed" icon="dashed-circle" size={size}>Not started</StatusPill>;
}
export function MarkerTag({ marker }: { marker: LessonAdaptiveMarker }) {
    const meta = markerMeta[marker];
    return <StatusPill tone={meta.tone} icon={meta.icon} size="tag">{meta.label}</StatusPill>;
}

const nodeFor = (lesson: OutlineLessonView): SpineNode => lesson.progress !== 'not-started' ? 'done' : lesson.isResume ? 'current'
    : lesson.markers?.some((marker) => marker === 'recommended' || marker === 'remedial') ? 'recommended' : 'todo';

export interface CourseOutlineProps {
    chapters: OutlineChapterView[];
    view: 'path' | 'outline';
    onOpenLesson: (lessonId: string) => void;
    lessonHref?: (lessonId: string) => string;
    currentLessonId?: string;
    /** Compact list for the lesson reader's left column. */
    compact?: boolean;
}

/** Canonical course order as the Path Spine (tiles) or a compact outline (rows). Markers never reorder it. */
export function CourseOutline({ chapters, view, onOpenLesson, lessonHref = (id) => `#lesson-${id}`, currentLessonId, compact }: CourseOutlineProps) {
    if (view === 'outline' || compact) return <Box component="nav" aria-label="Course outline">
        {chapters.map((chapter) => <Box key={chapter.id} sx={{ mb: compact ? 2.5 : 3 }}>
            <Typography component="h2" variant={compact ? 'subtitle2' : 'h4'} sx={{ mb: 1, color: compact ? 'text.secondary' : 'text.primary' }}>{chapter.title}</Typography>
            <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: compact ? .25 : 0, ...(compact ? {} : { bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }) }}>
                {chapter.lessons.map((lesson) => {
                    const current = lesson.id === currentLessonId;
                    return <Box component="li" key={lesson.id} sx={{ borderBottom: compact ? 0 : `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 } }}>
                        <ButtonBase component="a" href={lessonHref(lesson.id)} aria-current={current ? 'page' : undefined} onClick={(event) => { event.preventDefault(); onOpenLesson(lesson.id); }}
                            sx={{ width: '100%', justifyContent: 'flex-start', gap: 1.5, px: compact ? 1.25 : 2, py: compact ? 1 : 1.5, minHeight: 44, textAlign: 'left', borderRadius: compact ? `${radius.sm}px` : 0,
                                bgcolor: current ? color.brand100 : 'transparent', '&:hover': { bgcolor: current ? color.brand100 : compact ? color.fill150 : color.canvas100 } }}>
                            <OutlineDot progress={lesson.progress} current={current || !!lesson.isResume} />
                            <Typography component="span" className="cm-tabular" sx={{ color: 'text.secondary', fontSize: '0.8125rem', fontWeight: 600, minWidth: 20 }}>{lesson.position}</Typography>
                            <Typography component="span" sx={{ flex: 1, minWidth: 0, fontWeight: current ? 600 : 500, fontSize: compact ? '0.9375rem' : '1rem', overflowWrap: 'anywhere' }}>{lesson.title}</Typography>
                            {!compact && <Box sx={{ display: { xs: 'none', sm: 'flex' }, gap: .75, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                                {lesson.markers?.map((marker) => <MarkerTag key={marker} marker={marker} />)}
                                <LessonProgressPill progress={lesson.progress} />
                            </Box>}
                            {compact && <span className="cm-visually-hidden">{lesson.progress === 'not-started' ? 'Not started' : lesson.progress === 'completed' ? 'Completed' : 'Mastered, counted as complete'}</span>}
                        </ButtonBase>
                    </Box>;
                })}
            </Box>
        </Box>)}
    </Box>;
    return <PathSpine listLabel={(section) => `Lessons in ${section.title}`} sections={chapters.map((chapter) => ({
        id: chapter.id, title: chapter.title, meta: <Typography variant="caption" sx={{ color: 'text.secondary' }}>Chapter {chapter.position} · {chapter.lessons.length} lessons</Typography>,
        items: chapter.lessons.map((lesson) => ({ id: lesson.id, node: nodeFor(lesson), content: <LessonTile lesson={lesson} href={lessonHref(lesson.id)} onOpen={() => onOpenLesson(lesson.id)} /> })),
    }))} />;
}

function OutlineDot({ progress, current }: { progress: LessonProgressState; current: boolean }) {
    const done = progress !== 'not-started';
    return <Box aria-hidden sx={{ width: 12, height: 12, borderRadius: '50%', flexShrink: 0, bgcolor: done ? color.mint700 : current ? color.brand600 : color.surface,
        border: done || current ? 0 : `2px solid ${color.ink400}`, boxShadow: current && !done ? `0 0 0 3px ${color.brand100}` : 'none', boxSizing: 'border-box' }} />;
}

function LessonTile({ lesson, href, onOpen }: { lesson: OutlineLessonView; href: string; onOpen: () => void }) {
    const badge = lesson.markers?.includes('prerequisite-gap') ? { tone: 'butter' as const, icon: 'alert-triangle' as const }
        : lesson.markers?.length ? { tone: 'lilac' as const, icon: lesson.markers.includes('skippable') ? 'skip' as const : 'route' as const } : null;
    return <Tile interactive component="article" sx={{ display: 'grid', gap: 1.5 }}>
        {badge && <CornerBadge tone={badge.tone} icon={badge.icon} />}
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>Lesson {lesson.position}{lesson.isResume ? ' · Where you left off' : ''}</Typography>
        <TileTitleLink href={href} onClick={onOpen}>{lesson.title}</TileTitleLink>
        {lesson.summary && <Typography sx={{ color: 'text.secondary', maxWidth: '62ch' }}>{lesson.summary}</Typography>}
        {lesson.skills && lesson.skills.length > 0 && <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: .75, alignItems: 'center' }}>
            <Typography variant="caption" sx={{ color: 'text.secondary', mr: .5 }}>Skills</Typography>
            {lesson.skills.map((skill) => <Box key={skill.id} component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: .5 }}>
                <Typography component="span" variant="caption" sx={{ fontWeight: 600 }}>{skill.name}</Typography><MasteryTag state={skill.state} />
            </Box>)}
        </Box>}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: .5 }}>
            <Box sx={{ flex: 1, display: 'flex', flexWrap: 'wrap', gap: .75 }}>
                <LessonProgressPill progress={lesson.progress} />
                {lesson.isResume && lesson.progress === 'not-started' && <StatusPill tone="sky" icon="play" size="tag">Resume</StatusPill>}
                {lesson.markers?.map((marker) => <MarkerTag key={marker} marker={marker} />)}
            </Box>
            {/* Graphite only for the resume point; every other lesson opens through a neutral circle. */}
            <CircleAffordance emphasis={lesson.isResume ? 'primary' : 'secondary'} />
        </Box>
    </Tile>;
}
