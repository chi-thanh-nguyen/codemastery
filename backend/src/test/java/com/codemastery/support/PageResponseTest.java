package com.codemastery.support;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

/** SH01-delegated placement in the existing shared support test package. */
class PageResponseTest {

    @Test
    void defaultsAndEmptyResultsMatchContract() {
        var result = PageResponse.of(List.of(), null, null, 0);
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void rejectsInvalidRequestRatherThanClamping(int page, int size) {
        var failure = assertThrows(BusinessRuleException.class,
                () -> new PageResponse<>(List.of(), page, size, 0));
        assertEquals(400, failure.getStatus().value());
    }

    @ParameterizedTest
    @CsvSource({"0,1,0,0", "0,100,101,2", "2,20,41,3", "100,20,1,1"})
    void countsPagesWithoutChangingRequestedIndex(int page, int size, long total, long pages) {
        var result = new PageResponse<>(List.of(), page, size, total);
        assertEquals(pages, result.totalPages());
        assertEquals(page, result.page());
    }

    @Test
    void ceilingDoesNotOverflowAtMaximumLong() {
        assertEquals(Long.MAX_VALUE / 100 + 1,
                new PageResponse<>(List.of(), 0, 100, Long.MAX_VALUE).totalPages());
    }

    @Test
    void copiesContentAndSupportsSpringDataPage() {
        var original = new ArrayList<>(List.of("one"));
        var result = new PageResponse<>(original, 0, 20, 1);
        original.clear();
        assertEquals(List.of("one"), result.content());
        assertThrows(UnsupportedOperationException.class, () -> result.content().add("two"));
        assertEquals(result, PageResponse.from(new PageImpl<>(List.of("one"), PageRequest.of(0, 20), 1)));
    }

    @Test
    void rejectsInconsistentProducerMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 20, -1));
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 20, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of("x"), 1, 20, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of("x", "y"), 0, 1, 2));
    }
}
