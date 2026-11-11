package com.codemastery.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
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
