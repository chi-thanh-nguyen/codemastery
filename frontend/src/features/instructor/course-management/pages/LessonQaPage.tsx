import { useState } from 'react';
import type { ReactNode } from 'react';
import {
    Alert,
    Box,
    ButtonBase,
    Chip,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    Snackbar,
    Typography,
} from '@mui/material';
import { useSearchParams } from 'react-router';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { radius, roles } from '../../../../app/theme/theme';
import { DiscussionPanel } from '../../../interaction/components/DiscussionPanel';
import { ReportContentDialog } from '../../../interaction/components/ReportContentDialog';
import { useDiscussion } from '../../../interaction/hooks/useDiscussion';
import type {
    DiscussionPostView,
    DiscussionThread,
} from '../../../interaction/types/interactionTypes';

export interface QaLessonSummary {
    id: string;
    title: string;
    open: number;
    total: number;
}

export interface LessonQaPageProps {
    lessons: QaLessonSummary[];
    selectedId: string;
    onSelect: (lessonId: string) => void;
    discussion: ReactNode;
}

/** Preview S18 Lesson Q&A for Instructors (preserved for /preview compatibility). */
export function LessonQaPage({ lessons, selectedId, onSelect, discussion }: LessonQaPageProps) {
    return (
        <Box
            sx={{
                display: 'grid',
                gap: 3,
                gridTemplateColumns: { xs: 'minmax(0,1fr)', md: '280px minmax(0, 1fr)' },
                alignItems: 'start',
            }}
        >
            <Box
                component="nav"
                aria-label="Lessons with questions"
                sx={{ bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, p: 1 }}
            >
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                    {lessons.map((lesson) => (
                        <Box component="li" key={lesson.id}>
                            <ButtonBase
                                onClick={() => onSelect(lesson.id)}
                                aria-current={lesson.id === selectedId ? 'page' : undefined}
                                sx={{
                                    width: '100%',
                                    justifyContent: 'space-between',
                                    gap: 1,
                                    textAlign: 'left',
                                    px: 1.5,
                                    py: 1.25,
                                    minHeight: 48,
                                    borderRadius: `${radius.md}px`,
                                    bgcolor: lesson.id === selectedId ? roles.bg.selected : 'transparent',
                                    '&:hover': {
                                        bgcolor: lesson.id === selectedId ? roles.bg.selected : roles.bg.rowHover,
                                    },
                                }}
                            >
                                <Typography sx={{ fontWeight: lesson.id === selectedId ? 600 : 500, minWidth: 0 }}>
                                    {lesson.title}
                                </Typography>
                                {lesson.open > 0 ? (
                                    <StatusPill size="tag" tone="sky">
                                        {lesson.open} open
                                    </StatusPill>
                                ) : (
                                    <Typography variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>
                                        {lesson.total} total
                                    </Typography>
                                )}
                            </ButtonBase>
                        </Box>
                    ))}
                </Box>
            </Box>
            <Box sx={{ minWidth: 0 }}>{discussion}</Box>
        </Box>
    );
}

// --- Live S18c Instructor Lesson Q&A (DESIGN.md r12) ---

export interface CourseLessonOption {
    id: string;
    title: string;
    published: boolean;
}

export interface LiveLessonQaPageProps {
    lessons: CourseLessonOption[];
}

function toDiscussionPostView(thread: DiscussionThread): DiscussionPostView {
    return {
        id: thread.id,
        author: thread.authorDisplayName ?? 'Unknown user',
        role: thread.authorRole === 'Instructor' ? 'Instructor' : 'Learner',
        createdAtLabel: new Date(thread.createdAt).toLocaleString('en-US', {
            dateStyle: 'medium',
            timeStyle: 'short',
        }),
        body: thread.content,
        mine: thread.mine,
        replies: thread.replies.map((reply) => ({
            id: reply.id,
            author: reply.authorDisplayName ?? 'Unknown user',
            role: reply.authorRole === 'Instructor' ? 'Instructor' : 'Learner',
            createdAtLabel: new Date(reply.createdAt).toLocaleString('en-US', {
                dateStyle: 'medium',
                timeStyle: 'short',
            }),
            body: reply.content,
            mine: reply.mine,
        })),
    };
}

/**
 * Live S18c Instructor Lesson Q&A: canonical lesson select (no counts/unanswered inbox),
 * hidden lessons marked "(hidden)", persisted in `?lessonId=`.
 */
export function LiveLessonQaPage({ lessons }: LiveLessonQaPageProps) {
    const [searchParams, setSearchParams] = useSearchParams();
    const lessonParam = searchParams.get('lessonId');
    const activeLesson = lessons.find((l) => l.id === lessonParam) ?? lessons[0];

    const {
        threads,
        loading,
        loadingMore,
        posting,
        error,
        hasMore,
        loadMore,
        refresh,
        replyToQuestion,
        submitPostReport,
    } = useDiscussion(activeLesson?.id);

    const [reportingPostId, setReportingPostId] = useState<string | null>(null);
    const [reportingBusy, setReportingBusy] = useState(false);
    const [receiptOpen, setReceiptOpen] = useState(false);

    const posts = threads.map(toDiscussionPostView);

    if (lessons.length === 0) {
        return <Alert severity="info">This course has no lessons yet.</Alert>;
    }

    return (
        <Box>
            <FormControl sx={{ mb: 3, minWidth: 280 }} size="small">
                <InputLabel id="live-lesson-select-label">Lesson</InputLabel>
                <Select
                    labelId="live-lesson-select-label"
                    value={activeLesson?.id ?? ''}
                    label="Lesson"
                    onChange={(e) => {
                        const nextParams = new URLSearchParams(searchParams);
                        nextParams.set('lessonId', e.target.value);
                        setSearchParams(nextParams, { replace: true });
                    }}
                >
                    {lessons.map((lesson) => (
                        <MenuItem key={lesson.id} value={lesson.id}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                <span>{lesson.title}</span>
                                {!lesson.published && <Chip label="(hidden)" size="small" />}
                            </Box>
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>

            {activeLesson && (
                <DiscussionPanel
                    lessonTitle={activeLesson.title}
                    posts={posts}
                    loading={loading}
                    loadingMore={loadingMore}
                    posting={posting}
                    error={error}
                    hasMore={hasMore}
                    onLoadMore={loadMore}
                    onRetry={refresh}
                    onReply={(postId, text) => replyToQuestion(postId, text)}
                    onReport={(postId) => setReportingPostId(postId)}
                    canReply={true}
                    emptyMessage="No questions on this lesson yet."
                />
            )}

            <ReportContentDialog
                open={!!reportingPostId}
                targetLabel="this post"
                busy={reportingBusy}
                onSubmit={async (reason) => {
                    if (!reportingPostId) return;
                    setReportingBusy(true);
                    try {
                        await submitPostReport(reportingPostId, reason);
                        setReportingPostId(null);
                        setReceiptOpen(true);
                    } finally {
                        setReportingBusy(false);
                    }
                }}
                onCancel={() => setReportingPostId(null)}
            />

            <Snackbar open={receiptOpen} autoHideDuration={6000} onClose={() => setReceiptOpen(false)}>
                <Alert severity="success" role="status" onClose={() => setReceiptOpen(false)}>
                    Thanks. An administrator will review this report.
                </Alert>
            </Snackbar>
        </Box>
    );
}