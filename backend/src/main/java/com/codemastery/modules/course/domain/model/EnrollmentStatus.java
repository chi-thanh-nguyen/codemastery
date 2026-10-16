package com.codemastery.modules.course.domain.model;

public enum EnrollmentStatus {
    ACTIVE("active"),
    COMPLETED("completed"),
    LEFT("left");

    private final String status;

    EnrollmentStatus(String status) {this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
