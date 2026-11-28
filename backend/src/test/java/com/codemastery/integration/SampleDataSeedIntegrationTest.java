package com.codemastery.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * SD01/SD02/SD03 seed SQL executed by real psql against disposable, Flyway-migrated PostgreSQL databases,
 * and read back through the real Auth, catalogue and skill-graph REST consumers. Never touches a non-test database.
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
    }
    private static final AtomicInteger DATABASES = new AtomicInteger();
    private static final List<String> CREATED = new ArrayList<>();
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

    @Autowired WebApplicationContext context;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("JWT_SECRET", () -> KEY); }

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
                        + "adaptive_enabled, adaptive_settings FROM public.courses WHERE id <> '" + PYTHON + "' ORDER BY title"));
        assertEquals(List.of(uuid5("course/intro/chapter/1") + "|" + INTRO + "|Getting Started|1",
                        uuid5("course/practice/chapter/1") + "|" + PRACTICE + "|Getting Started|1"),
                rows(db, "SELECT ch.id, ch.course_id, ch.title, ch.position FROM public.chapters ch "
                        + "JOIN public.courses c ON c.id = ch.course_id WHERE c.id <> '" + PYTHON + "' ORDER BY c.title"));
        List<String> lessons = new ArrayList<>();
        for (String key : List.of("course/intro", "course/practice"))
            for (int position = 1; position <= 2; position++)
                lessons.add(uuid5(key + "/chapter/1/lesson/" + position) + "|" + uuid5(key + "/chapter/1") + "|"
                        + (position == 1 ? "Concepts" : "Practice") + "|NULL|" + position + "|published");
        assertEquals(lessons, rows(db, "SELECT l.id, l.chapter_id, l.title, l.content_markdown, l.position, l.publication_status "
                + "FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id JOIN public.courses c ON c.id = ch.course_id "
                + "WHERE c.id <> '" + PYTHON + "' ORDER BY c.title, l.position"));
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
                        + "JOIN public.courses c ON c.id = ch.course_id WHERE c.id <> '" + PYTHON + "' ORDER BY c.title, l.position, m.title"));
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
        assertSucceeded(psqlWithVariables(db, USERS));
        assertSucceeded(psqlWithVariables(db, COURSES));
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
            case "cost-12-hash" -> environment.put("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH",
                    secret(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 12).encode(INSTRUCTOR_PASSWORD)));
            case "truncated-hash" -> environment.put("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH.substring(0, 59));
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

    // SD03 (ADR013 §4.2, CT15.F-adaptive): course/python, its skill graph and the three profile Enrollments.

    @Test void loadsTheApprovedAdaptiveDatasetAndReloadsItUnchanged() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        insertProfileLearners(db);
        assertLoaded(db, GRAPH);
        assertLoaded(db, PROFILES);

        assertEquals(List.of(PYTHON + "|" + INSTRUCTOR + "|Python Fundamentals|published|true|No prerequisites"),
                rows(db, "SELECT id, instructor_id, title, publication_status, adaptive_enabled, prerequisite_description "
                        + "FROM public.courses WHERE id = '" + PYTHON + "'"));
        assertEquals(List.of("{\"weak\": {\"numerator\": 1, \"denominator\": 2}, \"mastered\": {\"numerator\": 4, \"denominator\": 5}, "
                        + "\"minimumQuestionsPerSkill\": 6}"),
                rows(db, "SELECT adaptive_settings::text FROM public.courses WHERE id = '" + PYTHON + "'"));

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

        List<String> enrollments = new ArrayList<>();
        for (String profile : PROFILE_KEYS)
            enrollments.add(final5("user/" + profile + "/enrollment/course/python") + "|" + final5("user/" + profile) + "|active|NULL|NULL");
        assertEquals(enrollments.stream().sorted().toList(), rows(db, "SELECT id, user_id, status, resume_lesson_id, last_visited_at "
                + "FROM public.enrollments WHERE course_id = '" + PYTHON + "' ORDER BY id"));
        assertEquals(List.of("0|0|0|0"), rows(db, "SELECT (SELECT count(*) FROM public.learner_skill_masteries), "
                + "(SELECT count(*) FROM public.lesson_progress), (SELECT count(*) FROM public.path_recommendations), "
                + "(SELECT count(*) FROM public.attempts)"), "no mastery, progress, recommendation or attempt is seeded");

        String loaded = snapshot(db);
        for (String file : List.of(USERS, COURSES, GRAPH, PROFILES)) assertLoaded(db, file);
        for (String file : List.of(COURSES, GRAPH, PROFILES)) assertSucceeded(psqlWithVariables(db, file));
        assertSafeEquals(loaded, snapshot(db), "row values and row versions after identical reruns");
    }

    @Test void aNonCanonicalPolicyIsStoredReducedAndItsCanonicalFormReloadsUnchanged() throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        Map<String, String> environment = seedEnvironment();
        environment.putAll(Map.of("CODEMASTERY_SEED_ADAPTIVE_WEAK", "2/4", "CODEMASTERY_SEED_ADAPTIVE_MASTERED", "8/10"));
        assertSucceeded(psql(db, COURSES, environment));
        assertEquals(List.of("1|2|4|5|6"), rows(db, "SELECT adaptive_settings #>> '{weak,numerator}', adaptive_settings #>> '{weak,denominator}', "
                + "adaptive_settings #>> '{mastered,numerator}', adaptive_settings #>> '{mastered,denominator}', "
                + "adaptive_settings ->> 'minimumQuestionsPerSkill' FROM public.courses WHERE id = '" + PYTHON + "'"));
        String loaded = snapshot(db);
        assertLoaded(db, COURSES);
        assertSafeEquals(loaded, snapshot(db), "the reduced policy equals the canonical one");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-weak", "missing-mastered", "missing-minimum", "zero-weak", "weak-above-one",
            "zero-denominator", "weak-equals-mastered", "weak-above-mastered", "decimal", "leading-zero", "zero-minimum",
            "minimum-overflow", "denominator-overflow", "changed-policy"})
    void invalidOrChangedPoliciesAreRejectedWithoutAnyWrite(String input) throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        Map<String, String> environment = seedEnvironment();
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
                assertLoaded(db, COURSES);
                environment.put(minimum, "5");
                message = "SD02 Course " + PYTHON + " conflicts with an existing Course";
            }
            default -> throw new IllegalArgumentException(input);
        }
        String before = snapshot(db);
        assertRejected(psql(db, COURSES, environment), message);
        assertSafeEquals(before, snapshot(db), "database after the rejected SD02 run");
    }

    @ParameterizedTest
    @ValueSource(strings = {"graph-without-course", "renamed-skill", "extra-skill", "extra-prerequisite", "extra-mapping",
            "hidden-lesson", "profiles-without-course", "profiles-without-learners", "profile-learner-role", "left-enrollment"})
    void graphAndProfileConflictsStopTheWholeTransactionWithoutPartialWrites(String conflict) throws Exception {
        String db = attested(freshDatabase());
        assertLoaded(db, USERS);
        String file = GRAPH, message;
        UUID s01 = final5("course/python/skill/S01"), s02 = final5("course/python/skill/S02");
        UUID l01 = final5("course/python/chapter/1/lesson/1");
        switch (conflict) {
            case "graph-without-course" -> message = "SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first";
            case "renamed-skill" -> {
                assertLoaded(db, COURSES);
                assertLoaded(db, GRAPH);
                execute(db, "UPDATE public.skills SET name = 'Printing' WHERE id = '" + s01 + "'");
                message = "SD03 Skill " + s01 + " conflicts with an existing Skill ID or Course skill name";
            }
            case "extra-skill", "extra-prerequisite", "extra-mapping" -> {
                assertLoaded(db, COURSES);
                assertLoaded(db, GRAPH);
                execute(db, switch (conflict) {
                    case "extra-skill" -> "INSERT INTO public.skills VALUES ('" + UUID.randomUUID() + "','" + PYTHON + "','Recursion',NULL)";
                    case "extra-prerequisite" -> "INSERT INTO public.skill_prerequisites VALUES ('" + UUID.randomUUID() + "','" + PYTHON + "','" + s02
                            + "','" + final5("course/python/skill/S20") + "')";
                    default -> "INSERT INTO public.lesson_skills VALUES ('" + UUID.randomUUID() + "','" + l01 + "','" + s02 + "')";
                });
                message = "SD03 course/python already has skills, prerequisites or mappings outside the approved graph";
            }
            case "hidden-lesson" -> {
                assertLoaded(db, COURSES);
                execute(db, "UPDATE public.lessons SET publication_status = 'hidden' WHERE id = '" + l01 + "'");
                message = "SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first";
            }
            case "profiles-without-course" -> {
                insertProfileLearners(db);
                file = PROFILES;
                message = "SD05 requires the SD02 course/python Course; load 02-course-python-fundamentals.sql first";
            }
            case "profiles-without-learners", "profile-learner-role" -> {
                assertLoaded(db, COURSES);
                if (conflict.equals("profile-learner-role")) {
                    insertProfileLearners(db);
                    execute(db, "UPDATE public.users SET role = 'instructor' WHERE id = '" + final5("user/p2") + "'");
                }
                file = PROFILES;
                message = "SD05 requires the three final-v1 profile Learners of 01-users.sql (SD01 extension)";
            }
            case "left-enrollment" -> {
                assertLoaded(db, COURSES);
                insertProfileLearners(db);
                assertLoaded(db, PROFILES);
                UUID enrollment = final5("user/p2/enrollment/course/python");
                execute(db, "UPDATE public.enrollments SET status = 'left' WHERE id = '" + enrollment + "'");
                file = PROFILES;
                message = "SD05 Enrollment " + enrollment + " conflicts with an existing Enrollment ID or membership";
            }
            default -> throw new IllegalArgumentException(conflict);
        }
        String before = snapshot(db);
        assertRejected(psql(db, file, seedEnvironment()), message);
        assertSafeEquals(before, snapshot(db), "database after the failed transaction (no partial write)");
    }

    /**
     * Stand-in for the ADR013 §3 SD01 extension, which is another Issue's scope and not in 01-users.sql yet:
     * the three approved final-v1 profile Learners, inserted directly into the disposable database.
     */
    private static void insertProfileLearners(String db) throws SQLException {
        for (int i = 0; i < PROFILE_KEYS.size(); i++)
            execute(db, "INSERT INTO public.users (id,email,password_hash,display_name,role,account_status) VALUES ('"
                    + final5("user/" + PROFILE_KEYS.get(i)) + "','" + PROFILE_KEYS.get(i) + "@example.test','" + OTHER_HASH + "','"
                    + PROFILE_NAMES.get(i) + "','learner','active')");
    }

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
            assertRejected(psql(db, USERS, environment), "SD01 " + message);
            assertRejected(psql(db, COURSES, environment), "SD02 " + message);
            assertRejected(psql(db, GRAPH, environment), "SD03 " + message);
            assertRejected(psql(db, PROFILES, environment), "SD05 " + message);
            holder.rollback();
        }
        assertSafeEquals(before, snapshot(db), "database after the rejected runs");
    }

    /**
     * Both loaders run the target guard, which takes the transaction's first snapshot, and then wait on the seed
     * lock. Under an inherited REPEATABLE READ or SERIALIZABLE default, the second loader would keep that stale
     * snapshot after the first commits; the seeds' explicit READ COMMITTED makes it see the committed rows.
     */
    @ParameterizedTest(name = "inherited {0}")
    @ValueSource(strings = {"read committed", "repeatable read", "serializable"})
    void concurrentLoadersConvergeWhateverTheInheritedIsolationLevel(String isolation) throws Exception {
        String db = inheritIsolation(attested(freshDatabase()), isolation);
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var step : List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"))) {
                List<Future<Run>> loaders = new ArrayList<>();
                try (Connection holder = connect(db)) {
                    holder.setAutoCommit(false);
                    holder.createStatement().execute("LOCK TABLE public." + step.getValue() + " IN SHARE ROW EXCLUSIVE MODE");
                    for (int i = 0; i < 2; i++) loaders.add(pool.submit(() -> psql(db, step.getKey(), seedEnvironment())));
                    // Both loaders have verified the target and read nothing yet: they wait on the seed lock.
                    awaitWaiters(holder, step.getValue(), 2);
                    holder.rollback();
                }
                for (var loader : loaders) assertSucceeded(loader.get(2, TimeUnit.MINUTES));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of("2|4|8|24|25"), rows(db, "SELECT (SELECT count(*) FROM public.users), (SELECT count(*) FROM public.courses), "
                + "(SELECT count(*) FROM public.chapters), (SELECT count(*) FROM public.lessons), (SELECT count(*) FROM public.learning_materials)"));
        String loaded = snapshot(db);
        assertLoaded(db, USERS);
        assertLoaded(db, COURSES);
        assertSafeEquals(loaded, snapshot(db), "row values and row versions after reruns at inherited " + isolation);
    }

    @Test void psqlVariablesKeepCredentialsOutOfProcessArguments() throws Exception {
        String db = attested(freshDatabase());
        for (var step : List.of(Map.entry(USERS, "users"), Map.entry(COURSES, "courses"))) {
            whileLaunchedPsqlIsBlocked(db, step.getKey(), step.getValue(), PSQL, List.of(), (launch, pid) ->
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
        var pool = Executors.newSingleThreadExecutor();
        Launch launch = Launch.create();
        try (Connection holder = connect(db)) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE public." + table + " IN SHARE ROW EXCLUSIVE MODE");
            Future<Run> loader = pool.submit(() -> launchPsqlWithVariables(db, file, launch, program, extraArguments));
            try {
                awaitWaiters(holder, table, 1);
                awaitLaunchedSession(holder, launch);
                check.check(launch, Integer.parseInt(inContainer(null, "cat", launch.pidFile())));
            } finally {
                holder.rollback();
                assertSucceeded(loader.get(2, TimeUnit.MINUTES));
            }
        } finally {
            pool.shutdownNow();
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

    @Test void seededRecordsAreServedByTheAuthAndCatalogueConsumers() throws Exception {
        String db = POSTGRES.getDatabaseName();
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();
        attested(db);
        try {
            assertLoaded(db, USERS);
            assertLoaded(db, COURSES);
            assertLoaded(db, GRAPH);

            String instructorToken = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(login("instructor@example.test", INSTRUCTOR_PASSWORD)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(INSTRUCTOR.toString()))
                    .andExpect(jsonPath("$.user.role").value("Instructor")).andExpect(jsonPath("$.user.displayName").value("Instructor Demo"))
                    .andReturn().getResponse().getContentAsString(), "$.accessToken");
            // AD07's owner read re-validates the stored graph and proves every mapping against CO06's structure.
            mvc.perform(get("/api/courses/" + PYTHON + "/skill-graph").header("Authorization", "Bearer " + instructorToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.skills[*].name", containsInAnyOrder(SKILLS.toArray())))
                    .andExpect(jsonPath("$.prerequisites.length()").value(24)).andExpect(jsonPath("$.mappings.length()").value(20));
            mvc.perform(get("/api/courses/" + PYTHON)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.syllabus[*].title", contains(CHAPTER_TITLES.toArray())))
                    .andExpect(jsonPath("$.syllabus[2].lessons[*].title", contains("While Loops", "For Loops", "Loop Control")));
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
            String seeded = "('" + INTRO + "','" + PRACTICE + "','" + PYTHON + "')";
            execute(db, "DELETE FROM public.lesson_skills WHERE skill_id IN (SELECT id FROM public.skills WHERE course_id = '" + PYTHON + "')");
            execute(db, "DELETE FROM public.skill_prerequisites WHERE course_id = '" + PYTHON + "'");
            execute(db, "DELETE FROM public.skills WHERE course_id = '" + PYTHON + "'");
            execute(db, "DELETE FROM public.learning_materials WHERE lesson_id IN (SELECT l.id FROM public.lessons l JOIN public.chapters ch ON ch.id = l.chapter_id WHERE ch.course_id IN " + seeded + ")");
            execute(db, "DELETE FROM public.lessons WHERE chapter_id IN (SELECT id FROM public.chapters WHERE course_id IN " + seeded + ")");
            execute(db, "DELETE FROM public.chapters WHERE course_id IN " + seeded);
            execute(db, "DELETE FROM public.courses WHERE id IN ('" + INTRO + "','" + PRACTICE + "','" + DRAFT + "','" + PYTHON + "')");
            // ADR014: the sign-ins above created refresh sessions for the seeded accounts.
            execute(db, "DELETE FROM public.refresh_tokens WHERE session_id IN (SELECT id FROM public.refresh_sessions WHERE user_id IN ('" + INSTRUCTOR + "','" + LEARNER + "'))");
            execute(db, "DELETE FROM public.refresh_sessions WHERE user_id IN ('" + INSTRUCTOR + "','" + LEARNER + "')");
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
                "CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", INSTRUCTOR_HASH, "CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", LEARNER_HASH,
                // ADR013 §16.1 approved demonstration policy of course/python.
                "CODEMASTERY_SEED_ADAPTIVE_WEAK", "1/2", "CODEMASTERY_SEED_ADAPTIVE_MASTERED", "4/5",
                "CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL", "6"));
    }

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
    private static Run psqlWithVariables(String db, String file) throws Exception {
        return runPsqlWithVariables(db, file, List.of(), PSQL, List.of(), Map.of());
    }

    /** {@link #psqlWithVariables} started through {@link #RECORD_PID_THEN_EXEC}, as {@code program}, with extra arguments. */
    private static Run launchPsqlWithVariables(String db, String file, Launch launch, List<String> program,
                                               List<String> extraArguments) throws Exception {
        return runPsqlWithVariables(db, file, List.of("sh", "-c", RECORD_PID_THEN_EXEC, "codemastery-psql-launcher",
                launch.pidFile()), program, extraArguments, Map.of("PGAPPNAME", launch.applicationName()));
    }

    private static Run runPsqlWithVariables(String db, String file, List<String> launcher, List<String> program,
                                            List<String> extraArguments, Map<String, String> extraEnvironment) throws Exception {
        List<String> command = new ArrayList<>(launcher);
        command.addAll(program);
        command.addAll(List.of("-X", "-q", "-U", POSTGRES.getUsername(), "-d", db,
                "-c", "\\getenv seed_target_system_identifier RUN_TARGET",
                "-c", "\\getenv instructor_password_hash RUN_INSTRUCTOR",
                "-c", "\\getenv learner_password_hash RUN_LEARNER",
                "-c", "\\getenv adaptive_weak RUN_WEAK",
                "-c", "\\getenv adaptive_mastered RUN_MASTERED",
                "-c", "\\getenv adaptive_minimum_questions_per_skill RUN_MINIMUM"));
        command.addAll(extraArguments);
        command.addAll(List.of("-f", "/seed/" + file));
        Map<String, String> environment = new HashMap<>(Map.ofEntries(
                Map.entry("RUN_TARGET", systemIdentifier), Map.entry("RUN_INSTRUCTOR", INSTRUCTOR_HASH),
                Map.entry("RUN_LEARNER", LEARNER_HASH), Map.entry("RUN_WEAK", "1/2"), Map.entry("RUN_MASTERED", "4/5"),
                Map.entry("RUN_MINIMUM", "6"),
                Map.entry("CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER", "invalid"),
                Map.entry("CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH", "invalid"),
                Map.entry("CODEMASTERY_SEED_LEARNER_PASSWORD_HASH", "invalid"),
                Map.entry("CODEMASTERY_SEED_ADAPTIVE_WEAK", "invalid"),
                Map.entry("CODEMASTERY_SEED_ADAPTIVE_MASTERED", "invalid"),
                Map.entry("CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL", "invalid")));
        environment.putAll(extraEnvironment);
        ExecResult result = POSTGRES.execInContainer(ExecConfig.builder()
                .command(command.toArray(String[]::new)).envVars(environment).build());
        return new Run(result.getExitCode(), result.getStdout(), result.getStderr());
    }

    private static void assertLoaded(String db, String file) throws Exception { assertSucceeded(psql(db, file, seedEnvironment())); }

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
    private static void assertNoDisclosure(AssertionError failure, List<String> sentinels) {
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

    private static String freshDatabase() throws SQLException {
        String db = "sd_seed_" + DATABASES.incrementAndGet();
        try (var admin = connect(POSTGRES.getDatabaseName())) { admin.createStatement().execute("CREATE DATABASE " + db); }
        synchronized (CREATED) { CREATED.add(db); }
        Flyway.configure().dataSource(url(db), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
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
        return DriverManager.getConnection(url(db), POSTGRES.getUsername(), POSTGRES.getPassword());
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
}
