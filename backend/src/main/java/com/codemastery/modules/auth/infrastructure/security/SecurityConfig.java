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
                                            AppConfig.CorsSettings corsSettings) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, userRepository, restSecurityErrorHandler);
        http
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
                        // Other protected bindings retain default-deny.
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsConfigurationSource(AppConfig.CorsSettings corsSettings) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsSettings.allowedOrigins());
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
