# CodeMastery Sample Data

Synthetic English seed data for local development and disposable test databases.

> **Local and test databases only.** Never load these files into a shared, staging
> or production database; production seeding is forbidden. Each seed file refuses
> to run unless the connected database passes the target verification in
> [Step 2](#step-2--identify-and-attest-the-disposable-target), and it stops before
> taking any lock or writing anything.

The decisions behind this dataset are recorded in
[ADR002 CT15.I-data](../docs/decisions/002-interim-delegated-contracts.md#ct15i-data--delegated_approved)
and the
[SD01/SD02 completion record](../docs/decisions/002-interim-delegated-contracts.md#sd01sd02-sample-data-completion--delegated-approval-30-october-2026).
Automated verification is described in
[docs/testing.md](../docs/testing.md#sd01sd02-seed-verification--30-october-2026).

## Contents

| Path | Content | Status |
| --- | --- | --- |
| `seed/01-users.sql` | SD01: synthetic Instructor and Learner | Implemented |
| `seed/02-course-python-fundamentals.sql` | SD02: Courses, Chapters, Lessons and text LearningMaterials | Implemented |
| `seed/03-skill-graph-and-lesson-mapping.sql` | Skill graph | Empty; not part of this dataset |
| `seed/04-questions-quizzes-assignments.sql` | Questions, quizzes, assignments | Empty; not part of this dataset |
| `seed/05-learner-profiles.sql` | Adaptive learner profiles | Empty; not part of this dataset |
| `materials/sample-slides.pdf` | Sample lecture slides | Repository file only; not uploaded to object storage or referenced by a seed row |

The 02 filename is its approved repository location. It does not rename the
approved Courses below.

## Dataset `interim-v1`

Every ID is a UUIDv5 in the standard URL namespace for the name
`https://codemastery.test/interim-v1/<logical-key>`. IDs, titles, ordering and
publication states are fixed; the only values chosen at load time are the two
password hashes.

| Logical key | ID | Record |
| --- | --- | --- |
| `user/instructor` | `1eacfb14-e494-596c-bb53-3d012e7caf72` | `instructor@example.test`, Instructor Demo, instructor, active |
| `user/learner` | `6ae6a0b2-7ef7-5500-a2ac-db04127da8fa` | `learner@example.test`, Learner Demo, learner, active |
| `course/intro` | `bd8ce62d-90d6-525b-ae6c-652c53ccfe6a` | Introduction to Programming, published |
| `course/practice` | `824937a6-f333-580a-96af-7b9b2c57453b` | Programming Practice, published |
| `course/draft` | `34ea40e0-54fd-5fb6-a402-5df72e49c45a` | Instructor Draft, hidden, no Chapters |
| `course/intro/chapter/1` | `b87c15db-8ae8-5458-ac5d-ab347cdc70aa` | Getting Started, position 1 |
| `course/intro/chapter/1/lesson/1` | `b69f291a-e0b3-5698-87c6-db1f21126802` | Concepts, position 1, published |
| `course/intro/chapter/1/lesson/2` | `824f67f9-9f73-5ff1-ac74-6e4f629bb432` | Practice, position 2, published |
| `course/practice/chapter/1` | `49fe9687-cf8a-5397-b704-fb6b2f3d5930` | Getting Started, position 1 |
| `course/practice/chapter/1/lesson/1` | `417c19ff-5c93-5aaa-a4d8-883f20574b55` | Concepts, position 1, published |
| `course/practice/chapter/1/lesson/2` | `bb0e05c9-ba94-5cb8-a861-39a999266373` | Practice, position 2, published |
| `course/intro/chapter/1/lesson/1/material/1` | `a1bb3d63-1519-5ab6-87dc-8a4468f82022` | Text: What a Program Is, published |
| `course/intro/chapter/1/lesson/2/material/1` | `7ca42bd5-0c62-53d6-9cb2-d614cb4a8a59` | Text: Practice: Variables and Output, published |
| `course/practice/chapter/1/lesson/1/material/1` | `8e74fd91-2b8e-5f74-ae12-32cb310de9c5` | Text: Breaking a Problem into Steps, published |
| `course/practice/chapter/1/lesson/2/material/1` | `05368d30-9616-575e-b680-9e1614e22566` | Text: Practice: Trace and Fix Short Programs, published |
| `course/practice/chapter/1/lesson/2/material/2` | `500fddc5-eed2-588c-b39a-f82adc3d24ca` | Text: Instructor Notes, **hidden** |

All three Courses belong to the synthetic Instructor and use the description
"Demo course for the Interim catalogue", the prerequisite text "No
prerequisites", `adaptive_enabled = false` and `adaptive_settings = NULL`.
Lesson Markdown is empty; each Lesson's instructional text is a text material.
There is no Administrator, no real person and no enrolment, progress, skill,
assessment or mastery data.

The hidden Course and the hidden material provide visibility contrast: learners
and the public catalogue must never see them.

## Rules for every run

- **Disposable targets only.** Seed only a database you created for local
  development or testing and can discard.
- **No reset, update or delete.** The files only insert missing rows. A row that
  already matches exactly is skipped. Any other existing data with the same ID,
  email or sibling position stops the whole transaction; nothing is written.
- **No committed credentials.** Choose synthetic passwords locally. Never commit
  passwords, hashes, `.env` files or command output that contains them, and never
  pass hashes as `-v` command-line arguments on a shared machine: other local users
  can read process arguments. The commands below pass them through environment
  variables instead.
- **Order.** Load `01-users.sql` before `02-course-python-fundamentals.sql`.
  Every Course references the synthetic Instructor.

## Prerequisites

- The local Docker Compose stack from [docs/setup.md](../docs/setup.md#local-docker-compose-runtime),
  started at least once so the backend has applied Flyway migrations V001–V006.
  Seeds never create or change schema.
- JDK 25 (for `jshell`) and the backend Maven Wrapper, to generate password hashes.
- bash or zsh. Run every command from the repository root.

The commands run `psql` (PostgreSQL 17) inside the Compose `postgres` container as
the local `POSTGRES_USER`. The seed files need psql 15 or later.

Define this helper in your shell first:

```bash
compose() { docker compose --env-file .env -f infrastructure/docker-compose.yml "$@"; }
```

## Step 1 — Confirm the migrated schema

```bash
compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, success FROM public.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;
SQL
```

Continue only if versions 001 to 006 are listed with `success = t`. If the table
does not exist, start the backend once so Flyway can migrate the database.

## Step 2 — Identify and attest the disposable target

The seeds verify two things before any lock or write:

1. **Cluster identity.** You read the server's system identifier from its control
   file, without going through the SQL connection the seed will use. The seed
   compares it with the identity of the cluster it actually reached, so a wrong
   host, port, tunnel or leftover `PGHOST` value is rejected.
2. **Disposable-target attestation.** The database must carry the setting
   `codemastery.seed_target = 'disposable:<system identifier>'`, which you add
   deliberately to a database you have created for seeding. The seed reads the
   stored database setting, so a session or `PGOPTIONS` value cannot impersonate it.

Read the identifier from the container's control file:

```bash
CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER="$(compose exec -T -u postgres postgres pg_controldata \
  | awk -F': *' '/^Database system identifier/ {print $2}')"
export CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
```

Attest the local database. **Never run this against a shared, staging or
production database.**

```bash
compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER postgres \
  sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
\getenv target_identifier CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER
SELECT format('ALTER DATABASE %I SET codemastery.seed_target = %L',
              current_database(), 'disposable:' || :'target_identifier') \gexec
SQL
```

The attestation stays on the database until you remove it (see
[Removing the attestation](#removing-the-attestation)). It is bound to this
cluster: a dump restored elsewhere, or the same setting copied to another server,
does not pass.

## Step 3 — Generate password hashes

Resolve the backend runtime classpath once. The file is written under the ignored
`backend/target/` directory:

```bash
(cd backend && ./mvnw -q dependency:build-classpath -Dmdep.includeScope=runtime \
  -Dmdep.outputFile=target/seed-hash.classpath)
```

Define the generator. It uses the backend's own Spring Security encoder with the
CT01 settings (BCrypt `$2b$`, cost 14) and accepts only passwords of at least 15
characters and at most 72 UTF-8 bytes. It reads the password from the environment,
never from the command line, and prints only the hash:

```bash
seed_hash() {
  jshell --feedback silent --class-path "$(cat backend/target/seed-hash.classpath)" - <<'JSH'
var password = System.getenv("CODEMASTERY_SEED_PASSWORD");
var valid = password != null &&
        password.codePointCount(0, password.length()) >= 15 &&
        password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2B, 14);
if (valid) { System.out.println(encoder.encode(password)); } else { System.err.println("The password must have at least 15 characters and at most 72 UTF-8 bytes."); }
/exit valid ? 0 : 1
JSH
}
```

Keep the line breaks exactly as shown: `jshell` runs each complete line as a
statement.

Type each synthetic password when prompted. Nothing is echoed, and each hash takes
about one second:

```bash
printf 'Instructor password: '; read -rs CODEMASTERY_SEED_PASSWORD; echo
export CODEMASTERY_SEED_PASSWORD
CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH

printf 'Learner password: '; read -rs CODEMASTERY_SEED_PASSWORD; echo
CODEMASTERY_SEED_LEARNER_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_LEARNER_PASSWORD_HASH
unset CODEMASTERY_SEED_PASSWORD
```

BCrypt uses a random salt, so the same password produces a different hash each
time. To re-run the seeds later against the same database, reuse the same hash
values; new hashes conflict with the stored rows.

## Step 4 — Load the seeds

```bash
compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER \
  -e CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH -e CODEMASTERY_SEED_LEARNER_PASSWORD_HASH postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/01-users.sql

compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/02-course-python-fundamentals.sql
```

`-e NAME` without a value passes your shell's value to the container without
placing it on a command line. Each file runs in one transaction, prints nothing on
success and exits with status 0. On any error psql stops with status 3 and the
transaction is rolled back.

The files also accept psql variables (`-v seed_target_system_identifier=...`,
`-v instructor_password_hash=...`, `-v learner_password_hash=...`). A psql
variable takes precedence over the environment variable of the same input.

## Step 5 — Verify the result

Database check:

```bash
compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT c.title, c.publication_status,
       count(DISTINCT l.id) AS lessons,
       count(m.id) FILTER (WHERE m.publication_status = 'published') AS published_materials
FROM public.courses c
LEFT JOIN public.chapters ch ON ch.course_id = c.id
LEFT JOIN public.lessons l ON l.chapter_id = ch.id
LEFT JOIN public.learning_materials m ON m.lesson_id = l.id
WHERE c.instructor_id = '1eacfb14-e494-596c-bb53-3d012e7caf72'
GROUP BY c.title, c.publication_status
ORDER BY c.title;
SQL
```

Expected output:

```text
            title            | publication_status | lessons | published_materials
-----------------------------+--------------------+---------+---------------------
 Instructor Draft            | hidden             |       0 |                   0
 Introduction to Programming | published          |       2 |                   2
 Programming Practice        | published          |       2 |                   2
```

Application check, with the backend running:

```bash
curl -s 'http://localhost:8080/api/courses?q=Demo%20course'
```

The response lists exactly Introduction to Programming and Programming Practice,
each with `"instructorDisplayName":"Instructor Demo"`, and never Instructor Draft.
Sign in through the frontend with `instructor@example.test` or
`learner@example.test` and the password you chose in Step 3. Successful SQL
loading alone does not prove login: the backend checks the stored hash at sign-in.

## Re-running and conflicts

Running both files again with the same inputs changes nothing. Partial earlier
loads are completed; for example, if only the Instructor exists, a re-run adds the
Learner.

| Message | Meaning and action |
| --- | --- |
| `Required input seed_target_system_identifier is missing.` / `Required psql variable ... is missing.` | An input is unset in the shell that ran `compose exec`. Export it and pass it with `-e`. |
| `target verification failed: malformed expected system identifier` | The identifier is empty or not a decimal number. Repeat Step 2. |
| `target verification failed: connected cluster is not the expected target` | psql reached a different server than the one whose control file you read. Stop and check which database you are connected to. |
| `target verification failed: database is not attested as a disposable seed target` | The database has no valid attestation for this cluster. Attest it only if it is a disposable local database. |
| `target verification failed: cluster identity is unavailable` | The server did not report its identity. The seed refuses to run. |
| `SD01 requires valid BCrypt $2b$14$ password hashes` | A hash is missing, truncated or uses another prefix or cost. Regenerate it with Step 3. |
| `SD01 User seed conflicts with an existing ID or canonical email` | A User with the same ID or email differs, often because the hash changed. Reuse the original hashes or use a new disposable database. |
| `SD02 requires the SD01 synthetic Instructor; load 01-users.sql first` | Load `01-users.sql` first. |
| `SD02 Instructor reference does not match the approved SD01 identity` | The Instructor row exists but is not the approved synthetic identity. |
| `SD02 Course / Chapter / Lesson / LearningMaterial <id> conflicts ...` | Existing content with that ID, or another row at the same Chapter or Lesson position, differs from the seed. |

Do not edit or delete rows to make a seed pass. Start from a new disposable
database instead. The destructive local reset in
[docs/setup.md](../docs/setup.md#persistence-shutdown-and-reset) removes **all**
local data; use it only if you intend to discard everything.

Error messages never include passwords, hashes or full rows: the seeds set psql
`VERBOSITY terse`, which suppresses constraint `DETAIL` lines.

## Removing the attestation

After seeding, you can remove the attestation so that no further seed run is
accepted until you attest the database again:

```bash
compose exec -T postgres sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT format('ALTER DATABASE %I RESET codemastery.seed_target', current_database()) \gexec
SQL
```

## Sample materials

`materials/sample-slides.pdf` contains 11 pages of English slides,
"Python Fundamentals — Lesson 1.1: Variable Declaration & Naming Conventions".

| Property | Value |
| --- | --- |
| SHA-256 | `d0a313773b9866031b33c2d153ed88a19d30b8c7ed26b7c202c3588d6f6f7d8f` |
| Format | PDF 1.4, 960 × 540 pt pages, not encrypted, no forms or JavaScript |
| Provenance | Prepared for this project as stated by the Project Leader; committed by Nguyen Anh Tai on 24 October 2026 (`348d2c2`) and integrated byte-for-byte |
| Licensing | **Not yet recorded.** The Project Leader must add the permitted-use statement before these slides are presented as licensed sample material. |

The slides are a repository file only. No seed row references them, and they are
not uploaded to MinIO: object keys, upload, access control and delivery belong to
the CT09 storage contract, which is not yet implemented. When that slice exists,
the object must be uploaded and checked against the SHA-256 above **before** a
file-material row is added. The reserved identity for that future row is
`course/intro/chapter/1/lesson/1/material/2`
(`045fe941-10fd-5916-98ad-04f06a6edfc7`).

No external-video material is seeded, because no approved video URL exists.

## Content provenance

All seeded text is original synthetic content written for CodeMastery. The text
materials were drafted with AI assistance (Claude Code) under the Project Leader's
delegated authorization on 30 October 2026, and they are subject to the normal
CODEOWNER review. They contain no third-party text. The `.test` email addresses
are reserved names that cannot reach a real mailbox.

## What this guide does and does not establish

- Successful SQL loading proves the rows exist and match the dataset. It does not
  prove sign-in or catalogue behavior; check those through the application.
- Verifying the seeds is a separate step from QA13 acceptance. The executed
  automated evidence is recorded in [docs/testing.md](../docs/testing.md#sd01sd02-seed-verification--30-october-2026).
- Nothing here provisions object storage, deploys an environment or authorizes
  seeding outside your own disposable database.
