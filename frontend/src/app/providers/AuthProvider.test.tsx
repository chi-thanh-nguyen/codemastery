// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, useLocation } from 'react-router';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, AuthContext } from './AuthProvider';
import type { AuthContextValue } from './AuthProvider';
import { useAuth } from '../../features/auth/hooks/useAuth';
import { LoginPage } from '../../features/auth/pages/LoginPage';
import { RegisterPage } from '../../features/auth/pages/RegisterPage';
import { registrationInput, canonicalEmail, decodeLoginResponse, passwordResetCompletedMessage, passwordResetRequestedMessage } from '../../features/auth/types/authTypes';
import { confirmPasswordReset, requestPasswordReset } from '../../features/auth/api/authApi';
import type { UserRole } from '../../features/auth/types/authTypes';
import { request } from '../../shared/api/httpClient';
import { appTheme } from '../theme/theme';
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const id = '00000000-0000-4000-8000-000000000001';
const user = (displayName = 'Person A', role: UserRole = 'Learner') => ({ id, displayName, role });
const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
function response(name = 'Person A', role: UserRole = 'Learner', claims = {}) {
    const iat = Math.floor(Date.now() / 1000);
    return { tokenType: 'Bearer', expiresIn: 900, user: user(name, role), accessToken: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: id, iss: 'codemastery', aud: 'codemastery-api', iat, exp: iat + 900, ...claims })}.${'A'.repeat(43)}` };
}
const json = (value: unknown, status = 200, media = 'application/json') => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': media } });
const payload = { email: ' LEARNER@EXAMPLE.COM ', password: ' exact password  ', displayName: '  Learner  ' };
function deferred() { let resolve!: (value: Response) => void; const promise = new Promise<Response>((done) => { resolve = done; }); return { promise, resolve }; }
let root: Root, host: HTMLDivElement, auth: AuthContextValue, fetcher: ReturnType<typeof vi.fn>;
function Probe() { auth = useAuth(); return <output aria-label="Session">{auth.user?.displayName ?? 'Anonymous'}</output>; }
beforeEach(() => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-25T10:00:00Z')); vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080');
    fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); host = document.createElement('div'); document.body.append(host); root = createRoot(host); localStorage.clear(); sessionStorage.clear();
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear(); });
async function mount(children?: ReactNode, strict = false) {
    const tree = <ThemeProvider theme={appTheme}><MemoryRouter><AuthProvider><Probe />{children}</AuthProvider></MemoryRouter></ThemeProvider>;
    await act(async () => root.render(strict ? <StrictMode>{tree}</StrictMode> : tree));
}
async function signIn(name = 'Person A', role: UserRole = 'Learner') { fetcher.mockResolvedValueOnce(json(response(name, role))); await act(async () => { await auth.login(payload); }); }
async function change(name: string, value: string) {
    const input = host.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
}
async function submit() { await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
async function fillRegister(password = payload.password) {
    await change('email', payload.email); await change('displayName', payload.displayName); await change('password', password); await change('confirmPassword', password);
}
describe('CT01 input predicates through FE03 boundaries', () => {
    it.each([
        ['a'.repeat(14), false], ['a'.repeat(15), true], ['😀'.repeat(8), false], ['😀'.repeat(15), true],
        ['e\u0301'.repeat(7), false], ['e\u0301'.repeat(7) + 'x', true],
        ['😀'.repeat(14) + 'a'.repeat(15), true], // 71 UTF-8 bytes, 29 points
        ['😀'.repeat(14) + 'a'.repeat(16), true], // 72 bytes, 30 points
        ['😀'.repeat(14) + 'a'.repeat(17), false], // 73 bytes, 31 points
    ])('password boundary %# preserves exact value or rejects before transport', async (password, valid) => {
        await mount(<RegisterPage />); await fillRegister(password as string); fetcher.mockResolvedValueOnce(json(user(), 201)); await submit(); expect(fetcher).toHaveBeenCalledTimes(valid ? 1 : 0);
        if (valid) expect(JSON.parse(fetcher.mock.calls[0][1].body).password).toBe(password);
        else { expect(host.querySelector('[name="password"]')?.getAttribute('aria-invalid')).toBe('true'); expect(document.activeElement).toBe(host.querySelector('[name="password"]')); }
        expect(auth.isAuthenticated).toBe(false);
    });
    it.each([' leading and trailing ', 'é'.repeat(15), 'e\u0301'.repeat(15)])('preserves byte-distinct passwords %#', (password) => { expect(registrationInput({ ...payload, password }).password).toBe(password); });
    it.each([['😀'.repeat(100), true], ['😀'.repeat(101), false], ['\t  \n', false], ['Valid\u0000Name', false], ['Name\u0085', false], ['😀'.repeat(51), true]])('display-name boundary %#', (name, valid) => {
        const run = () => registrationInput({ ...payload, displayName: ` ${name} ` }); if (valid) expect(run().displayName).toBe(name); else expect(run).toThrow();
    });
    it.each(['K@example.com', 'İ@example.com', 'é@example.com', 'a@ｅxample.com', 'a\u00a0@example.com', 'a..b@example.com', 'a@-example.com', 'a@example.com.', 'missing-at', 'a b@example.com'])('rejects email %# before canonicalization', (email) => { expect(() => canonicalEmail(email)).toThrow(); });
    it.each([' LEARNER@EXAMPLE.COM ', '\tPerson+tag@EXAMPLE.COM\n'])('canonicalizes ASCII email %#', (email) => { expect(canonicalEmail(email)).toBe(email.trim().toLowerCase()); });
    it('registration whitelists payload, trims name, expects 201 and never authenticates', async () => {
        await mount(<RegisterPage />); await fillRegister(); fetcher.mockResolvedValueOnce(json(user(), 201)); await submit();
        expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ email: 'learner@example.com', password: payload.password, displayName: 'Learner' }); expect(fetcher.mock.calls[0][1].headers.has('Authorization')).toBe(false);
        expect(host.textContent).toContain('Account created. Sign in to continue.'); expect(auth.user).toBeNull();
    });
    it('blurs the connected registration input before success replaces the form inside its Auth layout', async () => {
        await mount(<RegisterPage />); await fillRegister();
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        const input = host.querySelector<HTMLInputElement>('input[name="password"]')!;
        const connected: boolean[] = [];
        input.addEventListener('blur', () => connected.push(input.isConnected));
        await act(async () => input.focus()); await submit();
        expect(document.activeElement).toBe(input);
        await act(async () => work.resolve(json(user(), 201)));
        expect(connected).toEqual([true]); expect(host.querySelector('form')).toBeNull();
        expect(host.textContent).toContain('Account created. Sign in to continue.'); expect(auth.user).toBeNull();
    });
    it('rejects registration 200 and never publishes success', async () => { await mount(<RegisterPage />); await fillRegister(); fetcher.mockResolvedValueOnce(json(user(), 200)); await submit(); expect(host.querySelector('[role="alert"]')?.textContent).toContain('invalid response'); expect(auth.user).toBeNull(); });
    it.each(['', 'short', '😀'.repeat(30)])('login forwards unsuitable credentials unchanged %#', async (password) => {
        await mount(<LoginPage />); await change('email', payload.email); await change('password', password); fetcher.mockResolvedValueOnce(json(null, 401)); await submit();
        expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ email: 'learner@example.com', password }); expect(host.querySelector('[role="alert"]')?.textContent).toContain('Authentication failed'); expect(fetcher).toHaveBeenCalledTimes(1);
    });
});
describe('real provider concurrency, expiry and projection', () => {
    it('logout prevents unfinished login resurrection', async () => {
        await mount(); const work = deferred(); fetcher.mockReturnValueOnce(work.promise); let result!: Promise<unknown>;
        await act(async () => { result = auth.login(payload).catch((e) => e); auth.logout(); }); await act(async () => work.resolve(json(response())));
        expect(await result).toMatchObject({ kind: 'cancelled' }); expect(auth.user).toBeNull();
    });
    it('latest login wins despite older transport ignoring abort', async () => {
        await mount(); const old = deferred(); fetcher.mockReturnValueOnce(old.promise); let pending!: Promise<unknown>; await act(async () => { pending = auth.login(payload).catch((e) => e); }); await signIn('Person B');
        await act(async () => old.resolve(json(response('Person A')))); expect(await pending).toMatchObject({ kind: 'cancelled' }); expect(auth.user?.displayName).toBe('Person B');
    });
    it.each([401, 403, 409, 500])('failed replacement login %s preserves A', async (status) => {
        await mount(); await signIn(); fetcher.mockResolvedValueOnce(json(null, status)); await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ kind: 'http', status }); }); expect(auth.user?.displayName).toBe('Person A');
    });
    it.each([200, 401, 403])('stale private %s cannot publish or invalidate B', async (status) => {
        await mount(); await signIn(); const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        const result = request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v }).catch((e) => e);
        const signal = fetcher.mock.calls[1][1].signal; await signIn('Person B'); expect(signal.aborted).toBe(true);
        await act(async () => work.resolve(json({ private: 'Person A' }, status))); expect(await result).toMatchObject({ kind: 'cancelled' }); expect(auth.user?.displayName).toBe('Person B');
    });
    it('logout aborts private work and suppresses late results', async () => {
        await mount(); await signIn(); const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        const result = request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v }).catch((e) => e);
        const signal = fetcher.mock.calls[1][1].signal; await act(async () => auth.logout()); expect(signal.aborted).toBe(true); work.resolve(json({ private: true })); expect(await result).toMatchObject({ kind: 'cancelled' }); expect(auth.user).toBeNull();
    });
    it.each([401, 403])('current authenticated %s has scoped session effect', async (status) => {
        await mount(); await signIn(); fetcher.mockResolvedValueOnce(json(null, status)); await act(async () => { await expect(request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v })).rejects.toMatchObject({ status }); }); expect(auth.isAuthenticated).toBe(status !== 401);
    });
    it('public registration and network failure preserve A', async () => {
        await mount(); await signIn(); fetcher.mockResolvedValueOnce(json(user('Registered'), 201)); await act(async () => { await auth.register(payload); }); expect(auth.user?.displayName).toBe('Person A');
        fetcher.mockRejectedValueOnce(new Error('private')); await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ kind: 'network' }); }); expect(auth.user?.displayName).toBe('Person A');
    });
    it('cancelled login cannot install a response', async () => {
        await mount(); const work = deferred(), controller = new AbortController(); fetcher.mockReturnValueOnce(work.promise); const result = auth.login(payload, controller.signal).catch((e) => e); controller.abort(); work.resolve(json(response()));
        await act(async () => { expect(await result).toMatchObject({ kind: 'cancelled' }); }); expect(auth.user).toBeNull();
    });
    it.each(['timer', 'focus', 'pageshow', 'visibilitychange', 'request'])('expires before admission on %s', async (event) => {
        await mount(); await signIn(); if (event === 'timer') await act(async () => vi.advanceTimersByTime(900000));
        else { vi.setSystemTime(new Date(Date.now() + 900000)); expect(auth.isAuthenticated).toBe(false); await act(async () => {
            if (event === 'request') await expect(request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v })).rejects.toMatchObject({ status: 401 });
            else (event === 'visibilitychange' ? document : window).dispatchEvent(new Event(event));
        }); }
        expect(auth.user).toBeNull(); expect(host.textContent).toBe('Anonymous'); expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('uses JWT expiry earlier than receipt plus lifetime', async () => {
        await mount(); const now = Math.floor(Date.now() / 1000); fetcher.mockResolvedValueOnce(json(response('A', 'Learner', { iat: now - 899, exp: now + 1 })));
        await act(async () => { await auth.login(payload); }); await act(async () => vi.advanceTimersByTime(1000)); expect(auth.isAuthenticated).toBe(false);
    });
    it.each([
        (r: ReturnType<typeof response>) => ({ ...r, tokenType: 'Basic' }), (r: ReturnType<typeof response>) => ({ ...r, expiresIn: -1 }),
        (r: ReturnType<typeof response>) => ({ ...r, user: { ...r.user, role: 'Owner' } }), (r: ReturnType<typeof response>) => ({ ...r, accessToken: 'not-jwt' }),
        (r: ReturnType<typeof response>) => ({ ...r, user: null }), (r: ReturnType<typeof response>) => ({ ...r, unexpected: true }),
    ])('rejects malformed login DTO %# before installing', async (mutate) => {
        await mount(); fetcher.mockResolvedValueOnce(json(mutate(response()))); await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ kind: 'protocol' }); }); expect(auth.user).toBeNull();
    });
    it.each([{ iss: 'other' }, { aud: 'other' }, { sub: '00000000-0000-4000-8000-000000000002' }, { iat: 1, exp: 901 }, { iat: 0, exp: 899 }, { exp: '900' }])('rejects JWT claim/lifetime %#', (claims) => { expect(() => decodeLoginResponse(response('A', 'Learner', claims))).toThrow(); });
    it('StrictMode cleanup clears timers/binding and fresh mount starts anonymous', async () => {
        await mount(undefined, true); await signIn(); await act(async () => root.unmount()); root = createRoot(host); expect(vi.getTimerCount()).toBe(0);
        await expect(request('/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v })).rejects.toMatchObject({ status: 401 }); await mount(undefined, true); expect(auth.user).toBeNull(); expect(localStorage.length + sessionStorage.length).toBe(0);
    });
});
describe('real Auth UI observable states', () => {
    it.each(['login', 'register'])('keeps %s action/progress names while pending', async (kind) => {
        await mount(kind === 'login' ? <LoginPage /> : <RegisterPage />); const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        if (kind === 'login') { await change('email', payload.email); await change('password', payload.password); } else await fillRegister(); await submit();
        const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!; expect(button.disabled).toBe(true); expect(button.textContent).toBe(kind === 'login' ? 'Sign in' : 'Create account');
        const bar = host.querySelector('[role="progressbar"]')!; expect(document.getElementById(bar.getAttribute('aria-labelledby')!)?.textContent).toBe(kind === 'login' ? 'Signing in…' : 'Creating your account…');
        await act(async () => work.resolve(json(null, 503))); expect(button.disabled).toBe(false); expect(host.querySelector('[role="progressbar"]')).toBeNull(); expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('associates safe server errors and focuses affected input', async () => {
        await mount(<RegisterPage />); await fillRegister(); await act(async () => host.querySelector<HTMLButtonElement>('button[type="submit"]')!.focus()); fetcher.mockResolvedValueOnce(json({ type: 'about:blank', title: 'Bad Request', status: 400, detail: '<script>PRIVATE SENTINEL</script>', instance: '/api/auth/register', errors: [{ field: 'email', code: 'Email', message: 'PRIVATE SENTINEL' }] }, 400, 'application/problem+json')); await submit();
        const input = host.querySelector<HTMLInputElement>('[name="email"]')!; expect(input.getAttribute('aria-invalid')).toBe('true'); expect(document.getElementById(input.getAttribute('aria-describedby')!)?.textContent).toBe('Please check this field.'); expect(document.activeElement).toBe(input); expect(host.innerHTML).not.toContain('PRIVATE SENTINEL'); expect(host.querySelector('script')).toBeNull();
    });
    it('focuses local validation and leaves focus alone for late errors', async () => {
        await mount(<LoginPage />); await submit(); expect(document.activeElement).toBe(host.querySelector('[name="email"]')); await change('email', payload.email);
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise); await submit(); const link = host.querySelector<HTMLAnchorElement>('a[href="/register"]')!; await act(async () => link.focus()); await act(async () => work.resolve(json(null, 401))); expect(document.activeElement).toBe(link);
    });
    it('renders local general fallback without leaking diagnostics/password or logging', async () => {
        const log = vi.spyOn(console, 'log'), error = vi.spyOn(console, 'error'); await mount(<LoginPage />); await change('email', payload.email); await change('password', payload.password);
        fetcher.mockResolvedValueOnce(new Response('<html>PRIVATE SENTINEL</html>', { status: 503, headers: { 'Content-Type': 'text/html' } })); await submit();
        expect(host.querySelector('[role="alert"]')?.textContent).toBe('The request could not be completed.'); expect(host.textContent).not.toContain(payload.password); expect(host.innerHTML).not.toContain('PRIVATE SENTINEL'); expect(log).not.toHaveBeenCalled(); expect(error).not.toHaveBeenCalled(); expect(localStorage.length + sessionStorage.length).toBe(0);
    });
});

describe('additional UI failure and lifecycle regressions', () => {
    it.each(['login', 'register'].flatMap((kind) => [400, 401, 409, 500, 'network'].map((outcome) => [kind, outcome] as const)))('%s safely presents %s without retry or authentication', async (kind, outcome) => {
        await mount(kind === 'login' ? <LoginPage /> : <RegisterPage />);
        if (kind === 'login') { await change('email', payload.email); await change('password', payload.password); } else await fillRegister();
        if (outcome === 'network') fetcher.mockRejectedValueOnce(new Error('PRIVATE SENTINEL'));
        else fetcher.mockResolvedValueOnce(json({ secret: 'PRIVATE SENTINEL' }, outcome as number, 'application/problem+json'));
        await act(async () => host.querySelector<HTMLButtonElement>('button[type="submit"]')!.focus()); await submit();
        expect(host.querySelector('[role="alert"]')).not.toBeNull(); expect(host.innerHTML).not.toContain('PRIVATE SENTINEL'); expect(auth.user).toBeNull(); expect(fetcher).toHaveBeenCalledTimes(1);
        expect(document.activeElement).toBe(host.querySelector('[role="alert"]'));
    });
    it.each([['😀'.repeat(100), true], ['😀'.repeat(101), false], ['Good\u0001Name', false]])('real registration display-name boundary %#', async (name, valid) => {
        await mount(<RegisterPage />); await fillRegister(); await change('displayName', name as string); fetcher.mockResolvedValueOnce(json(user(), 201)); await submit(); expect(fetcher).toHaveBeenCalledTimes(valid ? 1 : 0);
    });
    it('rejects Unicode email in the actual form before transport', async () => {
        await mount(<RegisterPage />); await fillRegister(); await change('email', 'K@example.com'); await submit(); expect(fetcher).not.toHaveBeenCalled(); expect(document.activeElement).toBe(host.querySelector('[name="email"]'));
    });
    it('unmount cancels a pending StrictMode operation despite a late success', async () => {
        await mount(<LoginPage />, true); await change('email', payload.email); await change('password', payload.password); const work = deferred(); fetcher.mockReturnValueOnce(work.promise); await submit();
        const signal = fetcher.mock.calls[0][1].signal; const late = response(); await act(async () => root.unmount()); root = createRoot(host); expect(signal.aborted).toBe(true);
        await act(async () => work.resolve(json(late))); await mount(undefined, true); expect(auth.user).toBeNull(); expect(vi.getTimerCount()).toBe(0);
    });
});

it.each(['Instructor', 'Administrator'] as const)('registration rejects a non-Learner success snapshot %s', async (role) => {
    await mount(<RegisterPage />); await fillRegister(); fetcher.mockResolvedValueOnce(json(user('Wrong role', role), 201)); await submit();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('invalid response'); expect(auth.user).toBeNull(); expect(host.textContent).not.toContain('Your account was registered.');
});

it('returns a session receipt that consumers can reject after replacement or logout', async () => {
    await mount(); fetcher.mockResolvedValueOnce(json(response())); let receipt!: number;
    await act(async () => { receipt = await auth.login(payload); }); expect(auth.isCurrent(receipt)).toBe(true);
    await signIn('Person B'); expect(auth.isCurrent(receipt)).toBe(false);
    fetcher.mockResolvedValueOnce(json(response('Person C'))); await act(async () => { receipt = await auth.login(payload); });
    await act(async () => auth.logout()); expect(auth.isCurrent(receipt)).toBe(false);
});

it.each(['logout', 'replacement'])('real LoginPage suppresses a resolved continuation after %s', async (transition) => {
    function InterleavedLogin() {
        const actual = useAuth(), location = useLocation();
        // Delay only the consumer continuation; every session transition uses the real provider.
        const consumer = Object.create(actual) as AuthContextValue;
        consumer.login = async (input, signal) => {
            const receipt = await actual.login(input, signal);
            if (transition === 'logout') actual.logout(); else await actual.login(input);
            return receipt;
        };
        return <AuthContext.Provider value={consumer}><LoginPage /><output id="consumer-path">{location.pathname}</output></AuthContext.Provider>;
    }
    await mount(<InterleavedLogin />); await change('email', payload.email); await change('password', payload.password);
    fetcher.mockResolvedValueOnce(json(response('Person A')));
    if (transition === 'replacement') fetcher.mockResolvedValueOnce(json(response('Person B')));
    await submit(); expect(host.querySelector('#consumer-path')?.textContent).toBe('/');
    expect(auth.user?.displayName ?? null).toBe(transition === 'replacement' ? 'Person B' : null);
});

it('real registration form suppresses success after logout between result and UI continuation', async () => {
    function InterleavedRegistration() {
        const actual = useAuth(), consumer = Object.create(actual) as AuthContextValue;
        consumer.register = async (input, signal) => { const user = await actual.register(input, signal); actual.logout(); return user; };
        return <AuthContext.Provider value={consumer}><RegisterPage /></AuthContext.Provider>;
    }
    await mount(<InterleavedRegistration />); await fillRegister(); fetcher.mockResolvedValueOnce(json(user(), 201)); await submit();
    expect(host.textContent).not.toContain('Your account was registered.'); expect(host.querySelector('form')).not.toBeNull(); expect(auth.user).toBeNull();
});

it.each(['login', 'register'])('real %s form suppresses a rejected continuation after logout', async (kind) => {
    function InterleavedFailure() {
        const actual = useAuth(), consumer = Object.create(actual) as AuthContextValue;
        consumer.login = async (input, signal) => { try { return await actual.login(input, signal); } catch (error) { actual.logout(); throw error; } };
        consumer.register = async (input, signal) => { try { return await actual.register(input, signal); } catch (error) { actual.logout(); throw error; } };
        return <AuthContext.Provider value={consumer}>{kind === 'login' ? <LoginPage /> : <RegisterPage />}</AuthContext.Provider>;
    }
    await mount(<InterleavedFailure />);
    if (kind === 'login') { await change('email', payload.email); await change('password', payload.password); } else await fillRegister();
    fetcher.mockResolvedValueOnce(json(null, kind === 'login' ? 401 : 400)); await submit();
    expect(host.querySelector('[role="alert"]')).toBeNull(); expect(auth.user).toBeNull();
});

describe('ADR014 hybrid refresh-session lifecycle', () => {
    const sidA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', sidB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
    const session = (name = 'Person A', sid = sidA, role: UserRole = 'Learner') => response(name, role, { sid });
    const path = (call: unknown[]) => new URL(call[0] as string).pathname;
    const calls = (pathname: string) => fetcher.mock.calls.filter((call) => path(call) === pathname);
    const empty = () => new Response(null, { status: 204 });
    async function mountHybrid(strict = false, restoreOnLoad = true) {
        const tree = <ThemeProvider theme={appTheme}><MemoryRouter><AuthProvider refreshSessions restoreOnLoad={restoreOnLoad}><Probe /></AuthProvider></MemoryRouter></ThemeProvider>;
        await act(async () => root.render(strict ? <StrictMode>{tree}</StrictMode> : tree));
    }
    async function settle() { for (let i = 0; i < 5; i++) await act(async () => { await Promise.resolve(); }); }
    const bearer = async () => {
        fetcher.mockResolvedValueOnce(json({ ok: true }));
        await act(async () => { await request('/me/courses', { method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: (v) => v }); });
        return (fetcher.mock.calls.at(-1)![1].headers as Headers).get('Authorization');
    };

    it('restores on load from the cookie with one credentialed refresh, also under StrictMode, and stores nothing', async () => {
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        await mountHybrid(true);
        expect(auth.restoring).toBe(true); expect(auth.isAuthenticated).toBe(false);
        await act(async () => work.resolve(json(session())));
        await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(1);
        expect(calls('/api/auth/refresh')[0][1]).toMatchObject({ method: 'POST', credentials: 'include', cache: 'no-store', body: undefined });
        expect(auth.user?.displayName).toBe('Person A'); expect(auth.restoring).toBe(false);
        expect(localStorage.length + sessionStorage.length).toBe(0); expect(document.cookie).toBe('');
    });
    it('a refused restore leaves an anonymous, settled provider', async () => {
        fetcher.mockResolvedValueOnce(json(null, 401)); await mountHybrid(); await settle();
        expect(auth.user).toBeNull(); expect(auth.restoring).toBe(false); expect(calls('/api/auth/refresh')).toHaveLength(1);
    });
    it('a document opened at the design preview does not restore', async () => {
        await mountHybrid(false, false); await settle();
        expect(fetcher).not.toHaveBeenCalled(); expect(auth.restoring).toBe(false);
    });
    // ---- A13 (DESIGN S26, ADR014 §7, CT14): a document opened at /preview stays isolated. ----
    function previewChannels() {
        const list: { onmessage: ((event: MessageEvent) => void) | null; posted: unknown[] }[] = [];
        vi.stubGlobal('BroadcastChannel', class { onmessage: ((event: MessageEvent) => void) | null = null; posted: unknown[] = []; constructor() { list.push(this); } postMessage(value: unknown) { this.posted.push(value); } close() { /* test double */ } });
        return list;
    }
    const tell = async (tabs: { onmessage: ((event: MessageEvent) => void) | null }[], type: string) => {
        for (const tab of tabs) await act(async () => tab.onmessage?.({ data: { type } } as MessageEvent));
        await settle();
    };
    it('A13: another tab signing in, repeatedly, sends nothing from a preview document and installs no session', async () => {
        const tabs = previewChannels();
        await mountHybrid(false, false); await settle();
        const generation = auth.generation;
        for (let i = 0; i < 3; i++) await tell(tabs, 'signed-in');
        expect(fetcher).not.toHaveBeenCalled();
        expect(auth.user).toBeNull(); expect(auth.restoring).toBe(false); expect(auth.generation).toBe(generation);
        expect(localStorage.length + sessionStorage.length).toBe(0);
    });
    it('A13: another tab signing out sends nothing and changes nothing in a preview document', async () => {
        const tabs = previewChannels();
        await mountHybrid(false, false); await settle();
        const generation = auth.generation;
        await tell(tabs, 'signed-out'); await tell(tabs, 'signed-in'); await tell(tabs, 'signed-out');
        expect(fetcher).not.toHaveBeenCalled(); expect(auth.generation).toBe(generation); expect(auth.restoring).toBe(false);
        expect(tabs.flatMap((tab) => tab.posted)).toEqual([]);
    });
    it('A13: StrictMode setup and cleanup do not bypass the isolation', async () => {
        const tabs = previewChannels();
        await mountHybrid(true, false); await settle();
        expect(tabs.length).toBeGreaterThanOrEqual(2); // the StrictMode re-run created a second channel
        await tell(tabs, 'signed-in');
        expect(fetcher).not.toHaveBeenCalled(); expect(auth.user).toBeNull();
    });
    it('A13: focus, page show, visibility changes and timers never restore in a preview document', async () => {
        previewChannels();
        await mountHybrid(false, false); await settle();
        await act(async () => { window.dispatchEvent(new Event('focus')); window.dispatchEvent(new Event('pageshow')); document.dispatchEvent(new Event('visibilitychange')); });
        await act(async () => { vi.advanceTimersByTime(20 * 60_000); }); await settle();
        expect(fetcher).not.toHaveBeenCalled();
    });
    it('A13: a later change of restoreOnLoad cannot start a restore', async () => {
        previewChannels();
        await mountHybrid(false, false); await settle();
        await mountHybrid(false, true); await settle();
        expect(fetcher).not.toHaveBeenCalled(); expect(auth.restoring).toBe(false);
    });
    it('A13: a failed explicit sign-in in a preview document does not restore', async () => {
        previewChannels();
        await mountHybrid(false, false); await settle();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await auth.login(payload).catch(() => undefined); }); await settle();
        expect(fetcher.mock.calls.map(path)).toEqual(['/api/auth/login']);
        expect(auth.user).toBeNull();
    });
    it('A13: an explicit sign-in in the document ends the isolation; the normal lifecycle follows', async () => {
        const tabs = previewChannels();
        await mountHybrid(false, false); await settle();
        await signIn('Person A');
        expect(tabs.at(-1)!.posted).toContainEqual({ type: 'signed-in' });
        fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
        await tell(tabs.slice(-1), 'signed-in');
        expect(calls('/api/auth/refresh')).toHaveLength(1); expect(auth.user?.displayName).toBe('Person B');
        await tell(tabs.slice(-1), 'signed-out');
        expect(auth.user).toBeNull(); expect(calls('/api/auth/refresh')).toHaveLength(1);
    });
    it('A13: a sign-out in another tab still cancels a sign-in pending in a preview document', async () => {
        const tabs = previewChannels();
        await mountHybrid(false, false); await settle();
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        let outcome: unknown;
        await act(async () => { void auth.login(payload).then(() => 'installed', (error: unknown) => error).then((value) => { outcome = value; }); });
        await tell(tabs.slice(-1), 'signed-out');
        await act(async () => work.resolve(json(session('Late')))); await settle();
        expect(auth.user).toBeNull(); expect(outcome).not.toBe('installed');
    });
    it('normal documents still adopt another tab\'s sign-in and follow its sign-out', async () => {
        const tabs = previewChannels();
        fetcher.mockResolvedValueOnce(json(null, 401)); await mountHybrid(); await settle();
        fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
        await tell(tabs.slice(-1), 'signed-in');
        expect(auth.user?.displayName).toBe('Person B'); expect(calls('/api/auth/refresh')).toHaveLength(2);
        await tell(tabs.slice(-1), 'signed-out');
        expect(auth.user).toBeNull(); expect(calls('/api/auth/refresh')).toHaveLength(2);
    });
    it('renews before expiry in place: same generation, new bearer', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const generation = auth.generation, before = await bearer();
        const next = session(); next.accessToken = next.accessToken.replace(/\.[^.]+$/, `.${'B'.repeat(42)}A`);
        fetcher.mockResolvedValueOnce(json(next));
        await act(async () => vi.advanceTimersByTime(900_000 - 60_000)); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2);
        expect(auth.generation).toBe(generation); expect(auth.isAuthenticated).toBe(true);
        expect(await bearer()).not.toBe(before);
    });
    it('a renewal for another session or account is a replacement with a new generation', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const generation = auth.generation; fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
        await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        expect(auth.generation).toBeGreaterThan(generation); expect(auth.user?.displayName).toBe('Person B');
    });
    it.each([401, 403])('a refused renewal (%s) ends the session', async (status) => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        fetcher.mockResolvedValueOnce(json(null, status)); await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        expect(auth.user).toBeNull();
    });
    it('a transient renewal failure retries and keeps the session', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        fetcher.mockRejectedValueOnce(new Error('offline')); await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        expect(auth.isAuthenticated).toBe(true);
        fetcher.mockResolvedValueOnce(json(session())); await act(async () => vi.advanceTimersByTime(10_000)); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3); expect(auth.isAuthenticated).toBe(true);
    });
    it('a late renewal cannot revive a session signed out meanwhile', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        await act(async () => vi.advanceTimersByTime(840_000));
        fetcher.mockResolvedValueOnce(empty());
        await act(async () => auth.logout());
        await act(async () => work.resolve(json(session()))); await settle();
        expect(auth.user).toBeNull();
    });
    it('sign-out clears at once, revokes with keepalive and is retried on the next load when unconfirmed', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        fetcher.mockRejectedValueOnce(new Error('offline'));
        await act(async () => auth.logout()); await settle();
        expect(auth.user).toBeNull();
        expect(calls('/api/auth/logout')[0][1]).toMatchObject({ method: 'POST', credentials: 'include', keepalive: true });
        // Time-bounded inside the shared Web Lock, like refresh.
        expect(calls('/api/auth/logout')[0][1].signal).toBeInstanceOf(AbortSignal);
        expect(localStorage.getItem('codemastery.auth.pendingSignOut')).toBe('1');
        await act(async () => root.unmount()); root = createRoot(host);
        fetcher.mockResolvedValueOnce(empty()); await mountHybrid(); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(1); expect(calls('/api/auth/logout')).toHaveLength(2);
        expect(localStorage.length).toBe(0); expect(auth.user).toBeNull(); expect(auth.restoring).toBe(false);
    });
    it('sign-in while a restore is in flight wins and the late restore is ignored', async () => {
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise); await mountHybrid();
        await signIn('Person B'); expect(auth.restoring).toBe(false);
        await act(async () => work.resolve(json(session('Person A', sidB)))); await settle();
        expect(auth.user?.displayName).toBe('Person B');
    });
    it('follows other tabs: signed-out clears without a request, signed-in adopts the cookie session', async () => {
        const channels: { onmessage: ((event: MessageEvent) => void) | null; posted: unknown[] }[] = [];
        vi.stubGlobal('BroadcastChannel', class { onmessage: ((event: MessageEvent) => void) | null = null; posted: unknown[] = []; constructor() { channels.push(this); } postMessage(value: unknown) { this.posted.push(value); } close() { /* test double */ } });
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const channel = channels.at(-1)!, requests = fetcher.mock.calls.length;
        await act(async () => channel.onmessage?.({ data: { type: 'signed-out' } } as MessageEvent));
        expect(auth.user).toBeNull(); expect(fetcher.mock.calls.length).toBe(requests);
        fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
        await act(async () => channel.onmessage?.({ data: { type: 'signed-in' } } as MessageEvent)); await settle();
        expect(auth.user?.displayName).toBe('Person B');
        fetcher.mockResolvedValueOnce(json(session('Person C', sidA))); await act(async () => { await auth.login(payload); });
        expect(channel.posted).toContainEqual({ type: 'signed-in' });
        fetcher.mockResolvedValueOnce(empty()); await act(async () => auth.logout());
        expect(channel.posted).toContainEqual({ type: 'signed-out' });
    });
    it('runs sign-in, refresh and sign-out under one Web Lock', async () => {
        const held: string[] = [];
        Object.defineProperty(navigator, 'locks', { configurable: true, value: { request: (name: string, work: () => Promise<unknown>) => { held.push(name); return work(); } } });
        try {
            fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
            await signIn('Person B'); fetcher.mockResolvedValueOnce(empty()); await act(async () => auth.logout()); await settle();
            expect(held).toEqual(['codemastery-auth-session', 'codemastery-auth-session', 'codemastery-auth-session']);
        } finally { Reflect.deleteProperty(navigator, 'locks'); }
    });
    it('waking after expiry restores from the cookie once, rate-limited', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        vi.setSystemTime(new Date(Date.now() + 901_000));
        fetcher.mockResolvedValueOnce(json(session()));
        await act(async () => window.dispatchEvent(new Event('focus'))); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(auth.user?.displayName).toBe('Person A'); expect(auth.restoring).toBe(false);
        vi.setSystemTime(new Date(Date.now() + 901_000));
        await act(async () => window.dispatchEvent(new Event('focus'))); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3);
    });
    // ---- Independent-review remediation F04/F05 (ADR014 §15). Native-lock evidence is in e2e/. ----
    function fifoLocks() {
        let tail: Promise<unknown> = Promise.resolve();
        const value = { request: (_name: string, work: () => Promise<unknown>) => { const run = tail.then(work, work); tail = run.catch(() => undefined); return run; } };
        Object.defineProperty(navigator, 'locks', { configurable: true, value });
        return () => Reflect.deleteProperty(navigator, 'locks');
    }
    function channels() {
        const list: { onmessage: ((event: MessageEvent) => void) | null }[] = [];
        vi.stubGlobal('BroadcastChannel', class { onmessage: ((event: MessageEvent) => void) | null = null; constructor() { list.push(this); } postMessage() { /* test double */ } close() { /* test double */ } });
        return list;
    }
    it('F04: a signed-out broadcast during a pending restore discards the late response', async () => {
        const tabs = channels(); const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        await mountHybrid(); expect(auth.restoring).toBe(true);
        await act(async () => tabs.at(-1)!.onmessage?.({ data: { type: 'signed-out' } } as MessageEvent));
        expect(auth.restoring).toBe(false);
        await act(async () => work.resolve(json(session('Person A')))); await settle();
        expect(auth.user).toBeNull(); expect(host.textContent).toBe('Anonymous'); expect(auth.restoring).toBe(false);
    });
    it('F05: an explicit sign-in queued behind a pending restore is sent and wins', async () => {
        const release = fifoLocks();
        try {
            const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
            await mountHybrid();
            fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
            let signedIn!: Promise<number>;
            await act(async () => { signedIn = auth.login(payload); });
            expect(calls('/api/auth/login')).toHaveLength(0);
            await act(async () => work.resolve(json(session('Person A')))); await settle();
            await act(async () => { await signedIn; });
            expect(calls('/api/auth/login')).toHaveLength(1);
            expect(auth.user?.displayName).toBe('Person B'); expect(auth.restoring).toBe(false);
        } finally { release(); }
    });
    it('F05: a failed explicit sign-in restores the session it superseded', async () => {
        const release = fifoLocks();
        try {
            const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
            await mountHybrid();
            fetcher.mockResolvedValueOnce(json(null, 401));
            let attempt!: Promise<unknown>;
            await act(async () => { attempt = auth.login(payload).catch((e) => e); });
            fetcher.mockResolvedValueOnce(json(session('Person A')));
            await act(async () => work.resolve(json(session('Person A')))); await settle();
            expect(await attempt).toMatchObject({ kind: 'http', status: 401 }); await settle();
            expect(calls('/api/auth/refresh')).toHaveLength(2); expect(auth.user?.displayName).toBe('Person A');
        } finally { release(); }
    });
    it('F05: the old session expiring while a sign-in is in flight does not cancel it', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        let signedIn!: Promise<number>;
        await act(async () => { signedIn = auth.login(payload); });
        fetcher.mockResolvedValueOnce(json(null, 401));
        vi.setSystemTime(new Date(Date.now() + 901_000));
        await act(async () => window.dispatchEvent(new Event('focus'))); await settle();
        await act(async () => work.resolve(json(session('Person B', sidB)))); await act(async () => { await signedIn; });
        expect(auth.user?.displayName).toBe('Person B');
    });
    // ---- Second independent-review remediation N02 (ADR014 §16). Native-lock evidence is in e2e/. ----
    const sidC = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
    it('N02: a renewal resolving with another sid does not cancel a sign-in queued behind it', async () => {
        const release = fifoLocks();
        try {
            fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
            const renewal = deferred(); fetcher.mockReturnValueOnce(renewal.promise);
            await act(async () => vi.advanceTimersByTime(840_000));
            fetcher.mockResolvedValueOnce(json(session('Person B', sidB)));
            let signedIn!: Promise<number>;
            await act(async () => { signedIn = auth.login(payload); });
            expect(calls('/api/auth/login')).toHaveLength(0);
            await act(async () => renewal.resolve(json(session('Person C', sidC)))); await settle();
            await act(async () => { await signedIn; });
            expect(calls('/api/auth/login')).toHaveLength(1);
            expect(auth.user?.displayName).toBe('Person B');
        } finally { release(); }
    });
    it('N02: a renewal resolving while a sign-in is in flight does not abort or overwrite it', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const signIn = deferred(); fetcher.mockReturnValueOnce(signIn.promise);
        let signedIn!: Promise<number>;
        await act(async () => { signedIn = auth.login(payload); });
        const loginSignal = calls('/api/auth/login')[0][1].signal as AbortSignal;
        fetcher.mockResolvedValueOnce(json(session('Person C', sidC)));
        await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(loginSignal.aborted).toBe(false);
        expect(auth.user?.displayName).toBe('Person A');
        await act(async () => signIn.resolve(json(session('Person B', sidB)))); await act(async () => { await signedIn; });
        expect(auth.user?.displayName).toBe('Person B'); expect(calls('/api/auth/login')).toHaveLength(1);
    });
    it('N02: a failed sign-in renews the session whose renewal it superseded', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        const generation = auth.generation, signIn = deferred(); fetcher.mockReturnValueOnce(signIn.promise);
        let attempt!: Promise<unknown>;
        await act(async () => { attempt = auth.login(payload).catch((e) => e); });
        fetcher.mockResolvedValueOnce(json(session()));
        await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        fetcher.mockResolvedValueOnce(json(session()));
        await act(async () => signIn.resolve(json(null, 401)));
        expect(await attempt).toMatchObject({ kind: 'http', status: 401 }); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3);
        expect(auth.user?.displayName).toBe('Person A'); expect(auth.generation).toBe(generation);
    });
    // ---- Final remediation A01 (ADR014 §17): renewal versus a failed sign-in, both completion orders. ----
    /** A response for `sid` whose bearer is recognisable by its signature letter. */
    const signed = (letter: string, name = 'Person A', sid = sidA) => { const value = session(name, sid); value.accessToken = value.accessToken.replace(/\.[^.]+$/, `.${letter.repeat(42)}A`); return value; };
    const bearerOf = (value: { accessToken: string }) => `Bearer ${value.accessToken}`;
    /** Installs session A through a load-time restore and starts its delayed renewal. */
    async function renewalPending() {
        const first = signed('O'); fetcher.mockResolvedValueOnce(json(first)); await mountHybrid(); await settle();
        const renewal = deferred(); fetcher.mockReturnValueOnce(renewal.promise);
        await act(async () => vi.advanceTimersByTime(840_000));
        expect(calls('/api/auth/refresh')).toHaveLength(2);
        return { first, renewal, generation: auth.generation };
    }
    /** The recovered session is usable: its bearer is sent, and its own renewal is scheduled again. */
    async function expectUsable(recovered: { accessToken: string }, refreshes: number, sid = sidA) {
        expect(auth.isAuthenticated).toBe(true); expect(await bearer()).toBe(bearerOf(recovered));
        const next = signed('N', 'Person A', sid); fetcher.mockResolvedValueOnce(json(next));
        await act(async () => vi.advanceTimersByTime(840_000)); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(refreshes + 1); expect(await bearer()).toBe(bearerOf(next));
    }
    it('A01 order A: the renewal completes first, then the explicit sign-in fails; the session renews from the cookie', async () => {
        const { first, renewal, generation } = await renewalPending();
        const signIn = deferred(); fetcher.mockReturnValueOnce(signIn.promise);
        let attempt!: Promise<unknown>;
        await act(async () => { attempt = auth.login(payload).catch((e) => e); });
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(await bearer()).toBe(bearerOf(first));
        const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
        await act(async () => signIn.resolve(json(null, 401)));
        expect(await attempt).toMatchObject({ kind: 'http', status: 401 }); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3); expect(auth.generation).toBe(generation);
        await expectUsable(recovered, 3);
    });
    it('A01 order B: the explicit sign-in fails first, then the delayed renewal completes; the session renews from the cookie', async () => {
        const { first, renewal, generation } = await renewalPending();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ kind: 'http', status: 401 }); });
        await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(await bearer()).toBe(bearerOf(first));
        const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3); expect(auth.generation).toBe(generation);
        // Neither the original bearer nor the superseded renewal's bearer is kept.
        await expectUsable(recovered, 3);
    });
    it('A01: a renewal that completes while the sign-in is queued behind it waits; a failed sign-in then recovers', async () => {
        const release = fifoLocks();
        try {
            const { first, renewal } = await renewalPending();
            fetcher.mockResolvedValueOnce(json(null, 401));
            let attempt!: Promise<unknown>;
            await act(async () => { attempt = auth.login(payload).catch((e) => e); });
            expect(calls('/api/auth/login')).toHaveLength(0);
            const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
            await act(async () => renewal.resolve(json(signed('R')))); await settle();
            expect(await attempt).toMatchObject({ kind: 'http', status: 401 }); await settle();
            expect(calls('/api/auth/login')).toHaveLength(1); expect(calls('/api/auth/refresh')).toHaveLength(3);
            expect(await bearer()).not.toBe(bearerOf(first));
            await expectUsable(recovered, 3);
        } finally { release(); }
    });
    it('A01: a queued sign-in the user abandons settles as cancelled; the renewal it superseded still recovers', async () => {
        const release = fifoLocks();
        try {
            const { first, renewal, generation } = await renewalPending();
            const leave = new AbortController();
            let attempt!: Promise<unknown>;
            await act(async () => { attempt = auth.login(payload, leave.signal).catch((e) => e); });
            await act(async () => leave.abort());
            const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
            await act(async () => renewal.resolve(json(signed('R')))); await settle();
            expect(await attempt).toMatchObject({ kind: 'cancelled' }); await settle();
            expect(calls('/api/auth/login')).toHaveLength(0); expect(calls('/api/auth/refresh')).toHaveLength(3);
            expect(auth.generation).toBe(generation); expect(await bearer()).not.toBe(bearerOf(first));
            await expectUsable(recovered, 3);
        } finally { release(); }
    });
    it('A01: a renewal that completes while the sign-in is in flight neither aborts nor installs before the sign-in fails', async () => {
        const { renewal } = await renewalPending();
        const signIn = deferred(); fetcher.mockReturnValueOnce(signIn.promise);
        let attempt!: Promise<unknown>;
        await act(async () => { attempt = auth.login(payload).catch((e) => e); });
        const loginSignal = calls('/api/auth/login')[0][1].signal as AbortSignal;
        await act(async () => renewal.resolve(json(signed('R', 'Person C', sidC)))); await settle();
        expect(loginSignal.aborted).toBe(false); expect(auth.user?.displayName).toBe('Person A');
        const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
        await act(async () => signIn.resolve(json(null, 401)));
        expect(await attempt).toMatchObject({ kind: 'http', status: 401 }); await settle();
        expect(auth.user?.displayName).toBe('Person A'); await expectUsable(recovered, 3);
    });
    it('A01: an explicit sign-in that succeeds while the renewal is delayed wins; the late renewal changes nothing', async () => {
        const { renewal, generation } = await renewalPending();
        const chosen = signed('B', 'Person B', sidB); fetcher.mockResolvedValueOnce(json(chosen));
        await act(async () => { await auth.login(payload); });
        const installed = auth.generation; expect(installed).toBeGreaterThan(generation);
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(auth.user?.displayName).toBe('Person B'); expect(auth.generation).toBe(installed);
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(await bearer()).toBe(bearerOf(chosen));
    });
    it('A01: a sign-out before the delayed renewal completes wins, also while a failed sign-in was pending', async () => {
        const { renewal } = await renewalPending();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ status: 401 }); });
        fetcher.mockResolvedValueOnce(empty()); await act(async () => auth.logout());
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(auth.user).toBeNull(); expect(calls('/api/auth/refresh')).toHaveLength(2); expect(calls('/api/auth/logout')).toHaveLength(1);
    });
    it('A01: a newer sign-in supersedes the older one; the renewal waits for the newest and recovers only once', async () => {
        const { renewal } = await renewalPending();
        const older = deferred(), newer = deferred(); fetcher.mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise);
        let first!: Promise<unknown>, second!: Promise<unknown>;
        await act(async () => { first = auth.login(payload).catch((e) => e); });
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        await act(async () => { second = auth.login(payload).catch((e) => e); });
        await act(async () => older.resolve(json(signed('X', 'Person X', sidC))));
        expect(await first).toMatchObject({ kind: 'cancelled' }); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(2); expect(auth.user?.displayName).toBe('Person A');
        const recovered = signed('C'); fetcher.mockResolvedValueOnce(json(recovered));
        await act(async () => newer.resolve(json(null, 401)));
        expect(await second).toMatchObject({ kind: 'http', status: 401 }); await settle();
        expect(calls('/api/auth/refresh')).toHaveLength(3); await expectUsable(recovered, 3);
    });
    it('A01: a newer successful sign-in after a superseded one wins over the waiting renewal', async () => {
        const { renewal } = await renewalPending();
        const older = deferred(); fetcher.mockReturnValueOnce(older.promise);
        let first!: Promise<unknown>;
        await act(async () => { first = auth.login(payload).catch((e) => e); });
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        const chosen = signed('B', 'Person B', sidB); fetcher.mockResolvedValueOnce(json(chosen));
        await act(async () => { await auth.login(payload); });
        await act(async () => older.resolve(json(signed('X', 'Person X', sidC))));
        expect(await first).toMatchObject({ kind: 'cancelled' }); await settle();
        expect(auth.user?.displayName).toBe('Person B'); expect(calls('/api/auth/refresh')).toHaveLength(2);
        expect(await bearer()).toBe(bearerOf(chosen));
    });
    it('A01: the recovery after a failed sign-in installs another sid as a replacement, never the stale bearer', async () => {
        const { first, renewal, generation } = await renewalPending();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ status: 401 }); });
        const recovered = signed('D', 'Person A', sidB); fetcher.mockResolvedValueOnce(json(recovered));
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(auth.generation).toBeGreaterThan(generation); expect(auth.user?.displayName).toBe('Person A');
        expect(await bearer()).not.toBe(bearerOf(first)); await expectUsable(recovered, 3, sidB);
    });
    it('A01: a refused recovery after a failed sign-in ends the session instead of keeping the stale bearer', async () => {
        const { renewal } = await renewalPending();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await expect(auth.login(payload)).rejects.toMatchObject({ status: 401 }); });
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => renewal.resolve(json(signed('R')))); await settle();
        expect(auth.user).toBeNull(); expect(calls('/api/auth/refresh')).toHaveLength(3);
    });
    it('a newer sign-out still cancels a pending sign-in', async () => {
        fetcher.mockResolvedValueOnce(json(null, 401)); await mountHybrid(); await settle();
        const work = deferred(); fetcher.mockReturnValueOnce(work.promise);
        let attempt!: Promise<unknown>;
        await act(async () => { attempt = auth.login(payload).catch((e) => e); });
        fetcher.mockResolvedValueOnce(empty()); await act(async () => auth.logout());
        await act(async () => work.resolve(json(session('Person B', sidB))));
        expect(await attempt).toMatchObject({ kind: 'cancelled' }); expect(auth.user).toBeNull();
    });
    it('never retries a mutation after a refusal or renewal', async () => {
        fetcher.mockResolvedValueOnce(json(session())); await mountHybrid(); await settle();
        fetcher.mockResolvedValueOnce(json(null, 401));
        await act(async () => { await expect(request('/courses/x/membership', { method: 'PUT', auth: 'authenticated', expectedStatus: 200, decode: (v) => v })).rejects.toMatchObject({ status: 401 }); });
        await settle();
        expect(fetcher.mock.calls.filter((call) => (call[1] as RequestInit).method === 'PUT')).toHaveLength(1);
        expect(auth.user).toBeNull();
    });
});
describe('CT02 password-reset REST client (FE06 part 1)', () => {
    const requested = passwordResetRequestedMessage, completed = passwordResetCompletedMessage;
    const requestPath = '/api/auth/password-reset-requests', resetPath = '/api/auth/password-resets';
    const token = `${'A'.repeat(42)}g`, exact = '  exact new password  ';
    const problem = (status: number, title: string, instance: string) => json({
        type: 'about:blank', title, status, detail: 'The request could not be completed.', instance,
    }, status, 'application/problem+json');
    const sent = (index = 0) => ({ url: fetcher.mock.calls[index][0] as string, init: fetcher.mock.calls[index][1] as RequestInit & { headers: Headers } });
    const thrown = (run: () => unknown) => { try { run(); } catch (error) { return error; } throw new Error('Expected a synchronous rejection.'); };
    it('requests a reset with the canonical CT01 email and returns the approved acknowledgement', async () => {
        fetcher.mockResolvedValueOnce(json({ message: requested }, 202));
        const result = await requestPasswordReset({ email: ' \tLEARNER+Tag@EXAMPLE.COM\n' });
        expect(result).toEqual({ message: requested }); expect(Object.isFrozen(result)).toBe(true);
        const { url, init } = sent();
        expect(url).toBe(`http://localhost:8080${requestPath}`);
        expect(init).toMatchObject({ method: 'POST', credentials: 'omit', redirect: 'error' });
        expect(JSON.parse(init.body as string)).toEqual({ email: 'learner+tag@example.com' });
        expect(init.headers.get('Content-Type')).toBe('application/json'); expect(init.headers.has('Authorization')).toBe(false);
        expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it.each(['\u212a@example.com', 'a@example.com\u00a0', '\ufeffa@example.com', '\u00e9@example.com', 'not-an-email', '', '   '])('rejects reset-request email %# before transport', (email) => {
        expect(thrown(() => requestPasswordReset({ email }))).toMatchObject({ kind: 'validation', fields: { email: 'Enter a valid ASCII email address.' } });
        expect(fetcher).not.toHaveBeenCalled();
    });
    it('confirms with the exact capability and untrimmed password in the POST body only', async () => {
        fetcher.mockResolvedValueOnce(json({ message: completed }));
        const result = await confirmPasswordReset({ token, password: exact });
        expect(result).toEqual({ message: completed }); expect(Object.isFrozen(result)).toBe(true);
        const { url, init } = sent();
        expect(url).toBe(`http://localhost:8080${resetPath}`); expect(url).not.toContain(token);
        expect(init).toMatchObject({ method: 'POST', credentials: 'omit', redirect: 'error' });
        expect(JSON.parse(init.body as string)).toEqual({ token, password: exact });
        expect(init.headers.has('Authorization')).toBe(false);
        expect(localStorage.length + sessionStorage.length).toBe(0);
    });
    it.each([
        ['a'.repeat(14), false], ['a'.repeat(15), true], ['😀'.repeat(14) + 'a'.repeat(16), true], ['😀'.repeat(14) + 'a'.repeat(17), false],
    ])('applies the CT01 password predicate %# before transport', async (password, valid) => {
        if (valid) {
            fetcher.mockResolvedValueOnce(json({ message: completed }));
            await confirmPasswordReset({ token, password }); expect(JSON.parse(sent().init.body as string).password).toBe(password);
        } else {
            expect(thrown(() => confirmPasswordReset({ token, password }))).toMatchObject({ kind: 'validation', fields: { password: 'Use at least 15 Unicode characters and at most 72 UTF-8 bytes.' } });
            expect(fetcher).not.toHaveBeenCalled();
        }
    });
    it.each([[{}], [null], [[]], ['text'], [{ message: 1 }], [{ message: 'Something else.' }], [{ message: requested, token: 'x' }], [{ message: completed }]])(
        'rejects acknowledgement body %# as a protocol error', async (body) => {
            fetcher.mockResolvedValueOnce(json(body, 202));
            await expect(requestPasswordReset({ email: 'a@example.com' })).rejects.toMatchObject({ kind: 'protocol' });
        });
    it.each([[{}], [null], [{ message: 'Signed in.' }], [{ message: completed, accessToken: 'x' }], [{ message: requested }]])(
        'rejects completion body %# as a protocol error', async (body) => {
            fetcher.mockResolvedValueOnce(json(body));
            await expect(confirmPasswordReset({ token, password: exact })).rejects.toMatchObject({ kind: 'protocol' });
        });
    it.each([
        ['request', 200, 'application/json'], ['request', 202, 'text/plain'], ['confirm', 202, 'application/json'], ['confirm', 200, 'application/problem+json'],
    ] as const)('rejects an unexpected %s success (%i %s)', async (operation, status, media) => {
        fetcher.mockResolvedValueOnce(json({ message: operation === 'request' ? requested : completed }, status, media));
        const call = operation === 'request' ? requestPasswordReset({ email: 'a@example.com' }) : confirmPasswordReset({ token, password: exact });
        await expect(call).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('rejects malformed success JSON', async () => {
        fetcher.mockResolvedValueOnce(new Response('{"message":', { status: 202, headers: { 'Content-Type': 'application/json' } }));
        await expect(requestPasswordReset({ email: 'a@example.com' })).rejects.toMatchObject({ kind: 'protocol' });
    });
    // AU07 answers every 400 of these operations with a field-free problem (BusinessRuleException): the capability
    // failure carries the ADR006 §9.2 detail, a refused password or closed-syntax failure the safe default detail.
    it.each([
        ['an invalid, used, replaced or expired capability', resetPath, 'Reset link is invalid or expired.'],
        ['a refused password or malformed confirmation', resetPath, 'The request is invalid.'],
        ['a malformed reset request', requestPath, 'The request is invalid.'],
    ])('maps the producer 400 for %s to a field-free error without server text', async (_case, path, detail) => {
        fetcher.mockResolvedValueOnce(json({ type: 'about:blank', title: 'Bad Request', status: 400, detail, instance: path }, 400, 'application/problem+json'));
        const call = path === resetPath ? confirmPasswordReset({ token, password: exact }) : requestPasswordReset({ email: 'a@example.com' });
        const error = await call.catch((failure) => failure);
        expect(error).toMatchObject({ kind: 'http', status: 400, message: 'Please check the supplied information.' });
        expect(error.fields).toEqual({}); expect(error.problem).toMatchObject({ detail: 'Please check the supplied information.', errors: [] });
        expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it.each([[415, 'Unsupported Media Type'], [500, 'Internal Server Error']])('maps %i without retrying', async (status, title) => {
        fetcher.mockResolvedValueOnce(problem(status, title, requestPath));
        await expect(requestPasswordReset({ email: 'a@example.com' })).rejects.toMatchObject({ kind: 'http', status });
        expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('maps a network failure without retrying', async () => {
        fetcher.mockRejectedValueOnce(new Error('private'));
        await expect(confirmPasswordReset({ token, password: exact })).rejects.toMatchObject({ kind: 'network' });
        expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('honours abort and isCurrent before transport and after the response', async () => {
        const aborted = new AbortController(); aborted.abort();
        await expect(requestPasswordReset({ email: 'a@example.com' }, aborted.signal)).rejects.toMatchObject({ kind: 'cancelled' });
        await expect(confirmPasswordReset({ token, password: exact }, undefined, () => false)).rejects.toMatchObject({ kind: 'cancelled' });
        expect(fetcher).not.toHaveBeenCalled();
        const late = new AbortController(), first = deferred(); fetcher.mockReturnValueOnce(first.promise);
        const pendingAbort = requestPasswordReset({ email: 'a@example.com' }, late.signal).catch((error) => error);
        late.abort(); first.resolve(json({ message: requested }, 202));
        expect(await pendingAbort).toMatchObject({ kind: 'cancelled' });
        let current = true; const second = deferred(); fetcher.mockReturnValueOnce(second.promise);
        const pendingStale = confirmPasswordReset({ token, password: exact }, undefined, () => current).catch((error) => error);
        current = false; second.resolve(json({ message: completed }));
        expect(await pendingStale).toMatchObject({ kind: 'cancelled' });
    });
    it('neither sends nor ends the current session', async () => {
        await mount(); await signIn(); const generation = auth.generation;
        fetcher.mockResolvedValueOnce(json({ message: completed }));
        await act(async () => { await confirmPasswordReset({ token, password: exact }); });
        const [url, init] = fetcher.mock.calls.at(-1)! as [string, RequestInit & { headers: Headers }];
        expect(url).toBe(`http://localhost:8080${resetPath}`); expect(init.credentials).toBe('omit'); expect(init.headers.has('Authorization')).toBe(false);
        expect(auth.isAuthenticated).toBe(true); expect(auth.generation).toBe(generation);
    });
});
