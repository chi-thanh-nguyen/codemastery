package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.Member;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CT23.composition, CT23.publication and the QuizService bindings of CT23.bindings
 * (ADR007 §§5–7).
 *
 * <p>Every operation first consumes a CT03 assertion: Course ownership for authoring and owner
 * reads, Learner Course access for the learner list. Writes then run inside ADR005's Course
 * configuration guard, which locks the Course root before the quiz row, so composition, publication
 * and the attempt-existence probe share one serialization point with attempt start.
 * {@link #withCourseConfigurationWrite(UUID, Supplier)} mirrors the approved producer signature and
 * delegates to CO14's (#177) guard. Question scope comes from {@link QuestionSelectionService},
 * which reads AD07's (#165) CT40 {@code SkillGraphService.readSkillIds} under the held root.
 *
 * <p>Reads run in one read-only REPEATABLE READ snapshot covering admission, rows and count.
 */
@Service
public class QuizService {

    static final String HIDDEN = "hidden";
    static final String PUBLISHED = "published";
    private static final Set<String> KINDS = Set.of("diagnostic", "practice", "test");
    private static final Set<String> STATUSES = Set.of(HIDDEN, PUBLISHED);

    /** Closed B3QuizWrite input; the list is copied on entry and may hold nulls until validated. */
    public record QuizInput(String title, String kind, Integer timeLimitSeconds, List<UUID> questionIds) {
        public QuizInput {
            questionIds = questionIds == null ? null : Collections.unmodifiableList(new ArrayList<>(questionIds));
        }
    }

    /** Owner representation of a stored question, including its answer key; deeply immutable. */
    public record QuestionDefinition(UUID id, UUID skillId, String prompt, String kind, String difficulty,
                                     List<String> options, List<Boolean> correctOptions) {
        public QuestionDefinition {
            options = List.copyOf(options);
            correctOptions = List.copyOf(correctOptions);
        }
    }

    public record QuizQuestionView(int position, QuestionDefinition question) {
        public QuizQuestionView {
            Objects.requireNonNull(question, "question");
        }
    }

    /** B3QuizOwned: the full ordered definition, for the owning Instructor only. */
    public record QuizView(UUID id, UUID courseId, String title, String kind, int timeLimitSeconds,
                           boolean published, int questionCount, List<QuizQuestionView> questions) {
        public QuizView {
            questions = List.copyOf(questions);
        }
    }

    /** B3QuizSummary: Learner metadata only, never question content or keys. */
    public record QuizSummary(UUID id, UUID courseId, String title, String kind, int timeLimitSeconds,
                              boolean published, int questionCount) {}

    private final QuizRepository quizzes;
    private final QuestionSelectionService selection;
    private final CourseAccessService courseAccess;
    private final CourseManagementService courseConfiguration;

    public QuizService(QuizRepository quizzes, QuestionSelectionService selection,
                       CourseAccessService courseAccess, CourseManagementService courseConfiguration) {
        this.quizzes = Objects.requireNonNull(quizzes, "quizzes");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.courseAccess = Objects.requireNonNull(courseAccess, "courseAccess");
        this.courseConfiguration = Objects.requireNonNull(courseConfiguration, "courseConfiguration");
    }

    /** Creates a hidden quiz with the exact supplied membership (an empty draft is allowed). */
    public QuizView create(UUID courseId, QuizInput input) {
        requireId(courseId);
        courseAccess.requireCourseOwnership(courseId);
        requireValidInput(input);
        return withCourseConfigurationWrite(courseId, () -> {
            List<Question> questions = selection.requireOrderedCourseQuestions(courseId, input.questionIds());
            Quiz saved = quizzes.save(new Quiz(UUID.randomUUID(), courseId, input.title(), input.kind(),
                    input.timeLimitSeconds(), HIDDEN));
            quizzes.replaceMembership(saved.id(), input.questionIds());
            return view(courseId, saved, numbered(questions));
        });
    }

    /** Full replacement of title, kind, time limit and membership while hidden and never attempted. */
    public QuizView update(UUID courseId, UUID quizId, QuizInput input) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireCourseOwnership(courseId);
        requireValidInput(input);
        return withCourseConfigurationWrite(courseId, () -> {
            Quiz current = lockedQuiz(courseId, quizId);
            requireEditable(current);
            List<Question> questions = selection.requireOrderedCourseQuestions(courseId, input.questionIds());
            Quiz saved = quizzes.save(new Quiz(current.id(), courseId, input.title(), input.kind(),
                    input.timeLimitSeconds(), HIDDEN));
            quizzes.replaceMembership(saved.id(), input.questionIds());
            return view(courseId, saved, numbered(questions));
        });
    }

    /** Deletes a hidden, never-attempted quiz and only its own membership; returns the deleted definition. */
    public QuizView delete(UUID courseId, UUID quizId) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireCourseOwnership(courseId);
        return withCourseConfigurationWrite(courseId, () -> {
            Quiz current = lockedQuiz(courseId, quizId);
            requireEditable(current);
            QuizView deleted = view(courseId, current, members(current.id()));
            quizzes.delete(current.id());
            return deleted;
        });
    }

    /**
     * Idempotent expected-state command. Publishing (or republishing) requires a nonempty, valid,
     * same-Course membership: an empty draft is 409, an invalid stored definition is 500. Hiding
     * never cancels or deletes existing attempts and never unfreezes a used quiz.
     */
    public QuizView publish(UUID courseId, UUID quizId, boolean published) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireCourseOwnership(courseId);
        return withCourseConfigurationWrite(courseId, () -> {
            Quiz current = lockedQuiz(courseId, quizId);
            List<Member> members = members(current.id());
            if (published) {
                if (members.isEmpty()) {
                    throw new BusinessRuleException(HttpStatus.CONFLICT, "An empty quiz cannot be published");
                }
                selection.requireCourseMembers(courseId, members.stream().map(Member::question).toList());
            }
            String target = published ? PUBLISHED : HIDDEN;
            Quiz result = target.equals(current.publication_status()) ? current
                    : quizzes.save(new Quiz(current.id(), current.course_id(), current.title(), current.kind(),
                            current.time_limit_seconds(), target));
            return view(courseId, result, members);
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizView readOwned(UUID courseId, UUID quizId) {
        requireId(courseId);
        requireId(quizId);
        courseAccess.requireCourseOwnership(courseId);
        Quiz quiz = quizzes.findInCourse(quizId, courseId).orElseThrow(QuizService::unavailable);
        return view(courseId, quiz, members(quiz.id()));
    }

    /** Hidden and published quizzes with full definitions; title COLLATE "C" then UUID. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<QuizView> listOwned(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireCourseOwnership(courseId);
        long total = quizzes.countInCourse(courseId, false);
        List<Quiz> rows = page(courseId, false, page, size, total);
        Map<UUID, List<Member>> members = quizzes.findMembers(rows.stream().map(Quiz::id).toList());
        List<QuizView> content = rows.stream()
                .map(quiz -> view(courseId, quiz, members.getOrDefault(quiz.id(), List.of())))
                .toList();
        return new PageResponse<>(content, page, size, total);
    }

    /** Published quizzes of an admitted Learner's published Course; metadata only. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<QuizSummary> listForLearner(UUID courseId, int page, int size) {
        requireId(courseId);
        PageResponse.validateRequest(page, size);
        courseAccess.requireLearnerCourseAccess(courseId);
        long total = quizzes.countInCourse(courseId, true);
        List<Quiz> rows = page(courseId, true, page, size, total);
        Map<UUID, Integer> counts = quizzes.countMembers(rows.stream().map(Quiz::id).toList());
        List<QuizSummary> content = rows.stream().map(quiz -> {
            requireStoredQuiz(courseId, quiz);
            if (!PUBLISHED.equals(quiz.publication_status())) {
                throw new IllegalStateException("Learner quiz page returned an unpublished quiz");
            }
            return new QuizSummary(quiz.id(), quiz.course_id(), quiz.title(), quiz.kind(),
                    quiz.time_limit_seconds(), true, counts.getOrDefault(quiz.id(), 0));
        }).toList();
        return new PageResponse<>(content, page, size, total);
    }

    /**
     * ADR005 §5/§13.3 {@code CourseManagementService.withCourseConfigurationWrite(courseId, work)}:
     * the Course-root READ COMMITTED write unit that owns the transaction. Producer: CO14 (#177).
     */
    protected <T> T withCourseConfigurationWrite(UUID courseId, Supplier<T> work) {
        return courseConfiguration.withCourseConfigurationWrite(courseId, work);
    }

    private List<Quiz> page(UUID courseId, boolean publishedOnly, int page, int size, long total) {
        // Widened arithmetic: a page past the end is empty, never an int wrap or a row query.
        return (long) page * size >= total ? List.of() : quizzes.findPageInCourse(courseId, publishedOnly, page, size);
    }

    private Quiz lockedQuiz(UUID courseId, UUID quizId) {
        return quizzes.findInCourseForUpdate(quizId, courseId).orElseThrow(QuizService::unavailable);
    }

    /** Any Attempt, active or submitted, permanently freezes the definition; publication does too. */
    private void requireEditable(Quiz quiz) {
        if (!HIDDEN.equals(quiz.publication_status())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "A published quiz cannot be changed");
        }
        if (quizzes.hasAttempts(quiz.id())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "A quiz with attempts cannot be changed");
        }
    }

    private List<Member> members(UUID quizId) {
        return quizzes.findMembers(List.of(quizId)).getOrDefault(quizId, List.of());
    }

    private static List<Member> numbered(List<Question> questions) {
        List<Member> members = new ArrayList<>(questions.size());
        for (int index = 0; index < questions.size(); index++) {
            members.add(new Member(index + 1, questions.get(index)));
        }
        return members;
    }

    private static QuizView view(UUID courseId, Quiz quiz, List<Member> members) {
        requireStoredQuiz(courseId, quiz);
        List<QuizQuestionView> questions = new ArrayList<>(members.size());
        for (int index = 0; index < members.size(); index++) {
            Member member = members.get(index);
            if (member.position() != index + 1) {
                throw new IllegalStateException("Stored quiz membership positions are not 1..N");
            }
            Question question = QuestionSelectionService.requireValidDefinition(member.question());
            questions.add(new QuizQuestionView(member.position(), new QuestionDefinition(question.id(),
                    question.skill_id(), question.prompt(), question.kind(), question.difficulty(),
                    List.of(question.options()), List.of(question.correct_options()))));
        }
        return new QuizView(quiz.id(), quiz.course_id(), quiz.title(), quiz.kind(), quiz.time_limit_seconds(),
                PUBLISHED.equals(quiz.publication_status()), questions.size(), questions);
    }

    /** A stored row outside V004 or outside the requested Course is an integrity failure (500). */
    private static void requireStoredQuiz(UUID courseId, Quiz quiz) {
        if (quiz == null || quiz.id() == null || !courseId.equals(quiz.course_id()) || !isText(quiz.title())
                || !KINDS.contains(quiz.kind()) || quiz.time_limit_seconds() == null
                || quiz.time_limit_seconds() < 1 || !STATUSES.contains(quiz.publication_status())) {
            throw new IllegalStateException("Stored quiz violates V004");
        }
    }

    private static void requireValidInput(QuizInput input) {
        if (input == null || !isText(input.title()) || input.kind() == null || !KINDS.contains(input.kind())
                || input.timeLimitSeconds() == null || input.timeLimitSeconds() < 1
                || input.questionIds() == null || input.questionIds().stream().anyMatch(Objects::isNull)
                || new HashSet<>(input.questionIds()).size() != input.questionIds().size()) {
            throw new BusinessRuleException("Invalid quiz definition");
        }
    }

    /** Nonblank and representable in PostgreSQL text (no NUL, no unpaired surrogate); never trimmed. */
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

    private static void requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identifier must not be null");
        }
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("Quiz not available in this Course");
    }
}
