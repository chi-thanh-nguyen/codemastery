// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { ThemeProvider } from '@mui/material';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { appTheme, brandFontStack, codeFontStack, fontStack } from '../../app/theme/theme';
import { SidebarNav } from './SidebarNav';
import { LoadingView, EmptyView, ErrorView } from './StatusViews';
import { ConfirmDialog } from './ConfirmDialog';
import { PageHeader } from './PageHeader';
import { AppHeader, canvasReachPath } from './AppHeader';
import { BrandLogo, BrandWordmark } from './BrandLogo';
import { CodeBlock } from './CodeBlock';
import { AuthLayout } from '../../layouts/AuthLayout';
import { DashboardLayout, Page } from '../../layouts/DashboardLayout';
import { StatusPill, MasteryTag, MasteryLegend, RecommendationTag } from './StatusPill';
import { DataTable, PaginationBar } from './DataTable';
import { SegmentedControl } from './ViewRail';
import { PathSpine } from './PathSpine';
import { GuidanceStack } from './GuidanceStack';
import { SearchPill } from './PageHeader';
import { ProgressLine } from './Tile';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
async function render(node: ReactNode) { await act(async () => root.render(<ThemeProvider theme={appTheme}>{node}</ThemeProvider>)); }
async function click(node: Element | null) { expect(node).not.toBeNull(); await act(async () => node!.dispatchEvent(new MouseEvent('click', { bubbles: true }))); }

