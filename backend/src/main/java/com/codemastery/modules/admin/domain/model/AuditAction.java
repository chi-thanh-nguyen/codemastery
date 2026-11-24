package com.codemastery.modules.admin.domain.model;

import java.util.Optional;

/**
 * Closed CT31.actions catalogue. {@link #getCode()} is persisted in
 * {@code audit_logs.action}; {@link #getTargetType()} is persisted in
 * {@code audit_logs.target_type}. Codes are stored explicitly, never by ordinal or name.
 */
public enum AuditAction {
    USER_ROLE_SET_LEARNER("user_role_set_learner", "user"),
    USER_ROLE_SET_INSTRUCTOR("user_role_set_instructor", "user"),
    USER_ROLE_SET_ADMIN("user_role_set_admin", "user"),
    USER_LOCKED("user_locked", "user"),
    USER_UNLOCKED("user_unlocked", "user"),
    COURSE_HIDDEN("course_hidden", "course"),
    REPORT_POST_HIDDEN("report_post_hidden", "content_report"),
    REPORT_LESSON_HIDDEN("report_lesson_hidden", "content_report"),
    REPORT_DISMISSED("report_dismissed", "content_report");

    private final String code;
    private final String targetType;

    AuditAction(String code, String targetType) {
        this.code = code;
        this.targetType = targetType;
    }

    public String getCode() {
        return code;
    }

    public String getTargetType() {
        return targetType;
    }

    /** Exact match on the stored code; unknown or null returns empty. */
    public static Optional<AuditAction> fromCode(String code) {
        for (AuditAction action : values()) {
            if (action.code.equals(code)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
