import { useMemo, useRef } from 'react';
import type { ReactNode } from 'react';
import { Box, ButtonBase, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ViewRail } from '../../../shared/components/ViewRail';
import { DataTable } from '../../../shared/components/DataTable';
import { EmptyView } from '../../../shared/components/StatusViews';
import { MasteryLegend, masteryMeta, masteryOrder } from '../../../shared/components/StatusPill';
import { GuidanceEntry } from '../../../shared/components/GuidanceStack';
import { MasteryGraph } from '../components/MasteryGraph';
import type { MasteryGraphHandle } from '../components/MasteryGraph';
import { MasteryStateChip } from '../components/MasteryStateChip';
import { SkillDetailPanel } from '../components/SkillDetailPanel';
import { recommendationEntry } from '../components/NextRecommendedCard';
import { masteryGraphLayout, orderedSkillIds } from '../utils/masteryGraphLayout';
import type { RecommendationView, SkillView } from '../types/masteryTypes';
import { color, pathLine, radius } from '../../../app/theme/theme';

export type MasteryView = 'graph' | 'list';
export interface MasteryMapPageProps {
    courseTitle: string;
    skills: SkillView[];
    view: MasteryView;
    onViewChange: (view: MasteryView) => void;
    selectedId: string | null;
    onSelect: (id: string | null) => void;
    recommendation?: RecommendationView;
    updatedIds?: string[];
    onOpenLesson?: (lessonId: string) => void;
    /** Course section switch (Path / Mastery Map / Assessments). */
    sectionNav?: ReactNode;
    back?: { label: string; onClick: () => void };
    /** Shown above the map when the learner has no mastery data yet (diagnostic offer). */
    notice?: ReactNode;
    /** Read-only variant used inside Instructor learner detail. */
    embedded?: boolean;
    title?: string;
}

