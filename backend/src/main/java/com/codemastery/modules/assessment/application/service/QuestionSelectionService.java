package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * CT23 question selection for quiz composition (ADR007 §§5, 7) and the pure CT07.difficulty
 * practice-selection rule (ADR011 §8).
 *
 * <p>CT23 quiz composition ({@link #requireOrderedCourseQuestions} and {@link #requireCourseMembers})
 * uses exact explicit IDs in the supplied order only, with no count cap, randomization, weighting
 * or adaptive filtering. Under Leader Decision E02 (ADR013 §§19.1–19.2, 19.10), the pure
 * {@link #selectPracticeQuestions} rule remains published with no production caller in Final.
 *
 * <p>Course scope comes only from the approved CT40 projection
 * {@code SkillGraphService.readSkillIds(courseId)} (ADR005 §3.2), produced by AD07 (#165) and reached
 * through the single delegation {@link #readSkillIds(UUID)}. Callers invoke this service only inside
 * the Course configuration guard, so the projection is read under the held Course root.
 */
@Service
public class QuestionSelectionService {

    private static final Set<String> KINDS = Set.of("single_choice", "multiple_choice", "true_false");
    private static final Set<String> DIFFICULTIES = Arrays.stream(DifficultyLevel.values())
            .map(DifficultyLevel::getCode).collect(Collectors.toUnmodifiableSet());
    private static final List<String> TRUE_FALSE_OPTIONS = List.of("True", "False");

    private final QuizRepository quizzes;
    private final SkillGraphService skillGraph;

    public QuestionSelectionService(QuizRepository quizzes, SkillGraphService skillGraph) {
        this.quizzes = Objects.requireNonNull(quizzes, "quizzes");
        this.skillGraph = Objects.requireNonNull(skillGraph, "skillGraph");
    }

    /**
     * Candidate question input for the pure CT07.difficulty selection rule (ADR011 §8).
     */
    public record CandidateQuestion(
            UUID questionId,
            UUID skillId,
            DifficultyLevel difficulty,
            int membershipPosition
    ) {
        public CandidateQuestion {
            Objects.requireNonNull(questionId, "questionId must not be null");
            Objects.requireNonNull(skillId, "skillId must not be null");
            Objects.requireNonNull(difficulty, "difficulty must not be null");
            if (membershipPosition < 1) {
                throw new IllegalArgumentException("membershipPosition must be >= 1");
            }
        }
    }

    /**
     * Approved CT07.difficulty fallback order (ADR011 §8):
     * EASY -> MEDIUM -> HARD, MEDIUM -> EASY -> HARD, HARD -> MEDIUM -> EASY.
     */
    public static List<DifficultyLevel> fallbackOrder(DifficultyLevel target) {
        Objects.requireNonNull(target, "target difficulty must not be null");
        return switch (target) {
            case EASY -> List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD);
            case MEDIUM -> List.of(DifficultyLevel.MEDIUM, DifficultyLevel.EASY, DifficultyLevel.HARD);
            case HARD -> List.of(DifficultyLevel.HARD, DifficultyLevel.MEDIUM, DifficultyLevel.EASY);
        };
    }

    /**
     * Pure CT07.difficulty selection rule (ADR011 §8): per skill, selects candidates at the target
     * difficulty, falling back in {@link #fallbackOrder(DifficultyLevel)} when none exists.
     * Preserves membership order with no randomness and no count cap.
     */
    public static List<CandidateQuestion> selectPracticeQuestions(
            List<CandidateQuestion> candidates,
            Map<UUID, DifficultyLevel> targetDifficultyBySkill
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(targetDifficultyBySkill, "targetDifficultyBySkill must not be null");
        Map<UUID, DifficultyLevel> targets = copyTargetDifficulties(targetDifficultyBySkill);

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<CandidateQuestion> orderedCandidates = new ArrayList<>(candidates.size());
        Set<UUID> seenQuestionIds = new HashSet<>(candidates.size());
        Set<Integer> seenPositions = new HashSet<>(candidates.size());
        Map<UUID, Set<DifficultyLevel>> availableBySkill = new HashMap<>();

        for (CandidateQuestion candidate : candidates) {
            Objects.requireNonNull(candidate, "candidates must not contain null");
            if (!seenQuestionIds.add(candidate.questionId())) {
                throw new IllegalArgumentException("Duplicate candidate questionId: " + candidate.questionId());
            }
            if (!seenPositions.add(candidate.membershipPosition())) {
                throw new IllegalArgumentException(
                        "Duplicate candidate membershipPosition: " + candidate.membershipPosition());
            }
            orderedCandidates.add(candidate);
            availableBySkill
                    .computeIfAbsent(candidate.skillId(), ignored -> EnumSet.noneOf(DifficultyLevel.class))
                    .add(candidate.difficulty());
        }

        orderedCandidates.sort(Comparator.comparingInt(CandidateQuestion::membershipPosition));

        Map<UUID, DifficultyLevel> resolvedDifficultyBySkill = new HashMap<>(availableBySkill.size());
        for (Map.Entry<UUID, Set<DifficultyLevel>> entry : availableBySkill.entrySet()) {
            UUID skillId = entry.getKey();
            DifficultyLevel target = targets.get(skillId);
            if (target == null) {
                throw new IllegalArgumentException("Missing target difficulty for skill: " + skillId);
            }

            Set<DifficultyLevel> available = entry.getValue();
            for (DifficultyLevel candidateLevel : fallbackOrder(target)) {
                if (available.contains(candidateLevel)) {
                    resolvedDifficultyBySkill.put(skillId, candidateLevel);
                    break;
                }
            }
        }

        List<CandidateQuestion> selected = new ArrayList<>();
        for (CandidateQuestion candidate : orderedCandidates) {
            if (candidate.difficulty() == resolvedDifficultyBySkill.get(candidate.skillId())) {
                selected.add(candidate);
            }
        }
        return List.copyOf(selected);
    }

    private static Map<UUID, DifficultyLevel> copyTargetDifficulties(Map<UUID, DifficultyLevel> raw) {
        Map<UUID, DifficultyLevel> copy = new HashMap<>(raw.size());
        for (Map.Entry<UUID, DifficultyLevel> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("targetDifficultyBySkill must not contain null keys or values");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    /**
     * Resolves an explicit, already shape-validated ordered ID list to the Course's questions in the
     * same order. A missing or foreign question is the same safe 404; a stored definition that
     * violates V004 is an integrity failure (500).
     */
    public List<Question> requireOrderedCourseQuestions(UUID courseId, List<UUID> questionIds) {
        Objects.requireNonNull(courseId, "courseId");
        List<UUID> ids = List.copyOf(questionIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Question IDs must be unique");
        }
        Map<UUID, Question> found = new HashMap<>();
        for (Question question : quizzes.findQuestionsInSkills(ids, courseSkillIds(courseId))) {
            found.put(question.id(), question);
        }
        return ids.stream().map(id -> {
            Question question = found.get(id);
            if (question == null) {
                throw new ResourceNotFoundException("Question not available in this Course");
            }
            return requireValidDefinition(question);
        }).toList();
    }

    /**
     * Revalidates captured members for publication: every member must still be a valid question of
     * this Course. Any violation is an integrity failure (500), never a client error.
     */
    public void requireCourseMembers(UUID courseId, List<Question> members) {
        Objects.requireNonNull(courseId, "courseId");
        if (members.isEmpty()) {
            return;
        }
        Set<UUID> skillIds = Set.copyOf(courseSkillIds(courseId));
        for (Question question : members) {
            requireValidDefinition(question);
            if (!skillIds.contains(question.skill_id())) {
                throw new IllegalStateException("Quiz member question is outside the quiz Course");
            }
        }
    }

    /** V004/data-model §11 question validity; a violation by a stored row is an integrity failure. */
    public static Question requireValidDefinition(Question question) {
        if (question == null || question.id() == null || question.skill_id() == null
                || !isText(question.prompt()) || !KINDS.contains(question.kind())
                || !DIFFICULTIES.contains(question.difficulty())
                || question.options() == null || question.correct_options() == null) {
            throw invalidStoredQuestion();
        }
        List<String> options = Arrays.asList(question.options());
        List<Boolean> key = Arrays.asList(question.correct_options());
        if (options.size() < 2 || options.size() != key.size()
                || !options.stream().allMatch(QuestionSelectionService::isText)
                || key.stream().anyMatch(Objects::isNull)
                || question.kind().equals("true_false") && !options.equals(TRUE_FALSE_OPTIONS)) {
            throw invalidStoredQuestion();
        }
        long trueKeys = key.stream().filter(Boolean::booleanValue).count();
        if (question.kind().equals("multiple_choice") ? trueKeys < 1 : trueKeys != 1) {
            throw invalidStoredQuestion();
        }
        return question;
    }

    /**
     * CT40 {@code SkillGraphService.readSkillIds(courseId)} (ADR005 §3.2): the complete, unique skill
     * set of the Course. Producer: AD07 (#165).
     */
    protected List<UUID> readSkillIds(UUID courseId) {
        return skillGraph.readSkillIds(courseId);
    }

    private List<UUID> courseSkillIds(UUID courseId) {
        List<UUID> skillIds = readSkillIds(courseId);
        if (skillIds == null || skillIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(skillIds).size() != skillIds.size()) {
            throw new IllegalStateException("Invalid CT40 skill projection");
        }
        return List.copyOf(skillIds);
    }

    private static boolean isText(String value) {
        // Nonblank as CO06 and AD07 judge it: one code point that is space under none of Java whitespace,
        // Unicode space separators, U+0085 (PostgreSQL [:space:] in the musl image locale) and U+FEFF
        // (OpenAPI's ECMA-262 \S). PostgreSQL text stores neither NUL nor an unpaired UTF-16 surrogate
        // (PgJDBC would write '?').
        return value != null
                && value.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
                        && point != 0x0085 && point != 0xFEFF)
                && value.codePoints().noneMatch(point -> point == 0
                        || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE);
    }

    private static IllegalStateException invalidStoredQuestion() {
        return new IllegalStateException("Stored question definition violates V004");
    }
}
