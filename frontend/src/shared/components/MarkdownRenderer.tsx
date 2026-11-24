import React, { Fragment } from 'react';
import {
    Box,
    Link,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import { CodeBlock } from './CodeBlock';
import { codeFontStack, radius, roles } from '../../app/theme/theme';

export interface MarkdownRendererProps {
    markdown: string;
}

/**
 * CT37.markdown (CT37-MARKDOWN-2026-11-08-r1):
 * Renders Markdown to React elements without external dependencies or raw HTML interpretation.
 * Only https/http, single-slash root-relative paths (not //), and # fragments become links.
 */
function isSafeHref(rawUrl: string): string | null {
    const trimmed = rawUrl.trim();
    if (!trimmed) return null;

    let decoded = trimmed;
    try {
        decoded = decodeURIComponent(trimmed);
    } catch {
        return null;
    }

    if (/[\u0000-\u001f\u007f]/.test(decoded)) return null;

    if (decoded.startsWith('#')) return trimmed;
    if (decoded.startsWith('/') && !decoded.startsWith('//') && !decoded.startsWith('/\\')) {
        return trimmed;
    }

    const lower = decoded.toLowerCase();
    if (lower.startsWith('https://') || lower.startsWith('http://')) {
        return trimmed;
    }

    return null;
}

function renderInline(text: string): React.ReactNode[] {
    const nodes: React.ReactNode[] = [];
    const tokenRegex = /(`[^`]+`|!\[[^\]]*\]\([^)]+\)|\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*|\*[^*]+\*)/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let key = 0;

    while ((match = tokenRegex.exec(text)) !== null) {
        if (match.index > lastIndex) {
            nodes.push(text.slice(lastIndex, match.index));
        }

        const token = match[0];
        key += 1;

        if (token.startsWith('`') && token.endsWith('`')) {
            nodes.push(
                <Box
                    key={`c-${key}`}
                    component="code"
                    sx={{
                        fontFamily: codeFontStack,
                        fontSize: '0.875em',
                        bgcolor: roles.bg.fill,
                        color: roles.text.primary,
                        px: 0.75,
                        py: 0.25,
                        borderRadius: '4px',
                    }}
                >
                    {token.slice(1, -1)}
                </Box>
            );
        } else if (token.startsWith('![')) {
            const imgMatch = /^!\[([^\]]*)\]\(([^)]+)\)$/.exec(token);
            if (imgMatch) {
                const alt = imgMatch[1] || 'Image';
                const safeUrl = isSafeHref(imgMatch[2]);
                if (safeUrl) {
                    nodes.push(
                        <Link
                            key={`img-${key}`}
                            href={safeUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            sx={{ color: roles.accent.link }}
                        >
                            {alt}
                        </Link>
                    );
                } else {
                    nodes.push(alt);
                }
            } else {
                nodes.push(token);
            }
        } else if (token.startsWith('[')) {
            const linkMatch = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
            if (linkMatch) {
                const label = linkMatch[1];
                const safeUrl = isSafeHref(linkMatch[2]);
                if (safeUrl) {
                    const isExternal = safeUrl.toLowerCase().startsWith('http');
                    nodes.push(
                        <Link
                            key={`a-${key}`}
                            href={safeUrl}
                            target={isExternal ? '_blank' : undefined}
                            rel={isExternal ? 'noopener noreferrer' : undefined}
                            sx={{ color: roles.accent.link }}
                        >
                            {label}
                        </Link>
                    );
                } else {
                    nodes.push(label);
                }
            } else {
                nodes.push(token);
            }
        } else if (token.startsWith('**') && token.endsWith('**')) {
            nodes.push(<strong key={`b-${key}`}>{renderInline(token.slice(2, -2))}</strong>);
        } else if (token.startsWith('*') && token.endsWith('*')) {
            nodes.push(<em key={`i-${key}`}>{renderInline(token.slice(1, -1))}</em>);
        } else {
            nodes.push(token);
        }

        lastIndex = tokenRegex.lastIndex;
    }

    if (lastIndex < text.length) {
        nodes.push(text.slice(lastIndex));
    }

    return nodes;
}

export function MarkdownRenderer({ markdown }: MarkdownRendererProps) {
    const lines = markdown.replace(/\r\n/g, '\n').split('\n');
    const blocks: React.ReactNode[] = [];
    let i = 0;
    let blockKey = 0;

    while (i < lines.length) {
        const line = lines[i];

        if (!line.trim()) {
            i += 1;
            continue;
        }

        blockKey += 1;

        if (line.trimStart().startsWith('```')) {
            const codeLines: string[] = [];
            i += 1;
            while (i < lines.length && !lines[i].trimStart().startsWith('```')) {
                codeLines.push(lines[i]);
                i += 1;
            }
            i += 1;
            blocks.push(
                <Box key={`code-${blockKey}`} sx={{ my: 2 }}>
                    <CodeBlock code={codeLines.join('\n')} />
                </Box>
            );
            continue;
        }

        const headingMatch = /^(#{1,4})\s+(.+)$/.exec(line);
        if (headingMatch) {
            const level = headingMatch[1].length;
            const variant = level === 1 ? 'h2' : level === 2 ? 'h3' : 'h4';
            const component = level === 1 ? 'h2' : level === 2 ? 'h3' : 'h4';
            blocks.push(
                <Typography key={`h-${blockKey}`} component={component} variant={variant} sx={{ mt: 3, mb: 1.5 }}>
                    {renderInline(headingMatch[2])}
                </Typography>
            );
            i += 1;
            continue;
        }

        if (line.trimStart().startsWith('>')) {
            const quoteLines: string[] = [];
            while (i < lines.length && lines[i].trimStart().startsWith('>')) {
                quoteLines.push(lines[i].trimStart().replace(/^>\s?/, ''));
                i += 1;
            }
            blocks.push(
                <Box
                    key={`q-${blockKey}`}
                    component="blockquote"
                    sx={{
                        m: 0,
                        my: 2,
                        p: 2,
                        bgcolor: roles.bg.fill,
                        borderRadius: `${radius.sm}px`,
                        color: roles.text.primary,
                    }}
                >
                    <Typography variant="body1" sx={{ lineHeight: 1.7 }}>
                        {renderInline(quoteLines.join(' '))}
                    </Typography>
                </Box>
            );
            continue;
        }

        if (line.trim().startsWith('|') && i + 1 < lines.length && /^\|[\s\-:|]+\|$/.test(lines[i + 1].trim())) {
            const parseRow = (r: string) =>
                r.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
            const headers = parseRow(line);
            i += 2;
            const rows: string[][] = [];
            while (i < lines.length && lines[i].trim().startsWith('|')) {
                rows.push(parseRow(lines[i]));
                i += 1;
            }
            blocks.push(
                <TableContainer key={`tbl-${blockKey}`} sx={{ my: 2.5, bgcolor: roles.bg.surface, borderRadius: `${radius.lg}px` }}>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                {headers.map((h, idx) => (
                                    <TableCell key={idx} sx={{ fontWeight: 600, color: roles.text.secondary }}>
                                        {renderInline(h)}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {rows.map((r, rIdx) => (
                                <TableRow key={rIdx}>
                                    {headers.map((_, cIdx) => (
                                        <TableCell key={cIdx}>{renderInline(r[cIdx] ?? '')}</TableCell>
                                    ))}
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            );
            continue;
        }

        if (/^\s*[-*]\s+/.test(line)) {
            const items: string[] = [];
            while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
                items.push(lines[i].replace(/^\s*[-*]\s+/, ''));
                i += 1;
            }
            blocks.push(
                <Box key={`ul-${blockKey}`} component="ul" sx={{ my: 1.5, pl: 3, display: 'grid', gap: 0.75 }}>
                    {items.map((item, idx) => (
                        <Box key={idx} component="li">
                            <Typography component="span" sx={{ fontSize: '1.0625rem', lineHeight: 1.7 }}>
                                {renderInline(item)}
                            </Typography>
                        </Box>
                    ))}
                </Box>
            );
            continue;
        }

        if (/^\s*\d+\.\s+/.test(line)) {
            const items: string[] = [];
            while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
                items.push(lines[i].replace(/^\s*\d+\.\s+/, ''));
                i += 1;
            }
            blocks.push(
                <Box key={`ol-${blockKey}`} component="ol" sx={{ my: 1.5, pl: 3, display: 'grid', gap: 0.75 }}>
                    {items.map((item, idx) => (
                        <Box key={idx} component="li">
                            <Typography component="span" sx={{ fontSize: '1.0625rem', lineHeight: 1.7 }}>
                                {renderInline(item)}
                            </Typography>
                        </Box>
                    ))}
                </Box>
            );
            continue;
        }

        const paraLines: string[] = [];
        while (
            i < lines.length &&
            lines[i].trim() &&
            !lines[i].trimStart().startsWith('```') &&
            !/^(#{1,4})\s+/.test(lines[i]) &&
            !lines[i].trimStart().startsWith('>') &&
            !/^\s*[-*]\s+/.test(lines[i]) &&
            !/^\s*\d+\.\s+/.test(lines[i])
        ) {
            paraLines.push(lines[i]);
            i += 1;
        }

        blocks.push(
            <Typography key={`p-${blockKey}`} sx={{ fontSize: '1.0625rem', lineHeight: 1.7, color: roles.text.primary, mb: 2, maxWidth: '70ch' }}>
                {paraLines.map((pl, idx) => (
                    <Fragment key={idx}>
                        {idx > 0 && ' '}
                        {renderInline(pl)}
                    </Fragment>
                ))}
            </Typography>
        );
    }

    return <Box>{blocks}</Box>;
}