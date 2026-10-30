package com.codemastery.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Actual registration, persistence and canonical-email behavior over the configured Auth stack. */
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final BCryptPasswordEncoder PASSWORDS =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final String JWT_SECRET = ephemeralJwtSecret();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

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
        testEmails.add(email);
        String password = syntheticPassword();
        String displayName = "QA01 Synthetic Learner";
        String submittedEmail = email;

        switch (invalidField) {
            case "short-password" -> password = "a".repeat(14);
            case "invalid-email" -> submittedEmail = "not-an-email";
            case "blank-display-name" -> displayName = "   ";
            default -> throw new IllegalArgumentException("Unexpected test case");
        }

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
        assertEquals(0L, userCount(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "accountStatus"})
    void registrationRejectsCallerSuppliedRoleOrStatus(String forbiddenProperty) throws Exception {
        String email = newEmail();
        testEmails.add(email);
        Map<String, String> request = new HashMap<>(Map.of(
                "email", email,
                "password", syntheticPassword(),
                "displayName", "QA01 Synthetic Learner"));
        request.put(forbiddenProperty, "admin");

        register(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, userCount(email));
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
