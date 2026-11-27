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
 * CT30 intake and the CT32 Interaction-owned moderation handoff. Intake first applies CT03 Lesson admission, then
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

    /** CT32 Interaction-owned queue. Admin adds identity and Course context projections. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public com.codemastery.common.api.PageResponse<ModerationReport> listReportsForModeration(String status, int page, int size) {
        com.codemastery.common.api.PageResponse.validateRequest(page, size);
        ContentReport.Status filter;
        try { filter = ContentReport.Status.valueOf((status == null ? "open" : status).toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("Invalid report status"); }
        if (status != null && !status.equals(filter.getCode())) throw new BusinessRuleException("Invalid report status");
        requireAdmin();
        var rows = reports.findByStatus(filter, org.springframework.data.domain.PageRequest.of(page, size));
        return com.codemastery.common.api.PageResponse.from(rows.map(report -> {
            DiscussionPost post = report.getDiscussionPostId() == null ? null : posts.findById(report.getDiscussionPostId())
                    .orElseThrow(() -> new IllegalStateException("Report target disappeared"));
            return new ModerationReport(report.getId(), report.getStatus().getCode(), report.getReason(), report.getCreatedAt(),
                    report.getResolvedAt(), report.getReporterId(), post == null ? "lesson" : "discussion_post",
                    post == null ? null : post.getId(), post == null ? null : post.getContent(),
                    post == null ? null : post.getAuthorId(), post != null && post.isHidden(),
                    post == null ? report.getLessonId() : post.getLessonId());
        }));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED)
    public ModerationDecision hidePostForReport(UUID reportId) {
        requireAdmin();
        requireDecisionTransaction();
        var selected = requireReport(reportId);
        if (selected.getDiscussionPostId() == null) throw new BusinessRuleException("Report is not a post target");
        var post = posts.lockById(selected.getDiscussionPostId(), true)
                .orElseThrow(() -> new IllegalStateException("Report target disappeared"));
        var locked = reports.lockOpenForTarget(post.getId(), null);
        requireSelectedOpen(reportId, locked);
        posts.save(new DiscussionPost(post.getId(), post.getLessonId(), post.getAuthorId(), post.getParentPostId(),
                post.getContent(), post.getCreatedAt(), true));
        return resolve(locked, "hide", ContentReport.Status.RESOLVED);
    }

    /** Called after Admin's approved Course-root/lesson hide in the same transaction (ADR012 §11.2). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED)
    public ModerationDecision resolveLessonReports(UUID reportId, UUID lessonId) {
        requireAdmin();
        requireDecisionTransaction();
        var selected = requireReport(reportId);
        if (lessonId == null || !lessonId.equals(selected.getLessonId()) || selected.getDiscussionPostId() != null)
            throw new BusinessRuleException("Report target does not match Lesson");
        var locked = reports.lockOpenForTarget(null, lessonId);
        requireSelectedOpen(reportId, locked);
        return resolve(locked, "hide", ContentReport.Status.RESOLVED);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED)
    public ModerationDecision dismissReport(UUID reportId) {
        requireAdmin();
        requireDecisionTransaction();
        var selected = reports.lockById(reportId).orElseThrow(ContentReportService::unavailable);
        requireOpen(selected);
        return resolve(java.util.List.of(selected), "dismiss", ContentReport.Status.DISMISSED);
    }

    public record ModerationReport(UUID reportId, String status, String reason, java.time.Instant createdAt,
            java.time.Instant resolvedAt, UUID reporterId, String targetType, UUID postId, String postContent,
            UUID postAuthorId, boolean postHidden, UUID lessonId) {}
    public record ModerationDecision(String decision, java.util.List<UUID> resolvedReportIds, String status,
                                     java.time.Instant resolvedAt) {}

    private void requireAdmin() {
        if (identities.requireCurrentIdentity().role() != UserAccountService.IdentityRole.ADMINISTRATOR)
            throw new org.springframework.security.access.AccessDeniedException("Administrator required");
    }

    private static void requireDecisionTransaction() {
        var isolation = org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || isolation != null && isolation != java.sql.Connection.TRANSACTION_READ_COMMITTED)
            throw new IllegalStateException("Moderation requires the caller's read-write READ COMMITTED transaction");
    }

    private ContentReport requireReport(UUID id) { return reports.findById(id).orElseThrow(ContentReportService::unavailable); }

    private static void requireOpen(ContentReport report) {
        if (report.getStatus() != ContentReport.Status.OPEN)
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Report is terminal");
    }

    private static void requireSelectedOpen(UUID id, java.util.List<ContentReport> locked) {
        if (locked.stream().noneMatch(report -> id.equals(report.getId())))
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Report is terminal");
    }

    private ModerationDecision resolve(java.util.List<ContentReport> locked, String decision, ContentReport.Status status) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (var report : locked) reports.save(new ContentReport(report.getId(), report.getReporterId(),
                report.getDiscussionPostId(), report.getLessonId(), report.getReason(), status, report.getCreatedAt(), now));
        return new ModerationDecision(decision, locked.stream().map(ContentReport::getId).toList(), status.getCode(), now);
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
            throw BusinessRuleException.invalidField("reason");
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
