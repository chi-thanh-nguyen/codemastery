import { useState } from 'react';
import { BrowserRouter } from 'react-router';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter, SessionNavigation } from '../routes/index';
import { ThemeProvider, CssBaseline, Box, Button, TextField, Paper, Typography, Chip, LinearProgress,
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, Tab, InputAdornment } from '@mui/material';
import { appTheme, presentationTokens as tokens } from './theme/theme';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { AuthLayout } from '../layouts/AuthLayout';
import { PageHeader } from '../shared/components/PageHeader';
import { ConfirmDialog } from '../shared/components/ConfirmDialog';
import { LoadingView, EmptyView, ErrorView } from '../shared/components/StatusViews';
import { PresentationIcon } from '../shared/components/PresentationIcon';
import type { PresentationIconName } from '../shared/components/PresentationIcon';
import type { NavItem } from '../shared/components/SidebarNav';
import { useDebounce } from '../shared/hooks/useDebounce';
import { formatDate, formatNumber, formatDurationSeconds } from '../shared/utils/format';

// Isolated, illustrative UI fixtures. These are not learner records or analytics.
const DEMO_LESSONS = [
    { title: 'Thinking in variables', topic: 'Programming foundations', state: 'Completed', progress: 100, icon: 'code' },
    { title: 'Making decisions', topic: 'Control flow', state: 'In progress', progress: 68, icon: 'layers' },
    { title: 'Patterns in repetition', topic: 'Loops & iteration', state: 'Ready to explore', progress: 0, icon: 'book' },
] as const;
const DEMO_WEEK = [
    { day: 'Mon', minutes: 30, height: 50 }, { day: 'Tue', minutes: 45, height: 75 },
    { day: 'Wed', minutes: 25, height: 42 }, { day: 'Thu', minutes: 60, height: 100 },
    { day: 'Fri', minutes: 40, height: 67 }, { day: 'Sat', minutes: 35, height: 58 }, { day: 'Sun', minutes: 25, height: 42 },
];
const NAV_ITEMS: NavItem[] = [
    { id: 'overview', label: 'Overview', icon: <PresentationIcon name="grid" /> },
    { id: 'components', label: 'Components', badge: 0, icon: <PresentationIcon name="layers" /> },
    { id: 'auth-preview', label: 'Auth layout preview', icon: <PresentationIcon name="book" /> },
];

function DemoMetric({ label, value, note, icon, tint }: { label: string; value: string; note: string; icon: PresentationIconName; tint: string }) {
    return <Paper sx={{ p: 2.5, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 2 }}>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
            <Box sx={{ width: 32, height: 32, display: 'grid', placeItems: 'center', borderRadius: '8px', bgcolor: tint, color: 'text.primary' }}>
                <PresentationIcon name={icon} sx={{ fontSize: 18 }} />
            </Box>
        </Box>
        <Typography variant="h4" component="p" sx={{ fontVariantNumeric: 'tabular-nums', mb: .75 }}>{value}</Typography>
        <Typography variant="caption" color="text.secondary">{note}</Typography>
    </Paper>;
}

