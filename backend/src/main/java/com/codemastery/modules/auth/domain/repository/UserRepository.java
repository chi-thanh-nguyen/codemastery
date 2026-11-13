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
