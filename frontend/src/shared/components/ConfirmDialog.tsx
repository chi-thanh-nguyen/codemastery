import { useId, useRef } from 'react';
import type { FocusEvent } from 'react';
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
    const initialAction = useRef<HTMLButtonElement>(null);
    const focusInitialAction = () => {
        const action = initialAction.current;
        // Strict Mode can leave MUI's dialog surface focused after mount. Only
        // advance that default focus; preserve any action the user already chose.
        if (action?.closest('[role="dialog"]') === action?.ownerDocument.activeElement) action?.focus();
    };
    return <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth aria-labelledby={`${id}-title`} aria-describedby={`${id}-message`}
        slotProps={{ paper: { onFocus: (event: FocusEvent<HTMLDivElement>) => {
            // Let MUI record the original trigger before advancing surface focus.
            if (event.target === event.currentTarget) queueMicrotask(focusInitialAction);
        } } }}>
        <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
        <DialogContent><DialogContentText id={`${id}-message`}>{message}</DialogContentText></DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
            <Button ref={isDestructive ? initialAction : undefined} onClick={onCancel} color="inherit" autoFocus={isDestructive}>{cancelLabel}</Button>
            <Button ref={!isDestructive ? initialAction : undefined} onClick={onConfirm} variant="contained" color={isDestructive ? 'error' : 'primary'} autoFocus={!isDestructive}>{confirmLabel}</Button>
        </DialogActions>
    </Dialog>;
}
