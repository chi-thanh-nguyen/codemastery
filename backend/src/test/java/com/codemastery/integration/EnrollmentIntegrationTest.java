package com.codemastery.integration;

import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real MVC, JWT/current-account validation, application transactions and disposable PostgreSQL. */
class EnrollmentIntegrationTest extends AbstractIntegrationTest {
    private static final UUID OWNER = id("owner"), LEARNER = id("learner"), OTHER = id("other"), ADMIN = id("admin");
    private static final UUID COURSE = id("course"), SECOND = id("second"), HIDDEN = id("hidden");
    private static final UUID EMPTY_CHAPTER = id("empty-chapter");
    private static final UUID CHAPTER = id("chapter"), LESSON = id("lesson"), HIDDEN_LESSON = id("hidden-lesson");
    private static final UUID ENROLLMENT = id("enrollment"), PROGRESS = id("progress");
    private static final String KEY = key();
    private static final ThreadLocal<List<String>> INPUT_SQL = new ThreadLocal<>();
    public static class InputStatementInspector implements StatementInspector {
        @Override public String inspect(String sql) {
            var statements = INPUT_SQL.get();
            if (statements != null) statements.add(sql);
            return sql;
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.codemastery.modules.course.infrastructure.persistence.repository.CourseJpaRepository catalogueRows;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlatformTransactionManager transactions;
    private MockMvc mvc;

    private static UUID id(String name) { return UUID.nameUUIDFromBytes(("fe07/" + name).getBytes(StandardCharsets.UTF_8)); }
    private static String key() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return HexFormat.of().formatHex(key); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("JWT_SECRET", () -> KEY);
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector", () -> InputStatementInspector.class.getName());
    }

