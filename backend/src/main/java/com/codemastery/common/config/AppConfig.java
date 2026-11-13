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
import java.util.Objects;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * remain lazy until a consumer injects them; AU07 recovery activation never fails
 * startup and only reports whether recovery delivery is configured. No Auth, CORS enforcement, storage or
 * scheduling behavior is implemented here. VITE_API_BASE_URL belongs to the frontend.
 */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

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

    /**
     * AU07 recovery activation. Missing, placeholder or invalid recovery inputs make only
     * password-reset delivery inactive (feature-scoped fail-closed, ADR006 B2-O16); they
     * never stop unrelated application startup. Diagnostics name variables, never values.
     */
    @Bean
    public PasswordResetActivation passwordResetActivation(Environment environment) {
        PasswordResetActivation activation;
        try {
            String ttl = environment.getProperty("codemastery.security.password-reset.token-ttl");
            Duration duration;
            try {
                duration = Duration.parse(ttl);
            } catch (RuntimeException exception) {
                throw invalid("PASSWORD_RESET_TOKEN_TTL");
            }
            activation = PasswordResetActivation.active(new PasswordResetSettings(duration,
                    environment.getProperty("codemastery.security.password-reset.frontend-origin"),
                    environment.getProperty("codemastery.security.password-reset.email-from"),
                    environment.getProperty("codemastery.security.password-reset.smtp-host"),
                    environment.getProperty("codemastery.security.password-reset.smtp-username"),
                    environment.getProperty("codemastery.security.password-reset.smtp-password")));
        } catch (IllegalStateException inactive) {
            activation = PasswordResetActivation.inactive(inactive.getMessage());
        } catch (RuntimeException unresolvable) {
            // Never forward a resolver message: it could echo configured text.
            activation = PasswordResetActivation.inactive("Unresolvable password-recovery configuration");
        }
        if (!activation.isActive()) {
            LOGGER.warn("Password recovery delivery is inactive; reset requests are acknowledged "
                    + "but issue no token and send no email. Reason: {}", activation.inactiveReason());
        }
        return activation;
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

    public record PasswordResetSettings(Duration tokenTtl, String frontendOrigin, String emailFrom,
                                        String smtpHost, String smtpUsername, String smtpPassword) {
        public PasswordResetSettings {
            if (!Duration.ofMinutes(30).equals(tokenTtl)) {
                throw invalid("PASSWORD_RESET_TOKEN_TTL (must be PT30M)");
            }
            requireValue(frontendOrigin, "PASSWORD_RESET_FRONTEND_ORIGIN");
            try {
                URI uri = URI.create(frontendOrigin);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                        || !uri.getHost().equals(uri.getHost().toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null
                        || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1
                        || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || frontendOrigin.contains("*") || !uri.toASCIIString().equals(frontendOrigin)) {
                    throw invalid("PASSWORD_RESET_FRONTEND_ORIGIN");
                }
            } catch (RuntimeException exception) {
                throw invalid("PASSWORD_RESET_FRONTEND_ORIGIN");
            }
            requireValue(emailFrom, "PASSWORD_RESET_EMAIL_FROM");
            try {
                InternetAddress sender = new InternetAddress(emailFrom, true);
                sender.validate();
                if (sender.getPersonal() != null || !emailFrom.equals(sender.getAddress())) {
                    throw invalid("PASSWORD_RESET_EMAIL_FROM");
                }
            } catch (AddressException exception) {
                throw invalid("PASSWORD_RESET_EMAIL_FROM");
            }
            requireValue(smtpHost, "SMTP_HOST");
            if (smtpHost.chars().anyMatch(Character::isWhitespace)) {
                throw invalid("SMTP_HOST");
            }
            requireValue(smtpUsername, "SMTP_USERNAME");
            requireValue(smtpPassword, "SMTP_PASSWORD");
        }

        @Override
        public String toString() {
            return "PasswordResetSettings[tokenTtl=" + tokenTtl + ", frontendOrigin=<redacted>, "
                    + "emailFrom=<redacted>, smtpHost=<redacted>, smtpUsername=<redacted>, smtpPassword=<redacted>]";
        }
    }

    /** Either complete validated recovery settings or the variable-named reason recovery is inactive. */
    public record PasswordResetActivation(PasswordResetSettings settings, String inactiveReason) {
        public PasswordResetActivation {
            if ((settings == null) == (inactiveReason == null)) {
                throw new IllegalArgumentException("Exactly one of settings or inactive reason is required");
            }
        }

        public static PasswordResetActivation active(PasswordResetSettings settings) {
            return new PasswordResetActivation(Objects.requireNonNull(settings), null);
        }

        public static PasswordResetActivation inactive(String reason) {
            return new PasswordResetActivation(null, Objects.requireNonNull(reason));
        }

        public boolean isActive() {
            return settings != null;
        }

        /** Settings for active recovery; never called on the inactive path. */
        public PasswordResetSettings requireSettings() {
            if (settings == null) {
                throw new IllegalStateException("Password recovery delivery is inactive");
            }
            return settings;
        }
    }
}
