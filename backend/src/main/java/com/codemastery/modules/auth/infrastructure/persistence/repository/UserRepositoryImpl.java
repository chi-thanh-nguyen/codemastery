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

    private Optional<User> users(String sql, Object argument) {
        List<User> rows = jdbc.query(sql, (result, row) -> userMapper.toDomain(new UserEntity(
                result.getObject("id", UUID.class), result.getString("email"),
                result.getString("password_hash"), result.getString("display_name"),
                result.getString("role"), result.getString("account_status"))), argument);
        return rows.stream().findFirst();
    }

    private Optional<PasswordResetToken> resetTokens(String sql, Object argument) {
        return jdbc.query(sql, (result, row) -> new PasswordResetToken(
                        result.getObject("user_id", UUID.class), result.getBytes("token_digest"),
                        result.getTimestamp("issued_at").toInstant(), result.getTimestamp("expires_at").toInstant()),
                argument).stream().findFirst();
    }
}
