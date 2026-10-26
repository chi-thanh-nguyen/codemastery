// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient } from './httpClient';
import type { AuthSessionAccess, HttpSession, RequestOptions } from './httpClient';
import { decodePublicUser } from '../../features/auth/types/authTypes';
import { ApiError } from '../types/apiTypes';
const user = { id: '00000000-0000-4000-8000-000000000001', displayName: 'Learner', role: 'Learner' };
const json = (value: unknown, status = 200, media = 'application/json') => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': media } });
const operation = (extra: Partial<RequestOptions<typeof user>> = {}): RequestOptions<typeof user> => ({ method: 'GET', auth: 'public', expectedStatus: 200, decode: (value) => decodePublicUser(value), ...extra });
let fetcher: ReturnType<typeof vi.fn>;
beforeEach(() => { fetcher = vi.fn().mockImplementation(async () => json(user)); vi.stubGlobal('fetch', fetcher); });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
function sessionAccess() {
    let session: HttpSession | null = { generation: 1, accessToken: 'synthetic-session', expiresAt: Date.now() + 10000 };
    const access: AuthSessionAccess = { capture: () => session, isCurrent: (id) => session?.generation === id, invalidate: vi.fn(() => { session = null; }) };
    return { access, replace: () => { session = { generation: 2, accessToken: 'synthetic-next', expiresAt: Date.now() + 10000 }; }, logout: () => { session = null; } };
}
describe('CT16 HTTP boundary', () => {
    it.each(['', ' ', '//outside.test', 'https://api.test/api', 'https://a:b@api.test', 'https://api.test?x=1', 'https://api.test/#x', 'http://outside.test', '/other', 'https://api.test/../', 'https://api.test\\evil'])('rejects invalid API configuration %s before fetch', (base) => {
        expect(() => createHttpClient(base)).toThrow(ApiError); expect(fetcher).not.toHaveBeenCalled();
    });
    it.each(['http://localhost:8080', 'http://localhost:8080/'])('resolves absolute API root once for %s', async (base) => {
        await createHttpClient(base)('/courses', operation({ query: new URLSearchParams({ page: '0', q: 'a & b' }) }));
        expect(fetcher.mock.calls[0][0]).toBe('http://localhost:8080/api/courses?page=0&q=a+%26+b');
    });
    it.each(['/api', '/api/'])('resolves same-origin configuration %s', async (base) => {
        await createHttpClient(base, undefined, 'https://app.test')('/courses/caf%C3%A9', operation());
        expect(fetcher.mock.calls[0][0]).toBe('https://app.test/api/courses/caf%C3%A9');
    });
    it.each(['https://outside.test/data', '//outside.test/data', '/api/courses', '/API/courses', '/../secret', '/a/./b', '/a/%2e%2e/b', '/a/%252e%252e/b', '/a/%2fb', '/a/%5cb', '/a\\b', '/a?token=x', '/a#x', '/a//b', 'courses', '/a/%ZZ', '/a/%00'])('rejects unsafe endpoint %s', async (path) => {
        await expect(createHttpClient('https://api.test')(path, operation())).rejects.toMatchObject({ kind: 'configuration' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it.each([new Headers({ 'X-Trace': 'one' }), [['X-Trace', 'one']] as [string, string][], { 'X-Trace': 'one' }])('normalizes every HeadersInit form', async (headers) => {
        await createHttpClient('https://api.test')('/courses', operation({ headers }));
        const init = fetcher.mock.calls[0][1]; expect(init.headers.get('X-Trace')).toBe('one');
        expect(init.headers.get('Accept')).toBe('application/json, application/problem+json');
        expect(init.headers.has('Content-Type')).toBe(false); expect(init.credentials).toBe('omit'); expect(init.redirect).toBe('error');
    });
    it.each(['Authorization', 'Cookie', 'Proxy-Authorization', 'Accept', 'Content-Type'])('rejects caller-controlled %s', async (header) => {
        await expect(createHttpClient('https://api.test')('/courses', operation({ headers: { [header]: 'unsafe' } }))).rejects.toMatchObject({ kind: 'configuration' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it('public calls omit bearer; private calls capture eligible provider credentials with no request-option bypass', async () => {
        const { access } = sessionAccess(), send = createHttpClient('https://api.test', access);
        await send('/auth/register', operation({ method: 'POST', body: { public: true }, ...({ credentials: 'include', redirect: 'follow', token: 'caller-token' } as object) }));
        expect(fetcher.mock.calls[0][1].headers.has('Authorization')).toBe(false);
        expect(fetcher.mock.calls[0][1]).toMatchObject({ credentials: 'omit', redirect: 'error', cache: 'no-store' });
        expect(fetcher.mock.calls[0][1].headers.get('Content-Type')).toBe('application/json');
        await send('/courses', operation({ auth: 'authenticated' }));
        expect(fetcher.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer synthetic-session');
        expect(fetcher.mock.calls[1][1].cache).toBe('no-store');
    });
    it('absent or expired credentials cannot send a protected request', async () => {
        await expect(createHttpClient('https://api.test')('/courses', operation({ auth: 'authenticated' }))).rejects.toMatchObject({ status: 401 });
        const { access } = sessionAccess(); vi.spyOn(access, 'capture').mockReturnValue({ generation: 1, accessToken: 'expired', expiresAt: 0 });
        await expect(createHttpClient('https://api.test', access)('/courses', operation({ auth: 'authenticated' }))).rejects.toMatchObject({ kind: 'cancelled' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it.each([201, 202, 204])('rejects operation-incompatible success status %s', async (status) => {
        fetcher.mockResolvedValue(status === 204 ? new Response(null, { status }) : json(user, status));
        await expect(createHttpClient('https://api.test')('/auth/login', operation())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each(['text/html', '', 'application/problem+json'])('rejects success media %s', async (media) => {
        fetcher.mockResolvedValue(json(user, 200, media)); await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([null, {}, [], { ...user, role: 'Owner' }, { ...user, id: 'not-uuid' }, { ...user, email: 'private' }])('requires a valid decoded DTO %#', async (value) => {
        fetcher.mockResolvedValue(json(value)); await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each(['', '<html>PRIVATE SENTINEL</html>', '{invalid'])('rejects empty/malformed success without disclosing body', async (body) => {
        fetcher.mockResolvedValue(new Response(body, { headers: { 'Content-Type': 'application/json' } }));
        const error = await createHttpClient('https://api.test')('/courses', operation()).catch((e) => e);
        expect(error).toMatchObject({ kind: 'protocol' }); expect(String(error)).not.toContain('SENTINEL');
    });
    it.each([null, { status: 401 }, { type: 'about:blank', title: 'Private', status: 401, detail: 'password=SENTINEL', instance: '/courses' }])('retains actual HTTP status for malformed problem %#', async (value) => {
        fetcher.mockResolvedValue(json(value, 503, 'application/problem+json'));
        const error = await createHttpClient('https://api.test')('/courses', operation()).catch((e) => e);
        expect(error).toMatchObject({ kind: 'http', status: 503 }); expect(JSON.stringify(error)).not.toContain('SENTINEL');
    });
    it('sanitizes valid field errors and uses approved field/code order', async () => {
        fetcher.mockResolvedValue(json({ type: 'about:blank', title: 'Bad Request', status: 400, detail: 'SENTINEL', instance: '/api/auth/register', errors: [
            { field: 'password', code: 'StrongPassword', message: 'SENTINEL' }, { field: 'email', code: 'Pattern', message: '<img onerror=SENTINEL>' }, { field: 'secret', code: 'Oops', message: 'SENTINEL' },
        ] }, 400, 'application/problem+json; charset=utf-8'));
        const error = await createHttpClient('https://api.test')('/auth/register', operation()).catch((e) => e);
        expect(error.fields).toEqual({ email: 'Please check this field.', password: 'Please check this field.' });
        expect(error.problem.errors.map((entry: { field: string }) => entry.field)).toEqual(['email', 'password']); expect(JSON.stringify(error)).not.toContain('SENTINEL');
    });
    it.each([401, 403, 404, 405, 409, 415, 500, 503])('preserves status %s with HTML or empty error', async (status) => {
        fetcher.mockResolvedValue(new Response('<html>private</html>', { status, headers: { 'Content-Type': 'text/html' } }));
        await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'http', status });
    });
    it('classifies network failure and never retries mutations', async () => {
        fetcher.mockRejectedValue(new Error('sensitive network diagnostics'));
        const error = await createHttpClient('https://api.test')('/auth/login', operation({ method: 'POST', body: {} })).catch((e) => e);
        expect(error).toMatchObject({ kind: 'network' }); expect(String(error)).not.toContain('sensitive'); expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('forwards abort and suppresses even a successful response from an abort-ignoring transport', async () => {
        let resolve!: (response: Response) => void; fetcher.mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
        const controller = new AbortController(), promise = createHttpClient('https://api.test')('/courses', operation({ signal: controller.signal })).catch((e) => e);
        expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal); controller.abort(); resolve(json(user));
        expect(await promise).toMatchObject({ kind: 'cancelled' });
    });
    it.each([200, 401, 403])('suppresses stale private response %s after replacement', async (status) => {
        let resolve!: (response: Response) => void; fetcher.mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
        const { access, replace } = sessionAccess(); const promise = createHttpClient('https://api.test', access)('/courses', operation({ auth: 'authenticated' })).catch((e) => e);
        replace(); resolve(json(user, status)); expect(await promise).toMatchObject({ kind: 'cancelled' }); expect(access.invalidate).not.toHaveBeenCalled();
    });
    it('suppresses stale feature operations and invalidates only current authenticated 401', async () => {
        const { access } = sessionAccess(); fetcher.mockResolvedValue(json(null, 401));
        await expect(createHttpClient('https://api.test', access)('/courses', operation())).rejects.toMatchObject({ status: 401 }); expect(access.invalidate).not.toHaveBeenCalled();
        await expect(createHttpClient('https://api.test', access)('/courses', operation({ auth: 'authenticated' }))).rejects.toMatchObject({ status: 401 }); expect(access.invalidate).toHaveBeenCalledExactlyOnceWith(1);
        await expect(createHttpClient('https://api.test')('/courses', operation({ isCurrent: () => false }))).rejects.toMatchObject({ kind: 'cancelled' }); expect(fetcher).toHaveBeenCalledTimes(2);
    });
});

it('suppresses session replacement during JSON decoding, not only during fetch', async () => {
    let finish!: (value: unknown) => void; const body = new Promise<unknown>((done) => { finish = done; });
    fetcher.mockResolvedValue({ ok: true, status: 200, headers: new Headers({ 'Content-Type': 'application/json' }), json: () => body });
    const { access, replace } = sessionAccess(); const result = createHttpClient('https://api.test', access)('/courses', operation({ auth: 'authenticated' })).catch((e) => e);
    await Promise.resolve(); replace(); finish(user); expect(await result).toMatchObject({ kind: 'cancelled' });
});

it('malformed ProblemDetail missing its title retains status with no field diagnostics', async () => {
    fetcher.mockResolvedValue(json({ type: 'about:blank', status: 400, detail: 'PRIVATE SENTINEL', instance: '/api/auth/register', errors: [{ field: 'email', code: 'Email', message: 'PRIVATE SENTINEL' }] }, 400, 'application/problem+json'));
    const result = await createHttpClient('https://api.test')('/auth/register', operation()).catch((e) => e);
    expect(result).toMatchObject({ kind: 'http', status: 400, fields: {} }); expect(result.problem).toBeUndefined(); expect(JSON.stringify(result)).not.toContain('PRIVATE SENTINEL');
});
