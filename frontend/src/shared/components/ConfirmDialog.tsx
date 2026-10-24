import { useId, useRef } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button } from '@mui/material';

export interface ConfirmDialogProps {
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    isDestructive?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}
export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', isDestructive = false, onConfirm, onCancel }: ConfirmDialogProps) {
    const id = useId();
    const cancelButton = useRef<HTMLButtonElement>(null);
    const confirmButton = useRef<HTMLButtonElement>(null);
    return <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth aria-labelledby={`${id}-title`} aria-describedby={`${id}-message`}
        slotProps={{ transition: { onEntered: () => (isDestructive ? cancelButton.current : confirmButton.current)?.focus() } }}>
        <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
        <DialogContent><DialogContentText id={`${id}-message`}>{message}</DialogContentText></DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
            <Button ref={cancelButton} onClick={onCancel} color="inherit" autoFocus={isDestructive}>{cancelLabel}</Button>
            <Button ref={confirmButton} onClick={onConfirm} variant="contained" color={isDestructive ? 'error' : 'primary'} autoFocus={!isDestructive}>{confirmLabel}</Button>
        </DialogActions>
    </Dialog>;
}
