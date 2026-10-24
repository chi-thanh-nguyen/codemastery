export type UserRole = 'Learner' | 'Instructor' | 'Administrator';

export interface PublicUser {
    id: string; // UUID
    displayName: string;
    role: UserRole;
}

export interface RegisterRequest {
    email: string;
    password: string;
    displayName: string;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    tokenType: 'Bearer';
    expiresIn: number;
    user: PublicUser;
}

export interface AuthState {
    user: PublicUser | null;
    token: string | null;
    isAuthenticated: boolean;
    isLoading: boolean;
}