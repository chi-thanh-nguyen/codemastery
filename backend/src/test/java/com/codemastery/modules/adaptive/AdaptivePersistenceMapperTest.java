package com.codemastery.modules.adaptive;

import com.codemastery.modules.adaptive.domain.model.LearnerSkillMastery;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.PathRecommendation;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.LearnerSkillMasteryEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.entity.PathRecommendationEntity;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.MasteryMapper;
import com.codemastery.modules.adaptive.infrastructure.persistence.mapper.RecommendationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AD06.mapping unit tests for {@link MasteryMapper} and {@link RecommendationMapper}: exact V005
 * stored codes, field-for-field round trips and rejection of any other spelling. Real persistence
 * is covered by {@code AdaptivePersistenceIntegrationTest}.
 */
class AdaptivePersistenceMapperTest {

    private static final UUID ID = UUID.fromString("6f1c1a52-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("6f1c1a52-0000-4000-8000-000000000002");
    private static final UUID SKILL = UUID.fromString("6f1c1a52-0000-4000-8000-000000000003");
    private static final UUID ENROLLMENT = UUID.fromString("6f1c1a52-0000-4000-8000-000000000004");
    private static final UUID LESSON = UUID.fromString("6f1c1a52-0000-4000-8000-000000000005");
    private static final Instant AT = Instant.parse("2026-11-23T01:00:00.123456Z");

    private final MasteryMapper masteries = new MasteryMapper();
    private final RecommendationMapper recommendations = new RecommendationMapper();

    // ------------------------------------------------------------------- mastery

    @Test
    void masteryStatesAreExactlyTheFourV005CodesInDeclarationOrder() {
        assertEquals(List.of("unknown", "learning", "mastered", "weak"),
                Arrays.stream(MasteryState.values()).map(masteries::toCode).toList());
    }

    @ParameterizedTest
    @EnumSource(MasteryState.class)
    void masteryRoundTripPreservesEveryField(MasteryState state) {
        LearnerSkillMastery domain = new LearnerSkillMastery(ID, USER, SKILL, state);

        LearnerSkillMasteryEntity entity = masteries.toEntity(domain);

        assertAll(
                () -> assertEquals(ID, entity.getId()),
                () -> assertEquals(USER, entity.getUserId()),
                () -> assertEquals(SKILL, entity.getSkillId()),
                () -> assertEquals(state.getCode(), entity.getState()),
                () -> assertEquals(domain, masteries.toDomain(entity)),
                () -> assertEquals(state, masteries.toState(masteries.toCode(state))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mastered", "MASTERED", " mastered", "mastered ", "", "known", "0", "0.8"})
    void anyOtherStoredMasterySpellingIsRejectedNeverDefaultedToUnknown(String code) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> masteries.toState(code));
        assertTrue(thrown.getMessage().contains("'" + code + "'"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> masteries.toDomain(new LearnerSkillMasteryEntity(ID, USER, SKILL, code)));
    }

    @Test
    void masteryNullsMapToNullAndLeaveNotNullToV005() {
        assertAll(
                () -> assertNull(masteries.toEntity(null)),
                () -> assertNull(masteries.toDomain(null)),
                () -> assertNull(masteries.toCode(null)),
                () -> assertNull(masteries.toState(null)),
                () -> assertNull(masteries.toEntity(new LearnerSkillMastery(ID, USER, SKILL, null)).getState()));
    }

    // ------------------------------------------------------------------- recommendations

    @Test
    void recommendationTypesAreExactlyTheThreeV005CodesInDeclarationOrder() {
        assertEquals(List.of("continue", "skip", "remedial"),
                Arrays.stream(RecommendationType.values()).map(recommendations::toCode).toList());
    }

    @ParameterizedTest
    @EnumSource(RecommendationType.class)
    void recommendationRoundTripPreservesEveryFieldVerbatim(RecommendationType type) {
        String reason = "  Review this lesson first: your skill \"Loops\" is Weak and its prerequisite "
                + "\"Variables\" is not yet Mastered.\n";
        PathRecommendation domain = new PathRecommendation(ID, ENROLLMENT, LESSON, type, reason, AT);

        PathRecommendationEntity entity = recommendations.toEntity(domain);

        assertAll(
                () -> assertEquals(ID, entity.getId()),
                () -> assertEquals(ENROLLMENT, entity.getEnrollmentId()),
                () -> assertEquals(LESSON, entity.getLessonId()),
                () -> assertEquals(type.getCode(), entity.getRecommendationType()),
                () -> assertEquals(reason, entity.getReason(), "reason is neither trimmed nor rewritten"),
                () -> assertEquals(AT, entity.getRecommendedAt(), "instant is not re-sampled or truncated"),
                () -> assertEquals(domain, recommendations.toDomain(entity)),
                () -> assertEquals(type, recommendations.toType(recommendations.toCode(type))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Continue", "SKIP", "test-out", "testout", " remedial", "", "next"})
    void anyOtherStoredTypeSpellingIsRejected(String code) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> recommendations.toType(code));
        assertTrue(thrown.getMessage().contains("'" + code + "'"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> recommendations.toDomain(
                new PathRecommendationEntity(ID, ENROLLMENT, LESSON, code, "r", AT)));
    }

    @Test
    void recommendationNullsMapToNullAndLeaveNotNullToV005() {
        PathRecommendationEntity entity = recommendations.toEntity(new PathRecommendation(ID, null, null, null, null, null));
        assertAll(
                () -> assertNull(recommendations.toEntity(null)),
                () -> assertNull(recommendations.toDomain(null)),
                () -> assertNull(recommendations.toCode(null)),
                () -> assertNull(recommendations.toType(null)),
                () -> assertEquals(ID, entity.getId()),
                () -> assertNull(entity.getEnrollmentId()),
                () -> assertNull(entity.getLessonId()),
                () -> assertNull(entity.getRecommendationType()),
                () -> assertNull(entity.getReason()),
                () -> assertNull(entity.getRecommendedAt()));
    }
}
