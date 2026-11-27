package com.codemastery.modules.interaction.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.auth.application.service.UserAccountService;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.domain.repository.ContentReportRepository;
import com.codemastery.modules.interaction.domain.repository.DiscussionPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T05:06:07.123456789Z");
    private static final UUID LESSON = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID OTHER_LESSON = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    private static final UUID REPORTER = UUID.fromString("00000000-0000-0000-0000-00000000b001");

    @Mock
    ContentReportRepository reports;

    @Mock
    DiscussionPostRepository posts;

    @Mock
    CourseAccessService courseAccess;

    @Mock
    UserAccountService identities;

    private ContentReportService service;

    @BeforeEach
    void setUp() {
        service = new ContentReportService(reports, posts, courseAccess, identities, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void reporterIs(IdentityRole role) {
        when(identities.requireCurrentIdentity()).thenReturn(new CurrentIdentity(REPORTER, role));
    }

    private void saveEchoes() {
        when(reports.save(any(ContentReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static DiscussionPost post(UUID id, UUID lesson, UUID parent, boolean hidden) {
        return new DiscussionPost(id, lesson, UUID.randomUUID(), parent, "post text", NOW, hidden);
    }

    private static Supplier<String> reason(String text) {
        return () -> text;
    }

    private ContentReport saved() {
        ArgumentCaptor<ContentReport> captor = ArgumentCaptor.forClass(ContentReport.class);
        verify(reports).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- lesson target

    @Test
    void reportLessonCreatesAnOpenReportOfTheLessonForTheCurrentUser() {
        reporterIs(IdentityRole.LEARNER);
        saveEchoes();

        ContentReport returned = service.reportLesson(LESSON, reason("  Spam\n link  "));

        verify(courseAccess).requireLessonReadAccess(LESSON);
        ContentReport report = saved();
        assertAll(
                () -> assertEquals(report, returned),
                () -> assertNotNull(report.getId()),
                () -> assertEquals(REPORTER, report.getReporterId()),
                () -> assertNull(report.getDiscussionPostId()),
                () -> assertEquals(LESSON, report.getLessonId()),
                () -> assertEquals("  Spam\n link  ", report.getReason()),
                () -> assertEquals(ContentReport.Status.OPEN, report.getStatus()),
                () -> assertEquals(Instant.parse("2026-11-18T05:06:07.123456Z"), report.getCreatedAt()),
                () -> assertNull(report.getResolvedAt()));
    }

    // ---------------------------------------------------------------- post target

    @Test
    void reportPostCreatesAnOpenReportOfAVisibleQuestion() {
        UUID question = UUID.randomUUID();
        when(posts.findById(question)).thenReturn(Optional.of(post(question, LESSON, null, false)));
        reporterIs(IdentityRole.INSTRUCTOR);
        saveEchoes();

        service.reportPost(LESSON, question, reason("Off topic"));

        verify(courseAccess).requireLessonReadAccess(LESSON);
        ContentReport report = saved();
        assertAll(
                () -> assertEquals(question, report.getDiscussionPostId()),
                () -> assertNull(report.getLessonId()),
                () -> assertEquals(REPORTER, report.getReporterId()),
                () -> assertEquals(ContentReport.Status.OPEN, report.getStatus()),
                () -> assertEquals("Off topic", report.getReason()));
    }

    @Test
    void reportPostAcceptsAVisibleReplyOfAVisibleQuestion() {
        UUID question = UUID.randomUUID();
        UUID reply = UUID.randomUUID();
        when(posts.findById(reply)).thenReturn(Optional.of(post(reply, LESSON, question, false)));
        when(posts.findById(question)).thenReturn(Optional.of(post(question, LESSON, null, false)));
        reporterIs(IdentityRole.LEARNER);
        saveEchoes();

        service.reportPost(LESSON, reply, reason("Rude"));

        assertEquals(reply, saved().getDiscussionPostId());
    }

    @Test
    void reportPostConcealsEveryTargetThatIsNotAVisiblePostOfTheLesson() {
        UUID missing = UUID.randomUUID();
        UUID otherLessonPost = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        UUID replyUnderHidden = UUID.randomUUID();
        UUID hiddenQuestion = UUID.randomUUID();
        UUID replyUnderForeign = UUID.randomUUID();
        UUID foreignQuestion = UUID.randomUUID();
        UUID replyWithoutQuestion = UUID.randomUUID();
        UUID vanishedQuestion = UUID.randomUUID();
        when(posts.findById(missing)).thenReturn(Optional.empty());
        when(posts.findById(otherLessonPost)).thenReturn(Optional.of(post(otherLessonPost, OTHER_LESSON, null, false)));
        when(posts.findById(hidden)).thenReturn(Optional.of(post(hidden, LESSON, null, true)));
        when(posts.findById(replyUnderHidden)).thenReturn(Optional.of(post(replyUnderHidden, LESSON, hiddenQuestion, false)));
        when(posts.findById(hiddenQuestion)).thenReturn(Optional.of(post(hiddenQuestion, LESSON, null, true)));
        when(posts.findById(replyUnderForeign)).thenReturn(Optional.of(post(replyUnderForeign, LESSON, foreignQuestion, false)));
        when(posts.findById(foreignQuestion)).thenReturn(Optional.of(post(foreignQuestion, OTHER_LESSON, null, false)));
        when(posts.findById(replyWithoutQuestion)).thenReturn(Optional.of(post(replyWithoutQuestion, LESSON, vanishedQuestion, false)));
        when(posts.findById(vanishedQuestion)).thenReturn(Optional.empty());
        AtomicInteger reasonReads = new AtomicInteger();
        Supplier<String> counting = () -> {
            reasonReads.incrementAndGet();
            return "reason";
        };

        for (UUID target : List.of(missing, otherLessonPost, hidden, replyUnderHidden, replyUnderForeign, replyWithoutQuestion)) {
            assertThrows(ResourceNotFoundException.class, () -> service.reportPost(LESSON, target, counting));
        }

        assertEquals(0, reasonReads.get());
        verify(reports, never()).save(any());
        verifyNoInteractions(identities);
    }

    // ---------------------------------------------------------------- order of decisions

    @Test
    void anAdmissionFailureStopsBeforeAnyLookupBodyReadOrWrite() {
        doThrow(new AccessDeniedException("Access denied")).when(courseAccess).requireLessonReadAccess(LESSON);
        AtomicInteger reasonReads = new AtomicInteger();
        Supplier<String> counting = () -> {
            reasonReads.incrementAndGet();
            return "reason";
        };

        assertThrows(AccessDeniedException.class, () -> service.reportLesson(LESSON, counting));
        assertThrows(AccessDeniedException.class, () -> service.reportPost(LESSON, UUID.randomUUID(), counting));

        doThrow(new ResourceNotFoundException("Resource not available")).when(courseAccess).requireLessonReadAccess(OTHER_LESSON);
        assertThrows(ResourceNotFoundException.class, () -> service.reportLesson(OTHER_LESSON, counting));

        assertEquals(0, reasonReads.get());
        verifyNoInteractions(posts, reports, identities);
    }

    @Test
    void theBodyIsReadOnlyAfterAdmissionAndRulesRejectBlankReasons() {
        List<String> order = new ArrayList<>();
        doAnswer(invocation -> {
            order.add("admission");
            return null;
        }).when(courseAccess).requireLessonReadAccess(LESSON);

        for (String blank : new String[] {"", " ", "\n\t", "\u2003", "\u0085", null}) {
            BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> service.reportLesson(LESSON, () -> {
                order.add("body");
                return blank;
            }));
            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
        }

        assertEquals("admission", order.get(0));
        assertEquals("body", order.get(1));
        verify(reports, never()).save(any());
    }

    @Test
    void aShapeFailureFromTheBodySupplierPropagatesAndWritesNothing() {
        BusinessRuleException shape = new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid request");

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> service.reportLesson(LESSON, () -> {
            throw shape;
        }));

        assertEquals(shape, thrown);
        verify(courseAccess).requireLessonReadAccess(LESSON);
        verify(reports, never()).save(any());
    }

    @Test
    void nullArgumentsAreRejectedBeforeAnything() {
        assertThrows(NullPointerException.class, () -> service.reportLesson(null, reason("x")));
        assertThrows(NullPointerException.class, () -> service.reportLesson(LESSON, null));
        assertThrows(NullPointerException.class, () -> service.reportPost(LESSON, null, reason("x")));
        verifyNoInteractions(courseAccess, posts, reports, identities);
    }
}
