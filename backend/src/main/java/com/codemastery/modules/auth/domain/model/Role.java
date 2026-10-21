package com.codemastery.modules.auth.domain.model;

public enum Role {
    LEARNER("learner"),
    INSTRUCTOR("instructor"),
    ADMIN("admin");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
