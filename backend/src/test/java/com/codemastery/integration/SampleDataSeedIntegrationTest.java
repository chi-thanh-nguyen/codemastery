package com.codemastery.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.utility.MountableFile;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * SD01/SD02 seed SQL executed by real psql against disposable, Flyway-migrated PostgreSQL databases,
 * and read back through the real Auth and catalogue REST consumers. Never touches a non-test database.
 */
class SampleDataSeedIntegrationTest extends AbstractIntegrationTest {
    private static final Path SEED_DIR = Path.of("..", "sample-data", "seed");
    private static final Path SLIDES = Path.of("..", "sample-data", "materials", "sample-slides.pdf");
    private static final String SLIDES_SHA256 = "f9827139bf01b136430f727bc79773298414647df1a5ac49cb4e2e31710f381f";
    private static final String USERS = "01-users.sql", COURSES = "02-course-python-fundamentals.sql";
    private static final String KEY = HexFormat.of().formatHex(randomBytes(32));
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final String INSTRUCTOR_PASSWORD = password(), LEARNER_PASSWORD = password();
    private static final String INSTRUCTOR_HASH = BCRYPT.encode(INSTRUCTOR_PASSWORD), LEARNER_HASH = BCRYPT.encode(LEARNER_PASSWORD);
    private static final String OTHER_HASH = BCRYPT.encode(password());
    private static final List<String> SECRETS = List.of(INSTRUCTOR_PASSWORD, LEARNER_PASSWORD, INSTRUCTOR_HASH, LEARNER_HASH, OTHER_HASH);
    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static final List<String> CREATED = new ArrayList<>();
    private static String systemIdentifier;

    private static final UUID INSTRUCTOR = uuid5("user/instructor"), LEARNER = uuid5("user/learner");
    private static final UUID INTRO = uuid5("course/intro"), PRACTICE = uuid5("course/practice"), DRAFT = uuid5("course/draft");

