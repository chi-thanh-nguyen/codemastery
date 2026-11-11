package com.codemastery.modules.assessment.domain.model;

public enum DifficultyLevel {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String code;

    DifficultyLevel(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
