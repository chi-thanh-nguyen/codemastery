import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { ReactFlow, ReactFlowProvider, Handle, Position, MarkerType, useReactFlow } from '@xyflow/react';
import type { Edge, Node, NodeProps } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Box, ButtonBase, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { masteryMeta } from '../../../shared/components/StatusPill';
import type { MasteryState } from '../../../shared/components/StatusPill';
import { graphExtent, initialGraphViewport, masteryGraphLayout, orderedSkillIds, NODE_HEIGHT, NODE_WIDTH } from '../utils/masteryGraphLayout';
import { color, radius, shadow, tones } from '../../../app/theme/theme';

export interface GraphSkill { id: string; name: string; state: MasteryState; prerequisiteIds: string[] }

export interface MasteryGraphHandle { zoomIn: () => void; zoomOut: () => void; fit: () => void }
export interface MasteryGraphProps {
    skills: GraphSkill[];
    selectedId: string | null;
    onSelect: (id: string | null) => void;
    /** Skill targeted by the current recommendation (lilac corner badge). */
    recommendedSkillId?: string;
    /** Skills changed by the latest result (temporary "Updated" ring and tag). */
    updatedIds?: string[];
    label: string;
    /** Canvas height; defaults to the graph's own height at the reading zoom (capped). */
    height?: number | string;
    /** Smallest initial zoom. The map fits when it stays readable; otherwise it starts at the beginning of the path. */
    zoom?: number;
    /** Edit mode (Instructor graph editor): same canvas, no learner state. */
    hideStates?: boolean;
}

type SkillNodeData = { skill: GraphSkill; selected: boolean; recommended: boolean; updated: boolean; hideStates: boolean; onSelect: (id: string) => void; prerequisiteCount: number };

const stateTone: Record<MasteryState, { bg: string; fg: string; dashed?: boolean }> = {
    unknown: { bg: color.surface, fg: color.ink600, dashed: true },
    learning: { bg: tones.sky.tint, fg: color.brand600 },
    mastered: { bg: tones.mint.tint, fg: color.mint700 },
    weak: { bg: tones.butter.tint, fg: color.butter800 },
};

const SkillNode = memo(function SkillNode({ data }: NodeProps<Node<SkillNodeData>>) {
    const { skill, selected, recommended, updated, hideStates, onSelect, prerequisiteCount } = data;
    const meta = masteryMeta[skill.state], tone = stateTone[skill.state];
    const name = hideStates ? `${skill.name}, ${prerequisiteCount} ${prerequisiteCount === 1 ? 'prerequisite' : 'prerequisites'}`
        : `${skill.name}, ${meta.label}, ${prerequisiteCount} ${prerequisiteCount === 1 ? 'prerequisite' : 'prerequisites'}${recommended ? ', recommended next' : ''}${updated ? ', updated' : ''}`;
    return <>
        <Handle type="target" position={Position.Left} isConnectable={false} style={{ opacity: 0, left: 0 }} />
        <ButtonBase onClick={() => onSelect(skill.id)} aria-pressed={selected} aria-label={name} className="nodrag"
            sx={{ position: 'relative', width: NODE_WIDTH, height: NODE_HEIGHT, borderRadius: `${radius.lg}px`, bgcolor: color.surface, justifyContent: 'flex-start', gap: 1.5, px: 1.5, textAlign: 'left',
                boxShadow: selected ? `0 0 0 2px ${color.brand600}, ${shadow.raised}` : updated ? `0 0 0 2px ${skill.state === 'mastered' ? color.mint700 : color.butter800}` : '0 1px 2px rgba(10,11,15,.06)',
                transition: 'box-shadow 200ms', '&:hover': { boxShadow: selected ? `0 0 0 2px ${color.brand600}, ${shadow.raised}` : shadow.hover },
                '&.Mui-focusVisible': { outline: `2px solid ${color.brand600}`, outlineOffset: 3 } }}>
            {!hideStates && <Box aria-hidden sx={{ width: 40, height: 40, borderRadius: '50%', flexShrink: 0, display: 'grid', placeItems: 'center', bgcolor: tone.bg, color: tone.fg,
                border: tone.dashed ? `1.5px dashed ${color.ink400}` : 0, '& svg': { fontSize: 20 } }}><PresentationIcon name={meta.icon} /></Box>}
            <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography component="span" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', fontWeight: 600, fontSize: '0.9375rem', lineHeight: 1.25, color: color.ink900 }}>{skill.name}</Typography>
                <Typography component="span" sx={{ display: 'block', fontSize: '0.8125rem', fontWeight: 500, color: color.ink600, mt: .25 }}>
                    {hideStates ? `${prerequisiteCount} ${prerequisiteCount === 1 ? 'prerequisite' : 'prerequisites'}` : updated ? `${meta.label} · Updated` : meta.label}
                </Typography>
            </Box>
            {recommended && <Box aria-hidden sx={{ position: 'absolute', top: -10, right: -10, width: 28, height: 28, borderRadius: '50%', bgcolor: color.lilac100, color: color.lilac700,
                border: `3px solid ${color.surface}`, display: 'grid', placeItems: 'center', '& svg': { fontSize: 14 } }}><PresentationIcon name="route" /></Box>}
        </ButtonBase>
        <Handle type="source" position={Position.Right} isConnectable={false} style={{ opacity: 0, right: 0 }} />
    </>;
});
const nodeTypes = { skill: SkillNode };

