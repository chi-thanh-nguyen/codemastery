package com.codemastery.modules.adaptive.api.dto;

import com.codemastery.modules.adaptive.application.mastery.MasteryQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exact OpenAPI B6AdaptiveGuidance representation. Other CT33 bindings remain unimplemented. */
public final class MasteryResponses {
    private MasteryResponses() { }

    public record AdaptiveGuidance(UUID courseId, boolean adaptiveEnabled, boolean placementOffered,
            @JsonInclude(JsonInclude.Include.ALWAYS) UUID placementQuizId,
            @JsonInclude(JsonInclude.Include.ALWAYS) CurrentRecommendation currentRecommendation,
            List<LessonGuidance> lessons) {
        public AdaptiveGuidance { lessons = List.copyOf(lessons); }
        public static AdaptiveGuidance from(MasteryQueryService.AdaptiveGuidance result) {
            var row = result.currentRecommendation();
            return new AdaptiveGuidance(result.courseId(), result.adaptiveEnabled(), result.placementOffered(),
                    result.placementQuizId(), row == null ? null : new CurrentRecommendation(row.type(),
                            row.targetLessonId(), row.reason(), row.recommendedAt(), row.targetCompleted()),
                    result.lessons().stream().map(l -> new LessonGuidance(l.lessonId(),
                            l.unmasteredPrerequisiteSkillIds())).toList());
        }
    }
    public record CurrentRecommendation(String type, UUID targetLessonId, String reason, Instant recommendedAt,
            boolean targetCompleted) { }
    public record LessonGuidance(UUID lessonId, List<UUID> unmasteredPrerequisiteSkillIds) {
        public LessonGuidance { unmasteredPrerequisiteSkillIds = List.copyOf(unmasteredPrerequisiteSkillIds); }
    }
}
