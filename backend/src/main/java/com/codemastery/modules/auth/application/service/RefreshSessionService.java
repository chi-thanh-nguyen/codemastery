package com.codemastery.modules.auth.application.service;

import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import com.codemastery.modules.auth.domain.repository.UserRepository.RefreshSession;
import com.codemastery.modules.auth.domain.repository.UserRepository.RefreshToken;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Auth-owned refresh-session lifecycle (AUTH-SESSION-2026-11-16-r1, ADR014 §§4–6).
 *
 * <p>A refresh token is 32 bytes from {@link SecureRandom}, carried only in the HttpOnly
 * cookie as unpadded base64url; PostgreSQL stores its SHA-256 digest. Every token write
 * of a session happens under that session's row lock, so rotation is atomic and a
 * family can never fork (also enforced by a partial unique index). A consumed token
 * presented again after the concurrency grace window is a replay and revokes the whole
 * family; inside the window it is only rejected, because the browser may already hold
 * the successor. No token, digest or account identifier is logged.
 */
@Service
public class RefreshSessionService {

    /** Absolute session lifetime: no rotation extends a family beyond this. */
    public static final Duration SESSION_LIFETIME = Duration.ofDays(7);
    /** Idle lifetime of one refresh token; each rotation restarts it, capped by the session. */
    public static final Duration TOKEN_IDLE_LIFETIME = Duration.ofHours(24);
    /** A consumed token seen within this window is a benign concurrent refresh, not a replay. */
    public static final Duration REUSE_GRACE = Duration.ofSeconds(30);
    /** Active sessions kept per account; older ones are revoked on sign-in. */
    public static final int MAX_ACTIVE_SESSIONS = 10;
    /** Expired sessions purged (with their tokens) after each successful sign-in. */
    static final int PURGE_BATCH = 100;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Counter replays;
    private final Counter rejections;
    private final Counter maintenanceFailures;

    @Autowired
    public RefreshSessionService(UserRepository users, PlatformTransactionManager transactionManager,
                                 MeterRegistry meters) {
        this(users, transactionManager, Clock.systemUTC(), meters);
    }

