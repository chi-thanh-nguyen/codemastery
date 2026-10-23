// @vitest-environment jsdom
import { act } from 'react';
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

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
async function render(node: ReactNode) { await act(async () => root.render(<ThemeProvider theme={appTheme}>{node}</ThemeProvider>)); }
async function click(node: Element | null) { expect(node).not.toBeNull(); await act(async () => node!.dispatchEvent(new MouseEvent('click', { bubbles: true }))); }

describe('FE01 presentation contracts', () => {
    const items = [{ id: 'a', label: 'Alpha', badge: 0 }, { id: 'b', label: 'Beta', badge: 'New' }, { id: 'c', label: 'Gamma', badge: '' }];
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
});
