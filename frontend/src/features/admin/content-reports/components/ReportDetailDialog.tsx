import { useState } from 'react';
import { Box, Button, Drawer, IconButton, Typography, useMediaQuery, useTheme } from '@mui/material';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { FactList } from '../../../../shared/components/Tile';
import { ConfirmDialog } from '../../../../shared/components/ConfirmDialog';
import { reportStatus } from './ReportTable';
import type { ReportRow } from '../types/contentReportTypes';
import { color, radius } from '../../../../app/theme/theme';

/** Side sheet with the reported content and the two decisions: hide (resolves) or dismiss (US-16). */
export function ReportDetailDialog({ report, onClose, onHide, onDismiss }: { report: ReportRow | null; onClose: () => void; onHide: (id: string) => void; onDismiss: (id: string) => void }) {
    const full = useMediaQuery(useTheme().breakpoints.down('md'));
    const [confirm, setConfirm] = useState<'hide' | 'dismiss' | null>(null);
    return <Drawer anchor="right" open={!!report} onClose={onClose} slotProps={{ paper: { 'aria-labelledby': 'report-sheet-title', sx: { width: full ? '100%' : 480, bgcolor: color.surface, color: color.ink900, borderRadius: full ? 0 : `${radius.lg}px 0 0 ${radius.lg}px`, p: 3 } } as object }}>
        {report && <Box sx={{ display: 'grid', gap: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
                <Box><Typography variant="caption" sx={{ color: 'text.secondary' }}>Report on a {report.targetType.toLowerCase()}</Typography><Typography id="report-sheet-title" variant="h3" component="h2">{report.targetLabel}</Typography></Box>
                <IconButton aria-label="Close report details" onClick={onClose}><PresentationIcon name="close" /></IconButton>
            </Box>
            <StatusPill tone={reportStatus[report.status].tone} icon={reportStatus[report.status].icon}>{reportStatus[report.status].label}</StatusPill>
            <Box sx={{ bgcolor: color.canvas100, borderRadius: `${radius.md}px`, p: 2 }}><Typography variant="caption" sx={{ color: 'text.secondary' }}>Reported content</Typography><Typography sx={{ mt: .5, whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>{report.excerpt}</Typography></Box>
            <FactList items={[{ label: 'Course', value: report.courseTitle }, { label: 'Reported by', value: report.reporter }, { label: 'Reported', value: report.reportedAtLabel }, ...(report.resolvedAtLabel ? [{ label: 'Closed', value: report.resolvedAtLabel }] : [])]} />
            {report.status === 'open' ? <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Button variant="contained" color="error" onClick={() => setConfirm('hide')} startIcon={<PresentationIcon name="eye-off" />}>Hide content</Button>
                <Button variant="outlined" onClick={() => setConfirm('dismiss')}>Dismiss report</Button>
            </Box> : <Typography variant="body2" sx={{ color: 'text.secondary' }}>This report is closed. Decisions are recorded in the audit log.</Typography>}
        </Box>}
        <ConfirmDialog open={confirm === 'hide'} isDestructive title="Hide this content?" message="Learners will no longer see it. The report is resolved and the decision is recorded in the audit log."
            confirmLabel="Hide content" onCancel={() => setConfirm(null)} onConfirm={() => { setConfirm(null); if (report) onHide(report.id); }} />
        <ConfirmDialog open={confirm === 'dismiss'} title="Dismiss this report?" message="The content stays visible and the report closes. The decision is recorded in the audit log."
            confirmLabel="Dismiss report" onCancel={() => setConfirm(null)} onConfirm={() => { setConfirm(null); if (report) onDismiss(report.id); }} />
    </Drawer>;
}
