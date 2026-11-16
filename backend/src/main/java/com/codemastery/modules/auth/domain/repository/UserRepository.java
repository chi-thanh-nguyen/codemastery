package com.codemastery.modules.auth.domain.repository;

import com.codemastery.modules.auth.domain.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    User save(User user);

    /** Locks the Auth User row; every reset-capability write happens under this lock. */
    Optional<User> findByEmailForUpdate(String email);

    Optional<User> findByIdForUpdate(UUID id);

    Optional<PasswordResetToken> findResetTokenByUserId(UUID userId);

    Optional<PasswordResetToken> findResetTokenByDigest(byte[] digest);

    void replaceResetToken(UUID userId, byte[] digest, Instant issuedAt, Instant expiresAt);

    void updatePasswordHash(UUID userId, String encodedPasswordHash);

    boolean deleteResetToken(UUID userId, byte[] digest);

    // ---- Refresh sessions (AUTH-SESSION-2026-11-16-r1, ADR014 §4). Auth-private. ----

    /**
     * The active account bound to an unrevoked, unexpired refresh session. This is the
     * per-request access-JWT check: a revoked session ends its access JWTs immediately.
     */
    Optional<User> findSessionUser(UUID userId, UUID sessionId, Instant now);

    void insertRefreshSession(UUID sessionId, UUID userId, Instant createdAt, Instant expiresAt);

    void insertRefreshToken(UUID tokenId, UUID sessionId, byte[] digest, Instant issuedAt, Instant expiresAt);

    Optional<RefreshToken> findRefreshTokenByDigest(byte[] digest);

    /** Locks the session row; every token write of a session happens under this lock. */
    Optional<RefreshSession> findRefreshSessionForUpdate(UUID sessionId);

    /** Marks a current token consumed; false when it was already consumed. */
    boolean consumeRefreshToken(UUID tokenId, Instant consumedAt);

    /** Revokes one session; false when it was already revoked or does not exist. */
    boolean revokeRefreshSession(UUID sessionId, Instant revokedAt);

    /** Revokes every unrevoked session of the account (password reset, future CT12 lock). */
    int revokeRefreshSessionsOfUser(UUID userId, Instant revokedAt);

    /**
     * Locks the account's active sessions in {@code id} order. Sign-in calls it before any other
     * session write, so concurrent sign-ins of one account lock in the same order and cannot deadlock.
     */
    void lockActiveRefreshSessions(UUID userId, Instant now);

    /** Revokes the account's active sessions beyond the newest {@code keep}. */
    int revokeRefreshSessionsBeyond(UUID userId, int keep, Instant now);

    /**
     * Deletes at most {@code limit} sessions expired at {@code now}, with their tokens. Rows locked by
     * another transaction are skipped, so concurrent purges never wait for or deadlock with each other.
     */
    int deleteExpiredRefreshSessions(Instant now, int limit);

    /** Auth-private refresh-session value; never exposed by a REST/API boundary. */
    record RefreshSession(UUID id, UUID userId, Instant createdAt, Instant expiresAt, Instant revokedAt) { }

    /** Auth-private refresh-token value; the digest is copied and redacted from strings. */
    record RefreshToken(UUID id, UUID sessionId, byte[] digest, Instant issuedAt, Instant expiresAt,
                        Instant consumedAt) {
        public RefreshToken {
            digest = digest.clone();
        }

        @Override
        public byte[] digest() {
            return digest.clone();
        }

        @Override
        public String toString() {
            return "RefreshToken[id=" + id + ", sessionId=<redacted>, digest=<redacted>, issuedAt=" + issuedAt
                    + ", expiresAt=" + expiresAt + ", consumedAt=" + consumedAt + "]";
        }
    }

    /** Auth-private recovery persistence value; never exposed by a REST/API boundary. */
    record PasswordResetToken(UUID userId, byte[] digest, Instant issuedAt, Instant expiresAt) {
        public PasswordResetToken {
            digest = digest.clone();
        }

        @Override
        public byte[] digest() {
            return digest.clone();
        }

        @Override
        public String toString() {
            return "PasswordResetToken[userId=<redacted>, digest=<redacted>, issuedAt="
                    + issuedAt + ", expiresAt=" + expiresAt + "]";
        }
    }
}
