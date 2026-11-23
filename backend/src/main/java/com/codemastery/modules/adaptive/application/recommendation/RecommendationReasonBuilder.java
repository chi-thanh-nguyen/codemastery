package com.codemastery.modules.adaptive.application.recommendation;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds the fixed English recommendation reasons defined by CT07.reasons. */
public final class RecommendationReasonBuilder {

    private RecommendationReasonBuilder() {
    }

    public static String skip(Set<UUID> skillIds, Map<UUID, String> skillNames) {
        Comparator<UUID> nameOrder = Comparator
                .comparing((UUID id) -> skillNames.get(id))
                .thenComparing(UUID::toString);

        String names = skillIds.stream()
                .sorted(nameOrder)
                .map(id -> "\"" + skillNames.get(id) + "\"")
                .collect(Collectors.joining(", "));

        return "Tested out: you have mastered every skill in this lesson (" + names + ").";
    }

    public static String remedial(String weakSkillName, String prerequisiteSkillName) {
        return "Review this lesson first: your skill \"" + weakSkillName
                + "\" is Weak and its prerequisite \"" + prerequisiteSkillName
                + "\" is not yet Mastered.";
    }

    public static String continueInCourseOrder() {
        return "Continue with the next lesson in the course order.";
    }
}