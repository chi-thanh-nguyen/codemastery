package com.codemastery.modules.admin;

import com.codemastery.modules.admin.domain.model.AuditAction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditActionTest {

    @Test
    void catalogueIsExactlyTheNineApprovedCodesWithTheirTargetTypes() {
        Map<String, String> expected = Map.of(
                "user_role_set_learner", "user",
                "user_role_set_instructor", "user",
                "user_role_set_admin", "user",
                "user_locked", "user",
                "user_unlocked", "user",
                "course_hidden", "course",
                "report_post_hidden", "content_report",
                "report_lesson_hidden", "content_report",
                "report_dismissed", "content_report");

        Map<String, String> actual = Arrays.stream(AuditAction.values())
                .collect(Collectors.toMap(AuditAction::getCode, AuditAction::getTargetType));

        assertEquals(expected, actual);
    }

    @Test
    void fromCodeIsAnExactMatchOnTheStoredCode() {
        for (AuditAction action : AuditAction.values()) {
            assertEquals(Optional.of(action), AuditAction.fromCode(action.getCode()));
        }
        assertTrue(AuditAction.fromCode("USER_LOCKED").isEmpty());
        assertTrue(AuditAction.fromCode(" user_locked").isEmpty());
        assertTrue(AuditAction.fromCode("").isEmpty());
        assertTrue(AuditAction.fromCode(null).isEmpty());
    }
}
