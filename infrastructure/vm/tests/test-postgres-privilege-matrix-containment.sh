#!/usr/bin/env bash
# CI regression test for job "VM deployment PostgreSQL and containment tests" (deployment-script-postgres),
# step "Exercise the runtime privilege matrix on the real schema and failure containment" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-postgres-privilege-matrix-containment.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
suffix="${GITHUB_RUN_ID:-local}-$$"
network="codemastery-pr110-test-${suffix}"
container="${network}-postgres"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
chmod 700 "$work"
mkdir -m 700 "$work/env"
secret() { openssl rand -hex 24; }
bootstrap_password="$(secret)"
cat > "$work/env/.env" <<EOF
POSTGRES_DB=codemastery
POSTGRES_USER=cm_bootstrap
POSTGRES_PASSWORD=$bootstrap_password
DB_NAME=codemastery
DB_USER=cm_runtime
DB_PASSWORD=$(secret)
SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/codemastery
SPRING_FLYWAY_USER=cm_migrator
SPRING_FLYWAY_PASSWORD=$(secret)
EOF
chmod 600 "$work/env/.env"
docker network create --internal "$network" >/dev/null
start_postgres() {  # a fresh, empty cluster under the same name and network alias
  docker rm -f "$container" >/dev/null 2>&1 || true
  docker run --detach --name "$container" --network "$network" --network-alias postgres \
    --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=256m \
    -e POSTGRES_USER=cm_bootstrap -e POSTGRES_PASSWORD="$bootstrap_password" \
    -e POSTGRES_DB=codemastery postgres:17.11-bookworm >/dev/null
  for attempt in {1..60}; do
    if docker exec "$container" pg_isready -U cm_bootstrap -d codemastery >/dev/null 2>&1; then break; fi
    sleep 1
  done
  docker exec "$container" pg_isready -U cm_bootstrap -d codemastery >/dev/null
}
start_postgres
cat > "$work/exercise-db.sh" <<'BASH'
set -Eeuo pipefail
source "$1"
ENV_FILE="$2"
pgcompose() {
  [[ "$1" == exec ]] || return 97; shift
  [[ "$1" == -T ]] && shift
  [[ "$1" == postgres ]] && shift
  docker exec -i "$PGTEST_CONTAINER" "$@"
}
COMPOSE=(pgcompose)
APP="$(env_get DB_USER)"; APP_PW="$(env_get DB_PASSWORD)"
MIG="$(env_get SPRING_FLYWAY_USER)"; MIG_PW="$(env_get SPRING_FLYWAY_PASSWORD)"
as_app() { psql_as "$APP" "$APP_PW"; }
as_mig() { psql_as "$MIG" "$MIG_PW"; }
sqlstate() { grep -Eo 'ERROR:  [0-9A-Z]{5}' | head -1 | awk '{print $2}'; }
probe() {  # label expected(ok|SQLSTATE) SQL: runs as runtime inside a rolled-back transaction
  local out rc=0 got=ok
  out="$(printf 'BEGIN;\n%s\nROLLBACK;\n' "$3" | as_app 2>&1)" || rc=$?
  [[ $rc -eq 0 ]] || got="$(printf '%s' "$out" | sqlstate)"
  [[ "$got" == "$2" ]] || { echo "PROBE FAILED: $1 (expected $2, got ${got:-error})" >&2; exit 1; }
  printf '  probe ok: %-62s %s\n' "$1" "$got"
}
expect_failure() {  # label substring command...: the command must fail and mention substring
  local label="$1" want="$2" out; shift 2
  if out="$( ("$@") 2>&1 )"; then echo "NOT REJECTED: $label" >&2; exit 1; fi
  [[ "$out" == *"$want"* ]] || { echo "WRONG REJECTION: $label"; printf '%s\n' "$out" | tail -5; exit 1; } >&2
  printf '  rejected: %-60s [%s]\n' "$label" "$want"
}
admin() { psql_admin >/dev/null; }
acl_snapshot() { psql_admin <<<"SELECT string_agg(c.relname || '=' || coalesce(c.relacl::text,'') || coalesce((SELECT string_agg(a.attname || ':' || a.attacl::text, ';' ORDER BY a.attnum) FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attacl IS NOT NULL),''), ' ' ORDER BY c.relname) FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relkind='r';"; }

echo "== fresh provisioning (no schema yet) and Flyway-equivalent migration as the migrator"
provision_db_roles >/dev/null
as_mig >/dev/null <<'SQL'
CREATE TABLE public.flyway_schema_history (installed_rank integer PRIMARY KEY, version varchar(50),
  description varchar(200) NOT NULL, type varchar(20) NOT NULL, script varchar(1000) NOT NULL,
  checksum integer, installed_by varchar(100) NOT NULL, installed_on timestamp NOT NULL DEFAULT now(),
  execution_time integer NOT NULL, success boolean NOT NULL);
