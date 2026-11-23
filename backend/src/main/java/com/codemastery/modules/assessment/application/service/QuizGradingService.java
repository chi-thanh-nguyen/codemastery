package com.codemastery.modules.assessment.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * AS04 quiz grading: equal-weight exact selection-mask match with no partial credit.
 *
 * <p>This is the module-local typed binding approved by ADR007 §9
 * (CT04-SUBMISSION-2026-11-09-r1). The caller supplies valid, unique, nonempty items in
 * captured order; any other input is outside AS04's accepted domain and receives no
 * default here.
 */
@Service
public class QuizGradingService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Grade grade(List<GradingItem> items) {
        List<GradedItem> gradedItems = new ArrayList<>(items.size());
        int correctCount = 0;
        for (GradingItem item : items) {
            boolean correct = item.selectedOptions().equals(item.correctOptions());
            if (correct) {
                correctCount++;
            }
            gradedItems.add(new GradedItem(item.questionId(), correct));
        }
        BigDecimal scorePercentage = BigDecimal.valueOf(correctCount)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
        return new Grade(scorePercentage, gradedItems);
    }

    public record GradingItem(UUID questionId, List<Boolean> selectedOptions,
            List<Boolean> correctOptions) {

        public GradingItem {
            selectedOptions = List.copyOf(selectedOptions);
            correctOptions = List.copyOf(correctOptions);
        }
    }

    public record GradedItem(UUID questionId, boolean correct) {}

    public record Grade(BigDecimal scorePercentage, List<GradedItem> items) {

        public Grade {
            items = List.copyOf(items);
        }
    }
}
