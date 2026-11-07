// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { Button, ThemeProvider } from '@mui/material';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { appTheme, brandFontStack, codeFontStack, color, control, fontStack, pathLine, presentationTokens, shadow } from '../../app/theme/theme';
import { SidebarNav, isPlainClick } from './SidebarNav';
import { LoadingView, EmptyView, ErrorView } from './StatusViews';
import { ConfirmDialog } from './ConfirmDialog';
import { PageHeader } from './PageHeader';
import { AccountMenu, AppHeader, HeaderIconButton, Monogram, REACH_MASK, canvasReachPath } from './AppHeader';
import { BrandLogo, BrandWordmark } from './BrandLogo';
import { CodeBlock } from './CodeBlock';
import { AuthLayout } from '../../layouts/AuthLayout';
import { DashboardLayout, Page, canvasOutlinePath } from '../../layouts/DashboardLayout';
import { StatusPill, MasteryTag, MasteryLegend, RecommendationTag } from './StatusPill';
import { DataTable, PaginationBar } from './DataTable';
import { SegmentedControl } from './ViewRail';
import { PathSpine } from './PathSpine';
import { GuidanceStack } from './GuidanceStack';
import { SearchPill } from './PageHeader';
import { CircleAction, CircleAffordance, CourseTile, ProgressLine } from './Tile';
import { COURSE_LOGOS, CourseMark, courseLanguage } from './CourseMark';
import { LearnerHomePage } from '../../features/learning/pages/LearnerHomePage';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
/** Deep Logo Blue (brand-700) as the browser serialises it. */
const BRAND_700 = 'rgb(6, 82, 196)';
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
    it('routes plain clicks in-app but keeps native links for modified clicks or a missing handler', async () => {
        const items = [{ id: 'catalogue', label: 'Catalogue', href: '/courses' }];
        const fire = (link: Element, init: MouseEventInit = {}) => { const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...init }); link.dispatchEvent(event); return event.defaultPrevented; };
        const select = vi.fn();
        await render(<AppHeader navItems={items} onNavSelect={select} />);
        let link = host.querySelector('header nav a[href="/courses"]')!;
        expect(fire(link)).toBe(true); expect(select).toHaveBeenCalledWith('catalogue');
        select.mockClear();
        for (const modifier of [{ metaKey: true }, { ctrlKey: true }, { shiftKey: true }, { altKey: true }]) {
            expect(fire(link, modifier), JSON.stringify(modifier)).toBe(false);
        }
        expect(select).not.toHaveBeenCalled();
        // Without a handler the link is an ordinary link: the browser follows its href.
        await render(<AppHeader navItems={items} />);
        link = host.querySelector('header nav a[href="/courses"]')!;
        expect(fire(link)).toBe(false);
        expect(isPlainClick({ button: 1, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false, defaultPrevented: false })).toBe(false);
        expect(isPlainClick({ button: 0, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false, defaultPrevented: true })).toBe(false);
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
    it('names each loading state in its own status line with a content-shaped skeleton and no spinner', async () => {
        await render(<><LoadingView message="Loading sample lessons" variant="tiles" count={2} /><LoadingView message=" " /></>);
        const statuses = [...host.querySelectorAll<HTMLElement>('[role="status"]')];
        expect(statuses.map((node) => node.textContent)).toEqual(['Loading sample lessons', 'Loading...']);
        expect(new Set(statuses.map((node) => node.id)).size).toBe(2);
        // DESIGN.md › Feedback: skeletons, not spinners, carry loading feedback.
        expect(host.querySelector('[role="progressbar"], .MuiCircularProgress-root')).toBeNull();
        expect(host.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
        statuses.forEach((node) => expect(node.closest('[aria-hidden="true"]')).toBeNull());
    });
    it('updates the loading name in place, keeping the same status element', async () => {
        await render(<LoadingView message="First" />); const status = host.querySelector('[role="status"]')!; const id = status.id;
        await render(<LoadingView message="Second" />);
        expect(host.querySelector('[role="status"]')).toBe(status); expect(status.id).toBe(id); expect(status.textContent).toBe('Second');
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
    it('cuts the reach out of the header with a mask built from the bell path, painting nothing', () => {
        const svg = decodeURIComponent(REACH_MASK.match(/^url\("data:image\/svg\+xml,(.+)"\)$/)![1]);
        expect(svg).toContain('viewBox="0 0 88 15"'); expect(svg).toContain(`d="${canvasReachPath(88, 14)}"`);
        expect(svg).not.toMatch(/fill="#|fill="white|fill="rgb/);
    });
    it('outlines the canvas so it reaches under the bar and its visible corners start at the bar edge', () => {
        const d = canvasOutlinePath(1264, 900, 14, 32, 32);
        // A band under the bar (inset by the corner radius), then corners that begin at y = rise.
        expect(d.startsWith('M32 0 H1232 V14 A32 32 0 0 1 1264 46')).toBe(true);
        expect(d).toContain('V868 A32 32 0 0 1 1232 900 H32 A32 32 0 0 1 0 868 V46 A32 32 0 0 1 32 14 Z');
        // Phones: 24 px top corners, square bottom.
        expect(canvasOutlinePath(390, 600, 14, 24, 0)).toBe('M24 0 H366 V14 A24 24 0 0 1 390 38 V600 L390 600 H0 L0 600 V38 A24 24 0 0 1 24 14 Z');
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
    it('scales the header lockup as one unit: mark, gap and wordmark from one size', async () => {
        expect(presentationTokens.brandMark).toEqual({ narrow: 32, xs: 36, sm: 40 });
        await render(<><AppHeader navItems={[]} /><BrandWordmark size={40} /></>);
        // The header mark reserves the larger (>= 600 px) size; height stays the only sizing input.
        expect(host.querySelector('header img')?.getAttribute('height')).toBe('40');
        const [, plain] = host.querySelectorAll<HTMLElement>('[data-wordmark]');
        expect(getComputedStyle(plain).fontSize).toBe('1.5625rem'); // 0.625 × 40 px
        const img = host.querySelectorAll('img')[1];
        expect(img.getAttribute('height')).toBe('40'); expect(getComputedStyle(img).width).toBe('auto');
        expect(getComputedStyle(plain.parentElement!).gap).toBe('12px'); // 0.3 × 40 px
    });
    it('reads a course language only from a single language named by the course itself', () => {
        expect(courseLanguage({ title: 'Python Fundamentals' })).toBe('python');
        expect(courseLanguage({ title: 'Introduction to Programming', description: 'Variables and loops in Python.' })).toBe('python');
        expect(courseLanguage({ title: 'TypeScript Basics', description: 'Not Python.' })).toBe('typescript');
        // Nothing named, several named, or a language without an approved logo: no logo is guessed.
        expect(courseLanguage({ title: 'Introduction to Programming', description: 'Demo course for the Interim catalogue' })).toBeNull();
        expect(courseLanguage({ title: 'Computational Thinking', description: null })).toBeNull();
        expect(courseLanguage({ title: 'Python and Java side by side' })).toBeNull();
        expect(courseLanguage({ title: 'JavaScript Essentials' })).toBeNull();
        expect(courseLanguage({ title: 'C++ for Python programmers' })).toBeNull();
        expect(courseLanguage({ title: 'Pythonic patterns' })).toBeNull();
        expect(courseLanguage({ title: 'Course 01', description: 'Practice course number 1 for catalogue paging.' })).toBeNull();
    });
    it('shows every course mark on one neutral container: the official logo, otherwise a Deep Logo Blue code glyph; never letters, ink or a gradient', async () => {
        await render(<><CourseTile title="Python Fundamentals" description="Variables and loops." href="#" onOpen={() => undefined} />
            <CourseTile title="Introduction to Programming" description="Demo course for the Interim catalogue" href="#" onOpen={() => undefined} /></>);
        const [python, generic] = host.querySelectorAll<HTMLElement>('[data-course-mark]');
        expect(python.dataset.courseMark).toBe('python'); expect(python.getAttribute('aria-hidden')).toBe('true');
        const logo = python.querySelector('img')!;
        expect(logo.getAttribute('src')).toBe(COURSE_LOGOS.python.src); expect(logo.alt).toBe('');
        expect(generic.dataset.courseMark).toBe('generic'); expect(generic.getAttribute('aria-hidden')).toBe('true');
        expect(generic.textContent).toBe(''); expect(generic.querySelector('img')).toBeNull(); expect(generic.querySelector('svg')).not.toBeNull();
        expect(getComputedStyle(generic).color).toBe(BRAND_700);
        for (const mark of [python, generic]) {
            expect(getComputedStyle(mark).backgroundColor).toBe('rgb(246, 246, 247)'); // canvas-100
            expect(getComputedStyle(mark).boxShadow).toBe('inset 0 0 0 1px #E4E4E7');
            expect(getComputedStyle(mark).backgroundImage).not.toContain('gradient');
            expect(getComputedStyle(mark).backgroundColor).not.toBe('rgb(10, 11, 15)'); // never ink-950
            expect(getComputedStyle(mark).width).toBe('48px'); expect(getComputedStyle(mark).height).toBe('48px');
            expect(getComputedStyle(mark).borderRadius).toBe('16px');
        }
        // The tile's accessible name stays the course title; the mark adds no text or image name.
        expect(host.querySelector('a[data-tile-link]')?.textContent).toBe('Python Fundamentals');
    });
    it('gives the signed-in account avatar solid Deep Logo Blue with a white initial; other people stay on ink', async () => {
        await render(<><AppHeader navItems={[]} actions={<AccountMenu displayName="Linh Pham" role="Learner" renderItems={() => []} />} /><Monogram name="Q&A Author" tone="ink" /></>);
        const account = host.querySelector<HTMLElement>('header [data-monogram]')!;
        expect(account.dataset.monogram).toBe('brand'); expect(account.textContent).toBe('L');
        expect(getComputedStyle(account).backgroundColor).toBe(BRAND_700); expect(getComputedStyle(account).backgroundImage).not.toContain('gradient');
        expect(getComputedStyle(account).color).toBe('rgb(255, 255, 255)');
        const other = host.querySelector<HTMLElement>('[data-monogram="ink"]')!;
        expect(getComputedStyle(other).backgroundColor).toBe('rgb(10, 11, 15)');
        // White text on brand-700 stays above 4.5:1 (6.97:1).
        const lum = (hex: string) => { const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
        expect(1.05 / (lum(color.brand700) + 0.05)).toBeGreaterThan(4.5);
    });
    it('sizes each logo optically: the visible artwork fills its own share of the container, centred, unstretched', async () => {
        await render(<><CourseMark title="Python Fundamentals" /><CourseMark title="TypeScript Basics" /><CourseMark title="Python Fundamentals" size={32} /></>);
        for (const mark of host.querySelectorAll<HTMLElement>('[data-course-mark]')) {
            const spec = COURSE_LOGOS[mark.dataset.courseMark as keyof typeof COURSE_LOGOS];
            const size = parseFloat(getComputedStyle(mark).width), img = mark.querySelector('img')!, style = getComputedStyle(img);
            const width = parseFloat(style.width), height = parseFloat(style.height), left = parseFloat(style.left), top = parseFloat(style.top);
            expect(width / height).toBeCloseTo(spec.aspect, 6);
            const art = { w: spec.art.w * width, h: spec.art.h * height };
            expect(Math.max(art.w, art.h)).toBeCloseTo(spec.scale * size, 6);
            expect(left + (spec.art.x + spec.art.w / 2) * width).toBeCloseTo(size / 2, 6);
            expect(top + (spec.art.y + spec.art.h / 2) * height).toBeCloseTo(size / 2, 6);
            expect(top + height).toBeLessThanOrEqual(size); // Python's soft shadow stays inside the container.
        }
        // A solid square is set smaller than an open mark so the two read at the same size.
        expect(COURSE_LOGOS.typescript.scale).toBeLessThan(COURSE_LOGOS.python.scale);
    });
    it('draws the path line as a 4/6 dash, solid only after a completed item', async () => {
        expect(pathLine).toEqual({ width: 2, dash: 4, gap: 6, dasharray: '4 6' });
        await render(<PathSpine sections={[{ id: 's', items: [{ id: 'a', node: 'done', content: 'A' }, { id: 'b', node: 'current', content: 'B' }, { id: 'c', node: 'todo', content: 'C' }] }]} />);
        const lines = [...host.querySelectorAll<HTMLElement>('[data-path-line]')];
        expect(lines.map((line) => line.dataset.pathLine)).toEqual(['solid', 'dashed', 'dashed']);
        const dashed = getComputedStyle(lines[1]).backgroundImage;
        expect(dashed).toContain('repeating-linear-gradient'); expect(dashed).toContain('4px'); expect(dashed).toContain('10px');
        expect(getComputedStyle(lines[1]).width).toBe('2px');
    });
    it('keeps shadows and type in the shared tokens: segmented control shadow and the display step', () => {
        const toggle = appTheme.components?.MuiToggleButton?.styleOverrides?.root as Record<string, Record<string, unknown>>;
        expect(toggle['&.Mui-selected, &.Mui-selected:hover'].boxShadow).toBe(shadow.control);
        expect(appTheme.typography.display).toMatchObject({ fontSize: '3.5rem', fontWeight: 600, '@media (max-width:599.95px)': { fontSize: '2.5rem' } });
        expect(color.chromeHover).toBe('#2A2D35'); expect(color.rose800).toBe('#951C13');
    });
    it('marks a header utility as current for the page it opens', async () => {
        await render(<><HeaderIconButton label="Notifications" icon="bell" badge={3} current /><HeaderIconButton label="Help" icon="info" /></>);
        const [bell, other] = host.querySelectorAll('button');
        expect(bell.getAttribute('aria-current')).toBe('page'); expect(bell.getAttribute('aria-label')).toBe('Notifications, 3 unread');
        expect(getComputedStyle(bell).backgroundColor).toBe('rgb(28, 30, 36)'); // chrome-raised
        expect(other.hasAttribute('aria-current')).toBe(false);
    });
    it('gives each Learner home course row the same course mark as its tile', async () => {
        await render(<LearnerHomePage learnerName="Linh" onBrowse={() => undefined} courses={[
            { id: 'a', title: 'Python Fundamentals', onOpen: () => undefined, detail: 'Instructor · A' },
            { id: 'b', title: 'Introduction to Programming', description: 'Demo course', onOpen: () => undefined, detail: 'Instructor · B' }]} />);
        const marks = [...host.querySelectorAll<HTMLElement>('ul[aria-label="Your active courses"] [data-course-mark]')];
        expect(marks.map((mark) => mark.dataset.courseMark)).toEqual(['python', 'generic']);
        for (const mark of marks) { expect(getComputedStyle(mark).width).toBe('32px'); expect(getComputedStyle(mark).backgroundColor).toBe('rgb(246, 246, 247)'); }
    });
    it('ranks buttons: graphite primary, neutral outlined secondary, Logo Blue tertiary, red destructive', async () => {
        expect(control.primary).toBe(color.chromeRaised); expect(control.primaryHover).toBe(color.chromeHover);
        await render(<><Button variant="contained">Save</Button><Button variant="outlined">Cancel</Button><Button>Skip</Button>
            <Button variant="contained" color="error">Delete</Button><Button variant="contained" disabled>Off</Button><Button variant="outlined" disabled>Off 2</Button>
            <Button variant="contained" color="onChrome">Create account</Button><Button variant="outlined" color="onChrome">Browse</Button></>);
        const [primary, secondary, tertiary, danger, primaryOff, secondaryOff, chromePrimary, chromeSecondary] = [...host.querySelectorAll('button')].map((node) => getComputedStyle(node));
        // MUI sets contained label colours through a custom property; jsdom does not resolve var().
        const label = (style: CSSStyleDeclaration) => style.color.startsWith('var(') ? style.getPropertyValue('--variant-containedColor').toUpperCase() : style.color;
        expect(primary.backgroundColor).toBe('rgb(28, 30, 36)'); expect(label(primary)).toBe('#FFFFFF'); // graphite, not the ink frame
        expect(secondary.backgroundColor).toBe('rgb(255, 255, 255)'); expect(secondary.borderStyle).toBe('solid'); expect(secondary.borderColor).toBe('rgb(228, 228, 231)'); expect(secondary.color).toBe('rgb(21, 23, 28)');
        expect(['rgba(0, 0, 0, 0)', 'transparent', '']).toContain(tertiary.backgroundColor.startsWith('var(') ? tertiary.getPropertyValue('--variant-textBg') : tertiary.backgroundColor); expect(tertiary.color).toBe('rgb(8, 102, 242)'); expect(tertiary.borderStyle).not.toBe('solid');
        expect(danger.backgroundColor).toBe('rgb(180, 35, 24)');
        expect(primaryOff.backgroundColor).toBe('rgb(238, 238, 240)'); expect(primaryOff.color).toBe('rgb(107, 112, 121)');
        expect(secondaryOff.backgroundColor).toBe('rgb(255, 255, 255)'); expect(secondaryOff.color).toBe('rgb(107, 112, 121)');
        expect(chromePrimary.backgroundColor).toBe('rgb(255, 255, 255)'); expect(['rgb(10, 11, 15)', '#0A0B0F']).toContain(label(chromePrimary));
        expect(chromeSecondary.backgroundColor).toBe('rgb(28, 30, 36)'); expect(chromeSecondary.color).toBe('rgb(255, 255, 255)');
        for (const style of [primary, secondary, danger]) expect(style.minHeight).toBe('48px');
    });
    it('uses the same roles for circular actions: graphite primary circle, outlined secondary circle', async () => {
        await render(<><CircleAction label="Open" icon="arrow-right" variant="ink" /><CircleAction label="More" icon="more" /><CircleAffordance /><CircleAffordance icon="play" emphasis="primary" /></>);
        const [primary, secondary] = [...host.querySelectorAll('button')].map((node) => getComputedStyle(node));
        expect(primary.backgroundColor).toBe('rgb(28, 30, 36)');
        expect(secondary.backgroundColor).toBe('rgb(255, 255, 255)'); expect(secondary.borderColor).toBe('rgb(228, 228, 231)');
        // Tile arrows: neutral outlined with a graphite icon by default; graphite filled only for Resume/Continue.
        const [open, resume] = [...host.querySelectorAll<HTMLElement>('[data-affordance]')];
        expect(open.dataset.affordance).toBe('secondary'); expect(getComputedStyle(open).backgroundColor).toBe('rgb(255, 255, 255)');
        expect(getComputedStyle(open).borderColor).toBe('rgb(228, 228, 231)'); expect(getComputedStyle(open).color).toBe('rgb(28, 30, 36)');
        expect(resume.dataset.affordance).toBe('primary'); expect(getComputedStyle(resume).backgroundColor).toBe('rgb(28, 30, 36)');
    });
    it('keeps an error block recovery secondary so it never competes with the page primary', async () => {
        await render(<ErrorView title="Couldn't load" message="Try again." onRetry={() => undefined} />);
        const retry = host.querySelector('button')!;
        expect(retry.textContent).toBe('Try again'); expect(retry.className).toContain('MuiButton-outlined');
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
