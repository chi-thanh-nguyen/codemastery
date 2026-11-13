package com.codemastery.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    }

    @AfterEach
    void cleanCommittedUsersAndSecurityContext() {
        for (String email : testEmails) {
            jdbc.update("DELETE FROM public.password_reset_tokens WHERE user_id IN "
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
        String bearerIssuedBeforeReset = tokens.createToken(userId);
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
        // CT01 is unchanged (ADR006 9.4): an earlier bearer stays valid until its own PT15M
        // expiry. This records the residual behavior; it is not a revocation guarantee.
        mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + bearerIssuedBeforeReset))
                .andExpect(status().isOk());
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
        mvc.perform(get(path).header("Authorization", "Bearer " + tokens.createToken(insertUser("learner", "active"))))
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
    void protectedOperationStillRequiresValidBearerForActiveAccount() throws Exception {
        String valid = tokens.createToken(insertUser("learner", "active"));
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        List<String> rejected = List.of("not-a-jwt", tampered,
                tokens.createToken(UUID.randomUUID()), tokens.createToken(insertUser("learner", "locked")));

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
                        .header("Authorization", "Bearer " + tokens.createToken(insertUser(role, "active"))))
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
