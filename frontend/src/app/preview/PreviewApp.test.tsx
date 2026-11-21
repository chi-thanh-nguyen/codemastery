// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement, fetcher: ReturnType<typeof vi.fn>;
const userId = '00000000-0000-4000-8000-000000000001';
const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
function loginResponse(name: string) {
    const iat = Math.floor(Date.now() / 1000);
    return { accessToken: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: userId, iss: 'codemastery', aud: 'codemastery-api', iat, exp: iat + 900 })}.${'A'.repeat(43)}`, tokenType: 'Bearer', expiresIn: 900, user: { id: userId, displayName: name, role: 'Learner' } };
}
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
async function typeInto(input: HTMLInputElement, value: string) {
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
}

class ResizeObserverStub { observe() {} unobserve() {} disconnect() {} }
class DOMMatrixStub { m22 = 1; constructor(transform?: string) { const scale = transform?.match(/scale\(([\d.]+)\)/)?.[1]; this.m22 = scale ? Number(scale) : 1; } }

// The preview is a lazily loaded chunk. Its first import (transforming and evaluating every preview screen and the
// graph library) is a one-off cost of the test environment, paid here under the hook timeout rather than inside the
// 5 s timeout of whichever test mounts the preview first; App's lazy import then resolves at once.
beforeAll(async () => { await import('./PreviewApp'); }, 30_000);

beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.stubGlobal('DOMMatrixReadOnly', DOMMatrixStub);
    fetcher = vi.fn(() => Promise.reject(new Error('The preview must not call the network.')));
    vi.stubGlobal('fetch', fetcher);
    // A configured API base, so a stray request reaches fetch (and fails these tests) instead of a configuration error.
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
    localStorage.clear(); sessionStorage.clear();
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => {
    await act(async () => root.unmount()); host.remove();
    window.history.replaceState(null, '', '/'); vi.useRealTimers(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear();
});

/** Mount the real App at /preview and wait for the lazily loaded preview chunk. */
async function mount(search = '') {
    window.history.replaceState(null, '', `/preview${search}`);
    await act(async () => root.render(<App />));
    for (let attempt = 0; attempt < 50 && !host.querySelector('[data-preview-screen]'); attempt++) {
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)); });
    }
    expect(host.querySelector('[data-preview-screen]')).not.toBeNull();
}
async function mountGlobalShell(path = '/login') {
    window.history.replaceState(null, '', path);
    await act(async () => root.render(<App />));
    for (let attempt = 0; attempt < 50 && !host.querySelector('button[aria-label^="Theme:"]'); attempt++) {
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)); });
    }
    expect(host.querySelector('button[aria-label^="Theme:"]')).not.toBeNull();
}
const h1 = () => host.querySelector('main h1')?.textContent;
const buttons = (text: string, scope: ParentNode = host) => [...scope.querySelectorAll<HTMLElement>('button, a, [role="menuitem"]')].filter((node) => node.textContent?.trim() === text);
async function press(text: string, scope: ParentNode = host) { const node = buttons(text, scope)[0]; expect(node, text).toBeTruthy(); await act(async () => node.click()); }
/**
 * Waits (real timers, bounded) for an observable outcome. On timeout it fails with the
 * current route and any visible alert, so a genuine sign-in failure is not reported as
 * an ambiguous route mismatch.
 */
async function waitUntil(done: () => boolean, what: string, timeoutMs = 3000) {
    const deadline = Date.now() + timeoutMs;
    while (!done()) {
        if (Date.now() > deadline) {
            const alert = host.querySelector('[role="alert"]')?.textContent?.trim();
            throw new Error(`Timed out after ${timeoutMs} ms waiting for ${what}; route ${location.pathname}${alert ? `; alert: "${alert}"` : ''}.`);
        }
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 10)); });
    }
}
async function settle(ms: number) { await act(async () => { vi.advanceTimersByTime(ms); }); }

describe('isolated design preview', () => {
    it('persists an explicit global preference and keeps it active when storage writes fail', async () => {
        await mountGlobalShell();
        const trigger = host.querySelector<HTMLButtonElement>('button[aria-label="Theme: System (currently Light)"]')!;
        await act(async () => trigger.click());
        const dark = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((item) => item.textContent?.trim().startsWith('Dark'))!;
        await act(async () => dark.click());
        expect(localStorage.getItem('codemastery.theme')).toBe('dark');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')?.getAttribute('aria-label')).toBe('Theme: Dark');

        const originalSetItem = Storage.prototype.setItem;
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key: string, value: string) {
            if (key === 'codemastery.theme') throw new DOMException('Storage is unavailable.', 'QuotaExceededError');
            return originalSetItem.call(this, key, value);
        });
        const light = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((item) => item.textContent?.trim().startsWith('Light'))!;
        await act(async () => light.click());
        expect(host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')?.getAttribute('aria-label')).toBe('Theme: Light');
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(localStorage.getItem('codemastery.theme')).toBe('dark');
    });

    it('follows global theme changes until locally overridden without writing the preference', async () => {
        localStorage.setItem('codemastery.theme', 'light');
        const writePreference = vi.spyOn(Storage.prototype, 'setItem');
        // A workspace screen, not the index: the index renders the whole design reference (about 1,900 nodes), and
        // re-rendering it on every scheme change made this test the slowest of the suite (it timed out once on CI).
        await mount('?screen=learner-home');
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'codemastery.theme', newValue: 'dark' })));
        expect(document.documentElement.dataset.colorScheme).toBe('dark');

        const navToggle = host.querySelector<HTMLButtonElement>('button[aria-label="Toggle navigation"]')!;
        await act(async () => navToggle.click());
        const light = [...document.querySelectorAll<HTMLElement>('[role="radio"]')].find((item) => item.textContent?.includes('Light'))!;
        await act(async () => light.click());
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(document.querySelector('.MuiDrawer-modal')).not.toBeNull();
        expect(localStorage.getItem('codemastery.theme')).toBe('light');
        expect(writePreference).not.toHaveBeenCalled();

        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'codemastery.theme', newValue: 'dark' })));
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(host.textContent).not.toContain('Theme set to'); // another tab's change is not announced here

        // "Reset sample data" keeps the preview-local scheme.
        await act(async () => { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); });
        const drawerReset = [...document.querySelectorAll<HTMLElement>('button, a, [role="menuitem"]')].find((node) => node.textContent?.trim() === 'Reset sample data');
        if (!drawerReset) {
            await act(async () => host.querySelector<HTMLElement>('button[aria-label^="Account menu"]')!.click());
            await act(async () => [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((node) => node.textContent?.trim() === 'Reset sample data')!.click());
        } else await act(async () => drawerReset.click());
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(fetcher).not.toHaveBeenCalled();
        expect(writePreference).not.toHaveBeenCalled();
        expect(sessionStorage.length).toBe(0);

        // Leaving /preview in the same document drops the override: the shared preference (dark, from the other tab) applies.
        await act(async () => { window.history.pushState(null, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); });
        expect(host.querySelector('[data-preview-screen]')).toBeNull();
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(host.querySelector('button[aria-label^="Theme:"]')?.getAttribute('aria-label')).toBe('Theme: Dark');

        // A reload discards any preview selection and starts from the stored preference.
        writePreference.mockRestore();
        await act(async () => root.unmount());
        root = createRoot(host);
        await mount('?screen=learner-home');
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        await act(async () => host.querySelector<HTMLButtonElement>('button[aria-label="Toggle navigation"]')!.click());
        expect(document.querySelector('[role="radio"][aria-checked="true"]')?.textContent).toBe('Light');
    });

    it('follows other tabs in production shells: a new value applies, a cleared or invalid value means System', async () => {
        await mountGlobalShell();
        const label = () => host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')?.getAttribute('aria-label');
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'codemastery.theme', newValue: 'dark' })));
        expect(label()).toBe('Theme: Dark'); expect(document.documentElement.dataset.colorScheme).toBe('dark');
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'unrelated.key', newValue: 'x' })));
        expect(label()).toBe('Theme: Dark');
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'codemastery.theme', newValue: 'sepia' })));
        expect(label()).toBe('Theme: System (currently Light)'); expect(document.documentElement.dataset.colorScheme).toBe('light');
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: 'codemastery.theme', newValue: 'light' })));
        await act(async () => window.dispatchEvent(new StorageEvent('storage', { key: null, newValue: null })));
        expect(label()).toBe('Theme: System (currently Light)');
        expect(host.textContent).not.toContain('Theme set to'); // only a choice in this tab is announced
    });

    it('tracks operating-system theme changes while System is selected without storing the derived scheme', async () => {
        let matches = false;
        const listeners = new Set<(event: MediaQueryListEvent) => void>();
        const mediaQuery = {
            get matches() { return matches; },
            media: '(prefers-color-scheme: dark)',
            onchange: null,
            addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
            removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
            addListener: (listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
            removeListener: (listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
            dispatchEvent: () => true,
        };
        const staticQuery = (media: string) => ({
            matches: false, media, onchange: null, addEventListener: () => undefined, removeEventListener: () => undefined,
            addListener: () => undefined, removeListener: () => undefined, dispatchEvent: () => true,
        });
        vi.stubGlobal('matchMedia', (media: string) => media === '(prefers-color-scheme: dark)' ? mediaQuery : staticQuery(media));
        await mount();
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        await act(async () => {
            matches = true;
            listeners.forEach((listener) => listener({ matches, media: mediaQuery.media } as MediaQueryListEvent));
        });
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(localStorage.getItem('codemastery.theme')).toBeNull();
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('A13: with /preview open, another tab signing in or out sends no request and installs no session', async () => {
        const tabs: { onmessage: ((event: MessageEvent) => void) | null }[] = [];
        vi.stubGlobal('BroadcastChannel', class { onmessage: ((event: MessageEvent) => void) | null = null; constructor() { tabs.push(this); } postMessage() { /* test double */ } close() { /* test double */ } });
        await mount();
        for (const type of ['signed-in', 'signed-out', 'signed-in', 'signed-in']) {
            for (const tab of tabs) await act(async () => tab.onmessage?.({ data: { type } } as MessageEvent));
        }
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 50)); });
        expect(fetcher).not.toHaveBeenCalled();
        expect(host.querySelector('[data-preview-screen]')).not.toBeNull();
        expect(location.pathname).toBe('/preview');
        expect(localStorage.length + sessionStorage.length).toBe(0);
    });
    it('A13: leaving /preview in the same document and changing the theme still restores nothing', async () => {
        await mount();
        await act(async () => { window.history.pushState(null, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); });
        const theme = host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')!;
        await act(async () => theme.click());
        await act(async () => [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((item) => item.textContent?.startsWith('Dark'))!.click());
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 50)); });
        expect(fetcher).not.toHaveBeenCalled();
        expect(host.textContent).not.toContain('Restoring your session');
    });

    it('lists every workspace on the index and never calls the network or writes storage', async () => {
        await mount();
        expect(h1()).toBe('CodeMastery design preview');
        // Status pills: Live mint, Preview sky, Proposed dashed (lilac stays reserved for adaptive guidance).
        const pill = (text: string) => [...host.querySelectorAll<HTMLElement>('span')].find((node) => node.textContent === text && node.querySelector('span') !== null)!;
        expect(pill('Proposed').dataset.tone).toBe('dashed');
        for (const group of ['Live screens', 'Learner', 'Instructor', 'Administrator', 'Public and account']) expect(host.textContent).toContain(group);
        for (const spec of ['S6', 'S7', 'S8', 'S9', 'S10', 'S11', 'S12', 'S13', 'S14', 'S15', 'S17', 'S18', 'S21', 'S22', 'S23', 'S24', 'S25', 'S26']) expect(host.textContent).toContain(spec);
        expect(fetcher).not.toHaveBeenCalled(); expect(localStorage.length + sessionStorage.length).toBe(0);
    });

    it('shows no preview chrome: screens look as in production and are opened by link', async () => {
        for (const screen of ['overview', 'my-courses', 'admin-overview', 'sign-in', 'quiz-attempt']) {
            await act(async () => root.unmount()); root = createRoot(host);
            await mount(`?screen=${screen}`);
            expect(host.querySelector(`[data-preview-screen="${screen}"]`), screen).not.toBeNull();
            expect(host.querySelector('[aria-label="Design preview notice"]'), screen).toBeNull();
            expect(host.querySelector('[aria-label="Preview workspace"]'), screen).toBeNull();
            expect(host.textContent, screen).not.toContain('Design preview · Sample data.');
            for (const label of ['Reset sample data', 'Preview index', 'Simulate time running out']) expect(buttons(label).length, `${screen}: ${label}`).toBe(0);
            // The reach is cut into the header by its mask; no painted shape fakes the opening.
            expect(host.querySelector('header [data-canvas-reach]'), screen).toBeNull();
        }
    });

    it('navigates with query parameters inside /preview, keeps the parent destination current and focuses the new heading', async () => {
        await mount('?screen=course');
        expect(location.pathname).toBe('/preview'); expect(h1()).toBe('Introduction to Programming');
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('My courses');
        const lesson = [...host.querySelectorAll<HTMLAnchorElement>('a[data-tile-link]')].find((link) => link.textContent === 'For Loops')!;
        await act(async () => lesson.click());
        expect(location.pathname).toBe('/preview'); expect(new URLSearchParams(location.search).get('screen')).toBe('lesson');
        expect(h1()).toBe('For Loops'); expect(document.activeElement).toBe(host.querySelector('main h1'));
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('My courses');
        // US-06: an unmastered prerequisite warns but never blocks the lesson.
        expect(host.textContent).toContain("You haven't mastered Conditionals yet. This lesson builds on it. You can continue anyway.");
        expect(buttons('Mark as complete')).toHaveLength(1);
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('marks a lesson complete in sample state only and keeps mastery wording separate', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        await mount('?screen=lesson&item=l5');
        expect(host.textContent).toContain('Recommended review.');
        await press('Mark as complete'); expect(host.textContent).toContain('Saving…');
        await settle(800);
        expect(host.querySelector('[aria-label="Lesson actions"]')?.textContent).toContain('Lesson completed');
        expect(host.textContent).toContain("Completing a lesson doesn't change skill mastery. Quizzes do.");
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('runs a timed sample quiz: answer, navigate, confirm submit and show a labelled sample result', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        await mount('?screen=quiz-attempt');
        expect(host.querySelector('[role="timer"]')?.textContent).toBe('10:00');
        await settle(3000); expect(host.querySelector('[role="timer"]')?.textContent).toBe('09:57');
        const first = host.querySelector<HTMLInputElement>('input[type="radio"][value="a"]')!;
        await act(async () => first.click());
        expect(host.textContent).toContain('1 of 6 answered');
        await press('Next'); expect(host.textContent).toContain('Select all that apply.');
        await press('Submit quiz');
        const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
        expect(dialog.textContent).toContain('You answered 1 of 6. Unanswered questions score zero.');
        await press('Submit quiz', dialog); await settle(900);
        expect(new URLSearchParams(location.search).get('screen')).toBe('quiz-result');
        expect(h1()).toBe('Loops practice quiz');
        expect(host.textContent).toContain('16.67'); expect(host.textContent).toContain('1/6');
        expect(host.textContent).toContain("CodeMastery's server is the scoring authority");
        expect(fetcher).not.toHaveBeenCalled();
    });

    // One transition per case keeps the real App/controller coverage within the default CI timeout.
    for (const [kind, search] of [
        ['quiz', '?screen=quiz-attempt'],
        ['diagnostic', '?screen=quiz-attempt&item=diagnostic'],
    ] as const) it.each(['Light', 'Dark', 'System'] as const)(`G-01: the ${kind} countdown keeps running through Theme open, %s selection and close without changing answers or sending requests`, async (label) => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        const stored = label === 'Dark' ? 'light' : 'dark';
        localStorage.setItem('codemastery.theme', stored); // Every case selects a different preference.
        await mount(search);
        const firstAnswer = host.querySelector<HTMLInputElement>('main input[type="radio"][value="a"]')!;
        await act(async () => firstAnswer.click());
        expect(firstAnswer.checked).toBe(true);
        const answerState = () => [...host.querySelectorAll<HTMLInputElement>('main input')].map((input) => ({ value: input.value, checked: input.checked, disabled: input.disabled }));
        const answersBefore = answerState();
        const remaining = () => {
            const timer = host.querySelector('[role="timer"]');
            expect(timer, 'the attempt is still answering, with its countdown visible').not.toBeNull();
            return timer!.textContent!.split(':').map(Number).reduce((total, part) => total * 60 + part, 0);
        };
        let expected = remaining();
        const trigger = host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')!;
        const unchanged = () => {
            expect(remaining()).toBe(expected);
            expect(answerState()).toEqual(answersBefore);
            expect(new URLSearchParams(location.search).get('screen')).toBe('quiz-attempt');
            expect(new URLSearchParams(location.search).get('item')).toBe(kind === 'diagnostic' ? 'diagnostic' : null);
            expect(document.querySelector('[role="dialog"]')).toBeNull();
            expect(fetcher).not.toHaveBeenCalled(); // Includes any accidental save or submit request.
            expect(localStorage.getItem('codemastery.theme')).toBe(stored);
        };
        await act(async () => trigger.click());
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
        await settle(2000); expected -= 2; unchanged(); // The real preview controller ticks while the menu stays open.
        const choice = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((node) => node.textContent?.startsWith(label))!;
        await act(async () => choice.click());
        expect(choice.getAttribute('aria-checked')).toBe('true');
        expect(document.activeElement).toBe(choice);
        expect(document.documentElement.dataset.colorScheme).toBe(label === 'Dark' ? 'dark' : 'light');
        await settle(1000); expected--; unchanged();
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
        await act(async () => choice.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(document.querySelector('[role="menu"]')).toBeNull();
        expect(document.activeElement).toBe(trigger);
        await settle(1000); expected--; unchanged();
    });

    it('auto-submits when the sample timer runs out', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        await mount('?screen=quiz-attempt');
        await settle(600_000); // the full 10-minute sample limit
        expect(host.textContent).toContain('Time is up. Submitting your answers…');
        await settle(1500);
        expect(new URLSearchParams(location.search).get('screen')).toBe('quiz-result');
        expect(host.textContent).toContain('0/6');
    });

    it('shows the Mastery Map with exactly four states, the legend and the backend reason verbatim', async () => {
        await mount('?screen=mastery');
        expect(h1()).toBe('Mastery Map');
        for (const state of ['Unknown', 'Learning', 'Mastered', 'Weak']) expect(host.querySelector('[aria-label="Mastery states"]')?.textContent).toContain(state);
        expect(host.textContent).not.toMatch(/mastery\s*%|confidence/i);
        expect(host.textContent).toContain('For loops is Weak and its prerequisite Conditionals is not mastered yet.');
        await act(async () => [...host.querySelectorAll<HTMLButtonElement>('button[aria-pressed]')].find((button) => button.textContent === 'List view')!.click());
        expect(host.querySelector('table[aria-labelledby] caption')?.textContent).toBe('Skills in Introduction to Programming');
        const conditionals = [...host.querySelectorAll<HTMLButtonElement>('tbody button')].find((button) => button.textContent === 'Conditionals')!;
        await act(async () => conditionals.click());
        expect(host.querySelector('aside')?.textContent).toContain('Conditionals');
        expect(host.querySelector('aside')?.textContent).toContain('Some quiz evidence, not yet mastered.');
    });

    it('lets an administrator lock an account through a confirmation and records a sample audit entry; reset restores it', async () => {
        await mount('?screen=admin-users');
        await act(async () => host.querySelector<HTMLButtonElement>('button[aria-label="Actions for Linh Pham"]')!.click());
        await press('Lock account', document.querySelector('[role="menu"]')!);
        const dialogs = [...document.querySelectorAll<HTMLElement>('[role="dialog"]')];
        const dialog = dialogs.at(-1)!;
        expect(dialog.textContent).toContain("They won't be able to sign in");
        expect(document.activeElement?.textContent).toBe('Cancel');
        await press('Lock account', dialog);
        expect([...host.querySelectorAll('tbody tr')].find((row) => row.textContent?.includes('Linh Pham'))?.textContent).toContain('Locked');
        await press('Audit log');
        expect(host.querySelector('tbody tr')?.textContent).toContain('user.lock');
        // Reset stays available from the account menu only.
        await act(async () => host.querySelector<HTMLElement>('button[aria-haspopup="menu"][aria-label^="Account menu"]')!.click());
        await press('Reset sample data', document.querySelector('[role="menu"]')!);
        expect(host.querySelector('tbody tr')?.textContent).not.toContain('Just now');
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('rejects a cyclic prerequisite with the sample server message and leaves the graph unchanged', async () => {
        await mount('?screen=course-editor&item=c-intro&tab=skills');
        const variables = host.querySelector<HTMLButtonElement>('button[aria-label^="Variables,"]')!;
        await act(async () => variables.click());
        expect(host.textContent).toContain('Edit Variables');
        // Variables ← For loops would close a loop through Expressions → Conditionals → For loops.
        const input = host.querySelector<HTMLInputElement>('input[aria-autocomplete="list"]')!;
        await act(async () => { input.focus(); input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true })); });
        const option = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find((node) => node.textContent === 'For loops')!;
        await act(async () => option.click());
        await press('Save skill');
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('this prerequisite would create a cycle');
        expect(host.querySelector('button[aria-label^="Variables,"]')?.getAttribute('aria-label')).toContain('0 prerequisites');
    });

    it('uses the DESIGN.md primary navigation per role; Catalogue is the live public route', async () => {
        await mount('?screen=learner-home');
        const labels = () => [...host.querySelectorAll<HTMLAnchorElement>('header nav a')].map((link) => link.textContent);
        expect(labels()).toEqual(['Home', 'Catalogue', 'My courses']);
        expect(host.querySelector('header nav a[href="/courses"]')?.textContent).toBe('Catalogue');
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('Home');
        // Workspaces are opened by link, as the Leader switches them by hand.
        const open = (search: string) => act(async () => { window.history.pushState(null, '', `/preview${search}`); window.dispatchEvent(new PopStateEvent('popstate')); });
        await open('?screen=teaching');
        expect(labels()).toEqual(['Teaching', 'Grading', 'Learners', 'Catalogue']);
        await open('?screen=admin-overview');
        expect(labels()).toEqual(['Overview', 'Users', 'Courses', 'Reports', 'Audit log']);
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('changes sample membership from the My courses tile menu with the live confirmation copy', async () => {
        await mount('?screen=my-courses');
        expect(h1()).toBe('My courses'); expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('My courses');
        expect(host.textContent).toContain('3 courses');
        await act(async () => host.querySelector<HTMLButtonElement>('button[aria-label="More actions for Programming Practice"]')!.click());
        await press('Leave course', document.querySelector('[role="menu"]')!);
        const dialog = [...document.querySelectorAll<HTMLElement>('[role="dialog"]')].at(-1)!;
        expect(dialog.textContent).toContain('Your learning history will be preserved. You can re-enroll while the course is published.');
        expect(document.activeElement?.textContent).toBe('Cancel');
        await press('Leave course', dialog);
        expect(host.textContent).toContain('2 courses');
        await press('Left'); expect(host.textContent).toContain('Programming Practice'); expect(host.textContent).toContain('Membership: Left');
        await act(async () => host.querySelector<HTMLButtonElement>('button[aria-label="More actions for Programming Practice"]')!.click());
        await press('Re-enroll', document.querySelector('[role="menu"]')!);
        const list = host.querySelector('ul[aria-label="Your courses"]')!;
        expect(list.textContent).toContain('Working with Data'); expect(list.textContent).not.toContain('Programming Practice');
        expect(fetcher).not.toHaveBeenCalled(); expect(localStorage.length + sessionStorage.length).toBe(0);
    });

    it('never reads the signed-in session: a real sign-in stays out of the preview, which sends no request', async () => {
        fetcher.mockImplementation((url: string) => {
            const path = new URL(url, 'http://localhost').pathname;
            if (path.endsWith('/auth/login')) return Promise.resolve(json(loginResponse('Signed In Person')));
            if (path.endsWith('/courses')) return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
            return Promise.reject(new Error(`Unexpected request ${path}`));
        });
        // The API base is build-time configuration. Supply it here rather than depending on a
        // developer's untracked .env (CI has none, so the login request failed with a configuration error).
        vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
        window.history.replaceState(null, '', '/login');
        await act(async () => root.render(<App />));
        await typeInto(host.querySelector<HTMLInputElement>('input[name="email"]')!, 'learner@example.com');
        await typeInto(host.querySelector<HTMLInputElement>('input[name="password"]')!, 'synthetic-password');
        await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        // A Learner signs in to Learner home (7 November 2026).
        await waitUntil(() => location.pathname === '/home' && !!host.textContent?.includes('Signed In Person'), 'the signed-in Learner home');
        expect(location.pathname).toBe('/home'); expect(host.textContent).toContain('Signed In Person');
        const login = fetcher.mock.calls.find(([url]) => new URL(url).pathname === '/api/auth/login');
        expect(login, 'one real login request').toBeTruthy(); expect(login![1].method).toBe('POST');
        const requests = fetcher.mock.calls.length;
        await act(async () => { window.history.pushState(null, '', '/preview?screen=learner-home'); window.dispatchEvent(new PopStateEvent('popstate')); });
        await waitUntil(() => !!host.querySelector('[data-preview-screen="learner-home"]'), 'the lazily loaded preview');
        expect(host.textContent).not.toContain('Signed In Person'); expect(host.textContent).toContain('Linh Pham');
        expect(fetcher.mock.calls.length).toBe(requests); expect(localStorage.length + sessionStorage.length).toBe(0);
    });

    it('filters sample users after typing pauses, clears with Escape or the clear button and recovers from no matches', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        await mount('?screen=admin-users');
        const search = host.querySelector<HTMLInputElement>('input[aria-label="Search users"]')!;
        const rows = () => host.querySelectorAll('tbody tr').length;
        const initial = rows(); expect(initial).toBeGreaterThan(1);
        await act(async () => search.focus()); await typeInto(search, 'Linh');
        await settle(299); expect(rows()).toBe(initial);
        await settle(1); expect(rows()).toBe(1); expect(host.textContent).toContain('1 user');
        const clear = host.querySelector<HTMLButtonElement>('button[aria-label="Clear search users"]')!;
        await act(async () => { clear.focus(); clear.click(); }); expect(search.value).toBe(''); expect(document.activeElement).toBe(search);
        await settle(300); expect(rows()).toBe(initial);
        await typeInto(search, 'no such person'); await settle(300);
        expect(host.textContent).toContain('No users match'); await press('Clear filters'); await settle(300);
        expect(search.value).toBe(''); expect(rows()).toBe(initial);
        await typeInto(search, 'Linh'); await act(async () => search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(search.value).toBe(''); expect(fetcher).not.toHaveBeenCalled();
    });

    it('associates the pressed course-editor section with the region it replaces', async () => {
        await mount('?screen=course-editor&item=c-intro');
        const region = host.querySelector<HTMLElement>('[role="region"][aria-label$=" section"]')!;
        expect(region.id).toBeTruthy();
        for (const label of ['Skills', 'Settings', 'Content']) {
            const button = [...host.querySelectorAll<HTMLButtonElement>('[aria-label="Course editor sections"] button')].find((node) => node.textContent === label)!;
            await act(async () => button.click());
            expect(button.getAttribute('aria-pressed')).toBe('true'); expect(button.getAttribute('aria-controls')).toBe(region.id);
            expect(document.getElementById(region.id)?.getAttribute('aria-label')).toBe(`${label} section`);
        }
        expect(fetcher).not.toHaveBeenCalled();
    });
});

/**
 * AUD-01: the bootstrap isolation decision must follow the router's own matching. React Router 7 matches
 * `/preview` case-insensitively, with trailing slashes, after decoding each path segment, so every URL that
 * renders the preview must also start without restoring. The refresh endpoint answers with a valid session
 * here, so a missing isolation would install "Restored Person" instead of failing silently.
 */
describe('AUD-01: preview isolation follows the router for every URL that renders the preview', () => {
    let tabs: { onmessage: ((event: MessageEvent) => void) | null }[];
    const refreshCalls = () => fetcher.mock.calls.filter(([url]) => new URL(String(url), 'http://localhost').pathname === '/api/auth/refresh').length;
    beforeEach(() => {
        tabs = [];
        vi.stubGlobal('BroadcastChannel', class { onmessage: ((event: MessageEvent) => void) | null = null; constructor() { tabs.push(this); } postMessage() { /* test double */ } close() { /* test double */ } });
        fetcher.mockImplementation((url: string) => {
            const path = new URL(url, 'http://localhost').pathname;
            if (path === '/api/auth/refresh') return Promise.resolve(json(loginResponse('Restored Person')));
            if (path === '/api/courses') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
            if (path === '/api/me/courses') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
            return Promise.resolve(json({ type: 'about:blank', title: 'Not Found', status: 404 }, 404));
        });
    });
    async function open(path: string) {
        window.history.replaceState(null, '', path);
        await act(async () => root.render(<App />));
        await waitUntil(() => !!host.querySelector('[data-preview-screen], header, main'), `a shell at ${path}`);
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 30)); });
    }

    for (const path of ['/preview', '/PREVIEW', '/Preview', '/pre%76iew', '/%70review', '/PRE%56IEW', '/preview/', '/preview//', '/preview?screen=learner-home']) {
        it(`${path}: renders the preview, sends no refresh and installs no session, through tabs, wake-ups, theme changes and navigation`, async () => {
            await open(path);
            expect(host.querySelector('[data-preview-screen]'), 'the preview renders').not.toBeNull();
            expect(refreshCalls()).toBe(0);
            // Another tab signs in, out and in again; this document wakes up.
            for (const type of ['signed-in', 'signed-out', 'signed-in']) {
                for (const tab of tabs) await act(async () => tab.onmessage?.({ data: { type } } as MessageEvent));
            }
            await act(async () => {
                window.dispatchEvent(new Event('focus')); window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
                document.dispatchEvent(new Event('visibilitychange'));
                await new Promise((resolve) => setTimeout(resolve, 30));
            });
            expect(fetcher).not.toHaveBeenCalled();
            // A theme change, then leaving the preview in the same document: still no restore.
            await act(async () => { window.history.pushState(null, '', '/'); window.dispatchEvent(new PopStateEvent('popstate')); });
            await waitUntil(() => !!host.querySelector('button[aria-label^="Theme:"]'), 'the landing page');
            await act(async () => host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')!.click());
            await act(async () => [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((item) => item.textContent?.startsWith('Dark'))!.click());
            await act(async () => { window.history.pushState(null, '', '/courses'); window.dispatchEvent(new PopStateEvent('popstate')); await new Promise((resolve) => setTimeout(resolve, 30)); });
            expect(refreshCalls()).toBe(0);
            expect(host.textContent).not.toContain('Restored Person');
            expect(host.querySelector('button[aria-label^="Account menu"]')).toBeNull();
            expect(sessionStorage.length).toBe(0);
            expect(Object.keys(localStorage)).toEqual(['codemastery.theme']);
        });
    }

    // Controls: these do not render the preview, so they restore once as usual. Malformed encodings are not
    // decoded by the router either (it keeps the raw path), and must neither throw nor count as the preview.
    for (const path of ['/', '/courses', '/previewish', '/preview-x', '/preview/extra', '/pre%76iewish', '/preview%', '/pre%7view', '/%E0%A4%A']) {
        it(`${path}: does not render the preview and restores the session once`, async () => {
            vi.spyOn(console, 'warn').mockImplementation(() => undefined);
            await open(path);
            expect(host.querySelector('[data-preview-screen]')).toBeNull();
            await waitUntil(() => refreshCalls() === 1, 'one restore request');
            await act(async () => { await new Promise((resolve) => setTimeout(resolve, 30)); });
            expect(refreshCalls()).toBe(1);
        });
    }
});
