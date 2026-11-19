package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.api.dto.QuestionRequest;
import com.codemastery.modules.assessment.api.dto.QuestionResponse;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.AttemptRepository;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import com.codemastery.modules.course.application.service.CourseAccessService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionBankService {

    private static final Set<String> ALLOWED_KINDS = Set.of("single_choice", "multiple_choice", "true_false");
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of(
            DifficultyLevel.EASY.getCode(),
            DifficultyLevel.MEDIUM.getCode(),
            DifficultyLevel.HARD.getCode()
    );

    /** Cổng tra cứu CT40 (chờ duyệt/nối từ module adaptive). */
    @FunctionalInterface
    public interface SkillLookup {
        Optional<UUID> findCourseIdBySkillId(UUID skillId);
    }

    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final CourseAccessService courseAccessService;
    private final SkillLookup skillLookup;

    @Autowired
    public QuestionBankService(
            QuestionRepository questionRepository,
            AttemptRepository attemptRepository,
            CourseAccessService courseAccessService,
            ObjectProvider<SkillLookup> skillLookupProvider
    ) {
        this(questionRepository, attemptRepository, courseAccessService, skillLookupProvider.getIfAvailable());
    }

    // Constructor dùng cho Unit Test (Mockito)
    QuestionBankService(
            QuestionRepository questionRepository,
            AttemptRepository attemptRepository,
            CourseAccessService courseAccessService,
            SkillLookup skillLookup
    ) {
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.courseAccessService = courseAccessService;
        this.skillLookup = skillLookup;
    }

    @Transactional
    public QuestionResponse createQuestion(UUID courseId, QuestionRequest request) {
        courseAccessService.requireCourseOwnership(courseId);
        validateQuestionInput(request);
        requireSkillBelongsToCourse(courseId, request.skillId());

        Question question = new Question(
                UUID.randomUUID(),
                request.skillId(),
                request.prompt(),
                request.kind(),
                request.difficulty(),
                request.options().toArray(String[]::new),
                request.correctOptions().toArray(Boolean[]::new)
        );
        return QuestionResponse.from(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> listQuestions(
            UUID courseId, UUID skillId, String difficulty, int page, int size) {
        courseAccessService.requireCourseOwnership(courseId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("Invalid pagination parameters");
        }
        if (difficulty != null && !ALLOWED_DIFFICULTIES.contains(difficulty)) {
            throw new BusinessRuleException("Invalid difficulty filter");
        }
        if (skillId == null) {
            throw new BusinessRuleException("skillId filter is required");
        }
        requireSkillBelongsToCourse(courseId, skillId);

        List<Question> all = (difficulty == null)
                ? questionRepository.findBySkillId(skillId)
                : questionRepository.findBySkillIdAndDifficulty(skillId, difficulty);

        int totalElements = all.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<QuestionResponse> content = all.subList(fromIndex, toIndex).stream()
                .map(QuestionResponse::from)
                .toList();

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(UUID courseId, UUID questionId) {
        courseAccessService.requireCourseOwnership(courseId);
        Question question = requireScopedQuestion(courseId, questionId);
        return QuestionResponse.from(question);
    }

    @Transactional
    public QuestionResponse replaceQuestion(UUID courseId, UUID questionId, QuestionRequest request) {
        courseAccessService.requireCourseOwnership(courseId);
        validateQuestionInput(request);
        Question existing = requireScopedQuestion(courseId, questionId);
        requireSkillBelongsToCourse(courseId, request.skillId());
        requireQuestionUnused(existing.id());

        Question updated = new Question(
                existing.id(),
                request.skillId(),
                request.prompt(),
                request.kind(),
                request.difficulty(),
                request.options().toArray(String[]::new),
                request.correctOptions().toArray(Boolean[]::new)
        );
        return QuestionResponse.from(questionRepository.save(updated));
    }

    @Transactional
    public QuestionResponse deleteQuestion(UUID courseId, UUID questionId) {
        courseAccessService.requireCourseOwnership(courseId);
        Question existing = requireScopedQuestion(courseId, questionId);
        requireQuestionUnused(existing.id());

        questionRepository.deleteById(existing.id());
        return QuestionResponse.from(existing);
    }

    private Question requireScopedQuestion(UUID courseId, UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        requireSkillBelongsToCourse(courseId, question.skill_id());
        return question;
    }

    private void requireSkillBelongsToCourse(UUID courseId, UUID skillId) {
        if (skillLookup == null) {
            throw new IllegalStateException("CT40 SkillLookup provider is not configured");
        }
        UUID owningCourseId = skillLookup.findCourseIdBySkillId(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
        if (!courseId.equals(owningCourseId)) {
            throw new ResourceNotFoundException("Skill does not belong to course");
        }
    }

    private void requireQuestionUnused(UUID questionId) {
        if (questionRepository.isUsedInQuiz(questionId) || attemptRepository.hasAnswersForQuestion(questionId)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Question is already referenced");
        }
    }

    private void validateQuestionInput(QuestionRequest request) {
        if (request == null || request.skillId() == null) {
            throw new BusinessRuleException("Missing required question fields");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new BusinessRuleException("Prompt must not be blank");
        }
        if (request.kind() == null || !ALLOWED_KINDS.contains(request.kind())) {
            throw new BusinessRuleException("Invalid question kind");
        }
        if (request.difficulty() == null || !ALLOWED_DIFFICULTIES.contains(request.difficulty())) {
            throw new BusinessRuleException("Invalid difficulty level");
        }

        List<String> options = request.options();
        List<Boolean> correctOptions = request.correctOptions();
        if (options == null || correctOptions == null
                || options.size() < 2
                || options.size() != correctOptions.size()) {
            throw new BusinessRuleException("Options and correctOptions must have equal length >= 2");
        }
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new BusinessRuleException("Option text must not be null or blank");
            }
        }
        long trueCount = 0;
        for (Boolean flag : correctOptions) {
            if (flag == null) {
                throw new BusinessRuleException("correctOptions must not contain null");
            }
            if (flag) {
                trueCount++;
            }
        }

        if ("true_false".equals(request.kind())) {
            if (!List.of("True", "False").equals(options)) {
                throw new BusinessRuleException("true_false options must be exactly [True, False]");
            }
        }
        if ("single_choice".equals(request.kind()) || "true_false".equals(request.kind())) {
            if (trueCount != 1) {
                throw new BusinessRuleException("single_choice and true_false must have exactly one true answer");
            }
        } else if (trueCount < 1) {
            throw new BusinessRuleException("multiple_choice must have at least one true answer");
        }
    }
}