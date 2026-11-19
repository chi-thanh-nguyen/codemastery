package com.codemastery.modules.assessment.application.service;

import com.codemastery.modules.assessment.domain.model.AttemptAnswer;
import com.codemastery.modules.assessment.domain.model.Question;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QuizGradingService {
    public QuizGradingService() {}

    public BigDecimal gradeQuiz(List<Question> questions, List<AttemptAnswer> answers) {
        Map<UUID, AttemptAnswer> answersByQuestionId = new HashMap<>();
        for (AttemptAnswer answer : answers) 
            answersByQuestionId.put(answer.question_id(), answer);
        int correctCount = 0;
        for (Question question : questions) {
            AttemptAnswer answer = answersByQuestionId.get(question.id());
            if (answer != null && isExactMaskMatch(question.correct_options(), answer.selected_options())) 
                correctCount++;
        }

        return calculatePercentage(correctCount, questions.size());
    }
    
    private boolean isExactMaskMatch(Boolean[] correctOptions, Boolean[] selectedOptions) {
        return Arrays.equals(correctOptions, selectedOptions);
    }

    private BigDecimal calculatePercentage(int correctCount, int totalQuestions) {
        return BigDecimal.valueOf(correctCount)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
    }

}