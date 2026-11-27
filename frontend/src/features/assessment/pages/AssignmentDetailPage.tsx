import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';
import { Link, useNavigate, useParams } from 'react-router';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { FileDownloadButton, FileUploadField } from '../../../shared/components/FileUploadField';
import type { UploadState } from '../../../shared/components/FileUploadField';
import { StatusPill } from '../../../shared/components/StatusPill';
import { StatTile, StatGroup, Tile } from '../../../shared/components/Tile';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { useAuth } from '../../auth/hooks/useAuth';
import { isDestinationLive } from '../../../routes/routePaths';
import { MarkdownRenderer } from '../../../shared/components/MarkdownRenderer';
import { downloadOwnSubmission } from '../api/assignmentApi';
import { useAssignment } from '../hooks/useAssignment';
import type { AssignmentView, SubmissionView } from '../types/assessmentTypes';

export interface AssignmentDetailPageProps {
    assignment: AssignmentView;
    submission?: SubmissionView;
    upload: UploadState;
    onSelectFile: (file: File) => void;
    /** Accepted types/size from the owning contract (CT09 pending). */
    requirements?: string;
    back?: Crumb;
    notice?: ReactNode;
    instructions?: ReactNode;
    submissionActions?: ReactNode;
    disabled?: boolean;
}

