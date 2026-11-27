import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';
import { Link, useNavigate, useParams } from 'react-router';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { FileUploadField } from '../../../shared/components/FileUploadField';
import type { UploadState } from '../../../shared/components/FileUploadField';
import { StatusPill } from '../../../shared/components/StatusPill';
import { StatTile, StatGroup, Tile } from '../../../shared/components/Tile';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
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
}

/** S13 Assignment: instructions, deadline, submission and grade/feedback (US-08). Grades do not update mastery. */
export function AssignmentDetailPage({ assignment, submission, upload, onSelectFile, requirements, back, notice }: AssignmentDetailPageProps) {
    const closed = assignment.deadlineState === 'closed', graded = submission?.graded;
    return <Page width="reading">
        <PageHeader title={assignment.title} back={back} subtitle={assignment.lessonTitle ? `Linked lesson: ${assignment.lessonTitle}` : undefined}
            meta={<StatusPill tone={closed ? 'rose' : assignment.deadlineState === 'soon' ? 'butter' : 'neutral'} icon="calendar">{closed ? `Closed · ${assignment.deadlineLabel}` : `Due ${assignment.deadlineLabel}`}</StatusPill>} />
        {notice}
        <Box sx={{ display: 'grid', gap: 3 }}>
            <Tile component="section" aria-labelledby="assignment-instructions" padding={{ xs: 3, sm: 4 }}>
                <Typography id="assignment-instructions" variant="h2" sx={{ mb: 2 }}>Instructions</Typography>
                {assignment.instructions.map((paragraph) => <Typography key={paragraph} variant="reading" component="p" sx={{ m: 0, mb: 2, maxWidth: '70ch' }}>{paragraph}</Typography>)}
            </Tile>
            {graded && <Tile component="section" aria-labelledby="assignment-grade" padding={{ xs: 3, sm: 4 }}>
                <Typography id="assignment-grade" variant="h2" sx={{ mb: 2 }}>Grade and feedback</Typography>
                <StatGroup label="Grade"><StatTile value={`${graded.grade}`} label="Grade out of 100" tone="mint" /></StatGroup>
                <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>Graded {graded.gradedAtLabel}</Typography>
                <Typography variant="reading" component="p" sx={{ m: 0, mt: 1.5, maxWidth: '70ch', whiteSpace: 'pre-line' }}>{graded.feedback}</Typography>
                <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>Assignment grades count toward the course. They don't change skill mastery; quizzes do.</Typography>
            </Tile>}
            <Box component="section" aria-labelledby="assignment-submission" sx={{ mt: 2 }}>
                <Typography id="assignment-submission" variant="h2" sx={{ mb: 2 }}>Your submission</Typography>
                {submission && <Box sx={{ mb: 2.5 }}><StatusPill tone="mint" icon="check-circle">Submitted · {submission.submittedAtLabel}</StatusPill>
                    <Typography sx={{ mt: 1, overflowWrap: 'anywhere' }}>{submission.fileName}</Typography></Box>}
                {closed ? <Alert severity="error">The deadline has passed. Submissions are closed.</Alert>
                    : graded ? <Alert severity="info">This submission is graded and can't be replaced.</Alert>
                        : <FileUploadField label={submission ? 'Replace file' : 'Submit file'} requirements={requirements} state={upload} onSelect={onSelectFile} />}
                {!closed && !graded && submission && <Typography variant="caption" sx={{ display: 'block', mt: 1.5, color: 'text.secondary' }}>You can replace your file until the deadline while it is ungraded. The latest file is the one your instructor sees.</Typography>}
            </Box>
            {!submission && closed && <Button variant="outlined" onClick={back?.onClick}>Back to assessments</Button>}
        </Box>
    </Page>;
}

