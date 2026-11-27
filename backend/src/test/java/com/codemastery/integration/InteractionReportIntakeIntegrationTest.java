package com.codemastery.integration;

import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * IN06.report through the real filter chain on PostgreSQL: authentication, CT03 admission, target
 * concealment, strict bodies and the stored report (ADR012 sections 6.1, 6.3, 6.5, 12).
 * Fixtures are committed and removed after each test.
 */
@DirtiesContext
class InteractionReportIntakeIntegrationTest extends AbstractIntegrationTest {

    private static final UUID LEARNER = id("learner");
    private static final UUID OUTSIDER = id("outsider");
    private static final UUID INSTRUCTOR = id("instructor");
    private static final UUID OTHER_INSTRUCTOR = id("other-instructor");
    private static final UUID ADMIN = id("admin");
    private static final List<UUID> USERS = List.of(LEARNER, OUTSIDER, INSTRUCTOR, OTHER_INSTRUCTOR, ADMIN);
    private static final UUID COURSE = id("course");
    private static final UUID CHAPTER = id("chapter");
    private static final UUID LESSON = id("lesson");
    private static final UUID SECOND_LESSON = id("second-lesson");
    private static final UUID HIDDEN_LESSON = id("hidden-lesson");
    private static final UUID QUESTION = id("question");
    private static final UUID REPLY = id("reply");
    private static final UUID HIDDEN_QUESTION = id("hidden-question");
    private static final UUID REPLY_UNDER_HIDDEN = id("reply-under-hidden");
    private static final UUID HIDDEN_REPLY = id("hidden-reply");
    private static final UUID OTHER_LESSON_POST = id("other-lesson-post");
    private static final String KEY = key();

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenProvider tokens;

