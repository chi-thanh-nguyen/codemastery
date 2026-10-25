package com.codemastery.modules.auth.infrastructure.security;

import com.codemastery.modules.auth.domain.model.AccountStatus;
import com.codemastery.modules.auth.domain.model.User;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RestSecurityErrorHandler errors;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository,
                                   RestSecurityErrorHandler errors) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> bearer = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (bearer.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID userId = jwtTokenProvider.parseSubject(bearer.get());
                userRepository.findById(userId)
                        .filter(user -> user.accountStatus() == AccountStatus.ACTIVE)
                        .ifPresent(user -> SecurityContextHolder.getContext()
                                .setAuthentication(authentication(user, request)));
            } catch (AuthenticationException ignored) {
                SecurityContextHolder.clearContext();
            } catch (RuntimeException failure) {
                SecurityContextHolder.clearContext();
                errors.accountReadFailed(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        String trimmed = authorization.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()
                || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private static UsernamePasswordAuthenticationToken authentication(User user, HttpServletRequest request) {
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.role());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || SecurityConfig.PUBLIC_OPERATIONS.matches(request);
    }
}
