# CodeMastery Sample Data

Synthetic English seed data for local development and disposable test databases.

**Purpose and scope.** The dataset supports demonstration and verification of the
implemented Learner slice and of the adaptive Course. It has two versions, chosen
when you load it:

- **`interim-v1`** (ADR002 CT15.I-data): SD01 Instructor and Learner, SD02 Courses,
  Chapters, Lessons and text materials. Loading it is unchanged.
- **`final-v1`** (ADR013 §§3–4): everything in `interim-v1`, plus the SD01
  Administrator and three profile Learners, the adaptive Course “Python
  Fundamentals” with its twenty text materials (SD02), its skill graph and Lesson
  mapping, and one Enrollment per profile Learner (SD03). The L01 PDF is uploaded
  through the application afterwards ([Step 6](#step-6--provision-the-l01-slides-final-v1)).

Questions, quizzes and assignments (SD04, `04`) are not included yet; see
[Final dataset `final-v1`](#final-dataset-final-v1).

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
| `seed/01-users.sql` | SD01: Instructor and Learner (`interim-v1`); Administrator and profile Learners P1–P3 (`final-v1`) | Implemented |
| `seed/02-course-python-fundamentals.sql` | SD02: Courses, Chapters, Lessons and text LearningMaterials (`interim-v1`); Course `course/python` (`final-v1`) | Implemented |
| `seed/03-skill-graph-and-lesson-mapping.sql` | SD03: skills S01–S20, prerequisites and Lesson mapping of `course/python` (`final-v1` only) | Implemented |
| `seed/04-questions-quizzes-assignments.sql` | SD04: questions, quizzes, assignments | Empty; SD04 is not implemented |
| `seed/05-learner-profiles.sql` | SD03 (historical SD05): one active Enrollment in `course/python` per profile Learner (`final-v1` only) | Implemented |
| `materials/sample-slides.pdf` | Sample lesson slides (12 pages) | Repository file with recorded permission; uploaded to L01 through the CT09 API in Step 6, never by a seed row |

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
- **Dataset selection.** The validated selector overrides inherited session, role and database dataset GUCs.
  `01` and `02` load `interim-v1` unless the input
  `CODEMASTERY_SEED_DATASET` is `final-v1`. Only `final-v1` takes the four extra
  password hashes and the adaptive policy; a `final-v1` input supplied without the
  selector stops the run, so a forgotten selector cannot silently load only
  `interim-v1`. `03` and `05` belong to `final-v1` only and need no selector.
- **Order.** `interim-v1`: `01`, then `02`. `final-v1`: `01`, `02`,
  `03-skill-graph-and-lesson-mapping.sql`, then `05-learner-profiles.sql`. Every
  Course references the synthetic Instructor, the graph references `course/python`
  and the Enrollments reference `course/python` and the profile Learners. Each file
  checks its prerequisites and stops with a message naming the file to load first.
- **Upgrade.** `final-v1` contains `interim-v1` unchanged. Loading `final-v1` into a
  database that already holds `interim-v1` only adds the missing rows.

## Prerequisites

- The local Docker Compose stack from [docs/setup.md](../docs/setup.md#local-docker-compose-runtime),
  started at least once so the backend has applied the Flyway migrations (V001–V009 at the time of writing).
  Seeds never create or change schema.
- JDK 25 (for `jshell`) and the backend Maven Wrapper, to generate password hashes.
- bash or zsh, and Python 3 (standard library only) for the protected PDF procedure. Run every command from the repository root.

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

Continue only if every listed version, 001 to 009 at the time of writing, has `success = t`. If the table
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
printf 'Instructor password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
export CODEMASTERY_SEED_PASSWORD
CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH

printf 'Learner password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
CODEMASTERY_SEED_LEARNER_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_LEARNER_PASSWORD_HASH
unset CODEMASTERY_SEED_PASSWORD
```

For `final-v1` only, also generate the hashes of `admin@example.test` and of the
profile Learners `p1@`, `p2@` and `p3@example.test`. ADR013 §3 requires a
**different password for every account**; the seed rejects a hash reused for two
accounts, but it cannot see the passwords, so choosing six different ones is your
responsibility:

```bash
printf 'Administrator password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
export CODEMASTERY_SEED_PASSWORD
CODEMASTERY_SEED_ADMIN_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_ADMIN_PASSWORD_HASH

printf 'P1 password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
CODEMASTERY_SEED_P1_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_P1_PASSWORD_HASH

printf 'P2 password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
CODEMASTERY_SEED_P2_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_P2_PASSWORD_HASH

printf 'P3 password: '; IFS= read -rs CODEMASTERY_SEED_PASSWORD; echo
CODEMASTERY_SEED_P3_PASSWORD_HASH="$(seed_hash)" && export CODEMASTERY_SEED_P3_PASSWORD_HASH
unset CODEMASTERY_SEED_PASSWORD
```

BCrypt uses a random salt, so the same password produces a different hash each
time. To re-run the seeds later against the same database, reuse the same hash
values; new hashes conflict with the stored rows.

## Step 4 — Load the seeds

**`interim-v1`** — unchanged; the files take only the target identifier and the two
hashes:

```bash
compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER \
  -e CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH -e CODEMASTERY_SEED_LEARNER_PASSWORD_HASH postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/01-users.sql

compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/02-course-python-fundamentals.sql
```

**`final-v1`** — select the dataset, supply the approved demonstration policy of
`course/python`, then load the four files in order:

```bash
export CODEMASTERY_SEED_DATASET=final-v1 CODEMASTERY_SEED_ADAPTIVE_WEAK=1/2 \
  CODEMASTERY_SEED_ADAPTIVE_MASTERED=4/5 CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL=6

compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER -e CODEMASTERY_SEED_DATASET \
  -e CODEMASTERY_SEED_INSTRUCTOR_PASSWORD_HASH -e CODEMASTERY_SEED_LEARNER_PASSWORD_HASH \
  -e CODEMASTERY_SEED_ADMIN_PASSWORD_HASH -e CODEMASTERY_SEED_P1_PASSWORD_HASH \
  -e CODEMASTERY_SEED_P2_PASSWORD_HASH -e CODEMASTERY_SEED_P3_PASSWORD_HASH postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/01-users.sql

compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER -e CODEMASTERY_SEED_DATASET \
  -e CODEMASTERY_SEED_ADAPTIVE_WEAK -e CODEMASTERY_SEED_ADAPTIVE_MASTERED \
  -e CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL postgres \
  sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sample-data/seed/02-course-python-fundamentals.sql

for seed in 03-skill-graph-and-lesson-mapping.sql 05-learner-profiles.sql; do
  compose exec -T -e CODEMASTERY_SEED_TARGET_SYSTEM_IDENTIFIER postgres \
    sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "sample-data/seed/$seed" || break
done
```

The three adaptive values are the approved demonstration policy of
`course/python` (ADR013 §16.1); they are not secrets, not a system default, and no
number is committed in the seed. `weak` and `mastered` are
`numerator/denominator`; the seed checks the ADR005 §6.2 rules
(`0 < weak < mastered <= 1`, integers up to 2147483647) before any lock or write
and stores each fraction reduced. Reuse the same values on a re-run: another policy
conflicts with the stored Course. To load `interim-v1` into another database from
the same shell, first unset `CODEMASTERY_SEED_DATASET` and the `final-v1` hash and
policy variables; otherwise the files stop with the selector messages below:

```bash
unset CODEMASTERY_SEED_DATASET CODEMASTERY_SEED_ADMIN_PASSWORD_HASH CODEMASTERY_SEED_P1_PASSWORD_HASH \
  CODEMASTERY_SEED_P2_PASSWORD_HASH CODEMASTERY_SEED_P3_PASSWORD_HASH CODEMASTERY_SEED_ADAPTIVE_WEAK \
  CODEMASTERY_SEED_ADAPTIVE_MASTERED CODEMASTERY_SEED_ADAPTIVE_MINIMUM_QUESTIONS_PER_SKILL
```

`-e NAME` without a value passes your shell's value to the container without
placing it on a command line. Each file runs in one READ COMMITTED transaction,
whatever the database's default isolation level, prints nothing on success and
exits with status 0. On any error psql stops with status 3 and the
transaction is rolled back.

The files also accept psql variables (`-v seed_target_system_identifier=...`,
`-v seed_dataset=...`, `-v instructor_password_hash=...`,
`-v learner_password_hash=...`, `-v admin_password_hash=...`,
`-v p1_password_hash=...`, `-v p2_password_hash=...`, `-v p3_password_hash=...`,
`-v adaptive_weak=...`, `-v adaptive_mastered=...`,
`-v adaptive_minimum_questions_per_skill=...`). A psql variable takes precedence
over the environment variable of the same input. Do not pass hashes this way on a
shared machine.

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

Expected output for `interim-v1`:

```text
            title            | publication_status | lessons | published_materials
-----------------------------+--------------------+---------+---------------------
 Instructor Draft            | hidden             |       0 |                   0
 Introduction to Programming | published          |       2 |                   2
 Programming Practice        | published          |       2 |                   2
```

`final-v1` adds the row
`Python Fundamentals | published | 20 | 20` (21 after Step 6). Its accounts,
graph and Enrollments:

```bash
compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT email, role, account_status FROM public.users ORDER BY email;
SELECT (SELECT count(*) FROM public.skills) AS skills,
       (SELECT count(*) FROM public.skill_prerequisites) AS prerequisites,
       (SELECT count(*) FROM public.lesson_skills) AS mappings,
       (SELECT count(*) FROM public.enrollments WHERE status = 'active') AS enrollments,
       (SELECT count(*) FROM public.learner_skill_masteries) AS masteries;
SQL
```

Expected: six active accounts (`admin@` admin; `instructor@` instructor;
`learner@`, `p1@`, `p2@`, `p3@` learner), then `20 | 24 | 20 | 3 | 0`. The seeds
themselves reject a cycle, an unmapped published Lesson or any skill, edge or
mapping of `course/python` outside the approved graph.

Application check, with the backend running:

```bash
curl -s 'http://localhost:8080/api/courses?q=Demo%20course'
```

The response lists exactly Introduction to Programming and Programming Practice,
each with `"instructorDisplayName":"Instructor Demo"`, and never Instructor Draft.
Sign in through the frontend with each seeded account and the password you chose
for it in Step 3. Successful SQL loading alone does not prove login: the backend
checks the stored hash at sign-in.

## Step 6 — Provision the L01 slides (`final-v1`)

ADR013 §4.2 gives Lesson L01 of `course/python` the sample PDF as a file material.
It is uploaded through the application's CT09 operations, so the backend chooses
the material ID and the object key, stores the bytes in MinIO before it commits the
row, and enforces access on download. Never write the object to MinIO directly or
insert a `file` row yourself.

This needs the disposable Compose backend and MinIO from [docs/setup.md](../docs/setup.md)
and the `final-v1` seeds. Run one operator invocation at a time per database.
The Python 3 procedure below uses JSON encoding, preserves passwords exactly and
keeps passwords, access tokens and refresh cookies in process memory only. It
never prints request/response bodies, follows redirects, uses environment proxies,
retries a mutation, or writes credentials or PDF downloads to temporary files.
The existing input file is read; readback and checksum verification happen in memory.

The default action is **inspect** (no material mutation). For an initial upload,
select `upload` explicitly. The procedure first reads the owning Instructor's L01
and refuses to upload when a file material already exists. It prints the returned
application-generated material ID before publication; this ID is not a credential.
For a failed publication or download, select `resume` with that exact existing ID:
owner-preview bytes are verified before publication and P1 readback afterwards.
A published material is verified without another publication write.

```bash
export CODEMASTERY_SEED_PDF_ACTION=inspect
# Initial operation only, after inspection: export CODEMASTERY_SEED_PDF_ACTION=upload
# Recovery of an existing material:
# export CODEMASTERY_SEED_PDF_ACTION=resume CODEMASTERY_SEED_MATERIAL_ID=<returned UUID>
```

The script prompts without echo. Automated local verification may instead supply
`CODEMASTERY_SEED_INSTRUCTOR_PASSWORD` and `CODEMASTERY_SEED_P1_PASSWORD` through the
environment; never put their values in argv, files, reports or shell tracing.
`CODEMASTERY_SEED_API_URL` may override the default `http://localhost:8080` only with
this operator's loopback HTTP backend. Each request has a 30-second local timeout
and a 1 MiB response budget, sufficient for the recorded PDF; these are procedure
bounds, not API or production configuration.

<!-- SD-F-IR-03-05: executable provisioning procedure, regression-tested verbatim. -->
```bash
python3 - <<'PY'
import getpass
import hashlib
import json
import os
import re
import sys
import signal
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

# One operator at a time per disposable database. No HTTP mutation is retried.
API = os.environ.get("CODEMASTERY_SEED_API_URL", "http://localhost:8080")
COURSE = "68ea4540-e9da-5605-9054-28b29afa0992"
LESSON = "28a0dcbc-cc05-59f7-bcf2-7ab591581299"
TITLE = "Slides: Variable Declaration and Naming Conventions"
SHA256 = "c4b05622b87a9b45e46563a87fba220e27c7da6a89340cd0c3f409bcadb9935d"
ACTION = os.environ.get("CODEMASTERY_SEED_PDF_ACTION", "inspect")

class Stop(Exception):
    pass

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, target):
        return None

# Do not forward passwords/tokens through an environment proxy or a redirect.
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

def request(path, operation, token=None, method="GET", data=None,
            content_type="application/json", expected=200, upload=False):
    headers = {"Content-Type": content_type}
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    try:
        with opener.open(urllib.request.Request(API + path, data=data, headers=headers,
                                               method=method), timeout=30) as response:
            if response.status != expected:
                raise Stop(operation + " returned an unexpected status; stopped.")
            body = response.read(1024 * 1024 + 1)
            if len(body) > 1024 * 1024:
                raise Stop(operation + " exceeded the local response budget; stopped.")
            return response.headers, body
    except urllib.error.HTTPError as failure:
        if upload and not 400 <= failure.code < 500:
            raise Stop("Upload outcome UNKNOWN. Run inspect; do not upload again.") from None
        raise Stop(operation + " rejected (HTTP " + str(failure.code) + "); stopped.") from None
    except Stop:
        if upload:
            raise Stop("Upload outcome UNKNOWN. Run inspect; do not upload again.") from None
        raise
    except Exception:
        if upload:
            raise Stop("Upload outcome UNKNOWN. Run inspect; do not upload again.") from None
        raise Stop(operation + " transport failed; stopped. Reconcile any earlier mutation.") from None

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result

def json_response(response, operation, upload=False):
    headers, body = response
    try:
        if headers.get_content_type() != "application/json":
            raise ValueError("JSON media type")
        value = json.loads(body.decode("utf-8"), object_pairs_hook=unique_object)
        if not isinstance(value, dict):
            raise ValueError("JSON object")
        return value
    except Exception:
        if upload:
            raise Stop("Upload outcome UNKNOWN: invalid response. Run inspect; do not upload again.") from None
        raise Stop(operation + " returned invalid JSON; stopped.") from None

def identifier(value):
    try:
        parsed = uuid.UUID(value)
        if str(parsed) != value or parsed.version != 4:
            raise ValueError("canonical application UUID")
        return value
    except Exception:
        raise Stop("Invalid material identity; stopped. Reconcile before any upload retry.") from None

def material(value, publication=None):
    if (not isinstance(value, dict)
            or set(value) != {"id", "title", "materialType", "publicationStatus"}
            or value["title"] != TITLE or value["materialType"] != "file"
            or not isinstance(value["publicationStatus"], str)
            or value["publicationStatus"] not in {"hidden", "published"}
            or publication is not None and value["publicationStatus"] != publication):
        raise Stop("Unexpected file metadata; stopped. Inspect the owning Lesson before retrying.")
    identifier(value["id"])
    return value

def sign_in(account, expected_id, role):
    name = "CODEMASTERY_SEED_" + account.upper() + "_PASSWORD"
    password = os.environ[name] if name in os.environ else getpass.getpass("Password for " + account + ": ")
    body = json.dumps({"email": account + "@example.test", "password": password}, ensure_ascii=False).encode("utf-8")
    value = json_response(request("/api/auth/login", "Sign-in", method="POST", data=body), "Sign-in")
    token = value.get("accessToken")
    user = value.get("user")
    if (not isinstance(token, str) or len(token) > 16384
            or re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", token) is None
            or not isinstance(user, dict) or user.get("id") != expected_id or user.get("role") != role):
        raise Stop("Invalid sign-in response; stopped.")
    return token

def verify_file(path, token):
    headers, body = request(path, "Protected download", token)
    try:
        valid = (headers.get_content_type() == "application/pdf"
                 and headers.get("Cache-Control") == "no-store"
                 and headers.get("X-Content-Type-Options") == "nosniff"
                 and headers.get("Content-Disposition", "").startswith("attachment;")
                 and "filename=" in headers.get("Content-Disposition", "")
                 and "filename*=UTF-8''" in headers.get("Content-Disposition", "")
                 and int(headers.get("Content-Length", "-1")) == len(body)
                 and hashlib.sha256(body).hexdigest() == SHA256)
    except Exception:
        valid = False
    if not valid:
        raise Stop("Protected PDF headers/bytes failed verification; stopped.")

def main():
    parsed = urllib.parse.urlsplit(API)
    if (parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"}
            or parsed.username is not None or parsed.password is not None
            or parsed.path or parsed.query or parsed.fragment or ACTION not in {"inspect", "upload", "resume"}):
        raise Stop("Use the documented loopback API and inspect/upload/resume action.")
    pdf = Path("sample-data/materials/sample-slides.pdf").read_bytes()
    if hashlib.sha256(pdf).hexdigest() != SHA256:
        raise Stop("The source PDF checksum is wrong; no request was sent.")
    instructor = sign_in("instructor", "1eacfb14-e494-596c-bb53-3d012e7caf72", "Instructor")
    learner = sign_in("p1", "f53df771-d0d4-535d-ba42-4179e90806c9", "Learner")
    owned_path = "/api/instructor/courses/" + COURSE + "/lessons/" + LESSON
    lesson = json_response(request(owned_path, "Owned Lesson read", instructor), "Owned Lesson read")
    if lesson.get("id") != LESSON or not isinstance(lesson.get("materials"), list):
        raise Stop("Unexpected owned Lesson; stopped.")
    if any(not isinstance(row, dict) for row in lesson["materials"]):
        raise Stop("Invalid material list; stopped.")
    files = [material(row) for row in lesson["materials"] if row.get("materialType") == "file"]
    if len(files) > 1:
        raise Stop("Multiple file materials exist. Reconcile manually; no upload was performed.")
    if ACTION == "inspect":
        if not files:
            print("No file material is currently listed. Select upload only for an initial, serialized operation.")
        else:
            row = files[0]
            print("Material ID: " + row["id"] + " (" + row["publicationStatus"] + ").")
            print("To resume this existing material, select resume and its exact material ID.")
        return
    if ACTION == "upload":
        if files:
            raise Stop("A file already exists. Use inspect/resume; no second upload was performed.")
        boundary = "codemastery-" + uuid.uuid4().hex
        data = ("--" + boundary + '\r\nContent-Disposition: form-data; name="title"\r\n\r\n' + TITLE
                + "\r\n--" + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="sample-slides.pdf"'
                + "\r\nContent-Type: application/pdf\r\n\r\n").encode("utf-8") + pdf + ("\r\n--" + boundary + "--\r\n").encode("ascii")
        value = json_response(request(owned_path + "/file-materials", "Upload", instructor,
                                      "POST", data, "multipart/form-data; boundary=" + boundary, 201, True), "Upload", True)
        try:
            row = material(value, "hidden")
        except Stop:
            raise Stop("Upload outcome UNKNOWN: invalid metadata. Run inspect; do not upload again.") from None
        print("Material ID: " + row["id"] + " (hidden).", flush=True)
    else:
        selected = identifier(os.environ.get("CODEMASTERY_SEED_MATERIAL_ID", ""))
        if len(files) != 1 or files[0]["id"] != selected:
            raise Stop("Resume identity does not match the owned Lesson. No mutation was performed.")
        row = files[0]
    path = "/api/courses/" + COURSE + "/lessons/" + LESSON + "/materials/" + row["id"] + "/file"
    verify_file(path, instructor)
    if row["publicationStatus"] == "hidden":
        value = json_response(request(owned_path + "/file-materials/" + row["id"] + "/publication",
                                      "Publication", instructor, "PUT", b'{"publicationStatus":"published"}'), "Publication")
        published = material(value, "published")
        if published["id"] != row["id"]:
            raise Stop("Publication identity changed; stopped.")
    verify_file(path, learner)
    print("Protected P1 PDF verified: SHA-256 " + SHA256)

def interrupted(signum, frame):
    raise KeyboardInterrupt

signal.signal(signal.SIGTERM, interrupted)
try:
    main()
except KeyboardInterrupt:
    print("Interrupted: an in-flight mutation may have an UNKNOWN outcome. Run inspect; do not upload again.", file=sys.stderr)
    sys.exit(130)
except Stop as failure:
    print(str(failure), file=sys.stderr)
    sys.exit(1)
except Exception:
    print("Provisioning failed; diagnostics withheld. Inspect and reconcile before retrying.", file=sys.stderr)
    sys.exit(1)
PY
```

A verified upload returns **201, hidden**; the script uses that returned ID, verifies
the bytes through owner preview, explicitly publishes and checks enrolled P1's
protected readback, required download headers and the recorded SHA-256.

**Unknown upload outcome:** a network failure, 5xx or unvalidated response does
not prove that no material was committed. Stop; do not select `upload` again.
Run `inspect` after the operation has settled. If exactly one expected file exists,
select `resume` with its returned ID; the script checks its bytes before publishing.
Multiple files, unexpected content or an uncertain absence need manual reconciliation
through the existing owning API, or a fresh disposable database. An empty read while
a prior operation may still be in flight is not permission to retry. There is no new
API idempotency contract: a second POST can create a second material, and separate
operators/machines must serialize this once-per-database provisioning themselves.
A known HTTP rejection also stops the procedure; no subsequent step runs implicitly.

On every error, the process exits nonzero with safe diagnostics. No predictable
temporary filename, secret file or download file is created, so interruption and
concurrent verification invocations cannot overwrite or remove another local file.
Do not enable shell/Python tracing while handling credentials. Clear optional inputs
when finished:

```bash
unset CODEMASTERY_SEED_INSTRUCTOR_PASSWORD CODEMASTERY_SEED_P1_PASSWORD \
  CODEMASTERY_SEED_PDF_ACTION CODEMASTERY_SEED_MATERIAL_ID CODEMASTERY_SEED_API_URL
```

## Re-running and conflicts

Running the files again with the same inputs changes nothing. Partial earlier
loads are completed; for example, if only the Instructor exists, a re-run adds the
Learner, and a `final-v1` run over `interim-v1` adds only the `final-v1` rows.

| Message | Meaning and action |
| --- | --- |
| `Required input seed_target_system_identifier is missing.` / `Required psql variable ... is missing.` | An input is unset in the shell that ran `compose exec`. Export it and pass it with `-e`. |
| `target verification failed: malformed expected system identifier` | The identifier is empty or not a decimal number. Repeat Step 2. |
| `target verification failed: connected cluster is not the expected target` | psql reached a different server than the one whose control file you read. Stop and check which database you are connected to. |
| `target verification failed: database is not attested as a disposable seed target` | The database has no valid attestation for this cluster. Attest it only if it is a disposable local database. |
| `target verification failed: cluster identity is unavailable` | The server did not report its identity. The seed refuses to run. |
| `SD01 requires valid BCrypt $2b$14$ password hashes` | A hash is missing, truncated or uses another prefix or cost. Regenerate it with Step 3. |
| `SD01 requires seed_dataset interim-v1 or final-v1.` / `SD02 requires seed_dataset ...` | `CODEMASTERY_SEED_DATASET` is set to another value, or to an empty one. Use `final-v1` or unset it. |
| `SD01 final-v1 password hashes require seed_dataset final-v1.` / `SD02 adaptive policy inputs require seed_dataset final-v1.` | A `final-v1` input is set but the selector is not. Export `CODEMASTERY_SEED_DATASET=final-v1`, or unset the `final-v1` inputs to load `interim-v1`. |
| `SD01 final-v1 requires a distinct password hash per account` | Two accounts received the same hash. Choose a different password for each account and regenerate. |
| `SD01 User seed conflicts with an existing ID or canonical email` | A User with the same ID or email differs, often because the hash changed. Reuse the original hashes or use a new disposable database. |
| `SD02 requires the SD01 synthetic Instructor; load 01-users.sql first` | Load `01-users.sql` first. |
| `SD02 Instructor reference does not match the approved SD01 identity` | The Instructor row exists but is not the approved synthetic identity. |
| `SD02 Course / Chapter / Lesson / LearningMaterial <id> conflicts ...` | Existing content with that ID, or another row at the same Chapter or Lesson position, differs from the seed. For `course/python` this includes a different adaptive policy. |
| `Required input adaptive_... is missing.` / `SD02 requires a valid adaptive policy (ADR005 6.2)` | Export the three adaptive values of Step 4 and pass them with `-e`. |
| `Required psql variable admin_password_hash is missing.` (or `p1_`, `p2_`, `p3_`) | `final-v1` was selected without that hash. Generate it in Step 3 and pass it with `-e`. |
| `SD03 requires the SD02 course/python Lessons; load 02-course-python-fundamentals.sql first` | `course/python` or one of its twenty published Lessons is missing. |
| `SD03 Skill / prerequisite / Lesson mapping <id> conflicts ...` | A row with that ID, the same skill name or the same edge or pair differs from the seed. |
| `SD03 course/python already has skills, prerequisites or mappings outside the approved graph` | The Course was edited after loading. Use a new disposable database. |
| `SD05 requires the SD02 course/python Course ...` / `SD05 requires the three final-v1 profile Learners ...` | Load `01` and `02` with `CODEMASTERY_SEED_DATASET=final-v1` first. A locked or changed profile account is also refused. |
| `SD05 Enrollment <id> conflicts ...` | A profile already has a different Enrollment in `course/python`. |

Do not edit or delete rows to make a seed pass. Start from a new disposable
database instead. The destructive local reset in
[docs/setup.md](../docs/setup.md#persistence-shutdown-and-reset) removes **all**
local data; use it only if you intend to discard everything.

`01` first verifies the disposable target, then applies transaction-local PostgreSQL
logging controls before any credential-bearing statement. Built-in statement,
duration, parameter, debug and ordinary server-error logging are disabled only for
that seed transaction; psql retains terse client errors. Commit/rollback restores
the connection's previous settings. The operator needs permission to set these
privileged session parameters; otherwise `01` fails before interpolating any hash.
Database-wide, role-wide and production logging are never changed. The supported
target is the repository's standard PostgreSQL 17 image without extra auditing or
tracing extensions; privileged server/host instrumentation remains outside this
procedure's trust boundary. Do not run psql/shell tracing or SQL-capturing extensions
around credential loading. Regression tests examine both actual client output and
PostgreSQL container logs under injected failures and confirm logging restoration.

## Removing the attestation

After seeding, you can remove the attestation so that no further seed run is
accepted until you attest the database again:

```bash
compose exec -T postgres sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT format('ALTER DATABASE %I RESET codemastery.seed_target', current_database()) \gexec
SQL
```

## Sample materials

`materials/sample-slides.pdf` is a 12-page English lesson deck, "Python
Fundamentals — Lesson 1.1: Variable Declaration & Naming Conventions" (version
2026.3). It covers object references, assignment syntax, identifier rules and PEP 8
naming, common mistakes, two knowledge-check questions with explained answers, and
a debugging exercise with a suggested fix.

| Property | Value |
| --- | --- |
| SHA-256 | `c4b05622b87a9b45e46563a87fba220e27c7da6a89340cd0c3f409bcadb9935d` |
| Format | PDF 1.4, 12 pages of 960 × 540 pt, not encrypted, no forms or JavaScript; document author "CodeMastery Project Team" |
| Fonts | Plus Jakarta Sans (SIL Open Font License 1.1) and PT Mono (ParaType Free Font License, which permits embedding in documents) |
| Third-party content | No third-party images, logos or artwork. The deck cites PEP 8 and *The Python Language Reference* (Python Software Foundation) as references. |

### Permitted use and provenance

The sample slides were prepared for CodeMastery with AI assistance. The original
deck was contributed by Nguyen Anh Tai (commit `348d2c2`, 24 October 2026). On
30 October 2026 the Project Leader and the contributing team member confirmed that
CodeMastery may use and modify the material for project use, adaptation, teaching
demonstrations, testing and academic evaluation in CO3103.

The current version adapts that contribution. Its structure, examples and visual
design are kept, and the following changes were made with AI assistance under the
Project Leader's authorization:

- both knowledge-check answers are corrected;
- inaccurate statements about chained assignment, reassignment and the unpacking
  error are fixed;
- the identifier rules now state that Python 3 accepts Unicode letters, and present
  ASCII-only names as the recommended practice;
- an unverified instructor name and course code are replaced by "CodeMastery
  Project Team";
- explained answers are added.

No open-source licence or public-domain status is claimed, and the confirmation
does not grant redistribution rights outside the project. The original file remains
in the repository history.

### Storage boundary

The slides are a repository file. No seed row references them. In `final-v1` they
are uploaded to Lesson L01 of `course/python` through the CT09 API (Step 6, ADR013
§4.2), which stores the object in MinIO first and only then commits the file
material with an application-chosen ID and object key. The download is checked
against the SHA-256 above. The `interim-v1` identity once reserved for a future
file row (`course/intro/chapter/1/lesson/1/material/2`,
`045fe941-10fd-5916-98ad-04f06a6edfc7`, ADR002 M2) is not used: the CT09 upload
creates its own ID, and ADR013 places the slides on L01 of `course/python`.

The deck's topic (variables and naming) is closer to L02 than to L01 “Program
Output”; L01 follows the approved ADR013 §4.2 placement.

No external-video material is seeded, because no approved video URL exists.

## Content provenance

All seeded text is original synthetic content written for CodeMastery. The text
materials were drafted with AI assistance under the Project Leader's delegated
authorization on 30 October 2026 and are subject to the normal CODEOWNER review.
They contain no third-party text. The `.test` email addresses use a reserved
domain and cannot reach a real mailbox.

## Current limitations

- **Verification scope.** Successful SQL loading proves that the rows exist and
  match the dataset. Sign-in, catalogue, membership and protected file delivery
  are verified separately through the application. The automated evidence is
  recorded in [docs/testing.md](../docs/testing.md#sd01sd03-final-dataset-completion-and-author-self-audit--28-november-2026).
- **Adaptive behavior.** The profiles start with an Enrollment only. Their
  contrasting mastery and routes appear only after they take the placement quiz,
  which needs the SD04 questions and quizzes and the quiz-to-mastery processing;
  neither is implemented yet.
- **QA13.** QA13 acceptance on the team's verification environment is a separate,
  pending step. This guide and its automated tests do not replace it.
- **Environments.** Nothing here provisions object storage, deploys an environment
  or authorizes seeding outside your own disposable database.

## Final dataset `final-v1`

[ADR013 §§3–4](../docs/decisions/013-batch-08-contract-approvals.md#3-ct15f-admin-account--trial-accounts-per-role)
approves the Final dataset contract (`CT15-F-ADMIN-ACCOUNT-2026-11-15-r1`,
`CT15-F-ASSESSMENT-2026-11-15-r2` and `CT15-F-ADAPTIVE-2026-11-15-r1`, completed by
r2 in §16.1). **SD01, SD02 and SD03 are implemented**: the accounts in `01`,
`course/python` in `02`, its graph in `03`, the profile Enrollments in `05` and the
L01 slides through Step 6. SD04 (`04`) is not.

- **IDs.** UUIDv5 in the URL namespace for
  `https://codemastery.test/final-v1/<logical-key>`, insert-only and no-overwrite as
  above.
- **Accounts (`01-users.sql`).** The existing `interim-v1` Instructor and Learner rows
  are reused unchanged (a second row with the same email would conflict). New:
  `admin@example.test` (Administrator) and the profile learners `p1@`, `p2@` and
  `p3@example.test`. Every password is chosen at load time and only its hash is
  passed in through the environment; nothing is committed.
- **Course (`02`–`03`).** “Python Fundamentals”: six Chapters, twenty published
  Lessons L01–L20 with text materials, twenty skills S01–S20 mapped one to one, and
  the twenty-four prerequisite edges listed in ADR013 §4.2. The PDF material on L01
  is uploaded through the application after loading.
- **Assessment (`04`).** 120 questions (six per skill: two easy, two medium, two
  hard), a placement quiz over S01–S10, one mixed practice quiz per skill, three
  per-difficulty practice quizzes per skill (Easy, Medium, Hard; two questions each,
  sixty in total, ADR013 §19.6), one final test and two file assignments whose
  deadlines are load-time inputs.
- **Profiles (`05`).** Accounts and active enrolments only. Mastery, progress and
  recommendations are produced by taking the quizzes in the application, never
  seeded.
- **Adaptive policy.** Three load-time inputs validated by ADR005 §6.2. The approved
  demonstration values are `weak 1/2`, `mastered 4/5` and
  `minimumQuestionsPerSkill 6` (ADR013 §16.1); they are demonstration-course data,
  not a system default.
- **Deployed trial accounts.** Seeding a deployment target needs the Leader's
  authorization; credentials are handed to the lecturer only through a
  Leader-controlled secure location.
