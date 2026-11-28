// @vitest-environment jsdom
import { act, StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { BrowserRouter, matchRoutes, Route, Routes, useNavigate } from 'react-router';
import { currentSection, isDestinationLive, notificationsAvailable, roleNavigation, roleStartPath, ROUTE_PATHS, WORKSPACE_ROUTES } from './routePaths';
import type { UserRole } from '../features/auth/types/authTypes';
import { ProtectedRoute } from './guards/ProtectedRoute';
import type { RouteObject } from 'react-router';
import type { NavigateFunction } from 'react-router';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRouter } from './index';
import { useNavigationGuard } from '../layouts/DashboardLayout';
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
    // The lazy landing illustration may mount during any direct-entry test, independently of import timing.
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.stubGlobal('DOMMatrixReadOnly', class { m22 = 1; });
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-25T10:00:00Z')); vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
    fetcher = vi.fn((url: string, _init?: RequestInit) => {
        const pathname = new URL(url).pathname;
        if (pathname === '/api/me/notifications/unread-count') return Promise.resolve(json({ unreadCount: 0 }));
        if (pathname === '/api/me/notifications') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
        if (pathname === '/api/instructor/courses') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
        return Promise.resolve(json(pathname.startsWith('/api/courses/') ? detail : page));
    });
    vi.stubGlobal('fetch', fetcher);
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
    // CT16.routes: Learner starts at /home, Instructor at /teach once Teaching home is routed, Administrator at /courses.
    it.each([['Learner', '/home'], ['Instructor', '/teach'], ['Administrator', '/courses']])('login sends a %s to %s for arbitrary supplied return targets', async (role, start) => {
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
it.each([['Learner', '/home'], ['Instructor', '/teach'], ['Administrator', '/courses']])('sends a signed-in %s from / to %s and points the logo there', async (role, start) => {
    fetcher.mockImplementation((url: string) => {
        const pathname = new URL(url).pathname;
        if (pathname === '/api/me/courses') return Promise.resolve(json(membershipPage()));
        if (pathname === '/api/me/notifications/unread-count') return Promise.resolve(json({ unreadCount: 0 }));
        if (pathname === '/api/me/notifications') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
        if (pathname === '/api/instructor/courses') return Promise.resolve(json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
        return Promise.resolve(json(page));
    });
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
    expect(fetcher.mock.calls.every(([url]) => ['/api/auth/login', '/api/courses', '/api/me/courses', '/api/me/notifications/unread-count'].includes(new URL(url).pathname))).toBe(true);
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
describe('FE34 theme preference across routes and the session', () => {
    const desktopMedia = (media: string) => ({ matches: media.includes('min-width:900px'), media, addEventListener: () => undefined, removeEventListener: () => undefined, addListener: () => undefined, removeListener: () => undefined });
    const unauthorized = () => new Response(JSON.stringify({ type: 'about:blank', title: 'Unauthorized', status: 401, detail: 'Authentication failed.', instance: '/api/auth/refresh' }), { status: 401, headers: { 'Content-Type': 'application/problem+json' } });
    const themeButton = () => host.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]');
    async function chooseTheme(label: 'System' | 'Light' | 'Dark') {
        await act(async () => themeButton()!.click());
        const item = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((node) => node.textContent?.startsWith(label))!;
        await act(async () => item.click());
        await act(async () => { document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); });
    }
    const themeTraffic = () => fetcher.mock.calls.filter(([url, init]) => /theme/i.test(String(url)) || /theme/i.test(String((init as RequestInit | undefined)?.body ?? '')));
    it('renders the Theme control in the stored scheme while the ADR014 restore runs', async () => {
        vi.stubGlobal('matchMedia', desktopMedia);
        localStorage.setItem('codemastery.theme', 'dark');
        let resolve!: (response: Response) => void;
        fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }));
        window.history.replaceState(null, '', '/profile');
        await act(async () => root.render(<App />));
        expect(host.textContent).toContain('Restoring your session…');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(host.querySelectorAll('button[aria-label^="Theme:"]')).toHaveLength(1);
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Dark');
        expect(host.textContent).not.toContain('Sign in');
        await act(async () => resolve(unauthorized()));
        expect(location.pathname).toBe('/login');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
    });
    it.each([['desktop', true], ['mobile', false]])('keeps one Theme control on the landing page while the restore runs (%s), then exactly one after it', async (_name, desktop) => {
        if (desktop) vi.stubGlobal('matchMedia', desktopMedia);
        vi.stubGlobal('ResizeObserver', class { observe() { /* test double */ } unobserve() { /* test double */ } disconnect() { /* test double */ } });
        localStorage.setItem('codemastery.theme', 'dark');
        let resolve!: (response: Response) => void;
        fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }));
        window.history.replaceState(null, '', '/');
        await act(async () => root.render(<App />));
        expect([...host.querySelectorAll('[role="status"]')].map((node) => node.textContent)).toContain('Restoring your session…');
        expect(host.querySelectorAll('button[aria-label^="Theme:"]')).toHaveLength(1);
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Dark');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(host.textContent).not.toContain('Sign in'); expect(host.textContent).not.toContain('Create account');
        await chooseTheme('Light'); // usable during the restore, without touching the session
        expect(fetcher).toHaveBeenCalledTimes(1);
        await act(async () => resolve(unauthorized()));
        expect(location.pathname).toBe('/');
        expect(host.querySelector('h1')?.textContent).toBe('Learn programming at the pace of what you actually know.');
        expect(host.querySelectorAll('button[aria-label^="Theme:"]')).toHaveLength(1);
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Light');
        expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('orders the landing header Catalogue → Theme → Sign in → Create account in the DOM, so tab and reading order follow (D13-2)', async () => {
        vi.stubGlobal('matchMedia', desktopMedia);
        vi.stubGlobal('ResizeObserver', class { observe() { /* test double */ } unobserve() { /* test double */ } disconnect() { /* test double */ } });
        fetcher.mockResolvedValueOnce(unauthorized());
        window.history.replaceState(null, '', '/');
        await act(async () => root.render(<App />));
        const nav = host.querySelector('header nav[aria-label="Main navigation"]')!;
        expect([...nav.querySelectorAll('a, button')].map((node) => node.getAttribute('aria-label')?.startsWith('Theme:') ? 'Theme' : node.textContent))
            .toEqual(['Catalogue', 'Theme', 'Sign in', 'Create account']);
    });
    it('keeps "Create account" out of the mobile drawer while the restore runs, with the drawer Theme control still there (F2)', async () => {
        vi.stubGlobal('ResizeObserver', class { observe() { /* test double */ } unobserve() { /* test double */ } disconnect() { /* test double */ } });
        let resolve!: (response: Response) => void;
        const held = new Promise<Response>((done) => { resolve = done; });
        fetcher.mockImplementation((url: string) => new URL(url).pathname === '/api/auth/refresh' ? held : Promise.resolve(json(page)));
        window.history.replaceState(null, '', '/courses');
        await act(async () => root.render(<App />));
        const drawer = async () => {
            const toggle = host.querySelector<HTMLButtonElement>('button[aria-label="Toggle navigation"]')!;
            if (toggle.getAttribute('aria-expanded') !== 'true') await act(async () => toggle.click());
            return document.querySelector<HTMLElement>('.MuiDrawer-paper')!;
        };
        const createAccount = (scope: ParentNode) => [...scope.querySelectorAll('a, button')].filter((node) => node.textContent === 'Create account');
        const during = await drawer();
        expect(during.querySelector('[role="radiogroup"][aria-labelledby]')).not.toBeNull();
        expect(createAccount(during)).toHaveLength(0);
        expect(createAccount(host)).toHaveLength(0);
        await act(async () => resolve(unauthorized()));
        expect(location.pathname).toBe('/courses');
        const after = await drawer();
        expect(createAccount(after)).toHaveLength(1);
        expect(after.querySelector('[role="radiogroup"]')).not.toBeNull();
    });
    it.each([['Learner', '/home'], ['Instructor', '/teach'], ['Administrator', '/courses']])('sends a restored %s from the landing restore view to %s with the theme unchanged', async (role, start) => {
        vi.stubGlobal('matchMedia', desktopMedia);
        localStorage.setItem('codemastery.theme', 'dark');
        let resolve!: (response: Response) => void;
        fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }));
        window.history.replaceState(null, '', '/');
        await act(async () => root.render(<App />));
        expect(host.textContent).toContain('Restoring your session…');
        await act(async () => resolve(json(loginResponse('Person A', role))));
        expect(location.pathname).toBe(start);
        expect(host.querySelectorAll('button[aria-label^="Theme:"]')).toHaveLength(1);
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
    });
    it('keeps a visitor\'s choice through sign-in, another account and sign-out without any request', async () => {
        vi.stubGlobal('matchMedia', desktopMedia);
        fetcher.mockResolvedValueOnce(unauthorized());
        window.history.replaceState(null, '', '/login');
        await act(async () => root.render(<App />));
        expect(location.pathname).toBe('/login');
        await chooseTheme('Dark');
        expect(localStorage.getItem('codemastery.theme')).toBe('dark');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        await formLogin('Person A');
        expect(location.pathname).toBe('/home');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Dark');
        await signOutFromMenu();
        expect(location.pathname).toBe('/login');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        await formLogin('Person B', 'Instructor');
        expect(location.pathname).toBe('/teach');
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Dark');
        await chooseTheme('Light');
        await signOutFromMenu();
        expect(localStorage.getItem('codemastery.theme')).toBe('light');
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(themeTraffic()).toEqual([]);
        expect(Object.keys(localStorage).filter((key) => key.startsWith('codemastery.theme'))).toEqual(['codemastery.theme']);
    });
    it('falls back to System for invalid or unreadable values and keeps a blocked choice in memory', async () => {
        vi.stubGlobal('matchMedia', (media: string) => ({ ...desktopMedia(media), matches: media.includes('min-width:900px') || media === '(prefers-color-scheme: dark)' }));
        localStorage.setItem('codemastery.theme', 'sepia');
        fetcher.mockResolvedValueOnce(unauthorized());
        window.history.replaceState(null, '', '/login');
        await act(async () => root.render(<App />));
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: System (currently Dark)');
        expect(document.documentElement.dataset.colorScheme).toBe('dark');
        expect(localStorage.getItem('codemastery.theme')).toBe('sepia'); // overwritten only by the next choice
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('Blocked.', 'SecurityError'); });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Blocked.', 'SecurityError'); });
        await chooseTheme('Light');
        expect(document.documentElement.dataset.colorScheme).toBe('light');
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: Light');
        expect(host.querySelector('[role="alert"]')).toBeNull();
        await act(async () => root.unmount());
        root = createRoot(host);
        window.history.replaceState(null, '', '/login');
        fetcher.mockResolvedValueOnce(unauthorized());
        await act(async () => root.render(<App />));
        expect(themeButton()?.getAttribute('aria-label')).toBe('Theme: System (currently Dark)');
    });
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
        expect(host.querySelector('button[type="submit"]')?.hasAttribute('aria-disabled')).toBe(false);
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
        const busy = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;
        expect(busy.getAttribute('aria-disabled')).toBe('true'); expect(busy.disabled).toBe(false);
        await act(async () => busy.click());
        expect(calls('/api/auth/password-resets')).toHaveLength(1);
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
    const field = (name: string) => host.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    /** Focus then leave a field, as a keyboard or pointer user does (React's onBlur listens to focusout). */
    async function leave(name: string) { await act(async () => { field(name).focus(); field(name).blur(); }); }
    const summary = () => [...host.querySelectorAll('.MuiAlert-root')].find((element) => element.textContent === 'Please check the supplied information.');
    it('re-validates the request email on blur only once it is in error, with a non-live summary', async () => {
        respond(() => undefined);
        await mount('/forgot-password');
        await change('email', 'missing-at'); await leave('email');
        expect(field('email').getAttribute('aria-invalid')).toBe('false'); expect(summary()).toBeUndefined();
        await submit();
        expect(field('email').getAttribute('aria-invalid')).toBe('true'); expect(document.activeElement).toBe(field('email'));
        expect(summary()?.getAttribute('role')).toBe('none'); expect(host.querySelector('[role="alert"]')).toBeNull();
        await change('email', 'still wrong@'); await leave('email');
        expect(field('email').getAttribute('aria-invalid')).toBe('true'); expect(host.textContent).toContain('Enter a valid ASCII email address.');
        await change('email', 'learner@example.com'); await leave('email');
        expect(field('email').getAttribute('aria-invalid')).toBe('false'); expect(host.textContent).not.toContain('Enter a valid ASCII email address.');
        expect(summary()).toBeUndefined(); expect(calls('/api/auth/password-reset-requests')).toHaveLength(0);
    });
    it('re-validates reset fields on blur, checking the confirmation against the current password', async () => {
        respond(() => undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset('short', 'different'); await submit();
        expect(document.activeElement).toBe(field('password')); expect(summary()).toBeTruthy();
        expect(field('password').getAttribute('aria-invalid')).toBe('true'); expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true');
        await change('password', 'still short'); await leave('password');
        expect(field('password').getAttribute('aria-invalid')).toBe('true'); // still too short
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true'); // still different
        await change('password', newPassword); await leave('password');
        expect(field('password').getAttribute('aria-invalid')).toBe('false');
        expect(host.textContent).toContain('At least 15 Unicode characters, at most 72 UTF-8 bytes.');
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true'); expect(summary()).toBeTruthy();
        await change('confirmPassword', newPassword); await leave('confirmPassword');
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('false'); expect(host.textContent).not.toContain('Passwords must match.');
        expect(summary()).toBeUndefined(); expect(calls('/api/auth/password-resets')).toHaveLength(0);
    });
    it('clears a confirmation error when the password is changed to match it, and never adds one on blur', async () => {
        respond(() => undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset(newPassword, `${newPassword}x`); await submit();
        expect(document.activeElement).toBe(field('confirmPassword'));
        await change('password', `${newPassword}x`); await leave('password');
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('false'); expect(summary()).toBeUndefined();
        await change('confirmPassword', 'no longer equal at all'); await leave('confirmPassword'); await leave('password');
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('false');
    });
    it('applies a pointer-caused blur re-validation only after the press, so the submit click is not lost', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset(newPassword, 'different'); await submit();
        expect(document.activeElement).toBe(field('confirmPassword')); expect(summary()).toBeTruthy();
        await change('confirmPassword', newPassword);
        const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;
        // Pressing "Reset password" blurs the corrected field first; the layout must not change before the click.
        await act(async () => { button.dispatchEvent(new Event('pointerdown', { bubbles: true })); field('confirmPassword').blur(); });
        expect(summary()).toBeTruthy(); expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true');
        await act(async () => { button.dispatchEvent(new Event('pointerup', { bubbles: true })); button.click(); });
        await act(async () => { vi.advanceTimersByTime(0); });
        expect(calls('/api/auth/password-resets')).toHaveLength(1); expect(host.querySelector('h1')?.textContent).toBe('Password updated');
    });
    it('applies a pointer-caused blur re-validation after a press elsewhere is released', async () => {
        respond(() => undefined);
        await mount('/forgot-password');
        await change('email', 'missing-at'); await submit();
        await change('email', 'learner@example.com');
        await act(async () => { document.body.dispatchEvent(new Event('pointerdown', { bubbles: true })); field('email').blur(); });
        expect(field('email').getAttribute('aria-invalid')).toBe('true');
        await act(async () => { document.body.dispatchEvent(new Event('pointerup', { bubbles: true })); vi.advanceTimersByTime(0); });
        expect(field('email').getAttribute('aria-invalid')).toBe('false'); expect(summary()).toBeUndefined();
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(0);
    });
    it('keeps focus on the busy submit button, announces the request once and moves focus with the outcome', async () => {
        let release!: (value: Response) => void;
        respond((path) => path === '/api/auth/password-resets' ? new Promise<Response>((resolve) => { release = resolve; }) : undefined);
        await mount(`/reset-password#token=${token}`); await fillReset();
        const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;
        await act(async () => { button.focus(); button.click(); });
        expect(document.activeElement).toBe(button); expect(button.textContent).toBe('Resetting…');
        expect(host.querySelector('[role="status"]')?.textContent).toBe('Resetting your password…');
        await act(async () => release(problem(500, 'Internal Server Error', '/api/auth/password-resets')));
        expect(document.activeElement).toBe(host.querySelector('[role="alert"]'));
        expect(host.querySelector('main span[role="status"]')?.textContent).toBe('');
        // "Try again" disappears with its alert; focus stays on the busy submit button rather than the page.
        respond((path) => path === '/api/auth/password-resets' ? new Promise<Response>((resolve) => { release = resolve; }) : undefined);
        const retry = [...host.querySelectorAll<HTMLButtonElement>('button')].find((element) => element.textContent === 'Try again')!;
        await act(async () => { retry.focus(); retry.click(); });
        expect(host.querySelector('[role="alert"]')).toBeNull();
        expect(document.activeElement).toBe(host.querySelector('button[type="submit"]')); expect(document.activeElement).not.toBe(document.body);
        await act(async () => release(json({ message: completed })));
        expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
    it('keeps focus and announces the pending request on the request page, then focuses the acknowledgement', async () => {
        let release!: (value: Response) => void;
        respond((path) => path === '/api/auth/password-reset-requests' ? new Promise<Response>((resolve) => { release = resolve; }) : undefined);
        await mount('/forgot-password'); await change('email', 'learner@example.com');
        const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;
        await act(async () => { button.focus(); button.click(); });
        expect(document.activeElement).toBe(button); expect(button.getAttribute('aria-disabled')).toBe('true');
        expect(host.querySelector('[role="status"]')?.textContent).toBe('Sending the reset link…');
        await act(async () => { button.click(); });
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(1);
        await act(async () => release(json({ message: requested }, 202)));
        expect(document.activeElement?.textContent).toBe(requested);
    });
    /**
     * A tap as browsers deliver it: the pointer is released first, then, in a later task, the compatibility
     * `mousedown` moves focus (blurring the corrected field), followed by `mouseup` and `click`. jsdom has no layout, so
     * the hit test is modelled: once the summary has disappeared, the button has moved up and a tap on its lower half
     * lands on `main` instead (as measured in Chrome and WebKit touch emulation).
     */
    async function tapLowerHalf(button: HTMLButtonElement, blurred: HTMLInputElement) {
        const hit = () => summary() ? button : host.querySelector('main')!;
        await act(async () => {
            button.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerType: 'touch' }));
            button.dispatchEvent(new Event('touchstart', { bubbles: true }));
            button.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerType: 'touch' }));
            button.dispatchEvent(new Event('touchend', { bubbles: true }));
        });
        await act(async () => { vi.advanceTimersByTime(0); }); // the task boundary before the compatibility mouse events
        // React commits a blur's update before the next browser event, so `mouseup` is dispatched in a separate act().
        await act(async () => { button.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); blurred.blur(); });
        await act(async () => {
            const target = hit();
            target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
            if (target === button) button.click(); else target.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });
        await act(async () => { vi.advanceTimersByTime(0); });
    }
    it('submits on the first tap after a correction: a touch blur re-validates only after the tap (reset page)', async () => {
        respond((path) => path === '/api/auth/password-resets' ? json({ message: completed }) : undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset(newPassword, 'different'); await submit();
        expect(summary()).toBeTruthy();
        await change('confirmPassword', newPassword);
        await tapLowerHalf(host.querySelector<HTMLButtonElement>('button[type="submit"]')!, field('confirmPassword'));
        expect(calls('/api/auth/password-resets')).toHaveLength(1);
        expect(host.querySelector('h1')?.textContent).toBe('Password updated');
    });
    it('submits on the first tap after a correction on the request page', async () => {
        respond((path) => path === '/api/auth/password-reset-requests' ? json({ message: requested }, 202) : undefined);
        await mount('/forgot-password');
        await change('email', 'missing-at'); await submit();
        await change('email', 'learner@example.com');
        await tapLowerHalf(host.querySelector<HTMLButtonElement>('button[type="submit"]')!, field('email'));
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(1);
        expect(host.textContent).toContain(requested);
    });
    it('re-validates a blur from a tap that does not submit once the tap ends, with the current value', async () => {
        respond(() => undefined);
        await mount('/forgot-password');
        await change('email', 'missing-at'); await submit();
        await act(async () => { document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); field('email').blur(); });
        expect(field('email').getAttribute('aria-invalid')).toBe('true'); // still pressed: nothing moves yet
        await change('email', 'learner@example.com'); // the value read when the queue runs, not the one at blur time
        await act(async () => { document.body.dispatchEvent(new MouseEvent('mouseup', { bubbles: true })); vi.advanceTimersByTime(0); });
        expect(field('email').getAttribute('aria-invalid')).toBe('false'); expect(summary()).toBeUndefined();
        expect(calls('/api/auth/password-reset-requests')).toHaveLength(0);
    });
    it.each([
        ['the window losing focus', () => window.dispatchEvent(new Event('blur'))],
        ['keyboard input', () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))],
        ['a context menu', () => document.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true }))],
        ['a cancelled pointer', () => { document.dispatchEvent(new PointerEvent('pointercancel', { bubbles: true })); vi.advanceTimersByTime(0); }],
    ])('applies a re-validation held by a press whose release never arrives after %s', async (_case, interrupt) => {
        respond(() => undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset(newPassword, 'different'); await submit();
        await change('confirmPassword', newPassword);
        await act(async () => { document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); field('confirmPassword').blur(); });
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true');
        await act(async () => { interrupt(); });
        expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('false'); expect(summary()).toBeUndefined();
    });
    it('hides an earlier F14 failure when a later submit fails the local check, and shows the next failure', async () => {
        respond((path) => path === '/api/auth/password-resets' ? problem(500, 'Internal Server Error', path) : undefined);
        await mount(`/reset-password#token=${token}`);
        await fillReset(); await submit();
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong on our side.');
        await change('password', 'short'); await submit();
        expect(host.querySelector('[role="alert"]')).toBeNull(); expect(summary()).toBeTruthy();
        expect(document.activeElement).toBe(field('password'));
        await change('password', newPassword); await submit();
        expect(calls('/api/auth/password-resets')).toHaveLength(2);
        expect(host.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong on our side.');
    });
    it('keeps the pending announcement outside the busy form', async () => {
        respond((path) => path === '/api/auth/password-reset-requests' ? new Promise<Response>(() => undefined) : undefined);
        await mount('/forgot-password'); await change('email', 'learner@example.com'); await submit();
        expect(host.querySelector('form')?.getAttribute('aria-busy')).toBe('true');
        expect(host.querySelector('form [role="status"]')).toBeNull();
        expect(host.querySelector('[aria-busy="true"] [role="status"]')).toBeNull();
        expect(host.querySelector('main span[role="status"]')?.textContent).toBe('Sending the reset link…');
    });
});

