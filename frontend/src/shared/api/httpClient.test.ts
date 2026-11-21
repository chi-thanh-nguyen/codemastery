// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient } from './httpClient';
import type { AuthSessionAccess, HttpSession, MetadataRequestOptions, NoContentRequestOptions, RequestOptions, ResponseWithMetadata } from './httpClient';
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

describe('ADR014 refresh-cookie transport', () => {
    it.each(['/auth/login', '/auth/refresh', '/auth/logout'])('includes credentials only for the cookie operation %s', async (path) => {
        fetcher.mockResolvedValue(path === '/auth/logout' ? new Response(null, { status: 204 }) : json(user));
        const { access } = sessionAccess();
        await createHttpClient('https://api.test', access)(path, operation({ method: 'POST', ...(path === '/auth/logout' ? { expectedStatus: 204, decode: () => user } : {}) }));
        const init = fetcher.mock.calls[0][1];
        expect(init).toMatchObject({ credentials: 'include', redirect: 'error', cache: 'no-store' });
        expect(init.headers.has('Authorization')).toBe(false); expect(init.headers.has('Cookie')).toBe(false);
        expect(Boolean(init.keepalive)).toBe(path === '/auth/logout');
    });
    it.each([
        ['/auth/register', 'POST', 'public'], ['/auth/password-reset-requests', 'POST', 'public'], ['/courses', 'GET', 'public'],
        ['/me/courses', 'GET', 'authenticated'], ['/auth/refresh', 'GET', 'public'], ['/auth/refresh', 'POST', 'authenticated'],
    ] as const)('omits credentials for %s %s (%s)', async (path, method, auth) => {
        const { access } = sessionAccess();
        await createHttpClient('https://api.test', access)(path, operation({ method, auth })).catch(() => undefined);
        expect(fetcher.mock.calls[0][1].credentials).toBe('omit');
    });
    it('returns a 204 without reading a body and rejects an unexpected 204', async () => {
        const body = vi.fn();
        fetcher.mockResolvedValue({ ok: true, status: 204, headers: new Headers(), json: body });
        const decode = vi.fn(() => user);
        await expect(createHttpClient('https://api.test')('/auth/logout', operation({ method: 'POST', expectedStatus: 204, decode }))).resolves.toBe(user);
        expect(body).not.toHaveBeenCalled(); expect(decode).toHaveBeenCalledWith(undefined);
        fetcher.mockResolvedValue(new Response(null, { status: 204 }));
        await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('a refused refresh is a public 401 that never invalidates the in-memory session', async () => {
        const { access } = sessionAccess(); fetcher.mockResolvedValue(json(null, 401));
        await expect(createHttpClient('https://api.test', access)('/auth/refresh', operation({ method: 'POST' }))).rejects.toMatchObject({ kind: 'http', status: 401 });
        expect(access.invalidate).not.toHaveBeenCalled();
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

// ---- A14 / CT16-B1-NO-CONTENT-2026-11-09-r1 / CT14 B1-O19 (ADR005 §§16.2–16.3) ----
describe('explicit no-content mode', () => {
    const noContent = (extra: Partial<NoContentRequestOptions> = {}): NoContentRequestOptions => ({ method: 'DELETE', auth: 'public', expectedStatus: 204, responseMode: 'no-content', ...extra });
    it.each([['PUT', undefined], ['DELETE', undefined], ['PUT', 'application/json'], ['DELETE', 'text/html']] as const)('resolves an exact %s 204 (Content-Type %s) to undefined without reading a body', async (method, media) => {
        const response = new Response(null, { status: 204, headers: media ? { 'Content-Type': media } : {} });
        const json = vi.spyOn(response, 'json'), text = vi.spyOn(response, 'text');
        fetcher.mockResolvedValue(response);
        await expect(createHttpClient('https://api.test')('/courses/a/skills/b', noContent({ method }))).resolves.toBeUndefined();
        expect(json).not.toHaveBeenCalled(); expect(text).not.toHaveBeenCalled();
        expect(fetcher.mock.calls[0][1]).toMatchObject({ method, credentials: 'omit', redirect: 'error' });
    });
    it.each([200, 201, 202])('treats an unexpected %s success as a protocol failure, never as void success', async (status) => {
        fetcher.mockResolvedValue(json({}, status));
        await expect(createHttpClient('https://api.test')('/courses/a', noContent())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([
        ['a decoder', { decode: () => undefined }],
        ['a non-204 status', { expectedStatus: 200 }],
        ['an unknown mode', { responseMode: 'empty' }],
        ['metadata on POST', { responseMode: 'json-with-metadata', method: 'POST', expectedStatus: 200, decode: (value: unknown) => value }],
    ])('rejects %s before fetch, including untyped callers', async (_name, extra) => {
        const send = createHttpClient('https://api.test') as unknown as (path: string, options: object) => Promise<unknown>;
        await expect(send('/courses/a', { ...noContent(), ...extra })).rejects.toMatchObject({ kind: 'configuration' });
        expect(fetcher).not.toHaveBeenCalled();
    });
    it('keeps JSON callers strict: no decoder is a configuration error and a 204 is a protocol failure', async () => {
        const send = createHttpClient('https://api.test') as unknown as (path: string, options: object) => Promise<unknown>;
        await expect(send('/courses', { method: 'GET', auth: 'public', expectedStatus: 200 })).rejects.toMatchObject({ kind: 'configuration' });
        fetcher.mockResolvedValue(new Response(null, { status: 204 }));
        await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValue(new Response('', { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await expect(createHttpClient('https://api.test')('/courses', operation())).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([400, 401, 403, 404, 409, 412, 428, 500, 503])('keeps HTTP %s as an error in no-content mode, with valid or unusable problem bodies', async (status) => {
        const reason: Record<number, string> = { 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 409: 'Conflict', 412: 'Precondition Failed', 428: 'Precondition Required', 500: 'Internal Server Error', 503: 'Service Unavailable' };
        fetcher.mockResolvedValueOnce(json({ type: 'about:blank', title: reason[status], status, detail: 'server text', instance: '/api/courses/a' }, status, 'application/problem+json'));
        await expect(createHttpClient('https://api.test')('/courses/a', noContent())).rejects.toMatchObject({ kind: 'http', status });
        for (const body of [new Response('<html>x</html>', { status, headers: { 'Content-Type': 'text/html' } }), new Response('', { status, headers: { 'Content-Type': 'application/problem+json' } })]) {
            fetcher.mockResolvedValueOnce(body);
            await expect(createHttpClient('https://api.test')('/courses/a', noContent())).rejects.toMatchObject({ kind: 'http', status });
        }
    });
    it('gives 412 and 428 safe titles and local messages, never the server text', async () => {
        for (const [status, title] of [[412, 'Precondition Failed'], [428, 'Precondition Required']] as const) {
            fetcher.mockResolvedValueOnce(json({ type: 'about:blank', title, status, detail: 'PRIVATE SENTINEL', instance: '/api/courses/a/adaptive-settings' }, status, 'application/problem+json'));
            const error = await createHttpClient('https://api.test')('/courses/a/adaptive-settings', noContent({ method: 'PUT' })).catch((caught: unknown) => caught) as ApiError;
            expect(error.problem?.title).toBe(title); expect(error.message).not.toContain('PRIVATE'); expect(error.problem?.detail).not.toContain('PRIVATE');
        }
    });
    it('invalidates only a current authenticated 401 and keeps every current check through the return', async () => {
        const { access, logout } = sessionAccess(), send = createHttpClient('https://api.test', access);
        fetcher.mockResolvedValueOnce(new Response(null, { status: 401 }));
        await expect(send('/courses/a', noContent({ auth: 'authenticated' }))).rejects.toMatchObject({ status: 401 });
        expect(access.invalidate).toHaveBeenCalledWith(1);
        const second = sessionAccess(); let finish!: (response: Response) => void;
        fetcher.mockReturnValueOnce(new Promise<Response>((done) => { finish = done; }));
        const pending = createHttpClient('https://api.test', second.access)('/courses/a', noContent({ auth: 'authenticated' }));
        second.logout(); finish(new Response(null, { status: 204 }));
        await expect(pending).rejects.toMatchObject({ kind: 'cancelled' });
        const controller = new AbortController(); let settle!: (response: Response) => void;
        fetcher.mockReturnValueOnce(new Promise<Response>((done) => { settle = done; }));
        const aborted = createHttpClient('https://api.test')('/courses/a', noContent({ signal: controller.signal }));
        controller.abort(); settle(new Response(null, { status: 204 }));
        await expect(aborted).rejects.toMatchObject({ kind: 'cancelled' });
        let superseded = false; fetcher.mockResolvedValueOnce(new Response(null, { status: 204 }));
        const obsolete = createHttpClient('https://api.test')('/courses/a', noContent({ isCurrent: () => !superseded }));
        superseded = true;
        await expect(obsolete).rejects.toMatchObject({ kind: 'cancelled' });
        void logout; expect(fetcher).toHaveBeenCalledTimes(4); // no retries
    });
});
describe('GET response metadata (A14)', () => {
    const withMetadata = (extra: Partial<MetadataRequestOptions<typeof user>> = {}): MetadataRequestOptions<typeof user> =>
        ({ method: 'GET', auth: 'public', expectedStatus: 200, decode: (value) => decodePublicUser(value), responseMode: 'json-with-metadata', ...extra });
    it('returns the decoded body with its ETag verbatim, or null when absent', async () => {
        fetcher.mockResolvedValueOnce(new Response(JSON.stringify(user), { status: 200, headers: { 'Content-Type': 'application/json', ETag: '"v1-strong"' } }));
        await expect(createHttpClient('https://api.test')('/courses/a/adaptive-settings', withMetadata())).resolves.toEqual({ body: user, etag: '"v1-strong"' });
        fetcher.mockResolvedValueOnce(json(user));
        await expect(createHttpClient('https://api.test')('/courses/a/adaptive-settings', withMetadata())).resolves.toEqual({ body: user, etag: null });
    });
    it('never exposes a tag for a failed status, media, decoding or obsolete response', async () => {
        const tagged = (body: string, status: number, media: string) => new Response(body, { status, headers: { 'Content-Type': media, ETag: '"leak"' } });
        fetcher.mockResolvedValueOnce(tagged(JSON.stringify({ wrong: true }), 200, 'application/json'));
        await expect(createHttpClient('https://api.test')('/x', withMetadata())).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValueOnce(tagged(JSON.stringify(user), 201, 'application/json'));
        await expect(createHttpClient('https://api.test')('/x', withMetadata())).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValueOnce(tagged(JSON.stringify(user), 200, 'text/plain'));
        await expect(createHttpClient('https://api.test')('/x', withMetadata())).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValueOnce(tagged('{}', 404, 'application/problem+json'));
        await expect(createHttpClient('https://api.test')('/x', withMetadata())).rejects.toMatchObject({ kind: 'http', status: 404 });
        let current = true; fetcher.mockResolvedValueOnce(tagged(JSON.stringify(user), 200, 'application/json'));
        const late = createHttpClient('https://api.test')('/x', withMetadata({ isCurrent: () => current })); current = false;
        await expect(late).rejects.toMatchObject({ kind: 'cancelled' });
    });
    it('sends a caller If-Match unchanged and keeps credential headers managed by the client', async () => {
        fetcher.mockResolvedValueOnce(new Response(null, { status: 204 }));
        await createHttpClient('https://api.test')('/courses/a/adaptive-settings', { method: 'PUT', auth: 'public', expectedStatus: 204, responseMode: 'no-content', headers: { 'If-Match': '"v1-strong"' }, body: { enabled: true } });
        expect(fetcher.mock.calls[0][1].headers.get('If-Match')).toBe('"v1-strong"');
    });
});
// Compile-time overload checks (typecheck runs over test files).
async function typeChecks() {
    const send = createHttpClient('https://api.test');
    const nothing: void = await send('/a', { method: 'DELETE', auth: 'public', expectedStatus: 204, responseMode: 'no-content' });
    const pair: ResponseWithMetadata<number> = await send('/a', { method: 'GET', auth: 'public', expectedStatus: 200, decode: () => 1, responseMode: 'json-with-metadata' });
    const plain: number = await send('/a', { method: 'GET', auth: 'public', expectedStatus: 200, decode: () => 1 });
    // @ts-expect-error no-content takes no decoder
    await send('/a', { method: 'DELETE', auth: 'public', expectedStatus: 204, responseMode: 'no-content', decode: () => 1 });
    // @ts-expect-error no-content requires 204
    await send('/a', { method: 'DELETE', auth: 'public', expectedStatus: 200, responseMode: 'no-content' });
    // @ts-expect-error metadata is only for GET
    await send('/a', { method: 'POST', auth: 'public', expectedStatus: 200, decode: () => 1, responseMode: 'json-with-metadata' });
    return [nothing, pair, plain];
}
void typeChecks;
