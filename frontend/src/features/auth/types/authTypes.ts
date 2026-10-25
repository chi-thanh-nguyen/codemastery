import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';

export type UserRole = 'Learner' | 'Instructor' | 'Administrator';
export interface PublicUser { id: string; displayName: string; role: UserRole }
export interface RegisterRequest { email: string; password: string; displayName: string }
export interface LoginRequest { email: string; password: string }
export interface LoginResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: 900; user: PublicUser }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const control = /[\u0000-\u001f\u007f-\u009f]/;
// Match the producer's Java String.strip (not JS trim's extra NBSP/BOM removal).
export const stripInput = (value: string) => value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g, '');
function invalid(field: string, message: string): never {
    throw new ApiError('validation', 'Please check the supplied information.', undefined, undefined, { [field]: message });
}
export function canonicalEmail(value: string) {
    const trimmed = stripInput(value);
    // Reject Unicode BEFORE lowercasing (Kelvin sign must never become ASCII k).
    if (!/^[\x21-\x7e]+$/.test(trimmed)) return invalid('email', 'Enter a valid ASCII email address.');
    const email = trimmed.toLowerCase(), split = email.lastIndexOf('@');
    const local = email.slice(0, split), domain = email.slice(split + 1);
    const atom = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+";
    const quoted = '"(?:[a-z0-9!#$%&\'*.(),<>\\[\\]:;@+/=?^_`{|}~-]|\\\\[\\\\"])+"';
    const localPattern = new RegExp(`^(?:${atom}|${quoted})(?:\\.(?:${atom}|${quoted}))*$`, 'i');
    const label = /^[a-z0-9!#$%&'*+/=?^_`{|}~]+(?:-+[a-z0-9!#$%&'*+/=?^_`{|}~]+)*$/i;
    let domainValid = domain.length <= 255 && domain.split('.').every((part) => part.length <= 63 && label.test(part));
    if (/^\[\d{1,3}(?:\.\d{1,3}){3}\]$/.test(domain)) domainValid = true;
    if (domain.startsWith('[ipv6:') && domain.endsWith(']')) {
        try { domainValid = new URL(`http://[${domain.slice(6, -1)}]/`).hostname.startsWith('['); } catch { domainValid = false; }
    }
    if (split < 1 || local.length > 64 || !localPattern.test(local) || !domainValid) {
        return invalid('email', 'Enter a valid ASCII email address.');
    }
    return email;
}
export function registrationInput(payload: RegisterRequest): RegisterRequest {
    const email = canonicalEmail(payload.email), displayName = stripInput(payload.displayName);
    if (!displayName || [...displayName].length > 100 || control.test(displayName)) {
        return invalid('displayName', 'Use a nonblank display name of at most 100 characters without controls.');
    }
    if ([...payload.password].length < 15 || new TextEncoder().encode(payload.password).length > 72) {
        return invalid('password', 'Use at least 15 Unicode characters and at most 72 UTF-8 bytes.');
    }
    return { email, displayName, password: payload.password };
}
export function loginInput(payload: LoginRequest): LoginRequest {
    return { email: canonicalEmail(payload.email), password: payload.password };
}
function exactKeys(value: Record<string, unknown>, keys: string[]) {
    return Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key));
}
export function decodePublicUser(value: unknown): PublicUser {
    if (!isRecord(value) || !exactKeys(value, ['id', 'displayName', 'role']) ||
        typeof value.id !== 'string' || !uuid.test(value.id) || typeof value.displayName !== 'string' ||
        !stripInput(value.displayName) || [...value.displayName].length > 100 || control.test(value.displayName) ||
        !['Learner', 'Instructor', 'Administrator'].includes(value.role as string)) throw protocolError();
    return Object.freeze({ id: value.id, displayName: value.displayName, role: value.role as UserRole });
}
function jwtPart(value: string): unknown {
    if (!/^[A-Za-z0-9_-]+$/.test(value)) throw protocolError();
    const bytes = Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
}
/** Browser parsing only checks representation/expiry; it does not verify signatures or authorize operations. */
export function loginExpiresAt(response: LoginResponse, receivedAt: number): number {
    const parts = response.accessToken.split('.');
    if (parts.length !== 3 || !/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(parts[2])) throw protocolError();
    const header = jwtPart(parts[0]), claims = jwtPart(parts[1]);
    if (!isRecord(header) || header.alg !== 'HS256' || header.typ !== 'JWT' || !isRecord(claims) ||
        claims.iss !== 'codemastery' || !(claims.aud === 'codemastery-api' ||
            Array.isArray(claims.aud) && claims.aud.every((entry) => typeof entry === 'string') && claims.aud.includes('codemastery-api')) ||
        typeof claims.sub !== 'string' || !uuid.test(claims.sub) || claims.sub.toLowerCase() !== response.user.id.toLowerCase() ||
        !Number.isSafeInteger(claims.iat) || !Number.isSafeInteger(claims.exp) ||
        (claims.iat as number) > receivedAt / 1000 || (claims.exp as number) - (claims.iat as number) !== 900) throw protocolError();
    const expiry = Math.min(receivedAt + response.expiresIn * 1000, (claims.exp as number) * 1000);
    if (expiry <= receivedAt) throw protocolError();
    return expiry;
}
export function decodeLoginResponse(value: unknown): LoginResponse {
    if (!isRecord(value) || !exactKeys(value, ['accessToken', 'tokenType', 'expiresIn', 'user']) ||
        typeof value.accessToken !== 'string' || value.tokenType !== 'Bearer' || value.expiresIn !== 900) throw protocolError();
    const response: LoginResponse = { accessToken: value.accessToken, tokenType: 'Bearer', expiresIn: 900, user: decodePublicUser(value.user) };
    loginExpiresAt(response, Date.now());
    return response;
}