describe('FE32 Instructor navigation and parent-aware active section (CT16.routes)', () => {
    it('shows Teaching and Catalogue for Instructors and keeps Teaching active across /teach subroutes', async () => {
        await mount('/courses');
        await login('Teacher', 'Instructor');
        expect([...host.querySelectorAll('header nav a')].map((link) => link.textContent)).toEqual(['Teaching', 'Grading', 'Catalogue']);
        await go('/teach');
        expect(host.querySelector('main h1')?.textContent).toBe('Teaching');
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('Teaching');
        await go('/teach/courses/new');
        expect(host.querySelector('main h1')?.textContent).toBe('Create course');
        expect(host.querySelector('header nav [aria-current="page"]')?.textContent).toBe('Teaching');
    });

    it.each(['Learner', 'Administrator'] as const)('denies %s on /teach and /teach/courses/new without issuing Instructor API calls', async (role) => {
        await mount('/courses');
        await login('Other', role);
        expect(host.querySelector('header a[href="/teach"]')).toBeNull();
        fetcher.mockClear();
        await go('/teach');
        expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
        await go('/teach/courses/new');
        expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
        expect(fetcher.mock.calls.map(([url]) => new URL(url).pathname).filter((path) => path !== '/api/me/notifications/unread-count')).toEqual([]);
    });
});


