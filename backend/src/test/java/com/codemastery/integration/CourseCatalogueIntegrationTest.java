package com.codemastery.integration;

import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.model.PublishStatus;
import com.codemastery.modules.course.domain.repository.CourseRepository;
import com.codemastery.modules.course.domain.repository.CourseRepository.AccessSnapshot;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL provider verification, not Auth/security-chain acceptance.
 * Uses the existing shared disposable database and test-owned committed fixtures.
 */
class CourseCatalogueIntegrationTest extends AbstractIntegrationTest {
    private static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

    @DynamicPropertySource
    static void inspectSnapshotStatements(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> SnapshotStatementInspector.class.getName());
    }

    public static class SnapshotStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            List<String> statements = STATEMENTS.get();
            if (statements != null) statements.add(sql);
            return sql;
        }
    }

    @Autowired CourseRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();
    private final UUID learner = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID course = UUID.randomUUID();
    private final UUID otherCourse = UUID.randomUUID();
    private final UUID chapter = UUID.randomUUID();
    private final UUID otherChapter = UUID.randomUUID();
    private final UUID lesson = UUID.randomUUID();
    private final UUID hiddenLesson = UUID.randomUUID();
    private final UUID foreignLesson = UUID.randomUUID();
    private final UUID material = UUID.randomUUID();
    private final UUID hiddenMaterial = UUID.randomUUID();
    private final UUID foreignMaterial = UUID.randomUUID();
    private final UUID enrollment = UUID.randomUUID();

    @BeforeEach
    void fixtures() {
        user(owner, "instructor");
        user(otherOwner, "instructor");
        user(learner, "learner");
        user(outsider, "learner");
        jdbc.update("INSERT INTO public.courses (id,instructor_id,title,description,publication_status,adaptive_enabled) "
                + "VALUES (?,?,'Course','Description','published',false),(?,?,'Other','Description','hidden',false)",
                course, owner, otherCourse, otherOwner);
        jdbc.update("INSERT INTO public.chapters (id,course_id,title,position) VALUES (?,?,'Chapter',1),(?,?,'Other',1)",
                chapter, course, otherChapter, otherCourse);
        jdbc.update("INSERT INTO public.lessons (id,chapter_id,title,position,publication_status) "
                + "VALUES (?,?,'Lesson',1,'published'),(?,?,'Hidden',2,'hidden'),(?,?,'Foreign',1,'published')",
                lesson, chapter, hiddenLesson, chapter, foreignLesson, otherChapter);
        jdbc.update("INSERT INTO public.learning_materials "
                + "(id,lesson_id,title,material_type,publication_status,text_content) "
                + "VALUES (?,?,'Text','text','published','Body'),(?,?,'Hidden','text','hidden','Body'),"
                + "(?,?,'Foreign','text','published','Body')",
                material, lesson, hiddenMaterial, lesson, foreignMaterial, foreignLesson);
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status) VALUES (?,?,?,'active')",
                enrollment, learner, course);
    }

    private void user(UUID id, String role) {
        // Test-owned FK data only; no Auth private repository or usable login credentials.
        jdbc.update("INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) "
                + "VALUES (?,?,'not-an-authentication-fixture','Fixture',?,'active')",
                id, id + "@example.test", role);
    }

    @AfterEach
    void cleanup() {
        // Only this test's UUIDs; committed/threaded work is not covered by test rollback.
        jdbc.update("DELETE FROM public.enrollments WHERE id = ?", enrollment);
        jdbc.update("DELETE FROM public.learning_materials WHERE id IN (?,?,?)", material, hiddenMaterial, foreignMaterial);
        jdbc.update("DELETE FROM public.lessons WHERE id IN (?,?,?)", lesson, hiddenLesson, foreignLesson);
        jdbc.update("DELETE FROM public.chapters WHERE id IN (?,?)", chapter, otherChapter);
        jdbc.update("DELETE FROM public.courses WHERE id IN (?,?)", course, otherCourse);
        jdbc.update("DELETE FROM public.users WHERE id IN (?,?,?,?)", owner, otherOwner, learner, outsider);
        STATEMENTS.remove();
    }

    private <T> T oneStatement(Supplier<T> read) {
        var statements = new ArrayList<String>();
        STATEMENTS.set(statements);
        try {
            T result = read.get();
            assertEquals(1, statements.size(), "Admission facts must use one statement, without follow-up loads");
            assertTrue(statements.getFirst().stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("select"));
            return result;
        } finally {
            STATEMENTS.remove();
        }
    }

    private AccessSnapshot selected(UUID actor, UUID selectedLesson, UUID selectedMaterial) {
        return oneStatement(() -> repository.findCourseAccessSnapshot(actor, course, selectedLesson, selectedMaterial))
                .orElseThrow();
    }

    @Test void scalarAliasesUuidsAndNullableParametersBindAgainstPostgresql() {
        AccessSnapshot row = selected(learner, lesson, material);
        assertEquals(new AccessSnapshot(course, owner, PublishStatus.PUBLISHED,
                lesson, PublishStatus.PUBLISHED, material, PublishStatus.PUBLISHED, EnrollmentStatus.ACTIVE), row);
        assertEquals(new AccessSnapshot(course, owner, PublishStatus.PUBLISHED,
                null, null, null, null, EnrollmentStatus.ACTIVE), selected(learner, null, null));
        assertNull(selected(learner, lesson, null).materialId());
        assertNull(selected(learner, null, material).materialId());
    }

    @Test void missingAndForeignDescendantsKeepCourseWithoutDisclosingForeignAncestry() {
        for (UUID selectedLesson : List.of(UUID.randomUUID(), foreignLesson)) {
            AccessSnapshot row = selected(learner, selectedLesson, foreignMaterial);
            assertEquals(course, row.courseId());
            assertEquals(owner, row.instructorId());
            assertNull(row.lessonId());
            assertNull(row.lessonStatus());
            assertNull(row.materialId());
        }
        assertNull(selected(learner, lesson, foreignMaterial).materialId());
        assertNull(selected(learner, lesson, UUID.randomUUID()).materialId());
        assertNull(selected(learner, hiddenLesson, material).materialId());
    }

    @Test void hiddenResourcesRemainAvailableAsFactsForOwnerPreview() {
        jdbc.update("UPDATE public.courses SET publication_status = 'hidden' WHERE id = ?", course);
        var row = selected(owner, hiddenLesson, null);
        assertEquals(owner, row.instructorId());
        assertEquals(PublishStatus.HIDDEN, row.courseStatus());
        assertEquals(PublishStatus.HIDDEN, row.lessonStatus());
        assertNull(row.enrollmentStatus());
        assertEquals(PublishStatus.HIDDEN, selected(owner, lesson, hiddenMaterial).materialStatus());
        // The provider does not apply role policies or conceal facts before service precedence.
        assertEquals(PublishStatus.HIDDEN, selected(outsider, hiddenLesson, null).courseStatus());
    }

    @ParameterizedTest @EnumSource(EnrollmentStatus.class)
    void membershipIsActorScopedAndCannotMultiplyRows(EnrollmentStatus status) {
        jdbc.update("UPDATE public.enrollments SET status = ? WHERE id = ?", status.getStatus(), enrollment);
        assertEquals(status, selected(learner, lesson, null).enrollmentStatus());
        assertNull(selected(outsider, lesson, null).enrollmentStatus());
        assertNull(selected(owner, lesson, null).enrollmentStatus());
    }

    @Test void missingMembershipDoesNotHideCourseOrDescendants() {
        jdbc.update("DELETE FROM public.enrollments WHERE id = ?", enrollment);
        AccessSnapshot row = selected(learner, lesson, material);
        assertEquals(course, row.courseId());
        assertEquals(lesson, row.lessonId());
        assertEquals(material, row.materialId());
        assertNull(row.enrollmentStatus());
    }

    @Test void lessonRootResolvesActualCourseWithoutExportingContent() {
        AccessSnapshot row = oneStatement(() -> repository.findLessonAccessSnapshot(learner, lesson)).orElseThrow();
        assertEquals(course, row.courseId());
        assertEquals(owner, row.instructorId());
        assertEquals(EnrollmentStatus.ACTIVE, row.enrollmentStatus());
        assertNull(row.materialId());
        AccessSnapshot foreign = oneStatement(() -> repository.findLessonAccessSnapshot(learner, foreignLesson))
                .orElseThrow();
        assertEquals(otherCourse, foreign.courseId());
        assertEquals(otherOwner, foreign.instructorId());
        assertNull(foreign.enrollmentStatus());
    }

    @Test void missingRootsReturnEmptyWithOneStatement() {
        assertEquals(Optional.empty(), oneStatement(() -> repository.findCourseAccessSnapshot(
                learner, UUID.randomUUID(), lesson, material)));
        assertEquals(Optional.empty(), oneStatement(() -> repository.findLessonAccessSnapshot(learner, UUID.randomUUID())));
    }

    @Test void concurrentAtomicChangesNeverProducePublishedActiveHybrid() throws Exception {
        jdbc.update("UPDATE public.enrollments SET status = 'left' WHERE id = ?", enrollment);
        // State A denies by membership. State B denies by publication. Both facts must travel together.
        try (Connection writer = dataSource.getConnection()) {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, writer.getTransactionIsolation());
            writer.setAutoCommit(false);
            try {
                try (var statement = writer.prepareStatement(
                        "UPDATE public.courses SET publication_status = 'hidden' WHERE id = ?")) {
                    statement.setObject(1, course);
                    statement.executeUpdate();
                }
                try (var statement = writer.prepareStatement("UPDATE public.enrollments SET status = 'active' WHERE id = ?")) {
                    statement.setObject(1, enrollment);
                    statement.executeUpdate();
                }
                try (var readers = Executors.newSingleThreadExecutor()) {
                    // No timing sleep: writer has completed both updates and deliberately holds commit.
                    AccessSnapshot beforeCommit = readers.submit(() -> selected(learner, lesson, material))
                            .get(10, TimeUnit.SECONDS);
                    assertEquals(PublishStatus.PUBLISHED, beforeCommit.courseStatus());
                    assertEquals(EnrollmentStatus.LEFT, beforeCommit.enrollmentStatus());
                    writer.commit();
                    AccessSnapshot afterCommit = readers.submit(() -> selected(learner, lesson, material))
                            .get(10, TimeUnit.SECONDS);
                    assertEquals(PublishStatus.HIDDEN, afterCommit.courseStatus());
                    assertEquals(EnrollmentStatus.ACTIVE, afterCommit.enrollmentStatus());
                }
            } finally {
                writer.rollback();
            }
        }
    }
}