SQL
n=0
for f in "$MIGRATIONS"/V*.sql; do
  n=$((n+1))
  { printf 'BEGIN;\n'; cat "$f"; printf "INSERT INTO public.flyway_schema_history VALUES (%d,'%d','m','SQL','%s',0,current_user,now(),1,true);\nCOMMIT;\n" "$n" "$n" "${f##*/}"; } | as_mig >/dev/null
done
echo "  applied $n migrations"
# The violation report is sorted and capped at 25 lines; assert an early, stable entry
# (r4 adds lesson_progress INSERT columns, which push users.email beyond the cap).
expect_failure "migrated tables before hardening (no runtime grants yet)" "column privilege INSERT on public.enrollments.course_id: expected true" verify_runtime_privileges complete
harden_after_migration
verify_role_separation >/dev/null
echo "  verify_role_separation: PASS (exact matrix)"

echo "== approved operations succeed as the runtime role"
admin <<'SQL'
INSERT INTO public.users VALUES ('00000000-0000-0000-0000-0000000000a1','instructor@example.test','x','Instructor','instructor','active');
INSERT INTO public.courses VALUES ('00000000-0000-0000-0000-0000000000c1','00000000-0000-0000-0000-0000000000a1','Course','D',NULL,'published',false,NULL),
                                  ('00000000-0000-0000-0000-0000000000c2','00000000-0000-0000-0000-0000000000a1','Empty','D',NULL,'published',false,NULL);
