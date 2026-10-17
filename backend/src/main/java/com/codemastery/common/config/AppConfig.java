package com.codemastery.common.config;

import com.codemastery.common.logging.CorrelationIdFilter;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;

/**
 * CT38.core bindings, with consumption-scoped validation. DataSourceProperties is
 * validated when datasource infrastructure creates it; JWT/CORS/scheduler beans
 * remain lazy until a consumer injects them. No Auth, CORS enforcement, storage or
 * scheduling behavior is implemented here. VITE_API_BASE_URL belongs to the frontend.
 */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }

    @Bean
    @Lazy
    public JwtSettings jwtSettings(Environment environment) {
        String expiration = environment.getProperty("codemastery.security.jwt.expiration", "PT15M");
        Duration duration;
        try {
            duration = Duration.parse(expiration);
        } catch (RuntimeException exception) {
            throw invalid("JWT_EXPIRATION");
        }
        return new JwtSettings(environment.getProperty("codemastery.security.jwt.secret"), duration,
                environment.getProperty("codemastery.security.jwt.issuer", "codemastery"));
    }

    @Bean
    @Lazy
    public CorsSettings corsSettings(Environment environment) {
        String value = environment.getProperty("codemastery.security.cors.allowed-origins");
        requireValue(value, "CORS_ALLOWED_ORIGINS");
        return new CorsSettings(Arrays.asList(value.split(",", -1)));
    }

    @Bean
    @Lazy
    public SchedulingSettings schedulingSettings(Environment environment) {
        String value = environment.getProperty("codemastery.scheduling.enabled", "true");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw invalid("SCHEDULING_ENABLED");
        }
        return new SchedulingSettings(Boolean.parseBoolean(value));
    }

    @Bean
    public static BeanPostProcessor consumedDatasourceValidation() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof DataSourceProperties properties) {
                    validateDatasource(properties.getUrl(), properties.getUsername(), properties.getPassword());
                } else if (bean instanceof HikariDataSource pool) {
                    // Validate effective Hikari overrides too, not just intermediate Boot properties.
                    validateDatasource(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
                }
                return bean;
            }
        };
    }

    private static void validateDatasource(String url, String username, String password) {
        requireValue(username, "spring.datasource.username / DB_USER");
        requireValue(password, "spring.datasource.password / DB_PASSWORD");
        requireValue(url, "spring.datasource.url / DB_HOST / DB_PORT / DB_NAME");
        try {
            if (!url.startsWith("jdbc:postgresql://")) {
                throw invalid("spring.datasource.url");
            }
            URI uri = URI.create(url.substring("jdbc:".length()));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1
                    || uri.getPath() == null || uri.getPath().length() <= 1) {
                throw invalid("spring.datasource.url");
            }
        } catch (RuntimeException exception) {
            throw invalid("spring.datasource.url / DB_HOST / DB_PORT / DB_NAME");
        }
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank() || value.contains("${")
                || value.toUpperCase(Locale.ROOT).contains("<SET_ME")
                || value.equalsIgnoreCase("changeme") || value.equalsIgnoreCase("placeholder")) {
            throw invalid(name);
        }
    }

    private static IllegalStateException invalid(String name) {
        // Never include the configured value or original parsing exception in diagnostics.
        return new IllegalStateException("Missing, placeholder or invalid consumed configuration: " + name);
    }

    public record JwtSettings(String secret, Duration expiration, String issuer) {
        public JwtSettings {
            requireValue(secret, "JWT_SECRET");
            if (!secret.matches("[0-9a-fA-F]{64}")) {
                throw invalid("JWT_SECRET");
            }
            if (!Duration.ofMinutes(15).equals(expiration)) {
                throw invalid("JWT_EXPIRATION (must be PT15M)");
            }
            if (!"codemastery".equals(issuer)) {
                throw invalid("JWT issuer");
            }
        }

        public String audience() {
            return "codemastery-api";
        }

        public byte[] decodedSecret() {
            return HexFormat.of().parseHex(secret);
        }

        @Override
        public String toString() {
            return "JwtSettings[secret=<redacted>, expiration=" + expiration + ", issuer=" + issuer + "]";
        }
    }

    public record CorsSettings(List<String> allowedOrigins) {
        public CorsSettings {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                throw invalid("CORS_ALLOWED_ORIGINS");
            }
            allowedOrigins = allowedOrigins.stream().map(value -> {
                requireValue(value, "CORS_ALLOWED_ORIGINS");
                String origin = value.trim();
                try {
                    URI uri = URI.create(origin);
                    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                            || uri.getHost() == null || uri.getUserInfo() != null
                            || !uri.getRawPath().isEmpty() || uri.getQuery() != null
                            || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                            || uri.getPort() < -1 || origin.contains("*")) {
                        throw invalid("CORS_ALLOWED_ORIGINS");
                    }
                } catch (RuntimeException exception) {
                    throw invalid("CORS_ALLOWED_ORIGINS");
                }
                return origin;
            }).distinct().toList();
        }
    }

    public record SchedulingSettings(boolean enabled) { }
}
