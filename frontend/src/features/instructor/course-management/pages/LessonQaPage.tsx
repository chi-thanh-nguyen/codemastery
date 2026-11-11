import type { ReactNode } from 'react';
import { Box, ButtonBase, Typography } from '@mui/material';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { color, radius } from '../../../../app/theme/theme';

export interface QaLessonSummary { id: string; title: string; open: number; total: number }
export interface LessonQaPageProps { lessons: QaLessonSummary[]; selectedId: string; onSelect: (lessonId: string) => void; discussion: ReactNode }

/** S18 Lesson Q&A for Instructors: pick a lesson, answer its questions. The thread is Interaction-owned and composed by the caller. */
export function LessonQaPage({ lessons, selectedId, onSelect, discussion }: LessonQaPageProps) {
    return <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', md: '280px minmax(0, 1fr)' }, alignItems: 'start' }}>
        <Box component="nav" aria-label="Lessons with questions" sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: 1 }}>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {lessons.map((lesson) => <Box component="li" key={lesson.id}>
                    <ButtonBase onClick={() => onSelect(lesson.id)} aria-current={lesson.id === selectedId ? 'page' : undefined}
                        sx={{ width: '100%', justifyContent: 'space-between', gap: 1, textAlign: 'left', px: 1.5, py: 1.25, minHeight: 48, borderRadius: `${radius.md}px`, bgcolor: lesson.id === selectedId ? color.brand100 : 'transparent', '&:hover': { bgcolor: lesson.id === selectedId ? color.brand100 : color.canvas100 } }}>
                        <Typography sx={{ fontWeight: lesson.id === selectedId ? 600 : 500, minWidth: 0 }}>{lesson.title}</Typography>
                        {lesson.open > 0 ? <StatusPill size="tag" tone="sky">{lesson.open} open</StatusPill> : <Typography variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>{lesson.total} total</Typography>}
                    </ButtonBase>
                </Box>)}
            </Box>
        </Box>
        <Box sx={{ minWidth: 0 }}>{discussion}</Box>
    </Box>;
}
