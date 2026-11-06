import { Box, ButtonBase, Typography } from '@mui/material';
import { CodeBlock } from '../../../shared/components/CodeBlock';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import type { LessonBlock, LessonMaterialView } from '../types/learningTypes';
import { color, radius } from '../../../app/theme/theme';

/**
 * Structured lesson text rendered as plain React text. Lesson Markdown rendering
 * is BLOCKED pending an approved rendering/sanitisation decision (MarkdownRenderer).
 */
export function LessonContent({ blocks }: { blocks: LessonBlock[] }) {
    return <Box sx={{ maxWidth: '70ch', '& p': { m: 0, mb: 2.5 } }}>
        {blocks.map((block, index) => {
            if (block.kind === 'heading') return <Typography key={index} component="h2" variant="h3" sx={{ mt: 5, mb: 2 }}>{block.text}</Typography>;
            if (block.kind === 'list') return <Box key={index} component="ul" sx={{ pl: 3, mb: 2.5, '& li': { mb: 1 } }}>{block.items.map((item) => <Typography key={item} component="li" variant="reading">{item}</Typography>)}</Box>;
            if (block.kind === 'code') return <CodeBlock key={index} code={block.code} caption={block.caption} />;
            if (block.kind === 'note') return <Box key={index} sx={{ bgcolor: color.sky100, borderRadius: `${radius.md}px`, p: 2, mb: 2.5, display: 'flex', gap: 1.5 }}>
                <PresentationIcon name="info" sx={{ color: color.brand700, fontSize: 20, mt: .25 }} /><Typography variant="reading" component="p" sx={{ m: '0 !important' }}>{block.text}</Typography>
            </Box>;
            return <Typography key={index} component="p" variant="reading">{block.text}</Typography>;
        })}
    </Box>;
}

/** Lesson materials: files (CT09 delivery pending) and external video links (embed only; no hosting). */
export function MaterialList({ materials, onDownload }: { materials: LessonMaterialView[]; onDownload?: (id: string) => void }) {
    if (materials.length === 0) return <Typography variant="body2" sx={{ color: 'text.secondary' }}>No extra materials for this lesson.</Typography>;
    return <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
        {materials.map((material) => <Box component="li" key={material.id}>
            <ButtonBase component="a" href={material.kind === 'external_video' ? material.url : '#'} target={material.kind === 'external_video' ? '_blank' : undefined} rel="noreferrer noopener"
                onClick={(event) => { if (material.kind === 'file') { event.preventDefault(); onDownload?.(material.id); } }}
                sx={{ width: '100%', justifyContent: 'flex-start', gap: 1.5, p: 1.5, borderRadius: `${radius.md}px`, bgcolor: color.surface, textAlign: 'left', '&:hover': { bgcolor: color.canvas100 } }}>
                <Box aria-hidden sx={{ width: 40, height: 40, borderRadius: `${radius.sm}px`, bgcolor: color.fill150, display: 'grid', placeItems: 'center', flexShrink: 0 }}>
                    <PresentationIcon name={material.kind === 'file' ? 'file' : 'video'} sx={{ fontSize: 20 }} />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography component="span" sx={{ display: 'block', fontWeight: 600, overflowWrap: 'anywhere' }}>{material.kind === 'file' ? material.name : material.title}</Typography>
                    <Typography component="span" variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{material.kind === 'file' ? material.description : 'External video · opens in a new tab'}</Typography>
                </Box>
                <PresentationIcon name={material.kind === 'file' ? 'download' : 'external'} sx={{ fontSize: 20, color: 'text.secondary' }} />
            </ButtonBase>
        </Box>)}
    </Box>;
}
