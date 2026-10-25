import { createContext, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { bindAuthSession } from '../../shared/api/httpClient';
import { cancelled } from '../../shared/types/apiTypes';
import { login as apiLogin, registerLearner } from '../../features/auth/api/authApi';
import { loginExpiresAt } from '../../features/auth/types/authTypes';
import type { LoginRequest, PublicUser, RegisterRequest } from '../../features/auth/types/authTypes';

interface Session { user: PublicUser; accessToken: string; expiresAt: number; generation: number; controller: AbortController; signal: AbortSignal }
export interface AuthContextValue {
    readonly user: PublicUser | null;
    readonly isAuthenticated: boolean;
    readonly generation: number;
    login(payload: LoginRequest, signal?: AbortSignal): Promise<void>;
    register(payload: RegisterRequest, signal?: AbortSignal): Promise<PublicUser>;
    logout(): void;
    isCurrent(generation: number): boolean;
    rememberProfile(leavingSession?: boolean): void;
    consumeReturn(): '/profile' | '/courses';
}
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
export function AuthProvider({ children }: { children: ReactNode }) {
    const [snapshot, setSnapshot] = useState<Session | null>(null);
    const session = useRef<Session | null>(null), generation = useRef(0), attempt = useRef(0);
    const explicitLogout = useRef(false);
    const alive = useRef(true), returnPath = useRef<'/profile' | null>(null);
    const pending = useRef(new Set<AbortController>()), lastLogin = useRef<AbortController | null>(null);
    const eligible = useCallback(() => session.current && Date.now() < session.current.expiresAt ? session.current : null, []);
    const clear = useCallback((explicit = false) => {
        generation.current++; attempt.current++;
        for (const controller of pending.current) controller.abort();
        pending.current.clear(); lastLogin.current = null;
        session.current?.controller.abort(); session.current = null;
        explicitLogout.current = explicit;
        if (explicit) returnPath.current = null;
        if (alive.current) setSnapshot(null);
    }, []);
    const expire = useCallback(() => {
        if (session.current && !eligible()) clear();
    }, [clear, eligible]);
    const isCurrent = useCallback((value: number) => !!eligible() && generation.current === value, [eligible]);
    useLayoutEffect(() => {
        alive.current = true;
        const unbind = bindAuthSession({
            capture: () => { expire(); return eligible(); }, isCurrent,
            invalidate: (value) => { if (isCurrent(value)) clear(); },
        });
        return () => {
            unbind(); alive.current = false;
            clear(true);
        };
    }, [clear, eligible, expire, isCurrent]);
    useEffect(() => {
        window.addEventListener('focus', expire);
        window.addEventListener('pageshow', expire);
        document.addEventListener('visibilitychange', expire);
        const timer = snapshot ? window.setTimeout(expire, Math.max(0, snapshot.expiresAt - Date.now())) : undefined;
        return () => {
            window.removeEventListener('focus', expire);
            window.removeEventListener('pageshow', expire);
            document.removeEventListener('visibilitychange', expire);
            window.clearTimeout(timer);
        };
    }, [snapshot, expire]);
    const operation = useCallback((signal?: AbortSignal) => {
        const controller = new AbortController();
        const abort = () => controller.abort();
        if (signal?.aborted) controller.abort();
        else signal?.addEventListener('abort', abort, { once: true });
        pending.current.add(controller);
        return { controller, finish: () => { pending.current.delete(controller); signal?.removeEventListener('abort', abort); } };
    }, []);
    const login = useCallback(async (payload: LoginRequest, signal?: AbortSignal) => {
        expire();
        const id = ++attempt.current, captured = generation.current;
        lastLogin.current?.abort();
        const { controller, finish } = operation(signal);
        lastLogin.current = controller;
        const current = () => alive.current && !controller.signal.aborted && id === attempt.current && captured === generation.current;
        try {
            const response = await apiLogin(payload, controller.signal, current);
            expire();
            if (!current()) throw cancelled();
            const expiresAt = loginExpiresAt(response, Date.now());
            // Installation is atomic, including replacement with the same user UUID.
            generation.current++;
            for (const other of pending.current) if (other !== controller) other.abort();
            session.current?.controller.abort();
            const lifetime = new AbortController();
            explicitLogout.current = false;
            const next = { user: response.user, accessToken: response.accessToken, expiresAt, generation: generation.current, controller: lifetime, signal: lifetime.signal };
            session.current = next; setSnapshot(next);
        } finally { finish(); if (lastLogin.current === controller) lastLogin.current = null; }
    }, [eligible, expire, operation]);
    const register = useCallback(async (payload: RegisterRequest, signal?: AbortSignal) => {
        expire();
        const captured = generation.current, { controller, finish } = operation(signal);
        const current = () => alive.current && !controller.signal.aborted && captured === generation.current;
        try {
            const user = await registerLearner(payload, controller.signal, current);
            expire(); if (!current()) throw cancelled();
            return user;
        } finally { finish(); }
    }, [expire, operation]);
    const value = useMemo<AuthContextValue>(() => ({
        get user() { return eligible()?.user ?? null; },
        get isAuthenticated() { return !!eligible(); },
        get generation() { return generation.current; },
        login, register, logout: () => clear(true), isCurrent,
        rememberProfile: (leavingSession = false) => { if (!leavingSession || !explicitLogout.current) returnPath.current = '/profile'; },
        consumeReturn: () => { const path = returnPath.current; returnPath.current = null; return path ?? '/courses'; },
    }), [snapshot, eligible, login, register, clear, isCurrent]);
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
