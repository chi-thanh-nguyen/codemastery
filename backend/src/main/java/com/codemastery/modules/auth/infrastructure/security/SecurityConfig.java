package com.codemastery.modules.auth.infrastructure.security;

import com.codemastery.common.config.AppConfig;
import com.codemastery.modules.auth.application.service.UserAccountService.CurrentIdentity;
import com.codemastery.modules.auth.application.service.UserAccountService.IdentityRole;
import com.codemastery.modules.auth.domain.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String MATERIAL_ROOT = "/api/instructor/courses/{courseId}/lessons/{lessonId}";
    private static final RequestMatcher MATERIAL_UPLOADS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, MATERIAL_ROOT + "/file-materials"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, MATERIAL_ROOT + "/file-materials/{materialId}/file"));
    // Share Security/MVC's segment-aware decoding, including before Security's filter chain.
    // Exact methods and paths keep unrelated form processing and default-deny unchanged.
    private static final RequestMatcher MATERIAL_OPERATIONS = new OrRequestMatcher(
            MATERIAL_UPLOADS,
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, MATERIAL_ROOT + "/materials"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, MATERIAL_ROOT + "/materials/{materialId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, MATERIAL_ROOT + "/materials/{materialId}/publication"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, MATERIAL_ROOT + "/file-materials/{materialId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, MATERIAL_ROOT + "/file-materials/{materialId}/publication"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                    "/api/courses/{courseId}/lessons/{lessonId}/materials/{materialId}/file"));

    private static final String ASSIGNMENT_ROOT = "/api/courses/{courseId}";
    private static final RequestMatcher SUBMISSION_UPLOAD = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.PUT, ASSIGNMENT_ROOT + "/assignments/{assignmentId}/submission");
    private static final RequestMatcher ASSIGNMENT_OPERATIONS = new OrRequestMatcher(
            SUBMISSION_UPLOAD,
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, ASSIGNMENT_ROOT + "/assignments"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/assignments"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/assignments/{assignmentId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/assignments/{assignmentId}/submission/file"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/owned-assignments"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}/publication"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}/submissions"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}/submissions/{submissionId}/file"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, ASSIGNMENT_ROOT + "/owned-assignments/{assignmentId}/submissions/{submissionId}/grade"));

    static final RequestMatcher PUBLIC_OPERATIONS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/register"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            // ADR014: cookie-authenticated session operations; no bearer is read on them.
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/password-reset-requests"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/password-resets"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses/{courseId}"));

    private static final RequestMatcher NOTIFICATION_OPERATIONS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/me/notifications"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/me/notifications/unread-count"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, "/api/me/notifications/{notificationId}/read"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, "/api/me/notifications/read-all"));

    private static final RequestMatcher ATTEMPT_OPERATIONS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses/{courseId}/quizzes/{quizId}/attempts"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/courses/{courseId}/quizzes/{quizId}/attempts"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/result"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, "/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/answers"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/submission"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/courses/{courseId}/quizzes/{quizId}/owned-attempts/{attemptId}/result"));

    /** Adapts only Auth's validated principal, keeping infrastructure out of application consumers. */
    @Bean
    Supplier<CurrentIdentity> currentIdentity() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)
                    || principal.userId() == null || principal.role() == null) {
                throw new AuthenticationCredentialsNotFoundException("Authentication failed.");
            }
            IdentityRole role = switch (principal.role()) {
                case LEARNER -> IdentityRole.LEARNER;
                case INSTRUCTOR -> IdentityRole.INSTRUCTOR;
                case ADMIN -> IdentityRole.ADMINISTRATOR;
            };
            return new CurrentIdentity(principal.userId(), role);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtTokenProvider jwtTokenProvider,
                                            UserRepository userRepository,
                                            RestSecurityErrorHandler restSecurityErrorHandler,
                                            AppConfig.CorsSettings corsSettings,
                                            RefreshCookieSecurity refreshCookies) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, userRepository, restSecurityErrorHandler);
        http
                // Spring's token CSRF is not selected: bearer endpoints carry no ambient
                // credential, and the three refresh-cookie operations are protected by
                // RefreshCookieSecurity.TrustedOriginFilter below (ADR014 §5).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsSettings)))
                .headers(headers -> headers.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new OrRequestMatcher(NOTIFICATION_OPERATIONS, ATTEMPT_OPERATIONS), (request, response) -> response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"))))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restSecurityErrorHandler)
                        .accessDeniedHandler(restSecurityErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_OPERATIONS).permitAll()
                        // CT11.read: all active account roles, with authentication before MVC validation.
                        .requestMatchers(NOTIFICATION_OPERATIONS).authenticated()
                        // CT17 P4: nondetailed health for the private operator probe only. Ingress does not
                        // proxy /actuator; probe groups and other Actuator endpoints retain default-deny.
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/me/courses", "/api/courses/{courseId}/membership").hasRole("LEARNER")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/{courseId}/membership").hasRole("LEARNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/membership").hasRole("LEARNER")
                        // CT22.progress/resume (ADR006 §§3, 4, 7.2): self Learner only.
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/progress", "/api/courses/{courseId}/resume",
                                "/api/me/course-progress", "/api/me/resume").hasRole("LEARNER")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/{courseId}/lessons/{lessonId}/completion",
                                "/api/courses/{courseId}/resume").hasRole("LEARNER")
                        // CT31.read (ADR012 sections 7.3, 12): Administrator only. The chain decides only
                        // authentication here: malformed query values are 400 (stage 2) before the role 403
                        // (stage 3), which AuditLogService decides before any lookup. Other methods stay denied.
                        .requestMatchers(HttpMethod.GET, "/api/admin/audit-logs").authenticated()
                        // CT21.authoring (ADR006 §5): self Instructor; the Learner Lesson read is self Learner.
                        // Material, file and learner-monitoring routes under the same prefix stay denied.
                        .requestMatchers(HttpMethod.GET, "/api/instructor/courses", "/api/instructor/courses/{courseId}",
                                "/api/instructor/courses/{courseId}/lessons/{lessonId}").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/instructor/courses",
                                "/api/instructor/courses/{courseId}/chapters",
                                "/api/instructor/courses/{courseId}/chapters/{chapterId}/lessons").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/instructor/courses/{courseId}",
                                "/api/instructor/courses/{courseId}/publication",
                                "/api/instructor/courses/{courseId}/chapters/order",
                                "/api/instructor/courses/{courseId}/chapters/{chapterId}",
                                "/api/instructor/courses/{courseId}/chapters/{chapterId}/lessons/order",
                                "/api/instructor/courses/{courseId}/lessons/{lessonId}",
                                "/api/instructor/courses/{courseId}/lessons/{lessonId}/publication").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/lessons/{lessonId}").hasRole("LEARNER")
                        // CT05.bindings (ADR005 §8.1): Instructor only, including the graph read; Course
                        // ownership is CT03's decision in the handler. Administrator has no Instructor inheritance.
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/skill-graph").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/skills").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/{courseId}/skills/{skillId}",
                                "/api/courses/{courseId}/skills/{skillId}/prerequisites/{prerequisiteSkillId}",
                                "/api/courses/{courseId}/lessons/{lessonId}/skills/{skillId}").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/skills/{skillId}",
                                "/api/courses/{courseId}/skills/{skillId}/prerequisites/{prerequisiteSkillId}",
                                "/api/courses/{courseId}/lessons/{lessonId}/skills/{skillId}").hasRole("INSTRUCTOR")
                        // CT23.questions/bindings (ADR007 §§4, 7): Instructor only; Course ownership is
                        // CT03's decision in the handler. Administrator has no Instructor inheritance.
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/questions",
                                "/api/courses/{courseId}/questions/{questionId}").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/questions").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/{courseId}/questions/{questionId}").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/questions/{questionId}").hasRole("INSTRUCTOR")
                        // CT23.composition/publication/bindings (ADR007 §§5–7): the quiz list is self Learner
                        // only; authoring and owner reads are Instructor only, with Course ownership decided by
                        // CT03 in the handler. Administrator has no Instructor inheritance.
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/quizzes").hasRole("LEARNER")
                        .requestMatchers(HttpMethod.GET, "/api/courses/{courseId}/owned-quizzes",
                                "/api/courses/{courseId}/quizzes/{quizId}").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/quizzes").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/{courseId}/quizzes/{quizId}",
                                "/api/courses/{courseId}/quizzes/{quizId}/publication").hasRole("INSTRUCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/quizzes/{quizId}").hasRole("INSTRUCTOR")
                        // CT04/ADR007: exact Attempt methods; role before MVC, Course admission in application.
                        .requestMatchers(HttpMethod.GET,
                                "/api/courses/{courseId}/quizzes/{quizId}/owned-attempts/{attemptId}/result").hasRole("INSTRUCTOR")
                        .requestMatchers(ATTEMPT_OPERATIONS).hasRole("LEARNER")
                        // CT07-PRACTICE-RECOMMENDATION (ADR013 §19.4): self Learner. The chain decides only
                        // authentication: malformed UUIDs are 400 (order 2) before the role 403 (order 3),
                        // which CT03 inside CT33 readLearnerState decides before any lookup.
                        .requestMatchers(HttpMethod.GET,
                                "/api/courses/{courseId}/lessons/{lessonId}/practice-recommendation").authenticated()
                        // CT09/CT10: request shape precedes CT03 admission in the application.
                        .requestMatchers(ASSIGNMENT_OPERATIONS).authenticated()
                        // CT09/CT21 material operations: exact paths only; stage 2 precedes application role checks.
                        .requestMatchers(MATERIAL_OPERATIONS).authenticated()
                        // ADR012 §12: authentication precedes media/path/query, then application CT03 admission.
                        .requestMatchers(HttpMethod.GET, "/api/lessons/{lessonId}/discussion").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/lessons/{lessonId}/discussion",
                                "/api/lessons/{lessonId}/reports", "/api/lessons/{lessonId}/discussion/{postId}/reports").authenticated()
                        // Other protected bindings retain default-deny.
                        .anyRequest().denyAll())
                .addFilterBefore(new RefreshCookieSecurity.TrustedOriginFilter(refreshCookies, restSecurityErrorHandler),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new MaterialUploadTransport(), JwtAuthenticationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsConfigurationSource(AppConfig.CorsSettings corsSettings) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // ADR014 §5: only the cookie operations admit credentials, for the same explicit
        // origins (a same-site loopback development frontend on another port). Registered
        // first: the first matching pattern applies. They take no Authorization header.
        CorsConfiguration cookieOperations = new CorsConfiguration();
        cookieOperations.setAllowedOrigins(corsSettings.allowedOrigins());
        cookieOperations.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        cookieOperations.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
        cookieOperations.setAllowCredentials(true);
        for (String path : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")) {
            source.registerCorsConfiguration(path, cookieOperations);
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsSettings.allowedOrigins());
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** The default form filter parses PUT bodies before Security. Material handlers own body admission. */
    @Bean
    org.springframework.boot.servlet.filter.OrderedFormContentFilter formContentFilter() {
        return new org.springframework.boot.servlet.filter.OrderedFormContentFilter() {
            @Override protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
                return MATERIAL_OPERATIONS.matches(request) || ASSIGNMENT_OPERATIONS.matches(request);
            }
        };
    }

    /** ADR010 §11: do not drain a rejected protected upload merely to reuse its connection. */
    private static final class MaterialUploadTransport extends org.springframework.web.filter.OncePerRequestFilter {
        @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            if (MATERIAL_UPLOADS.matches(request) || SUBMISSION_UPLOAD.matches(request)) response.setHeader(HttpHeaders.CONNECTION, "close");
            chain.doFilter(request, response);
        }
    }
}