INSERT INTO public.chapters VALUES ('00000000-0000-0000-0000-0000000000d1','00000000-0000-0000-0000-0000000000c1','Ch',1);
INSERT INTO public.lessons VALUES ('00000000-0000-0000-0000-0000000000e1','00000000-0000-0000-0000-0000000000d1','L',NULL,1,'published');
INSERT INTO public.learning_materials VALUES ('00000000-0000-0000-0000-0000000000f1','00000000-0000-0000-0000-0000000000e1','M','file','published',NULL,'k','f.pdf',NULL);
SQL
probe "users: SELECT (login, JWT account check, names)"      ok "SELECT * FROM public.users;"
probe "users: INSERT (registration)"                         ok "INSERT INTO public.users VALUES (gen_random_uuid(),'l@example.test','h','L','learner','active');"
probe "courses/chapters/lessons: catalogue and syllabus"     ok "SELECT * FROM public.courses c JOIN public.chapters ch ON ch.course_id=c.id JOIN public.lessons l ON l.chapter_id=ch.id;"
probe "courses: membership row lock (FOR UPDATE)"            ok "SELECT id FROM public.courses WHERE publication_status='published' FOR UPDATE;"
probe "learning_materials: access-snapshot columns"          ok "SELECT m.id, m.publication_status FROM public.learning_materials m WHERE m.lesson_id IS NOT NULL;"
probe "enrollments: SELECT/INSERT/UPDATE status"             ok "INSERT INTO public.enrollments VALUES ('00000000-0000-0000-0000-0000000000b1','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000c1','active',NULL); UPDATE public.enrollments SET status='left' WHERE id='00000000-0000-0000-0000-0000000000b1'; SELECT * FROM public.enrollments;"
probe "users: AU07 User-first row lock (FOR UPDATE)"        ok "SELECT id FROM public.users WHERE email='instructor@example.test' FOR UPDATE;"
probe "users: AU07 reset password-hash update"               ok "UPDATE public.users SET password_hash='x' WHERE email='instructor@example.test';"
probe "password_reset_tokens: issue, re-read and consume"    ok "INSERT INTO public.password_reset_tokens VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000a1',decode(repeat('ab',32),'hex'),now(),now()+interval '30 minutes'); SELECT user_id, token_digest, issued_at, expires_at FROM public.password_reset_tokens WHERE token_digest=decode(repeat('ab',32),'hex'); DELETE FROM public.password_reset_tokens WHERE user_id='00000000-0000-0000-0000-0000000000a1' AND token_digest=decode(repeat('ab',32),'hex');"
probe "users: ADR014 sign-in account lock (FOR NO KEY UPDATE)" ok "SELECT id, password_hash, account_status FROM public.users WHERE id='00000000-0000-0000-0000-0000000000a1' FOR NO KEY UPDATE;"
probe "refresh_sessions/tokens: ADR014 sign-in, rotation, revoke, purge" ok "INSERT INTO public.refresh_sessions VALUES ('00000000-0000-0000-0000-0000000000a2','00000000-0000-0000-0000-0000000000a1',now(),now()+interval '7 days',NULL); INSERT INTO public.refresh_tokens VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000a2',decode(repeat('cd',32),'hex'),now(),now()+interval '1 day',NULL); SELECT id, session_id, token_digest, issued_at, expires_at, consumed_at FROM public.refresh_tokens WHERE token_digest=decode(repeat('cd',32),'hex'); SELECT id FROM public.refresh_sessions WHERE id='00000000-0000-0000-0000-0000000000a2' FOR UPDATE; SELECT id FROM public.refresh_sessions WHERE expires_at <= now() ORDER BY expires_at, id LIMIT 100 FOR UPDATE SKIP LOCKED; UPDATE public.refresh_tokens SET consumed_at=now() WHERE session_id='00000000-0000-0000-0000-0000000000a2' AND consumed_at IS NULL; INSERT INTO public.refresh_tokens VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000a2',decode(repeat('ce',32),'hex'),now(),now()+interval '1 day',NULL); UPDATE public.refresh_sessions SET revoked_at=now() WHERE user_id='00000000-0000-0000-0000-0000000000a1' AND revoked_at IS NULL; SELECT u.id FROM public.users u JOIN public.refresh_sessions s ON s.user_id=u.id WHERE s.revoked_at IS NULL; DELETE FROM public.refresh_tokens WHERE session_id='00000000-0000-0000-0000-0000000000a2'; DELETE FROM public.refresh_sessions WHERE id='00000000-0000-0000-0000-0000000000a2';"
probe "enrollments/lesson_progress: CO08 r4 completion and visit" ok "INSERT INTO public.enrollments VALUES ('00000000-0000-0000-0000-0000000000b2','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000c1','active',NULL,NULL); SELECT id FROM public.courses WHERE id='00000000-0000-0000-0000-0000000000c1' FOR UPDATE; SELECT id, status, resume_lesson_id FROM public.enrollments WHERE id='00000000-0000-0000-0000-0000000000b2' FOR UPDATE; INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000b2','00000000-0000-0000-0000-0000000000e1',true,false) ON CONFLICT (enrollment_id,lesson_id) DO UPDATE SET completed=true; INSERT INTO public.lesson_progress (id,enrollment_id,lesson_id,completed,mastery_skipped) VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000b2','00000000-0000-0000-0000-0000000000e1',true,false) ON CONFLICT (enrollment_id,lesson_id) DO UPDATE SET completed=true; SELECT l.id, COALESCE(p.completed,false), COALESCE(p.mastery_skipped,false) FROM public.lessons l LEFT JOIN public.lesson_progress p ON p.lesson_id=l.id AND p.enrollment_id='00000000-0000-0000-0000-0000000000b2'; UPDATE public.enrollments SET status='completed' WHERE id='00000000-0000-0000-0000-0000000000b2'; UPDATE public.enrollments SET resume_lesson_id='00000000-0000-0000-0000-0000000000e1', last_visited_at=clock_timestamp() WHERE id='00000000-0000-0000-0000-0000000000b2'; SELECT course_id, resume_lesson_id, last_visited_at FROM public.enrollments WHERE last_visited_at IS NOT NULL;"
# Accepted r4 capability (ADR012 §19.2 ceiling, docs/deployment.md r4): table-level INSERT also
# initializes mastery_skipped; the V002 check still forbids a skip without completion.
probe "lesson_progress: INSERT may set completed+skipped (accepted)" ok "INSERT INTO public.enrollments VALUES ('00000000-0000-0000-0000-0000000000b3','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000c2','active',NULL,NULL); INSERT INTO public.lesson_progress VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000b3','00000000-0000-0000-0000-0000000000e1',true,true);"
probe "lesson_progress: skip without completion (check constraint)" 23514 "INSERT INTO public.enrollments VALUES ('00000000-0000-0000-0000-0000000000b3','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000c2','active',NULL,NULL); INSERT INTO public.lesson_progress VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000b3','00000000-0000-0000-0000-0000000000e1',false,true);"
echo "== everything else is denied"
probe "users: change role"                                   42501 "UPDATE public.users SET role='admin';"
probe "users: change email"                                  42501 "UPDATE public.users SET email='x@example.test';"
probe "users: change account status"                         42501 "UPDATE public.users SET account_status='active';"
probe "users: change display name"                           42501 "UPDATE public.users SET display_name='x';"
probe "password_reset_tokens: rewrite expiry or digest"      42501 "UPDATE public.password_reset_tokens SET expires_at=expires_at;"
probe "password_reset_tokens: row lock (no UPDATE granted)"  42501 "SELECT id FROM public.password_reset_tokens FOR UPDATE;"
probe "password_reset_tokens: TRUNCATE"                      42501 "TRUNCATE public.password_reset_tokens;"
probe "refresh_sessions: extend lifetime or re-own a session" 42501 "UPDATE public.refresh_sessions SET expires_at=expires_at, user_id=user_id;"
probe "refresh_sessions: rewrite the creation time"           42501 "UPDATE public.refresh_sessions SET created_at=created_at;"
probe "refresh_tokens: rewrite digest, expiry or session"     42501 "UPDATE public.refresh_tokens SET token_digest=token_digest, expires_at=expires_at, session_id=session_id;"
probe "refresh_sessions/tokens: TRUNCATE"                     42501 "TRUNCATE public.refresh_tokens, public.refresh_sessions;"
probe "users: delete"                                        42501 "DELETE FROM public.users;"
probe "courses: change publication"                          42501 "UPDATE public.courses SET publication_status='hidden';"
probe "courses: change instructor ownership"                 42501 "UPDATE public.courses SET instructor_id=instructor_id;"
probe "courses: insert"                                      42501 "INSERT INTO public.courses SELECT * FROM public.courses WHERE false;"
probe "courses: re-key a referenced Course (FK NO ACTION)"   23503 "UPDATE public.courses SET id=gen_random_uuid() WHERE id='00000000-0000-0000-0000-0000000000c1';"
probe "chapters/lessons: modify structure"                   42501 "UPDATE public.lessons SET publication_status='published';"
probe "learning_materials: read payload/object keys"         42501 "SELECT object_key, text_content FROM public.learning_materials;"
probe "learning_materials: SELECT *"                         42501 "SELECT * FROM public.learning_materials;"
probe "enrollments: reassign learner/Course"                 42501 "UPDATE public.enrollments SET user_id=user_id;"
probe "enrollments: reassign Course or re-key"                42501 "UPDATE public.enrollments SET course_id=course_id, id=id;"
probe "enrollments: delete history"                          42501 "DELETE FROM public.enrollments;"
probe "lesson_progress: update mastery-skip flag (CT22.atomic)" 42501 "UPDATE public.lesson_progress SET mastery_skipped=true;"
probe "lesson_progress: re-target a progress row"            42501 "UPDATE public.lesson_progress SET enrollment_id=enrollment_id, lesson_id=lesson_id;"
probe "lesson_progress: delete history"                      42501 "DELETE FROM public.lesson_progress;"
probe "lesson_progress: TRUNCATE"                            42501 "TRUNCATE public.lesson_progress;"
probe "questions: read answer keys"                          42501 "SELECT correct_options FROM public.questions;"
probe "attempts: rewrite scores"                             42501 "UPDATE public.attempts SET score_percentage=100;"
probe "attempt_answers: delete"                              42501 "DELETE FROM public.attempt_answers;"
probe "submissions: self-grade"                              42501 "UPDATE public.submissions SET grade_percentage=100;"
probe "learner_skill_masteries: write"                       42501 "INSERT INTO public.learner_skill_masteries SELECT * FROM public.learner_skill_masteries WHERE false;"
probe "path_recommendations: read"                           42501 "SELECT 1 FROM public.path_recommendations;"
probe "notifications: read"                                  42501 "SELECT 1 FROM public.notifications;"
probe "audit_logs: erase"                                    42501 "DELETE FROM public.audit_logs;"
probe "audit_logs: forge"                                    42501 "INSERT INTO public.audit_logs VALUES (gen_random_uuid(),'00000000-0000-0000-0000-0000000000a1','x','x','x',now());"
probe "flyway_schema_history: read"                          42501 "SELECT 1 FROM public.flyway_schema_history;"
probe "any table: TRUNCATE"                                  42501 "TRUNCATE public.enrollments;"
probe "DDL in public"                                        42501 "CREATE TABLE public.x (id int);"

