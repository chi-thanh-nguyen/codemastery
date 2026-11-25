import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Autocomplete, Box, Button, Chip, IconButton, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { Tile } from '../../../../shared/components/Tile';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { EmptyView, ErrorView, LoadingView } from '../../../../shared/components/StatusViews';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { radius, roles } from '../../../../app/theme/theme';
import { ApiError } from '../../../../shared/types/apiTypes';
import { SkillGraphCanvas } from '../components/SkillGraphCanvas';
import { SkillForm } from '../components/SkillForm';
import { LessonSkillMappingTable } from '../components/LessonSkillMappingTable';
import { AdaptiveSettingsPanel } from '../components/AdaptiveSettingsPanel';
import type { EditableSkill, GraphSkill, MappingLesson, ReadinessCheck } from '../types/skillGraphTypes';
import { useSkillGraphMutations } from '../hooks/useSkillGraph';
import type { OwnedSkillGraph } from '../api/skillGraphApi';
import { useCourseEditorContext } from '../../course-management/hooks/useCourseEditor';
import { failureStatus, isCancelled, outcomeUnknown } from '../../course-management/hooks/useAssessmentEditor';
import { PublicationPill, errorMessage } from '../../course-management/pages/QuestionBankPage';

/*
 * S20 Skill graph editor (DESIGN.md r12, D12-3; CT05 representation/mutation/mapping/bindings).
 * `CourseSkillsSection` powers `/teach/courses/:courseId/skills` inside the Course Editor shell,
 * sharing `getOwnedCourse` and `getOwnedSkillGraph` with S18 and S19. Adaptive-settings editing
 * (`getCourseAdaptiveSettings` / `replaceCourseAdaptiveSettings`) stays deferred until AD08 (#166)
 * delivers the backend endpoint.
 */

type ProductionSkillTab = 'graph' | 'mapping';

