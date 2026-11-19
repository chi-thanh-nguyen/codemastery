// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { BrowserRouter, useNavigate } from 'react-router';
import type { NavigateFunction } from 'react-router';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRouter } from './index';
import { AuthProvider } from '../app/providers/AuthProvider';
import type { AuthContextValue } from '../app/providers/AuthProvider';
import { useAuth } from '../features/auth/hooks/useAuth';
import { appTheme } from '../app/theme/theme';
import App from '../app/App';
import { request } from '../shared/api/httpClient';
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement, auth: AuthContextValue, navigate: NavigateFunction, fetcher: ReturnType<typeof vi.fn>;
const id = '00000000-0000-4000-8000-000000000001';
const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
function loginResponse(name = 'Person A', role = 'Learner') {
    const iat = Math.floor(Date.now() / 1000);
    return { accessToken: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: id, iss: 'codemastery', aud: 'codemastery-api', iat, exp: iat + 900 })}.${'A'.repeat(43)}`, tokenType: 'Bearer', expiresIn: 900, user: { id, displayName: name, role } };
}
const course = { id, title: 'Course details', description: 'Programming foundations', instructorId: id, instructorDisplayName: 'Instructor Demo' };
const page = { content: [course], page: 0, size: 20, totalElements: 1, totalPages: 1 };
const detail = { ...course, prerequisiteDescription: null, syllabus: [] };
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
function Probe() { auth = useAuth(); navigate = useNavigate(); return null; }
beforeEach(() => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-25T10:00:00Z')); vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn((url: string, _init?: RequestInit) => Promise.resolve(json(new URL(url).pathname.startsWith('/api/courses/') ? detail : page))); vi.stubGlobal('fetch', fetcher);
    window.history.replaceState(null, '', '/'); localStorage.clear(); sessionStorage.clear(); host = document.createElement('div'); document.body.append(host); root = createRoot(host);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); window.history.replaceState(null, '', '/'); vi.useRealTimers(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear(); });
async function mount(path = '/') {
    window.history.replaceState(null, '', path); await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><Probe /><AppRouter /></AuthProvider></BrowserRouter></ThemeProvider>));
}
async function go(path: string) { await act(async () => navigate(path)); }
async function login(name = 'Person A', role = 'Learner') { fetcher.mockResolvedValueOnce(json(loginResponse(name, role))); await act(async () => { await auth.login({ email: 'learner@example.com', password: 'synthetic' }); }); }
async function change(name: string, value: string) {
    const input = host.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
}
async function formLogin(name = 'Person A', role = 'Learner') {
    await change('email', 'learner@example.com'); await change('password', 'synthetic'); fetcher.mockResolvedValueOnce(json(loginResponse(name, role)));
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
async function click(text: string) {
    const node = [...host.querySelectorAll<HTMLElement>('button,a')].find((element) => element.textContent === text)!; expect(node).toBeTruthy(); await act(async () => node.click());
}
async function openAccountMenu() {
    const button = host.querySelector<HTMLButtonElement>('header [aria-label^="Account menu"]')!; expect(button).toBeTruthy(); await act(async () => button.click());
    return document.querySelector<HTMLElement>('[role="menu"]')!;
}
async function signOutFromMenu() {
    const menu = await openAccountMenu();
    const item = [...menu.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((node) => node.textContent === 'Sign out')!; expect(item).toBeTruthy(); await act(async () => item.click());
}
describe('real declarative routing and Profile', () => {
    it.each([['/login', 'email'], ['/login', 'password'], ['/register', 'displayName'], ['/register', 'email'], ['/register', 'password'], ['/register', 'confirmPassword']])('blurs %s %s while connected before Back removes its form', async (path, name) => {
        vi.useRealTimers(); await mount('/courses'); await go(path);
        expect(document.activeElement).toBe(host.querySelector('main h1'));
        const input = host.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
        const blurredWhileConnected: boolean[] = [];
        input.addEventListener('blur', () => blurredWhileConnected.push(input.isConnected));
        await act(async () => input.focus());
        async function travel(action: () => void) { await act(async () => { const popped = new Promise<void>((done) => window.addEventListener('popstate', () => done(), { once: true })); action(); await popped; }); }
        await travel(() => window.history.back());
        expect(blurredWhileConnected).toEqual([true]); expect(location.pathname).toBe('/courses');
        expect(document.activeElement).toBe(host.querySelector('main h1'));
        await travel(() => window.history.forward());
        expect(location.pathname).toBe(path); expect(document.activeElement).toBe(host.querySelector('main h1'));
        const restored = host.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
        restored.addEventListener('blur', () => blurredWhileConnected.push(restored.isConnected));
        await act(async () => restored.focus()); await go('/courses');
        expect(blurredWhileConnected).toEqual([true, true]);
    });
    it.each(['/login', '/register'])('blurs a focused password on pagehide without changing %s form contents', async (path) => {
        await mount(path); await change('password', 'Local sample password');
        const input = host.querySelector<HTMLInputElement>('input[name="password"]')!;
        const blur = vi.fn(); input.addEventListener('blur', blur);
        await act(async () => input.focus());
        await act(async () => window.dispatchEvent(new Event('pagehide')));
        expect(blur).toHaveBeenCalledTimes(1); expect(input.isConnected).toBe(true);
        expect(input.value).toBe('Local sample password'); expect(document.activeElement).not.toBe(input);
        expect(fetcher).not.toHaveBeenCalled();
    });
    it.each([['/', '/', 'Learn programming at the pace of what you actually know.'], ['/login', '/login', 'Sign in'], ['/register', '/register', 'Create account'], ['/courses', '/courses', 'Course catalogue'], ['/courses/course-one', '/courses/course-one', 'Course details'], ['/missing', '/missing', 'Page not found']])('direct entry %s', async (path, finalPath, title) => {
        await mount(path); expect(location.pathname).toBe(finalPath); expect(host.querySelector('main h1')?.textContent).toBe(title); expect(fetcher.mock.calls.every(([url]) => new URL(url).pathname.startsWith('/api/courses'))).toBe(true); expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
    it('anonymous /profile never mounts personal fields and replaces with login', async () => {
        const seen: string[] = [], observer = new MutationObserver(() => seen.push(host.textContent ?? '')); observer.observe(host, { subtree: true, childList: true });
        await mount('/profile'); observer.disconnect(); expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull(); expect(seen.every((text) => !text.includes('Person A'))).toBe(true); expect(fetcher).not.toHaveBeenCalled();
    });
    it('consumes literal Profile return once, ignoring query/fragment/history redirect payloads', async () => {
        await mount('/profile?returnTo=https://outside.test#secret'); window.history.replaceState({ returnTo: 'https://outside.test' }, '', '/login?returnTo=//outside.test');
        await formLogin(); expect(location.pathname).toBe('/profile'); expect(location.search + location.hash).toBe(''); // Consumed once: a second read falls back to the Learner's start page, never /profile again.
        expect(auth.consumeReturn()).toBe('/home'); expect(host.querySelector('dl')?.textContent).toContain('Person A');
    });
    // Leader instruction, 7 November 2026: a Learner's default destination is Learner home; other roles keep the
    // CT16 /courses fallback. A caller-supplied return target is still never authority.
    it.each([['Learner', '/home'], ['Instructor', '/courses'], ['Administrator', '/courses']])('login sends a %s to %s for arbitrary supplied return targets', async (role, start) => {
        await mount('/login?returnTo=/api/secrets'); await formLogin('Person A', role); expect(location.pathname).toBe(start); expect(location.search + location.hash).toBe('');
    });
    it.each(['Learner', 'Instructor', 'Administrator'])('shows exactly the validated three fields for %s without any Profile request', async (role) => {
        await mount('/courses'); await login('Person A', role); await go('/profile');
        expect([...host.querySelectorAll('dt')].map((node) => node.textContent)).toEqual(['Display name', 'Role', 'User ID']);
        expect([...host.querySelectorAll('dd')].map((node) => node.textContent)).toEqual(['Person A', role, id]); expect(host.querySelector('form')).toBeNull();
        expect(host.textContent).not.toMatch(/email|permissions|password|accessToken/i);
        const menu = await openAccountMenu(); expect(menu.querySelectorAll('a[href="/profile"]')).toHaveLength(1);
        await act(async () => menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/auth/login')).toHaveLength(1); expect(host.textContent).not.toMatch(/email|permissions|password|accessToken/i);
    });
    it('renders hostile display names as plain text and preserves snapshot after leaving Profile', async () => {
        await mount('/courses'); await login('<img src=x onerror=alert(1)>'); await go('/profile');
        const displayName = host.querySelectorAll('dd')[0]!;
        expect(displayName.textContent).toBe('<img src=x onerror=alert(1)>'); expect(displayName.children).toHaveLength(0);
        expect(host.querySelector('img[src="x"], [onerror]')).toBeNull();
        expect([...host.querySelectorAll('img')].every((image) => image.getAttribute('src') === '/favicon.svg')).toBe(true);
        await go('/courses'); expect(auth.isAuthenticated).toBe(true); await go('/profile'); expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/auth/login')).toHaveLength(1);
    });
    it('successful same-UUID replacement removes A and renders only B without stealing focus', async () => {
        await mount('/courses'); await login(); await go('/profile'); const link = host.querySelector<HTMLAnchorElement>('a[href="/courses"]')!; link.focus();
        await login('Person B', 'Administrator'); expect(host.textContent).not.toContain('Person A'); expect(host.querySelector('dl')?.textContent).toContain('Person B'); expect(host.querySelector('dl')?.textContent).toContain('Administrator'); expect(document.activeElement).toBe(link);
    });
    it('explicit logout clears Profile, does not re-arm return, and deliberate revisit can set it', async () => {
        await mount('/courses'); await login(); await go('/profile'); await act(async () => auth.logout()); expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('Person A');
        await formLogin('Person B'); expect(location.pathname).toBe('/home'); await signOutFromMenu(); expect(location.pathname).toBe('/login'); await go('/profile'); await formLogin('Person C'); expect(location.pathname).toBe('/profile'); expect(host.textContent).not.toContain('Person B');
    });
    it.each(['timer', 'pageshow'])('expiry via %s removes active Profile before redirect', async (mode) => {
        await mount('/courses'); await login(); await go('/profile');
        if (mode === 'timer') await act(async () => vi.advanceTimersByTime(900000)); else { vi.setSystemTime(new Date(Date.now() + 900000)); await act(async () => window.dispatchEvent(new Event('pageshow'))); }
        expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull(); expect(host.textContent).not.toContain('Person A'); await formLogin('Person B'); expect(location.pathname).toBe('/profile');
    });
    it('eligible current 401 removes Profile; public 401 does not remove it', async () => {
        await mount('/courses'); await login(); await go('/profile'); fetcher.mockResolvedValueOnce(json(null, 401)); await act(async () => { await expect(auth.login({ email: 'learner@example.com', password: 'wrong' })).rejects.toMatchObject({ status: 401 }); }); expect(host.querySelector('dl')?.textContent).toContain('Person A');
        fetcher.mockResolvedValueOnce(json(null, 401)); await act(async () => { await expect(request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (value) => value })).rejects.toMatchObject({ status: 401 }); }); expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull();
    });
    it('fresh document/provider loses memory session and return intent', async () => {
        await mount('/courses'); await login(); await go('/profile'); await act(async () => root.unmount()); root = createRoot(host); await mount('/profile'); expect(auth.user).toBeNull(); expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull(); expect(localStorage.length + sessionStorage.length).toBe(0);
    });
    it('browser history back/forward updates routes and guard after logout within jsdom', async () => {
        vi.useRealTimers(); await mount('/courses'); await login(); await go('/profile'); await go('/courses/one');
        async function travel(action: () => void) { await act(async () => { const popped = new Promise<void>((done) => window.addEventListener('popstate', () => done(), { once: true })); action(); await popped; }); }
        await travel(() => window.history.back()); expect(location.pathname).toBe('/profile'); expect(host.querySelector('dl')).not.toBeNull(); await travel(() => window.history.forward()); expect(location.pathname).toBe('/courses/one'); await act(async () => auth.logout()); await travel(() => window.history.back()); expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull();
    });
    it('ADR014: a reload of a protected route waits for the refresh cookie instead of redirecting', async () => {
        let resolve!: (response: Response) => void;
        fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }));
        window.history.replaceState(null, '', '/profile');
        await act(async () => root.render(<App />));
        expect(location.pathname).toBe('/profile'); expect(host.textContent).toContain('Restoring your session…');
        expect(host.textContent).not.toContain('Sign in');
        expect(new URL(fetcher.mock.calls[0][0]).pathname).toBe('/api/auth/refresh');
        await act(async () => resolve(json(loginResponse('Person A'))));
        expect(location.pathname).toBe('/profile'); expect(host.querySelector('dl')?.textContent).toContain('Person A');
    });
    it('ADR014: a refused restore sends a protected reload to Sign in without a session-ended notice', async () => {
        fetcher.mockResolvedValueOnce(new Response(JSON.stringify({ type: 'about:blank', title: 'Unauthorized', status: 401, detail: 'Authentication failed.', instance: '/api/auth/refresh' }), { status: 401, headers: { 'Content-Type': 'application/problem+json' } }));
        window.history.replaceState(null, '', '/profile');
        await act(async () => root.render(<App />));
        expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('Your session ended');
    });
    it('composes the actual public catalogue/detail with current Auth navigation', async () => {
        window.history.replaceState(null, '', '/courses');
        await act(async () => root.render(<App />));
        expect(location.pathname).toBe('/courses'); expect(host.textContent).toContain('Course catalogue');
        await act(async () => host.querySelector<HTMLAnchorElement>('a[data-tile-link]')!.click()); expect(location.pathname).toBe(`/courses/${id}`); expect(host.querySelector('h1')?.textContent).toBe('Course details');
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('Catalogue');
        const back = [...host.querySelectorAll<HTMLAnchorElement>('main a')].find((link) => link.textContent === 'Catalogue')!;
        await act(async () => back.click()); expect(location.pathname).toBe('/courses');
        expect(fetcher.mock.calls.every(([, init]) => !new Headers((init as RequestInit).headers).has('Authorization'))).toBe(true);
        await click('Sign in'); expect(location.pathname).toBe('/login'); expect(host.querySelector('h1')?.textContent).toBe('Sign in');
    });

});


it.each(['Instructor', 'Administrator'])('denies %s before mounting My Courses or issuing private requests', async (role) => {
    await mount('/courses'); await login('Role test', role); await go('/my-courses');
    expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
    expect(host.textContent).toContain('This page is for learners.');
    expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/me/courses')).toHaveLength(0);
    expect(host.querySelector('header a[href="/my-courses"]')).toBeNull();
});
it('mounts real learner My Courses with authenticated transport and removes it at expiry', async () => {
    await mount('/courses'); await login();
    fetcher.mockImplementation((url: string) => Promise.resolve(json(new URL(url).pathname === '/api/me/courses' ? { ...page, content: [], totalElements: 0, totalPages: 0 } : page)));
    await go('/my-courses'); expect(host.querySelector('main h1')?.textContent).toBe('My courses'); expect(host.textContent).toContain("You haven't joined a course yet");
    expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('My courses');
    const [, init] = fetcher.mock.calls.find(([url]) => new URL(url).pathname === '/api/me/courses')!;
    expect(init.headers.has('Authorization')).toBe(true);
    await act(async () => vi.advanceTimersByTime(900000)); expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('My courses');
});

const membershipPage = (status: 'active' | 'left' = 'active') => ({ ...page, content: [{ ...course, title: 'Active course', status }] });
class ResizeObserverStub { observe() {} unobserve() {} disconnect() {} }
it('serves the public landing page at / with public navigation and no request', async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub); vi.stubGlobal('DOMMatrixReadOnly', class { m22 = 1; });
    await mount('/');
    for (let attempt = 0; attempt < 50 && !host.textContent?.includes('Illustration'); attempt++) await act(async () => { await vi.advanceTimersByTimeAsync(20); });
    expect(host.querySelector('main h1')?.textContent).toBe('Learn programming at the pace of what you actually know.');
    const nav = host.querySelector('nav[aria-label="Main navigation"]')!;
    expect([...nav.querySelectorAll('a')].map((link) => [link.textContent, link.getAttribute('href')])).toEqual([['Catalogue', '/courses'], ['Sign in', '/login'], ['Create account', '/register']]);
    expect(host.querySelector('a[href="#main-content"]')?.textContent).toBe('Skip to main content');
    expect(host.textContent).toContain('Illustration · not learner data');
    expect(fetcher).not.toHaveBeenCalled();
    await click('Catalogue'); expect(location.pathname).toBe('/courses');
    expect(host.querySelector('header a[aria-label="CodeMastery home"]')?.getAttribute('href')).toBe('/');
});
it.each([['Learner', '/home'], ['Instructor', '/courses'], ['Administrator', '/courses']])('sends a signed-in %s from / to %s and points the logo there', async (role, start) => {
    fetcher.mockImplementation((url: string) => Promise.resolve(json(new URL(url).pathname === '/api/me/courses' ? membershipPage() : page)));
    await mount('/courses'); await login('Role test', role); await go('/');
    expect(location.pathname).toBe(start);
    expect(host.querySelector('header a[aria-label="CodeMastery home"]')?.getAttribute('href')).toBe(start);
});
it('shows a Learner home of active courses from the membership API only', async () => {
    fetcher.mockImplementation((url: string) => Promise.resolve(json(new URL(url).pathname === '/api/me/courses' ? membershipPage() : page)));
    await mount('/courses'); await login();
    expect([...host.querySelectorAll('header nav a')].map((link) => link.textContent)).toEqual(['Home', 'Catalogue', 'My courses']);
    await go('/home');
    expect(host.querySelector('main h1')?.textContent).toBe('Welcome back, Person A');
    expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('Home');
    const call = fetcher.mock.calls.find(([url]) => new URL(url).pathname === '/api/me/courses')!;
    expect(new URL(call[0]).searchParams.get('status')).toBe('active'); expect(call[1].headers.has('Authorization')).toBe(true);
    const list = host.querySelector('ul[aria-label="Your active courses"]')!;
    expect(list.textContent).toContain('Active course'); expect(list.textContent).toContain('Instructor · Instructor Demo');
    expect(list.querySelector('a')?.getAttribute('href')).toBe(`/courses/${id}`);
    // Resume, progress and recommendations have no contract yet, so their sections are absent.
    expect(host.textContent).not.toMatch(/Continue learning|Up next|lessons ·|Resume/);
    expect(fetcher.mock.calls.every(([url]) => ['/api/auth/login', '/api/courses', '/api/me/courses'].includes(new URL(url).pathname))).toBe(true);
});
it('links "See all N active courses" to My courses filtered by Active, which reads ?status= and keeps Joined as the default', async () => {
    // 25 active memberships: Home lists the first 20 and links to the rest.
    const statusOf = (url: string) => new URL(url).searchParams.get('status');
    fetcher.mockImplementation((url: string) => Promise.resolve(json(new URL(url).pathname === '/api/me/courses'
        ? { ...membershipPage(), totalElements: statusOf(url) === 'active' ? 25 : 28, totalPages: 2 } : page)));
    await mount('/courses'); await login(); await go('/home');
    const more = [...host.querySelectorAll<HTMLAnchorElement>('main a')].find((link) => link.textContent === 'See all 25 active courses in My courses')!;
    expect(more.getAttribute('href')).toBe('/my-courses?status=active');
    fetcher.mockClear(); await act(async () => more.click());
    expect(location.pathname + location.search).toBe('/my-courses?status=active');
    const lastStatus = () => statusOf(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/me/courses').at(-1)![0]);
    const pressed = () => host.querySelector('[aria-label="Membership status"] [aria-pressed="true"]')?.textContent;
    expect(lastStatus()).toBe('active'); expect(pressed()).toBe('Active'); expect(host.textContent).toContain('25 courses');
    // Choosing a filter updates the URL; Joined removes the parameter again.
    await act(async () => [...host.querySelectorAll<HTMLButtonElement>('[aria-label="Membership status"] button')].find((b) => b.textContent === 'Completed')!.click());
    expect(location.search).toBe('?status=completed'); expect(lastStatus()).toBe('completed');
    await act(async () => [...host.querySelectorAll<HTMLButtonElement>('[aria-label="Membership status"] button')].find((b) => b.textContent === 'Joined')!.click());
    expect(location.search).toBe(''); expect(lastStatus()).toBe('joined');
    // No or unknown values fall back to Joined.
    for (const search of ['', '?status=none', '?status=ACTIVE', '?status=']) {
        // Arrive fresh each time (from the catalogue), as a link or typed URL would.
        await go('/courses'); fetcher.mockClear(); await go(`/my-courses${search}`);
        expect(lastStatus(), search).toBe('joined'); expect(pressed(), search).toBe('Joined');
    }
});
it('guards Learner home: anonymous visitors sign in, other roles are denied without a request', async () => {
    await mount('/home'); expect(location.pathname).toBe('/login');
    await mount('/courses'); await login('Teacher', 'Instructor'); await go('/home');
    expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
    expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/me/courses')).toHaveLength(0);
});
it('says the session ended on the sign-in page after expiry, once, but not after signing out', async () => {
    await mount('/courses'); await login(); await go('/profile');
    await act(async () => vi.advanceTimersByTime(900000));
    expect(location.pathname).toBe('/login');
    expect(host.querySelector('[role="status"]')?.textContent).toBe('Your session ended. Sign in again to continue.');
    await go('/courses'); await go('/login');
    expect(host.textContent).not.toContain('Your session ended');
    await go('/courses'); await login(); await signOutFromMenu();
    expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('Your session ended');
});
it('shows Learner home empty and retryable error states', async () => {
    let fail = true;
    fetcher.mockImplementation((url: string) => {
        if (new URL(url).pathname !== '/api/me/courses') return Promise.resolve(json(page));
        return Promise.resolve(fail ? json({ type: 'about:blank', title: 'Internal Server Error', status: 500, detail: 'Unavailable.', instance: '/api/me/courses' }, 500)
            : json({ ...page, content: [], totalElements: 0, totalPages: 0 }));
    });
    await mount('/courses'); await login(); await go('/home');
    expect(host.textContent).toContain("Couldn't load your courses");
    fail = false; await click('Try again');
    expect(host.textContent).toContain("You haven't joined a course yet"); expect(host.querySelector('a[href="/courses"]')?.textContent).toBeTruthy();
});
describe('FE06 password recovery routes (CT02, CT16.routes)', () => {
    const token = `${'Q'.repeat(42)}w`, newPassword = '  a new exact password  ';
    const requested = 'If an account matches, a password reset link will be sent.', completed = 'Password reset. Sign in with your new password.';
    /** AU07 problems are field-free (BusinessRuleException); only the detail differs, and the client never shows it. */
    const problem = (status: number, title: string, instance: string, detail = 'The request could not be completed.') => new Response(JSON.stringify({
        type: 'about:blank', title, status, detail, instance,
    }), { status, headers: { 'Content-Type': 'application/problem+json' } });
    const tokenB = `${'R'.repeat(42)}A`;
    /** A link opened in a tab already showing the page: a same-document fragment navigation (no reload). */
    async function openInSameTab(path: string) {
        await act(async () => { window.history.pushState(null, '', path); window.dispatchEvent(new PopStateEvent('popstate')); });
    }
    /** Records where the page was when each request left, so capability erasure can be ordered against it. */
    let seen: { path: string; href: string; body?: string; referrerPolicy?: string | null }[];
    function respond(handler: (path: string) => Response | Promise<Response> | undefined) {
        seen = [];
        fetcher.mockImplementation((url: string, init?: RequestInit) => {
            const path = new URL(url).pathname;
            seen.push({ path, href: window.location.href, body: init?.body as string | undefined, referrerPolicy: document.head.querySelector('meta[name="referrer"]')?.getAttribute('content') });
            const answer = handler(path);
            return answer ? Promise.resolve(answer) : Promise.resolve(json(page));
        });
    }
    const calls = (path: string) => seen.filter((entry) => entry.path === path);
    async function submit() { await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
    async function fillReset(password = newPassword, confirm = password) { await change('password', password); await change('confirmPassword', confirm); }
    it('links Sign in to the request page and acknowledges with the API text verbatim', async () => {
        respond((path) => path === '/api/auth/password-reset-requests' ? json({ message: requested }, 202) : undefined);
        await mount('/login'); await click('Forgot password?');
        expect(location.pathname).toBe('/forgot-password'); expect(host.querySelector('h1')?.textContent).toBe('Forgot password');
        await change('email', ' Learner@Example.COM '); await submit();
        expect(calls('/api/auth/password-reset-requests').map((entry) => JSON.parse(entry.body!))).toEqual([{ email: 'learner@example.com' }]);
        expect(host.querySelector('[role="status"]')?.textContent).toBe(requested);
        expect(document.activeElement?.textContent).toBe(requested);
        expect(host.querySelector('a[href="/login"]')?.textContent).toBe('Back to sign in');
    });
    it.each(['K@example.com', 'a@example.com ', 'missing-at'])('refuses request email %# locally and focuses it', async (email) => {
        respond(() => undefined);
        await mount('/forgot-password'); await change('email', email); await submit();
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(0);
        expect(host.textContent).toContain('Enter a valid ASCII email address.');
        expect(document.activeElement).toBe(host.querySelector('input[name="email"]'));
    });
    it.each([
        ['network', () => Promise.reject(new Error('private')), "We couldn't reach CodeMastery. Check your connection and try again."],
        ['server', () => problem(500, 'Internal Server Error', '/api/auth/password-reset-requests'), 'Something went wrong on our side. Try again in a moment.'],
    ] as const)('shows the F14 %s failure on the request page and retries only on demand', async (_kind, failure, text) => {
        let fail = true;
        respond((path) => path === '/api/auth/password-reset-requests' ? (fail ? failure() as Response : json({ message: requested }, 202)) : undefined);
        await mount('/forgot-password'); await change('email', 'learner@example.com'); await submit();
        expect(host.querySelector('[role="alert"]')?.textContent).toContain(text); expect(host.textContent).not.toContain('private');
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(1);
        fail = false; await click('Try again');
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(2); expect(host.querySelector('[role="status"]')?.textContent).toBe(requested);
    });
    it('erases the capability before paint and any request, keeps it out of the DOM and storage, and creates no session', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount(`/reset-password#token=${token}`);
        expect(window.location.pathname).toBe('/reset-password'); expect(window.location.hash).toBe(''); expect(window.location.search).toBe('');
        expect(seen.every((entry) => !entry.href.includes(token))).toBe(true);
        expect(document.head.querySelector('meta[name="referrer"]')?.getAttribute('content')).toBe('no-referrer');
        expect(host.querySelector('h1')?.textContent).toBe('Reset password');
        await fillReset(); await submit();
        const sent = calls('/api/auth/password-resets');
        expect(sent).toHaveLength(1); expect(JSON.parse(sent[0].body!)).toEqual({ token, password: newPassword }); expect(sent[0].href).not.toContain(token);
        // ADR006 §9.3: every request from this page left under the no-referrer policy and without the capability in the URL.
        expect(seen.every((entry) => entry.referrerPolicy === 'no-referrer' && !entry.href.includes(token))).toBe(true);
        expect(host.innerHTML).not.toContain(token); expect(document.documentElement.innerHTML).not.toContain(token);
        expect(localStorage.length + sessionStorage.length).toBe(0); expect(JSON.stringify(window.history.state ?? {})).not.toContain(token);
        expect(host.querySelector('[role="status"]')?.textContent).toBe(`${completed}Other signed-in sessions on your account have been signed out.`);
        expect(host.querySelector('a[href="/login"]')?.textContent).toBe('Sign in');
        expect(auth.isAuthenticated).toBe(false); expect(calls('/api/auth/login')).toHaveLength(0);
        expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
    it.each([['/reset-password', 'a reload without a capability'], ['/reset-password#token=short', 'a malformed capability'], [`/reset-password?token=${token}`, 'a capability in the query']])(
        'shows the invalid-link state for %s (%s) without a request', async (path) => {
            respond(() => undefined);
            await mount(path);
            expect(window.location.pathname + window.location.search + window.location.hash).toBe('/reset-password');
            expect(host.querySelector('h1')?.textContent).toBe('This reset link is invalid or has expired.');
            expect(host.textContent).toContain('Request a new link from the sign-in page.');
            expect(host.querySelector('a[href="/forgot-password"]')?.textContent).toBe('Request a new link');
            expect(calls('/api/auth/password-resets')).toHaveLength(0); expect(host.innerHTML).not.toContain(token);
        });
    it.each([
        ['an invalid, used, replaced or expired capability', 'Reset link is invalid or expired.'],
        ['any other field-free AU07 400', 'The request is invalid.'],
    ])('shows the invalid-link state for the producer 400 for %s', async (_case, detail) => {
        respond((path) => path === '/api/auth/password-resets' ? problem(400, 'Bad Request', path, detail) : undefined);
        await mount(`/reset-password#token=${token}`); await fillReset(); await submit();
        expect(host.querySelector('h1')?.textContent).toBe('This reset link is invalid or has expired.');
        expect(host.textContent).not.toContain(detail);
        expect(document.activeElement).toBe(host.querySelector('main h1'));
        expect(calls('/api/auth/password-resets')).toHaveLength(1);
    });
    it('replaces the capability when a new link opens in the same tab after a reset', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount(`/reset-password#token=${token}`); await fillReset(); await submit();
        expect(host.querySelector('h1')?.textContent).toBe('Password updated');
        await openInSameTab(`/reset-password#token=${tokenB}`);
        expect(window.location.pathname + window.location.search + window.location.hash).toBe('/reset-password');
        expect(host.querySelector('h1')?.textContent).toBe('Reset password'); expect(document.activeElement).toBe(host.querySelector('main h1'));
        expect(host.querySelector<HTMLInputElement>('input[name="password"]')?.value).toBe('');
        await fillReset(); await submit();
        expect(calls('/api/auth/password-resets').map((entry) => JSON.parse(entry.body!).token)).toEqual([token, tokenB]);
        expect(document.documentElement.innerHTML).not.toContain(tokenB); expect(JSON.stringify(window.history.state ?? {})).not.toContain(tokenB);
        expect(seen.every((entry) => !entry.href.includes(token) && !entry.href.includes(tokenB))).toBe(true);
    });
    it('opens the form for a new link in a tab that showed the invalid-link state', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount('/reset-password');
        expect(host.querySelector('h1')?.textContent).toBe('This reset link is invalid or has expired.');
        await openInSameTab(`/reset-password#token=${tokenB}`);
        expect(host.querySelector('h1')?.textContent).toBe('Reset password');
        await fillReset(); await submit();
        expect(calls('/api/auth/password-resets').map((entry) => JSON.parse(entry.body!).token)).toEqual([tokenB]);
    });
    it('abandons a pending reset for the replaced capability and clears the form', async () => {
        let first: AbortSignal | undefined;
        respond(() => undefined);
        fetcher.mockImplementation((url: string, init?: RequestInit) => {
            if (new URL(url).pathname === '/api/auth/password-resets') {
                const body = JSON.parse(init!.body as string);
                if (body.token === token) { first = init?.signal ?? undefined; return new Promise<Response>(() => undefined); }
                seen.push({ path: '/api/auth/password-resets', href: window.location.href, body: init!.body as string });
                return Promise.resolve(json({ message: completed }));
            }
            return Promise.resolve(json(page));
        });
        await mount(`/reset-password#token=${token}`); await fillReset(); await submit();
        expect(first?.aborted).toBe(false);
        await openInSameTab(`/reset-password#token=${tokenB}`);
        expect(first?.aborted).toBe(true);
        expect(host.querySelector('button[type="submit"]')?.hasAttribute('disabled')).toBe(false);
        expect(host.querySelector<HTMLInputElement>('input[name="confirmPassword"]')?.value).toBe('');
        await fillReset(); await submit();
        expect(calls('/api/auth/password-resets').map((entry) => JSON.parse(entry.body!).token)).toEqual([tokenB]);
        expect(host.querySelector('h1')?.textContent).toBe('Password updated');
    });
    it('drops the held capability for a malformed new link but keeps it for a fragment without a token', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount(`/reset-password#token=${token}`);
        await openInSameTab('/reset-password#section');
        expect(window.location.hash).toBe(''); expect(host.querySelector('h1')?.textContent).toBe('Reset password');
        await openInSameTab('/reset-password#token=short');
        expect(window.location.hash).toBe(''); expect(host.querySelector('h1')?.textContent).toBe('This reset link is invalid or has expired.');
        expect(host.querySelector('form')).toBeNull(); expect(calls('/api/auth/password-resets')).toHaveLength(0);
    });
    it('checks the CT01 rule and confirmation locally before any request', async () => {
        respond(() => undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset('a'.repeat(14)); await submit();
        expect(document.activeElement).toBe(host.querySelector('input[name="password"]'));
        await fillReset(newPassword, `${newPassword}x`); await submit();
        expect(host.textContent).toContain('Passwords must match.'); expect(document.activeElement).toBe(host.querySelector('input[name="confirmPassword"]'));
        expect(calls('/api/auth/password-resets')).toHaveLength(0);
    });
    it('reports F14 failures, sends one request at a time and never replays', async () => {
        let release!: (value: Response) => void;
        respond((path) => path === '/api/auth/password-resets' ? new Promise<Response>((resolve) => { release = resolve; }) : undefined);
        await mount(`/reset-password#token=${token}`); await fillReset();
        await submit(); await submit();
        expect(calls('/api/auth/password-resets')).toHaveLength(1);
        expect(host.querySelector('button[type="submit"]')?.hasAttribute('disabled')).toBe(true);
        await act(async () => release(problem(500, 'Internal Server Error', '/api/auth/password-resets')));
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong on our side. Try again in a moment.');
        expect(document.activeElement).toBe(host.querySelector('[role="alert"]'));
        expect(calls('/api/auth/password-resets')).toHaveLength(1);
    });
    it('aborts an in-flight reset when the page is left', async () => {
        let signal: AbortSignal | undefined;
        respond(() => undefined);
        fetcher.mockImplementation((url: string, init?: RequestInit) => {
            if (new URL(url).pathname === '/api/auth/password-resets') { signal = init?.signal ?? undefined; return new Promise<Response>(() => undefined); }
            return Promise.resolve(json(page));
        });
        await mount(`/reset-password#token=${token}`); await fillReset(); await submit();
        expect(signal?.aborted).toBe(false);
        await go('/login');
        expect(signal?.aborted).toBe(true);
    });
});
