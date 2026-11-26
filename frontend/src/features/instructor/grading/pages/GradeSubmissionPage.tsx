import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { Page, useNavigationGuard } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { FactList, Tile } from '../../../../shared/components/Tile';
import { FileDownloadButton } from '../../../../shared/components/FileUploadField';
import { ErrorView, LoadingView, SystemStateView } from '../../../../shared/components/StatusViews';
import { useAuth } from '../../../auth/hooks/useAuth';
import { readOwnedAssignment } from '../../course-management/api/assessmentAuthoringApi';
import { failureStatus, isCancelled, useAuthoringOperation, useOwnerRead } from '../../course-management/hooks/useAssessmentEditor';
import { errorMessage } from '../../course-management/pages/QuestionBankPage';
import { downloadSubmission, gradeSubmission, gradingId, validGrade } from '../api/gradingApi';
import { gradingPath, readReviewedState, useReviewedSubmission } from '../hooks/useSubmissions';
import { toSubmissionRow } from './SubmissionListPage';
import type { SubmissionRow, SubmissionSummary } from '../types/gradingTypes';
import { protocolError } from '../../../../shared/types/apiTypes';
import { radius, roles } from '../../../../app/theme/theme';

export interface GradingDraft { grade: string; feedback: string }
export function gradeErrors(draft: GradingDraft) {
    const errors: Record<string, string> = {};
    if (!/^\d+(?:\.\d{1,2})?$/.test(draft.grade) || !validGrade(Number(draft.grade))) errors.grade = 'Enter a grade from 0 to 100 with up to two decimal places.';
    if (!/\S/.test(draft.feedback)) errors.feedback = 'Write feedback for the learner.';
    return errors;
}
export interface GradeSubmissionPageProps {
    submission: SubmissionRow; onSave: (grade: number, feedback: string) => void;
    onDownload?: () => void; download?: ReactNode; back?: Crumb; notice?: ReactNode;
    draft?: GradingDraft; onDraftChange?: (draft: GradingDraft) => void; busy?: boolean; locked?: boolean;
}
/** Shared S21 presentation. Production supplies decoded data and a controlled draft; preview stays local. */
export function GradeSubmissionPage({ submission, onSave, onDownload, download, back, notice, draft: controlled, onDraftChange, busy, locked }: GradeSubmissionPageProps) {
    const [local, setLocal] = useState<GradingDraft>({ grade: submission.graded ? String(submission.graded.grade) : '', feedback: submission.graded?.feedback ?? '' });
    const draft = controlled ?? local, change = onDraftChange ?? setLocal;
    const [errors, setErrors] = useState<Record<string, string>>({}), [confirm, setConfirm] = useState(false);
    const gradeInput = useRef<HTMLInputElement>(null), feedbackInput = useRef<HTMLTextAreaElement>(null);
    return <Page width="reading"><PageHeader title={submission.assignmentTitle} subtitle={submission.learnerName} back={back} />{notice}
        <Box sx={{ display: 'grid', gap: 3, minWidth: 0 }}>
            <Tile padding={{ xs: 3, sm: 4 }}><FactList columns={2} items={[{ label: 'Learner', value: submission.learnerName }, { label: 'Submitted', value: submission.submittedAtLabel }]} />
                <Box sx={{ mt: 3, display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: `${radius.md}px`, bgcolor: roles.bg.canvas, flexWrap: 'wrap' }}>
                    <Typography sx={{ flex: 1, minWidth: 0, fontWeight: 600, overflowWrap: 'anywhere' }}>{submission.fileName}</Typography>
                    {download ?? <Button variant="outlined" onClick={onDownload}>Download</Button>}
                </Box>
            </Tile>
            <Tile padding={{ xs: 3, sm: 4 }}><Box component="form" noValidate onSubmit={e => { e.preventDefault(); if (locked || busy) return; const next = gradeErrors(draft); setErrors(next); if (!Object.keys(next).length) setConfirm(true); else (next.grade ? gradeInput : feedbackInput).current?.focus(); }} sx={{ display: 'grid', gap: 2.5 }}>
                <Typography variant="h2">Grade and feedback</Typography>
                {Object.keys(errors).length > 0 && <Alert severity="error" role="alert">Check the grade and feedback fields below.</Alert>}
                {submission.graded && <Typography variant="body2">Current grade: {submission.graded.grade} / 100 · Graded {submission.graded.gradedAtLabel}</Typography>}
                <TextField inputRef={gradeInput} label="Grade (0–100)" value={draft.grade} onChange={e => { change({ ...draft, grade: e.target.value }); setErrors({}); }} error={!!errors.grade} helperText={errors.grade} required disabled={busy || locked} sx={{ maxWidth: 260 }} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
                <TextField inputRef={feedbackInput} label="Feedback" multiline minRows={5} value={draft.feedback} onChange={e => { change({ ...draft, feedback: e.target.value }); setErrors({}); }} error={!!errors.feedback} helperText={errors.feedback ?? 'The learner sees this with their grade.'} required disabled={busy || locked} />
                <Alert severity="info">Assignment grades don't update skill mastery.</Alert>
                <Box><Button type="submit" variant="contained" disabled={busy || locked}>{busy ? 'Saving grade…' : submission.graded ? 'Update grade' : 'Save grade'}</Button></Box>
            </Box></Tile>
        </Box>
        <ConfirmDialog open={confirm && !locked} title="Save grade?" message="The learner will see this grade and feedback on the assignment page." confirmLabel={submission.graded ? 'Update grade' : 'Save grade'} cancelLabel="Keep editing" busy={busy}
            onCancel={() => setConfirm(false)} onConfirm={() => { if (!busy && !locked) { setConfirm(false); onSave(Number(draft.grade), draft.feedback); } }} />
    </Page>;
}
const UNKNOWN_LOOKUP = "We couldn't find this submission in the current list. The list may have changed, for example because the learner replaced the file.";
export function ProductionGradeSubmissionPage() {
    const { submissionId } = useParams(), [query] = useSearchParams(), auth = useAuth(), location = useLocation();
    const courseId = query.get('courseId'), assignmentId = query.get('assignmentId');
    if (!gradingId(courseId) || !gradingId(assignmentId) || !gradingId(submissionId) || query.getAll('courseId').length !== 1 || query.getAll('assignmentId').length !== 1) return <Page><SystemStateView icon="search" title="We can't open this submission from this link." message="Choose an assignment in grading to continue." actions={<Button component={Link} to="/teach/grading">Back to grading</Button>} /></Page>;
    return <GradeController key={`${auth.generation}|${courseId.toLowerCase()}|${assignmentId.toLowerCase()}|${submissionId.toLowerCase()}`}
        courseId={courseId.toLowerCase()} assignmentId={assignmentId.toLowerCase()} submissionId={submissionId.toLowerCase()} state={location.state} />;
}
function GradeController({ courseId, assignmentId, submissionId, state }: { courseId: string; assignmentId: string; submissionId: string; state: unknown }) {
    const auth = useAuth(), navigate = useNavigate();
    const [context] = useState(() => readReviewedState(state, auth.generation, courseId, assignmentId, submissionId));
    const read = useReviewedSubmission(courseId, assignmentId, submissionId, context?.row ?? null);
    const assignment = useOwnerRead(context?.title && context.title !== 'Assignment' ? null : `grading-title|${courseId}|${assignmentId}`, options => readOwnedAssignment(courseId, assignmentId, options));
    const title = context?.title && context.title !== 'Assignment' ? context.title : assignment.data?.title ?? 'Assignment';
    const [last, setLast] = useState<SubmissionSummary | null>(null), [draft, setDraft] = useState<GradingDraft | null>(null);
    const [saved, setSaved] = useState<SubmissionSummary | null>(null);
    const [message, setMessage] = useState<string | null>(null), [recovery, setRecovery] = useState<'replacement' | 'unknown' | null>(null);
    const [downloaded, setDownloaded] = useState<string | null>(null), [refused, setRefused] = useState<number | null>(null);
    const operation = useAuthoringOperation();
    if (read.data && read.data !== last) { setLast(read.data); setSaved(null); if (!draft) setDraft({ grade: read.data.graded ? String(read.data.gradePercentage) : '', feedback: read.data.feedback ?? '' }); }
    const row = saved ?? read.data ?? last;
    const ready = !!read.data && !read.loading && !read.error;
    const locked = !ready || !!recovery || refused !== null;
    const [leave, setLeave] = useState<(() => void) | null>(null);
    const dirty = !!draft && !!row && (draft.grade !== (row.graded ? String(row.gradePercentage) : '') || draft.feedback !== (row.feedback ?? ''));
    useNavigationGuard(proceed => { if (!dirty && !operation.busy) return false; setLeave(() => proceed); return true; });
    const back = gradingPath(courseId, assignmentId);
    const goBack = () => { if (dirty || operation.busy) setLeave(() => () => navigate(back)); else navigate(back); };
    useEffect(() => {
        if (!dirty && !operation.busy) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty, operation.busy]);
    const recoveryView = message || read.loading || read.error || !read.data || recovery ? <Box sx={{ display: 'grid', gap: 2, mb: 3 }}>
        {message && <Alert severity={message === 'Grade saved.' ? 'success' : 'warning'} role={message === 'Grade saved.' ? 'status' : 'alert'}>{message}</Alert>}
        {read.loading && <LoadingView message="Loading submission…" />}
        {!!read.error && <ErrorView title="Submission couldn't be loaded." message={errorMessage(read.error)} onRetry={read.reload} />}
        {!read.loading && !read.error && !read.data && <ErrorView title="Submission lookup incomplete" message={UNKNOWN_LOOKUP} onRetry={read.reload} />}
        {recovery && ready && <>
            {recovery === 'unknown' && read.data && !read.data.graded && <Typography>Current result: not graded.</Typography>}
            {recovery === 'unknown' && read.data?.graded && <Box><Typography>Current saved grade: {read.data.gradePercentage} / 100</Typography><Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{read.data.feedback}</Typography></Box>}
            <Button variant="outlined" disabled={recovery === 'replacement' && downloaded !== read.data?.submittedAt} onClick={() => { setRecovery(null); setMessage(null); }}>{recovery === 'replacement' ? 'I have reviewed the new file' : 'I have reviewed the current result'}</Button>
            {recovery === 'replacement' && <Typography>Download and review the current file before saving again. Your draft is still here.</Typography>}
        </>}
    </Box> : null;
    if (refused === 403 || refused === 404 || [403, 404].includes(failureStatus(read.error) ?? 0)) return <Page><SystemStateView icon="shield" title={refused === 403 || failureStatus(read.error) === 403 ? "You don't have access to this submission" : 'Submission not available'} message="Go back to grading to see the current assignments." actions={<Button component={Link} to={back}>Back to submissions</Button>} /></Page>;
    async function save(grade: number, feedback: string) {
        if (!row || !ready || locked || operation.busy) return;
        setMessage(null);
        try {
            const result = await operation.run(options => gradeSubmission(courseId, assignmentId, submissionId, { gradePercentage: grade, feedback, submittedAt: row.submittedAt }, options));
            if (result.learnerId.toLowerCase() !== row.learnerId.toLowerCase() || result.originalFilename !== row.originalFilename) throw protocolError();
            setSaved(result); setMessage('Grade saved.'); setDraft({ grade: String(result.gradePercentage), feedback: result.feedback! });
        } catch (error) {
            if (isCancelled(error)) return;
            const status = failureStatus(error);
            if (status === 403 || status === 404) { setRefused(status); return; }
            if (status === 409) {
                setMessage('The learner replaced the file after you opened it. Review the new file before grading.');
                setRecovery('replacement'); setDownloaded(null); read.reload();
            } else if (status === 400) setMessage('Check the grade and feedback. The draft has been kept.');
            else {
                setMessage("We couldn't confirm whether your grade was saved. Review the current result before saving again. Your draft is still here.");
                setRecovery('unknown'); read.reload();
            }
        }
    }
    return <>
        {row && draft ? <GradeSubmissionPage submission={toSubmissionRow(row, title)} draft={draft} onDraftChange={next => { setDraft(next); setMessage(null); }} busy={operation.busy} locked={locked}
            back={{ label: 'Back to submissions', href: back, onClick: goBack }} notice={recoveryView} onSave={(g, f) => void save(g, f)}
            download={<FileDownloadButton key={row.submittedAt} label="Download" disabled={!ready || operation.busy} load={signal => downloadSubmission(courseId, assignmentId, submissionId, { signal })} onDownloaded={() => setDownloaded(row.submittedAt)} />} />
            : <Page><PageHeader title={title} back={{ label: 'Back to submissions', href: back, onClick: goBack }} />{recoveryView}</Page>}
        <ConfirmDialog open={!!leave} title="Leave without saving?" message="Your changes will be lost." confirmLabel="Leave" cancelLabel="Stay" onCancel={() => setLeave(null)} onConfirm={() => { const go = leave; setLeave(null); go?.(); }} />
    </>;
}