describe('FE01 presentation contracts', () => {
    const items = [{ id: 'a', label: 'Alpha', badge: 0 }, { id: 'b', label: 'Beta', badge: 'New' }, { id: 'c', label: 'Gamma', badge: '' }];
    it('uses canonical artwork with a name or decorative semantics and caller-sized height', async () => {
        await render(<><BrandLogo /><BrandLogo size={44} decorative /></>);
        const [named, decorative] = host.querySelectorAll('img');
        expect(named.getAttribute('src')).toBe('/favicon.svg'); expect(named.alt).toBe('CodeMastery');
        expect(named.getAttribute('height')).toBe('34'); expect(named.hasAttribute('aria-hidden')).toBe(false);
        expect(decorative.getAttribute('src')).toBe(named.getAttribute('src')); expect(decorative.alt).toBe('');
        expect(decorative.getAttribute('height')).toBe('44'); expect(decorative.getAttribute('aria-hidden')).toBe('true');
    });
    it('shares canonical artwork between navigation and authentication without duplicating the wordmark name', async () => {
        await render(<><AppHeader navItems={[]} />
            <AuthLayout title="Welcome back"><p>Caller content</p></AuthLayout></>);
        const header = host.querySelector('header')!;
        expect(header.querySelector('img')?.getAttribute('src')).toBe('/favicon.svg');
        expect(header.querySelector('img')?.alt).toBe(''); expect(header.textContent).toContain('CodeMastery');
        // Both surfaces use the same mark and wordmark component; the mark is decorative beside the text.
        expect([...host.querySelectorAll('img')].map((img) => [img.getAttribute('src'), img.alt])).toEqual([['/favicon.svg', ''], ['/favicon.svg', '']]);
        expect([...host.querySelectorAll('[data-wordmark]')].map((node) => node.textContent)).toEqual(['CodeMastery', 'CodeMastery']);
        expect(host.querySelector('a[aria-label="CodeMastery home"]')).not.toBeNull();
        expect(host.textContent).toContain('Caller content'); expect(host.querySelector('h1')?.textContent).toBe('Welcome back');
    });
    it.each(['input', 'textarea'])('blurs a connected Auth layout %s before unmount', async (tag) => {
        await render(<AuthLayout>{tag === 'input' ? <input defaultValue="Local value" /> : <textarea defaultValue="Local value" />}</AuthLayout>);
        const input = host.querySelector<HTMLInputElement | HTMLTextAreaElement>(tag)!;
        const connected: boolean[] = [];
        input.addEventListener('blur', () => connected.push(input.isConnected));
        await act(async () => input.focus()); await render(null);
        expect(connected).toEqual([true]); expect(input.value).toBe('Local value');
    });
    it('preserves Auth input focus across rerenders and releases only its own pagehide listener', async () => {
        const external = document.createElement('input'); document.body.append(external);
        const add = vi.spyOn(window, 'addEventListener'), remove = vi.spyOn(window, 'removeEventListener');
        try {
            await render(<StrictMode><AuthLayout title="First"><input defaultValue="Local value" /></AuthLayout></StrictMode>);
            const input = host.querySelector('input')!;
            await act(async () => input.focus());
            await render(<StrictMode><AuthLayout title="Second"><input defaultValue="Local value" /></AuthLayout></StrictMode>);
            expect(document.activeElement).toBe(input);
            const pagehide = add.mock.calls.filter(([type]) => type === 'pagehide').at(-1)?.[1];
            expect(typeof pagehide).toBe('function');
            await act(async () => window.dispatchEvent(new Event('pagehide')));
            expect(document.activeElement).not.toBe(input); expect(input.value).toBe('Local value');
            await act(async () => external.focus()); await render(null);
            expect(document.activeElement).toBe(external);
            expect(remove).toHaveBeenCalledWith('pagehide', pagehide);
        } finally { external.remove(); add.mockRestore(); remove.mockRestore(); }
    });
    it('exposes controlled current navigation in the top bar and the mobile drawer, and wraps zero badges', async () => {
        const onSelect = vi.fn(), onClose = vi.fn();
        await render(<AppHeader navItems={items} selectedNavId="a" onNavSelect={onSelect} />);
        const top = host.querySelector('header nav[aria-label="Main navigation"]')!;
        expect(top.querySelectorAll('[aria-current="page"]')).toHaveLength(1);
        expect(top.querySelector('[aria-current="page"]')?.textContent).toContain('Alpha');
        await click([...top.querySelectorAll('button')].find((node) => node.textContent?.includes('Beta'))!);
        expect(onSelect).toHaveBeenCalledExactlyOnceWith('b');
        await render(<AppHeader navItems={items} selectedNavId="missing" onNavSelect={onSelect} />);
        expect(host.querySelector('header [aria-current]')).toBeNull();
        onSelect.mockClear();
        const props = { items, onSelect, onMobileClose: onClose };
        await render(<SidebarNav {...props} mobileOpen selectedId="a" />);
        const nav = document.querySelector('.MuiDrawer-modal nav')!;
        expect(nav.getAttribute('aria-label')).toBe('Main navigation');
        expect(nav.querySelectorAll('[aria-current="page"]')).toHaveLength(1);
        const alpha = nav.querySelector('[aria-current="page"]')!;
        expect(Array.from(alpha.children).some((child) => child.textContent === '0')).toBe(true);
        const beta = [...nav.querySelectorAll('[role="button"]')].find((node) => node.textContent?.includes('Beta'))!;
        await click(beta); expect(onSelect).toHaveBeenCalledExactlyOnceWith('b'); expect(onClose).toHaveBeenCalledTimes(1);
        await render(<SidebarNav {...props} mobileOpen selectedId="b" />);
        expect(document.querySelector('.MuiDrawer-modal nav [aria-current="page"]')?.textContent).toContain('Beta');
        await render(<SidebarNav {...props} mobileOpen selectedId="missing" />);
        expect(document.querySelector('.MuiDrawer-modal nav [aria-current]')).toBeNull();
    });
    it('accepts empty navigation without creating current state', async () => {
        await render(<SidebarNav items={[]} mobileOpen={false} onSelect={vi.fn()} onMobileClose={vi.fn()} />);
        expect(document.querySelector('[aria-current]')).toBeNull();
    });
    it('preserves caller-owned header search and account actions with the mobile toggle', async () => {
        const toggle = vi.fn();
        await render(<AppHeader menuOpen={false} onMenuToggle={toggle}
            search={<input aria-label="Caller search" defaultValue="variables" />} actions={<a href="/profile">Profile</a>} />);
        expect(host.querySelector<HTMLInputElement>('[aria-label="Caller search"]')?.value).toBe('variables');
        expect(host.querySelector('a')?.getAttribute('href')).toBe('/profile');
        await click(host.querySelector('[aria-label="Toggle navigation"]')); expect(toggle).toHaveBeenCalledTimes(1);
        expect(host.querySelector('[aria-label="Toggle navigation"]')?.getAttribute('aria-expanded')).toBe('false');
    });
    it('associates unique visible status text with each indeterminate indicator', async () => {
        await render(<><LoadingView message="Loading sample lessons" /><LoadingView message=" " /></>);
        const bars = [...host.querySelectorAll('[role="progressbar"]')];
        expect(bars).toHaveLength(2);
        const ids = bars.map((bar) => bar.getAttribute('aria-labelledby'));
        expect(new Set(ids).size).toBe(2);
        expect(document.getElementById(ids[0]!)?.textContent).toBe('Loading sample lessons');
        expect(document.getElementById(ids[1]!)?.textContent).toBe('Loading...');
        expect(host.querySelectorAll('[role="status"]')).toHaveLength(2);
        bars.forEach((bar) => { expect(bar.hasAttribute('aria-valuenow')).toBe(false); expect(bar.hasAttribute('aria-live')).toBe(false); });
    });
    it('updates the loading name without replacing its association', async () => {
        await render(<LoadingView message="First" />); const id = host.querySelector('[role="progressbar"]')!.getAttribute('aria-labelledby');
        await render(<LoadingView message="Second" />);
        expect(host.querySelector('[role="progressbar"]')!.getAttribute('aria-labelledby')).toBe(id);
        expect(document.getElementById(id!)?.textContent).toBe('Second');
    });
    it('preserves empty/error supplied content, actions and optional retry', async () => {
        const retry = vi.fn();
        await render(<><EmptyView title="No samples" message="Choose a fixture" action={<button>Browse samples</button>} />
            <ErrorView title="Preview failed" message="Sample error" onRetry={retry} retryLabel="Try preview" /></>);
        expect(host.textContent).toContain('No samples'); expect(host.textContent).toContain('Choose a fixture');
        await click([...host.querySelectorAll('button')].find((button) => button.textContent === 'Try preview')!);
        expect(retry).toHaveBeenCalledTimes(1);
        await render(<ErrorView />); expect(host.querySelector('button')).toBeNull();
    });
    it('preserves supplied page actions and dialog callbacks', async () => {
        const confirm = vi.fn(), cancel = vi.fn();
        await render(<><PageHeader title="Preview" subtitle="Sample only" actions={<button>Action</button>} />
            <ConfirmDialog open title="Reset preview?" message="Local samples only" onConfirm={confirm} onCancel={cancel} /></>);
        expect(host.textContent).toContain('Sample only');
        const dialog = document.querySelector('[role="dialog"]')!;
        expect(dialog).not.toBeNull();
        await click([...dialog.querySelectorAll('button')].find((button) => button.textContent === 'Confirm')!);
        await click([...dialog.querySelectorAll('button')].find((button) => button.textContent === 'Cancel')!);
        expect(confirm).toHaveBeenCalledTimes(1); expect(cancel).toHaveBeenCalledTimes(1);
    });
    it('names/describes the dialog and focuses cancel after destructive-dialog entry', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        try {
            await render(<StrictMode><ConfirmDialog open title="Discard preview?" message="Only local inputs change" isDestructive onConfirm={vi.fn()} onCancel={vi.fn()} /></StrictMode>);
            expect(document.activeElement?.textContent).toBe('Cancel');
            await act(async () => vi.advanceTimersByTime(300));
            const dialog = document.querySelector('[role="dialog"]')!;
            expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.textContent).toBe('Discard preview?');
            expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.textContent).toBe('Only local inputs change');
            expect(document.activeElement?.textContent).toBe('Cancel');
        } finally { vi.useRealTimers(); }
    });
    it('advances dialog-surface focus while preserving an action chosen before the focus microtask', async () => {
        await render(<StrictMode><ConfirmDialog open title="Discard preview?" message="Local inputs only" isDestructive onConfirm={vi.fn()} onCancel={vi.fn()} /></StrictMode>);
        const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
        const actions = dialog.querySelectorAll('button');
        await act(async () => { dialog.focus(); actions[1].focus(); });
        expect(document.activeElement).toBe(actions[1]);
        await act(async () => dialog.focus());
        expect(document.activeElement).toBe(actions[0]);
    });
    it('keeps a user-selected dialog action focused when the entry transition completes', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        try {
            await render(<ConfirmDialog open title="Discard preview?" message="Local inputs only" isDestructive onConfirm={vi.fn()} onCancel={vi.fn()} />);
            const actions = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')];
            expect(document.activeElement).toBe(actions[0]);
            await act(async () => actions[1].focus());
            await act(async () => vi.advanceTimersByTime(300));
            expect(document.activeElement).toBe(actions[1]);
        } finally { vi.useRealTimers(); }
    });
    it('releases the dialog and scroll lock without an authored exit delay under reduced motion', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        vi.stubGlobal('matchMedia', (media: string) => ({ matches: true, media, onchange: null,
            addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: () => false }));
        try {
            const props = { title: 'Preview', message: 'Local only', onConfirm: vi.fn(), onCancel: vi.fn() };
            await render(<ConfirmDialog {...props} open />);
            await act(async () => vi.advanceTimersByTime(1));
            expect(document.querySelector('[role="dialog"]')).not.toBeNull();
            await render(<ConfirmDialog {...props} open={false} />);
            await act(async () => vi.advanceTimersByTime(1));
            expect(document.querySelector('[role="dialog"]')).toBeNull();
            expect(document.body.style.overflow).toBe('');
        } finally { vi.unstubAllGlobals(); vi.useRealTimers(); }
    });
    it('removes the mobile modal and restores visible focus when crossing the desktop breakpoint', async () => {
        let desktop = false;
        const listeners = new Set<() => void>();
        vi.stubGlobal('matchMedia', (media: string) => ({ get matches() { return media.includes('min-width:900px') && desktop; }, media,
            addEventListener: (_: string, listener: () => void) => listeners.add(listener),
            removeEventListener: (_: string, listener: () => void) => listeners.delete(listener) }));
        try {
            const onSelect = vi.fn();
            await render(<DashboardLayout navItems={items} selectedNavId="a" onNavSelect={onSelect}><PageHeader title="Preview" /></DashboardLayout>);
            await click(host.querySelector('[aria-label="Toggle navigation"]'));
            expect(document.querySelector('.MuiDrawer-modal')).not.toBeNull(); expect(document.body.style.overflow).toBe('hidden');
            await act(async () => { desktop = true; [...listeners].forEach((notify) => notify()); });
            expect(document.querySelector('.MuiDrawer-modal')).toBeNull(); expect(document.body.style.overflow).toBe('');
            expect(document.activeElement).toBe(host.querySelector('header nav [aria-current="page"]'));
            expect(onSelect).not.toHaveBeenCalled();
        } finally { vi.unstubAllGlobals(); }
    });
    it('reopens reduced-motion navigation with working focus and Escape, without retaining a closed modal', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        vi.stubGlobal('matchMedia', (media: string) => ({ matches: media.includes('prefers-reduced-motion'), media,
            addEventListener: vi.fn(), removeEventListener: vi.fn() }));
        try {
            const onClose = vi.fn();
            const nav = (open: boolean) => <SidebarNav items={items} selectedId="a" mobileOpen={open} onSelect={vi.fn()} onMobileClose={onClose} />;
            await render(nav(false)); expect(document.querySelector('.MuiDrawer-modal')).toBeNull();
            for (let attempt = 1; attempt <= 2; attempt++) {
                await render(nav(true)); await act(async () => vi.advanceTimersByTime(1));
                const paper = document.querySelector<HTMLElement>('.MuiDrawer-modal .MuiDrawer-paper')!;
                expect(paper.contains(document.activeElement)).toBe(true);
                await act(async () => paper.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
                expect(onClose).toHaveBeenCalledTimes(attempt);
                await render(nav(false)); await act(async () => vi.advanceTimersByTime(1));
                expect(document.querySelector('.MuiDrawer-modal')).toBeNull(); expect(document.body.style.overflow).toBe('');
            }
        } finally { vi.unstubAllGlobals(); vi.useRealTimers(); }
    });
});