const Canvas = forwardRef<MasteryGraphHandle, MasteryGraphProps>(function Canvas({ skills, selectedId, onSelect, recommendedSkillId, updatedIds = [], label, height, zoom = 0.85, hideStates = false }, ref) {
    const flow = useReactFlow();
    useImperativeHandle(ref, () => ({ zoomIn: () => void flow.zoomIn({ duration: 200 }), zoomOut: () => void flow.zoomOut({ duration: 200 }), fit: () => void flow.fitView({ padding: 0.12, duration: 200 }) }), [flow]);
    const select = useCallback((id: string) => onSelect(id === selectedId ? null : id), [onSelect, selectedId]);
    const { nodes, edges, rows, extent: layoutExtent, positions: layoutPositions } = useMemo(() => {
        const positions = masteryGraphLayout(skills);
        const rows = Math.max(1, ...[...positions.values()].map((position) => position.order + 1));
        const order = orderedSkillIds(positions);
        const byId = new Map(skills.map((skill) => [skill.id, skill]));
        const nodes: Node<SkillNodeData>[] = order.map((id) => {
            const skill = byId.get(id)!, position = positions.get(id)!;
            return { id, type: 'skill', position: { x: position.x, y: position.y }, draggable: false, selectable: false, focusable: false,
                data: { skill, selected: id === selectedId, recommended: id === recommendedSkillId, updated: updatedIds.includes(id), hideStates, onSelect: select, prerequisiteCount: skill.prerequisiteIds.filter((p) => byId.has(p)).length } };
        });
        const edges: Edge[] = skills.flatMap((skill) => skill.prerequisiteIds.filter((p) => byId.has(p)).map((prerequisite) => {
            const active = selectedId !== null && (prerequisite === selectedId || skill.id === selectedId);
            return { id: `${prerequisite}->${skill.id}`, source: prerequisite, target: skill.id, type: 'smoothstep', focusable: false, selectable: false,
                style: { stroke: active ? color.brand600 : color.ink400, strokeWidth: 2, strokeDasharray: active ? undefined : '5 5' },
                markerEnd: { type: MarkerType.ArrowClosed, color: active ? color.brand600 : color.ink400, width: 16, height: 16 } };
        }));
        return { nodes, edges, rows, extent: graphExtent(positions), positions };
    }, [skills, selectedId, recommendedSkillId, updatedIds, hideStates, select]);
    const extent = layoutExtent;
    const autoHeight = Math.min(560, Math.max(280, Math.round(rows * (NODE_HEIGHT + 28) * zoom + 96)));
    // Fit once the canvas has a measured width; later resizes and the rail controls never fight the user's pan and zoom.
    const container = useRef<HTMLDivElement>(null), placed = useRef(false), [ready, setReady] = useState(false);
    useEffect(() => {
        const element = container.current;
        if (!element || placed.current) return;
        const place = () => {
            const { width, height: canvasHeight } = element.getBoundingClientRect();
            if (width === 0 || placed.current) return;
            placed.current = true;
            const focus = selectedId ? layoutPositions.get(selectedId) : undefined;
            void flow.setViewport(initialGraphViewport(extent, { width, height: canvasHeight }, { minZoom: zoom, focus }));
            setReady(true);
        };
        place();
        if (placed.current || typeof ResizeObserver === 'undefined') return;
        const observer = new ResizeObserver(place);
        observer.observe(element);
        return () => observer.disconnect();
    }, [flow, extent, zoom, selectedId, layoutPositions]);
    return <Box ref={container} role="group" aria-label={label} data-ready={ready || undefined} sx={{ height: height ?? autoHeight, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden', position: 'relative',
        '& .react-flow__node': { cursor: 'default' }, '& .react-flow__attribution': { display: 'none' } }}
        onKeyDown={(event) => { if (event.key === 'Escape' && selectedId) { event.stopPropagation(); onSelect(null); } }}>
        <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} defaultViewport={{ x: 32, y: 32, zoom }} minZoom={0.4} maxZoom={1.6}
            nodesDraggable={false} nodesConnectable={false} elementsSelectable={false} nodesFocusable={false} edgesFocusable={false}
            zoomOnScroll={false} panOnScroll={false} preventScrolling={false} zoomOnDoubleClick={false}
            onPaneClick={() => onSelect(null)} proOptions={{ hideAttribution: true }} />
    </Box>;
});

/** Read-only course skill graph (React Flow). Always paired with the list view and legend. */
export const MasteryGraph = forwardRef<MasteryGraphHandle, MasteryGraphProps>(function MasteryGraph(props, ref) {
    return <ReactFlowProvider><Canvas ref={ref} {...props} /></ReactFlowProvider>;
});
