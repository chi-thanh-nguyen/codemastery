package com.codemastery.modules.course.api.dto;

import com.codemastery.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


public record CourseSearchRequest(
        @Min(PageResponse.DEFAULT_PAGE) Integer page,
        @Min(1) @Max(PageResponse.MAX_SIZE) Integer size
) {

    /* Request using only the approved defaults (page=0, size=20). */
    public static CourseSearchRequest defaults() {
        return new CourseSearchRequest(null, null);
    }

    public static CourseSearchRequest of(Integer page, Integer size) {
        return new CourseSearchRequest(page, size);
    }

    /* Zero-based page; null resolves to the approved default 0. */
    public int effectivePage() {
        return page != null ? page : PageResponse.DEFAULT_PAGE;
    }

    /* Page size; {@code null} resolves to the approved default 20. */
    public int effectiveSize() {
        return size != null ? size : PageResponse.DEFAULT_SIZE;
    }

    /*
     * Validates paging bounds (throws BusinessRuleException, HTTP 400, when page < 0,
     * size < 1 or size > 100) and returns an unsorted Pageabl}.
     */
    public Pageable toValidatedPageable() {
        int resolvedPage = effectivePage();
        int resolvedSize = effectiveSize();
        PageResponse.validateRequest(resolvedPage, resolvedSize);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
