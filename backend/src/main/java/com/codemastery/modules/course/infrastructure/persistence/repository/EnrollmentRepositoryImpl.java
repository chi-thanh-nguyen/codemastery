package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import com.codemastery.modules.course.domain.repository.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EnrollmentRepositoryImpl implements EnrollmentRepository {
    private final JdbcTemplate jdbc;
    public EnrollmentRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
    private static EnrollmentStatus status(String value) {
        return switch (value) {
            case "active" -> EnrollmentStatus.ACTIVE;
            case "completed" -> EnrollmentStatus.COMPLETED;
            case "left" -> EnrollmentStatus.LEFT;
            default -> throw new IllegalStateException("Invalid membership status");
        };
    }
}
