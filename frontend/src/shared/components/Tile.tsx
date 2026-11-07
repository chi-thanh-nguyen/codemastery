import type { ElementType, MouseEvent, ReactNode } from 'react';
import { Box, IconButton, LinearProgress, Tooltip, Typography } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { CourseMark } from './CourseMark';
import { isPlainClick } from './SidebarNav';
import type { PresentationIconName } from './PresentationIcon';
import { color, control, motion, radius, shadow, tones } from '../../app/theme/theme';
import type { Tone } from '../../app/theme/theme';

export interface TileProps {
    children: ReactNode;
    component?: ElementType;
    /** Whole tile is one link (see TileTitleLink); adds pointer hover lift. */
    interactive?: boolean;
    padding?: number | { xs?: number; sm?: number; md?: number };
    sx?: SxProps<Theme>;
    id?: string;
    'aria-labelledby'?: string;
}

/** Borderless white tile at 24 px radius (DESIGN.md › Tiles). Never nest tiles. */
export function Tile({ children, component = 'div', interactive = false, padding = 3, sx, ...rest }: TileProps) {
    return <Box component={component} {...rest} sx={[{
        position: 'relative', bgcolor: color.surface, borderRadius: `${radius.lg}px`, p: padding, minWidth: 0, boxSizing: 'border-box',
        ...(interactive ? {
            transition: `transform ${motion.standard}ms ${motion.easeIn}, box-shadow ${motion.standard}ms ${motion.easeIn}`,
            // The whole tile is the link: hovering it also fills a secondary arrow circle, like a hovered secondary button.
            '@media (hover: hover) and (pointer: fine)': { '&:hover': { transform: 'translateY(-2px)', boxShadow: shadow.hover, '& [data-affordance="secondary"]': { bgcolor: color.fill150 } } },
            '&:focus-within:has(a[data-tile-link]:focus-visible)': { boxShadow: `0 0 0 2px ${color.brand600}` },
        } : {}),
    }, ...(Array.isArray(sx) ? sx : [sx])]}>{children}</Box>;
}

export interface TileTitleLinkProps {
    children: ReactNode;
    href?: string;
    onClick?: () => void;
    component?: 'h2' | 'h3' | 'h4';
    variant?: 'h3' | 'h4';
    id?: string;
}
/** Tile title whose link stretches over the tile; inner actions sit above it (z-index 1). */
export function TileTitleLink({ children, href, onClick, component = 'h3', variant = 'h3', id }: TileTitleLinkProps) {
    return <Typography component={component} variant={variant} id={id} sx={{ overflowWrap: 'anywhere', textWrap: 'balance' }}>
        <Box component="a" data-tile-link href={href ?? '#'} onClick={(event: MouseEvent) => { if (!onClick || (href && !isPlainClick(event))) return; event.preventDefault(); onClick(); }}
            sx={{ color: 'inherit', textDecoration: 'none', '&::after': { content: '""', position: 'absolute', inset: 0, borderRadius: `${radius.lg}px` },
                '&:focus-visible': { outline: 'none' }, '&:hover': { textDecoration: 'underline', textUnderlineOffset: 4, textDecorationThickness: 2 } }}>
            {children}
        </Box>
    </Typography>;
}

/** Content above a stretched tile link (actions, menus). */
export function TileLayer({ children, sx }: { children: ReactNode; sx?: SxProps<Theme> }) {
    return <Box sx={[{ position: 'relative', zIndex: 1 }, ...(Array.isArray(sx) ? sx : [sx])]}>{children}</Box>;
}

/** 40 px marker overlapping the tile's top-right corner. Decorative: its meaning is repeated as a text tag. */
export function CornerBadge({ tone, icon }: { tone: Tone; icon: PresentationIconName }) {
    return <Box aria-hidden sx={{ position: 'absolute', top: -12, right: -12, width: 40, height: 40, borderRadius: '50%', bgcolor: tones[tone].tint,
        border: `4px solid ${color.canvas100}`, display: 'grid', placeItems: 'center', color: tones[tone].strong, zIndex: 2, '& svg': { fontSize: 18 } }}>
        <PresentationIcon name={icon} />
    </Box>;
}