/** S13 Assignment: instructions, deadline, submission and grade/feedback (US-08). Grades do not update mastery. */
export function AssignmentDetailPage({ assignment, submission, upload, onSelectFile, requirements, back, notice, instructions, submissionActions, disabled }: AssignmentDetailPageProps) {
    const closed = assignment.deadlineState === 'closed', graded = submission?.graded;
    return <Page width="reading">
        <PageHeader title={assignment.title} back={back} subtitle={assignment.lessonTitle ? `Linked lesson: ${assignment.lessonTitle}` : undefined}
            meta={<StatusPill tone={closed ? 'neutral' : assignment.deadlineState === 'soon' ? 'butter' : 'neutral'} icon="calendar">{closed ? `Closed · ${assignment.deadlineLabel}` : `Due ${assignment.deadlineLabel}`}</StatusPill>} />
        {notice}
        <Box sx={{ display: 'grid', gap: 3 }}>
            <Box component="section" aria-labelledby="assignment-submission" sx={{ mt: 2 }}>
                <Typography id="assignment-submission" variant="h2" sx={{ mb: 2 }}>Your submission</Typography>
                {submission && <Box sx={{ mb: 2.5 }}><StatusPill tone="mint" icon="check-circle">Submitted · {submission.submittedAtLabel}</StatusPill>
                    <Typography sx={{ mt: 1, overflowWrap: 'anywhere' }}>{submission.fileName}</Typography></Box>}
                {submissionActions}
                {closed ? <Alert severity="error">The deadline has passed. Submissions are closed.</Alert>
                    : graded ? <Alert severity="info">This submission has been graded and can no longer be replaced.</Alert>
                        : <FileUploadField label={submission ? 'Replace file' : 'Submit file'} requirements={requirements} state={upload} disabled={disabled} accept=".pdf,.zip" onSelect={onSelectFile} />}
                {!closed && !graded && submission && <Typography variant="caption" sx={{ display: 'block', mt: 1.5, color: 'text.secondary' }}>You can replace your file until the deadline while it is ungraded. The latest file is the one your instructor sees.</Typography>}
            </Box>
            {graded && <Box component="section" aria-labelledby="assignment-grade">
                <Typography id="assignment-grade" variant="h2" sx={{ mb: 2 }}>Grade and feedback</Typography>
                <StatGroup label="Grade"><StatTile value={`${graded.grade} / 100`} label="Grade" tone="mint" /></StatGroup>
                <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>Graded · {graded.gradedAtLabel}</Typography>
                <Typography variant="reading" component="p" sx={{ m: 0, mt: 1.5, maxWidth: '70ch', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{graded.feedback}</Typography>
            </Box>}
            <Tile component="section" aria-labelledby="assignment-instructions" padding={{ xs: 3, sm: 4 }}>
                <Typography id="assignment-instructions" variant="h2" sx={{ mb: 2 }}>Instructions</Typography>
                {instructions ?? assignment.instructions.map((paragraph) => <Typography key={paragraph} variant="reading" component="p" sx={{ m: 0, mb: 2, maxWidth: '70ch' }}>{paragraph}</Typography>)}
            </Tile>
            {!submission && closed && <Button variant="outlined" onClick={back?.onClick}>Back to assessments</Button>}
        </Box>
    </Page>;
}


/** Display a copy; contract tokens remain unchanged in the API/hook. */
export function assignmentTime(value: string) {
    return new Intl.DateTimeFormat('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' }).format(new Date(value));
}
export function assignmentDeadline(value: string, open: boolean, now: number) {
    const delta = new Date(value).getTime() - now;
    const minutes = Math.ceil(Math.abs(delta) / 60000);
    const count = minutes < 60 ? minutes : minutes < 1440 ? Math.ceil(minutes / 60) : Math.ceil(minutes / 1440);
    const unit = minutes < 60 ? 'minute' : minutes < 1440 ? 'hour' : 'day';
    const relative = `${count} ${unit}${count === 1 ? '' : 's'}`;
    return { label: assignmentTime(value), relative: delta > 0 ? `Due in ${relative}` : `Deadline ${relative} ago`, state: !open ? 'closed' as const : delta >= 0 && delta <= 48 * 3600000 ? 'soon' as const : 'open' as const };
}
export function LiveAssignmentDetailPage() {
    const { courseId, assignmentId } = useParams(), auth = useAuth();
    return <AssignmentSessionPage key={`${auth.generation}|${courseId}|${assignmentId}`} courseId={courseId} assignmentId={assignmentId} />;
}
function AssignmentSessionPage({ courseId, assignmentId }: { courseId?: string; assignmentId?: string }) {
    const auth = useAuth(), navigate = useNavigate();
    const state = useAssignment(courseId, assignmentId), assignment = state.assignment;
    const [pending, setPending] = useState<{ file: File; submittedAt: string } | null>(null);
    const [selectionError, setSelectionError] = useState(''), [now, setNow] = useState(Date.now);
    useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer); }, []);
    // Browser time schedules one fresh authoritative read; it never decides open/closed.
    useEffect(() => {
        if (!assignment?.open) return;
        const delay = new Date(assignment.deadlineAt).getTime() - Date.now();
        if (delay <= 0 || delay > 2147483647) return;
        const timer = setTimeout(() => { setPending(null); void state.refresh(); }, delay);
        return () => clearTimeout(timer);
    }, [assignment?.deadlineAt, assignment?.open]);
    useEffect(() => { if (!state.canSubmit || pending && pending.submittedAt !== assignment?.submission?.submittedAt) setPending(null); }, [state.canSubmit, assignment?.submission?.submittedAt]);
    const backPath = `/courses/${encodeURIComponent(courseId ?? '')}/assessments`;
    const back = { label: 'Assessments', href: backPath, onClick: () => navigate(backPath) };
    const heading = <PageHeader title="Assignment" back={back} />;
    if (state.loading && !assignment) return <Page><LoadingView message="Loading assignment details…" /></Page>;
    if (state.error?.status === 403) return <Page>{heading}<SystemStateView icon="lock" title="Rejoin the course to see this assignment." message="Your submission and grade are kept."
        actions={<Button component={Link} to={`/courses/${encodeURIComponent(courseId ?? '')}`} variant="contained">View course details</Button>} /></Page>;
    if (state.error?.status === 404) return <Page>{heading}<SystemStateView icon="search" title="Assignment not available" message="It may have been hidden or removed from this course." actions={<Button component={Link} to={backPath}>Back to assessments</Button>} /></Page>;
    if (!assignment) return <Page>{heading}{state.notice && <Alert severity="warning">{state.notice}</Alert>}<ErrorView title="Couldn't load assignment" message={state.error?.message ?? 'Check the assignment link and try again.'} onRetry={() => void state.refresh()} /></Page>;
    const deadline = assignmentDeadline(assignment.deadlineAt, assignment.open, now), sub = assignment.submission;
    const lessonPath = assignment.lessonId ? `/courses/${encodeURIComponent(assignment.courseId)}/lessons/${encodeURIComponent(assignment.lessonId)}` : null;
    const upload: UploadState = state.phase === 'uploading' ? { kind: 'uploading', fileName: state.fileName }
        : selectionError ? { kind: 'rejected', message: selectionError } : { kind: 'idle' };
    const select = (file: File) => {
        if (!state.canSubmit) return;
        setSelectionError('');
        if (!/\.(pdf|zip)$/i.test(file.name) || !file.size) { setSelectionError('Choose a non-empty PDF or ZIP file.'); return; }
        if (sub) setPending({ file, submittedAt: sub.submittedAt });
        else void state.submitFile(file, null).catch(() => {});
    };
    return <>
        <AssignmentDetailPage assignment={{ id: assignment.id, title: assignment.title, instructions: [], deadlineLabel: deadline.label, deadlineState: deadline.state }}
            instructions={<MarkdownRenderer markdown={assignment.instructions} />} submission={sub ? { fileName: sub.originalFilename, submittedAtLabel: assignmentTime(sub.submittedAt),
                graded: sub.graded ? { grade: sub.gradePercentage!, feedback: sub.feedback!, gradedAtLabel: assignmentTime(sub.gradedAt!) } : undefined } : undefined}
            upload={upload} onSelectFile={select} disabled={!state.canSubmit || !!pending} requirements="PDF or ZIP file" back={back}
            submissionActions={sub ? <Box sx={{ mb: 2 }}><FileDownloadButton key={`${sub.submittedAt}|${auth.generation}`} label="Download" load={signal => downloadOwnSubmission(assignment.courseId, assignment.id, { signal, isCurrent: () => auth.isCurrent(auth.generation) })} /></Box> : undefined}
            notice={<Box sx={{ mb: 3 }}><Typography variant="body2" sx={{ color: 'text.secondary' }}>{deadline.relative}</Typography>
                {lessonPath && (isDestinationLive(lessonPath, 'Learner') ? <Button component={Link} to={lessonPath}>Related lesson</Button> : <Typography variant="body2">Related lesson is not available yet.</Typography>)}
                {state.notice && <Alert severity={state.notice.startsWith('File submitted.') ? 'success' : 'warning'}>{state.notice}</Alert>}
                {(state.phase === 'checking' || state.phase === 'loading') && <LoadingView message="Reloading current assignment state…" variant="none" />}
                {state.phase === 'uploading' && <Button onClick={state.cancelUpload}>Cancel upload</Button>}
                <Button disabled={state.phase === 'uploading' || state.phase === 'checking'} onClick={() => { setPending(null); void state.refresh(); }}>Refresh assignment</Button>
            </Box>} />
        <ConfirmDialog open={!!pending} title="Replace file?" isDestructive confirmLabel="Replace file"
            message={`Your current file "${sub?.originalFilename ?? ''}", submitted ${sub ? assignmentTime(sub.submittedAt) : ''}, will be replaced.`}
            busy={!state.canSubmit} onCancel={() => setPending(null)} onConfirm={() => { const selected = pending; setPending(null); if (selected && state.canSubmit) void state.submitFile(selected.file, selected.submittedAt).catch(() => {}); }} />
    </>;
}
