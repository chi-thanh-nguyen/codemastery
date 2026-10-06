package com.codemastery.integration;

import com.codemastery.CodeMasteryApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Spring Boot and PostgreSQL foundation for concrete integration tests.
 *
 * <p>The container lives for the test JVM so cached Spring contexts retain a working datasource.
 * Testcontainers cleans it up when the JVM exits; it is not reused across Maven executions.
 * This abstract class contains no executable tests.
 */
@SpringBootTest(
        classes = CodeMasteryApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.11-bookworm").withReuse(false);

    static {
        // Start once before Spring initializes the datasource; do not stop after each test class.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
