import { useId, useRef } from 'react';
import type { FocusEvent, ReactNode } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, useMediaQuery, useTheme } from '@mui/material';

export interface ConfirmDialogProps {
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    isDestructive?: boolean;
    busy?: boolean;
    /** Extra content below the message (for example a short form). */
    children?: ReactNode;
    onConfirm: () => void;
    onCancel: () => void;
}
/**
 * Confirmation dialog; destructive confirmations focus Cancel first. The confirm label repeats the action verb.
 * Below 600 px it becomes a full-screen sheet with full-width actions (DESIGN.md › Dialog).
 */
export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', isDestructive = false, busy = false, children, onConfirm, onCancel }: ConfirmDialogProps) {
    const id = useId();
    const sheet = useMediaQuery(useTheme().breakpoints.down('sm'));
    const initialAction = useRef<HTMLButtonElement>(null);
    const focusInitialAction = () => {
        const action = initialAction.current;
        // Strict Mode can leave MUI's dialog surface focused after mount. Only
        // advance that default focus; preserve any action the user already chose.
        if (action?.closest('[role="dialog"]') === action?.ownerDocument.activeElement) action?.focus();
    };
    return <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth fullScreen={sheet} aria-labelledby={`${id}-title`} aria-describedby={`${id}-message`}
        slotProps={{ paper: { sx: sheet ? { borderRadius: 0, display: 'flex', flexDirection: 'column' } : { maxWidth: 560 }, onFocus: (event: FocusEvent<HTMLDivElement>) => {
            // Let MUI record the original trigger before advancing surface focus.
            if (event.target === event.currentTarget) queueMicrotask(focusInitialAction);
        } } }}>
        <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
        <DialogContent sx={sheet ? { flex: '1 1 auto' } : undefined}><DialogContentText id={`${id}-message`}>{message}</DialogContentText>{children}</DialogContent>
        <DialogActions sx={sheet ? { flexDirection: 'column', alignItems: 'stretch', gap: 1.5, '& > :not(style) ~ :not(style)': { ml: 0 }, '& .MuiButton-root': { width: '100%' } } : undefined}>
            <Button ref={isDestructive ? initialAction : undefined} onClick={onCancel} variant="outlined" autoFocus={isDestructive}>{cancelLabel}</Button>
            <Button ref={!isDestructive ? initialAction : undefined} onClick={onConfirm} variant="contained" color={isDestructive ? 'error' : 'ink'} autoFocus={!isDestructive}
                disabled={busy} aria-busy={busy || undefined}>{confirmLabel}</Button>
        </DialogActions>
    </Dialog>;
}
