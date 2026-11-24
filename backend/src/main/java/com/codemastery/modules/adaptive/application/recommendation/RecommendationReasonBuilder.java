package com.codemastery.modules.adaptive.application.recommendation;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the fixed English recommendation reasons of {@code CT07-REASONS-2026-11-12-r1}
 * (ADR011 §9). Skill names are inserted verbatim in double quotes; several names are ordered by
 * {@link String#compareTo} (UTF-16 code units, case-sensitive, no normalization, locale or
 * collation), equal names by lowercase UUID text, and joined with {@code ", "}.
 */
public final class RecommendationReasonBuilder {

    private RecommendationReasonBuilder() {
    }

    /**
     * Returns the ADR011 §9 name order: stored name, then lowercase canonical UUID text, both
     * compared with {@link String#compareTo}.
     *
     * @param skillNames the stored name of every skill that will be compared
     */
    public static Comparator<UUID> nameOrder(Map<UUID, String> skillNames) {
        return Comparator
                .comparing((UUID id) -> requireName(id, skillNames))
                .thenComparing(UUID::toString);
    }

    public static String skip(Set<UUID> skillIds, Map<UUID, String> skillNames) {
        if (skillIds.isEmpty()) {
            throw new IllegalArgumentException("A test-out reason names at least one skill");
        }

        String names = skillIds.stream()
                .sorted(nameOrder(skillNames))
                .map(id -> "\"" + skillNames.get(id) + "\"")
                .collect(Collectors.joining(", "));

        return "Tested out: you have mastered every skill in this lesson (" + names + ").";
    }

    public static String remedial(String weakSkillName, String prerequisiteSkillName) {
        if (weakSkillName == null || prerequisiteSkillName == null) {
            throw new IllegalArgumentException("A remedial reason names both skills");
        }
        return "Review this lesson first: your skill \"" + weakSkillName
                + "\" is Weak and its prerequisite \"" + prerequisiteSkillName
                + "\" is not yet Mastered.";
    }

    public static String continueInCourseOrder() {
        return "Continue with the next lesson in the course order.";
    }

    private static String requireName(UUID skillId, Map<UUID, String> skillNames) {
        String name = skillNames.get(skillId);
        if (name == null) {
            throw new IllegalArgumentException("Skill name is missing");
        }
        return name;
    }
}
