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
    /** Exact operation-defined successes; a set never causes mutation replay. */
    expectedStatus: number | readonly number[];
    decode: (value: unknown) => T;
    query?: URLSearchParams;
    body?: unknown;
    /** ADR010 §11: raw FormData, with the boundary owned by the browser. */
    bodyFormat?: 'multipart';
    headers?: HeadersInit;
    signal?: AbortSignal;
    /** Owning feature rejects superseded operations even within one session. */
    isCurrent?: () => boolean;
    /** Default JSON mode: exact status, JSON media, a nonempty body and successful decoding. */
    responseMode?: 'json';
}
/**
 * CT16-B1-NO-CONTENT-2026-11-09-r1 (ADR005 §16.2): explicitly selected by approved 204 operations.
 * Only an exact 204 succeeds; no media type is required, no body is read and no decoder runs.
 */
export type NoContentRequestOptions =
    Omit<RequestOptions<never>, 'responseMode' | 'expectedStatus' | 'decode'> &
    { responseMode: 'no-content'; expectedStatus: 204; decode?: never };
/**
 * A14 response metadata (setup.md Batch 1 / R1–R2 handoffs, ADR005 §§13, 15): a JSON `GET` that also
 * returns the response `ETag`, observed only after the status, media type, decoding and every
 * current-generation check succeeded. The tag is returned verbatim (or null when absent); the
 * caller keeps it paired with this body and never derives or combines tags.
 */
export type MetadataRequestOptions<T> = Omit<RequestOptions<T>, 'responseMode' | 'method'> & { method: 'GET'; responseMode: 'json-with-metadata' };
export interface ResponseWithMetadata<T> { body: T; etag: string | null }
/** The transport entry point: JSON by default, plus the explicit no-content and metadata modes. */
export type BinaryDownloadOptions = Omit<RequestOptions<never>, 'responseMode' | 'method' | 'decode' | 'body' | 'bodyFormat' | 'auth' | 'expectedStatus'> & {
    method: 'GET'; auth: 'authenticated'; expectedStatus: 200; responseMode: 'binary'; decode?: never; body?: never;
};
export interface BinaryDownload { blob: Blob; filename: string }
export interface HttpSend {
    (path: string, options: BinaryDownloadOptions): Promise<BinaryDownload>;
    (path: string, options: NoContentRequestOptions): Promise<void>;
    <T>(path: string, options: MetadataRequestOptions<T>): Promise<ResponseWithMetadata<T>>;
    <T>(path: string, options: RequestOptions<T>): Promise<T>;
}
type AnyRequestOptions = RequestOptions<unknown> | NoContentRequestOptions | MetadataRequestOptions<unknown> | BinaryDownloadOptions;
let authAccess: AuthSessionAccess | undefined;
/**
 * The only operations that may carry the HttpOnly refresh cookie (ADR014 §5). Every other
 * request omits credentials, so the cookie never reaches unrelated endpoints.
 */
