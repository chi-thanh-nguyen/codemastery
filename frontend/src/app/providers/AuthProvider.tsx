import React, { createContext, useState, useEffect, useCallback, useMemo } from 'react';
import { setUnauthorizedHandler } from '../../shared/api/httpClient';
import { login as apiLogin, registerLearner as apiRegister } from '../../features/auth/api/authApi';
import type {
    PublicUser,
    LoginRequest,
    RegisterRequest,
} from '../../features/auth/types/authTypes';

export interface AuthContextValue {
    user: PublicUser | null;
    token: string | null;
    isAuthenticated: boolean;
    login: (payload: LoginRequest) => Promise<void>;
    register: (payload: RegisterRequest) => Promise<PublicUser>;
    logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export interface AuthProviderProps {
    children: React.ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
    // Memory-only storage: Never persisted to localStorage per CT01 / ADR002
    const [user, setUser] = useState<PublicUser | null>(null);
    const [token, setToken] = useState<string | null>(null);

    const logout = useCallback(() => {
        setUser(null);
        setToken(null);
    }, []);

    // Hook into httpClient 401 callback to clear memory-only session
    useEffect(() => {
        setUnauthorizedHandler(logout);
        return () => {
            setUnauthorizedHandler(null);
        };
    }, [logout]);

    const login = useCallback(async (payload: LoginRequest) => {
        const response = await apiLogin(payload);
        setUser(response.user);
        setToken(response.accessToken);
    }, []);

    const register = useCallback(async (payload: RegisterRequest) => {
        return apiRegister(payload);
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({
            user,
            token,
            isAuthenticated: Boolean(user && token),
            login,
            register,
            logout,
        }),
        [user, token, login, register, logout]
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};