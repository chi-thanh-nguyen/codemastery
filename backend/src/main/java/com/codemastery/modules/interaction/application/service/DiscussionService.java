package com.codemastery.modules.interaction.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.UserSummary;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseCatalogueService;
import com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite;
import com.codemastery.modules.interaction.api.dto.InteractionResponses.*;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** CT30: admission, plain-text questions/replies and atomic Interaction-owned notification. */
@Service
public class DiscussionService {
    private final DiscussionPostRepository posts;
    private final CourseAccessService access;
    private final UserAccountService identities;
    private final CourseCatalogueService catalogue;
    private final NotificationService notifications;
    private final Clock clock;

    @Autowired
    public DiscussionService(DiscussionPostRepository posts, CourseAccessService access,
            UserAccountService identities, CourseCatalogueService catalogue, NotificationService notifications) {
        this(posts, access, identities, catalogue, notifications, Clock.systemUTC());
    }

    DiscussionService(DiscussionPostRepository posts, CourseAccessService access,
            UserAccountService identities, CourseCatalogueService catalogue, NotificationService notifications, Clock clock) {
        this.posts = Objects.requireNonNull(posts);
        this.access = Objects.requireNonNull(access);
        this.identities = Objects.requireNonNull(identities);
        this.catalogue = Objects.requireNonNull(catalogue);
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<DiscussionThread> list(UUID lessonId, int page, int size) {
        PageResponse.validateRequest(page, size);
        access.requireLessonReadAccess(lessonId);
        UUID actor = identities.requireCurrentIdentity().userId();
        var questions = posts.findVisibleQuestions(lessonId, PageRequest.of(page, size));
        var replies = posts.findVisibleReplies(questions.stream().map(DiscussionPost::getId).collect(Collectors.toSet()));
        List<DiscussionPost> all = new ArrayList<>(questions.getContent());
        all.addAll(replies);
        Map<UUID, UserSummary> names = summaries(all);
        Map<UUID, List<DiscussionReply>> grouped = new HashMap<>();
        for (var reply : replies) {
            var name = names.get(reply.getAuthorId());
            grouped.computeIfAbsent(reply.getParentPostId(), ignored -> new ArrayList<>()).add(new DiscussionReply(
                    reply.getId(), reply.getParentPostId(), reply.getAuthorId(), name == null ? null : name.displayName(),
                    role(name), actor.equals(reply.getAuthorId()), reply.getContent(), reply.getCreatedAt()));
        }
        var content = questions.stream().map(question -> {
            var name = names.get(question.getAuthorId());
            return new DiscussionThread(question.getId(), question.getAuthorId(), name == null ? null : name.displayName(),
                    role(name), actor.equals(question.getAuthorId()), question.getContent(), question.getCreatedAt(),
                    List.copyOf(grouped.getOrDefault(question.getId(), List.of())));
        }).toList();
        return new PageResponse<>(content, page, size, questions.getTotalElements());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiscussionPostResponse create(UUID lessonId, Supplier<DiscussionPostWrite> body) {
        access.requireLessonReadAccess(lessonId);
        UUID actor = identities.requireCurrentIdentity().userId();
        var write = Objects.requireNonNull(body.get());
        DiscussionPost parent = null;
        if (write.parentPostId() != null) {
            parent = posts.lockById(write.parentPostId(), false)
                    .filter(p -> lessonId.equals(p.getLessonId()) && !p.isHidden())
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not available"));
            if (parent.getParentPostId() != null) {
                // Hidden ancestors remain concealed even when the supplied parent is itself a reply.
                var question = posts.findById(parent.getParentPostId());
                if (question.isEmpty() || question.get().isHidden() || !lessonId.equals(question.get().getLessonId())
                        || question.get().getParentPostId() != null)
                    throw new ResourceNotFoundException("Resource not available");
                throw BusinessRuleException.invalidField("parentPostId");
            }
        }
        if (!write.validShape()) throw new BusinessRuleException("Invalid discussion request body");
        if (!com.codemastery.modules.interaction.api.dto.InteractionRequests.isRepresentableText(write.content())
                || write.content().codePoints().allMatch(DiscussionService::whitespace))
            throw BusinessRuleException.invalidField("content");
        var post = posts.save(new DiscussionPost(UUID.randomUUID(), lessonId, actor, write.parentPostId(),
                write.content(), clock.instant().truncatedTo(ChronoUnit.MICROS), false));
        if (parent != null && !actor.equals(parent.getAuthorId())) {
            var context = catalogue.readLessonContext(lessonId)
                    .orElseThrow(() -> new IllegalStateException("Admitted Lesson context disappeared"));
            if ("published".equals(context.coursePublicationStatus()) && "published".equals(context.lessonPublicationStatus())) {
                notifications.create(List.of(new NotificationService.NewNotification(
                        NotificationService.derivedId("discussion-reply", post.getId().toString()), parent.getAuthorId(),
                        "New reply to your question in \"" + context.lessonTitle() + "\".")));
            } else {
                org.slf4j.LoggerFactory.getLogger(DiscussionService.class).debug("Skipped reply notification: unpublished context (count=1)");
            }
        }
        var name = summaries(List.of(post)).get(actor);
        return new DiscussionPostResponse(post.getId(), lessonId, post.getParentPostId(), actor,
                name == null ? null : name.displayName(), role(name), true, post.getContent(), post.getCreatedAt());
    }

    private Map<UUID, UserSummary> summaries(List<DiscussionPost> rows) {
        List<UUID> ids = rows.stream().map(DiscussionPost::getAuthorId).distinct().toList();
        Map<UUID, UserSummary> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100)
            result.putAll(identities.userSummaries(Set.copyOf(ids.subList(start, Math.min(start + 100, ids.size())))));
        return result;
    }

    private static String role(UserSummary user) {
        if (user == null) return null;
        return switch (user.role()) {
            case "learner" -> "Learner";
            case "instructor" -> "Instructor";
            case "admin" -> "Administrator";
            default -> throw new IllegalStateException("Invalid author role");
        };
    }

    private static boolean whitespace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85;
    }
}
