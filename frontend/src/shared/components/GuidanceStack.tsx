import type { MouseEvent, ReactNode } from 'react';
import { Box, ButtonBase, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { color, motion, radius, tones } from '../../app/theme/theme';
import type { Tone } from '../../app/theme/theme';

export interface GuidanceAction { label: string; onClick?: () => void; href?: string }
export interface GuidanceEntryData {
    id: string;
    /** Semantic family: lilac = recommendation, butter = deadline/warning, mint = result, sky = reply/info. */
    tone: Tone;
    icon: PresentationIconName;
    type: string;
    meta?: string;
    body: ReactNode;
    action?: GuidanceAction;
}

export interface GuidanceStackProps {
    title?: string;
    entries: GuidanceEntryData[];
    emptyText?: string;
    headingLevel?: 'h2' | 'h3';
    titleId?: string;
}

/** Stacked, colour-coded guidance entries for the contextual aside (DESIGN.md › Guidance Stack). */
export function GuidanceStack({ title, entries, emptyText = 'Nothing else needs your attention.', headingLevel = 'h2', titleId }: GuidanceStackProps) {
    return <Box>
        {title && <Typography component={headingLevel} variant="h2" id={titleId} sx={{ mb: 3 }}>{title}</Typography>}
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
            {entries.map((entry) => <GuidanceEntry key={entry.id} entry={entry} />)}
            {entries.length < 3 && <Box component="li" sx={{ border: `1.5px dashed ${color.ink400}`, borderRadius: `${radius.lg}px`, px: 2.5, py: 2.5, textAlign: 'center', color: 'text.secondary' }}>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>{emptyText}</Typography>
            </Box>}
        </Box>
    </Box>;
}

export function GuidanceEntry({ entry, component = 'li' }: { entry: GuidanceEntryData; component?: 'li' | 'div' }) {
    const tone = tones[entry.tone];
    return <Box component={component} sx={{ bgcolor: tone.tint, borderRadius: `${radius.lg}px`, p: 2, minWidth: 0 }}>
        {/* A long meta (a course title) wraps to its own line instead of squeezing the type label. */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', columnGap: 1.25, rowGap: .25, px: .5, mb: 1.5, minHeight: 28 }}>
            <PresentationIcon name={entry.icon} sx={{ fontSize: 20, color: tone.strong }} />
            <Typography component="span" sx={{ fontWeight: 600, fontSize: '0.9375rem', flex: '1 0 auto', maxWidth: 'calc(100% - 30px)' }}>{entry.type}</Typography>
            {entry.meta && <Typography component="span" sx={{ fontSize: '0.8125rem', fontWeight: 500, color: color.ink700, ml: 'auto', maxWidth: '100%', overflowWrap: 'anywhere' }}>{entry.meta}</Typography>}
        </Box>
        <Box sx={{ bgcolor: tone.soft, borderRadius: `${radius.md}px`, px: 2, py: 1.75, overflowWrap: 'anywhere', '& p': { m: 0 } }}>{entry.body}</Box>
        {entry.action && <ButtonBase component="a" href={entry.action.href ?? '#'}
            onClick={(event: MouseEvent) => { if (entry.action?.onClick) { event.preventDefault(); entry.action.onClick(); } }}
            sx={{ mt: 1.5, width: '100%', minHeight: 44, borderRadius: `${radius.pill}px`, bgcolor: color.surface, color: color.ink900, fontWeight: 600, fontSize: '0.9375rem',
                gap: 1, transition: `background-color ${motion.fast}ms`, '&:hover': { bgcolor: tone.soft }, '&:focus-visible': { outline: `2px solid ${color.brand600}`, outlineOffset: 2 } }}>
            {entry.action.label}<PresentationIcon name="arrow-right" sx={{ fontSize: 18 }} />
        </ButtonBase>}
    </Box>;
}
