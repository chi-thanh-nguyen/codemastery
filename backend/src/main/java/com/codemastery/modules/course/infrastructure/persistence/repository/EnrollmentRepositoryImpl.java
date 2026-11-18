package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EnrollmentRepositoryImpl implements EnrollmentRepository {
    private final JdbcTemplate jdbc;
    public EnrollmentRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // CT13.reads. "scope" selects the Courses; every figure is aggregated once per Course. R is the set of
    // currently published Lessons of the Course; a joined learner completes it when R is nonempty and every
    // R member has a completed=true progress row of that learner's Enrollment (mastery_skipped implies it).
    private static final String METRICS = "WITH scope AS (%s), "
            + "required AS (SELECT ch.course_id, l.id AS lesson_id FROM public.lessons l "
            + "JOIN public.chapters ch ON ch.id = l.chapter_id JOIN scope s ON s.id = ch.course_id "
            + "WHERE l.publication_status = 'published'), "
            + "required_total AS (SELECT course_id, count(*) AS total FROM required GROUP BY course_id), "
            + "memberships AS (SELECT e.id, e.course_id, e.status FROM public.enrollments e JOIN scope s ON s.id = e.course_id), "
            + "completed_required AS (SELECT m.id, count(*) AS done FROM memberships m "
            + "JOIN public.lesson_progress p ON p.enrollment_id = m.id AND p.completed "
            + "JOIN required r ON r.lesson_id = p.lesson_id AND r.course_id = m.course_id "
            + "WHERE m.status IN ('active','completed') GROUP BY m.id) "
            + "SELECT s.id, s.title, s.publication_status, "
            + "count(m.id) FILTER (WHERE m.status = 'active') AS active_count, "
            + "count(m.id) FILTER (WHERE m.status = 'completed') AS completed_count, "
            + "count(m.id) FILTER (WHERE m.status = 'left') AS left_count, "
            + "count(m.id) FILTER (WHERE m.status IN ('active','completed') AND t.total > 0 AND c.done = t.total) AS completed_learners "
            + "FROM scope s LEFT JOIN required_total t ON t.course_id = s.id "
            + "LEFT JOIN memberships m ON m.course_id = s.id LEFT JOIN completed_required c ON c.id = m.id "
            + "GROUP BY s.id, s.title, s.publication_status, t.total "
            + "ORDER BY s.title COLLATE \"C\" ASC, s.id ASC";

    @Override public Page<EnrollmentMetricsRow> enrollmentMetrics(Pageable page) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM public.courses", Long.class);
        var rows = jdbc.query(METRICS.formatted("SELECT id, title, publication_status FROM public.courses "
                + "ORDER BY title COLLATE \"C\" ASC, id ASC LIMIT ? OFFSET ?"), EnrollmentRepositoryImpl::metricsRow,
                page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total == null ? 0 : total);
    }
    @Override public Optional<EnrollmentMetricsRow> enrollmentMetrics(UUID courseId) {
        return jdbc.query(METRICS.formatted("SELECT id, title, publication_status FROM public.courses WHERE id = ?"),
                EnrollmentRepositoryImpl::metricsRow, courseId).stream().findFirst();
    }
    @Override public java.util.Map<UUID, java.util.Set<UUID>> joinedLearnerIds(java.util.Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) return java.util.Map.of();
        var ids = java.util.List.copyOf(new java.util.LinkedHashSet<>(courseIds));
        var learners = new java.util.HashMap<UUID, java.util.Set<UUID>>();
        jdbc.query("SELECT course_id, user_id FROM public.enrollments WHERE status IN ('active','completed') AND course_id IN ("
                + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> learners.computeIfAbsent(rs.getObject("course_id", UUID.class),
                        course -> new java.util.HashSet<>()).add(rs.getObject("user_id", UUID.class)), ids.toArray());
        var result = new java.util.HashMap<UUID, java.util.Set<UUID>>();
        learners.forEach((course, users) -> result.put(course, java.util.Set.copyOf(users)));
        return java.util.Map.copyOf(result);
    }
    private static EnrollmentMetricsRow metricsRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new EnrollmentMetricsRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("publication_status"),
                rs.getLong("active_count"), rs.getLong("completed_count"), rs.getLong("left_count"), rs.getLong("completed_learners"));
    }

    @Override public boolean lockPublishedCourse(UUID courseId) {
        return !jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? AND publication_status = 'published' FOR UPDATE",
                UUID.class, courseId).isEmpty();
    }
    @Override public Optional<Enrollment> find(UUID userId, UUID courseId) {
        return jdbc.query("SELECT * FROM public.enrollments WHERE user_id = ? AND course_id = ?", (rs, row) ->
                new Enrollment(rs.getObject("id", UUID.class), userId, courseId, status(rs.getString("status")),
                        rs.getObject("resume_lesson_id", UUID.class)), userId, courseId).stream().findFirst();
    }
    @Override public void insert(Enrollment enrollment) {
        jdbc.update("INSERT INTO public.enrollments (id,user_id,course_id,status,resume_lesson_id) VALUES (?,?,?,?,?)",
                enrollment.id(), enrollment.userId(), enrollment.courseId(), enrollment.status().getStatus(), enrollment.resumeLessonId());
    }
    @Override public void updateStatus(UUID enrollmentId, EnrollmentStatus status) {
        if (jdbc.update("UPDATE public.enrollments SET status = ? WHERE id = ?", status.getStatus(), enrollmentId) != 1)
            throw new IllegalStateException("Membership update lost its row");
    }
    @Override public Page<MembershipCourse> list(UUID userId, String status, Pageable page) {
        String predicate = " FROM public.enrollments e JOIN public.courses c ON c.id=e.course_id "
                + "WHERE e.user_id=? AND c.publication_status='published' "
                + "AND (?='all' OR (?='joined' AND e.status IN ('active','completed')) OR e.status=?)";
        Long total = jdbc.queryForObject("SELECT count(*)" + predicate, Long.class, userId, status, status, status);
        var rows = jdbc.query("SELECT c.id,c.title,c.description,c.instructor_id,e.status" + predicate
                + " ORDER BY c.title COLLATE \"C\" ASC,c.id ASC LIMIT ? OFFSET ?", (rs, row) -> new MembershipCourse(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getObject("instructor_id", UUID.class), status(rs.getString("status"))),
                userId, status, status, status, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total == null ? 0 : total);
    }
    // CT22.progress / CT22.resume. Canonical order: Chapter.position, then Lesson.position.
    private static final String PUBLISHED_LESSONS = " FROM public.lessons l JOIN public.chapters ch ON ch.id=l.chapter_id "
            + "WHERE ch.course_id=c.id AND l.publication_status='published'";

    @Override public boolean lockCourse(UUID courseId) {
        return !jdbc.queryForList("SELECT id FROM public.courses WHERE id = ? FOR UPDATE", UUID.class, courseId).isEmpty();
    }
    @Override public Optional<Enrollment> lockEnrollment(UUID userId, UUID courseId) {
        return jdbc.query("SELECT id,status,resume_lesson_id FROM public.enrollments WHERE user_id = ? AND course_id = ? FOR UPDATE",
                (rs, row) -> new Enrollment(rs.getObject("id", UUID.class), userId, courseId, status(rs.getString("status")),
                        rs.getObject("resume_lesson_id", UUID.class)), userId, courseId).stream().findFirst();
    }
    @Override public Optional<String> courseTitle(UUID courseId) {
        return jdbc.queryForList("SELECT title FROM public.courses WHERE id = ?", String.class, courseId).stream().findFirst();
    }
    @Override public List<LessonFlags> publishedLessonFlags(UUID courseId, UUID enrollmentId) {
        return jdbc.query("SELECT l.id, COALESCE(p.completed,false) AS completed, COALESCE(p.mastery_skipped,false) AS skipped "
                + "FROM public.lessons l JOIN public.chapters ch ON ch.id=l.chapter_id "
                + "LEFT JOIN public.lesson_progress p ON p.lesson_id=l.id AND p.enrollment_id=? "
                + "WHERE ch.course_id=? AND l.publication_status='published' ORDER BY ch.position ASC, l.position ASC",
                (rs, row) -> new LessonFlags(rs.getObject("id", UUID.class), rs.getBoolean("completed"), rs.getBoolean("skipped")),
                enrollmentId, courseId);
    }
    @Override public void markCompleted(UUID enrollmentId, UUID lessonId) {
        // Monotonic: only completed is written; an existing mastery_skipped=true is untouched.
        if (jdbc.update("INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) "
                + "VALUES (?,?,?,true,false) ON CONFLICT (enrollment_id,lesson_id) DO UPDATE SET completed=true",
                UUID.randomUUID(), enrollmentId, lessonId) != 1) throw new IllegalStateException("Progress write lost its row");
    }
    @Override public boolean isPublishedCourseLesson(UUID courseId, UUID lessonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.lessons l JOIN public.chapters ch "
                + "ON ch.id=l.chapter_id WHERE l.id=? AND ch.course_id=? AND l.publication_status='published')",
                Boolean.class, lessonId, courseId));
    }
    @Override public void recordVisit(UUID enrollmentId, UUID lessonId) {
        if (jdbc.update("UPDATE public.enrollments SET resume_lesson_id = ?, last_visited_at = clock_timestamp() WHERE id = ?",
                lessonId, enrollmentId) != 1) throw new IllegalStateException("Visit update lost its row");
    }
    @Override public Page<ProgressSummary> activeProgress(UUID userId, Pageable page) {
        String predicate = " FROM public.enrollments e JOIN public.courses c ON c.id=e.course_id "
                + "WHERE e.user_id=? AND e.status='active' AND c.publication_status='published'";
        Long total = jdbc.queryForObject("SELECT count(*)" + predicate, Long.class, userId);
        var rows = jdbc.query("SELECT c.id, c.title, "
                + "(SELECT count(*)" + PUBLISHED_LESSONS + ") AS total_lessons, "
                + "(SELECT count(*)" + PUBLISHED_LESSONS + " AND EXISTS (SELECT 1 FROM public.lesson_progress p "
                + "WHERE p.enrollment_id=e.id AND p.lesson_id=l.id AND p.completed)) AS completed_lessons, "
                + "(SELECT l.id" + PUBLISHED_LESSONS + " AND NOT EXISTS (SELECT 1 FROM public.lesson_progress p "
                + "WHERE p.enrollment_id=e.id AND p.lesson_id=l.id AND p.completed) "
                + "ORDER BY ch.position ASC, l.position ASC LIMIT 1) AS next_lesson_id"
                + predicate + " ORDER BY c.title COLLATE \"C\" ASC, c.id ASC LIMIT ? OFFSET ?",
                (rs, row) -> new ProgressSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getLong("total_lessons"), rs.getLong("completed_lessons"), rs.getObject("next_lesson_id", UUID.class)),
                userId, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total == null ? 0 : total);
    }
    @Override public Optional<VisitPosition> latestVisit(UUID userId) {
        return jdbc.query("SELECT e.course_id, e.resume_lesson_id, e.last_visited_at FROM public.enrollments e "
                + "JOIN public.courses c ON c.id=e.course_id AND c.publication_status='published' "
                + "JOIN public.lessons l ON l.id=e.resume_lesson_id AND l.publication_status='published' "
                + "JOIN public.chapters ch ON ch.id=l.chapter_id AND ch.course_id=e.course_id "
                + "WHERE e.user_id=? AND e.status='active' AND e.last_visited_at IS NOT NULL "
                + "ORDER BY e.last_visited_at DESC, e.course_id ASC LIMIT 1",
                (rs, row) -> new VisitPosition(rs.getObject("course_id", UUID.class), rs.getObject("resume_lesson_id", UUID.class),
                        rs.getObject("last_visited_at", java.time.OffsetDateTime.class).toInstant()), userId).stream().findFirst();
    }
    private static EnrollmentStatus status(String value) {
        return switch (value) {
            case "active" -> EnrollmentStatus.ACTIVE;
            case "completed" -> EnrollmentStatus.COMPLETED;
            case "left" -> EnrollmentStatus.LEFT;
            default -> throw new IllegalStateException("Invalid membership status");
        };
    }
}
