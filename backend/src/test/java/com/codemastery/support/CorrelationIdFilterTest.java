package com.codemastery.support;

import com.codemastery.common.logging.CorrelationIdFilter;
import com.codemastery.common.config.AppConfig;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanTestMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid-client-id", "09ce3f29-1dc1-4e9c-809d-a45b97cedb18", "injected\r\nlog-entry", ""})
    void ignoresEveryUntrustedHeaderAndDoesNotCreateAnExternalContract(String incoming) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-ID", incoming);
        request.addHeader("X-Request-ID", incoming);
        filter.doFilter(request, response, (req, res) -> {
            String id = MDC.get("correlationId");
            assertNotNull(UUID.fromString(id));
            assertNotEquals(incoming, id);
        });
        assertNull(MDC.get("correlationId"));
        assertNull(response.getHeader("X-Correlation-ID"));
        assertNull(response.getHeader("X-Request-ID"));
    }

    @Test
    void restoresPreviousContextAndOtherMdcKeysOnFailure() {
        MDC.put("correlationId", "outer-context");
        MDC.put("other", "preserved");
        assertThrows(ServletException.class, () -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> {
                    assertNotEquals("outer-context", MDC.get("correlationId"));
                    throw new ServletException("failure");
                }));
        assertEquals("outer-context", MDC.get("correlationId"));
        assertEquals("preserved", MDC.get("other"));
    }

    @Test
    void cleansNewContextOnFailure() {
        assertThrows(ServletException.class, () -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("failure"); }));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void retainsServerIdAcrossAsyncAndErrorDispatchOnAnotherThread() throws Exception {
        var request = new MockHttpServletRequest();
        var id = new AtomicReference<String>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> id.set(MDC.get("correlationId")));
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                for (var type : new DispatcherType[] {DispatcherType.ASYNC, DispatcherType.ERROR}) {
                    request.setDispatcherType(type);
                    if (type == DispatcherType.ERROR) {
                        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test");
                    }
                    filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                            assertEquals(id.get(), MDC.get("correlationId")));
                    assertNull(MDC.get("correlationId"));
                }
                return null;
            }).get();
        }
    }

    @Test
    void differentRequestsOnSameThreadGetDifferentIds() throws Exception {
        var first = new AtomicReference<String>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> first.set(MDC.get("correlationId")));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> assertNotEquals(first.get(), MDC.get("correlationId")));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void nestedErrorDispatchPreservesOuterRequestContext() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String original = MDC.get("correlationId");
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failure");
            filter.doFilter(request, response, (nestedRequest, nestedResponse) ->
                    assertEquals(original, MDC.get("correlationId")));
            assertEquals(original, MDC.get("correlationId"));
        });
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void bootRegistrationIncludesActualRequestAsyncAndErrorMappings() throws Exception {
        var mappings = new AtomicReference<Set<DispatcherType>>();
        var dynamic = (jakarta.servlet.FilterRegistration.Dynamic) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {jakarta.servlet.FilterRegistration.Dynamic.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addMappingForUrlPatterns")) {
                        @SuppressWarnings("unchecked")
                        var types = (Set<DispatcherType>) arguments[0];
                        mappings.set(Set.copyOf(types));
                    }
                    return method.getName().equals("setInitParameters") ? Set.of() : null;
                });
        var servletContext = new MockServletContext() {
            @Override
            public jakarta.servlet.FilterRegistration.Dynamic addFilter(String name, jakarta.servlet.Filter candidate) {
                assertInstanceOf(CorrelationIdFilter.class, candidate);
                return dynamic;
            }
        };
        new AppConfig().correlationIdFilterRegistration().onStartup(servletContext);
        assertEquals(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR), mappings.get());
    }
}
