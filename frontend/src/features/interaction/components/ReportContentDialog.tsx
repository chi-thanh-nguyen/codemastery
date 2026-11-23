import { useEffect, useState } from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
    Typography,
} from '@mui/material';
import { roles } from '../../../app/theme/theme';

export interface ReportContentDialogProps {
    open: boolean;
    targetLabel: string;
    targetType?: 'post' | 'lesson';
    onSubmit: (reason: string) => void | Promise<void>;
    onCancel: () => void;
    busy?: boolean;
}

/**
 * Report a post or lesson to administrators (CT30 / DESIGN.md › F10, S8a).
 * Requires a non-blank plain-text reason.
 */
export function ReportContentDialog({
    open,
    targetLabel,
    targetType = 'post',
    onSubmit,
    onCancel,
    busy = false,
}: ReportContentDialogProps) {
    const [reason, setReason] = useState('');
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setReason('');
            setError(null);
        }
    }, [open]);

    const title = targetType === 'lesson' ? 'Report lesson' : 'Report post';

    const handleSubmit = (event: React.FormEvent) => {
        event.preventDefault();
        if (!reason.trim()) {
            setError('Please enter a reason for reporting this content.');
            return;
        }
        setError(null);
        onSubmit(reason);
    };

    return (
        <Dialog open={open} onClose={busy ? undefined : onCancel} maxWidth="sm" fullWidth>
            <form onSubmit={handleSubmit} noValidate>
                <DialogTitle sx={{ pb: 1 }}>{title}</DialogTitle>
                <DialogContent sx={{ display: 'grid', gap: 2 }}>
                    <Typography variant="body2" sx={{ color: roles.text.secondary }}>
                        An administrator will review {targetLabel}. Please describe why you are reporting it.
                    </Typography>
                    <TextField
                        name="reason"
                        label="Reason (required)"
                        multiline
                        minRows={3}
                        fullWidth
                        autoFocus
                        required
                        disabled={busy}
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        error={!!error}
                        helperText={error}
                    />
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 3, gap: 1 }}>
                    <Button variant="outlined" onClick={onCancel} disabled={busy}>
                        Cancel
                    </Button>
                    <Button type="submit" variant="contained" disabled={busy}>
                        {busy ? 'Reporting…' : 'Report'}
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    );
}