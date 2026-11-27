import { useState } from 'react';
import { Box, Button, IconButton, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { StatusPill } from '../../../shared/components/StatusPill';
import { Monogram } from '../../../shared/components/AppHeader';
import { ErrorView, LoadingView } from '../../../shared/components/StatusViews';
import type { DiscussionPostView, DiscussionReplyView } from '../types/interactionTypes';
import { radius, roles } from '../../../app/theme/theme';

export interface DiscussionPanelProps {
    lessonTitle: string;
    posts: DiscussionPostView[];
    onAsk?: (text: string) => void | Promise<unknown>;
    onReply?: (postId: string, text: string) => void | Promise<unknown>;
    onReport?: (postId: string) => void;
    /** Both learners with lesson access and the owning instructor can reply (CT30). */
    canReply: boolean;
    title?: string;
    posting?: boolean;
    loading?: boolean;
    error?: string | null;
    onRetry?: () => void;
    hasMore?: boolean;
    loadingMore?: boolean;
    onLoadMore?: () => void;
    emptyMessage?: string;
}

/** Lesson Q&A: questions newest first, one level of replies oldest first (CT30 / DESIGN.md › S8a). */
export function DiscussionPanel({
    lessonTitle,
    posts,
    onAsk,
    onReply,
    onReport,
    canReply,
    title = 'Questions',
    posting = false,
    loading = false,
    error = null,
    onRetry,
    hasMore = false,
    loadingMore = false,
    onLoadMore,
    emptyMessage = 'Ask the first question about this lesson.',
}: DiscussionPanelProps) {
    const [draft, setDraft] = useState('');
    const [formError, setFormError] = useState<string | null>(null);

    const visiblePosts = posts.filter((post) => !post.hidden);

    return (
        <Box component="section" aria-labelledby="discussion-title">
            <Typography id="discussion-title" variant="h2" sx={{ mb: 0.5 }}>
                {title}
            </Typography>
            <Typography variant="body2" sx={{ color: roles.text.secondary, mb: 3 }}>
                Questions about “{lessonTitle}”.
            </Typography>

            {onAsk && (
                <Box
                    component="form"
                    onSubmit={async (event) => {
                        event.preventDefault();
                        if (!draft.trim()) {
                            setFormError('Write your question first.');
                            return;
                        }
                        setFormError(null);
                        try {
                            await onAsk(draft);
                            setDraft('');
                        } catch (err: unknown) {
                            setFormError(err instanceof Error ? err.message : "Couldn't post your question. Try again.");
                        }
                    }}
                    sx={{ bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, p: 2.5, mb: 3 }}
                >
                    <TextField
                        label="Ask a question"
                        name="question"
                        multiline
                        minRows={3}
                        fullWidth
                        disabled={posting}
                        value={draft}
                        onChange={(event) => setDraft(event.target.value)}
                        error={!!formError}
                        helperText={
                            formError ?? 'Be specific: include the line of code or the step that confused you.'
                        }
                    />
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1.5 }}>
                        <Button
                            type="submit"
                            variant="contained"
                            disabled={posting}
                            startIcon={<PresentationIcon name="send" />}
                        >
                            {posting ? 'Posting…' : 'Post question'}
                        </Button>
                    </Box>
                </Box>
            )}

            {loading ? (
                <LoadingView message="Loading questions…" />
            ) : error ? (
                <ErrorView title="Couldn't load questions" message={error} onRetry={onRetry} />
            ) : visiblePosts.length === 0 ? (
                <Box
                    sx={{
                        border: `1.5px dashed ${roles.line.strong}`,
                        borderRadius: `${radius.lg}px`,
                        p: 3,
                        textAlign: 'center',
                    }}
                >
                    <Typography sx={{ fontWeight: 600 }}>No questions yet</Typography>
                    <Typography variant="body2" sx={{ color: roles.text.secondary, mt: 0.5 }}>
                        {emptyMessage}
                    </Typography>
                </Box>
            ) : (
                <Box sx={{ display: 'grid', gap: 2 }}>
                    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
                        {visiblePosts.map((post) => {
                            const visibleReplies = post.replies.filter((r) => !r.hidden);
                            return (
                                <Box
                                    component="li"
                                    key={post.id}
                                    sx={{ bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, p: { xs: 2, sm: 2.5 } }}
                                >
                                    <Post item={post} onReport={onReport} />
                                    {visibleReplies.length > 0 && (
                                        <Box
                                            component="ul"
                                            aria-label="Replies"
                                            sx={{
                                                listStyle: 'none',
                                                m: 0,
                                                mt: 2,
                                                p: 0,
                                                pl: { xs: 2, sm: 6 },
                                                display: 'grid',
                                                gap: 2,
                                                borderLeft: { xs: `2px solid ${roles.line.hairline}`, sm: 0 },
                                            }}
                                        >
                                            {visibleReplies.map((reply) => (
                                                <Box component="li" key={reply.id}>
                                                    <Post item={reply} onReport={onReport} />
                                                </Box>
                                            ))}
                                        </Box>
                                    )}
                                    {canReply && onReply && (
                                        <ReplyComposer disabled={posting} onSend={(text) => onReply(post.id, text)} />
                                    )}
                                </Box>
                            );
                        })}
                    </Box>

                    {hasMore && onLoadMore && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1 }}>
                            <Button variant="outlined" onClick={onLoadMore} disabled={loadingMore}>
                                {loadingMore ? 'Loading more…' : 'Show more questions'}
                            </Button>
                        </Box>
                    )}
                </Box>
            )}
        </Box>
    );
}