/** Live S13 Assignment Detail Page (DESIGN.md r12 / CT10 / CT09). */
export function LiveAssignmentDetailPage() {
    const { courseId, assignmentId } = useParams<{ courseId: string; assignmentId: string }>();
    const navigate = useNavigate();

    const {
        assignment,
        loading,
        errorStatus,
        errorMessage,
        submitting,
        submitError,
        refresh,
        submitFile,
    } = useAssignment(courseId, assignmentId);

    const [replaceConfirm, setReplaceConfirm] = useState(false);
    const [pendingFile, setPendingFile] = useState<File | null>(null);
    const [uploadState, setUploadState] = useState<UploadState>({ kind: 'idle' });

    if (loading) {
        return <Page><LoadingView message="Loading assignment details…" /></Page>;
    }

    if (errorStatus === 403) {
        return (
            <Page>
                <SystemStateView
                    icon="lock"
                    title="Rejoin the course to see this assignment"
                    message="Your membership is required to access assignment details and submit files."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId ?? '')}`} variant="contained">
                            View course details
                        </Button>
                    }
                />
            </Page>
        );
    }

    if (errorStatus === 404) {
        return (
            <Page>
                <SystemStateView
                    icon="search"
                    title="Assignment not available"
                    message="This assignment doesn't exist or isn't published."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId ?? '')}/assessments`} variant="contained">
                            Back to assessments
                        </Button>
                    }
                />
            </Page>
        );
    }

    if (errorMessage || !assignment || !courseId || !assignmentId) {
        return (
            <Page>
                <ErrorView title="Couldn't load assignment" message={errorMessage ?? 'Please try again.'} onRetry={refresh} />
            </Page>
        );
    }

    const deadlineDate = new Date(assignment.deadlineAt);
    const deadlineLabel = deadlineDate.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
    const isSoon = deadlineDate.getTime() - Date.now() < 48 * 3600 * 1000 && assignment.open;

    const uploadFile = async (file: File) => {
        try {
            setUploadState({ kind: 'uploading', fileName: file.name });
            const sub = await submitFile(file);
            setUploadState({ kind: 'accepted', fileName: sub.originalFilename });
        } catch (err: unknown) {
            setUploadState({
                kind: 'rejected',
                message: err instanceof Error ? err.message : 'Upload failed. Please try again.',
            });
        }
    };

    const handleFileSelected = (file: File) => {
        if (assignment.submission && !assignment.submission.graded) {
            setPendingFile(file);
            setReplaceConfirm(true);
            return;
        }
        void uploadFile(file);
    };

    const instructionsParagraphs = assignment.instructions
        .split(/\r?\n\s*\r?\n/)
        .map((p) => p.trim())
        .filter(Boolean);

    const assignmentView: AssignmentView = {
        id: assignment.id,
        title: assignment.title,
        instructions: instructionsParagraphs.length > 0 ? instructionsParagraphs : [assignment.instructions],
        deadlineLabel,
        deadlineState: !assignment.open ? 'closed' : isSoon ? 'soon' : 'open',
    };

    const submissionView: SubmissionView | undefined = assignment.submission
        ? {
            fileName: assignment.submission.originalFilename,
            submittedAtLabel: new Date(assignment.submission.submittedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }),
            graded: assignment.submission.graded
                ? {
                    grade: assignment.submission.gradePercentage!,
                    feedback: assignment.submission.feedback!,
                    gradedAtLabel: new Date(assignment.submission.gradedAt!).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }),
                }
                : undefined,
        }
        : undefined;

    const relatedNotice = (
        <>
            {submitError && <Alert severity="error" sx={{ mb: 2 }}>{submitError}</Alert>}
            {assignment.lessonId && (
                <Box sx={{ mb: 2 }}>
                    <Button
                        component={Link}
                        to={`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(assignment.lessonId)}`}
                        variant="text"
                        size="small"
                    >
                        Related lesson
                    </Button>
                </Box>
            )}
        </>
    );

    return (
        <Box>
            <AssignmentDetailPage
                assignment={assignmentView}
                submission={submissionView}
                requirements="PDF or ZIP file"
                upload={submitting && pendingFile ? { kind: 'uploading', fileName: pendingFile.name } : uploadState}
                onSelectFile={handleFileSelected}
                notice={relatedNotice}
                back={{
                    label: 'Assessments',
                    href: `/courses/${encodeURIComponent(courseId)}/assessments`,
                    onClick: () => navigate(`/courses/${encodeURIComponent(courseId)}/assessments`),
                }}
            />

            <ConfirmDialog
                open={replaceConfirm}
                title="Replace file?"
                message={`Your current file "${assignment.submission?.originalFilename ?? ''}", submitted ${assignment.submission ? new Date(assignment.submission.submittedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : ''}, will be replaced.`}
                confirmLabel={submitting ? 'Replacing…' : 'Replace file'}
                cancelLabel="Cancel"
                isDestructive={true}
                onCancel={() => {
                    setReplaceConfirm(false);
                    setPendingFile(null);
                }}
                onConfirm={() => {
                    const targetFile = pendingFile;
                    setReplaceConfirm(false);
                    setPendingFile(null);
                    if (targetFile) void uploadFile(targetFile);
                }}
            />
        </Box>
    );
}