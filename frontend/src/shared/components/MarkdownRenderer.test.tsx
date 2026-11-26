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

describe('FE14-IR-02 URL fidelity and admission agree on the browser destination', () => {
    it.each([
        'https://example.test/a%2Fb', 'https://example.test/?q=a%26admin%3Dtrue',
        'https://example.test/a%20b', 'https://example.test/?q=%252F', 'https://example.test/100%25',
        'https://example.test/%23path?q=%3F%3D%26#x%2Fy', '/courses/a%2Fb?q=a%20b', '#part%20two',
    ])('preserves the rendered anchor and URL components: %s', target => {
        const anchor = render(`[Go](${target})`).querySelector('a')!;
        expect(anchor).not.toBeNull(); expect(anchor.getAttribute('href')).toBe(target);
        const expected = new URL(target, document.baseURI);
        expect([anchor.protocol, anchor.host, anchor.pathname, anchor.search, anchor.hash])
            .toEqual([expected.protocol, expected.host, expected.pathname, expected.search, expected.hash]);
    });
    it('preserves the query as one value, literal percent, entities and CommonMark escapes', () => {
        const host = render('[query](https://example.test/?q=a%26admin%3Dtrue) [entity](https://example.test/?a=1&amp;b=2) [escape](https://example.test/a\\(b\\))');
        const anchors = [...host.querySelectorAll('a')];
        expect(new URL(anchors[0].href).searchParams.get('q')).toBe('a&admin=true');
        expect(new URL(anchors[0].href).searchParams.has('admin')).toBe(false);
        expect(anchors[1].getAttribute('href')).toBe('https://example.test/?a=1&b=2');
        expect(anchors[2].getAttribute('href')).toBe('https://example.test/a(b)');
    });
    it.each([
        'JaVaScRiPt:evil', 'java&#x09;script:evil', 'jav&#x61;script:evil', '%6Aavascript:evil',
        '%2525256aavascript:evil', '%26%23x6a%3Bavascript:evil', 'java%0Ascript:evil', 'java%20script:evil',
        '//evil.test', '/%2Fevil.test', '/%255cevil.test', '/\\evil.test', 'https://good.test%255c@evil.test',
        'https://good.test%252f@evil.test', '%23part', '%2Fpath', 'relative/path', '/','%ZZ',
        'https://example.test/%E0%A4%A', 'https://example.test/%E0%A4',
    ])('renders rejected targets as text: %s', target => {
        const host = render(`[Go](<${target}>) ![Picture](<${target}>)`);
        expect(host.querySelector('a,img,script')).toBeNull(); expect(host.textContent).toContain('Go');
        expect(host.textContent).toContain('Picture');
    });
    it('retains external attributes for mixed-case safe schemes', () => {
        const anchor = render('[Go](HtTpS://example.test/a%20b)').querySelector('a')!;
        expect(anchor.protocol).toBe('https:'); expect(anchor.getAttribute('rel')).toBe('noopener noreferrer');
    });
});

describe('FE14-IR-03 document-wide definition collection', () => {
    it.each([
        '> [ref]: https://example.test/a%2Fb',
        '- [ref]: https://example.test/a%2Fb',
        '> - [ref]: https://example.test/a%2Fb',
        '- > [ref]: https://example.test/a%2Fb',
    ])('resolves forward/backward links and images in nested definitions: %s', definition => {
        const host = render(`[Before][REF]\n\n${definition}\n\n[After][ref]\n\n![Picture][Ref]`);
        expect([...host.querySelectorAll('a')].map(a => a.getAttribute('href')))
            .toEqual(Array(3).fill('https://example.test/a%2Fb'));
        expect(host.querySelector('img')).toBeNull();
    });
    it('keeps the first definition in document order and normalized identifiers', () => {
        const host = render('[Before][A  B]\n\n> [a b]: https://example.test/first%20path\n\n[a b]: https://example.test/second\n\n![Picture][a b]');
        expect([...host.querySelectorAll('a')].map(a => a.getAttribute('href')))
            .toEqual(Array(2).fill('https://example.test/first%20path'));
        expect(host.querySelector('img')).toBeNull();
    });
    it('does not substitute a safe later duplicate for an unsafe first definition', () => {
        const host = render('> - [ref]: java&#x73;cript:evil\n\n[ref]: https://example.test\n\n[Go][ref]\n\n![Picture][ref]');
        expect(host.querySelector('a,img')).toBeNull(); expect(host.textContent).toContain('Picture');
    });
});


it('keeps external attributes when browser URL parsing removes scheme controls', () => {
    const anchor = render('[Go](<ht&#x09;tps://example.test/path>)').querySelector('a')!;
    expect(anchor.protocol).toBe('https:');
    expect(anchor.getAttribute('rel')).toBe('noopener noreferrer');
});