function Post({ item, onReport }: { item: DiscussionReplyView; onReport?: (id: string) => void }) {
    const [menu, setMenu] = useState<HTMLElement | null>(null);
    const authorName = item.author?.trim() ? item.author : 'Unknown user';

    return (
        <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
            <Monogram name={authorName} size={36} tone="ink" />
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <Typography sx={{ fontWeight: 600 }}>{authorName}</Typography>
                    {item.role === 'Instructor' && (
                        <StatusPill size="tag" tone="sky" icon="user">
                            Instructor
                        </StatusPill>
                    )}
                    {item.mine && (
                        <StatusPill size="tag" tone="neutral">
                            You
                        </StatusPill>
                    )}
                    <Typography variant="caption" sx={{ color: roles.text.secondary }}>
                        {item.createdAtLabel}
                    </Typography>
                </Box>
                <Typography sx={{ mt: 0.5, overflowWrap: 'anywhere', whiteSpace: 'pre-line' }}>
                    {item.body}
                </Typography>
            </Box>
            {onReport && (
                <>
                    <IconButton
                        aria-label={`More actions for ${authorName}'s post`}
                        aria-haspopup="menu"
                        onClick={(event) => setMenu(event.currentTarget)}
                        sx={{ width: 44, height: 44 }}
                    >
                        <PresentationIcon name="more" sx={{ fontSize: 20 }} />
                    </IconButton>
                    <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
                        <MenuItem
                            onClick={() => {
                                setMenu(null);
                                onReport(item.id);
                            }}
                        >
                            <PresentationIcon name="flag" sx={{ fontSize: 18, mr: 1 }} />
                            Report post
                        </MenuItem>
                    </Menu>
                </>
            )}
        </Box>
    );
}

function ReplyComposer({
    onSend,
    disabled = false,
}: {
    onSend: (text: string) => void | Promise<unknown>;
    disabled?: boolean;
}) {
    const [open, setOpen] = useState(false);
    const [text, setText] = useState('');
    const [replyError, setReplyError] = useState<string | null>(null);

    if (!open) {
        return (
            <Button
                variant="text"
                size="small"
                sx={{ mt: 1.5, ml: { sm: 5.5 } }}
                onClick={() => setOpen(true)}
                startIcon={<PresentationIcon name="reply" />}
            >
                Reply
            </Button>
        );
    }

    return (
        <Box
            component="form"
            onSubmit={async (event) => {
                event.preventDefault();
                if (!text.trim()) return;
                setReplyError(null);
                try {
                    await onSend(text);
                    setText('');
                    setOpen(false);
                } catch (err: unknown) {
                    setReplyError(err instanceof Error ? err.message : "Couldn't post reply. Try again.");
                }
            }}
            sx={{ mt: 2, ml: { sm: 6 } }}
        >
            <TextField
                label="Your reply"
                multiline
                minRows={2}
                fullWidth
                autoFocus
                disabled={disabled}
                value={text}
                onChange={(event) => setText(event.target.value)}
                error={!!replyError}
                helperText={replyError}
            />
            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', mt: 1 }}>
                <Button variant="outlined" size="small" onClick={() => setOpen(false)} disabled={disabled}>
                    Cancel
                </Button>
                <Button type="submit" variant="contained" size="small" disabled={disabled || !text.trim()}>
                    {disabled ? 'Posting…' : 'Post reply'}
                </Button>
            </Box>
        </Box>
    );
}