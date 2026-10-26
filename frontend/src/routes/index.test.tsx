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
import App, { PresentationPreview } from '../app/App';
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
async function formLogin(name = 'Person A') {
    await change('email', 'learner@example.com'); await change('password', 'synthetic'); fetcher.mockResolvedValueOnce(json(loginResponse(name)));
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
async function click(text: string) {
    const node = [...host.querySelectorAll<HTMLElement>('button,a')].find((element) => element.textContent === text)!; expect(node).toBeTruthy(); await act(async () => node.click());
}
describe('real declarative routing and Profile', () => {
    it.each([['/', '/courses', 'Course Catalogue'], ['/login', '/login', 'Welcome back'], ['/register', '/register', 'Create account'], ['/courses', '/courses', 'Course Catalogue'], ['/courses/course-one', '/courses/course-one', 'Course details'], ['/missing', '/missing', 'Page not found']])('direct entry %s', async (path, finalPath, title) => {
        await mount(path); expect(location.pathname).toBe(finalPath); expect(host.querySelector('main h1')?.textContent).toBe(title); expect(fetcher.mock.calls.every(([url]) => new URL(url).pathname.startsWith('/api/courses'))).toBe(true); expect(document.activeElement).toBe(host.querySelector('main h1'));
    });
    it('anonymous /profile never mounts personal fields and replaces with login', async () => {
        const seen: string[] = [], observer = new MutationObserver(() => seen.push(host.textContent ?? '')); observer.observe(host, { subtree: true, childList: true });
        await mount('/profile'); observer.disconnect(); expect(location.pathname).toBe('/login'); expect(host.querySelector('dl')).toBeNull(); expect(seen.every((text) => !text.includes('Person A'))).toBe(true); expect(fetcher).not.toHaveBeenCalled();
    });
    it('consumes literal Profile return once, ignoring query/fragment/history redirect payloads', async () => {
        await mount('/profile?returnTo=https://outside.test#secret'); window.history.replaceState({ returnTo: 'https://outside.test' }, '', '/login?returnTo=//outside.test');
        await formLogin(); expect(location.pathname).toBe('/profile'); expect(location.search + location.hash).toBe(''); expect(auth.consumeReturn()).toBe('/courses'); expect(host.querySelector('dl')?.textContent).toContain('Person A');
    });
    it('login defaults to Courses for arbitrary supplied return targets', async () => {
        await mount('/login?returnTo=/api/secrets'); await formLogin(); expect(location.pathname).toBe('/courses');
    });
    it.each(['Learner', 'Instructor', 'Administrator'])('shows exactly the validated three fields for %s without any Profile request', async (role) => {
        await mount('/courses'); await login('Person A', role); await go('/profile');
        expect([...host.querySelectorAll('dt')].map((node) => node.textContent)).toEqual(['User ID', 'Display name', 'Role']);
        expect([...host.querySelectorAll('dd')].map((node) => node.textContent)).toEqual([id, 'Person A', role]); expect(host.querySelector('form')).toBeNull();
        const links = host.querySelectorAll('nav[aria-label="Account navigation"] a[href="/profile"]'); expect(links).toHaveLength(1);
        expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/auth/login')).toHaveLength(1); expect(host.textContent).not.toMatch(/email|permissions|password|accessToken/i);
    });
    it('renders hostile display names as plain text and preserves snapshot after leaving Profile', async () => {
        await mount('/courses'); await login('<img src=x onerror=alert(1)>'); await go('/profile'); expect(host.querySelector('dd:nth-of-type(2)')?.textContent).toBe('<img src=x onerror=alert(1)>'); expect(host.querySelector('img')).toBeNull();
        await go('/courses'); expect(auth.isAuthenticated).toBe(true); await go('/profile'); expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/auth/login')).toHaveLength(1);
    });
    it('successful same-UUID replacement removes A and renders only B without stealing focus', async () => {
        await mount('/courses'); await login(); await go('/profile'); const link = host.querySelector<HTMLAnchorElement>('a[href="/courses"]')!; link.focus();
        await login('Person B', 'Administrator'); expect(host.textContent).not.toContain('Person A'); expect(host.querySelector('dl')?.textContent).toContain('Person B'); expect(host.querySelector('dl')?.textContent).toContain('Administrator'); expect(document.activeElement).toBe(link);
    });
    it('explicit logout clears Profile, does not re-arm return, and deliberate revisit can set it', async () => {
        await mount('/courses'); await login(); await go('/profile'); await act(async () => auth.logout()); expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('Person A');
        await formLogin('Person B'); expect(location.pathname).toBe('/courses'); await click('Sign out'); await go('/profile'); await formLogin('Person C'); expect(location.pathname).toBe('/profile'); expect(host.textContent).not.toContain('Person B');
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
    it('preserves current-main FE01 dashboard, formatter runtime and utilities as an independent preview', async () => {
        window.history.replaceState(null, '', '/courses');
        await act(async () => root.render(<ThemeProvider theme={appTheme}><BrowserRouter><AuthProvider><AppRouter catalogue={<PresentationPreview />} /></AuthProvider></BrowserRouter></ThemeProvider>)); expect(location.pathname).toBe('/courses'); expect(host.textContent).toContain('Your learning, in focus.'); expect(host.textContent).toContain('Demo workspace'); expect(host.textContent).not.toContain('Demo learner');
        await click('Explore components'); expect(host.textContent).toContain('October 23, 2026'); expect(host.textContent).toContain('88.5%'); expect(host.textContent).toContain('1h 2m 5s'); expect(fetcher).not.toHaveBeenCalled();
        await click('Sign in'); expect(location.pathname).toBe('/login'); expect(host.querySelector('main h1')?.textContent).toBe('Welcome back');
    });
    it('composes the actual public catalogue/detail with current Auth navigation', async () => {
        await act(async () => root.render(<App />));
        expect(location.pathname).toBe('/courses'); expect(host.textContent).toContain('Course Catalogue');
        await click('View Syllabus'); expect(location.pathname).toBe(`/courses/${id}`); expect(host.querySelector('h1')?.textContent).toBe('Course details');
        await click('Back to Catalogue'); expect(location.pathname).toBe('/courses');
        expect(fetcher.mock.calls.every(([, init]) => !new Headers((init as RequestInit).headers).has('Authorization'))).toBe(true);
        await click('Sign in'); expect(location.pathname).toBe('/login'); expect(host.querySelector('h1')?.textContent).toBe('Welcome back');
    });

});

it.each(['Instructor', 'Administrator'])('denies %s before mounting My Courses or issuing private requests', async (role) => {
    await mount('/courses'); await login('Role test', role); await go('/my-courses');
    expect(host.querySelector('main h1')?.textContent).toBe('Access is denied.');
    expect(fetcher.mock.calls.filter(([url]) => new URL(url).pathname === '/api/me/courses')).toHaveLength(0);
    expect(host.querySelector('nav[aria-label="Account navigation"] a[href="/my-courses"]')).toBeNull();
});
it('mounts real learner My Courses with authenticated transport and removes it at expiry', async () => {
    await mount('/courses'); await login();
    fetcher.mockImplementation((url: string) => Promise.resolve(json(new URL(url).pathname === '/api/me/courses' ? { ...page, content: [] } : page)));
    await go('/my-courses'); expect(host.querySelector('main h1')?.textContent).toBe('My Courses'); expect(host.textContent).toContain('No courses found');
    const [, init] = fetcher.mock.calls.find(([url]) => new URL(url).pathname === '/api/me/courses')!;
    expect(init.headers.has('Authorization')).toBe(true);
    await act(async () => vi.advanceTimersByTime(900000)); expect(location.pathname).toBe('/login'); expect(host.textContent).not.toContain('My Courses');
});