export interface CircleActionProps {
    label: string;
    icon: PresentationIconName;
    onClick?: () => void;
    href?: string;
    variant?: 'ink' | 'fill' | 'plain';
    disabled?: boolean;
    'aria-haspopup'?: 'menu';
    'aria-expanded'?: boolean;
    'aria-pressed'?: boolean;
    size?: 'md' | 'sm';
}
/** 44 px circular action. `ink` is the single primary action of a tile or row. */
export function CircleAction({ label, icon, onClick, href, variant = 'fill', disabled, size = 'md', ...aria }: CircleActionProps) {
    const s = size === 'sm' ? 36 : 44;
    // ink = primary (graphite fill); fill = secondary (neutral outlined circle); plain = no surface.
    const palette = variant === 'ink' ? { bgcolor: control.primary, color: color.surface, '&:hover': { bgcolor: control.primaryHover } }
        : variant === 'fill' ? control.secondary
            : { color: color.ink700, '&:hover': { bgcolor: color.fill150 } };
    return <Tooltip title={label}>
        <span style={{ display: 'inline-flex' }}>
            <IconButton aria-label={label} onClick={(event: MouseEvent) => { if (href && onClick) event.preventDefault(); onClick?.(); }} disabled={disabled}
                {...(href ? { component: 'a', href } : {})} {...aria}
                sx={{ width: s, height: s, flexShrink: 0, ...palette, '&.Mui-disabled': { bgcolor: variant === 'plain' ? 'transparent' : variant === 'fill' ? color.surface : color.fill150, color: variant === 'plain' ? color.line200 : color.ink500 }, '& svg': { fontSize: 20 } }}>
                <PresentationIcon name={icon} />
            </IconButton>
        </span>
    </Tooltip>;
}

/** Visual ink circle for a tile whose title link already covers the tile (no duplicate tab stop). */
/**
 * `secondary` (default): neutral outlined circle with a graphite icon, for ordinary "open" tiles.
 * `primary`: graphite circle, only for the one important next step (Resume, Continue).
 */
export function CircleAffordance({ icon = 'arrow-right', emphasis = 'secondary' }: { icon?: PresentationIconName; emphasis?: 'primary' | 'secondary' }) {
    const surface = emphasis === 'primary' ? { bgcolor: control.primary, color: color.surface }
        : { bgcolor: color.surface, color: control.primary, border: `1px solid ${color.line200}`, boxSizing: 'border-box' };
    return <Box aria-hidden data-affordance={emphasis} sx={{ width: 44, height: 44, borderRadius: '50%', ...surface, display: 'grid', placeItems: 'center',
        flexShrink: 0, pointerEvents: 'none', transition: `background-color ${motion.fast}ms`, '& svg': { fontSize: 20 } }}><PresentationIcon name={icon} /></Box>;
}

export interface StatTileProps { value: ReactNode; label: string; tone?: 'sky' | 'mint' | 'butter' | 'lilac' | 'neutral' | 'dashed' | 'white' }
/** Real counts only. Dashed means "not yet" (Remaining, Unknown). */
export function StatTile({ value, label, tone = 'white' }: StatTileProps) {
    const bg = tone === 'dashed' || tone === 'white' ? color.surface : tone === 'neutral' ? color.fill150 : tones[tone].tint;
    return <Box component="li" sx={{ flex: '1 1 0', minWidth: 112, bgcolor: bg, borderRadius: `${radius.lg}px`, p: { xs: 2, sm: 2.5 }, textAlign: 'center',
        border: tone === 'dashed' ? `1.5px dashed ${color.ink400}` : 0, boxSizing: 'border-box', listStyle: 'none' }}>
        <Typography component="span" variant="stat" sx={{ display: 'block' }}>{value}</Typography>
        <Typography component="span" sx={{ display: 'block', mt: 1, color: 'text.secondary', fontWeight: 500 }}>{label}</Typography>
    </Box>;
}
export function StatGroup({ children, label }: { children: ReactNode; label: string }) {
    return <Box component="ul" aria-label={label} sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, m: 0, p: 0 }}>{children}</Box>;
}

