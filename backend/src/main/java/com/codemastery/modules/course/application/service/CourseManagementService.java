package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourse;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedCourseSummary;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseManagementService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<OwnedCourseSummary> listOwnedCourses(int page, int size) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedCourse createOwnedCourse(CourseRequest request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OwnedCourse getOwnedCourse(UUID courseId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedCourse updateOwnedCourse(UUID courseId, CourseRequest request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedCourse setOwnedCoursePublication(UUID courseId, CourseRequest.Publication request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AdminCourseView> listCoursesForAdministration(String query, String publicationStatus,
                                                                      int page, int size) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseChange hideCourseAsAdministrator(UUID courseId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LessonChange hideLessonAsAdministrator(UUID lessonId) {
        throw new UnsupportedOperationException();
    }

    public record AdminCourseView(UUID courseId, String title, String publicationStatus, UUID instructorId) {}
    public record CourseChange(AdminCourseView course, boolean changed) {}
    public record LessonChange(UUID courseId, UUID lessonId, String publicationStatus, boolean changed) {}
}