echo "== repeated provisioning is idempotent"
before="$(acl_snapshot)"
provision_db_roles >/dev/null; harden_after_migration; verify_role_separation >/dev/null
[[ "$(acl_snapshot)" == "$before" ]] || { echo "ACL changed on repeated provisioning" >&2; exit 1; }
echo "  ACLs identical after a second provision + harden + verify"

echo "== drift and escalation fail closed"
admin <<<"GRANT UPDATE, DELETE ON public.users TO $APP; GRANT UPDATE (title) ON public.courses TO $APP; GRANT SELECT ON public.questions TO $APP;"
expect_failure "owner-granted excess before reconciliation" "table privilege UPDATE on public.users: expected false" verify_runtime_privileges complete
drift="$(harden_after_migration)"
[[ "$drift" == *"excess: column privilege UPDATE on public.courses.title: expected false"* && "$drift" == *"excess: table privilege DELETE on public.users: expected false"* ]] \
  || { echo "drift was not reported: $drift" >&2; exit 1; }
verify_runtime_privileges complete >/dev/null
echo "  owner-granted excess (table and column level) reported, revoked and re-verified"
admin <<<"GRANT SELECT ON public.questions TO PUBLIC;"
harden_after_migration >/dev/null
expect_failure "SELECT on answer keys through PUBLIC" "table privilege SELECT on public.questions: expected false" verify_runtime_privileges complete
admin <<<"REVOKE SELECT ON public.questions FROM PUBLIC;"
admin <<<"CREATE ROLE cm_grantor NOLOGIN; GRANT UPDATE ON public.users TO cm_grantor WITH GRANT OPTION; SET ROLE cm_grantor; GRANT UPDATE ON public.users TO $APP; RESET ROLE;"
harden_after_migration >/dev/null
expect_failure "UPDATE users from another grantor survives reset" "table privilege UPDATE on public.users: expected false" verify_runtime_privileges complete
admin <<<"REVOKE UPDATE ON public.users FROM cm_grantor CASCADE; DROP ROLE cm_grantor;"
admin <<<"CREATE ROLE cm_group NOLOGIN; GRANT SELECT ON public.audit_logs TO cm_group; GRANT cm_group TO $APP;"
expect_failure "inherited role membership (hardening)" "hardening failed" harden_after_migration
expect_failure "inherited role membership (verification)" "role elevation detected" verify_role_separation
admin <<<"REVOKE cm_group FROM $APP; GRANT cm_group TO $APP WITH INHERIT FALSE;"
expect_failure "NOINHERIT membership (SET ROLE path)" "role elevation detected" verify_role_separation
admin <<<"REVOKE cm_group FROM $APP; REVOKE ALL ON public.audit_logs FROM cm_group; DROP ROLE cm_group;"
admin <<<"CREATE ROLE cm_other NOLOGIN; GRANT SELECT ON public.users TO $APP WITH GRANT OPTION;"
as_app >/dev/null <<<"GRANT SELECT ON public.users TO cm_other;"
expect_failure "runtime passed a grant on (no CASCADE)" "hardening failed" harden_after_migration
admin <<<"REVOKE SELECT ON public.users FROM $APP CASCADE; DROP ROLE cm_other;"
harden_after_migration >/dev/null
as_mig >/dev/null <<<"CREATE TABLE public.future_table (id uuid PRIMARY KEY);"
harden_after_migration >/dev/null
expect_failure "unreviewed table from a future migration" "unexpected relation public.future_table" verify_runtime_privileges complete
[[ "$(psql_admin <<<"SELECT has_any_column_privilege('$APP','public.future_table','SELECT,INSERT,UPDATE,REFERENCES') OR has_table_privilege('$APP','public.future_table','DELETE,TRUNCATE,TRIGGER');")" == f ]] \
  || { echo "future table received runtime privileges" >&2; exit 1; }