describe('Lit Path design-system primitives', () => {
    it('never communicates state by colour alone: pills carry text and a glyph', async () => {
        await render(<><StatusPill tone="mint" icon="check-circle">Completed</StatusPill><RecommendationTag type="skip" /></>);
        expect(host.textContent).toContain('Completed'); expect(host.textContent).toContain('Test-out');
        expect(host.querySelectorAll('svg')).toHaveLength(2);
    });
    it('exposes exactly the four approved mastery states with their meanings and no numbers', async () => {
        await render(<><MasteryLegend /><MasteryTag state="weak" /></>);
        expect([...host.querySelectorAll('[aria-label="Mastery states"] li')].map((li) => li.textContent)).toEqual([
            'UnknownNot assessed yet.', 'LearningSome quiz evidence, not yet mastered.', "MasteredQuiz evidence shows you've mastered this skill.", 'WeakQuiz evidence shows a gap to review.']);
        expect(host.textContent).not.toMatch(/\d/);
    });
    it('renders a captioned table with a row header and switches to stacked label/value rows below 600 px', async () => {
        const props = { label: 'People', rows: [{ id: '1', name: 'Ana', role: 'Learner' }], getRowId: (row: { id: string }) => row.id,
            columns: [{ key: 'name', header: 'Name', primary: true, render: (row: { name: string }) => row.name }, { key: 'role', header: 'Role', render: (row: { role: string }) => row.role }] };
        await render(<DataTable {...props} />);
        expect(host.querySelector('table caption')?.textContent).toBe('People'); expect(host.querySelector('tbody th[scope="row"]')?.textContent).toBe('Ana');
        vi.stubGlobal('matchMedia', (media: string) => ({ matches: media.includes('max-width'), media, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
        try {
            await render(<DataTable {...props} />);
            expect(host.querySelector('table')).toBeNull(); expect(host.querySelector('ul[aria-label="People"] dt')?.textContent).toBe('Role');
        } finally { vi.unstubAllGlobals(); }
    });
    it('turns the confirmation into a full-screen sheet below 600 px with Cancel still focused first', async () => {
        const props = { title: 'Leave course?', message: 'Your learning history will be preserved.', confirmLabel: 'Leave course', isDestructive: true, onConfirm: vi.fn(), onCancel: vi.fn() };
        await render(<ConfirmDialog {...props} open />);
        expect(document.querySelector('.MuiDialog-paperFullScreen')).toBeNull();
        await render(<ConfirmDialog {...props} open={false} />);
        vi.stubGlobal('matchMedia', (media: string) => ({ matches: media.includes('max-width:599.95px'), media, onchange: null,
            addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: () => false }));
        try {
            await render(<ConfirmDialog {...props} open />);
            const sheet = document.querySelector<HTMLElement>('[role="dialog"].MuiDialog-paperFullScreen');
            expect(sheet).not.toBeNull(); expect(document.activeElement?.textContent).toBe('Cancel');
            expect([...sheet!.querySelectorAll('button')].map((button) => button.textContent)).toEqual(['Cancel', 'Leave course']);
        } finally { vi.unstubAllGlobals(); }
    });
    it('clears a search pill with Escape or its clear button and keeps focus in the field', async () => {
        const clear = vi.fn(), change = vi.fn();
        await render(<SearchPill label="Search users" value="Linh" onChange={change} onClear={clear} />);
        const input = host.querySelector<HTMLInputElement>('input[aria-label="Search users"]')!;
        await act(async () => input.focus());
        await act(async () => input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })));
        expect(clear).toHaveBeenCalledTimes(1); expect(document.activeElement).toBe(input);
        const button = host.querySelector<HTMLButtonElement>('button[aria-label="Clear search users"]')!;
        await act(async () => { button.focus(); button.click(); });
        expect(clear).toHaveBeenCalledTimes(2); expect(document.activeElement).toBe(input);
        await render(<SearchPill label="Search users" value="" onChange={change} onClear={clear} />);
        await act(async () => input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })));
        expect(clear).toHaveBeenCalledTimes(2); expect(host.querySelector('button[aria-label="Clear search users"]')).toBeNull();
    });
    it('draws the active-tab reach as one symmetric bell that leaves the edge tangentially', () => {
        const d = canvasReachPath(88, 14);
        const segments = [...d.matchAll(/C([\d. -]+)/g)].map((m) => m[1].trim().split(/\s+/).map(Number));
        const start = d.match(/^M([\d.]+) ([\d.]+)/)!.slice(1).map(Number);
        const ends = [start, ...segments.map((c) => [c[4], c[5]])];
        expect(start).toEqual([0, 14]); expect(ends.at(-1)).toEqual([88, 14]);
        expect(Math.min(...ends.map(([, y]) => y))).toBe(0); expect(ends.find(([, y]) => y === 0)![0]).toBe(44);
        // Mirror symmetry: every end point has its reflection about x = 44.
        for (const [x, y] of ends) expect(ends.some(([mx, my]) => Math.abs(mx - (88 - x)) < 0.002 && Math.abs(my - y) < 0.002)).toBe(true);
        // Horizontal tangents where it meets the straight edge, and a monotonic rise to the crest.
        expect(segments[0][1]).toBe(14); expect(segments.at(-1)![3]).toBe(14);
        const half = ends.slice(0, ends.length / 2 + 1).map(([, y]) => y);
        expect(half.every((y, i) => i === 0 || y <= half[i - 1])).toBe(true);
        expect(d.endsWith('V15 H0 Z')).toBe(true);
    });
    it('keeps one UI face, a dedicated code face and a wordmark face in the theme', async () => {
        expect(fontStack.split(',')[0]).toBe('"Google Sans Flex"'); expect(codeFontStack.split(',')[0]).toBe('"JetBrains Mono"');
        expect(brandFontStack.split(',')[0]).toBe('"CodeMastery Wordmark"');
        for (const stack of [fontStack, codeFontStack, brandFontStack]) expect(stack).not.toMatch(/Figtree|Jakarta|Inter|Neue Montreal|Suisse|Be Vietnam/);
        expect(appTheme.typography.fontFamily).toBe(fontStack);
        for (const variant of ['h1', 'body1', 'button', 'caption'] as const) expect(appTheme.typography[variant].fontFamily).toBe(fontStack);
        expect(appTheme.typography.code.fontFamily).toBe(codeFontStack);
        await render(<><BrandWordmark size={32} /><CodeBlock code="print('xin chào')" /></>);
        const wordmark = host.querySelector<HTMLElement>('[data-wordmark]')!;
        expect(wordmark.textContent).toBe('CodeMastery'); expect(getComputedStyle(wordmark).fontWeight).toBe('800');
        expect(getComputedStyle(wordmark).fontFamily).toContain('CodeMastery Wordmark');
        expect(getComputedStyle(host.querySelector('pre')!).fontFamily).toContain('JetBrains Mono');
    });
    it('shows one-based ranges over zero-based pages and the singular noun', async () => {
        const change = vi.fn();
        await render(<PaginationBar page={1} totalPages={3} totalItems={45} pageSize={20} itemsOnPage={20} onChange={change} noun="courses" />);
        expect(host.textContent).toContain('Showing 21–40 of 45 courses');
        await click(host.querySelector('[aria-label="Go to page 3"]')); expect(change).toHaveBeenCalledWith(2);
        await render(<PaginationBar page={0} totalPages={1} totalItems={1} pageSize={20} itemsOnPage={1} onChange={change} noun="courses" nounOne="course" />);
        expect(host.textContent).toContain('Showing 1 of 1 course');
    });
    it('uses a pressed-state toggle group for segmented filters', async () => {
        const change = vi.fn();
        await render(<SegmentedControl label="Status" value="a" onChange={change} options={[{ value: 'a', label: 'All' }, { value: 'b', label: 'Left', count: 2 }]} />);
        const group = host.querySelector('[role="group"][aria-label="Status"]')!;
        const [all, left] = [...group.querySelectorAll('button')];
        expect(all.getAttribute('aria-pressed')).toBe('true'); expect(left.textContent).toBe('Left2');
        await click(left); expect(change).toHaveBeenCalledWith('b');
        await click(all); expect(change).toHaveBeenCalledTimes(1);
    });
    it('draws canonical order as ordered lists and keeps node state decorative', async () => {
        await render(<PathSpine listLabel={(section) => `Lessons in ${section.title}`} sections={[{ id: 'c', title: 'Chapter', items: [{ id: '1', node: 'done', content: <p>First</p> }, { id: '2', node: 'current', content: <p>Second</p> }] }]} />);
        const list = host.querySelector('ol[aria-label="Lessons in Chapter"]')!;
        expect([...list.children].map((li) => li.textContent)).toEqual(['First', 'Second']);
        expect([...list.children].every((li) => li.firstElementChild?.getAttribute('aria-hidden') === 'true' || li.children[1]?.getAttribute('aria-hidden') === 'true')).toBe(true);
    });
    it('ends a short guidance stack with a dashed "nothing else" slot and runs entry actions', async () => {
        const open = vi.fn();
        await render(<GuidanceStack title="Up next" entries={[{ id: 'r', tone: 'lilac', icon: 'route', type: 'Recommended', body: <p>Review loops</p>, action: { label: 'Open lesson', onClick: open } }]} />);
        expect(host.querySelector('h2')?.textContent).toBe('Up next'); expect(host.textContent).toContain('Nothing else needs your attention.');
        await click([...host.querySelectorAll('a')].find((a) => a.textContent === 'Open lesson')!); expect(open).toHaveBeenCalledTimes(1);
    });
    it('gives the search pill an accessible name and a clear action, and progress a text value', async () => {
        const clear = vi.fn();
        await render(<><SearchPill label="Search courses" value="loops" onChange={vi.fn()} onClear={clear} maxLength={100} /><ProgressLine completed={4} total={12} percent={33} /></>);
        expect(host.querySelector<HTMLInputElement>('input[aria-label="Search courses"]')?.maxLength).toBe(100);
        await click(host.querySelector('[aria-label="Clear search courses"]')); expect(clear).toHaveBeenCalledTimes(1);
        expect(host.querySelector('[role="progressbar"]')?.getAttribute('aria-valuetext')).toBe('4 of 12 lessons · 33%');
    });
    it('renders the page aside as a labelled complementary region', async () => {
        await render(<Page aside={<p>Aside content</p>} asideLabel="Up next"><h1>Title</h1></Page>);
        expect(host.querySelector('aside[aria-label="Up next"]')?.textContent).toBe('Aside content');
    });
});