/** S14 Mastery Map: four states, prerequisite edges, list equivalent, legend and skill detail (US-07). */
export function MasteryMapPage({ courseTitle, skills, view, onViewChange, selectedId, onSelect, recommendation, updatedIds, onOpenLesson, sectionNav, back, notice, embedded, title }: MasteryMapPageProps) {
    const graph = useRef<MasteryGraphHandle>(null);
    const byId = useMemo(() => new Map(skills.map((skill) => [skill.id, skill])), [skills]);
    const ordered = useMemo(() => orderedSkillIds(masteryGraphLayout(skills)).map((id) => byId.get(id)!), [skills, byId]);
    const selected = selectedId ? byId.get(selectedId) ?? null : null;
    const counts = masteryOrder.map((state) => ({ state, count: skills.filter((skill) => skill.state === state).length }));
    const body = skills.length === 0
        ? <EmptyView icon="map" title="This course doesn't have a skill map yet" message="The instructor hasn't published skills for this course. Lessons are still available in course order." />
        : <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: '52px minmax(0, 1fr)' }, alignItems: 'start' }}>
            <ViewRail label="Map view" value={view} onChange={onViewChange}
                options={[{ value: 'graph', label: 'Graph view', icon: 'graph' }, { value: 'list', label: 'List view', icon: 'list' }]}
                tools={view === 'graph' ? [{ label: 'Zoom in', icon: 'zoom-in', onClick: () => graph.current?.zoomIn() }, { label: 'Zoom out', icon: 'zoom-out', onClick: () => graph.current?.zoomOut() }, { label: 'Fit map to screen', icon: 'fit', onClick: () => graph.current?.fit() }] : []} />
            <Box sx={{ minWidth: 0 }}>
                {view === 'graph'
                    ? <MasteryGraph ref={graph} label={`Skill map for ${courseTitle}`} skills={skills} selectedId={selectedId} onSelect={onSelect} recommendedSkillId={recommendation?.skillId} updatedIds={updatedIds} />
                    : <DataTable label={`Skills in ${courseTitle}`} rows={ordered} getRowId={(skill) => skill.id} selectedId={selectedId ?? undefined}
                        columns={[
                            { key: 'skill', header: 'Skill', primary: true, render: (skill) => <ButtonBase onClick={() => onSelect(skill.id)} aria-pressed={skill.id === selectedId}
                                sx={{ fontWeight: 600, fontSize: '0.9375rem', textAlign: 'left', borderRadius: `${radius.xs}px`, color: color.ink900, minHeight: 36, '&:hover': { textDecoration: 'underline' } }}>{skill.name}</ButtonBase> },
                            { key: 'state', header: 'State', render: (skill) => <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}><MasteryStateChip state={skill.state} />{updatedIds?.includes(skill.id) && <Typography component="span" variant="caption" sx={{ color: 'text.secondary', alignSelf: 'center' }}>Updated</Typography>}</Box> },
                            { key: 'prerequisites', header: 'Prerequisites', render: (skill) => skill.prerequisiteIds.length === 0 ? <Typography variant="body2" sx={{ color: 'text.secondary' }}>None</Typography>
                                : skill.prerequisiteIds.map((id) => byId.get(id)).filter(Boolean).map((p) => `${p!.name} (${masteryMeta[p!.state].label})`).join(', ') },
                            { key: 'lessons', header: 'Related lessons', render: (skill) => skill.relatedLessons.length === 0 ? '—' : skill.relatedLessons.map((lesson) => lesson.title).join(', ') },
                            { key: 'score', header: 'Latest score', align: 'right', render: (skill) => <span className="cm-tabular">{skill.latestScore === undefined || skill.latestScore === null ? '—' : `${skill.latestScore}%`}</span> },
                        ]} />}
                <Box sx={{ mt: 2.5, display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', justifyContent: 'space-between' }}>
                    <MasteryLegend dense={embedded} />
                    <Typography variant="caption" sx={{ color: 'text.secondary', display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Box component="svg" aria-hidden viewBox="0 0 40 10" sx={{ width: 40, height: 10 }}><path d="M2 5h30" stroke={color.ink400} strokeWidth="2" strokeDasharray={pathLine.dasharray} /><path d="m30 1 6 4-6 4Z" fill={color.ink400} /></Box>
                        Arrow: from a prerequisite to the skill that builds on it
                    </Typography>
                </Box>
            </Box>
        </Box>;
    const aside = <Box sx={{ display: 'grid', gap: 3 }}>
        {recommendation && <Box><Typography component="h2" variant="h2" sx={{ mb: 2 }}>Next recommended</Typography><GuidanceEntry component="div" entry={recommendationEntry(recommendation, onOpenLesson)} /></Box>}
        <SkillDetailPanel skill={selected} skillsById={byId} onOpenLesson={onOpenLesson} onSelectSkill={(id) => onSelect(id)} />
        <Box component="section" aria-labelledby="mastery-summary">
            <Typography id="mastery-summary" component="h2" variant="h4" sx={{ mb: 1.5 }}>Skills by state</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
                {counts.map(({ state, count }) => <Box component="li" key={state} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <MasteryStateChip state={state} /><Typography className="cm-tabular" sx={{ fontWeight: 600 }}>{count}</Typography>
                </Box>)}
            </Box>
        </Box>
    </Box>;
    if (embedded) return <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', xl: 'minmax(0, 1fr) 340px' } }}><Box sx={{ minWidth: 0 }}>{body}</Box>{skills.length > 0 && aside}</Box>;
    return <Page aside={skills.length > 0 ? aside : undefined} asideLabel="Skill details">
        <PageHeader title={title ?? 'Mastery Map'} subtitle={`${courseTitle} · Your skills, their prerequisites and what to study next.`} back={back} />
        {sectionNav && <Box sx={{ mb: 4 }}>{sectionNav}</Box>}
        {notice}
        {body}
    </Page>;
}
