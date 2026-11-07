import { useState } from 'react';
import { Box, Button } from '@mui/material';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { StatusPill } from '../../../../shared/components/StatusPill';
import type { Publication } from '../types/courseManagementTypes';

export function PublicationPill({ publication }: { publication: Publication }) {
    return publication === 'published' ? <StatusPill tone="mint" icon="eye" size="tag">Published</StatusPill> : <StatusPill tone="neutral" icon="eye-off" size="tag">Hidden</StatusPill>;
}

/** Publish or hide with a confirmation that states what learners will see (US-10). */
export function PublishToggle({ publication, itemLabel, onChange, consequence }: { publication: Publication; itemLabel: string; onChange: (next: Publication) => void; consequence?: string }) {
    const [open, setOpen] = useState(false);
    const publishing = publication === 'hidden';
    return <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
        <PublicationPill publication={publication} />
        <Button variant={publishing ? 'contained' : 'outlined'} onClick={() => setOpen(true)}>{publishing ? `Publish ${itemLabel}` : `Hide ${itemLabel}`}</Button>
        <ConfirmDialog open={open} title={publishing ? `Publish ${itemLabel}?` : `Hide ${itemLabel}?`}
            message={consequence ?? (publishing ? 'Enrolled learners will see published content. Hidden lessons, quizzes and assignments stay hidden.' : 'Learners will no longer see it. Their history is kept.')}
            confirmLabel={publishing ? `Publish ${itemLabel}` : `Hide ${itemLabel}`} isDestructive={!publishing}
            onCancel={() => setOpen(false)} onConfirm={() => { setOpen(false); onChange(publishing ? 'published' : 'hidden'); }} />
    </Box>;
}
