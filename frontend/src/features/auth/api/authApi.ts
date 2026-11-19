import { protocolError } from '../../../shared/types/apiTypes';
import { request } from '../../../shared/api/httpClient';
import { decodeLoginResponse, decodePublicUser, loginInput, registrationInput } from '../types/authTypes';
import type { LoginRequest, RegisterRequest } from '../types/authTypes';
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
export function refreshSession(signal?: AbortSignal) {
    return request('/auth/refresh', { method: 'POST', auth: 'public', expectedStatus: 200, decode: decodeLoginResponse, signal });
}
export function endSession(signal?: AbortSignal) {
    return request('/auth/logout', { method: 'POST', auth: 'public', expectedStatus: 204, decode: () => undefined, signal });
}
import type {
    PasswordResetRequestPayload,
    PasswordResetConfirmPayload,
    PasswordResetResponse,
} from '../types/authTypes';

export function requestPasswordReset(
    payload: PasswordResetRequestPayload,
    signal?: AbortSignal,
    isCurrent?: () => boolean
) {
    return request<PasswordResetResponse>('/auth/password-reset-requests', {
        method: 'POST',
        auth: 'public',
        expectedStatus: 202,
        decode: (value) => {
            const obj = typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
            return {
                message:
                    typeof obj.message === 'string'
                        ? obj.message
                        : 'If an account matches, a password reset link will be sent.',
            };
        },
        body: {
            email: payload.email.trim().toLowerCase(),
        },
        signal,
        isCurrent,
    });
}

export function confirmPasswordReset(
    payload: PasswordResetConfirmPayload,
    signal?: AbortSignal,
    isCurrent?: () => boolean
) {
    return request<PasswordResetResponse>('/auth/password-resets', {
        method: 'POST',
        auth: 'public',
        expectedStatus: 200,
        decode: (value) => {
            const obj = typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
            return {
                message:
                    typeof obj.message === 'string'
                        ? obj.message
                        : 'Password reset. Sign in with your new password.',
            };
        },
        body: {
            token: payload.token,
            password: payload.password, // Exact untrimmed password
        },
        signal,
        isCurrent,
    });
}