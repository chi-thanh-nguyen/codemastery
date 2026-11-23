package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.adaptive.application.skillgraph.SkillGraphService;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CT23.questions and the QuestionBankService bindings of CT23.bindings (ADR007 §§4, 7).
 *
 * <p>Every operation first consumes CT03 {@link CourseAccessService#requireCourseOwnership(UUID)}.
 * Course scope comes only from CT40 (ADR005 §3.2) and mutations run inside ADR005's Course
 * configuration guard. Each upstream producer is reached through one protected delegation point
 * that mirrors the approved producer signature exactly:
 * <ul>
 *   <li>{@code CourseManagementService.withCourseConfigurationWrite}: CO14 (#177);</li>
 *   <li>{@code SkillGraphService.requireSkillInCourse} and {@code readSkillIds}: AD07 (#165).</li>
 * </ul>
 * Both CT40 lookups join the caller's transaction: inside the guard they read under the held
 * Course root, and in an owner read they share its REPEATABLE READ snapshot.
 */
@Service
public class QuestionBankService {

    private static final Set<String> KINDS = Set.of("single_choice", "multiple_choice", "true_false");
    private static final Set<String> DIFFICULTIES = Arrays.stream(DifficultyLevel.values())
            .map(DifficultyLevel::getCode).collect(Collectors.toUnmodifiableSet());
    private static final List<String> TRUE_FALSE_OPTIONS = List.of("True", "False");

    /** Closed question definition supplied by an authoring caller; lists are copied on entry. */
    public record QuestionInput(UUID skillId, String prompt, String kind, String difficulty,
                                List<String> options, List<Boolean> correctOptions) {
        public QuestionInput {
            options = options == null ? null : Collections.unmodifiableList(new ArrayList<>(options));
            correctOptions = correctOptions == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(correctOptions));
        }
    }

    /** Owner representation of a stored question, including its answer key; deeply immutable. */
    public record QuestionView(UUID id, UUID skillId, String prompt, String kind, String difficulty,
                               List<String> options, List<Boolean> correctOptions) {
        public QuestionView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(difficulty, "difficulty");
            options = List.copyOf(options);
            correctOptions = List.copyOf(correctOptions);
        }
    }

    private final QuestionRepository questions;
    private final CourseAccessService courseAccess;
    private final CourseManagementService courseConfiguration;
    private final SkillGraphService skillGraph;

    public QuestionBankService(QuestionRepository questions, CourseAccessService courseAccess,
                               CourseManagementService courseConfiguration, SkillGraphService skillGraph) {
        this.questions = Objects.requireNonNull(questions, "questions");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.courseConfiguration = Objects.requireNonNull(courseConfiguration, "courseConfiguration");
        this.skillGraph = Objects.requireNonNull(skillGraph, "skillGraph");
    }

    public QuestionView create(UUID courseId, QuestionInput input) {
        requireId(courseId);
        courseAccess.requireCourseOwnership(courseId);
        requireValidInput(input);
        return withCourseConfigurationWrite(courseId, () -> {
            requireSkillInCourse(courseId, input.skillId());
            return toView(questions.save(toQuestion(UUID.randomUUID(), input)));
        });
    }

    /** Full replacement of an unused definition; any AttemptAnswer reference freezes it (409). */
    public QuestionView update(UUID courseId, UUID questionId, QuestionInput input) {
        requireId(courseId);
        requireId(questionId);
        courseAccess.requireCourseOwnership(courseId);
        requireValidInput(input);
        return withCourseConfigurationWrite(courseId, () -> {
            Question current = questions.findInSkillsForUpdate(questionId, courseSkillIds(courseId))
                    .orElseThrow(QuestionBankService::unavailable);
            requireSkillInCourse(courseId, input.skillId());
            if (questions.isReferencedByAttemptAnswer(current.id())) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Question definition is used by an attempt");
            }
            return toView(questions.save(toQuestion(current.id(), input)));
        });
    }

    /** Deletes a question referenced by neither a QuizQuestion nor an AttemptAnswer (else 409). */
    public QuestionView delete(UUID courseId, UUID questionId) {
        requireId(courseId);
        requireId(questionId);
        courseAccess.requireCourseOwnership(courseId);
        return withCourseConfigurationWrite(courseId, () -> {
            Question current = questions.findInSkillsForUpdate(questionId, courseSkillIds(courseId))
                    .orElseThrow(QuestionBankService::unavailable);
            QuestionView deleted = toView(current);
            if (questions.isReferencedByQuizQuestion(current.id())
                    || questions.isReferencedByAttemptAnswer(current.id())) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "Question is referenced");
            }
            questions.delete(current.id());
            return deleted;
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuestionView read(UUID courseId, UUID questionId) {
        requireId(courseId);
        requireId(questionId);
        courseAccess.requireCourseOwnership(courseId);
        return questions.findInSkills(questionId, courseSkillIds(courseId))
                .map(this::toView)
                .orElseThrow(QuestionBankService::unavailable);
    }

    /** Course-wide page in UUID ascending order; scope, count and rows share one snapshot. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<QuestionView> list(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireCourseOwnership(courseId);
        List<UUID> skillIds = courseSkillIds(courseId);
        long total = questions.countInSkills(skillIds);
        // Widened arithmetic: a page past the end is empty, never an int wrap or a row query.
        List<QuestionView> content = (long) page * size >= total ? List.of()
                : questions.findPageInSkills(skillIds, page, size).stream().map(this::toView).toList();
        return new PageResponse<>(content, page, size, total);
    }

    /**
     * CT40 {@code SkillGraphService.requireSkillInCourse(courseId, skillId)} (ADR005 §3.2): returns
     * normally for a skill of the Course, otherwise the same safe 404. Producer: AD07 (#165).
     */
    protected void requireSkillInCourse(UUID courseId, UUID skillId) {
        skillGraph.requireSkillInCourse(courseId, skillId);
    }

    /**
     * CT40 {@code SkillGraphService.readSkillIds(courseId)} (ADR005 §3.2): the complete, unique
     * skill set of the Course. Producer: AD07 (#165).
     */
    protected List<UUID> readSkillIds(UUID courseId) {
        return skillGraph.readSkillIds(courseId);
    }

    /**
     * ADR005 §5/§13.3 {@code CourseManagementService.withCourseConfigurationWrite(courseId, work)}:
     * the Course-root READ COMMITTED write unit that owns the transaction. Producer: CO14 (#177).
     */
    protected <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        return courseConfiguration.withCourseConfigurationWrite(courseId, work);
    }

    private List<UUID> courseSkillIds(UUID courseId) {
        List<UUID> skillIds = readSkillIds(courseId);
        if (skillIds == null || skillIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(skillIds).size() != skillIds.size()) {
            throw new IllegalStateException("Invalid CT40 skill projection");
        }
        return List.copyOf(skillIds);
    }

    private void requireValidInput(QuestionInput input) {
        if (input == null || input.skillId() == null || !isDefinition(input.prompt(), input.kind(),
                input.difficulty(), input.options(), input.correctOptions())) {
            throw new BusinessRuleException("Invalid question definition");
        }
    }

    /** V004/data-model §11 question validity; shared by input validation and stored-row integrity. */
    private static boolean isDefinition(String prompt, String kind, String difficulty,
                                        List<String> options, List<Boolean> correctOptions) {
        if (!isText(prompt) || kind == null || !KINDS.contains(kind)
                || difficulty == null || !DIFFICULTIES.contains(difficulty)
                || options == null || correctOptions == null
                || options.size() < 2 || options.size() != correctOptions.size()
                || !options.stream().allMatch(QuestionBankService::isText)
                || correctOptions.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        if (kind.equals("true_false") && !options.equals(TRUE_FALSE_OPTIONS)) {
            return false;
        }
        long keys = correctOptions.stream().filter(Boolean::booleanValue).count();
        return kind.equals("multiple_choice") ? keys >= 1 : keys == 1;
    }

    /**
     * Nonblank and representable in PostgreSQL text: no NUL and no unpaired UTF-16 surrogate, which
     * PgJDBC would store as '?' while the response echoed the input. Never trimmed.
     */
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

    private static Question toQuestion(UUID id, QuestionInput input) {
        return new Question(id, input.skillId(), input.prompt(), input.kind(), input.difficulty(),
                input.options().toArray(String[]::new), input.correctOptions().toArray(Boolean[]::new));
    }

    private QuestionView toView(Question question) {
        if (question == null || question.id() == null || question.skill_id() == null
                || question.options() == null || question.correct_options() == null
                || !isDefinition(question.prompt(), question.kind(), question.difficulty(),
                Arrays.asList(question.options()), Arrays.asList(question.correct_options()))) {
            throw new IllegalStateException("Stored question definition violates V004");
        }
        return new QuestionView(question.id(), question.skill_id(), question.prompt(), question.kind(),
                question.difficulty(), List.of(question.options()), List.of(question.correct_options()));
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Question not available in this Course");
    }
}
