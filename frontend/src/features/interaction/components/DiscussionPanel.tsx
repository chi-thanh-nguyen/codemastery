import { useState } from 'react';
import { Box, Button, IconButton, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { StatusPill } from '../../../shared/components/StatusPill';
import { Monogram } from '../../../shared/components/AppHeader';
import type { DiscussionPostView, DiscussionReplyView } from '../types/interactionTypes';
import { color, radius } from '../../../app/theme/theme';

export interface DiscussionPanelProps {
    lessonTitle: string;
    posts: DiscussionPostView[];
    onAsk?: (text: string) => void;
    onReply?: (postId: string, text: string) => void;
    onReport?: (postId: string) => void;
    /** Instructors answer; learners ask. Visibility rules belong to the Interaction contract. */
    canReply: boolean;
    title?: string;
}

/** Lesson Q&A: questions with one level of replies, ask composer and report action (US-09). */
export function DiscussionPanel({ lessonTitle, posts, onAsk, onReply, onReport, canReply, title = 'Questions' }: DiscussionPanelProps) {
    const [draft, setDraft] = useState(''), [error, setError] = useState<string | null>(null);
    return <Box component="section" aria-labelledby="discussion-title">
        <Typography id="discussion-title" variant="h2" sx={{ mb: .5 }}>{title}</Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>Questions about “{lessonTitle}”. Your instructor gets notified.</Typography>
        {onAsk && <Box component="form" onSubmit={(event) => { event.preventDefault(); if (!draft.trim()) { setError('Write your question first.'); return; } onAsk(draft.trim()); setDraft(''); setError(null); }}
            sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: 2.5, mb: 3 }}>
            <TextField label="Ask a question" name="question" multiline minRows={3} fullWidth value={draft} onChange={(event) => setDraft(event.target.value)} error={!!error} helperText={error ?? 'Be specific: include the line of code or the step that confused you.'} />
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1.5 }}><Button type="submit" variant="contained" startIcon={<PresentationIcon name="send" />}>Post question</Button></Box>
        </Box>}
        {posts.length === 0 ? <Box sx={{ border: `1.5px dashed ${color.ink400}`, borderRadius: `${radius.lg}px`, p: 3, textAlign: 'center' }}>
            <Typography sx={{ fontWeight: 600 }}>No questions yet</Typography><Typography variant="body2" sx={{ color: 'text.secondary', mt: .5 }}>Questions about this lesson will appear here.</Typography>
        </Box> : <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
            {posts.map((post) => <Box component="li" key={post.id} sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: { xs: 2, sm: 2.5 } }}>
                <Post item={post} onReport={onReport} />
                {post.replies.length > 0 && <Box component="ul" aria-label="Replies" sx={{ listStyle: 'none', m: 0, mt: 2, p: 0, pl: { xs: 2, sm: 6 }, display: 'grid', gap: 2, borderLeft: { xs: `2px solid ${color.line200}`, sm: 0 } }}>
                    {post.replies.map((reply) => <Box component="li" key={reply.id}><Post item={reply} onReport={onReport} /></Box>)}
                </Box>}
                {canReply && onReply && <ReplyComposer onSend={(text) => onReply(post.id, text)} />}
            </Box>)}
        </Box>}
    </Box>;
}

function Post({ item, onReport }: { item: DiscussionReplyView; onReport?: (id: string) => void }) {
    const [menu, setMenu] = useState<HTMLElement | null>(null);
    if (item.hidden) return <Typography variant="body2" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>This post was hidden by an administrator.</Typography>;
    return <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
        <Monogram name={item.author} size={36} tone="ink" />
        <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Typography sx={{ fontWeight: 600 }}>{item.author}</Typography>
                {item.role === 'Instructor' && <StatusPill size="tag" tone="sky" icon="user">Instructor</StatusPill>}
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{item.createdAtLabel}</Typography>
            </Box>
            <Typography sx={{ mt: .5, overflowWrap: 'anywhere', whiteSpace: 'pre-line' }}>{item.body}</Typography>
        </Box>
        {onReport && <>
            <IconButton aria-label={`More actions for ${item.author}'s post`} aria-haspopup="menu" onClick={(event) => setMenu(event.currentTarget)} sx={{ width: 36, height: 36 }}><PresentationIcon name="more" sx={{ fontSize: 20 }} /></IconButton>
            <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}><MenuItem onClick={() => { setMenu(null); onReport(item.id); }}><PresentationIcon name="flag" sx={{ fontSize: 18 }} />Report post</MenuItem></Menu>
        </>}
    </Box>;
}

function ReplyComposer({ onSend }: { onSend: (text: string) => void }) {
    const [open, setOpen] = useState(false), [text, setText] = useState('');
    if (!open) return <Button variant="text" size="small" sx={{ mt: 1.5, ml: { sm: 5.5 } }} onClick={() => setOpen(true)} startIcon={<PresentationIcon name="reply" />}>Reply</Button>;
    return <Box component="form" onSubmit={(event) => { event.preventDefault(); if (text.trim()) { onSend(text.trim()); setText(''); setOpen(false); } }} sx={{ mt: 2, ml: { sm: 6 } }}>
        <TextField label="Your reply" multiline minRows={2} fullWidth autoFocus value={text} onChange={(event) => setText(event.target.value)} />
        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', mt: 1 }}><Button variant="outlined" size="small" onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" variant="contained" size="small" disabled={!text.trim()}>Post reply</Button></Box>
    </Box>;
}
