import { Box, Typography } from '@mui/material';
import { brandFontStack } from '../../app/theme/theme';

export interface BrandLogoProps { size?: number; decorative?: boolean }

/** The favicon is the canonical artwork. Size controls height; SVG proportions stay intrinsic. */
export function BrandLogo({ size = 34, decorative = false }: BrandLogoProps) {
    // Reserve the canonical viewBox ratio before the SVG decodes; no artwork is duplicated.
    return <Box component="img" src={`${import.meta.env.BASE_URL}favicon.svg`}
        alt={decorative ? '' : 'CodeMastery'} aria-hidden={decorative ? true : undefined}
        height={size} draggable={false}
        sx={{ display: 'block', height: size, width: 'auto', aspectRatio: '1 / 1', objectFit: 'contain', flexShrink: 0 }} />;
}

export interface BrandWordmarkProps { size?: number; tone?: 'onChrome' | 'onLight'; hideTextBelow?: number; textSize?: string }
/**
 * Logo mark plus the "CodeMastery" wordmark; the mark is decorative because the text names it.
 * The wordmark is the only text set in the brand face (Poppins ExtraBold 800, see theme `brandFontStack`).
 */
export function BrandWordmark({ size = 32, tone = 'onChrome', hideTextBelow, textSize }: BrandWordmarkProps) {
    return <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: `${Math.round(size * 0.3)}px`, minWidth: 0 }}>
        <BrandLogo size={size} decorative />
        <Typography component="span" data-wordmark sx={{ fontFamily: brandFontStack, fontWeight: 800, fontSize: textSize ?? `${(size * 0.625) / 16}rem`,
            letterSpacing: '-0.035em', lineHeight: 1, fontSynthesis: 'none', fontKerning: 'normal',
            // Optical alignment: Poppins sits slightly high in its line box, so drop it to the mark's centre.
            transform: 'translateY(0.04em)',
            color: tone === 'onChrome' ? 'common.white' : 'text.primary', whiteSpace: 'nowrap',
            ...(hideTextBelow ? { [`@media (max-width:${hideTextBelow - 0.05}px)`]: { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' } } : {}) }}>
            CodeMastery
        </Typography>
    </Box>;
}