// Full future activation is a test fixture only. These routes are never passed to AppRouter.
const learnerPaths = ['/home', '/my-courses', '/courses/:courseId/learn', '/courses/:courseId/lessons/:lessonId',
    '/courses/:courseId/diagnostic', '/courses/:courseId/mastery', '/courses/:courseId/assessments',
    '/courses/:courseId/quizzes/:quizId', '/courses/:courseId/quizzes/:quizId/attempt', '/courses/:courseId/quizzes/:quizId/history',
    '/courses/:courseId/attempts/:attemptId', '/courses/:courseId/assignments/:assignmentId'];
const instructorPaths = ['/teach', '/teach/courses/new', '/teach/courses/:courseId', '/teach/courses/:courseId/content',
    '/teach/courses/:courseId/settings', '/teach/courses/:courseId/lessons/:lessonId', '/teach/courses/:courseId/skills',
    '/teach/courses/:courseId/assessments', '/teach/courses/:courseId/quizzes/:quizId', '/teach/courses/:courseId/assignments/:assignmentId',
    '/teach/courses/:courseId/qa', '/teach/courses/:courseId/learners', '/teach/courses/:courseId/learners/:learnerId',
    '/teach/grading', '/teach/grading/:submissionId', '/teach/learners'];
const adminPaths = ['/admin', '/admin/users', '/admin/courses', '/admin/reports', '/admin/audit'];
const activatedRoutes: RouteObject[] = [
    { path: '/courses' }, { path: '/courses/:courseId' },
    ...([['Learner', learnerPaths], ['Instructor', instructorPaths], ['Administrator', adminPaths]] as const)
        .map(([role, paths]) => ({ handle: { roles: [role] }, children: paths.map((path) => ({ path })) })),
    { path: '/notifications', handle: { roles: ['Learner', 'Instructor'] } },
];
const concrete = (path: string) => path.replace(/:[A-Za-z]+/g, '00000000-0000-4000-8000-000000000001');
describe('FE32 complete model and actual route activation', () => {
    it.each([
        ['Learner', ['Home', 'Catalogue', 'My courses']],
        ['Instructor', ['Teaching', 'Grading', 'Learners', 'Catalogue']],
        ['Administrator', ['Overview', 'Users', 'Courses', 'Reports', 'Audit log', 'Catalogue']],
    ] as const)('keeps the approved %s order, icons and isolation when all destinations are delivered', (role, labels) => {
        const items = roleNavigation(role, activatedRoutes);
        expect(items.map(({ label }) => label)).toEqual(labels);
        expect(items.every(({ icon, id, href }) => !!icon && id === href)).toBe(true);
        expect(items.some(({ href }) => href === '/notifications')).toBe(false);
    });
    it.each([
        ['Learner', ['Home', 'Catalogue', 'My courses']], ['Instructor', ['Teaching', 'Grading', 'Catalogue']], ['Administrator', ['Catalogue']],
    ] as const)('filters %s against the actual delivered route tree', (role, labels) => {
        expect(roleNavigation(role).map(({ label }) => label)).toEqual(labels);
        expect(notificationsAvailable(role)).toBe(role !== 'Administrator');
        expect(roleNavigation(undefined).map(({ label }) => label)).toEqual(['Catalogue']);
    });
    it('activates future start pages by registration alone, while constants and preview do not activate them', () => {
        expect(roleStartPath('Instructor', [])).toBe('/courses');
        expect(roleStartPath('Administrator')).toBe('/courses');
        expect(roleStartPath('Instructor', activatedRoutes)).toBe('/teach');
        expect(roleStartPath('Administrator', activatedRoutes)).toBe('/admin');
        expect(roleStartPath('Learner', [])).toBe('/home');
        expect(ROUTE_PATHS.ADMIN).toBe('/admin');
        expect(isDestinationLive('/admin', 'Administrator')).toBe(false);
        expect(roleStartPath('Administrator', [{ path: '/admin', handle: { roles: ['Instructor'] } }])).toBe('/courses');
        expect(roleNavigation('Administrator', [{ path: '/admin/users', handle: { roles: ['Administrator'] } }]).map(({ label }) => label)).toEqual(['Users']);
    });
    it.each(['Learner', 'Instructor', 'Administrator'] as const)('keeps the bell utility activation separate for %s', (role) => {
        expect(notificationsAvailable(role, activatedRoutes)).toBe(role !== 'Administrator');
        expect(currentSection('/notifications?unread=true#top', role, activatedRoutes)).toBe(role !== 'Administrator' ? '/notifications' : undefined);
    });
    it.each([
        ...learnerPaths.map((path) => [path, 'Learner', path === '/home' ? '/home' : '/my-courses']),
        ...instructorPaths.map((path) => [path, 'Instructor', path.startsWith('/teach/grading') ? '/teach/grading' : path === '/teach/learners' ? path : '/teach']),
        ...adminPaths.map((path) => [path, 'Administrator', path]),
        ['/courses', 'Learner', '/courses'], ['/courses/:courseId', 'Learner', '/courses'], ['/courses/:courseId', 'Instructor', '/courses'],
    ])('maps the approved %s screen to exactly %s / %s', (path, role, expected) => {
        const url = concrete(path);
        expect(currentSection(url, role as UserRole, activatedRoutes)).toBe(expected);
        expect(currentSection(`${url.toUpperCase()}/?view=one#details`, role as UserRole, activatedRoutes)).toBe(expected);
    });
    it.each(['/teach/courses-extra', '/teach/courses', '/teach/grading-extra', '/admin/users-extra', '/admin/unknown',
        '/courses/one/lessons', '/courses/one/learn/extra', '/courses/one/quizzes/two/unknown', '/courses/one/unknown', '/homepage', '/my-courses-extra'])
    ('never marks unknown or segment-prefix path %s current', (path) => {
        for (const role of ['Learner', 'Instructor', 'Administrator'] as const) expect(currentSection(path, role, activatedRoutes)).toBeUndefined();
    });
    it.each(['/TEACH/', '/%74each', '/teach/courses/a%2Fb/settings/', '/courses/a%2Fb', '/%63ourses/one', '/courses/%ZZ'])
    ('uses router-equivalent matching for %s', (path) => {
        const role = path.toLowerCase().includes('teach') || path.includes('%74') ? 'Instructor' : 'Learner';
        expect(isDestinationLive(path, role)).toBe(matchRoutes(WORKSPACE_ROUTES, path) !== null);
        expect(currentSection(path, role)).toBe(role === 'Instructor' ? '/teach' : '/courses');
    });
    it('never leaks an active item to another role or highlights an inactive feature', () => {
        for (const role of ['Learner', 'Administrator'] as const) expect(currentSection('/teach/courses/one', role)).toBeUndefined();
        expect(currentSection('/courses/one/learn', 'Learner')).toBeUndefined();
        expect(currentSection('/teach/grading', 'Instructor')).toBe('/teach/grading');
        expect(currentSection('/teach/grading/one', 'Instructor')).toBe('/teach/grading');
        expect(currentSection('/admin', 'Administrator')).toBeUndefined();
        expect(currentSection('/courses/one')).toBe('/courses');
        expect(currentSection('/home')).toBeUndefined();
    });
    it.each(['/admin', '/admin/users', '/admin/courses', '/admin/reports', '/admin/audit', '/teach/learners', '/teach/courses/one/learners'])
    ('leaves inactive %s unregistered for anonymous visitors and every authenticated role', async (path) => {
        await mount(path);
        expect(location.pathname).toBe(path); expect(host.querySelector('main h1')?.textContent).toBe('Page not found');
        for (const role of ['Learner', 'Instructor', 'Administrator']) {
            await login('Role fixture', role); fetcher.mockClear(); await go(path);
            expect(location.pathname).toBe(path); expect(host.querySelector('main h1')?.textContent).toBe('Page not found');
            expect(host.querySelectorAll('header nav [aria-current]')).toHaveLength(0); expect(fetcher.mock.calls.map(([url]) => new URL(url).pathname).filter((path) => path !== '/api/me/notifications/unread-count')).toEqual([]);
        }
    });
    it('replaces all menu and active state on session role replacement, without privilege inheritance', async () => {
        await mount('/courses'); await login('A', 'Learner'); await go('/home');
        expect(host.querySelector('header nav [aria-current]')?.textContent).toBe('Home');
        await login('B', 'Instructor');
        expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
        expect([...host.querySelectorAll('header nav a')].map((n) => n.textContent)).toEqual(['Teaching', 'Grading', 'Catalogue']);
        expect(host.querySelector('header nav [aria-current]')).toBeNull();
        await login('C', 'Administrator');
        expect([...host.querySelectorAll('header nav a')].map((n) => n.textContent)).toEqual(['Catalogue']);
        expect(host.querySelector('header a[aria-label="CodeMastery home"]')?.getAttribute('href')).toBe('/courses');
    });
    it.each(['Learner', 'Instructor', 'Administrator'])('a pending Profile return wins once for %s sign-in', async (role) => {
        await mount('/PROFILE/?returnTo=https://outside.test#ignored'); await formLogin('A', role);
        expect(location.pathname).toBe('/profile'); expect(auth.consumeReturn()).toBe(roleStartPath(role as UserRole));
    });
    it('the landing redirect consumes a pending Profile return once, including StrictMode', async () => {
        window.history.replaceState(null, '', '/courses');
        await act(async () => root.render(<StrictMode><ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><Probe /><AppRouter /></AuthProvider></BrowserRouter></ThemeProvider></StrictMode>));
        await login('A', 'Instructor'); auth.rememberProfile();
        await go('/');
        expect(location.pathname).toBe('/profile'); expect(auth.consumeReturn()).toBe('/teach');
    });
    it('the logo exposes the pending Profile target and consumes it only when followed', async () => {
        await mount('/courses'); await login('A', 'Instructor'); auth.rememberProfile(); await go('/teach');
        const brand = host.querySelector<HTMLAnchorElement>('header a[aria-label="CodeMastery home"]')!;
        expect(brand.getAttribute('href')).toBe('/profile');
        await act(async () => brand.click());
        expect(location.pathname).toBe('/profile'); expect(auth.consumeReturn()).toBe('/teach');
    });
});

