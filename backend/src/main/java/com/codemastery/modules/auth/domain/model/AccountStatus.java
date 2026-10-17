package com.codemastery.modules.auth.domain.model;

public enum AccountStatus {
    ACTIVE("active"),
    LOCKED("locked");

    private final String code;

    AccountStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
