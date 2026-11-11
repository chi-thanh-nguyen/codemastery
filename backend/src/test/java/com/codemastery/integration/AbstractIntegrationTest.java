package com.codemastery.integration;

import com.codemastery.CodeMasteryApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.UUID;

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

    /**
     * Built from infrastructure/postgres/Dockerfile (DP01 hardened PostgreSQL) rather than a plain
     * image reference, so Testcontainers always exercises the same su-exec-patched image used by
     * local Compose and the VM release, not an unpatched upstream tag. Built once per JVM under a
     * tag unique to that JVM, so parallel checkouts or JVMs never start each other's build, and
     * removed by Testcontainers when the session ends.
     */
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName
                    .parse(new ImageFromDockerfile("localhost/codemastery/postgres-it:" + UUID.randomUUID(), true)
                            .withFileFromPath(".", Path.of("..", "infrastructure", "postgres"))
                            .get())
                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(false);

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
