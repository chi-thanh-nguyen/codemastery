import { describe, expect, it } from 'vitest';
import { graphExtent, initialGraphViewport, masteryGraphLayout, NODE_HEIGHT, NODE_WIDTH, orderedSkillIds } from './masteryGraphLayout';

const skill = (id: string, prerequisiteIds: string[] = [], name = id) => ({ id, name, prerequisiteIds });

describe('mastery graph presentation layout', () => {
    it('layers skills by their longest prerequisite chain and orders ties by name', () => {
        const positions = masteryGraphLayout([skill('loops', ['conditionals']), skill('variables'), skill('conditionals', ['expressions']), skill('expressions', ['variables']), skill('output', ['variables'])]);
        expect(Object.fromEntries([...positions].map(([id, p]) => [id, p.layer]))).toEqual({ variables: 0, expressions: 1, output: 1, conditionals: 2, loops: 3 });
        expect(positions.get('expressions')!.order).toBe(0); expect(positions.get('output')!.order).toBe(1);
        expect(orderedSkillIds(positions)).toEqual(['variables', 'expressions', 'output', 'conditionals', 'loops']);
    });
    it('is deterministic regardless of input order', () => {
        const a = [skill('b', ['a']), skill('a'), skill('c', ['a'])];
        expect(Object.fromEntries(masteryGraphLayout(a))).toEqual(Object.fromEntries(masteryGraphLayout([...a].reverse())));
        expect(orderedSkillIds(masteryGraphLayout(a))).toEqual(orderedSkillIds(masteryGraphLayout([...a].reverse())));
    });
    it('tolerates malformed input (unknown prerequisites and cycles) without validating or throwing', () => {
        const positions = masteryGraphLayout([skill('x', ['y']), skill('y', ['x']), skill('z', ['missing'])]);
        expect(positions.size).toBe(3); expect(positions.get('z')!.layer).toBe(0);
    });
    it('uses the 220 × 72 px node and reports the laid-out extent', () => {
        expect([NODE_WIDTH, NODE_HEIGHT]).toEqual([220, 72]);
        expect(graphExtent(new Map())).toEqual({ width: 0, height: 0 });
        const extent = graphExtent(masteryGraphLayout([skill('a'), skill('b', ['a']), skill('c', ['a'])]));
        expect(extent).toEqual({ width: 2 * 220 + 56, height: 2 * 72 + 28 });
    });
    it('fits a small graph centred at up to 100% and starts a wide graph at the beginning at the readable floor', () => {
        const small = initialGraphViewport({ width: 496, height: 172 }, { width: 900, height: 420 });
        expect(small.zoom).toBe(1); expect(small.x).toBe(202); expect(small.y).toBe(124);
        const medium = initialGraphViewport({ width: 960, height: 272 }, { width: 900, height: 420 });
        expect(medium.zoom).toBeCloseTo(836 / 960, 3); expect(medium.x).toBe(32); // fitted width fills the padded canvas exactly
        const wide = initialGraphViewport({ width: 2000, height: 272 }, { width: 600, height: 420 });
        expect(wide).toEqual({ x: 32, y: Math.round((420 - 272 * 0.85) / 2), zoom: 0.85 });
        expect(initialGraphViewport({ width: 0, height: 0 }, { width: 600, height: 400 })).toEqual({ x: 32, y: 32, zoom: 1 });
        // A preselected node beyond the right edge is pulled into view; one already visible changes nothing.
        const near = initialGraphViewport({ width: 2000, height: 272 }, { width: 908, height: 420 }, { focus: { x: 828, y: 100 } });
        expect(near.x).toBe(32); expect(near.zoom).toBeCloseTo(844 / 1048, 3); // zooms out a little, path still starts at the left
        const far = initialGraphViewport({ width: 2000, height: 272 }, { width: 600, height: 420 }, { focus: { x: 828, y: 100 } });
        expect(far.zoom).toBe(0.75); expect(far.x).toBe(Math.round(600 - 32 - (828 + 220) * 0.75));
        expect(initialGraphViewport({ width: 2000, height: 272 }, { width: 600, height: 420 }, { focus: { x: 0, y: 0 } }).x).toBe(32);
    });
});
