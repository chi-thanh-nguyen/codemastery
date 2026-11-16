import { createContext, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { bindAuthSession } from '../../shared/api/httpClient';
import { ApiError, cancelled } from '../../shared/types/apiTypes';
import { endSession, login as apiLogin, refreshSession, registerLearner } from '../../features/auth/api/authApi';
import { accessSessionId, loginExpiresAt } from '../../features/auth/types/authTypes';
import type { LoginRequest, LoginResponse, PublicUser, RegisterRequest } from '../../features/auth/types/authTypes';

interface Session { user: PublicUser; accessToken: string; expiresAt: number; generation: number; controller: AbortController; signal: AbortSignal; sid: string | null }
export interface AuthContextValue {
    readonly user: PublicUser | null;
    readonly isAuthenticated: boolean;
    readonly generation: number;
    /**
     * True while the provider restores a session from the HttpOnly refresh cookie (ADR014 §7).
     * Route guards wait instead of redirecting; it never authorizes anything.
     */
    readonly restoring: boolean;
    /** Receipt identifies the installed session; consumers recheck before publishing UI. */
    login(payload: LoginRequest, signal?: AbortSignal): Promise<number>;
    register(payload: RegisterRequest, signal?: AbortSignal): Promise<PublicUser>;
    logout(): void;
    isCurrent(generation: number): boolean;
    rememberProfile(leavingSession?: boolean): void;
    /**
     * Post-sign-in destination: the one-use `/profile` return when one was captured (CT16-PROFILE P1),
     * otherwise the role's start page — `/home` for a Learner (Leader instruction, 7 November 2026),
     * `/courses` for every other role.
     */
    consumeReturn(): '/profile' | '/home' | '/courses';
}
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Renew the access token this long before its expiry; transient failures retry at RETRY_MS. */
export const RENEW_LEAD_MS = 60_000;
/** Floor between renewals, so a browser clock running ahead of the server cannot loop refreshes. */
const MIN_RENEW_DELAY_MS = 30_000;
const RETRY_MS = 10_000;
const REFRESH_TIMEOUT_MS = 15_000;
const LOCK = 'codemastery-auth-session';
const CHANNEL = 'codemastery-auth';
/** Non-secret marker: a sign-out whose server revocation has not been confirmed yet. */
const PENDING_SIGN_OUT = 'codemastery.auth.pendingSignOut';

/**
 * Cookie operations (sign-in, refresh, sign-out) of every document of this origin run one at a
 * time under a Web Lock, so a tab always sends the newest rotated cookie and a late response can
 * never overwrite a newer one. Without Web Locks only this document is serialized.
 */
function withSessionLock<T>(work: () => Promise<T>): Promise<T> {
    const locks = (globalThis.navigator as Navigator | undefined)?.locks;
    return locks?.request ? locks.request(LOCK, work) as Promise<T> : work();
}
let sharedRefresh: Promise<LoginResponse> | null = null;
/** Single flight per document: React StrictMode and concurrent callers share one rotation. */
function coordinatedRefresh() {
    sharedRefresh ??= withSessionLock(() => refreshSession(AbortSignal.timeout(REFRESH_TIMEOUT_MS)))
        .finally(() => { sharedRefresh = null; });
    return sharedRefresh;
}
function pendingSignOut(value?: boolean) {
    try {
        if (value === undefined) return globalThis.localStorage?.getItem(PENDING_SIGN_OUT) === '1';
        if (value) globalThis.localStorage?.setItem(PENDING_SIGN_OUT, '1');
        else globalThis.localStorage?.removeItem(PENDING_SIGN_OUT);
    } catch { /* Storage can be unavailable; the server revocation is still attempted. */ }
    return false;
}
/** Revokes the server session; until confirmed, a later load retries sign-out instead of restoring. */
function serverSignOut() {
    pendingSignOut(true);
    return withSessionLock(() => endSession()).then(() => { pendingSignOut(false); }, () => undefined);
}
/** The server refused the refresh credential (or answered unusably): the session is over. */
const refused = (error: unknown) => error instanceof ApiError &&
    (error.kind === 'protocol' || error.kind === 'http' && (error.status === 401 || error.status === 403));

/**
 * `refreshSessions` enables the ADR014 hybrid lifecycle: restore on load from the HttpOnly refresh
 * cookie, renew before access-token expiry, server sign-out and cross-tab coordination. Without it
 * the provider keeps the memory-only behavior (used by isolated component tests). `restoreOnLoad`
 * false skips only the load-time restore (a document opened at the network-free design preview).
 */
export function AuthProvider({ children, refreshSessions = false, restoreOnLoad = true }: { children: ReactNode; refreshSessions?: boolean; restoreOnLoad?: boolean }) {
    const [snapshot, setSnapshot] = useState<Session | null>(null);
    const [restoring, setRestoring] = useState(refreshSessions && restoreOnLoad);
    const session = useRef<Session | null>(null), generation = useRef(0), attempt = useRef(0);
    const explicitLogout = useRef(false);
    const alive = useRef(true), returnPath = useRef<'/profile' | null>(null);
    const pending = useRef(new Set<AbortController>()), lastLogin = useRef<AbortController | null>(null);
    const restoreRun = useRef(0), retryTimer = useRef<number | undefined>(undefined), lastWake = useRef(0);
    const channel = useRef<BroadcastChannel | null>(null);
    const eligible = useCallback(() => session.current && Date.now() < session.current.expiresAt ? session.current : null, []);
    const broadcast = useCallback((type: 'signed-in' | 'signed-out') => { try { channel.current?.postMessage({ type }); } catch { /* closed */ } }, []);
    const clear = useCallback((explicit = false) => {
        generation.current++; attempt.current++;
        for (const controller of pending.current) controller.abort();
        pending.current.clear(); lastLogin.current = null;
        session.current?.controller.abort(); session.current = null;
        window.clearTimeout(retryTimer.current);
        explicitLogout.current = explicit;
        if (explicit) returnPath.current = null;
        if (alive.current) setSnapshot(null);
    }, []);
    /**
     * Installs a response. The same user and server session (`sid`) is an in-place rotation that keeps
     * the generation, so mounted pages and their requests stay current; anything else is a replacement.
     */
    const install = useCallback((response: LoginResponse, replace: boolean, keep?: AbortController) => {
        const expiresAt = loginExpiresAt(response, Date.now()), sid = accessSessionId(response.accessToken);
        const current = session.current;
        if (!replace && current && current.user.id === response.user.id && current.sid === sid) {
            const next = { ...current, user: response.user, accessToken: response.accessToken, expiresAt };
            session.current = next; if (alive.current) setSnapshot(next);
            return next.generation;
        }
        // Installation is atomic, including replacement with the same user UUID.
        generation.current++;
        for (const other of pending.current) if (other !== keep) other.abort();
        session.current?.controller.abort();
        window.clearTimeout(retryTimer.current);
        const lifetime = new AbortController();
        explicitLogout.current = false;
        const next = { user: response.user, accessToken: response.accessToken, expiresAt, generation: generation.current, controller: lifetime, signal: lifetime.signal, sid };
        session.current = next; if (alive.current) setSnapshot(next);
        return next.generation;
    }, []);
    /** Restores (or adopts another tab's) session from the cookie; never redirects while running. */
    const restore = useCallback(async () => {
        const run = ++restoreRun.current, captured = generation.current;
        if (alive.current) setRestoring(true);
        try {
            if (pendingSignOut()) { await serverSignOut(); return; }
            const response = await coordinatedRefresh();
            if (alive.current && captured === generation.current) install(response, false);
        } catch (error) {
            if (alive.current && captured === generation.current && session.current && refused(error)) clear();
        } finally {
            if (alive.current && run === restoreRun.current) setRestoring(false);
        }
    }, [clear, install]);
    /** Proactive rotation for the current generation; a refusal ends the session, other failures retry. */
    const renew = useCallback(async (value: number) => {
        window.clearTimeout(retryTimer.current);
        try {
            const response = await coordinatedRefresh();
            if (alive.current && value === generation.current && session.current) install(response, false);
        } catch (error) {
            if (!alive.current || value !== generation.current || !session.current) return;
            if (refused(error)) { clear(); return; }
            if (Date.now() + RETRY_MS < session.current.expiresAt) retryTimer.current = window.setTimeout(() => { void renew(value); }, RETRY_MS);
        }
    }, [clear, install]);
    const expire = useCallback(() => {
        if (!session.current || eligible()) return;
        clear();
        // A sleeping device can wake after expiry: the refresh cookie may still restore the session.
        // Rate-limited, so a badly skewed browser clock cannot turn expiry into a refresh loop.
        if (refreshSessions && Date.now() - lastWake.current >= MIN_RENEW_DELAY_MS) { lastWake.current = Date.now(); void restore(); }
    }, [clear, eligible, refreshSessions, restore]);
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
        if (!refreshSessions) return;
        // StrictMode re-runs share the in-flight rotation through coordinatedRefresh.
        if (restoreOnLoad) void restore();
        if (typeof BroadcastChannel === 'undefined') return;
        const tabs = new BroadcastChannel(CHANNEL);
        tabs.onmessage = (event: MessageEvent<{ type?: unknown }>) => {
            // Another tab signed out: clear this tab without another server call.
            if (event.data?.type === 'signed-out') { if (session.current) clear(); }
            // Another tab signed in (its sign-in revoked any previous cookie session): adopt it.
            else if (event.data?.type === 'signed-in') void restore();
        };
        channel.current = tabs;
        return () => { tabs.close(); if (channel.current === tabs) channel.current = null; };
    }, [clear, refreshSessions, restoreOnLoad, restore]);
    useEffect(() => {
        window.addEventListener('focus', expire);
        window.addEventListener('pageshow', expire);
        document.addEventListener('visibilitychange', expire);
        const timer = snapshot ? window.setTimeout(expire, Math.max(0, snapshot.expiresAt - Date.now())) : undefined;
        const renewal = snapshot && refreshSessions
            ? window.setTimeout(() => { void renew(snapshot.generation); }, Math.max(MIN_RENEW_DELAY_MS, snapshot.expiresAt - RENEW_LEAD_MS - Date.now()))
            : undefined;
        return () => {
            window.removeEventListener('focus', expire);
            window.removeEventListener('pageshow', expire);
            document.removeEventListener('visibilitychange', expire);
            window.clearTimeout(timer); window.clearTimeout(renewal);
        };
    }, [snapshot, expire, refreshSessions, renew]);
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
            const response = await (refreshSessions
                ? withSessionLock(() => apiLogin(payload, controller.signal, current))
                : apiLogin(payload, controller.signal, current));
            expire();
            if (!current()) throw cancelled();
            const installed = install(response, true, controller);
            if (refreshSessions) {
                pendingSignOut(false);
                restoreRun.current++; if (alive.current) setRestoring(false);
                broadcast('signed-in');
            }
            return installed;
        } finally { finish(); if (lastLogin.current === controller) lastLogin.current = null; }
    }, [broadcast, expire, install, operation, refreshSessions]);
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
    const logout = useCallback(() => {
        // The browser state clears at once (offline too); revocation follows and is retried on a later load.
        clear(true);
        if (!refreshSessions) return;
        restoreRun.current++; if (alive.current) setRestoring(false);
        broadcast('signed-out');
        void serverSignOut();
    }, [broadcast, clear, refreshSessions]);
    const value = useMemo<AuthContextValue>(() => ({
        get user() { return eligible()?.user ?? null; },
        get isAuthenticated() { return !!eligible(); },
        get generation() { return generation.current; },
        restoring,
        login, register, logout, isCurrent,
        rememberProfile: (leavingSession = false) => { if (!leavingSession || !explicitLogout.current) returnPath.current = '/profile'; },
        consumeReturn: () => { const path = returnPath.current; returnPath.current = null; return path ?? (eligible()?.user.role === 'Learner' ? '/home' : '/courses'); },
    }), [snapshot, restoring, eligible, login, register, logout, isCurrent]);
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
