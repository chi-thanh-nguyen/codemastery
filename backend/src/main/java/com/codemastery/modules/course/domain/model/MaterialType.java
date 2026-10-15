package com.codemastery.modules.course.domain.model;

public enum MaterialType {
    TEXT("text"),
    FILE("file"),
    EXTERNAL_VIDEO("external_video");

    private final String type;

    MaterialType(String type) {this.type = type;
    }

    public String getType() {
        return type;
    }
}
