package com.codemastery.modules.auth.application.service;

import com.codemastery.common.config.AppConfig.PasswordResetActivation;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.validation.StrongPassword;
import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import com.codemastery.modules.auth.domain.repository.UserRepository.PasswordResetToken;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Auth-owned CT02 request/reset use case. The queue acknowledges best-effort work only.
 * When recovery delivery is not configured (inactive), requests keep the identical 202
 * acknowledgement but issue no capability and send nothing (ADR006 B2-O16).
 */
@Service
@Validated
public class PasswordResetService {

    private static final String REQUEST_ACKNOWLEDGEMENT =
            "If an account matches, a password reset link will be sent.";
    private static final String COMPLETION_MESSAGE = "Password reset. Sign in with your new password.";
    private static final String INVALID_RESET_LINK = "Reset link is invalid or expired.";
    private static final Duration REISSUE_INTERVAL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder PASSWORDS =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);

    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final PasswordResetActivation activation;
    private final ResetEmailDelivery delivery;
    private final Validator validator;
    private final Clock clock;
    private final Executor executor;
    private final ThreadPoolExecutor ownedExecutor;
    private final Counter rejectedRequests;
    private final Counter processingFailures;
    private final Counter inactiveRequests;

    @Autowired
    public PasswordResetService(UserRepository users,
                               PlatformTransactionManager transactionManager,
                               PasswordResetActivation activation,
                               ResetEmailDelivery delivery,
                               Validator validator,
                               MeterRegistry meters) {
        this(users, transactionManager, activation, delivery, validator, Clock.systemUTC(), newWorker(), meters);
    }

    /** Explicit seams keep the time and queue deterministic in service tests. */
    public PasswordResetService(UserRepository users,
                                PlatformTransactionManager transactionManager,
                                PasswordResetActivation activation,
                                ResetEmailDelivery delivery,
                                Validator validator,
                                Clock clock,
                                Executor executor,
                                MeterRegistry meters) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.activation = activation;
        this.delivery = delivery;
        this.validator = validator;
        this.clock = clock;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ThreadPoolExecutor pool ? pool : null;
        this.rejectedRequests = meters.counter("auth.password_reset.queue.rejected");
        this.processingFailures = meters.counter("auth.password_reset.processing.failures");
        this.inactiveRequests = meters.counter("auth.password_reset.inactive");
    }

    /** Enqueues only the canonical email; account lookup, token work and mail stay off the HTTP path. */
    public RequestAcknowledgement requestReset(String canonicalEmail) {
        if (!validator.validate(new CanonicalEmail(canonicalEmail)).isEmpty()) {
            throw new BusinessRuleException("Invalid reset request.");
        }
        if (!activation.isActive()) {
            // Same acknowledgement for every account; no lookup, capability or delivery.
            inactiveRequests.increment();
            return new RequestAcknowledgement(REQUEST_ACKNOWLEDGEMENT);
        }
        try {
            executor.execute(() -> processRequest(canonicalEmail));
        } catch (RejectedExecutionException rejected) {
            rejectedRequests.increment();
        }
        return new RequestAcknowledgement(REQUEST_ACKNOWLEDGEMENT);
    }

    /** Changes the password and consumes one valid capability in a single transaction. */
    public ResetCompletion resetPassword(String capability, String password) {
        if (password == null || !new StrongPassword.Validator().isValid(password, null)) {
            throw new BusinessRuleException("Invalid reset password.");
        }
        byte[] rawToken = decodeCanonicalToken(capability);
        byte[] digest = sha256(rawToken);
        // Resolve the digest privately before any work: an unknown, used or replaced capability
        // costs no BCrypt hash. The cost-14 hash is then computed before the User row lock is
        // taken, so the lock is held only for the re-read, checks and two writes.
        PasswordResetToken observed = users.findResetTokenByDigest(digest)
                .orElseThrow(PasswordResetService::invalidResetLink);
        String passwordHash = PASSWORDS.encode(password);

        transactions.executeWithoutResult(transaction -> {
            User account = users.findByIdForUpdate(observed.userId())
                    .orElseThrow(PasswordResetService::invalidResetLink);
            // Every capability write (issue/replace/consume) holds this User row lock, so a
            // fresh read after acquiring it observes the latest committed capability state.
            // No token row lock is needed (and none is granted to the runtime role).
            PasswordResetToken locked = users.findResetTokenByDigest(digest)
                    .orElseThrow(PasswordResetService::invalidResetLink);
            // Exact expiry is evaluated after the lock wait, not when the request arrived.
            Instant now = clock.instant();
            if (!account.id().equals(locked.userId())
                    || !MessageDigest.isEqual(digest, locked.digest())
                    || account.accountStatus() != AccountStatus.ACTIVE
                    || locked.issuedAt().isAfter(now)
                    || !now.isBefore(locked.expiresAt())) {
                throw invalidResetLink();
            }

            users.updatePasswordHash(account.id(), passwordHash);
            if (!users.deleteResetToken(account.id(), digest)) {
                throw invalidResetLink();
            }
            // AUTH-SESSION-2026-11-16-r1 (ADR014 §6): a credential change ends every refresh
            // session, and with it every session-bound access JWT, in the same transaction.
            users.revokeRefreshSessionsOfUser(account.id(), now);
        });
        return new ResetCompletion(COMPLETION_MESSAGE);
    }

    private void processRequest(String canonicalEmail) {
        try {
            var settings = activation.requireSettings();
            ResetEmailMessage message = transactions.execute(status -> {
                User account = users.findByEmailForUpdate(canonicalEmail).orElse(null);
                if (account == null || account.accountStatus() != AccountStatus.ACTIVE) {
                    return null;
                }
                Instant issuedAt = clock.instant();
                var existing = users.findResetTokenByUserId(account.id());
                if (existing.isPresent()
                        && issuedAt.isBefore(existing.get().issuedAt().plus(REISSUE_INTERVAL))) {
                    return null;
                }

                byte[] tokenBytes = new byte[32];
                RANDOM.nextBytes(tokenBytes);
                String capability = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
                byte[] digest = sha256(tokenBytes);
                Instant expiresAt = issuedAt.plus(settings.tokenTtl());
                users.replaceResetToken(account.id(), digest, issuedAt, expiresAt);
                String link = settings.frontendOrigin() + "/reset-password#token="
                        + URLEncoder.encode(capability, StandardCharsets.US_ASCII);
                return new ResetEmailMessage(account.email(), link, expiresAt);
            });
            if (message != null) {
                delivery.send(message);
            }
        } catch (RuntimeException failure) {
            // No account identifier, address, token, exception text or provider detail is logged.
            processingFailures.increment();
        }
    }

    private static byte[] decodeCanonicalToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidResetLink();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length != 32
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token)) {
                throw invalidResetLink();
            }
            return decoded;
        } catch (IllegalArgumentException malformed) {
            throw invalidResetLink();
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest algorithm is unavailable.");
        }
    }

    private static BusinessRuleException invalidResetLink() {
        return BusinessRuleException.withPublicDetail("Invalid reset capability.", INVALID_RESET_LINK);
    }

    private static ThreadPoolExecutor newWorker() {
        ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128), task -> {
                    Thread thread = new Thread(task, "auth-password-reset");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        worker.prestartCoreThread();
        return worker;
    }

    @PreDestroy
    void stopWorker() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    public interface ResetEmailDelivery {
        void send(ResetEmailMessage message);
    }

    public record ResetEmailMessage(String recipient, String resetLink, Instant expiresAt) {
        @Override
        public String toString() {
            return "ResetEmailMessage[recipient=<redacted>, resetLink=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }

    public record RequestAcknowledgement(String message) { }

    public record ResetCompletion(String message) { }

    private record CanonicalEmail(@NotNull @Email @Pattern(regexp = "[!-~]+") String value) { }
}
