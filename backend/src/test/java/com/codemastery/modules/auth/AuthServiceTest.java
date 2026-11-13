package com.codemastery.modules.auth;

import com.codemastery.common.config.AppConfig;
import com.codemastery.common.config.AppConfig.PasswordResetSettings;
import com.codemastery.common.config.AppConfig.PasswordResetActivation;
import com.codemastery.modules.auth.domain.repository.UserRepository.PasswordResetToken;
import com.codemastery.modules.auth.infrastructure.email.SmtpPasswordResetEmailAdapter;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Locale;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.mail.MailException;
import java.util.concurrent.RejectedExecutionException;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.GlobalExceptionHandler;
import com.codemastery.modules.auth.api.controller.AuthController;
import com.codemastery.modules.auth.api.dto.AuthRequests;
import com.codemastery.modules.auth.api.dto.UserResponse;
import com.codemastery.modules.auth.application.service.AuthService;
import com.codemastery.modules.auth.application.service.PasswordResetService;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailDelivery;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailMessage;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import com.codemastery.modules.auth.infrastructure.security.SecurityConfig;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.auth.infrastructure.security.RestSecurityErrorHandler;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PASSWORD = " unit-only-passphrase ";
    private static final BCryptPasswordEncoder BCRYPT =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ValidatorFactory factory;
    private static Validator validator;
    private static String passwordHash;

    @Mock
    private UserRepository users;
    @Mock
    private PlatformTransactionManager transactionManager;
    private AuthService service;

    @BeforeAll
    static void setUpValidationAndSyntheticHash() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        passwordHash = BCRYPT.encode(PASSWORD);
    }

    @AfterAll
    static void closeValidation() {
        if (factory != null) {
            factory.close();
        }
    }

    @BeforeEach
    void setUpService() {
        service = new AuthService(users, validator, transactionManager);
    }

    private void allowTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void saveSubmittedAccount() {
        allowTransaction();
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static User account(Role role, AccountStatus status) {
        return new User(USER_ID, "learner@example.test", passwordHash, "Unit Learner", role, status);
    }

    private static DataIntegrityViolationException constraintFailure(String name, String state) {
        return new DataIntegrityViolationException("Synthetic persistence failure",
                new ConstraintViolationException("Synthetic constraint failure",
                        new SQLException("Synthetic SQL error", state), "synthetic SQL", name));
    }

    @Nested
    class PasswordRecovery {

        private static final String ACK = "If an account matches, a password reset link will be sent.";
        private static final String INVALID_LINK = "Reset link is invalid or expired.";
        private static final String NEW_PASSWORD = "unit-only-new-passphrase";

        private final Instant now = Instant.parse("2026-11-12T12:00:00Z");
        private final PasswordResetSettings settings = new PasswordResetSettings(Duration.ofMinutes(30),
                "https://codemastery.test", "auth@codemastery.test", "smtp.example.test", "test-user", "test-secret");
        private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

        private PasswordResetService service(ResetEmailDelivery delivery) {
            return service(delivery, Runnable::run, PasswordResetActivation.active(settings));
        }

        private PasswordResetService service(ResetEmailDelivery delivery, Executor executor,
                                             PasswordResetActivation activation) {
            return new PasswordResetService(users, transactionManager, activation, delivery, validator,
                    Clock.fixed(now, ZoneOffset.UTC), executor, meters);
        }

        private double count(String counter) {
            return meters.counter(counter).count();
        }

        private static byte[] randomCapability() {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            return raw;
        }

        private static String encode(byte[] raw) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        }

        private static byte[] sha256(byte[] value) throws Exception {
            return MessageDigest.getInstance("SHA-256").digest(value);
        }

        private PasswordResetToken token(byte[] digest, Instant issuedAt) {
            return new PasswordResetToken(USER_ID, digest, issuedAt, issuedAt.plus(Duration.ofMinutes(30)));
        }

        private void assertInvalidLink(Executable confirmation) {
            BusinessRuleException rejected = assertThrows(BusinessRuleException.class, confirmation);
            assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatus());
            assertEquals(INVALID_LINK, rejected.getPublicDetail());
        }

        @Test
        void activeRequestStoresOnlyDigestCommitsThenSendsConfiguredFragmentLink() throws Exception {
            allowTransaction();
            when(users.findByEmailForUpdate("learner@example.test"))
                    .thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findResetTokenByUserId(USER_ID)).thenReturn(Optional.empty());
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);

            assertEquals(ACK, service(delivery).requestReset("learner@example.test").message());

            ArgumentCaptor<ResetEmailMessage> message = ArgumentCaptor.forClass(ResetEmailMessage.class);
            InOrder order = inOrder(users, transactionManager, delivery);
            order.verify(users).findByEmailForUpdate("learner@example.test");
            order.verify(users).replaceResetToken(eq(USER_ID), any(), eq(now), eq(now.plus(Duration.ofMinutes(30))));
            order.verify(transactionManager).commit(any());
            order.verify(delivery).send(message.capture());
            String link = message.getValue().resetLink();
            assertTrue(link.startsWith("https://codemastery.test/reset-password#token="));
            String capability = link.substring(link.indexOf("#token=") + "#token=".length());
            assertTrue(capability.matches("[A-Za-z0-9_-]{43}"), "canonical 43-character Base64url capability");
            byte[] raw = Base64.getUrlDecoder().decode(capability);
            assertEquals(32, raw.length);
            ArgumentCaptor<byte[]> digest = ArgumentCaptor.forClass(byte[].class);
            verify(users).replaceResetToken(eq(USER_ID), digest.capture(), any(), any());
            assertArrayEquals(sha256(raw), digest.getValue());
            assertEquals("learner@example.test", message.getValue().recipient());
            assertEquals(now.plus(Duration.ofMinutes(30)), message.getValue().expiresAt());
            assertFalse(message.getValue().toString().contains(capability));
            assertFalse(message.getValue().toString().contains("learner@example.test"));
        }

        @Test
        void unknownLockedAndRecentlyIssuedRequestsAcknowledgeIdenticallyWithoutCapabilityOrEmail() {
            allowTransaction();
            when(users.findByEmailForUpdate("unknown@example.test")).thenReturn(Optional.empty());
            when(users.findByEmailForUpdate("locked@example.test"))
                    .thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.LOCKED)));
            when(users.findByEmailForUpdate("learner@example.test"))
                    .thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findResetTokenByUserId(USER_ID)).thenReturn(Optional.of(
                    token(new byte[32], now.minus(Duration.ofSeconds(59)))));
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);
            PasswordResetService recovery = service(delivery);

            for (String email : List.of("unknown@example.test", "locked@example.test", "learner@example.test")) {
                assertEquals(ACK, recovery.requestReset(email).message());
            }

            verify(users, never()).replaceResetToken(any(), any(), any(), any());
            verifyNoInteractions(delivery);
            assertEquals(0, count("auth.password_reset.processing.failures"));
        }

        @Test
        void reissueIsAllowedExactlyAtThePt1mBoundaryAndReplacesTheSoleCapability() {
            allowTransaction();
            when(users.findByEmailForUpdate("learner@example.test"))
                    .thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findResetTokenByUserId(USER_ID)).thenReturn(Optional.of(
                    token(new byte[32], now.minus(Duration.ofMinutes(1)))));
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);

            service(delivery).requestReset("learner@example.test");

            verify(users).replaceResetToken(eq(USER_ID), any(), eq(now), eq(now.plus(Duration.ofMinutes(30))));
            verify(delivery).send(any());
        }

        @Test
        void httpPathOnlyEnqueuesTheCanonicalEmailAndNeverLooksUpTheAccount() {
            List<Runnable> queued = new ArrayList<>();
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);

            assertEquals(ACK, service(delivery, queued::add, PasswordResetActivation.active(settings))
                    .requestReset("learner@example.test").message());

            assertEquals(1, queued.size());
            verifyNoInteractions(users, transactionManager, delivery);
        }

        @Test
        void fullQueueReturnsTheSameAcknowledgementAndCountsOnlyAnAggregate() {
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);
            Executor full = task -> {
                throw new RejectedExecutionException("synthetic full queue");
            };

            assertEquals(ACK, service(delivery, full, PasswordResetActivation.active(settings))
                    .requestReset("learner@example.test").message());

            assertEquals(1, count("auth.password_reset.queue.rejected"));
            verifyNoInteractions(users, transactionManager, delivery);
        }

        @Test
        void inactiveRecoveryAcknowledgesButNeverQueuesLooksUpIssuesOrSends() {
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);
            Executor executor = mock(Executor.class);
            PasswordResetService recovery = service(delivery, executor,
                    PasswordResetActivation.inactive("Missing, placeholder or invalid consumed configuration: SMTP_HOST"));

            assertEquals(ACK, recovery.requestReset("learner@example.test").message());
            assertThrows(BusinessRuleException.class, () -> recovery.requestReset("not-an-email"));

            assertEquals(1, count("auth.password_reset.inactive"));
            verifyNoInteractions(executor, users, transactionManager, delivery);
        }

        @Test
        void deliveryFailureIsIsolatedAfterTheCapabilityCommits() {
            allowTransaction();
            when(users.findByEmailForUpdate("learner@example.test"))
                    .thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findResetTokenByUserId(USER_ID)).thenReturn(Optional.empty());
            ResetEmailDelivery delivery = mock(ResetEmailDelivery.class);
            doThrow(new IllegalStateException("synthetic provider detail")).when(delivery).send(any());

            assertEquals(ACK, service(delivery).requestReset("learner@example.test").message());

            InOrder order = inOrder(transactionManager, delivery);
            order.verify(transactionManager).commit(any());
            order.verify(delivery).send(any());
            verify(transactionManager, never()).rollback(any());
            verify(users, never()).updatePasswordHash(any(), any());
            assertEquals(1, count("auth.password_reset.processing.failures"));
        }

        @Test
        void invalidPasswordAndMalformedOrNoncanonicalCapabilitiesNeverTouchPersistence() {
            PasswordResetService recovery = service(mock(ResetEmailDelivery.class));
            String canonical = encode(randomCapability());
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
            // The final character carries two unused bits; setting one keeps the decoded bytes.
            String noncanonical = canonical.substring(0, 42)
                    + alphabet.charAt(alphabet.indexOf(canonical.charAt(42)) ^ 1);

            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> recovery.resetPassword(canonical, "short")).getStatus());
            assertThrows(BusinessRuleException.class, () -> recovery.resetPassword(canonical, null));
            for (String malformed : Arrays.asList(null, "", "malformed", canonical.substring(1), canonical + "A",
                    canonical + "=", canonical.substring(0, 42) + "+", noncanonical)) {
                assertInvalidLink(() -> recovery.resetPassword(malformed, NEW_PASSWORD));
            }
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void validCapabilityJustBeforeExpiryUpdatesHashAndConsumesItUnderTheUserLock() throws Exception {
            allowTransaction();
            byte[] raw = randomCapability();
            byte[] digest = sha256(raw);
            PasswordResetToken stored = token(digest, now.minus(Duration.ofMinutes(30)).plusNanos(1));
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.of(stored));
            when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.deleteResetToken(eq(USER_ID), any())).thenReturn(true);

            assertEquals("Password reset. Sign in with your new password.",
                    service(mock(ResetEmailDelivery.class)).resetPassword(encode(raw), NEW_PASSWORD).message());

            InOrder order = inOrder(users, transactionManager);
            order.verify(users).findResetTokenByDigest(argThat(value -> Arrays.equals(digest, value)));
            order.verify(transactionManager).getTransaction(any());
            order.verify(users).findByIdForUpdate(USER_ID);
            order.verify(users).findResetTokenByDigest(argThat(value -> Arrays.equals(digest, value)));
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            order.verify(users).updatePasswordHash(eq(USER_ID), hash.capture());
            order.verify(users).deleteResetToken(eq(USER_ID), argThat(value -> Arrays.equals(digest, value)));
            order.verify(transactionManager).commit(any());
            assertTrue(hash.getValue().startsWith("$2b$14$"));
            assertTrue(BCRYPT.matches(NEW_PASSWORD, hash.getValue()));
        }

        @Test
        void capabilityIssuedExactlyNowIsValid() throws Exception {
            allowTransaction();
            byte[] raw = randomCapability();
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.of(token(sha256(raw), now)));
            when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.deleteResetToken(eq(USER_ID), any())).thenReturn(true);

            service(mock(ResetEmailDelivery.class)).resetPassword(encode(raw), NEW_PASSWORD);

            verify(users).updatePasswordHash(eq(USER_ID), any());
        }

        static Stream<Arguments> rejectedStoredStates() {
            return Stream.of(
                    Arguments.of("expired exactly at expires_at", Duration.ofMinutes(-30), AccountStatus.ACTIVE, true),
                    Arguments.of("issued in the future", Duration.ofNanos(1), AccountStatus.ACTIVE, true),
                    Arguments.of("locked account", Duration.ofMinutes(-1), AccountStatus.LOCKED, true),
                    Arguments.of("replaced or used before the lock", Duration.ofMinutes(-1), AccountStatus.ACTIVE, false));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rejectedStoredStates")
        void rejectedStatesShareTheGenericErrorAndChangeNothing(String scenario, Duration issuedOffset,
                                                                AccountStatus status, boolean stillPresentAfterLock)
                throws Exception {
            allowTransaction();
            byte[] raw = randomCapability();
            PasswordResetToken stored = token(sha256(raw), now.plus(issuedOffset));
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.of(stored),
                    stillPresentAfterLock ? Optional.of(stored) : Optional.empty());
            when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account(Role.LEARNER, status)));

            assertInvalidLink(() -> service(mock(ResetEmailDelivery.class)).resetPassword(encode(raw), NEW_PASSWORD));

            verify(users, never()).updatePasswordHash(any(), any());
            verify(users, never()).deleteResetToken(any(), any());
            verify(transactionManager).rollback(any());
        }

        @Test
        void unknownCapabilityIsGenericWithoutTransactionLockOrPasswordHash() {
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.empty());
            PasswordResetService recovery = service(mock(ResetEmailDelivery.class));

            long started = System.nanoTime();
            assertInvalidLink(() -> recovery.resetPassword(encode(randomCapability()), NEW_PASSWORD));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            verifyNoInteractions(transactionManager);
            verify(users, never()).findByIdForUpdate(any());
            verify(users, never()).updatePasswordHash(any(), any());
            // A cost-14 BCrypt hash alone takes far longer; an unknown capability costs none.
            assertTrue(elapsed.compareTo(Duration.ofMillis(250)) < 0, "unknown capability was hashed: " + elapsed);
        }

        @Test
        void passwordHashIsComputedBeforeTheUserRowLockIsTaken() throws Exception {
            allowTransaction();
            byte[] raw = randomCapability();
            byte[] digest = sha256(raw);
            long[] marks = new long[2];
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.of(token(digest, now)));
            when(users.findByIdForUpdate(USER_ID)).thenAnswer(call -> {
                marks[0] = System.nanoTime();
                return Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE));
            });
            doAnswer(call -> {
                marks[1] = System.nanoTime();
                return null;
            }).when(users).updatePasswordHash(eq(USER_ID), any());
            when(users.deleteResetToken(eq(USER_ID), any())).thenReturn(true);
            PasswordResetService recovery = service(mock(ResetEmailDelivery.class));

            long started = System.nanoTime();
            recovery.resetPassword(encode(raw), NEW_PASSWORD);

            Duration beforeLock = Duration.ofNanos(marks[0] - started);
            Duration underLock = Duration.ofNanos(marks[1] - marks[0]);
            // The hash (hundreds of milliseconds at cost 14) happens before the lock; under the
            // lock only in-memory checks run here, so that interval is a small fraction of it.
            assertTrue(underLock.multipliedBy(10).compareTo(beforeLock) < 0,
                    "BCrypt ran under the User lock: before=" + beforeLock + ", under=" + underLock);
        }

        @Test
        void failedConsumptionAfterHashUpdateRollsBackTheWholeTransaction() throws Exception {
            allowTransaction();
            byte[] raw = randomCapability();
            PasswordResetToken stored = token(sha256(raw), now.minus(Duration.ofMinutes(1)));
            when(users.findResetTokenByDigest(any())).thenReturn(Optional.of(stored));
            when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.deleteResetToken(eq(USER_ID), any())).thenReturn(false);

            assertInvalidLink(() -> service(mock(ResetEmailDelivery.class)).resetPassword(encode(raw), NEW_PASSWORD));

            verify(users).updatePasswordHash(eq(USER_ID), any());
            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }

        private MockEnvironment recoveryEnvironment(String name, String value) {
            Map<String, String> values = new java.util.LinkedHashMap<>(Map.of(
                    "token-ttl", "PT30M",
                    "frontend-origin", "https://codemastery.test",
                    "email-from", "auth@codemastery.test",
                    "smtp-host", "smtp.example.test",
                    "smtp-username", "unit-user",
                    "smtp-password", "unit-secret-value"));
            if (name != null) {
                values.put(name, value);
            }
            MockEnvironment environment = new MockEnvironment();
            values.forEach((key, setting) -> environment.setProperty("codemastery.security.password-reset." + key, setting));
            return environment;
        }

        @Test
        void completeValidRecoveryConfigurationActivatesWithoutExposingSecrets() {
            PasswordResetActivation active = new AppConfig().passwordResetActivation(recoveryEnvironment(null, null));
            assertTrue(active.isActive());
            assertEquals(Duration.ofMinutes(30), active.requireSettings().tokenTtl());
            assertFalse(active.toString().contains("unit-secret-value"));
        }

        static Stream<Arguments> inactiveRecoveryConfigurations() {
            return Stream.of(Arguments.of("smtp-host", ""), Arguments.of("smtp-host", "smtp example"),
                    Arguments.of("smtp-username", ""), Arguments.of("smtp-password", "<SET_ME_SMTP_PASSWORD>"),
                    Arguments.of("frontend-origin", ""), Arguments.of("frontend-origin", "http://codemastery.test"),
                    Arguments.of("frontend-origin", "https://codemastery.test/reset"),
                    Arguments.of("frontend-origin", "https://CodeMastery.test"),
                    Arguments.of("frontend-origin", "https://user@codemastery.test"),
                    Arguments.of("frontend-origin", "https://*.codemastery.test"),
                    Arguments.of("email-from", "CodeMastery <auth@codemastery.test>"),
                    Arguments.of("email-from", "not-an-address"),
                    Arguments.of("token-ttl", "PT31M"), Arguments.of("token-ttl", "thirty minutes"));
        }

        @ParameterizedTest(name = "{0}={1}")
        @MethodSource("inactiveRecoveryConfigurations")
        void missingPlaceholderOrInvalidRecoveryInputsOnlyDeactivateRecovery(String name, String value) {
            PasswordResetActivation inactive = new AppConfig().passwordResetActivation(recoveryEnvironment(name, value));

            assertFalse(inactive.isActive());
            assertTrue(inactive.inactiveReason().startsWith("Missing, placeholder or invalid consumed configuration: "));
            assertFalse(inactive.inactiveReason().contains("unit-secret-value"));
            if (!value.isEmpty()) {
                assertFalse(inactive.inactiveReason().contains(value), "diagnostic names variables, never values");
            }
            assertThrows(IllegalStateException.class, inactive::requireSettings);
        }

        @Test
        void smtpAdapterFixesAuthenticatedStarttlsSubmissionWithFiniteDeadlines() {
            JavaMailSenderImpl sender = SmtpPasswordResetEmailAdapter.newMailSender(settings);
            assertEquals("smtp.example.test", sender.getHost());
            assertEquals(587, sender.getPort());
            assertEquals("smtp", sender.getProtocol());
            Properties properties = sender.getJavaMailProperties();
            for (String debug : List.of("mail.debug", "mail.debug.auth", "mail.debug.auth.username",
                    "mail.debug.auth.password")) {
                assertEquals("false", properties.getProperty(debug));
            }
            assertFalse(sender.getSession().getDebug());
            assertEquals("true", properties.getProperty("mail.smtp.auth"));
            assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
            assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
            assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
            for (String timeout : List.of("connectiontimeout", "timeout", "writetimeout")) {
                assertEquals("5000", properties.getProperty("mail.smtp." + timeout));
            }
            assertNull(properties.getProperty("mail.smtp.ssl.trust"), "no blanket certificate trust");
        }

        @Test
        void smtpAdapterSendsOnlyThePlainTextLinkMessageAndRefusesWhenInactive() {
            JavaMailSender mail = mock(JavaMailSender.class);
            new SmtpPasswordResetEmailAdapter(mail, "auth@codemastery.test").send(new ResetEmailMessage(
                    "learner@example.test", "https://codemastery.test/reset-password#token=abc", now));
            ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mail).send(sent.capture());
            assertEquals("auth@codemastery.test", sent.getValue().getFrom());
            assertArrayEquals(new String[] {"learner@example.test"}, sent.getValue().getTo());
            assertEquals("Reset your CodeMastery password", sent.getValue().getSubject());
            assertTrue(sent.getValue().getText().contains("https://codemastery.test/reset-password#token=abc"));
            assertTrue(sent.getValue().getText().contains("30 minutes"));

            SmtpPasswordResetEmailAdapter inactive = new SmtpPasswordResetEmailAdapter(
                    PasswordResetActivation.inactive("Missing, placeholder or invalid consumed configuration: SMTP_HOST"));
            assertThrows(IllegalStateException.class, () -> inactive.send(new ResetEmailMessage(
                    "learner@example.test", "https://codemastery.test/reset-password#token=abc", now)));
        }
    }

    /**
     * Real SMTP exchanges between the production adapter and an isolated in-process submission
     * server on loopback, using an ephemeral certificate generated for this test JVM. No network,
     * provider or real credential is involved. Only the listener port and the trust anchor for
     * that ephemeral certificate differ from the production client.
     */
    @Nested
    class SmtpSubmissionPolicy {
        private static final List<String> NOTHING_SUBMITTED = List.of("MAIL", "RCPT", "DATA");
        private final ResetEmailMessage reset = new ResetEmailMessage("learner@example.test",
                "https://codemastery.test/reset-password#token=abc", Instant.parse("2026-11-12T12:30:00Z"));

        private JavaMailSenderImpl sender(String host, int port, boolean trustTestCertificate) throws Exception {
            JavaMailSenderImpl sender = SmtpPasswordResetEmailAdapter.newMailSender(new PasswordResetSettings(
                    Duration.ofMinutes(30), "https://codemastery.test", "auth@codemastery.test", host,
                    SubmissionServer.USER, SubmissionServer.SECRET));
            sender.setPort(port);
            if (trustTestCertificate) {
                Properties properties = new Properties();
                properties.putAll(sender.getJavaMailProperties());
                properties.put("mail.smtp.ssl.socketFactory", TestCertificate.clientTrust().getSocketFactory());
                sender.setJavaMailProperties(properties);
            }
            return sender;
        }

        private void send(JavaMailSenderImpl sender) {
            new SmtpPasswordResetEmailAdapter(sender, "auth@codemastery.test").send(reset);
        }

        private static void assertNothingSubmitted(SubmissionServer server) {
            assertNull(server.message());
            for (String verb : server.verbs()) {
                assertFalse(NOTHING_SUBMITTED.contains(verb.replace("TLS ", "")), "no submission after refusal: " + verb);
            }
        }

        static Stream<Arguments> diagnosticExchanges() {
            return Stream.of("JUL", "SLF4J", "SESSION").flatMap(path -> Stream.of("DEBUG", "TRACE")
                    .flatMap(level -> Stream.of(true, false).map(success -> Arguments.of(path, level, success))));
        }

        @ParameterizedTest(name = "{0} {1}, delivery succeeds={2}")
        @MethodSource("diagnosticExchanges")
        void verboseDiagnosticsNeverDiscloseRecoveryInformation(String path, String level, boolean success)
                throws Exception {
            TestCertificate.serverContext();
            try (MailLogCapture logs = new MailLogCapture(path, level);
                 SubmissionServer server = new SubmissionServer(true, "PLAIN", SubmissionServer.SECRET,
                         null, !success)) {
                JavaMailSenderImpl client = sender("localhost", server.port(), true);
                if (path.equals("SESSION")) {
                    client.getSession().setDebug(true); // Direct PrintStream path also stays silent.
                }
                // Even a later logging-level change must not reopen the source diagnostic path.
                logs.enableVerboseLevels();
                if (success) {
                    send(client);
                } else {
                    assertThrows(MailException.class, () -> send(client));
                }
                server.awaitSession();
                assertTrue(server.authenticated());
                assertTrue(server.verbs().contains("TLS DATA"), "exercise the sensitive payload path");
                logs.assertNoRecoveryInformation();
            }
        }

        @Test
        void incompatibleAuthenticationMechanismsNeverReceiveCredentialsOrMessage() throws Exception {
            try (MailLogCapture logs = new MailLogCapture("JUL", "TRACE");
                 SubmissionServer server = new SubmissionServer(true, "UNSUPPORTED-TEST-MECHANISM",
                         SubmissionServer.SECRET, null, false)) {
                assertThrows(MailException.class, () -> send(sender("localhost", server.port(), true)));
                server.awaitSession();
                assertEquals(List.of("EHLO", "STARTTLS", "TLS EHLO"), server.verbs());
                assertFalse(server.authenticated());
                assertNothingSubmitted(server);
                logs.assertNoRecoveryInformation();
            }
        }

        @Test
        void nonListeningLoopbackEndpointFailsWithoutDisclosingCredentials() throws Exception {
            // Keep the port reserved without listening: no race with another process taking it.
            // The OS may refuse immediately or let the configured connection deadline expire.
            try (var reserved = java.nio.channels.SocketChannel.open();
                 MailLogCapture logs = new MailLogCapture("SLF4J", "TRACE")) {
                reserved.bind(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                int port = ((java.net.InetSocketAddress) reserved.getLocalAddress()).getPort();
                long started = System.nanoTime();
                MailException failure = assertThrows(MailException.class,
                        () -> send(sender("localhost", port, true)));
                assertTrue(hasCause(failure, java.net.ConnectException.class)
                        || hasCause(failure, java.net.SocketTimeoutException.class));
                assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(15)) < 0);
                logs.assertNoRecoveryInformation();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"GREETING", "AUTH"})
        void stalledExchangeEnforcesTheProductionReadTimeout(String stallAt) throws Exception {
            try (MailLogCapture logs = new MailLogCapture("SLF4J", "TRACE");
                 SubmissionServer server = new SubmissionServer(true, "PLAIN", SubmissionServer.SECRET,
                         stallAt, false)) {
                JavaMailSenderImpl client = sender("localhost", server.port(), true);
                long started = System.nanoTime();
                MailException failure = assertThrows(MailException.class, () -> send(client));
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                assertTrue(hasCause(failure, java.net.SocketTimeoutException.class));
                assertTrue(elapsed.compareTo(Duration.ofSeconds(4)) >= 0, "production 5000 ms deadline");
                assertTrue(elapsed.compareTo(Duration.ofSeconds(15)) < 0, "exchange did not time out");
                assertFalse(server.authenticated());
                assertNothingSubmitted(server);
                assertEquals(stallAt.equals("AUTH"), server.verbs().contains("TLS AUTH"));
                logs.assertNoRecoveryInformation();
            }
        }

        private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
            if (type.isInstance(failure)) {
                return true;
            }
            if (failure.getCause() != null && hasCause(failure.getCause(), type)) {
                return true;
            }
            if (failure instanceof org.springframework.mail.MailSendException send) {
                return Arrays.stream(send.getMessageExceptions()).anyMatch(cause -> hasCause(cause, type));
            }
            return false;
        }

        @Test
        void authenticatedStarttlsSubmissionDeliversOnlyAfterTlsAndAuthentication() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(true, true, SubmissionServer.SECRET)) {
                send(sender("localhost", server.port(), true));
                server.awaitSession();

                assertTrue(server.authenticated());
                assertEquals(List.of("EHLO", "STARTTLS", "TLS EHLO", "TLS AUTH", "TLS MAIL", "TLS RCPT",
                        "TLS DATA", "TLS QUIT"), server.verbs());
                assertTrue(server.message().contains("Subject: Reset your CodeMastery password"));
                assertTrue(server.message().contains("https://codemastery.test/reset-password#token=abc"));
            }
        }

        @Test
        void starttlsServerThatDoesNotOfferAuthenticationReceivesNoMessage() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(true, false, SubmissionServer.SECRET)) {
                MailException refused = assertThrows(MailException.class,
                        () -> send(sender("localhost", server.port(), true)));
                server.awaitSession();

                assertFalse(server.authenticated());
                assertNothingSubmitted(server);
                assertFalse(String.valueOf(refused.getMessage()).contains(SubmissionServer.SECRET));
            }
        }

        @Test
        void rejectedCredentialsSubmitNothing() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(true, true, "a-different-test-secret")) {
                assertThrows(MailException.class, () -> send(sender("localhost", server.port(), true)));
                server.awaitSession();

                assertFalse(server.authenticated());
                assertNothingSubmitted(server);
            }
        }

        @Test
        void serverWithoutStarttlsNeverReceivesCredentials() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(false, true, SubmissionServer.SECRET)) {
                assertThrows(MailException.class, () -> send(sender("localhost", server.port(), true)));
                server.awaitSession();

                assertFalse(server.verbs().contains("AUTH"), "credentials are never offered without TLS");
                assertNothingSubmitted(server);
            }
        }

        @Test
        void untrustedCertificateIsRefusedBeforeCredentials() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(true, true, SubmissionServer.SECRET)) {
                assertThrows(MailException.class, () -> send(sender("localhost", server.port(), false)));
                server.awaitSession();

                assertEquals(List.of("EHLO", "STARTTLS"), server.verbs());
                assertNothingSubmitted(server);
            }
        }

        @Test
        void certificateThatDoesNotNameTheConfiguredHostIsRefusedBeforeCredentials() throws Exception {
            try (SubmissionServer server = SubmissionServer.start(true, true, SubmissionServer.SECRET)) {
                // The ephemeral certificate names only "localhost", not the loopback IP address.
                assertThrows(MailException.class, () -> send(sender("127.0.0.1", server.port(), true)));
                server.awaitSession();

                assertEquals(List.of("EHLO", "STARTTLS"), server.verbs());
                assertNothingSubmitted(server);
            }
        }
    }

    /** Captures raw JUL handlers or Boot's JUL-to-SLF4J/Logback path, restoring global test state. */
    private static final class MailLogCapture implements AutoCloseable {
        private static final List<String> SOURCES = List.of("org.eclipse.angus.mail.smtp",
                "org.eclipse.angus.mail.smtp.protocol", "org.eclipse.angus.mail.util.socket");
        private final java.util.logging.Logger julRoot = java.util.logging.Logger.getLogger("");
        private final java.util.logging.Level oldJulLevel = julRoot.getLevel();
        private final java.util.logging.Handler[] oldHandlers = julRoot.getHandlers();
        private final List<java.util.logging.Logger> sources = SOURCES.stream()
                .map(java.util.logging.Logger::getLogger).toList();
        private final List<java.util.logging.Level> oldLevels = sources.stream().map(java.util.logging.Logger::getLevel)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        private final List<java.util.logging.Filter> oldFilters = sources.stream().map(java.util.logging.Logger::getFilter)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        private final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        private final ch.qos.logback.classic.Level oldLevel = root.getLevel();
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        private final List<String> julMessages = new CopyOnWriteArrayList<>();
        private final ByteArrayOutputStream directOutput = new ByteArrayOutputStream();
        private final PrintStream oldOut = System.out;
        private final PrintStream oldErr = System.err;
        private final PrintStream capturedOutput = new PrintStream(directOutput, true, StandardCharsets.UTF_8);

        MailLogCapture(String path, String level) {
            System.setOut(capturedOutput);
            System.setErr(capturedOutput);
            root.setLevel(ch.qos.logback.classic.Level.toLevel(level));
            appender.start();
            root.addAppender(appender);
            Arrays.stream(oldHandlers).forEach(julRoot::removeHandler);
            if (path.equals("SLF4J")) {
                // The same bridge used by Spring Boot's LogbackLoggingSystem.
                org.slf4j.bridge.SLF4JBridgeHandler.install();
            } else {
                julRoot.addHandler(new java.util.logging.Handler() {
                    { setLevel(java.util.logging.Level.ALL); }
                    @Override public void publish(java.util.logging.LogRecord record) {
                        julMessages.add(record.getMessage());
                        if (record.getParameters() != null) {
                            julMessages.add(Arrays.toString(record.getParameters()));
                        }
                        if (record.getThrown() != null) {
                            var trace = new java.io.StringWriter();
                            record.getThrown().printStackTrace(new java.io.PrintWriter(trace));
                            julMessages.add(trace.toString());
                        }
                    }
                    @Override public void flush() { }
                    @Override public void close() { }
                });
            }
            julRoot.setLevel(level.equals("TRACE") ? java.util.logging.Level.FINEST : java.util.logging.Level.FINE);
            sources.forEach(source -> source.setFilter(null));
            enableVerboseLevels();
        }

        void enableVerboseLevels() {
            sources.forEach(source -> source.setLevel(julRoot.getLevel()));
        }

        void assertNoRecoveryInformation() {
            // Prove that the capture works and unrelated application diagnostics remain available.
            org.slf4j.LoggerFactory.getLogger("com.codemastery.smtp-test-control").debug("application-log-control");
            julRoot.log(java.util.logging.Level.FINE, "jul-log-control");
            String output = directOutput.toString(StandardCharsets.UTF_8) + julMessages
                    + appender.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil
                            .asString(event.getThrowableProxy()))).toList();
            assertTrue(output.contains("application-log-control"));
            assertTrue(output.contains("jul-log-control"));
            for (String secret : List.of(SubmissionServer.USER, SubmissionServer.SECRET,
                    Base64.getEncoder().encodeToString(("\0" + SubmissionServer.USER + "\0" + SubmissionServer.SECRET)
                            .getBytes(StandardCharsets.UTF_8)),
                    "https://codemastery.test/reset-password#token=abc", "token=abc", "learner@example.test",
                    "auth@codemastery.test", "A password reset was requested", "Reset your CodeMastery password")) {
                assertFalse(output.contains(secret), "sensitive SMTP diagnostics were emitted");
            }
        }

        @Override
        public void close() {
            System.setOut(oldOut);
            System.setErr(oldErr);
            root.detachAppender(appender);
            appender.stop();
            root.setLevel(oldLevel);
            for (java.util.logging.Handler handler : julRoot.getHandlers()) {
                julRoot.removeHandler(handler);
                handler.close();
            }
            Arrays.stream(oldHandlers).forEach(julRoot::addHandler);
            julRoot.setLevel(oldJulLevel);
            for (int i = 0; i < sources.size(); i++) {
                sources.get(i).setLevel(oldLevels.get(i));
                sources.get(i).setFilter(oldFilters.get(i));
            }
            capturedOutput.close();
        }
    }

    /** One ephemeral self-signed certificate per test JVM, generated with the JDK keytool. */
    private static final class TestCertificate {
        private static SSLContext server;
        private static SSLContext client;

        static synchronized SSLContext serverContext() throws Exception {
            generate();
            return server;
        }

        static synchronized SSLContext clientTrust() throws Exception {
            generate();
            return client;
        }

        private static void generate() throws Exception {
            if (server != null) {
                return;
            }
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            char[] storeSecret = HexFormat.of().formatHex(random).toCharArray();
            Path directory = Files.createTempDirectory("codemastery-smtp-test");
            Path store = directory.resolve("server.p12");
            try {
                Process keytool = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                        "-genkeypair", "-alias", "smtp", "-keyalg", "EC", "-groupname", "secp256r1",
                        "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1",
                        "-storetype", "PKCS12", "-keystore", store.toString(),
                        "-storepass", new String(storeSecret))
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                assertTrue(keytool.waitFor(60, TimeUnit.SECONDS) && keytool.exitValue() == 0, "keytool failed");
                KeyStore keys = KeyStore.getInstance("PKCS12");
                try (var input = Files.newInputStream(store)) {
                    keys.load(input, storeSecret);
                }
                KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagers.init(keys, storeSecret);
                SSLContext serverContext = SSLContext.getInstance("TLS");
                serverContext.init(keyManagers.getKeyManagers(), null, null);

                KeyStore anchors = KeyStore.getInstance("PKCS12");
                anchors.load(null, null);
                anchors.setCertificateEntry("smtp", keys.getCertificate("smtp"));
                TrustManagerFactory trustManagers =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagers.init(anchors);
                SSLContext clientContext = SSLContext.getInstance("TLS");
                clientContext.init(null, trustManagers.getTrustManagers(), null);
                server = serverContext;
                client = clientContext;
            } finally {
                Files.deleteIfExists(store);
                Files.deleteIfExists(directory);
            }
        }
    }

    /**
     * Minimal single-session SMTP submission server: optional STARTTLS and AUTH PLAIN offers, and
     * it accepts MAIL/RCPT/DATA without requiring authentication, so a client that skips
     * authentication would be observed delivering.
     */
    private static final class SubmissionServer implements AutoCloseable {
        static final String USER = "smtp-test-user";
        static final String SECRET = "smtp-test-secret";

        private final ServerSocket listener;
        private final boolean offerStartTls;
        private final String authMechanisms;
        private final String acceptedSecret;
        private final String stallAt;
        private final boolean rejectData;
        private final java.util.concurrent.CountDownLatch stop = new java.util.concurrent.CountDownLatch(1);
        private final List<String> verbs = new CopyOnWriteArrayList<>();
        private final AtomicBoolean authenticated = new AtomicBoolean();
        private final AtomicReference<String> message = new AtomicReference<>();
        private final Thread session;

        private SubmissionServer(boolean offerStartTls, boolean offerAuth, String acceptedSecret) throws Exception {
            this(offerStartTls, offerAuth ? "PLAIN" : null, acceptedSecret, null, false);
        }

        private SubmissionServer(boolean offerStartTls, String authMechanisms, String acceptedSecret,
                                 String stallAt, boolean rejectData) throws Exception {
            TestCertificate.serverContext();
            this.offerStartTls = offerStartTls;
            this.authMechanisms = authMechanisms;
            this.acceptedSecret = acceptedSecret;
            this.stallAt = stallAt;
            this.rejectData = rejectData;
            this.listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.listener.setSoTimeout(10_000);
            this.session = Thread.ofPlatform().daemon().name("isolated-smtp-test-server").start(this::serve);
        }

        static SubmissionServer start(boolean offerStartTls, boolean offerAuth, String acceptedSecret)
                throws Exception {
            return new SubmissionServer(offerStartTls, offerAuth, acceptedSecret);
        }

        int port() {
            return listener.getLocalPort();
        }

        List<String> verbs() {
            return List.copyOf(verbs);
        }

        boolean authenticated() {
            return authenticated.get();
        }

        String message() {
            return message.get();
        }

        void awaitSession() throws InterruptedException {
            session.join(Duration.ofSeconds(15));
            assertFalse(session.isAlive(), "SMTP test session did not finish");
        }

        @Override
        public void close() throws Exception {
            stop.countDown();
            listener.close();
            session.join(Duration.ofSeconds(15));
        }

        private void serve() {
            try (Socket plain = listener.accept()) {
                plain.setSoTimeout(10_000);
                Socket socket = plain;
                boolean tls = false;
                BufferedReader in = reader(socket);
                Writer out = writer(socket);
                if ("GREETING".equals(stallAt)) {
                    stop.await(20, TimeUnit.SECONDS);
                    return;
                }
                reply(out, "220 localhost ESMTP isolated test server");
                String line;
                while ((line = in.readLine()) != null) {
                    String verb = line.split(" ", 2)[0].toUpperCase(Locale.ROOT);
                    verbs.add(tls ? "TLS " + verb : verb);
                    switch (verb) {
                        case "EHLO" -> {
                            List<String> lines = new ArrayList<>(List.of("localhost"));
                            if (!tls && offerStartTls) {
                                lines.add("STARTTLS");
                            }
                            if (authMechanisms != null && (tls || !offerStartTls)) {
                                lines.add("AUTH " + authMechanisms);
                            }
                            for (int i = 0; i < lines.size(); i++) {
                                reply(out, "250" + (i == lines.size() - 1 ? " " : "-") + lines.get(i));
                            }
                        }
                        case "STARTTLS" -> {
                            reply(out, "220 2.0.0 Ready to start TLS");
                            SSLSocket secured = (SSLSocket) TestCertificate.serverContext().getSocketFactory()
                                    .createSocket(plain, "localhost", plain.getPort(), true);
                            secured.setUseClientMode(false);
                            secured.startHandshake();
                            socket = secured;
                            in = reader(socket);
                            out = writer(socket);
                            tls = true;
                        }
                        case "AUTH" -> {
                            if ("AUTH".equals(stallAt)) {
                                stop.await(20, TimeUnit.SECONDS);
                                return;
                            }
                            String[] parts = line.split(" ");
                            String response = parts.length > 2 ? parts[2] : null;
                            if (response == null) {
                                reply(out, "334 ");
                                response = in.readLine();
                            }
                            String[] plainAuth = new String(Base64.getDecoder().decode(response),
                                    StandardCharsets.UTF_8).split("\u0000", -1);
                            boolean accepted = plainAuth.length == 3 && USER.equals(plainAuth[1])
                                    && acceptedSecret.equals(plainAuth[2]);
                            authenticated.set(accepted);
                            reply(out, accepted ? "235 2.7.0 Authentication successful"
                                    : "535 5.7.8 Authentication credentials invalid");
                        }
                        case "MAIL", "RCPT", "RSET", "NOOP" -> reply(out, "250 2.0.0 OK");
                        case "DATA" -> {
                            reply(out, "354 End data with <CR><LF>.<CR><LF>");
                            StringBuilder data = new StringBuilder();
                            for (String body = in.readLine(); body != null && !body.equals("."); body = in.readLine()) {
                                data.append(body).append('\n');
                            }
                            message.set(data.toString());
                            reply(out, rejectData ? "550 5.0.0 synthetic refusal " + USER + " " + SECRET
                                    : "250 2.0.0 Accepted");
                        }
                        case "QUIT" -> {
                            reply(out, "221 2.0.0 Bye");
                            return;
                        }
                        default -> reply(out, "502 5.5.1 Unrecognized command");
                    }
                }
            } catch (Exception ended) {
                // The client ended the session, for example by refusing the certificate.
            }
        }

        private static BufferedReader reader(Socket socket) throws Exception {
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        }

        private static Writer writer(Socket socket) throws Exception {
            return new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
        }

        private static void reply(Writer out, String line) throws Exception {
            out.write(line + "\r\n");
            out.flush();
        }
    }

    @Nested
    class Registration {

        @Test
        void canonicalizesIdentityCreatesOnlyActiveLearnerAndUsesExactBcryptPolicy() {
            saveSubmittedAccount();
            User result = service.register("  LEARNER@EXAMPLE.TEST\t", PASSWORD, "  Unit Learner  ");
            assertNotNull(result.id());
            assertEquals("learner@example.test", result.email());
            assertEquals("Unit Learner", result.displayName());
            assertEquals(Role.LEARNER, result.role());
            assertEquals(AccountStatus.ACTIVE, result.accountStatus());
            assertTrue(result.passwordHash().startsWith("$2b$14$"));
            assertNotEquals(PASSWORD, result.passwordHash());
            assertTrue(BCRYPT.matches(PASSWORD, result.passwordHash()));
            assertFalse(BCRYPT.matches(PASSWORD.strip(), result.passwordHash()));
            verify(users).findByEmail("learner@example.test");
            verify(users).save(result);
            verify(transactionManager).commit(any());
        }

        @Test
        void acceptsOneHundredSupplementaryDisplayNameCodePoints() {
            saveSubmittedAccount();
            String name = "\uD83D\uDE00".repeat(100);
            assertEquals(name, service.register("learner@example.test", PASSWORD, name).displayName());
        }

        @Test
        void preservesAValidSpaceOnlyPassword() {
            saveSubmittedAccount();
            User result = service.register("learner@example.test", " ".repeat(15), "Unit Learner");
            assertTrue(BCRYPT.matches(" ".repeat(15), result.passwordHash()));
        }

        @Test
        void returnsTheActualSavedRepresentation() {
            allowTransaction();
            User stored = account(Role.LEARNER, AccountStatus.ACTIVE);
            when(users.save(any())).thenReturn(stored);
            assertSame(stored, service.register("learner@example.test", PASSWORD, "Unit Learner"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "learner", "name @example.test", "é@example.test", "K@example.test"})
        void rejectsInvalidOrNonAsciiEmailBeforePersistence(String email) {
            BusinessRuleException error = assertThrows(BusinessRuleException.class,
                    () -> service.register(email, PASSWORD, "Unit Learner"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "Unit\tLearner", "Unit\u0000Learner"})
        void rejectsMissingBlankOrControlCharacterDisplayNames(String name) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, name)).getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void rejectsOneHundredAndOneDisplayNameCodePoints() {
            assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "\uD83D\uDE00".repeat(101)));
            verifyNoInteractions(users, transactionManager);
        }

        static Stream<Arguments> invalidPasswords() {
            return Stream.of(Arguments.of((Object) null), Arguments.of("a".repeat(14)),
                    Arguments.of("a".repeat(73)), Arguments.of("\uD83D\uDE00".repeat(19)));
        }

        @ParameterizedTest
        @MethodSource("invalidPasswords")
        void rejectsInvalidPasswordsBeforePersistence(String password) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", password, "Unit Learner")).getStatus());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void rejectsAnAlreadyRegisteredCanonicalEmail() {
            when(users.findByEmail("learner@example.test")).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("LEARNER@EXAMPLE.TEST", PASSWORD, "Unit Learner")).getStatus());
            verify(users, never()).save(any());
            verifyNoInteractions(transactionManager);
        }

        @Test
        void translatesTheExactUniqueEmailConstraintAtSave() {
            allowTransaction();
            when(users.save(any())).thenThrow(constraintFailure("uq_users_email", "23505"));
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")).getStatus());
            verify(transactionManager).rollback(any());
        }

        @Test
        void translatesAUniqueEmailRaceThatAppearsOnlyAtCommit() {
            saveSubmittedAccount();
            doThrow(constraintFailure("uq_users_email", "23505")).when(transactionManager).commit(any());
            assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessRuleException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")).getStatus());
        }

        static Stream<Arguments> unrelatedConstraints() {
            return Stream.of(Arguments.of("pk_users", "23505"),
                    Arguments.of("ck_users_text", "23514"), Arguments.of("uq_users_email", "23514"));
        }

        @ParameterizedTest
        @MethodSource("unrelatedConstraints")
        void doesNotMisclassifyOtherDatabaseFailuresAsDuplicateEmail(String name, String state) {
            allowTransaction();
            RuntimeException failure = constraintFailure(name, state);
            when(users.save(any())).thenThrow(failure);
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> service.register("learner@example.test", PASSWORD, "Unit Learner")));
        }
    }

    @Nested
    class CredentialVerification {

        @ParameterizedTest
        @EnumSource(Role.class)
        void checksTheExactPasswordAndPreservesTheCurrentAccountRole(Role role) {
            User current = account(role, AccountStatus.ACTIVE);
            when(users.findByEmail("learner@example.test")).thenReturn(Optional.of(current));
            assertSame(current, service.authenticate(" LEARNER@EXAMPLE.TEST ", PASSWORD));
            verifyNoInteractions(transactionManager);
        }

        @ParameterizedTest
        @ValueSource(strings = {"missing", "wrong", "locked"})
        void missingIncorrectAndLockedCredentialsHaveTheSameGenericFailure(String scenario) {
            when(users.findByEmail("learner@example.test")).thenReturn("missing".equals(scenario)
                    ? Optional.empty() : Optional.of(account(Role.LEARNER,
                    "locked".equals(scenario) ? AccountStatus.LOCKED : AccountStatus.ACTIVE)));
            String password = "wrong".equals(scenario) ? PASSWORD.strip() : PASSWORD;
            BadCredentialsException error = assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", password));
            assertEquals("Authentication failed.", error.getMessage());
            verify(users, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"ascii", "supplementary"})
        void overlongCredentialsUseGenericRejectionWithoutTruncation(String type) {
            String password = "ascii".equals(type) ? "a".repeat(73) : "\uD83D\uDE00".repeat(19);
            assertEquals("Authentication failed.", assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", password)).getMessage());
            verifyNoInteractions(users, transactionManager);
        }

        @Test
        void registrationMinimumLengthIsNotAnAdditionalLoginValidationError() {
            assertThrows(BadCredentialsException.class,
                    () -> service.authenticate("learner@example.test", "short"));
        }

        @Test
        void absentPasswordIsAnInvalidRequest() {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessRuleException.class,
                    () -> service.authenticate("learner@example.test", null)).getStatus());
            verifyNoInteractions(users);
        }

        @Test
        void missingAccountDataDueToStorageFailureDoesNotBecomeInvalidCredentials() {
            var failure = new DataAccessResourceFailureException("Synthetic unavailable storage");
            when(users.findByEmail("learner@example.test")).thenThrow(failure);
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> service.authenticate("learner@example.test", PASSWORD)));
        }
    }

    @Nested
    class PublicInstructorNames {

        @Test
        void emptyInputDoesNotQueryStorage() {
            assertEquals(Map.of(), new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); }).instructorDisplayNames(Set.of()));
            verifyNoInteractions(users);
        }

        @Test
        void returnsOnlyRequestedInstructorNamesIncludingLockedAccountsInAnImmutableMap() {
            UUID locked = UUID.fromString("00000000-0000-4000-8000-000000000002");
            UUID learner = UUID.fromString("00000000-0000-4000-8000-000000000003");
            UUID missing = UUID.fromString("00000000-0000-4000-8000-000000000004");
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(Role.INSTRUCTOR, AccountStatus.ACTIVE)));
            when(users.findById(locked)).thenReturn(Optional.of(new User(locked, "instructor@example.test",
                    passwordHash, "Locked Instructor", Role.INSTRUCTOR, AccountStatus.LOCKED)));
            when(users.findById(learner)).thenReturn(Optional.of(account(Role.LEARNER, AccountStatus.ACTIVE)));
            when(users.findById(missing)).thenReturn(Optional.empty());
            Map<UUID, String> result = new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); })
                    .instructorDisplayNames(Set.of(USER_ID, locked, learner, missing));
            assertEquals(Map.of(USER_ID, "Unit Learner", locked, "Locked Instructor"), result);
            assertThrows(UnsupportedOperationException.class, () -> result.put(learner, "Forbidden addition"));
        }

        @Test
        void propagatesStorageFailuresWithoutInventingNames() {
            var failure = new DataAccessResourceFailureException("Synthetic unavailable storage");
            when(users.findById(USER_ID)).thenThrow(failure);
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> new UserAccountService(users, () -> { throw new AssertionError("Public names must not read identity"); }).instructorDisplayNames(Set.of(USER_ID))));
        }
    }

    @Nested
    class CurrentIdentityBinding {
        private AnnotationConfigWebApplicationContext context;
        private UserAccountService identities;
        private CourseRepository courses;

        @BeforeEach void startProductionBeans() {
            SecurityContextHolder.clearContext();
            context = new AnnotationConfigWebApplicationContext();
            context.setServletContext(new MockServletContext());
            context.register(IdentityFixture.class);
            context.refresh();
            identities = context.getBean(UserAccountService.class);
            courses = context.getBean(CourseRepository.class);
        }

        @AfterEach void closeProductionBeans() {
            SecurityContextHolder.clearContext();
            if (context != null) context.close();
        }

        @ParameterizedTest @EnumSource(Role.class)
        void springWiresRequestLocalIdentityAndCourseToTheActualAuthAdapter(Role role) {
            var principal = new AuthenticatedUser(USER_ID, role);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            IdentityRole expected = switch (role) {
                case LEARNER -> IdentityRole.LEARNER;
                case INSTRUCTOR -> IdentityRole.INSTRUCTOR;
                case ADMIN -> IdentityRole.ADMINISTRATOR;
            };
            assertEquals(new CurrentIdentity(USER_ID, expected), identities.requireCurrentIdentity());
            UUID course = UUID.randomUUID();
            if (role == Role.ADMIN) {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> context.getBean(CourseAccessService.class).requireLearnerCourseAccess(course));
                verifyNoInteractions(courses);
            } else {
                when(courses.findCourseAccessSnapshot(USER_ID, course, null, null)).thenReturn(Optional.of(
                        new CourseRepository.AccessSnapshot(course, USER_ID, PublishStatus.PUBLISHED,
                                null, null, null, null, EnrollmentStatus.ACTIVE)));
                var access = context.getBean(CourseAccessService.class);
                if (role == Role.LEARNER) access.requireLearnerCourseAccess(course);
                else access.requireCourseOwnership(course);
                verify(courses).findCourseAccessSnapshot(USER_ID, course, null, null);
            }
            verifyNoInteractions(context.getBean(UserRepository.class)); // No redundant account read.
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class, identities::requireCurrentIdentity);
        }

        @Test void rejectsAbsentUnauthenticatedAndForeignPrincipalsWithoutCourseReads() {
            assertThrows(AuthenticationCredentialsNotFoundException.class, identities::requireCurrentIdentity);
            for (var authentication : List.of(
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID, Role.LEARNER), null),
                    new UsernamePasswordAuthenticationToken("caller-supplied-id", null, List.of()),
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(null, Role.LEARNER), null, List.of()),
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID, null), null, List.of()))) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                assertThrows(AuthenticationCredentialsNotFoundException.class,
                        () -> context.getBean(CourseAccessService.class).requireLearnerCourseAccess(UUID.randomUUID()));
            }
            verifyNoInteractions(courses, context.getBean(UserRepository.class));
        }

        @Test void producerFailureAndInvalidResultsAreServerFailuresRatherThanAuthenticationDenials() {
            var failure = new DataAccessResourceFailureException("Synthetic provider failure");
            var facade = new UserAccountService(users, () -> { throw failure; });
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class, facade::requireCurrentIdentity));
            for (var invalid : java.util.Arrays.asList(null,
                    new CurrentIdentity(null, IdentityRole.LEARNER), new CurrentIdentity(USER_ID, null))) {
                assertThrows(IllegalStateException.class,
                        () -> new UserAccountService(users, () -> invalid).requireCurrentIdentity());
            }
            verifyNoInteractions(users);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtTokenProvider.class, RestSecurityErrorHandler.class,
            JacksonAutoConfiguration.class, UserAccountService.class, CourseAccessService.class})
    static class IdentityFixture {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean CourseRepository courses() { return mock(CourseRepository.class); }
        @Bean AppConfig.JwtSettings jwtSettings() {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return new AppConfig.JwtSettings(HexFormat.of().formatHex(key), Duration.ofMinutes(15), "codemastery");
        }
        @Bean AppConfig.CorsSettings corsSettings() {
            return new AppConfig.CorsSettings(List.of("http://localhost:5173"));
        }
    }

    @Nested
    class DtoAndMvcBinding {

        private AuthService application;
        private PasswordResetService resets;
        private MockMvc mvc;

        @BeforeEach
        void setUpMvc() {
            application = mock(AuthService.class);
            resets = mock(PasswordResetService.class);
            mvc = MockMvcBuilders.standaloneSetup(new AuthController(application, mock(JwtTokenProvider.class),
                            resets))
                    .setControllerAdvice(new GlobalExceptionHandler(), new AuthController.RegistrationCacheAdvice())
                    .setValidator(new SpringValidatorAdapter(validator)).build();
        }

        private String registrationJson(String email, String password, String name) {
            return JSON.writeValueAsString(Map.of("email", email, "password", password, "displayName", name));
        }

        @Test
        void registrationBindingReturnsOnlyTheApprovedPublicShapeAndDoesNotIssueAToken() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenReturn(account(Role.LEARNER, AccountStatus.ACTIVE));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson(" LEARNER@EXAMPLE.TEST ", PASSWORD, " Unit Learner ")))
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$", aMapWithSize(3)))
                    .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.displayName").value("Unit Learner"))
                    .andExpect(jsonPath("$.role").value("Learner"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        private static final String RESET_PASSWORD = "unit-only-new-passphrase";

        @Test
        void resetRequestReturnsOnlyTheGeneric202AcknowledgementWithNoStore() throws Exception {
            when(resets.requestReset("learner@example.test")).thenReturn(
                    new PasswordResetService.RequestAcknowledgement("If an account matches, a password reset link will be sent."));
            mvc.perform(post("/api/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\" LEARNER@EXAMPLE.TEST \"}"))
                    .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$", aMapWithSize(1)))
                    .andExpect(jsonPath("$.message").value("If an account matches, a password reset link will be sent."));
        }

        @Test
        void resetConfirmationReturnsOnlyTheCompletionMessageAndNoCredential() throws Exception {
            String token = "A".repeat(43);
            when(resets.resetPassword(token, RESET_PASSWORD)).thenReturn(
                    new PasswordResetService.ResetCompletion("Password reset. Sign in with your new password."));
            mvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("token", token, "password", RESET_PASSWORD))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$", aMapWithSize(1)))
                    .andExpect(jsonPath("$.message").value("Password reset. Sign in with your new password."))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }

        @Test
        void genericResetLinkFailureUsesTheContractDetailWithoutFieldErrors() throws Exception {
            when(resets.resetPassword(any(), any())).thenThrow(BusinessRuleException.withPublicDetail(
                    "synthetic diagnostic with capability", "Reset link is invalid or expired."));
            mvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("token", "A".repeat(43), "password", RESET_PASSWORD))))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Reset link is invalid or expired."))
                    .andExpect(jsonPath("$.errors").doesNotExist())
                    .andExpect(content().string(not(containsString("synthetic diagnostic"))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/auth/password-reset-requests", "/api/auth/password-resets"})
        void resetOperationsAreJsonOnly(String path) throws Exception {
            String body = path.endsWith("requests") ? "{\"email\":\"learner@example.test\"}"
                    : JSON.writeValueAsString(Map.of("token", "A".repeat(43), "password", RESET_PASSWORD));
            for (MediaType type : List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML,
                    MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_OCTET_STREAM)) {
                mvc.perform(post(path).contentType(type).content(body))
                        .andExpect(status().isUnsupportedMediaType())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.detail").value("The request media type is not supported."));
            }
            mvc.perform(post(path).content(body)).andExpect(status().isUnsupportedMediaType());
            verifyNoInteractions(resets);
        }

        @Test
        void jsonWithAnExplicitUtf8CharsetIsAccepted() throws Exception {
            when(resets.requestReset("learner@example.test")).thenReturn(
                    new PasswordResetService.RequestAcknowledgement("If an account matches, a password reset link will be sent."));
            mvc.perform(post("/api/auth/password-reset-requests").contentType("application/json;charset=UTF-8")
                            .content("{\"email\":\"learner@example.test\"}"))
                    .andExpect(status().isAccepted());
        }

        static Stream<Arguments> malformedResetBodies() {
            String token = "A".repeat(43);
            return Stream.of(
                    Arguments.of("/api/auth/password-reset-requests", "not json"),
                    Arguments.of("/api/auth/password-reset-requests", "null"),
                    Arguments.of("/api/auth/password-reset-requests", "[]"),
                    Arguments.of("/api/auth/password-reset-requests", "{}"),
                    Arguments.of("/api/auth/password-reset-requests", "{\"email\":null}"),
                    Arguments.of("/api/auth/password-reset-requests", "{\"email\":7}"),
                    Arguments.of("/api/auth/password-reset-requests", "{\"email\":[\"a@example.test\"]}"),
                    Arguments.of("/api/auth/password-reset-requests", "{\"email\":\"a@example.test\",\"role\":\"x\"}"),
                    Arguments.of("/api/auth/password-reset-requests",
                            "{\"email\":\"a@example.test\",\"email\":\"b@example.test\"}"),
                    Arguments.of("/api/auth/password-reset-requests", "{\"email\":\"a@example.test\"} {}"),
                    Arguments.of("/api/auth/password-resets", "{\"token\":\"" + token + "\"}"),
                    Arguments.of("/api/auth/password-resets", "{\"token\":\"" + token + "\",\"password\":null}"),
                    Arguments.of("/api/auth/password-resets", "{\"token\":123,\"password\":\"" + RESET_PASSWORD + "\"}"),
                    Arguments.of("/api/auth/password-resets",
                            "{\"token\":\"" + token + "\",\"password\":12345678901234567}"),
                    Arguments.of("/api/auth/password-resets",
                            "{\"token\":\"" + token + "\",\"token\":\"" + token + "\",\"password\":\"" + RESET_PASSWORD + "\"}"),
                    Arguments.of("/api/auth/password-resets",
                            "{\"token\":\"" + token + "\",\"password\":\"" + RESET_PASSWORD + "\",\"email\":\"a@example.test\"}"),
                    Arguments.of("/api/auth/password-resets",
                            "{\"token\":\"" + token + "\",\"password\":\"" + RESET_PASSWORD + "\"}[]"));
        }

        @ParameterizedTest
        @MethodSource("malformedResetBodies")
        void malformedDuplicateUnknownTypedInvalidOrTrailingResetBodiesAreSafe400s(String path, String json)
                throws Exception {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("The request is invalid."))
                    .andExpect(content().string(not(containsString(RESET_PASSWORD))));
            verifyNoInteractions(resets);
        }

        static Stream<String> invalidRequests() {
            return Stream.of("null", "[]", "{}",
                    JSON.writeValueAsString(Map.of("email", 7, "password", PASSWORD, "displayName", "Unit Learner")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", 123456789012345L,
                            "displayName", "Unit Learner")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", 12345)),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "role", "Instructor")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "accountStatus", "active")),
                    JSON.writeValueAsString(Map.of("email", "learner@example.test", "password", PASSWORD,
                            "displayName", "Unit Learner", "extra", "value")));
        }

        @ParameterizedTest
        @MethodSource("invalidRequests")
        void malformedMissingTypedInvalidOrUnknownPropertiesAreSafe400s(String json) throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("The request is invalid."));
            verifyNoInteractions(application);
        }

        static Stream<Arguments> invalidRegistrationFields() {
            return Stream.of(Arguments.of("not-an-email", PASSWORD, "Unit Learner"),
                    Arguments.of("é@example.test", PASSWORD, "Unit Learner"),
                    Arguments.of("learner@example.test", "a".repeat(14), "Unit Learner"),
                    Arguments.of("learner@example.test", "a".repeat(73), "Unit Learner"),
                    Arguments.of("learner@example.test", PASSWORD, "\uD83D\uDE00".repeat(101)),
                    Arguments.of("learner@example.test", PASSWORD, "Unit\tLearner"));
        }

        @ParameterizedTest
        @MethodSource("invalidRegistrationFields")
        void approvedFieldPredicatesAreActuallyBoundBeforeTheService(
                String email, String password, String name) throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson(email, password, name)))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(content().string(not(containsString(password))));
            verifyNoInteractions(application);
        }

        @Test
        void conflictUsesSharedSafe409AndNoStore() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenThrow(new BusinessRuleException(HttpStatus.CONFLICT, "Synthetic confidential SQL details"));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson("learner@example.test", PASSWORD, "Unit Learner")))
                    .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.detail").value("The request conflicts with an existing resource."))
                    .andExpect(content().string(not(containsString("confidential"))));
        }

        @Test
        void unexpectedFailureDoesNotDiscloseThePasswordOrPersistenceDetails() throws Exception {
            when(application.register("learner@example.test", PASSWORD, "Unit Learner"))
                    .thenThrow(new DataAccessResourceFailureException("Synthetic confidential storage error"));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationJson("learner@example.test", PASSWORD, "Unit Learner")))
                    .andExpect(status().isInternalServerError()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(not(containsString("confidential"))));
        }

        @Test
        void loginDtoDoesNotApplyRegistrationComplexityOrTransformThePassword() {
            var input = JSON.readValue("{\"email\":\" LEARNER@EXAMPLE.TEST \",\"password\":\"short\"}",
                    AuthRequests.LoginRequest.class);
            assertEquals("learner@example.test", input.email());
            assertEquals("short", input.password());
            assertTrue(validator.validate(input).isEmpty());
            assertFalse(input.toString().contains("short"));
            assertFalse(new AuthRequests.RegisterRequest("learner@example.test", PASSWORD, "Unit Learner")
                    .toString().contains(PASSWORD));
        }

        static Stream<Arguments> publicRoles() {
            return Stream.of(Arguments.of(Role.LEARNER, "Learner"),
                    Arguments.of(Role.INSTRUCTOR, "Instructor"), Arguments.of(Role.ADMIN, "Administrator"));
        }

        @ParameterizedTest
        @MethodSource("publicRoles")
        void publicRoleNamesMatchTheApprovedContract(Role role, String expected) {
            assertEquals(expected, UserResponse.from(account(role, AccountStatus.ACTIVE)).role());
        }
    }
}
