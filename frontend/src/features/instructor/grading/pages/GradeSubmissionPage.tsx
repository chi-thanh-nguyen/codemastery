import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, TextField, Typography } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { FactList, Tile, StatGroup, StatTile } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import type { SubmissionRow } from '../types/gradingTypes';
import { color, radius } from '../../../../app/theme/theme';

export interface GradeSubmissionPageProps { submission: SubmissionRow; onSave: (grade: number, feedback: string) => void; onDownload: () => void; back?: Crumb; notice?: ReactNode }

/** S21 Grade a submission: grade 0–100 and required feedback; read-only once graded (US-13). */
export function GradeSubmissionPage({ submission, onSave, onDownload, back, notice }: GradeSubmissionPageProps) {
    const [grade, setGrade] = useState(''), [feedback, setFeedback] = useState(''), [errors, setErrors] = useState<Record<string, string>>({}), [confirm, setConfirm] = useState(false);
    const validate = () => {
        const next: Record<string, string> = {}, value = Number(grade);
        if (grade.trim() === '' || !Number.isFinite(value) || value < 0 || value > 100) next.grade = 'Enter a grade from 0 to 100.';
        if (!feedback.trim()) next.feedback = 'Write feedback for the learner.';
        setErrors(next); return !Object.keys(next).length;
    };
    return <Page width="reading">
        <PageHeader title={submission.assignmentTitle} subtitle={`${submission.learnerName} · ${submission.courseTitle}`} back={back} />
        {notice}
        <Box sx={{ display: 'grid', gap: 3 }}>
            <Tile padding={{ xs: 3, sm: 4 }}>
                <FactList columns={2} items={[{ label: 'Learner', value: submission.learnerName }, { label: 'Submitted', value: submission.submittedAtLabel }]} />
                <Box sx={{ mt: 3, display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: `${radius.md}px`, bgcolor: color.canvas100, flexWrap: 'wrap' }}>
                    <PresentationIcon name="file" sx={{ color: 'text.secondary' }} /><Typography sx={{ flex: 1, fontWeight: 600, overflowWrap: 'anywhere' }}>{submission.fileName}</Typography>
                    <Button variant="outlined" size="small" onClick={onDownload} startIcon={<PresentationIcon name="download" />}>Download</Button>
                </Box>
            </Tile>
            {submission.graded ? <Tile padding={{ xs: 3, sm: 4 }}>
                <Typography variant="h2" sx={{ mb: 2 }}>Grade and feedback</Typography>
                <StatGroup label="Grade"><StatTile value={submission.graded.grade} label="Grade out of 100" tone="mint" /></StatGroup>
                <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>Graded {submission.graded.gradedAtLabel}. Graded submissions can't be changed.</Typography>
                <Typography variant="reading" component="p" sx={{ m: 0, mt: 1.5, whiteSpace: 'pre-line' }}>{submission.graded.feedback}</Typography>
            </Tile> : <Tile padding={{ xs: 3, sm: 4 }}>
                <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); if (validate()) setConfirm(true); }} sx={{ display: 'grid', gap: 2.5 }}>
                    <Typography variant="h2">Grade</Typography>
                    <TextField label="Grade (0–100)" value={grade} onChange={(event) => setGrade(event.target.value)} error={!!errors.grade} helperText={errors.grade} required sx={{ maxWidth: 220 }}
                        slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
                    <TextField label="Feedback" multiline minRows={5} value={feedback} onChange={(event) => setFeedback(event.target.value)} error={!!errors.feedback} helperText={errors.feedback ?? 'The learner sees this with their grade.'} required />
                    <Alert severity="info">Assignment grades count toward the course. They don't update skill mastery.</Alert>
                    <Box><Button type="submit" variant="contained">Save grade</Button></Box>
                </Box>
            </Tile>}
        </Box>
        <ConfirmDialog open={confirm} title="Save this grade?" message="The learner will see this grade and receive a notification. You can't change it afterwards."
            confirmLabel="Save grade" cancelLabel="Keep editing" onCancel={() => setConfirm(false)} onConfirm={() => { setConfirm(false); onSave(Number(grade), feedback.trim()); }} />
    </Page>;
}
