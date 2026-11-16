package com.codemastery.modules.auth.infrastructure.security;

import com.codemastery.common.config.AppConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Refresh-cookie transport and its CSRF protection (AUTH-SESSION-2026-11-16-r1, ADR014 §5).
 *
 * <p>Production (any HTTPS origin configured): {@code __Host-cm_refresh}; Secure, HttpOnly,
 * SameSite=Strict, Path=/, no Domain, so no sibling host can set or read it. Only when
 * every configured origin is an HTTP loopback origin (the approved local development
 * exception of CT01 §11.5) is the cookie {@code cm_refresh} without Secure, because
 * some browsers drop Secure cookies on plain-HTTP loopback. The mode is derived from
 * {@code CORS_ALLOWED_ORIGINS}; no new variable exists and an HTTPS deployment cannot
 * select the loopback mode.
 *
 * <p>The cookie is an ambient credential only for the three cookie operations, which
 * {@link TrustedOriginFilter} protects with Fetch Metadata and exact Origin checks.
 * Bearer-only endpoints never read it, and production ingress forwards Cookie only to
 * those three paths.
 */
@Component
public class RefreshCookieSecurity {

    static final String SECURE_NAME = "__Host-cm_refresh";
    static final String LOOPBACK_NAME = "cm_refresh";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshCookieSecurity.class);

    private final boolean secure;
    private final List<String> trustedOrigins;

    public RefreshCookieSecurity(AppConfig.CorsSettings cors) {
        this.trustedOrigins = List.copyOf(cors.allowedOrigins());
        this.secure = !trustedOrigins.stream().allMatch(RefreshCookieSecurity::httpLoopback);
        LOGGER.info("Refresh cookie mode: {}", secure ? "secure (__Host- prefix)" : "HTTP loopback development");
    }

    public String name() {
        return secure ? SECURE_NAME : LOOPBACK_NAME;
    }

    public boolean secure() {
        return secure;
    }

    /**
     * The single presented refresh token. Several cookies with the name are ambiguous and
     * return an unusable value, so refresh fails closed instead of choosing one.
     */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        List<String> values = Arrays.stream(cookies).filter(cookie -> name().equals(cookie.getName()))
                .map(Cookie::getValue).toList();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.size() == 1 && values.getFirst() != null ? values.getFirst() : "");
    }

    /** Set-Cookie for a new refresh token; Max-Age ends with the token's own expiry. */
    public ResponseCookie issue(String token, Instant expiresAt, Instant now) {
        long seconds = Math.max(0, Duration.between(now, expiresAt).getSeconds());
        return base(token).maxAge(seconds).build();
    }

    /** Set-Cookie that deletes the refresh cookie (same name, path and attributes). */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name(), value).path("/").secure(secure).httpOnly(true).sameSite("Strict");
    }

    private static boolean httpLoopback(String origin) {
        URI uri = URI.create(origin);
        return "http".equals(uri.getScheme()) && uri.getHost() != null
                && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
    }

    boolean trusted(String origin) {
        return origin != null && trustedOrigins.contains(origin);
    }

    /**
     * CSRF and login-CSRF protection for the cookie operations. Refresh and logout use the
     * ambient cookie, so a browser Origin from the configured list is mandatory. Login
     * authenticates with body credentials, so an absent Origin (a non-browser client) is
     * allowed, but a present one must be trusted. Browsers always send Origin on
     * cross-origin requests. Fetch Metadata, when sent, must show a same-origin or
     * same-site fetch rather than a navigation such as a form post. CORS and SameSite are
     * additional layers, not the decision.
     */
    public static class TrustedOriginFilter extends OncePerRequestFilter {

        private static final RequestMatcher LOGIN =
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login");
        static final RequestMatcher COOKIE_OPERATIONS = new OrRequestMatcher(LOGIN,
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"));
        private static final Set<String> FETCH_SITES = Set.of("same-origin", "same-site");
        private static final Set<String> FETCH_MODES = Set.of("cors", "same-origin");

        private final RefreshCookieSecurity cookies;
        private final RestSecurityErrorHandler errors;

        public TrustedOriginFilter(RefreshCookieSecurity cookies, RestSecurityErrorHandler errors) {
            this.cookies = cookies;
            this.errors = errors;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String site = request.getHeader("Sec-Fetch-Site");
            String mode = request.getHeader("Sec-Fetch-Mode");
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            boolean allowed = (site == null || FETCH_SITES.contains(site))
                    && (mode == null || FETCH_MODES.contains(mode))
                    && countOrigins(request) <= 1
                    && (origin == null ? LOGIN.matches(request) : cookies.trusted(origin));
            if (!allowed) {
                errors.handle(request, response, new AccessDeniedException("Untrusted cookie operation origin."));
                return;
            }
            chain.doFilter(request, response);
        }

        private static int countOrigins(HttpServletRequest request) {
            int count = 0;
            for (var values = request.getHeaders(HttpHeaders.ORIGIN); values.hasMoreElements(); values.nextElement()) {
                count++;
            }
            return count;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !COOKIE_OPERATIONS.matches(request);
        }
    }
}
