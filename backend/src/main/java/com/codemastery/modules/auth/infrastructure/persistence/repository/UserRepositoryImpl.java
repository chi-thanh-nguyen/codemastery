package com.codemastery.modules.auth.infrastructure.persistence.repository;

import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import com.codemastery.modules.auth.domain.repository.UserRepository.PasswordResetToken;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import com.codemastery.modules.auth.infrastructure.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;
    private JdbcTemplate jdbc;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository, UserMapper userMapper) {
        this.userJpaRepository = userJpaRepository;
        this.userMapper = userMapper;
    }

    @Autowired
    void setJdbcTemplate(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id).map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(userMapper::toDomain);
    }

    @Override
    public User save(User user) {
        return userMapper.toDomain(userJpaRepository.save(userMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findByEmailForUpdate(String email) {
        return users("SELECT id, email, password_hash, display_name, role, account_status "
                + "FROM public.users WHERE email = ? FOR UPDATE", email);
    }

    @Override
    public Optional<User> findByIdForUpdate(UUID id) {
        return users("SELECT id, email, password_hash, display_name, role, account_status "
                + "FROM public.users WHERE id = ? FOR UPDATE", id);
    }

    @Override
    public Optional<PasswordResetToken> findResetTokenByUserId(UUID userId) {
        return resetTokens("SELECT user_id, token_digest, issued_at, expires_at "
                + "FROM public.password_reset_tokens WHERE user_id = ?", userId);
    }

    @Override
    public Optional<PasswordResetToken> findResetTokenByDigest(byte[] digest) {
        return resetTokens("SELECT user_id, token_digest, issued_at, expires_at "
                + "FROM public.password_reset_tokens WHERE token_digest = ?", digest);
    }

    @Override
    public void replaceResetToken(UUID userId, byte[] digest, Instant issuedAt, Instant expiresAt) {
        jdbc.update("DELETE FROM public.password_reset_tokens WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO public.password_reset_tokens "
                        + "(id, user_id, token_digest, issued_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, digest, Timestamp.from(issuedAt), Timestamp.from(expiresAt));
    }

    @Override
    public void updatePasswordHash(UUID userId, String encodedPasswordHash) {
        int updated = jdbc.update("UPDATE public.users SET password_hash = ? WHERE id = ?",
                encodedPasswordHash, userId);
        if (updated != 1) {
            throw new IllegalStateException("Locked Auth user disappeared during reset.");
        }
    }

    @Override
    public boolean deleteResetToken(UUID userId, byte[] digest) {
        return jdbc.update("DELETE FROM public.password_reset_tokens WHERE user_id = ? AND token_digest = ?",
                userId, digest) == 1;
    }

    @Override
    public Optional<User> findSessionUser(UUID userId, UUID sessionId, Instant now) {
        List<User> rows = jdbc.query("SELECT u.id, u.email, u.password_hash, u.display_name, u.role, "
                        + "u.account_status FROM public.users u JOIN public.refresh_sessions s ON s.user_id = u.id "
                        + "WHERE u.id = ? AND s.id = ? AND s.revoked_at IS NULL AND s.expires_at > ?",
                (result, row) -> user(result), userId, sessionId, Timestamp.from(now));
        return rows.stream().findFirst();
    }

    @Override
    public void insertRefreshSession(UUID sessionId, UUID userId, Instant createdAt, Instant expiresAt) {
        jdbc.update("INSERT INTO public.refresh_sessions (id, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                sessionId, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    @Override
    public void insertRefreshToken(UUID tokenId, UUID sessionId, byte[] digest, Instant issuedAt,
                                   Instant expiresAt) {
        jdbc.update("INSERT INTO public.refresh_tokens (id, session_id, token_digest, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                tokenId, sessionId, digest, Timestamp.from(issuedAt), Timestamp.from(expiresAt));
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByDigest(byte[] digest) {
        return jdbc.query("SELECT id, session_id, token_digest, issued_at, expires_at, consumed_at "
                        + "FROM public.refresh_tokens WHERE token_digest = ?",
                (result, row) -> new RefreshToken(result.getObject("id", UUID.class),
                        result.getObject("session_id", UUID.class), result.getBytes("token_digest"),
                        instant(result.getTimestamp("issued_at")), instant(result.getTimestamp("expires_at")),
                        instant(result.getTimestamp("consumed_at"))),
                digest).stream().findFirst();
    }

    @Override
    public Optional<RefreshSession> findRefreshSessionForUpdate(UUID sessionId) {
        return jdbc.query("SELECT id, user_id, created_at, expires_at, revoked_at "
                        + "FROM public.refresh_sessions WHERE id = ? FOR UPDATE",
                (result, row) -> new RefreshSession(result.getObject("id", UUID.class),
                        result.getObject("user_id", UUID.class), instant(result.getTimestamp("created_at")),
                        instant(result.getTimestamp("expires_at")), instant(result.getTimestamp("revoked_at"))),
                sessionId).stream().findFirst();
    }

    @Override
    public boolean consumeRefreshToken(UUID tokenId, Instant consumedAt) {
        return jdbc.update("UPDATE public.refresh_tokens SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL",
                Timestamp.from(consumedAt), tokenId) == 1;
    }

    @Override
    public boolean revokeRefreshSession(UUID sessionId, Instant revokedAt) {
        return jdbc.update("UPDATE public.refresh_sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), sessionId) == 1;
    }

    @Override
    public int revokeRefreshSessionsOfUser(UUID userId, Instant revokedAt) {
        return jdbc.update("UPDATE public.refresh_sessions SET revoked_at = ? "
                + "WHERE user_id = ? AND revoked_at IS NULL", Timestamp.from(revokedAt), userId);
    }

    @Override
    public void lockActiveRefreshSessions(UUID userId, Instant now) {
        jdbc.queryForList("SELECT id FROM public.refresh_sessions WHERE user_id = ? AND revoked_at IS NULL "
                + "AND expires_at > ? ORDER BY id FOR UPDATE", UUID.class, userId, Timestamp.from(now));
    }

    @Override
    public int revokeRefreshSessionsBeyond(UUID userId, int keep, Instant now) {
        return jdbc.update("UPDATE public.refresh_sessions SET revoked_at = ? WHERE id IN ("
                        + "SELECT id FROM public.refresh_sessions WHERE user_id = ? AND revoked_at IS NULL "
                        + "AND expires_at > ? ORDER BY created_at DESC, id DESC OFFSET ?)",
                Timestamp.from(now), userId, Timestamp.from(now), keep);
    }

    @Override
    public int deleteExpiredRefreshSessions(Instant now, int limit) {
        List<UUID> expired = jdbc.queryForList("SELECT id FROM public.refresh_sessions WHERE expires_at <= ? "
                + "ORDER BY expires_at, id LIMIT ? FOR UPDATE SKIP LOCKED", UUID.class, Timestamp.from(now), limit);
        int deleted = 0;
        for (UUID sessionId : expired) {
            jdbc.update("DELETE FROM public.refresh_tokens WHERE session_id = ?", sessionId);
            deleted += jdbc.update("DELETE FROM public.refresh_sessions WHERE id = ?", sessionId);
        }
        return deleted;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private User user(java.sql.ResultSet result) throws java.sql.SQLException {
        return userMapper.toDomain(new UserEntity(
                result.getObject("id", UUID.class), result.getString("email"),
                result.getString("password_hash"), result.getString("display_name"),
                result.getString("role"), result.getString("account_status")));
    }

    private Optional<User> users(String sql, Object argument) {
        List<User> rows = jdbc.query(sql, (result, row) -> user(result), argument);
        return rows.stream().findFirst();
    }

    private Optional<PasswordResetToken> resetTokens(String sql, Object argument) {
        return jdbc.query(sql, (result, row) -> new PasswordResetToken(
                        result.getObject("user_id", UUID.class), result.getBytes("token_digest"),
                        result.getTimestamp("issued_at").toInstant(), result.getTimestamp("expires_at").toInstant()),
                argument).stream().findFirst();
    }
}
