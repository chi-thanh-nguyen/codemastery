import { ApiError, cancelled, isRecord, protocolError } from '../types/apiTypes';
import type { ProblemDetail } from '../types/apiTypes';

export interface HttpSession { generation: number; accessToken: string; expiresAt: number; signal?: AbortSignal }
export interface AuthSessionAccess {
    capture(): HttpSession | null;
    isCurrent(generation: number): boolean;
    invalidate(generation: number): void;
}
export interface RequestOptions<T> {
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    auth: 'public' | 'authenticated';
    expectedStatus: number;
    decode: (value: unknown) => T;
    query?: URLSearchParams;
    body?: unknown;
    headers?: HeadersInit;
    signal?: AbortSignal;
    /** Owning feature rejects superseded operations even within one session. */
    isCurrent?: () => boolean;
}
let authAccess: AuthSessionAccess | undefined;
/** The provider owns this binding; request callers cannot supply credentials. */
export function bindAuthSession(access: AuthSessionAccess) {
    authAccess = access;
    return () => { if (authAccess === access) authAccess = undefined; };
}
function rootFor(value: string, browserOrigin: string) {
    const input = value?.trim();
    const fail = () => { throw new ApiError('configuration', 'The API address is not configured correctly.'); };
    if (!input || /[\\<>\s]/.test(input)) return fail();
    let url: URL;
    try {
        if (input === '/api' || input === '/api/') url = new URL(browserOrigin);
        else {
            // Check the original spelling: URL parsers normalize unsafe paths.
            if (!/^https?:\/\/[^/?#]+\/?$/.test(input)) return fail();
            url = new URL(input);
        }
    } catch { return fail(); }
    if (url.username || url.password || url.search || url.hash || url.pathname !== '/') return fail();
    if (url.protocol !== 'https:' && !(url.protocol === 'http:' &&
        ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname))) return fail();
    return new URL('/api/', url.origin);
}
function endpointFor(root: URL, path: string, query?: URLSearchParams) {
    const fail = () => { throw new ApiError('configuration', 'The API request path is invalid.'); };
    if (!path.startsWith('/') || path.startsWith('//') || /[\\?#\s]/.test(path)) return fail();
    const segments = path.slice(1).split('/');
    if (!segments.length || segments.some((segment) => !segment)) return fail();
    const encoded = segments.map((segment) => {
        let decoded: string;
        try { decoded = decodeURIComponent(segment); } catch { return fail(); }
        if (decoded === '.' || decoded === '..' || /[\\/%:?#\s\u0000-\u001f\u007f]/.test(decoded)) return fail();
        return encodeURIComponent(decoded);
    });
    if (encoded[0].toLowerCase() === 'api') return fail();
    const url = new URL(encoded.join('/'), root);
    if (url.origin !== root.origin || !url.pathname.startsWith('/api/')) return fail();
    if (query) url.search = query.toString();
    return url;
}
const messages: Record<number, string> = {
    400: 'Please check the supplied information.', 401: 'Authentication failed.',
    403: 'Access is denied.', 404: 'The requested resource is unavailable.',
    409: 'The request conflicts with existing information.',
};
const reasons: Record<number, string> = {
    400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found',
    405: 'Method Not Allowed', 409: 'Conflict', 415: 'Unsupported Media Type',
    500: 'Internal Server Error', 503: 'Service Unavailable',
};
function httpFailure(status: number, value: unknown, media: string, path: string): ApiError {
    const message = messages[status] ?? 'The request could not be completed.';
    let problem: ProblemDetail | undefined;
    const fields: Record<string, string> = {};
    if (media === 'application/problem+json' && isRecord(value) && value.type === 'about:blank' &&
        value.status === status && typeof value.title === 'string' && value.title === reasons[status] && typeof value.detail === 'string' &&
        value.instance === path &&
        (value.errors === undefined || Array.isArray(value.errors) && value.errors.every((entry) =>
            isRecord(entry) && ['field', 'code', 'message'].every((key) => typeof entry[key] === 'string')))) {
        // Server text and arbitrary extensions never become UI/log diagnostics.
        const errors = Array.isArray(value.errors) ? value.errors as Record<string, string>[] : [];
        const safeErrors = errors.filter((entry) => ['email', 'password', 'displayName'].includes(entry.field))
            .sort((a, b) => a.field.localeCompare(b.field) || a.code.localeCompare(b.code))
            .map((entry) => ({ field: entry.field, code: /^[A-Z][A-Za-z]{0,39}$/.test(entry.code) && ['NotNull', 'NotBlank', 'Email', 'Pattern', 'Size', 'StrongPassword', 'DisplayName'].includes(entry.code) ? entry.code : 'Invalid', message: 'Please check this field.' }));
        for (const error of safeErrors) fields[error.field] ??= error.message;
        problem = { type: 'about:blank', title: reasons[status], status, detail: message, instance: path, errors: safeErrors };
    }
    return new ApiError('http', message, status, problem, fields);
}
/** Independently usable transport; the access seam is provider-owned. */
export function createHttpClient(apiBase: string, access?: AuthSessionAccess, browserOrigin = globalThis.location?.origin ?? '') {
    const root = rootFor(apiBase, browserOrigin);
    return async function send<T>(path: string, options: RequestOptions<T>): Promise<T> {
        const url = endpointFor(root, path, options.query);
        if (!['public', 'authenticated'].includes(options.auth) || typeof options.decode !== 'function' ||
            !['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(options.method) ||
            options.method === 'GET' && options.body !== undefined) {
            throw new ApiError('configuration', 'The API operation is not configured correctly.');
        }
        const session = options.auth === 'authenticated' ? access?.capture() : undefined;
        const signals = [options.signal, session?.signal].filter((signal): signal is AbortSignal => !!signal);
        const signal = signals.length > 1 ? AbortSignal.any(signals) : signals[0];
        const current = () => !signal?.aborted && options.isCurrent?.() !== false &&
            (!session || !!access?.isCurrent(session.generation) && Date.now() < session.expiresAt);
        const requireCurrent = () => { if (!current()) throw cancelled(); };
        requireCurrent();
        if (options.auth === 'authenticated' && !session) throw new ApiError('http', 'Please sign in to continue.', 401);
        const headers = new Headers(options.headers);
        if (['authorization', 'cookie', 'proxy-authorization', 'accept', 'content-type'].some((key) => headers.has(key))) {
            throw new ApiError('configuration', 'Credential and representation headers are managed by the API client.');
        }
        headers.set('Accept', 'application/json, application/problem+json');
        if (options.body !== undefined) headers.set('Content-Type', 'application/json');
        if (session) headers.set('Authorization', `Bearer ${session.accessToken}`);
        let response: Response;
        try {
            response = await fetch(url.href, {
                method: options.method, headers, signal,
                body: options.body === undefined ? undefined : JSON.stringify(options.body),
                credentials: 'omit', redirect: 'error',
                ...(session || path === '/auth/login' || path === '/auth/register' ? { cache: 'no-store' as const } : {}),
            });
        } catch {
            requireCurrent();
            throw new ApiError('network', 'Unable to connect. Please try again.');
        }
        requireCurrent();
        // Do not await an untrusted error stream before invalidating current 401.
        if (response.status === 401 && session) {
            access!.invalidate(session.generation);
            throw httpFailure(401, undefined, '', url.pathname);
        }
        const media = (response.headers.get('Content-Type') ?? '').split(';')[0].trim().toLowerCase();
        if (response.ok && (response.status !== options.expectedStatus || media !== 'application/json')) throw protocolError();
        if (!response.ok && media !== 'application/problem+json') throw httpFailure(response.status, undefined, media, url.pathname);
        let value: unknown;
        try { value = await response.json(); } catch {
            requireCurrent();
            if (!response.ok) throw httpFailure(response.status, undefined, media, url.pathname);
            throw protocolError();
        }
        requireCurrent();
        if (!response.ok) throw httpFailure(response.status, value, media, url.pathname);
        if (response.status !== options.expectedStatus || media !== 'application/json') throw protocolError();
        try {
            const decoded = options.decode(value);
            requireCurrent();
            return decoded;
        } catch (error) {
            if (error instanceof ApiError && error.kind === 'cancelled') throw error;
            throw protocolError();
        }
    };
}
export function request<T>(path: string, options: RequestOptions<T>): Promise<T> {
    return createHttpClient(import.meta.env.VITE_API_BASE_URL ?? '', authAccess)(path, options);
}
