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
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restSecurityErrorHandler)
                        .accessDeniedHandler(restSecurityErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_OPERATIONS).permitAll()
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
                        // Other protected bindings retain default-deny.
                        .anyRequest().denyAll())
                .addFilterBefore(new RefreshCookieSecurity.TrustedOriginFilter(refreshCookies, restSecurityErrorHandler),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
