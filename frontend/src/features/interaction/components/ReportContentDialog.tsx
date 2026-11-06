import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';

/** Report a post or lesson to administrators (US-09). The data model records no reason field, so none is asked. */
export function ReportContentDialog({ open, targetLabel, onSubmit, onCancel, busy }: { open: boolean; targetLabel: string; onSubmit: () => void; onCancel: () => void; busy?: boolean }) {
    return <ConfirmDialog open={open} title="Report this post?" message={`An administrator will review ${targetLabel}. They can hide it if it breaks the course rules.`}
        confirmLabel="Report post" cancelLabel="Cancel" onConfirm={onSubmit} onCancel={onCancel} busy={busy} />;
}