describe('FE32 registered role guard composition (test-only Administrator screen)', () => {
    const mounted = vi.fn();
    function PersonalScreen() { useEffect(() => { mounted(); }, []); return <h1>Delivered Administrator fixture</h1>; }
    async function mountRegistered(restoring = false) {
        mounted.mockClear(); window.history.replaceState(null, '', '/admin');
        await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider refreshSessions={restoring}><Probe /><Routes>
            <Route element={<ProtectedRoute roles={['Administrator']} />}><Route path="/admin" element={<PersonalScreen />} /></Route>
            <Route path="/login" element={<h1>Sign in fixture</h1>} />
            <Route path="*" element={<h1>Page not found fixture</h1>} />
        </Routes></AuthProvider></BrowserRouter></ThemeProvider>));
    }
    it('redirects an anonymous registered visit with replace and captures no return', async () => {
        await mountRegistered(); expect(location.pathname).toBe('/login'); expect(mounted).not.toHaveBeenCalled();
        await login('A', 'Administrator'); expect(auth.consumeReturn()).toBe('/courses');
    });
    it.each(['Learner', 'Instructor'])('denies %s without mounting personal content or starting its effects', async (role) => {
        await mountRegistered(); await login('Wrong role', role); fetcher.mockClear(); await go('/admin');
        expect(host.textContent).toContain("You don't have access to this page"); expect(host.textContent).toContain('administrators');
        expect(mounted).not.toHaveBeenCalled(); expect(fetcher).not.toHaveBeenCalled();
    });
    it('admits only the matching role and invalidates it on a session replacement', async () => {
        await mountRegistered(); await login('Administrator', 'Administrator'); await go('/admin');
        expect(mounted).toHaveBeenCalledTimes(1); await login('Instructor', 'Instructor');
        expect(host.textContent).not.toContain('Delivered Administrator fixture'); expect(host.textContent).toContain("You don't have access");
    });
    it('waits through unresolved restoration and never registers unknown children as protected pages', async () => {
        let resolve!: (value: Response) => void;
        fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }));
        await mountRegistered(true); expect(location.pathname).toBe('/admin'); expect(host.textContent).toContain('Restoring your session…');
        expect(mounted).not.toHaveBeenCalled(); await act(async () => resolve(json(loginResponse('Admin', 'Administrator'))));
        expect(location.pathname).toBe('/admin'); expect(mounted).toHaveBeenCalledTimes(1);
        fetcher.mockClear(); await go('/admin/unknown'); expect(host.textContent).toContain('Page not found fixture'); expect(fetcher).not.toHaveBeenCalled();
    });
});


