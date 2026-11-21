package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * CT23 question selection for quiz composition (ADR007 §§5, 7): exact explicit IDs in the supplied
 * order only. No count cap, randomization, weighting or adaptive/difficulty selection.
 *
 * <p>Course scope comes only from the approved CT40 projection
 * {@code SkillGraphService.readSkillIds(courseId)} (ADR005 §3.2). Its producer, AD07 (#165), is not
 * implemented, so {@link #readSkillIds(UUID)} fails closed with a safe 500 instead of substituting
 * another scope lookup. It mirrors the approved signature and becomes a single delegation when the
 * producer is integrated. Callers invoke this service only inside the Course configuration guard.
 */
@Service
public class QuestionSelectionService {

    private static final Set<String> KINDS = Set.of("single_choice", "multiple_choice", "true_false");
    private static final Set<String> DIFFICULTIES = Arrays.stream(DifficultyLevel.values())
            .map(DifficultyLevel::getCode).collect(Collectors.toUnmodifiableSet());
    private static final List<String> TRUE_FALSE_OPTIONS = List.of("True", "False");
    private static final Pattern NON_SPACE = Pattern.compile("\\S", Pattern.UNICODE_CHARACTER_CLASS);

    private final QuizRepository quizzes;

    public QuestionSelectionService(QuizRepository quizzes) {
        this.quizzes = Objects.requireNonNull(quizzes, "quizzes");
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
        throw new IllegalStateException("CT40 SkillGraphService.readSkillIds is not implemented; failing closed");
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
        // PostgreSQL text stores neither NUL nor an unpaired UTF-16 surrogate (PgJDBC would write '?').
        return value != null && NON_SPACE.matcher(value).find() && value.codePoints().noneMatch(codePoint -> codePoint == 0
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    private static IllegalStateException invalidStoredQuestion() {
        return new IllegalStateException("Stored question definition violates V004");
    }
}
