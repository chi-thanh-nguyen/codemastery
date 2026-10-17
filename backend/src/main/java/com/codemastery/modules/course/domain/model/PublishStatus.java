package com.codemastery.modules.course.domain.model;

public enum PublishStatus {
    HIDDEN("hidden"),
    PUBLISHED("published");

    private final String status;

    PublishStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
