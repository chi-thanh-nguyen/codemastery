import { useEffect, useId, useRef, useState } from 'react';
import type { DragEvent } from 'react';
import { Box, Button, LinearProgress, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { BinaryDownload } from '../api/httpClient';
import { radius, roles } from '../../app/theme/theme';

export type UploadState = { kind: 'idle' } | { kind: 'uploading'; fileName: string; percent?: number } | { kind: 'accepted'; fileName: string; detail?: string } | { kind: 'rejected'; message: string };

export interface FileUploadFieldProps {
    label: string;
    /** Accepted types and size limit as defined by the owning contract (CT09); omitted until defined. */
    requirements?: string;
    state: UploadState;
    onSelect: (file: File) => void;
    disabled?: boolean;
    accept?: string;
    onReject?: (message: string) => void;
}

/**
 * Dashed drop zone with a native file input (keyboard: the "Choose a file" button).
 * Presentation only: no bytes are read or sent by this component.
 */
export function FileUploadField({ label, requirements, state, onSelect, disabled, accept, onReject }: FileUploadFieldProps) {
    const input = useRef<HTMLInputElement>(null), id = useId();
    const [over, setOver] = useState(false);
    const locked = disabled || state.kind === 'uploading';
    const [selectionError, setSelectionError] = useState<string | null>(null);
    const select = (files: FileList | null) => {
        if (locked) return;
        if (!files || files.length !== 1) { const message = 'Choose one file at a time.'; setSelectionError(message); onReject?.(message); return; }
        setSelectionError(null); onSelect(files[0]);
    };
    const drop = (event: DragEvent) => { event.preventDefault(); setOver(false); select(event.dataTransfer.files); };
    return <Box>
        <Typography component="label" htmlFor={id} sx={{ display: 'block', fontWeight: 600, mb: 1 }}>{label}</Typography>
        <Box onDragOver={(event) => { event.preventDefault(); if (!locked) setOver(true); }} onDragLeave={() => setOver(false)} onDrop={drop}
            sx={{ border: `1.5px dashed ${over ? roles.accent.progress : roles.line.strong}`, bgcolor: over ? roles.bg.selected : roles.bg.surface, borderRadius: `${radius.lg}px`, p: { xs: 3, sm: 4 },
                textAlign: 'center', transition: 'background-color 120ms, border-color 120ms', opacity: locked ? .6 : 1 }}>
            <PresentationIcon name="upload" sx={{ fontSize: 28, color: roles.text.secondary }} />
            <Typography sx={{ mt: 1, fontWeight: 600 }}>Choose a file or drop it here</Typography>
            <Typography variant="body2" sx={{ mt: .5, color: 'text.secondary' }} id={`${id}-requirements`}>{requirements ?? 'Choose one file. The server checks its type and contents.'}</Typography>
            <Button variant="outlined" sx={{ mt: 2 }} disabled={locked} onClick={() => input.current?.click()} startIcon={<PresentationIcon name="file" />}>Choose a file</Button>
            <input ref={input} id={id} type="file" hidden accept={accept} aria-describedby={`${id}-requirements`} disabled={locked}
                onChange={(event) => { if (event.target.files?.length) select(event.target.files); event.target.value = ''; }} />
        </Box>
        <Box role="status" sx={{ mt: 1.5 }}>
            {selectionError && <Typography sx={{ color: 'error.main' }}>{selectionError}</Typography>}
            {state.kind === 'uploading' && <Box><Typography variant="body2" sx={{ mb: 1 }}>Uploading {state.fileName}…</Typography><LinearProgress variant={state.percent === undefined ? 'indeterminate' : 'determinate'} value={state.percent} aria-label={`Uploading ${state.fileName}`} /></Box>}
            {state.kind === 'accepted' && <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', bgcolor: roles.tone.mint.tint, borderRadius: `${radius.md}px`, px: 2, py: 1.5 }}>
                <PresentationIcon name="check-circle" sx={{ color: roles.tone.mint.strong }} /><Box><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{state.fileName}</Typography>{state.detail && <Typography variant="caption">{state.detail}</Typography>}</Box>
            </Box>}
            {state.kind === 'rejected' && <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', bgcolor: roles.tone.rose.tint, borderRadius: `${radius.md}px`, px: 2, py: 1.5 }}>
                <PresentationIcon name="alert-circle" sx={{ color: roles.tone.rose.strong }} /><Typography>{state.message}</Typography>
            </Box>}
        </Box>
    </Box>;
}


/** CT37.widget: authenticated Blob, server filename, transient object URL; no navigation/token URL. */
export function FileDownloadButton({ load, disabled, label = 'Download PDF', onDownloaded }: { load: (signal: AbortSignal) => Promise<BinaryDownload>; disabled?: boolean; label?: string; onDownloaded?: () => void }) {
    const work = useRef<AbortController | null>(null);
    const [busy, setBusy] = useState(false), [message, setMessage] = useState('');
    useEffect(() => () => { work.current?.abort(); work.current = null; }, []);
    async function download() {
        if (work.current) return;
        const controller = new AbortController(); work.current = controller; setBusy(true); setMessage('Downloading…');
        try {
            const file = await load(controller.signal);
            if (controller.signal.aborted || work.current !== controller) return;
            const url = URL.createObjectURL(file.blob);
            try {
                const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.filename;
                document.body.append(anchor); try { anchor.click(); } finally { anchor.remove(); }
                setMessage('Download ready.'); onDownloaded?.();
            } finally { URL.revokeObjectURL(url); }
        } catch { if (!controller.signal.aborted) setMessage('The download could not be completed. Try again.'); }
        finally { if (work.current === controller) { work.current = null; setBusy(false); } }
    }
    return <Box><Button variant="outlined" disabled={disabled || busy} onClick={() => void download()}>{label}</Button><Typography role="status" variant="caption" sx={{ display: 'block' }}>{message}</Typography></Box>;
}