echo "  the unreviewed table received no runtime privilege"
as_mig >/dev/null <<<'DROP TABLE public.future_table; CREATE TABLE public."QuotedTable" (id integer);'
expect_failure "unreviewed quoted identifier" "unexpected relation public.QuotedTable" verify_runtime_privileges complete
as_mig >/dev/null <<<'DROP TABLE public."QuotedTable"; CREATE SEQUENCE public.unreviewed_seq;'
expect_failure "unreviewed sequence" "unexpected relation public.unreviewed_seq" verify_runtime_privileges complete
as_mig >/dev/null <<<"DROP SEQUENCE public.unreviewed_seq; CREATE VIEW public.user_view AS SELECT id FROM public.users;"
expect_failure "unreviewed view" "unexpected relation public.user_view" verify_runtime_privileges complete
as_mig >/dev/null <<<"DROP VIEW public.user_view; CREATE FUNCTION public.f() RETURNS int LANGUAGE sql SECURITY DEFINER AS 'SELECT 1';"
expect_failure "SECURITY DEFINER routine in public" "unexpected routine public.f()" verify_runtime_privileges complete
as_mig >/dev/null <<<"DROP FUNCTION public.f(); ALTER TABLE public.users ADD COLUMN mfa_secret text;"
expect_failure "column added to a table-level grant" "columns differ from the reviewed matrix on public.users" verify_runtime_privileges complete
as_mig >/dev/null <<<"ALTER TABLE public.users DROP COLUMN mfa_secret; DROP TABLE public.audit_logs;"
expect_failure "reviewed table missing after migration" "missing reviewed table public.audit_logs" verify_runtime_privileges complete
as_mig >/dev/null <<<"CREATE VIEW public.audit_logs AS SELECT id AS actor_id FROM public.users;"
expect_failure "reviewed name replaced by a view" "unexpected relation kind for public.audit_logs" verify_runtime_privileges complete
as_mig >/dev/null <<<"DROP VIEW public.audit_logs;"
sed -n '/CREATE TABLE public.audit_logs/,$p' "$MIGRATIONS"/V006__*.sql | as_mig >/dev/null
admin <<<"ALTER DEFAULT PRIVILEGES FOR ROLE $MIG GRANT SELECT ON TABLES TO $APP;"
expect_failure "default privileges for future tables" "default privileges grant future tables to the runtime role" verify_runtime_privileges complete
admin <<<"ALTER DEFAULT PRIVILEGES FOR ROLE $MIG REVOKE SELECT ON TABLES FROM $APP;"
admin <<<"GRANT MAINTAIN ON public.users TO PUBLIC;"
expect_failure "MAINTAIN through PUBLIC" "table privilege MAINTAIN on public.users: expected false" verify_runtime_privileges complete
admin <<<"REVOKE MAINTAIN ON public.users FROM PUBLIC;"
admin <<<"ALTER TABLE public.users OWNER TO cm_bootstrap;"
expect_failure "table owned outside the migrator" "hardening failed" harden_after_migration
admin <<<"ALTER TABLE public.users OWNER TO $MIG;"
harden_after_migration; verify_role_separation >/dev/null
[[ "$(acl_snapshot)" == "$before" ]] || { echo "ACL differs from the approved state after restoration" >&2; exit 1; }
psql_admin <<'SQL'
CREATE ROLE cm_escalated NOLOGIN CREATEDB;
GRANT cm_escalated TO cm_runtime;
SQL
if role_policy_sql | psql_admin >/dev/null 2>&1; then
  echo 'Role policy accepted indirect CREATEDB membership.' >&2; exit 1
