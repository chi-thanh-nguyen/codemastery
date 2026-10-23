import { request } from '../../../shared/api/httpClient';
import type {
    LoginRequest,
    LoginResponse,
    RegisterRequest,
    PublicUser,
} from '../types/authTypes';

/**
 * Public operation: Register a new Learner.
 * Auto-login is NOT performed per API contract.
 */
export async function registerLearner(payload: RegisterRequest): Promise<PublicUser> {
    const formattedPayload: RegisterRequest = {
        email: payload.email.trim().toLowerCase(),
        password: payload.password, // Never trim or normalize password
        displayName: payload.displayName.trim(),
    };

    return request<PublicUser>('/api/auth/register', {
        method: 'POST',
        body: formattedPayload,
    });
}

/**
 * Public operation: Authenticate with email and password.
 * Returns in-memory bearer token and user metadata.
 */
export async function login(payload: LoginRequest): Promise<LoginResponse> {
    const formattedPayload: LoginRequest = {
        email: payload.email.trim().toLowerCase(),
        password: payload.password, // Exact password
    };

    return request<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: formattedPayload,
    });
}