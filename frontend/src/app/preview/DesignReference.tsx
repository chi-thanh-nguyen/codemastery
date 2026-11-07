import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, IconButton, MenuItem, TextField, Typography } from '@mui/material';
import { Page } from '../../layouts/DashboardLayout';
import { PageHeader, SearchPill } from '../../shared/components/PageHeader';
import { StatusPill, MasteryLegend, RecommendationTag } from '../../shared/components/StatusPill';
import { CircleAction, CourseTile, ProgressLine, StatGroup, StatTile, Tile, SectionHeading } from '../../shared/components/Tile';
import { PathSpine } from '../../shared/components/PathSpine';
import { CourseMark } from '../../shared/components/CourseMark';
import { GuidanceStack } from '../../shared/components/GuidanceStack';
import { SegmentedControl } from '../../shared/components/ViewRail';
import { DataTable, PaginationBar } from '../../shared/components/DataTable';
import { EmptyView, ErrorView, LoadingView } from '../../shared/components/StatusViews';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { CodeBlock } from '../../shared/components/CodeBlock';
import { PresentationIcon } from '../../shared/components/PresentationIcon';
import { color, radius, tones } from '../theme/theme';
import { usePreview } from './previewKit';

export interface DirectoryEntry { id: string; title: string; spec: string; status: 'Live' | 'Preview' | 'Proposed'; note: string; href?: string; onOpen?: () => void }
export interface DirectoryGroup { title: string; description: string; entries: DirectoryEntry[] }

// Lilac is reserved for adaptive guidance; a proposal is "not yet", so it takes the dashed outline.
const statusTone = { Live: 'mint', Preview: 'sky', Proposed: 'dashed' } as const;
const swatches: { name: string; value: string; role: string; dark?: boolean }[] = [
    { name: 'Ink', value: color.ink950, role: 'Frame, primary buttons', dark: true }, { name: 'Canvas', value: color.canvas100, role: 'Working ground' }, { name: 'Panel fill', value: color.fill150, role: 'Aside, secondary controls' },
    { name: 'Surface', value: color.surface, role: 'Tiles, tables, forms' }, { name: 'Logo Blue', value: color.brand600, role: 'Links, focus, selection, progress', dark: true }, { name: 'Text', value: color.ink900, role: 'Primary text', dark: true },
    { name: 'Text secondary', value: color.ink600, role: 'Descriptions, metadata', dark: true }, { name: 'Strong line', value: color.ink400, role: 'Input borders, dashed "not yet"', dark: true },
];

