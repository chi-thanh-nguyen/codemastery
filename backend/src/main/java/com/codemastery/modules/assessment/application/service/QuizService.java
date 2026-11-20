package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.dto.QuizRequest.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.QuizRequest.WriteQuizRequest;
import com.codemastery.modules.assessment.api.dto.QuizResponses.OwnedQuizResponse;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuestionDefinition;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuizQuestionItem;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuizSummaryResponse;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.model.Quiz;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.QuizQuestionMembership;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    public static final String STATUS_HIDDEN = "hidden";
    public static final String STATUS_PUBLISHED = "published";

    private static final Set<String> ALLOWED_KINDS = Set.of("diagnostic", "practice", "test");

    private final QuizRepository quizRepository;
    private final QuestionSelectionService questionSelectionService;
    private final CourseAccessService courseAccessService;

    public QuizService(
            QuizRepository quizRepository,
            QuestionSelectionService questionSelectionService,
            CourseAccessService courseAccessService
    ) {
        this.quizRepository = Objects.requireNonNull(quizRepository);
        this.questionSelectionService = Objects.requireNonNull(questionSelectionService);
        this.courseAccessService = Objects.requireNonNull(courseAccessService);
    }

    @Transactional
    public OwnedQuizResponse createQuiz(UUID courseId, WriteQuizRequest request) {
        courseAccessService.requireCourseOwnership(courseId);
        validateWriteRequest(request);

        List<Question> orderedQuestions = questionSelectionService.resolveOrderedCourseQuestions(
                courseId, request.questionIds());

        Quiz draft = new Quiz(
                UUID.randomUUID(),
                courseId,
                request.title(),
                request.kind(),
                request.timeLimitSeconds(),
                STATUS_HIDDEN
        );

        Quiz saved = quizRepository.save(draft, request.questionIds());
        return toOwnedResponse(saved, orderedQuestions);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuizSummaryResponse> listLearnerQuizzes(UUID courseId, Integer page, Integer size) {
        int pageNum = page == null ? PageResponse.DEFAULT_PAGE : page;
        int pageSize = size == null ? PageResponse.DEFAULT_SIZE : size;
        PageResponse.validateRequest(pageNum, pageSize);

        courseAccessService.requireLearnerCourseAccess(courseId);

        List<Quiz> published = quizRepository.findPublishedByCourseId(courseId);
        List<Quiz> slice = slicePage(published, pageNum, pageSize);

        List<QuizSummaryResponse> content = slice.stream()
                .map(quiz -> {
                    int questionCount = quizRepository.findMembershipsByQuizId(quiz.id()).size();
                    return new QuizSummaryResponse(
                            quiz.id(),
                            quiz.course_id(),
                            quiz.title(),
                            quiz.kind(),
                            quiz.time_limit_seconds(),
                            STATUS_PUBLISHED.equals(quiz.publication_status()),
                            questionCount
                    );
                })
                .toList();

        return new PageResponse<>(content, pageNum, pageSize, published.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnedQuizResponse> listOwnedQuizzes(UUID courseId, Integer page, Integer size) {
        int pageNum = page == null ? PageResponse.DEFAULT_PAGE : page;
        int pageSize = size == null ? PageResponse.DEFAULT_SIZE : size;
        PageResponse.validateRequest(pageNum, pageSize);

        courseAccessService.requireCourseOwnership(courseId);

        List<Quiz> owned = quizRepository.findByCourseId(courseId);
        List<Quiz> slice = slicePage(owned, pageNum, pageSize);

        List<OwnedQuizResponse> content = slice.stream()
                .map(quiz -> loadOwnedResponse(courseId, quiz))
                .toList();

        return new PageResponse<>(content, pageNum, pageSize, owned.size());
    }

    @Transactional(readOnly = true)
    public OwnedQuizResponse getOwnedQuiz(UUID courseId, UUID quizId) {
        courseAccessService.requireCourseOwnership(courseId);
        Quiz quiz = requireScopedQuiz(courseId, quizId);
        return loadOwnedResponse(courseId, quiz);
    }

    @Transactional
    public OwnedQuizResponse replaceUnusedHiddenQuiz(UUID courseId, UUID quizId, WriteQuizRequest request) {
        courseAccessService.requireCourseOwnership(courseId);
        Quiz existing = requireScopedQuiz(courseId, quizId);
        requireMutableHiddenUnused(existing);
        validateWriteRequest(request);

        List<Question> orderedQuestions = questionSelectionService.resolveOrderedCourseQuestions(
                courseId, request.questionIds());

        Quiz updated = new Quiz(
                existing.id(),
                existing.course_id(),
                request.title(),
                request.kind(),
                request.timeLimitSeconds(),
                STATUS_HIDDEN
        );

        Quiz saved = quizRepository.save(updated, request.questionIds());
        return toOwnedResponse(saved, orderedQuestions);
    }

    @Transactional
    public OwnedQuizResponse deleteUnusedHiddenQuiz(UUID courseId, UUID quizId) {
        courseAccessService.requireCourseOwnership(courseId);
        Quiz existing = requireScopedQuiz(courseId, quizId);
        requireMutableHiddenUnused(existing);

        OwnedQuizResponse snapshot = loadOwnedResponse(courseId, existing);
        quizRepository.deleteById(existing.id());
        return snapshot;
    }

    @Transactional
    public OwnedQuizResponse setQuizPublication(UUID courseId, UUID quizId, PublicationRequest request) {
        courseAccessService.requireCourseOwnership(courseId);
        if (request == null || request.published() == null) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "published flag must not be null");
        }

        Quiz existing = requireScopedQuiz(courseId, quizId);
        List<QuizQuestionMembership> memberships = quizRepository.findMembershipsByQuizId(existing.id());
        List<UUID> orderedQuestionIds = memberships.stream()
                .map(QuizQuestionMembership::questionId)
                .toList();

        if (request.published()) {
            validatePublishable(courseId, existing, orderedQuestionIds);
            List<Question> orderedQuestions = questionSelectionService.resolveOrderedCourseQuestions(
                    courseId, orderedQuestionIds);
            Quiz updated = STATUS_PUBLISHED.equals(existing.publication_status())
                    ? existing
                    : quizRepository.updatePublicationStatus(existing.id(), STATUS_PUBLISHED);
            return toOwnedResponse(updated, orderedQuestions);
        }

        Quiz updated = STATUS_HIDDEN.equals(existing.publication_status())
                ? existing
                : quizRepository.updatePublicationStatus(existing.id(), STATUS_HIDDEN);
        return loadOwnedResponse(courseId, updated);
    }

    private void validatePublishable(UUID courseId, Quiz quiz, List<UUID> orderedQuestionIds) {
        if (quiz.time_limit_seconds() == null || quiz.time_limit_seconds() < 1) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Quiz must define a positive time limit before publication");
        }
        if (orderedQuestionIds.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Cannot publish an empty quiz");
        }
        try {
            questionSelectionService.resolveOrderedCourseQuestions(courseId, orderedQuestionIds);
        } catch (ResourceNotFoundException ex) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Quiz contains questions outside the target course");
        }
    }

    private void requireMutableHiddenUnused(Quiz quiz) {
        if (!STATUS_HIDDEN.equals(quiz.publication_status())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Published quiz cannot be modified or deleted");
        }
        if (quizRepository.hasAttempts(quiz.id())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Quiz with existing attempts cannot be modified or deleted");
        }
    }

    private Quiz requireScopedQuiz(UUID courseId, UUID quizId) {
        if (quizId == null) {
            throw new IllegalArgumentException("quizId must not be null");
        }
        return quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found in course"));
    }

    private OwnedQuizResponse loadOwnedResponse(UUID courseId, Quiz quiz) {
        List<QuizQuestionMembership> memberships = quizRepository.findMembershipsByQuizId(quiz.id());
        List<UUID> orderedIds = memberships.stream()
                .map(QuizQuestionMembership::questionId)
                .toList();
        List<Question> orderedQuestions = questionSelectionService.resolveOrderedCourseQuestions(courseId, orderedIds);
        return toOwnedResponse(quiz, orderedQuestions);
    }

    private static OwnedQuizResponse toOwnedResponse(Quiz quiz, List<Question> orderedQuestions) {
        List<QuizQuestionItem> items = new ArrayList<>(orderedQuestions.size());
        for (int i = 0; i < orderedQuestions.size(); i++) {
            Question q = orderedQuestions.get(i);
            QuestionDefinition def = new QuestionDefinition(
                    q.id(),
                    q.skill_id(),
                    q.prompt(),
                    q.kind(),
                    q.difficulty(),
                    List.copyOf(Arrays.asList(q.options())),
                    List.copyOf(Arrays.asList(q.correct_options()))
            );
            items.add(new QuizQuestionItem(i + 1, def));
        }
        return new OwnedQuizResponse(
                quiz.id(),
                quiz.course_id(),
                quiz.title(),
                quiz.kind(),
                quiz.time_limit_seconds(),
                STATUS_PUBLISHED.equals(quiz.publication_status()),
                items.size(),
                List.copyOf(items)
        );
    }

    private static void validateWriteRequest(WriteQuizRequest request) {
        if (request == null) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Request body must not be null");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Quiz title must contain non-whitespace characters");
        }
        if (request.kind() == null || !ALLOWED_KINDS.contains(request.kind())) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid quiz kind");
        }
        if (request.timeLimitSeconds() == null || request.timeLimitSeconds() < 1) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "timeLimitSeconds must be at least 1");
        }
        if (request.questionIds() == null) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "questionIds must not be null");
        }
    }

    private static <T> List<T> slicePage(List<T> all, int page, int size) {
        int fromIndex = page * size;
        if (fromIndex >= all.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + size, all.size());
        return all.subList(fromIndex, toIndex);
    }
}