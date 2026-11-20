import type { ReactNode } from 'react';
import { Box, Tooltip } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { radius, tones, roles } from '../../app/theme/theme';
import type { Tone } from '../../app/theme/theme';

export type PillTone = Tone | 'dashed' | 'ink';

export interface StatusPillProps {
    tone?: PillTone;
    icon?: PresentationIconName;
    children: ReactNode;
    /** `pill` (36 px, label type) for statuses; `tag` (28 px, caption) for compact domain tags. */
    size?: 'pill' | 'tag';
    title?: string;
    sx?: SxProps<Theme>;
}

/** Short state text with glyph. Never colour alone (DESIGN.md › The Never-Alone Rule). */
export function StatusPill({ tone = 'neutral', icon, children, size = 'pill', title, sx }: StatusPillProps) {
    const tag = size === 'tag';
    const background = tone === 'dashed' ? roles.bg.surface : tone === 'ink' ? roles.bg.inverse : tones[tone].tint;
    const glyph = tone === 'dashed' ? roles.text.secondary : tone === 'ink' ? roles.text.inverse : tones[tone].strong;
    const pill = <Box component="span" data-tone={tone} sx={[{
        display: 'inline-flex', alignItems: 'center', gap: tag ? .625 : .75, height: tag ? 28 : 36, px: tag ? 1.25 : 1.75, maxWidth: '100%',
        borderRadius: tag ? `${radius.xs}px` : `${radius.pill}px`, bgcolor: background, color: tone === 'ink' ? roles.text.inverse : roles.text.primary,
        border: tone === 'dashed' ? `1.5px dashed ${roles.line.strong}` : 0, boxSizing: 'border-box',
        fontSize: tag ? '0.8125rem' : '0.9375rem', fontWeight: 600, lineHeight: 1.2, whiteSpace: 'nowrap', verticalAlign: 'middle', flexShrink: 0,
        '& svg': { fontSize: tag ? 16 : 18, color: glyph, flexShrink: 0 },
    }, ...(Array.isArray(sx) ? sx : [sx])]}>
        {icon && <PresentationIcon name={icon} />}
        <Box component="span" sx={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{children}</Box>
    </Box>;
    return title ? <Tooltip title={title}>{pill}</Tooltip> : pill;
}

/** Exactly the four approved mastery states (Adaptive §4). Missing data = Unknown. */
export type MasteryState = 'unknown' | 'learning' | 'mastered' | 'weak';
export const masteryMeta: Record<MasteryState, { label: string; meaning: string; tone: PillTone; icon: PresentationIconName; data: string }> = {
    unknown: { label: 'Unknown', meaning: 'Not assessed yet.', tone: 'dashed', icon: 'dashed-circle', data: roles.data.unknown },
    learning: { label: 'Learning', meaning: 'Some quiz evidence, not yet mastered.', tone: 'sky', icon: 'half-circle', data: roles.data.learning },
    mastered: { label: 'Mastered', meaning: "Quiz evidence shows you've mastered this skill.", tone: 'mint', icon: 'check-circle', data: roles.data.mastered },
    weak: { label: 'Weak', meaning: 'Quiz evidence shows a gap to review.', tone: 'butter', icon: 'alert-triangle', data: roles.data.weak },
};
export const masteryOrder: MasteryState[] = ['unknown', 'learning', 'mastered', 'weak'];

export function MasteryTag({ state, size = 'tag', withMeaning = false }: { state: MasteryState; size?: 'pill' | 'tag'; withMeaning?: boolean }) {
    const meta = masteryMeta[state];
    return <StatusPill tone={meta.tone} icon={meta.icon} size={size} title={withMeaning ? meta.meaning : undefined}>{meta.label}</StatusPill>;
}

/** Approved recommendation categories (Adaptive §6) with their UI wording. */
export type RecommendationType = 'continue' | 'skip' | 'remedial';
export const recommendationMeta: Record<RecommendationType, { label: string; icon: PresentationIconName }> = {
    continue: { label: 'Continue', icon: 'arrow-right' },
    skip: { label: 'Test-out', icon: 'skip' },
    remedial: { label: 'Remedial', icon: 'route' },
};
export function RecommendationTag({ type, size = 'tag' }: { type: RecommendationType; size?: 'pill' | 'tag' }) {
    const meta = recommendationMeta[type];
    return <StatusPill tone="lilac" icon={meta.icon} size={size}>{meta.label}</StatusPill>;
}

/** Legend of the four states with one-line meanings (Mastery Map, insights). */
export function MasteryLegend({ dense = false }: { dense?: boolean }) {
    return <Box component="ul" aria-label="Mastery states" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexWrap: 'wrap', gap: dense ? 1 : 2 }}>
        {masteryOrder.map((state) => <Box component="li" key={state} sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
            <MasteryTag state={state} />
            {!dense && <Box component="span" sx={{ fontSize: '0.8125rem', fontWeight: 500, color: 'text.secondary' }}>{masteryMeta[state].meaning}</Box>}
        </Box>)}
    </Box>;
}
