import { Box } from '@mui/material';

export interface BrandLogoProps { size?: number; decorative?: boolean }

/** The favicon is the canonical artwork. Size controls height; SVG proportions stay intrinsic. */
export function BrandLogo({ size = 34, decorative = false }: BrandLogoProps) {
    // Reserve the canonical viewBox ratio before the SVG decodes; no artwork is duplicated.
    return <Box component="img" src={`${import.meta.env.BASE_URL}favicon.svg`}
        alt={decorative ? '' : 'CodeMastery'} aria-hidden={decorative ? true : undefined}
        height={size} draggable={false}
        sx={{ display: 'block', height: size, width: 'auto', aspectRatio: '702 / 760', objectFit: 'contain', flexShrink: 0 }} />;
}
