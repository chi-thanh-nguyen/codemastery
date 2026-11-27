package com.codemastery.modules.interaction.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.ContentReportRepository;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CT30.reports intake (IN06.report). Every operation first applies CT03 Lesson admission, then
 * resolves the target, then applies the body rules, in the order of ADR012 section 12: a report
 * never hides anything by itself and duplicates are allowed.
 */
@Service
public class ContentReportService {

    private final ContentReportRepository reports;
    private final DiscussionPostRepository posts;
    private final CourseAccessService courseAccess;
    private final UserAccountService identities;
    private final Clock clock;

    /** Production seam: UTC. There is no shared Clock bean (ADR008 section 6). */
    @Autowired
    public ContentReportService(ContentReportRepository reports, DiscussionPostRepository posts,
                                CourseAccessService courseAccess, UserAccountService identities) {
        this(reports, posts, courseAccess, identities, Clock.systemUTC());
    }

    /** Test seam: a fixed or mutable Clock. */
    ContentReportService(ContentReportRepository reports, DiscussionPostRepository posts,
                         CourseAccessService courseAccess, UserAccountService identities, Clock clock) {
        this.reports = Objects.requireNonNull(reports);
        this.posts = Objects.requireNonNull(posts);
        this.courseAccess = Objects.requireNonNull(courseAccess);
        this.identities = Objects.requireNonNull(identities);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Reports the Lesson itself. {@code reason} is read only after admission so that a body error
     * can never precede an access decision (stage 4 after stage 3).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContentReport reportLesson(UUID lessonId, Supplier<String> reason) {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(reason, "reason");
        courseAccess.requireLessonReadAccess(lessonId);
        String text = requireReason(reason.get());
        return create(null, lessonId, text);
    }

    /** Reports one visible post of the addressed Lesson; anything else is a concealed 404. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContentReport reportPost(UUID lessonId, UUID postId, Supplier<String> reason) {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(reason, "reason");
        courseAccess.requireLessonReadAccess(lessonId);
        DiscussionPost post = posts.findById(postId)
                .filter(candidate -> isVisibleIn(candidate, lessonId))
                .orElseThrow(ContentReportService::unavailable);
        String text = requireReason(reason.get());
        return create(post.getId(), null, text);
    }

    /** A post is visible when it is not hidden and, for a reply, its question is visible too. */
    private boolean isVisibleIn(DiscussionPost post, UUID lessonId) {
        if (!lessonId.equals(post.getLessonId()) || post.isHidden()) {
            return false;
        }
        if (post.getParentPostId() == null) {
            return true;
        }
        return posts.findById(post.getParentPostId())
                .filter(parent -> lessonId.equals(parent.getLessonId()) && !parent.isHidden())
                .isPresent();
    }

    private ContentReport create(UUID postId, UUID lessonId, String reason) {
        UUID reporter = identities.requireCurrentIdentity().userId();
        ContentReport report = new ContentReport(
                UUID.randomUUID(), reporter, postId, lessonId, reason, ContentReport.Status.OPEN,
                clock.instant().truncatedTo(ChronoUnit.MICROS), null);
        return reports.save(report);
    }

    /** The reason is required and must contain a non-whitespace character; it is stored verbatim. */
    private static String requireReason(String reason) {
        if (reason == null || reason.codePoints().allMatch(ContentReportService::isWhitespace)) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid report reason");
        }
        return reason;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Resource not available");
    }
}
