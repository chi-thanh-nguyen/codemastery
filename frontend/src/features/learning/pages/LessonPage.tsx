import { useState } from 'react';
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
import type { LessonBlock, LessonMaterialView, LessonProgressState, OutlineChapterView, SkillTagView, LessonAdaptiveMarker } from '../types/learningTypes';
import { color, control, presentationTokens, radius, shadow } from '../../../app/theme/theme';

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
                {remedialReason && <Alert severity="info" icon={<PresentationIcon name="route" />} sx={{ mb: 3, bgcolor: color.lilac100, '& .MuiAlert-icon': { color: color.lilac700 } }}>
                    <strong>Recommended review.</strong> {remedialReason}
                </Alert>}
                {prerequisiteWarning && <Alert severity="warning" sx={{ mb: 3 }} action={prerequisiteWarning.onReview && prerequisiteWarning.reviewLessonTitle
                    ? <Button variant="text" size="small" color="inherit" onClick={prerequisiteWarning.onReview}>Review {prerequisiteWarning.reviewLessonTitle}</Button> : undefined}>
                    You haven't mastered {prerequisiteWarning.skillName} yet. This lesson builds on it. You can continue anyway.
                </Alert>}
                <LessonContent blocks={lesson.blocks} />
                {lesson.video && <Box component="figure" sx={{ m: 0, my: 4 }}>
                    <Box sx={{ aspectRatio: '16 / 9', bgcolor: color.ink950, borderRadius: `${radius.md}px`, display: 'grid', placeItems: 'center', color: color.surface, textAlign: 'center', p: 3 }}>
                        <Box><PresentationIcon name="video" sx={{ fontSize: 32, mb: 1 }} /><Typography>External video embed</Typography>
                            <Typography variant="caption" sx={{ color: color.chromeMuted }}>Videos play from the instructor's external link. CodeMastery doesn't host video.</Typography></Box>
                    </Box>
                    <Typography component="figcaption" variant="caption" sx={{ display: 'block', mt: 1, color: 'text.secondary' }}>
                        {lesson.video.title} · <Box component="a" href={lesson.video.url} target="_blank" rel="noreferrer noopener" sx={{ color: color.brand600 }}>Open video in a new tab</Box>
                    </Typography>
                </Box>}
                {discussion && <Box sx={{ mt: 6 }}>{discussion}</Box>}
                <Box role="region" aria-label="Lesson actions" sx={{ position: 'sticky', bottom: { xs: 8, sm: 16 }, zIndex: 5, mt: 6, maxWidth: 720, bgcolor: color.surface, borderRadius: `${radius.pill}px`,
            boxShadow: shadow.raised, p: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton aria-label="Previous lesson" onClick={onPrevious} disabled={!onPrevious} sx={control.secondary}><PresentationIcon name="arrow-left" /></IconButton>
            <Box sx={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                {completion === 'error' && <Typography role="alert" variant="body2" sx={{ color: color.rose700, display: { xs: 'none', sm: 'block' } }}>Couldn't save. Try again.</Typography>}
                {done ? <Typography role="status" sx={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: 1 }}><PresentationIcon name="check-circle" sx={{ color: color.mint700 }} />{lesson.progress === 'completed' ? 'Lesson completed' : 'Counted as complete'}</Typography>
                    : <Button variant="contained" onClick={onMarkComplete} disabled={completion === 'saving'} aria-busy={completion === 'saving' || undefined}>{completion === 'saving' ? 'Saving…' : completion === 'error' ? 'Try again' : 'Mark as complete'}</Button>}
            </Box>
            {onNext ? <Button variant={done ? 'contained' : 'outlined'} onClick={onNext} endIcon={<PresentationIcon name="arrow-right" />} sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>Next lesson</Button> : null}
            <IconButton aria-label="Next lesson" onClick={onNext} disabled={!onNext} sx={{ ...control.secondary, display: { sm: 'none' } }}><PresentationIcon name="arrow-right" /></IconButton>
        </Box>
            </Box>
        </Box>
        <Drawer anchor="left" open={outlineOpen && !wide} onClose={() => setOutlineOpen(false)}
            slotProps={{ paper: { sx: { bgcolor: color.canvas100, color: color.ink900, width: 'min(340px, calc(100vw - 48px))', p: 2.5, borderRadius: '0 24px 24px 0' } } }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant="h4" component="p">Course outline</Typography>
                <IconButton aria-label="Close course outline" onClick={() => setOutlineOpen(false)}><PresentationIcon name="close" /></IconButton>
            </Box>
            {outline}
        </Drawer>
    </Page>;
}
