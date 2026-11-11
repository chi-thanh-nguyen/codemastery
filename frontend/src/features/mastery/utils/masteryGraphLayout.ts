/**
 * Deterministic presentation layout for a course skill graph: layers by the
 * longest prerequisite chain, ties ordered by skill name (DESIGN.md › S14).
 * This positions nodes only; it implements no adaptive or validation logic and
 * tolerates (does not detect or report) malformed input.
 */
export interface LayoutSkill { id: string; name: string; prerequisiteIds: string[] }
export interface LayoutPosition { x: number; y: number; layer: number; order: number }

/** DESIGN.md › Mastery Map node: 220 × 72 px. */
export const NODE_WIDTH = 220;
export const NODE_HEIGHT = 72;
const COLUMN_GAP = 56;
const ROW_GAP = 28;

export function masteryGraphLayout(skills: readonly LayoutSkill[]): Map<string, LayoutPosition> {
    const byId = new Map(skills.map((skill) => [skill.id, skill]));
    const depth = new Map<string, number>();
    const visiting = new Set<string>();
    const layerOf = (id: string): number => {
        const known = depth.get(id);
        if (known !== undefined) return known;
        if (visiting.has(id)) return 0;
        visiting.add(id);
        const skill = byId.get(id);
        const parents = (skill?.prerequisiteIds ?? []).filter((parent) => byId.has(parent));
        const value = parents.length === 0 ? 0 : Math.max(...parents.map(layerOf)) + 1;
        visiting.delete(id);
        depth.set(id, value);
        return value;
    };
    skills.forEach((skill) => layerOf(skill.id));
    const layers = new Map<number, LayoutSkill[]>();
    skills.forEach((skill) => {
        const layer = depth.get(skill.id) ?? 0;
        layers.set(layer, [...(layers.get(layer) ?? []), skill]);
    });
    const positions = new Map<string, LayoutPosition>();
    [...layers.keys()].sort((a, b) => a - b).forEach((layer) => {
        const ordered = [...layers.get(layer)!].sort((a, b) => a.name.localeCompare(b.name, 'en') || a.id.localeCompare(b.id));
        ordered.forEach((skill, order) => positions.set(skill.id, {
            x: layer * (NODE_WIDTH + COLUMN_GAP), y: order * (NODE_HEIGHT + ROW_GAP), layer, order,
        }));
    });
    return positions;
}

/** Skills in reading order (layer, then position) for keyboard and list order. */
export function orderedSkillIds(positions: Map<string, LayoutPosition>): string[] {
    return [...positions.entries()].sort(([, a], [, b]) => a.layer - b.layer || a.order - b.order).map(([id]) => id);
}

/** Untransformed size of the laid-out graph in canvas pixels. */
export function graphExtent(positions: Map<string, LayoutPosition>): { width: number; height: number } {
    if (positions.size === 0) return { width: 0, height: 0 };
    const values = [...positions.values()];
    const layers = Math.max(...values.map((position) => position.layer)) + 1;
    const rows = Math.max(...values.map((position) => position.order)) + 1;
    return { width: layers * NODE_WIDTH + (layers - 1) * COLUMN_GAP, height: rows * NODE_HEIGHT + (rows - 1) * ROW_GAP };
}

/**
 * Initial viewport: fit the whole graph when it stays readable (zoom between
 * `minZoom` and 1); otherwise start at the beginning of the path at `minZoom`
 * so the first skills are legible and the rest is reached by panning or Fit.
 */
export function initialGraphViewport(extent: { width: number; height: number }, canvas: { width: number; height: number },
    { minZoom = 0.85, padding = 32, focus }: { minZoom?: number; padding?: number; focus?: { x: number; y: number } } = {}) {
    if (extent.width === 0 || canvas.width === 0) return { x: padding, y: padding, zoom: 1 };
    const fit = Math.min((canvas.width - padding * 2) / extent.width, (canvas.height - padding * 2) / extent.height, 1);
    let zoom = Math.max(minZoom, fit);
    // A preselected skill (for example the recommendation target) must be fully in view: first zoom out slightly
    // (never below 0.75) so the path still starts at the left edge, otherwise pan to it.
    if (focus && (focus.x + NODE_WIDTH) * zoom > canvas.width - padding * 2) {
        zoom = Math.max(Math.min(zoom, 0.75), (canvas.width - padding * 2) / (focus.x + NODE_WIDTH));
    }
    let x = extent.width * zoom <= canvas.width - padding * 2 ? (canvas.width - extent.width * zoom) / 2 : padding;
    let y = extent.height * zoom <= canvas.height - padding * 2 ? (canvas.height - extent.height * zoom) / 2 : padding;
    if (focus) {
        x = Math.min(x, canvas.width - padding - (focus.x + NODE_WIDTH) * zoom);
        y = Math.min(y, canvas.height - padding - (focus.y + NODE_HEIGHT) * zoom);
    }
    return { x: Math.round(x), y: Math.round(y), zoom: Math.round(zoom * 1000) / 1000 };
}
