package com.codemastery.modules.course.api.dto;

import com.codemastery.common.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record CourseSearchRequest(int page, int size, String query, String filter) {
    private static final Pattern COURSE_ID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    public static CourseSearchRequest parse(MultiValueMap<String, String> params, boolean membership) {
        Set<String> allowed = membership ? Set.of("page", "size", "status") : Set.of("page", "size", "q", "prerequisites");
        if (!allowed.containsAll(params.keySet()) || params.values().stream().anyMatch(values -> values.size() != 1)) throw invalid();
        int page = number(params.getFirst("page"), 0), size = number(params.getFirst("size"), 20);
        String query = params.getFirst("q") == null ? "" : params.getFirst("q").strip();
        String filter = params.getFirst(membership ? "status" : "prerequisites");
        if (filter == null) filter = membership ? "joined" : "any";
        if (page < 0 || size < 1 || size > 100 || query.length() > 100 || query.indexOf(0) >= 0
                || !(membership ? Set.of("joined", "active", "completed", "left", "all") : Set.of("any", "none", "described")).contains(filter)) throw invalid();
        return new CourseSearchRequest(page, size, query, filter);
    }
    public static void noQuery(MultiValueMap<String, String> params) { if (!params.isEmpty()) throw invalid(); }

    public static UUID parseCourseId(String value) {
        if (value == null || !COURSE_ID.matcher(value).matches()) throw invalid();
        return UUID.fromString(value);
    }

    private static int number(String input, int fallback) {
        if (input == null) return fallback;
        if (!input.matches("[0-9]+")) throw invalid();
        try { return Integer.parseInt(input); } catch (NumberFormatException exception) { throw invalid(); }
    }
    private static BusinessRuleException invalid() { return new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid query"); }
}