    private MockMvc mvc;

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("in06-report/" + name).getBytes(StandardCharsets.UTF_8));
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
        user(LEARNER, "learner");
        user(OUTSIDER, "learner");
        user(INSTRUCTOR, "instructor");
        user(OTHER_INSTRUCTOR, "instructor");
        user(ADMIN, "admin");
        jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'IN06 report course', 'IN06 report fixture', 'published', false)", COURSE, INSTRUCTOR);
        jdbc.update("INSERT INTO public.chapters (id, course_id, title, position) VALUES (?, ?, 'Chapter', 1)", CHAPTER, COURSE);
        lesson(LESSON, 1, "published");
        lesson(SECOND_LESSON, 2, "published");
        lesson(HIDDEN_LESSON, 3, "hidden");
        jdbc.update("INSERT INTO public.enrollments (id, user_id, course_id, status) VALUES (?, ?, ?, 'active')",
                id("enrolment"), LEARNER, COURSE);
        insertPost(QUESTION, LESSON, null, false);
        insertPost(HIDDEN_QUESTION, LESSON, null, true);
        insertPost(OTHER_LESSON_POST, SECOND_LESSON, null, false);
        insertPost(REPLY, LESSON, QUESTION, false);
        insertPost(REPLY_UNDER_HIDDEN, LESSON, HIDDEN_QUESTION, false);
        insertPost(HIDDEN_REPLY, LESSON, QUESTION, true);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM public.content_reports WHERE reporter_id IN (?,?,?,?,?)", USERS.toArray());
        jdbc.update("DELETE FROM public.discussion_posts WHERE author_id IN (?,?,?,?,?) AND parent_post_id IS NOT NULL", USERS.toArray());
        jdbc.update("DELETE FROM public.discussion_posts WHERE author_id IN (?,?,?,?,?)", USERS.toArray());
        jdbc.update("DELETE FROM public.enrollments WHERE course_id = ?", COURSE);
        jdbc.update("DELETE FROM public.lessons WHERE chapter_id = ?", CHAPTER);
        jdbc.update("DELETE FROM public.chapters WHERE course_id = ?", COURSE);
        jdbc.update("DELETE FROM public.courses WHERE id = ?", COURSE);
        jdbc.update("DELETE FROM public.refresh_tokens WHERE session_id IN "
                + "(SELECT id FROM public.refresh_sessions WHERE user_id IN (?,?,?,?,?))", USERS.toArray());
        jdbc.update("DELETE FROM public.refresh_sessions WHERE user_id IN (?,?,?,?,?)", USERS.toArray());
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?,?)", USERS.toArray());
    }

    private void user(UUID id, String role) {
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-login-fixture', ?, ?, 'active')", id, "in06-" + id + "@example.test", "IN06 " + role, role);
    }

    private void lesson(UUID id, int position, String publication) {
        jdbc.update("INSERT INTO public.lessons (id, chapter_id, title, position, publication_status) VALUES (?, ?, 'Lesson', ?, ?)",
                id, CHAPTER, position, publication);
    }

    private void insertPost(UUID id, UUID lesson, UUID parent, boolean hidden) {
        jdbc.update("INSERT INTO public.discussion_posts (id, lesson_id, author_id, parent_post_id, content, created_at, is_hidden) "
                + "VALUES (?, ?, ?, ?, 'IN06 fixture post', now(), ?)", id, lesson, LEARNER, parent, hidden);
    }

    private UUID refreshSession(UUID actor) {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_sessions (id, user_id, created_at, expires_at) "
                + "SELECT ?, id, now(), now() + interval '1 hour' FROM public.users WHERE id = ?", session, actor);
        return session;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID actor) {
        return request.header("Authorization", "Bearer " + tokens.createToken(actor, refreshSession(actor)));
    }

    private MockHttpServletRequestBuilder json(String path, String body, UUID actor) {
        return as(post(path).contentType(MediaType.APPLICATION_JSON).content(body), actor);
    }

    private static String lessonPath(UUID lesson) {
        return "/api/lessons/" + lesson + "/reports";
    }

    private static String postPath(UUID lesson, UUID post) {
        return "/api/lessons/" + lesson + "/discussion/" + post + "/reports";
    }

    private static final String REASON = "{\"reason\":\"Spam\"}";

    private int reportRows() {
        return jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE reporter_id IN (?,?,?,?,?)",
                Integer.class, USERS.toArray());
    }

    // ---------------------------------------------------------------- admission

    @Test
    void anonymousRequestsAre401() throws Exception {
        mvc.perform(post(lessonPath(LESSON)).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(postPath(LESSON, QUESTION)).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isUnauthorized());
        assertEquals(0, reportRows());
    }

    @Test
    void anAdministratorIs403OnBothOperations() throws Exception {
        mvc.perform(json(lessonPath(LESSON), REASON, ADMIN)).andExpect(status().isForbidden());
        mvc.perform(json(postPath(LESSON, QUESTION), REASON, ADMIN)).andExpect(status().isForbidden());
        assertEquals(0, reportRows());
    }

    @Test
    void callersWithoutLessonAccessGetAConcealed404() throws Exception {
        for (UUID actor : List.of(OUTSIDER, OTHER_INSTRUCTOR)) {
            mvc.perform(json(lessonPath(LESSON), REASON, actor)).andExpect(status().isNotFound());
            mvc.perform(json(postPath(LESSON, QUESTION), REASON, actor)).andExpect(status().isNotFound());
        }
        mvc.perform(json(lessonPath(HIDDEN_LESSON), REASON, LEARNER)).andExpect(status().isNotFound());
        mvc.perform(json(lessonPath(UUID.randomUUID()), REASON, LEARNER)).andExpect(status().isNotFound());
        assertEquals(0, reportRows());
    }

    // ---------------------------------------------------------------- lesson target

    @Test
    void anEnrolledLearnerReportsTheLessonAndTheReasonIsStoredVerbatim() throws Exception {
        mvc.perform(json(lessonPath(LESSON), "{\"reason\":\"  keep\\n  spaces  \"}", LEARNER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.createdAt").exists());

        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM public.content_reports WHERE reporter_id = ?", LEARNER);
        assertEquals(LESSON, stored.get("lesson_id"));
        assertNull(stored.get("discussion_post_id"));
        assertEquals("  keep\n  spaces  ", stored.get("reason"));
        assertEquals("open", stored.get("status"));
        assertNull(stored.get("resolved_at"));
    }

    @Test
    void theOwningInstructorMayReportIncludingAHiddenLessonPreview() throws Exception {
        mvc.perform(json(lessonPath(LESSON), REASON, INSTRUCTOR)).andExpect(status().isCreated());
        mvc.perform(json(lessonPath(HIDDEN_LESSON), REASON, INSTRUCTOR)).andExpect(status().isCreated());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE reporter_id = ?",
                Integer.class, INSTRUCTOR));
    }

    @Test
    void duplicateReportsAreAllowedAndKeepDistinctIds() throws Exception {
        mvc.perform(json(lessonPath(LESSON), REASON, LEARNER)).andExpect(status().isCreated());
        mvc.perform(json(lessonPath(LESSON), REASON, LEARNER)).andExpect(status().isCreated());

        List<UUID> ids = jdbc.queryForList("SELECT id FROM public.content_reports WHERE reporter_id = ?", UUID.class, LEARNER);
        assertEquals(2, ids.size());
        assertNotEquals(ids.get(0), ids.get(1));
    }

    // ---------------------------------------------------------------- post target

    @Test
    void visibleQuestionsAndRepliesCanBeReportedByLearnerAndInstructor() throws Exception {
        mvc.perform(json(postPath(LESSON, QUESTION), REASON, LEARNER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("open"));
        mvc.perform(json(postPath(LESSON, REPLY), REASON, LEARNER)).andExpect(status().isCreated());
        mvc.perform(json(postPath(LESSON, QUESTION), REASON, INSTRUCTOR)).andExpect(status().isCreated());

        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT * FROM public.content_reports WHERE reporter_id = ? AND discussion_post_id = ?", LEARNER, QUESTION);
        assertNull(stored.get("lesson_id"));
        assertEquals("open", stored.get("status"));
        assertEquals(3, reportRows());
    }

    @Test
    void postsThatAreHiddenConcealedOrOfAnotherLessonAre404AndWriteNothing() throws Exception {
        for (UUID target : List.of(HIDDEN_QUESTION, REPLY_UNDER_HIDDEN, HIDDEN_REPLY, UUID.randomUUID())) {
            mvc.perform(json(postPath(LESSON, target), REASON, LEARNER)).andExpect(status().isNotFound());
        }
        mvc.perform(json(postPath(LESSON, OTHER_LESSON_POST), REASON, LEARNER)).andExpect(status().isNotFound());
        mvc.perform(json(postPath(SECOND_LESSON, QUESTION), REASON, LEARNER)).andExpect(status().isNotFound());
        assertEquals(0, reportRows());
    }

    // ---------------------------------------------------------------- input rules and precedence

    @Test
    void malformedPathValuesAre400BeforeAdmission() throws Exception {
        mvc.perform(json("/api/lessons/not-a-uuid/reports", REASON, OUTSIDER))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(json("/api/lessons/" + LESSON + "/discussion/12345/reports", REASON, OUTSIDER))
                .andExpect(status().isBadRequest());
        assertEquals(0, reportRows());
    }

    @Test
    void invalidBodiesAre400WithNoStoreAfterAdmission() throws Exception {
        for (String body : List.of("{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":\"\\u2003\"}", "{}",
                "{\"reason\":1}", "{\"reason\":null}", "{\"reason\":\"x\",\"extra\":\"y\"}", "[]")) {
            mvc.perform(json(lessonPath(LESSON), body, LEARNER))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(json(postPath(LESSON, QUESTION), body, LEARNER)).andExpect(status().isBadRequest());
        }
        mvc.perform(as(post(lessonPath(LESSON)).contentType(MediaType.APPLICATION_JSON), LEARNER))
                .andExpect(status().isBadRequest());
        assertEquals(0, reportRows());
    }

    @Test
    void bodyErrorsNeverPrecedeAnAccessDecision() throws Exception {
        String blank = "{\"reason\":\"  \"}";
        mvc.perform(json(lessonPath(LESSON), blank, OUTSIDER)).andExpect(status().isNotFound());
        mvc.perform(json(lessonPath(LESSON), blank, ADMIN)).andExpect(status().isForbidden());
        mvc.perform(json(postPath(LESSON, HIDDEN_QUESTION), blank, LEARNER)).andExpect(status().isNotFound());
        assertEquals(0, reportRows());
    }

    @Test
    void aNonJsonMediaTypeIs415() throws Exception {
        mvc.perform(as(post(lessonPath(LESSON)).contentType(MediaType.TEXT_PLAIN).content("Spam"), LEARNER))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(as(post(postPath(LESSON, QUESTION)).contentType(MediaType.TEXT_PLAIN).content("Spam"), LEARNER))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, reportRows());
    }
}
