package com.codemastery.modules.admin.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.domain.model.AuditAction;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.application.service.UserAccountService.UserSummary;
import com.codemastery.modules.course.application.service.CourseManagementService;
import com.codemastery.modules.course.application.service.CourseManagementService.AdminCourseView;
import com.codemastery.modules.course.application.service.CourseManagementService.CourseChange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administrator course administration (IN20; CT12.course, CT12.rest, CT31 in ADR012 sections 10.2, 10.3, 7).
 *
 * <p>Admin owns only orchestration. Course data is read and changed solely through Course's public
 * {@link CourseManagementService} methods (list and hide; nothing else is approved), Instructor names come
 * from Auth's {@code userSummaries}, and every actual change is audited through {@link AuditLogService}.
 */
@Service
public class AdminCourseService {

    private static final Set<String> PUBLICATION_STATUSES = Set.of("hidden", "published");

    private final CourseManagementService courses;
    private final UserAccountService identities;
    private final AuditLogService audit;

    public AdminCourseService(CourseManagementService courses, UserAccountService identities, AuditLogService audit) {
        this.courses = Objects.requireNonNull(courses);
        this.identities = Objects.requireNonNull(identities);
        this.audit = Objects.requireNonNull(audit);
    }

    /** One row of the Administrator Course list ({@code B7AdminCourse}); the name is null for a missing account. */
    public record AdminCourseItem(UUID courseId, String title, String publicationStatus, UUID instructorId,
                                  String instructorDisplayName) {}

    /** Result of a hide request ({@code B7AdminCourseChange}); {@code changed} is false when already hidden. */
    public record AdminCourseOutcome(AdminCourseItem course, boolean changed) {}

    /** Stage 3 role decision (ADR012 section 12): only an Administrator passes; there is no inheritance. */
    public void requireAdministrator() {
        CurrentIdentity actor = identities.requireCurrentIdentity();
        if (actor.role() != IdentityRole.ADMINISTRATOR) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Hidden and published Courses, title substring and status filters, title order. Malformed values are
     * 400 (stage 2) before the role 403 (stage 3). One read-only REPEATABLE READ snapshot covers Course's
     * page and the Auth name lookup, which Course's own read joins.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AdminCourseItem> list(String query, String publicationStatus, Integer page, Integer size) {
        int pageNumber = page == null ? PageResponse.DEFAULT_PAGE : page;
        int pageSize = size == null ? PageResponse.DEFAULT_SIZE : size;
        PageResponse.validateRequest(pageNumber, pageSize);
        if (publicationStatus != null && !PUBLICATION_STATUSES.contains(publicationStatus)) {
            throw new BusinessRuleException("Invalid publication status");
        }
        requireAdministrator();

        PageResponse<AdminCourseView> result =
                courses.listCoursesForAdministration(query, publicationStatus, pageNumber, pageSize);
        Map<UUID, UserSummary> names = instructorNames(result.content());
        List<AdminCourseItem> items = result.content().stream().map(view -> item(view, names)).toList();
        return new PageResponse<>(items, result.page(), result.size(), result.totalElements());
    }

    /**
     * Hides one Course in a single READ COMMITTED read-write transaction (CT31.consistency): Course's public
     * mutation joins it, taking the Course root lock first, and only an actual change inserts the
     * {@code course_hidden} audit row. Any failure rolls back both. Already hidden is a no-op without audit.
     * A missing Course is Course's 404.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminCourseOutcome hideCourse(UUID courseId) {
        Objects.requireNonNull(courseId, "courseId");
        requireAdministrator();

        CourseChange change = courses.hideCourseAsAdministrator(courseId);
        if (change.changed()) {
            audit.record(AuditAction.COURSE_HIDDEN, courseId);
        }
        Map<UUID, UserSummary> names = instructorNames(List.of(change.course()));
        return new AdminCourseOutcome(item(change.course(), names), change.changed());
    }

    /** Only the instructor IDs of the rows being returned (at most 100 per page), in one call. */
    private Map<UUID, UserSummary> instructorNames(List<AdminCourseView> views) {
        Set<UUID> instructorIds = views.stream().map(AdminCourseView::instructorId).collect(Collectors.toSet());
        return instructorIds.isEmpty() ? Map.of() : identities.userSummaries(instructorIds);
    }

    private static AdminCourseItem item(AdminCourseView view, Map<UUID, UserSummary> names) {
        UserSummary instructor = names.get(view.instructorId());
        return new AdminCourseItem(view.courseId(), view.title(), view.publicationStatus(), view.instructorId(),
                instructor == null ? null : instructor.displayName());
    }
}
