import { protocolError } from '../../../shared/types/apiTypes';
import { request } from '../../../shared/api/httpClient';
import {
    decodeLoginResponse, decodePasswordResetCompleted, decodePasswordResetRequested, decodePublicUser, loginInput,
    passwordResetConfirmInput, passwordResetRequestInput, registrationInput,
} from '../types/authTypes';
import type { LoginRequest, PasswordResetConfirmPayload, PasswordResetRequestPayload, RegisterRequest } from '../types/authTypes';
export function registerLearner(payload: RegisterRequest, signal?: AbortSignal, isCurrent?: () => boolean) {
    return request('/auth/register', {
        method: 'POST', auth: 'public', expectedStatus: 201, decode: (value) => {
            const user = decodePublicUser(value);
            if (user.role !== 'Learner') throw protocolError();
            return user;
        },
        body: registrationInput(payload), signal, isCurrent,
    });
}
export function login(payload: LoginRequest, signal?: AbortSignal, isCurrent?: () => boolean) {
    return request('/auth/login', {
        method: 'POST', auth: 'public', expectedStatus: 200, decode: decodeLoginResponse,
        body: loginInput(payload), signal, isCurrent,
    });
}
/** ADR014 rotation: the HttpOnly cookie is the only credential; the result has the login shape. */
export function refreshSession(signal?: AbortSignal) {
    return request('/auth/refresh', { method: 'POST', auth: 'public', expectedStatus: 200, decode: decodeLoginResponse, signal });
}
/** ADR014 logout: revokes this browser's server session and deletes its cookie (204). */
export function endSession(signal?: AbortSignal) {
    return request('/auth/logout', { method: 'POST', auth: 'public', expectedStatus: 204, decode: () => undefined, signal });
}
/** CT02-REST (ADR006 §9.3): public, no credentials; the 202 acknowledgement is identical for every account. */
export function requestPasswordReset(payload: PasswordResetRequestPayload, signal?: AbortSignal, isCurrent?: () => boolean) {
    return request('/auth/password-reset-requests', {
        method: 'POST', auth: 'public', expectedStatus: 202, decode: decodePasswordResetRequested,
        body: passwordResetRequestInput(payload), signal, isCurrent,
    });
}
/**
 * CT02-REST: the capability travels only in this POST body. Success returns no credential and creates no
 * session (ADR006 §9.3); the server revokes every session of the account (ADR014 §6).
 */
export function confirmPasswordReset(payload: PasswordResetConfirmPayload, signal?: AbortSignal, isCurrent?: () => boolean) {
    return request('/auth/password-resets', {
        method: 'POST', auth: 'public', expectedStatus: 200, decode: decodePasswordResetCompleted,
        body: passwordResetConfirmInput(payload), signal, isCurrent,
    });
}
