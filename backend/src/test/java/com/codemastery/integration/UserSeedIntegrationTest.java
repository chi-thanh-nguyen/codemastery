package com.codemastery.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual seed with psql in isolated databases on the shared test container. */
class UserSeedIntegrationTest extends AbstractIntegrationTest {

    private static String seedSql;
    private static String instructorHash;
    private static String learnerHash;
    private String database;
    private String jdbcUrl;
    private String scriptPath;
    private UUID unrelatedUserId;

    @DynamicPropertySource
    static void configureEphemeralAuthSecret(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String hex = HexFormat.of().formatHex(secret);
        registry.add("codemastery.security.jwt.secret", () -> hex);
    }

    @BeforeAll
    static void prepareSeedInputs() throws Exception {
        seedSql = Files.readString(Path.of("..", "sample-data", "seed", "01-users.sql"));
        instructorHash = newHash();
        learnerHash = newHash();
    }

    private static String newHash() {
        // Runtime-only synthetic passwords: 36 ASCII characters satisfy CT01's bounds.
        return BCrypt.hashpw(UUID.randomUUID().toString(),
                BCrypt.gensalt("$2b", 14, new SecureRandom()));
    }

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        database = "sd01_" + UUID.randomUUID().toString().replace("-", "");
        jdbcUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        scriptPath = "/tmp/" + database + ".sql";
        try (Connection connection = controlConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        Flyway.configure().dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        unrelatedUserId = UUID.randomUUID();
        try (Connection connection = testConnection(); var statement = connection.prepareStatement(
                "INSERT INTO public.users VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, unrelatedUserId);
            statement.setString(2, unrelatedUserId + "@example.test");
            statement.setString(3, learnerHash);
            statement.setString(4, "Unrelated User");
            statement.setString(5, "learner");
            statement.setString(6, "locked");
            statement.executeUpdate();
        }
    }

    @AfterEach
    void removeOnlyThisTestDatabase() throws Exception {
        if (scriptPath != null) {
            assertEquals(0, POSTGRES.execInContainer("rm", "-f", "--", scriptPath).getExitCode());
        }
        if (database != null) {
            try (Connection connection = controlConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "instructor_password_hash,false", "learner_password_hash,false", "both,false",
            "instructor_password_hash,true", "learner_password_hash,true", "both,true"
    })
    void missingInputsFailWithoutChangingExistingUsers(String missing, boolean callerStopsOnError)
            throws Exception {
        Map<String, String> inputs = validInputs();
        if (missing.equals("both")) {
            inputs.clear();
        } else {
            inputs.remove(missing);
        }
        String before = usersFingerprint();

        ExecResult result = runSeed(inputs, callerStopsOnError);

        String rejected = missing.equals("both") ? "instructor_password_hash" : missing;
        assertEquals(3, result.getExitCode());
        assertTrue(result.getStderr().contains("Required psql variable " + rejected + " is missing."));
        assertFalse(result.getStderr().contains("extra argument"));
        assertEquals(before, usersFingerprint());
        assertEquals(1, countUsers());
        assertNoHashesInOutput(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validInputsLoadAndRepeatWithoutChangingUnrelatedUsers(boolean callerStopsOnError)
            throws Exception {
        String unrelatedBefore = unrelatedUserFingerprint();
        ExecResult first = runSeed(validInputs(), callerStopsOnError);
        assertEquals(0, first.getExitCode());
        assertTrue(first.getStderr().isBlank());
        assertNoHashesInOutput(first);
        assertEquals(3, countUsers());
        assertEquals(unrelatedBefore, unrelatedUserFingerprint());
        try (Connection connection = testConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT id, email, display_name, role, account_status FROM public.users
                        WHERE email IN ('instructor@example.test', 'learner@example.test') ORDER BY id
                        """)) {
            assertTrue(rows.next());
            assertEquals("1eacfb14-e494-596c-bb53-3d012e7caf72", rows.getString("id"));
            assertEquals("instructor@example.test", rows.getString("email"));
            assertEquals("Instructor Demo", rows.getString("display_name"));
            assertEquals("instructor", rows.getString("role"));
            assertEquals("active", rows.getString("account_status"));
            assertTrue(rows.next());
            assertEquals("6ae6a0b2-7ef7-5500-a2ac-db04127da8fa", rows.getString("id"));
            assertEquals("learner@example.test", rows.getString("email"));
            assertEquals("Learner Demo", rows.getString("display_name"));
            assertEquals("learner", rows.getString("role"));
            assertEquals("active", rows.getString("account_status"));
            assertFalse(rows.next());
        }

        String afterFirst = usersFingerprint();
        ExecResult repeated = runSeed(validInputs(), callerStopsOnError);
        assertEquals(0, repeated.getExitCode());
        assertTrue(repeated.getStderr().isBlank());
        assertEquals(afterFirst, usersFingerprint());
        assertNoHashesInOutput(repeated);
    }

    private Map<String, String> validInputs() {
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("instructor_password_hash", instructorHash);
        inputs.put("learner_password_hash", learnerHash);
        return inputs;
    }

    private ExecResult runSeed(Map<String, String> inputs, boolean callerStopsOnError) throws Exception {
        StringBuilder script = new StringBuilder();
        inputs.forEach((name, hash) -> script.append("\\set ").append(name).append(" '")
                .append(hash).append("'\n"));
        script.append(seedSql);
        POSTGRES.copyFileToContainer(Transferable.of(script.toString().getBytes(StandardCharsets.UTF_8)),
                scriptPath);
        return POSTGRES.execInContainer("psql", "-X", "--set",
                "ON_ERROR_STOP=" + (callerStopsOnError ? "on" : "off"),
                "--username", POSTGRES.getUsername(), "--dbname", database, "--file", scriptPath);
    }

    private void assertNoHashesInOutput(ExecResult result) {
        assertFalse(result.getStdout().contains(instructorHash) || result.getStdout().contains(learnerHash));
        assertFalse(result.getStderr().contains(instructorHash) || result.getStderr().contains(learnerHash));
    }

    private int countUsers() throws Exception {
        try (Connection connection = testConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM public.users")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String usersFingerprint() throws Exception {
        return fingerprint("SELECT row_to_json(u)::text FROM public.users u ORDER BY id");
    }

    private String unrelatedUserFingerprint() throws Exception {
        return fingerprint("SELECT row_to_json(u)::text FROM public.users u WHERE id = '"
                + unrelatedUserId + "'");
    }

    private String fingerprint(String query) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Connection connection = testConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                digest.update(rows.getString(1).getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Connection testConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Connection controlConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
