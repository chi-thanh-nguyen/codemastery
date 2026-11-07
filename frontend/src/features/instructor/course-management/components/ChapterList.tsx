import { Box, Button, Typography } from '@mui/material';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { PublicationPill } from './PublishToggle';
import type { ChapterEditView } from '../types/courseManagementTypes';
import { color, radius } from '../../../../app/theme/theme';

export interface ChapterListProps {
    chapters: ChapterEditView[];
    onEditLesson: (lessonId: string) => void;
    onMoveLesson: (chapterId: string, lessonId: string, direction: -1 | 1) => void;
    onAddLesson: (chapterId: string) => void;
    onAddChapter: () => void;
}

/** Ordered chapters and lessons with keyboard-operable reordering (Move up / Move down), announced via status. */
export function ChapterList({ chapters, onEditLesson, onMoveLesson, onAddLesson, onAddChapter }: ChapterListProps) {
    return <Box>
        {chapters.map((chapter) => <Box component="section" key={chapter.id} aria-labelledby={`chapter-${chapter.id}`} sx={{ mb: 4 }}>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', mb: 1.5 }}>
                <Typography id={`chapter-${chapter.id}`} component="h2" variant="h3">Chapter {chapter.position}: {chapter.title}</Typography>
                <Button variant="text" onClick={() => onAddLesson(chapter.id)} startIcon={<PresentationIcon name="plus" />}>Add lesson</Button>
            </Box>
            {chapter.lessons.length === 0 ? <Box sx={{ border: `1.5px dashed ${color.ink400}`, borderRadius: `${radius.lg}px`, p: 3, textAlign: 'center', color: 'text.secondary' }}>No lessons in this chapter yet.</Box>
                : <Box component="ol" aria-label={`Lessons in ${chapter.title}`} sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {chapter.lessons.map((lesson, index) => <Box component="li" key={lesson.id} sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: { xs: 'wrap', sm: 'nowrap' }, px: { xs: 2, sm: 3 }, py: 1.5, minHeight: 64,
                        borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 } }}>
                        <Typography className="cm-tabular" sx={{ color: 'text.secondary', fontWeight: 600, width: 24 }}>{lesson.position}</Typography>
                        <Box sx={{ flex: '1 1 200px', minWidth: 0 }}>
                            <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{lesson.title}</Typography>
                            <Typography variant="caption" sx={{ color: lesson.skillIds.length ? 'text.secondary' : color.butter800 }}>{lesson.skillIds.length ? `${lesson.skillIds.length} ${lesson.skillIds.length === 1 ? 'skill' : 'skills'} mapped` : 'No skill mapped yet'}</Typography>
                        </Box>
                        <PublicationPill publication={lesson.publication} />
                        <Box sx={{ display: 'flex', gap: .5 }}>
                            <CircleAction label={`Move ${lesson.title} up`} icon="move-up" variant="plain" disabled={index === 0} onClick={() => onMoveLesson(chapter.id, lesson.id, -1)} />
                            <CircleAction label={`Move ${lesson.title} down`} icon="move-down" variant="plain" disabled={index === chapter.lessons.length - 1} onClick={() => onMoveLesson(chapter.id, lesson.id, 1)} />
                            <CircleAction label={`Edit ${lesson.title}`} icon="edit" variant="ink" onClick={() => onEditLesson(lesson.id)} />
                        </Box>
                    </Box>)}
                </Box>}
        </Box>)}
        <Button variant="outlined" onClick={onAddChapter} startIcon={<PresentationIcon name="plus" />}>Add chapter</Button>
    </Box>;
}
