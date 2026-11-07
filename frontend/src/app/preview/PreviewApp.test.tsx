// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.stubGlobal('DOMMatrixReadOnly', DOMMatrixStub);
    fetcher = vi.fn(() => Promise.reject(new Error('The preview must not call the network.')));
    vi.stubGlobal('fetch', fetcher);
    localStorage.clear(); sessionStorage.clear();
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => {
    await act(async () => root.unmount()); host.remove();
    window.history.replaceState(null, '', '/'); vi.useRealTimers(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); localStorage.clear(); sessionStorage.clear();
});

/** Mount the real App at /preview and wait for the lazily loaded preview chunk. */
async function mount(search = '') {
    window.history.replaceState(null, '', `/preview${search}`);
    await act(async () => root.render(<App />));
    for (let attempt = 0; attempt < 50 && !host.querySelector('[aria-label="Design preview notice"]'); attempt++) {
        await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)); });
    }
    expect(host.querySelector('[aria-label="Design preview notice"]')).not.toBeNull();
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
    it('labels sample data, lists every workspace and never calls the network or writes storage', async () => {
        await mount();
        expect(h1()).toBe('CodeMastery design preview');
        expect(host.textContent).toContain('Design preview · Sample data.');
        for (const group of ['Live screens', 'Learner', 'Instructor', 'Administrator', 'Public and account']) expect(host.textContent).toContain(group);
        for (const spec of ['S6', 'S7', 'S8', 'S9', 'S10', 'S11', 'S12', 'S13', 'S14', 'S15', 'S17', 'S18', 'S21', 'S22', 'S23', 'S24', 'S25', 'S26']) expect(host.textContent).toContain(spec);
        expect(fetcher).not.toHaveBeenCalled(); expect(localStorage.length + sessionStorage.length).toBe(0);
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

    it('auto-submits when the sample timer runs out', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        await mount('?screen=quiz-attempt');
        await press('Simulate time running out'); await settle(5000);
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
        await press('Reset sample data');
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
        await press('Instructor');
        expect(labels()).toEqual(['Teaching', 'Grading', 'Learners', 'Catalogue']);
        await press('Administrator');
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
        await waitUntil(() => location.pathname === '/courses' && !!host.textContent?.includes('Signed In Person'), 'the signed-in catalogue');
        expect(location.pathname).toBe('/courses'); expect(host.textContent).toContain('Signed In Person');
        const login = fetcher.mock.calls.find(([url]) => new URL(url).pathname === '/api/auth/login');
        expect(login, 'one real login request').toBeTruthy(); expect(login![1].method).toBe('POST');
        const requests = fetcher.mock.calls.length;
        await act(async () => { window.history.pushState(null, '', '/preview?screen=learner-home'); window.dispatchEvent(new PopStateEvent('popstate')); });
        await waitUntil(() => !!host.querySelector('[aria-label="Design preview notice"]'), 'the lazily loaded preview');
        expect(host.textContent).toContain('Design preview · Sample data.');
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
