import { useState } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { FileUploadField } from '../../../../shared/components/FileUploadField';
import type { UploadState } from '../../../../shared/components/FileUploadField';
import type { MaterialDraft, MaterialInput } from '../types/courseManagementTypes';

type Kind = MaterialDraft['kind'];
/** Add a lesson material: text, file (CT09 pending) or external video link (embed only). */
export function MaterialForm({ open, onCancel, onSave }: { open: boolean; onCancel: () => void; onSave: (material: MaterialInput) => void }) {
    const [kind, setKind] = useState<Kind>('text'), [title, setTitle] = useState(''), [text, setText] = useState(''), [url, setUrl] = useState('');
    const [file, setFile] = useState<UploadState>({ kind: 'idle' }), [errors, setErrors] = useState<Record<string, string>>({});
    const reset = () => { setKind('text'); setTitle(''); setText(''); setUrl(''); setFile({ kind: 'idle' }); setErrors({}); };
    const save = () => {
        const next: Record<string, string> = {};
        if (!title.trim()) next.title = 'Enter a title.';
        if (kind === 'text' && !text.trim()) next.text = 'Enter the text.';
        if (kind === 'external_video' && !/^https:\/\/\S+$/.test(url.trim())) next.url = 'Enter a full https:// link.';
        if (kind === 'file' && file.kind !== 'accepted') next.file = 'Choose a file.';
        setErrors(next);
        if (Object.keys(next).length) return;
        onSave(kind === 'text' ? { kind, title: title.trim(), text } : kind === 'external_video' ? { kind, title: title.trim(), url: url.trim() } : { kind, title: title.trim(), fileName: file.kind === 'accepted' ? file.fileName : '' });
        reset();
    };
    return <Dialog open={open} onClose={() => { reset(); onCancel(); }} fullWidth maxWidth="sm" aria-labelledby="material-form-title">
        <DialogTitle id="material-form-title">Add material</DialogTitle>
        <DialogContent>
            <Box sx={{ display: 'grid', gap: 2.5, pt: 1 }}>
                <SegmentedControl label="Material type" value={kind} onChange={setKind} options={[{ value: 'text', label: 'Text', icon: 'text' }, { value: 'file', label: 'File', icon: 'file' }, { value: 'external_video', label: 'Video link', icon: 'video' }]} />
                <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} error={!!errors.title} helperText={errors.title} required />
                {kind === 'text' && <TextField label="Text" multiline minRows={5} value={text} onChange={(event) => setText(event.target.value)} error={!!errors.text} helperText={errors.text ?? 'Plain text. Markdown formatting is shown once Markdown rendering is approved.'} required />}
                {kind === 'external_video' && <TextField label="Video link" type="url" value={url} onChange={(event) => setUrl(event.target.value)} error={!!errors.url} helperText={errors.url ?? 'CodeMastery embeds external videos; it does not host or transcode them.'} required />}
                {kind === 'file' && <Box><FileUploadField label="File" state={file} onSelect={(selected) => setFile({ kind: 'accepted', fileName: selected.name })} />
                    {errors.file && <Box role="alert" sx={{ color: 'error.main', fontSize: '0.8125rem', mt: 1 }}>{errors.file}</Box>}</Box>}
            </Box>
        </DialogContent>
        <DialogActions><Button variant="outlined" onClick={() => { reset(); onCancel(); }}>Cancel</Button><Button variant="contained" onClick={save}>Add material</Button></DialogActions>
    </Dialog>;
}

// Production FE14 form. The preview form above remains isolated from REST and bytes.
export interface MaterialEditRequest {
    kind: Kind;
    title: string;
    text: string;
    url: string;
    file: File | null;
}
export interface MaterialEditorFormProps {
    kind: Kind;
    initial?: import('../api/instructorCourseApi').OwnedMaterial;
    replaceFile?: boolean;
    locked: boolean;
    uploading: boolean;
    message: string | null;
    fields: Record<string, string>;
    onSubmit: (value: MaterialEditRequest) => Promise<boolean>;
    onCancel: () => void;
    onCancelUpload: () => void;
    onDirty: (dirty: boolean) => void;
}

/** Canonical CT37.embed address, checked with the URL parser and exact serialization. */
export function youtubeEmbedAddress(input: string): string | null {
    const value = input.replace(/^ +| +$/g, '');
    try {
        const url = new URL(value);
        if (url.href !== value || url.origin !== 'https://www.youtube-nocookie.com' || url.username || url.password || url.port || url.search || url.hash ||
            !/^\/embed\/[A-Za-z0-9_-]+$/.test(url.pathname) || !/^https:\/\/www\.youtube-nocookie\.com\/embed\/[A-Za-z0-9_-]+$/.test(value)) return null;
        return value;
    } catch { return null; }
}

