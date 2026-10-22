package com.codemastery.modules.course.api.dto;

import com.codemastery.common.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


public record CourseSearchRequest(Integer page, Integer size) {

    public static CourseSearchRequest defaults() {
        return new CourseSearchRequest(PageResponse.DEFAULT_PAGE, PageResponse.DEFAULT_SIZE);
    }

    public static CourseSearchRequest of(Integer page, Integer size) {
        return new CourseSearchRequest(page, size);
    }

    public int effectivePage() {
        return page != null ? page : PageResponse.DEFAULT_PAGE;
    }

    public int effectiveSize() {
        return size != null ? size : PageResponse.DEFAULT_SIZE;
    }


    public Pageable toValidatedPageable() {
        int resolvedPage = effectivePage();
        int resolvedSize = effectiveSize();
        PageResponse.validateRequest(resolvedPage, resolvedSize);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