describe('FE32 role groups cover every delivered Instructor screen', () => {
    it.each(['/teach', '/teach/courses/new', '/teach/courses/one', '/teach/courses/one/content', '/teach/courses/one/skills',
        '/teach/courses/one/assessments', '/teach/courses/one/settings', '/teach/courses/one/quizzes/one', '/teach/courses/one/assignments/one'])
    ('denies both other roles on %s before any feature request', async (path) => {
        await mount('/courses');
        for (const role of ['Learner', 'Administrator']) {
            await login('Other role', role); fetcher.mockClear(); await go(path);
            expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page");
            expect(host.querySelector('header nav [aria-current]')).toBeNull(); expect(fetcher.mock.calls.map(([url]) => new URL(url).pathname).filter((path) => path !== '/api/me/notifications/unread-count')).toEqual([]);
        }
    });
});


it('a cancelled editor logo navigation and a modified click never consume the pending Profile return', async () => {
    let proceed: (() => void) | undefined;
    function DirtyEditorFixture() {
        useNavigationGuard((next) => { proceed = next; return true; });
        return <h1>Dirty editor fixture</h1>;
    }
    window.history.replaceState(null, '', '/courses');
    await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><Probe />
        <AppRouter catalogue={<DirtyEditorFixture />} /></AuthProvider></BrowserRouter></ThemeProvider>));
    await login('Teacher', 'Instructor'); auth.rememberProfile(); await go('/courses?draft=1');
    const brand = host.querySelector<HTMLAnchorElement>('header a[aria-label="CodeMastery home"]')!;
    const modified = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
    await act(async () => { brand.dispatchEvent(modified); });
    expect(modified.defaultPrevented).toBe(false); expect(proceed).toBeUndefined(); expect(auth.startPath).toBe('/profile');
    await act(async () => brand.click());
    expect(location.pathname).toBe('/courses'); expect(auth.startPath).toBe('/profile'); expect(proceed).toBeTypeOf('function');
    await act(async () => proceed!());
    expect(location.pathname).toBe('/profile'); expect(auth.startPath).toBe('/teach');
});


