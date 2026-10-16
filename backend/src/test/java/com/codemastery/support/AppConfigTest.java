package com.codemastery.support;

import com.codemastery.common.config.AppConfig;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AppConfig.class);

    static String ephemeralKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return HexFormat.of().formatHex(key);
    }

    @Test
    void unusedOptionalSettingsDoNotRequireSecretsOrIntegrations() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.getBeanFactory().containsSingleton("jwtSettings"));
            assertFalse(context.getBeanFactory().containsSingleton("corsSettings"));
            assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.JwtSettings.class));
        });
    }

    @Test
    void consumesApprovedJwtAndNeverPrintsSecret() {
        String key = ephemeralKey();
        runner.withPropertyValues("codemastery.security.jwt.secret=" + key).run(context -> {
            var settings = context.getBean(AppConfig.JwtSettings.class);
            assertEquals(Duration.ofMinutes(15), settings.expiration());
            assertEquals("codemastery", settings.issuer());
            assertEquals("codemastery-api", settings.audience());
            assertArrayEquals(HexFormat.of().parseHex(key), settings.decodedSecret());
            assertFalse(settings.toString().contains(key));
        });
    }

    @Test
    void validatesKeyLengthAlphabetAndFixedIssuerWithoutDisclosingValues() {
        String key = ephemeralKey();
        for (String invalidKey : List.of(key.substring(2), key + "00", "z".repeat(64))) {
            runner.withPropertyValues("codemastery.security.jwt.secret=" + invalidKey).run(context -> {
                Throwable failure = assertThrows(RuntimeException.class,
                        () -> context.getBean(AppConfig.JwtSettings.class));
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(String.valueOf(cause.getMessage()).contains(invalidKey));
                }
            });
        }
        runner.withPropertyValues("codemastery.security.jwt.secret=" + key,
                "codemastery.security.jwt.issuer=another-issuer").run(context ->
                assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.JwtSettings.class)));
        runner.withPropertyValues("codemastery.security.jwt.secret=" + key.toUpperCase(java.util.Locale.ROOT))
                .run(context -> assertArrayEquals(HexFormat.of().parseHex(key),
                        context.getBean(AppConfig.JwtSettings.class).decodedSecret()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<SET_ME_LONG_RANDOM_SECRET>", "not-hex", "${JWT_SECRET}", "placeholder"})
    void rejectsConsumedMissingPlaceholderOrInvalidKey(String value) {
        runner.withPropertyValues("codemastery.security.jwt.secret=" + value).run(context ->
                assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.JwtSettings.class)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1H", "PT0S", "PT14M", "PT16M", "nonsense", ""})
    void rejectsOtherJwtLifetimesWithoutChangingPolicy(String value) {
        runner.withPropertyValues("codemastery.security.jwt.secret=" + ephemeralKey(),
                "codemastery.security.jwt.expiration=" + value).run(context ->
                assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.JwtSettings.class)));
    }

    @Test
    void eagerConsumerFailsDuringInitialization() {
        runner.withUserConfiguration(JwtConsumer.class).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    static class JwtConsumer {
        @Bean
        Object consumesJwt(AppConfig.JwtSettings settings) {
            return new Object();
        }
    }

    @Test
    void loadsExistingYamlAndUsesApprovedDefaultWhileLeavingStorageUnused() throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addLast(sources.getFirst()))
                .withPropertyValues("JWT_SECRET=" + ephemeralKey()).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(Duration.ofMinutes(15), context.getBean(AppConfig.JwtSettings.class).expiration());
                    assertEquals(List.of("http://localhost:5173"),
                            context.getBean(AppConfig.CorsSettings.class).allowedOrigins());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "http://*.example.test", "https://example.test/path",
            "https://user@example.test", "https://example.test?query", "https://example.test#fragment",
            "http://localhost:0", "http://localhost:65536", "not-a-url", "http://localhost:5173,"})
    void rejectsMalformedConsumedCorsOrigins(String value) {
        runner.withPropertyValues("codemastery.security.cors.allowed-origins=" + value).run(context ->
                assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.CorsSettings.class)));
    }

    @Test
    void allowsExplicitOriginListAndSchedulerFalseWithoutEnforcingCors() {
        runner.withPropertyValues("codemastery.security.cors.allowed-origins=http://localhost:5173, https://app.example.test",
                "codemastery.scheduling.enabled=false").run(context -> {
                    assertEquals(List.of("http://localhost:5173", "https://app.example.test"),
                            context.getBean(AppConfig.CorsSettings.class).allowedOrigins());
                    assertFalse(context.getBean(AppConfig.SchedulingSettings.class).enabled());
                });
    }

    @Test
    void rejectsInvalidConsumedSchedulerFlag() {
        runner.withPropertyValues("codemastery.scheduling.enabled=not-a-boolean").run(context ->
                assertThrows(RuntimeException.class, () -> context.getBean(AppConfig.SchedulingSettings.class)));
    }

    @Test
    void datasourceValidationAcceptsTestcontainerStyleOverrides() {
        datasource("jdbc:postgresql://localhost:51321/test?loggerLevel=OFF", "test", "ephemeral")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost:0/demo", "jdbc:postgresql://localhost:65536/demo",
            "jdbc:postgresql://localhost/", "jdbc:postgresql://<SET_ME>:5432/demo", "jdbc:h2:mem:test",
            "jdbc:postgresql://${DB_HOST}/demo"})
    void rejectsInvalidActiveDatasource(String url) {
        datasource(url, "test", "ephemeral").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsMissingAndPlaceholderActiveDatabaseCredentials() {
        datasource("jdbc:postgresql://localhost:5432/test", "test", "<SET_ME_STRONG_PASSWORD>")
                .run(context -> assertNotNull(context.getStartupFailure()));
        datasource("jdbc:postgresql://localhost:5432/test", "", "")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void validatesRealBootDatasourceBindingWithoutOpeningAConnection() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/test",
                        "spring.datasource.username=test", "spring.datasource.password=runtime-only")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(javax.sql.DataSource.class));
                });
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/test",
                        "spring.datasource.username=test", "spring.datasource.password=<SET_ME_STRONG_PASSWORD>")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void datasourceValidationDoesNotInventPasswordCharacterPolicy() {
        datasource("jdbc:postgresql://localhost:5432/test", "test", "runtime<special>characters")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void rejectsInvalidEffectiveHikariOverride() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/test",
                        "spring.datasource.username=test", "spring.datasource.password=runtime-only",
                        "spring.datasource.hikari.password=<SET_ME_STRONG_PASSWORD>")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private ApplicationContextRunner datasource(String url, String username, String password) {
        return runner.withUserConfiguration(DatasourceConsumer.class).withPropertyValues(
                "spring.datasource.url=" + url, "spring.datasource.username=" + username,
                "spring.datasource.password=" + password);
    }

    @Configuration(proxyBeanMethods = false)
    static class DatasourceConsumer {
        @Bean
        DataSourceProperties dataSourceProperties(Environment environment) {
            var properties = new DataSourceProperties();
            properties.setUrl(environment.getProperty("spring.datasource.url"));
            properties.setUsername(environment.getProperty("spring.datasource.username"));
            properties.setPassword(environment.getProperty("spring.datasource.password"));
            return properties;
        }
    }
}