export function MaterialEditorForm(props: MaterialEditorFormProps) {
    const { kind, initial, replaceFile, locked, uploading, message, fields, onSubmit, onCancel, onCancelUpload, onDirty } = props;
    const [title, setTitle] = useState(initial?.title ?? ''), [text, setText] = useState(initial?.textContent ?? ''), [url, setUrl] = useState(initial?.externalVideoUrl ?? '');
    const [file, setFile] = useState<File | null>(null), [errors, setErrors] = useState<Record<string, string>>({}), [confirm, setConfirm] = useState(false);
    const update = (change: () => void) => { change(); onDirty(true); setErrors({}); };
    const submit = async () => {
        const next: Record<string, string> = {};
        if (!replaceFile && !title.trim()) next.title = 'Enter a title.';
        if (kind === 'text' && !text.trim()) next.textContent = 'Enter the text.';
        if (kind === 'external_video' && !youtubeEmbedAddress(url)) next.externalVideoUrl = 'Enter a YouTube privacy-enhanced embed address.';
        if (kind === 'file' && (!initial || replaceFile) && !file) next.file = 'Choose a PDF file.';
        setErrors(next);
        if (Object.keys(next).length) return;
        if (replaceFile && !confirm) { setConfirm(true); return; }
        setConfirm(false);
        if (await onSubmit({ kind, title: title.trim(), text, url: youtubeEmbedAddress(url) ?? url, file })) onCancel();
    };
    const field = (name: string) => fields[name] ?? errors[name];
    return <Dialog open onClose={() => { if (!locked) onCancel(); }} fullWidth maxWidth="sm" aria-labelledby="production-material-title">
        <DialogTitle id="production-material-title">{replaceFile ? 'Replace file' : initial ? 'Edit material' : kind === 'text' ? 'Add text' : kind === 'file' ? 'Add PDF file' : 'Add video link'}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2.5 }}>
            {message && <Alert severity="warning">{message}</Alert>}
            {Object.keys(errors).length > 0 && <Alert severity="error">Fix the highlighted fields, then save again.</Alert>}
            {!replaceFile && <TextField label="Title" value={title} onChange={(e) => update(() => setTitle(e.target.value))} required disabled={locked} error={!!field('title')} helperText={field('title')} sx={{ mt: 1 }} />}
            {kind === 'text' && <TextField label="Text (Markdown)" multiline minRows={6} value={text} onChange={(e) => update(() => setText(e.target.value))} required disabled={locked} error={!!field('textContent')} helperText={field('textContent')} />}
            {kind === 'external_video' && <TextField label="Video link" value={url} onChange={(e) => update(() => setUrl(e.target.value))} required disabled={locked} error={!!field('externalVideoUrl')} helperText={field('externalVideoUrl') ?? 'Use the embed address that starts with https://www.youtube-nocookie.com/embed/'} />}
            {kind === 'file' && (!initial || replaceFile) && <Box><FileUploadField label="PDF file" requirements="PDF file. The server checks the file type and contents." accept=".pdf" disabled={locked}
                state={uploading ? { kind: 'uploading', fileName: file?.name ?? '' } : file ? { kind: 'accepted', fileName: file.name, detail: 'Selected; not yet uploaded.' } : { kind: 'idle' }}
                onReject={(error) => { setFile(null); setErrors({ file: error }); }}
                onSelect={(selected) => {
                    if (!/\.pdf$/i.test(selected.name) || selected.size === 0) { setFile(null); setErrors({ file: 'Choose a non-empty PDF file. The server validates its contents.' }); return; }
                    update(() => setFile(selected));
                }} />{field('file') && <Typography role="alert" sx={{ color: 'error.main' }}>{field('file')}</Typography>}</Box>}
        </DialogContent>
        <DialogActions sx={{ flexWrap: 'wrap', gap: 1 }}>
            {uploading ? <Button onClick={onCancelUpload}>Cancel upload</Button> : <Button onClick={onCancel} disabled={locked}>Cancel</Button>}
            <Button variant="contained" disabled={locked} onClick={() => void submit()}>{initial ? 'Save material' : 'Add material'}</Button>
        </DialogActions>
        <ConfirmDialog open={confirm} title="Replace file?" message={`The PDF for “${initial?.title}” will be replaced.`} confirmLabel="Replace file" onCancel={() => setConfirm(false)} onConfirm={() => void submit()} />
    </Dialog>;
}