describe('B1-F02 navigation-scoped delayed heading focus', () => {
    const deferredHeading = () => {
        let resolve!: () => void;
        const promise = new Promise<void>((done) => { resolve = done; });
        function Fixture() {
            const [ready, setReady] = useState(false);
            useEffect(() => { let alive = true; void promise.then(() => { if (alive) setReady(true); }); return () => { alive = false; }; }, []);
            return <><input aria-label="Work in progress" />{ready && <h1>Delayed route heading</h1>}</>;
        }
        return { Fixture, resolve };
    };
    async function mountDeferred(strict = false) {
        const state = deferredHeading();
        window.history.replaceState(null, '', '/courses');
        const app = <ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><Probe /><AppRouter catalogue={<state.Fixture />} /></AuthProvider></BrowserRouter></ThemeProvider>;
        await act(async () => root.render(strict ? <StrictMode>{app}</StrictMode> : app));
        return state;
    }
    it.each([false, true])('focuses the first delayed heading once, StrictMode=%s', async (strict) => {
        const state = await mountDeferred(strict);
        await act(async () => state.resolve());
        const heading = host.querySelector('main h1')!;
        expect(document.activeElement).toBe(heading);
        const input = host.querySelector<HTMLInputElement>('input[aria-label="Work in progress"]')!;
        input.focus();
        await act(async () => { heading.append(document.createTextNode(' refreshed')); });
        expect(document.activeElement).toBe(input);
    });
    it.each(['focusin', 'pointerdown', 'keydown', 'input'] as const)('respects user %s before the response', async (event) => {
        const state = await mountDeferred();
        const input = host.querySelector<HTMLInputElement>('input[aria-label="Work in progress"]')!;
        if (event === 'focusin') input.focus();
        else input.dispatchEvent(new Event(event, { bubbles: true }));
        await act(async () => state.resolve());
        expect(document.activeElement).not.toBe(host.querySelector('main h1'));
        if (event === 'focusin') expect(document.activeElement).toBe(input);
    });
    it('cancels the intent on query-only navigation', async () => {
        const state = await mountDeferred(); await go('/courses?q=next');
        await act(async () => state.resolve());
        expect(document.activeElement).not.toBe(host.querySelector('main h1'));
    });
    it('cannot focus a superseded route after navigation away', async () => {
        const state = await mountDeferred(); await go('/missing');
        const heading = host.querySelector('main h1'); expect(document.activeElement).toBe(heading);
        await act(async () => state.resolve());
        expect(host.textContent).not.toContain('Delayed route heading'); expect(document.activeElement).toBe(heading);
    });
    it('cancels a pending intent when an installed session is replaced', async () => {
        const state = await mountDeferred(); await login('First session', 'Instructor');
        await act(async () => state.resolve());
        expect(document.activeElement).not.toBe(host.querySelector('main h1'));
    });
    it('focuses the actual owned Course heading when its GET completes', async () => {
        let resolve!: (value: Response) => void;
        fetcher.mockImplementation((url: string) => {
            const path = new URL(url).pathname;
            if (path === `/api/instructor/courses/${id}`) return new Promise<Response>((done) => { resolve = done; });
            if (path.endsWith('/skill-graph')) return Promise.resolve(json({ courseId: id, skills: [], prerequisites: [], mappings: [] }));
            return Promise.resolve(json(page));
        });
        await mount('/courses'); await login('Instructor', 'Instructor'); await go(`/teach/courses/${id}`);
        expect(host.querySelector('main h1')).toBeNull();
        await act(async () => resolve(json({ id, title: 'Delayed owned Course', description: 'Synthetic course', publicationStatus: 'hidden', prerequisiteDescription: null, chapters: [] })));
        expect(host.querySelector('main h1')?.textContent).toBe('Delayed owned Course');
        expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
});

describe('FE28 production activation and shell integration', () => {
    it.each(['Learner', 'Instructor'] as const)('delivers notifications and a current header bell for %s without marking on open', async (role) => {
        await mount('/courses'); await login('Notice reader', role); fetcher.mockClear();
        const bell = host.querySelector<HTMLAnchorElement>('header a[href="/notifications"]')!; expect(bell).not.toBeNull();
        await act(async () => bell.click()); expect(location.pathname).toBe('/notifications'); expect(host.querySelector('main h1')?.textContent).toBe('Notifications');
        expect(host.querySelector('header a[href="/notifications"]')?.getAttribute('aria-current')).toBe('page');
        expect(host.querySelector('header nav [aria-current]')).toBeNull();
        expect(fetcher.mock.calls.map(([url, init]) => [new URL(url).pathname, init.method])).toEqual(expect.arrayContaining([
            ['/api/me/notifications', 'GET'], ['/api/me/notifications/unread-count', 'GET'],
        ]));
        expect(fetcher.mock.calls.every(([, init]) => init.method === 'GET')).toBe(true);
    });
    it('redirects anonymous deep links to sign-in without a notification request or arbitrary return capture', async () => {
        await mount('/notifications?returnTo=https://outside.test'); expect(location.pathname).toBe('/login'); expect(fetcher).not.toHaveBeenCalled(); expect(auth.consumeReturn()).toBe('/courses');
    });
    it('denies Administrator while retaining its session, hides the bell and never requests notifications', async () => {
        await mount('/courses'); await login('Administrator', 'Administrator'); fetcher.mockClear(); await go('/notifications');
        expect(host.querySelector('main h1')?.textContent).toBe("You don't have access to this page"); expect(auth.isAuthenticated).toBe(true);
        expect(host.querySelector('header a[href="/notifications"]')).toBeNull(); expect(fetcher).not.toHaveBeenCalled();
    });
    it('waits for session restoration before mounting the notification page and issuing protected reads', async () => {
        let release!: (value: Response) => void;
        fetcher.mockImplementation((url: string) => new URL(url).pathname === '/api/auth/refresh' ? new Promise<Response>((done) => { release = done; })
            : Promise.resolve(json(new URL(url).pathname.endsWith('unread-count') ? { unreadCount: 0 } : { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })));
        window.history.replaceState(null, '', '/notifications');
        await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider refreshSessions><Probe /><AppRouter /></AuthProvider></BrowserRouter></ThemeProvider>));
        expect(host.textContent).toContain('Restoring your session'); expect(fetcher.mock.calls.every(([url]) => new URL(url).pathname === '/api/auth/refresh')).toBe(true);
        await act(async () => release(json(loginResponse('Restored learner')))); expect(location.pathname).toBe('/notifications'); expect(host.querySelector('main h1')?.textContent).toBe('Notifications');
    });
    it('preserves dirty-editor interception for a plain bell click and native modified clicks', async () => {
        let proceed: (() => void) | undefined;
        function DirtyEditor() { useNavigationGuard((next) => { proceed = next; return true; }); return <h1>Unsaved draft</h1>; }
        window.history.replaceState(null, '', '/courses');
        await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><Probe /><AppRouter catalogue={<DirtyEditor />} /></AuthProvider></BrowserRouter></ThemeProvider>));
        await login('Teacher', 'Instructor'); const bell = host.querySelector<HTMLAnchorElement>('header a[href="/notifications"]')!;
        const modified = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true }); await act(async () => bell.dispatchEvent(modified));
        expect(modified.defaultPrevented).toBe(false); expect(proceed).toBeUndefined();
        const plain = new MouseEvent('click', { bubbles: true, cancelable: true }); await act(async () => bell.dispatchEvent(plain));
        expect(plain.defaultPrevented).toBe(true); expect(location.pathname).toBe('/courses'); expect(proceed).toBeTypeOf('function');
        expect(fetcher.mock.calls.some(([url]) => new URL(url).pathname === '/api/me/notifications')).toBe(false);
        await act(async () => proceed!()); expect(location.pathname).toBe('/notifications'); expect(host.querySelector('main h1')?.textContent).toBe('Notifications');
    });
});

