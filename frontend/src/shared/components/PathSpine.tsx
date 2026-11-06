import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { color, motion } from '../../app/theme/theme';

/** Decorative node state; the item's own text (status pill) always carries the meaning. */
export type SpineNode = 'done' | 'current' | 'recommended' | 'todo' | 'static';
export interface SpineItem { id: string; node: SpineNode; content: ReactNode }
export interface SpineSection { id: string; title?: string; meta?: ReactNode; items: SpineItem[] }

export interface PathSpineProps {
    sections: SpineSection[];
    /** Accessible name of each ordered list ("Lessons in Getting Started"). */
    listLabel?: (section: SpineSection) => string;
    /** `tiles` aligns nodes with tile titles; `rows` with compact rows. */
    density?: 'tiles' | 'rows';
    headingLevel?: 'h2' | 'h3';
}

/**
 * The Lit Path: canonical order drawn as a dashed line through ordered items
 * (DESIGN.md › Path Spine). Order is never changed by recommendations.
 */
export function PathSpine({ sections, listLabel, density = 'tiles', headingLevel = 'h3' }: PathSpineProps) {
    const nodeTop = density === 'tiles' ? 30 : 16;
    const gutter = { xs: density === 'tiles' ? 28 : 24, sm: density === 'tiles' ? 40 : 32 };
    const gutterPx = { xs: `${gutter.xs}px`, sm: `${gutter.sm}px` };
    return <Box>
        {sections.map((section, sectionIndex) => <Box key={section.id} component="section" aria-label={section.title} sx={{ mt: sectionIndex === 0 ? 0 : density === 'tiles' ? 5 : 3 }}>
            {section.title && <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, flexWrap: 'wrap', mb: density === 'tiles' ? 2 : 1, pl: gutterPx }}>
                <Typography component={headingLevel} variant={density === 'tiles' ? 'h3' : 'h4'}>{section.title}</Typography>
                {section.meta}
            </Box>}
            <Box component="ol" aria-label={listLabel?.(section)} sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {section.items.map((item, index) => {
                    const first = index === 0, last = index === section.items.length - 1;
                    const solid = item.node === 'done';
                    return <Box component="li" key={item.id} data-node={item.node} sx={{ position: 'relative', display: 'grid', gridTemplateColumns: { xs: `${gutter.xs}px minmax(0, 1fr)`, sm: `${gutter.sm}px minmax(0, 1fr)` },
                        pb: last ? 0 : density === 'tiles' ? 2 : 0 }}>
                        {section.items.length > 1 && <Box aria-hidden sx={{ position: 'absolute', left: { xs: gutter.xs / 2 - 1, sm: gutter.sm / 2 - 1 }, width: 0,
                            top: first ? nodeTop : 0, bottom: last ? `calc(100% - ${nodeTop}px)` : 0,
                            borderLeft: `2px ${solid && !last ? 'solid' : 'dashed'} ${solid && !last ? color.mint700 : color.brand400}`,
                            transition: `border-color ${motion.signature}ms ${motion.easeIn}` }} />}
                        <Box aria-hidden sx={{ position: 'relative', zIndex: 1 }}><SpineDot node={item.node} top={nodeTop} /></Box>
                        <Box sx={{ minWidth: 0 }}>{item.content}</Box>
                    </Box>;
                })}
            </Box>
        </Box>)}
    </Box>;
}

function SpineDot({ node, top }: { node: SpineNode; top: number }) {
    const size = node === 'static' ? 10 : node === 'todo' ? 14 : 20;
    const common = { position: 'absolute', left: '50%', top, width: size, height: size, borderRadius: '50%', transform: 'translate(-50%, -50%)', boxSizing: 'border-box',
        display: 'grid', placeItems: 'center', transition: `background-color ${motion.signature}ms ${motion.easeIn}, box-shadow ${motion.signature}ms` } as const;
    if (node === 'done') return <Box sx={{ ...common, bgcolor: color.mint700, color: color.surface }}><PresentationIcon name="check" sx={{ fontSize: 14, strokeWidth: 2.5 }} /></Box>;
    if (node === 'current') return <Box sx={{ ...common, bgcolor: color.brand600, boxShadow: `0 0 0 4px ${color.brand100}` }} />;
    if (node === 'recommended') return <Box sx={{ ...common, bgcolor: color.surface, border: `4px solid ${color.lilac700}` }} />;
    if (node === 'static') return <Box sx={{ ...common, bgcolor: color.ink400 }} />;
    return <Box sx={{ ...common, bgcolor: color.surface, border: `2px solid ${color.ink400}` }} />;
}