const cookieOperations = new Set(['/auth/login', '/auth/refresh', '/auth/logout']);
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
    413: 'Content Too Large',
    409: 'The request conflicts with existing information.',
    412: 'This information was changed elsewhere. Review the latest version before saving again.',
    428: 'Your copy of this information is out of date. Load the latest version before saving again.',
};
const reasons: Record<number, string> = {
    400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found',
    405: 'Method Not Allowed', 413: 'Content Too Large', 409: 'Conflict', 412: 'Precondition Failed', 415: 'Unsupported Media Type', 428: 'Precondition Required',
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
export function createHttpClient(apiBase: string, access?: AuthSessionAccess, browserOrigin = globalThis.location?.origin ?? ''): HttpSend {
    const root = rootFor(apiBase, browserOrigin);
    async function send(path: string, options: AnyRequestOptions): Promise<unknown> {
        const url = endpointFor(root, path, options.query);
        const mode = options.responseMode ?? 'json';
        // Modes are chosen explicitly; nothing is inferred from method, decoder, status or payload.
        const expected = typeof options.expectedStatus === 'number' ? [options.expectedStatus] : options.expectedStatus;
        const statusValid = Array.isArray(expected) && expected.length > 0 && expected.every(s => Number.isInteger(s) && s >= 200 && s < 300) && new Set(expected).size === expected.length;
        const acceptsStatus = (status: number) => expected.includes(status);
        const multipart = 'bodyFormat' in options && options.bodyFormat === 'multipart';
        const modeValid = mode === 'binary' ? options.method === 'GET' && options.auth === 'authenticated' && options.expectedStatus === 200
            : mode === 'no-content'
            ? options.decode === undefined && options.expectedStatus === 204
            : (mode === 'json' || mode === 'json-with-metadata' && options.method === 'GET') && typeof options.decode === 'function';
        if (!statusValid || !modeValid || !['public', 'authenticated'].includes(options.auth) ||
            !['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(options.method) ||
            options.method === 'GET' && options.body !== undefined ||
            multipart && (!(options.body instanceof FormData) || options.auth !== 'authenticated' || !['POST', 'PUT'].includes(options.method))) {
            throw new ApiError('configuration', 'The API operation is not configured correctly.');
        }
        const session = options.auth === 'authenticated' ? access?.capture() : undefined;
        const cookieOperation = options.auth === 'public' && options.method === 'POST' && cookieOperations.has(path);
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
        headers.set('Accept', mode === 'binary' ? 'application/pdf, application/zip, application/problem+json' : 'application/json, application/problem+json');
        if (options.body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
        if (session) headers.set('Authorization', `Bearer ${session.accessToken}`);
        let response: Response;
        try {
            response = await fetch(url.href, {
                method: options.method, headers, signal,
                body: multipart ? options.body as FormData : options.body === undefined ? undefined : JSON.stringify(options.body),
                credentials: cookieOperation ? 'include' : 'omit', redirect: 'error',
                // Sign-out must reach the server even while the page unloads.
                ...(cookieOperation && path === '/auth/logout' ? { keepalive: true } : {}),
                ...(session || cookieOperation || path === '/auth/register' ? { cache: 'no-store' as const } : {}),
            });
        } catch {
            requireCurrent();
            throw multipart ? unknownUpload() : new ApiError('network', 'Unable to connect. Please try again.');
        }
        requireCurrent();
        // Do not await an untrusted error stream before invalidating current 401.
        if (response.status === 401 && session) {
            access!.invalidate(session.generation);
            throw httpFailure(401, undefined, '', url.pathname);
        }
        if (multipart && response.status >= 500) throw unknownUpload(response.status);
        if (mode === 'binary' && response.ok) {
            const media = (response.headers.get('Content-Type') ?? '').split(';')[0].trim().toLowerCase();
            if (response.status !== 200 || !['application/pdf', 'application/zip'].includes(media)) throw protocolError();
            const filename = downloadFilename(response.headers.get('Content-Disposition'));
            let blob: Blob;
            try { blob = await response.blob(); } catch { requireCurrent(); throw new ApiError('network', 'The download could not be completed.'); }
            requireCurrent();
            return { blob, filename };
        }
        if (mode === 'no-content' && response.ok) {
            // Only an exact 204 succeeds; its body is never read. Other successes are protocol failures.
            if (response.status !== 204) throw protocolError();
            requireCurrent();
            return undefined;
        }
        if (mode === 'json' && response.status === 204 && options.expectedStatus === 204) {
            // ADR014 sign-out: no representation; a 204 body is never read or decoded as data.
            const decoded = options.decode!(undefined);
            requireCurrent();
            return decoded;
        }
        const media = (response.headers.get('Content-Type') ?? '').split(';')[0].trim().toLowerCase();
        if (response.ok && (!acceptsStatus(response.status) || media !== 'application/json')) throw multipart ? unknownUpload(response.status) : protocolError();
        if (!response.ok && media !== 'application/problem+json') throw httpFailure(response.status, undefined, media, url.pathname);
        let value: unknown;
        try { value = await response.json(); } catch {
            requireCurrent();
            if (!response.ok) throw httpFailure(response.status, undefined, media, url.pathname);
            throw multipart ? unknownUpload(response.status) : protocolError();
        }
        requireCurrent();
        if (!response.ok) throw httpFailure(response.status, value, media, url.pathname);
        if (!acceptsStatus(response.status) || media !== 'application/json') throw multipart ? unknownUpload(response.status) : protocolError();
        let decoded: unknown;
        try {
            decoded = options.decode!(value);
        } catch (error) {
            if (error instanceof ApiError && error.kind === 'cancelled') throw error;
            throw multipart ? unknownUpload(response.status) : protocolError();
        }
        requireCurrent();
        // The validator is read only now, after status, media, decoding and current checks (A14).
        return mode === 'json-with-metadata' ? { body: decoded, etag: response.headers.get('ETag') } : decoded;
    }
    return send as HttpSend;
}
function unknownUpload(status?: number) {
    return new ApiError('unknown-outcome', 'The upload outcome is unknown. Reload the resource before uploading again.', status);
}
function downloadFilename(header: string | null): string {
    if (!header || !/^attachment(?:;|$)/i.test(header)) throw protocolError();
    const extended = /(?:^|;)\s*filename\*=UTF-8''([^;]*)/i.exec(header);
    const fallback = /(?:^|;)\s*filename="([^"]*)"/i.exec(header);
    let name: string;
    try { name = extended ? decodeURIComponent(extended[1]) : fallback?.[1] ?? ''; } catch { throw protocolError(); }
    if (!name.trim() || /[\\/\u0000-\u001f\u007f]/.test(name)) throw protocolError();
    return name;
}
export function request(path: string, options: BinaryDownloadOptions): Promise<BinaryDownload>;
export function request(path: string, options: NoContentRequestOptions): Promise<void>;
export function request<T>(path: string, options: MetadataRequestOptions<T>): Promise<ResponseWithMetadata<T>>;
export function request<T>(path: string, options: RequestOptions<T>): Promise<T>;
export function request(path: string, options: AnyRequestOptions): Promise<unknown> {
    return (createHttpClient(import.meta.env.VITE_API_BASE_URL ?? '', authAccess) as (path: string, options: AnyRequestOptions) => Promise<unknown>)(path, options);
}
