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

    @Autowired com.codemastery.modules.interaction.application.service.DiscussionService discussions;
    @Autowired com.codemastery.modules.interaction.application.service.ContentReportService reports;
    @Autowired com.codemastery.modules.course.application.service.CourseCatalogueService catalogue;
    @Autowired com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository posts;
    @Autowired com.codemastery.modules.interaction.domain.repository.ContentReportRepository reportRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired javax.sql.DataSource dataSource;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

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
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM public.notifications WHERE user_id IN (?,?,?,?,?)", USERS.toArray());
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

    private static String discussionPath(UUID lesson) { return "/api/lessons/" + lesson + "/discussion"; }

    private static void signIn(UUID actor, com.codemastery.modules.auth.domain.model.Role role) {
        var principal = new com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser(actor, role);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private org.springframework.transaction.support.TransactionTemplate tx() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    private int committedCount(String table) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next(); return result.getInt(1);
        }
    }

    private UUID report(UUID post) {
        signIn(LEARNER, com.codemastery.modules.auth.domain.model.Role.LEARNER);
        return (post == null ? reports.reportLesson(LESSON, () -> "reason")
                : reports.reportPost(LESSON, post, () -> "reason")).getId();
    }

    @Test
    void malformedMissingAndDuplicateJsonNeverPrecedesAdmissionOrTargetConcealment() throws Exception {
        for (String path : List.of(lessonPath(LESSON), postPath(LESSON, QUESTION), discussionPath(LESSON))) {
            for (String body : List.of("", "{", "null", "[]", "{\"reason\":\"a\",\"reason\":\"b\"}", "{} {}")) {
                mvc.perform(json(path, body, ADMIN)).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"));
                mvc.perform(json(path, body, OUTSIDER)).andExpect(status().isNotFound());
                mvc.perform(json(path, body, LEARNER)).andExpect(status().isBadRequest());
            }
            mvc.perform(as(post(path).contentType(MediaType.TEXT_PLAIN).content("{"), ADMIN)).andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(json(postPath(LESSON, HIDDEN_QUESTION), "{", LEARNER)).andExpect(status().isNotFound());
        for (String body : List.of("{\"parentPostId\":\"" + HIDDEN_QUESTION + "\",\"content\":1}",
                "{\"parentPostId\":\"" + HIDDEN_QUESTION + "\",\"extra\":true}"))
            mvc.perform(json(discussionPath(LESSON), body, LEARNER)).andExpect(status().isNotFound());
    }

    @Test
    void questionsAndRepliesUseTrustedIdentityPlainTextProjectionsAndQuestionOnlyPagination() throws Exception {
        String path = discussionPath(LESSON);
        mvc.perform(json(path, "{\"content\":\"  <b>Question</b>\\nLine  \"}", LEARNER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(LEARNER.toString()))
                .andExpect(jsonPath("$.authorRole").value("Learner")).andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.content").value("  <b>Question</b>\nLine  "));
        mvc.perform(json(path, "{\"content\":\"Reply\",\"parentPostId\":\"" + QUESTION + "\"}", INSTRUCTOR))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorRole").value("Instructor"));
        // Force ties to exercise PostgreSQL UUID order in both directions.
        jdbc.update("UPDATE public.discussion_posts SET created_at='2026-01-01T00:00:00Z' WHERE lesson_id=?", LESSON);
        var expected = jdbc.queryForList("SELECT id FROM public.discussion_posts WHERE lesson_id=? AND parent_post_id IS NULL AND NOT is_hidden ORDER BY created_at DESC,id DESC", UUID.class, LESSON);
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("size", "1"), LEARNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(expected.getFirst().toString()));
        signIn(LEARNER, com.codemastery.modules.auth.domain.model.Role.LEARNER);
        var distant = discussions.list(LESSON, Integer.MAX_VALUE, 100);
        assertEquals(2, distant.totalElements()); assertEquals(0, distant.content().size());
        var page = discussions.list(LESSON, 0, 20);
        var thread = page.content().stream().filter(q -> q.id().equals(QUESTION)).findFirst().orElseThrow();
        assertEquals(2, thread.replies().size());
        var expectedReplies = jdbc.queryForList("SELECT id FROM public.discussion_posts WHERE parent_post_id=? AND NOT is_hidden ORDER BY created_at,id", UUID.class, QUESTION);
        assertEquals(expectedReplies, thread.replies().stream().map(reply -> reply.id()).toList());
        org.junit.jupiter.api.Assertions.assertTrue(page.content().stream().noneMatch(q -> q.id().equals(HIDDEN_QUESTION)));
    }

    @Test
    void discussionConcealsParentsRejectsDepthAndStrictRepresentations() throws Exception {
        String path = discussionPath(LESSON);
        for (UUID parent : List.of(HIDDEN_QUESTION, REPLY_UNDER_HIDDEN, OTHER_LESSON_POST, UUID.randomUUID()))
            mvc.perform(json(path, "{\"content\":\"x\",\"parentPostId\":\"" + parent + "\"}", LEARNER)).andExpect(status().isNotFound());
        mvc.perform(json(path, "{\"content\":\"x\",\"parentPostId\":\"" + REPLY + "\"}", LEARNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentPostId"));
        for (String body : List.of("{}", "{\"content\":\" \"}", "{\"content\":null}", "{\"content\":1}",
                "{\"content\":\"x\",\"authorId\":\"x\"}", "{\"content\":\"x\",\"content\":\"y\"}", "{\"content\":\"x\",\"parentPostId\":1}", "{\"content\":\"x\"} {}"))
            mvc.perform(json(path, body, LEARNER)).andExpect(status().isBadRequest());
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("size", "0"), ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("page", "0", "1"), LEARNER)).andExpect(status().isBadRequest());
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path), ADMIN)).andExpect(status().isForbidden());
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path), OUTSIDER)).andExpect(status().isNotFound());
    }

    @Test
    void replyNotificationUsesDerivedIdRecipientTemplateAndPublicationConditions() {
        signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
        var reply = discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("Reply", QUESTION));
        var notificationId = com.codemastery.modules.interaction.application.service.NotificationService.derivedId("discussion-reply", reply.id().toString());
        var row = jdbc.queryForMap("SELECT * FROM public.notifications WHERE id=?", notificationId);
        assertEquals(LEARNER, row.get("user_id"));
        assertEquals("New reply to your question in \"" + catalogue.readLessonContext(LESSON).orElseThrow().lessonTitle() + "\".", row.get("message"));
        assertEquals(false, row.get("is_read"));
        signIn(LEARNER, com.codemastery.modules.auth.domain.model.Role.LEARNER);
        discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("Self", QUESTION));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.notifications", Integer.class));
        signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
        jdbc.update("UPDATE public.lessons SET publication_status='hidden' WHERE id=?", LESSON);
        discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("Preview", QUESTION));
        jdbc.update("UPDATE public.lessons SET publication_status='published' WHERE id=?", LESSON);
        jdbc.update("UPDATE public.courses SET publication_status='hidden' WHERE id=?", COURSE);
        discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("Course preview", QUESTION));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM public.notifications", Integer.class));
    }

    @Test
    void replyAndNotificationAreInvisibleBeforeCommitAndRollbackTogether() throws Exception {
        signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
        int initial = committedCount("discussion_posts");
        tx().executeWithoutResult(status -> {
            discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("atomic", QUESTION));
            try { assertEquals(initial, committedCount("discussion_posts")); assertEquals(0, committedCount("notifications")); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
            status.setRollbackOnly();
        });
        assertEquals(initial, committedCount("discussion_posts"));
        assertEquals(0, committedCount("notifications"));
        jdbc.execute("CREATE FUNCTION public.in06_fail_notification() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic notification failure' USING ERRCODE='23514'; END $$");
        jdbc.execute("CREATE TRIGGER in06_fail_notification BEFORE INSERT ON public.notifications FOR EACH ROW EXECUTE FUNCTION public.in06_fail_notification()");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> discussions.create(LESSON,
                    () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("must roll back", QUESTION)));
            assertEquals(initial, committedCount("discussion_posts"));
            assertEquals(0, committedCount("notifications"));
        } finally { jdbc.execute("DROP TRIGGER in06_fail_notification ON public.notifications"); jdbc.execute("DROP FUNCTION public.in06_fail_notification()"); }
    }

    @Test
    void contextIsActorlessBoundedScopedAndIncludesHiddenTitles() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var context = catalogue.readCourseContext(COURSE, java.util.Set.of(LESSON, HIDDEN_LESSON, UUID.randomUUID())).orElseThrow();
        assertEquals(java.util.Set.of(LESSON, HIDDEN_LESSON), context.lessons().keySet());
        assertEquals("hidden", context.lessons().get(HIDDEN_LESSON).publicationStatus());
        assertEquals(COURSE, catalogue.readLessonContext(LESSON).orElseThrow().courseId());
        org.junit.jupiter.api.Assertions.assertTrue(catalogue.readLessonContext(UUID.randomUUID()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(catalogue.readCourseContext(UUID.randomUUID(), java.util.Set.of()).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> catalogue.readCourseContext(COURSE,
                java.util.stream.IntStream.range(0, 101).mapToObj(n -> UUID.randomUUID()).collect(java.util.stream.Collectors.toSet())));
    }

    @Test
    void moderationResolvesDuplicatesPreservesOtherTargetsAndHasTerminalStates() {
        UUID first = report(QUESTION), second = report(QUESTION), other = report(REPLY), lesson = report(null);
        signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
        assertEquals(4, reports.listReportsForModeration(null, 0, 20).totalElements());
        var distant = reports.listReportsForModeration(null, Integer.MAX_VALUE, 100);
        assertEquals(4, distant.totalElements()); assertEquals(0, distant.content().size());
        var decision = tx().execute(status -> reports.hidePostForReport(first));
        assertEquals(java.util.Set.of(first, second), java.util.Set.copyOf(decision.resolvedReportIds()));
        assertEquals("resolved", decision.status());
        assertEquals(0, decision.resolvedAt().getNano() % 1000);
        org.junit.jupiter.api.Assertions.assertTrue(posts.findById(QUESTION).orElseThrow().isHidden());
        assertEquals(2, reports.listReportsForModeration("resolved", 0, 20).totalElements());
        assertEquals(2, reports.listReportsForModeration("open", 0, 20).totalElements());
        var conflict = org.junit.jupiter.api.Assertions.assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> tx().execute(status -> reports.dismissReport(second)));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.getStatus());
        assertEquals("dismissed", tx().execute(status -> reports.dismissReport(other)).status());
        org.junit.jupiter.api.Assertions.assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                () -> tx().execute(status -> reports.resolveLessonReports(lesson, SECOND_LESSON)));
        assertEquals("resolved", tx().execute(status -> reports.resolveLessonReports(lesson, LESSON)).status());
        signIn(LEARNER, com.codemastery.modules.auth.domain.model.Role.LEARNER);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reports.listReportsForModeration(null, 0, 20));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tx().execute(status -> reports.hidePostForReport(first)));
    }

    @Test
    void moderationParticipatesInCallerRollbackAndRequiresItsTransaction() {
        UUID report = report(QUESTION);
        signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> reports.hidePostForReport(report));
        tx().executeWithoutResult(status -> { reports.hidePostForReport(report); status.setRollbackOnly(); });
        org.junit.jupiter.api.Assertions.assertFalse(posts.findById(QUESTION).orElseThrow().isHidden());
        assertEquals("open", reportRepository.findById(report).orElseThrow().getStatus().getCode());
        // A managed report selected before a competing commit must be refreshed after its lock.
        tx().executeWithoutResult(status -> {
            reportRepository.findById(report).orElseThrow();
            var independent = tx(); independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(inner -> reports.dismissReport(report));
            var conflict = org.junit.jupiter.api.Assertions.assertThrows(com.codemastery.common.exception.BusinessRuleException.class,
                    () -> reports.dismissReport(report));
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.getStatus());
            status.setRollbackOnly();
        });
        assertEquals("dismissed", reportRepository.findById(report).orElseThrow().getStatus().getCode());
    }

    @Test
    void runtimeRoleExecutesJpaWritesParentLocksAndModerationWithoutImmutableUpdates() {
        String role = "in06_runtime_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
        jdbc.execute("GRANT SELECT ON public.users, public.courses, public.chapters, public.lessons, public.enrollments TO " + role);
        jdbc.execute("GRANT SELECT,INSERT ON public.discussion_posts,public.content_reports,public.notifications TO " + role);
        jdbc.execute("GRANT UPDATE(is_hidden) ON public.discussion_posts TO " + role);
        jdbc.execute("GRANT UPDATE(status,resolved_at) ON public.content_reports TO " + role);
        try {
            signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
            var reply = tx().execute(status -> {
                entityManager.createNativeQuery("SET LOCAL ROLE " + role).executeUpdate();
                return discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("runtime reply", QUESTION));
            });
            org.junit.jupiter.api.Assertions.assertNotNull(reply);
            UUID report = tx().execute(status -> {
                entityManager.createNativeQuery("SET LOCAL ROLE " + role).executeUpdate();
                return reports.reportPost(LESSON, QUESTION, () -> "runtime report").getId();
            });
            signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
            tx().executeWithoutResult(status -> {
                entityManager.createNativeQuery("SET LOCAL ROLE " + role).executeUpdate();
                assertEquals(1, reports.listReportsForModeration(null, 0, 20).totalElements());
                reports.hidePostForReport(report);
            });
            org.junit.jupiter.api.Assertions.assertTrue(posts.findById(QUESTION).orElseThrow().isHidden());
            assertEquals("resolved", reportRepository.findById(report).orElseThrow().getStatus().getCode());
        } finally { jdbc.execute("DROP OWNED BY " + role); jdbc.execute("DROP ROLE " + role); }
    }

    @Test
    void concurrentHideSerializesReplyAndDismissalWithoutDeadlock() throws Exception {
        UUID report = report(QUESTION);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        var hidden = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.CountDownLatch(1);
        try {
            var hide = pool.submit(() -> {
                signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
                return tx().execute(status -> {
                    var result = reports.hidePostForReport(report); hidden.countDown();
                    try { if (!commit.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
                    catch (InterruptedException ex) { throw new IllegalStateException(ex); }
                    return result;
                });
            });
            org.junit.jupiter.api.Assertions.assertTrue(hidden.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var reply = pool.submit(() -> {
                signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
                return discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("racing reply", QUESTION));
            });
            var dismiss = pool.submit(() -> {
                signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
                return tx().execute(status -> reports.dismissReport(report));
            });
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> reply.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> dismiss.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            commit.countDown(); hide.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var replyFailure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> reply.get(10, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertInstanceOf(com.codemastery.common.exception.ResourceNotFoundException.class, replyFailure.getCause());
            var dismissFailure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> dismiss.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, ((com.codemastery.common.exception.BusinessRuleException) dismissFailure.getCause()).getStatus());
        } finally { commit.countDown(); pool.shutdownNow(); }
    }

    @Test
    void replyShareLockCommitsBeforeAWaitingHideAndBothRowsRemainAtomic() throws Exception {
        UUID report = report(QUESTION);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var inserted = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.CountDownLatch(1);
        try {
            var reply = pool.submit(() -> {
                signIn(INSTRUCTOR, com.codemastery.modules.auth.domain.model.Role.INSTRUCTOR);
                return tx().execute(status -> {
                    var result = discussions.create(LESSON, () -> new com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite("before hide", QUESTION));
                    inserted.countDown();
                    try { if (!commit.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
                    catch (InterruptedException ex) { throw new IllegalStateException(ex); }
                    return result;
                });
            });
            org.junit.jupiter.api.Assertions.assertTrue(inserted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var hide = pool.submit(() -> {
                signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
                return tx().execute(status -> reports.hidePostForReport(report));
            });
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> hide.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
            commit.countDown(); var row = reply.get(10, java.util.concurrent.TimeUnit.SECONDS); hide.get(10, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertTrue(posts.findById(row.id()).isPresent());
            assertEquals(1, committedCount("notifications"));
            signIn(LEARNER, com.codemastery.modules.auth.domain.model.Role.LEARNER);
            org.junit.jupiter.api.Assertions.assertTrue(discussions.list(LESSON, 0, 20).content().isEmpty());
        } finally { commit.countDown(); pool.shutdownNow(); }
    }

    @Test
    void decisionRejectsReadOnlyAndRepeatableReadCallerTransactions() {
        UUID report = report(QUESTION);
        signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
        var readOnly = tx(); readOnly.setReadOnly(true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> readOnly.execute(status -> reports.dismissReport(report)));
        var repeatable = tx(); repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> repeatable.execute(status -> reports.hidePostForReport(report)));
        assertEquals("open", reportRepository.findById(report).orElseThrow().getStatus().getCode());
    }

    @Test
    void simultaneousHidesOnDifferentReportsOfOneTargetResolveOnce() throws Exception {
        UUID first = report(QUESTION), second = report(QUESTION);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (UUID id : List.of(first, second)) jobs.add(pool.submit(() -> {
                signIn(ADMIN, com.codemastery.modules.auth.domain.model.Role.ADMIN);
                start.await();
                try { return tx().execute(status -> reports.hidePostForReport(id)).resolvedReportIds().size(); }
                catch (com.codemastery.common.exception.BusinessRuleException conflict) {
                    assertEquals(org.springframework.http.HttpStatus.CONFLICT, conflict.getStatus()); return 0;
                }
            }));
            start.countDown();
            assertEquals(2, jobs.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS) + jobs.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM public.content_reports WHERE status='resolved'", Integer.class));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void malformedUtf8AndUnsupportedMethodsHaveSafeNoStoreErrors() throws Exception {
        for (String path : List.of(lessonPath(LESSON), discussionPath(LESSON))) {
            mvc.perform(as(post(path).contentType(MediaType.APPLICATION_JSON).content(new byte[]{(byte)0xc3,0x28}), LEARNER))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path), LEARNER)).andExpect(status().isForbidden());
        }
    }

    @Test
    void completedMembersRetainDiscussionAccessButLeftAndLockedActorsDoNot() throws Exception {
        jdbc.update("UPDATE public.enrollments SET status='completed' WHERE user_id=? AND course_id=?", LEARNER, COURSE);
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(discussionPath(LESSON)), LEARNER)).andExpect(status().isOk());
        mvc.perform(json(discussionPath(LESSON), "{\"content\":\"completed member question\"}", LEARNER)).andExpect(status().isCreated());
        mvc.perform(json(lessonPath(LESSON), REASON, LEARNER)).andExpect(status().isCreated());
        jdbc.update("UPDATE public.enrollments SET status='left' WHERE user_id=? AND course_id=?", LEARNER, COURSE);
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(discussionPath(LESSON)), LEARNER)).andExpect(status().isNotFound());
        mvc.perform(json(discussionPath(LESSON), "{", LEARNER)).andExpect(status().isNotFound());
        jdbc.update("UPDATE public.users SET account_status='locked' WHERE id=?", LEARNER);
        mvc.perform(json(discussionPath(LESSON), "{", LEARNER)).andExpect(status().isUnauthorized());
    }
}
