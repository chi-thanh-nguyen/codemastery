import { createElement, Fragment, useMemo } from 'react';
import type { ReactNode } from 'react';
import { Box, Link, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { fromMarkdown } from 'mdast-util-from-markdown';
import { gfmTableFromMarkdown } from 'mdast-util-gfm-table';
import { gfmTable } from 'micromark-extension-gfm-table';
import { decodeString } from 'micromark-util-decode-string';
import type { Nodes, Definition } from 'mdast';
import { CodeBlock } from './CodeBlock';
import { codeFontStack, roles } from '../../app/theme/theme';

/** CT37.markdown: inspect decoded spellings, navigate only the parser's original destination. */
export function markdownHref(raw: string): string | null {
    const base = 'https://markdown.invalid';
    const classify = (value: string) => {
        if (!value || value.includes('\\')) return null;
        const url = new URL(value, base);
        if (value[0] === '#') return { kind: 'fragment', url };
        if (value[0] === '/' && value.length > 1 && value[1] !== '/' && url.origin === base) return { kind: 'root', url };
        const absolute = new URL(value);
        return absolute.protocol === 'http:' || absolute.protocol === 'https:' ? { kind: 'absolute', url: absolute } : null;
    };
    try {
        // mdast already applies CommonMark entity/escape handling. Do not decode it again for output.
        // Validate source encoding once. Later layers may contain a legitimate literal percent (100%25).
        decodeURIComponent(raw);
        const navigation = classify(raw);
        if (!navigation) return null;
        let inspected = raw;
        for (let i = 0; i < 8; i++) {
            const decoded = decodeURIComponent(inspected.replace(/%(?![0-9a-f]{2})/gi, '%25'));
            // Preserve A2: escape decoding must never consume a percent-decoded backslash.
            if (decoded.includes('\\')) return null;
            const next = decodeString(decoded).replace(/[\s\u0000-\u001f\u007f-\u009f]/gu, '');
            const inspection = classify(next);
            if (!inspection || inspection.kind !== navigation.kind || inspection.url.protocol !== navigation.url.protocol
                || inspection.url.origin !== navigation.url.origin) return null;
            if (next === inspected) return raw;
            inspected = next;
        }
        return null; // Bounded inspection: unresolved nested obfuscation is not admitted.
    } catch { return null; }
}

/** CommonMark + tables only. HTML nodes are text, images are links, never HTML strings. */
export function MarkdownRenderer({ markdown }: { markdown: string }) {
    const tree = useMemo(() => fromMarkdown(markdown, { extensions: [gfmTable()], mdastExtensions: [gfmTableFromMarkdown()] }), [markdown]);
    const definitions = new Map<string, Definition>();
    // Depth-first document order preserves CommonMark's first-definition precedence. mdast has
    // already normalized identifiers consistently on both definitions and reference nodes.
    const collectDefinitions = (node: Nodes) => {
        if (node.type === 'definition' && !definitions.has(node.identifier)) definitions.set(node.identifier, node);
        if ('children' in node) for (const child of node.children) collectDefinitions(child);
    };
    collectDefinitions(tree);
    const link = (target: string | undefined, content: ReactNode, title?: string | null) => {
        const href = target === undefined ? null : markdownHref(target);
        return href ? <Link href={href} title={title ?? undefined} rel={href[0] !== '/' && href[0] !== '#' ? 'noopener noreferrer' : undefined}>{content}</Link> : content;
    };
    const children = (node: Nodes): ReactNode => 'children' in node ? node.children.map((child, i) => <Fragment key={i}>{render(child)}</Fragment>) : null;
    const render = (node: Nodes): ReactNode => {
        switch (node.type) {
            case 'root': return children(node);
            case 'text': return node.value;
            case 'html': return <Box component="span" sx={{ whiteSpace: 'pre-wrap' }}>{node.value}</Box>;
            case 'paragraph': return <p>{children(node)}</p>;
            case 'heading': return createElement(`h${Math.min(6, node.depth + 1)}`, null, children(node));
            case 'emphasis': return <em>{children(node)}</em>;
            case 'strong': return <strong>{children(node)}</strong>;
            case 'inlineCode': return <Box component="code" sx={{ fontFamily: codeFontStack, bgcolor: roles.bg.fill, px: .5 }}>{node.value}</Box>;
            case 'code': return <CodeBlock code={node.value} caption={node.lang ?? undefined} />;
            case 'blockquote': return <blockquote>{children(node)}</blockquote>;
            case 'list': return node.ordered ? <ol start={node.start ?? undefined}>{children(node)}</ol> : <ul>{children(node)}</ul>;
            case 'listItem': return <li>{children(node)}</li>;
            case 'break': return <br />;
            case 'thematicBreak': return <hr />;
            case 'link': return link(node.url, children(node), node.title);
            case 'image': return link(node.url, node.alt || 'Image', node.title);
            case 'linkReference': { const def = definitions.get(node.identifier); return link(def?.url, children(node), def?.title); }
            case 'imageReference': { const def = definitions.get(node.identifier); return link(def?.url, node.alt || 'Image', def?.title); }
            case 'definition': return null;
            case 'table': return <TableContainer tabIndex={0} role="region" aria-label="Markdown table"><Table>
                <TableHead><TableRow>{node.children[0]?.children.map((cell, i) => <TableCell key={i} align={node.align?.[i] ?? undefined}>{children(cell)}</TableCell>)}</TableRow></TableHead>
                <TableBody>{node.children.slice(1).map((row, i) => <TableRow key={i}>{row.children.map((cell, j) => <TableCell key={j} align={node.align?.[j] ?? undefined}>{children(cell)}</TableCell>)}</TableRow>)}</TableBody>
            </Table></TableContainer>;
            default: return null;
        }
    };
    return <Box sx={{ overflowWrap: 'anywhere', minWidth: 0, color: 'text.primary', '& p': { lineHeight: 1.7 }, '& blockquote': { ml: 2, pl: 2, borderLeft: `1px solid ${roles.line.strong}` } }}>{render(tree)}</Box>;
}
