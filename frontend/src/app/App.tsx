import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { BrowserRouter } from 'react-router';
import { alpha, darken } from '@mui/material/styles';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter, SessionNavigation } from '../routes/index';
import { ThemeProvider, CssBaseline, Box, Button, TextField, Paper, Typography, Chip, LinearProgress,
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, Tab, InputAdornment, IconButton } from '@mui/material';
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
    { id: 'overview', label: 'Overview', group: 'Learning preview', icon: <PresentationIcon name="grid" /> },
    { id: 'components', label: 'Components', group: 'Presentation lab', badge: 0, icon: <PresentationIcon name="layers" /> },
    { id: 'auth-preview', label: 'Auth layout preview', icon: <PresentationIcon name="book" /> },
];

const weekMinutes = DEMO_WEEK.reduce((total, day) => total + day.minutes, 0);
const weekDuration = formatDurationSeconds(weekMinutes * 60);

function DemoMetric({ label, value, note, icon, accent }: { label: string; value: string; note: string; icon: PresentationIconName; accent: string }) {
    // Shared row tracks align labels, values and captions even when one card wraps.
    return <Paper sx={{ p: 2, minWidth: 0, display: 'grid', gridTemplateRows: 'subgrid', gridRow: 'span 3', rowGap: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box sx={{ width: 40, height: 40, flexShrink: 0, display: 'grid', placeItems: 'center', borderRadius: '10px',
                bgcolor: darken(accent, .04), color: '#FFFFFF', border: `1px solid ${alpha(accent, .25)}`,
                boxShadow: `0 2px 6px ${alpha(accent, .06)}, inset 0 1px 0 rgba(255, 255, 255, .45)` }}>
                <PresentationIcon name={icon} sx={{ fontSize: 23 }} />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>{label}</Typography>
        </Box>
        <Typography variant="h3" component="p" sx={{ fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
        <Typography variant="caption" color="text.secondary">{note}</Typography>
    </Paper>;
}

function LearningRhythm() {
    const [selected, setSelected] = useState<number | null>(3);
    const [hovered, setHovered] = useState<number | null>(null);
    const [tooltipOpen, setTooltipOpen] = useState(true);
    const buttons = useRef<Array<HTMLButtonElement | null>>([]);
    const chart = useRef<HTMLElement>(null);
    useEffect(() => {
        const dismissOutside = (event: PointerEvent) => {
            if (event.target instanceof Node && chart.current && !chart.current.contains(event.target)) {
                setSelected(null); setHovered(null); setTooltipOpen(false);
            }
        };
        document.addEventListener('pointerdown', dismissOutside, true);
        return () => document.removeEventListener('pointerdown', dismissOutside, true);
    }, []);
    const tooltipId = useId();
    const active = hovered ?? selected;
    const maximum = Math.max(...DEMO_WEEK.map((day) => day.minutes));
    return <Paper ref={chart} component="section" aria-labelledby="learning-rhythm-title" sx={{ p: 2.5, minWidth: 0 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}>
            <Box><Typography id="learning-rhythm-title" variant="h6" component="h2">Learning rhythm</Typography>
                <Typography variant="caption" color="text.secondary">A little time, a stronger foundation.</Typography></Box>
            <Chip label="Sample week" size="small" variant="outlined" />
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mt: 1.5 }}>
            <Typography variant="h3" component="p">{weekMinutes}</Typography>
            <Typography variant="body2" color="text.secondary">sample minutes this week</Typography>
        </Box>
        <Box role="group" aria-label="Sample weekly study minutes" aria-describedby={`${tooltipId}-summary`}
            sx={{ display: 'grid', gridTemplateColumns: '28px minmax(0, 1fr)', columnGap: 1, pt: 5 }}>
            <Box aria-hidden="true" sx={{ position: 'relative', height: 168 }}>
                {[60, 45, 30, 15, 0].map((tick) => <Typography key={tick} variant="caption" color="text.secondary"
                    sx={{ position: 'absolute', right: 0, top: `${(1 - tick / maximum) * 100}%`, transform: 'translateY(-50%)', fontVariantNumeric: 'tabular-nums' }}>{tick}</Typography>)}
            </Box>
            <Box sx={{ position: 'relative', minWidth: 0 }}>
                <Box aria-hidden="true" sx={{ position: 'absolute', inset: '0 0 24px', pointerEvents: 'none' }}>
                    {[0, 25, 50, 75, 100].map((position) => <Box key={position} sx={{ position: 'absolute', top: `${position}%`, width: '100%', borderTop: `1px solid ${tokens.chartGrid}` }} />)}
                </Box>
                <Box onMouseLeave={() => setHovered(null)} sx={{ display: 'flex', gap: { xs: .75, sm: 1.5 }, position: 'relative' }}>
                    {DEMO_WEEK.map((day, index) => <Box component="button" key={day.day} type="button"
                        ref={(node: HTMLButtonElement | null) => { buttons.current[index] = node; }}
                        aria-label={`${day.day}: ${day.minutes} sample study minutes`} aria-pressed={selected === index}
                        aria-describedby={tooltipOpen && active === index ? tooltipId : undefined}
                        onClick={() => { setSelected(index); setTooltipOpen(true); }} onFocus={() => { setSelected(index); setHovered(null); setTooltipOpen(true); }}
                        onMouseEnter={() => { setHovered(index); setTooltipOpen(true); }}
                        onKeyDown={(event) => {
                            if (event.key === 'Escape') { event.preventDefault(); setTooltipOpen(false); return; }
                            let next: number;
                            if (event.key === 'ArrowRight') next = (index + 1) % DEMO_WEEK.length;
                            else if (event.key === 'ArrowLeft') next = (index + DEMO_WEEK.length - 1) % DEMO_WEEK.length;
                            else if (event.key === 'Home') next = 0;
                            else if (event.key === 'End') next = DEMO_WEEK.length - 1;
                            else return;
                            event.preventDefault(); buttons.current[next]?.focus();
                        }}
                        sx={{ flex: 1, minWidth: 0, p: 0, m: 0, border: 0, bgcolor: 'transparent', color: 'text.secondary', cursor: 'pointer',
                            borderRadius: '6px', font: 'inherit', '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } }}>
                        <Box sx={{ height: 168, position: 'relative', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}>
                            {tooltipOpen && active === index && <Box id={tooltipId} role="tooltip" sx={{ position: 'absolute', bottom: `calc(${day.minutes / maximum * 100}% + 8px)`,
                                left: index === 0 ? 0 : index === DEMO_WEEK.length - 1 ? 'auto' : '50%', right: index === DEMO_WEEK.length - 1 ? 0 : 'auto',
                                transform: index > 0 && index < DEMO_WEEK.length - 1 ? 'translateX(-50%)' : 'none', zIndex: 1, px: 1.5, py: .5,
                                borderRadius: '8px', border: 1, borderColor: 'divider', bgcolor: 'background.paper', color: 'text.primary',
                                whiteSpace: 'nowrap', typography: 'caption', fontWeight: 500 }}>{day.day} · {day.minutes} min</Box>}
                            <Box data-study-bar aria-hidden="true" sx={{ width: '100%', maxWidth: 36, height: `${day.minutes / maximum * 100}%`,
                                // A contrasting cap preserves the value boundary without a full column outline.
                                borderRadius: '4px 4px 0 0', border: 0, borderTop: '2px solid',
                                borderColor: active === index ? tokens.chartBarActiveEdge : tokens.chartBarEdge,
                                bgcolor: tokens.chartBarFade,
                                backgroundImage: `linear-gradient(180deg, ${active === index ? tokens.chartBarActive : tokens.chartBarFill} 0%, ${tokens.chartBarFade} 100%)` }} />
                        </Box>
                        <Typography component="span" variant="caption" sx={{ height: 24, display: 'grid', placeItems: 'end center', color: selected === index ? 'primary.dark' : 'text.secondary', fontWeight: selected === index ? 600 : 400,
                            textDecoration: selected === index ? 'underline' : 'none', textUnderlineOffset: '3px', textDecorationThickness: '2px' }}>{day.day}</Typography>
                    </Box>)}
                </Box>
            </Box>
        </Box>
        <Typography id={`${tooltipId}-summary`} variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
            Seven illustrative days · 0–{maximum} minutes. Select a day, or use arrow keys to explore.
        </Typography>
    </Paper>;
}

export function PresentationPreview() {
    const [selectedNav, setSelectedNav] = useState('overview');
    const previousNav = useRef(selectedNav);
    useEffect(() => {
        if (previousNav.current === selectedNav) return;
        previousNav.current = selectedNav;
        // Local preview switches can remove the initiating control without changing the URL.
        const heading = document.querySelector<HTMLElement>('#main-content h1');
        if (heading) { heading.tabIndex = -1; heading.focus(); }
    }, [selectedNav]);
    const [query, setQuery] = useState('');
    const [filter, setFilter] = useState('all');
    const lessonContent = useRef<HTMLDivElement>(null);
    const [lessonAreaHeight, setLessonAreaHeight] = useState(0);
    useLayoutEffect(() => {
        const content = lessonContent.current;
        if (!content) return;
        // Reserve the largest rendered result area so fewer rows cannot pull the page upward.
        // Observe real content to accommodate font loading and responsive row wrapping.
        const measure = () => {
            const height = Math.ceil(content.getBoundingClientRect().height);
            setLessonAreaHeight((previous) => Math.max(previous, height));
        };
        measure();
        if (typeof ResizeObserver === 'undefined') return;
        const observer = new ResizeObserver(measure);
        observer.observe(content);
        return () => observer.disconnect();
    }, [selectedNav]);
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
    const sampleSearch = <Box component="form" role="search" aria-label="Search local sample lessons"
        onSubmit={(event) => {
            event.preventDefault();
            const section = document.getElementById('sample-learning-path');
            section?.focus(); section?.scrollIntoView({ block: 'nearest' });
        }}>
        <TextField fullWidth size="small" variant="outlined" hiddenLabel placeholder="Find a sample lesson…" value={query} onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Escape') { event.preventDefault(); setQuery(''); } }}
            slotProps={{ htmlInput: { 'aria-label': 'Search sample lessons', 'aria-description': 'Filters local sample lessons only. Enter jumps to results. Escape clears search.' }, input: {
                sx: { pr: 1.0 },
                endAdornment: <InputAdornment position="end" sx={{ maxHeight: 'none', gap: .5, flexShrink: 0 }}>
                    {query && <IconButton size="small" aria-label="Clear sample lesson search" onClick={() => setQuery('')} sx={{ width: 28, height: 28, fontSize: 20 }}>×</IconButton>}
                    <IconButton type="submit" aria-label="Search sample lessons" disableRipple sx={{ width: 36, height: 36, borderRadius: '50%', bgcolor: 'primary.main', color: 'primary.contrastText',
                        '&:hover, &:active': { bgcolor: 'primary.main' }, '&.Mui-focusVisible': { outline: '2px solid', outlineColor: 'primary.dark', outlineOffset: 2 } }}>
                        <PresentationIcon name="search" sx={{ fontSize: 18 }} />
                    </IconButton>
                </InputAdornment>,
            } }} />
    </Box>;
    function resetPreview() { setQuery(''); setFilter('all'); setUtilityInput(''); setNotice('Sample controls reset.'); setDialog(null); }

    const overview = <>
        <PageHeader title="Your learning, in focus." subtitle="A little practice today. A stronger foundation tomorrow."
            actions={<Button key="explore" variant="contained" endIcon={<PresentationIcon name="arrow" />} onClick={() => setSelectedNav('components')}>Explore components</Button>} />
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, mb: 2, px: 1.5, py: 1, borderRadius: '10px', bgcolor: tokens.softBlue, border: '1px solid #DCE8FC' }}>
            <Chip label="Demo workspace" size="small" sx={{ bgcolor: tokens.softBlue, color: 'primary.dark' }} />
            <Typography variant="caption" color="text.secondary">All learning data below is illustrative. Nothing is saved.</Typography>
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 2, mb: 2 }}>
            <DemoMetric label="Study time" value={`${weekDuration.hours}h ${weekDuration.minutes}m`} note="Illustrative weekly total" icon="clock" accent="#1769FF" />
            <DemoMetric label="Lessons completed" value="12" note="Sample learning snapshot" icon="check" accent="#10A570" />
            <DemoMetric label="Practice streak" value="5 days" note="An example of steady progress" icon="flame" accent="#F56516" />
            <DemoMetric label="Course progress" value="68%" note="Illustrative course completion" icon="chart" accent="#8B5CF6" />
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 3fr) minmax(0, 2fr)' }, gap: 2, mb: 2 }}>
            <LearningRhythm />
            <Paper sx={{ p: 2.5, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                    <Typography variant="h6" component="h2">Keep your curiosity going</Typography>
                    <PresentationIcon name="book" sx={{ fontSize: 20, color: 'text.secondary' }} />
                </Box>
                <Box sx={{ p: 2, borderRadius: '8px', bgcolor: tokens.softBlue, color: 'primary.dark', mb: 1.5, overflow: 'hidden' }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>practice.ts · illustrative code</Typography>
                    <Typography component="pre" sx={{ m: 0, fontFamily: 'ui-monospace, SFMono-Regular, monospace', fontSize: '.8125rem', lineHeight: 1.7, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{"const goal = 'understand';\npractice({ step: 'one at a time' });"}</Typography>
                </Box>
                <Typography variant="h6" component="h3">Programming foundations</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: .5, mb: 1.5 }}>Practice in small steps. Revisit what matters.</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}><Typography variant="caption" color="text.secondary">Demo completion</Typography><Typography variant="caption">68%</Typography></Box>
                <LinearProgress variant="determinate" value={68} aria-label="Sample course completion" />
                <Button variant="outlined" endIcon={<PresentationIcon name="arrow" />} onClick={() => setDialog('lesson')} sx={{ mt: 2, alignSelf: 'flex-start' }}>Preview sample lesson</Button>
            </Paper>
        </Box>
        <Paper component="section" id="sample-learning-path" aria-labelledby="sample-path-title" tabIndex={-1} sx={{ overflow: 'hidden', minWidth: 0, scrollMarginTop: 180 }}>
            <Box sx={{ p: 2.5, display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between', gap: 2 }}>
                <Box><Typography id="sample-path-title" variant="h6" component="h2">Your sample learning path</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>Small steps through programming foundations.</Typography></Box>
                <Typography role="status" variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>{lessons.length} of {DEMO_LESSONS.length} sample lessons</Typography>
            </Box>
            <Tabs value={filter} onChange={(_, value: string) => setFilter(value)} variant="scrollable" scrollButtons="auto" aria-label="Filter sample lessons" sx={{ px: 2, '& .MuiTab-root': { minHeight: 48 }, borderBottom: 1, borderColor: 'divider' }}>
                <Tab value="all" label="All lessons" /><Tab value="active" label="To explore" /><Tab value="completed" label="Completed" />
            </Tabs>
            <Box sx={{ minHeight: lessonAreaHeight, minWidth: 0 }}>
            <Box ref={lessonContent} sx={{ minWidth: 0 }}>
            {lessons.length ? <TableContainer tabIndex={0} role="region" aria-label="Sample lesson table" sx={{ overflowX: 'auto' }}>
                <Table size="small" sx={{ minWidth: 620 }}>
                    <TableHead><TableRow><TableCell>Lesson</TableCell><TableCell>Sample progress</TableCell><TableCell>Status</TableCell><TableCell align="right">Presentation</TableCell></TableRow></TableHead>
                    <TableBody>{lessons.map((lesson) => <TableRow key={lesson.title} sx={{ '&:hover': { bgcolor: tokens.softBlue }, '& td': { py: 1.5 }, '&:last-child td': { borderBottom: 0 } }}>
                        <TableCell><Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <PresentationIcon name={lesson.icon} sx={{ fontSize: 20, color: 'primary.main', flexShrink: 0 }} />
                            <Box><Typography variant="body2" sx={{ fontWeight: 500 }}>{lesson.title}</Typography><Typography variant="caption" color="text.secondary">{lesson.topic}</Typography></Box>
                        </Box></TableCell>
                        <TableCell sx={{ minWidth: 125 }}><Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><LinearProgress variant="determinate" value={lesson.progress} aria-label={`${lesson.title} sample completion`} sx={{ flex: 1 }} /><Typography variant="caption">{lesson.progress}%</Typography></Box></TableCell>
                        <TableCell><Chip label={lesson.state} size="small" sx={{ bgcolor: lesson.state === 'Completed' ? tokens.softGreen : lesson.state === 'In progress' ? tokens.softBlue : tokens.softOrange,
                            color: lesson.state === 'Completed' ? 'success.dark' : lesson.state === 'In progress' ? 'primary.dark' : 'warning.dark' }} /></TableCell>
                        <TableCell align="right"><Button size="small" onClick={() => setDialog('lesson')} aria-label={`Preview ${lesson.title}`}>Preview</Button></TableCell>
                    </TableRow>)}</TableBody>
                </Table>
            </TableContainer> : <Box sx={{ p: 2 }}><EmptyView title="No sample lessons match" message="Try another lesson name or clear the filters."
                action={<Button onClick={() => { setQuery(''); setFilter('all'); }}>Clear filters</Button>} /></Box>}
            </Box>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, p: 2, borderTop: 1, borderColor: 'divider' }}>
                <PresentationIcon name="spark" sx={{ color: 'primary.dark', fontSize: 21, mt: .25, flexShrink: 0 }} />
                <Box><Typography variant="subtitle2" sx={{ color: 'primary.dark' }}>Sample recommendation · strengthen the foundation</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>Revisit variables before exploring loops: in this static example, a prerequisite skill needs reinforcement.</Typography>
                    <Typography variant="caption" color="text.secondary">Illustration only. No live mastery calculation is performed.</Typography></Box>
            </Box>
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

    // Simplify motion only in the local demo; real authentication keeps the shared theme.
    return <ThemeProvider theme={{ motion: { reducedMotion: 'always' } }}>
        <Box component="a" href="#main-content" sx={{ position: 'fixed', top: 8, left: 8, zIndex: 1600, transform: 'translateY(-200%)',
            '&:focus': { transform: 'none' }, bgcolor: 'primary.main', color: 'primary.contrastText', p: 1.5, borderRadius: '8px' }}>Skip to content</Box>
        {selectedNav === 'auth-preview' ? <Box component="main" id="main-content" tabIndex={-1}><AuthLayout title="A calm space to start." subtitle="Authentication layout preview. No sign-in or session is implemented here.">
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>A reusable frame for caller-provided content, with room to focus.</Typography>
            <Button fullWidth variant="contained" onClick={() => setSelectedNav('overview')}>Back to demo workspace</Button>
        </AuthLayout></Box> : <DashboardLayout headerTitle={selectedNav === 'components' ? 'Component preview' : 'Learning workspace'} headerActions={<SessionNavigation />} headerSearch={selectedNav === 'overview' ? sampleSearch : undefined} navItems={NAV_ITEMS} selectedNavId={selectedNav} onNavSelect={setSelectedNav}>
            {selectedNav === 'components' ? components : overview}
            <Typography role="status" variant="body2" sx={{ mt: notice ? 2 : 0 }}>{notice}</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3 }}>CodeMastery · Independent presentation preview</Typography>
        </DashboardLayout>}
        <ConfirmDialog open={dialog !== null} title={dialog === 'reset' ? 'Reset sample controls?' : 'Sample lesson preview'}
            message={dialog === 'reset' ? 'This clears only the search and debounce demonstration inputs. No learning data is changed.' : 'This is an illustrative lesson card. Course content, lesson delivery and learning progress are not connected in this presentation preview.'}
            confirmLabel={dialog === 'reset' ? 'Reset preview' : 'Done'} cancelLabel={dialog === 'reset' ? 'Keep editing' : 'Close'} isDestructive={dialog === 'reset'}
            onConfirm={dialog === 'reset' ? resetPreview : () => setDialog(null)} onCancel={() => setDialog(null)} />
    </ThemeProvider>;
}

export default function App() {
    return <ThemeProvider theme={appTheme}><CssBaseline />
        <BrowserRouter><AuthProvider><AppRouter preview={<PresentationPreview />} /></AuthProvider></BrowserRouter>
    </ThemeProvider>;
}
