import { useMemo } from 'react';
import { ReactFlow, ReactFlowProvider, Handle, Position, MarkerType } from '@xyflow/react';
import type { Edge, Node, NodeProps } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Box, ButtonBase, Typography } from '@mui/material';
import type { EditableSkill } from '../types/skillGraphTypes';
import { color, pathLine, radius, shadow } from '../../../../app/theme/theme';

const W = 184, H = 64, GAP_X = 56, GAP_Y = 28;
type Data = { skill: EditableSkill; selected: boolean; onSelect: (id: string) => void };

/** Layered positions by longest prerequisite chain (presentation only; tolerates malformed input). */
function layout(skills: EditableSkill[]) {
    const byId = new Map(skills.map((skill) => [skill.id, skill])), depth = new Map<string, number>(), seen = new Set<string>();
    const layer = (id: string): number => {
        if (depth.has(id)) return depth.get(id)!; if (seen.has(id)) return 0; seen.add(id);
        const parents = (byId.get(id)?.prerequisiteIds ?? []).filter((p) => byId.has(p));
        const value = parents.length ? Math.max(...parents.map(layer)) + 1 : 0; seen.delete(id); depth.set(id, value); return value;
    };
    const columns = new Map<number, EditableSkill[]>();
    skills.forEach((skill) => { const l = layer(skill.id); columns.set(l, [...(columns.get(l) ?? []), skill]); });
    const out = new Map<string, { x: number; y: number }>();
    columns.forEach((list, l) => [...list].sort((a, b) => a.name.localeCompare(b.name)).forEach((skill, i) => out.set(skill.id, { x: l * (W + GAP_X), y: i * (H + GAP_Y) })));
    return out;
}

function SkillBox({ data }: NodeProps<Node<Data>>) {
    const { skill, selected, onSelect } = data;
    return <>
        <Handle type="target" position={Position.Left} isConnectable={false} style={{ opacity: 0 }} />
        <ButtonBase onClick={() => onSelect(skill.id)} aria-pressed={selected} className="nodrag"
            aria-label={`${skill.name}, ${skill.prerequisiteIds.length} prerequisites, ${skill.questionCount} questions`}
            sx={{ width: W, height: H, borderRadius: `${radius.lg}px`, bgcolor: color.surface, px: 2, justifyContent: 'flex-start', textAlign: 'left',
                boxShadow: selected ? `0 0 0 2px ${color.brand600}, ${shadow.raised}` : `inset 0 0 0 1px ${color.line200}`, '&.Mui-focusVisible': { outline: `2px solid ${color.brand600}`, outlineOffset: 3 } }}>
            <Box sx={{ minWidth: 0 }}>
                <Typography component="span" sx={{ display: 'block', fontWeight: 600, fontSize: '0.9375rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{skill.name}</Typography>
                <Typography component="span" sx={{ display: 'block', fontSize: '0.8125rem', fontWeight: 500, color: color.ink600 }}>{skill.questionCount} questions</Typography>
            </Box>
        </ButtonBase>
        <Handle type="source" position={Position.Right} isConnectable={false} style={{ opacity: 0 }} />
    </>;
}
const nodeTypes = { skill: SkillBox };

/** Instructor graph view; edges are created through the accessible prerequisite form, not by dragging. */
export function SkillGraphCanvas({ skills, selectedId, onSelect, label }: { skills: EditableSkill[]; selectedId: string | null; onSelect: (id: string | null) => void; label: string }) {
    const { nodes, edges } = useMemo(() => {
        const positions = layout(skills);
        const nodes: Node<Data>[] = [...skills].sort((a, b) => (positions.get(a.id)!.x - positions.get(b.id)!.x) || (positions.get(a.id)!.y - positions.get(b.id)!.y))
            .map((skill) => ({ id: skill.id, type: 'skill', position: positions.get(skill.id)!, draggable: false, selectable: false, focusable: false, data: { skill, selected: skill.id === selectedId, onSelect: (id: string) => onSelect(id === selectedId ? null : id) } }));
        const ids = new Set(skills.map((skill) => skill.id));
        const edges: Edge[] = skills.flatMap((skill) => skill.prerequisiteIds.filter((p) => ids.has(p)).map((p) => {
            const active = p === selectedId || skill.id === selectedId;
            return { id: `${p}->${skill.id}`, source: p, target: skill.id, type: 'smoothstep', focusable: false, selectable: false,
                style: { stroke: active ? color.brand600 : color.ink400, strokeWidth: 2, strokeDasharray: active ? undefined : pathLine.dasharray }, markerEnd: { type: MarkerType.ArrowClosed, color: active ? color.brand600 : color.ink400 } };
        }));
        return { nodes, edges };
    }, [skills, selectedId, onSelect]);
    return <ReactFlowProvider><Box role="group" aria-label={label} sx={{ height: 440, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden', '& .react-flow__attribution': { display: 'none' } }}>
        <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} fitView fitViewOptions={{ padding: 0.12, minZoom: 0.72, maxZoom: 1 }} minZoom={0.4} nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}
            nodesFocusable={false} edgesFocusable={false} zoomOnScroll={false} preventScrolling={false} onPaneClick={() => onSelect(null)} proOptions={{ hideAttribution: true }} />
    </Box></ReactFlowProvider>;
}