export interface ProgressLineProps { completed: number; total: number; percent: number; unit?: string; label?: string }
/** Linear progress with its value in text ("12 of 20 lessons · 60%"). Values come from the owning contract. */
export function ProgressLine({ completed, total, percent, unit = 'lessons', label = 'Course progress' }: ProgressLineProps) {
    const text = `${completed} of ${total} ${unit} · ${percent}%`;
    return <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
        <LinearProgress variant="determinate" value={Math.max(0, Math.min(100, percent))} aria-label={label} aria-valuetext={text} sx={{ flex: '1 1 120px', minWidth: 80 }} />
        <Typography component="span" className="cm-tabular" sx={{ fontSize: '0.9375rem', fontWeight: 600, whiteSpace: 'nowrap' }}>{text}</Typography>
    </Box>;
}

/** Label + value pairs (definition list) for fact asides and record details. */
export function FactList({ items, columns = 1 }: { items: { label: string; value: ReactNode }[]; columns?: 1 | 2 }) {
    return <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: columns === 2 ? { xs: '1fr', sm: '1fr 1fr' } : '1fr', columnGap: 3, rowGap: 2.5 }}>
        {items.map((item) => <Box key={item.label} sx={{ minWidth: 0 }}>
            <Box component="dt" sx={{ fontSize: '0.8125rem', fontWeight: 600, color: 'text.secondary', mb: .5 }}>{item.label}</Box>
            <Box component="dd" sx={{ m: 0, overflowWrap: 'anywhere' }}>{item.value}</Box>
        </Box>)}
    </Box>;
}

/** Section heading with optional trailing action (h2 by default). */
export function SectionHeading({ children, action, component = 'h2', id }: { children: ReactNode; action?: ReactNode; component?: 'h2' | 'h3'; id?: string }) {
    return <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography component={component} variant={component === 'h2' ? 'h2' : 'h3'} id={id}>{children}</Typography>
        {action}
    </Box>;
}

export interface CourseTileProps {
    title: string;
    description: string;
    /** Secondary line, for example "Instructor · Name". */
    byline?: string;
    href: string;
    onOpen: () => void;
    status?: ReactNode;
    extraActions?: ReactNode;
    headingLevel?: 'h2' | 'h3';
    children?: ReactNode;
}
/** Course tile (DESIGN.md › Tiles): the whole tile opens the course. Shared by catalogue, learning and teaching views. */
export function CourseTile({ title, description, byline, href, onOpen, status, extraActions, headingLevel = 'h2', children }: CourseTileProps) {
    return <Tile component="article" interactive sx={{ display: 'flex', flexDirection: 'column', gap: 2, height: '100%' }}>
        <CourseMark title={title} description={description} />
        <Box sx={{ minWidth: 0 }}>
            <TileTitleLink component={headingLevel} href={href} onClick={onOpen}>{title}</TileTitleLink>
            <Typography sx={{ mt: 1.5, color: 'text.secondary', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden', overflowWrap: 'anywhere' }}>
                {description}
            </Typography>
        </Box>
        {children}
        {byline && <Typography variant="caption" sx={{ color: 'text.secondary', overflowWrap: 'anywhere', mt: 'auto' }}>{byline}</Typography>}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minHeight: 44, mt: byline ? 0 : 'auto' }}>
            <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexWrap: 'wrap', gap: 1 }}>{status}</Box>
            {extraActions && <TileLayer sx={{ display: 'flex', gap: 1 }}>{extraActions}</TileLayer>}
            <CircleAffordance />
        </Box>
    </Tile>;
}
