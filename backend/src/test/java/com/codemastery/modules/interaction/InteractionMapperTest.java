package com.codemastery.modules.interaction;

import com.codemastery.modules.interaction.domain.model.ContentReport;
import com.codemastery.modules.interaction.domain.model.DiscussionPost;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.ContentReportEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.entity.DiscussionPostEntity;
import com.codemastery.modules.interaction.infrastructure.persistence.mapper.InteractionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IN06.repo: entity/domain mapping of discussion posts and content reports (V006). */
@DisplayName("InteractionMapper Unit Tests")
class InteractionMapperTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID LESSON_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final Instant CREATED = Instant.parse("2026-11-18T01:02:03.123456Z");
    private static final Instant RESOLVED = Instant.parse("2026-11-18T04:05:06.654321Z");

    private final InteractionMapper mapper = new InteractionMapper();

    @Test
    void discussionPostRoundTripsEveryField() {
        DiscussionPost reply = new DiscussionPost(POST_ID, LESSON_ID, USER_ID, PARENT_ID, "  verbatim\nreply ", CREATED, true);

        DiscussionPostEntity entity = mapper.toEntity(reply);
        DiscussionPost back = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(POST_ID, entity.getId()),
                () -> assertEquals(LESSON_ID, entity.getLessonId()),
                () -> assertEquals(USER_ID, entity.getAuthorId()),
                () -> assertEquals(PARENT_ID, entity.getParentPostId()),
                () -> assertEquals("  verbatim\nreply ", entity.getContent()),
                () -> assertEquals(CREATED, entity.getCreatedAt()),
                () -> assertTrue(entity.isHidden()),
                () -> assertPostEquals(reply, back));
    }

    @Test
    void questionKeepsNullParent() {
        DiscussionPost question = new DiscussionPost(POST_ID, LESSON_ID, USER_ID, null, "Question?", CREATED, false);

        assertNull(mapper.toEntity(question).getParentPostId());
        assertPostEquals(question, mapper.toDomain(mapper.toEntity(question)));
    }

    @Test
    void postReportRoundTripsEveryField() {
        ContentReport report = new ContentReport(
                REPORT_ID, USER_ID, POST_ID, null, " spam ", ContentReport.Status.RESOLVED, CREATED, RESOLVED);

        ContentReportEntity entity = mapper.toEntity(report);
        ContentReport back = mapper.toDomain(entity);

        assertAll(
                () -> assertEquals(REPORT_ID, entity.getId()),
                () -> assertEquals(USER_ID, entity.getReporterId()),
                () -> assertEquals(POST_ID, entity.getDiscussionPostId()),
                () -> assertNull(entity.getLessonId()),
                () -> assertEquals(" spam ", entity.getReason()),
                () -> assertEquals("resolved", entity.getStatus()),
                () -> assertEquals(CREATED, entity.getCreatedAt()),
                () -> assertEquals(RESOLVED, entity.getResolvedAt()),
                () -> assertReportEquals(report, back));
    }

    @Test
    void lessonReportRoundTripsWithNullPostAndResolution() {
        ContentReport report = new ContentReport(
                REPORT_ID, USER_ID, null, LESSON_ID, "Wrong lesson", ContentReport.Status.OPEN, CREATED, null);

        ContentReportEntity entity = mapper.toEntity(report);

        assertAll(
                () -> assertNull(entity.getDiscussionPostId()),
                () -> assertEquals(LESSON_ID, entity.getLessonId()),
                () -> assertEquals("open", entity.getStatus()),
                () -> assertNull(entity.getResolvedAt()),
                () -> assertReportEquals(report, mapper.toDomain(entity)));
    }

    @ParameterizedTest
    @EnumSource(ContentReport.Status.class)
    void statusIsStoredByItsExactV006Code(ContentReport.Status status) {
        String expected = switch (status) {
            case OPEN -> "open";
            case RESOLVED -> "resolved";
            case DISMISSED -> "dismissed";
        };
        ContentReport report = new ContentReport(REPORT_ID, USER_ID, POST_ID, null, "r", status, CREATED, null);

        ContentReportEntity entity = mapper.toEntity(report);

        assertEquals(expected, entity.getStatus());
        assertEquals(status, mapper.toDomain(entity).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Open", "OPEN", " open", "open ", "RESOLVED", "Dismissed", "closed", "0", ""})
    void nonExactStatusCodeIsRejected(String code) {
        ContentReportEntity entity = new ContentReportEntity(REPORT_ID, USER_ID, POST_ID, null, "r", code, CREATED, null);

        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(entity));
    }

    @Test
    void nullStatusAndNullObjectsMapToNull() {
        ContentReportEntity entity = new ContentReportEntity(REPORT_ID, USER_ID, POST_ID, null, "r", null, CREATED, null);
        ContentReport report = new ContentReport(REPORT_ID, USER_ID, POST_ID, null, "r", null, CREATED, null);

        assertAll(
                () -> assertNull(mapper.toDomain(entity).getStatus()),
                () -> assertNull(mapper.toEntity(report).getStatus()),
                () -> assertNull(mapper.toEntity((DiscussionPost) null)),
                () -> assertNull(mapper.toDomain((DiscussionPostEntity) null)),
                () -> assertNull(mapper.toEntity((ContentReport) null)),
                () -> assertNull(mapper.toDomain((ContentReportEntity) null)));
    }

    private static void assertPostEquals(DiscussionPost expected, DiscussionPost actual) {
        assertAll(
                () -> assertEquals(expected.getId(), actual.getId()),
                () -> assertEquals(expected.getLessonId(), actual.getLessonId()),
                () -> assertEquals(expected.getAuthorId(), actual.getAuthorId()),
                () -> assertEquals(expected.getParentPostId(), actual.getParentPostId()),
                () -> assertEquals(expected.getContent(), actual.getContent()),
                () -> assertEquals(expected.getCreatedAt(), actual.getCreatedAt()),
                () -> assertEquals(expected.isHidden(), actual.isHidden()));
    }

    private static void assertReportEquals(ContentReport expected, ContentReport actual) {
        assertAll(
                () -> assertEquals(expected.getId(), actual.getId()),
                () -> assertEquals(expected.getReporterId(), actual.getReporterId()),
                () -> assertEquals(expected.getDiscussionPostId(), actual.getDiscussionPostId()),
                () -> assertEquals(expected.getLessonId(), actual.getLessonId()),
                () -> assertEquals(expected.getReason(), actual.getReason()),
                () -> assertEquals(expected.getStatus(), actual.getStatus()),
                () -> assertEquals(expected.getCreatedAt(), actual.getCreatedAt()),
                () -> assertEquals(expected.getResolvedAt(), actual.getResolvedAt()));
    }
}
