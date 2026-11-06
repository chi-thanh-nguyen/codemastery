import { useState } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material';
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
