// @vitest-environment jsdom
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { MarkdownRenderer, markdownHref } from './MarkdownRenderer';
const render = (markdown: string) => { const host = document.createElement('div'); host.innerHTML = renderToStaticMarkup(<MarkdownRenderer markdown={markdown} />); return host; };
describe('CT37 Markdown AST rendering', () => {
    it('renders CommonMark nesting, setext headings, references, tables and code without extra extensions', () => {
        const host = render('# Title\n\nSubheading\n----------\n\n> **strong *nested***\n\n3. list\n\n[reference][id]\n\n[id]: https://example.com/path\n\n| A | B |\n| - | -: |\n| x | y |\n\n```ts\n<script>literal</script>\n```\n\n~~not an extension~~');
        expect(host.querySelector('h1')).toBeNull(); expect(host.querySelector('h2')?.textContent).toBe('Title'); expect(host.querySelector('h3')?.textContent).toBe('Subheading');
        expect(host.querySelector('strong em')?.textContent).toBe('nested'); expect(host.querySelector('ol')?.start).toBe(3);
        expect(host.querySelector('a')?.getAttribute('rel')).toBe('noopener noreferrer'); expect(host.querySelectorAll('tbody td')).toHaveLength(2);
        expect(host.querySelector('pre code')?.textContent).toBe('<script>literal</script>'); expect(host.querySelector('del')).toBeNull();
    });
    it.each(['<script>alert(1)</script>', '<img src="x" onerror="alert(1)">', '<div onclick="alert(1)">block</div>', 'inline <iframe src="https://x.test"></iframe>'])('renders HTML literally: %s', (source) => {
        const host = render(source); expect(host.querySelector('script,img,iframe,div[onclick]')).toBeNull(); expect(host.textContent).toContain(source.replace('inline ', ''));
    });
    it.each(['javascript:alert(1)', 'data:text/html,evil', 'vbscript:evil', 'file:///tmp/x', '//evil.test/x', '/\\evil.test', '\\evil.test', 'jav&#x61;script:evil', '%6Aavascript:evil', 'java%0Ascript:evil', '%252f%252fevil.test', '%26%23x2f%3B%26%23x2f%3Bevil.test', 'https://good.test%5c@evil.test', 'relative/path', '/'])('rejects unsafe or unapproved target %s', (source) => {
        expect(markdownHref(source)).toBeNull();
    });
    it.each(['https://example.com/x', 'HTTP://example.com/x', '/courses/x', '#part'])('admits approved target %s', (source) => { expect(markdownHref(source)).not.toBeNull(); });
    it('never loads images; safe images become links and unsafe ones become alt text', () => {
        const host = render('![safe](https://example.com/a.png) ![bad](data:image/svg+xml,evil) [bad](jav&#x61;script:evil)');
        expect(host.querySelector('img')).toBeNull(); expect(host.querySelectorAll('a')).toHaveLength(1); expect(host.querySelector('a')?.textContent).toBe('safe'); expect(host.textContent).toContain('bad');
    });
    it('does not interpret event attributes, entity or percent encoded link schemes', () => {
        const host = render('[x](java&#x09;script:evil) [y](%6Aavascript:evil) ![z](data:text/html,evil)\n\n<div onmouseover="evil()">raw</div>');
        expect(host.querySelector('a,img,[onmouseover]')).toBeNull(); expect(host.textContent).toContain('<div onmouseover="evil()">raw</div>');
    });
});
