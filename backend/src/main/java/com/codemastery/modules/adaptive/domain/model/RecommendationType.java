package com.codemastery.modules.adaptive.domain.model;

public enum RecommendationType {
    CONTINUE("continue"),
    SKIP("skip"),
    REMEDIAL("remedial");

    private final String code;

    RecommendationType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}