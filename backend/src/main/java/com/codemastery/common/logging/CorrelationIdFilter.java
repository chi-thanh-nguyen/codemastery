package com.codemastery.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SH01-local delegated decision: correlation is server-owned and internal.
 * All inbound correlation headers (including syntactically valid UUIDs) are ignored;
 * none is echoed or propagated over HTTP. Trusting/echoing client IDs would require a
 * wider public/security contract. A UUID request attribute carries this server's ID
 * across servlet ASYNC/ERROR redispatches; arbitrary executor threads are not covered.
 * MDC ownership is scoped to each dispatch and restored even after failure/nesting.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    private static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        correlate(request, response, filterChain);
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        correlate(request, response, filterChain);
    }

    private void correlate(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Object attribute = request.getAttribute(REQUEST_ATTRIBUTE);
        UUID id = attribute instanceof UUID serverId ? serverId : UUID.randomUUID();
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
