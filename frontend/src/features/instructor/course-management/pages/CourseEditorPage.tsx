import { useEffect, useId, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Skeleton, TextField, Typography } from '@mui/material';
import { Outlet, useLocation, useNavigate, useParams } from 'react-router';
import { Page, useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { CircleAction, Tile } from '../../../../shared/components/Tile';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { EmptyView, ErrorView } from '../../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { ChapterList } from '../components/ChapterList';
import type { ChapterListProps } from '../components/ChapterList';
import { PublicationPill, PublishToggle } from '../components/PublishToggle';
import type { InstructorCourseView, Publication } from '../types/courseManagementTypes';
import { radius, roles } from '../../../../app/theme/theme';
import { ApiError } from '../../../../shared/types/apiTypes';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown } from '../hooks/useAssessmentEditor';
import { CourseEditorContext, useCourseEditorContext, useCourseOperations, useSharedCourseState } from '../hooks/useCourseEditor';
import type { CoursePublicationStatus, OwnedCourse } from '../api/instructorCourseApi';
import { CourseUnavailable, courseRefused, errorMessage } from './QuestionBankPage';

/*
 * S18 Course editor shell & tabs (DESIGN.md r12, D12-3; CT21.authoring, CT16.routes).
 * `CourseEditorShell` holds one `getOwnedCourse` read and one `getOwnedSkillGraph` read for the
 * active `courseId` and session generation across all course-editor tabs and child quiz/assignment
 * editors, preventing duplicated reads and discarding stale state on course or session changes.
 */

export type DeliveredCourseTab = 'content' | 'skills' | 'assessments' | 'settings';

/**
 * A re-read of the course tree failed with an unknown outcome while an earlier copy is on screen (U2). The copy and
 * every open draft stay; this alert says the copy may be out of date and offers one deliberate re-read. Refusals
 * (400, 403, 404) never reach it: the read discards the copy and the shell shows the F14 state instead.
 */
export function CourseRefreshAlert({ course }: { course: { data: OwnedCourse | null; error: unknown; reload: () => void } | undefined }) {
    if (!course?.data || !course.error || courseRefused(course.error)) return null;
    return <Alert severity="warning" sx={{ mb: 2.5 }}
        action={<Button size="small" variant="text" onClick={course.reload}>Try again</Button>}>
        We couldn&apos;t load this course. The last loaded version is shown.
    </Alert>;
}

function activeTabFrom(pathname: string): DeliveredCourseTab {
    if (pathname.endsWith('/skills')) return 'skills';
    if (pathname.endsWith('/assessments')) return 'assessments';
    if (pathname.endsWith('/settings')) return 'settings';
    return 'content';
}

function tabPath(courseId: string, tab: DeliveredCourseTab) {
    const base = `/teach/courses/${encodeURIComponent(courseId)}`;
    return tab === 'content' ? base : `${base}/${tab}`;
}

export function CourseEditorShell() {
    const courseId = useParams().courseId!;
    return <CourseEditorProvider key={courseId} courseId={courseId} />;
}

function CourseEditorProvider({ courseId }: { courseId: string }) {
    const shared = useSharedCourseState(courseId);
    return <CourseEditorContext.Provider value={shared}><Outlet /></CourseEditorContext.Provider>;
}

