package com.codemastery.integration;

import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.DecisionContext;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.LessonInput;
import com.codemastery.modules.adaptive.application.decision.AdaptiveDecisionEngine.PrerequisiteInput;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision;
import com.codemastery.modules.adaptive.domain.model.AdaptiveDecision.Recommendation;
import com.codemastery.modules.adaptive.domain.model.MasteryState;
import com.codemastery.modules.adaptive.domain.model.RecommendationType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * SD01/SD02/SD03 seed SQL (datasets interim-v1 and final-v1) executed by real psql against disposable,
 * Flyway-migrated PostgreSQL databases, and read back through the real Auth, catalogue, skill-graph, membership and
 * CT09 protected-material REST consumers, with an isolated MinIO for the L01 PDF provisioning. Never touches a
 * non-test database or object store.
 */
class SampleDataSeedIntegrationTest extends AbstractIntegrationTest {
    private static final Path SEED_DIR = Path.of("..", "sample-data", "seed");
    private static final Path SLIDES = Path.of("..", "sample-data", "materials", "sample-slides.pdf");
    private static final String SLIDES_SHA256 = "c4b05622b87a9b45e46563a87fba220e27c7da6a89340cd0c3f409bcadb9935d";
    private static final String USERS = "01-users.sql", COURSES = "02-course-python-fundamentals.sql";
    private static final String GRAPH = "03-skill-graph-and-lesson-mapping.sql", PROFILES = "05-learner-profiles.sql";
    private static final String KEY = HexFormat.of().formatHex(randomBytes(32));
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 14);
    private static final String INSTRUCTOR_PASSWORD = password(), LEARNER_PASSWORD = password();
    private static final String INSTRUCTOR_HASH = BCRYPT.encode(INSTRUCTOR_PASSWORD), LEARNER_HASH = BCRYPT.encode(LEARNER_PASSWORD);
    private static final String OTHER_HASH = BCRYPT.encode(password());
    /** ADR013 §3 final-v1 accounts added to the reused interim-v1 Instructor and Learner; one distinct password each. */
    private static final List<String> FINAL_ACCOUNTS = List.of("admin", "p1", "p2", "p3");
    private static final Map<String, String> FINAL_PASSWORDS = new LinkedHashMap<>(), FINAL_HASHES = new LinkedHashMap<>();
    /** Passwords and hashes, with each hash's salt and checksum, that no diagnostic may contain. */
    private static final List<String> SECRETS = new CopyOnWriteArrayList<>();
    /** Any BCrypt-shaped text, including truncated or re-prefixed hashes that are not registered. */
    private static final Pattern BCRYPT_TEXT = Pattern.compile("\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]+");
    /**
     * Starts psql through a shell that records its own PID and then execs psql in place, so the
     * recorded PID is the client this test started. Neither {@code /proc/<pid>/exe} (the translator
     * under Rosetta or QEMU, and unreadable across OS users without CAP_SYS_PTRACE) nor {@code comm}
     * (taken by any executable named psql) can tell which process that is.
     */
    private static final String RECORD_PID_THEN_EXEC = "printf '%s' \"$$\" > \"$1\"; shift; exec \"$@\"";
    /**
     * Prints {@code <launched> <disclosed>}; raw argv must never reach an assertion or a process-output
     * log. {@code launched} is 1 when the recorded PID runs with the expected database and seed file
     * arguments and no longer with the launcher's own (which name the PID file). {@code disclosed} is 1
     * when the argv of any process in the container holds a registered password, hash, salt or checksum.
     */
    private static final String INSPECT_LAUNCHED_PSQL = """
            set -eu
            launched=0
            disclosed=0
            if argv="$(tr '\\000' '\\n' < "/proc/$ARGV_PID/cmdline" 2>/dev/null)" \\
                    && printf '%s\\n' "$argv" | grep -Fx -- "$ARGV_DB" >/dev/null \\
                    && printf '%s\\n' "$argv" | grep -Fx -- "$ARGV_FILE" >/dev/null \\
                    && ! printf '%s\\n' "$argv" | grep -Fx -- "$ARGV_PID_FILE" >/dev/null; then
                launched=1
            fi
            for process in /proc/[0-9]*; do
                argv="$(tr '\\000' '\\n' < "$process/cmdline" 2>/dev/null)" || continue
                while IFS= read -r sensitive; do
                    [ -n "$sensitive" ] || continue
                    case "$argv" in *"$sensitive"*) disclosed=1 ;; esac
                done <<ARGV_INPUTS
            $ARGV_INPUTS
            ARGV_INPUTS
            done
            printf '%s %s\\n' "$launched" "$disclosed"
            """;

    static {
        for (String secret : List.of(INSTRUCTOR_PASSWORD, LEARNER_PASSWORD, INSTRUCTOR_HASH, LEARNER_HASH, OTHER_HASH)) secret(secret);
        for (String account : FINAL_ACCOUNTS) {
            String password = secret(password());
            FINAL_PASSWORDS.put(account, password);
            FINAL_HASHES.put(account, secret(BCRYPT.encode(password)));
        }
    }
    private static final Map<String, Long> CREATED = new LinkedHashMap<>();
    private static final Map<String, Long> CREATED_ROLES = new LinkedHashMap<>();
    private static final Set<String> PENDING_DATABASES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> PENDING_ROLES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final List<ConsumerFixture> ACTIVE_CONSUMERS = new CopyOnWriteArrayList<>();
    private static String systemIdentifier;

    private static final UUID INSTRUCTOR = uuid5("user/instructor"), LEARNER = uuid5("user/learner");
    private static final UUID INTRO = uuid5("course/intro"), PRACTICE = uuid5("course/practice"), DRAFT = uuid5("course/draft");
    /** ADR013 §4.2 final-v1 Course, its six Chapters, Lessons L01-L20 (Lk titled and mapped to Sk) and skill names. */
    private static final UUID PYTHON = final5("course/python");
    private static final List<String> CHAPTER_TITLES = List.of("Basics", "Decisions", "Loops", "Collections", "Functions",
            "Text, errors and files");
    private static final int[] CHAPTER_SIZES = {5, 2, 3, 4, 3, 3};
    private static final List<String> SKILLS = List.of("Program Output", "Variables and Assignment", "Numbers and Arithmetic",
            "Strings", "Input and Type Conversion", "Booleans and Comparisons", "Conditionals", "While Loops", "For Loops",
            "Loop Control", "Lists", "List Iteration Patterns", "Tuples and Sets", "Dictionaries", "Functions",
            "Parameters and Return Values", "Variable Scope", "String Methods and Formatting", "Exceptions", "File Input and Output");
    /** Edge skill>prerequisite, both 1-based skill numbers, in the ADR013 §4.2 table order. */
    private static final List<String> EDGES = List.of("2>1", "3>2", "4>2", "5>3", "5>4", "6>3", "7>6", "8>7", "9>7", "10>8",
            "10>9", "11>9", "12>9", "12>11", "13>11", "14>11", "15>7", "16>15", "17>16", "18>4", "18>9", "19>15", "20>9", "20>19");
    private static final List<String> PROFILE_KEYS = List.of("p1", "p2", "p3");
    private static final List<String> PROFILE_NAMES = List.of("Profile P1 Strong Foundation", "Profile P2 Loops Gap",
            "Profile P3 Complete Beginner");

    /** Title chosen at provisioning time for L01's PDF file material (an operator value, documented in the README). */
    private static final String SLIDES_TITLE = "Slides: Variable Declaration and Naming Conventions";

    /**
     * Isolated object store for the ADR013 §4.2 L01 PDF, provisioned only through the CT09 upload operation. Same
     * pinned server and client as Compose (asserted below) and an application identity limited to its own bucket;
     * credentials, network and bucket belong to this JVM.
     */
    private static final String MINIO_IMAGE = "ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z"
            + "@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40";
    private static final String MC_IMAGE = "ghcr.io/golithus/mc:RELEASE.2025-08-13T08-35-41Z"
            + "@sha256:dde55ccc0bc9e65dc582c8e652d652eea1438c6e60d774524f4da15d7b26a900";
    private static final String STORAGE_ROOT_USER = secret(password()), STORAGE_ROOT_SECRET = secret(password() + password());
    private static final Network STORAGE_NETWORK = Network.newNetwork();
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withNetwork(STORAGE_NETWORK).withNetworkAliases("storage")
            .withEnv("MINIO_ROOT_USER", STORAGE_ROOT_USER).withEnv("MINIO_ROOT_PASSWORD", STORAGE_ROOT_SECRET)
            .withEnv("MINIO_REGION_NAME", "us-east-1").withCommand("server", "/data")
            .withExposedPorts(9000).withReuse(false)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static { guarded("isolated MinIO startup", MINIO::start); }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> KEY);

    }

    private static String storageEndpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }

    private static S3Client storageAdministrator() {
        return S3Client.builder().endpointOverride(java.net.URI.create(storageEndpoint())).region(Region.US_EAST_1)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(15))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .retryStrategy(software.amazon.awssdk.retries.StandardRetryStrategy.builder().maxAttempts(1).build()))
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(STORAGE_ROOT_USER, STORAGE_ROOT_SECRET))).build();
    }

    @BeforeAll static void verifyPinnedStorageImages() throws Exception {
        String compose = Files.readString(Path.of("..", "infrastructure", "docker-compose.yml"));
        assertTrue(compose.contains(MINIO_IMAGE) && compose.contains(MC_IMAGE), "the test follows the pinned Compose storage images");
    }

    @BeforeAll static void copySeedsAndReadIndependentIdentity() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(SEED_DIR.resolve(USERS)), "/seed/" + USERS);
        for (String file : List.of(COURSES, GRAPH, PROFILES))
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(SEED_DIR.resolve(file)), "/seed/" + file);
        // Read the control file directly, independently of any SQL connection, as operators do.
        ExecResult control = POSTGRES.execInContainerWithUser("postgres", "pg_controldata");
        assertEquals(0, control.getExitCode(), "pg_controldata failed: " + redact(control.getStderr()));
        systemIdentifier = control.getStdout().lines().filter(line -> line.startsWith("Database system identifier:"))
                .map(line -> line.substring(line.indexOf(':') + 1).trim()).findFirst().orElseThrow();
        assertTrue(systemIdentifier.matches("[1-9][0-9]{0,19}"));
    }

    @AfterAll static void dropDisposableDatabases() {
        boolean failed = false;
        for (ConsumerFixture fixture : List.copyOf(ACTIVE_CONSUMERS)) {
            try { fixture.close(); } catch (IllegalStateException failure) { failed = true; }
        }
        try { dropOwnedDatabases(() -> connect(POSTGRES.getDatabaseName())); }
        catch (IllegalStateException failure) { failed = true; }
        try { dropOwnedRoles(); } catch (IllegalStateException failure) { failed = true; }
        if (failed) throw unsafe("outer seed teardown");
    }

    /** interim-v1 is unchanged by final-v1: without seed_dataset the files take only the ADR002 inputs and load only its rows. */
    @Test void loadsTheApprovedDatasetIntoAFreshMigratedDatabase() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);

        assertSafeEquals(List.of(
                INSTRUCTOR + "|instructor@example.test|Instructor Demo|instructor|active|" + INSTRUCTOR_HASH,
                LEARNER + "|learner@example.test|Learner Demo|learner|active|" + LEARNER_HASH),
                rows(db, "SELECT id, email, display_name, role, account_status, password_hash FROM public.users ORDER BY email"),
                "seeded Users");
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
        assertSucceeded(psqlWithVariables(db, USERS, false));
        assertSucceeded(psqlWithVariables(db, COURSES, false));
        assertSafeEquals(before, snapshot(db), "row values and row versions after identical reruns");
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
        assertRejected(psql(db, file, environment), message);
        assertSafeEquals(before, snapshot(db), "database after the failed transaction (no partial write)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-target", "empty-target", "non-numeric-target", "missing-instructor-hash",
            "missing-learner-hash", "plaintext-hash", "2a-hash", "cost-12-hash", "truncated-hash",
            "unknown-dataset", "empty-dataset", "hash-without-final-dataset", "final-missing-admin-hash",
            "final-missing-p3-hash", "final-2a-p2-hash", "final-truncated-admin-hash", "final-hash-reused-from-interim",
            "final-hash-shared-by-profiles"})
    void missingOrInvalidInputsAreRejectedBeforeAnyWrite(String input) throws Exception {
        String db = attested(freshDatabase());
        Map<String, String> environment = input.startsWith("final-") ? finalEnvironment() : seedEnvironment();
        String message = "SD01 requires valid BCrypt $2b$14$ password hashes";
        switch (input) {
            case "missing-target" -> { environment.remove("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER"); message = "Required input seed_target_system_identifier is missing."; }
            case "empty-target" -> { environment.put("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", ""); message = "SD01 target verification failed: malformed expected system identifier"; }
            case "non-numeric-target" -> { environment.put("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", systemIdentifier + "x"); message = "SD01 target verification failed: malformed expected system identifier"; }
            case "missing-instructor-hash" -> { environment.remove("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH"); message = "Required psql variable instructor_password_hash is missing."; }
            case "missing-learner-hash" -> { environment.remove("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH"); message = "Required psql variable learner_password_hash is missing."; }
            case "plaintext-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_PASSWORD);
            case "2a-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_HASH.replace("$2b$", "$2a$"));
            case "cost-12-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH",
                    secret(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 12).encode(INSTRUCTOR_PASSWORD)));
            case "truncated-hash" -> environment.put("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH.substring(0, 59));
            case "unknown-dataset" -> { environment.put("CODEMASTERY_SEED_DATASET", "final-v2"); message = "SD01 requires seed_dataset interim-v1 or final-v1."; }
            case "empty-dataset" -> { environment.put("CODEMASTERY_SEED_DATASET", ""); message = "SD01 requires seed_dataset interim-v1 or final-v1."; }
            // A forgotten selector must not silently load only the interim-v1 accounts.
            case "hash-without-final-dataset" -> { environment.put(hashVariable("p1"), FINAL_HASHES.get("p1")); message = "SD01 final-v1 password hashes require seed_dataset final-v1."; }
            case "final-missing-admin-hash" -> { environment.remove(hashVariable("admin")); message = "Required psql variable admin_password_hash is missing."; }
            case "final-missing-p3-hash" -> { environment.remove(hashVariable("p3")); message = "Required psql variable p3_password_hash is missing."; }
            case "final-2a-p2-hash" -> environment.put(hashVariable("p2"), FINAL_HASHES.get("p2").replace("$2b$", "$2a$"));
            case "final-truncated-admin-hash" -> environment.put(hashVariable("admin"), FINAL_HASHES.get("admin").substring(0, 59));
            case "final-hash-reused-from-interim" -> { environment.put(hashVariable("p1"), LEARNER_HASH); message = "SD01 final-v1 requires a distinct password hash per account"; }
            case "final-hash-shared-by-profiles" -> { environment.put(hashVariable("p3"), FINAL_HASHES.get("p2")); message = "SD01 final-v1 requires a distinct password hash per account"; }
            default -> throw new IllegalArgumentException(input);
        }
        String before = snapshot(db);
        assertRejected(psql(db, USERS, environment), message);
        assertSafeEquals(before, snapshot(db), "database after the rejected SD01 run");
        if (input.endsWith("target")) {
            for (var seed : List.of(Map.entry(COURSES, "SD02"), Map.entry(GRAPH, "SD03"), Map.entry(PROFILES, "SD05"))) {
                assertRejected(psql(db, seed.getKey(), environment), message.replace("SD01", seed.getValue()));
                assertSafeEquals(before, snapshot(db), "database after the rejected " + seed.getValue() + " run");
            }
        }
    }

    // final-v1 (ADR013 §§3-4): the SD01 accounts, course/python, its skill graph and the three profile Enrollments.

    /**
     * The interim-v1 dataset is a subset of final-v1: loading final-v1 over it reuses the Instructor and Learner rows
     * and every other interim-v1 row unchanged (same values and row versions) and adds only final-v1 rows.
     */
    @Test void upgradesTheInterimDatasetToFinalAndReloadsItUnchanged() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        List<String> interim = snapshot(db).lines().toList();
        for (String file : List.of(USERS, COURSES, GRAPH, PROFILES)) assertFinalLoaded(db, file);
        List<String> upgraded = snapshot(db).lines().toList();
        assertSafeEquals(List.of(), interim.stream().filter(row -> !upgraded.contains(row)).toList(),
                "interim-v1 rows whose values or row versions the final-v1 load changed");
        assertFinalDataset(db);

        String loaded = snapshot(db);
        for (String file : List.of(USERS, COURSES, GRAPH, PROFILES)) assertFinalLoaded(db, file);
        for (String file : List.of(USERS, COURSES, GRAPH, PROFILES)) assertSucceeded(psqlWithVariables(db, file, true));
        // interim-v1 mode stays valid on a final-v1 database: it only re-verifies its own rows.
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        assertSafeEquals(loaded, snapshot(db), "row values and row versions after identical reruns");
    }

    /** Exact final-v1 content of a database loaded with all four seed files, every ID re-derived from its logical key. */
    private static void assertFinalDataset(String db) throws Exception {
        assertSafeEquals(finalUsers(), rows(db, "SELECT id, email, display_name, role, account_status, password_hash "
                + "FROM public.users ORDER BY email"), "final-v1 Users: the reused interim-v1 accounts and the four added accounts");
        assertEquals(List.of("6"), rows(db, "SELECT count(DISTINCT password_hash) FROM public.users"), "one distinct hash per account");
        assertEquals(List.of(PYTHON + "|" + INSTRUCTOR + "|Python Fundamentals|published|true|No prerequisites"),
                rows(db, "SELECT id, instructor_id, title, publication_status, adaptive_enabled, prerequisite_description "
                        + "FROM public.courses WHERE id = '" + PYTHON + "'"));
        assertEquals(List.of("{\"weak\": {\"numerator\": 1, \"denominator\": 2}, \"mastered\": {\"numerator\": 4, \"denominator\": 5}, "
                        + "\"minimumQuestionsPerSkill\": 6}"),
                rows(db, "SELECT adaptive_settings::text FROM public.courses WHERE id = '" + PYTHON + "'"));
        assertEquals(List.of("4|3"), rows(db, "SELECT count(*), count(*) FILTER (WHERE NOT adaptive_enabled AND adaptive_settings IS NULL) "
                + "FROM public.courses"), "the three interim-v1 Courses stay non-adaptive beside course/python");

        List<String> chapters = new ArrayList<>(), lessons = new ArrayList<>(), materials = new ArrayList<>(), mappings = new ArrayList<>();
        int skill = 0;
        for (int chapter = 1; chapter <= CHAPTER_SIZES.length; chapter++) {
            String chapterKey = "course/python/chapter/" + chapter;
            chapters.add(final5(chapterKey) + "|" + CHAPTER_TITLES.get(chapter - 1) + "|" + chapter);
            for (int position = 1; position <= CHAPTER_SIZES[chapter - 1]; position++) {
                String lessonKey = chapterKey + "/lesson/" + position;
                lessons.add(final5(lessonKey) + "|" + final5(chapterKey) + "|" + SKILLS.get(skill) + "|NULL|" + position + "|published");
                materials.add(final5(lessonKey + "/material/1") + "|" + final5(lessonKey) + "|text|published|true|NULL|NULL|NULL");
                mappings.add(final5(lessonKey + "/skill/" + sk(skill + 1)) + "|" + final5(lessonKey) + "|" + final5("course/python/skill/" + sk(skill + 1)));
                skill++;
            }
        }
        assertEquals(20, skill);
        assertEquals(chapters, rows(db, "SELECT id, title, position FROM public.chapters WHERE course_id = '" + PYTHON + "' ORDER BY position"));
        String inCourse = " JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = '" + PYTHON + "' ORDER BY ch.position, l.position";
        assertEquals(lessons, rows(db, "SELECT l.id, l.chapter_id, l.title, l.content_markdown, l.position, l.publication_status "
                + "FROM public.lessons l" + inCourse));
        assertEquals(materials, rows(db, "SELECT m.id, m.lesson_id, m.material_type, m.publication_status, "
                + "m.text_content ~ '^[[:print:][:space:]]+$' AND m.text_content ~ '[^[:space:]]' AND m.title ~ '[^[:space:]]', "
                + "m.object_key, m.original_filename, m.external_video_url "
                + "FROM public.learning_materials m JOIN public.lessons l ON l.id = m.lesson_id" + inCourse));

        List<String> skills = new ArrayList<>();
        for (int k = 1; k <= 20; k++) skills.add(final5("course/python/skill/" + sk(k)) + "|" + SKILLS.get(k - 1) + "|NULL");
        assertEquals(skills.stream().sorted().toList(), rows(db, "SELECT id, name, description FROM public.skills "
                + "WHERE course_id = '" + PYTHON + "' ORDER BY id"));
        List<String> edges = new ArrayList<>();
        for (String edge : EDGES) {
            int s = Integer.parseInt(edge.split(">")[0]), p = Integer.parseInt(edge.split(">")[1]);
            assertTrue(p < s, "every prerequisite is an earlier skill, so the graph is acyclic: " + edge);
            edges.add(final5("course/python/skill/" + sk(s) + "/prerequisite/" + sk(p)) + "|" + final5("course/python/skill/" + sk(s))
                    + "|" + final5("course/python/skill/" + sk(p)));
        }
        assertEquals(edges.stream().sorted().toList(), rows(db, "SELECT id, skill_id, prerequisite_skill_id "
                + "FROM public.skill_prerequisites WHERE course_id = '" + PYTHON + "' ORDER BY id"));
        assertEquals(mappings.stream().sorted().toList(), rows(db, "SELECT ls.id, ls.lesson_id, ls.skill_id FROM public.lesson_skills ls "
                + "JOIN public.skills s ON s.id = ls.skill_id WHERE s.course_id = '" + PYTHON + "' ORDER BY ls.id"));
        assertEquals(List.of("20|24|20"), rows(db, "SELECT (SELECT count(*) FROM public.skills), (SELECT count(*) FROM public.skill_prerequisites), "
                + "(SELECT count(*) FROM public.lesson_skills)"), "no graph record outside course/python");

        List<String> enrollments = new ArrayList<>();
        for (String profile : PROFILE_KEYS)
            enrollments.add(final5("user/" + profile + "/enrollment/course/python") + "|" + final5("user/" + profile) + "|" + PYTHON
                    + "|active|NULL|NULL");
        assertEquals(enrollments.stream().sorted().toList(), rows(db, "SELECT id, user_id, course_id, status, resume_lesson_id, "
                + "last_visited_at FROM public.enrollments ORDER BY id"), "exactly one active Enrollment per profile, nothing else");
        assertEquals(List.of(), rows(db, finalUnrelatedRowsQuery()),
                "no mastery, attempt, answer, progress, recommendation, assessment or other row is seeded");
    }

    private static List<String> finalUsers() {
        List<String> users = new ArrayList<>(List.of(
                final5("user/admin") + "|admin@example.test|Administrator Demo|admin|active|" + FINAL_HASHES.get("admin"),
                INSTRUCTOR + "|instructor@example.test|Instructor Demo|instructor|active|" + INSTRUCTOR_HASH,
                LEARNER + "|learner@example.test|Learner Demo|learner|active|" + LEARNER_HASH));
        for (int i = 0; i < PROFILE_KEYS.size(); i++)
            users.add(final5("user/" + PROFILE_KEYS.get(i)) + "|" + PROFILE_KEYS.get(i) + "@example.test|" + PROFILE_NAMES.get(i)
                    + "|learner|active|" + FINAL_HASHES.get(PROFILE_KEYS.get(i)));
        return users;
    }

    @Test void aNonCanonicalPolicyIsStoredReducedAndItsCanonicalFormReloadsUnchanged() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        Map<String, String> environment = finalEnvironment();
        environment.putAll(Map.of("CODEMASTERY_SEED_ADAPTIVE_WEAK", "2/4", "CODEMASTERY_SEED_ADAPTIVE_MASTERED", "8/10"));
        assertSucceeded(psql(db, COURSES, environment));
        assertEquals(List.of("1|2|4|5|6"), rows(db, "SELECT adaptive_settings #>> '{weak,numerator}', adaptive_settings #>> '{weak,denominator}', "
                + "adaptive_settings #>> '{mastered,numerator}', adaptive_settings #>> '{mastered,denominator}', "
                + "adaptive_settings ->> 'minimumQuestionsPerSkill' FROM public.courses WHERE id = '" + PYTHON + "'"));
        String loaded = snapshot(db);
        assertFinalLoaded(db, COURSES);
        assertSafeEquals(loaded, snapshot(db), "the reduced policy equals the canonical one");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-weak", "missing-mastered", "missing-minimum", "zero-weak", "weak-above-one",
            "zero-denominator", "weak-equals-mastered", "weak-above-mastered", "decimal", "leading-zero", "zero-minimum",
            "minimum-overflow", "denominator-overflow", "changed-policy", "policy-without-final-dataset", "unknown-dataset"})
    void invalidOrChangedPoliciesAreRejectedWithoutAnyWrite(String input) throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        Map<String, String> environment = finalEnvironment();
        String weak = "CODEMASTERY_SEED_ADAPTIVE_WEAK", mastered = "CODEMASTERY_SEED_ADAPTIVE_MASTERED",
                minimum = "CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL";
        String message = "SD02 requires a valid adaptive policy (ADR005 6.2)";
        switch (input) {
            case "missing-weak" -> { environment.remove(weak); message = "Required input adaptive_weak is missing."; }
            case "missing-mastered" -> { environment.remove(mastered); message = "Required input adaptive_mastered is missing."; }
            case "missing-minimum" -> { environment.remove(minimum); message = "Required input adaptive_minimum_questions_per_skill is missing."; }
            case "zero-weak" -> environment.put(weak, "0/2");
            case "weak-above-one" -> environment.put(weak, "3/2");
            case "zero-denominator" -> environment.put(mastered, "4/0");
            case "weak-equals-mastered" -> environment.put(weak, "8/10");
            case "weak-above-mastered" -> environment.put(weak, "9/10");
            case "decimal" -> environment.put(mastered, "0.8/1");
            case "leading-zero" -> environment.put(minimum, "06");
            case "zero-minimum" -> environment.put(minimum, "0");
            case "minimum-overflow" -> environment.put(minimum, "2147483648");
            case "denominator-overflow" -> environment.put(weak, "1/2147483648");
            case "changed-policy" -> {
                assertFinalLoaded(db, COURSES);
                environment.put(minimum, "5");
                message = "SD02 Course " + PYTHON + " conflicts with an existing Course";
            }
            // The interim-v1 invocation is unchanged, so a policy without the selector is an operator mistake.
            case "policy-without-final-dataset" -> {
                environment = seedEnvironment();
                environment.put(minimum, "6");
                message = "SD02 adaptive policy inputs require seed_dataset final-v1.";
            }
            case "unknown-dataset" -> { environment.put("CODEMASTERY_SEED_DATASET", "Final-v1"); message = "SD02 requires seed_dataset interim-v1 or final-v1."; }
            default -> throw new IllegalArgumentException(input);
        }
        String before = snapshot(db);
        assertRejected(psql(db, COURSES, environment), message);
        assertSafeEquals(before, snapshot(db), "database after the rejected SD02 run");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin-email-taken", "profile-id-taken", "changed-profile-hash", "graph-without-course",
            "renamed-skill", "extra-skill", "extra-prerequisite", "extra-mapping", "cross-course-mapping", "hidden-lesson",
            "profiles-without-course", "profiles-without-learners", "profile-learner-role", "profile-locked", "left-enrollment"})
    void finalDatasetConflictsStopTheWholeTransactionWithoutPartialWrites(String conflict) throws Exception {
        String db = attested(freshDatabase());
        Map<String, String> environment = finalEnvironment();
        String file = GRAPH, message;
        UUID s01 = final5("course/python/skill/S01"), s02 = final5("course/python/skill/S02");
        UUID l01 = final5("course/python/chapter/1/lesson/1");
        String notApproved = "SD03 course/python already has skills, prerequisites or mappings outside the approved graph";
        String noProfiles = "SD05 requires the three final-v1 profile Learners; load 01-users.sql with seed_dataset final-v1 first";
        switch (conflict) {
            case "admin-email-taken" -> {
                // The interim-v1 Instructor and Learner sort before the conflict; they must be rolled back with it.
                execute(db, "INSERT INTO public.users VALUES ('" + UUID.randomUUID() + "','admin@example.test','x','Someone','admin','active')");
                file = USERS;
                message = "SD01 User seed conflicts with an existing ID or canonical email";
            }
            case "profile-id-taken" -> {
                execute(db, "INSERT INTO public.users VALUES ('" + final5("user/p3") + "','someone@example.test','x','Someone','learner','active')");
                file = USERS;
                message = "SD01 User seed conflicts with an existing ID or canonical email";
            }
            case "changed-profile-hash" -> {
                assertFinalLoaded(db, USERS);
                environment.put(hashVariable("p2"), OTHER_HASH);
                file = USERS;
                message = "SD01 User seed conflicts with an existing ID or canonical email";
            }
            case "graph-without-course" -> {
                assertFinalLoaded(db, USERS);
                message = "SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first";
            }
            case "renamed-skill" -> {
                loadFinal(db, USERS, COURSES, GRAPH);
                execute(db, "UPDATE public.skills SET name = 'Printing' WHERE id = '" + s01 + "'");
                message = "SD03 Skill " + s01 + " conflicts with an existing Skill ID or Course skill name";
            }
            case "extra-skill", "extra-prerequisite", "extra-mapping" -> {
                loadFinal(db, USERS, COURSES, GRAPH);
                execute(db, switch (conflict) {
                    case "extra-skill" -> "INSERT INTO public.skills VALUES ('" + UUID.randomUUID() + "','" + PYTHON + "','Recursion',NULL)";
                    case "extra-prerequisite" -> "INSERT INTO public.skill_prerequisites VALUES ('" + UUID.randomUUID() + "','" + PYTHON + "','" + s02
                            + "','" + final5("course/python/skill/S20") + "')";
                    default -> "INSERT INTO public.lesson_skills VALUES ('" + UUID.randomUUID() + "','" + l01 + "','" + s02 + "')";
                });
                message = notApproved;
            }
            case "cross-course-mapping" -> {
                // A course/python skill mapped to an interim-v1 Lesson of another Course would leak routing across Courses.
                loadFinal(db, USERS, COURSES, GRAPH);
                execute(db, "INSERT INTO public.lesson_skills VALUES ('" + UUID.randomUUID() + "','" + uuid5("course/intro/chapter/1/lesson/1")
                        + "','" + s01 + "')");
                message = notApproved;
            }
            case "hidden-lesson" -> {
                loadFinal(db, USERS, COURSES);
                execute(db, "UPDATE public.lessons SET publication_status = 'hidden' WHERE id = '" + l01 + "'");
                message = "SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first";
            }
            case "profiles-without-course" -> {
                assertFinalLoaded(db, USERS);
                file = PROFILES;
                message = "SD05 requires the SD02 course/python Course; load 02-course-python-fundamentals.sql first";
            }
            case "profiles-without-learners" -> {
                // The interim-v1 invocation of 01-users.sql creates no profile Learner.
                assertLoaded(db, USERS);
                assertFinalLoaded(db, COURSES);
                file = PROFILES;
                message = noProfiles;
            }
            case "profile-learner-role", "profile-locked" -> {
                loadFinal(db, USERS, COURSES);
                execute(db, "UPDATE public.users SET " + (conflict.equals("profile-locked") ? "account_status = 'locked'" : "role = 'instructor'")
                        + " WHERE id = '" + final5("user/p2") + "'");
                file = PROFILES;
                message = noProfiles;
            }
            case "left-enrollment" -> {
                loadFinal(db, USERS, COURSES, PROFILES);
                UUID enrollment = final5("user/p2/enrollment/course/python");
                execute(db, "UPDATE public.enrollments SET status = 'left' WHERE id = '" + enrollment + "'");
                file = PROFILES;
                message = "SD05 Enrollment " + enrollment + " conflicts with an existing Enrollment ID or membership";
            }
            default -> throw new IllegalArgumentException(conflict);
        }
        String before = snapshot(db);
        assertRejected(psql(db, file, environment), message);
        assertSafeEquals(before, snapshot(db), "database after the failed transaction (no partial write)");
    }

    /**
     * Rule-level check of the loaded SD03 data against the ADR013 §5 (CT36.F-scenarios) expectations, which the approved
     * contract derived by hand before any run: the existing AdaptiveDecisionEngine receives the graph, mapping and
     * canonical Lesson order read back from the database, with each scenario's mastery states as inputs (never as seeded
     * rows). It discriminates edge direction and mapping, not routing: the QA05 evaluation through real Quiz submissions
     * and its independently reviewed scenario files are not claimed here.
     */
    @Test void loadedGraphYieldsTheApprovedProfileAndGapRoutesAtRuleLevel() throws Exception {
        String db = attested(freshDatabase());
        loadFinal(db, USERS, COURSES, GRAPH, PROFILES);
        List<UUID> lessons = rows(db, "SELECT l.id FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id = '"
                + PYTHON + "' AND l.publication_status = 'published' ORDER BY ch.position, l.position").stream().map(UUID::fromString).toList();
        Map<UUID, UUID> skillOfLesson = new HashMap<>();
        for (String row : rows(db, "SELECT ls.lesson_id, ls.skill_id FROM public.lesson_skills ls JOIN public.skills s ON s.id = ls.skill_id "
                + "WHERE s.course_id = '" + PYTHON + "'"))
            assertNull(skillOfLesson.put(UUID.fromString(row.split("\\|")[0]), UUID.fromString(row.split("\\|")[1])), "one skill per Lesson");
        Map<UUID, String> names = new HashMap<>();
        for (String row : rows(db, "SELECT id, name FROM public.skills WHERE course_id = '" + PYTHON + "'"))
            names.put(UUID.fromString(row.split("\\|")[0]), row.split("\\|", 2)[1]);
        List<PrerequisiteInput> edges = rows(db, "SELECT skill_id, prerequisite_skill_id FROM public.skill_prerequisites WHERE course_id = '"
                + PYTHON + "'").stream().map(row -> new PrerequisiteInput(UUID.fromString(row.split("\\|")[0]),
                UUID.fromString(row.split("\\|")[1]))).toList();
        List<UUID> skillsInLessonOrder = lessons.stream().map(skillOfLesson::get).toList();
        assertEquals(20, lessons.size());
        assertEquals(SKILLS, skillsInLessonOrder.stream().map(names::get).toList(), "Lk maps exactly Sk");

        String next = "Continue with the next lesson in the course order.";
        // P1 strong foundation: all twenty placement answers correct.
        assertRoute(lessons, skillsInLessonOrder, names, edges, states(skillsInLessonOrder, 10, 0, 0), Set.of(),
                lessons.subList(0, 10), RecommendationType.CONTINUE, lessons.get(10), next);
        // P2 Loops gap: S01-S07 correct, S08-S10 incorrect; P2-R repeats it after L08 was completed manually.
        String p2 = remedialReason("Loop Control", "While Loops");
        assertRoute(lessons, skillsInLessonOrder, names, edges, states(skillsInLessonOrder, 7, 7, 3), Set.of(),
                lessons.subList(0, 7), RecommendationType.REMEDIAL, lessons.get(7), p2);
        assertRoute(lessons, skillsInLessonOrder, names, edges, states(skillsInLessonOrder, 7, 7, 3), Set.of(lessons.get(7)),
                lessons.subList(0, 7), RecommendationType.REMEDIAL, lessons.get(7), p2);
        // P3 complete beginner: all twenty answers incorrect.
        assertRoute(lessons, skillsInLessonOrder, names, edges, states(skillsInLessonOrder, 0, 0, 10), Set.of(),
                List.of(), RecommendationType.REMEDIAL, lessons.get(0), remedialReason("Variables and Assignment", "Program Output"));

        // G01-G24 and their discriminating G'01-G'24 variants: one per approved edge s -> p, in the §4.2 table order.
        for (String edge : EDGES) {
            int s = Integer.parseInt(edge.split(">")[0]) - 1, p = Integer.parseInt(edge.split(">")[1]) - 1;
            Map<UUID, MasteryState> gap = new HashMap<>();
            for (UUID skill : skillsInLessonOrder) gap.put(skill, MasteryState.MASTERED);
            gap.put(skillsInLessonOrder.get(s), MasteryState.WEAK);
            gap.put(skillsInLessonOrder.get(p), MasteryState.WEAK);
            List<UUID> skipped = new ArrayList<>(lessons);
            skipped.removeAll(List.of(lessons.get(s), lessons.get(p)));
            String reason = remedialReason(SKILLS.get(s), SKILLS.get(p));
            assertRoute(lessons, skillsInLessonOrder, names, edges, gap, Set.of(), skipped, RecommendationType.REMEDIAL, lessons.get(p), reason);
            assertRoute(lessons, skillsInLessonOrder, names, edges, gap, Set.of(lessons.get(p)), skipped, RecommendationType.REMEDIAL,
                    lessons.get(p), reason);
        }
    }

    /** Skills 1..mastered are Mastered, the next {@code weak} skills from index {@code weakFrom} Weak, every other skill Unknown. */
    private static Map<UUID, MasteryState> states(List<UUID> skills, int mastered, int weakFrom, int weak) {
        Map<UUID, MasteryState> states = new HashMap<>();
        for (int i = 0; i < skills.size(); i++)
            states.put(skills.get(i), i < mastered ? MasteryState.MASTERED : i >= weakFrom && i < weakFrom + weak ? MasteryState.WEAK : MasteryState.UNKNOWN);
        return states;
    }

    private static String remedialReason(String weakSkill, String prerequisite) {
        return "Review this lesson first: your skill \"" + weakSkill + "\" is Weak and its prerequisite \"" + prerequisite
                + "\" is not yet Mastered.";
    }

    private static void assertRoute(List<UUID> lessons, List<UUID> skills, Map<UUID, String> names, List<PrerequisiteInput> edges,
                                    Map<UUID, MasteryState> states, Set<UUID> completed, List<UUID> skipped,
                                    RecommendationType type, UUID target, String reason) {
        List<LessonInput> inputs = new ArrayList<>();
        for (int i = 0; i < lessons.size(); i++)
            inputs.add(new LessonInput(lessons.get(i), completed.contains(lessons.get(i)), Set.of(skills.get(i))));
        Map<UUID, MasteryState> before = Map.copyOf(states);
        AdaptiveDecision decision = new AdaptiveDecisionEngine().decide(
                new DecisionContext(PYTHON, final5("user/p1"), inputs, states, edges, names));
        assertEquals(before, states, "unassessed Unknown states stay unchanged");
        for (int i = 0; i < skipped.size(); i++) {
            UUID lesson = skipped.get(i);
            String name = names.get(skills.get(lessons.indexOf(lesson)));
            assertEquals(new Recommendation(RecommendationType.SKIP, lesson,
                    "Tested out: you have mastered every skill in this lesson (\"" + name + "\")."),
                    decision.recommendations().get(i), "ordered skip draft and reason");
        }
        assertEquals(skipped, decision.testOutLessonIds(), "skip (Test-out) Lessons");
        assertEquals(skipped.size() + 1, decision.recommendations().size(), "skips followed by exactly one guidance draft");
        assertEquals(new Recommendation(type, target, reason), decision.recommendations().getLast(), "current guidance");
    }

    /** Loads the given seed files in order with the final-v1 inputs. */
    private static void loadFinal(String db, String... files) throws Exception { for (String file : files) assertFinalLoaded(db, file); }

    private static String sk(int number) { return "S%02d".formatted(number); }

    static Stream<Arguments> untrustedTargetsUnderEachIsolationLevel() {
        return Stream.of("unattested", "wrong-expected-identifier", "attested-for-another-cluster", "unbound-attestation",
                        "role-scoped-attestation", "session-impersonation", "identity-unavailable")
                .flatMap(target -> Stream.of("read committed", "repeatable read").map(isolation -> Arguments.of(target, isolation)));
    }

    @ParameterizedTest(name = "{0} with inherited {1}")
    @MethodSource("untrustedTargetsUnderEachIsolationLevel")
    void untrustedTargetsAreRejectedBeforeAnyLockOrWrite(String target, String isolation) throws Exception {
        String db = inheritIsolation(freshDatabase(), isolation);
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
                String role = unprivilegedRole();
                execute(db, "REVOKE EXECUTE ON FUNCTION pg_control_system() FROM PUBLIC");
                environment.put("PGUSER", role);
                message = "target verification failed: cluster identity is unavailable";
            }
            default -> throw new IllegalArgumentException(target);
        }
        environment.put("PGOPTIONS", options);
        String before = snapshot(db);
        try (Connection holder = connect(db)) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE public.users, public.courses IN ACCESS EXCLUSIVE MODE");
            assertRejected(psql(db, USERS, environment), "SD01 " + message);
            assertRejected(psql(db, COURSES, environment), "SD02 " + message);
            assertRejected(psql(db, GRAPH, environment), "SD03 " + message);
            assertRejected(psql(db, PROFILES, environment), "SD05 " + message);
            // final-v1 adds a literal dataset comparison before the transaction; it must not reach a lock either.
            Map<String, String> finalInputs = finalEnvironment();
            finalInputs.putAll(environment);
            finalInputs.put("CODEMASTERY_SEED_DATASET", "final-v1");
            assertRejected(psql(db, USERS, finalInputs), "SD01 " + message);
            assertRejected(psql(db, COURSES, finalInputs), "SD02 " + message);
            holder.rollback();
        }
        assertSafeEquals(before, snapshot(db), "database after the rejected runs");
    }

    /**
     * Both loaders run the target guard, which takes the transaction's first snapshot, and then wait on the seed
     * lock. Under an inherited REPEATABLE READ or SERIALIZABLE default, the second loader would keep that stale
     * snapshot after the first commits; the seeds' explicit READ COMMITTED makes it see the committed rows.
     */
    static Stream<Arguments> isolationLevelsAndDatasets() {
        return Stream.of("read committed", "repeatable read", "serializable")
                .flatMap(isolation -> Stream.of("interim-v1", "final-v1").map(dataset -> Arguments.of(isolation, dataset)));
    }

    @ParameterizedTest(name = "inherited {0}, {1}")
    @MethodSource("isolationLevelsAndDatasets")
    void concurrentLoadersConvergeWhateverTheInheritedIsolationLevel(String isolation, String dataset) throws Exception {
        String db = inheritIsolation(attested(freshDatabase()), isolation);
        boolean finalDataset = dataset.equals("final-v1");
        Map<String, String> environment = finalDataset ? finalEnvironment() : seedEnvironment();
        var steps = finalDataset
                ? List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"), Map.entry(GRAPH, "skills"), Map.entry(PROFILES, "enrollments"))
                : List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var step : steps) {
                List<Future<Run>> loaders = new ArrayList<>();
                try (Connection holder = connect(db)) {
                    holder.setAutoCommit(false);
                    holder.createStatement().execute("LOCK TABLE public." + step.getValue() + " IN SHARE ROW EXCLUSIVE MODE");
                    for (int i = 0; i < 2; i++) loaders.add(pool.submit(() -> psql(db, step.getKey(), environment)));
                    // Both loaders have verified the target and read nothing yet: they wait on the seed lock.
                    awaitWaiters(holder, step.getValue(), 2);
                    holder.rollback();
                }
                for (var loader : loaders) assertSucceeded(loader.get(2, TimeUnit.MINUTES));
            }
        } finally {
            shutdownPool(pool);
        }
        assertEquals(List.of(finalDataset ? "6|4|8|24|25|20|24|20|3" : "2|3|2|4|5|0|0|0|0"), rows(db, "SELECT "
                + "(SELECT count(*) FROM public.users), (SELECT count(*) FROM public.courses), (SELECT count(*) FROM public.chapters), "
                + "(SELECT count(*) FROM public.lessons), (SELECT count(*) FROM public.learning_materials), (SELECT count(*) FROM public.skills), "
                + "(SELECT count(*) FROM public.skill_prerequisites), (SELECT count(*) FROM public.lesson_skills), "
                + "(SELECT count(*) FROM public.enrollments)"));
        String loaded = snapshot(db);
        for (var step : steps) assertSucceeded(psql(db, step.getKey(), environment));
        assertSafeEquals(loaded, snapshot(db), "row values and row versions after reruns at inherited " + isolation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void psqlVariablesKeepCredentialsOutOfProcessArguments(boolean finalDataset) throws Exception {
        String db = attested(freshDatabase());
        for (var step : List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"))) {
            whileLaunchedPsqlIsBlocked(db, step.getKey(), step.getValue(), PSQL, List.of(), finalDataset, (launch, pid) ->
                    assertEquals("1 0", inspect(db, step.getKey(), launch, pid, null),
                            "the launched psql is found; no password, hash, salt or checksum in any process argv"));
        }
    }

    /**
     * A different OS user cannot read {@code /proc/<pid>/exe} (ptrace_may_access, without the
     * CAP_SYS_PTRACE that containers drop by default), while {@code cmdline} stays readable. The
     * precondition proves the inspector really runs without exe; detection must still succeed.
     */
    @Test void psqlVariablesDetectionSurvivesUnreadableExeAcrossOsUsers() throws Exception {
        String db = attested(freshDatabase());
        whileLaunchedPsqlIsBlocked(db, USERS, "users", PSQL, List.of(), (launch, pid) -> {
            assertEquals("", output("postgres", "readlink", "/proc/" + pid + "/exe"),
                    "precondition: another OS user cannot read the client's /proc/<pid>/exe");
            assertEquals("1 0", inspect(db, USERS, launch, pid, "postgres"),
                    "found across OS users, with no credential in any process argv");
        });
    }

    /**
     * Under Rosetta or QEMU, {@code /proc/<pid>/exe} names the translator for every process. Starting
     * the real psql through the musl dynamic loader reproduces that deterministically on any host:
     * neither exe nor comm names psql, yet detection must still find the launched client.
     */
    @Test void psqlVariablesDetectionSurvivesATranslatedExecutable() throws Exception {
        String db = attested(freshDatabase());
        whileLaunchedPsqlIsBlocked(db, USERS, "users", translatedPsql(), List.of(), (launch, pid) -> {
            assertThat("precondition: exe names the loader, not psql",
                    output(null, "readlink", "/proc/" + pid + "/exe"), not(endsWith("/psql")));
            assertNotEquals("psql", inContainer(null, "cat", "/proc/" + pid + "/comm"), "precondition: comm is not psql");
            assertEquals("1 0", inspect(db, USERS, launch, pid, null),
                    "found through correlation alone, with no credential in any process argv");
        });
    }

    /** The detector is not vacuous: a credential in the launched client's argv is reported, by either OS user. */
    @Test void psqlVariablesDetectionReportsACredentialInProcessArguments() throws Exception {
        String db = attested(freshDatabase());
        String leak = "codemastery_leak_probe=" + secret("leak-" + password());
        whileLaunchedPsqlIsBlocked(db, USERS, "users", PSQL, List.of("-v", leak), (launch, pid) -> {
            assertEquals("1 1", inspect(db, USERS, launch, pid, null), "the leak is reported to the same OS user");
            assertEquals("1 1", inspect(db, USERS, launch, pid, "postgres"), "the leak is reported across OS users");
        });
    }

    /**
     * A decoy executable named psql, run by another OS user with the same database and seed file
     * arguments, meets every check of a comm/exe heuristic (comm psql, unreadable exe, exact argv),
     * while the real, leaking client is translated and meets none. Detection must follow the launched
     * client and report its leak instead of accepting the decoy.
     */
    @Test void psqlVariablesDetectionIsNotDeceivedByAProcessImpersonatingPsql() throws Exception {
        String db = attested(freshDatabase());
        String leak = "codemastery_leak_probe=" + secret("leak-" + password());
        // The decoy outlives this exec, so the postmaster (PID 1) adopts it. Any child that does not exit
        // with status 0 or 1 makes the postmaster restart every server process, so it exits 0 on TERM.
        String[] decoy = inContainer(null, "sh", "-c", """
                set -eu
                directory="$(mktemp -d)"
                printf '#!/bin/sh\\ntrap "exit 0" TERM\\nwhile :; do sleep 1; done\\n' > "$directory/psql"
                chmod 755 "$directory" "$directory/psql"
                su-exec postgres "$directory/psql" "$1" "$2" >/dev/null 2>&1 &
                printf '%s %s' "$!" "$directory"
                """, "decoy", db, "/seed/" + USERS).split(" ");
        try {
            assumeTrue("psql".equals(inContainer(null, "cat", "/proc/" + decoy[0] + "/comm")),
                    "a translated shell script does not report its own name as comm; the decoy needs a native host");
            assertEquals("", output(null, "readlink", "/proc/" + decoy[0] + "/exe"),
                    "precondition: the decoy's exe is unreadable to the inspector");
            whileLaunchedPsqlIsBlocked(db, USERS, "users", translatedPsql(), List.of("-v", leak), (launch, pid) ->
                    assertEquals("1 1", inspect(db, USERS, launch, pid, null),
                            "the launched, leaking client is found and reported despite the decoy"));
        } finally {
            // Signal the PID only while it is still this decoy: a reused PID could be a server process.
            String cleanup = inContainer(null, "sh", "-c", """
                    if [ "$(cat "/proc/$1/comm" 2>/dev/null)" = psql ] \\
                            && tr '\\000' '\\n' < "/proc/$1/cmdline" 2>/dev/null | grep -Fx -- "$2/psql" >/dev/null; then
                        kill "$1"; echo alive
                    else
                        echo gone
                    fi
                    rm -rf "$2"
                    """, "cleanup", decoy[0], decoy[1]);
            assertEquals("alive", cleanup, "the decoy must outlive the inspection it is meant to deceive");
        }
    }

    private static final List<String> PSQL = List.of("psql");

    /** psql run by the musl dynamic loader, so the process's exe and comm name the loader (as a translator would). */
    private static List<String> translatedPsql() throws Exception {
        return List.of(inContainer(null, "sh", "-c", "ls /lib/ld-musl-*.so.1"), "/usr/local/bin/psql");
    }

    @FunctionalInterface interface BlockedCheck { void check(Launch launch, int pid) throws Exception; }

    /**
     * A psql client started by this test. Its launcher writes the client's PID to {@code pidFile}, and its
     * session carries {@code applicationName}, so the blocked session and the process are both its own.
     */
    record Launch(String pidFile, String applicationName) {
        static Launch create() {
            String id = UUID.randomUUID().toString();
            return new Launch("/tmp/codemastery-psql-" + id + ".pid", "codemastery-it-" + id);
        }
    }

    /** Launches one variables-mode loader blocked on the table's seed lock, runs the check, then lets it finish. */
    private static void whileLaunchedPsqlIsBlocked(String db, String file, String table, List<String> program,
                                                   List<String> extraArguments, BlockedCheck check) throws Exception {
        whileLaunchedPsqlIsBlocked(db, file, table, program, extraArguments, false, check);
    }

    private static void whileLaunchedPsqlIsBlocked(String db, String file, String table, List<String> program,
                                                   List<String> extraArguments, boolean finalDataset, BlockedCheck check) throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        Launch launch = Launch.create();
        try (Connection holder = connect(db)) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE public." + table + " IN SHARE ROW EXCLUSIVE MODE");
            Future<Run> loader = pool.submit(() -> launchPsqlWithVariables(db, file, launch, program, extraArguments, finalDataset));
            try {
                awaitWaiters(holder, table, 1);
                awaitLaunchedSession(holder, launch);
                check.check(launch, Integer.parseInt(inContainer(null, "cat", launch.pidFile())));
            } finally {
                holder.rollback();
                assertSucceeded(loader.get(2, TimeUnit.MINUTES));
            }
        } finally {
            shutdownPool(pool);
            inContainer(null, "rm", "-f", launch.pidFile());
        }
    }

    /** The one waiting session is the launched client's own, identified by its application name. */
    private static void awaitLaunchedSession(Connection holder, Launch launch) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        try (var query = holder.prepareStatement(
                "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'")) {
            query.setString(1, launch.applicationName());
            while (System.nanoTime() < deadline) {
                try (var result = query.executeQuery()) { result.next(); if (result.getInt(1) == 1) return; }
                Thread.sleep(50);
            }
        }
        fail("the launched psql session did not reach the seed lock");
    }

    /** Runs {@link #INSPECT_LAUNCHED_PSQL} as {@code user} (null: the default exec user). */
    private static String inspect(String db, String file, Launch launch, int pid, String user) throws Exception {
        var config = ExecConfig.builder().command(new String[] {"sh", "-c", INSPECT_LAUNCHED_PSQL})
                .envVars(Map.of("ARGV_PID", Integer.toString(pid), "ARGV_DB", db, "ARGV_FILE", "/seed/" + file,
                        "ARGV_PID_FILE", launch.pidFile(), "ARGV_INPUTS", String.join("\n", SECRETS)));
        if (user != null) config.user(user);
        ExecResult inspection = POSTGRES.execInContainer(config.build());
        assertSucceeded(new Run(inspection.getExitCode(), inspection.getStdout(), inspection.getStderr()));
        return inspection.getStdout().trim();
    }

    /** A credential-free command in the container that must succeed; returns trimmed stdout. */
    private static String inContainer(String user, String... command) throws Exception {
        Run run = exec(user, command);
        assertSucceeded(run);
        return run.stdout().trim();
    }

    /** Trimmed stdout of a probe whose failure is an expected answer, such as readlink of an unreadable exe. */
    private static String output(String user, String... command) throws Exception {
        Run run = exec(user, command);
        assertRedacted(run);
        return run.stdout().trim();
    }

    private static Run exec(String user, String... command) throws Exception {
        var config = ExecConfig.builder().command(command);
        if (user != null) config.user(user);
        ExecResult result = POSTGRES.execInContainer(config.build());
        return new Run(result.getExitCode(), result.getStdout(), result.getStderr());
    }

    @Test void failedComparisonsNeverDiscloseCredentials() {
        String password = secret("sentinel-" + password()), hash = secret(BCRYPT.encode(password));
        String otherHash = secret(BCRYPT.encode(password + "-other"));
        List<String> sentinels = List.of(password, hash, otherHash);

        AssertionError rows = assertThrows(AssertionError.class, () -> assertSafeEquals(
                List.of(INSTRUCTOR + "|Instructor Demo|" + hash), List.of(INSTRUCTOR + "|Instructor Demo|" + otherHash), "seeded Users"));
        assertNoDisclosure(rows, sentinels);
        assertThat(rows.getMessage(), containsString("seeded Users"));
        assertThat(rows.getMessage(), containsString(INSTRUCTOR.toString()));

        // Fragments that are not registered secrets themselves: a truncated hash, a re-prefixed hash, a bare salt.
        AssertionError fragments = assertThrows(AssertionError.class, () -> assertSafeEquals(
                hash.substring(0, 40) + " " + hash.replace("$2b$", "$2a$"), hash.substring(7, 29) + " " + password, "fragments"));
        assertNoDisclosure(fragments, sentinels);
    }

    @Test void failedProcessChecksNeverDiscloseCredentials() {
        String password = secret("sentinel-" + password()), hash = secret(BCRYPT.encode(password));
        List<String> sentinels = List.of(password, hash);
        String leakingOutput = "psql:/seed/01-users.sql:1: ERROR:  failing row contains " + hash + " for " + password;

        assertNoDisclosure(assertThrows(AssertionError.class, () -> assertSucceeded(new Run(3, "", leakingOutput))), sentinels);
        assertNoDisclosure(assertThrows(AssertionError.class, () -> assertSucceeded(new Run(0, leakingOutput, ""))), sentinels);
        assertNoDisclosure(assertThrows(AssertionError.class, () -> assertRejected(new Run(0, "", leakingOutput), "expected error")), sentinels);
        assertNoDisclosure(assertThrows(AssertionError.class, () -> assertRejected(new Run(3, "", leakingOutput), "expected error")), sentinels);
        assertNoDisclosure(assertThrows(AssertionError.class, () -> assertRejected(
                new Run(3, "", "ERROR:  expected error\n" + leakingOutput), "expected error")), sentinels);
        assertFalse(new Run(3, leakingOutput, leakingOutput).toString().contains(hash), "Run.toString must omit process output");
    }

    @Test void failedSnapshotComparisonsNeverDiscloseStoredHashes() throws Exception {
        String instructorPassword = secret("sentinel-" + password()), learnerPassword = secret("sentinel-" + password());
        String instructorHash = secret(BCRYPT.encode(instructorPassword)), learnerHash = secret(BCRYPT.encode(learnerPassword));
        String db = attested(freshDatabase());
        Map<String, String> environment = seedEnvironment();
        environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", instructorHash);
        environment.put("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", learnerHash);
        assertSucceeded(psql(db, USERS, environment));
        String before = snapshot(db);
        assertTrue(before.contains(instructorHash) && before.contains(learnerHash), "the snapshot carries the stored hashes");

        execute(db, "UPDATE public.users SET display_name = 'Changed Name' WHERE id = '" + LEARNER + "'");
        AssertionError failure = assertThrows(AssertionError.class, () -> assertSafeEquals(before, snapshot(db), "Users snapshot"));
        assertNoDisclosure(failure, List.of(instructorPassword, learnerPassword, instructorHash, learnerHash));
        assertThat(failure.getMessage(), containsString("Changed Name"));
    }

    /**
     * On an exclusively owned consumer database/bucket: the real SD01-SD03 scripts, then Auth, catalogue, skill-graph
     * and membership consumers, and the ADR013 §4.2 provisioning of L01's PDF through the CT09 upload, publication and
     * protected download operations against an isolated MinIO. No row or object reference is written around the API.
     */
    @Test void seededRecordsAreServedByTheAuthAndCatalogueConsumers() throws Exception {
        try (ConsumerFixture fixture = ConsumerFixture.open()) {
            String db = fixture.database;
            MockMvc mvc = fixture.mvc();
            loadFinal(db, USERS, COURSES, GRAPH, PROFILES);

            String instructorToken = signIn(mvc, "instructor@example.test", INSTRUCTOR_PASSWORD, INSTRUCTOR, "Instructor", "Instructor Demo");
            String learnerToken = signIn(mvc, "learner@example.test", LEARNER_PASSWORD, LEARNER, "Learner", "Learner Demo");
            signIn(mvc, "admin@example.test", FINAL_PASSWORDS.get("admin"), final5("user/admin"), "Administrator", "Administrator Demo");
            Map<String, String> profileTokens = new HashMap<>();
            for (int i = 0; i < PROFILE_KEYS.size(); i++) {
                String profile = PROFILE_KEYS.get(i);
                profileTokens.put(profile, signIn(mvc, profile + "@example.test", FINAL_PASSWORDS.get(profile), final5("user/" + profile),
                        "Learner", PROFILE_NAMES.get(i)));
            }
            // Each account has its own password: another account's password, or the right one with a wrong case, fails.
            for (var attempt : List.of(Map.entry("learner@example.test", INSTRUCTOR_PASSWORD), Map.entry("p1@example.test", FINAL_PASSWORDS.get("p2")),
                    Map.entry("admin@example.test", FINAL_PASSWORDS.get("p3")), Map.entry("p3@example.test", FINAL_PASSWORDS.get("p3").toUpperCase(Locale.ROOT))))
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(attempt.getKey(), attempt.getValue())))
                        .andExpect(status().isUnauthorized());

            // AD07's owner read re-validates the stored graph and proves every mapping against CO06's structure.
            mvc.perform(get("/api/courses/" + PYTHON + "/skill-graph").header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.skills[*].name", containsInAnyOrder(SKILLS.toArray())))
                    .andExpect(jsonPath("$.prerequisites.length()").value(24)).andExpect(jsonPath("$.mappings.length()").value(20));
            mvc.perform(get("/api/courses/" + PYTHON)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.syllabus[*].title", contains(CHAPTER_TITLES.toArray())))
                    .andExpect(jsonPath("$.syllabus[2].lessons[*].title", contains("While Loops", "For Loops", "Loop Control")));
            for (String profile : PROFILE_KEYS)
                mvc.perform(get("/api/courses/" + PYTHON + "/membership").header(HttpHeaders.AUTHORIZATION, bearer(profileTokens.get(profile))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
            mvc.perform(get("/api/courses/" + PYTHON + "/membership").header(HttpHeaders.AUTHORIZATION, bearer(learnerToken)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("none"));

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

            provisionAndReadBackTheL01Slides(mvc, db, fixture.storage.bucket, instructorToken, profileTokens.get("p1"), learnerToken);
        }
    }

    /** ADR013 §4.2: upload through CT09 after loading, publish, then protected readback with the recorded SHA-256. */
    private void provisionAndReadBackTheL01Slides(MockMvc mvc, String db, String bucket, String instructorToken, String profileToken,
                                                  String outsiderToken) throws Exception {
        byte[] slides = Files.readAllBytes(SLIDES);
        assertEquals(SLIDES_SHA256, sha256(slides), "the committed file is the recorded revision");
        UUID l01 = final5("course/python/chapter/1/lesson/1");
        String files = "/api/instructor/courses/" + PYTHON + "/lessons/" + l01 + "/file-materials";
        String created = mvc.perform(multipart(files)
                        .part(new MockPart("title", SLIDES_TITLE.getBytes(StandardCharsets.UTF_8)),
                                new MockPart("file", "sample-slides.pdf", slides))
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(SLIDES_TITLE))
                .andExpect(jsonPath("$.materialType").value("file")).andExpect(jsonPath("$.publicationStatus").value("hidden"))
                .andReturn().getResponse().getContentAsString();
        assertThat("no storage key, filename or URL in JSON", created, allOf(not(containsString("objectKey")),
                not(containsString("sample-slides")), not(containsString(bucket))));
        UUID material = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        assertNotEquals(final5("course/python/chapter/1/lesson/1/material/2"), material, "the application, not the dataset, chooses the identity");
        assertEquals(List.of(l01 + "|file|hidden|sample-slides.pdf|true|true"), rows(db, "SELECT lesson_id, material_type, "
                + "publication_status, original_filename, object_key IS NOT NULL, text_content IS NULL FROM public.learning_materials WHERE id = '"
                + material + "'"));
        String download = "/api/courses/" + PYTHON + "/lessons/" + l01 + "/materials/" + material + "/file";
        // Hidden: even the enrolled profile Learner cannot see it (404, never revealing the hidden row).
        mvc.perform(get(download).header(HttpHeaders.AUTHORIZATION, bearer(profileToken))).andExpect(status().isNotFound());

        mvc.perform(put(files + "/" + material + "/publication").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicationStatus\":\"published\"}").header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationStatus").value("published"));
        mvc.perform(get("/api/courses/" + PYTHON + "/lessons/" + l01).header(HttpHeaders.AUTHORIZATION, bearer(profileToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.materials[*].id", hasItem(material.toString())))
                .andExpect(jsonPath("$.materials[*].materialType", containsInAnyOrder("text", "file")));
        byte[] readBack = mvc.perform(get(download).header(HttpHeaders.AUTHORIZATION, bearer(profileToken)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(SLIDES_SHA256, sha256(readBack), "protected readback is byte-identical to the committed PDF");
        // ADR010 §4 (CT03 precedence): the interim-v1 Learner has no course/python membership (403); anonymous is 401.
        mvc.perform(get(download).header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken))).andExpect(status().isForbidden());
        mvc.perform(get(download)).andExpect(status().isUnauthorized());
    }

    private static String signIn(MockMvc mvc, String email, String password, UUID id, String role, String displayName) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, password)))
                .andReturn().getResponse();
        for (String cookie : response.getHeaders(HttpHeaders.SET_COOKIE)) {
            int equals = cookie.indexOf('='), end = cookie.indexOf(';');
            if (equals >= 0 && end > equals + 1) secret(cookie.substring(equals + 1, end));
        }
        String body = response.getContentAsString();
        String token;
        try { token = secret(com.jayway.jsonpath.JsonPath.read(body, "$.accessToken")); }
        catch (RuntimeException invalid) { fail("invalid sign-in response (diagnostics withheld)"); return null; }
        assertEquals(200, response.getStatus(), "seeded sign-in status");
        try {
            assertSafeEquals(id.toString(), com.jayway.jsonpath.JsonPath.read(body, "$.user.id"), "signed-in identity");
            assertSafeEquals(role, com.jayway.jsonpath.JsonPath.read(body, "$.user.role"), "signed-in role");
            assertSafeEquals(displayName, com.jayway.jsonpath.JsonPath.read(body, "$.user.displayName"), "signed-in display name");
        } catch (RuntimeException invalid) { fail("invalid sign-in metadata (diagnostics withheld)"); }
        return token;
    }

    private static String bearer(String token) { return "Bearer " + token; }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    @FunctionalInterface private interface Connections { Connection open() throws SQLException; }

    /** Never retain provider/SQL diagnostics as the shared failure's cause. */
    private static IllegalStateException unsafe(String operation) {
        IllegalStateException safe = new IllegalStateException(operation + " failed (diagnostics withheld)");
        recordIsolationLoss(safe);
        return safe;
    }

    private static void guarded(String operation, CheckedAction action) {
        try { action.run(); }
        catch (Exception | LinkageError | AssertionError failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw unsafe(operation);
        }
    }

    private static void shutdownPool(java.util.concurrent.ExecutorService pool) {
        guarded("worker completion", () -> {
            pool.shutdownNow();
            if (!pool.awaitTermination(15, TimeUnit.SECONDS)) throw new IllegalStateException("worker remains active");
        });
    }

    private static long databaseOid(Connection admin, String name) throws SQLException {
        try (var query = admin.prepareStatement("SELECT oid::bigint FROM pg_database WHERE datname = ?")) {
            query.setString(1, name);
            try (var result = query.executeQuery()) { return result.next() ? result.getLong(1) : -1; }
        }
    }

    /** Only names whose successful CREATE and catalog OID this class recorded may be dropped. */
    private static void dropOwnedDatabase(Connection admin, String name, long oid) throws SQLException {
        if (!name.matches("sd_seed_[0-9a-f]{32}")) throw new SQLException("invalid owned database name");
        long current = databaseOid(admin, name);
        if (current != -1 && current != oid) throw new SQLException("database ownership changed");
        if (current != -1) {
            try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE " + name + " WITH (FORCE)"); }
        }
        if (databaseOid(admin, name) != -1) throw new SQLException("owned database remains");
        synchronized (CREATED) { CREATED.remove(name, oid); }
    }

    private static void dropOwnedDatabases(Connections connections) {
        guarded("owned PostgreSQL teardown", () -> {
            try (Connection admin = connections.open()) {
                Map<String, Long> owned;
                synchronized (CREATED) { owned = Map.copyOf(CREATED); }
                boolean failed = false;
                for (var entry : owned.entrySet()) {
                    try { dropOwnedDatabase(admin, entry.getKey(), entry.getValue()); }
                    catch (SQLException problem) { failed = true; }
                }
                if (failed || !PENDING_DATABASES.isEmpty()) throw new SQLException("owned database teardown incomplete or creation unacknowledged");
            }
        });
    }

    private static void dropOwnedRoles() {
        guarded("owned PostgreSQL role teardown", () -> {
            try (Connection admin = connect(POSTGRES.getDatabaseName())) {
                for (var role : Map.copyOf(CREATED_ROLES).entrySet()) {
                    try (var query = admin.prepareStatement("SELECT oid::bigint FROM pg_roles WHERE rolname = ?")) {
                        query.setString(1, role.getKey());
                        try (var result = query.executeQuery()) {
                            if (result.next()) {
                                if (result.getLong(1) != role.getValue()) throw new SQLException("role ownership changed");
                                admin.createStatement().execute("DROP ROLE " + role.getKey());
                            }
                        }
                    }
                    CREATED_ROLES.remove(role.getKey());
                }
                if (!PENDING_ROLES.isEmpty()) throw new SQLException("role creation unacknowledged");
            }
        });
    }

    private static String unprivilegedRole() throws SQLException {
        String name = "sd_seed_role_" + UUID.randomUUID().toString().replace("-", "");
        PENDING_ROLES.add(name);
        try (Connection admin = connect(POSTGRES.getDatabaseName()); var statement = admin.createStatement()) {
            statement.execute("CREATE ROLE " + name + " LOGIN");
            try (var result = statement.executeQuery("SELECT oid::bigint FROM pg_roles WHERE rolname = '" + name + "'")) {
                result.next(); CREATED_ROLES.put(name, result.getLong(1)); PENDING_ROLES.remove(name);
            }
        } catch (SQLException failure) { throw unsafe("disposable role creation"); }
        return name;
    }

    /** Bucket, policy and identity belong to one private consumer context, never to a developer store. */
    private static final class StorageFixture implements AutoCloseable {
        final String bucket = "sd02-" + UUID.randomUUID();
        final String user = secret(password()), credential = secret(password() + password());
        final String policy = "sd02-" + UUID.randomUUID();
        boolean admitted;
        boolean closed;

        void provision() {
            guarded("isolated MinIO bootstrap", () -> {
                try (S3Client admin = storageAdministrator()) {
                    if (admin.listBuckets().buckets().stream().anyMatch(row -> bucket.equals(row.name())))
                        throw new IllegalStateException("bucket already exists");
                    admitted = true;
                    admin.createBucket(request -> request.bucket(bucket));
                }
                storageCommand(this, """
                        mc admin user add local "$APP_USER" "$APP_SECRET" >/dev/null 2>&1
                        printf '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket"],"Resource":["arn:aws:s3:::%s"]},{"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::%s/*"]}]}' "$BUCKET" "$BUCKET" >/tmp/policy.json
                        mc admin policy create local "$POLICY" /tmp/policy.json >/dev/null 2>&1
                        mc admin policy attach local "$POLICY" --user "$APP_USER" >/dev/null 2>&1
                        """);
            });
        }

        @Override public void close() {
            close(() -> {
                try (S3Client admin = storageAdministrator()) {
                    // The entire bucket is exclusively owned, including an unreferenced object from a failed upload.
                    try {
                        for (var page : admin.listObjectsV2Paginator(request -> request.bucket(bucket)))
                            for (var object : page.contents()) admin.deleteObject(request -> request.bucket(bucket).key(object.key()));
                        if (admin.listObjectsV2(request -> request.bucket(bucket)).keyCount() != 0)
                            throw new IllegalStateException("objects remain");
                        admin.deleteBucket(request -> request.bucket(bucket));
                    } catch (software.amazon.awssdk.services.s3.model.NoSuchBucketException absent) { /* verified absent */ }
                }
                storageCommand(this, """
                        mc admin policy detach local "$POLICY" --user "$APP_USER" >/dev/null 2>&1
                        mc admin user remove local "$APP_USER" >/dev/null 2>&1
                        mc admin policy remove local "$POLICY" >/dev/null 2>&1
                        """);
            });
        }

        void close(CheckedAction cleanup) {
            if (closed || !admitted) return;
            guarded("owned MinIO teardown", cleanup);
            closed = true;
        }
    }

    private static void storageCommand(StorageFixture fixture, String commands) throws Exception {
        try (GenericContainer<?> initializer = new GenericContainer<>(DockerImageName.parse(MC_IMAGE))
                .withNetwork(STORAGE_NETWORK).withEnv("ROOT_USER", STORAGE_ROOT_USER).withEnv("ROOT_SECRET", STORAGE_ROOT_SECRET)
                .withEnv("APP_USER", fixture.user).withEnv("APP_SECRET", fixture.credential)
                .withEnv("BUCKET", fixture.bucket).withEnv("POLICY", fixture.policy)
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/sh"))
                .withCommand("-ec", "mc alias set local http://storage:9000 \"$ROOT_USER\" \"$ROOT_SECRET\" >/dev/null 2>&1\n" + commands)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(60)))) {
            initializer.start();
            if (!Long.valueOf(0).equals(initializer.getCurrentContainerInfo().getState().getExitCodeLong()))
                throw new IllegalStateException("storage initializer failed");
        }
    }

    @FunctionalInterface private interface FixtureAdmission { void run(ConsumerFixture fixture) throws Exception; }

    private static final class ConsumerFixture implements AutoCloseable {
        String database;
        final StorageFixture storage = new StorageFixture();
        org.springframework.context.ConfigurableApplicationContext application;
        boolean closed;

        static ConsumerFixture open() { return open(fixture -> { }); }

        static ConsumerFixture open(FixtureAdmission beforeApplication) {
            ConsumerFixture fixture = new ConsumerFixture();
            ACTIVE_CONSUMERS.add(fixture); // Track before bootstrap so partial setup is owned too.
            guarded("private consumer bootstrap", () -> {
                fixture.database = attested(freshDatabase());
                fixture.storage.provision();
                beforeApplication.run(fixture);
                fixture.application = new org.springframework.boot.builder.SpringApplicationBuilder(com.codemastery.CodeMasteryApplication.class)
                        .profiles("test").web(org.springframework.boot.WebApplicationType.SERVLET)
                        .run("--server.port=0", "--server.address=127.0.0.1", "--spring.config.import=",
                                "--spring.datasource.url=" + url(fixture.database),
                                "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(),
                                "--JWT_SECRET=" + KEY, "--codemastery.storage.endpoint=" + storageEndpoint(),
                                "--codemastery.storage.bucket=" + fixture.storage.bucket,
                                "--codemastery.storage.access-key=" + fixture.storage.user,
                                "--codemastery.storage.secret-key=" + fixture.storage.credential,
                                "--codemastery.storage.region=us-east-1", "--codemastery.storage.path-style-access=true",
                                "--codemastery.scheduling.enabled=false", "--app.quiz.auto-submit.enabled=false",
                                "--app.quiz.auto-submit.delay-ms=1000", "--app.quiz.auto-submit.batch-size=2");
            });
            return fixture;
        }

        MockMvc mvc() { return webAppContextSetup((WebApplicationContext) application).apply(springSecurity()).build(); }
        String api() { return "http://127.0.0.1:" + application.getEnvironment().getProperty("local.server.port"); }

        @Override public void close() { close(() -> { if (application != null) application.close(); }); }

        void close(CheckedAction stopContext) {
            if (closed) return;
            // Join callers before close. If context shutdown fails, preserve resources and invalidate later tests.
            guarded("private consumer context shutdown", stopContext);
            boolean failed = false;
            try { storage.close(); } catch (IllegalStateException failure) { failed = true; }
            if (database != null) {
                try {
                    guarded("private consumer database teardown", () -> {
                        Long oid; synchronized (CREATED) { oid = CREATED.get(database); }
                        if (oid != null) try (Connection admin = connect(POSTGRES.getDatabaseName())) { dropOwnedDatabase(admin, database, oid); }
                    });
                } catch (IllegalStateException failure) { failed = true; }
            }
            if (failed) throw unsafe("private consumer cleanup");
            closed = true; ACTIVE_CONSUMERS.remove(this);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void sampleSlidesAreTheRecordedValidPdf() throws Exception {
        byte[] pdf = Files.readAllBytes(SLIDES);
        assertEquals(SLIDES_SHA256, sha256(pdf));
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

    private static String unrelatedRowsQuery() { return nonEmptyTablesExcept("'users','courses','chapters','lessons','learning_materials'"); }

    /** final-v1 may also fill the graph and Enrollments; every other table (mastery, attempts, progress, ...) must stay empty. */
    private static String finalUnrelatedRowsQuery() {
        return nonEmptyTablesExcept("'users','courses','chapters','lessons','learning_materials','skills','skill_prerequisites',"
                + "'lesson_skills','enrollments'");
    }

    private static String nonEmptyTablesExcept(String tables) {
        return "SELECT table_name FROM information_schema.tables t WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                + "AND table_name NOT IN (" + tables + ",'flyway_schema_history') "
                + "AND (xpath('/row/n/text()', query_to_xml(format('SELECT count(*) AS n FROM public.%I', table_name), false, true, '')))[1]::text <> '0'";
    }

    private static String login(String email, String password) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("email", email, "password", password));
    }

    /** The unchanged ADR002 interim-v1 inputs: target identifier and the two interim-v1 hashes, no selector. */
    private static Map<String, String> seedEnvironment() {
        return new HashMap<>(Map.of("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", systemIdentifier,
                "CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_HASH, "CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH));
    }

    /** The final-v1 inputs: the interim-v1 ones, the selector, the four final-v1 hashes and the course/python policy. */
    private static Map<String, String> finalEnvironment() {
        Map<String, String> environment = seedEnvironment();
        environment.put("CODEMASTERY_SEED_DATASET", "final-v1");
        FINAL_HASHES.forEach((account, hash) -> environment.put(hashVariable(account), hash));
        // ADR013 §16.1 approved demonstration policy of course/python.
        environment.putAll(Map.of("CODEMASTERY_SEED_ADAPTIVE_WEAK", "1/2", "CODEMASTERY_SEED_ADAPTIVE_MASTERED", "4/5",
                "CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL", "6"));
        return environment;
    }

    private static String hashVariable(String account) { return "CODEMASTERY_SEED_" + account.toUpperCase(Locale.ROOT) + "_PASSWORD_HASH"; }

    /** One psql run. {@code toString()} deliberately omits the output, which could carry credentials. */
    record Run(int exitCode, String stdout, String stderr) {
        @Override public String toString() { return "psql run with exit code " + exitCode; }
    }

    /** Seed inputs as environment variables; the command line carries no credential. */
    private static Run psql(String db, String file, Map<String, String> environment) throws Exception {
        String user = environment.getOrDefault("PGUSER", POSTGRES.getUsername());
        String[] command = {"psql", "-X", "-q", "-U", user, "-d", db, "-f", "/seed/" + file};
        ExecResult result = POSTGRES.execInContainer(ExecConfig.builder().command(command).envVars(environment).build());
        return new Run(result.getExitCode(), result.getStdout(), result.getStderr());
    }

    /**
     * Populate psql variables from RUN_* before reading the seed, without putting values in process arguments.
     * Invalid seed-environment fallbacks prove that the explicitly populated variables take precedence.
     */
    private static Run psqlWithVariables(String db, String file, boolean finalDataset) throws Exception {
        return runPsqlWithVariables(db, file, List.of(), PSQL, List.of(), Map.of(), finalDataset);
    }

    /** {@link #psqlWithVariables} started through {@link #RECORD_PID_THEN_EXEC}, as {@code program}, with extra arguments. */
    private static Run launchPsqlWithVariables(String db, String file, Launch launch, List<String> program,
                                               List<String> extraArguments, boolean finalDataset) throws Exception {
        return runPsqlWithVariables(db, file, List.of("sh", "-c", RECORD_PID_THEN_EXEC, "codemastery-psql-launcher",
                launch.pidFile()), program, extraArguments, Map.of("PGAPPNAME", launch.applicationName()), finalDataset);
    }

    /**
     * The selector is always set as a psql variable over an invalid environment fallback. interim-v1 sets no final-v1
     * input at all, because a final-v1 input without the selector is itself rejected.
     */
    private static Run runPsqlWithVariables(String db, String file, List<String> launcher, List<String> program,
                                            List<String> extraArguments, Map<String, String> extraEnvironment,
                                            boolean finalDataset) throws Exception {
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("seed_target_system_identifier", "CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER");
        inputs.put("instructor_password_hash", "CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH");
        inputs.put("learner_password_hash", "CODEMASTERY_SEED_LEARNER_PASSWORD_HASH");
        inputs.put("seed_dataset", "CODEMASTERY_SEED_DATASET");
        if (finalDataset) {
            for (String account : FINAL_ACCOUNTS) inputs.put(account + "_password_hash", hashVariable(account));
            inputs.put("adaptive_weak", "CODEMASTERY_SEED_ADAPTIVE_WEAK");
            inputs.put("adaptive_mastered", "CODEMASTERY_SEED_ADAPTIVE_MASTERED");
            inputs.put("adaptive_minimum_questions_per_skill", "CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL");
        }
        Map<String, String> values = finalDataset ? finalEnvironment() : seedEnvironment();
        values.put("CODEMASTERY_SEED_DATASET", finalDataset ? "final-v1" : "interim-v1");
        List<String> command = new ArrayList<>(launcher);
        command.addAll(program);
        command.addAll(List.of("-X", "-q", "-U", POSTGRES.getUsername(), "-d", db));
        Map<String, String> environment = new HashMap<>();
        int index = 0;
        for (var input : inputs.entrySet()) {
            String run = "RUN_" + index++;
            command.addAll(List.of("-c", "\\getenv " + input.getKey() + " " + run));
            environment.put(run, values.get(input.getValue()));
            environment.put(input.getValue(), "invalid");
        }
        command.addAll(extraArguments);
        command.addAll(List.of("-f", "/seed/" + file));
        environment.putAll(extraEnvironment);
        ExecResult result = POSTGRES.execInContainer(ExecConfig.builder()
                .command(command.toArray(String[]::new)).envVars(environment).build());
        return new Run(result.getExitCode(), result.getStdout(), result.getStderr());
    }

    private static void assertLoaded(String db, String file) throws Exception { assertSucceeded(psql(db, file, seedEnvironment())); }

    private static void assertFinalLoaded(String db, String file) throws Exception { assertSucceeded(psql(db, file, finalEnvironment())); }

    // Every failure message below is built from redacted text only, and no assertion is given raw output to print.

    private static void assertSucceeded(Run run) {
        assertRedacted(run);
        if (run.exitCode() != 0) fail("psql exited with " + run.exitCode() + "; stderr: " + redact(run.stderr()));
    }

    private static void assertRejected(Run run, String message) {
        assertRedacted(run);
        if (run.exitCode() != 3) fail("psql must stop on the script error but exited with " + run.exitCode() + "; stderr: " + redact(run.stderr()));
        if (!run.stderr().contains("ERROR:  " + message)) fail("expected the error \"" + message + "\"; stderr: " + redact(run.stderr()));
    }

    private static void assertRedacted(Run run) {
        if (discloses(run.stdout() + run.stderr())) fail("seed output disclosed a credential, hash or salt (output withheld)");
    }

    /** Exact comparison; on a mismatch, reports only redacted values and attaches no expected/actual objects. */
    private static void assertSafeEquals(Object expected, Object actual, String context) {
        if (!Objects.equals(expected, actual)) {
            fail(context + " differs (credentials redacted)\nexpected: " + redact(String.valueOf(expected))
                    + "\nactual:   " + redact(String.valueOf(actual)));
        }
    }

    private static String secret(String value) {
        SECRETS.add(value);
        if (value.startsWith("$2")) {
            SECRETS.add(value.substring(7, 29));
            SECRETS.add(value.substring(29));
        }
        return value;
    }

    /** Masks whole BCrypt-shaped text first, so a partly registered hash cannot leave fragments behind. */
    private static String redact(String text) {
        String safe = BCRYPT_TEXT.matcher(text).replaceAll("<bcrypt-hash>");
        for (String secret : SECRETS) safe = safe.replace(secret, "<redacted>");
        return safe;
    }

    private static boolean discloses(String text) {
        return SECRETS.stream().anyMatch(text::contains) || BCRYPT_TEXT.matcher(text).find();
    }

    /** Checks everything a test report can show for a failure: message, string form, stack trace and attached values. */
    private static void assertNoDisclosure(Throwable failure, List<String> sentinels) {
        var trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        String rendered = failure + "\n" + trace;
        if (failure instanceof AssertionFailedError detailed) rendered += "\n" + detailed.getExpected() + "\n" + detailed.getActual();
        for (String sentinel : sentinels) {
            List<String> parts = sentinel.startsWith("$2") ? List.of(sentinel, sentinel.substring(7, 29), sentinel.substring(29)) : List.of(sentinel);
            for (String part : parts) if (rendered.contains(part)) fail("a failure diagnostic disclosed a sentinel credential (diagnostic withheld)");
        }
        if (BCRYPT_TEXT.matcher(rendered).find()) fail("a failure diagnostic contains BCrypt-shaped text (diagnostic withheld)");
        assertTrue(rendered.contains("<redacted>") || rendered.contains("<bcrypt-hash>") || rendered.contains("withheld"),
                "the diagnostic shows that redaction happened");
    }

    @FunctionalInterface private interface CreatedDatabaseCheckpoint { void run(String database) throws Exception; }

    private static String freshDatabase() throws SQLException { return freshDatabase(database -> { }); }

    private static String freshDatabase(CreatedDatabaseCheckpoint afterCreate) throws SQLException {
        String db = "sd_seed_" + UUID.randomUUID().toString().replace("-", "");
        PENDING_DATABASES.add(db); // Reserve before CREATE; never infer ownership from a guessed name after an error.
        try {
            try (var admin = connect(POSTGRES.getDatabaseName()); var statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE " + db);
                afterCreate.run(db);
                long oid = databaseOid(admin, db);
                if (oid == -1) throw new SQLException("created database identity unavailable");
                synchronized (CREATED) { CREATED.put(db, oid); }
                PENDING_DATABASES.remove(db);
            }
            Flyway.configure().dataSource(url(db), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        } catch (Exception failure) { throw unsafe("disposable database creation/migration"); }
        return db;
    }

    private static String attested(String db) throws SQLException { attest(db, "disposable:" + systemIdentifier); return db; }

    /** Sets the database default that new sessions, including psql loaders, inherit; confirms it is in effect. */
    private static String inheritIsolation(String db, String isolation) throws Exception {
        execute(db, "ALTER DATABASE " + db + " SET default_transaction_isolation = '" + isolation + "'");
        ExecResult shown = POSTGRES.execInContainer("psql", "-X", "-At", "-U", POSTGRES.getUsername(), "-d", db,
                "-c", "SHOW default_transaction_isolation");
        assertEquals(isolation, shown.getStdout().trim(), "new sessions inherit the database default");
        return db;
    }

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
                + "SELECT 'learning_materials', xmin::text, row_to_json(r)::text FROM public.learning_materials r UNION ALL "
                + "SELECT 'skills', xmin::text, row_to_json(r)::text FROM public.skills r UNION ALL "
                + "SELECT 'skill_prerequisites', xmin::text, row_to_json(r)::text FROM public.skill_prerequisites r UNION ALL "
                + "SELECT 'lesson_skills', xmin::text, row_to_json(r)::text FROM public.lesson_skills r UNION ALL "
                + "SELECT 'enrollments', xmin::text, row_to_json(r)::text FROM public.enrollments r) s "
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
        java.util.Properties settings = new java.util.Properties();
        settings.setProperty("user", POSTGRES.getUsername()); settings.setProperty("password", POSTGRES.getPassword());
        settings.setProperty("connectTimeout", "5"); settings.setProperty("socketTimeout", "15");
        return DriverManager.getConnection(url(db), settings);
    }

    private static String url(String db) { return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1"); }

    static UUID uuid5(String logicalKey) { return datasetUuid("interim-v1", logicalKey); }

    static UUID final5(String logicalKey) { return datasetUuid("final-v1", logicalKey); }

    private static UUID datasetUuid(String dataset, String logicalKey) {
        try {
            var sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16).putLong(0x6ba7b8119dad11d1L).putLong(0x80b400c04fd430c8L).array());
            sha1.update(("https://codemastery.test/" + dataset + "/" + logicalKey).getBytes(StandardCharsets.UTF_8));
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
    @ParameterizedTest
    @ValueSource(strings = {"options-default", "options-explicit", "session", "role", "database", "clean"})
    void validatedSelectorOverridesInheritedDatasetSettings(String mode) throws Exception {
        String db = attested(freshDatabase());
        Map<String, String> environment = seedEnvironment();
        environment.put("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", INSTRUCTOR_HASH);
        if (mode.equals("options-explicit")) environment.put("CODEMASTERY_SEED_DATASET", "interim-v1");
        if (mode.startsWith("options")) environment.put("PGOPTIONS", "-c codemastery.seed_dataset=final-v1");
        if (mode.equals("role")) execute(db, "ALTER ROLE " + POSTGRES.getUsername() + " IN DATABASE " + db + " SET codemastery.seed_dataset='final-v1'");
        if (mode.equals("database")) execute(db, "ALTER DATABASE " + db + " SET codemastery.seed_dataset='final-v1'");
        if (mode.equals("session")) {
            ExecResult result = POSTGRES.execInContainer(ExecConfig.builder().command(new String[] {
                    "psql", "-X", "-q", "-U", POSTGRES.getUsername(), "-d", db,
                    "-c", "SET codemastery.seed_dataset='final-v1'", "-f", "/seed/" + USERS, "-f", "/seed/" + USERS})
                    .envVars(environment).build());
            assertSucceeded(new Run(result.getExitCode(), result.getStdout(), result.getStderr()));
        } else {
            assertSucceeded(psql(db, USERS, environment));
            String before = snapshot(db);
            assertSucceeded(psql(db, USERS, environment));
            assertSafeEquals(before, snapshot(db), "Interim rerun under inherited settings");
        }
        assertEquals(List.of("2"), rows(db, "SELECT count(*) FROM public.users"));
        Map<String, String> finalInputs = finalEnvironment();
        finalInputs.put("PGOPTIONS", "-c codemastery.seed_dataset=interim-v1");
        finalInputs.put(hashVariable("p1"), LEARNER_HASH);
        assertRejected(psql(db, USERS, finalInputs), "SD01 final-v1 requires a distinct password hash per account");
    }

    @ParameterizedTest
    @ValueSource(strings = {"constraint", "statement", "success", "unprivileged"})
    void credentialStatementsProtectActualServerLogsAndRestoreLogging(String failure) throws Exception {
        String db = attested(freshDatabase());
        for (String setting : List.of("log_statement='all'", "log_duration=on", "log_min_duration_statement=0",
                "log_min_duration_sample=0", "log_transaction_sample_rate=1", "log_error_verbosity='verbose'",
                "log_min_messages='debug5'", "debug_print_parse=on", "debug_print_plan=on", "debug_print_rewritten=on"))
            execute(db, "ALTER DATABASE " + db + " SET " + setting);
        String marker = "sd-log-" + UUID.randomUUID();
        execute(db, "DO $$ BEGIN RAISE LOG '" + marker + "-before'; END $$");
        Map<String, String> environment = finalEnvironment();
        switch (failure) {
            case "constraint" -> execute(db, "ALTER TABLE public.users ADD CONSTRAINT sd_reject_learner CHECK (display_name <> 'Learner Demo')");
            case "statement" -> execute(db, "CREATE FUNCTION public.sd_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "RAISE EXCEPTION 'injected statement failure' USING DETAIL = NEW.password_hash; END $$; "
                    + "CREATE TRIGGER sd_failure BEFORE INSERT ON public.users FOR EACH ROW EXECUTE FUNCTION public.sd_failure()");
            case "unprivileged" -> {
                String role = unprivilegedRole();
                execute(db, "GRANT EXECUTE ON FUNCTION pg_control_system() TO " + role);
                environment.put("PGUSER", role);
            }
            default -> { }
        }
        Run run = psql(db, USERS, environment);
        if (failure.equals("success")) assertSucceeded(run);
        else { assertEquals(3, run.exitCode(), "script must stop on injected/permission error"); assertRedacted(run); }
        assertEquals(List.of(failure.equals("success") ? "6" : "0"), rows(db, "SELECT count(*) FROM public.users"));
        execute(db, "DO $$ BEGIN RAISE LOG '" + marker + "-after'; END $$");
        String logs = POSTGRES.getLogs();
        assertTrue(logs.contains(marker + "-before") && logs.contains(marker + "-after"), "actual server-log oracle is not vacuous");
        assertFalse(discloses(logs), "PostgreSQL logs must contain no registered password, hash, salt or checksum (logs withheld)");
        assertEquals(List.of("all|on|debug5|verbose"), rows(db, "SELECT current_setting('log_statement'), current_setting('log_duration'), "
                + "current_setting('log_min_messages'), current_setting('log_error_verbosity')"));
        if (failure.equals("success")) {
            // Same connection, not only a new session: LOCAL settings revert after COMMIT.
            ExecResult result = POSTGRES.execInContainer(ExecConfig.builder().command(new String[] {
                    "psql", "-X", "-q", "-U", POSTGRES.getUsername(), "-d", db, "-f", "/seed/" + USERS,
                    "-c", "SHOW log_statement", "-c", "SHOW log_min_messages"}).envVars(environment).build());
            Run rerun = new Run(result.getExitCode(), result.getStdout(), result.getStderr());
            assertSucceeded(rerun);
            assertTrue(rerun.stdout().contains("all") && rerun.stdout().contains("debug5"));
            assertFalse(discloses(POSTGRES.getLogs()), "same-session rerun server diagnostics (withheld)");
        }
    }

    @Test void privateConsumerPreservesPreexistingSharedIdentitiesAndAttestation() throws Exception {
        String db = POSTGRES.getDatabaseName();
        assertEquals(List.of("0|0"), rows(db, "SELECT (SELECT count(*) FROM public.users WHERE id IN ('" + INSTRUCTOR + "','" + LEARNER
                + "')), (SELECT count(*) FROM public.courses WHERE id='" + PYTHON + "')"), "sentinel admission");
        UUID enrollment = UUID.randomUUID(), material = UUID.randomUUID(), session = UUID.randomUUID(), token = UUID.randomUUID();
        UUID chapter = final5("course/python/chapter/1"), lesson = final5("course/python/chapter/1/lesson/1");
        String previous = targetSetting(db);
        boolean inserted = false, settingAttempted = false;
        try {
            try (Connection c = connect(db); var statement = c.createStatement()) {
                c.setAutoCommit(false);
                statement.execute("INSERT INTO public.users VALUES ('" + INSTRUCTOR + "','instructor@example.test','sentinel-only','Preexisting','instructor','active'),"
                        + "('" + LEARNER + "','learner@example.test','sentinel-only','Foreign Learner','learner','active')");
                statement.execute("INSERT INTO public.courses VALUES ('" + PYTHON + "','" + INSTRUCTOR + "','Foreign Course','Foreign description',NULL,'published',false,NULL)");
                statement.execute("INSERT INTO public.chapters VALUES ('" + chapter + "','" + PYTHON + "','Foreign Chapter',1)");
                statement.execute("INSERT INTO public.lessons VALUES ('" + lesson + "','" + chapter + "','Foreign Lesson',NULL,1,'published')");
                statement.execute("INSERT INTO public.learning_materials VALUES ('" + material + "','" + lesson + "','Foreign Material','text','published','Foreign text',NULL,NULL,NULL)");
                statement.execute("INSERT INTO public.enrollments(id,user_id,course_id,status) VALUES ('" + enrollment + "','" + LEARNER + "','" + PYTHON + "','active')");
                statement.execute("INSERT INTO public.refresh_sessions(id,user_id,created_at,expires_at) VALUES ('" + session + "','" + INSTRUCTOR + "',now(),now()+interval '1 day')");
                statement.execute("INSERT INTO public.refresh_tokens(id,session_id,token_digest,issued_at,expires_at) VALUES ('" + token + "','" + session + "',decode(repeat('00',32),'hex'),now(),now()+interval '1 hour')");
                c.commit(); inserted = true;
            }
            settingAttempted = true; attest(db, "preexisting-sentinel");
            String before = snapshot(db);
            List<String> auth = rows(db, "SELECT id,xmin::text FROM public.refresh_sessions UNION ALL SELECT id,xmin::text FROM public.refresh_tokens ORDER BY id");
            seededRecordsAreServedByTheAuthAndCatalogueConsumers();
            assertSafeEquals(before, snapshot(db), "preexisting shared rows and xmin, including foreign Enrollment/material");
            assertEquals(auth, rows(db, "SELECT id,xmin::text FROM public.refresh_sessions UNION ALL SELECT id,xmin::text FROM public.refresh_tokens ORDER BY id"), "preexisting session/token identities and versions");
            assertEquals("preexisting-sentinel", targetSetting(db));
        } finally {
            try {
                if (inserted) guarded("owned shared sentinel cleanup", () -> {
                    try (Connection c = connect(db); var statement = c.createStatement()) {
                        c.setAutoCommit(false);
                        statement.execute("DELETE FROM public.refresh_tokens WHERE id='" + token + "'");
                        statement.execute("DELETE FROM public.refresh_sessions WHERE id='" + session + "'");
                        statement.execute("DELETE FROM public.enrollments WHERE id='" + enrollment + "'");
                        statement.execute("DELETE FROM public.learning_materials WHERE id='" + material + "'");
                        statement.execute("DELETE FROM public.lessons WHERE id='" + lesson + "'");
                        statement.execute("DELETE FROM public.chapters WHERE id='" + chapter + "'");
                        statement.execute("DELETE FROM public.courses WHERE id='" + PYTHON + "'");
                        statement.execute("DELETE FROM public.users WHERE id IN ('" + INSTRUCTOR + "','" + LEARNER + "')");
                        c.commit();
                    }
                });
            } finally { if (settingAttempted) restoreTargetSetting(db, previous); }
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void partialConsumerLoadsOwnOnlyTheirDatabaseAndBucket(int committedFiles) throws Exception {
        String shared = snapshot(POSTGRES.getDatabaseName());
        String setting = targetSetting(POSTGRES.getDatabaseName());
        String name, bucket;
        try (ConsumerFixture fixture = ConsumerFixture.open()) {
            name = fixture.database; bucket = fixture.storage.bucket;
            List<String> files = List.of(USERS, COURSES, GRAPH, PROFILES);
            for (int i = 0; i < committedFiles; i++) assertFinalLoaded(name, files.get(i));
            // An unreferenced object is still owned by this private bucket and must be removed.
            try (S3Client admin = storageAdministrator()) {
                admin.putObject(request -> request.bucket(bucket).key("materials/" + UUID.randomUUID()),
                        software.amazon.awssdk.core.sync.RequestBody.fromString("owned orphan sentinel"));
            }
        }
        try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertEquals(-1, databaseOid(admin, name)); }
        try (S3Client admin = storageAdministrator()) { assertTrue(admin.listBuckets().buckets().stream().noneMatch(row -> bucket.equals(row.name()))); }
        assertSafeEquals(shared, snapshot(POSTGRES.getDatabaseName()), "shared database after partial private load");
        assertEquals(setting, targetSetting(POSTGRES.getDatabaseName()));
    }

    @Test void outerConnectionFailureInvalidatesLaterSharedTestsAndPreservesOwnedDatabases() throws Exception {
        String db = freshDatabase();
        assertThrows(IllegalStateException.class, () -> dropOwnedDatabases(() -> {
            Connection closed = connect(POSTGRES.getDatabaseName()); closed.close(); return closed;
        }));
        Throwable simulated = isolationLoss();
        try {
            assertNotNull(simulated);
            assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
            try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertEquals(CREATED.get(db).longValue(), databaseOid(admin, db)); }
        } finally { assertTrue(clearSimulatedIsolationLoss(simulated)); }
    }

    @Test void databaseReplacementAtOuterTeardownIsPreservedAndFailsClosed() throws Exception {
        String db = freshDatabase(), renamed = db + "_old";
        long original = CREATED.get(db), replacement;
        try (Connection admin = connect(POSTGRES.getDatabaseName()); var statement = admin.createStatement()) {
            statement.execute("ALTER DATABASE " + db + " RENAME TO " + renamed);
            statement.execute("CREATE DATABASE " + db);
            replacement = databaseOid(admin, db);
        }
        Throwable simulated = null;
        try {
            assertThrows(IllegalStateException.class, () -> dropOwnedDatabases(() -> connect(POSTGRES.getDatabaseName())));
            simulated = isolationLoss();
            assertNotNull(simulated);
            assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
            try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertEquals(replacement, databaseOid(admin, db)); }
        } finally {
            if (simulated != null) assertTrue(clearSimulatedIsolationLoss(simulated));
            try (Connection admin = connect(POSTGRES.getDatabaseName()); var statement = admin.createStatement()) {
                // This replacement was created above by the probe, never by an external fixture.
                dropOwnedDatabase(admin, db, replacement);
                statement.execute("ALTER DATABASE " + renamed + " RENAME TO " + db);
                synchronized (CREATED) { CREATED.put(db, original); }
            }
        }
    }

    @Test void minioTeardownFailurePreservesOtherBucketsAndInvalidatesLaterTests() throws Exception {
        StorageFixture owned = new StorageFixture(), other = new StorageFixture();
        owned.provision(); other.provision();
        try {
            try (S3Client admin = storageAdministrator()) {
                admin.putObject(r -> r.bucket(owned.bucket).key("materials/" + UUID.randomUUID()),
                        software.amazon.awssdk.core.sync.RequestBody.fromString("owned sentinel"));
                assertThrows(IllegalStateException.class, () -> owned.close(() -> admin.deleteBucket(r -> r.bucket(owned.bucket))));
            }
            Throwable simulated = isolationLoss();
            try {
                assertNotNull(simulated);
                assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
                try (S3Client admin = storageAdministrator()) {
                    assertTrue(admin.listBuckets().buckets().stream().anyMatch(r -> other.bucket.equals(r.name())));
                    assertEquals(1, admin.listObjectsV2(r -> r.bucket(owned.bucket)).keyCount());
                }
            } finally { assertTrue(clearSimulatedIsolationLoss(simulated)); }
            owned.close();
            try (S3Client admin = storageAdministrator()) { assertTrue(admin.listBuckets().buckets().stream().anyMatch(r -> other.bucket.equals(r.name()))); }
        } finally { owned.close(); other.close(); }
    }

    @Test void minioBootstrapRefusesAnExistingBucketAndPreservesIt() throws Exception {
        StorageFixture proposed = new StorageFixture();
        try (S3Client admin = storageAdministrator()) {
            admin.createBucket(r -> r.bucket(proposed.bucket));
            try {
                assertThrows(IllegalStateException.class, proposed::provision);
                Throwable simulated = isolationLoss();
                try {
                    assertNotNull(simulated);
                    assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
                    assertFalse(proposed.admitted);
                    assertTrue(admin.listBuckets().buckets().stream().anyMatch(r -> proposed.bucket.equals(r.name())));
                    proposed.close(); // No admission means no right to delete this bucket.
                    assertTrue(admin.listBuckets().buckets().stream().anyMatch(r -> proposed.bucket.equals(r.name())));
                } finally { assertTrue(clearSimulatedIsolationLoss(simulated)); }
            } finally { admin.deleteBucket(r -> r.bucket(proposed.bucket)); }
        }
    }

    @Test void isolationFailuresNeverRetainSensitiveProviderDiagnostics() {
        String sentinel = secret("failure-" + password());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> guarded("injected teardown", () -> { throw new SQLException(sentinel); }));
        try { assertNoDisclosure(failure, List.of(sentinel)); assertNull(failure.getCause()); }
        finally { assertTrue(clearSimulatedIsolationLoss(failure)); }
    }

    private static String targetSetting(String db) throws SQLException {
        List<String> values = rows(db, "SELECT substr(value, length('codemastery.seed_target=')+1) FROM pg_db_role_setting "
                + "CROSS JOIN LATERAL unnest(setconfig) value WHERE setdatabase=(SELECT oid FROM pg_database WHERE datname=current_database()) "
                + "AND setrole=0 AND value LIKE 'codemastery.seed\\_target=%'");
        return values.isEmpty() ? null : values.getFirst();
    }

    private static void restoreTargetSetting(String db, String previous) {
        guarded("shared sentinel attestation restore", () -> {
            try (Connection c = connect(db)) {
                if (previous == null) c.createStatement().execute("ALTER DATABASE " + db + " RESET codemastery.seed_target");
                else try (var q = c.prepareStatement("SELECT format('ALTER DATABASE %I SET codemastery.seed_target=%L',current_database(),?)")) {
                    q.setString(1, previous);
                    try (var r = q.executeQuery()) { r.next(); c.createStatement().execute(r.getString(1)); }
                }
            }
        });
    }
    private static final String PDF_TITLE = "Slides: Variable Declaration and Naming Conventions";

    private static String readmeProcedure() throws Exception {
        String text = Files.readString(Path.of("..", "sample-data", "README.md"));
        int marker = text.indexOf("<!-- SD-F-IR-03-05:");
        int begin = text.indexOf("python3 - <<'PY'\n", marker) + "python3 - <<'PY'\n".length();
        int end = text.indexOf("\nPY\n```", begin);
        assertTrue(marker >= 0 && begin > marker && end > begin, "executable README procedure markers");
        return text.substring(begin, end);
    }

    private static Process startReadme(String api, String action, String selected, String instructor, String p1, Path temporary) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("python3", "-").directory(Path.of("..").toFile());
        builder.environment().putAll(Map.of("CODEMASTERY_SEED_API_URL", api, "CODEMASTERY_SEED_PDF_ACTION", action,
                "CODEMASTERY_SEED_INSTRUCTOR_PASSWORD", instructor, "CODEMASTERY_SEED_P1_PASSWORD", p1,
                "TMPDIR", temporary.toAbsolutePath().toString(), "HTTP_PROXY", "http://127.0.0.1:1"));
        if (selected != null) builder.environment().put("CODEMASTERY_SEED_MATERIAL_ID", selected);
        else builder.environment().remove("CODEMASTERY_SEED_MATERIAL_ID");
        Process child = builder.start();
        try {
            String argv = child.info().command().orElse("") + " " + String.join(" ", child.info().arguments().orElse(new String[0]));
            assertFalse(discloses(argv), "README subprocess arguments contain no credential");
            try (var input = child.getOutputStream()) { input.write(readmeProcedure().getBytes(StandardCharsets.UTF_8)); }
            return child;
        } catch (Exception | AssertionError failure) {
            child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); throw failure;
        }
    }

    private static Run runReadme(String api, String action, String selected, String instructor, String p1, Path temporary) throws Exception {
        Process child = startReadme(api, action, selected, instructor, p1, temporary);
        try {
            assertTrue(child.waitFor(90, TimeUnit.SECONDS), "bounded README procedure");
            Run run = new Run(child.exitValue(), new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            assertRedacted(run);
            return run;
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS), "join README writer before fixture cleanup"); }
        }
    }

    /** HTTP fault oracle for the literal README client; real CT09 is checked separately below. */
    private static final class ProcedureServer implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;
        final String instructorPassword, p1Password;
        final String instructorToken = secret("header." + password() + ".signature");
        final String p1Token = secret("header." + password() + ".signature");
        final String id = UUID.randomUUID().toString();
        final AtomicInteger uploads = new AtomicInteger(), publications = new AtomicInteger(), redirects = new AtomicInteger();
        final java.util.concurrent.CountDownLatch uploadReceived = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releaseUpload = new java.util.concurrent.CountDownLatch(1);
        volatile String fault;
        volatile boolean uploaded, published, passwordsPreserved = true;

        ProcedureServer(String fault, String instructor, String p1) throws Exception {
            this.fault = fault; instructorPassword = instructor; p1Password = p1;
            uploaded = fault.equals("existing") || fault.equals("ambiguous");
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try { handle(exchange); }
                catch (Exception failure) {
                    try { respond(exchange, 500, "{}".getBytes(StandardCharsets.UTF_8), "application/json"); }
                    catch (Exception ignored) { exchange.close(); }
                }
            });
            server.start();
        }

        String api() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        Map<String, Object> metadata() { return Map.of("id", id, "title", PDF_TITLE, "materialType", "file", "publicationStatus", published ? "published" : "hidden"); }
        void json(com.sun.net.httpserver.HttpExchange exchange, int code, Object value) throws Exception {
            respond(exchange, code, tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(value), "application/json");
        }
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception {
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
            if (path.equals("/redirect-target")) { redirects.incrementAndGet(); json(exchange, 200, Map.of()); return; }
            if (path.equals("/api/auth/login")) {
                var value = tools.jackson.databind.json.JsonMapper.builder().build().readTree(exchange.getRequestBody().readAllBytes());
                boolean instructor = value.path("email").asString().equals("instructor@example.test");
                passwordsPreserved &= value.path("password").asString().equals(instructor ? instructorPassword : p1Password);
                if (fault.equals("login-401") || fault.equals("p1-login-401") && !instructor) { json(exchange, 401, Map.of()); return; }
                if (fault.equals("login-empty")) { respond(exchange, 200, new byte[0], "application/json"); return; }
                if (fault.equals("login-malformed")) { respond(exchange, 200, instructorPassword.getBytes(StandardCharsets.UTF_8), "application/json"); return; }
                if (fault.equals("login-duplicate")) { respond(exchange, 200, "{\"accessToken\":\"a\",\"accessToken\":\"b\"}".getBytes(StandardCharsets.UTF_8), "application/json"); return; }
                if (fault.equals("login-redirect")) { redirect(exchange); return; }
                json(exchange, 200, Map.of("accessToken", instructor ? instructorToken : p1Token, "user", Map.of(
                        "id", instructor ? INSTRUCTOR.toString() : final5("user/p1").toString(), "role", instructor ? "Instructor" : "Learner")));
                return;
            }
            if (path.endsWith("/file-materials") && method.equals("POST")) {
                exchange.getRequestBody().readAllBytes(); uploads.incrementAndGet();
                if (fault.equals("upload-400")) { json(exchange, 400, Map.of()); return; }
                uploaded = true;
                if (fault.equals("upload-wait")) {
                    uploadReceived.countDown();
                    if (!releaseUpload.await(20, TimeUnit.SECONDS)) { exchange.close(); return; }
                }
                switch (fault) {
                    case "upload-lost-response" -> exchange.close();
                    case "upload-500" -> json(exchange, 500, Map.of());
                    case "upload-redirect" -> redirect(exchange);
                    case "upload-empty" -> respond(exchange, 201, new byte[0], "application/json");
                    case "upload-malformed" -> respond(exchange, 201, p1Password.getBytes(StandardCharsets.UTF_8), "application/json");
                    case "upload-missing-id" -> json(exchange, 201, Map.of("materialType", "file"));
                    case "upload-bad-id" -> json(exchange, 201, Map.of("id", "invalid", "title", PDF_TITLE, "materialType", "file", "publicationStatus", "hidden"));
                    case "upload-wrong-kind" -> json(exchange, 201, Map.of("id", id, "title", PDF_TITLE, "materialType", "text", "publicationStatus", "hidden"));
                    default -> json(exchange, 201, metadata());
                }
                return;
            }
            if (path.endsWith("/publication") && method.equals("PUT")) {
                exchange.getRequestBody().readAllBytes(); publications.incrementAndGet();
                if (fault.equals("publication-500")) { json(exchange, 500, Map.of()); return; }
                published = true;
                if (fault.equals("publication-malformed")) respond(exchange, 200, new byte[0], "application/json");
                else json(exchange, 200, metadata());
                return;
            }
            if (path.endsWith("/file")) {
                boolean owner = ("Bearer " + instructorToken).equals(exchange.getRequestHeaders().getFirst("Authorization"));
                if (fault.equals("owner-download-500") && owner || fault.equals("learner-download-403") && !owner) { json(exchange, owner ? 500 : 403, Map.of()); return; }
                byte[] pdf = Files.readAllBytes(SLIDES);
                if (fault.equals("digest-mismatch")) pdf[20] ^= 1;
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                if (!fault.equals("headers-invalid")) exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"sample-slides.pdf\"; filename*=UTF-8''sample-slides.pdf");
                respond(exchange, 200, pdf, "application/pdf"); return;
            }
            if (method.equals("GET")) {
                if (fault.equals("owned-malformed")) { json(exchange, 200, Map.of()); return; }
                List<Object> materials = new ArrayList<>();
                if (uploaded) materials.add(metadata());
                if (fault.equals("ambiguous")) materials.add(Map.of("id", UUID.randomUUID().toString(), "title", PDF_TITLE, "materialType", "file", "publicationStatus", "hidden"));
                json(exchange, 200, Map.of("id", final5("course/python/chapter/1/lesson/1").toString(), "materials", materials));
                return;
            }
            json(exchange, 404, Map.of());
        }
        void redirect(com.sun.net.httpserver.HttpExchange exchange) throws Exception {
            exchange.getResponseHeaders().set("Location", api() + "/redirect-target"); json(exchange, 302, Map.of());
        }
        private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, byte[] bytes, String type) throws Exception {
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
            exchange.close();
        }
        @Override public void close() { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"login-401", "p1-login-401", "login-empty", "login-malformed", "login-duplicate", "login-redirect", "owned-malformed",
            "existing", "ambiguous", "upload-400", "upload-500", "upload-empty", "upload-malformed", "upload-missing-id", "upload-bad-id",
            "upload-wrong-kind", "upload-redirect", "upload-lost-response", "publication-500", "publication-malformed",
            "owner-download-500", "learner-download-403", "digest-mismatch", "headers-invalid"})
    void readmeProcedureFailsClosedWithoutMutationReplayOrLocalFileWrites(String fault) throws Exception {
        String instructor = secret("  Instructor-\"quoted\\-é  "), p1 = secret("\tP1-\\quoted\"-日本語  ");
        Path directory = Files.createTempDirectory(Path.of("target"), "sd-procedure-");
        Path target = directory.resolve("preserve"), link = directory.resolve("l01-slides.pdf");
        Files.writeString(target, "owned sentinel"); Files.createSymbolicLink(link, target.getFileName());
        try (ProcedureServer server = new ProcedureServer(fault, instructor, p1)) {
            Run run = runReadme(server.api(), "upload", null, instructor, p1, directory);
            assertNotEquals(0, run.exitCode(), "HTTP/parsing/digest fault must fail the literal procedure");
            assertTrue(server.passwordsPreserved, "JSON preserves exact whitespace, quotes, backslashes and Unicode");
            assertEquals(0, server.redirects.get(), "no credential-bearing redirect");
            assertTrue(server.uploads.get() <= 1, "no automatic mutation retry");
            assertEquals("owned sentinel", Files.readString(target));
            assertTrue(Files.isSymbolicLink(link), "pre-existing symlink untouched");
            try (var names = Files.list(directory)) { assertEquals(2, names.count(), "procedure created no secret/download/temp files"); }
            if (fault.startsWith("login") || fault.equals("p1-login-401") || fault.startsWith("owned") || fault.equals("existing") || fault.equals("ambiguous"))
                assertEquals(0, server.uploads.get());
            if (fault.startsWith("upload")) assertEquals(0, server.publications.get());
            if (server.uploaded && !fault.equals("existing") && !fault.equals("ambiguous")) {
                // A stopped/unknown operation is recovered through an explicit existing identity, never another POST.
                server.fault = "none";
                Run resumed = runReadme(server.api(), "resume", server.id, instructor, p1, directory);
                assertSucceeded(resumed);
                assertEquals(1, server.uploads.get());
                assertTrue(resumed.stdout().contains(SLIDES_SHA256));
            }
        } finally { Files.deleteIfExists(link); Files.deleteIfExists(target); Files.deleteIfExists(directory); }
    }

    @Test void readmeProcedureSupportsInspectAndConcurrentExistingMaterialVerification() throws Exception {
        String password = secret("  concurrent-\"\\é-日本語  ");
        Path directory = Files.createTempDirectory(Path.of("target"), "sd-procedure-");
        try (ProcedureServer server = new ProcedureServer("none", password, password)) {
            assertSucceeded(runReadme(server.api(), "inspect", null, password, password, directory));
            assertEquals(0, server.uploads.get());
            assertSucceeded(runReadme(server.api(), "upload", null, password, password, directory));
            var pool = Executors.newFixedThreadPool(2);
            try {
                var one = pool.submit(() -> runReadme(server.api(), "resume", server.id, password, password, directory));
                var two = pool.submit(() -> runReadme(server.api(), "resume", server.id, password, password, directory));
                assertSucceeded(one.get(30, TimeUnit.SECONDS)); assertSucceeded(two.get(30, TimeUnit.SECONDS));
                assertEquals(1, server.uploads.get()); assertEquals(1, server.publications.get());
            } finally { shutdownPool(pool); }
            try (var names = Files.list(directory)) { assertEquals(0, names.count()); }
        } finally { Files.deleteIfExists(directory); }
    }

    @Test void literalReadmeProcedureProvisionsAndResumesAgainstRealCt09() throws Exception {
        String instructor = secret("  real-\"\\é-Instructor-" + password() + "  ");
        String learner = secret("\tP1-\"\\日本語-" + password() + "  ");
        Path directory = Files.createTempDirectory(Path.of("target"), "sd-real-procedure-");
        try (ConsumerFixture fixture = ConsumerFixture.open()) {
            Map<String, String> inputs = finalEnvironment();
            inputs.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", secret(BCRYPT.encode(instructor)));
            inputs.put(hashVariable("p1"), secret(BCRYPT.encode(learner)));
            for (String file : List.of(USERS, COURSES, GRAPH, PROFILES)) assertSucceeded(psql(fixture.database, file, inputs));
            Run first = runReadme(fixture.api(), "upload", null, instructor, learner, directory);
            assertSucceeded(first); assertTrue(first.stdout().contains(SLIDES_SHA256));
            String id = rows(fixture.database, "SELECT id FROM public.learning_materials WHERE material_type='file'").getFirst();
            Run inspect = runReadme(fixture.api(), "inspect", null, instructor, learner, directory);
            assertSucceeded(inspect); assertTrue(inspect.stdout().contains(id));
            assertSucceeded(runReadme(fixture.api(), "resume", id, instructor, learner, directory));
            assertNotEquals(0, runReadme(fixture.api(), "upload", null, instructor, learner, directory).exitCode());
            assertEquals(List.of("1"), rows(fixture.database, "SELECT count(*) FROM public.learning_materials WHERE material_type='file'"));
            try (var names = Files.list(directory)) { assertEquals(0, names.count()); }
        } finally { Files.deleteIfExists(directory); }
    }

    @Test void contextShutdownFailurePreservesPrivateResourcesAndInvalidatesLaterTests() throws Exception {
        ConsumerFixture fixture = ConsumerFixture.open();
        try {
            assertThrows(IllegalStateException.class, () -> fixture.close(() -> {
                try (Connection c = connect(POSTGRES.getDatabaseName())) { c.close(); c.createStatement(); }
            }));
            Throwable simulated = isolationLoss();
            try {
                assertNotNull(simulated);
                assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
                assertTrue(fixture.application.isActive());
                try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertEquals(CREATED.get(fixture.database).longValue(), databaseOid(admin, fixture.database)); }
                try (S3Client admin = storageAdministrator()) { assertTrue(admin.listBuckets().buckets().stream().anyMatch(r -> fixture.storage.bucket.equals(r.name()))); }
            } finally { assertTrue(clearSimulatedIsolationLoss(simulated)); }
        } finally { fixture.close(); }
    }
    @Test void documentedInteractiveHashBlocksPreserveExactCt01Passwords() throws Exception {
        String readme = Files.readString(Path.of("..", "sample-data", "README.md"));
        int generator = readme.indexOf("seed_hash() {");
        String function = readme.substring(generator, readme.indexOf("\n```", generator));
        String interim = bashBlock(readme, "printf 'Instructor password:");
        String added = bashBlock(readme, "printf 'Administrator password:");
        List<String> passwords = List.of("  Instructor-Exact-Whitespace  ", " ".repeat(15),
                "Admin-\"quoted\\exact-password", "日本語-é-P1-Exact-Password", "😀".repeat(15), "é".repeat(36));
        for (String value : passwords) secret(value);
        Path directory = Files.createTempDirectory(Path.of("target"), "sd-password-procedure-");
        Path classpath = directory.resolve("classpath");
        Files.writeString(classpath, System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        String cp = "'" + classpath.toAbsolutePath().toString().replace("'", "'\\''") + "'";
        function = function.replace("backend/target/seed-hash.classpath", cp);
        StringBuilder verify = new StringBuilder("\njshell --feedback silent --class-path \"$(cat " + cp + ")\" - <<'SD_VERIFY'\n"
                + "var e = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();\n");
        List<String> names = List.of("INSTRUCTOR", "LEARNER", "ADMIN", "P1", "P2", "P3");
        for (int i = 0; i < names.size(); i++) verify.append("System.out.println(\"PASSWORD_ROUND_TRIP=\" + e.matches(System.getenv(\"SD_EXPECTED_")
                .append(i).append("\"), System.getenv(\"CODEMASTERY_SEED_").append(names.get(i)).append("_PASSWORD_HASH\")));\n");
        verify.append("/exit\nSD_VERIFY\n");
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", "set -eu\n" + function + "\n" + interim + "\n" + added + verify)
                .directory(Path.of("..").toFile());
        builder.environment().remove("BASH_ENV"); builder.environment().remove("SHELLOPTS");
        for (int i = 0; i < passwords.size(); i++) builder.environment().put("SD_EXPECTED_" + i, passwords.get(i));
        Process child = builder.start();
        try {
            try (var input = child.getOutputStream()) { input.write((String.join("\n", passwords) + "\n").getBytes(StandardCharsets.UTF_8)); }
            assertTrue(child.waitFor(90, TimeUnit.SECONDS), "bounded literal password procedure");
            Run result = new Run(child.exitValue(), new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            assertSucceeded(result);
            assertEquals(6, result.stdout().lines().filter(line -> line.equals("PASSWORD_ROUND_TRIP=true")).count(), "every exact password matches its generated hash");
            assertFalse(result.stdout().contains("PASSWORD_ROUND_TRIP=false"));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS)); }
            Files.deleteIfExists(classpath); Files.deleteIfExists(directory);
        }
    }

    private static String bashBlock(String readme, String marker) {
        int position = readme.indexOf(marker);
        int start = readme.lastIndexOf("```bash\n", position) + "```bash\n".length();
        return readme.substring(start, readme.indexOf("```", position));
    }
    @Test void partialConsumerBootstrapFailureRetainsOwnershipForSafeOuterCleanup() throws Exception {
        String before = snapshot(POSTGRES.getDatabaseName());
        ConsumerFixture[] created = new ConsumerFixture[1];
        assertThrows(IllegalStateException.class, () -> ConsumerFixture.open(fixture -> {
            created[0] = fixture;
            execute(fixture.database, "SELECT 1/0");
        }));
        Throwable simulated = isolationLoss();
        try {
            assertNotNull(created[0]); assertNotNull(simulated);
            assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
            assertTrue(ACTIVE_CONSUMERS.contains(created[0]));
            try (Connection admin = connect(POSTGRES.getDatabaseName())) {
                assertEquals(CREATED.get(created[0].database).longValue(), databaseOid(admin, created[0].database));
            }
            assertSafeEquals(before, snapshot(POSTGRES.getDatabaseName()), "shared state after failed private bootstrap");
        } finally { assertTrue(clearSimulatedIsolationLoss(simulated)); created[0].close(); }
        assertFalse(ACTIVE_CONSUMERS.contains(created[0]));
    }

    @Test void interruptedReadmeUploadIsJoinedAndRecoveredWithoutASecondPost() throws Exception {
        String password = secret("  interrupted-\"\\é-password  ");
        Path directory = Files.createTempDirectory(Path.of("target"), "sd-interrupted-procedure-");
        try (ProcedureServer server = new ProcedureServer("upload-wait", password, password)) {
            Process child = startReadme(server.api(), "upload", null, password, password, directory);
            try {
                assertTrue(server.uploadReceived.await(15, TimeUnit.SECONDS), "upload reached controlled server barrier");
                child.destroy();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS), "interrupted caller joined before cleanup/recovery");
                assertNotEquals(0, child.exitValue());
                assertEquals(1, server.uploads.get()); assertEquals(0, server.publications.get());
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS)); }
                server.releaseUpload.countDown();
            }
            server.fault = "none";
            assertSucceeded(runReadme(server.api(), "resume", server.id, password, password, directory));
            assertEquals(1, server.uploads.get());
            try (var names = Files.list(directory)) { assertEquals(0, names.count()); }
        } finally { Files.deleteIfExists(directory); }
    }

    @Test void unacknowledgedDatabaseCreationIsPreservedAndOuterTeardownCannotSucceed() throws Exception {
        String[] attempted = new String[1];
        assertThrows(IllegalStateException.class, () -> freshDatabase(database -> {
            attempted[0] = database;
            throw new SQLException("injected acknowledgement loss before catalog identity");
        }));
        Throwable simulated = isolationLoss();
        try {
            assertNotNull(attempted[0]); assertTrue(PENDING_DATABASES.contains(attempted[0]));
            assertFalse(CREATED.containsKey(attempted[0]));
            assertThrows(IllegalStateException.class, this::requireSharedPostgresqlIsolation);
            try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertTrue(databaseOid(admin, attempted[0]) > 0); }
            assertThrows(IllegalStateException.class, () -> dropOwnedDatabases(() -> connect(POSTGRES.getDatabaseName())));
            try (Connection admin = connect(POSTGRES.getDatabaseName())) { assertTrue(databaseOid(admin, attempted[0]) > 0, "unproven resource was not dropped"); }
        } finally {
            // Only this qualification test observed its successful CREATE checkpoint; production cleanup never guesses.
            try (Connection admin = connect(POSTGRES.getDatabaseName())) {
                synchronized (CREATED) { CREATED.put(attempted[0], databaseOid(admin, attempted[0])); }
            }
            PENDING_DATABASES.remove(attempted[0]);
            assertTrue(clearSimulatedIsolationLoss(simulated));
        }
    }
}
