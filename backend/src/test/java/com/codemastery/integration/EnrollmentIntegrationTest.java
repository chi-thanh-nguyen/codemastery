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
        jdbc.update("DELETE FROM public.lesson_progress WHERE enrollment_id IN (SELECT id FROM public.enrollments WHERE course_id IN (?,?,?))", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.enrollments WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id IN (?,?,?))", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.chapters WHERE course_id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.courses WHERE id IN (?,?,?)", COURSE, SECOND, HIDDEN);
        jdbc.update("DELETE FROM public.refresh_tokens WHERE session_id IN (SELECT id FROM public.refresh_sessions WHERE user_id IN (?,?,?,?))", OWNER, LEARNER, OTHER, ADMIN);
        jdbc.update("DELETE FROM public.refresh_sessions WHERE user_id IN (?,?,?,?)", OWNER, LEARNER, OTHER, ADMIN);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?)", OWNER, LEARNER, OTHER, ADMIN);
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID actor) { return request.header("Authorization", "Bearer " + tokens.createToken(actor, refreshSession(actor))); }
    /** ADR014: access JWTs are bound to an unrevoked refresh session of the same account. */
    private UUID refreshSession(UUID actor) { UUID session = UUID.randomUUID(); jdbc.update("INSERT INTO public.refresh_sessions (id,user_id,created_at,expires_at) SELECT ?, id, now(), now() + interval '1 hour' FROM public.users WHERE id = ?", session, actor); return session; }
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
    /** CO07.status: in-progress (stored ACTIVE) versus completed (stored COMPLETED, set by CT22.progress) Courses. */
    @Test void myCoursesDistinguishesInProgressFromCompletedCoursesByStoredCt22Status() throws Exception {
        row("active");
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'completed'),(?,?,?,'completed'),(?,?,?,'completed')",
                id("completed"), LEARNER, SECOND, id("hidden-completed"), LEARNER, HIDDEN, id("other-completed"), OTHER, COURSE);
        // The default joined view lists both, in title order, each with its own status; the closed payload gains no progress fields.
        mvc.perform(as(get("/api/me/courses"), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(contains(COURSE.toString(), SECOND.toString())))
                .andExpect(jsonPath("$.content[*].status").value(contains("active", "completed")))
                .andExpect(jsonPath("$.content[0].progressPercentage").doesNotExist()).andExpect(jsonPath("$.content[0].isComplete").doesNotExist());
        mvc.perform(as(get("/api/me/courses").param("status", "active"), LEARNER)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(COURSE.toString())).andExpect(jsonPath("$.content[0].status").value("active"));
        mvc.perform(as(get("/api/me/courses").param("status", "completed"), LEARNER)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(SECOND.toString())).andExpect(jsonPath("$.content[0].status").value("completed"));
        // Each learner sees only their own statuses.
        mvc.perform(as(get("/api/me/courses").param("status", "completed"), OTHER)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(COURSE.toString()));
        mvc.perform(as(get("/api/me/courses").param("status", "active"), OTHER)).andExpect(jsonPath("$.totalElements").value(0));
        // FE07 membership semantics are unchanged: joining a completed Course keeps it completed, leaving moves it to
        // left, and rejoining makes it in progress again without recalculating completion.
        mvc.perform(as(put(membership(SECOND)), LEARNER)).andExpect(jsonPath("$.status").value("completed"));
        mvc.perform(as(delete(membership(SECOND)), LEARNER)).andExpect(jsonPath("$.status").value("left"));
        mvc.perform(as(get("/api/me/courses").param("status", "completed"), LEARNER)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get("/api/me/courses"), LEARNER)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(COURSE.toString()));
        mvc.perform(as(put(membership(SECOND)), LEARNER)).andExpect(jsonPath("$.status").value("active"));
        mvc.perform(as(get("/api/me/courses").param("status", "active"), LEARNER))
                .andExpect(jsonPath("$.content[*].id").value(contains(COURSE.toString(), SECOND.toString())));
        // A hidden Course is absent from every view while its stored completed status is retained.
        mvc.perform(as(get("/api/me/courses").param("status", "all"), LEARNER)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(content().string(not(containsString(HIDDEN.toString()))));
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM public.enrollments WHERE id=?", String.class, id("hidden-completed")));
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


    // ---- CO08: CT22.progress / CT22.resume (ADR006 §§3, 4, 7.2) against real PostgreSQL, MVC and security

    private static final UUID L2 = id("lesson-2"), L3 = id("lesson-3"), SECOND_CHAPTER = id("second-chapter"), SECOND_LESSON = id("second-lesson");
    private static final UUID HIDDEN_CHAPTER = id("hidden-chapter"), HIDDEN_COURSE_LESSON = id("hidden-course-lesson");
    private String completion(UUID course, UUID lesson) { return "/api/courses/" + course + "/lessons/" + lesson + "/completion"; }
    private String progressPath(UUID course) { return "/api/courses/" + course + "/progress"; }
    private String resumePath(UUID course) { return "/api/courses/" + course + "/resume"; }
    private static final String DONE = "{\"completed\":true}";
    private static String visitBody(UUID lesson) { return "{\"lessonId\":\"" + lesson + "\"}"; }
    private org.springframework.test.web.servlet.ResultActions complete(UUID actor, UUID course, UUID lesson) throws Exception {
        return mvc.perform(as(put(completion(course, lesson)), actor).contentType("application/json").content(DONE));
    }
    private org.springframework.test.web.servlet.ResultActions visit(UUID actor, UUID course, UUID lesson) throws Exception {
        return mvc.perform(as(put(resumePath(course)), actor).contentType("application/json").content(visitBody(lesson)));
    }
    /** Canonical R for COURSE: LESSON (ch1 p1), L2 (ch1 p3), L3 (ch2 p1); HIDDEN_LESSON (ch1 p2) is excluded. */
    private void moreLessons() {
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,'Third in chapter one',3,'published'),(?,?,'First in chapter two',1,'published')", L2, CHAPTER, L3, EMPTY_CHAPTER);
    }
    private void secondCourseLesson() {
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,'Second chapter',1)", SECOND_CHAPTER, SECOND);
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) VALUES (?,?,'Second lesson',1,'published')", SECOND_LESSON, SECOND_CHAPTER);
    }
    private String storedStatus() { return jdbc.queryForObject("SELECT status FROM public.enrollments WHERE id=?", String.class, ENROLLMENT); }
    private int progressRows() { return jdbc.queryForObject("SELECT count(*) FROM public.lesson_progress p JOIN public.enrollments e ON e.id=p.enrollment_id WHERE e.course_id=?", Integer.class, COURSE); }

    @Test void v009AddsOneNullableTimestamptzWithoutDefaultOrBackfill() {
        var column = jdbc.queryForMap("SELECT data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema='public' AND table_name='enrollments' AND column_name='last_visited_at'");
        assertEquals("timestamp with time zone", column.get("data_type")); assertEquals("YES", column.get("is_nullable")); assertNull(column.get("column_default"));
        assertEquals(List.of("id", "user_id", "course_id", "status", "resume_lesson_id", "last_visited_at"),
                jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='enrollments' ORDER BY ordinal_position", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE script='V009__add_enrollment_last_visited_at.sql' AND success", Integer.class));
        row("active");
        assertNull(jdbc.queryForObject("SELECT last_visited_at FROM public.enrollments WHERE id=?", java.time.OffsetDateTime.class, ENROLLMENT));
    }

    @Test void progressCompletionAndReconciliationFollowTheExactCt22Rules() throws Exception {
        moreLessons(); row("active");
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.courseId").value(COURSE.toString())).andExpect(jsonPath("$.title").value("Alpha"))
                .andExpect(jsonPath("$.membershipStatus").value("active")).andExpect(jsonPath("$.completedLessons").value(0))
                .andExpect(jsonPath("$.totalLessons").value(3)).andExpect(jsonPath("$.progressPercentage").value(0))
                .andExpect(jsonPath("$.isComplete").value(false)).andExpect(jsonPath("$.nextLessonId").value(LESSON.toString()))
                .andExpect(jsonPath("$.lessons[*].lessonId").value(contains(LESSON.toString(), L2.toString(), L3.toString())))
                .andExpect(jsonPath("$.lessons[0].completed").value(false)).andExpect(jsonPath("$.lessons[0].masterySkipped").value(false))
                .andExpect(content().string(not(containsString(HIDDEN_LESSON.toString()))));
        complete(LEARNER, COURSE, LESSON).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.completedLessons").value(1)).andExpect(jsonPath("$.progressPercentage").value(33))
                .andExpect(jsonPath("$.nextLessonId").value(L2.toString()));
        // A future approved skip producer writes completed+mastery_skipped; manual completion preserves it.
        jdbc.update("INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (?,?,?,true,true)", PROGRESS, ENROLLMENT, L2);
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(jsonPath("$.completedLessons").value(2))
                .andExpect(jsonPath("$.progressPercentage").value(66)).andExpect(jsonPath("$.lessons[1].masterySkipped").value(true))
                .andExpect(jsonPath("$.nextLessonId").value(L3.toString()));
        for (int i = 0; i < 2; i++) complete(LEARNER, COURSE, L2).andExpect(status().isOk()).andExpect(jsonPath("$.completedLessons").value(2));
        assertEquals(List.of(true), jdbc.queryForList("SELECT mastery_skipped FROM public.lesson_progress WHERE enrollment_id=? AND lesson_id=?", Boolean.class, ENROLLMENT, L2));
        complete(LEARNER, COURSE, L3).andExpect(jsonPath("$.progressPercentage").value(100)).andExpect(jsonPath("$.isComplete").value(true))
                .andExpect(jsonPath("$.nextLessonId").value(nullValue())).andExpect(jsonPath("$.membershipStatus").value("completed"));
        assertEquals("completed", storedStatus());
        assertEquals(3, progressRows());
        // CO07: My Courses now lists the Course as completed, not in progress.
        mvc.perform(as(get("/api/me/courses").param("status", "completed"), LEARNER)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(COURSE.toString())).andExpect(jsonPath("$.content[0].status").value("completed"));
        mvc.perform(as(get("/api/me/courses").param("status", "active"), LEARNER)).andExpect(jsonPath("$.totalElements").value(0));
        // CT21 publication (simulated) adds an incomplete Lesson: reads never rewrite stored status.
        jdbc.update("UPDATE public.lessons SET publication_status='published' WHERE id=?", HIDDEN_LESSON);
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(jsonPath("$.isComplete").value(false))
                .andExpect(jsonPath("$.membershipStatus").value("completed")).andExpect(jsonPath("$.nextLessonId").value(HIDDEN_LESSON.toString()));
        assertEquals("completed", storedStatus());
        // The next qualifying completion reconciles inside the Course unit.
        complete(LEARNER, COURSE, LESSON).andExpect(jsonPath("$.membershipStatus").value("active")).andExpect(jsonPath("$.progressPercentage").value(75));
        assertEquals("active", storedStatus());
        mvc.perform(as(get("/api/me/courses").param("status", "active"), LEARNER)).andExpect(jsonPath("$.content[0].id").value(COURSE.toString()))
                .andExpect(jsonPath("$.content[0].status").value("active"));
        complete(LEARNER, COURSE, HIDDEN_LESSON).andExpect(jsonPath("$.membershipStatus").value("completed")).andExpect(jsonPath("$.isComplete").value(true));
        // Manual completion never writes mastery, evidence or recommendations.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.learner_skill_masteries WHERE user_id=?", Integer.class, LEARNER));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.path_recommendations WHERE enrollment_id=?", Integer.class, ENROLLMENT));
    }

    @Test void zeroLessonCourseReportsZeroAndCannotBeCompletedVacuously() throws Exception {
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'completed')", id("empty-enrolment"), LEARNER, SECOND);
        mvc.perform(as(get(progressPath(SECOND)), LEARNER)).andExpect(status().isOk()).andExpect(jsonPath("$.totalLessons").value(0))
                .andExpect(jsonPath("$.progressPercentage").value(0)).andExpect(jsonPath("$.isComplete").value(false))
                .andExpect(jsonPath("$.nextLessonId").value(nullValue())).andExpect(jsonPath("$.lessons", hasSize(0)))
                .andExpect(jsonPath("$.membershipStatus").value("completed"));
        mvc.perform(as(get(resumePath(SECOND)), LEARNER)).andExpect(jsonPath("$.resumeLessonId").value(nullValue())).andExpect(jsonPath("$.nextLessonId").value(nullValue()));
    }

    @Test void storedMembershipAndComputedCompletionStayDistinctAcrossLeaveAndRejoin() throws Exception {
        row("active"); complete(LEARNER, COURSE, LESSON).andExpect(jsonPath("$.membershipStatus").value("completed"));
        mvc.perform(as(delete(membership(COURSE)), LEARNER)).andExpect(jsonPath("$.status").value("left"));
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(status().isForbidden());
        complete(LEARNER, COURSE, LESSON).andExpect(status().isForbidden());
        mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(jsonPath("$.status").value("active"));
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(jsonPath("$.membershipStatus").value("active"))
                .andExpect(jsonPath("$.isComplete").value(true)).andExpect(jsonPath("$.progressPercentage").value(100));
        assertEquals("active", storedStatus());
        mvc.perform(as(get("/api/me/courses").param("status", "active"), LEARNER)).andExpect(jsonPath("$.content[0].status").value("active"));
    }

    @Test void completionAdmissionPrecedenceConcealmentAndNoDeniedWrites() throws Exception {
        moreLessons();
        mvc.perform(put(completion(COURSE, LESSON)).contentType("application/json").content(DONE)).andExpect(status().isUnauthorized());
        for (var actor : List.of(OWNER, ADMIN)) {
            mvc.perform(as(put("/api/courses/not-a-uuid/lessons/x/completion"), actor).contentType("text/plain").content("x")).andExpect(status().isForbidden());
            mvc.perform(as(get("/api/me/course-progress").param("size", "0"), actor)).andExpect(status().isForbidden());
            mvc.perform(as(get("/api/me/resume"), actor)).andExpect(status().isForbidden());
            mvc.perform(as(get(progressPath(COURSE)), actor)).andExpect(status().isForbidden());
        }
        complete(LEARNER, COURSE, LESSON).andExpect(status().isForbidden()); // no membership
        mvc.perform(as(put(completion(COURSE, LESSON)), LEARNER).contentType("application/json").content("{\"completed\":false}")).andExpect(status().isForbidden());
        row("left");
        complete(LEARNER, COURSE, LESSON).andExpect(status().isForbidden());
        jdbc.update("UPDATE public.enrollments SET status='active' WHERE id=?", ENROLLMENT);
        mvc.perform(as(put("/api/courses/" + COURSE + "/lessons/0-0-4000-8000-2/completion"), LEARNER).contentType("application/json").content(DONE)).andExpect(status().isBadRequest());
        for (var body : List.of("{\"completed\":false}", "{\"completed\":true,\"masterySkipped\":true}", "{\"completed\":true,\"enrollmentId\":\"" + ENROLLMENT + "\"}", "{}", "{\"completed\":true,\"completed\":true}"))
            mvc.perform(as(put(completion(COURSE, LESSON)), LEARNER).contentType("application/json").content(body)).andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(as(put(completion(COURSE, LESSON)), LEARNER).contentType("text/plain").content(DONE)).andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(as(put(completion(COURSE, LESSON)), LEARNER).content(DONE)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(as(put(completion(COURSE, LESSON)), LEARNER).param("learnerId", OTHER.toString()).contentType("application/json").content(DONE)).andExpect(status().isBadRequest());
        // Hidden Lesson, foreign Lesson, hidden Course and missing Course are concealed alike.
        complete(LEARNER, COURSE, HIDDEN_LESSON).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("The requested resource is unavailable."));
        secondCourseLesson();
        complete(LEARNER, COURSE, SECOND_LESSON).andExpect(status().isNotFound());
        complete(LEARNER, HIDDEN, LESSON).andExpect(status().isNotFound());
        complete(LEARNER, id("missing"), LESSON).andExpect(status().isNotFound());
        assertEquals(0, progressRows());
        assertEquals("active", storedStatus());
        jdbc.update("UPDATE public.users SET account_status='locked' WHERE id=?", LEARNER);
        complete(LEARNER, COURSE, LESSON).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(status().isUnauthorized());
        assertEquals(0, progressRows());
    }

    @Test void myCourseProgressListsOnlySelfActivePublishedCoursesInTitleOrderWithExactCounts() throws Exception {
        moreLessons(); secondCourseLesson(); row("active");
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active'),(?,?,?,'active'),(?,?,?,'active')",
                id("second-enrolment"), LEARNER, SECOND, id("hidden-enrolment"), LEARNER, HIDDEN, id("other-enrolment"), OTHER, COURSE);
        complete(LEARNER, COURSE, L2).andExpect(status().isOk());
        complete(OTHER, COURSE, LESSON).andExpect(status().isOk());
        mvc.perform(as(get("/api/me/course-progress"), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].courseId").value(COURSE.toString())).andExpect(jsonPath("$.content[0].title").value("Alpha"))
                .andExpect(jsonPath("$.content[0].completedLessons").value(1)).andExpect(jsonPath("$.content[0].totalLessons").value(3))
                .andExpect(jsonPath("$.content[0].progressPercentage").value(33)).andExpect(jsonPath("$.content[0].nextLessonId").value(LESSON.toString()))
                .andExpect(jsonPath("$.content[0].membershipStatus").value("active")).andExpect(jsonPath("$.content[0].lessons").doesNotExist())
                .andExpect(jsonPath("$.content[1].courseId").value(SECOND.toString())).andExpect(jsonPath("$.content[1].progressPercentage").value(0));
        mvc.perform(as(get("/api/me/course-progress").param("page", "1").param("size", "1"), LEARNER)).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].courseId").value(SECOND.toString())).andExpect(jsonPath("$.totalPages").value(2));
        complete(LEARNER, SECOND, SECOND_LESSON).andExpect(jsonPath("$.membershipStatus").value("completed"));
        mvc.perform(as(get("/api/me/course-progress"), LEARNER)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courseId").value(COURSE.toString()));
        for (var query : List.of("size=0", "size=101", "page=-1", "status=active", "page=1&page=2"))
            mvc.perform(as(get("/api/me/course-progress?" + query), LEARNER)).andExpect(status().isBadRequest());
        // The closed My Courses payload is unchanged.
        mvc.perform(as(get("/api/me/courses"), LEARNER)).andExpect(jsonPath("$.content[0].progressPercentage").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").value("active"));
    }

    @Test void perCourseResumeRecordsExplicitVisitsOnlyAndRetainsHistory() throws Exception {
        moreLessons(); jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')", ENROLLMENT, LEARNER, COURSE);
        mvc.perform(as(get(resumePath(COURSE)), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resumeLessonId").value(nullValue())).andExpect(jsonPath("$.nextLessonId").value(LESSON.toString()));
        visit(LEARNER, COURSE, L2).andExpect(status().isOk()).andExpect(jsonPath("$.resumeLessonId").value(L2.toString()));
        visit(LEARNER, COURSE, LESSON).andExpect(status().isOk()).andExpect(jsonPath("$.resumeLessonId").value(LESSON.toString()))
                .andExpect(jsonPath("$.nextLessonId").value(LESSON.toString()));
        var first = jdbc.queryForObject("SELECT last_visited_at FROM public.enrollments WHERE id=?", java.time.OffsetDateTime.class, ENROLLMENT);
        assertNotNull(first);
        // Reads, completion and leave/rejoin never write the pointer or the time.
        mvc.perform(as(get(resumePath(COURSE)), LEARNER)).andExpect(jsonPath("$.resumeLessonId").value(LESSON.toString()));
        mvc.perform(as(get(progressPath(COURSE)), LEARNER)).andExpect(status().isOk());
        complete(LEARNER, COURSE, L3).andExpect(status().isOk());
        mvc.perform(as(delete(membership(COURSE)), LEARNER)).andExpect(status().isOk());
        visit(LEARNER, COURSE, LESSON).andExpect(status().isForbidden());
        mvc.perform(as(put(membership(COURSE)), LEARNER)).andExpect(status().isOk());
        assertEquals(first, jdbc.queryForObject("SELECT last_visited_at FROM public.enrollments WHERE id=?", java.time.OffsetDateTime.class, ENROLLMENT));
        assertEquals(LESSON, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
        // Repeating the same visit refreshes recency only.
        visit(LEARNER, COURSE, LESSON).andExpect(status().isOk());
        assertTrue(jdbc.queryForObject("SELECT last_visited_at > ? FROM public.enrollments WHERE id=?", Boolean.class, first, ENROLLMENT));
        // Invalid targets are denied and leave the pointer unchanged.
        secondCourseLesson();
        visit(LEARNER, COURSE, HIDDEN_LESSON).andExpect(status().isNotFound());
        visit(LEARNER, COURSE, SECOND_LESSON).andExpect(status().isNotFound());
        visit(OWNER, COURSE, LESSON).andExpect(status().isForbidden());
        mvc.perform(as(put(resumePath(COURSE)), LEARNER).contentType("application/json").content("{\"lessonId\":\"" + L2 + "\",\"visitedAt\":\"2000-01-01T00:00:00Z\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(put(resumePath(COURSE)), LEARNER).contentType("text/plain").content(visitBody(L2))).andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        for (var body : List.of("{}", "{\"lessonId\":null}", "{\"lessonId\":\"not-a-uuid\"}", "{\"lessonId\":\"" + L2 + "\",\"lessonId\":\"" + L2 + "\"}", "[]"))
            mvc.perform(as(put(resumePath(COURSE)), LEARNER).contentType("application/json").content(body)).andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertEquals(LESSON, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
        // Hiding the stored target reads as null plus canonical next, without deleting history.
        jdbc.update("UPDATE public.lessons SET publication_status='hidden' WHERE id=?", LESSON);
        mvc.perform(as(get(resumePath(COURSE)), LEARNER)).andExpect(jsonPath("$.resumeLessonId").value(nullValue()))
                .andExpect(jsonPath("$.nextLessonId").value(L2.toString()));
        assertEquals(LESSON, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
    }

    @Test void accountResumeSelectsTheLatestValidRecordedVisitWithDeterministicTieBreak() throws Exception {
        moreLessons(); secondCourseLesson();
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"position\":null}", true));
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status,resume_lesson_id,last_visited_at) VALUES (?,?,?,'active',?,NULL),(?,?,?,'active',?,NULL)",
                ENROLLMENT, LEARNER, COURSE, LESSON, id("second-enrolment"), LEARNER, SECOND, SECOND_LESSON);
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(content().json("{\"position\":null}", true)); // legacy NULL times
        jdbc.update("UPDATE public.enrollments SET last_visited_at=TIMESTAMPTZ '2026-11-16 01:00:00+00' WHERE id=?", ENROLLMENT);
        jdbc.update("UPDATE public.enrollments SET last_visited_at=TIMESTAMPTZ '2026-11-16 02:00:00.123456+00' WHERE id=?", id("second-enrolment"));
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(jsonPath("$.position.courseId").value(SECOND.toString()))
                .andExpect(jsonPath("$.position.lessonId").value(SECOND_LESSON.toString()))
                .andExpect(jsonPath("$.position.lastVisitedAt").value("2026-11-16T02:00:00.123456Z"));
        jdbc.update("UPDATE public.enrollments SET last_visited_at=TIMESTAMPTZ '2026-11-16 02:00:00.123456+00' WHERE id=?", ENROLLMENT);
        UUID lower = jdbc.queryForObject("SELECT least(?::uuid, ?::uuid)", UUID.class, COURSE, SECOND);
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(jsonPath("$.position.courseId").value(lower.toString()));
        // A real visit is newer than either fixture time and wins regardless of UUID.
        visit(LEARNER, COURSE, L2).andExpect(status().isOk());
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(jsonPath("$.position.courseId").value(COURSE.toString()))
                .andExpect(jsonPath("$.position.lessonId").value(L2.toString()));
        // Inaccessible newest target falls back to the next valid recorded visit; nothing is rewritten.
        jdbc.update("UPDATE public.lessons SET publication_status='hidden' WHERE id=?", L2);
        mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(jsonPath("$.position.courseId").value(SECOND.toString()));
        assertEquals(L2, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
        jdbc.update("UPDATE public.lessons SET publication_status='published' WHERE id=?", L2);
        for (var status : List.of("completed", "left")) {
            jdbc.update("UPDATE public.enrollments SET status=? WHERE id=?", status, ENROLLMENT);
            mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(jsonPath("$.position.courseId").value(SECOND.toString()));
        }
        jdbc.update("UPDATE public.courses SET publication_status='hidden' WHERE id=?", SECOND);
        try {
            mvc.perform(as(get("/api/me/resume"), LEARNER)).andExpect(content().json("{\"position\":null}", true));
            mvc.perform(as(get("/api/me/resume"), OTHER)).andExpect(content().json("{\"position\":null}", true));
            mvc.perform(as(get("/api/me/resume").param("courseId", COURSE.toString()), LEARNER)).andExpect(status().isBadRequest());
        } finally { jdbc.update("UPDATE public.courses SET publication_status='published' WHERE id=?", SECOND); }
    }

    @Test void failureAfterTheWriteRollsBackProgressStatusPointerAndTimeTogether() throws Exception {
        moreLessons(); row("active");
        complete(LEARNER, COURSE, LESSON).andExpect(status().isOk()); complete(LEARNER, COURSE, L2).andExpect(status().isOk());
        var before = jdbc.queryForList("SELECT * FROM public.enrollments WHERE id=?", ENROLLMENT);
        var progressBefore = jdbc.queryForList("SELECT * FROM public.lesson_progress WHERE enrollment_id=? ORDER BY lesson_id", ENROLLMENT);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            try {
                complete(LEARNER, COURSE, L3).andExpect(status().isOk()).andExpect(jsonPath("$.membershipStatus").value("completed"));
                visit(LEARNER, COURSE, L3).andExpect(status().isOk());
            } catch (Exception failure) { throw new AssertionError(failure); }
            throw new IllegalStateException("Test rollback");
        }));
        assertEquals(before, jdbc.queryForList("SELECT * FROM public.enrollments WHERE id=?", ENROLLMENT));
        assertEquals(progressBefore, jdbc.queryForList("SELECT * FROM public.lesson_progress WHERE enrollment_id=? ORDER BY lesson_id", ENROLLMENT));
    }

    @Test void concurrentCompletionsOfOneLessonConvergeOnOneRow() throws Exception {
        row("active");
        var ready = new CountDownLatch(6); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = java.util.stream.IntStream.range(0, 6).mapToObj(i -> pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return complete(LEARNER, COURSE, LESSON).andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var future : futures) assertEquals(200, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, progressRows()); assertEquals("completed", storedStatus());
    }

    enum Interleaving { LEAVE, PUBLISH }

    /** B2-O04: a held Course root blocks the learner write; it then rereads state committed by the holder. */
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(Interleaving.class)
    void completionSerializesOnTheCourseRootAndRereadsCommittedState(Interleaving interleaving) throws Exception {
        moreLessons(); row("active"); secondCourseLesson();
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')", id("second-enrolment"), LEARNER, SECOND);
        complete(LEARNER, COURSE, LESSON).andExpect(status().isOk()); complete(LEARNER, COURSE, L2).andExpect(status().isOk());
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForList("SELECT id FROM public.courses WHERE id=? FOR UPDATE", UUID.class, COURSE);
                locked.countDown();
                try { assertTrue(release.await(20, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                if (interleaving == Interleaving.LEAVE) jdbc.update("UPDATE public.enrollments SET status='left' WHERE id=?", ENROLLMENT);
                else jdbc.update("UPDATE public.lessons SET publication_status='published' WHERE id=?", HIDDEN_LESSON);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var waiting = pool.submit(() -> complete(LEARNER, COURSE, L3).andReturn().getResponse());
            awaitLockWaiters(1);
            // Another Course proceeds independently while this root is held.
            complete(LEARNER, SECOND, SECOND_LESSON).andExpect(status().isOk()).andExpect(jsonPath("$.membershipStatus").value("completed"));
            assertFalse(waiting.isDone());
            release.countDown(); holder.get(20, TimeUnit.SECONDS);
            var response = waiting.get(20, TimeUnit.SECONDS);
            if (interleaving == Interleaving.LEAVE) {
                assertEquals(403, response.getStatus());
                assertEquals(2, progressRows()); assertEquals("left", storedStatus());
            } else {
                assertEquals(200, response.getStatus());
                assertTrue(response.getContentAsString().contains("\"totalLessons\":4"), response.getContentAsString());
                assertTrue(response.getContentAsString().contains("\"membershipStatus\":\"active\""));
                assertEquals("active", storedStatus());
            }
        }
    }

    /** ADR006 §§4, 7.2: a visit waits for the held Course root, then rechecks access against committed state. */
    @Test void visitSerializesOnTheCourseRootAndRechecksTheTargetAfterTheWait() throws Exception {
        moreLessons(); row("active");
        visit(LEARNER, COURSE, LESSON).andExpect(status().isOk());
        var before = jdbc.queryForObject("SELECT last_visited_at FROM public.enrollments WHERE id=?", java.time.OffsetDateTime.class, ENROLLMENT);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForList("SELECT id FROM public.courses WHERE id=? FOR UPDATE", UUID.class, COURSE);
                locked.countDown();
                try { assertTrue(release.await(20, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                jdbc.update("UPDATE public.lessons SET publication_status='hidden' WHERE id=?", L2);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var waiting = pool.submit(() -> visit(LEARNER, COURSE, L2).andReturn().getResponse().getStatus());
            awaitLockWaiters(1);
            assertFalse(waiting.isDone());
            release.countDown(); holder.get(20, TimeUnit.SECONDS);
            assertEquals(404, waiting.get(20, TimeUnit.SECONDS));
        }
        assertEquals(LESSON, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
        assertEquals(before, jdbc.queryForObject("SELECT last_visited_at FROM public.enrollments WHERE id=?", java.time.OffsetDateTime.class, ENROLLMENT));
        // After the wait a still-valid target is written; the last committed visit wins.
        visit(LEARNER, COURSE, L3).andExpect(status().isOk()).andExpect(jsonPath("$.resumeLessonId").value(L3.toString()));
        assertEquals(L3, jdbc.queryForObject("SELECT resume_lesson_id FROM public.enrollments WHERE id=?", UUID.class, ENROLLMENT));
    }

    private void awaitLockWaiters(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Integer.class);
            if (waiting != null && waiting >= expected) return;
            Thread.onSpinWait();
            TimeUnit.MILLISECONDS.sleep(20);
        }
        fail("No session waited on the held Course root lock");
    }

}
