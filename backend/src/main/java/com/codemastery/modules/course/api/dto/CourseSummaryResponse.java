package com.codemastery.modules.course.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public record CourseSummaryResponse(UUID id, String title, String description,
                                    UUID instructorId, @JsonInclude(JsonInclude.Include.ALWAYS) String instructorDisplayName) {
    public record CoursePage(List<CourseSummaryResponse> content, int page, int size,
                             long totalElements, long totalPages) {
        public CoursePage { content = List.copyOf(content); }
    }
}
