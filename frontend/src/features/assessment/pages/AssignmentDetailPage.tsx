import type { ReactNode } from 'react';
import { Alert, Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { FileUploadField } from '../../../shared/components/FileUploadField';
import type { UploadState } from '../../../shared/components/FileUploadField';
import { StatusPill } from '../../../shared/components/StatusPill';
import { StatTile, StatGroup, Tile } from '../../../shared/components/Tile';
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
