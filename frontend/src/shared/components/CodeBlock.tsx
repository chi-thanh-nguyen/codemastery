import { useState } from 'react';
import { Box, ButtonBase, Tooltip, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import { codeFontStack, color, radius } from '../../app/theme/theme';

/** Code panel (DESIGN.md › Code and Markdown content). Text only; nothing is executed. */
export function CodeBlock({ code, caption }: { code: string; caption?: string }) {
    const [copied, setCopied] = useState(false);
    return <Box component="figure" sx={{ m: 0, my: 3 }}>
        <Box sx={{ position: 'relative', bgcolor: color.ink950, borderRadius: `${radius.sm}px`, color: color.fill150 }}>
            <Box component="pre" tabIndex={0} aria-label={caption ? `Code: ${caption}` : 'Code sample'} sx={{ m: 0, p: 2.5, pr: 7, overflowX: 'auto', fontFamily: codeFontStack, fontSize: '0.9375rem', lineHeight: 1.6,
                '&:focus-visible': { outline: `2px solid ${color.focusOnChrome}`, outlineOffset: 2 } }}><code>{code}</code></Box>
            <Tooltip title={copied ? 'Copied' : 'Copy code'}>
                <ButtonBase aria-label={copied ? 'Code copied' : 'Copy code'} onClick={() => { void navigator.clipboard?.writeText(code).then(() => setCopied(true), () => setCopied(false)); }}
                    onBlur={() => setCopied(false)} sx={{ position: 'absolute', top: 8, right: 8, width: 40, height: 40, borderRadius: '50%', color: color.surface, '&:hover': { bgcolor: color.chromeRaised },
                        '&.Mui-focusVisible': { outline: `2px solid ${color.focusOnChrome}` } }}>
                    <PresentationIcon name={copied ? 'check' : 'copy'} sx={{ fontSize: 18 }} />
                </ButtonBase>
            </Tooltip>
            <span role="status" className="cm-visually-hidden">{copied ? 'Code copied to clipboard' : ''}</span>
        </Box>
        {caption && <Typography component="figcaption" variant="caption" sx={{ display: 'block', mt: 1, color: 'text.secondary' }}>{caption}</Typography>}
    </Box>;
}

