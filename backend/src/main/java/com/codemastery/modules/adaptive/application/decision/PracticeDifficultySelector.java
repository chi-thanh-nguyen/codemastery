package com.codemastery.modules.adaptive.application.decision;

import com.codemastery.modules.adaptive.application.recommendation.RecommendationReasonBuilder;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure practice-difficulty rules: the {@code CT07-DIFFICULTY-2026-11-12-r1} mapping and fallback
 * orders (ADR011 §8) and the {@code CT07-PRACTICE-RECOMMENDATION-2026-11-15-r1} rule over fixed,
 * per-difficulty practice Quizzes (ADR013 §19.4, Leader decision E02).
 *
 * <p>Nothing here performs I/O, reads a clock, writes or starts an attempt; no numeric threshold
 * is used. The Learner admission, the Course snapshot and the Assessment profile read that feed
 * {@link #recommendPractice} belong to the REST binding.
 */
public final class PracticeDifficultySelector {

    private PracticeDifficultySelector() {
    }

    /**
     * Maps a resolved mastery state to its target practice difficulty under CT07.
     *
     * @param state the skill's mastery state, with UNKNOWN used for an absent
     *              mastery row
     * @return the target difficulty before quiz availability is considered
     */
    public static DifficultyLevel targetDifficulty(MasteryState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case UNKNOWN, WEAK -> DifficultyLevel.EASY;
            case LEARNING -> DifficultyLevel.MEDIUM;
            case MASTERED -> DifficultyLevel.HARD;
        };
    }

    /**
     * Returns the CT07 search order for the target difficulty, starting with the
     * target.
     *
     * @param target the difficulty derived from mastery
     * @return the immutable order in which to look for eligible practice quizzes
     */
    public static List<DifficultyLevel> fallbackOrder(DifficultyLevel target) {
        Objects.requireNonNull(target, "target");
        return switch (target) {
            case EASY -> List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD);
            case MEDIUM -> List.of(DifficultyLevel.MEDIUM, DifficultyLevel.EASY, DifficultyLevel.HARD);
            case HARD -> List.of(DifficultyLevel.HARD, DifficultyLevel.MEDIUM, DifficultyLevel.EASY);
        };
    }

    /**
     * Returns the first level of {@code target}'s fallback order that is available.
     *
     * @param target    the difficulty derived from mastery
     * @param available the levels that have at least one candidate
     * @return the selected level, or empty when none of the three is available
     */
    public static Optional<DifficultyLevel> selectLevel(DifficultyLevel target, Set<DifficultyLevel> available) {
        Objects.requireNonNull(available, "available");
        return fallbackOrder(target).stream().filter(available::contains).findFirst();
    }

    /**
     * Applies the ADR013 §19.4 rule to one Lesson L.
     *
     * <ol>
     *   <li>No recommendation when adaptive mode is disabled or no skill is mapped to L.</li>
     *   <li>The governing skill is the mapped skill with the easiest target (EASY &lt; MEDIUM &lt;
     *       HARD); ties follow the CT07.reasons name order.</li>
     *   <li>Candidates are the profiles with exactly one difficulty; mixed-difficulty and empty
     *       Quizzes are never recommended.</li>
     *   <li>The level is the first of the governing target's fallback order that has a candidate;
     *       none means no recommendation.</li>
     *   <li>At that level the Quiz with the smallest title ({@link String#compareTo}), then the
     *       smallest lowercase UUID, is recommended; {@code fallback} is true exactly when the
     *       level differs from the target.</li>
     * </ol>
     *
     * @param adaptiveEnabled the Course's stored {@code adaptiveEnabled} of the same snapshot
     * @param mappedSkills    M(L) with each learner state (absent row as {@code UNKNOWN}) and name
     * @param profiles        the published practice Quiz profiles of the Course for M(L)
     * @return the recommended practice Quiz, or empty for a null result
     * @throws IllegalArgumentException if an input is null, contains null, or repeats a skill or Quiz
     */
    public static Optional<PracticeChoice> recommendPractice(
            boolean adaptiveEnabled, List<PracticeSkill> mappedSkills, List<PracticeQuiz> profiles) {
        List<PracticeSkill> skills = copy(mappedSkills, "mapped skills");
        List<PracticeQuiz> quizzes = copy(profiles, "practice quiz profiles");
        requireUnique(skills.stream().map(PracticeSkill::skillId).toList(), "skill");
        requireUnique(quizzes.stream().map(PracticeQuiz::quizId).toList(), "quiz");

        if (!adaptiveEnabled || skills.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, String> names = new HashMap<>();
        skills.forEach(skill -> names.put(skill.skillId(), skill.name()));
        Comparator<PracticeSkill> governingOrder = Comparator
                .comparingInt((PracticeSkill skill) -> easeRank(targetDifficulty(skill.state())))
                .thenComparing(PracticeSkill::skillId, RecommendationReasonBuilder.nameOrder(names));
        PracticeSkill governing = skills.stream().min(governingOrder).orElseThrow();
        DifficultyLevel target = targetDifficulty(governing.state());

        List<PracticeQuiz> candidates = quizzes.stream()
                .filter(quiz -> quiz.difficulties().size() == 1)
                .toList();
        Set<DifficultyLevel> available = new HashSet<>();
        candidates.forEach(quiz -> available.addAll(quiz.difficulties()));

        return selectLevel(target, available).map(level -> {
            PracticeQuiz quiz = candidates.stream()
                    .filter(candidate -> candidate.difficulties().contains(level))
                    .min(Comparator.comparing(PracticeQuiz::title)
                            .thenComparing(candidate -> candidate.quizId().toString()))
                    .orElseThrow();
            boolean fallback = level != target;
            return new PracticeChoice(quiz.quizId(), quiz.title(), level, target, fallback,
                    governing.skillId(), governing.name(), governing.state(),
                    practiceReason(level, target, governing, fallback));
        });
    }

    private static String practiceReason(
            DifficultyLevel level, DifficultyLevel target, PracticeSkill governing, boolean fallback) {
        String reason = "Recommended practice: " + label(level) + ", because your skill \""
                + governing.name() + "\" is " + stateText(governing.state());
        return fallback
                ? reason + " and no " + label(target) + " practice quiz is available for this lesson."
                : reason + ".";
    }

    /** EASY &lt; MEDIUM &lt; HARD, stated explicitly rather than relying on enum declaration order. */
    private static int easeRank(DifficultyLevel level) {
        return switch (level) {
            case EASY -> 0;
            case MEDIUM -> 1;
            case HARD -> 2;
        };
    }

    private static String label(DifficultyLevel level) {
        return switch (level) {
            case EASY -> "Easy";
            case MEDIUM -> "Medium";
            case HARD -> "Hard";
        };
    }

    private static String stateText(MasteryState state) {
        return switch (state) {
            case UNKNOWN -> "not yet assessed";
            case WEAK -> "Weak";
            case LEARNING -> "Learning";
            case MASTERED -> "Mastered";
        };
    }

    private static <T> List<T> copy(List<T> values, String label) {
        try {
            return List.copyOf(values);
        } catch (NullPointerException cause) {
            throw new IllegalArgumentException("The " + label + " must not be or contain null", cause);
        }
    }

    private static void requireUnique(List<UUID> ids, String label) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate " + label + " ID");
        }
    }

    /** One skill of M(L): its ID, stored name and the learner's state (absent row as {@code UNKNOWN}). */
    public record PracticeSkill(UUID skillId, String name, MasteryState state) {
        public PracticeSkill {
            if (skillId == null || name == null || state == null) {
                throw new IllegalArgumentException("Practice skill fields are required");
            }
        }
    }

    /** A published practice Quiz of the Course whose every Question's skill is in M(L). */
    public record PracticeQuiz(UUID quizId, String title, Set<DifficultyLevel> difficulties) {
        public PracticeQuiz {
            if (quizId == null || title == null || difficulties == null) {
                throw new IllegalArgumentException("Practice quiz fields are required");
            }
            try {
                difficulties = Set.copyOf(difficulties);
            } catch (NullPointerException cause) {
                throw new IllegalArgumentException("Difficulties must not contain null", cause);
            }
        }
    }

    /** The recommended practice Quiz and its explanation (OpenAPI {@code B8RecommendedPractice}). */
    public record PracticeChoice(
            UUID quizId,
            String quizTitle,
            DifficultyLevel difficulty,
            DifficultyLevel targetDifficulty,
            boolean fallback,
            UUID skillId,
            String skillName,
            MasteryState skillState,
            String reason) {
    }
}
