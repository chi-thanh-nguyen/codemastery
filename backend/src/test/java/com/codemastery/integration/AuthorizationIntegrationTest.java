package com.codemastery.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.auth.application.service.RefreshSessionService;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailDelivery;
import com.codemastery.modules.auth.application.service.PasswordResetService.ResetEmailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Actual registration, login rejection and persistence over the configured Auth stack. */
@Import(AuthorizationIntegrationTest.RequiredDependencyHealth.class)
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final BCryptPasswordEncoder PASSWORDS =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final String JWT_SECRET = ephemeralJwtSecret();
    /** Test-only contributor proving a DOWN dependency is aggregated, not concealed, by the probe. */
    private static final AtomicReference<Health> REQUIRED_DEPENDENCY = new AtomicReference<>(Health.up().build());

    @TestConfiguration(proxyBeanMethods = false)
    static class RequiredDependencyHealth {
        @Bean
        HealthIndicator requiredDependencyHealthIndicator() {
            return REQUIRED_DEPENDENCY::get;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private javax.sql.DataSource dataSource;

    @MockitoBean
    private ResetEmailDelivery resetEmailDelivery;

    @Autowired
    private com.codemastery.modules.auth.application.service.AuthService authService;

    @Autowired
    private RefreshSessionService refreshSessions;

    @Autowired
    private com.codemastery.modules.auth.domain.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final Set<String> testEmails = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void configureMockMvcWithProductionSecurityChain() {
        mvc = webAppContextSetup(context)
                .addFilters(new DelegatingFilterProxy("springSecurityFilterChain", context))
                .build();
    }

    private static String ephemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void provideEphemeralJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        // F02 forces 11 concurrent issuance transactions plus one lock holder.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @AfterEach
    void cleanCommittedUsersAndSecurityContext() {
        for (String email : testEmails) {
            jdbc.update("DELETE FROM public.password_reset_tokens WHERE user_id IN "
                    + "(SELECT id FROM public.users WHERE email = ?)", email);
            jdbc.update("DELETE FROM public.refresh_tokens WHERE session_id IN (SELECT s.id FROM "
                    + "public.refresh_sessions s JOIN public.users u ON u.id = s.user_id WHERE u.email = ?)", email);
            jdbc.update("DELETE FROM public.refresh_sessions WHERE user_id IN "
                    + "(SELECT id FROM public.users WHERE email = ?)", email);
            jdbc.update("DELETE FROM public.users WHERE email = ?", email);
        }
        testEmails.clear();
        REQUIRED_DEPENDENCY.set(Health.up().build());
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationPersistsCanonicalLearnerAndVerifiableBcryptHash() throws Exception {
        String email = newEmail();
        String password = syntheticPassword();
        testEmails.add(email);

        MvcResult result = register(Map.of(
                "email", "  " + email.toUpperCase(Locale.ROOT) + "\t",
                "password", password,
                "displayName", "  QA01 Synthetic Learner  "))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.displayName").value("QA01 Synthetic Learner"))
                .andExpect(jsonPath("$.role").value("Learner"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode response = JSON.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(response.get("id").asString());
        assertEquals(1L, userCount(email));
        assertEquals(email, jdbc.queryForObject(
                "SELECT email FROM public.users WHERE id = ?", String.class, id));
        assertEquals("QA01 Synthetic Learner", jdbc.queryForObject(
                "SELECT display_name FROM public.users WHERE id = ?", String.class, id));
        assertEquals("learner", jdbc.queryForObject(
                "SELECT role FROM public.users WHERE id = ?", String.class, id));
        assertEquals("active", jdbc.queryForObject(
                "SELECT account_status FROM public.users WHERE id = ?", String.class, id));

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM public.users WHERE id = ?", String.class, id);
        assertTrue(storedHash.startsWith("$2b$14$"));
        assertNotEquals(password, storedHash);
        assertTrue(PASSWORDS.matches(password, storedHash));
    }

    @Test
    void passwordResetStoresOnlyDigestConsumesTokenAndRequiresOrdinarySignIn() throws Exception {
        String email = newEmail();
        String oldPassword = syntheticPassword();
        String newPassword = syntheticPassword();
        testEmails.add(email);
        register(Map.of("email", email, "password", oldPassword, "displayName", "QA01 Reset Learner"))
                .andExpect(status().isCreated());

        UUID userId = jdbc.queryForObject("SELECT id FROM public.users WHERE email = ?", UUID.class, email);
        UUID sessionBeforeReset = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) "
                + "VALUES (?,?,now(),now() + interval '1 hour')", sessionBeforeReset, userId);
        String bearerIssuedBeforeReset = tokens.createToken(userId, sessionBeforeReset);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<ResetEmailMessage> message = new AtomicReference<>();
        doAnswer(invocation -> {
            message.set(invocation.getArgument(0));
            delivered.countDown();
            return null;
        }).when(resetEmailDelivery).send(any(ResetEmailMessage.class));

        mvc.perform(post("/api/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message")
                        .value("If an account matches, a password reset link will be sent."));
        assertTrue(delivered.await(10, TimeUnit.SECONDS), "Reset email should be delivered asynchronously");

        String link = message.get().resetLink();
        String capability = link.substring(link.indexOf("#token=") + "#token=".length());
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.util.Base64.getUrlDecoder().decode(capability));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.password_reset_tokens "
                + "WHERE user_id = (SELECT id FROM public.users WHERE email = ?) AND token_digest = ?",
                Integer.class, email, digest));

        mvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("token", capability, "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message")
                        .value("Password reset. Sign in with your new password."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.password_reset_tokens "
                + "WHERE user_id = (SELECT id FROM public.users WHERE email = ?)", Integer.class, email));
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM public.users WHERE email = ?", String.class, email);
        assertTrue(PASSWORDS.matches(newPassword, storedHash));
        assertFalse(PASSWORDS.matches(oldPassword, storedHash));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", oldPassword))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", newPassword))))
                .andExpect(status().isOk());
        // ADR014 §6 supersedes the ADR006 9.4 residual: the reset revoked every refresh
        // session in its transaction, so a session-bound bearer issued before it is rejected.
        mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + bearerIssuedBeforeReset))
                .andExpect(status().isUnauthorized());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM public.refresh_sessions "
                + "WHERE id = ?", Boolean.class, sessionBeforeReset));
    }

    /** ADR014: a session-bound bearer for an existing account (one unrevoked refresh session). */
    private String sessionToken(UUID userId) {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) "
                + "VALUES (?,?,now(),now() + interval '1 hour')", session, userId);
        return tokens.createToken(userId, session);
    }

    private static final String RESET_ACK = "{\"message\":\"If an account matches, a password reset link will be sent.\"}";

    private String requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions confirmReset(String capability, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", capability, "password", password))));
    }

    /** Captures every delivered reset message; the FIFO single worker makes a later sentinel a drain barrier. */
    private List<ResetEmailMessage> captureDeliveries(CountDownLatch delivered) {
        List<ResetEmailMessage> messages = java.util.Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            delivered.countDown();
            return null;
        }).when(resetEmailDelivery).send(any(ResetEmailMessage.class));
        return messages;
    }

    private static String capabilityOf(ResetEmailMessage message) {
        String link = message.resetLink();
        return link.substring(link.indexOf("#token=") + "#token=".length());
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(value);
    }

    /** Inserts an active account with a known password and one valid stored capability; returns the capability. */
    private String storedCapability(UUID userId) throws Exception {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        jdbc.update("INSERT INTO public.password_reset_tokens (id,user_id,token_digest,issued_at,expires_at) "
                        + "VALUES (?,?,?,now() - interval '1 minute',now() + interval '29 minutes')",
                UUID.randomUUID(), userId, sha256(raw));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private UUID insertActiveAccountWithPassword(String email, String password) {
        UUID id = UUID.randomUUID();
        testEmails.add(email);
        jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                + "VALUES (?,?,?,'QA01 Reset Fixture','learner','active')", id, email, PASSWORDS.encode(password));
        return id;
    }

    private String storedHash(UUID userId) {
        return jdbc.queryForObject("SELECT password_hash FROM public.users WHERE id = ?", String.class, userId);
    }

    private int resetTokenCount(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM public.password_reset_tokens WHERE user_id = ?",
                Integer.class, userId);
    }

    @Test
    void unknownLockedActiveAndRepeatedRequestsAreIndistinguishableAndOnlyEligibleOnesIssue() throws Exception {
        CountDownLatch delivered = new CountDownLatch(2);
        List<ResetEmailMessage> messages = captureDeliveries(delivered);
        String activeEmail = newEmail();
        UUID active = insertActiveAccountWithPassword(activeEmail, syntheticPassword());
        UUID locked = insertUser("learner", "locked");
        String lockedEmail = jdbc.queryForObject("SELECT email FROM public.users WHERE id = ?", String.class, locked);
        String sentinelEmail = newEmail();
        UUID sentinel = insertActiveAccountWithPassword(sentinelEmail, syntheticPassword());

        List<String> bodies = List.of(requestReset(newEmail()), requestReset(lockedEmail),
                requestReset(activeEmail), requestReset(activeEmail.toUpperCase(Locale.ROOT)),
                requestReset(sentinelEmail));
        bodies.forEach(body -> assertEquals(RESET_ACK, body));
        // FIFO single worker: the sentinel's delivery proves every earlier request was processed.
        assertTrue(delivered.await(20, TimeUnit.SECONDS), "active and sentinel deliveries");

        assertEquals(List.of(activeEmail, sentinelEmail), messages.stream().map(ResetEmailMessage::recipient).toList());
        assertEquals(1, resetTokenCount(active), "the PT1M repeat issued nothing new");
        assertEquals(0, resetTokenCount(locked));
        assertEquals(1, resetTokenCount(sentinel));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.password_reset_tokens WHERE user_id = ? "
                + "AND token_digest = ?", Integer.class, active,
                sha256(java.util.Base64.getUrlDecoder().decode(capabilityOf(messages.get(0))))));
        assertEquals(Duration.ofMinutes(30), Duration.ofSeconds(jdbc.queryForObject(
                "SELECT extract(epoch FROM expires_at - issued_at)::bigint FROM public.password_reset_tokens WHERE user_id = ?",
                Long.class, active)));
    }

    @Test
    void deliveryRunsOnlyAfterCommitAndWithoutHoldingTheUserLock() throws Exception {
        String email = newEmail();
        UUID userId = insertActiveAccountWithPassword(email, syntheticPassword());
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> observed = new AtomicReference<>();
        doAnswer(invocation -> {
            try {
                int visible = resetTokenCount(userId);
                // NOWAIT fails at once (55P03) if the worker still held the User row lock.
                jdbc.queryForList("SELECT id FROM public.users WHERE id = ? FOR UPDATE NOWAIT", userId);
                observed.set("committed=" + visible + ",unlocked");
            } catch (RuntimeException failure) {
                observed.set("failure:" + failure.getClass().getSimpleName());
            } finally {
                delivered.countDown();
            }
            return null;
        }).when(resetEmailDelivery).send(any(ResetEmailMessage.class));

        requestReset(email);

        assertTrue(delivered.await(20, TimeUnit.SECONDS));
        assertEquals("committed=1,unlocked", observed.get());
    }

    @Test
    void concurrentConfirmationsOfOneCapabilityProduceExactlyOneSuccess() throws Exception {
        String email = newEmail();
        String original = syntheticPassword();
        UUID userId = insertActiveAccountWithPassword(email, original);
        String capability = storedCapability(userId);
        List<String> passwords = List.of(syntheticPassword(), syntheticPassword());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String password : passwords) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return confirmReset(capability, password).andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(results.get(0).get(60, TimeUnit.SECONDS),
                    results.get(1).get(60, TimeUnit.SECONDS)));
            statuses.sort(Integer::compareTo);

            assertEquals(List.of(200, 400), statuses);
            assertEquals(0, resetTokenCount(userId));
            String hash = storedHash(userId);
            assertFalse(PASSWORDS.matches(original, hash));
            assertEquals(1, passwords.stream().filter(password -> PASSWORDS.matches(password, hash)).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedCapabilityConsumptionRollsBackThePasswordChange() throws Exception {
        String email = newEmail();
        String original = syntheticPassword();
        UUID userId = insertActiveAccountWithPassword(email, original);
        String capability = storedCapability(userId);
        // Test-only fault injection on the real schema: deleting this user's capability fails
        // after the password-hash UPDATE has already run inside the confirmation transaction.
        jdbc.execute("CREATE FUNCTION public.qa01_fail_reset_consumption() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$BEGIN IF OLD.user_id = '" + userId + "'::uuid THEN RAISE EXCEPTION 'synthetic consumption failure'; "
                + "END IF; RETURN OLD; END$$");
        jdbc.execute("CREATE TRIGGER qa01_fail_reset_consumption BEFORE DELETE ON public.password_reset_tokens "
                + "FOR EACH ROW EXECUTE FUNCTION public.qa01_fail_reset_consumption()");
        try {
            String body = confirmReset(capability, syntheticPassword())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("synthetic consumption failure"));
        } finally {
            jdbc.execute("DROP TRIGGER qa01_fail_reset_consumption ON public.password_reset_tokens");
            jdbc.execute("DROP FUNCTION public.qa01_fail_reset_consumption()");
        }

        assertTrue(PASSWORDS.matches(original, storedHash(userId)), "password update rolled back");
        assertEquals(1, resetTokenCount(userId), "capability still present after rollback");
        confirmReset(capability, syntheticPassword()).andExpect(status().isOk());
    }

    @Test
    void confirmationRechecksTheCapabilityAfterWaitingForTheUserLock() throws Exception {
        String email = newEmail();
        String original = syntheticPassword();
        UUID userId = insertActiveAccountWithPassword(email, original);
        String capability = storedCapability(userId);
        byte[] replacement = sha256(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (java.sql.Connection lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            try (var lock = lockHolder.prepareStatement("SELECT id FROM public.users WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, userId);
                lock.executeQuery().close();
            }
            Future<Integer> confirmation = executor.submit(() ->
                    confirmReset(capability, syntheticPassword()).andReturn().getResponse().getStatus());
            // Condition wait (not an ordering sleep): the confirmation has resolved the digest
            // and is now blocked on the User row lock held above.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                    + "AND query LIKE '%FROM public.users WHERE id = $1 FOR UPDATE%'", Integer.class) == 0) {
                assertFalse(confirmation.isDone(), "confirmation finished without waiting for the User lock");
                assertTrue(System.nanoTime() < deadline, "confirmation never waited for the User lock");
                Thread.onSpinWait();
            }
            // A competing eligible request replaces the sole capability under the same User lock.
            try (var replace = lockHolder.prepareStatement(
                    "DELETE FROM public.password_reset_tokens WHERE user_id = ?")) {
                replace.setObject(1, userId);
                replace.executeUpdate();
            }
            try (var insert = lockHolder.prepareStatement("INSERT INTO public.password_reset_tokens "
                    + "(id,user_id,token_digest,issued_at,expires_at) VALUES (?,?,?,now(),now() + interval '30 minutes')")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, userId);
                insert.setBytes(3, replacement);
                insert.executeUpdate();
            }
            lockHolder.commit();

            assertEquals(400, confirmation.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertTrue(PASSWORDS.matches(original, storedHash(userId)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.password_reset_tokens WHERE user_id = ? "
                + "AND token_digest = ?", Integer.class, userId, replacement));
    }

    @Test
    void expiredFutureLockedAndReusedCapabilitiesShareTheGenericErrorAndChangeNothing() throws Exception {
        String original = syntheticPassword();
        UUID expired = insertActiveAccountWithPassword(newEmail(), original);
        UUID future = insertActiveAccountWithPassword(newEmail(), original);
        UUID locked = insertActiveAccountWithPassword(newEmail(), original);
        String expiredCapability = storedCapability(expired);
        String futureCapability = storedCapability(future);
        String lockedCapability = storedCapability(locked);
        jdbc.update("UPDATE public.password_reset_tokens SET issued_at = now() - interval '31 minutes', "
                + "expires_at = now() - interval '1 minute' WHERE user_id = ?", expired);
        jdbc.update("UPDATE public.password_reset_tokens SET issued_at = now() + interval '5 minutes', "
                + "expires_at = now() + interval '35 minutes' WHERE user_id = ?", future);
        jdbc.update("UPDATE public.users SET account_status = 'locked' WHERE id = ?", locked);

        for (String capability : List.of(expiredCapability, futureCapability, lockedCapability,
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]))) {
            String body = confirmReset(capability, syntheticPassword())
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.detail").value("Reset link is invalid or expired."))
                    .andExpect(jsonPath("$.errors").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(capability));
        }
        for (UUID userId : List.of(expired, future, locked)) {
            assertTrue(PASSWORDS.matches(original, storedHash(userId)));
            assertEquals(1, resetTokenCount(userId));
        }
        assertEquals("locked", jdbc.queryForObject("SELECT account_status FROM public.users WHERE id = ?",
                String.class, locked), "recovery never unlocks an account");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-password", "invalid-email", "blank-display-name"})
    void invalidRegistrationReturnsSafeProblemAndDoesNotPersist(String invalidField) throws Exception {
        String email = newEmail();
        String password = syntheticPassword();
        String displayName = "QA01 Synthetic Learner";
        String submittedEmail = email;

        switch (invalidField) {
            case "short-password" -> password = "a".repeat(14);
            case "invalid-email" -> submittedEmail = "qa01-invalid-" + UUID.randomUUID();
            case "blank-display-name" -> displayName = "   ";
            default -> throw new IllegalArgumentException("Unexpected test case");
        }
        testEmails.add(submittedEmail);

        MvcResult result = register(Map.of(
                "email", submittedEmail,
                "password", password,
                "displayName", displayName))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The request is invalid."))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(password));
        assertEquals(0L, userCount(submittedEmail));
    }

    @ParameterizedTest
    @CsvSource({"role,admin", "accountStatus,active", "accountStatus,locked"})
    void registrationRejectsCallerSuppliedRoleOrStatus(String forbiddenProperty, String suppliedValue)
            throws Exception {
        String email = newEmail();
        testEmails.add(email);
        Map<String, String> request = new HashMap<>(Map.of(
                "email", email,
                "password", syntheticPassword(),
                "displayName", "QA01 Synthetic Learner"));
        request.put(forbiddenProperty, suppliedValue);

        register(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, userCount(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user"})
    void invalidCredentialsReturnGeneric401WithoutChangingAccounts(String scenario) throws Exception {
        String email = newEmail();
        String password = syntheticPassword();
        String submittedPassword = syntheticPassword();
        testEmails.add(email);
        assertNotEquals(password, submittedPassword);

        if ("wrong-password".equals(scenario)) {
            register(Map.of("email", email, "password", password, "displayName", "QA01 Login Learner"))
                    .andExpect(status().isCreated());
        }
        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT id, email, password_hash, display_name, role, account_status "
                        + "FROM public.users WHERE email = ?", email);
        assertEquals("wrong-password".equals(scenario) ? 1 : 0, before.size());
        if ("wrong-password".equals(scenario)) {
            assertEquals("active", before.getFirst().get("account_status"));
            assertTrue(PASSWORDS.matches(password, (String) before.getFirst().get("password_hash")));
        }

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", submittedPassword))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication failed."))
                .andExpect(jsonPath("$.instance").value("/api/auth/login"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertEquals(Set.of("type", "title", "status", "detail", "instance"),
                Set.copyOf(JSON.readTree(body).propertyNames()));
        assertFalse(body.contains(email));
        assertFalse(body.contains(password));
        assertFalse(body.contains(submittedPassword));
        assertEquals(before, jdbc.queryForList(
                "SELECT id, email, password_hash, display_name, role, account_status "
                        + "FROM public.users WHERE email = ?", email));
    }

    @Test
    void duplicateCanonicalEmailReturns409WithoutChangingPersistedAccount() throws Exception {
        String email = newEmail();
        String password = syntheticPassword();
        testEmails.add(email);

        register(Map.of("email", email, "password", password, "displayName", "Original QA01 Learner"))
                .andExpect(status().isCreated());
        String originalHash = jdbc.queryForObject(
                "SELECT password_hash FROM public.users WHERE email = ?", String.class, email);

        MvcResult duplicate = register(Map.of(
                "email", "  " + email.toUpperCase(Locale.ROOT) + " ",
                "password", syntheticPassword(),
                "displayName", "Replacement QA01 Learner"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The request conflicts with an existing resource."))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andReturn();

        assertFalse(duplicate.getResponse().getContentAsString().contains(password));
        assertEquals(1L, userCount(email));
        assertEquals(originalHash, jdbc.queryForObject(
                "SELECT password_hash FROM public.users WHERE email = ?", String.class, email));
        assertEquals("Original QA01 Learner", jdbc.queryForObject(
                "SELECT display_name FROM public.users WHERE email = ?", String.class, email));
    }

    @Test
    void concurrentCanonicalRegistrationsProduceOneCreatedAndOneConflict() throws Exception {
        String email = newEmail();
        String password = syntheticPassword();
        testEmails.add(email);
        String firstBody = JSON.writeValueAsString(Map.of(
                "email", email, "password", password, "displayName", "QA01 Concurrent Learner"));
        String secondBody = JSON.writeValueAsString(Map.of(
                "email", " " + email.toUpperCase(Locale.ROOT) + " ",
                "password", password, "displayName", "QA01 Concurrent Learner"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> firstRequest = concurrentRegistration(ready, start, firstBody);
        Callable<Integer> secondRequest = concurrentRegistration(ready, start, secondBody);
        try {
            Future<Integer> first = executor.submit(firstRequest);
            Future<Integer> second = executor.submit(secondRequest);
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both registration requests should be ready");
            start.countDown();

            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            statuses.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), statuses);
            assertEquals(1L, userCount(email));
            String storedHash = jdbc.queryForObject(
                    "SELECT password_hash FROM public.users WHERE email = ?", String.class, email);
            assertTrue(PASSWORDS.matches(password, storedHash));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Registration workers should terminate");
        }
    }

    @Test
    void anonymousPrivateHealthProbeReturnsNondetailedUp() throws Exception {
        String body = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn().getResponse().getContentAsString();

        // deploy.sh wait_backend_health accepts only HTTP 200 containing this exact compact token.
        assertTrue(body.contains("\"status\":\"UP\""), body);
        assertNondetailedHealth(body);
    }

    @Test
    void privateHealthProbeReportsDownDependencyAs503WithoutDetails() throws Exception {
        REQUIRED_DEPENDENCY.set(Health.down().withDetail("cause", "synthetic-dependency-diagnostic").build());

        String body = mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andReturn().getResponse().getContentAsString();

        assertNondetailedHealth(body);
        assertFalse(body.contains("synthetic-dependency-diagnostic"));
        assertFalse(body.contains("\"status\":\"UP\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health/", "/actuator/health/liveness",
            "/actuator/health/readiness", "/actuator/health/db", "/actuator/info", "/actuator/metrics",
            "/v3/api-docs", "/swagger-ui.html"})
    void otherManagementAndDocumentationSurfacesStayDenied(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get(path).header("Authorization", "Bearer " + sessionToken(insertUser("learner", "active"))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void healthPermitIsLimitedToGet(String method) throws Exception {
        String body = mvc.perform(request(HttpMethod.valueOf(method), "/actuator/health"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("\"status\":\"UP\""));
    }

    @Test
    void recoveryMediaTypesAndUnlistedApiPathsReachTheProductionHandlersInTheFullContext() throws Exception {
        // Test-only controllers must not be component-scanned into the application context, or a
        // catch-all fixture would answer these permitted paths and hide the production 415.
        for (String path : List.of("/api/auth/password-reset-requests", "/api/auth/password-resets")) {
            mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("{}"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("The request media type is not supported."));
        }
        String valid = sessionToken(insertUser("learner", "active"));
        // An operation that is not explicitly listed stays deny-by-default, even for a valid bearer.
        mvc.perform(get("/api/au07-unlisted-probe").header("Authorization", "Bearer " + valid))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void protectedOperationStillRequiresValidBearerForActiveAccount() throws Exception {
        String valid = sessionToken(insertUser("learner", "active"));
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        List<String> rejected = List.of("not-a-jwt", tampered,
                tokens.createToken(UUID.randomUUID(), UUID.randomUUID()), sessionToken(insertUser("learner", "locked")));

        mvc.perform(get("/api/me/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        for (String token : rejected) {
            mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + valid))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"instructor", "admin"})
    void learnerOnlyOperationRejectsOtherRolesWith403(String role) throws Exception {
        mvc.perform(get("/api/me/courses")
                        .header("Authorization", "Bearer " + sessionToken(insertUser(role, "active"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Unconfigured delivery: the rest of the backend runs; recovery acknowledges but never issues. */
    @Nested
    @TestPropertySource(properties = {"codemastery.security.password-reset.smtp-host=",
            "codemastery.security.password-reset.smtp-password=<SET_ME_SMTP_PASSWORD>"})
    class UnconfiguredRecoveryDelivery {

        @Autowired
        private com.codemastery.common.config.AppConfig.PasswordResetActivation activation;

        @Test
        void unrelatedOperationsAndHealthWorkWhileRecoveryIssuesNothing() throws Exception {
            assertFalse(activation.isActive(), "this context must run with recovery delivery inactive");
            assertTrue(activation.inactiveReason().endsWith("SMTP_HOST"), activation.inactiveReason());
            mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
            String email = newEmail();
            String password = syntheticPassword();
            testEmails.add(email);
            register(Map.of("email", email, "password", password, "displayName", "QA01 Inactive Recovery"))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk());

            assertEquals(RESET_ACK, requestReset(email));
            assertEquals(RESET_ACK, requestReset(newEmail()));

            UUID userId = jdbc.queryForObject("SELECT id FROM public.users WHERE email = ?", UUID.class, email);
            assertEquals(0, resetTokenCount(userId));
            org.mockito.Mockito.verifyNoInteractions(resetEmailDelivery);
        }
    }

    /**
     * ADR014 refresh sessions over the real security chain and PostgreSQL: cookie issuance,
     * rotation, replay and grace, concurrency, logout, revocation and CSRF/Origin rules.
     * The test CORS origin is the HTTP loopback default, so the cookie is {@code cm_refresh}.
     */
    @Nested
    class RefreshSessions {

        private static final String ORIGIN = "http://localhost:5173";
        private static final String COOKIE = "cm_refresh";

        private record Signed(String accessToken, String refreshToken, UUID userId) { }

        private Signed signIn(String email, String password) throws Exception {
            MvcResult result = mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();
            JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
            return new Signed(body.get("accessToken").asString(), cookieValue(result),
                    UUID.fromString(body.get("user").get("id").asString()));
        }

        private Signed newSession() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            insertActiveAccountWithPassword(email, password);
            return signIn(email, password);
        }

        private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
            var request = post("/api/auth/refresh").header("Origin", ORIGIN);
            if (token != null) {
                request.cookie(new jakarta.servlet.http.Cookie(COOKIE, token));
            }
            return mvc.perform(request);
        }

        private String cookieValue(MvcResult result) {
            String header = result.getResponse().getHeader("Set-Cookie");
            assertTrue(header != null && header.startsWith(COOKIE + "="), "refresh cookie issued");
            return header.substring(COOKIE.length() + 1, header.indexOf(';'));
        }

        private int protectedStatus(String accessToken) throws Exception {
            return mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + accessToken))
                    .andReturn().getResponse().getStatus();
        }

        private UUID sessionOf(String refreshToken) throws Exception {
            return jdbc.queryForObject("SELECT session_id FROM public.refresh_tokens WHERE token_digest = ?",
                    UUID.class, sha256(java.util.Base64.getUrlDecoder().decode(refreshToken)));
        }

        private boolean revoked(UUID session) {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT revoked_at IS NOT NULL FROM public.refresh_sessions WHERE id = ?", Boolean.class, session));
        }

        @Test
        void loginIssuesAnHttpOnlyCookieStoresOnlyItsDigestAndBindsTheAccessToken() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            insertActiveAccountWithPassword(email, password);
            MvcResult result = mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andReturn();
            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertTrue(setCookie.matches("cm_refresh=[A-Za-z0-9_-]{43}; Path=/; Max-Age=86400; Expires=.+; HttpOnly; SameSite=Strict"),
                    setCookie);
            assertFalse(setCookie.contains("Domain"));
            JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
            assertEquals(Set.of("accessToken", "tokenType", "expiresIn", "user"), Set.copyOf(body.propertyNames()));
            assertFalse(result.getResponse().getContentAsString().contains(cookieValue(result)));

            UUID userId = UUID.fromString(body.get("user").get("id").asString());
            UUID session = sessionOf(cookieValue(result));
            assertEquals(userId, jdbc.queryForObject("SELECT user_id FROM public.refresh_sessions WHERE id = ?",
                    UUID.class, session));
            assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT expires_at = created_at + interval '7 days' "
                    + "FROM public.refresh_sessions WHERE id = ?", Boolean.class, session));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_tokens WHERE token_digest = ?",
                    Integer.class, (Object) cookieValue(result).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            assertEquals(session, tokens.parse(body.get("accessToken").asString()).sessionId());
            assertEquals(200, protectedStatus(body.get("accessToken").asString()));
        }

        @Test
        void refreshRotatesTheTokenKeepsTheSessionAndReturnsTheLoginRepresentation() throws Exception {
            Signed first = newSession();
            MvcResult rotated = refresh(first.refreshToken())
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.user.id").value(first.userId().toString()))
                    .andReturn();
            String next = cookieValue(rotated);
            assertNotEquals(first.refreshToken(), next);
            UUID session = sessionOf(first.refreshToken());
            assertEquals(session, sessionOf(next));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.refresh_tokens WHERE session_id = ? "
                    + "AND consumed_at IS NULL", Integer.class, session));
            String access = JSON.readTree(rotated.getResponse().getContentAsString()).get("accessToken").asString();
            assertEquals(200, protectedStatus(access));
            assertEquals(200, protectedStatus(first.accessToken()));
            refresh(next).andExpect(status().isOk());
        }

        @Test
        void reuseInsideTheGraceWindowIsRejectedWithoutRevokingTheFamily() throws Exception {
            Signed first = newSession();
            String next = cookieValue(refresh(first.refreshToken()).andExpect(status().isOk()).andReturn());
            MvcResult reused = refresh(first.refreshToken()).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
            assertFalse(reused.getResponse().getContentAsString().contains(first.refreshToken()));
            assertFalse(revoked(sessionOf(next)));
            refresh(next).andExpect(status().isOk());
        }

        @Test
        void replayAfterTheGraceWindowRevokesTheFamilyAndItsAccessTokens() throws Exception {
            Signed first = newSession();
            MvcResult rotated = refresh(first.refreshToken()).andExpect(status().isOk()).andReturn();
            String next = cookieValue(rotated);
            String nextAccess = JSON.readTree(rotated.getResponse().getContentAsString()).get("accessToken").asString();
            jdbc.update("UPDATE public.refresh_tokens SET consumed_at = consumed_at - interval '31 seconds', "
                    + "issued_at = issued_at - interval '31 seconds' WHERE token_digest = ?",
                    (Object) sha256(java.util.Base64.getUrlDecoder().decode(first.refreshToken())));
            refresh(first.refreshToken()).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
            assertTrue(revoked(sessionOf(next)));
            refresh(next).andExpect(status().isUnauthorized());
            assertEquals(401, protectedStatus(first.accessToken()));
            assertEquals(401, protectedStatus(nextAccess));
        }

        @Test
        void concurrentRefreshesOfOneTokenProduceExactlyOneRotationAndNoRevocation() throws Exception {
            Signed first = newSession();
            int callers = 6;
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                CountDownLatch ready = new CountDownLatch(callers), start = new CountDownLatch(1);
                List<Future<MvcResult>> results = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return refresh(first.refreshToken()).andReturn();
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                List<MvcResult> done = new ArrayList<>();
                for (Future<MvcResult> result : results) {
                    done.add(result.get(30, TimeUnit.SECONDS));
                }
                assertEquals(1, done.stream().filter(r -> r.getResponse().getStatus() == 200).count());
                assertEquals(callers - 1, done.stream().filter(r -> r.getResponse().getStatus() == 401).count());
                UUID session = sessionOf(first.refreshToken());
                assertFalse(revoked(session));
                assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.refresh_tokens WHERE session_id = ?",
                        Integer.class, session));
                String winner = done.stream().filter(r -> r.getResponse().getStatus() == 200)
                        .map(this::cookieValue).findFirst().orElseThrow();
                refresh(winner).andExpect(status().isOk());
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void logoutRevokesTheSessionAndItsAccessTokensAndAlwaysDeletesTheCookie() throws Exception {
            Signed first = newSession();
            String cleared = mvc.perform(post("/api/auth/logout").header("Origin", ORIGIN)
                            .cookie(new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken())))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getHeader("Set-Cookie");
            assertTrue(cleared.startsWith("cm_refresh=; Path=/; Max-Age=0;") && cleared.contains("HttpOnly")
                    && cleared.contains("SameSite=Strict"), cleared);
            assertTrue(revoked(sessionOf(first.refreshToken())));
            assertEquals(401, protectedStatus(first.accessToken()));
            refresh(first.refreshToken()).andExpect(status().isUnauthorized());
            for (String value : new String[]{null, "malformed", first.refreshToken()}) {
                var request = post("/api/auth/logout").header("Origin", ORIGIN);
                if (value != null) {
                    request.cookie(new jakarta.servlet.http.Cookie(COOKIE, value));
                }
                mvc.perform(request).andExpect(status().isNoContent()).andExpect(header().exists("Set-Cookie"));
            }
        }

        @Test
        void missingMalformedUnknownExpiredAndAmbiguousCookiesFailClosedWithoutSettingACookie() throws Exception {
            Signed first = newSession();
            for (String token : new String[]{null, "", "short", first.refreshToken() + "A",
                    first.refreshToken().substring(0, 42) + "!", java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new byte[32])}) {
                refresh(token).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
            }
            mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN)
                            .cookie(new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken()),
                                    new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken())))
                    .andExpect(status().isUnauthorized());
            jdbc.update("UPDATE public.refresh_tokens SET issued_at = issued_at - interval '25 hours', "
                    + "expires_at = expires_at - interval '24 hours' WHERE session_id = ?", sessionOf(first.refreshToken()));
            refresh(first.refreshToken()).andExpect(status().isUnauthorized());
            assertFalse(revoked(sessionOf(first.refreshToken())));

            Signed second = newSession();
            jdbc.update("UPDATE public.refresh_sessions SET created_at = created_at - interval '8 days', "
                    + "expires_at = expires_at - interval '7 days' WHERE id = ?", sessionOf(second.refreshToken()));
            refresh(second.refreshToken()).andExpect(status().isUnauthorized());
            assertEquals(401, protectedStatus(second.accessToken()));
        }

        @Test
        void lockedAccountsCannotRefreshAndTheirSessionIsRevoked() throws Exception {
            Signed first = newSession();
            jdbc.update("UPDATE public.users SET account_status = 'locked' WHERE id = ?", first.userId());
            refresh(first.refreshToken()).andExpect(status().isUnauthorized());
            assertTrue(revoked(sessionOf(first.refreshToken())));
            jdbc.update("UPDATE public.users SET account_status = 'active' WHERE id = ?", first.userId());
            refresh(first.refreshToken()).andExpect(status().isUnauthorized());
            assertEquals(401, protectedStatus(first.accessToken()));
        }

        @Test
        void refreshReturnsTheCurrentRoleAfterARoleChange() throws Exception {
            Signed first = newSession();
            jdbc.update("UPDATE public.users SET role = 'instructor' WHERE id = ?", first.userId());
            refresh(first.refreshToken()).andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("Instructor"));
            assertEquals(403, protectedStatus(first.accessToken()));
        }

        @Test
        void loginReplacementRevokesThePresentedSessionAndOldSessionsBeyondTheCapAreRevoked() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            Signed first = signIn(email, password);
            List<UUID> older = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID session = UUID.randomUUID();
                older.add(session);
                jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) VALUES "
                        + "(?,?,now() - make_interval(hours => ?),now() + interval '1 day')", session, userId, 20 - i);
            }
            MvcResult replaced = mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                            .cookie(new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk()).andReturn();
            assertTrue(revoked(sessionOf(first.refreshToken())));
            assertTrue(revoked(older.getFirst()), "oldest session beyond the cap of 10 is revoked");
            assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ? "
                    + "AND revoked_at IS NULL", Integer.class, userId));
            refresh(cookieValue(replaced)).andExpect(status().isOk());
        }

        @Test
        void cookieOperationsRejectUntrustedOriginsAndCrossSiteFetchMetadata() throws Exception {
            Signed first = newSession();
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken());
            for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
                mvc.perform(post(path).cookie(cookie)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.detail").value("Access is denied."));
                mvc.perform(post(path).cookie(cookie).header("Origin", "https://attacker.example.test"))
                        .andExpect(status().isForbidden());
                mvc.perform(post(path).cookie(cookie).header("Origin", "null")).andExpect(status().isForbidden());
                mvc.perform(post(path).cookie(cookie).header("Origin", ORIGIN).header("Sec-Fetch-Site", "cross-site"))
                        .andExpect(status().isForbidden());
                mvc.perform(post(path).cookie(cookie).header("Origin", ORIGIN).header("Sec-Fetch-Mode", "navigate"))
                        .andExpect(status().isForbidden());
            }
            assertFalse(revoked(sessionOf(first.refreshToken())));
            refresh(first.refreshToken()).andExpect(status().isOk());
            mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN).header("Sec-Fetch-Site", "same-site")
                    .header("Sec-Fetch-Mode", "cors").cookie(cookie)).andExpect(status().isUnauthorized());

            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            mvc.perform(post("/api/auth/login").header("Origin", "https://attacker.example.test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/login").header("Sec-Fetch-Site", "cross-site")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isForbidden());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ?",
                    Integer.class, userId));
        }

        @Test
        void signInPurgesExpiredSessionsWithTheirTokensAndKeepsLiveOnes() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            UUID expired = UUID.randomUUID(), live = UUID.randomUUID();
            jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) VALUES "
                    + "(?,?,now() - interval '9 days',now() - interval '2 days'),"
                    + "(?,?,now() - interval '1 day',now() + interval '6 days')", expired, userId, live, userId);
            jdbc.update("INSERT INTO public.refresh_tokens (id,session_id,token_digest,issued_at,expires_at) "
                    + "VALUES (?,?,?,now() - interval '9 days',now() - interval '8 days')",
                    UUID.randomUUID(), expired, sha256(new byte[]{1}));
            signIn(email, password);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE id = ?",
                    Integer.class, expired));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_tokens WHERE session_id = ?",
                    Integer.class, expired));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE id = ?",
                    Integer.class, live));
        }

        @Test
        void trustedOriginsMatchIgnoringAsciiCaseLikeCors() throws Exception {
            Signed first = newSession();
            mvc.perform(post("/api/auth/refresh").header("Origin", "HTTP://LOCALHOST:5173")
                            .cookie(new jakarta.servlet.http.Cookie(COOKIE, first.refreshToken())))
                    .andExpect(status().isOk());
        }

        // ---- Independent-review remediation F01–F03 (ADR014 §15): forced interleavings. ----

        /** A row lock held on a separate connection; closing it commits (or rolls back) and releases. */
        private final class HeldLock implements AutoCloseable {
            private final java.sql.Connection connection;

            HeldLock(String sql, Object... ids) throws Exception {
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement(sql)) {
                    for (int i = 0; i < ids.length; i++) {
                        statement.setObject(i + 1, ids[i]);
                    }
                    statement.executeQuery().close();
                }
            }

            void execute(String sql, Object... values) throws Exception {
                try (var statement = connection.prepareStatement(sql)) {
                    for (int i = 0; i < values.length; i++) {
                        statement.setObject(i + 1, values[i]);
                    }
                    statement.executeUpdate();
                }
            }

            @Override
            public void close() throws Exception {
                try {
                    connection.commit();
                } finally {
                    connection.close();
                }
            }
        }

        /** Waits (bounded) until PostgreSQL reports at least {@code count} backends blocked on a lock. */
        private void awaitLockWaiters(int count) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                    + "AND wait_event_type = 'Lock'", Integer.class) < count) {
                assertTrue(System.nanoTime() < deadline, "expected " + count + " lock waiters");
                Thread.onSpinWait();
            }
        }

        private int activeSessions(UUID userId) {
            return jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ? "
                    + "AND revoked_at IS NULL AND expires_at > now()", Integer.class, userId);
        }

        private org.springframework.test.web.servlet.ResultActions login(String email, String password, String cookie)
                throws Exception {
            var request = post("/api/auth/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("email", email, "password", password)));
            if (cookie != null) {
                request.cookie(new jakarta.servlet.http.Cookie(COOKIE, cookie));
            }
            return mvc.perform(request);
        }

        @Test
        void f01AResetCommittedAfterVerificationRejectsTheStaleSignIn() throws Exception {
            String email = newEmail(), oldPassword = syntheticPassword(), newPassword = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, oldPassword);
            com.codemastery.modules.auth.domain.model.User verified = authService.authenticate(email, oldPassword);
            confirmReset(storedCapability(userId), newPassword).andExpect(status().isOk());
            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> refreshSessions.start(verified, java.util.Optional.empty()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ?",
                    Integer.class, userId));
        }

        @Test
        void f01BResetCommittingWhileIssuanceWaitsForTheAccountLockRejectsIt() throws Exception {
            String email = newEmail(), oldPassword = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, oldPassword);
            com.codemastery.modules.auth.domain.model.User verified = authService.authenticate(email, oldPassword);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<?> issuance;
                try (HeldLock reset = new HeldLock("SELECT id FROM public.users WHERE id = ? FOR UPDATE", userId)) {
                    issuance = pool.submit(() -> refreshSessions.start(verified, java.util.Optional.empty()));
                    awaitLockWaiters(1);
                    // The holder now commits exactly what a reset commits: a new hash.
                    reset.execute("UPDATE public.users SET password_hash = ? WHERE id = ?",
                            PASSWORDS.encode(syntheticPassword()), userId);
                }
                var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> issuance.get(30, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof org.springframework.security.authentication.BadCredentialsException);
            } finally {
                pool.shutdownNow();
            }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ?",
                    Integer.class, userId));
        }

        @Test
        void f01CIssuanceCommittingBeforeAWaitingResetIsRevokedByIt() throws Exception {
            String email = newEmail(), oldPassword = syntheticPassword(), newPassword = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, oldPassword);
            Signed earlier = signIn(email, oldPassword);
            UUID earlierSession = sessionOf(earlier.refreshToken());
            String capability = storedCapability(userId);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<MvcResult> signIn;
                Future<Integer> reset;
                // Holding the earlier session row stops the sign-in after it took the account lock
                // (its login-replacement revoke waits); the reset then queues on the account lock.
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.refresh_sessions WHERE id = ? FOR UPDATE",
                        earlierSession)) {
                    signIn = pool.submit(() -> login(email, oldPassword, earlier.refreshToken()).andReturn());
                    awaitLockWaiters(1);
                    reset = pool.submit(() -> confirmReset(capability, newPassword).andReturn().getResponse().getStatus());
                    awaitLockWaiters(2);
                }
                MvcResult issued = signIn.get(30, TimeUnit.SECONDS);
                assertEquals(200, issued.getResponse().getStatus());
                assertEquals(200, reset.get(30, TimeUnit.SECONDS));
                UUID issuedSession = sessionOf(cookieValue(issued));
                assertTrue(revoked(issuedSession), "the reset revokes the session issued just before it");
                refresh(cookieValue(issued)).andExpect(status().isUnauthorized());
                String access = JSON.readTree(issued.getResponse().getContentAsString()).get("accessToken").asString();
                assertEquals(401, protectedStatus(access));
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void f01DAccountLockedAfterVerificationRejectsIssuance() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            com.codemastery.modules.auth.domain.model.User verified = authService.authenticate(email, password);
            jdbc.update("UPDATE public.users SET account_status = 'locked' WHERE id = ?", userId);
            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> refreshSessions.start(verified, java.util.Optional.empty()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ?",
                    Integer.class, userId));
        }

        @Test
        void rotationQueuedTogetherWithAResetEndsRevokedWhicheverRunsFirst() throws Exception {
            String email = newEmail(), oldPassword = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, oldPassword);
            Signed first = signIn(email, oldPassword);
            UUID session = sessionOf(first.refreshToken());
            String capability = storedCapability(userId);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<MvcResult> rotation;
                Future<Integer> reset;
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.refresh_sessions WHERE id = ? FOR UPDATE", session)) {
                    rotation = pool.submit(() -> refresh(first.refreshToken()).andReturn());
                    awaitLockWaiters(1);
                    reset = pool.submit(() -> confirmReset(capability, syntheticPassword()).andReturn().getResponse().getStatus());
                    awaitLockWaiters(2);
                }
                MvcResult rotated = rotation.get(30, TimeUnit.SECONDS);
                assertEquals(200, reset.get(30, TimeUnit.SECONDS));
                assertTrue(revoked(session));
                if (rotated.getResponse().getStatus() == 200) {
                    refresh(cookieValue(rotated)).andExpect(status().isUnauthorized());
                    String access = JSON.readTree(rotated.getResponse().getContentAsString()).get("accessToken").asString();
                    assertEquals(401, protectedStatus(access));
                } else {
                    assertEquals(401, rotated.getResponse().getStatus());
                }
                assertEquals(401, protectedStatus(first.accessToken()));
            } finally {
                pool.shutdownNow();
            }
        }

        // ---- Second independent-review remediation N01 (ADR014 §16). ----

        @Test
        void n01DelayedIssuanceReturnsASessionItsOwnCapEnforcementDidNotRevoke() throws Exception {
            String emailA = newEmail(), emailB = newEmail(), password = syntheticPassword();
            insertActiveAccountWithPassword(emailA, password);
            UUID userB = insertActiveAccountWithPassword(emailB, password);
            Signed cookieOfA = signIn(emailA, password);
            UUID sessionA = sessionOf(cookieOfA.refreshToken());
            com.codemastery.modules.auth.domain.model.User verifiedB = authService.authenticate(emailB, password);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            RefreshSessionService.IssuedSession delayed;
            try {
                Future<RefreshSessionService.IssuedSession> waiting;
                // The sign-in to B presenting A's cookie waits on A's session row (cross-account
                // replacement) while ten other sign-ins to B commit.
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.refresh_sessions WHERE id = ? FOR UPDATE",
                        sessionA)) {
                    waiting = pool.submit(() -> refreshSessions.start(verifiedB,
                            java.util.Optional.of(cookieOfA.refreshToken())));
                    awaitLockWaiters(1);
                    for (int i = 0; i < 10; i++) {
                        refreshSessions.start(verifiedB, java.util.Optional.empty());
                    }
                }
                delayed = waiting.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            assertFalse(revoked(delayed.sessionId()), "issuance must not revoke the session it returns");
            refresh(delayed.refreshToken()).andExpect(status().isOk());
            assertEquals(RefreshSessionService.MAX_ACTIVE_SESSIONS, activeSessions(userB));
            assertTrue(revoked(sessionA));
            // A later, separately committed sign-in revokes the then-oldest session as usual.
            UUID oldest = jdbc.queryForObject("SELECT id FROM public.refresh_sessions WHERE user_id = ? "
                    + "AND revoked_at IS NULL ORDER BY created_at, id LIMIT 1", UUID.class, userB);
            refreshSessions.start(verifiedB, java.util.Optional.empty());
            assertTrue(revoked(oldest));
            assertEquals(RefreshSessionService.MAX_ACTIVE_SESSIONS, activeSessions(userB));
        }

        @Test
        void n01CapNeverSelectsTheIssuingSessionEvenOnATimestampTie() throws Exception {
            UUID userId = insertUser("learner", "active");
            Instant tie = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            List<UUID> sessions = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                UUID session = UUID.randomUUID();
                sessions.add(session);
                jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                        session, userId, java.sql.Timestamp.from(tie), java.sql.Timestamp.from(tie.plus(Duration.ofDays(7))));
            }
            for (UUID candidate : sessions) {
                jdbc.update("UPDATE public.refresh_sessions SET revoked_at = NULL WHERE user_id = ?", userId);
                Integer revokedRows = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .execute(status -> userRepository.revokeRefreshSessionsBeyond(userId,
                                RefreshSessionService.MAX_ACTIVE_SESSIONS, tie, candidate));
                assertEquals(1, revokedRows);
                assertFalse(revoked(candidate), "the issuing session is never a cap candidate");
            }
            assertEquals(RefreshSessionService.MAX_ACTIVE_SESSIONS, activeSessions(userId));
        }

        @Test
        void f02ElevenConcurrentIssuancesFromNoSessionKeepExactlyTenActive() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            com.codemastery.modules.auth.domain.model.User verified = authService.authenticate(email, password);
            int callers = 11;
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                List<Future<?>> issued = new ArrayList<>();
                // All 11 transactions queue on the same lock and are released together.
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.users WHERE id = ? FOR UPDATE", userId)) {
                    for (int i = 0; i < callers; i++) {
                        issued.add(pool.submit(() -> refreshSessions.start(verified, java.util.Optional.empty())));
                    }
                    awaitLockWaiters(callers);
                }
                for (Future<?> result : issued) {
                    result.get(60, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
            assertEquals(RefreshSessionService.MAX_ACTIVE_SESSIONS, activeSessions(userId));
            assertEquals(callers, jdbc.queryForObject("SELECT count(*) FROM public.refresh_sessions WHERE user_id = ?",
                    Integer.class, userId));
        }

        @Test
        void f02ConcurrentReplacementsAboveTheCapLeaveAtMostTenActive() throws Exception {
            String email = newEmail(), password = syntheticPassword();
            UUID userId = insertActiveAccountWithPassword(email, password);
            Signed a = signIn(email, password), b = signIn(email, password);
            for (int i = 0; i < 10; i++) {
                jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) VALUES "
                        + "(?,?,now() - make_interval(hours => ?),now() + interval '1 day')", UUID.randomUUID(), userId, 30 - i);
            }
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.users WHERE id = ? FOR UPDATE", userId)) {
                    for (Signed presented : List.of(a, b)) {
                        results.add(pool.submit(() -> login(email, password, presented.refreshToken())
                                .andReturn().getResponse().getStatus()));
                    }
                    awaitLockWaiters(2);
                }
                for (Future<Integer> result : results) {
                    assertEquals(200, result.get(60, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }
            assertTrue(revoked(sessionOf(a.refreshToken())));
            assertTrue(revoked(sessionOf(b.refreshToken())));
            assertEquals(RefreshSessionService.MAX_ACTIVE_SESSIONS, activeSessions(userId));
        }

        @Test
        void f03OppositeCrossAccountReplacementsBothSucceedWithoutDeadlock() throws Exception {
            String emailA = newEmail(), emailB = newEmail(), password = syntheticPassword();
            UUID userA = insertActiveAccountWithPassword(emailA, password);
            UUID userB = insertActiveAccountWithPassword(emailB, password);
            Signed cookieOfA = signIn(emailA, password), cookieOfB = signIn(emailB, password);
            UUID sessionA = sessionOf(cookieOfA.refreshToken()), sessionB = sessionOf(cookieOfB.refreshToken());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> browserX, browserY;
                // Holding both old session rows makes the two sign-ins overlap: the previous design
                // then locked A's then B's sessions in X and B's then A's in Y (PostgreSQL 40P01).
                try (HeldLock ignored = new HeldLock("SELECT id FROM public.refresh_sessions WHERE id IN (?, ?) "
                        + "ORDER BY id FOR UPDATE", sessionA, sessionB)) {
                    browserX = pool.submit(() -> login(emailA, password, cookieOfB.refreshToken())
                            .andReturn().getResponse().getStatus());
                    browserY = pool.submit(() -> login(emailB, password, cookieOfA.refreshToken())
                            .andReturn().getResponse().getStatus());
                    awaitLockWaiters(2);
                }
                assertEquals(200, browserX.get(60, TimeUnit.SECONDS));
                assertEquals(200, browserY.get(60, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }
            assertTrue(revoked(sessionA));
            assertTrue(revoked(sessionB));
            assertEquals(1, activeSessions(userA));
            assertEquals(1, activeSessions(userB));
        }

        @Test
        void onlyCookieOperationsAdmitCredentialedCorsForTheConfiguredOrigin() throws Exception {
            mvc.perform(options("/api/auth/refresh").header("Origin", ORIGIN)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
            mvc.perform(options("/api/auth/refresh").header("Origin", ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isForbidden());
            mvc.perform(options("/api/me/courses").header("Origin", ORIGIN)
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
    }

    /**
     * CT23 quiz composition, publication and reads (ADR007 §§5–7, B3-O01/O03) through the production
     * security chain, CT03, CT18 and PostgreSQL: Learner-only list, Instructor-only authoring and owner
     * reads, Course ownership/membership precedence, closed bodies and no-store on every outcome. The
     * Course guard (CO14 #177) and CT40 producer (AD07 #165) are absent, so an admitted owner's write
     * fails closed with a safe 500 and no effect; reads run against real rows seeded here.
     */
    @Nested
    class QuizEndpoints {

        private final List<UUID> courses = new ArrayList<>();
        private UUID owner;
        private UUID course;
        private UUID hiddenCourse;
        private UUID skill;
        private UUID q1;
        private UUID q2;
        private UUID quiz;

        @BeforeEach
        void fixtures() {
            owner = insertUser("instructor", "active");
            course = insertCourse(owner, "published");
            hiddenCourse = insertCourse(owner, "hidden");
            skill = UUID.randomUUID();
            jdbc.update("INSERT INTO public.skills (id, course_id, name) VALUES (?, ?, 'QA01 quiz skill')", skill, course);
            q1 = insertQuestion("Prompt one", "ARRAY[true,false]");
            q2 = insertQuestion("Prompt two", "ARRAY[false,true]");
            quiz = insertQuiz(course, "Loops", "hidden", q2, q1);
        }

        @AfterEach
        void removeFixtures() {
            // Memberships first: a quiz may reference a question of another fixture Course.
            for (UUID id : courses) {
                jdbc.update("DELETE FROM public.quiz_questions WHERE quiz_id IN (SELECT id FROM public.quizzes WHERE course_id = ?)", id);
                jdbc.update("DELETE FROM public.quizzes WHERE course_id = ?", id);
            }
            for (UUID id : courses) {
                jdbc.update("DELETE FROM public.questions WHERE skill_id IN (SELECT id FROM public.skills WHERE course_id = ?)", id);
                jdbc.update("DELETE FROM public.skills WHERE course_id = ?", id);
                jdbc.update("DELETE FROM public.enrollments WHERE course_id = ?", id);
                jdbc.update("DELETE FROM public.courses WHERE id = ?", id);
            }
            courses.clear();
        }

        private UUID insertCourse(UUID instructor, String status) {
            UUID id = UUID.randomUUID();
            courses.add(id);
            jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                    + "VALUES (?, ?, 'QA01 quizzes', 'CT23 fixture', ?, false)", id, instructor, status);
            return id;
        }

        private UUID insertQuestion(String prompt, String key) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO public.questions (id, skill_id, prompt, kind, difficulty, options, correct_options) "
                    + "VALUES (?, ?, ?, 'single_choice', 'easy', ARRAY['A','B'], " + key + ")", id, skill, prompt);
            return id;
        }

        private UUID insertQuiz(UUID courseId, String title, String status, UUID... members) {
            return insertQuiz(UUID.randomUUID(), courseId, title, status, members);
        }

        private UUID insertQuiz(UUID id, UUID courseId, String title, String status, UUID... members) {
            jdbc.update("INSERT INTO public.quizzes (id, course_id, title, kind, time_limit_seconds, publication_status) "
                    + "VALUES (?, ?, ?, 'practice', 600, ?)", id, courseId, title, status);
            for (int index = 0; index < members.length; index++) {
                jdbc.update("INSERT INTO public.quiz_questions (id, quiz_id, question_id, position) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(), id, members[index], index + 1);
            }
            return id;
        }

        private void enrol(UUID learner, UUID courseId, String status) {
            jdbc.update("INSERT INTO public.enrollments (id, user_id, course_id, status) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), learner, courseId, status);
        }

        private String validBody() {
            return "{\"title\":\"Loops II\",\"kind\":\"test\",\"timeLimitSeconds\":120,\"questionIds\":[\"" + q1 + "\"]}";
        }

        /** The six Instructor operations against one Course; bodies are valid. */
        private List<org.springframework.test.web.servlet.RequestBuilder> instructorOperations(UUID courseId, String bearer) {
            String base = "/api/courses/" + courseId;
            return authorized(bearer, List.of(
                    get(base + "/owned-quizzes"),
                    get(base + "/quizzes/" + quiz),
                    post(base + "/quizzes").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                    request(HttpMethod.PUT, base + "/quizzes/" + quiz).contentType(MediaType.APPLICATION_JSON).content(validBody()),
                    request(HttpMethod.DELETE, base + "/quizzes/" + quiz),
                    request(HttpMethod.PUT, base + "/quizzes/" + quiz + "/publication")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"publicationStatus\":\"published\"}")));
        }

        private List<org.springframework.test.web.servlet.RequestBuilder> authorized(String bearer,
                List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> builders) {
            List<org.springframework.test.web.servlet.RequestBuilder> requests = new ArrayList<>();
            for (var builder : builders) {
                requests.add(bearer == null ? builder : builder.header("Authorization", "Bearer " + bearer));
            }
            return requests;
        }

        private org.springframework.test.web.servlet.RequestBuilder learnerList(UUID courseId, String bearer) {
            return authorized(bearer, List.of(get("/api/courses/" + courseId + "/quizzes"))).getFirst();
        }

        private String quizState() {
            return jdbc.queryForObject("SELECT coalesce(string_agg(q.id::text || q.title || q.kind || q.time_limit_seconds "
                    + "|| q.publication_status || coalesce(m.members, ''), ',' ORDER BY q.id), '') FROM public.quizzes q "
                    + "LEFT JOIN (SELECT quiz_id, string_agg(question_id::text || ':' || position, ';' ORDER BY position) members "
                    + "FROM public.quiz_questions GROUP BY quiz_id) m ON m.quiz_id = q.id WHERE q.course_id IN (?, ?)",
                    String.class, course, hiddenCourse);
        }

        @Test
        void unauthenticatedRequestsAre401WithNoStoreAndNoEffect() throws Exception {
            String before = quizState();
            List<org.springframework.test.web.servlet.RequestBuilder> requests = new ArrayList<>(instructorOperations(course, null));
            requests.add(learnerList(course, null));
            for (var request : requests) {
                mvc.perform(request)
                        .andExpect(status().isUnauthorized())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
            assertEquals(before, quizState());
        }

        @Test
        void routesAdmitOnlyTheirRoleBeforeAnyHandler() throws Exception {
            String before = quizState();
            UUID enrolled = insertUser("learner", "active");
            enrol(enrolled, course, "active");
            String learner = sessionToken(enrolled);
            String admin = sessionToken(insertUser("admin", "active"));
            for (var request : instructorOperations(course, learner)) {
                mvc.perform(request).andExpect(status().isForbidden())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            List<org.springframework.test.web.servlet.RequestBuilder> adminRequests = new ArrayList<>(instructorOperations(course, admin));
            adminRequests.add(learnerList(course, admin));
            for (var request : adminRequests) {
                mvc.perform(request).andExpect(status().isForbidden())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            // The owning Instructor has no Learner list access either.
            mvc.perform(learnerList(course, sessionToken(owner))).andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"));
            assertEquals(before, quizState());
        }

        @Test
        void nonOwningInstructorIs403OnAPublishedCourseAnd404OnHiddenOrMissingOnes() throws Exception {
            String other = sessionToken(insertUser("instructor", "active"));
            String before = quizState();
            for (var request : instructorOperations(course, other)) {
                mvc.perform(request).andExpect(status().isForbidden())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            for (UUID unavailable : List.of(hiddenCourse, UUID.randomUUID())) {
                for (var request : instructorOperations(unavailable, other)) {
                    mvc.perform(request).andExpect(status().isNotFound())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
                }
            }
            // A malformed body never overtakes CT03: the non-owner still receives 403.
            mvc.perform(post("/api/courses/{c}/quizzes", course).header("Authorization", "Bearer " + other)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"unknown\":true}"))
                    .andExpect(status().isForbidden());
            mvc.perform(request(HttpMethod.PUT, "/api/courses/{c}/quizzes/{q}/publication", course, quiz)
                            .header("Authorization", "Bearer " + other)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                    .andExpect(status().isForbidden());
            assertEquals(before, quizState());
        }

        @Test
        void malformedPathQueryAndMediaTypeAre400ForTheOwner() throws Exception {
            String bearer = sessionToken(owner);
            for (String uri : List.of("/api/courses/not-a-uuid/owned-quizzes", "/api/courses/" + course + "/quizzes/1-1-1-1-1",
                    "/api/courses/" + course + "/owned-quizzes?published=true", "/api/courses/" + course + "/owned-quizzes?page=-1",
                    "/api/courses/" + course + "/owned-quizzes?size=101", "/api/courses/" + course + "/owned-quizzes?size=0",
                    "/api/courses/" + course + "/owned-quizzes?page=2147483648", "/api/courses/" + course + "/owned-quizzes?page=1&page=2",
                    "/api/courses/" + course + "/quizzes/" + quiz + "?x=1")) {
                mvc.perform(get(uri).header("Authorization", "Bearer " + bearer))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            mvc.perform(post("/api/courses/{c}/quizzes", course).header("Authorization", "Bearer " + bearer)
                            .contentType(MediaType.TEXT_PLAIN).content(validBody()))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.quizzes WHERE course_id = ?", Integer.class, course));
        }

        @Test
        void ownerBodiesOutsideTheClosedContractAre400WithNoEffect() throws Exception {
            String bearer = sessionToken(owner);
            String valid = validBody();
            List<String> invalidWrites = List.of(
                    valid.replace("}", ",\"extra\":1}"),
                    valid.replace("\"kind\"", "\"title\":\"dup\",\"kind\""),
                    valid.replace("120", "\"120\""),
                    valid.replace("120", "1.5"),
                    valid.replace("120", "0"),
                    valid.replace("120", "2147483648"),
                    valid.replace("\"test\"", "\"exam\""),
                    valid.replace("\"Loops II\"", "\"   \""),
                    valid.replace("\"Loops II\"", "null"),
                    valid.replace(",\"timeLimitSeconds\":120", ""),
                    valid.replace("[\"" + q1 + "\"]", "[\"" + q1 + "\",\"" + q1 + "\"]"),
                    valid.replace("[\"" + q1 + "\"]", "[null]"),
                    valid + " {}",
                    "{",
                    "");
            String before = quizState();
            for (String body : invalidWrites) {
                for (var request : List.of(post("/api/courses/{c}/quizzes", course),
                        request(HttpMethod.PUT, "/api/courses/{c}/quizzes/{q}", course, quiz))) {
                    mvc.perform(request.header("Authorization", "Bearer " + bearer)
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(status().isBadRequest())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
                }
            }
            for (String body : List.of("{\"published\":true}", "{\"publicationStatus\":\"PUBLISHED\"}",
                    "{\"publicationStatus\":true}", "{\"publicationStatus\":\"hidden\",\"x\":1}", "{}", "")) {
                mvc.perform(request(HttpMethod.PUT, "/api/courses/{c}/quizzes/{q}/publication", course, quiz)
                                .header("Authorization", "Bearer " + bearer)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            assertEquals(before, quizState());
        }

        @Test
        void admittedOwnerWritesFailClosedWhileTheCourseGuardAndCt40AreAbsent() throws Exception {
            String bearer = sessionToken(owner);
            String before = quizState();
            String base = "/api/courses/" + course;
            List<org.springframework.test.web.servlet.RequestBuilder> writes = authorized(bearer, List.of(
                    post(base + "/quizzes").contentType(MediaType.APPLICATION_JSON).content(validBody()),
                    post(base + "/quizzes").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Draft\",\"kind\":\"practice\",\"timeLimitSeconds\":60,\"questionIds\":[]}"),
                    request(HttpMethod.PUT, base + "/quizzes/" + quiz).contentType(MediaType.APPLICATION_JSON).content(validBody()),
                    request(HttpMethod.DELETE, base + "/quizzes/" + quiz),
                    request(HttpMethod.PUT, base + "/quizzes/" + quiz + "/publication")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"publicationStatus\":\"published\"}"),
                    request(HttpMethod.PUT, base + "/quizzes/" + quiz + "/publication")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"publicationStatus\":\"hidden\"}")));
            for (var request : writes) {
                String body = mvc.perform(request)
                        .andExpect(status().isInternalServerError())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                        .andReturn().getResponse().getContentAsString();
                assertFalse(body.contains("CT40") || body.contains("withCourseConfigurationWrite")
                        || body.contains("Prompt") || body.contains("correctOptions"));
            }
            assertEquals(before, quizState());
        }

        @Test
        void ownerReadsReturnRealRowsInCollateCThenUuidOrderWithKeys() throws Exception {
            UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe");
            insertQuiz(high, course, "Same", "published");
            insertQuiz(low, course, "Same", "hidden");
            insertQuiz(course, "😀 emoji", "hidden");
            insertQuiz(course, "� replacement", "hidden");
            insertQuiz(course, "apple", "published", q1);
            insertQuiz(hiddenCourse, "Elsewhere", "published");
            String bearer = sessionToken(owner);

            String page = mvc.perform(get("/api/courses/{c}/owned-quizzes", course).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.totalElements").value(6))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andReturn().getResponse().getContentAsString();
            JsonNode content = JSON.readTree(page).get("content");
            List<String> titles = new ArrayList<>();
            content.forEach(item -> titles.add(item.get("title").asString()));
            // Byte order: "L" < "S" < "a" < U+FFFD (EF BF BD) < U+1F600 (F0 9F 98 80); equal titles by UUID.
            assertEquals(List.of("Loops", "Same", "Same", "apple", "� replacement", "😀 emoji"), titles);
            assertEquals(low.toString(), content.get(1).get("id").asString());
            assertEquals(high.toString(), content.get(2).get("id").asString());
            JsonNode loops = content.get(0);
            assertEquals(2, loops.get("questionCount").asInt());
            assertEquals(q2.toString(), loops.get("questions").get(0).get("question").get("id").asString());
            assertEquals(1, loops.get("questions").get(0).get("position").asInt());
            assertEquals("[false,true]", loops.get("questions").get(0).get("question").get("correctOptions").toString());
            assertEquals(Set.of("id", "courseId", "title", "kind", "timeLimitSeconds", "published", "questionCount", "questions"),
                    Set.copyOf(loops.propertyNames()));

            mvc.perform(get("/api/courses/{c}/owned-quizzes?page=1&size=4", course).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].title").value("� replacement"))
                    .andExpect(jsonPath("$.totalElements").value(6))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(get("/api/courses/{c}/owned-quizzes?page=9", course).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(6));

            mvc.perform(get("/api/courses/{c}/quizzes/{q}", course, quiz).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.published").value(false))
                    .andExpect(jsonPath("$.questions[1].position").value(2))
                    .andExpect(jsonPath("$.questions[1].question.id").value(q1.toString()))
                    .andExpect(jsonPath("$.questions[1].question.correctOptions[0]").value(true));
            // A quiz of another Course, or no quiz at all, is the same safe 404 under this Course.
            UUID foreignQuiz = jdbc.queryForObject("SELECT id FROM public.quizzes WHERE course_id = ?", UUID.class, hiddenCourse);
            for (UUID unavailable : List.of(foreignQuiz, UUID.randomUUID())) {
                mvc.perform(get("/api/courses/{c}/quizzes/{q}", course, unavailable).header("Authorization", "Bearer " + bearer))
                        .andExpect(status().isNotFound())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
            }
        }

        @Test
        void learnerListShowsOnlyPublishedQuizzesOfAnAdmittedCourseWithoutKeys() throws Exception {
            UUID published = insertQuiz(course, "Arrays", "published", q1, q2);
            insertQuiz(course, "Zero", "published");
            insertQuiz(hiddenCourse, "Hidden course quiz", "published");
            UUID active = insertUser("learner", "active");
            UUID completed = insertUser("learner", "active");
            UUID left = insertUser("learner", "active");
            UUID stranger = insertUser("learner", "active");
            enrol(active, course, "active");
            enrol(completed, course, "completed");
            enrol(left, course, "left");
            enrol(active, hiddenCourse, "active");

            for (UUID learner : List.of(active, completed)) {
                String body = mvc.perform(learnerList(course, sessionToken(learner)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.content[0].id").value(published.toString()))
                        .andExpect(jsonPath("$.content[0].questionCount").value(2))
                        .andExpect(jsonPath("$.content[0].published").value(true))
                        .andExpect(jsonPath("$.content[1].title").value("Zero"))
                        .andExpect(jsonPath("$.content[1].questionCount").value(0))
                        .andReturn().getResponse().getContentAsString();
                JsonNode first = JSON.readTree(body).get("content").get(0);
                assertEquals(Set.of("id", "courseId", "title", "kind", "timeLimitSeconds", "published", "questionCount"),
                        Set.copyOf(first.propertyNames()));
                assertFalse(body.contains("Loops") || body.contains("Prompt") || body.contains("correctOptions"));
            }
            for (UUID denied : List.of(left, stranger)) {
                mvc.perform(learnerList(course, sessionToken(denied)))
                        .andExpect(status().isForbidden())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
            mvc.perform(learnerList(hiddenCourse, sessionToken(active)))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/courses/{c}/quizzes?size=1&page=1", course).header("Authorization", "Bearer " + sessionToken(active)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Zero"))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(get("/api/courses/{c}/quizzes?sort=title", course).header("Authorization", "Bearer " + sessionToken(active)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    /** Only the aggregate status and probe group names; no components, details or diagnostics. */
    private static void assertNondetailedHealth(String body) {
        JsonNode health = JSON.readTree(body);
        assertEquals(Set.of("status", "groups"), Set.copyOf(health.propertyNames()));
        assertEquals("[\"liveness\",\"readiness\"]", health.get("groups").toString());
    }

    private UUID insertUser(String role, String accountStatus) {
        UUID id = UUID.randomUUID();
        String email = newEmail();
        testEmails.add(email);
        jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                + "VALUES (?,?,'not-an-authentication-fixture','QA01 Role Fixture',?,?)", id, email, role, accountStatus);
        return id;
    }

    private Callable<Integer> concurrentRegistration(CountDownLatch ready, CountDownLatch start, String body) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent registration");
            }
            return mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };
    }

    private org.springframework.test.web.servlet.ResultActions register(Map<String, String> request)
            throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(request)));
    }

    private long userCount(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM public.users WHERE email = ?", Long.class, email);
    }

    private static String newEmail() {
        return "qa01-" + UUID.randomUUID() + "@example.test";
    }

    private static String syntheticPassword() {
        return "qa01-" + UUID.randomUUID() + "-synthetic";
    }
}