export function CourseSkillsSection() {
    const { courseId, course, skillGraph, notifySkillsChanged } = useCourseEditorContext()!;
    const mutations = useSkillGraphMutations(courseId);
    const [tab, setTab] = useState<ProductionSkillTab>('graph');
    // The open aside form: `skillId` null adds a skill. The key changes only for a deliberate new opening, so a
    // partially saved draft (for example a created skill whose prerequisite was refused) stays on screen.
    const [form, setForm] = useState<{ key: number; skillId: string | null } | null>(null);
    // The created skill until the re-read graph lists it.
    const [created, setCreated] = useState<GraphSkill | null>(null);
    const [formError, setFormError] = useState<string | null>(null);
    const [confirmDelete, setConfirmDelete] = useState<GraphSkill | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const openForm = (skillId: string | null) => { setFormError(null); setForm({ key: Date.now(), skillId }); };

    const graph = skillGraph.graph;
    // CT05 returns no question references, so no question count is shown (DESIGN.md shared screen rules).
    const editableSkills = useMemo<GraphSkill[]>(() => {
        if (!graph) return [];
        const prereqBySkill = new Map<string, string[]>();
        for (const edge of graph.prerequisites) {
            const list = prereqBySkill.get(edge.skillId) ?? [];
            list.push(edge.prerequisiteSkillId);
            prereqBySkill.set(edge.skillId, list);
        }
        return [...graph.skills]
            .sort((a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }) || a.id.localeCompare(b.id))
            .map((skill) => ({
                id: skill.id,
                name: skill.name,
                description: skill.description ?? '',
                prerequisiteIds: prereqBySkill.get(skill.id) ?? [],
            }));
    }, [graph]);

    const formSkill = form?.skillId
        ? editableSkills.find((skill) => skill.id === form.skillId) ?? (created?.id === form.skillId ? created : null)
        : null;
    const selectedId = form?.skillId ?? null;

    if (skillGraph.loading && !graph) {
        return <LoadingView message="Loading skills…" variant="block" />;
    }
    if (skillGraph.error && !graph) {
        return <ErrorView title="Skills couldn't be loaded." message={errorMessage(skillGraph.error)} onRetry={skillGraph.reload} />;
    }

    const nameOf = (skillId: string) => editableSkills.find((skill) => skill.id === skillId)?.name ?? 'the selected skill';

    /**
     * CT05 has no multi-step transaction: the skill write and each prerequisite edge commit on their own. The
     * steps run in order (edge additions first, because a cycle refusal is their expected failure, then edge
     * removals, then name and description), stop at the first failure without any compensating write, and the
     * outcome names what was saved and what was refused. The graph is re-read after every attempt and the
     * draft stays on screen; nothing is retried.
     */
    async function saveSkill(draft: { id?: string; name: string; description: string; prerequisiteIds: string[] }) {
        setFormError(null);
        setNotice(null);
        const payload = {
            name: draft.name.trim(),
            description: /\S/.test(draft.description) ? draft.description.trim() : null,
        };
        const saved: string[] = [];
        let skillId = draft.id;
        let skillName = payload.name;
        const steps: { label: string; run: () => Promise<unknown>; refusal: (status: number | undefined) => string }[] = [];
        const current = formSkill;
        const existing = new Set(current?.prerequisiteIds ?? []);
        const desired = new Set(draft.prerequisiteIds);
        const metadataChanged = !current || current.name !== payload.name || (current.description || null) !== payload.description;
        if (skillId) {
            // Steps for an existing skill; a new skill's prerequisite steps are added after it is created.
            for (const prerequisiteId of draft.prerequisiteIds.filter((id) => !existing.has(id))) {
                steps.push(prerequisiteStep(() => skillId!, prerequisiteId));
            }
            for (const prerequisiteId of [...existing].filter((id) => !desired.has(id))) {
                const prerequisite = nameOf(prerequisiteId);
                steps.push({ label: `removed ${prerequisite} as a prerequisite`,
                    run: () => mutations.removePrerequisite(skillId!, prerequisiteId),
                    refusal: () => `${prerequisite} couldn't be removed as a prerequisite.` });
            }
            if (metadataChanged) {
                steps.push({ label: 'the name and description', run: () => mutations.updateSkill(skillId!, payload),
                    refusal: () => "The name and description couldn't be saved." });
            }
        }
        function prerequisiteStep(target: () => string, prerequisiteId: string) {
            const prerequisite = nameOf(prerequisiteId);
            return {
                label: `${prerequisite} as a prerequisite`,
                run: () => mutations.addPrerequisite(target(), prerequisiteId),
                // CT05 addCoursePrerequisite: 409 is exactly a new edge that would close a cycle (self-edges are 400).
                refusal: (status: number | undefined) => status === 409
                    ? `${prerequisite} can't be a prerequisite of ${skillName}, because it would create a cycle of prerequisites.`
                    : `${prerequisite} couldn't be added as a prerequisite.`,
            };
        }
        try {
            if (!skillId) {
                const result = await mutations.createSkill(payload);
                skillId = result.id;
                skillName = result.name;
                saved.push(`the skill ${result.name}`);
                const fallback: GraphSkill = { id: result.id, name: result.name, description: result.description ?? '', prerequisiteIds: [] };
                setCreated(fallback);
                // Keep the same form (and its draft) but bind it to the created skill, so a further save edits it
                // instead of creating a duplicate.
                setForm((value) => value ? { key: value.key, skillId: result.id } : value);
                for (const prerequisiteId of draft.prerequisiteIds) steps.push(prerequisiteStep(() => skillId!, prerequisiteId));
            }
            for (let index = 0; index < steps.length; index += 1) {
                const step = steps[index];
                try {
                    await step.run();
                    saved.push(step.label);
                } catch (error) {
                    if (isCancelled(error)) return;
                    const notAttempted = steps.length - index - 1;
                    const what = outcomeUnknown(error)
                        ? `We couldn't confirm whether this change was saved: ${step.label}.`
                        : failureStatus(error) === 404
                            ? `${step.refusal(404)} A skill it needs is no longer available.`
                            : step.refusal(failureStatus(error));
                    setFormError([
                        saved.length ? `Saved: ${saved.join('; ')}.` : 'Nothing was saved.',
                        what,
                        notAttempted ? `${notAttempted} other ${notAttempted === 1 ? 'change was' : 'changes were'} not attempted.` : '',
                        'Your edits are still here.',
                    ].filter(Boolean).join(' '));
                    notifySkillsChanged();
                    return;
                }
            }
            notifySkillsChanged();
            if (!draft.id) {
                setNotice('Skill added.');
                // Every requested edge committed: the opened form starts from the saved state until the re-read lands.
                setCreated((value) => value && { ...value, prerequisiteIds: [...draft.prerequisiteIds] });
                setForm({ key: Date.now(), skillId });
            } else {
                setNotice(steps.length ? 'Skill saved.' : 'No changes to save.');
            }
        } catch (error) {
            // Only the creation itself reaches here: nothing was saved, or its outcome is unknown.
            if (isCancelled(error)) return;
            setFormError(outcomeUnknown(error)
                ? "We couldn't confirm whether the skill was added. Check the skill list before adding it again."
                : error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.');
            notifySkillsChanged();
        }
    }

    async function removeSkill(skill: GraphSkill) {
        setConfirmDelete(null);
        setFormError(null);
        setNotice(null);
        try {
            await mutations.deleteSkill(skill.id);
            setForm(null);
            notifySkillsChanged();
            setNotice('Skill deleted.');
        } catch (error) {
            if (isCancelled(error)) return;
            if (outcomeUnknown(error)) notifySkillsChanged();
            setFormError(error instanceof ApiError
                ? (failureStatus(error) === 409
                    ? `This skill can't be deleted while it is used by prerequisites, lessons, questions or learner mastery. ${error.message}`
                    : error.message)
                : 'Something went wrong on our side. Try again in a moment.');
        }
    }

    const showForm = form !== null && (form.skillId === null || formSkill !== null);
    return <Box sx={{ display: 'grid', gap: 2.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <Typography component="h2" variant="h2">Skills</Typography>
            {tab === 'graph' && <Button variant="contained" disabled={mutations.busy}
                onClick={() => openForm(null)}
                startIcon={<PresentationIcon name="plus" />}>Add skill</Button>}
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <SegmentedControl label="Skill editor sections" value={tab}
                onChange={(next) => { setTab(next); setFormError(null); setNotice(null); }}
                options={[{ value: 'graph', label: 'Skill graph' }, { value: 'mapping', label: 'Lesson mapping' }]} />
        </Box>
        {notice && <Typography role="status" sx={{ color: 'text.secondary' }}>{notice}</Typography>}
        {/* U2: a failed re-read with an unknown outcome keeps the last graph and the open skill draft (D12-3 page alert). */}
        {skillGraph.error ? <Alert severity="error" action={<Button size="small" variant="text" onClick={skillGraph.reload}>Try again</Button>}>Skills couldn&apos;t be loaded.</Alert> : null}
        {tab === 'graph' && (editableSkills.length === 0 && !showForm
            ? <EmptyView icon="graph" headingLevel="h3" title="No skills yet"
                message="Add the skills this course teaches, then link prerequisites."
                action={<Button variant="outlined" onClick={() => openForm(null)}>Add skill</Button>} />
            : <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1fr) 360px' }, alignItems: 'start' }}>
                <Box sx={{ display: 'grid', gap: 1.5 }}>
                    <SkillGraphCanvas skills={editableSkills} selectedId={selectedId}
                        onSelect={(id) => (id ? openForm(id) : setForm(null))}
                        label={`Skill graph for ${course.data?.title ?? 'course'}`} />
                    <Box component="ul" aria-label="Course skills" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {editableSkills.map((skill) => <Box component="li" key={skill.id}>
                            <Chip
                                label={skill.name}
                                onClick={() => (skill.id === selectedId ? setForm(null) : openForm(skill.id))}
                                variant={skill.id === selectedId ? 'filled' : 'outlined'}
                                color={skill.id === selectedId ? 'primary' : 'default'} />
                        </Box>)}
                    </Box>
                </Box>
                {showForm
                    ? <Tile sx={{ display: 'grid', gap: 2 }}>
                        <ProductionSkillForm
                            key={form.key}
                            skill={formSkill}
                            skills={editableSkills}
                            busy={mutations.busy}
                            serverError={formError}
                            onSave={(draft) => void saveSkill(draft)}
                            onCancel={() => { setForm(null); setFormError(null); }}
                            onDelete={formSkill ? () => setConfirmDelete(formSkill) : undefined} />
                    </Tile>
                    : <EmptyView compact icon="graph" headingLevel="h3" title="Select a skill"
                        message="Select a skill to edit it and its prerequisites, or add a new skill." />}
            </Box>)}
        {tab === 'mapping' && graph && course.data && <ProductionLessonMapping
            courseChapters={course.data.chapters}
            graph={graph}
            skills={editableSkills}
            mutations={mutations}
            onCourseReload={course.reload}
            onGraphChanged={notifySkillsChanged} />}
        <ConfirmDialog open={confirmDelete !== null} isDestructive title="Delete this skill?"
            message="This can't be undone." confirmLabel="Delete skill"
            onCancel={() => setConfirmDelete(null)}
            onConfirm={() => { if (confirmDelete) void removeSkill(confirmDelete); }} />
    </Box>;
}

function ProductionSkillForm({ skill, skills, busy, serverError, onSave, onCancel, onDelete }: {
    skill: GraphSkill | null;
    skills: GraphSkill[];
    busy: boolean;
    serverError: string | null;
    onSave: (draft: { id?: string; name: string; description: string; prerequisiteIds: string[] }) => void;
    onCancel: () => void;
    onDelete?: () => void;
}) {
    const [name, setName] = useState(skill?.name ?? '');
    const [description, setDescription] = useState(skill?.description ?? '');
    const [prerequisites, setPrerequisites] = useState<string[]>(skill?.prerequisiteIds ?? []);
    const [error, setError] = useState<string | null>(null);
    const options = skills.filter((item) => item.id !== skill?.id);

    return <Box component="form" noValidate sx={{ display: 'grid', gap: 2.5 }}
        onSubmit={(event) => {
            event.preventDefault();
            if (!/\S/.test(name)) { setError('Enter a skill name.'); return; }
            setError(null);
            onSave({ id: skill?.id, name: name.trim(), description, prerequisiteIds: prerequisites });
        }}>
        <Typography variant="h4" component="h3">{skill ? `Edit ${skill.name}` : 'Add skill'}</Typography>
        {serverError && <Alert severity="error" role="alert">{serverError}</Alert>}
        <TextField label="Skill name" value={name} required disabled={busy}
            onChange={(event) => { setName(event.target.value); setError(null); }}
            error={!!error} helperText={error} />
        <TextField label="Description (optional)" multiline minRows={2} value={description} disabled={busy}
            onChange={(event) => setDescription(event.target.value)} />
        <Autocomplete multiple disabled={busy} options={options} getOptionLabel={(option) => option.name}
            value={options.filter((item) => prerequisites.includes(item.id))}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            onChange={(_, value) => setPrerequisites(value.map((item) => item.id))}
            renderValue={(value, getItemProps) => value.map((option, index) => <Chip {...getItemProps({ index })} key={option.id} label={option.name} />)}
            renderInput={(params) => <TextField {...params} label="Prerequisites"
                helperText="Skills a learner should master first. Prerequisites guide; they never lock lessons." />} />
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
            <Button type="submit" variant="contained" disabled={busy}>{skill ? 'Save skill' : 'Add skill'}</Button>
            <Button variant="outlined" onClick={onCancel} disabled={busy}>Cancel</Button>
            {onDelete && <Button variant="outlined" color="error" onClick={onDelete} disabled={busy} sx={{ ml: 'auto' }} startIcon={<PresentationIcon name="trash" />}>Delete skill</Button>}
        </Box>
    </Box>;
}

function ProductionLessonMapping({ courseChapters, graph, skills, mutations, onCourseReload, onGraphChanged }: {
    courseChapters: { id: string; title: string; position: number; lessons: { id: string; title: string; position: number; publicationStatus: 'hidden' | 'published' }[] }[];
    graph: OwnedSkillGraph;
    skills: GraphSkill[];
    mutations: ReturnType<typeof useSkillGraphMutations>;
    onCourseReload: () => void;
    onGraphChanged: () => void;
}) {
    const rereadLessons = useRef(new Set<string>());
    const [savingLessonId, setSavingLessonId] = useState<string | null>(null);
    const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
    const [menuTarget, setMenuTarget] = useState<{ lessonId: string; anchor: HTMLElement } | null>(null);
    // Focus to restore after a map or unmap: the row's controls are disabled while it saves and a removed chip
    // unmounts, so focus would otherwise fall back to <body>. When the change succeeded (or its outcome is
    // unknown) focus waits for the re-read graph, so it lands on a control that still exists.
    const [pendingFocus, setPendingFocus] = useState<{ lessonId: string; graph: OwnedSkillGraph | null } | null>(null);
    const rows = useRef(new Map<string, HTMLElement>());
    useEffect(() => {
        if (!pendingFocus || savingLessonId !== null || mutations.busy) return;
        if (pendingFocus.graph !== null && graph === pendingFocus.graph) return;
        const row = rows.current.get(pendingFocus.lessonId);
        setPendingFocus(null);
        const target = row?.querySelector<HTMLElement>('[data-mapping-add]:not([disabled])')
            ?? row?.querySelector<HTMLElement>('[data-mapping-remove]:not([disabled])')
            ?? row?.querySelector<HTMLElement>('[data-mapping-title]');
        target?.focus();
    }, [pendingFocus, savingLessonId, mutations.busy, graph]);

    const allLessonIds = useMemo(() => new Set(
        courseChapters.flatMap((chapter) => chapter.lessons.map((lesson) => lesson.id.toLowerCase())),
    ), [courseChapters]);

    // S20 D12-3: if a mapping names a lesson missing from the course tree, re-read the course once.
    useEffect(() => {
        const missing = graph.mappings
            .map((item) => item.lessonId.toLowerCase())
            .filter((lessonId) => !allLessonIds.has(lessonId) && !rereadLessons.current.has(lessonId));
        if (!missing.length) return;
        missing.forEach((lessonId) => rereadLessons.current.add(lessonId));
        onCourseReload();
    }, [graph.mappings, allLessonIds, onCourseReload]);

    const totalLessons = courseChapters.reduce((sum, chapter) => sum + chapter.lessons.length, 0);
    if (totalLessons === 0) {
        return <EmptyView icon="book" headingLevel="h3" title="This course has no lessons yet. Add lessons in the Content tab."
            message="Lessons appear here in canonical course order." />;
    }
    if (skills.length === 0) {
        return <EmptyView icon="graph" headingLevel="h3" title="Add a skill in the Graph tab before mapping lessons."
            message="Every published lesson needs at least one mapped skill before adaptive mode can be enabled." />;
    }

    const mappedByLesson = new Map<string, GraphSkill[]>();
    const skillById = new Map(skills.map((skill) => [skill.id.toLowerCase(), skill]));
    for (const pair of graph.mappings) {
        const skill = skillById.get(pair.skillId.toLowerCase());
        if (!skill) continue;
        const key = pair.lessonId.toLowerCase();
        const current = mappedByLesson.get(key) ?? [];
        current.push(skill);
        mappedByLesson.set(key, current);
    }

    async function mutateMapping(lessonId: string, skillId: string, mode: 'map' | 'unmap') {
        setMenuTarget(null);
        setSavingLessonId(lessonId);
        setRowErrors((current) => { const next = { ...current }; delete next[lessonId]; return next; });
        try {
            if (mode === 'map') await mutations.mapLesson(lessonId, skillId);
            else await mutations.unmapLesson(lessonId, skillId);
            setPendingFocus({ lessonId, graph });
            onGraphChanged();
        } catch (error) {
            if (isCancelled(error)) return;
            if (outcomeUnknown(error)) {
                setPendingFocus({ lessonId, graph });
                onGraphChanged();
            } else {
                setPendingFocus({ lessonId, graph: null });
                setRowErrors((current) => ({
                    ...current,
                    [lessonId]: error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.',
                }));
            }
        } finally {
            setSavingLessonId(null);
        }
    }

    const activeLessonMapped = menuTarget ? new Set((mappedByLesson.get(menuTarget.lessonId.toLowerCase()) ?? []).map((item) => item.id)) : new Set<string>();
    const availableSkills = skills.filter((skill) => !activeLessonMapped.has(skill.id));

    return <Box sx={{ display: 'grid', gap: 3 }}>
        {courseChapters.filter((chapter) => chapter.lessons.length > 0).map((chapter) => (
            <Box component="section" key={chapter.id} aria-labelledby={`map-chapter-${chapter.id}`} sx={{ display: 'grid', gap: 1.5 }}>
                <Typography id={`map-chapter-${chapter.id}`} component="h3" variant="h3">Chapter {chapter.position}: {chapter.title}</Typography>
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    {chapter.lessons.map((lesson) => {
                        const mapped = mappedByLesson.get(lesson.id.toLowerCase()) ?? [];
                        const rowBusy = savingLessonId === lesson.id || mutations.busy;
                        const unmappedSkills = skills.filter((skill) => !mapped.some((entry) => entry.id === skill.id));
                        return <Box component="li" key={lesson.id}
                            ref={(element: HTMLElement | null) => { if (element) rows.current.set(lesson.id, element); else rows.current.delete(lesson.id); }} sx={{
                            display: 'grid', gap: 1.5, px: { xs: 2, sm: 3 }, py: 2,
                            borderBottom: `1px solid ${roles.line.hairline}`, '&:last-child': { borderBottom: 0 },
                        }}>
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}>
                                <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
                                    <Typography data-mapping-title tabIndex={-1} sx={{ fontWeight: 600 }}>{lesson.title}</Typography>
                                    <PublicationPill published={lesson.publicationStatus === 'published'} />
                                    {mapped.length === 0 && (
                                        <StatusPill size="tag" tone={lesson.publicationStatus === 'published' ? 'butter' : 'neutral'}>
                                            Not mapped
                                        </StatusPill>
                                    )}
                                </Box>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    {savingLessonId === lesson.id && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>Saving…</Typography>}
                                    <Button data-mapping-add size="small" variant="outlined" disabled={rowBusy || unmappedSkills.length === 0}
                                        onClick={(event) => setMenuTarget({ lessonId: lesson.id, anchor: event.currentTarget })}
                                        startIcon={<PresentationIcon name="plus" />}>
                                        Add skill
                                    </Button>
                                </Box>
                            </Box>
                            {mapped.length > 0 && <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                                {mapped.map((skill) => <Box
                                    key={skill.id}
                                    sx={{
                                        display: 'inline-flex', alignItems: 'center', gap: 0.5,
                                        pl: 1.5, pr: 0.5, py: 0.25,
                                        borderRadius: `${radius.pill}px`,
                                        bgcolor: roles.bg.fill,
                                        border: `1px solid ${roles.line.hairline}`,
                                    }}>
                                    <Typography variant="body2">{skill.name}</Typography>
                                    <IconButton data-mapping-remove size="small" disabled={rowBusy}
                                        aria-label={`Remove ${skill.name} from ${lesson.title}`}
                                        onClick={() => void mutateMapping(lesson.id, skill.id, 'unmap')}>
                                        <PresentationIcon name="close" sx={{ fontSize: 16 }} />
                                    </IconButton>
                                </Box>)}
                            </Box>}
                            {rowErrors[lesson.id] && <Alert severity="error">{rowErrors[lesson.id]}</Alert>}
                        </Box>;
                    })}
                </Box>
            </Box>
        ))}
        <Menu anchorEl={menuTarget?.anchor ?? null} open={menuTarget !== null} onClose={() => setMenuTarget(null)}>
            {availableSkills.map((skill) => <MenuItem key={skill.id}
                onClick={() => { if (menuTarget) void mutateMapping(menuTarget.lessonId, skill.id, 'map'); }}>
                {skill.name}
            </MenuItem>)}
        </Menu>
    </Box>;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

export type SkillEditorTab = 'graph' | 'mapping' | 'adaptive';
export interface SkillGraphEditorPageProps {
    courseTitle: string;
    tab: SkillEditorTab;
    onTabChange: (tab: SkillEditorTab) => void;
    skills: EditableSkill[];
    selectedId: string | null;
    onSelect: (id: string | null) => void;
    adding: boolean;
    onAdd: () => void;
    onSaveSkill: (skill: { id?: string; name: string; description: string; prerequisiteIds: string[] }) => void;
    serverError?: string | null;
    lessons: MappingLesson[];
    onMappingChange: (lessonId: string, skillIds: string[]) => void;
    readiness: ReadinessCheck[];
    adaptiveEnabled: boolean;
    onAdaptiveToggle: (next: boolean) => void;
    status?: ReactNode;
}

/** S20 Skill graph editor preview component. */
export function SkillGraphEditorPage(props: SkillGraphEditorPageProps) {
    const { courseTitle, tab, onTabChange, skills, selectedId, onSelect, adding, onAdd, onSaveSkill, serverError, lessons, onMappingChange, readiness, adaptiveEnabled, onAdaptiveToggle, status } = props;
    const selected = skills.find((skill) => skill.id === selectedId) ?? null;
    return <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 3 }}>
            <SegmentedControl label="Skill editor sections" value={tab} onChange={onTabChange} options={[{ value: 'graph', label: 'Skill graph' }, { value: 'mapping', label: 'Lesson mapping' }, { value: 'adaptive', label: 'Adaptive mode' }]} />
            {tab === 'graph' && <Button variant="contained" onClick={onAdd} startIcon={<PresentationIcon name="plus" />}>Add skill</Button>}
        </Box>
        {status}
        {tab === 'graph' && (skills.length === 0 && !adding ? <EmptyView icon="graph" title="No skills yet" message="Add the skills this course teaches, then link prerequisites." action={<Button variant="outlined" onClick={onAdd}>Add skill</Button>} />
            : <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1fr) 360px' }, alignItems: 'start' }}>
                <SkillGraphCanvas skills={skills} selectedId={selectedId} onSelect={onSelect} label={`Skill graph for ${courseTitle}`} />
                {adding || selected ? <Tile><SkillForm key={adding ? 'new' : selected!.id} skill={adding ? null : selected} skills={skills} onSave={onSaveSkill} serverError={serverError} onCancel={() => onSelect(null)} /></Tile>
                    : <EmptyView compact icon="graph" headingLevel="h3" title="Select a skill" message="Select a skill on the graph to edit it and its prerequisites, or add a new skill." />}
            </Box>)}
        {tab === 'mapping' && <LessonSkillMappingTable lessons={lessons} skills={skills} onChange={onMappingChange} />}
        {tab === 'adaptive' && <AdaptiveSettingsPanel checks={readiness} enabled={adaptiveEnabled} onToggle={onAdaptiveToggle} />}
    </Box>;
}
