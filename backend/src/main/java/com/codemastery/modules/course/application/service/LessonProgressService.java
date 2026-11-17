package com.codemastery.modules.course.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.course.api.dto.CourseProgressResponse;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.LearnerResume;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.Resume;
import com.codemastery.modules.course.api.dto.CourseProgressResponse.Summary;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonProgressService {

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseProgressResponse progress(UUID courseId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseProgressResponse complete(UUID courseId, UUID lessonId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Summary> myCourseProgress(int page, int size) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Resume resume(UUID courseId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resume visit(UUID courseId, UUID lessonId) {
        throw new UnsupportedOperationException();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LearnerResume myResume() {
        throw new UnsupportedOperationException();
    }
}
