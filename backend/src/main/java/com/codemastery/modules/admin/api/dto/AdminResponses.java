package com.codemastery.modules.admin.api.dto;

import com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseItem;
import com.codemastery.modules.admin.application.service.AdminCourseService.AdminCourseOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** Response DTOs of the Admin REST boundary. Persistence entities are never exposed. */
public final class AdminResponses {

    private AdminResponses() {
    }

    /** B7AdminCourse. {@code instructorDisplayName} is always present and null for a missing account. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminCourse(UUID courseId, String title, String publicationStatus, UUID instructorId,
                              String instructorDisplayName) {

        public static AdminCourse from(AdminCourseItem item) {
            return new AdminCourse(item.courseId(), item.title(), item.publicationStatus(), item.instructorId(),
                    item.instructorDisplayName());
        }
    }

    /** B7AdminCourseChange. */
    public record AdminCourseChange(AdminCourse course, boolean changed) {

        public static AdminCourseChange from(AdminCourseOutcome outcome) {
            return new AdminCourseChange(AdminCourse.from(outcome.course()), outcome.changed());
        }
    }
}
