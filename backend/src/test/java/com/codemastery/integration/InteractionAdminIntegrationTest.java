package com.codemastery.integration;

import com.codemastery.modules.admin.application.service.AuditLogService;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.admin.domain.model.AuditLog;
import com.codemastery.modules.auth.domain.model.Role;
import com.codemastery.modules.auth.infrastructure.persistence.entity.UserEntity;
import com.codemastery.modules.auth.infrastructure.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-PostgreSQL Interaction/Admin suite approved by ADR012 section 19.3
 * ({@code CT14-INTEGRATION-PLACEMENT-2026-11-15-r1}).
 *
 * <p>IN10 (#184) contributes the CT31.consistency guarantees that {@link AuditLogService#record} owns
 * (B7-O07, recording side): the audit row commits only with its caller's unit, an audit-insert failure
 * rolls the caller's change back, a later failure removes the audit row, the caller's pending JPA-managed
 * changes are written before the audit insert, and recording never runs or commits on its own. The caller's
 * "change" here is a test-only fixture write of an unrelated {@code users.display_name} (by JDBC, or through
 * JPA and still pending), not a CT12 or CT32 mutation: B7-O07's per-action catalogue rows and no-op
 * cases belong to IN11, IN20 and IN21, which add their audited actions to this suite. Every outcome is
 * checked through an independent committed read on a separate connection, and committed fixtures are
 * removed in FK-safe order after each test (ADR008 section 5).
 */
@DirtiesContext
@org.springframework.context.annotation.Import(InteractionAdminIntegrationTest.ContentProbe.class)
class InteractionAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String JWT_SECRET = ephemeralJwtSecret();
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String READ_ONLY_TRANSACTION = "25006";
    private static final String CHECK_VIOLATION = "23514";

    private static String ephemeralJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @DynamicPropertySource
    static void provideEphemeralJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuditLogService audit;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<UUID> users = new ArrayList<>();
    private UUID admin;
    private UUID fixtureTarget;

    @BeforeEach
    void fixtures() {
        admin = user("admin", "IN10 Atomicity Admin");
        fixtureTarget = user("learner", "Original name");
    }

    @AfterEach
    void removeFixtures() {
        if (isolationLoss() != null) throw new IllegalStateException("Unsafe shared PostgreSQL cleanup", isolationLoss());
        SecurityContextHolder.clearContext();
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.audit_logs WHERE actor_id = ?", id);
        }
        for (UUID id : users) {
            jdbc.update("DELETE FROM public.users WHERE id = ?", id);
        }
        users.clear();
    }

    @Test
    void theAuditRowCommitsTogetherWithTheCallersChangeAndIsInvisibleBeforeTheCommit() {
        signIn(admin, Role.ADMIN);

        AuditLog recorded = writable().execute(status -> {
            changeFixture("Changed name");
            AuditLog row = audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            // Recording never commits on its own (no REQUIRES_NEW): another connection sees nothing yet.
            assertEquals(0, committedAuditRows(admin));
            assertEquals("Original name", committedFixtureName());
            return row;
        });

        assertEquals(1, committedAuditRows(admin));
        assertEquals("Changed name", committedFixtureName());
        assertEquals(List.of(admin + "|user_locked|user|" + fixtureTarget), committedAuditRowText(recorded.getId()));
    }

    @Test
    void anInjectedAuditInsertFailureRollsBackTheCallersChange() {
        // The injected fault: an authenticated Administrator principal without a users row makes the
        // audit INSERT fail its actor foreign key, inside record() and after the caller's change.
        UUID ghost = UUID.randomUUID();
        signIn(ghost, Role.ADMIN);
        AtomicBoolean continuedAfterRecord = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> writable().executeWithoutResult(status -> {
            changeFixture("Changed name");
            audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            continuedAfterRecord.set(true);
        }));

        assertEquals(FOREIGN_KEY_VIOLATION, sqlState(thrown));
        assertFalse(continuedAfterRecord.get(), "the insert failure surfaces from record(), not at commit");
        assertEquals("Original name", committedFixtureName());
        assertEquals(0, committedAuditRows(ghost));
    }

    @Test
    void aFailureAfterRecordingRollsBackTheAuditRowAndTheChange() {
        signIn(admin, Role.ADMIN);

        assertThrows(IllegalStateException.class, () -> writable().executeWithoutResult(status -> {
            changeFixture("Changed name");
            audit.record(AuditAction.COURSE_HIDDEN, UUID.randomUUID());
            throw new IllegalStateException("synthetic failure after the audit insert");
        }));

        assertEquals(0, committedAuditRows(admin));
        assertEquals("Original name", committedFixtureName());
    }

    /**
     * CT31.consistency lock order (ADR012 section 7.2): the caller's own writes precede the audit insert.
     * Here the caller's change is a JPA-managed update still pending in the persistence context. Within one
     * flush Hibernate executes queued inserts before queued updates, so only the flush that {@code record}
     * performs before persisting keeps the caller's UPDATE ahead of the audit INSERT. PostgreSQL's
     * {@code cmin} (the command identifier within the writing transaction) shows which row was written first.
     */
    @Test
    void aPendingJpaManagedChangeIsWrittenBeforeTheAuditInsert() {
        signIn(admin, Role.ADMIN);

        long[] commandIds = writable().execute(status -> {
            changeFixtureThroughJpa("Managed name");
            // Still pending: a plain JDBC read on the transaction's connection does not flush Hibernate.
            assertEquals("Original name", jdbc.queryForObject(
                    "SELECT display_name FROM public.users WHERE id = ?", String.class, fixtureTarget));

            AuditLog row = audit.record(AuditAction.USER_LOCKED, fixtureTarget);

            return new long[] {
                    commandId("SELECT cmin::text::bigint FROM public.users WHERE id = ?", fixtureTarget),
                    commandId("SELECT cmin::text::bigint FROM public.audit_logs WHERE id = ?", row.getId())};
        });

        assertTrue(commandIds[0] < commandIds[1], () -> "the caller's UPDATE (command " + commandIds[0]
                + ") must be written before the audit INSERT (command " + commandIds[1] + ")");
        assertEquals("Managed name", committedFixtureName());
        assertEquals(1, committedAuditRows(admin));
    }

    /**
     * A failure of the caller's pending JPA-managed change surfaces from {@code record} before the audit
     * INSERT runs. The pending UPDATE breaks {@code ck_users_text} and the audit INSERT would break its actor
     * foreign key, so the reported SQLSTATE shows which statement executed first. Nothing commits.
     */
    @Test
    void aFailingPendingJpaManagedChangeSurfacesBeforeTheAuditInsert() {
        UUID ghost = UUID.randomUUID();
        signIn(ghost, Role.ADMIN);
        AtomicBoolean continuedAfterRecord = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> writable().executeWithoutResult(status -> {
            changeFixtureThroughJpa("   ");
            audit.record(AuditAction.USER_LOCKED, fixtureTarget);
            continuedAfterRecord.set(true);
        }));

        assertEquals(CHECK_VIOLATION, sqlState(thrown), "the caller's UPDATE must execute before the audit INSERT");
        assertFalse(continuedAfterRecord.get(), "the caller's flush failure surfaces from record()");
        assertEquals("Original name", committedFixtureName());
        assertEquals(0, committedAuditRows(ghost));
    }

    @Test
    void recordingRefusesToRunWithoutAWritableCallerTransaction() {
        signIn(admin, Role.ADMIN);

        assertThrows(IllegalTransactionStateException.class,
                () -> audit.record(AuditAction.USER_UNLOCKED, fixtureTarget));

        TransactionTemplate readOnly = writable();
        readOnly.setReadOnly(true);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> readOnly.executeWithoutResult(status -> audit.record(AuditAction.USER_UNLOCKED, fixtureTarget)));

        assertEquals(READ_ONLY_TRANSACTION, sqlState(thrown));
        assertEquals(0, committedAuditRows(admin));
    }

    @Test
    void eachCallRecordsOneRowWithItsOwnIdentifierInTheSameUnit() {
        signIn(admin, Role.ADMIN);
        List<UUID> reports = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<UUID> ids = writable().execute(status -> reports.stream()
                .map(report -> audit.record(AuditAction.REPORT_POST_HIDDEN, report).getId()).toList());

        assertEquals(3, ids.stream().distinct().count());
        assertEquals(3, committedAuditRows(admin));
        for (int i = 0; i < reports.size(); i++) {
            assertEquals(List.of(admin + "|report_post_hidden|content_report|" + reports.get(i)),
                    committedAuditRowText(ids.get(i)));
        }
    }

    // ---------------------------------------------------------------- helpers

    private TransactionTemplate writable() {
        return new TransactionTemplate(transactionManager);
    }

    private UUID user(String role, String displayName) {
        UUID id = UUID.randomUUID();
        users.add(id);
        jdbc.update("INSERT INTO public.users (id, email, password_hash, display_name, role, account_status) "
                + "VALUES (?, ?, 'not-a-login-fixture', ?, ?, 'active')", id, "in10-" + id + "@example.test",
                displayName, role);
        return id;
    }

    /** The test-only stand-in for an audited caller's own change, in the caller's transaction. */
    private void changeFixture(String displayName) {
        assertEquals(1, jdbc.update("UPDATE public.users SET display_name = ? WHERE id = ?", displayName, fixtureTarget));
    }

    /** A caller's change made through JPA: merged onto the managed account and written only when flushed. */
    private void changeFixtureThroughJpa(String displayName) {
        UserEntity managed = entityManager.find(UserEntity.class, fixtureTarget);
        entityManager.merge(new UserEntity(managed.getId(), managed.getEmail(), managed.getPasswordHash(),
                displayName, managed.getRole(), managed.getAccountStatus()));
    }

    /** The command identifier of a row version, read on the caller's own transaction connection. */
    private long commandId(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private static void signIn(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private int committedAuditRows(UUID actor) {
        return Integer.parseInt(independentRead("SELECT count(*) FROM public.audit_logs WHERE actor_id = ?", actor).get(0));
    }

    private String committedFixtureName() {
        return independentRead("SELECT display_name FROM public.users WHERE id = ?", fixtureTarget).get(0);
    }

    private List<String> committedAuditRowText(UUID id) {
        return independentRead("SELECT actor_id || '|' || action || '|' || target_type || '|' || target_reference "
                + "FROM public.audit_logs WHERE id = ?", id);
    }

    /** A separate pooled connection outside any Spring-managed transaction: committed state only. */
    private List<String> independentRead(String sql, UUID parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            assertTrue(connection.getAutoCommit(), "independent reads run outside the caller's transaction");
            statement.setObject(1, parameter);
            List<String> values = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(result.getString(1));
                }
            }
            return values;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sqlState(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "none: " + thrown;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class ContentProbe {
        final java.util.List<com.codemastery.modules.course.application.event.ContentPublishedEvent> events=new ArrayList<>();
        boolean fail, committedRead;
        @Autowired DataSource source;
        @org.springframework.transaction.event.TransactionalEventListener
        @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        public void delivered(com.codemastery.modules.course.application.event.ContentPublishedEvent event) {
            try(var c=source.getConnection();var q=c.prepareStatement("SELECT publication_status FROM lessons WHERE id=?")) {
                q.setObject(1,event.lessonId());try(var r=q.executeQuery()){committedRead=r.next()&&"published".equals(r.getString(1));}
            }catch(SQLException error){throw new IllegalStateException("Synthetic committed read failure",error);}
            events.add(event);if(fail)throw new IllegalStateException("Synthetic after-commit consumer failure");
        }
    }
    @Autowired ContentProbe contentProbe;
    @Autowired com.codemastery.modules.course.application.service.CourseStructureService contentStructure;
    @Autowired com.codemastery.modules.course.application.service.CourseManagementService contentManagement;
    final class ContentFixture implements AutoCloseable {
        final UUID owner=user("instructor","CO12 owner"),learner=user("learner","CO12 learner"),course=UUID.randomUUID(),chapter=UUID.randomUUID(),lesson=UUID.randomUUID(),enrollment=UUID.randomUUID();
        ContentFixture(){
            jdbc.update("INSERT INTO courses(id,instructor_id,title,description,publication_status,adaptive_enabled)VALUES(?,?,'CO12','Test','published',false)",course,owner);
            jdbc.update("INSERT INTO chapters(id,course_id,title,position)VALUES(?,?,'C',1)",chapter,course);
            jdbc.update("INSERT INTO lessons(id,chapter_id,title,position,publication_status)VALUES(?,?,'L',1,'hidden')",lesson,chapter);
            jdbc.update("INSERT INTO enrollments(id,user_id,course_id,status)VALUES(?,?,?,'completed')",enrollment,learner,course);
            contentProbe=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(contentProbe);contentProbe.events.clear();contentProbe.fail=false;contentProbe.committedRead=false;
            signIn(owner,Role.INSTRUCTOR);
        }
        String lessonStatus(){return jdbc.queryForObject("SELECT publication_status FROM lessons WHERE id=?",String.class,lesson);}
        public void close(){contentProbe.fail=false;contentProbe.events.clear();jdbc.update("DELETE FROM enrollments WHERE id=?",enrollment);jdbc.update("DELETE FROM lessons WHERE id=?",lesson);jdbc.update("DELETE FROM chapters WHERE id=?",chapter);jdbc.update("DELETE FROM courses WHERE id=?",course);SecurityContextHolder.clearContext();}
    }
    @Test void co12PublicationReconcilesThenDeliversOnlyAfterCommitAndConsumerFailureIsIsolated(){
        try(var f=new ContentFixture()){
            contentProbe.fail=true;
            contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            assertEquals("published",f.lessonStatus());assertTrue(contentProbe.committedRead);
            assertEquals("active",jdbc.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,f.enrollment));
            assertEquals(1,contentProbe.events.size());var event=contentProbe.events.getFirst();assertEquals(List.of(f.learner),event.recipientLearnerIds());
            assertEquals(0,event.publishedAt().getNano()%1000);assertThrows(UnsupportedOperationException.class,()->event.recipientLearnerIds().add(admin));
            contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.HIDDEN);
            contentManagement.setOwnedCoursePublication(f.course,com.codemastery.modules.course.domain.model.PublishStatus.HIDDEN);
            contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            assertEquals(1,contentProbe.events.size());
        }
    }
    @Test void co12ProducerRollbackAndEmptyRecipientsNeverDeliver(){
        try(var f=new ContentFixture()){
            assertThrows(IllegalStateException.class,()->writable().executeWithoutResult(tx->{
                contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
                assertTrue(contentProbe.events.isEmpty());throw new IllegalStateException("Synthetic producer rollback");
            }));
            assertEquals("hidden",f.lessonStatus());assertTrue(contentProbe.events.isEmpty());
            jdbc.update("UPDATE enrollments SET status='left' WHERE id=?",f.enrollment);
            contentStructure.setCourseLessonPublication(f.course,f.lesson,com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            assertTrue(contentProbe.events.isEmpty());
        }
    }
    /** IN20's B7-O07/O09/O17 cases share the approved PostgreSQL lifecycle. */
    @org.junit.jupiter.api.Nested
    class AdminCourses {
        @Autowired com.codemastery.modules.admin.application.service.AdminCourseService service;
        @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
        com.codemastery.modules.auth.application.service.UserAccountService identities;
        @Autowired com.codemastery.modules.course.application.service.LessonProgressService progress;
        @Autowired com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService graph;

        private final List<UUID> courses = new ArrayList<>();
        private UUID instructor;
        private String tag;

        @BeforeEach void courseFixtures() {
            instructor = user("instructor", "IN20 Instructor");
            tag = "IN20-" + UUID.randomUUID();
        }

        @AfterEach void cleanCourses() {
            if (isolationLoss() != null) throw new IllegalStateException("Unsafe IN20 cleanup", isolationLoss());
            SecurityContextHolder.clearContext();
            try {
                for (UUID id : courses) {
                    jdbc.update("DELETE FROM public.lesson_progress WHERE enrollment_id IN (SELECT id FROM public.enrollments WHERE course_id=?)", id);
                    jdbc.update("DELETE FROM public.enrollments WHERE course_id=?", id);
                    jdbc.update("DELETE FROM public.lesson_skills WHERE skill_id IN (SELECT id FROM public.skills WHERE course_id=?)", id);
                    jdbc.update("DELETE FROM public.skill_prerequisites WHERE course_id=?", id);
                    jdbc.update("DELETE FROM public.skills WHERE course_id=?", id);
                    jdbc.update("DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id=?)", id);
                    jdbc.update("DELETE FROM public.chapters WHERE course_id=?", id);
                    jdbc.update("DELETE FROM public.courses WHERE id=?", id);
                }
            } catch (RuntimeException failure) {
                recordIsolationLoss(failure);
                throw failure;
            }
            courses.clear();
        }

        private UUID course(String status, String suffix) {
            UUID id = UUID.randomUUID(); courses.add(id);
            jdbc.update("INSERT INTO public.courses(id,instructor_id,title,description,publication_status,adaptive_enabled) VALUES(?,?,?,'IN20 fixture',?,false)",
                    id, instructor, tag + " " + suffix, status);
            return id;
        }

        private String storedStatus(UUID id) {
            return independentRead("SELECT publication_status FROM public.courses WHERE id=?", id).getFirst();
        }

        private UUID enrollment(UUID course) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO public.enrollments(id,user_id,course_id,status) VALUES(?,?,?,'completed')", id, fixtureTarget, course);
            return id;
        }

        @Test void hideCommitsExactlyOneCanonicalAuditRowAndNoOpDoesNotAudit() {
            UUID id = course("published", "Hide");
            UUID joined = enrollment(id);
            signIn(admin, Role.ADMIN);
            var before = java.time.Instant.now();
            var first = service.hideCourse(id);
            var again = service.hideCourse(id);
            assertTrue(first.changed()); assertFalse(again.changed());
            assertEquals("IN20 Instructor", first.course().instructorDisplayName());
            assertEquals("hidden", storedStatus(id));
            assertEquals("active", independentRead("SELECT status FROM public.enrollments WHERE id=?", joined).getFirst());
            assertEquals(List.of(admin + "|course_hidden|course|" + id), independentRead(
                    "SELECT actor_id||'|'||action||'|'||target_type||'|'||target_reference FROM public.audit_logs WHERE actor_id=?", admin));
            var at = jdbc.queryForObject("SELECT occurred_at FROM public.audit_logs WHERE actor_id=?", java.time.OffsetDateTime.class, admin).toInstant();
            assertFalse(at.isBefore(before.minusMillis(1))); assertFalse(at.isAfter(java.time.Instant.now()));
            assertEquals(0, at.getNano() % 1000);
            assertEquals(1, service.list(tag, null, 0, 20).totalElements());
            assertEquals(1, committedAuditRows(admin), "reads never audit");
        }

        @Test void aRealAuditInsertFaultAfterCourseAndReconciliationWritesRollsEverythingBack() {
            UUID id = course("published", "Rollback"); UUID joined = enrollment(id);
            String fault = "in20_fault_" + id.toString().replace("-", "");
            // The trigger runs at the real audit INSERT, under a valid Administrator. Its distinct
            // SQLSTATE proves both prior changes were visible in the SAME transaction before failure.
            jdbc.execute("CREATE FUNCTION public." + fault + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.target_reference='" + id + "' THEN "
                    + "IF (SELECT publication_status FROM public.courses WHERE id='" + id + "')='hidden' "
                    + "AND (SELECT status FROM public.enrollments WHERE id='" + joined + "')='active' THEN "
                    + "RAISE EXCEPTION 'controlled audit insert fault' USING ERRCODE='P2001'; "
                    + "ELSE RAISE EXCEPTION 'mutation did not precede audit' USING ERRCODE='P2002'; END IF; END IF; RETURN NEW; END $$");
            try {
                jdbc.execute("CREATE TRIGGER " + fault + " BEFORE INSERT ON public.audit_logs FOR EACH ROW EXECUTE FUNCTION public." + fault + "()");
                signIn(admin, Role.ADMIN);
                RuntimeException failure = assertThrows(RuntimeException.class, () -> service.hideCourse(id));
                assertEquals("P2001", sqlState(failure));
                assertEquals("published", storedStatus(id));
                assertEquals("completed", independentRead("SELECT status FROM public.enrollments WHERE id=?", joined).getFirst());
                assertEquals(0, committedAuditRows(admin));
            } finally {
                jdbc.execute("DROP TRIGGER IF EXISTS " + fault + " ON public.audit_logs");
                jdbc.execute("DROP FUNCTION public." + fault + "()");
            }
        }

        @Test void denialMissingAndAdmissionDoNotWriteOrAudit() {
            UUID id = course("published", "Admission"); signIn(instructor, Role.INSTRUCTOR);
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.hideCourse(id));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.list(tag, null, 0, 20));
            signIn(admin, Role.ADMIN);
            assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class, () -> service.requireCourseForHiding(UUID.randomUUID()));
            service.requireCourseForHiding(id);
            assertEquals("published", storedStatus(id)); assertEquals(0, committedAuditRows(admin));
        }

        @Test void listingUsesLiteralSubstringCOrderingPagingAndFormerInstructorNames() {
            UUID upper = course("published", "Alpha"); UUID lower = course("hidden", "alpha");
            UUID tie = course("hidden", "alpha"); UUID literal = course("hidden", "%_");
            jdbc.update("UPDATE public.users SET role='learner' WHERE id=?", instructor);
            signIn(admin, Role.ADMIN);
            var expected = new ArrayList<>(List.of(lower, tie));
            expected.sort(java.util.Comparator.comparing(UUID::toString));
            expected.addFirst(upper); expected.addFirst(literal);
            var all = service.list(tag.toLowerCase(), null, 0, 100);
            assertEquals(expected, all.content().stream().map(com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseItem::courseId).toList());
            assertTrue(all.content().stream().allMatch(row -> "IN20 Instructor".equals(row.instructorDisplayName())));
            assertEquals(List.of(literal), service.list("%_", "hidden", 0, 100).content().stream()
                    .filter(row -> row.courseId().equals(literal)).map(com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseItem::courseId).toList());
            assertEquals(upper, service.list(tag, null, 1, 1).content().getFirst().courseId());
            var beyond = service.list(tag, null, 20, 1);
            assertEquals(4, beyond.totalElements()); assertTrue(beyond.content().isEmpty());
            assertEquals("IN20 Instructor", service.hideCourse(lower).course().instructorDisplayName());
            org.mockito.Mockito.verify(identities, org.mockito.Mockito.never()).instructorDisplayNames(org.mockito.ArgumentMatchers.any());
        }

        @Test void aNameProviderFailureAfterAuditInsertAlsoRollsBackTheWholeHide() {
            UUID id = course("published", "Name failure"); enrollment(id);
            org.mockito.Mockito.doThrow(new IllegalStateException("controlled summary failure"))
                    .when(identities).userSummaries(java.util.Set.of(instructor));
            signIn(admin, Role.ADMIN);
            assertThrows(IllegalStateException.class, () -> service.hideCourse(id));
            assertEquals("published", storedStatus(id)); assertEquals(0, committedAuditRows(admin));
        }

        @Test void courseRowsAndAuthNamesShareOneReadOnlyRepeatableReadSnapshot() {
            UUID id = course("hidden", "Snapshot");
            org.mockito.Mockito.doAnswer(call -> {
                assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                // A separately committed writer changes BOTH projections after Course read, before Auth read.
                try (var db = dataSource.getConnection(); var stmt = db.prepareStatement("UPDATE public.users SET display_name='New name' WHERE id=?")) {
                    db.setAutoCommit(false); stmt.setObject(1, instructor); stmt.executeUpdate();
                    try (var title = db.prepareStatement("UPDATE public.courses SET title=? WHERE id=?")) {
                        title.setString(1, tag + " New title"); title.setObject(2, id); title.executeUpdate();
                    }
                    db.commit();
                }
                return call.callRealMethod();
            }).when(identities).userSummaries(java.util.Set.of(instructor));
            signIn(admin, Role.ADMIN);
            var row = service.list(tag, null, 0, 20).content().getFirst();
            assertEquals(tag + " Snapshot", row.title()); assertEquals("IN20 Instructor", row.instructorDisplayName());
            assertEquals("New name", independentRead("SELECT display_name FROM public.users WHERE id=?", instructor).getFirst());
            org.mockito.Mockito.verify(identities).userSummaries(java.util.Set.of(instructor));
        }

        @Test void ownerCanRepublishAndHideHasNoLessonCascade() {
            UUID id = course("published", "Republish");
            signIn(instructor, Role.INSTRUCTOR);
            var chapter = contentStructure.createCourseChapter(id, "Chapter");
            var lesson = contentStructure.createCourseLesson(id, chapter.id(), "Lesson", null);
            contentStructure.setCourseLessonPublication(id, lesson.id(), com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            signIn(admin, Role.ADMIN); service.hideCourse(id);
            assertEquals("published", independentRead("SELECT publication_status FROM public.lessons WHERE id=?", lesson.id()).getFirst());
            signIn(fixtureTarget, Role.LEARNER);
            assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class, () -> contentStructure.getLearnerLesson(id, lesson.id()));
            signIn(instructor, Role.INSTRUCTOR);
            contentManagement.setOwnedCoursePublication(id, com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            assertEquals("published", storedStatus(id)); assertEquals(1, committedAuditRows(admin));
        }

        @Test void simultaneousHidesSerializeAndAuditExactlyOnce() throws Exception {
            UUID id = course("published", "Race");
            var start = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Callable<Boolean> task = () -> {
                    signIn(admin, Role.ADMIN);
                    try { assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS)); return service.hideCourse(id).changed(); }
                    finally { SecurityContextHolder.clearContext(); }
                };
                var first = pool.submit(task); var second = pool.submit(task); start.countDown();
                assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS) ^ second.get(10, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(1, committedAuditRows(admin));
            } finally { start.countDown(); shutdown(pool); }
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings={"guard", "authoring", "coverage", "progress"})
        void hideWaitsForTheActualRootParticipantAndReReadsItsCommittedState(String operation) throws Exception {
            UUID id = course("published", "Before");
            signIn(instructor, Role.INSTRUCTOR);
            var chapter = contentStructure.createCourseChapter(id, "Chapter");
            var lesson = contentStructure.createCourseLesson(id, chapter.id(), "Lesson", null);
            contentStructure.setCourseLessonPublication(id, lesson.id(), com.codemastery.modules.course.domain.model.PublishStatus.PUBLISHED);
            enrollment(id);
            var held = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
            var pid = new java.util.concurrent.atomic.AtomicInteger();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var holder = pool.submit(() -> {
                    signIn(operation.equals("progress") ? fixtureTarget : instructor, operation.equals("progress") ? Role.LEARNER : Role.INSTRUCTOR);
                    try {
                        writable().executeWithoutResult(tx -> {
                            jdbc.execute("SET LOCAL statement_timeout='8s'");
                            if (operation.equals("progress")) progress.complete(id, lesson.id());
                            else contentManagement.withCourseConfigurationWrite(id, () -> {
                                if (operation.equals("authoring")) contentManagement.updateOwnedCourse(id, tag + " After", "Changed", null);
                                if (operation.equals("coverage")) {
                                    var skill = graph.createSkill(id, "IN20 coverage", null);
                                    graph.mapLesson(id, lesson.id(), skill.id());
                                }
                                return null;
                            });
                            held.countDown(); await(release);
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var hide = pool.submit(() -> {
                    signIn(admin, Role.ADMIN);
                    try { return writable().execute(tx -> {
                        jdbc.execute("SET LOCAL statement_timeout='8s'");
                        pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        return service.hideCourse(id);
                    }); } finally { SecurityContextHolder.clearContext(); }
                });
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (System.nanoTime() < deadline && !hide.isDone()) {
                    if (pid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0", Boolean.class, pid.get()))) { blocked = true; break; }
                    Thread.sleep(10);
                }
                assertTrue(blocked, "hide must be observed waiting on the held Course root");
                release.countDown(); holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
                var result = hide.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(result.changed()); assertEquals("hidden", storedStatus(id)); assertEquals(1, committedAuditRows(admin));
                if (operation.equals("authoring")) assertEquals(tag + " After", result.course().title());
            } finally { release.countDown(); shutdown(pool); }
        }

        private void await(java.util.concurrent.CountDownLatch latch) {
            try { if (!latch.await(8, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("IN20 barrier timed out"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        }

        private void shutdown(java.util.concurrent.ExecutorService pool) throws InterruptedException {
            pool.shutdownNow();
            if (!pool.awaitTermination(12, java.util.concurrent.TimeUnit.SECONDS)) {
                var failure = new IllegalStateException("IN20 workers did not stop; cleanup is unsafe");
                recordIsolationLoss(failure); throw failure;
            }
        }

        @Test void executableRuntimeMatrixSupportsListRootHideReconciliationAuditAndPreservesContainment() throws Exception {
            UUID id = course("published", "Runtime"); enrollment(id);
            String role = "in20_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            try {
                jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
                String deployment = java.nio.file.Files.readString(java.nio.file.Path.of("..", "infrastructure", "vm", "deploy.sh"));
                String matrix = deployment.split("RUNTIME_PRIVILEGE_MATRIX='\n", 2)[1].split("\n'", 2)[0];
                for (String line : matrix.split("\n")) {
                    String[] fields = line.trim().split("\\s+");
                    if (!fields[1].equals("-")) jdbc.execute("GRANT " + fields[1] + " ON public." + fields[0] + " TO " + role);
                    if (!fields[2].equals("-")) for (String columns : fields[2].split("\\+"))
                        jdbc.execute("GRANT " + columns + " ON public." + fields[0] + " TO " + role);
                }
                signIn(admin, Role.ADMIN);
                var read = new TransactionTemplate(transactionManager);
                read.setReadOnly(true); read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                int rows = read.execute(tx -> {
                    jdbc.execute("SET LOCAL ROLE " + role);
                    return service.list(tag, null, 0, 20).content().size();
                });
                assertEquals(1, rows);
                writable().executeWithoutResult(tx -> { jdbc.execute("SET LOCAL ROLE " + role); service.requireCourseForHiding(id); assertTrue(service.hideCourse(id).changed()); });
                assertEquals("hidden", storedStatus(id)); assertEquals(1, committedAuditRows(admin));
                for (String sql : List.of("UPDATE audit_logs SET action=action WHERE false", "DELETE FROM audit_logs WHERE false",
                        "UPDATE courses SET instructor_id=instructor_id WHERE false", "UPDATE enrollments SET user_id=user_id WHERE false",
                        "UPDATE users SET role=role WHERE false", "TRUNCATE audit_logs", "CREATE TABLE public.in20_forbidden(id integer)")) {
                    RuntimeException denied = assertThrows(RuntimeException.class, () -> writable().executeWithoutResult(tx -> {
                        jdbc.execute("SET LOCAL ROLE " + role); jdbc.execute(sql);
                    }));
                    assertEquals("42501", sqlState(denied), sql);
                }
            } finally { jdbc.execute("DROP OWNED BY " + role); jdbc.execute("DROP ROLE " + role); }
        }
    }

}
