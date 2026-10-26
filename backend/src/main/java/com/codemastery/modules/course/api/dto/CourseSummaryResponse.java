package com.codemastery.modules.course.api.dto;

import java.util.List;
import java.util.UUID;

public record CourseSummaryResponse(UUID id, String title, String description,
                                    UUID instructorId, String instructorDisplayName) {
    public record CoursePage(List<CourseSummaryResponse> content, int page, int size,
                             long totalElements, int totalPages) {}
}