    /** Explicit clock seam for deterministic tests. */
    public RefreshSessionService(UserRepository users, PlatformTransactionManager transactionManager,
                                 Clock clock, MeterRegistry meters) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.replays = meters.counter("auth.refresh.replay_detected");
        this.rejections = meters.counter("auth.refresh.rejected");
        this.maintenanceFailures = meters.counter("auth.refresh.maintenance.failures");
    }

    /**
     * Issues a session for a credential verified by {@link AuthService#authenticate}. The BCrypt
     * check runs before this call, without locks; issuance then revalidates that verification under
     * the account lock, so a password reset or lock committed in between rejects the sign-in with
     * the generic 401 and nothing is issued (ADR014 §15, F01). A refresh cookie the browser still
     * presented (login replacement) has its session revoked.
     *
     * <p><b>Lock order</b> (global, every session writer): (1) at most one User row per transaction
     * except CT12, which locks Administrator rows in id order and then its target; (2) session
     * rows; (3) token rows. Concurrent sign-ins of one account serialize on its User row even when
     * it has no session yet, so the 10-session cap is decided on committed state (F02). A replaced
     * session of <em>another</em> account is revoked first, in its own single-row transaction, so a
     * sign-in never holds two User rows and opposite account switches cannot deadlock (F03).
     * Password reset (User {@code FOR UPDATE}, then sessions) and CT12 follow this order; rotation,
     * logout and purge lock only session rows (then tokens) and never wait for a User lock.
     */
    public IssuedSession start(User verified, Optional<String> presentedToken) {
        Instant now = now();
        UUID sessionId = UUID.randomUUID();
        byte[] raw = newToken();
        Instant sessionExpiry = now.plus(SESSION_LIFETIME);
        Instant tokenExpiry = min(now.plus(TOKEN_IDLE_LIFETIME), sessionExpiry);
        Optional<RefreshSession> replaced = presentedSession(presentedToken);
        Optional<RefreshSession> replacedOwn = replaced.filter(session -> session.userId().equals(verified.id()));
        replaced.filter(session -> !session.userId().equals(verified.id())).ifPresent(other ->
                transactions.executeWithoutResult(status -> users.revokeRefreshSession(other.id(), now)));
        User issued = transactions.execute(status -> {
            User current = users.lockUserForSessionIssue(verified.id()).orElse(null);
            // Statements after the lock see every committed reset or status change. A BCrypt hash
            // is salted, so any reset (even to the same password) changes it.
            if (current == null || current.accountStatus() != AccountStatus.ACTIVE
                    || !current.passwordHash().equals(verified.passwordHash())) {
                return null;
            }
            users.insertRefreshSession(sessionId, current.id(), now, sessionExpiry);
            users.insertRefreshToken(UUID.randomUUID(), sessionId, sha256(raw), now, tokenExpiry);
            replacedOwn.ifPresent(previous -> users.revokeRefreshSession(previous.id(), now));
            users.revokeRefreshSessionsBeyond(current.id(), MAX_ACTIVE_SESSIONS, now);
            return current;
        });
        if (issued == null) {
            throw rejected();
        }
        purgeExpired(now);
        return new IssuedSession(issued, sessionId, encode(raw), now, tokenExpiry);
    }

    /** Rotates a presented refresh token, or fails closed with the generic 401 exception. */
    public IssuedSession rotate(String presentedToken) {
        byte[] raw = decode(presentedToken).orElseThrow(this::rejected);
        byte[] digest = sha256(raw);
        // An unknown digest costs one indexed read and takes no lock.
        RefreshToken observed = users.findRefreshTokenByDigest(digest).orElseThrow(this::rejected);
        byte[] next = newToken();
        Outcome outcome = transactions.execute(status -> {
            // Lock-order steps 2–3 only: the session row, then its tokens. The account is read
            // without a lock; a reset or lock committing later revokes this session (step 2 wait).
            RefreshSession session = users.findRefreshSessionForUpdate(observed.sessionId()).orElse(null);
            if (session == null) {
                return Outcome.failure(false);
            }
            // Re-read after the lock: every token write of this session holds it.
            RefreshToken token = users.findRefreshTokenByDigest(digest).orElse(null);
            Instant now = now();
            if (token == null || !token.sessionId().equals(session.id()) || !MessageDigest.isEqual(digest, token.digest())
                    || session.revokedAt() != null || !now.isBefore(session.expiresAt())
                    || token.issuedAt().isAfter(now)) {
                return Outcome.failure(false);
            }
            if (token.consumedAt() != null) {
                if (now.isBefore(token.consumedAt().plus(REUSE_GRACE))) {
                    return Outcome.failure(false);
                }
                users.revokeRefreshSession(session.id(), now);
                return Outcome.failure(true);
            }
            if (!now.isBefore(token.expiresAt())) {
                return Outcome.failure(false);
            }
            User account = users.findById(session.userId()).orElse(null);
            if (account == null || account.accountStatus() != AccountStatus.ACTIVE) {
                users.revokeRefreshSession(session.id(), now);
                return Outcome.failure(false);
            }
            if (!users.consumeRefreshToken(token.id(), now)) {
                return Outcome.failure(false);
            }
            Instant tokenExpiry = min(now.plus(TOKEN_IDLE_LIFETIME), session.expiresAt());
            users.insertRefreshToken(UUID.randomUUID(), session.id(), sha256(next), now, tokenExpiry);
            return Outcome.success(new IssuedSession(account, session.id(), encode(next), now, tokenExpiry));
        });
        if (outcome.session() == null) {
            // Revocations above are committed before the failure is reported.
            if (outcome.replay()) {
                replays.increment();
            }
            throw rejected();
        }
        return outcome.session();
    }

    /** Ends the presented token's session. Unknown or malformed tokens are ignored (idempotent logout). */
    public void end(Optional<String> presentedToken) {
        // A single-row session lock (lock-order step 2 only); logout never waits for a User lock.
        presentedSession(presentedToken).ifPresent(session -> transactions.executeWithoutResult(
                status -> users.revokeRefreshSession(session.id(), now())));
    }

    private Optional<RefreshSession> presentedSession(Optional<String> presentedToken) {
        return presentedToken.flatMap(RefreshSessionService::decode)
                .flatMap(raw -> users.findRefreshTokenByDigest(sha256(raw)))
                .flatMap(token -> users.findRefreshSession(token.sessionId()));
    }

    /** Best-effort hygiene after sign-in; failures never fail the sign-in itself. */
    private void purgeExpired(Instant now) {
        try {
            transactions.executeWithoutResult(status -> users.deleteExpiredRefreshSessions(now, PURGE_BATCH));
        } catch (RuntimeException failure) {
            maintenanceFailures.increment();
        }
    }

    private BadCredentialsException rejected() {
        rejections.increment();
        return new BadCredentialsException("Authentication failed.");
    }

    private Instant now() {
        // PostgreSQL timestamptz keeps microseconds; compare exactly what is stored.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static byte[] newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** Accepts only the canonical 43-character unpadded base64url form of 32 bytes. */
    static Optional<byte[]> decode(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            return raw.length == 32 && encode(raw).equals(token) ? Optional.of(raw) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest algorithm is unavailable.");
        }
    }

    /** A started or rotated session. {@code refreshToken} goes only into the HttpOnly cookie. */
    public record IssuedSession(User account, UUID sessionId, String refreshToken, Instant issuedAt,
                                Instant refreshExpiresAt) {
        @Override
        public String toString() {
            return "IssuedSession[account=<redacted>, sessionId=<redacted>, refreshToken=<redacted>, issuedAt="
                    + issuedAt + ", refreshExpiresAt=" + refreshExpiresAt + "]";
        }
    }

    private record Outcome(IssuedSession session, boolean replay) {
        static Outcome success(IssuedSession session) {
            return new Outcome(session, false);
        }

        static Outcome failure(boolean replay) {
            return new Outcome(null, replay);
        }
    }
}
