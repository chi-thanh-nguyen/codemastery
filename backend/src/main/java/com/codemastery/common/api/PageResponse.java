package com.codemastery.common.api;

import com.codemastery.common.exception.BusinessRuleException;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;

/** CT18.minimum page envelope. Querying, filtering and sorting belong to consumers. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, long totalPages) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageResponse {
        validateRequest(page, size);
        content = List.copyOf(Objects.requireNonNull(content, "content"));
        if (totalElements < 0 || totalPages != countPages(totalElements, size)
                || content.size() > size || content.size() > totalElements
                || (!content.isEmpty() && (long) page * size >= totalElements)) {
            throw new IllegalArgumentException("Inconsistent page result metadata");
        }
    }

    public PageResponse(List<T> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements, countPages(totalElements, size));
    }

    /** Null request parameters use the approved defaults; explicit invalid values never clamp. */
    public static <T> PageResponse<T> of(List<T> content, Integer page, Integer size, long totalElements) {
        return new PageResponse<>(content, page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size, totalElements);
    }

    public static <T> PageResponse<T> from(Page<T> result) {
        Objects.requireNonNull(result, "result");
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    public static void validateRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessRuleException("Invalid page or size");
        }
    }

    private static long countPages(long totalElements, int size) {
        validateRequest(0, size);
        // Avoid overflow even at Long.MAX_VALUE. An empty result has zero pages.
        return totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }
}