export function PresentationPreview() {
    const [selectedNav, setSelectedNav] = useState('overview');
    const [query, setQuery] = useState('');
    const [filter, setFilter] = useState('all');
    const [utilityInput, setUtilityInput] = useState('');
    const [dialog, setDialog] = useState<'reset' | 'lesson' | null>(null);
    const [notice, setNotice] = useState('');
    // Explicit demonstration timing; not a shared or project-wide default.
    const debouncedQuery = useDebounce(query, 400);
    const debouncedInput = useDebounce(utilityInput, 400);
    const lessons = DEMO_LESSONS.filter((lesson) => lesson.title.toLowerCase().includes(debouncedQuery.toLowerCase()) &&
        (filter === 'all' || (filter === 'completed' ? lesson.state === 'Completed' : lesson.state !== 'Completed')));
    const sampleDate = formatDate('2026-10-23T09:00:00Z', { locale: 'en-US', options: { dateStyle: 'long', timeZone: 'UTC' } });
    const sampleRatio = formatNumber(.885, { locale: 'en-US', options: {
        style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1, roundingMode: 'halfExpand',
    } });
    const duration = formatDurationSeconds(3725);
    function resetPreview() { setQuery(''); setFilter('all'); setUtilityInput(''); setNotice('Sample controls reset.'); setDialog(null); }

    const overview = <>
        <PageHeader title="Your learning, in focus." subtitle="A little practice today. A stronger foundation tomorrow."
            actions={<Button key="explore" variant="contained" endIcon={<PresentationIcon name="arrow" />} onClick={() => setSelectedNav('components')}>Explore components</Button>} />
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, mb: 2 }}>
            <Chip label="Demo workspace" size="small" sx={{ bgcolor: tokens.softBlue, color: 'primary.dark' }} />
            <Typography variant="caption" color="text.secondary">All learning data below is illustrative. Nothing is saved.</Typography>
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 2, mb: 2 }}>
            <DemoMetric label="Study time" value="4h 20m" note="Illustrative weekly total" icon="clock" tint={tokens.softBlue} />
            <DemoMetric label="Lessons completed" value="12" note="Sample learning snapshot" icon="check" tint={tokens.softGreen} />
            <DemoMetric label="Practice streak" value="5 days" note="An example of steady progress" icon="spark" tint={tokens.softOrange} />
            <DemoMetric label="Course progress" value="68%" note="Illustrative course completion" icon="chart" tint={tokens.softPink} />
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 3fr) minmax(0, 2fr)' }, gap: 2, mb: 2 }}>
            <Paper sx={{ p: 2.5, minWidth: 0 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
                    <Box><Typography variant="h6" component="h2">Learning rhythm</Typography><Typography variant="caption" color="text.secondary">Illustrative study time · minutes</Typography></Box>
                    <Chip label="Sample week" size="small" variant="outlined" />
                </Box>
                <Box role="img" aria-label={`Demo study minutes: ${DEMO_WEEK.map((day) => `${day.day} ${day.minutes}`).join(', ')}.`}
                    sx={{ display: 'flex', gap: { xs: 1, sm: 2 }, height: 210, alignItems: 'flex-end', mt: 3 }}>
                    {DEMO_WEEK.map((day) => <Box key={day.day} aria-hidden="true" sx={{ flex: 1, minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', alignItems: 'center', gap: 1 }}>
                        <Typography variant="caption" color="text.secondary">{day.minutes}</Typography>
                        <Box sx={{ width: '100%', maxWidth: 38, height: `${day.height * .75}%`, minHeight: 3, borderRadius: '6px 6px 2px 2px', bgcolor: 'primary.light', border: '1px solid', borderColor: 'primary.dark' }} />
                        <Typography variant="caption" color="text.secondary">{day.day}</Typography>
                    </Box>)}
                </Box>
            </Paper>
            <Paper sx={{ p: 2.5, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Typography variant="h6" component="h2">Keep your curiosity going</Typography>
                    <PresentationIcon name="book" sx={{ fontSize: 20, color: 'text.secondary' }} />
                </Box>
                <Box sx={{ px: 2, py: 2.5, borderRadius: '10px', bgcolor: tokens.softBlue, mb: 2, overflow: 'hidden' }}>
                    <Typography variant="overline" color="primary.dark">Sample course</Typography>
                    <Typography component="pre" sx={{ fontFamily: 'ui-monospace, SFMono-Regular, monospace', fontSize: { xs: '.65rem', sm: '.75rem' }, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', color: 'primary.dark', mt: 1, mb: 0 }}>{"const nextStep = 'keep learning';"}</Typography>
                </Box>
                <Typography variant="subtitle1" component="h3">Programming foundations</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: .5, mb: 2 }}>Practice in small steps. Revisit what matters.</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}><Typography variant="caption" color="text.secondary">Demo completion</Typography><Typography variant="caption">68%</Typography></Box>
                <LinearProgress variant="determinate" value={68} aria-label="Sample course completion" />
                <Button variant="outlined" endIcon={<PresentationIcon name="arrow" />} onClick={() => setDialog('lesson')} sx={{ mt: 2.5, alignSelf: 'flex-start' }}>Preview sample lesson</Button>
            </Paper>
        </Box>
        <Paper sx={{ overflow: 'hidden', minWidth: 0 }}>
            <Box sx={{ p: 2.5, display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between', gap: 2 }}>
                <Box><Typography variant="h6" component="h2">Your sample learning path</Typography><Typography variant="caption" color="text.secondary">Three lesson cards to explore the presentation.</Typography></Box>
                <TextField size="small" label="Search sample lessons" value={query} onChange={(event) => setQuery(event.target.value)}
                    slotProps={{ input: { startAdornment: <InputAdornment position="start"><PresentationIcon name="search" sx={{ fontSize: 18 }} /></InputAdornment> } }}
                    sx={{ width: { xs: '100%', sm: 245 }, flexShrink: 0 }} />
            </Box>
            <Tabs value={filter} onChange={(_, value: string) => setFilter(value)} variant="scrollable" scrollButtons="auto" aria-label="Filter sample lessons" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
                <Tab value="all" label="All lessons" /><Tab value="active" label="To explore" /><Tab value="completed" label="Completed" />
            </Tabs>
            {lessons.length ? <TableContainer tabIndex={0} role="region" aria-label="Sample lesson table" sx={{ overflowX: 'auto' }}>
                <Table size="small" sx={{ minWidth: 620 }}>
                    <TableHead><TableRow><TableCell>Lesson</TableCell><TableCell>Sample progress</TableCell><TableCell>Status</TableCell><TableCell align="right">Presentation</TableCell></TableRow></TableHead>
                    <TableBody>{lessons.map((lesson) => <TableRow key={lesson.title} sx={{ '&:last-child td': { borderBottom: 0 } }}>
                        <TableCell><Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Box sx={{ width: 34, height: 34, flexShrink: 0, display: 'grid', placeItems: 'center', bgcolor: tokens.softBlue, color: 'primary.dark', borderRadius: '8px' }}><PresentationIcon name={lesson.icon} sx={{ fontSize: 18 }} /></Box>
                            <Box><Typography variant="body2" sx={{ fontWeight: 600 }}>{lesson.title}</Typography><Typography variant="caption" color="text.secondary">{lesson.topic}</Typography></Box>
                        </Box></TableCell>
                        <TableCell sx={{ minWidth: 125 }}><Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><LinearProgress variant="determinate" value={lesson.progress} aria-label={`${lesson.title} sample completion`} sx={{ flex: 1 }} /><Typography variant="caption">{lesson.progress}%</Typography></Box></TableCell>
                        <TableCell><Chip label={lesson.state} size="small" sx={{ bgcolor: lesson.state === 'Completed' ? tokens.softGreen : lesson.state === 'In progress' ? tokens.softBlue : tokens.softOrange,
                            color: lesson.state === 'Completed' ? 'success.dark' : lesson.state === 'In progress' ? 'primary.dark' : 'warning.dark' }} /></TableCell>
                        <TableCell align="right"><Button size="small" onClick={() => setDialog('lesson')} aria-label={`Preview ${lesson.title}`}>Preview</Button></TableCell>
                    </TableRow>)}</TableBody>
                </Table>
            </TableContainer> : <Box sx={{ p: 2 }}><EmptyView title="No sample lessons match" message="Try another lesson name or clear the filters."
                action={<Button onClick={() => { setQuery(''); setFilter('all'); }}>Clear filters</Button>} /></Box>}
        </Paper>
    </>;

    const components = <>
        <PageHeader title="Made for the details." subtitle="Reusable presentation, with working local controls."
            actions={<Button key="reset" variant="outlined" onClick={() => setDialog('reset')}>Reset sample controls</Button>} />
        <Chip label="Component preview · Demonstration content" size="small" sx={{ mb: 3, bgcolor: tokens.softBlue, color: 'primary.dark' }} />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' }, gap: 2, mb: 3 }}>
            <Paper sx={{ p: 2.5 }}>
                <Typography variant="h6" component="h2" sx={{ mb: .5 }}>A moment to settle</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>A caller-supplied 400 ms debounce example.</Typography>
                <TextField fullWidth size="small" label="Debounce sample input" value={utilityInput} onChange={(event) => setUtilityInput(event.target.value)} />
                <Typography variant="body2" sx={{ mt: 2, overflowWrap: 'anywhere' }}>Current: {utilityInput || '(empty)'}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Debounced: {debouncedInput || '(empty)'}</Typography>
            </Paper>
            <Paper sx={{ p: 2.5 }}>
                <Typography variant="h6" component="h2" sx={{ mb: .5 }}>A little more context</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Explicit demonstration formatting, chosen by this caller.</Typography>
                <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '1fr auto', gap: 1.5, '& dt': { color: 'text.secondary' }, '& dd': { m: 0, textAlign: 'right', fontWeight: 600 }, typography: 'body2' }}>
                    <dt>Sample date · UTC</dt><dd>{sampleDate}</dd><dt>Sample ratio · en-US</dt><dd>{sampleRatio}</dd>
                    <dt>Duration · 3,725 seconds</dt><dd>{duration.hours}h {duration.minutes}m {duration.seconds}s</dd>
                </Box>
            </Paper>
        </Box>
        <Typography variant="h6" component="h2" sx={{ mb: .5 }}>Thoughtful in every state</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Loading, empty and error examples. No network request is made.</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
            <Paper><LoadingView message="Loading sample content..." /></Paper>
            <EmptyView title="A fresh start" message="Your sample collection is empty." />
            <ErrorView title="A preview hit a snag" message="An illustrative error state." retryLabel="Try preview again" onRetry={() => setNotice('Preview retry callback received.')} />
        </Box>
        <Paper sx={{ p: 2.5, mt: 2, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
            <Box><Typography variant="subtitle1" component="h2">Space to make a choice</Typography><Typography variant="body2" color="text.secondary">A keyboard-accessible confirmation dialog.</Typography></Box>
            <Button variant="contained" onClick={() => setDialog('reset')}>Open confirmation</Button>
        </Paper>
    </>;

    return <>
        <Box component="a" href="#main-content" sx={{ position: 'fixed', top: 8, left: 8, zIndex: 1600, transform: 'translateY(-200%)',
            '&:focus': { transform: 'none' }, bgcolor: 'primary.main', color: 'primary.contrastText', p: 1.5, borderRadius: '8px' }}>Skip to content</Box>
        {selectedNav === 'auth-preview' ? <Box component="main" id="main-content" tabIndex={-1}><AuthLayout title="A calm space to start." subtitle="Authentication layout preview. No sign-in or session is implemented here.">
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>A reusable frame for caller-provided content, with room to focus.</Typography>
            <Button fullWidth variant="contained" onClick={() => setSelectedNav('overview')}>Back to demo workspace</Button>
        </AuthLayout></Box> : <DashboardLayout headerTitle="Learning workspace" headerActions={<SessionNavigation />} navItems={NAV_ITEMS} selectedNavId={selectedNav} onNavSelect={setSelectedNav}>
            {selectedNav === 'components' ? components : overview}
            <Typography role="status" variant="body2" sx={{ mt: notice ? 2 : 0 }}>{notice}</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3 }}>CodeMastery · Independent presentation preview</Typography>
        </DashboardLayout>}
        <ConfirmDialog open={dialog !== null} title={dialog === 'reset' ? 'Reset sample controls?' : 'Sample lesson preview'}
            message={dialog === 'reset' ? 'This clears only the search and debounce demonstration inputs. No learning data is changed.' : 'This is an illustrative lesson card. Course content, lesson delivery and learning progress are not connected in this presentation preview.'}
            confirmLabel={dialog === 'reset' ? 'Reset preview' : 'Done'} cancelLabel={dialog === 'reset' ? 'Keep editing' : 'Close'} isDestructive={dialog === 'reset'}
            onConfirm={dialog === 'reset' ? resetPreview : () => setDialog(null)} onCancel={() => setDialog(null)} />
    </>;
}

export default function App() {
    return <ThemeProvider theme={appTheme}><CssBaseline />
        <BrowserRouter><AuthProvider><AppRouter preview={<PresentationPreview />} /></AuthProvider></BrowserRouter>
    </ThemeProvider>;
}