/** S18 shell frame around the delivered tabs: Content · Skills · Assessments · Settings. */
export function CourseEditorFrame() {
    const ctx = useCourseEditorContext()!;
    const { courseId, course, isTabDirty, setTabDirty } = ctx;
    const ops = useCourseOperations(courseId);
    const location = useLocation(), navigate = useNavigate();
    const tab = activeTabFrom(location.pathname);
    const regionId = `${useId()}-course-tab`;
    const [confirmPub, setConfirmPub] = useState<CoursePublicationStatus | null>(null);
    const [leaveTarget, setLeaveTarget] = useState<{ go: () => void } | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [alert, setAlert] = useState<string | null>(null);
    // Shell navigation away from a tab with unsaved changes (the Settings form) uses the same confirmation as
    // the tab switch, so one dialog appears whichever control started the navigation (S18/S19).
    useNavigationGuard((proceed) => {
        if (!isTabDirty()) return false;
        setLeaveTarget({ go: proceed });
        return true;
    });

    if (course.error && courseRefused(course.error)) {
        return <Page><CourseUnavailable status={failureStatus(course.error)} /></Page>;
    }
    if (course.status === 'loading' && !course.data) {
        return <Page>
            <Box role="status" aria-label="Loading course" sx={{ display: 'grid', gap: 2.5 }}>
                <Skeleton width={96} height={24} />
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                    <Skeleton width="45%" height={44} />
                    <Skeleton width={140} height={38} />
                </Box>
                <Skeleton width={320} height={40} />
                <Tile sx={{ display: 'grid', gap: 1.5 }}><Skeleton width="35%" height={28} /><Skeleton width="80%" /><Skeleton width="65%" /></Tile>
            </Box>
        </Page>;
    }
    if (course.status === 'error' && !course.data) {
        return <Page>
            <ErrorView title="We couldn't load this course." message={errorMessage(course.error)} onRetry={course.reload} />
        </Page>;
    }

    const data = course.data!;
    const published = data.publicationStatus === 'published';

    const go = (to: string) => {
        if (to === location.pathname) return;
        if (isTabDirty()) {
            setLeaveTarget({ go: () => navigate(to) });
        } else {
            setNotice(null);
            setAlert(null);
            navigate(to);
        }
    };

    async function applyPublication(next: CoursePublicationStatus) {
        setConfirmPub(null);
        setNotice(null);
        setAlert(null);
        try {
            await ops.setPublication(next);
            course.reload();
            setNotice(next === 'published' ? 'Course published.' : 'Course hidden.');
        } catch (error) {
            if (isCancelled(error)) return;
            if (outcomeUnknown(error)) {
                setAlert("We couldn't confirm whether the course publication changed.");
                course.reload();
                return;
            }
            setAlert(error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.');
        }
    }

    return <Page>
        <PageHeader
            title={data.title}
            back={{ label: 'Teaching', href: '/teach', onClick: () => go('/teach') }}
            meta={<PublicationPill publication={data.publicationStatus} />}
            actions={published
                ? <Button variant="outlined" onClick={() => setConfirmPub('hidden')} disabled={ops.busy} startIcon={<PresentationIcon name="eye-off" />}>Hide course</Button>
                : <Button variant="contained" onClick={() => setConfirmPub('published')} disabled={ops.busy} startIcon={<PresentationIcon name="eye" />}>Publish course</Button>} />
        {notice && <Typography role="status" sx={{ color: 'text.secondary', mb: 2 }}>{notice}</Typography>}
        {alert && <Alert severity="warning" sx={{ mb: 2.5 }}>{alert}</Alert>}
        <CourseRefreshAlert course={course} />
        <Box sx={{ mb: 4 }}>
            <SegmentedControl
                label="Course editor sections"
                value={tab}
                onChange={(next) => go(tabPath(courseId, next))}
                controls={regionId}
                options={[
                    { value: 'content', label: 'Content' },
                    { value: 'skills', label: 'Skills' },
                    { value: 'assessments', label: 'Assessments' },
                    { value: 'settings', label: 'Settings' },
                ]} />
        </Box>
        <Box role="region" id={regionId} aria-label={`${tab[0].toUpperCase()}${tab.slice(1)} section`}>
            <Outlet />
        </Box>
        <ConfirmDialog open={confirmPub === 'published'} title="Publish course?"
            message="Learners will see published lessons and materials." confirmLabel="Publish course"
            onCancel={() => setConfirmPub(null)} onConfirm={() => void applyPublication('published')} />
        <ConfirmDialog open={confirmPub === 'hidden'} isDestructive title="Hide course?"
            message="Learners will no longer see this course." confirmLabel="Hide course"
            onCancel={() => setConfirmPub(null)} onConfirm={() => void applyPublication('hidden')} />
        <ConfirmDialog open={leaveTarget !== null} isDestructive title="Leave without saving?" message="Your changes will be lost."
            cancelLabel="Stay" confirmLabel="Leave" onCancel={() => setLeaveTarget(null)}
            onConfirm={() => { const next = leaveTarget!; setLeaveTarget(null); setTabDirty(false); setNotice(null); setAlert(null); next.go(); }} />
    </Page>;
}

/** S18 Content tab: chapters, canonical ordered lessons, mapped-skill counts, reorder and lesson publication. */
export function CourseContentTab() {
    const { courseId, course, skillGraph } = useCourseEditorContext()!;
    const ops = useCourseOperations(courseId);
    const data = course.data!;
    const [chapterModal, setChapterModal] = useState(false);
    const [chapterTitle, setChapterTitle] = useState('');
    const [chapterError, setChapterError] = useState<string | null>(null);
    const [lessonModal, setLessonModal] = useState<string | null>(null);
    const [lessonTitle, setLessonTitle] = useState('');
    const [lessonContent, setLessonContent] = useState('');
    const [lessonError, setLessonError] = useState<string | null>(null);
    const [statusLine, setStatusLine] = useState('');
    const [errorAlert, setErrorAlert] = useState<string | null>(null);

    const skillCounts = useMemo(() => {
        const counts = new Map<string, number>();
        for (const mapping of skillGraph.graph?.mappings ?? []) {
            const key = mapping.lessonId.toLowerCase();
            counts.set(key, (counts.get(key) ?? 0) + 1);
        }
        return counts;
    }, [skillGraph.graph]);

    async function addChapter() {
        if (!/\S/.test(chapterTitle)) { setChapterError('Enter a chapter title.'); return; }
        setChapterError(null); setErrorAlert(null);
        try {
            await ops.createChapter(chapterTitle.trim());
            setChapterModal(false); setChapterTitle('');
            course.reload();
            setStatusLine('Chapter added.');
        } catch (error) {
            if (isCancelled(error)) return;
            setErrorAlert(errorMessage(error));
        }
    }

    async function addLesson() {
        if (!lessonModal) return;
        if (!/\S/.test(lessonTitle)) { setLessonError('Enter a lesson title.'); return; }
        setLessonError(null); setErrorAlert(null);
        try {
            await ops.createLesson(lessonModal, {
                title: lessonTitle.trim(),
                contentMarkdown: /\S/.test(lessonContent) ? lessonContent : null,
            });
            setLessonModal(null); setLessonTitle(''); setLessonContent('');
            course.reload();
            setStatusLine('Lesson added.');
        } catch (error) {
            if (isCancelled(error)) return;
            setErrorAlert(errorMessage(error));
        }
    }

    async function moveLesson(chapterId: string, lessonId: string, direction: -1 | 1) {
        const chapter = data.chapters.find((item) => item.id === chapterId);
        if (!chapter) return;
        const index = chapter.lessons.findIndex((item) => item.id === lessonId);
        const targetIndex = index + direction;
        if (index < 0 || targetIndex < 0 || targetIndex >= chapter.lessons.length) return;
        const ids = chapter.lessons.map((item) => item.id);
        [ids[index], ids[targetIndex]] = [ids[targetIndex], ids[index]];
        const movedLesson = chapter.lessons[index];
        setErrorAlert(null);
        try {
            await ops.orderLessons(chapterId, ids);
            course.reload();
            setStatusLine(`Moved ${movedLesson.title} to position ${targetIndex + 1}.`);
        } catch (error) {
            if (isCancelled(error)) return;
            setErrorAlert(errorMessage(error));
        }
    }

    async function toggleLessonPublication(lessonId: string, next: CoursePublicationStatus) {
        setErrorAlert(null);
        try {
            await ops.setLessonPublication(lessonId, next);
            course.reload();
            setStatusLine(next === 'published' ? 'Lesson published.' : 'Lesson hidden.');
        } catch (error) {
            if (isCancelled(error)) return;
            setErrorAlert(errorMessage(error));
        }
    }

    return <Box sx={{ display: 'grid', gap: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <Typography component="h2" variant="h2">Content</Typography>
            <Button variant="outlined" onClick={() => { setChapterTitle(''); setChapterError(null); setChapterModal(true); }}
                disabled={ops.busy} startIcon={<PresentationIcon name="plus" />}>Add chapter</Button>
        </Box>
        {errorAlert && <Alert severity="error">{errorAlert}</Alert>}
        <Typography role="status" className="cm-visually-hidden">{statusLine}</Typography>
        {data.chapters.length === 0
            ? <EmptyView icon="book" headingLevel="h3" title="No chapters yet"
                message="Add chapters and lessons to structure your course."
                action={<Button variant="contained" onClick={() => setChapterModal(true)} startIcon={<PresentationIcon name="plus" />}>Add chapter</Button>} />
            : data.chapters.map((chapter) => <Box component="section" key={chapter.id} aria-labelledby={`chapter-${chapter.id}`} sx={{ display: 'grid', gap: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                    <Typography id={`chapter-${chapter.id}`} component="h3" variant="h3">Chapter {chapter.position}: {chapter.title}</Typography>
                    <Button variant="text" disabled={ops.busy} onClick={() => { setLessonTitle(''); setLessonContent(''); setLessonError(null); setLessonModal(chapter.id); }}
                        startIcon={<PresentationIcon name="plus" />}>Add lesson</Button>
                </Box>
                {chapter.lessons.length === 0
                    ? <Box sx={{ border: `1.5px dashed ${roles.line.strong}`, borderRadius: `${radius.lg}px`, p: 3, textAlign: 'center', color: 'text.secondary' }}>
                        No lessons in this chapter yet.
                    </Box>
                    : <Box component="ol" aria-label={`Lessons in ${chapter.title}`} sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                        {chapter.lessons.map((lesson, index) => {
                            const mapped = skillCounts.get(lesson.id.toLowerCase()) ?? 0;
                            const isPublished = lesson.publicationStatus === 'published';
                            return <Box component="li" key={lesson.id} sx={{
                                display: 'flex', alignItems: 'center', gap: 2, flexWrap: { xs: 'wrap', sm: 'nowrap' },
                                px: { xs: 2, sm: 3 }, py: 1.5, minHeight: 64,
                                borderBottom: `1px solid ${roles.line.hairline}`, '&:last-child': { borderBottom: 0 },
                            }}>
                                <Typography className="cm-tabular" sx={{ color: 'text.secondary', fontWeight: 600, width: 24 }}>{lesson.position}</Typography>
                                <Box sx={{ flex: '1 1 200px', minWidth: 0 }}>
                                    <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{lesson.title}</Typography>
                                    <Typography variant="caption" sx={{ color: mapped ? 'text.secondary' : roles.tone.butter.strong }}>
                                        {mapped ? `${mapped} ${mapped === 1 ? 'skill' : 'skills'} mapped` : 'No skill mapped yet'}
                                    </Typography>
                                </Box>
                                <PublicationPill publication={lesson.publicationStatus} />
                                <Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center', flexWrap: 'wrap' }}>
                                    <CircleAction label={`Move ${lesson.title} up`} icon="move-up" variant="plain"
                                        disabled={ops.busy || index === 0} onClick={() => void moveLesson(chapter.id, lesson.id, -1)} />
                                    <CircleAction label={`Move ${lesson.title} down`} icon="move-down" variant="plain"
                                        disabled={ops.busy || index === chapter.lessons.length - 1} onClick={() => void moveLesson(chapter.id, lesson.id, 1)} />
                                    <Button size="small" variant="outlined" disabled={ops.busy}
                                        onClick={() => void toggleLessonPublication(lesson.id, isPublished ? 'hidden' : 'published')}>
                                        {isPublished ? 'Hide lesson' : 'Publish lesson'}
                                    </Button>
                                </Box>
                            </Box>;
                        })}
                    </Box>}
            </Box>)}
        <Dialog open={chapterModal} onClose={() => { if (!ops.busy) setChapterModal(false); }} fullWidth maxWidth="xs" aria-labelledby="add-chapter-title">
            <DialogTitle id="add-chapter-title">Add chapter</DialogTitle>
            <DialogContent>
                <TextField autoFocus fullWidth label="Chapter title" value={chapterTitle} sx={{ mt: 1 }} disabled={ops.busy}
                    onChange={(event) => { setChapterTitle(event.target.value); setChapterError(null); }}
                    error={!!chapterError} helperText={chapterError} required />
            </DialogContent>
            <DialogActions>
                <Button variant="outlined" onClick={() => setChapterModal(false)} disabled={ops.busy}>Cancel</Button>
                <Button variant="contained" onClick={() => void addChapter()} disabled={ops.busy}>Add chapter</Button>
            </DialogActions>
        </Dialog>
        <Dialog open={lessonModal !== null} onClose={() => { if (!ops.busy) setLessonModal(null); }} fullWidth maxWidth="sm" aria-labelledby="add-lesson-title">
            <DialogTitle id="add-lesson-title">Add lesson</DialogTitle>
            <DialogContent sx={{ display: 'grid', gap: 2 }}>
                <TextField autoFocus fullWidth label="Lesson title" value={lessonTitle} sx={{ mt: 1 }} disabled={ops.busy}
                    onChange={(event) => { setLessonTitle(event.target.value); setLessonError(null); }}
                    error={!!lessonError} helperText={lessonError} required />
                <TextField fullWidth multiline minRows={4} label="Content (optional)" value={lessonContent} disabled={ops.busy}
                    onChange={(event) => setLessonContent(event.target.value)}
                    helperText="Markdown text. The new lesson stays hidden until you publish it." />
            </DialogContent>
            <DialogActions>
                <Button variant="outlined" onClick={() => setLessonModal(null)} disabled={ops.busy}>Cancel</Button>
                <Button variant="contained" onClick={() => void addLesson()} disabled={ops.busy}>Add lesson</Button>
            </DialogActions>
        </Dialog>
    </Box>;
}

/** S18 Settings tab: course title, description, optional prerequisites text, and metadata save. */
export function CourseSettingsTab() {
    const { courseId, course, setTabDirty } = useCourseEditorContext()!;
    const ops = useCourseOperations(courseId);
    const data = course.data!;
    // Keyed by the course identity only (U5): a re-read after a save, or another writer's change, must not remount the
    // form, which would drop the "Saved" status or an unsaved draft. The form adopts new committed values itself.
    return <CourseSettingsForm key={data.id}
        course={data} successfulRead={course.successfulRead} refreshError={course.error}
        ops={ops} onSaved={course.reload} setTabDirty={setTabDirty} />;
}

function CourseSettingsForm({ course, successfulRead, refreshError, ops, onSaved, setTabDirty }: {
    course: OwnedCourse;
    successfulRead: number;
    refreshError: unknown;
    ops: ReturnType<typeof useCourseOperations>;
    onSaved: () => number;
    setTabDirty: (dirty: boolean) => void;
}) {
    const [title, setTitle] = useState(course.title);
    const [description, setDescription] = useState(course.description);
    const [prerequisites, setPrerequisites] = useState(course.prerequisiteDescription ?? '');
    const [dirty, setDirty] = useState(false);
    const [savedNotice, setSavedNotice] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [alert, setAlert] = useState<string | null>(null);
    // A GET observed during a PUT cannot establish their commit order. Keep the acknowledged fields until a
    // current shared GET initiated after acknowledgment succeeds. Equality and object identity prove no ordering.
    const [adoptedRead, setAdoptedRead] = useState(successfulRead);
    const [reconcileAfter, setReconcileAfter] = useState<number | null>(null);
    const reconciled = reconcileAfter === null || successfulRead >= reconcileAfter;
    if (reconcileAfter !== null && reconciled) setReconcileAfter(null);
    if (adoptedRead !== successfulRead && reconciled && !dirty && !ops.busy) {
        setAdoptedRead(successfulRead);
        setTitle(course.title);
        setDescription(course.description);
        setPrerequisites(course.prerequisiteDescription ?? '');
    }

    useEffect(() => {
        setTabDirty(dirty);
        return () => setTabDirty(false);
    }, [dirty, setTabDirty]);

    useEffect(() => {
        if (!dirty) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    async function save() {
        const next: Record<string, string> = {};
        if (!/\S/.test(title)) next.title = 'Enter a course title.';
        if (!/\S/.test(description)) next.description = 'Enter a course description.';
        setErrors(next);
        if (Object.keys(next).length) return;
        setAlert(null);
        setSavedNotice(false);
        setReconcileAfter(null);
        const write = {
            title: title.trim(),
            description: description.trim(),
            prerequisiteDescription: /\S/.test(prerequisites) ? prerequisites.trim() : null,
        };
        try {
            const saved = await ops.updateMetadata(write);
            setTitle(saved.title);
            setDescription(saved.description);
            setPrerequisites(saved.prerequisiteDescription ?? '');
            setDirty(false);
            setSavedNotice(true);
            setReconcileAfter(onSaved());
        } catch (error) {
            if (isCancelled(error)) return;
            const fields = fieldErrorsFrom(error, {
                title: 'title',
                description: 'description',
                prerequisiteDescription: 'prerequisites',
            });
            if (Object.keys(fields).length) { setErrors(fields); return; }
            if (outcomeUnknown(error)) {
                setAlert("We couldn't confirm whether your changes were saved.");
                onSaved();
                return;
            }
            setAlert(error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.');
        }
    }

    const status = ops.busy ? 'Saving…' : dirty ? 'Unsaved changes' : savedNotice
        ? reconcileAfter !== null
            ? refreshError ? "Settings saved, but the current values couldn't be loaded." : 'Settings saved. Refreshing course…'
            : 'Saved'
        : null;
    return <Box sx={{ display: 'grid', gap: 2.5, maxWidth: 760 }}>
        <Typography component="h2" variant="h2">Settings</Typography>
        {alert && <Alert severity="warning">{alert}</Alert>}
        <Tile padding={{ xs: 3, sm: 4 }}>
            <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); void save(); }}
                sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
                <TextField label="Course title" value={title} required disabled={ops.busy}
                    onChange={(event) => { setTitle(event.target.value); setDirty(true); setSavedNotice(false); setErrors({}); }}
                    error={!!errors.title} helperText={errors.title} />
                <TextField label="Description" multiline minRows={4} value={description} required disabled={ops.busy}
                    onChange={(event) => { setDescription(event.target.value); setDirty(true); setSavedNotice(false); setErrors({}); }}
                    error={!!errors.description} helperText={errors.description ?? 'Shown on the course detail page.'} />
                <TextField label="Prerequisites (optional)" multiline minRows={2} value={prerequisites} disabled={ops.busy}
                    onChange={(event) => { setPrerequisites(event.target.value); setDirty(true); setSavedNotice(false); setErrors({}); }}
                    error={!!errors.prerequisites} helperText={errors.prerequisites ?? 'A learner-facing description. It never blocks enrolment.'} />
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
                    <Button type="submit" variant="contained" disabled={ops.busy || !dirty} aria-busy={ops.busy || undefined}>Save changes</Button>
                    {status && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>{status}</Typography>}
                </Box>
            </Box>
        </Tile>
    </Box>;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

export type CourseEditorTab = 'content' | 'skills' | 'assessments' | 'learners' | 'qa' | 'settings';
export interface CourseEditorPageProps extends Omit<ChapterListProps, never> {
    course: InstructorCourseView & { prerequisiteDescription: string };
    tab: CourseEditorTab;
    onTabChange: (tab: CourseEditorTab) => void;
    onPublicationChange: (next: Publication) => void;
    onSaveSettings: (settings: { title: string; description: string; prerequisiteDescription: string }) => void;
    /** Tabs owned by other Instructor features are composed by the caller. */
    tabContent?: Partial<Record<Exclude<CourseEditorTab, 'content' | 'settings'>, ReactNode>>;
    back?: Crumb;
    status?: ReactNode;
}

/** S18 Course editor preview component. */
export function CourseEditorPage({ course, tab, onTabChange, onPublicationChange, onSaveSettings, tabContent = {}, back, status, ...chapterProps }: CourseEditorPageProps) {
    const regionId = `${useId()}-section`;
    return <Page>
        <PageHeader title={course.title} back={back} actions={<PublishToggle publication={course.publication} itemLabel="course" onChange={onPublicationChange} />} />
        {status}
        <Box sx={{ mb: 4 }}><SegmentedControl label="Course editor sections" value={tab} onChange={onTabChange} controls={regionId}
            options={[{ value: 'content', label: 'Content' }, { value: 'skills', label: 'Skills' }, { value: 'assessments', label: 'Assessments' }, { value: 'learners', label: 'Learners' }, { value: 'qa', label: 'Q&A' }, { value: 'settings', label: 'Settings' }]} /></Box>
        <Box role="region" id={regionId} aria-label={`${tab === 'qa' ? 'Q&A' : tab[0].toUpperCase() + tab.slice(1)} section`}>
            {tab === 'content' && <ChapterList {...chapterProps} />}
            {tab === 'settings' && <PreviewCourseSettings course={course} onSave={onSaveSettings} />}
            {tab !== 'content' && tab !== 'settings' && tabContent[tab]}
        </Box>
    </Page>;
}

function PreviewCourseSettings({ course, onSave }: { course: CourseEditorPageProps['course']; onSave: CourseEditorPageProps['onSaveSettings'] }) {
    const [title, setTitle] = useState(course.title), [description, setDescription] = useState(course.description), [prerequisites, setPrerequisites] = useState(course.prerequisiteDescription);
    const [error, setError] = useState<string | null>(null);
    return <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 760 }}>
        <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); if (!title.trim()) { setError('Enter a course title.'); return; } setError(null); onSave({ title: title.trim(), description, prerequisiteDescription: prerequisites }); }} sx={{ display: 'grid', gap: 2.5 }}>
            <TextField label="Course title" value={title} onChange={(event) => setTitle(event.target.value)} error={!!error} helperText={error} required />
            <TextField label="Description" multiline minRows={4} value={description} onChange={(event) => setDescription(event.target.value)} helperText="Shown on the course detail page." />
            <TextField label="Prerequisites (optional)" multiline minRows={2} value={prerequisites} onChange={(event) => setPrerequisites(event.target.value)} helperText="A learner-facing description. It never blocks enrolment." />
            <Box><Button type="submit" variant="contained">Save changes</Button></Box>
        </Box>
    </Tile>;
}