describe('FE11 production activation', () => {
    const assignmentPath = `/courses/${id}/assignments/${id}`, listPath = `/courses/${id}/assessments`;
    function assignmentResponses() {
        const original = fetcher.getMockImplementation()! as (url: string, init?: RequestInit) => Promise<Response>;
        fetcher.mockImplementation((url:string,init?:RequestInit) => {
            const path = new URL(url).pathname;
            if (path === `/api${assignmentPath}`) return Promise.resolve(json({id,courseId:id,lessonId:null,title:'Production assignment',instructions:'**Actual decoded instructions**',deadlineAt:'2026-12-01T00:00:00Z',open:true,submission:null}));
            if (path === `/api/courses/${id}/assignments`) return Promise.resolve(json({content:[{id,courseId:id,lessonId:null,title:'Production assignment',deadlineAt:'2026-12-01T00:00:00Z',open:true,submissionStatus:'none'}],page:0,size:20,totalElements:1,totalPages:1}));
            return original(url,init);
        });
    }
    it.each([assignmentPath,listPath])('anonymous direct entry %s requires login',async path=>{await mount(path);expect(location.pathname).toBe('/login');expect(fetcher).not.toHaveBeenCalled();});
    it.each(['Instructor','Administrator'])('excludes %s from S10 and S13 without protected reads',async role=>{
        await mount('/login');await login('Other role',role);fetcher.mockClear();await go(assignmentPath);expect(host.textContent).toContain("You don't have access to this page");await go(listPath);expect(host.textContent).toContain("You don't have access to this page");expect(fetcher.mock.calls.some(([url])=>new URL(url).pathname.includes('/assignments'))).toBe(false);
    });
    it('production list handoff reaches decoded S13 with My courses navigation and heading focus',async()=>{
        await mount('/login');await login();assignmentResponses();await go(listPath);expect(host.textContent).toContain('Production assignment');
        expect(currentSection(listPath,'Learner')).toBe('/my-courses');expect(currentSection(assignmentPath,'Learner')).toBe('/my-courses');
        await act(async()=>host.querySelector<HTMLAnchorElement>(`a[href="${assignmentPath}"]`)!.click());expect(location.pathname).toBe(assignmentPath);
        expect(host.querySelector('main h1')?.textContent).toBe('Production assignment');expect(host.querySelector('strong')?.textContent).toBe('Actual decoded instructions');expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
    it('invalid assignment UUID causes no production assignment request',async()=>{await mount('/login');await login();fetcher.mockClear();await go(`/courses/${id}/assignments/bad`);expect(host.textContent).toContain('Invalid assignment link.');expect(fetcher.mock.calls.some(([url])=>new URL(url).pathname.includes('/assignments/'))).toBe(false);});
});
