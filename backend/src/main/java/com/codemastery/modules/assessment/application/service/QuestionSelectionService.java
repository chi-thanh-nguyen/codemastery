package com.codemastery.modules.assessment.application.service;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.modules.assessment.domain.model.Question;
import com.codemastery.modules.assessment.domain.repository.QuestionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
public class QuestionSelectionService {

    public interface SkillCourseLookup {
        Optional<UUID> findCourseIdBySkillId(UUID skillId);
    }

    private final QuestionRepository questionRepository;
    private final ObjectProvider<SkillCourseLookup> skillCourseLookupProvider;

    public QuestionSelectionService(
            QuestionRepository questionRepository,
            ObjectProvider<SkillCourseLookup> skillCourseLookupProvider
    ) {
        this.questionRepository = Objects.requireNonNull(questionRepository);
        this.skillCourseLookupProvider = Objects.requireNonNull(skillCourseLookupProvider);
    }

    public List<Question> resolveOrderedCourseQuestions(UUID courseId, List<UUID> questionIds) {
        Objects.requireNonNull(courseId, "courseId");
        if (questionIds == null) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "questionIds must not be null");
        }
        if (questionIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> uniqueIds = new HashSet<>(questionIds.size());
        for (UUID questionId : questionIds) {
            if (questionId == null) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "questionIds must not contain null");
            }
            if (!uniqueIds.add(questionId)) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "questionIds must be unique");
            }
        }

        List<Question> fetched = questionRepository.findAllByIds(uniqueIds);
        if (fetched.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more questions were not found");
        }

        Map<UUID, Question> byId = new HashMap<>(fetched.size());
        for (Question question : fetched) {
            byId.put(question.id(), question);
        }

        SkillCourseLookup lookup = skillCourseLookupProvider.getIfAvailable();
        if (lookup == null) {
            throw new IllegalStateException("CT40 SkillCourseLookup provider is not available");
        }

        List<Question> ordered = new ArrayList<>(questionIds.size());
        for (UUID questionId : questionIds) {
            Question question = byId.get(questionId);
            UUID questionCourseId = lookup.findCourseIdBySkillId(question.skill_id())
                    .orElseThrow(() -> new ResourceNotFoundException("Question skill not found"));
            if (!courseId.equals(questionCourseId)) {
                throw new ResourceNotFoundException("Question does not belong to target course");
            }
            ordered.add(question);
        }
        return List.copyOf(ordered);
    }
}