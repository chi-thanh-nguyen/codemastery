import { Box, Typography } from '@mui/material';
import { brandFontStack } from '../../app/theme/theme';

/**
 * A pixel size, or one per breakpoint (`xs` below 600 px, `sm` from 600 px). `narrow` is one fixed
 * step for phones narrower than 390 px, where the `xs` lockup would push the top bar (menu, bell,
 * account) into horizontal scroll. Fixed steps only: no `clamp()` or viewport units.
 */
export type BrandSize = number | { xs: number; sm: number; narrow?: number };
/** Lockup-only step (DESIGN.md › Brand and logo); not a global breakpoint. */
export const BRAND_NARROW_QUERY = '@media (max-width:389.95px)';
/** `prop: factor × size` at each step, as sx entries (breakpoint object plus the narrow step). */
function scaled(size: BrandSize, prop: string, factor: number, unit: (px: number) => string = (px) => `${px}px`) {
    if (typeof size === 'number') return { [prop]: unit(size * factor) };
    return { [prop]: { xs: unit(size.xs * factor), sm: unit(size.sm * factor) },
        ...(size.narrow === undefined ? {} : { [BRAND_NARROW_QUERY]: { [prop]: unit(size.narrow * factor) } }) };
}

export interface BrandLogoProps { size?: BrandSize; decorative?: boolean }

/** The favicon is the canonical artwork. Size controls height; SVG proportions stay intrinsic. */
export function BrandLogo({ size = 34, decorative = false }: BrandLogoProps) {
    // Reserve the canonical viewBox ratio before the SVG decodes; no artwork is duplicated.
    return <Box component="img" src={`${import.meta.env.BASE_URL}favicon.svg`}
        alt={decorative ? '' : 'CodeMastery'} aria-hidden={decorative ? true : undefined}
        height={typeof size === 'number' ? size : size.sm} draggable={false}
        sx={{ display: 'block', ...scaled(size, 'height', 1), width: 'auto', aspectRatio: '1 / 1', objectFit: 'contain', flexShrink: 0 }} />;
}

export interface BrandWordmarkProps { size?: BrandSize; tone?: 'onChrome' | 'onLight'; hideTextBelow?: number; textSize?: string }
/**
 * Logo mark plus the "CodeMastery" wordmark; the mark is decorative because the text names it.
 * The wordmark is the only text set in the brand face (Poppins ExtraBold 800, see theme `brandFontStack`).
 * Mark, gap and text scale together from one size (text 0.625 × mark, gap 0.3 × mark), so the lockup
 * keeps its proportions at every size.
 */
export function BrandWordmark({ size = 32, tone = 'onChrome', hideTextBelow, textSize }: BrandWordmarkProps) {
    return <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', ...scaled(size, 'gap', 0.3, (px) => `${Math.round(px)}px`), minWidth: 0 }}>
        <BrandLogo size={size} decorative />
        <Typography component="span" data-wordmark sx={{ fontFamily: brandFontStack, fontWeight: 800, ...(textSize ? { fontSize: textSize } : scaled(size, 'fontSize', 0.625, (px) => `${px / 16}rem`)),
            letterSpacing: '-0.035em', lineHeight: 1, fontSynthesis: 'none', fontKerning: 'normal',
            // Optical alignment: Poppins sits slightly high in its line box, so drop it to the mark's centre.
            transform: 'translateY(0.04em)',
            color: tone === 'onChrome' ? 'common.white' : 'text.primary', whiteSpace: 'nowrap',
            ...(hideTextBelow ? { [`@media (max-width:${hideTextBelow - 0.05}px)`]: { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' } } : {}) }}>
            CodeMastery
        </Typography>
    </Box>;
}