    @BeforeEach void fixtures() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        for (var user : List.of(OWNER, LEARNER, OTHER, ADMIN)) {
            String role = user.equals(OWNER) ? "instructor" : user.equals(ADMIN) ? "admin" : "learner";
            jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) VALUES (?,?,'not-a-login-fixture','FE07 Instructor',?,'active')", user, user + "@example.test", role);
        }
        jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,prerequisite_description,publication_status,adaptive_enabled) VALUES (?,?,'Alpha','Literal 100%_ basics',NULL,'published',false),(?,?,'Beta','Advanced basics','Read the introduction','published',false),(?,?,'Hidden','Secret',NULL,'hidden',false)", COURSE, OWNER, SECOND, OWNER, HIDDEN, OWNER);
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,'Chapter',1)", CHAPTER, COURSE);
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?, ?, 'Private empty chapter', 2)", EMPTY_CHAPTER, COURSE);
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,'Visible lesson',1,'published'),(?,?,'Secret lesson',2,'hidden')", LESSON, CHAPTER, HIDDEN_LESSON, CHAPTER);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM public.lesson_progress WHERE id=?", PROGRESS);
        jdbc.update("DELETE FROM public.enrollments WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.lessons WHERE id IN (?,?)", LESSON, HIDDEN_LESSON);
        jdbc.update("DELETE FROM public.chapters WHERE id IN (?,?)", CHAPTER, EMPTY_CHAPTER);
        jdbc.update("DELETE FROM public.courses WHERE id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?)", OWNER, LEARNER, OTHER, ADMIN);
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID actor) { return request.header("Authorization", "Bearer " + tokens.createToken(actor)); }
    private String membership(UUID course) { return "/api/courses/" + course + "/membership"; }
    private void row(String status) { jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status,resume_lesson_id) VALUES (?,?,?,?,?)", ENROLLMENT, LEARNER, COURSE, status, LESSON); }

    @Test void anonymousCatalogueSearchFilteringPagingAndPublishedSyllabus() throws Exception {
        mvc.perform(get("/api/courses").param("size", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].title").value("Alpha")).andExpect(jsonPath("$.content[0].instructorDisplayName").value("FE07 Instructor"));
        mvc.perform(get("/api/courses").param("q", "BASICS").param("prerequisites", "described")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(SECOND.toString()));
        mvc.perform(get("/api/courses").param("q", "100%_").param("prerequisites", "none")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/courses").param("q", "%_")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/courses").param("page", "9").param("size", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/courses/" + COURSE)).andExpect(status().isOk()).andExpect(jsonPath("$.syllabus", hasSize(1))).andExpect(jsonPath("$.syllabus[0].lessons", hasSize(1))).andExpect(content().string(not(containsString("Secret")))).andExpect(jsonPath("$.adaptiveSettings").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings = {"size=0", "size=101", "page=-1", "page=1.0", "sort=title", "prerequisites=invalid", "q=a&q=b"})
    void invalidCatalogueQueriesAre400(String query) throws Exception { mvc.perform(get("/api/courses?" + query)).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json")); }
    @ParameterizedTest
    @CsvSource({"107374181,20", "107374182,20", "107374183,20", "2147483646,1", "2147483647,1", "2147483647,100"})
    void largeCataloguePagesPreserveCountsFiltersAndAcceptedMetadata(int page, int size) throws Exception {
        mvc.perform(get("/api/courses").param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value((2 + size - 1) / size));
        mvc.perform(get("/api/courses").param("page", Integer.toString(page)).param("size", Integer.toString(size))
                        .param("q", "BASICS").param("prerequisites", "described"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/courses").param("page", Integer.toString(page)).param("size", Integer.toString(size))
                        .param("q", "no matching course"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }
    @Test void longSearchIs400AndInstructorNameDoesNotDependOnAccountLock() throws Exception {
        mvc.perform(get("/api/courses/" + COURSE).param("unsupported", "value")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/courses").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE public.users SET account_status='locked' WHERE id=?", OWNER);
        mvc.perform(get("/api/courses/" + COURSE)).andExpect(status().isOk()).andExpect(jsonPath("$.instructorDisplayName").value("FE07 Instructor"));
    }
    @Test void authenticationAndRolePrecedeResourceOrInputAndUnknownRoutesRemainDenied() throws Exception {
        mvc.perform(put(membership(COURSE))).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(put(membership(HIDDEN)).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (var actor : List.of(OWNER, ADMIN)) {
            mvc.perform(as(put("/api/courses/not-a-uuid/membership"), actor)).andExpect(status().isForbidden());
            mvc.perform(as(get("/api/me/courses"), actor)).andExpect(status().isForbidden());
        }
        mvc.perform(as(put("/api/courses/not-a-uuid/membership"), LEARNER)).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(as(post("/api/courses/" + COURSE + "/enroll"), LEARNER)).andExpect(status().isForbidden());
        jdbc.update("UPDATE public.users SET account_status='locked' WHERE id=?", LEARNER);
        mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.enrollments WHERE course_id=?", Integer.class, COURSE));
    }
    @Test void hiddenAndMissingAreIndistinguishableAndDoNotMutate() throws Exception {
        for (var id : List.of(HIDDEN, id("missing"))) {
            mvc.perform(get("/api/courses/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
            for (var request : List.of(get(membership(id)), put(membership(id)), delete(membership(id))))
                mvc.perform(as(request, LEARNER)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
        }
    }
    @Test void repeatsConvergeAndLeaveRejoinPreservesCanonicalRowAndHistory() throws Exception {
        mvc.perform(as(get(membership(COURSE)), LEARNER)).andExpect(jsonPath("$.status").value("none"));
        mvc.perform(as(delete(membership(COURSE)), LEARNER)).andExpect(jsonPath("$.status").value("none"));
        row("completed");
        jdbc.update("INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (?,?,?,true,false)", PROGRESS, ENROLLMENT, LESSON);
        for (int i=0;i<2;i++) mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed"));
        for (int i=0;i<2;i++) mvc.perform(as(delete(membership(COURSE)), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("left"));
        for (int i=0;i<2;i++) mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        assertEquals(ENROLLMENT, jdbc.queryForObject("SELECT id FROM public.enrollments WHERE user_id=? AND course_id=?", UUID.class, LEARNER, COURSE));
        assertEquals(LESSON, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT completed FROM public.lesson_progress WHERE id=?", Boolean.class, PROGRESS)));
    }
    @Test void myCoursesUsesActorStatusPublishedVisibilityCountsAndOrder() throws Exception {
        row("active");
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'left'),(?,?,?,'active'),(?,?,?,'active')", id("left"), LEARNER, SECOND, id("concealed"), LEARNER, HIDDEN, id("other-enrolment"), OTHER, SECOND);
        mvc.perform(as(get("/api/me/courses"), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(COURSE.toString())).andExpect(jsonPath("$.content[0].userId").doesNotExist());
        mvc.perform(as(get("/api/me/courses").param("status", "all").param("size", "1").param("page", "1"), LEARNER)).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].status").value("left"));
        mvc.perform(as(get("/api/me/courses").param("status", "left"), LEARNER)).andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(SECOND.toString()));
        mvc.perform(as(get("/api/me/courses").param("status", "unknown"), LEARNER)).andExpect(status().isBadRequest());
    }
    @Test void concurrentInitialJoinsCreateExactlyOneRow() throws Exception { concurrentMutations(true); }
    @Test void concurrentLeavesConvergeWithoutDeletingTheRow() throws Exception {
        row("active"); concurrentMutations(false);
        assertEquals("left", jdbc.queryForObject("SELECT status FROM public.enrollments WHERE id=?", String.class, ENROLLMENT));
    }
    private void concurrentMutations(boolean joining) throws Exception {
        var ready = new CountDownLatch(6); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = java.util.stream.IntStream.range(0,6).mapToObj(i -> pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return mvc.perform(as(joining ? put(membership(COURSE)) : delete(membership(COURSE)), LEARNER)).andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var future : futures) assertEquals(200, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.enrollments WHERE user_id=? AND course_id=?", Integer.class, LEARNER, COURSE));
    }
    @Test void transactionFailureRollsBackMembershipInsertion() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            try { mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(status().isOk()); }
            catch (Exception failure) { throw new AssertionError(failure); }
            throw new IllegalStateException("Test rollback");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.enrollments WHERE user_id=? AND course_id=?", Integer.class, LEARNER, COURSE));
    }
    @Test void actualLoginBindsExistingCredentialsJwtAndPublicUserWithoutLeakingCredentials() throws Exception {
        String password = UUID.randomUUID().toString();
        String hash = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14).encode(password);
        jdbc.update("UPDATE public.users SET password_hash=? WHERE id=?", hash, LEARNER);
        String body = "{\"email\":\"" + LEARNER + "@example.test\",\"password\":\"" + password + "\"}";
        String response = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(LEARNER.toString())).andExpect(jsonPath("$.user.role").value("Learner"))
                .andExpect(jsonPath("$.user.email").doesNotExist()).andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
        String token = tree.get("accessToken").asString(); assertEquals(LEARNER, tokens.parseSubject(token));
        mvc.perform(get("/api/me/courses").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        jdbc.update("UPDATE public.users SET account_status='locked' WHERE id=?", LEARNER);
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":5,\"password\":\"x\"}")).andExpect(status().isBadRequest());
    }
    @Test void corsAllowsSelectedMembershipMethodsWithoutCookies() throws Exception {
        mvc.perform(options(membership(COURSE)).header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "PUT").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT"))).andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
    @Test void nativeLongOffsetQueryRetainsPublishedFiltersStableOrderAndBounds() {
        var rows = catalogueRows.findPublishedAtOffset("BASICS", "any", 1, 1L);
        assertEquals(List.of(SECOND), rows.stream().map(row -> row.getId()).toList());
        assertEquals(List.of(SECOND), catalogueRows.findPublishedAtOffset("BASICS", "described", 20, 0L)
                .stream().map(row -> row.getId()).toList());
        assertTrue(catalogueRows.findPublishedAtOffset("", "any", 20, 2147483660L).isEmpty());
        assertEquals(2, catalogueRows.countPublishedCatalogue());
        assertEquals(1, catalogueRows.countPublishedSearch("BASICS", "described"));
        assertEquals(0, catalogueRows.countPublishedSearch("Secret", "any"));
    }

    @ParameterizedTest @ValueSource(strings = {"a b", " ", " prefix", "suffix ", "😀 "})
    void nulSearchIsRejectedBeforeAnyCatalogueSql(String query) throws Exception {
        var statements = new java.util.ArrayList<String>(); INPUT_SQL.set(statements);
        try {
            mvc.perform(get("/api/courses").param("q", query)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("about:blank")).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("The request is invalid."));
            assertTrue(statements.isEmpty(), "Invalid search must not reach catalogue persistence");
        } finally { INPUT_SQL.remove(); }
    }
    @ParameterizedTest @ValueSource(strings = {"café", "東京", "😀", "Учимся", "100%_"})
    void validUnicodeSearchRemainsLiteralAndFindsPublishedContent(String query) throws Exception {
        jdbc.update("UPDATE public.courses SET title=? WHERE id=?", query, COURSE);
        mvc.perform(get("/api/courses").param("q", query)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(COURSE.toString()));
    }
    static java.util.stream.Stream<Arguments> utf16SearchBoundaries() {
        return java.util.stream.Stream.of(Arguments.of("😀".repeat(50), 200), Arguments.of("😀".repeat(51), 400),
                Arguments.of("a".repeat(100), 200), Arguments.of("a".repeat(101), 400),
                Arguments.of("a" + "😀".repeat(49) + "b", 200), Arguments.of("a" + "😀".repeat(49) + "bc", 400),
                Arguments.of("  " + "😀".repeat(50) + "  ", 200));
    }
    @ParameterizedTest @MethodSource("utf16SearchBoundaries")
    void searchUsesTheApprovedTrimmedUtf16UnitLimit(String query, int expected) throws Exception {
        var statements = new java.util.ArrayList<String>(); INPUT_SQL.set(statements);
        try {
            mvc.perform(get("/api/courses").param("q", query)).andExpect(status().is(expected));
            assertEquals(expected == 400, statements.isEmpty());
        } finally { INPUT_SQL.remove(); }
    }
    static java.util.stream.Stream<Arguments> malformedMembershipIds() {
        return java.util.stream.Stream.of("GET", "PUT", "DELETE").flatMap(method -> java.util.stream.Stream.of(
                "0-0-4000-8000-2", "00000000-0000-4000-8000-2", "+0-0-4000-8000-2", "000000000-0000-4000-8000-000000000002")
                .map(value -> Arguments.of(method, value)));
    }
    @ParameterizedTest @MethodSource("malformedMembershipIds")
    void malformedCourseSyntaxCannotReadOrMutateCanonicalMembership(String method, String value) throws Exception {
        var canonical = UUID.fromString("00000000-0000-4000-8000-000000000002");
        try {
            jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES (?,?,'Canonical','UUID fixture','published',false)", canonical, OWNER);
            jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,?)", id("strict-uuid-membership"), LEARNER, canonical, method.equals("PUT") ? "left" : "active");
            var before = jdbc.queryForList("SELECT * FROM public.enrollments WHERE user_id=? AND course_id=?", LEARNER, canonical);
            mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            org.springframework.http.HttpMethod.valueOf(method), "/api/courses/" + value + "/membership"), LEARNER))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("The request is invalid."));
            assertEquals(before, jdbc.queryForList("SELECT * FROM public.enrollments WHERE user_id=? AND course_id=?", LEARNER, canonical));
            mvc.perform(get("/api/courses/" + value)).andExpect(status().isBadRequest());
        } finally {
            jdbc.update("DELETE FROM public.enrollments WHERE course_id=?", canonical);
            jdbc.update("DELETE FROM public.courses WHERE id=?", canonical);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void authenticationAndRolePrecedeMalformedUuidValidation(String method) throws Exception {
        String path = "/api/courses/0-0-4000-8000-2/membership";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized());
        for (var actor : List.of(OWNER, ADMIN)) mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path), actor)).andExpect(status().isForbidden());
    }
    @Test void fullUpperAndMixedCaseUuidsPreserveIdentityAndHiddenResourceRules() throws Exception {
        String upper = COURSE.toString().toUpperCase(java.util.Locale.ROOT);
        var mixed = new StringBuilder(COURSE.toString());
        for (int index = 0; index < mixed.length(); index += 2) mixed.setCharAt(index, Character.toUpperCase(mixed.charAt(index)));
        for (String value : List.of(upper, mixed.toString())) {
            mvc.perform(get("/api/courses/" + value)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(COURSE.toString()));
            mvc.perform(as(get("/api/courses/" + value + "/membership"), LEARNER)).andExpect(status().isOk());
            mvc.perform(as(put("/api/courses/" + value + "/membership"), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
            mvc.perform(as(delete("/api/courses/" + value + "/membership"), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("left"));
        }
        for (String value : List.of(HIDDEN.toString().toUpperCase(java.util.Locale.ROOT), "00000000-0000-0000-0000-000000000000")) {
            mvc.perform(get("/api/courses/" + value)).andExpect(status().isNotFound());
            for (var request : List.of(get("/api/courses/" + value + "/membership"), put("/api/courses/" + value + "/membership"), delete("/api/courses/" + value + "/membership")))
                mvc.perform(as(request, LEARNER)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
        }
    }

}