fi
psql_admin <<'SQL'
REVOKE cm_escalated FROM cm_runtime;
DROP ROLE cm_escalated;
ALTER ROLE cm_migrator CREATEDB;
SQL
if role_policy_sql | psql_admin >/dev/null 2>&1; then
  echo 'Role policy accepted a migrator with CREATEDB.' >&2; exit 1
fi
psql_admin <<'SQL'
ALTER ROLE cm_migrator NOCREATEDB;
CREATE TABLE public.owner_probe (id integer);
ALTER TABLE public.owner_probe OWNER TO cm_runtime;
SQL
if role_policy_sql | psql_admin >/dev/null 2>&1; then
  echo 'Role policy accepted an application-owned table.' >&2; exit 1
fi
if application_grants_sql | psql_admin >/dev/null 2>&1; then
  echo 'Grant policy accepted an unexpected table owner.' >&2; exit 1
fi
psql_admin <<'SQL'
ALTER TABLE public.owner_probe OWNER TO cm_migrator;
DROP TABLE public.owner_probe;
SQL
echo 'MATRIX_TESTS_PASS: exact grants, denials, idempotency, drift, escalation and ownership.'
BASH
PGTEST_CONTAINER="$container" MIGRATIONS=backend/src/main/resources/db/migration \
  bash "$work/exercise-db.sh" "$script" "$work/env/.env"

