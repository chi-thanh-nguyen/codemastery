package com.codemastery.modules.adaptive.domain.model;

public enum MasteryState {
    UNKNOWN("unknown"),
    LEARNING("learning"),
    MASTERED("mastered"),
    WEAK("weak");

    private final String code;

    MasteryState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}