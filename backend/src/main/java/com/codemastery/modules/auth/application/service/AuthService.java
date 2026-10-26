package com.codemastery.modules.auth.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.validation.StrongPassword;
import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Auth-internal registration and credential verification; JWT issuance remains AU05 integration. */
@Service
public class AuthService {

    // CT01.core fixes both the BCrypt version and work factor; these are not framework defaults.
    private static final PasswordEncoder PASSWORDS =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final String DUMMY_HASH = PASSWORDS.encode(UUID.randomUUID().toString());

    private final UserRepository users;
    private final Validator validator;
    private final TransactionTemplate transactions;

    public AuthService(UserRepository users, Validator validator, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.validator = validator;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public User register(String email, String password, String displayName) {
        String canonicalEmail = canonicalEmail(email);
        String publicName = displayName == null ? null : displayName.strip();
        validateEmail(canonicalEmail);
        if (password == null || !new StrongPassword.Validator().isValid(password, null)
                || publicName == null || publicName.isBlank()
                || publicName.codePointCount(0, publicName.length()) > 100
                || publicName.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        if (users.findByEmail(canonicalEmail).isPresent()) {
            throw duplicateEmail();
        }
        User account = new User(UUID.randomUUID(), canonicalEmail, PASSWORDS.encode(password),
                publicName, Role.LEARNER, AccountStatus.ACTIVE);
        try {
            // Commit is inside this try block: uniqueness races can surface after save returns.
            return transactions.execute(status -> users.save(account));
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "uq_users_email".equals(violation.getConstraintName())) {
                    throw duplicateEmail();
                }
            }
            throw failure;
        }
    }

    /** Verifies credentials only. No token, session or successful login response is manufactured. */
    public User authenticate(String email, String password) {
        String canonicalEmail = canonicalEmail(email);
        validateEmail(canonicalEmail);
        if (password == null) {
            throw invalidRequest();
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            PASSWORDS.matches("", DUMMY_HASH);
            throw authenticationFailed();
        }
        User account = users.findByEmail(canonicalEmail).orElse(null);
        boolean matches = PASSWORDS.matches(password, account == null ? DUMMY_HASH : account.passwordHash());
        if (account == null || !matches || account.accountStatus() != AccountStatus.ACTIVE) {
            throw authenticationFailed();
        }
        return account;
    }

    private void validateEmail(String email) {
        if (!validator.validate(new EmailInput(email)).isEmpty()) {
            throw invalidRequest();
        }
    }

    private static String canonicalEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.strip();
        return trimmed.chars().allMatch(c -> c < 128) ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    private static BusinessRuleException invalidRequest() {
        return new BusinessRuleException("Invalid account request.");
    }

    private static BusinessRuleException duplicateEmail() {
        return new BusinessRuleException(HttpStatus.CONFLICT, "Canonical email already exists.");
    }

    private static BadCredentialsException authenticationFailed() {
        return new BadCredentialsException("Authentication failed.");
    }

    private record EmailInput(@NotNull @Email @Pattern(regexp = "[!-~]+") String email) {}
}
