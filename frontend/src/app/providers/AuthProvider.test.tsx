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
import { registrationInput, canonicalEmail, decodeLoginResponse } from '../../features/auth/types/authTypes';
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
        expect(host.textContent).toContain('Your account was registered.'); expect(auth.user).toBeNull();
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
        const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!; expect(button.disabled).toBe(true); expect(button.textContent).toBe(kind === 'login' ? 'Sign in' : 'Register');
        const bar = host.querySelector('[role="progressbar"]')!; expect(document.getElementById(bar.getAttribute('aria-labelledby')!)?.textContent).toBe(kind === 'login' ? 'Signing in…' : 'Registering account…');
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
