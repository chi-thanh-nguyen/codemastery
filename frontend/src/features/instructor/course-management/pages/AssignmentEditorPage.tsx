import { gradingPath } from '../../grading/hooks/useSubmissions';
import { isDestinationLive } from '../../../../routes/routePaths';
import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, ListSubheader, MenuItem, TextField, Typography } from '@mui/material';
import { Link, useLocation, useNavigate } from 'react-router';
import { useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { Tile } from '../../../../shared/components/Tile';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { MarkdownRenderer } from '../../../../shared/components/MarkdownRenderer';
import { radius, roles, shadow } from '../../../../app/theme/theme';
import { ErrorView, LoadingView, SystemStateView } from '../../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { ApiError } from '../../../../shared/types/apiTypes';
import type { AssignmentDraft } from '../types/courseManagementTypes';
import type { AssignmentPublicationStatus, B5AssignmentWrite, B5OwnedAssignment } from '../api/assessmentAuthoringApi';
import { failureStatus, fieldErrorsFrom, isCancelled, outcomeUnknown, useAssignmentEditor } from '../hooks/useAssessmentEditor';
import { useCourseEditorContext } from '../hooks/useCourseEditor';
import {
    CourseUnavailable, PublicationPill, assignmentPath, assessmentsPath, errorMessage, formatDeadlineDisplay, localTimeZone,
} from './QuestionBankPage';
import type { CourseLessonSource } from './QuestionBankPage';

/*
 * S19 Assignment editor (DESIGN.md r12, lines 2618–2634; CT10.rest, ADR010 §§8, 12).
 * Supports `/teach/courses/:courseId/assignments/:assignmentId` and literal `new`.
 * Assignments cannot be deleted (ADR010 §12) and may be edited whether hidden or published.
 */

export interface AssignmentFormDraft {
    title: string;
    instructions: string;
    deadlineInput: string;
    lessonId: string | null;
}

const pad2 = (n: number) => String(n).padStart(2, '0');

/** Formats an RFC 3339 instant into a local `datetime-local` input string (`YYYY-MM-DDTHH:mm` or `:ss`). */
export function toLocalDateTimeInput(isoString: string): string {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return '';
    const base = `${String(date.getFullYear()).padStart(4, '0')}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
    const seconds = date.getSeconds();
    return seconds ? `${base}:${pad2(seconds)}` : base;
}

/** Converts a `datetime-local` value into an RFC 3339 UTC instant string, or null when invalid. */
export function localInputToRfc3339(localValue: string): string | null {
    const trimmed = localValue.trim();
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(trimmed)) return null;
    const parsed = new Date(trimmed);
    if (Number.isNaN(parsed.getTime()) || parsed.getUTCFullYear() < 0 || parsed.getUTCFullYear() > 9999) return null;
    // Refuse normalized invalid calendar times (including a local DST gap), rather than silently changing the deadline.
    const canonical = toLocalDateTimeInput(parsed.toISOString());
    if (canonical !== (trimmed.length === 19 && trimmed.endsWith(':00') ? trimmed.slice(0, -3) : trimmed)) return null;
    return parsed.toISOString();
}

export const emptyAssignmentDraft = (): AssignmentFormDraft => ({
    title: '',
    instructions: '',
    deadlineInput: '',
    lessonId: null,
});

export function draftFromAssignment(assignment: B5OwnedAssignment): AssignmentFormDraft {
    return {
        title: assignment.title,
        instructions: assignment.instructions,
        deadlineInput: toLocalDateTimeInput(assignment.deadlineAt),
        lessonId: assignment.lessonId,
    };
}

export function resolveDeadlineIso(draftInput: string, baseline: { input: string; iso: string } | null): string | null {
    if (baseline && draftInput === baseline.input) return baseline.iso;
    return localInputToRfc3339(draftInput);
}

export function validateAssignment(draft: AssignmentFormDraft, baseline: { input: string; iso: string } | null): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!/\S/.test(draft.title)) errors.title = 'Enter an assignment title.';
    if (!/\S/.test(draft.instructions)) errors.instructions = 'Write the assignment instructions.';
    if (!resolveDeadlineIso(draft.deadlineInput, baseline)) errors.deadline = 'Choose a valid deadline date and time.';
    return errors;
}

export function assignmentWrite(draft: AssignmentFormDraft, baseline: { input: string; iso: string } | null): B5AssignmentWrite {
    return {
        title: draft.title.trim(),
        instructions: draft.instructions,
        deadlineAt: resolveDeadlineIso(draft.deadlineInput, baseline) as string,
        lessonId: draft.lessonId,
    };
}

const rfc3339Instant = /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(?:([Zz])|([+-])(\d{2}):(\d{2}))$/;

/**
 * An RFC 3339 instant as whole UTC seconds plus microseconds, or null when it is not one. JavaScript `Date` keeps
 * only milliseconds, so it cannot tell `…00.123456Z` from `…00.123999Z` (ADR010 §8 deadlines carry microseconds).
 * Digits below the microsecond must be zero, as the server requires.
 */
export function instantMicros(value: string): { seconds: number; micros: number } | null {
    const match = rfc3339Instant.exec(value);
    if (!match) return null;
    const [, year, month, day, hour, minute, second, fraction = '', zulu, sign, offsetHours, offsetMinutes] = match;
    if (fraction.length > 6 && !/^0+$/.test(fraction.slice(6))) return null;
    // setUTCFullYear keeps years 0000–0099 literal (Date.UTC would map them to 1900–1999).
    const date = new Date(0);
    date.setUTCFullYear(Number(year), Number(month) - 1, Number(day));
    date.setUTCHours(Number(hour), Number(minute), Number(second), 0);
    const local = date.getTime();
    if (Number.isNaN(local)) return null;
    const offset = zulu ? 0 : (sign === '-' ? -1 : 1) * (Number(offsetHours) * 3600 + Number(offsetMinutes) * 60);
    return { seconds: local / 1000 - offset, micros: Number(fraction.slice(0, 6).padEnd(6, '0')) };
}

/** True when both texts name the same instant to the microsecond (offsets and trailing zeros may differ). */
export function sameInstant(left: string, right: string): boolean {
    const a = instantMicros(left), b = instantMicros(right);
    return a !== null && b !== null && a.seconds === b.seconds && a.micros === b.micros;
}

export function sameAssignment(draft: AssignmentFormDraft, baseline: { input: string; iso: string } | null, current: B5OwnedAssignment): boolean {
    const write = assignmentWrite(draft, baseline);
    const sameLesson = (write.lessonId ?? '').toLowerCase() === (current.lessonId ?? '').toLowerCase();
    return write.title === current.title &&
        write.instructions === current.instructions &&
        sameInstant(write.deadlineAt, current.deadlineAt) &&
        sameLesson;
}

const CREATED = 'Assignment created. It stays hidden until you publish it.';
const ASSIGNMENT_FIELD_MAP = {
    title: 'title',
    instructions: 'instructions',
    deadlineAt: 'deadline',
    lessonId: 'lessonId',
} as const;

type AssignmentAlert =
    | { type: 'error'; message: string }
    | { type: 'not-found' | 'course-not-found' | 'unknown-create' | 'no-access' }
    | { type: 'unknown'; current: B5OwnedAssignment | null; retry: () => void };

const messageOf = (error: unknown) => error instanceof ApiError ? error.message : 'Something went wrong on our side. Try again in a moment.';

export interface AssignmentEditorProps {
    courseId: string;
    /** An assignment UUID, or the literal `new` creation segment (CT16.routes). */
    assignmentId: string;
    /** Optional course lesson tree override (defaults to CourseEditorContext in production). */
    courseContext?: CourseLessonSource;
}

export function AssignmentEditor({ courseId, assignmentId, courseContext }: AssignmentEditorProps) {
    const sharedCtx = useCourseEditorContext();
    const course = courseContext ?? sharedCtx?.course;
    const navigate = useNavigate(), location = useLocation();
    const editor = useAssignmentEditor(courseId);
    const creating = assignmentId === 'new';
    const listPath = assessmentsPath(courseId, 'assignments');
    const timeZone = useMemo(localTimeZone, []);

    const [assignment, setAssignment] = useState<B5OwnedAssignment | null>(null);
    const [baselineDeadline, setBaselineDeadline] = useState<{ input: string; iso: string } | null>(null);
    const [entry, setEntry] = useState<'loading' | 'ready' | 'failed'>(creating ? 'ready' : 'loading');
    const [refusal, setRefusal] = useState<number | undefined>(undefined);
    const [revision, setRevision] = useState(0);
    const [draft, setDraft] = useState<AssignmentFormDraft>(emptyAssignmentDraft);
    const [dirty, setDirty] = useState(false);
    const [gone, setGone] = useState(false);
    const [noAccess, setNoAccess] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [alert, setAlert] = useState<AssignmentAlert | null>(null);
    const [notice, setNotice] = useState<string | null>(() => (location.state as { notice?: unknown } | null)?.notice === CREATED ? CREATED : null);
    const [confirm, setConfirm] = useState<'publish' | 'hide' | 'leave' | null>(null);
    const [leaveTo, setLeaveTo] = useState<{ go: () => void } | null>(null);
    const [preview, setPreview] = useState(false);
    const instructionsRegion = useId();
    const form = useRef<HTMLDivElement>(null);
    const [validationRevision, setValidationRevision] = useState(0);

    useEffect(() => {
        if (!validationRevision) return;
        form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    }, [validationRevision]);

    const showErrors = (next: Record<string, string>) => {
        setErrors(next);
        if (Object.keys(next).length) {
            if (next.instructions) setPreview(false);
            setValidationRevision(value => value + 1);
        }
    };

    const dirtyRef = useRef(dirty);
    dirtyRef.current = dirty;
    const draftRef = useRef(draft), deadlineRef = useRef(baselineDeadline);
    draftRef.current = draft; deadlineRef.current = baselineDeadline;
    const rereadLessons = useRef(new Set<string>());
    const { read } = editor;

    useEffect(() => {
        if ((location.state as { notice?: unknown } | null)?.notice !== undefined) {
            navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: null });
        }
    }, [location, navigate]);

    const applyLoaded = useCallback((loaded: B5OwnedAssignment, overwriteDraft: boolean) => {
        setAssignment(loaded);
        if (overwriteDraft) {
            setBaselineDeadline({ input: toLocalDateTimeInput(loaded.deadlineAt), iso: loaded.deadlineAt });
            setDraft(draftFromAssignment(loaded));
            setDirty(false);
        }
    }, []);

    useEffect(() => {
        if (creating) return;
        let live = true;
        setEntry('loading');
        read(assignmentId).then((current) => {
            if (!live) return;
            applyLoaded(current, true);
            setEntry('ready');
        }, (error: unknown) => {
            if (!live || isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 400 || status === 403 || status === 404) setRefusal(status);
            else { setEntry('failed'); setAlert({ type: 'error', message: errorMessage(error) }); }
        });
        return () => { live = false; };
    }, [creating, assignmentId, read, revision, applyLoaded]);

    // S19: if the assignment's `lessonId` is not in a loaded course tree, re-read the course once.
    const knownLessons = useMemo(() => {
        const map = new Map<string, { id: string; title: string; publicationStatus: 'hidden' | 'published' }>();
        for (const chapter of course?.data?.chapters ?? []) {
            for (const lesson of chapter.lessons) {
                map.set(lesson.id.toLowerCase(), { id: lesson.id, title: lesson.title, publicationStatus: lesson.publicationStatus });
            }
        }
        return map;
    }, [course?.data]);

    useEffect(() => {
        if (!draft.lessonId || !course || course.status !== 'ready' || !course.data) return;
        const key = draft.lessonId.toLowerCase();
        if (!knownLessons.has(key) && !rereadLessons.current.has(key)) {
            rereadLessons.current.add(key);
            course.reload();
        }
    }, [draft.lessonId, course, knownLessons]);

    useEffect(() => {
        if (!dirty) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const go = (to: string) => {
        if (dirtyRef.current) {
            setLeaveTo({ go: () => navigate(to) });
            setConfirm('leave');
        } else {
            navigate(to);
        }
    };
    // Shell navigation (Teaching, Catalogue, the home link, Profile) asks through the same confirmation (S19).
    useNavigationGuard((proceed) => {
        if (!dirtyRef.current) return false;
        setLeaveTo({ go: proceed });
        setConfirm('leave');
        return true;
    });

    const change = (patch: Partial<AssignmentFormDraft>) => {
        setDraft((current) => ({ ...current, ...patch }));
        setDirty(true);
        setErrors({});
        setNotice(null);
    };

    const recheck = useCallback(async (confirmed: (current: B5OwnedAssignment) => boolean, onConfirmed: (current: B5OwnedAssignment) => void) => {
        if (!assignment) return;
        const retry = () => void recheck(confirmed, onConfirmed);
        try {
            const current = await read(assignment.id);
            applyLoaded(current, !dirtyRef.current);
            if (confirmed(current)) {
                setAlert(null);
                onConfirmed(current);
            } else {
                setAlert({ type: 'unknown', current, retry });
            }
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); return; }
            setAlert({ type: 'unknown', current: null, retry });
        }
    }, [assignment, read, applyLoaded]);

    async function create() {
        if (noAccess || gone) return;
        const next = validateAssignment(draft, baselineDeadline);
        showErrors(next);
        if (Object.keys(next).length) return;
        setAlert(null); setNotice(null);
        try {
            const created = await editor.create(assignmentWrite(draft, baselineDeadline));
            setDirty(false); dirtyRef.current = false;
            navigate(assignmentPath(courseId, created.id), { replace: true, state: { notice: CREATED } });
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 400) {
                const mapped = fieldErrorsFrom(error, ASSIGNMENT_FIELD_MAP);
                if (Object.keys(mapped).length) { showErrors(mapped); return; }
            }
            if (status === 404 && draft.lessonId) {
                course?.reload();
                showErrors({ lessonId: 'The selected lesson is no longer available in this course.' });
                return;
            }
            if (status === 404) { setNoAccess(true); setAlert({ type: 'course-not-found' }); return; }
            setAlert(outcomeUnknown(error) ? { type: 'unknown-create' } : { type: 'error', message: messageOf(error) });
        }
    }

    async function save() {
        if (!assignment || noAccess || gone) return;
        const next = validateAssignment(draft, baselineDeadline);
        showErrors(next);
        if (Object.keys(next).length) return;
        const sent = draft;
        const sentBaseline = baselineDeadline;
        setAlert(null); setNotice(null);
        try {
            const saved = await editor.replace(assignment.id, assignmentWrite(sent, sentBaseline));
            applyLoaded(saved, true);
            setNotice('Saved');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 400) {
                const mapped = fieldErrorsFrom(error, ASSIGNMENT_FIELD_MAP);
                if (Object.keys(mapped).length) { showErrors(mapped); return; }
            }
            if (status === 404) {
                // Distinguish a missing lessonId from a missing assignment row.
                if (sent.lessonId && sent.lessonId.toLowerCase() !== (assignment.lessonId ?? '').toLowerCase()) {
                    course?.reload();
                    try {
                        const current = await read(assignment.id);
                        applyLoaded(current, false);
                        showErrors({ lessonId: 'The selected lesson is no longer available in this course.' });
                        return;
                    } catch (readError) {
                        if (isCancelled(readError)) return;
                        if (failureStatus(readError) === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
                        if (failureStatus(readError) !== 404) {
                            setAlert({ type: 'error', message: 'The save was refused. We could not refresh the assignment. Your edits are still here.' });
                            return;
                        }
                    }
                }
                setGone(true);
                setAlert({ type: 'not-found' });
                return;
            }
            if (outcomeUnknown(error)) {
                setAlert({ type: 'unknown', current: null, retry: () => undefined });
                await recheck((current) => sameAssignment(sent, sentBaseline, current), (current) => {
                    // A deliberate later GET can confirm the earlier write after the user has edited again.
                    // It must not mark those newer, unsent edits as saved or disable their Save action.
                    const unchanged = JSON.stringify(draftRef.current) === JSON.stringify(sent)
                        && JSON.stringify(deadlineRef.current) === JSON.stringify(sentBaseline);
                    // Confirmation advances the persisted baseline just like a successful PUT.
                    // Otherwise returning to an older displayed date could restore its stale microseconds.
                    if (unchanged) applyLoaded(current, true);
                    else setBaselineDeadline({ input: toLocalDateTimeInput(current.deadlineAt), iso: current.deadlineAt });
                    setNotice(unchanged ? 'Saved' : 'Earlier changes were saved. Your current edits are not saved.');
                });
                return;
            }
            setAlert({ type: 'error', message: messageOf(error) });
        }
    }

    async function publication(targetStatus: AssignmentPublicationStatus) {
        setConfirm(null);
        if (!assignment || noAccess || gone) return;
        setAlert(null); setNotice(null);
        try {
            const updated = await editor.setPublication(assignment.id, targetStatus);
            applyLoaded(updated, !dirtyRef.current);
            setNotice(targetStatus === 'published' ? 'Assignment published.' : 'Assignment hidden.');
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403) { setNoAccess(true); setAlert({ type: 'no-access' }); return; }
            if (status === 404) { setGone(true); setAlert({ type: 'not-found' }); return; }
            if (outcomeUnknown(error)) {
                setAlert({ type: 'unknown', current: null, retry: () => undefined });
                await recheck(
                    (current) => current.publicationStatus === targetStatus,
                    () => setNotice(targetStatus === 'published' ? 'Assignment published.' : 'Assignment hidden.'),
                );
                return;
            }
            setAlert({ type: 'error', message: messageOf(error) });
        }
    }

    if (refusal !== undefined) {
        const back = <Button component={Link} to={listPath} variant="contained">Back to assignments</Button>;
        if (refusal === 403) return <CourseUnavailable status={403} />;
        return refusal === 400
            ? <SystemStateView icon="search" title="We can't open this assignment from this link." message="Check the address, or go back to the assignments." actions={back} />
            : <SystemStateView icon="search" title="This assignment isn't available." message="Go back to the assignments to see the current list." actions={back} />;
    }
    if (entry === 'loading') return <LoadingView message="Loading the assignment…" variant="block" />;
    if (entry === 'failed') return <Box sx={{ display: 'grid', gap: 2 }}>
        <PageHeader title="Assignment" back={{ label: 'Back to assignments', href: listPath, onClick: () => go(listPath) }} />
        <ErrorView title="The assignment couldn't be loaded." message={alert?.type === 'error' ? alert.message : undefined}
            onRetry={() => { setAlert(null); setRevision((value) => value + 1); }} />
    </Box>;

    const published = assignment?.publicationStatus === 'published';
    const readOnly = gone || noAccess || editor.busy;
    const chapters = course?.data?.chapters ?? [];
    const totalLessons = chapters.reduce((sum, chapter) => sum + chapter.lessons.length, 0);
    const courseFailed = course?.status === 'error' && !course.data;
    const courseLoading = course?.status === 'loading' && !course.data;
    const lessonMissing = !!draft.lessonId && !courseLoading && !courseFailed && !knownLessons.has(draft.lessonId.toLowerCase());

    const lessonMenuItems: ReactNode[] = [<MenuItem key="__none" value="">No related lesson</MenuItem>];
    if (lessonMissing && draft.lessonId) {
        lessonMenuItems.push(<MenuItem key={draft.lessonId} value={draft.lessonId} disabled>Lesson unavailable</MenuItem>);
    }
    for (const chapter of chapters) {
        if (!chapter.lessons.length) continue;
        lessonMenuItems.push(<ListSubheader key={`chapter-${chapter.id}`}>Chapter {chapter.position}: {chapter.title}</ListSubheader>);
        for (const lesson of chapter.lessons) {
            lessonMenuItems.push(
                <MenuItem key={lesson.id} value={lesson.id}>
                    {lesson.title}{lesson.publicationStatus === 'hidden' ? ' (hidden)' : ''}
                </MenuItem>,
            );
        }
    }

    const alertView = (() => {
        if (!alert) return null;
        switch (alert.type) {
            case 'course-not-found':
                return <Alert severity="warning" action={<Button size="small" variant="text" onClick={() => go('/courses')}>Back to catalogue</Button>}>
                    This course isn&apos;t available. Your edits are still here.
                </Alert>;
            case 'not-found':
                return <Alert severity="warning" action={<Button size="small" variant="text" onClick={() => go(listPath)}>Back to assignments</Button>}>
                    This assignment isn&apos;t available.
                </Alert>;
            case 'no-access':
                return <Alert severity="error" action={<Button size="small" variant="text" onClick={() => go('/courses')}>Back to catalogue</Button>}>
                    You don&apos;t have access to this course. Only the course&apos;s instructor can manage its assessments.
                </Alert>;
            case 'unknown-create':
                return <Alert severity="warning" action={<Button size="small" variant="text" onClick={() => go(listPath)}>Go to assignments</Button>}>
                    We couldn&apos;t confirm whether the assignment was created. Check the assignment list before creating it again.
                </Alert>;
            case 'unknown':
                return <Alert severity="warning" action={alert.current === null && !editor.busy ? <Button size="small" variant="text" onClick={alert.retry}>Try again</Button> : undefined}>
                    We couldn&apos;t confirm whether your changes were saved.
                    {alert.current && <Box component="span" sx={{ display: 'block', mt: 1 }}>
                        Current version: {alert.current.title} · {formatDeadlineDisplay(alert.current.deadlineAt).absolute} · {alert.current.publicationStatus === 'published' ? 'Published' : 'Hidden'}
                    </Box>}
                </Alert>;
            case 'error':
                return <Alert severity="error">{alert.message}</Alert>;
        }
    })();

    const status = editor.busy ? 'Saving…' : notice ?? (dirty ? 'Unsaved changes' : null);

    return <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 3, maxWidth: 860 }}>
        <PageHeader
            title={creating ? 'New assignment' : assignment?.title ?? 'Assignment'}
            back={{ label: 'Back to assignments', href: listPath, onClick: () => go(listPath) }}
            meta={assignment && <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                <PublicationPill published={published} />
                <Typography variant="body2" className="cm-tabular" sx={{ color: 'text.secondary' }}>
                    Submitted {assignment.submissionCount} · Graded {assignment.gradedCount}
                </Typography>
                {!gone && !noAccess && isDestinationLive('/teach/grading', 'Instructor') && <Button component={Link} to={gradingPath(courseId, assignment.id)} variant="outlined" size="small" onClick={event => { if (!event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) { event.preventDefault(); go(gradingPath(courseId, assignment.id)); } }}>Open grading</Button>}
            </Box>}
            actions={assignment && !gone && !noAccess && (published
                ? <Button variant="outlined" onClick={() => setConfirm('hide')} disabled={editor.busy} startIcon={<PresentationIcon name="eye-off" />}>Hide assignment</Button>
                : <Button variant="contained" onClick={() => setConfirm('publish')} disabled={editor.busy || dirty} startIcon={<PresentationIcon name="eye" />}>Publish assignment</Button>)} />
        {assignment && !published && !gone && !noAccess && dirty && <Typography variant="body2" sx={{ color: 'text.secondary', mt: -2 }}>Save your changes before publishing.</Typography>}
        {alertView}
        <Box ref={form}>
        <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 2.5 }}>
            <Typography variant="h3" component="h2">Assignment details</Typography>
            {Object.keys(errors).length > 0 && <Alert severity="error">
                Check the highlighted fields. Your edits are still here.
            </Alert>}
            <TextField label="Assignment title" value={draft.title} required disabled={readOnly}
                onChange={(event) => change({ title: event.target.value })}
                error={!!errors.title} helperText={errors.title} />
            <SegmentedControl label="Assignment instructions view" value={preview ? 'preview' : 'edit'} controls={instructionsRegion}
                onChange={value => setPreview(value === 'preview')}
                options={[{ value: 'edit', label: 'Instructions' }, { value: 'preview', label: 'Preview' }]} />
            <Box id={instructionsRegion} role="region" aria-label={preview ? 'Instructions preview' : 'Instructions editor'} sx={{ minWidth: 0 }}>
                {preview
                    ? draft.instructions.trim()
                        ? <MarkdownRenderer markdown={draft.instructions} />
                        : <Typography sx={{ color: 'text.secondary' }}>No instructions yet. Add Markdown in Instructions.</Typography>
                    : <TextField label="Instructions" fullWidth multiline minRows={6} value={draft.instructions} required disabled={readOnly}
                        onChange={(event) => change({ instructions: event.target.value })}
                        error={!!errors.instructions}
                        helperText={errors.instructions ?? 'Markdown plain text. Line breaks and indentation are kept.'} />}
            </Box>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, alignItems: 'start' }}>
                <TextField label="Deadline" type="datetime-local" value={draft.deadlineInput} required disabled={readOnly}
                    onChange={(event) => change({ deadlineInput: event.target.value })}
                    error={!!errors.deadline}
                    helperText={errors.deadline ?? `Time zone: ${timeZone}. Any date and time may be set, including a past deadline.`}
                    slotProps={{ inputLabel: { shrink: true }, htmlInput: { step: 1 } }} />
                {courseFailed
                    ? <Alert severity="error" action={course ? <Button size="small" variant="text" onClick={course.reload}>Try again</Button> : undefined}>
                        Lessons couldn&apos;t be loaded.
                    </Alert>
                    : <TextField select label="Related lesson" value={courseLoading ? '' : draft.lessonId ?? ''}
                        disabled={readOnly || courseLoading}
                        onChange={(event) => change({ lessonId: event.target.value ? event.target.value : null })}
                        error={!!errors.lessonId}
                        helperText={errors.lessonId ?? (courseLoading
                            ? 'Loading lessons…'
                            : totalLessons === 0
                                ? 'This course has no lessons yet.'
                                : 'Learners see a lesson-linked assignment only while that lesson is published.')}
                        slotProps={{
                            inputLabel: { shrink: true },
                            select: {
                                displayEmpty: true,
                                renderValue: (selected) => {
                                    if (!selected) return 'No related lesson';
                                    const found = knownLessons.get(String(selected).toLowerCase());
                                    if (!found) return 'Lesson unavailable';
                                    return `${found.title}${found.publicationStatus === 'hidden' ? ' (hidden)' : ''}`;
                                },
                            },
                        }}>
                        {lessonMenuItems}
                    </TextField>}
            </Box>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                Assignment grades do not trigger real-time skill mastery updates.
            </Typography>
        </Tile>
        </Box>
        <Box role="region" aria-label="Save assignment" sx={{ position: 'sticky', bottom: 16, zIndex: 5,
            bgcolor: roles.bg.raised, borderRadius: `${radius.lg}px`, boxShadow: shadow.raised, p: 2,
            display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
            {!noAccess && !gone && (creating
                ? <Button variant="contained" onClick={() => void create()} disabled={editor.busy} aria-busy={editor.busy || undefined}>Create assignment</Button>
                : <Button variant="contained" onClick={() => void save()} disabled={editor.busy || !dirty} aria-busy={editor.busy || undefined}>Save changes</Button>)}
            {status && <Typography role="status" variant="body2" sx={{ color: 'text.secondary' }}>{status}</Typography>}
        </Box>
        <ConfirmDialog open={confirm === 'publish'} title="Publish assignment?"
            message="Learners in this course will see this assignment and can submit until the deadline."
            confirmLabel="Publish assignment" onCancel={() => setConfirm(null)} onConfirm={() => void publication('published')} />
        <ConfirmDialog open={confirm === 'hide'} title="Hide assignment?"
            message="Learners will no longer see this assignment. Their submissions and grades are kept."
            confirmLabel="Hide assignment" onCancel={() => setConfirm(null)} onConfirm={() => void publication('hidden')} />
        <ConfirmDialog open={confirm === 'leave'} isDestructive title="Leave without saving?" message="Your changes will be lost."
            cancelLabel="Stay" confirmLabel="Leave"
            onCancel={() => { setConfirm(null); setLeaveTo(null); }}
            onConfirm={() => { setConfirm(null); setLeaveTo(null); setDirty(false); dirtyRef.current = false; leaveTo?.go(); }} />
    </Box>;
}

// --- Design preview (fixture-backed, `/preview` only; never routed in production) ---

export interface AssignmentEditorPageProps { initial: AssignmentDraft; lessons: { id: string; title: string }[]; timeZoneLabel: string; onSave: (assignment: AssignmentDraft) => void; onCancel: () => void }

/** S19 Assignment editor preview component: instructions, deadline with explicit timezone, optional lesson link (US-12). */
export function AssignmentEditorPage({ initial, lessons, timeZoneLabel, onSave, onCancel }: AssignmentEditorPageProps) {
    const [draft, setDraft] = useState(initial), [errors, setErrors] = useState<Record<string, string>>({}), [saved, setSaved] = useState(false);
    const set = (patch: Partial<AssignmentDraft>) => { setDraft((current) => ({ ...current, ...patch })); setSaved(false); };
    return <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 760 }}>
        <Box component="form" noValidate sx={{ display: 'grid', gap: 2.5 }} onSubmit={(event) => {
            event.preventDefault(); const next: Record<string, string> = {};
            if (!draft.title.trim()) next.title = 'Enter an assignment title.';
            if (!draft.instructions.trim()) next.instructions = 'Write the instructions.';
            if (!draft.deadline) next.deadline = 'Choose a deadline.';
            setErrors(next); if (!Object.keys(next).length) { onSave(draft); setSaved(true); }
        }}>
            <Typography variant="h3" component="h2">{initial.title ? 'Edit assignment' : 'New assignment'}</Typography>
            <TextField label="Assignment title" value={draft.title} onChange={(event) => set({ title: event.target.value })} error={!!errors.title} helperText={errors.title} required />
            <TextField label="Instructions" multiline minRows={5} value={draft.instructions} onChange={(event) => set({ instructions: event.target.value })} error={!!errors.instructions} helperText={errors.instructions} required />
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <TextField label="Deadline" type="datetime-local" value={draft.deadline} onChange={(event) => set({ deadline: event.target.value })} error={!!errors.deadline} helperText={errors.deadline ?? `Time zone: ${timeZoneLabel}. Files are refused after the deadline.`} required />
                <TextField select label="Linked lesson (optional)" value={draft.lessonId ?? ''} onChange={(event) => set({ lessonId: event.target.value || undefined })}>
                    <MenuItem value="">No linked lesson</MenuItem>{lessons.map((lesson) => <MenuItem key={lesson.id} value={lesson.id}>{lesson.title}</MenuItem>)}
                </TextField>
            </Box>
            <Box><Typography component="p" sx={{ fontWeight: 600, mb: 1 }}>Visibility</Typography>
                <SegmentedControl label="Assignment visibility" value={draft.publication} onChange={(publication) => set({ publication })} options={[{ value: 'hidden', label: 'Hidden', icon: 'eye-off' }, { value: 'published', label: 'Published', icon: 'eye' }]} /></Box>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Assignment grades count toward the course but don&apos;t update skill mastery.</Typography>
            <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', flexWrap: 'wrap' }}>
                <Button type="submit" variant="contained">Save assignment</Button><Button variant="outlined" onClick={onCancel}>Cancel</Button>
                {saved && <StatusPill tone="mint" icon="check-circle" size="tag">Saved</StatusPill>}
            </Box>
        </Box>
    </Tile>;
}
