package com.codemastery.modules.course.application.service;

import com.codemastery.modules.course.api.dto.CourseRequest;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.ChapterWrite;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.LessonWrite;
import com.codemastery.modules.course.api.dto.CourseStructureRequests.SiblingOrder;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.LearnerLesson;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedChapter;
import com.codemastery.modules.course.api.dto.CourseStructureResponses.OwnedLesson;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseStructureService {
    private final CourseManagementService management;

    public CourseStructureService(CourseManagementService management) {
        this.management = management;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedChapter createCourseChapter(UUID courseId, ChapterWrite request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedChapter updateCourseChapter(UUID courseId, UUID chapterId, ChapterWrite request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SiblingOrder orderCourseChapters(UUID courseId, SiblingOrder request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedLesson createCourseLesson(UUID courseId, UUID chapterId, LessonWrite request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OwnedLesson getOwnedLesson(UUID courseId, UUID lessonId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedLesson updateCourseLesson(UUID courseId, UUID lessonId, LessonWrite request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SiblingOrder orderChapterLessons(UUID courseId, UUID chapterId, SiblingOrder request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnedLesson setCourseLessonPublication(UUID courseId, UUID lessonId, CourseRequest.Publication request) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearnerLesson getLearnerLesson(UUID courseId, UUID lessonId) {
        throw new UnsupportedOperationException();
    }
}
