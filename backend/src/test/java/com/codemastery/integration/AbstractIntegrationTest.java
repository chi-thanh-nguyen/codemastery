package com.codemastery.integration;

import com.codemastery.CodeMasteryApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    /** First failure after which committed, FK-safe fixture cleanup could not be established in this JVM. */
    private static final AtomicReference<Throwable> ISOLATION_LOSS = new AtomicReference<>();

    /**
     * Fail closed for the shared database. A test whose committed cleanup was withheld or rolled back records
     * that failure here, so every later integration test in this JVM, in any class, fails instead of running
     * against a database whose isolation is no longer established. Nothing resets the database.
     */
    protected static void recordIsolationLoss(Throwable cause) {
        ISOLATION_LOSS.compareAndSet(null, cause);
    }

    /** Only for a qualification test that simulated a loss: clears exactly that recorded failure. */
    protected static boolean clearSimulatedIsolationLoss(Throwable simulated) {
        return simulated != null && ISOLATION_LOSS.compareAndSet(simulated, null);
    }

    protected static Throwable isolationLoss() {
        return ISOLATION_LOSS.get();
    }

    /** Attempt every teardown step, but never let an unproved cleanup validate later suites. */
    protected static void cleanupSharedPostgresql(Runnable... steps) {
        Throwable loss = isolationLoss();
        if (loss != null) throw new IllegalStateException("Unsafe shared PostgreSQL cleanup", loss);
        Throwable failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException | Error thrown) {
                if (failure == null) failure = thrown;
                else if (failure != thrown) failure.addSuppressed(thrown);
            }
        }
        if (failure != null) {
            recordIsolationLoss(failure);
            if (failure instanceof Error error) throw error;
            throw (RuntimeException) failure;
        }
    }

    @BeforeEach
    protected void requireSharedPostgresqlIsolation() {
        Throwable loss = ISOLATION_LOSS.get();
        if (loss != null) {
            throw new IllegalStateException("Shared PostgreSQL isolation was lost earlier in this JVM; "
                    + "this integration result is invalid", loss);
        }
    }
}
