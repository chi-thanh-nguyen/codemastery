// @vitest-environment jsdom
import { StrictMode, act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebounce } from './useDebounce';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let host: HTMLDivElement;
let root: Root;
beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => {
    await act(async () => root.unmount()); host.remove(); vi.clearAllTimers(); vi.useRealTimers();
});

async function mount<T>(initial: T, delay: number, strict = false) {
    let current = initial;
    function Probe({ value, wait }: { value: T; wait: number }) { current = useDebounce(value, wait); return null; }
    async function render(value: T, wait = delay) {
        await act(async () => root.render(strict ? <StrictMode><Probe value={value} wait={wait} /></StrictMode> : <Probe value={value} wait={wait} />));
    }
    await render(initial);
    return { value: () => current, render, advance: async (ms: number) => { await act(async () => vi.advanceTimersByTime(ms)); } };
}

describe('useDebounce with actual React DOM lifecycle', () => {
    it.each([false, true])('stores functions without calling them (StrictMode=%s)', async (strict) => {
        const first = vi.fn(() => 'first'), next = vi.fn(() => 'next');
        const hook = await mount(first, 50, strict);
        expect(hook.value()).toBe(first); expect(first).not.toHaveBeenCalled();
        await hook.render(next); await hook.advance(49); expect(hook.value()).toBe(first);
        await hook.advance(1); expect(hook.value()).toBe(next);
        expect(first).not.toHaveBeenCalled(); expect(next).not.toHaveBeenCalled();
    });
    it('retains primitives until the requested delay', async () => {
        const hook = await mount('a', 50); await hook.render('b');
        await hook.advance(49); expect(hook.value()).toBe('a');
        await hook.advance(1); expect(hook.value()).toBe('b');
    });
    it('cancels obsolete rapid updates', async () => {
        const hook = await mount('a', 50); await hook.render('b'); await hook.advance(30);
        await hook.render('c'); await hook.advance(20); expect(hook.value()).toBe('a');
        await hook.advance(30); expect(hook.value()).toBe('c');
    });
    it('reschedules when delay changes', async () => {
        const hook = await mount('a', 100); await hook.render('b'); await hook.advance(40);
        await hook.render('b', 20); await hook.advance(19); expect(hook.value()).toBe('a');
        await hook.advance(1); expect(hook.value()).toBe('b');
    });
    it('does not reschedule unchanged dependencies', async () => {
        const hook = await mount('a', 50); await hook.render('b'); await hook.advance(30);
        await hook.render('b'); await hook.advance(20); expect(hook.value()).toBe('b');
    });
    it('preserves object and array identity', async () => {
        const first: object = { id: 1 }, next = [1, 2]; const hook = await mount(first, 10);
        await hook.render(next); expect(hook.value()).toBe(first); await hook.advance(10); expect(hook.value()).toBe(next);
    });
    it('keeps zero-delay updates asynchronous', async () => {
        const hook = await mount('a', 0); await hook.render('b'); expect(hook.value()).toBe('a');
        await hook.advance(0); expect(hook.value()).toBe('b');
    });
    it('cancels the timer on unmount', async () => {
        const hook = await mount('a', 50); await hook.render('b');
        await act(async () => root.render(null)); expect(vi.getTimerCount()).toBe(0);
        await hook.advance(100); expect(hook.value()).toBe('a');
    });
});
