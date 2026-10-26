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
