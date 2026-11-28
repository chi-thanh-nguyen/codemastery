package com.codemastery.modules.admin;

import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.modules.admin.api.dto.AdminRequests.AdminCourseSearch;
import com.codemastery.modules.admin.api.dto.AdminRequests.CoursePublicationWrite;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** IN20 request-shape tests for the CT12.rest course operations (ADR012 section 10.3). */
class AdminCourseRequestsTest {

    @Test
    void acceptsTheExactHiddenBodyWithJsonWhitespace() {
        assertEquals("hidden", CoursePublicationWrite.parse("{\"publicationStatus\":\"hidden\"}")
                .requireHidden().publicationStatus());
        assertEquals("hidden", CoursePublicationWrite.parse(" {\n \"publicationStatus\" : \"hidden\" }\n")
                .requireHidden().publicationStatus());
    }

    @Test
    void publishedAndEveryOtherValueIsRejected() {
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"published\"}").requireHidden());
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"\"}").requireHidden());
        assertThrows(BusinessRuleException.class,
                () -> CoursePublicationWrite.parse("{\"publicationStatus\":\"Hidden\"}").requireHidden());
    }

    @Test
    void anyOtherBodyShapeIsRejected() {
        for (String body : new String[] {
                null, "", "{}", "[]", "{\"publicationStatus\":1}", "{\"publicationStatus\":null}",
                "{\"publicationStatus\":\"hidden\",\"extra\":true}",
                "{\"publicationStatus\":\"hidden\",\"publicationStatus\":\"hidden\"}",
                "{\"publicationStatus\":\"hid\\u0064en\"}", "{\"publicationStatus\":\"hidden\"", "not json",
                "{'publicationStatus':'hidden'}", "{\"publicationStatus\":\"hidden\"} trailing"}) {
            assertThrows(BusinessRuleException.class, () -> CoursePublicationWrite.parse(body), String.valueOf(body));
        }
    }

    @Test
    void searchAcceptsKnownSingleValuedParametersAndNoneAtAll() {
        AdminCourseSearch none = AdminCourseSearch.parse(new LinkedMultiValueMap<>());
        assertNull(none.query());
        assertNull(none.publicationStatus());
        assertNull(none.page());
        assertNull(none.size());

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "Py");
        params.add("publicationStatus", "hidden");
        params.add("page", "2");
        params.add("size", "50");
        AdminCourseSearch search = AdminCourseSearch.parse(params);
        assertEquals("Py", search.query());
        assertEquals("hidden", search.publicationStatus());
        assertEquals(2, search.page());
        assertEquals(50, search.size());
    }

    @Test
    void searchRejectsUnknownRepeatedBlankOrNonNumericParameters() {
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("unknown", "x")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("page", "-1")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("page", "abc")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("size", "99999999999")));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(one("publicationStatus", " ")));
        MultiValueMap<String, String> repeated = new LinkedMultiValueMap<>();
        repeated.put("page", List.of("1", "2"));
        assertThrows(BusinessRuleException.class, () -> AdminCourseSearch.parse(repeated));
    }

    private static MultiValueMap<String, String> one(String name, String value) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(name, value);
        return params;
    }
}
