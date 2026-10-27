package com.codemastery.modules.course.domain.repository;

import com.codemastery.modules.course.domain.model.Enrollment;
import com.codemastery.modules.course.domain.model.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

/** Course-internal CT22.membership persistence; callers own the transaction. */
public interface EnrollmentRepository {
    boolean lockPublishedCourse(UUID courseId);
    Optional<Enrollment> find(UUID userId, UUID courseId);
    void insert(Enrollment enrollment);
    void updateStatus(UUID enrollmentId, EnrollmentStatus status);
    Page<MembershipCourse> list(UUID userId, String status, Pageable page);

    record MembershipCourse(UUID id, String title, String description, UUID instructorId, EnrollmentStatus status) {}
}
