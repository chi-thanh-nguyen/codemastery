package com.codemastery.integration;

import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * IN10.read through the real filter chain: authentication and role precede input, only
 * Administrators read, filters and paging follow CT31.read, and responses are no-store.
 * Fixtures are committed and removed after each test.
 */
@DirtiesContext
class AuditLogReadIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/admin/audit-logs";
    private static final UUID ADMIN = id("admin");
    private static final UUID OTHER_ADMIN = id("other-admin");
    private static final UUID LEARNER = id("learner");
    private static final UUID INSTRUCTOR = id("instructor");
    private static final UUID USER_TARGET = id("user-target");
    private static final UUID COURSE_TARGET = id("course-target");
    private static final UUID REPORT_TARGET = id("report-target");
    private static final Instant T0 = Instant.parse("2026-11-18T01:00:00.123456Z");
    private static final String KEY = key();

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenProvider tokens;

    private MockMvc mvc;

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("in10/" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> KEY);
    }

    @BeforeEach
    void fixtures() {
        cleanup();
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        user(ADMIN, "admin", "IN10 Admin");
        user(OTHER_ADMIN, "admin", "IN10 Second Admin");
        user(LEARNER, "learner", "IN10 Learner");
        user(INSTRUCTOR, "instructor", "IN10 Instructor");
        audit(ADMIN, "user_locked", "user", USER_TARGET, T0);
        audit(ADMIN, "course_hidden", "course", COURSE_TARGET, T0.plusSeconds(1));
        audit(OTHER_ADMIN, "report_dismissed", "content_report", REPORT_TARGET, T0.plusSeconds(2));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM public.audit_logs WHERE actor_id IN (?,?,?,?)", ADMIN, OTHER_ADMIN, LEARNER, INSTRUCTOR);
        jdbc.update("DELETE FROM public.refresh_tokens WHERE session_id IN "
                + "(SELECT id FROM public.refresh_sessions WHERE user_id IN (?,?,?,?))", ADMIN, OTHER_ADMIN, LEARNER, INSTRUCTOR);
        jdbc.update("DELETE FROM public.refresh_sessions WHERE user_id IN (?,?,?,?)", ADMIN, OTHER_ADMIN, LEARNER, INSTRUCTOR);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?)", ADMIN, OTHER_ADMIN, LEARNER, INSTRUCTOR);
    }

    private void user(UUID id, String role, String displayName) {
        jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                + "VALUES (?,?,'not-a-login-fixture',?,?,'active')", id, "in10-" + id + "@example.test", displayName, role);
    }

    private void audit(UUID actor, String action, String targetType, UUID target, Instant at) {
        jdbc.update("INSERT INTO public.audit_logs (id,actor_id,action,target_type,target_reference,occurred_at) "
                + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), actor, action, targetType, target.toString(),
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    private UUID refreshSession(UUID actor) {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) "
                + "SELECT ?, id, now(), now() + interval '1 hour' FROM public.users WHERE id = ?", session, actor);
        return session;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID actor) {
        return request.header("Authorization", "Bearer " + tokens.createToken(actor, refreshSession(actor)));
    }

    @Test
    void anonymousRequestsAre401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdministratorsMayReadAndTheRolePrecedesInputValidation() throws Exception {
        for (UUID actor : new UUID[] {LEARNER, INSTRUCTOR}) {
            mvc.perform(as(get(PATH), actor)).andExpect(status().isForbidden());
            mvc.perform(as(get(PATH).param("size", "0"), actor)).andExpect(status().isForbidden());
        }
    }

    @Test
    void administratorListsNewestFirstWithActorNamesAndNoStore() throws Exception {
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("course_hidden"))
                .andExpect(jsonPath("$.content[0].targetType").value("course"))
                .andExpect(jsonPath("$.content[0].targetReference").value(COURSE_TARGET.toString()))
                .andExpect(jsonPath("$.content[0].actorId").value(ADMIN.toString()))
                .andExpect(jsonPath("$.content[0].actorDisplayName").value("IN10 Admin"))
                .andExpect(jsonPath("$.content[1].action").value("user_locked"))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].occurredAt").exists());
    }

    @Test
    void filtersAreAndCombinedWithAnInclusiveFromAndExclusiveTo() throws Exception {
        mvc.perform(as(get(PATH).param("actorId", OTHER_ADMIN.toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorDisplayName").value("IN10 Second Admin"));
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("action", "user_locked"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].targetReference").value(USER_TARGET.toString()));
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("targetType", "course"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("targetReference", USER_TARGET.toString()), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("user_locked"));
        // 08:00:01.123456+07:00 is exactly T0 + 1 s: the inclusive lower bound keeps that row and drops the T0 row.
        mvc.perform(as(get(PATH).param("occurredFrom", "2026-11-18T08:00:01.123456+07:00")
                        .param("occurredTo", "2026-11-18T01:00:02.123456Z").param("actorId", ADMIN.toString()), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("course_hidden"));
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("action", "report_dismissed"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void pagingReportsTheTrueTotalAndAPageBeyondTheEndIsEmpty() throws Exception {
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("size", "1").param("page", "1"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action").value("user_locked"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(as(get(PATH).param("actorId", ADMIN.toString()).param("page", "2147483647").param("size", "100"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(2147483647))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "size=0", "size=101", "page=-1", "page=1.5", "sort=id", "unknown=1", "action=a&action=b",
            "actorId=not-a-uuid", "targetReference=12345", "action=nope", "action=USER_LOCKED", "targetType=group",
            "occurredFrom=yesterday", "occurredFrom=2026-11-18T01:00:00.1234567Z",
            "occurredFrom=2026-11-18T02:00:00Z&occurredTo=2026-11-18T01:00:00Z",
            "occurredFrom=2026-11-18T01:00:00Z&occurredTo=2026-11-18T01:00:00Z"})
    void invalidQueriesAre400WithNoStore(String query) throws Exception {
        mvc.perform(as(get(PATH + "?" + query), ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