# CT17-P6 r4 upgrade path on a fresh cluster: an existing V008 database with the r3
# grants of the previous release -> pre-migration provisioning -> V009 -> r4 hardening.
cat > "$work/exercise-upgrade.sh" <<'BASH'
set -Eeuo pipefail
source "$1"
ENV_FILE="$2"
pgcompose() {
  [[ "$1" == exec ]] || return 97; shift
  [[ "$1" == -T ]] && shift
  [[ "$1" == postgres ]] && shift
  docker exec -i "$PGTEST_CONTAINER" "$@"
}
COMPOSE=(pgcompose)
APP="$(env_get DB_USER)"; APP_PW="$(env_get DB_PASSWORD)"
MIG="$(env_get SPRING_FLYWAY_USER)"; MIG_PW="$(env_get SPRING_FLYWAY_PASSWORD)"
as_app() { psql_as "$APP" "$APP_PW"; }
as_mig() { psql_as "$MIG" "$MIG_PW"; }
sqlstate() { grep -Eo 'ERROR:  [0-9A-Z]{5}' | head -1 | awk '{print $2}'; }
probe() {  # label expected(ok|SQLSTATE) SQL: runs as runtime inside a rolled-back transaction
  local out rc=0 got=ok
  out="$(printf 'BEGIN;\n%s\nROLLBACK;\n' "$3" | as_app 2>&1)" || rc=$?
  [[ $rc -eq 0 ]] || got="$(printf '%s' "$out" | sqlstate)"
  [[ "$got" == "$2" ]] || { echo "PROBE FAILED: $1 (expected $2, got ${got:-error})" >&2; exit 1; }
  printf '  probe ok: %-62s %s\n' "$1" "$got"
}
expect_failure() {  # label substring command...: the command must fail and mention substring
  local label="$1" want="$2" out; shift 2
  if out="$( ("$@") 2>&1 )"; then echo "NOT REJECTED: $label" >&2; exit 1; fi
  [[ "$out" == *"$want"* ]] || { echo "WRONG REJECTION: $label"; printf '%s\n' "$out" | tail -5; exit 1; } >&2
  printf '  rejected: %-60s [%s]\n' "$label" "$want"
}
admin() { psql_admin >/dev/null; }
acl_snapshot() { psql_admin <<<"SELECT string_agg(c.relname || '=' || coalesce(c.relacl::text,'') || coalesce((SELECT string_agg(a.attname || ':' || a.attacl::text, ';' ORDER BY a.attnum) FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attacl IS NOT NULL),''), ' ' ORDER BY c.relname) FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relkind='r';"; }
migrate_to() {  # apply pending V*.sql up to version $1 as the migrator, recorded the way Flyway records them ('009')
  local max="$1" f v rank
  for f in "$MIGRATIONS"/V*.sql; do
    v="${f##*/V}"; v="${v%%__*}"
    (( 10#$v <= max )) || continue
    [[ "$(psql_admin <<<"SELECT count(*) FROM public.flyway_schema_history WHERE version='$v';")" == 0 ]] || continue
    rank="$(psql_admin <<<'SELECT coalesce(max(installed_rank), 0) + 1 FROM public.flyway_schema_history;')"
    { printf 'BEGIN;\n'; cat "$f"; printf "INSERT INTO public.flyway_schema_history VALUES (%d,'%s','m','SQL','%s',0,current_user,now(),1,true);\nCOMMIT;\n" "$rank" "$v" "${f##*/}"; } | as_mig >/dev/null
  done
}
r3_on_v008() {  # the previous release's r3 runtime grants on V008: this release's minus the r4-only rows
  application_grants_sql present | psql_admin >/dev/null
  admin <<SQL
REVOKE UPDATE (resume_lesson_id) ON public.enrollments FROM "$APP";
REVOKE ALL ON public.lesson_progress FROM "$APP";
SQL
}

echo "== fresh provisioning and V001-V008, as the previous release left it (r3 grants)"
provision_db_roles >/dev/null
as_mig >/dev/null <<'SQL'
CREATE TABLE public.flyway_schema_history (installed_rank integer PRIMARY KEY, version varchar(50),
  description varchar(200) NOT NULL, type varchar(20) NOT NULL, script varchar(1000) NOT NULL,
  checksum integer, installed_by varchar(100) NOT NULL, installed_on timestamp NOT NULL DEFAULT now(),
  execution_time integer NOT NULL, success boolean NOT NULL);
SQL
migrate_to 8
[[ "$(psql_admin <<<"SELECT count(*) FROM pg_attribute WHERE attrelid='public.enrollments'::regclass AND attname='last_visited_at';")" == 0 ]]
r3_on_v008
probe "r3: enrollments resume pointer is not yet writable"     42501 "UPDATE public.enrollments SET resume_lesson_id=NULL WHERE false;"
probe "r3: lesson_progress is not yet readable"                42501 "SELECT 1 FROM public.lesson_progress;"
echo "  V008_BASELINE_PASS"
# The original defect: granting the whole r4 matrix before V009 runs.
out="$( { printf 'BEGIN;\n'; application_grants_sql complete; printf 'ROLLBACK;\n'; } | psql_admin 2>&1 )" && { echo "Whole-matrix grant succeeded on V008" >&2; exit 1; }
[[ "$out" == *'column "last_visited_at" of relation "enrollments" does not exist'* ]] || { echo "Unexpected V008 grant failure: $out" >&2; exit 1; }
echo "  rejected: whole r4 matrix on V008 (the pre-fix provisioning path)   [last_visited_at does not exist]"

echo "== pre-migration provisioning on the exact V008 predecessor"
provision_db_roles >/dev/null
verify_runtime_privileges present >/dev/null
probe "r4 before V009: Enrollment lock and resume pointer"      ok "SELECT id FROM public.enrollments WHERE false FOR UPDATE; UPDATE public.enrollments SET resume_lesson_id=NULL WHERE false;"
probe "r4 before V009: progress read, insert and completion"    ok "SELECT 1 FROM public.lesson_progress; INSERT INTO public.lesson_progress SELECT * FROM public.lesson_progress WHERE false; UPDATE public.lesson_progress SET completed=true WHERE false;"
probe "r4 before V009: mastery-skip flag stays denied"          42501 "UPDATE public.lesson_progress SET mastery_skipped=true WHERE false;"
expect_failure "complete verification before V009" "columns differ from the reviewed matrix on public.enrollments" verify_runtime_privileges complete
before="$(acl_snapshot)"
provision_db_roles >/dev/null
[[ "$(acl_snapshot)" == "$before" ]] || { echo "ACL changed on repeated V008 provisioning" >&2; exit 1; }
echo "  repeated pre-migration provisioning leaves identical ACLs"

echo "== anything other than the exact V008 predecessor fails closed"
admin <<<"UPDATE public.flyway_schema_history SET version='9' WHERE version='008';"
expect_failure "history out of sequence (no 008)" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"UPDATE public.flyway_schema_history SET version='008' WHERE version='9';"
admin <<<"INSERT INTO public.flyway_schema_history VALUES (99,'009','m','SQL','V009__x.sql',0,'x',now(),1,false);"
expect_failure "failed V009 attempt in history" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"UPDATE public.flyway_schema_history SET success=true WHERE installed_rank=99;"
expect_failure "history claims V009 without its column" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"DELETE FROM public.flyway_schema_history WHERE installed_rank=99;"
admin <<<"DELETE FROM public.flyway_schema_history WHERE version='007';"
expect_failure "history with a missing earlier migration" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"INSERT INTO public.flyway_schema_history VALUES (7,'007','m','SQL','V007__x.sql',0,'x',now(),1,true);"
admin <<<"INSERT INTO public.flyway_schema_history VALUES (98,NULL,'r','SQL','R__x.sql',0,'x',now(),1,true);"
expect_failure "unexpected repeatable migration in history" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"DELETE FROM public.flyway_schema_history WHERE installed_rank=98;"
admin <<<"ALTER TABLE public.flyway_schema_history RENAME TO flyway_history_moved;"
expect_failure "column absent without Flyway history" "PostgreSQL role provisioning failed" provision_db_roles
admin <<<"ALTER TABLE public.flyway_history_moved RENAME TO flyway_schema_history;"
as_mig <<<"ALTER TABLE public.enrollments ADD COLUMN unexpected_column integer;"
expect_failure "unreviewed column on the predecessor" "columns differ from the reviewed matrix on public.enrollments" provision_db_roles
as_mig <<<"ALTER TABLE public.enrollments DROP COLUMN unexpected_column;"
admin <<<"GRANT SELECT ON public.questions TO PUBLIC;"
expect_failure "excess privilege through PUBLIC on the predecessor" "table privilege SELECT on public.questions: expected false" provision_db_roles
admin <<<"REVOKE SELECT ON public.questions FROM PUBLIC;"
admin <<<"GRANT UPDATE (last_visited_at) ON public.enrollments TO \"$APP\";" 2>/dev/null \
  && { echo "Granted a privilege on a column that does not exist" >&2; exit 1; }
provision_db_roles >/dev/null
[[ "$(acl_snapshot)" == "$before" ]] || { echo "ACL changed after the drift cases" >&2; exit 1; }
echo "  exact predecessor restored; provisioning passes again with identical ACLs"

echo "== Flyway V009, then r4 hardening and complete verification"
migrate_to 9
expect_failure "complete verification before hardening" "column privilege UPDATE on public.enrollments.last_visited_at: expected true" verify_runtime_privileges complete
harden_after_migration
verify_role_separation >/dev/null
verify_runtime_privileges complete >/dev/null
probe "r4 after V009: visit writes pointer and server time"     ok "UPDATE public.enrollments SET resume_lesson_id=NULL, last_visited_at=clock_timestamp() WHERE false;"
probe "r4 after V009: re-owning an Enrollment stays denied"     42501 "UPDATE public.enrollments SET user_id=user_id WHERE false;"

echo "== an already migrated V009 database defers nothing and stays idempotent"
[[ "$(runtime_deferred_columns present)" == "ARRAY[]::text[]" ]]
before="$(acl_snapshot)"
provision_db_roles >/dev/null; harden_after_migration; verify_role_separation >/dev/null
[[ "$(acl_snapshot)" == "$before" ]] || { echo "ACL changed on repeated V009 provisioning" >&2; exit 1; }
as_mig <<<"ALTER TABLE public.enrollments DROP COLUMN last_visited_at;"
expect_failure "V009 recorded but its column dropped" "PostgreSQL role provisioning failed" provision_db_roles
echo 'UPGRADE_TESTS_PASS: V008/r3 -> pre-migration provisioning -> V009 -> exact r4; drift fails closed.'
BASH
start_postgres
PGTEST_CONTAINER="$container" MIGRATIONS=backend/src/main/resources/db/migration \
  bash "$work/exercise-upgrade.sh" "$script" "$work/env/.env"

cat > "$work/exercise-containment.sh" <<'BASH'
set -Eeuo pipefail
source "$1"
WAIT_TIMEOUT=10
INITIALIZER_QUALIFICATION=synthetic-test
RELEASE_COMPLETE=no
stop_count=0
test_compose() {
  case "$1" in
    stop)
      stop_count=$((stop_count + 1))
      printf '%s\n' "$*" >> "$CONTAINMENT_MARKER"
      [[ "$stop_count" -ne 2 || "${TEST_STOP_FAIL:-no}" != yes ]]
      ;;
    up) return 0 ;;
    ps) printf 'synthetic-minio-init\n' ;;
    *) return 97 ;;
  esac
}
docker() { [[ "$1" == wait ]] || return 97; printf '23\n'; }
timeout() { shift; "$@"; }
assert_release_configuration() { :; }
verify_images() { :; }
verify_container_images() { :; }
provision_db_roles() { :; }
COMPOSE=(test_compose)
trap contain_failure EXIT
quiesce_application
run_provision
BASH
run_containment_case() {
  local mode="$1" marker="$work/$1.stop-log" output
  if output="$(CONTAINMENT_MARKER="$marker" TEST_STOP_FAIL="$mode" bash \
      "$work/exercise-containment.sh" "$script" 2>&1)"; then
    echo "Expected synthetic provisioning failure for stop mode $mode." >&2
    exit 1
  fi
  [[ "$output" == *"minio-init exited with code 23"* ]]
  [[ "$(wc -l < "$marker" | tr -d ' ')" == 2 ]]
  if [[ "$mode" == yes ]]; then
    [[ "$output" == *"ingress containment is NOT verified"* ]]
    echo 'CONTAINMENT_TEST_PASS: failed stop is reported as unverified.'
  else
    [[ "$output" == *"Containing application services"* ]]
    echo 'CONTAINMENT_TEST_PASS: provisioning failure stops application services.'
  fi
}
run_containment_case no
run_containment_case yes