    @Autowired WebApplicationContext context;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("JWT_SECRET", () -> KEY); }

    @BeforeAll static void copySeedsAndReadIndependentIdentity() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(SEED_DIR.resolve(USERS)), "/seed/" + USERS);
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(SEED_DIR.resolve(COURSES)), "/seed/" + COURSES);
        // Read the control file directly, independently of any SQL connection, as operators do.
        ExecResult control = POSTGRES.execInContainerWithUser("postgres", "pg_controldata");
        assertEquals(0, control.getExitCode(), control.getStderr());
        systemIdentifier = control.getStdout().lines().filter(line -> line.startsWith("Database system identifier:"))
                .map(line -> line.substring(line.indexOf(':') + 1).trim()).findFirst().orElseThrow();
        assertTrue(systemIdentifier.matches("[1-9][0-9]{0,19}"));
    }

    @AfterAll static void dropDisposableDatabases() throws SQLException {
        try (var admin = connect(POSTGRES.getDatabaseName()); var statement = admin.createStatement()) {
            for (String database : CREATED) statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            statement.execute("ALTER DATABASE " + POSTGRES.getDatabaseName() + " RESET codemastery.seed_target");
        }
    }

    @Test void loadsTheApprovedDatasetIntoAFreshMigratedDatabase() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);

        assertEquals(List.of(
                INSTRUCTOR + "|instructor@example.test|Instructor Demo|instructor|active|" + INSTRUCTOR_HASH,
                LEARNER + "|learner@example.test|Learner Demo|learner|active|" + LEARNER_HASH),
                rows(db, "SELECT id, email, display_name, role, account_status, password_hash FROM public.users ORDER BY email"));
        String course = "|" + INSTRUCTOR + "|%s|Demo course for the Interim catalogue|No prerequisites|%s|false|NULL";
        assertEquals(List.of(DRAFT + course.formatted("Instructor Draft", "hidden"),
                        INTRO + course.formatted("Introduction to Programming", "published"),
                        PRACTICE + course.formatted("Programming Practice", "published")),
                rows(db, "SELECT id, instructor_id, title, description, prerequisite_description, publication_status, "
                        + "adaptive_enabled, adaptive_settings FROM public.courses ORDER BY title"));
        assertEquals(List.of(uuid5("course/intro/chapter/1") + "|" + INTRO + "|Getting Started|1",
                        uuid5("course/practice/chapter/1") + "|" + PRACTICE + "|Getting Started|1"),
                rows(db, "SELECT ch.id, ch.course_id, ch.title, ch.position FROM public.chapters ch "
                        + "JOIN public.courses c ON c.id = ch.course_id ORDER BY c.title"));
        List<String> lessons = new ArrayList<>();
        for (String key : List.of("course/intro", "course/practice"))
            for (int position = 1; position <= 2; position++)
                lessons.add(uuid5(key + "/chapter/1/lesson/" + position) + "|" + uuid5(key + "/chapter/1") + "|"
                        + (position == 1 ? "Concepts" : "Practice") + "|NULL|" + position + "|published");
        assertEquals(lessons, rows(db, "SELECT l.id, l.chapter_id, l.title, l.content_markdown, l.position, l.publication_status "
                + "FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id JOIN public.courses c ON c.id = ch.course_id "
                + "ORDER BY c.title, l.position"));
        assertEquals(List.of(
                material("course/intro/chapter/1/lesson/1/material/1", "What a Program Is", "published"),
                material("course/intro/chapter/1/lesson/2/material/1", "Practice: Variables and Output", "published"),
                material("course/practice/chapter/1/lesson/1/material/1", "Breaking a Problem into Steps", "published"),
                material("course/practice/chapter/1/lesson/2/material/2", "Instructor Notes", "hidden"),
                material("course/practice/chapter/1/lesson/2/material/1", "Practice: Trace and Fix Short Programs", "published")),
                rows(db, "SELECT m.id, m.lesson_id, m.title, m.material_type, m.publication_status, "
                        + "m.text_content ~ '^[[:print:][:space:]]+$' AND m.text_content ~ '[^[:space:]]', "
                        + "m.object_key, m.original_filename, m.external_video_url FROM public.learning_materials m "
                        + "JOIN public.lessons l ON l.id = m.lesson_id JOIN public.chapters ch ON ch.id = l.chapter_id "
                        + "JOIN public.courses c ON c.id = ch.course_id ORDER BY c.title, l.position, m.title"));
        assertEquals(List.of("0"), rows(db, "SELECT count(*) FROM public.chapters WHERE course_id = '" + DRAFT + "'"));
        assertEquals(List.of(), rows(db, unrelatedRowsQuery()), "no enrolment, progress, skill, assessment or mastery data");
    }

    @Test void identicalRerunsChangeNothingThroughEitherInputMechanism() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        String before = snapshot(db);
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        var variables = List.of("-v", "seed_target_system_identifier=" + systemIdentifier,
                "-v", "instructor_password_hash=" + INSTRUCTOR_HASH, "-v", "learner_password_hash=" + LEARNER_HASH);
        assertSucceeded(psql(db, USERS, Map.of(), variables));
        assertSucceeded(psql(db, COURSES, Map.of(), variables));
        assertEquals(before, snapshot(db), "row values and row versions are unchanged");
    }

    @ParameterizedTest
    @ValueSource(strings = {"learner-email", "changed-hash", "course-id", "chapter-position", "lesson-id",
            "lesson-position", "material-content", "instructor-missing", "instructor-role"})
    void conflictsStopTheWholeTransactionWithoutPartialWrites(String conflict) throws Exception {
        String db = attested(freshDatabase());
        String file = COURSES;
        String message;
        Map<String, String> environment = seedEnvironment();
        switch (conflict) {
            case "learner-email" -> {
                // The Instructor sorts first; it must be rolled back with the conflicting Learner.
                execute(db, "INSERT INTO public.users VALUES ('" + UUID.randomUUID() + "','learner@example.test','x','Someone','learner','active')");
                file = USERS;
                message = "SD01 User seed conflicts with an existing ID or canonical email";
            }
            case "changed-hash" -> {
                assertLoaded(db, USERS);
                environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", OTHER_HASH);
                file = USERS;
                message = "SD01 User seed conflicts with an existing ID or canonical email";
            }
            case "course-id" -> {
                assertLoaded(db, USERS);
                execute(db, "INSERT INTO public.courses VALUES ('" + PRACTICE + "','" + INSTRUCTOR + "','Other title','Demo course for the Interim catalogue','No prerequisites','published',false,NULL)");
                message = "SD02 Course " + PRACTICE + " conflicts with an existing Course";
            }
            case "chapter-position" -> {
                assertLoaded(db, USERS);
                execute(db, "INSERT INTO public.courses VALUES ('" + INTRO + "','" + INSTRUCTOR + "','Introduction to Programming','Demo course for the Interim catalogue','No prerequisites','published',false,NULL)");
                execute(db, "INSERT INTO public.chapters VALUES ('" + UUID.randomUUID() + "','" + INTRO + "','Existing chapter',1)");
                message = "SD02 Chapter " + uuid5("course/intro/chapter/1") + " conflicts with an existing Chapter ID or Course position";
            }
            case "lesson-id", "lesson-position" -> {
                assertLoaded(db, USERS);
                execute(db, "INSERT INTO public.courses VALUES ('" + INTRO + "','" + INSTRUCTOR + "','Introduction to Programming','Demo course for the Interim catalogue','No prerequisites','published',false,NULL)");
                execute(db, "INSERT INTO public.chapters VALUES ('" + uuid5("course/intro/chapter/1") + "','" + INTRO + "','Getting Started',1)");
                UUID lesson = conflict.equals("lesson-id") ? uuid5("course/intro/chapter/1/lesson/2") : UUID.randomUUID();
                execute(db, "INSERT INTO public.lessons VALUES ('" + lesson + "','" + uuid5("course/intro/chapter/1") + "','Changed',NULL,2,'published')");
                message = "SD02 Lesson " + uuid5("course/intro/chapter/1/lesson/2") + " conflicts with an existing Lesson ID or Chapter position";
            }
            case "material-content" -> {
                assertLoaded(db, USERS);
                assertLoaded(db, COURSES);
                execute(db, "UPDATE public.learning_materials SET text_content = 'Edited' WHERE id = '" + uuid5("course/intro/chapter/1/lesson/1/material/1") + "'");
                message = "SD02 LearningMaterial " + uuid5("course/intro/chapter/1/lesson/1/material/1") + " conflicts with an existing LearningMaterial";
            }
            case "instructor-missing" -> message = "SD02 requires the SD01 synthetic Instructor; load 01-users.sql first";
            case "instructor-role" -> {
                execute(db, "INSERT INTO public.users VALUES ('" + INSTRUCTOR + "','instructor@example.test','x','Instructor Demo','learner','active')");
                message = "SD02 Instructor reference does not match the approved SD01 identity";
            }
            default -> throw new IllegalArgumentException(conflict);
        }
        String before = snapshot(db);
        assertRejected(psql(db, file, environment, List.of()), message);
        assertEquals(before, snapshot(db), "the failed transaction left no partial write");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-target", "empty-target", "non-numeric-target", "missing-instructor-hash",
            "missing-learner-hash", "plaintext-hash", "2a-hash", "cost-12-hash", "truncated-hash"})
    void missingOrInvalidInputsAreRejectedBeforeAnyWrite(String input) throws Exception {
        String db = attested(freshDatabase());
        Map<String, String> environment = seedEnvironment();
        String message = "SD01 requires valid BCrypt $2b$14$ password hashes";
        switch (input) {
            case "missing-target" -> { environment.remove("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER"); message = "Required input seed_target_system_identifier is missing."; }
            case "empty-target" -> { environment.put("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", ""); message = "SD01 target verification failed: malformed expected system identifier"; }
            case "non-numeric-target" -> { environment.put("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", systemIdentifier + "x"); message = "SD01 target verification failed: malformed expected system identifier"; }
            case "missing-instructor-hash" -> { environment.remove("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH"); message = "Required psql variable instructor_password_hash is missing."; }
            case "missing-learner-hash" -> { environment.remove("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH"); message = "Required psql variable learner_password_hash is missing."; }
            case "plaintext-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_PASSWORD);
            case "2a-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_HASH.replace("$2b$", "$2a$"));
            case "cost-12-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 12).encode(INSTRUCTOR_PASSWORD));
            case "truncated-hash" -> environment.put("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH.substring(0, 59));
            default -> throw new IllegalArgumentException(input);
        }
        String before = snapshot(db);
        assertRejected(psql(db, USERS, environment, List.of()), message);
        assertEquals(before, snapshot(db));
        if (input.endsWith("target")) {
            assertRejected(psql(db, COURSES, environment, List.of()), message.replace("SD01", "SD02"));
            assertEquals(before, snapshot(db));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unattested", "wrong-expected-identifier", "attested-for-another-cluster",
            "unbound-attestation", "role-scoped-attestation", "session-impersonation", "identity-unavailable"})
    void untrustedTargetsAreRejectedBeforeAnyLockOrWrite(String target) throws Exception {
        String db = freshDatabase();
        Map<String, String> environment = seedEnvironment();
        String message = "target verification failed: database is not attested as a disposable seed target";
        // A loader that locked before verifying would hit this timeout instead of the expected error.
        String options = "-c lock_timeout=5s";
        switch (target) {
            case "unattested" -> { }
            case "wrong-expected-identifier" -> {
                attested(db);
                environment.put("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", systemIdentifier.equals("1") ? "2" : "1");
                message = "target verification failed: connected cluster is not the expected target";
            }
            case "attested-for-another-cluster" -> attest(db, "disposable:1" + systemIdentifier.substring(1).replaceAll(".", "0"));
            case "unbound-attestation" -> attest(db, "disposable");
            case "role-scoped-attestation" -> execute(db, "ALTER ROLE " + POSTGRES.getUsername() + " IN DATABASE " + db
                    + " SET codemastery.seed_target = 'disposable:" + systemIdentifier + "'");
            case "session-impersonation" -> options += " -c codemastery.seed_target=disposable:" + systemIdentifier;
            case "identity-unavailable" -> {
                attested(db);
                execute(db, "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sd_seed_unprivileged') "
                        + "THEN CREATE ROLE sd_seed_unprivileged LOGIN; END IF; END $$");
                execute(db, "REVOKE EXECUTE ON FUNCTION pg_control_system() FROM PUBLIC");
                environment.put("PGUSER", "sd_seed_unprivileged");
                message = "target verification failed: cluster identity is unavailable";
            }
            default -> throw new IllegalArgumentException(target);
        }
        environment.put("PGOPTIONS", options);
        String before = snapshot(db);
        try (Connection holder = connect(db)) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE public.users, public.courses IN ACCESS EXCLUSIVE MODE");
            assertRejected(psql(db, USERS, environment, List.of()), "SD01 " + message);
            assertRejected(psql(db, COURSES, environment, List.of()), "SD02 " + message);
            holder.rollback();
        }
        assertEquals(before, snapshot(db));
    }

    @Test void concurrentLoadersStartedTogetherConvergeOnOneDataset() throws Exception {
        String db = attested(freshDatabase());
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var step : List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"))) {
                List<Future<ExecResult>> loaders = new ArrayList<>();
                try (Connection holder = connect(db)) {
                    holder.setAutoCommit(false);
                    holder.createStatement().execute("LOCK TABLE public." + step.getValue() + " IN SHARE ROW EXCLUSIVE MODE");
                    for (int i = 0; i < 2; i++) loaders.add(pool.submit(() -> psql(db, step.getKey(), seedEnvironment(), List.of())));
                    // Both loaders have verified the target and read nothing yet: they wait on the seed lock.
                    awaitWaiters(holder, step.getValue(), 2);
                    holder.rollback();
                }
                for (var loader : loaders) assertSucceeded(loader.get(2, TimeUnit.MINUTES));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of("2|3|2|4|5"), rows(db, "SELECT (SELECT count(*) FROM public.users), (SELECT count(*) FROM public.courses), "
                + "(SELECT count(*) FROM public.chapters), (SELECT count(*) FROM public.lessons), (SELECT count(*) FROM public.learning_materials)"));
    }

    @Test void seededRecordsAreServedByTheAuthAndCatalogueConsumers() throws Exception {
        String db = POSTGRES.getDatabaseName();
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();
        attested(db);
        try {
            assertLoaded(db, USERS);
            assertLoaded(db, COURSES);

            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(login("instructor@example.test", INSTRUCTOR_PASSWORD)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(INSTRUCTOR.toString()))
                    .andExpect(jsonPath("$.user.role").value("Instructor")).andExpect(jsonPath("$.user.displayName").value("Instructor Demo"));
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(login("learner@example.test", LEARNER_PASSWORD)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("Learner"));
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(login("learner@example.test", INSTRUCTOR_PASSWORD)))
                    .andExpect(status().isUnauthorized());

            mvc.perform(get("/api/courses").param("q", "Demo course for the Interim catalogue"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[*].id", contains(INTRO.toString(), PRACTICE.toString())))
                    .andExpect(jsonPath("$.content[*].instructorDisplayName", everyItem(is("Instructor Demo"))));
            mvc.perform(get("/api/courses/" + INTRO)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.prerequisiteDescription").value("No prerequisites"))
                    .andExpect(jsonPath("$.syllabus[*].title", contains("Getting Started")))
                    .andExpect(jsonPath("$.syllabus[0].lessons[*].title", contains("Concepts", "Practice")))
                    .andExpect(jsonPath("$.syllabus[0].lessons[*].position", contains(1, 2)));
            mvc.perform(get("/api/courses/" + DRAFT)).andExpect(status().isNotFound());
        } finally {
            execute(db, "DELETE FROM public.learning_materials WHERE lesson_id IN (SELECT l.id FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id IN ('" + INTRO + "','" + PRACTICE + "'))");
            execute(db, "DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id IN ('" + INTRO + "','" + PRACTICE + "'))");
            execute(db, "DELETE FROM public.chapters WHERE course_id IN ('" + INTRO + "','" + PRACTICE + "')");
            execute(db, "DELETE FROM public.courses WHERE id IN ('" + INTRO + "','" + PRACTICE + "','" + DRAFT + "')");
            execute(db, "DELETE FROM public.users WHERE id IN ('" + INSTRUCTOR + "','" + LEARNER + "')");
            execute(db, "ALTER DATABASE " + db + " RESET codemastery.seed_target");
        }
    }

    @Test void sampleSlidesAreTheRecordedValidPdf() throws Exception {
        byte[] pdf = Files.readAllBytes(SLIDES);
        assertEquals(SLIDES_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(new String(pdf, pdf.length - 32, 32, StandardCharsets.US_ASCII).contains("%%EOF"));
        String body = new String(pdf, StandardCharsets.ISO_8859_1);
        assertEquals(12, Pattern.compile("/Type\\s*/Page(?![s\\w])").matcher(body).results().count(), "page objects");
        assertTrue(body.contains("/Author (CodeMastery Project Team)"), "project attribution metadata");
    }

    private static String material(String key, String title, String status) {
        String lesson = key.substring(0, key.indexOf("/material/"));
        return uuid5(key) + "|" + uuid5(lesson) + "|" + title + "|text|" + status + "|true|NULL|NULL|NULL";
    }

    private static String unrelatedRowsQuery() {
        return "SELECT table_name FROM information_schema.tables t WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                + "AND table_name NOT IN ('users','courses','chapters','lessons','learning_materials','flyway_schema_history') "
                + "AND (xpath('/row/n/text()', query_to_xml(format('SELECT count(*) AS n FROM public.%I', table_name), false, true, '')))[1]::text <> '0'";
    }

    private static String login(String email, String password) { return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"; }

    private static Map<String, String> seedEnvironment() {
        return new HashMap<>(Map.of("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", systemIdentifier,
                "CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_HASH, "CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH));
    }

    private static ExecResult psql(String db, String file, Map<String, String> environment, List<String> variables) throws Exception {
        String user = environment.getOrDefault("PGUSER", POSTGRES.getUsername());
        List<String> command = new ArrayList<>(List.of("psql", "-X", "-q", "-U", user, "-d", db));
        command.addAll(variables);
        command.addAll(List.of("-f", "/seed/" + file));
        return POSTGRES.execInContainer(ExecConfig.builder().command(command.toArray(String[]::new)).envVars(environment).build());
    }

    private static void assertLoaded(String db, String file) throws Exception { assertSucceeded(psql(db, file, seedEnvironment(), List.of())); }

    private static void assertSucceeded(ExecResult result) {
        assertEquals(0, result.getExitCode(), result.getStderr());
        assertRedacted(result);
    }

    private static void assertRejected(ExecResult result, String message) {
        assertEquals(3, result.getExitCode(), "psql must stop on the script error");
        assertThat(result.getStderr(), containsString("ERROR:  " + message));
        assertRedacted(result);
    }

    private static void assertRedacted(ExecResult result) {
        String output = result.getStdout() + result.getStderr();
        for (String secret : SECRETS) assertFalse(output.contains(secret), "seed output must not disclose credentials or hashes");
        assertFalse(output.contains("$2b$14$" + INSTRUCTOR_HASH.substring(7, 29)), "no salt disclosure");
    }

    private static String freshDatabase() throws SQLException {
        String db = "sd_seed_" + DATABASES.incrementAndGet();
        try (var admin = connect(POSTGRES.getDatabaseName())) { admin.createStatement().execute("CREATE DATABASE " + db); }
        synchronized (CREATED) { CREATED.add(db); }
        Flyway.configure().dataSource(url(db), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        return db;
    }

    private static String attested(String db) throws SQLException { attest(db, "disposable:" + systemIdentifier); return db; }

    private static void attest(String db, String value) throws SQLException {
        execute(db, "ALTER DATABASE " + db + " SET codemastery.seed_target = '" + value + "'");
    }

    private static void awaitWaiters(Connection holder, String table, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        try (var query = holder.prepareStatement("SELECT count(*) FROM pg_locks WHERE relation = ?::regclass AND NOT granted")) {
            query.setString(1, "public." + table);
            while (System.nanoTime() < deadline) {
                try (var result = query.executeQuery()) { result.next(); if (result.getInt(1) == expected) return; }
                Thread.sleep(50);
            }
        }
        fail("loaders did not reach the seed lock");
    }

    private static String snapshot(String db) throws SQLException {
        return String.join("\n", rows(db, "SELECT relation, version, data FROM ("
                + "SELECT 'users' AS relation, xmin::text AS version, row_to_json(r)::text AS data FROM public.users r UNION ALL "
                + "SELECT 'courses', xmin::text, row_to_json(r)::text FROM public.courses r UNION ALL "
                + "SELECT 'chapters', xmin::text, row_to_json(r)::text FROM public.chapters r UNION ALL "
                + "SELECT 'lessons', xmin::text, row_to_json(r)::text FROM public.lessons r UNION ALL "
                + "SELECT 'learning_materials', xmin::text, row_to_json(r)::text FROM public.learning_materials r) s "
                + "ORDER BY relation, data"));
    }

    private static List<String> rows(String db, String sql) throws SQLException {
        try (var connection = connect(db); var result = connection.createStatement().executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    Object value = result.getObject(column);
                    values.add(value == null ? "NULL" : value.toString());
                }
                rows.add(String.join("|", values));
            }
            return rows;
        }
    }

    private static void execute(String db, String sql) throws SQLException {
        try (var connection = connect(db)) { connection.createStatement().execute(sql); }
    }

    private static Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(url(db), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String url(String db) { return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1"); }

    static UUID uuid5(String logicalKey) {
        try {
            var sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16).putLong(0x6ba7b8119dad11d1L).putLong(0x80b400c04fd430c8L).array());
            sha1.update(("https://codemastery.test/interim-v1/" + logicalKey).getBytes(StandardCharsets.UTF_8));
            byte[] hash = Arrays.copyOf(sha1.digest(), 16);
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            var bytes = ByteBuffer.wrap(hash);
            return new UUID(bytes.getLong(), bytes.getLong());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String password() { return HexFormat.of().formatHex(randomBytes(12)); }

    private static byte[] randomBytes(int length) { byte[] bytes = new byte[length]; new SecureRandom().nextBytes(bytes); return bytes; }
}