/** S26 Living design reference + directory of every screen (DESIGN.md › S26). */
export function DesignReference({ groups }: { groups: DirectoryGroup[] }) {
    const { notify } = usePreview();
    const [segment, setSegment] = useState<'one' | 'two' | 'three'>('one'), [dialog, setDialog] = useState(false), [query, setQuery] = useState('');
    return <Page>
        <PageHeader title="CodeMastery design preview" subtitle="The Lit Path design system and every screen in the DESIGN.md specification, with sample data. Live screens use the real API outside this preview." />
        <Section title="Screens" id="ref-screens">
            <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'repeat(2, minmax(0, 1fr))' } }}>
                {groups.map((group) => <Box key={group.title} component="section" aria-labelledby={`dir-${group.title}`} sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                    <Box sx={{ px: 3, pt: 3, pb: 1.5 }}><Typography id={`dir-${group.title}`} variant="h3" component="h3">{group.title}</Typography><Typography variant="body2" sx={{ color: 'text.secondary', mt: .5 }}>{group.description}</Typography></Box>
                    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                        {group.entries.map((entry) => <Box component="li" key={entry.id} sx={{ borderTop: `1px solid ${color.line200}` }}>
                            <Box component="a" href={entry.href ?? '#'} onClick={(event) => { if (entry.onOpen) { event.preventDefault(); entry.onOpen(); } }}
                                sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 3, py: 1.5, color: 'inherit', textDecoration: 'none', minHeight: 56, '&:hover': { bgcolor: color.canvas100 } }}>
                                <Typography component="span" className="cm-tabular" sx={{ width: 34, color: 'text.secondary', fontWeight: 600, fontSize: '0.8125rem' }}>{entry.spec}</Typography>
                                <Box sx={{ flex: 1, minWidth: 0 }}><Typography component="span" sx={{ display: 'block', fontWeight: 600 }}>{entry.title}</Typography>
                                    <Typography component="span" variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{entry.note}</Typography></Box>
                                <StatusPill size="tag" tone={statusTone[entry.status]}>{entry.status}</StatusPill>
                                <PresentationIcon name="chevron-right" sx={{ fontSize: 18, color: 'text.secondary' }} />
                            </Box>
                        </Box>)}
                    </Box>
                </Box>)}
            </Box>
        </Section>
        <Section title="Colour" id="ref-colour">
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0,1fr))', md: 'repeat(4, minmax(0,1fr))' } }}>
                {swatches.map((swatch) => <Box component="li" key={swatch.name}><Box sx={{ height: 72, borderRadius: `${radius.md}px`, bgcolor: swatch.value, border: swatch.value === color.surface || swatch.value === color.canvas100 ? `1px solid ${color.line200}` : 0 }} />
                    <Typography sx={{ mt: 1, fontWeight: 600 }}>{swatch.name}</Typography><Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>{swatch.value} · {swatch.role}</Typography></Box>)}
            </Box>
            <Typography variant="h4" component="h3" sx={{ mt: 4, mb: 2 }}>Semantic families</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(5, minmax(0,1fr))' } }}>
                {([['sky', 'Learning · information'], ['mint', 'Mastered · completed · success'], ['butter', 'Weak · prerequisite gap · deadline'], ['lilac', 'Recommendations · guidance'], ['rose', 'Errors · destructive']] as const).map(([tone, job]) =>
                    <Box component="li" key={tone} sx={{ bgcolor: tones[tone].tint, borderRadius: `${radius.lg}px`, p: 2 }}>
                        <Box sx={{ bgcolor: tones[tone].soft, borderRadius: `${radius.md}px`, p: 1.5 }}><Typography sx={{ fontWeight: 600, textTransform: 'capitalize' }}>{tone}</Typography><Typography variant="body2">{job}</Typography></Box>
                    </Box>)}
            </Box>
        </Section>
        <Section title="Type" id="ref-type">
            <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2 }}>
                <Typography variant="h1" component="p">Page title 40</Typography><Typography variant="h2" component="p">Section title 28</Typography><Typography variant="h3" component="p">Item title 24</Typography>
                <Typography variant="h4" component="p">Title 18</Typography><Typography variant="reading">Body reading 17 — lesson text keeps a comfortable 70 character measure so long explanations stay easy to follow.</Typography>
                <Typography>Body 16 — descriptions and form text.</Typography><Typography variant="caption" sx={{ color: 'text.secondary' }}>Caption 13 — timestamps and helper text</Typography>
                <Typography variant="stat" component="p">66.67%</Typography>
            </Tile>
        </Section>
        <Section title="Actions and inputs" id="ref-actions">
            <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 3 }}>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
                    <Button variant="contained">Primary action</Button><Button variant="outlined">Secondary</Button><Button variant="text">Text link action</Button>
                    <Button variant="contained" color="error">Delete</Button><Button variant="contained" disabled>Disabled</Button>
                    <CircleAction label="Open" icon="arrow-right" variant="ink" onClick={() => notify('Circle action (sample).')} /><CircleAction label="Edit" icon="edit" onClick={() => notify('Fill circle (sample).')} />
                    <IconButton aria-label="More actions"><PresentationIcon name="more" /></IconButton>
                </Box>
                <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, minmax(0, 1fr))' } }}>
                    <TextField label="Text field" placeholder="Type here" helperText="Helper text explains the rule." />
                    <TextField label="With an error" defaultValue="x" error helperText="Enter at least 2 characters." />
                    <TextField select label="Select" defaultValue="b"><MenuItem value="a">Option A</MenuItem><MenuItem value="b">Option B</MenuItem></TextField>
                </Box>
                <Box sx={{ maxWidth: 420 }}><SearchPill label="Search sample" value={query} onChange={setQuery} onClear={() => setQuery('')} /></Box>
                <SegmentedControl label="Segmented control" value={segment} onChange={setSegment} options={[{ value: 'one', label: 'Joined', count: 3 }, { value: 'two', label: 'Completed', count: 1 }, { value: 'three', label: 'All' }]} />
                <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}><Button variant="outlined" onClick={() => setDialog(true)}>Open a confirmation</Button><Button variant="outlined" onClick={() => notify('A toast confirms background work.')}>Show a toast</Button></Box>
            </Tile>
        </Section>
        <Section title="States and tags" id="ref-states">
            <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2.5 }}>
                <MasteryLegend />
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}><RecommendationTag type="continue" /><RecommendationTag type="skip" /><RecommendationTag type="remedial" />
                    <StatusPill tone="mint" icon="check-circle">Completed</StatusPill><StatusPill tone="dashed" icon="dashed-circle">Not started</StatusPill><StatusPill tone="butter" icon="alert-triangle">Prerequisite gap</StatusPill><StatusPill tone="rose" icon="lock">Locked</StatusPill></Box>
                <Box sx={{ display: 'grid', gap: 1.5 }}><Alert severity="info">Information alert.</Alert><Alert severity="success">Success alert.</Alert><Alert severity="warning">You haven't mastered Loops yet. This lesson builds on it.</Alert><Alert severity="error">We couldn't reach CodeMastery. Check your connection and try again.</Alert></Box>
                <ProgressLine completed={4} total={12} percent={33} />
                <StatGroup label="Sample counts"><StatTile value={12} label="Lessons" tone="sky" /><StatTile value={4} label="Completed" tone="mint" /><StatTile value={8} label="Remaining" tone="dashed" /></StatGroup>
            </Tile>
        </Section>
        <Section title="Composition" id="ref-composition">
            <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1fr) 360px' }, alignItems: 'start' }}>
                <Box sx={{ display: 'grid', gap: 3 }}>
                    <CourseTile title="Introduction to Programming" description="Demo course for the Interim catalogue." byline="Instructor · Instructor Demo" href="#" onOpen={() => notify('Course tile (sample).')} headingLevel="h3" />
                    <Tile padding={{ xs: 3, sm: 4 }} sx={{ display: 'grid', gap: 2 }}>
                        <Typography variant="h4" component="h3">Course marks</Typography>
                        <Typography variant="body2" sx={{ color: 'text.secondary', maxWidth: '60ch' }}>The official logo of the language a course names in its title or description, in one neutral container and sized by eye. Courses that name no single language show one Deep Logo Blue code glyph in the same container.</Typography>
                        <Box component="ul" aria-label="Course mark samples" sx={{ m: 0, p: 0, listStyle: 'none', display: 'flex', flexWrap: 'wrap', gap: { xs: 2.5, sm: 4 } }}>
                            {[{ title: 'Python Fundamentals', caption: 'Python' }, { title: 'TypeScript Basics', caption: 'TypeScript' }, { title: 'Introduction to Programming', caption: 'No language named' }].map((sample) =>
                                <Box component="li" key={sample.title} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                                    <CourseMark title={sample.title} /><CourseMark title={sample.title} size={32} />
                                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>{sample.caption}</Typography>
                                </Box>)}
                        </Box>
                    </Tile>
                    <PathSpine sections={[{ id: 's', title: 'Path Spine', items: [
                        { id: 'a', node: 'done', content: <Tile><Typography variant="h4" component="p">Completed lesson</Typography></Tile> },
                        { id: 'b', node: 'current', content: <Tile><Typography variant="h4" component="p">Current lesson</Typography></Tile> },
                        { id: 'c', node: 'recommended', content: <Tile><Typography variant="h4" component="p">Recommended review</Typography></Tile> },
                        { id: 'd', node: 'todo', content: <Tile><Typography variant="h4" component="p">Not started</Typography></Tile> }] }]} />
                    <CodeBlock code={'for i in range(3):\n    print(i)'} caption="Code block" />
                </Box>
                <Box>
                    <GuidanceStack title="Guidance Stack" headingLevel="h3" entries={[{ id: 'g1', tone: 'lilac', icon: 'route', type: 'Recommended · Remedial', meta: 'Thu 5 Nov', body: <Typography>Review If Statements before continuing.</Typography>, action: { label: 'Open lesson', onClick: () => notify('Guidance action (sample).') } }]} />
                </Box>
            </Box>
        </Section>
        <Section title="Tables and feedback" id="ref-tables">
            <DataTable label="Sample table" rows={[{ id: '1', name: 'Linh Pham', role: 'Learner' }, { id: '2', name: 'Instructor Demo', role: 'Instructor' }]} getRowId={(row) => row.id}
                columns={[{ key: 'name', header: 'Name', primary: true, render: (row) => row.name }, { key: 'role', header: 'Role', render: (row) => row.role }]}
                footer={<PaginationBar page={0} totalPages={1} totalItems={2} pageSize={20} itemsOnPage={2} onChange={() => undefined} noun="people" />} />
            <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'repeat(3, minmax(0, 1fr))' } }}>
                <LoadingView message="Loading sample rows" variant="rows" count={2} />
                <EmptyView headingLevel="h3" title="Empty state" message="Says what this place is for and the next step." action={<Button variant="outlined">Next step</Button>} />
                <ErrorView headingLevel="h3" title="Error state" message="Names the problem and the recovery." onRetry={() => notify('Retry (sample).')} />
            </Box>
        </Section>
        <ConfirmDialog open={dialog} title="Leave course?" message="Your learning history will be preserved. You can re-enroll while the course is published." confirmLabel="Leave course" isDestructive onCancel={() => setDialog(false)} onConfirm={() => setDialog(false)} />
    </Page>;
}

function Section({ title, id, children }: { title: string; id: string; children: ReactNode }) {
    return <Box component="section" aria-labelledby={id} sx={{ mb: 6 }}><SectionHeading id={id}>{title}</SectionHeading>{children}</Box>;
}
