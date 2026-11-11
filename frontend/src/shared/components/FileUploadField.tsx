import { useId, useRef, useState } from 'react';
import type { DragEvent } from 'react';
import { Box, Button, LinearProgress, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { color, radius } from '../../app/theme/theme';

export type UploadState = { kind: 'idle' } | { kind: 'uploading'; fileName: string; percent?: number } | { kind: 'accepted'; fileName: string; detail?: string } | { kind: 'rejected'; message: string };

export interface FileUploadFieldProps {
    label: string;
    /** Accepted types and size limit as defined by the owning contract (CT09); omitted until defined. */
    requirements?: string;
    state: UploadState;
    onSelect: (file: File) => void;
    disabled?: boolean;
    accept?: string;
}

/**
 * Dashed drop zone with a native file input (keyboard: the "Choose a file" button).
 * Presentation only: no bytes are read or sent by this component.
 */
export function FileUploadField({ label, requirements, state, onSelect, disabled, accept }: FileUploadFieldProps) {
    const input = useRef<HTMLInputElement>(null), id = useId();
    const [over, setOver] = useState(false);
    const drop = (event: DragEvent) => { event.preventDefault(); setOver(false); const file = event.dataTransfer.files?.[0]; if (file && !disabled) onSelect(file); };
    return <Box>
        <Typography component="label" htmlFor={id} sx={{ display: 'block', fontWeight: 600, mb: 1 }}>{label}</Typography>
        <Box onDragOver={(event) => { event.preventDefault(); if (!disabled) setOver(true); }} onDragLeave={() => setOver(false)} onDrop={drop}
            sx={{ border: `1.5px dashed ${over ? color.brand600 : color.ink400}`, bgcolor: over ? color.brand100 : color.surface, borderRadius: `${radius.lg}px`, p: { xs: 3, sm: 4 },
                textAlign: 'center', transition: 'background-color 120ms, border-color 120ms', opacity: disabled ? .6 : 1 }}>
            <PresentationIcon name="upload" sx={{ fontSize: 28, color: color.ink600 }} />
            <Typography sx={{ mt: 1, fontWeight: 600 }}>Choose a file or drop it here</Typography>
            <Typography variant="body2" sx={{ mt: .5, color: 'text.secondary' }} id={`${id}-requirements`}>{requirements ?? 'Accepted file types and size limit will be shown here once they are defined.'}</Typography>
            <Button variant="outlined" sx={{ mt: 2 }} disabled={disabled} onClick={() => input.current?.click()} startIcon={<PresentationIcon name="file" />}>Choose a file</Button>
            <input ref={input} id={id} type="file" hidden accept={accept} aria-describedby={`${id}-requirements`} disabled={disabled}
                onChange={(event) => { const file = event.target.files?.[0]; if (file) onSelect(file); event.target.value = ''; }} />
        </Box>
        <Box role="status" sx={{ mt: 1.5 }}>
            {state.kind === 'uploading' && <Box><Typography variant="body2" sx={{ mb: 1 }}>Uploading {state.fileName}…</Typography><LinearProgress variant={state.percent === undefined ? 'indeterminate' : 'determinate'} value={state.percent} aria-label={`Uploading ${state.fileName}`} /></Box>}
            {state.kind === 'accepted' && <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', bgcolor: color.mint100, borderRadius: `${radius.md}px`, px: 2, py: 1.5 }}>
                <PresentationIcon name="check-circle" sx={{ color: color.mint700 }} /><Box><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{state.fileName}</Typography>{state.detail && <Typography variant="caption">{state.detail}</Typography>}</Box>
            </Box>}
            {state.kind === 'rejected' && <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', bgcolor: color.rose100, borderRadius: `${radius.md}px`, px: 2, py: 1.5 }}>
                <PresentationIcon name="alert-circle" sx={{ color: color.rose700 }} /><Typography>{state.message}</Typography>
            </Box>}
        </Box>
    </Box>;
}
