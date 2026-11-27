package com.codemastery.modules.interaction.application.service;

import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseCatalogueService;
import com.codemastery.modules.interaction.api.dto.InteractionRequests.DiscussionPostWrite;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceTest {
    @Mock DiscussionPostRepository posts;
    @Mock CourseAccessService access;
    @Mock UserAccountService identities;
    @Mock CourseCatalogueService catalogue;
    @Mock NotificationService notifications;
    UUID lesson = UUID.randomUUID(), actor = UUID.randomUUID();
    Instant time = Instant.parse("2026-11-27T01:02:03.123456789Z");
    DiscussionService service;

    @BeforeEach void setup() {
        service = new DiscussionService(posts, access, identities, catalogue, notifications, Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test void questionUsesTrustedActorVerbatimTextAndMicrosecondClock() {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, IdentityRole.LEARNER));
        when(posts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(identities.userSummaries(Set.of(actor))).thenReturn(Map.of());
        var row = service.create(lesson, () -> new DiscussionPostWrite("  <p>Question</p>\nLine  ", null));
        assertEquals(actor, row.authorId()); assertEquals(lesson, row.lessonId());
        assertEquals("  <p>Question</p>\nLine  ", row.content()); assertNull(row.parentPostId());
        assertNull(row.authorDisplayName()); assertNull(row.authorRole()); assertTrue(row.mine());
        assertEquals(time.truncatedTo(java.time.temporal.ChronoUnit.MICROS), row.createdAt());
        verifyNoInteractions(catalogue, notifications);
    }

    @Test void admissionRunsBeforeBodyOrRepository() {
        doThrow(new com.codemastery.common.exception.ResourceNotFoundException("concealed")).when(access).requireLessonReadAccess(lesson);
        assertThrows(com.codemastery.common.exception.ResourceNotFoundException.class,
                () -> service.create(lesson, () -> { fail("body must not be read"); return null; }));
        verifyNoInteractions(posts, identities, notifications, catalogue);
    }

    @Test void moreThanOneHundredReturnedAuthorsAreProjectedInBoundedChunks() {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(actor, IdentityRole.LEARNER));
        var question = new DiscussionPost(UUID.randomUUID(), lesson, actor, null, "Question", time, false);
        var replies = java.util.stream.IntStream.range(0, 105).mapToObj(n -> new DiscussionPost(UUID.randomUUID(), lesson,
                UUID.randomUUID(), question.getId(), "Reply", time, false)).toList();
        when(posts.findVisibleQuestions(lesson, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(question), PageRequest.of(0, 20), 1));
        when(posts.findVisibleReplies(Set.of(question.getId()))).thenReturn(replies);
        Set<UUID> seen = new HashSet<>();
        when(identities.userSummaries(anySet())).thenAnswer(call -> {
            Set<UUID> ids = call.getArgument(0); assertTrue(ids.size() <= 100); seen.addAll(ids); return Map.of();
        });
        var result = service.list(lesson, 0, 20);
        assertEquals(1, result.totalElements()); assertEquals(105, result.content().getFirst().replies().size());
        assertEquals(106, seen.size()); verify(identities, times(2)).userSummaries(anySet());
    }
}
