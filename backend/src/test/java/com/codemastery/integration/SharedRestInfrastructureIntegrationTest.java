package com.codemastery.integration;

import com.codemastery.common.api.PageResponse;
import com.codemastery.common.exception.BusinessRuleException;
import com.codemastery.common.exception.GlobalExceptionHandler;
import com.codemastery.common.exception.ResourceNotFoundException;
import com.codemastery.common.logging.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * SH01-local test placement delegated by the current implementation instruction.
 * Real Spring MVC dispatch/validation/serialization, test-only routes, no feature API,
 * SecurityFilterChain or database. PostgreSQL/Auth/browser verification stays with QA owners.
 */
@SpringJUnitWebConfig(SharedRestInfrastructureIntegrationTest.MvcConfiguration.class)
class SharedRestInfrastructureIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).addFilters(new CorrelationIdFilter()).build();
    }

    @ParameterizedTest
    @CsvSource({"missing,404,Not Found", "business,400,Bad Request", "conflict,409,Conflict",
            "unexpected,500,Internal Server Error", "unauthorized,401,Unauthorized",
            "forbidden,403,Forbidden", "annotated,400,Bad Request"})
    void returnsOnlySafeProblemDetails(String scenario, int expectedStatus, String title) throws Exception {
        mvc.perform(get("/sh01-test/error").param("scenario", scenario).queryParam("secret", "query-sensitive"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.instance").value("/sh01-test/error"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("sensitive"))))
                .andExpect(content().string(not(containsString("postgres"))));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void validationExtensionIsOrderedAndNeverReflectsCustomMessagesOrInput() throws Exception {
        mvc.perform(post("/sh01-test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zeta\":\"\",\"alpha\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("alpha"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[1].field").value("alpha"))
                .andExpect(jsonPath("$.errors[1].code").value("Size"))
                .andExpect(jsonPath("$.errors[2].field").value("zeta"))
                .andExpect(jsonPath("$.errors[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."))
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @ParameterizedTest
    @CsvSource({"{,400", "null,400"})
    void malformedOrMissingBodyIsSafe(String body, int expected) throws Exception {
        mvc.perform(post("/sh01-test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The request is invalid."));
    }

    @Test
    void malformedUuidAndNumberDoNotLeakRejectedInput() throws Exception {
        mvc.perform(get("/sh01-test/uuid").param("id", "sensitive-not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("sensitive"))));
        mvc.perform(get("/sh01-test/page").param("page", "sensitive-not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void methodParameterValidationUsesStructuredErrors() throws Exception {
        mvc.perform(get("/sh01-test/validated").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"))
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void invalidReturnValueRemainsServerFailure() throws Exception {
        mvc.perform(get("/sh01-test/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void methodAndMediaTypeFailuresPreserveSemantics() throws Exception {
        mvc.perform(put("/sh01-test/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/sh01-test/body").contentType(MediaType.TEXT_PLAIN).content("sensitive"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("sensitive"))));
        mvc.perform(get("/sh01-test/page").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void absentRouteReturnsSafe404() throws Exception {
        mvc.perform(get("/no-such-sh01-test-route").param("secret", "sensitive"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void instancePreservesEncodedPathAndExcludesActualQueryString() throws Exception {
        mvc.perform(get(java.net.URI.create("/missing%20resource?password=sensitive")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.instance").value("/missing%20resource"))
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void pageHasExactlyApprovedFieldsAndDefaultValues() throws Exception {
        mvc.perform(get("/sh01-test/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/sh01-test/page").param("page", "999").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPageBoundsAre400(int page, int size) throws Exception {
        mvc.perform(get("/sh01-test/page").param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void mvcAdviceDoesNotTakeOverSecurityFilterHandling() {
        var failure = assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(get("/sh01-test/security")));
        assertInstanceOf(AccessDeniedException.class, failure.getCause());
        var authentication = assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(get("/sh01-test/authentication")));
        assertInstanceOf(BadCredentialsException.class, authentication.getCause());
        assertNull(MDC.get("correlationId"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    @RequestMapping("/sh01-test")
    static class TestController {
        @GetMapping("/error")
        Object error(@RequestParam String scenario) {
            throw switch (scenario) {
                case "missing" -> new ResourceNotFoundException("sensitive identity");
                case "business" -> new BusinessRuleException("sensitive business input");
                case "conflict" -> new BusinessRuleException(HttpStatus.CONFLICT, "sensitive email");
                case "unauthorized" -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sensitive credentials");
                case "forbidden" -> new ResponseStatusException(HttpStatus.FORBIDDEN, "sensitive role");
                case "annotated" -> new AnnotatedFailure();
                default -> new IllegalStateException("sensitive postgres://user:password@host/database");
            };
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Object body(@Valid @RequestBody Input input) {
            return input;
        }

        @GetMapping("/uuid")
        UUID uuid(@RequestParam UUID id) {
            return id;
        }

        @GetMapping("/validated")
        int validated(@RequestParam("count") @Min(value = 1, message = "sensitive validation") int number) {
            return number;
        }

        @GetMapping("/invalid-return")
        @Min(value = 1, message = "sensitive return value")
        int invalidReturn() {
            return 0;
        }

        @GetMapping("/page")
        PageResponse<String> page(@RequestParam(required = false) Integer page,
                @RequestParam(required = false) Integer size) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        @GetMapping("/security")
        void security() {
            throw new AccessDeniedException("sensitive security decision");
        }

        @GetMapping("/authentication")
        void authentication() {
            throw new BadCredentialsException("sensitive credentials");
        }
    }

    record Input(@NotBlank(message = "sensitive zeta") String zeta,
            @NotBlank(message = "sensitive alpha") @Size(min = 2, message = "sensitive length") String alpha) { }

    @ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "sensitive annotation")
    static class AnnotatedFailure extends RuntimeException { }
}
