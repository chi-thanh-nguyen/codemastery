import { Box, Typography } from '@mui/material';

export interface BrandLogoProps { size?: number; decorative?: boolean }

/** The favicon is the canonical artwork. Size controls height; SVG proportions stay intrinsic. */
export function BrandLogo({ size = 34, decorative = false }: BrandLogoProps) {
    // Reserve the canonical viewBox ratio before the SVG decodes; no artwork is duplicated.
    return <Box component="img" src={`${import.meta.env.BASE_URL}favicon.svg`}
        alt={decorative ? '' : 'CodeMastery'} aria-hidden={decorative ? true : undefined}
        height={size} draggable={false}
        sx={{ display: 'block', height: size, width: 'auto', aspectRatio: '1 / 1', objectFit: 'contain', flexShrink: 0 }} />;
}

export interface BrandWordmarkProps { size?: number; tone?: 'onChrome' | 'onLight'; hideTextBelow?: number }
/** Logo mark plus the "CodeMastery" wordmark; the mark is decorative because the text names it. */
export function BrandWordmark({ size = 32, tone = 'onChrome', hideTextBelow }: BrandWordmarkProps) {
    return <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
        <BrandLogo size={size} decorative />
        <Typography component="span" sx={{ fontSize: '1.25rem', fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1,
            color: tone === 'onChrome' ? 'common.white' : 'text.primary', whiteSpace: 'nowrap',
            ...(hideTextBelow ? { [`@media (max-width:${hideTextBelow - 0.05}px)`]: { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' } } : {}) }}>
            CodeMastery
        </Typography>
    </Box>;
}
