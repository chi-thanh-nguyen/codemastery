package com.codemastery.integration.repository;

import com.codemastery.integration.AbstractIntegrationTest;
import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.ContentReportRepository;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.repository.ContentReportJpaRepository;
import com.codemastery.modules.interaction.infrastructure.persistence.repository.DiscussionPostJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * IN06.repo real-PostgreSQL regression suite for the Interaction discussion-post and content-report
 * repositories (V006; ADR012 §§6, 11, 19.2).
 *
 * <p>Every test runs in one transaction that is always rolled back, so fixtures, test-scoped roles
 * and grants never persist. Privilege tests switch the transaction to a {@code NOLOGIN} role holding
 * exactly the ADR012 §19.2 ceiling for these two tables; nothing is granted outside the transaction.
 *
 * <p>The context is closed after this class so its connection pool does not stay open next to the
 * other cached integration contexts that share the one per-JVM PostgreSQL container.
 */
@DirtiesContext
class InteractionRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = generateEphemeralJwtSecret();
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final Instant T0 = Instant.parse("2026-11-18T01:00:00.123456Z");

    private static String generateEphemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DiscussionPostRepository posts;

    @Autowired
    private ContentReportRepository reports;

    @Autowired
    private DiscussionPostJpaRepository postJpa;

    @Autowired
    private ContentReportJpaRepository reportJpa;

    // ---------------------------------------------------------------- insert and permitted update

    @Test
    void insertsPostAndReportAndReadsThemBackFromTheDatabase() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            DiscussionPost question = post(f.lessonId, f.learnerId, null, " Why?\n", T0, false);
            DiscussionPost reply = post(f.lessonId, f.instructorId, question.getId(), "Because.", T0.plusSeconds(1), false);
            ContentReport report = openReport(f.learnerId, reply.getId(), null, " spam ", T0.plusSeconds(2));

            posts.save(question);
            posts.save(reply);
            reports.save(report);
            flushAndClear();

            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM public.discussion_posts WHERE id = ?", reply.getId());
            assertAll(
                    () -> assertEquals(f.lessonId, row.get("lesson_id")),
                    () -> assertEquals(f.instructorId, row.get("author_id")),
                    () -> assertEquals(question.getId(), row.get("parent_post_id")),
                    () -> assertEquals("Because.", row.get("content")),
                    () -> assertEquals(Boolean.FALSE, row.get("is_hidden")),
                    () -> assertEquals("open", jdbc.queryForObject(
                            "SELECT status FROM public.content_reports WHERE id = ?", String.class, report.getId())));
            assertPostEquals(question, posts.findById(question.getId()).orElseThrow());
            assertPostEquals(reply, posts.findById(reply.getId()).orElseThrow());
            assertReportEquals(report, reports.findById(report.getId()).orElseThrow());
            assertTrue(posts.findById(UUID.randomUUID()).isEmpty());
            assertTrue(reports.findById(null).isEmpty());
        });
    }

    @Test
    void existingPostUpdatesOnlyTheHiddenFlagAndFindByIdSeesIt() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            DiscussionPost question = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false));
            entityManager.flush();

            DiscussionPost hidden = posts.save(withHidden(question, true));

            assertTrue(hidden.isHidden());
            assertTrue(posts.findById(question.getId()).orElseThrow().isHidden(), "same-transaction read is not stale");
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT is_hidden FROM public.discussion_posts WHERE id = ?", Boolean.class, question.getId()));
            flushAndClear();
            assertPostEquals(withHidden(question, true), posts.findById(question.getId()).orElseThrow());
        });
    }

    @Test
    void existingReportUpdatesOnlyStatusAndResolutionTime() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            ContentReport open = reports.save(openReport(f.learnerId, null, f.lessonId, "Wrong", T0));
            entityManager.flush();
            Instant decided = T0.plusSeconds(60);

            ContentReport dismissed = reports.save(withResolution(open, ContentReport.Status.DISMISSED, decided));

            assertEquals(ContentReport.Status.DISMISSED, dismissed.getStatus());
            assertEquals(decided, dismissed.getResolvedAt());
            assertEquals(ContentReport.Status.DISMISSED, reports.findById(open.getId()).orElseThrow().getStatus());
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, resolved_at, reason, lesson_id FROM public.content_reports WHERE id = ?", open.getId());
            assertAll(
                    () -> assertEquals("dismissed", row.get("status")),
                    () -> assertEquals(decided, instant(row.get("resolved_at"))),
                    () -> assertEquals("Wrong", row.get("reason")),
                    () -> assertEquals(f.lessonId, row.get("lesson_id")));
            flushAndClear();
            assertReportEquals(withResolution(open, ContentReport.Status.DISMISSED, decided),
                    reports.findById(open.getId()).orElseThrow());
        });
    }

    @Test
    void unchangedExistingRowsAreReturnedAsStored() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            DiscussionPost question = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false));
            ContentReport report = reports.save(openReport(f.learnerId, question.getId(), null, "r", T0));
            flushAndClear();

            assertPostEquals(question, posts.save(question));
            assertReportEquals(report, reports.save(report));
        });
    }

    // ---------------------------------------------------------------- immutable fields

    @Test
    void changingAnyImmutablePostFieldIsRejectedAndLeavesTheRowUnchanged() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            DiscussionPost question = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false));
            DiscussionPost other = posts.save(post(f.lessonId, f.learnerId, null, "Other", T0, false));
            UUID otherLesson = lesson(f.chapterId, 2);
            flushAndClear();

            List<UnaryOperator<DiscussionPost>> changes = List.of(
                    p -> new DiscussionPost(p.getId(), otherLesson, p.getAuthorId(), p.getParentPostId(), p.getContent(), p.getCreatedAt(), true),
                    p -> new DiscussionPost(p.getId(), p.getLessonId(), f.instructorId, p.getParentPostId(), p.getContent(), p.getCreatedAt(), true),
                    p -> new DiscussionPost(p.getId(), p.getLessonId(), p.getAuthorId(), other.getId(), p.getContent(), p.getCreatedAt(), true),
                    p -> new DiscussionPost(p.getId(), p.getLessonId(), p.getAuthorId(), p.getParentPostId(), "Edited", p.getCreatedAt(), true),
                    p -> new DiscussionPost(p.getId(), p.getLessonId(), p.getAuthorId(), p.getParentPostId(), p.getContent(), T0.plusSeconds(5), true));
            for (UnaryOperator<DiscussionPost> change : changes) {
                assertImmutableFieldRejected(() -> posts.save(change.apply(question)));
            }
            entityManager.flush();
            assertEquals(List.of(Map.of("lesson_id", f.lessonId, "author_id", f.learnerId, "content", "Q", "is_hidden", false)),
                    jdbc.queryForList("SELECT lesson_id, author_id, content, is_hidden FROM public.discussion_posts "
                            + "WHERE id = ? AND parent_post_id IS NULL", question.getId()));
            assertEquals(T0, instant(jdbc.queryForObject(
                    "SELECT created_at FROM public.discussion_posts WHERE id = ?", OffsetDateTime.class, question.getId())));
        });
    }

    @Test
    void changingAnyImmutableReportFieldIsRejectedAndLeavesTheRowUnchanged() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            DiscussionPost question = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false));
            ContentReport report = reports.save(openReport(f.learnerId, question.getId(), null, "r", T0));
            flushAndClear();
            Instant decided = T0.plusSeconds(9);

            List<ContentReport> changes = List.of(
                    new ContentReport(report.getId(), f.instructorId, question.getId(), null, "r", ContentReport.Status.RESOLVED, T0, decided),
                    new ContentReport(report.getId(), f.learnerId, null, f.lessonId, "r", ContentReport.Status.RESOLVED, T0, decided),
                    new ContentReport(report.getId(), f.learnerId, question.getId(), null, "edited", ContentReport.Status.RESOLVED, T0, decided),
                    new ContentReport(report.getId(), f.learnerId, question.getId(), null, "r", ContentReport.Status.RESOLVED, T0.plusSeconds(1), decided));
            for (ContentReport change : changes) {
                assertImmutableFieldRejected(() -> reports.save(change));
            }
            entityManager.flush();
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM public.content_reports WHERE id = ?", report.getId());
            assertAll(
                    () -> assertEquals(f.learnerId, row.get("reporter_id")),
                    () -> assertEquals(question.getId(), row.get("discussion_post_id")),
                    () -> assertNull(row.get("lesson_id")),
                    () -> assertEquals("r", row.get("reason")),
                    () -> assertEquals("open", row.get("status")),
                    () -> assertNull(row.get("resolved_at")),
                    () -> assertEquals(T0, instant(row.get("created_at"))));
        });
    }

    // ---------------------------------------------------------------- reads, filtering and paging

    @Test
    void hiddenPostsAreFilteredBeforePagingAndCounting() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            UUID otherLesson = lesson(f.chapterId, 2);
            List<DiscussionPost> lessonPosts = List.of(
                    posts.save(post(f.lessonId, f.learnerId, null, "p1", T0.plusSeconds(1), false)),
                    posts.save(post(f.lessonId, f.learnerId, null, "p2", T0.plusSeconds(2), true)),
                    posts.save(post(f.lessonId, f.learnerId, null, "p3", T0.plusSeconds(3), false)),
                    posts.save(post(f.lessonId, f.learnerId, null, "p4", T0.plusSeconds(4), true)),
                    posts.save(post(f.lessonId, f.learnerId, null, "p5", T0.plusSeconds(5), false)));
            posts.save(post(otherLesson, f.learnerId, null, "foreign", T0.plusSeconds(6), false));
            flushAndClear();
            Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

            Page<DiscussionPost> visible0 = posts.findByLessonId(f.lessonId, false, PageRequest.of(0, 2, newestFirst));
            Page<DiscussionPost> visible1 = posts.findByLessonId(f.lessonId, false, PageRequest.of(1, 2, newestFirst));
            Page<DiscussionPost> all0 = posts.findByLessonId(f.lessonId, true, PageRequest.of(0, 2, newestFirst));

            assertAll(
                    () -> assertEquals(List.of("p5", "p3"), contents(visible0)),
                    () -> assertEquals(List.of("p1"), contents(visible1)),
                    () -> assertEquals(3, visible0.getTotalElements()),
                    () -> assertEquals(2, visible0.getTotalPages()),
                    () -> assertTrue(visible0.getContent().stream().noneMatch(DiscussionPost::isHidden)),
                    () -> assertEquals(List.of("p5", "p4"), contents(all0)),
                    () -> assertEquals(5, all0.getTotalElements()),
                    () -> assertEquals(lessonPosts.size(), all0.getTotalElements()));
        });
    }

    @Test
    void orderingComesOnlyFromTheCallerAndIsStableAcrossTies() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            for (int i = 0; i < 5; i++) {
                posts.save(post(f.lessonId, f.learnerId, null, "tie" + i, T0, false));
            }
            flushAndClear();
            List<UUID> idsAscending = jdbc.queryForList(
                    "SELECT id FROM public.discussion_posts WHERE lesson_id = ? ORDER BY id ASC", UUID.class, f.lessonId);

            Sort byTimeThenIdAsc = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            Sort byTimeThenIdDesc = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("id"));
            List<UUID> ascPaged = new java.util.ArrayList<>();
            for (int page = 0; page < 3; page++) {
                posts.findByLessonId(f.lessonId, false, PageRequest.of(page, 2, byTimeThenIdAsc))
                        .forEach(p -> ascPaged.add(p.getId()));
            }
            List<UUID> desc = posts.findByLessonId(f.lessonId, false, PageRequest.of(0, 5, byTimeThenIdDesc))
                    .map(DiscussionPost::getId).getContent();

            assertEquals(idsAscending, ascPaged, "pages neither repeat nor skip tied rows");
            assertEquals(idsAscending.reversed(), desc);
        });
    }

    // ---------------------------------------------------------------- V006 constraints

    @Test
    void v006PostChecksRejectBlankContentAndSelfParent() {
        assertConstraintViolation("ck_discussion_posts_text", f ->
                posts.save(post(f.lessonId, f.learnerId, null, " \n\t", T0, false)));
        assertConstraintViolation("ck_discussion_posts_not_self", f -> {
            UUID id = UUID.randomUUID();
            posts.save(new DiscussionPost(id, f.lessonId, f.learnerId, id, "self", T0, false));
        });
        assertConstraintViolation("fk_discussion_posts_parent_post_id", f ->
                posts.save(post(f.lessonId, f.learnerId, UUID.randomUUID(), "orphan", T0, false)));
    }

    @Test
    void v006ReportChecksRejectInvalidTargetsResolutionsAndStatusCodes() {
        assertConstraintViolation("ck_content_reports_target", f -> {
            UUID postId = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false)).getId();
            reports.save(openReport(f.learnerId, postId, f.lessonId, "both", T0));
        });
        assertConstraintViolation("ck_content_reports_target", f ->
                reports.save(openReport(f.learnerId, null, null, "neither", T0)));
        assertConstraintViolation("ck_content_reports_text", f ->
                reports.save(openReport(f.learnerId, null, f.lessonId, "  ", T0)));
        assertConstraintViolation("ck_content_reports_resolution", f ->
                reports.save(new ContentReport(UUID.randomUUID(), f.learnerId, null, f.lessonId, "r",
                        ContentReport.Status.OPEN, T0, T0.plusSeconds(1))));
        assertConstraintViolation("ck_content_reports_resolution", f -> {
            ContentReport open = reports.save(openReport(f.learnerId, null, f.lessonId, "r", T0));
            entityManager.flush();
            reports.save(withResolution(open, ContentReport.Status.RESOLVED, null));
        });
        assertConstraintViolation("ck_content_reports_times", f -> {
            ContentReport open = reports.save(openReport(f.learnerId, null, f.lessonId, "r", T0));
            entityManager.flush();
            reports.save(withResolution(open, ContentReport.Status.DISMISSED, T0.minusSeconds(1)));
        });
        // A non-V006 code also fails ck_content_reports_resolution, which PostgreSQL may report first.
        assertConstraintViolation(List.of("ck_content_reports_status", "ck_content_reports_resolution"), f ->
                jdbc.update("INSERT INTO public.content_reports (id, reporter_id, lesson_id, reason, status, created_at) "
                        + "VALUES (?, ?, ?, 'r', 'Open', now())", UUID.randomUUID(), f.learnerId, f.lessonId));
    }

    // ---------------------------------------------------------------- ADR012 §19.2 privilege ceiling

    @Test
    void repositorySqlRunsUnderTheSection192PrivilegeCeiling() {
        inRolledBackTransaction(() -> {
            Fixture f = fixture();
            switchToCeilingRole();

            DiscussionPost question = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false));
            DiscussionPost reply = posts.save(post(f.lessonId, f.instructorId, question.getId(), "A", T0.plusSeconds(1), false));
            ContentReport postReport = reports.save(openReport(f.learnerId, reply.getId(), null, "r1", T0.plusSeconds(2)));
            ContentReport lessonReport = reports.save(openReport(f.learnerId, null, f.lessonId, "r2", T0.plusSeconds(2)));
            entityManager.flush();

            posts.save(withHidden(reply, true));
            reports.save(withResolution(postReport, ContentReport.Status.RESOLVED, T0.plusSeconds(10)));
            reports.save(withResolution(lessonReport, ContentReport.Status.DISMISSED, T0.plusSeconds(11)));
            flushAndClear();

            assertEquals(List.of("Q"), contents(posts.findByLessonId(f.lessonId, false, PageRequest.of(0, 10, Sort.by("id")))));
            assertTrue(posts.findById(reply.getId()).orElseThrow().isHidden());
            assertEquals(ContentReport.Status.RESOLVED, reports.findById(postReport.getId()).orElseThrow().getStatus());
            assertEquals(ContentReport.Status.DISMISSED, reports.findById(lessonReport.getId()).orElseThrow().getStatus());
            assertFalse(Boolean.TRUE.equals(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                    Boolean.class)), "statements above ran as the restricted role");
        });
    }

    @Test
    void ceilingRejectsTheFullRowPostUpdateThatTheOldMergePathIssued() {
        UUID postId = UUID.randomUUID();
        assertInsufficientPrivilege(f -> {
            jdbc.update("INSERT INTO public.discussion_posts (id, lesson_id, author_id, content, created_at, is_hidden) "
                    + "VALUES (?, ?, ?, 'Q', ?, false)", postId, f.lessonId, f.learnerId, OffsetDateTime.parse("2026-11-18T01:00:00Z"));
            switchToCeilingRole();
            postJpa.save(new DiscussionPostEntity(postId, f.lessonId, f.learnerId, null, "Q",
                    Instant.parse("2026-11-18T01:00:00Z"), true));
            entityManager.flush();
        });
    }

    @Test
    void ceilingRejectsTheFullRowReportUpdateThatTheOldMergePathIssued() {
        UUID reportId = UUID.randomUUID();
        assertInsufficientPrivilege(f -> {
            jdbc.update("INSERT INTO public.content_reports (id, reporter_id, lesson_id, reason, status, created_at) "
                    + "VALUES (?, ?, ?, 'r', 'open', ?)", reportId, f.learnerId, f.lessonId, OffsetDateTime.parse("2026-11-18T01:00:00Z"));
            switchToCeilingRole();
            reportJpa.save(new ContentReportEntity(reportId, f.learnerId, null, f.lessonId, "r", "dismissed",
                    Instant.parse("2026-11-18T01:00:00Z"), Instant.parse("2026-11-18T02:00:00Z")));
            entityManager.flush();
        });
    }

    @Test
    void ceilingRejectsUpdatesOfImmutablePostAndReportColumns() {
        assertInsufficientPrivilege(f -> {
            UUID postId = posts.save(post(f.lessonId, f.learnerId, null, "Q", T0, false)).getId();
            entityManager.flush();
            switchToCeilingRole();
            jdbc.update("UPDATE public.discussion_posts SET content = 'Edited' WHERE id = ?", postId);
        });
        assertInsufficientPrivilege(f -> {
            UUID reportId = reports.save(openReport(f.learnerId, null, f.lessonId, "r", T0)).getId();
            entityManager.flush();
            switchToCeilingRole();
            jdbc.update("UPDATE public.content_reports SET reason = 'Edited' WHERE id = ?", reportId);
        });
    }

    // ---------------------------------------------------------------- helpers

    private record Fixture(UUID learnerId, UUID instructorId, UUID chapterId, UUID lessonId) {
    }

    private void inRolledBackTransaction(Runnable body) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        });
    }

    private void assertConstraintViolation(String constraint, java.util.function.Consumer<Fixture> body) {
        assertConstraintViolation(List.of(constraint), body);
    }

    private void assertConstraintViolation(List<String> anyOf, java.util.function.Consumer<Fixture> body) {
        Throwable thrown = assertThrows(RuntimeException.class, () -> inRolledBackTransaction(() -> {
            body.accept(fixture());
            entityManager.flush();
        }));
        String messages = causeMessages(thrown);
        assertTrue(anyOf.stream().anyMatch(messages::contains), () -> "expected one of " + anyOf + " but got " + messages);
    }

    /** The adapter rejects the change before any SQL; Spring's repository translation wraps the cause. */
    private static void assertImmutableFieldRejected(org.junit.jupiter.api.function.Executable save) {
        InvalidDataAccessApiUsageException thrown = assertThrows(InvalidDataAccessApiUsageException.class, save);
        assertTrue(thrown.getCause() instanceof IllegalArgumentException, () -> "unexpected cause " + thrown.getCause());
    }

    private void assertInsufficientPrivilege(java.util.function.Consumer<Fixture> body) {
        Throwable thrown = assertThrows(RuntimeException.class, () -> inRolledBackTransaction(() -> body.accept(fixture())));
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && INSUFFICIENT_PRIVILEGE.equals(sql.getSQLState())) {
                return;
            }
        }
        fail("expected SQLSTATE 42501 but got " + causeMessages(thrown));
    }

    /**
     * Switches the current transaction to a new {@code NOLOGIN} role holding exactly the ADR012 §19.2
     * ceiling for {@code discussion_posts} and {@code content_reports}. Role, grants and the role
     * switch end with the transaction's rollback.
     */
    private void switchToCeilingRole() {
        entityManager.flush();
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class),
                "the disposable test container user creates the test-scoped role");
        String role = "in06_ceiling_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + role + " NOLOGIN");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE (is_hidden) ON public.discussion_posts TO " + role);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE (status, resolved_at) ON public.content_reports TO " + role);
        jdbc.execute("SET LOCAL ROLE " + role);
    }

    private Fixture fixture() {
        UUID learner = user("learner");
        UUID instructor = user("instructor");
        UUID course = UUID.randomUUID();
        jdbc.update("INSERT INTO public.courses (id, instructor_id, title, description, publication_status, adaptive_enabled) "
                + "VALUES (?, ?, 'IN06 course', 'IN06 repository fixture', 'published', false)", course, instructor);
        UUID chapter = UUID.randomUUID();
        jdbc.update("INSERT INTO public.chapters (id, course_id, title, position) VALUES (?, ?, 'Chapter', 1)", chapter, course);
        return new Fixture(learner, instructor, chapter, lesson(chapter, 1));
    }

    private UUID lesson(UUID chapterId, int position) {
        UUID lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO public.lessons (id, chapter_id, title, position, publication_status) "
                + "VALUES (?, ?, 'Lesson', ?, 'published')", lesson, chapterId, position);
        return lesson;
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-real-hash', 'IN06 user', ?, 'active')", id, "in06-" + id + "@example.test", role);
        return id;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static DiscussionPost post(UUID lessonId, UUID authorId, UUID parentId, String content, Instant createdAt, boolean hidden) {
        return new DiscussionPost(UUID.randomUUID(), lessonId, authorId, parentId, content, createdAt.truncatedTo(ChronoUnit.MICROS), hidden);
    }

    private static DiscussionPost withHidden(DiscussionPost p, boolean hidden) {
        return new DiscussionPost(p.getId(), p.getLessonId(), p.getAuthorId(), p.getParentPostId(), p.getContent(), p.getCreatedAt(), hidden);
    }

    private static ContentReport openReport(UUID reporterId, UUID postId, UUID lessonId, String reason, Instant createdAt) {
        return new ContentReport(UUID.randomUUID(), reporterId, postId, lessonId, reason, ContentReport.Status.OPEN, createdAt, null);
    }

    private static ContentReport withResolution(ContentReport r, ContentReport.Status status, Instant resolvedAt) {
        return new ContentReport(r.getId(), r.getReporterId(), r.getDiscussionPostId(), r.getLessonId(), r.getReason(),
                status, r.getCreatedAt(), resolvedAt);
    }

    private static List<String> contents(Page<DiscussionPost> page) {
        return page.map(DiscussionPost::getContent).getContent();
    }

    private static Instant instant(Object value) {
        return value instanceof OffsetDateTime odt ? odt.toInstant() : ((java.sql.Timestamp) value).toInstant();
    }

    private static String causeMessages(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            messages.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private static void assertPostEquals(DiscussionPost expected, DiscussionPost actual) {
        assertAll(
                () -> assertEquals(expected.getId(), actual.getId()),
                () -> assertEquals(expected.getLessonId(), actual.getLessonId()),
                () -> assertEquals(expected.getAuthorId(), actual.getAuthorId()),
                () -> assertEquals(expected.getParentPostId(), actual.getParentPostId()),
                () -> assertEquals(expected.getContent(), actual.getContent()),
                () -> assertEquals(expected.getCreatedAt(), actual.getCreatedAt()),
                () -> assertEquals(expected.isHidden(), actual.isHidden()));
    }

    private static void assertReportEquals(ContentReport expected, ContentReport actual) {
        assertAll(
                () -> assertEquals(expected.getId(), actual.getId()),
                () -> assertEquals(expected.getReporterId(), actual.getReporterId()),
                () -> assertEquals(expected.getDiscussionPostId(), actual.getDiscussionPostId()),
                () -> assertEquals(expected.getLessonId(), actual.getLessonId()),
                () -> assertEquals(expected.getReason(), actual.getReason()),
                () -> assertEquals(expected.getStatus(), actual.getStatus()),
                () -> assertEquals(expected.getCreatedAt(), actual.getCreatedAt()),
                () -> assertEquals(expected.getResolvedAt(), actual.getResolvedAt()));
    }
}
