// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { ThemeProvider } from '@mui/material';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { appTheme } from '../../app/theme/theme';
import { SidebarNav } from './SidebarNav';
import { LoadingView, EmptyView, ErrorView } from './StatusViews';
import { ConfirmDialog } from './ConfirmDialog';
import { PageHeader } from './PageHeader';
import { AppHeader } from './AppHeader';
import { BrandLogo } from './BrandLogo';
import { AuthLayout } from '../../layouts/AuthLayout';
import { DashboardLayout } from '../../layouts/DashboardLayout';

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
        await render(<><SidebarNav items={[]} mobileOpen={false} onSelect={vi.fn()} onMobileClose={vi.fn()} />
            <AuthLayout title="Welcome back"><p>Caller content</p></AuthLayout></>);
        const sidebar = host.querySelector('.MuiDrawer-docked nav')!;
        expect(sidebar.querySelector('img')?.getAttribute('src')).toBe('/favicon.svg');
        expect(sidebar.querySelector('img')?.alt).toBe(''); expect(sidebar.textContent).toContain('CodeMastery');
        expect(host.querySelector('img[alt="CodeMastery"]')?.getAttribute('src')).toBe('/favicon.svg');
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
    it('exposes controlled current navigation and wraps zero badges', async () => {
        const onSelect = vi.fn(), onClose = vi.fn();
        const props = { items, mobileOpen: false, onSelect, onMobileClose: onClose };
        await render(<SidebarNav {...props} selectedId="a" />);
        const nav = document.querySelector('.MuiDrawer-docked nav')!;
        expect(nav.getAttribute('aria-label')).toBe('Main navigation');
        expect(nav.querySelectorAll('[aria-current="true"]')).toHaveLength(1);
        const alpha = nav.querySelector('[aria-current="true"]')!;
        expect(Array.from(alpha.children).some((child) => child.textContent === '0')).toBe(true);
        const beta = [...nav.querySelectorAll('[role="button"]')].find((node) => node.textContent?.includes('Beta'))!;
        await click(beta); expect(onSelect).toHaveBeenCalledExactlyOnceWith('b'); expect(onClose).toHaveBeenCalledTimes(1);
        expect(alpha.getAttribute('aria-current')).toBe('true');
        await render(<SidebarNav {...props} selectedId="b" />);
        expect(nav.querySelector('[aria-current="true"]')?.textContent).toContain('Beta');
        await render(<SidebarNav {...props} selectedId="missing" />);
        expect(nav.querySelector('[aria-current]')).toBeNull();
    });
    it('accepts empty navigation without creating current state', async () => {
        await render(<SidebarNav items={[]} mobileOpen={false} onSelect={vi.fn()} onMobileClose={vi.fn()} />);
        expect(document.querySelector('[aria-current]')).toBeNull();
    });
    it('preserves caller-owned header search and account actions with the mobile toggle', async () => {
        const toggle = vi.fn();
        await render(<AppHeader title="Learning workspace" menuOpen={false} onMenuToggle={toggle}
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
            expect(document.activeElement).toBe(host.querySelector('.MuiDrawer-docked [aria-current="true"]'));
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
