import { useNavigate, useParams } from 'react-router';
import { useAuth } from '../../../auth/hooks/useAuth';
import { ApiError } from '../../../../shared/types/apiTypes';
import { ErrorView, SystemStateView } from '../../../../shared/components/StatusViews';
import { MarkdownRenderer } from '../../../../shared/components/MarkdownRenderer';
import { FileDownloadButton } from '../../../../shared/components/FileUploadField';
import { MaterialEditorForm, youtubeEmbedAddress } from '../components/MaterialForm';
import type { MaterialEditRequest } from '../components/MaterialForm';
import { PublicationPill } from '../components/PublishToggle';
import { CourseRefreshAlert } from './CourseEditorPage';
import { CourseUnavailable, courseRefused, errorMessage } from './QuestionBankPage';
import { useCourseEditorContext } from '../hooks/useCourseEditor';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown, useAuthoringOperation } from '../hooks/useAssessmentEditor';
import * as api from '../api/instructorCourseApi';
import type { OwnedLesson, OwnedMaterial, OwnedMaterialType } from '../api/instructorCourseApi';
import { mapCourseLessonSkill, unmapCourseLessonSkill } from '../../skill-graph-editor/api/skillGraphApi';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Autocomplete, Box, Button, Chip, Skeleton, TextField, Typography } from '@mui/material';
import { Page, useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { Tile } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { MaterialForm } from '../components/MaterialForm';
import type { LessonDraft, SkillOption } from '../types/courseManagementTypes';
import { radius, shadow, roles } from '../../../../app/theme/theme';

const materialIcon = { text: 'text', file: 'file', external_video: 'video' } as const;
const materialLabel = { text: 'Text', file: 'File', external_video: 'Video link' } as const;

export interface LessonEditorPageProps { courseTitle: string; initial: LessonDraft; skills: SkillOption[]; onSave: (draft: LessonDraft) => void; onLeave: () => void; back?: Crumb; notice?: ReactNode }

/** S18 Lesson editor (Form archetype): title, text, materials, skills and publication; unsaved-change guard. */
export function LessonEditorPage({ courseTitle, initial, skills, onSave, onLeave, back, notice }: LessonEditorPageProps) {
    const [draft, setDraft] = useState(initial), [saved, setSaved] = useState(initial), [materialOpen, setMaterialOpen] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({}), [leaving, setLeaving] = useState(false), [status, setStatus] = useState<string | null>(null);
    const dirty = JSON.stringify(draft) !== JSON.stringify(saved);
    const set = (patch: Partial<LessonDraft>) => { setDraft((current) => ({ ...current, ...patch })); setStatus(null); };
    const save = () => {
        const next: Record<string, string> = {};
        if (!draft.title.trim()) next.title = 'Enter a lesson title.';
        if (draft.skillIds.length === 0) next.skills = 'Map at least one skill to this lesson.';
        setErrors(next);
        if (Object.keys(next).length) { setStatus(null); return; }
        onSave(draft); setSaved(draft); setStatus('Saved');
    };
    return <Page width="form">
        <PageHeader title={saved.title || 'New lesson'} subtitle={courseTitle} back={back ? { ...back, onClick: () => (dirty ? setLeaving(true) : back.onClick?.()) } : undefined} />
        {notice}
        <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); save(); }} sx={{ display: 'grid', gap: 3, maxWidth: 760, pb: 12 }}>
            <Tile component="section" aria-labelledby="lesson-basics" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
                <Typography id="lesson-basics" variant="h3" component="h2">Lesson</Typography>
                <TextField label="Lesson title" value={draft.title} onChange={(event) => set({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required />
                <TextField label="Lesson text" multiline minRows={8} value={draft.content} onChange={(event) => set({ content: event.target.value })} helperText="Optional when materials carry the content. Formatting preview arrives with approved Markdown rendering." />
            </Tile>
            <Tile component="section" aria-labelledby="lesson-materials-edit" padding={{ xs: 3, sm: 4 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
                    <Typography id="lesson-materials-edit" variant="h3" component="h2">Materials</Typography>
                    <Button variant="outlined" onClick={() => setMaterialOpen(true)} startIcon={<PresentationIcon name="plus" />}>Add material</Button>
                </Box>
                {draft.materials.length === 0 ? <Typography sx={{ color: 'text.secondary' }}>No materials yet. Courses need at least two material types across their lessons.</Typography>
                    : <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                        {draft.materials.map((material) => <Box component="li" key={material.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: `${radius.md}px`, bgcolor: roles.bg.canvas }}>
                            <PresentationIcon name={materialIcon[material.kind]} sx={{ fontSize: 20, color: 'text.secondary' }} />
                            <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{material.title}</Typography>
                                <Typography variant="caption" sx={{ color: 'text.secondary' }}>{materialLabel[material.kind]}{material.kind === 'file' ? ` · ${material.fileName}` : material.kind === 'external_video' ? ` · ${material.url}` : ''}</Typography></Box>
                            <CircleAction label={`Remove ${material.title}`} icon="trash" variant="plain" onClick={() => set({ materials: draft.materials.filter((item) => item.id !== material.id) })} />
                        </Box>)}
                    </Box>}
            </Tile>
            <Tile component="section" aria-labelledby="lesson-skills-edit" padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
                <Typography id="lesson-skills-edit" variant="h3" component="h2">Skills and publication</Typography>
                <Autocomplete multiple options={skills} getOptionLabel={(option) => option.name} value={skills.filter((skill) => draft.skillIds.includes(skill.id))}
                    onChange={(_, value) => set({ skillIds: value.map((skill) => skill.id) })} isOptionEqualToValue={(a, b) => a.id === b.id}
                    renderValue={(value, getItemProps) => value.map((option, index) => <Chip {...getItemProps({ index })} key={option.id} label={option.name} />)}
                    renderInput={(params) => <TextField {...params} label="Skills covered" error={!!errors.skills} helperText={errors.skills ?? 'Every published lesson needs at least one skill for adaptive mode.'} required />} />
                <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Visibility</Typography>
                    <SegmentedControl label="Lesson visibility" value={draft.publication} onChange={(publication) => set({ publication })} options={[{ value: 'hidden', label: 'Hidden', icon: 'eye-off' }, { value: 'published', label: 'Published', icon: 'eye' }]} /></Box>
            </Tile>
            {Object.keys(errors).length > 0 && <Alert severity="error">Fix the highlighted fields, then save again.</Alert>}
            <Box role="region" aria-label="Save lesson" sx={{ position: 'sticky', bottom: 16, zIndex: 5, bgcolor: roles.bg.raised, borderRadius: `${radius.pill}px`, boxShadow: `${shadow.raised}, inset 0 0 0 1px ${roles.line.floating}`, p: 1, pl: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Typography role="status" sx={{ flex: 1, color: dirty ? roles.tone.butter.strong : 'text.secondary', fontWeight: 600 }}>{dirty ? 'Unsaved changes' : status ?? 'All changes saved'}</Typography>
                <Button type="submit" variant="contained" disabled={!dirty}>Save changes</Button>
            </Box>
        </Box>
        <MaterialForm open={materialOpen} onCancel={() => setMaterialOpen(false)} onSave={(material) => { set({ materials: [...draft.materials, { ...material, id: `mat-${Date.now().toString(36)}` }] }); setMaterialOpen(false); }} />
        <ConfirmDialog open={leaving} title="Leave without saving?" message="Your unsaved changes to this lesson will be lost." confirmLabel="Leave without saving" cancelLabel="Keep editing" isDestructive
            onCancel={() => setLeaving(false)} onConfirm={() => { setLeaving(false); onLeave(); }} />
    </Page>;
}

/** Production route: the owner tree from the shell is the only Lesson read (D12-3). */
export function ProductionLessonEditorPage() {
    const ctx = useCourseEditorContext()!;
    const { lessonId } = useParams();
    if (ctx.course.error && courseRefused(ctx.course.error)) return <Page><CourseUnavailable status={failureStatus(ctx.course.error)} /></Page>;
    if (!ctx.course.data) return <Page>{ctx.course.status === 'error'
        ? <ErrorView title="We couldn't load this course." message={errorMessage(ctx.course.error)} onRetry={ctx.course.reload} />
        : <Box role="status" aria-label="Loading lesson"><Skeleton height={48} /><Skeleton height={240} /></Box>}</Page>;
    const lesson = ctx.course.data.chapters.flatMap((chapter) => chapter.lessons).find((item) => item.id.toLowerCase() === lessonId?.toLowerCase());
    if (!lesson) return <Page><SystemStateView icon="search" title="Lesson not available" message="This lesson doesn't exist or isn't published." /></Page>;
    return <ProductionLessonForm key={lesson.id} lesson={lesson} />;
}

function ProductionLessonForm({ lesson }: { lesson: OwnedLesson }) {
    const ctx = useCourseEditorContext()!, navigate = useNavigate(), auth = useAuth();
    const { courseId, course, skillGraph } = ctx;
    const ops = useAuthoringOperation(ctx.courseMutations);
    const mappingOps = useAuthoringOperation(ctx.graphMutations);
    const initial = { title: lesson.title, contentMarkdown: lesson.contentMarkdown ?? '' };
    const [draft, setDraft] = useState(initial), [saved, setSaved] = useState(initial);
    const [preview, setPreview] = useState(false), [notice, setNotice] = useState(''), [alert, setAlert] = useState<string | null>(null);
    const [fields, setFields] = useState<Record<string, string>>({});
    const [refusal, setRefusal] = useState<number | null>(null);
    const [material, setMaterial] = useState<{ kind: OwnedMaterialType; initial?: OwnedMaterial; replaceFile?: boolean } | null>(null);
    const [materialDirty, setMaterialDirty] = useState(false), [uploading, setUploading] = useState(false);
    const [confirmation, setConfirmation] = useState<{ title: string; message: string; action: () => void } | null>(null);
    const [leave, setLeave] = useState<{ go: () => void } | null>(null);
    const alive = useRef(false), cancelledUpload = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    const dirty = draft.title !== saved.title || draft.contentMarkdown !== saved.contentMarkdown;
    const reconciling = course.revalidating;
    const locked = ops.busy || reconciling || ctx.skillsRevalidating || !!course.error;
    const current = () => alive.current && auth.isCurrent(auth.generation);
    useEffect(() => {
        // Neither an old in-flight GET nor a failed refresh may overwrite a confirmed save or a draft.
        if (!dirty && !reconciling && !course.error) {
            const next = { title: lesson.title, contentMarkdown: lesson.contentMarkdown ?? '' };
            setDraft(next); setSaved(next);
        }
    }, [lesson, dirty, reconciling, course.error]);
    useEffect(() => {
        if (!dirty && !materialDirty && !ops.pending) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
    }, [dirty, materialDirty, ops.pending]);
    const attemptLeave = (go: () => void) => {
        if (dirty || materialDirty || ops.pending) setLeave({ go }); else go();
    };
    useNavigationGuard((go) => { if (!dirty && !materialDirty && !ops.pending) return false; setLeave({ go }); return true; });
    const handleError = (error: unknown, wasUpload = false) => {
        if (!current()) return;
        if (isCancelled(error) && !cancelledUpload.current) return;
        const status = failureStatus(error);
        if (status === 403 || status === 404) { setRefusal(status); course.reload(); return; }
        if (outcomeUnknown(error) || error instanceof ApiError && error.kind === 'unknown-outcome' || wasUpload && cancelledUpload.current) {
            setAlert(wasUpload ? 'The upload outcome is unknown. Checking the current materials before another upload. Review the returned list before submitting again.' : 'The change could not be confirmed. Checking the current lesson before another change.');
            return;
        }
        setFields(fieldErrorsFrom(error, { title: 'title', contentMarkdown: 'contentMarkdown', textContent: 'textContent', externalVideoUrl: 'externalVideoUrl', file: 'file' }));
        setAlert(status === 413 ? 'The file is larger than the allowed size.' : errorMessage(error));
    };
    async function save() {
        if (locked || !dirty) return;
        if (!draft.title.trim()) { setFields({ title: 'Enter a lesson title.' }); setAlert('Fix the highlighted fields, then save again.'); return; }
        setAlert(null); setNotice(''); setFields({});
        try {
            const response = await ops.run((options) => api.updateCourseLesson(courseId, lesson.id, { title: draft.title.trim(), contentMarkdown: /\S/.test(draft.contentMarkdown) ? draft.contentMarkdown : null }, options));
            const next = { title: response.title, contentMarkdown: response.contentMarkdown ?? '' };
            if (!current()) return;
            setDraft(next); setSaved(next); setNotice('Saved');
        } catch (error) { handleError(error); }
    }
    async function publish(item?: OwnedMaterial) {
        if (locked) return;
        const next = (item?.publicationStatus ?? lesson.publicationStatus) === 'published' ? 'hidden' : 'published';
        setAlert(null); setFields({}); setNotice('');
        try {
            await ops.run<OwnedMaterial | OwnedLesson>((options) => item ? item.materialType === 'file'
                ? api.b5SetFileMaterialPublication(courseId, lesson.id, item.id, next, options)
                : api.setRelationalMaterialPublication(courseId, lesson.id, item, next, options)
                : api.setCourseLessonPublication(courseId, lesson.id, next, options));
            if (!current()) return;
            setNotice(`${item ? 'Material' : 'Lesson'} ${next}.`);
        } catch (error) { handleError(error); }
    }
    const confirmPublication = (item?: OwnedMaterial) => {
        const published = (item?.publicationStatus ?? lesson.publicationStatus) === 'published';
        setConfirmation({ title: `${published ? 'Hide' : 'Publish'} ${item ? 'material' : 'lesson'}?`,
            message: published ? 'Learners will no longer see this content.' : 'Learners can see this content when its course and lesson are published.', action: () => void publish(item) });
    };
    async function saveMaterial(value: MaterialEditRequest): Promise<boolean> {
        if (!material || locked) return false;
        setAlert(null); setFields({}); setNotice(''); cancelledUpload.current = false;
        const isUpload = value.kind === 'file' && (!material.initial || material.replaceFile === true);
        setUploading(isUpload);
        try {
            await ops.run((options) => {
                if (value.kind === 'file') {
                    if (material.replaceFile) return api.b5ReplaceFileMaterialFile(courseId, lesson.id, material.initial!.id, value.file!, options);
                    if (material.initial) return api.b5UpdateFileMaterialTitle(courseId, lesson.id, material.initial.id, value.title, options);
                    return api.b5CreateFileMaterial(courseId, lesson.id, value.title, value.file!, options);
                }
                const payload: api.RelationalMaterialWrite = value.kind === 'text' ? { materialType: 'text', title: value.title, textContent: value.text }
                    : { materialType: 'external_video', title: value.title, externalVideoUrl: value.url };
                return material.initial ? api.updateRelationalMaterial(courseId, lesson.id, material.initial.id, payload, options)
                    : api.createRelationalMaterial(courseId, lesson.id, payload, options);
            });
            if (!current()) return false;
            setNotice('Material saved.'); return true;
        } catch (error) { handleError(error, isUpload); return false; }
        finally { if (current()) setUploading(false); }
    }
    async function toggleMapping(skillId: string, mapped: boolean) {
        if (locked || !skillGraph.graph || skillGraph.error || skillGraph.loading) return;
        setAlert(null); setNotice('');
        try {
            await mappingOps.run((options) => mapped ? unmapCourseLessonSkill(courseId, lesson.id, skillId, options) : mapCourseLessonSkill(courseId, lesson.id, skillId, options));
            if (!current()) return;
            setNotice('Skill mapping saved.');
        } catch (error) {
            if (!current()) return;
            handleError(error);
        }
    }
    if (refusal !== null) return <Page><CourseUnavailable status={refusal} /></Page>;
    const openMaterial = (value: NonNullable<typeof material>) => { setAlert(null); setFields({}); setMaterialDirty(false); setMaterial(value); };
    return <Page width="form">
        <PageHeader title={saved.title} subtitle={course.data!.title}
            back={{ label: 'Content', href: `/teach/courses/${courseId}`, onClick: () => attemptLeave(() => navigate(`/teach/courses/${courseId}`)) }}
            meta={<PublicationPill publication={lesson.publicationStatus} />} actions={<Button variant="outlined" disabled={locked} onClick={() => confirmPublication()}>{lesson.publicationStatus === 'published' ? 'Hide lesson' : 'Publish lesson'}</Button>} />
        <CourseRefreshAlert course={course} />
        {alert && <Alert severity="warning" sx={{ mb: 2 }}>{alert}</Alert>}
        {reconciling && <Box sx={{ mb: 2 }}><Typography role="status">Checking the current lesson…</Typography></Box>}
        <Box sx={{ display: 'grid', gap: 3, minWidth: 0, pb: 12 }}>
            <Tile component="section" aria-labelledby="lesson-edit-heading" sx={{ display: 'grid', gap: 2.5 }}>
                <Typography id="lesson-edit-heading" component="h2" variant="h3">Lesson</Typography>
                <TextField label="Lesson title" required value={draft.title} disabled={locked} error={!!fields.title} helperText={fields.title} onChange={(e) => { setDraft({ ...draft, title: e.target.value }); setNotice(''); }} />
                <SegmentedControl label="Lesson content view" value={preview ? 'preview' : 'edit'} onChange={(value) => setPreview(value === 'preview')} options={[{ value: 'edit', label: 'Content' }, { value: 'preview', label: 'Preview' }]} />
                {preview ? draft.contentMarkdown ? <MarkdownRenderer markdown={draft.contentMarkdown} /> : <Typography>No content yet. Add Markdown in Content.</Typography>
                    : <TextField label="Content (Markdown)" multiline minRows={8} value={draft.contentMarkdown} disabled={locked} error={!!fields.contentMarkdown} helperText={fields.contentMarkdown ?? 'Optional when materials carry the content. Saving replaces the whole lesson; last save wins.'} onChange={(e) => { setDraft({ ...draft, contentMarkdown: e.target.value }); setNotice(''); }} />}
            </Tile>
            <Tile component="section" aria-labelledby="production-materials-heading" sx={{ display: 'grid', gap: 2 }}>
                <Typography id="production-materials-heading" component="h2" variant="h3">Materials</Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                    <Button variant="outlined" disabled={locked} onClick={() => openMaterial({ kind: 'text' })}>Add text</Button>
                    <Button variant="outlined" disabled={locked} onClick={() => openMaterial({ kind: 'external_video' })}>Add video link</Button>
                    <Button variant="outlined" disabled={locked} onClick={() => openMaterial({ kind: 'file' })}>Add PDF file</Button>
                </Box>
                {lesson.materials.length === 0 && <Typography sx={{ color: 'text.secondary' }}>No materials yet. Add text, a video link or a PDF file.</Typography>}
                <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
                    {lesson.materials.map((item) => <Box component="li" key={item.id} sx={{ py: 2.5, borderBottom: `1px solid ${roles.line.hairline}`, display: 'grid', gap: 1.5, minWidth: 0 }}>
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5 }}><Typography component="h3" variant="h3" sx={{ overflowWrap: 'anywhere' }}>{item.title}</Typography><PublicationPill publication={item.publicationStatus} /><Typography variant="caption">{item.materialType === 'file' ? 'PDF file' : item.materialType === 'text' ? 'Text' : 'Video link'}</Typography></Box>
                        {item.materialType === 'text' && <MarkdownRenderer markdown={item.textContent!} />}
                        {item.materialType === 'external_video' && <Typography sx={{ overflowWrap: 'anywhere' }}>{youtubeEmbedAddress(item.externalVideoUrl!) ?? 'Video unavailable. Edit the video link to use an approved embed address.'}</Typography>}
                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'start' }}>
                            <Button variant="outlined" disabled={locked} onClick={() => openMaterial({ kind: item.materialType, initial: item })}>Edit {item.materialType === 'file' ? 'title' : 'material'}</Button>
                            {item.materialType === 'file' && <><Button variant="outlined" disabled={locked} onClick={() => openMaterial({ kind: 'file', initial: item, replaceFile: true })}>Replace file</Button>
                                <FileDownloadButton disabled={locked} load={(signal) => api.b5DownloadMaterialFile(courseId, lesson.id, item.id, { signal, isCurrent: () => auth.isCurrent(auth.generation) })} /></>}
                            <Button disabled={locked} onClick={() => confirmPublication(item)}>{item.publicationStatus === 'published' ? 'Hide material' : 'Publish material'}</Button>
                        </Box>
                    </Box>)}
                </Box>
            </Tile>
            <Tile component="section" aria-labelledby="production-skills-heading" sx={{ display: 'grid', gap: 2 }}>
                <Typography id="production-skills-heading" component="h2" variant="h3">Skills</Typography>
                <Typography>Map at least one skill so adaptive mode can use this lesson.</Typography>
                {skillGraph.loading && <Typography role="status">Loading skills…</Typography>}
                {!!skillGraph.error && <Alert severity="warning" action={<Button onClick={skillGraph.reload}>Try again</Button>}>We couldn&apos;t load the current skills.</Alert>}
                {!skillGraph.loading && !skillGraph.error && skillGraph.graph?.skills.length === 0 && <Typography>No skills yet. Add course skills in the Skills tab.</Typography>}
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>{skillGraph.graph?.skills.map((skill) => {
                    const mapped = skillGraph.graph!.mappings.some((pair) => pair.lessonId.toLowerCase() === lesson.id.toLowerCase() && pair.skillId.toLowerCase() === skill.id.toLowerCase());
                    return <Chip key={skill.id} label={skill.name} component="button" type="button" clickable aria-pressed={mapped} variant={mapped ? 'filled' : 'outlined'} disabled={locked || skillGraph.loading || !!skillGraph.error} onClick={() => void toggleMapping(skill.id, mapped)} sx={{ minHeight: 44 }} />;
                })}</Box>
            </Tile>
            <Box role="region" aria-label="Save lesson" sx={{ position: 'sticky', bottom: 16, zIndex: 5, bgcolor: roles.bg.raised, borderRadius: `${radius.lg}px`, boxShadow: shadow.raised, p: 2, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 2 }}>
                <Typography role="status" sx={{ flex: '1 1 120px', color: 'text.secondary' }}>{ops.pending ? uploading ? 'Uploading…' : 'Saving…' : dirty ? 'Unsaved changes' : notice || 'All changes saved'}</Typography>
                <Button variant="contained" disabled={locked || !dirty} onClick={() => void save()}>Save changes</Button>
            </Box>
        </Box>
        {material && <MaterialEditorForm {...material} locked={locked} uploading={uploading} message={alert} fields={fields} onDirty={setMaterialDirty} onSubmit={saveMaterial}
            onCancelUpload={() => { cancelledUpload.current = true; ops.cancel(); }} onCancel={() => { setMaterial(null); setMaterialDirty(false); }} />}
        <ConfirmDialog open={confirmation !== null} title={confirmation?.title ?? ''} message={confirmation?.message ?? ''} confirmLabel={confirmation?.title.replace('?', '') ?? 'Confirm'} onCancel={() => setConfirmation(null)} onConfirm={() => { const action = confirmation!.action; setConfirmation(null); action(); }} />
        <ConfirmDialog open={leave !== null} title="Leave without saving?" message="Your unsaved changes will be lost. An upload already sent may still complete." confirmLabel="Leave" cancelLabel="Keep editing" isDestructive onCancel={() => setLeave(null)} onConfirm={() => { const go = leave!.go; setLeave(null); go(); }} />
    </Page>;
}
