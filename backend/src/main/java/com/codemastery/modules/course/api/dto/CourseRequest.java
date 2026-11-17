package com.codemastery.modules.course.api.dto;

public record CourseRequest(String title, String description, String prerequisiteDescription) {
    public record Publication(String publicationStatus) {}
}
