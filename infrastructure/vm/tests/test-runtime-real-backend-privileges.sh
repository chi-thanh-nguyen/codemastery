#!/usr/bin/env bash
# CI regression test for job "VM runtime privileges with the real backend" (deployment-script-runtime),
# step "Exercise real Auth, catalogue and membership under the approved matrix" in .github/workflows/ci-vm.yml.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-runtime-real-backend-privileges.sh
set -euo pipefail
count_permission_errors() {
  grep -ciE 'permission denied|SQLState:[[:space:]]*42501([^[:alnum:]_]|$)' || [[ $? == 1 ]]
}
export -f count_permission_errors
# Match the PostgreSQL diagnostic, including the SQLState form Hibernate logs.
# The same digits can occur in timestamps and correlation IDs.
[[ "$(printf '%s\n' '2026-11-25T08:39:11.263425019Z' 'SQLState: 425012' \
  'SQL Error: 0, SQLState: 42501' 'ERROR: permission denied for table courses' \
  | count_permission_errors)" == 2 ]]

script=infrastructure/vm/deploy.sh
work="$(mktemp -d)"
suffix="${GITHUB_RUN_ID:-local}-$$"
network="codemastery-runtime-test-${suffix}"
container="${network}-postgres"
cleanup() {
  docker rm -f "${container}-backend" "$container" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
chmod 700 "$work"
mkdir -m 700 "$work/env" "$work/http"
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
# Not --internal: the backend API is published to the runner's loopback only.
docker network create "$network" >/dev/null
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
cat > "$work/exercise-runtime.sh" <<'BASH'
set -Eeuo pipefail
source "$1"
ENV_FILE="$2"; work="$3"
pgcompose() {
  [[ "$1" == exec ]] || return 97; shift
  [[ "$1" == -T ]] && shift
  [[ "$1" == postgres ]] && shift
  docker exec -i "$PGTEST_CONTAINER" "$@"
}
COMPOSE=(pgcompose)
backend="${PGTEST_CONTAINER}-backend"
port="${BACKEND_PORT:-18080}"
api="http://127.0.0.1:${port}"
cleanup_backend() { docker rm -f "$backend" >/dev/null 2>&1 || true; }
[[ "${KEEP_BACKEND:-no}" == yes ]] || trap cleanup_backend EXIT
check() { [[ "$2" == "$3" ]] || { echo "CHECK FAILED: $1 (expected $3, got $2)" >&2; docker logs "$backend" 2>&1 | tail -20 >&2; exit 1; }; printf '  ok: %-64s %s\n' "$1" "$2"; }
call() {  # method path token body -> sets STATUS and BODY
  local args=(-s -o "$work/body" -w '%{http_code}' -X "$1" "$api$2" -H 'Accept: application/json')
  [[ -z "$3" ]] || args+=(-H "Authorization: Bearer $3")
  [[ -z "${4:-}" ]] || args+=(-H 'Content-Type: application/json' --data-binary "$4")
  STATUS="$(curl "${args[@]}")"; BODY="$(cat "$work/body")"
}
field() { python3 -c 'import json,sys; d=json.loads(sys.argv[1]); [d:=d[int(k)] if isinstance(d,list) else d[k] for k in sys.argv[2].split(".")]; print(d)' "$BODY" "$1"; }

echo "== provision roles on an empty database, then start the real backend (Flyway runs as the migrator)"
provision_db_roles >/dev/null
umask 077
{ printf 'DB_HOST=postgres\nDB_PORT=5432\nDB_NAME=%s\nDB_USER=%s\nDB_PASSWORD=%s\n' "$(env_get DB_NAME)" "$(env_get DB_USER)" "$(env_get DB_PASSWORD)"
  printf 'SPRING_FLYWAY_URL=%s\nSPRING_FLYWAY_USER=%s\nSPRING_FLYWAY_PASSWORD=%s\n' "$(env_get SPRING_FLYWAY_URL)" "$(env_get SPRING_FLYWAY_USER)" "$(env_get SPRING_FLYWAY_PASSWORD)"
  printf 'JWT_SECRET=%s\nCORS_ALLOWED_ORIGINS=https://codemastery.example.test\nSTORAGE_ACCESS_KEY=unused-in-this-test\nSTORAGE_SECRET_KEY=unused-in-this-test\nSPRING_PROFILES_ACTIVE=prod\nAPP_QUIZ_AUTO_SUBMIT_ENABLED=false\nAPP_QUIZ_AUTO_SUBMIT_DELAY_MS=1000\nAPP_QUIZ_AUTO_SUBMIT_BATCH_SIZE=2\n' "$(openssl rand -hex 32)"
} > "$work/backend.env"
# Read the whole log before matching: `docker logs | grep -q` fails under pipefail
# (docker logs gets SIGPIPE, status 141) once the log outgrows the pipe buffer.
backend_started() { local logs; logs="$(docker logs "$backend" 2>&1)" && [[ "$logs" == *'Started CodeMasteryApplication'* ]]; }
backend_failed() {
  echo "backend did not start: $1" >&2
  docker inspect -f 'state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}} started={{.State.StartedAt}} finished={{.State.FinishedAt}}' "$backend" >&2 || true
  docker logs --tail 80 "$backend" >&2 2>&1 || true
  exit 1
}
start_backend() {  # env-file
  docker run -d --name "$backend" --network "$PGTEST_NETWORK" -p "127.0.0.1:${port}:8080" --env-file "$1" "$BACKEND_IMAGE" >/dev/null
  local started=no
  for _ in $(seq 1 180); do
    if backend_started; then started=yes; break; fi
    [[ "$(docker inspect -f '{{.State.Running}}' "$backend")" == true ]] || backend_failed "the container exited during startup"
    sleep 1
  done
  # One last read after the final sleep, so a marker logged during it still counts.
  [[ "$started" == yes ]] || backend_started || backend_failed "no 'Started CodeMasteryApplication' within 180 s"
}
start_backend "$work/backend.env"
check "backend started (Flyway + Hibernate validate) before any runtime grant" "$(psql_admin <<<"SELECT count(*) FILTER (WHERE success) || '/' || count(*) || ' ' || string_agg(DISTINCT installed_by, ',') FROM public.flyway_schema_history WHERE version IS NOT NULL;")" "9/9 $(env_get SPRING_FLYWAY_USER)"
harden_after_migration
verify_role_separation >/dev/null
echo "  verify_role_separation: PASS (exact matrix on the real Flyway schema)"

echo "== fixture Course data (inserted by the bootstrap administrator; no seed policy implied)"
psql_admin >/dev/null <<'SQL'
INSERT INTO public.users VALUES ('10000000-0000-0000-0000-0000000000a1','instructor@example.test','not-a-login-hash','Test Instructor','instructor','active');
INSERT INTO public.courses VALUES
 ('20000000-0000-0000-0000-0000000000c1','10000000-0000-0000-0000-0000000000a1','Algorithms','Published course',NULL,'published',false,NULL),
 ('20000000-0000-0000-0000-0000000000c2','10000000-0000-0000-0000-0000000000a1','Hidden','Hidden course',NULL,'hidden',false,NULL);
INSERT INTO public.chapters VALUES ('30000000-0000-0000-0000-0000000000d1','20000000-0000-0000-0000-0000000000c1','Basics',1);
INSERT INTO public.lessons VALUES
 ('40000000-0000-0000-0000-0000000000e1','30000000-0000-0000-0000-0000000000d1','Published lesson','Body',1,'published'),
 ('40000000-0000-0000-0000-0000000000e2','30000000-0000-0000-0000-0000000000d1','Hidden lesson','Body',2,'hidden');
INSERT INTO public.learning_materials VALUES ('50000000-0000-0000-0000-0000000000f1','40000000-0000-0000-0000-0000000000e1','Slides','file','published',NULL,'private/object-key','slides.pdf',NULL);
SQL
course=20000000-0000-0000-0000-0000000000c1; hidden=20000000-0000-0000-0000-0000000000c2
password='Correct-Horse-Battery-2026'

echo "== Auth (users: SELECT, INSERT)"
for who in learner.a learner.b learner.c; do
  call POST /api/auth/register "" "{\"email\":\"$who@example.test\",\"password\":\"$password\",\"displayName\":\"$who\"}"
  check "register $who" "$STATUS" 201
done
call POST /api/auth/register "" "{\"email\":\"learner.a@example.test\",\"password\":\"$password\",\"displayName\":\"dup\"}"
check "duplicate canonical email" "$STATUS" 409
check "self-registration stores role learner/active" "$(psql_admin <<<"SELECT role || '/' || account_status FROM public.users WHERE email='learner.a@example.test';")" "learner/active"
call POST /api/auth/login "" "{\"email\":\"learner.a@example.test\",\"password\":\"wrong-password-value\"}"
check "login with a wrong password" "$STATUS" 401
for who in a b c; do
  call POST /api/auth/login "" "{\"email\":\"learner.$who@example.test\",\"password\":\"$password\"}"
  check "login learner.$who" "$STATUS" 200
  printf -v "token_$who" '%s' "$(field accessToken)"
done

echo "== ADR014 refresh sessions (refresh_sessions/refresh_tokens SELECT, INSERT, DELETE; UPDATE(revoked_at), UPDATE(consumed_at))"
origin=https://codemastery.example.test
auth_call() {  # path cookie [json] -> STATUS BODY COOKIE (the rotated __Host-cm_refresh value, if any)
  local args=(-s -o "$work/body" -D "$work/headers" -w '%{http_code}' -X POST "$api$1" -H "Origin: $origin" -H 'Accept: application/json')
  [[ -z "$2" ]] || args+=(-H "Cookie: __Host-cm_refresh=$2")
  [[ -z "${3:-}" ]] || args+=(-H 'Content-Type: application/json' --data-binary "$3")
  STATUS="$(curl "${args[@]}")"; BODY="$(cat "$work/body")"
  COOKIE="$(tr -d '\r' < "$work/headers" | sed -n 's/^[Ss]et-[Cc]ookie: __Host-cm_refresh=\([^;]*\);.*/\1/p')"
}
auth_call /api/auth/login "" "{\"email\":\"learner.a@example.test\",\"password\":\"$password\"}"
check "login issues a refresh cookie under the runtime role" "$STATUS/${#COOKIE}" "200/43"
first="$COOKIE"
auth_call /api/auth/refresh "$first"
check "refresh rotates (row lock, consume, insert)" "$STATUS/${#COOKIE}" "200/43"
rotated="$COOKIE"; refreshed_token="$(field accessToken)"
check "rotation kept one current token in the session" "$(psql_admin <<<"SELECT count(*) FILTER (WHERE t.consumed_at IS NULL) || '/' || count(*) FROM public.refresh_tokens t JOIN public.refresh_sessions s ON s.id=t.session_id WHERE s.id=(SELECT session_id FROM public.refresh_tokens WHERE token_digest=sha256(decode(translate('$rotated','-_','+/') || '=','base64')));")" "1/2"
call GET "/api/me/courses" "$refreshed_token"; check "refreshed bearer authenticates (session join)" "$STATUS" 200
auth_call /api/auth/logout "$rotated"
check "logout revokes (UPDATE revoked_at) and deletes the cookie" "$STATUS/$COOKIE" "204/"
call GET "/api/me/courses" "$refreshed_token"; check "logout ends the session-bound bearer at once" "$STATUS" 401
auth_call /api/auth/refresh "$rotated"; check "refresh after logout" "$STATUS" 401

echo "== catalogue and detail (courses/chapters/lessons SELECT; instructor names via users SELECT)"
call GET /api/courses ""
check "catalogue list" "$STATUS" 200
check "catalogue shows only the published Course" "$(field totalElements)/$(field content.0.title)/$(field content.0.instructorDisplayName)" "1/Algorithms/Test Instructor"
call GET "/api/courses?q=algo&prerequisites=none" ""
check "catalogue search/filter" "$STATUS/$(field totalElements)" "200/1"
call GET "/api/courses/$course" ""
check "Course detail with published syllabus" "$STATUS" 200
check "syllabus excludes the hidden lesson" "$(python3 -c 'import sys; b=sys.argv[1]; print(("Published lesson" in b, "Hidden lesson" in b, "object-key" in b))' "$BODY")" "(True, False, False)"
call GET "/api/courses/$hidden" ""
check "hidden Course detail is concealed" "$STATUS" 404

echo "== CT22 membership (enrollments SELECT/INSERT/UPDATE(status); courses FOR UPDATE via UPDATE(id))"
call GET "/api/courses/$course/membership" "$token_a"; check "membership read (access snapshot incl. material columns)" "$STATUS/$(field status)" "200/none"
call PUT "/api/courses/$course/membership" "$token_a"; check "join (row lock + INSERT)" "$STATUS/$(field status)" "200/active"
call PUT "/api/courses/$course/membership" "$token_a"; check "join again is unchanged" "$STATUS/$(field status)" "200/active"
call DELETE "/api/courses/$course/membership" "$token_a"; check "leave (UPDATE status)" "$STATUS/$(field status)" "200/left"
call PUT "/api/courses/$course/membership" "$token_a"; check "rejoin reuses the row" "$STATUS/$(field status)" "200/active"
check "one canonical row per learner/Course" "$(psql_admin <<<"SELECT count(*) FROM public.enrollments e JOIN public.users u ON u.id=e.user_id WHERE u.email='learner.a@example.test';")" 1
call GET "/api/me/courses" "$token_a"; check "My Courses" "$STATUS/$(field totalElements)/$(field content.0.status)" "200/1/active"
call PUT "/api/courses/$hidden/membership" "$token_a"; check "hidden Course membership is concealed" "$STATUS" 404

echo "== Course-root lock still serializes membership mutations under the runtime role"
docker exec -i "$PGTEST_CONTAINER" psql -X -q -U "$(env_get POSTGRES_USER)" -d "$(env_get POSTGRES_DB)" >/dev/null 2>&1 <<SQL &
BEGIN;
SELECT id FROM public.courses WHERE id='$course' FOR UPDATE;
SELECT pg_sleep(6);
COMMIT;
SQL
holder=$!
sleep 1
start=$SECONDS
call PUT "/api/courses/$course/membership" "$token_b" &
put_pid=$!
waiting=0
for _ in $(seq 1 30); do
  waiting="$(psql_admin <<<"SELECT count(*) FROM pg_stat_activity WHERE usename='$(env_get DB_USER)' AND wait_event_type='Lock';")"
  [[ "$waiting" == 0 ]] || break
  sleep 0.2
done
check "runtime session waits on the held Course row lock" "$waiting" 1
wait "$put_pid"; wait "$holder"
elapsed=$((SECONDS - start))
call GET "/api/courses/$course/membership" "$token_b"
check "blocked join completes after the lock is released" "$STATUS/$(field status)/$([[ $elapsed -ge 3 ]] && echo waited)" "200/active/waited"
( call PUT "/api/courses/$course/membership" "$token_c"; echo "$STATUS" > "$work/c1" ) &
( call DELETE "/api/courses/$course/membership" "$token_b"; echo "$STATUS" > "$work/b1" ) &
wait
check "concurrent mutations by two learners both succeed" "$(cat "$work/c1")/$(cat "$work/b1")" "200/200"
check "membership rows after the concurrent operations" "$(psql_admin <<<"SELECT string_agg(u.email || '=' || e.status, ',' ORDER BY u.email) FROM public.enrollments e JOIN public.users u ON u.id=e.user_id;")" "learner.a@example.test=active,learner.b@example.test=left,learner.c@example.test=active"

echo "== CO08 progress and resume (lesson_progress SELECT, INSERT, UPDATE(completed); enrollments UPDATE(status, resume_lesson_id, last_visited_at))"
lesson=40000000-0000-0000-0000-0000000000e1
call GET "/api/courses/$course/progress" "$token_a"
check "progress read (published Lessons only, canonical order)" "$STATUS/$(field completedLessons)/$(field totalLessons)/$(field progressPercentage)/$(field nextLessonId)" "200/0/1/0/$lesson"
call PUT "/api/courses/$course/resume" "$token_a" "{\"lessonId\":\"$lesson\"}"
check "visit (Course root and Enrollment locks, pointer + server time)" "$STATUS/$(field resumeLessonId)" "200/$lesson"
check "visit stored the pointer and the server time together" "$(psql_admin <<<"SELECT (e.resume_lesson_id='$lesson' AND e.last_visited_at IS NOT NULL)::text FROM public.enrollments e JOIN public.users u ON u.id=e.user_id WHERE u.email='learner.a@example.test';")" "true"
call GET "/api/me/resume" "$token_a"
check "account-level resume position" "$STATUS/$(field position.courseId)/$(field position.lessonId)" "200/$course/$lesson"
call PUT "/api/courses/$course/lessons/$lesson/completion" "$token_a" '{"completed":true}'
check "manual completion (upsert) completes the Course (UPDATE status)" "$STATUS/$(field progressPercentage)/$(field isComplete)/$(field membershipStatus)" "200/100/True/completed"
call PUT "/api/courses/$course/lessons/$lesson/completion" "$token_a" '{"completed":true}'
check "repeated completion is idempotent (ON CONFLICT DO UPDATE)" "$STATUS/$(field completedLessons)" "200/1"
check "one progress row for the learner and Lesson" "$(psql_admin <<<"SELECT count(*) FROM public.lesson_progress;")" 1
call GET "/api/me/course-progress" "$token_a"
check "ACTIVE-only progress summary excludes the completed Course" "$STATUS/$(field totalElements)" "200/0"
call GET "/api/me/courses?status=completed" "$token_a"
check "My Courses lists the Course as completed" "$STATUS/$(field totalElements)/$(field content.0.status)" "200/1/completed"
call GET "/api/courses/$course/resume" "$token_a"
check "per-Course resume keeps the pointer; no next Lesson remains" "$STATUS/$(field resumeLessonId)/$(field nextLessonId)" "200/$lesson/None"

echo "== AD07 skill graph (r5: skills SELECT, INSERT, DELETE, UPDATE(name, description); skill_prerequisites and lesson_skills SELECT, INSERT, DELETE)"
# The account is registered, then promoted by the bootstrap administrator: no Instructor
# provisioning API exists, and the role change is not a runtime-role capability.
call POST /api/auth/register "" "{\"email\":\"instructor.ad07@example.test\",\"password\":\"$password\",\"displayName\":\"AD07 instructor\"}"
check "register the AD07 instructor" "$STATUS" 201
psql_admin >/dev/null <<<"UPDATE public.users SET role='instructor' WHERE email='instructor.ad07@example.test';"
call POST /api/auth/login "" "{\"email\":\"instructor.ad07@example.test\",\"password\":\"$password\"}"
check "login the AD07 instructor" "$STATUS" 200
token_i="$(field accessToken)"
psql_admin >/dev/null <<'SQL'
INSERT INTO public.courses SELECT '20000000-0000-0000-0000-0000000000c3', id, 'Graphs', 'AD07 course', NULL, 'hidden', false, NULL
  FROM public.users WHERE email='instructor.ad07@example.test';
INSERT INTO public.chapters VALUES ('30000000-0000-0000-0000-0000000000d3','20000000-0000-0000-0000-0000000000c3','Basics',1);
INSERT INTO public.lessons VALUES ('40000000-0000-0000-0000-0000000000e3','30000000-0000-0000-0000-0000000000d3','Hidden lesson','Body',1,'hidden');
SQL
graph=/api/courses/20000000-0000-0000-0000-0000000000c3; graph_lesson=40000000-0000-0000-0000-0000000000e3
call POST "$graph/skills" "$token_i" '{"name":"Variables"}'
check "create a skill (Course root lock, INSERT)" "$STATUS" 201
skill_a="$(field id)"
call POST "$graph/skills" "$token_i" '{"name":"Loops","description":"for and while"}'
check "create a second skill" "$STATUS" 201
skill_b="$(field id)"
call PUT "$graph/skills/$skill_b" "$token_i" '{"name":"Iteration"}'
check "replace a skill (UPDATE name, description)" "$STATUS/$(field name)/$(field description)" "200/Iteration/None"
call PUT "$graph/skills/$skill_a/prerequisites/$skill_b" "$token_i"; check "add a prerequisite edge" "$STATUS" 204
call PUT "$graph/skills/$skill_b/prerequisites/$skill_a" "$token_i"; check "a cycle-closing edge is refused" "$STATUS" 409
call PUT "$graph/lessons/$graph_lesson/skills/$skill_a" "$token_i"; check "map the hidden Lesson" "$STATUS" 204
call GET "$graph/skill-graph" "$token_i"
check "owner graph read" "$STATUS/$(field prerequisites.0.skillId)/$(field mappings.0.lessonId)" "200/$skill_a/$graph_lesson"
call GET "$graph/skill-graph" "$token_a"; check "a Learner cannot read the authoring graph" "$STATUS" 403
psql_admin >/dev/null <<<"INSERT INTO public.questions (id,skill_id,prompt,kind,difficulty,options,correct_options) VALUES (gen_random_uuid(),'$skill_b','P','true_false','easy',ARRAY['True','False'],ARRAY[true,false]);"
call DELETE "$graph/lessons/$graph_lesson/skills/$skill_a" "$token_i"; check "unmap" "$STATUS" 204
call DELETE "$graph/skills/$skill_a/prerequisites/$skill_b" "$token_i"; check "remove the edge" "$STATUS" 204
call DELETE "$graph/skills/$skill_b" "$token_i"; check "a skill a Question references is a 409 (FK 23503)" "$STATUS" 409
call DELETE "$graph/skills/$skill_a" "$token_i"; check "delete an unreferenced skill" "$STATUS" 204
check "graph rows left: the referenced skill only" "$(psql_admin <<<"SELECT (SELECT count(*) FROM public.skills WHERE course_id='20000000-0000-0000-0000-0000000000c3') || '/' || (SELECT count(*) FROM public.skill_prerequisites) || '/' || (SELECT count(*) FROM public.lesson_skills);")" "1/0/0"

echo "== AS05 question bank (r6: questions SELECT, INSERT, DELETE, UPDATE(definition); quiz_questions and attempt_answers SELECT(id, question_id))"
call POST "$graph/skills" "$token_i" '{"name":"Tagged"}'; check "create the tagging skill" "$STATUS" 201
skill_q="$(field id)"
qbody() { printf '{"skillId":"%s","prompt":"%s","kind":"single_choice","difficulty":"medium","options":["1","2"],"correctOptions":[false,true]}' "$1" "$2"; }
call POST "$graph/questions" "$token_i" "$(qbody "$skill_q" 'x = 1; x += 1; x?')"
check "create a question (CT40 tag check under the Course root)" "$STATUS/$(field skillId)/$(field difficulty)" "201/$skill_q/medium"
question_a="$(field id)"
call GET "$graph/questions" "$token_i"; check "owner list (both stored questions)" "$STATUS/$(field totalElements)" "200/2"
call GET "$graph/questions/$question_a" "$token_i"; check "owner read includes the answer key" "$STATUS/$(field correctOptions.1)" "200/True"
call PUT "$graph/questions/$question_a" "$token_i" "$(qbody "$skill_q" 'Replaced')"
check "replace an unused definition (UPDATE six columns)" "$STATUS/$(field prompt)" "200/Replaced"
call GET "$graph/questions" "$token_a"; check "a Learner cannot read the question bank" "$STATUS" 403
psql_admin >/dev/null <<<"INSERT INTO public.skills VALUES ('70000000-0000-0000-0000-0000000000f1','20000000-0000-0000-0000-0000000000c1','Foreign',NULL);"
call POST "$graph/questions" "$token_i" "$(qbody 70000000-0000-0000-0000-0000000000f1 'Foreign')"
check "a skill of another Course is concealed" "$STATUS" 404
psql_admin >/dev/null <<SQL
INSERT INTO public.quizzes VALUES ('80000000-0000-0000-0000-0000000000a1','20000000-0000-0000-0000-0000000000c3','Referencing','practice',60,'hidden');
INSERT INTO public.quiz_questions VALUES (gen_random_uuid(),'80000000-0000-0000-0000-0000000000a1','$question_a',1);
SQL
call DELETE "$graph/questions/$question_a" "$token_i"; check "a question a Quiz references cannot be deleted (SELECT quiz_questions)" "$STATUS" 409
psql_admin >/dev/null <<SQL
INSERT INTO public.attempts SELECT '80000000-0000-0000-0000-0000000000b1', id, '80000000-0000-0000-0000-0000000000a1', 1, now(), now() + interval '5 minutes' FROM public.users WHERE email='learner.a@example.test';
INSERT INTO public.attempt_answers VALUES (gen_random_uuid(),'80000000-0000-0000-0000-0000000000b1','$question_a',1,ARRAY[false,false],NULL);
SQL
call PUT "$graph/questions/$question_a" "$token_i" "$(qbody "$skill_q" 'Changed')"
check "a used definition is frozen (SELECT attempt_answers)" "$STATUS" 409
check "the used definition is unchanged" "$(psql_admin <<<"SELECT prompt FROM public.questions WHERE id='$question_a';")" "Replaced"
call POST "$graph/questions" "$token_i" "$(qbody "$skill_q" 'Temporary')"; question_b="$(field id)"
call DELETE "$graph/questions/$question_b" "$token_i"; check "delete an unreferenced question" "$STATUS/$(field id)" "200/$question_b"
psql_admin >/dev/null <<SQL
DELETE FROM public.attempt_answers WHERE attempt_id='80000000-0000-0000-0000-0000000000b1';
DELETE FROM public.attempts WHERE id='80000000-0000-0000-0000-0000000000b1';
DELETE FROM public.quiz_questions WHERE quiz_id='80000000-0000-0000-0000-0000000000a1';
DELETE FROM public.quizzes WHERE id='80000000-0000-0000-0000-0000000000a1';
DELETE FROM public.skills WHERE id='70000000-0000-0000-0000-0000000000f1';
SQL

echo "== AS06 quiz composition (r7: quizzes SELECT, INSERT, DELETE, UPDATE(definition); quiz_questions SELECT, INSERT, DELETE; attempts SELECT(id, quiz_id))"
call POST "$graph/questions" "$token_i" "$(qbody "$skill_q" 'Second question')"; question_c="$(field id)"
quizbody() { printf '{"title":"%s","kind":"practice","timeLimitSeconds":300,"questionIds":[%s]}' "$1" "$2"; }
call POST "$graph/quizzes" "$token_i" "$(quizbody 'Check' "\"$question_a\"")"
check "create a hidden quiz with same-Course membership" "$STATUS/$(field published)/$(field questionCount)" "201/False/1"
quiz_a="$(field id)"
call PUT "$graph/quizzes/$quiz_a" "$token_i" "$(quizbody 'Check' "\"$question_c\",\"$question_a\"")"
check "replace membership in order (targeted UPDATE, DELETE + INSERT)" "$STATUS/$(field questions.0.question.id)/$(field questions.1.position)" "200/$question_c/2"
call POST "$graph/quizzes" "$token_i" "$(quizbody 'Foreign' '"70000000-0000-0000-0000-0000000000f2"')"
check "a question outside the Course is concealed" "$STATUS" 404
call POST "$graph/quizzes" "$token_i" "$(quizbody 'Draft' '')"; check "an empty draft" "$STATUS" 201
draft="$(field id)"
call PUT "$graph/quizzes/$draft/publication" "$token_i" '{"publicationStatus":"published"}'; check "an empty draft cannot be published" "$STATUS" 409
call DELETE "$graph/quizzes/$draft" "$token_i"; check "delete a hidden never-attempted quiz" "$STATUS/$(field id)" "200/$draft"
call PUT "$graph/quizzes/$quiz_a/publication" "$token_i" '{"publicationStatus":"published"}'
check "publish (UPDATE publication_status)" "$STATUS/$(field published)" "200/True"
call PUT "$graph/quizzes/$quiz_a/publication" "$token_i" '{"publicationStatus":"published"}'; check "publication is idempotent" "$STATUS" 200
call PUT "$graph/quizzes/$quiz_a" "$token_i" "$(quizbody 'Edited' "\"$question_a\"")"; check "a published quiz is frozen" "$STATUS" 409
call GET "$graph/owned-quizzes" "$token_i"; check "owner list with definitions" "$STATUS/$(field totalElements)/$(field content.0.questions.0.question.correctOptions.1)" "200/1/True"
# CO06 publication writes need CO06's own CT17-P6 revision; the bootstrap administrator publishes the fixture Course.
psql_admin >/dev/null <<<"UPDATE public.courses SET publication_status='published' WHERE id='20000000-0000-0000-0000-0000000000c3';"
call PUT "$graph/membership" "$token_a"; check "the Learner joins the published Course" "$STATUS" 200
call GET "$graph/quizzes" "$token_a"
check "the Learner lists the published quiz" "$STATUS/$(field totalElements)/$(field content.0.id)/$(field content.0.questionCount)" "200/1/$quiz_a/2"
check "the Learner list carries no keys or prompts" "$([[ "$BODY" == *correctOptions* || "$BODY" == *'Second question'* ]] && echo leaked || echo clean)" clean
call GET "$graph/owned-quizzes" "$token_a"; check "a Learner cannot read owned quizzes" "$STATUS" 403
call PUT "$graph/quizzes/$quiz_a/publication" "$token_i" '{"publicationStatus":"hidden"}'; check "hide" "$STATUS/$(field published)" "200/False"
psql_admin >/dev/null <<<"INSERT INTO public.attempts SELECT gen_random_uuid(), id, '$quiz_a', 1, now(), now() + interval '5 minutes' FROM public.users WHERE email='learner.a@example.test';"
call PUT "$graph/quizzes/$quiz_a" "$token_i" "$(quizbody 'Edited' "\"$question_a\"")"; check "an attempted quiz stays frozen after hiding (SELECT attempts)" "$STATUS" 409
call DELETE "$graph/quizzes/$quiz_a" "$token_i"; check "an attempted quiz cannot be deleted" "$STATUS" 409
check "the attempted quiz and its membership are intact" "$(psql_admin <<<"SELECT title || '/' || (SELECT count(*) FROM public.quiz_questions WHERE quiz_id='$quiz_a') FROM public.quizzes WHERE id='$quiz_a';")" "Check/2"
psql_admin >/dev/null <<SQL
DELETE FROM public.attempts WHERE quiz_id='$quiz_a';
DELETE FROM public.quiz_questions WHERE quiz_id='$quiz_a';
DELETE FROM public.quizzes WHERE id='$quiz_a';
DELETE FROM public.enrollments WHERE course_id='20000000-0000-0000-0000-0000000000c3';
UPDATE public.courses SET publication_status='hidden' WHERE id='20000000-0000-0000-0000-0000000000c3';
SQL

echo "== CO06 Instructor authoring (r12: courses INSERT, UPDATE(title, description, prerequisite_description, publication_status); chapters/lessons SELECT, INSERT, UPDATE(structure))"
owned=/api/instructor/courses
call POST "$owned" "$token_i" '{"title":"Authored","description":"CO06 course","prerequisiteDescription":null}'
check "create a Course (INSERT, hidden by default)" "$STATUS/$(field publicationStatus)" "201/hidden"
authored="$(field id)"
call PUT "$owned/$authored" "$token_i" '{"title":"Authored 2","description":"Replaced","prerequisiteDescription":"Loops"}'
check "replace Course metadata (UPDATE three columns)" "$STATUS/$(field title)/$(field prerequisiteDescription)" "200/Authored 2/Loops"
call POST "$owned/$authored/chapters" "$token_i" '{"title":"First"}'; check "append a Chapter (INSERT ... RETURNING)" "$STATUS/$(field position)" "201/1"
chapter_a="$(field id)"
call POST "$owned/$authored/chapters" "$token_i" '{"title":"Second"}'; check "append a second Chapter" "$STATUS/$(field position)" "201/2"
chapter_b="$(field id)"
call PUT "$owned/$authored/chapters/$chapter_a" "$token_i" '{"title":"First renamed"}'; check "rename a Chapter (UPDATE title)" "$STATUS/$(field title)" "200/First renamed"
call PUT "$owned/$authored/chapters/order" "$token_i" "{\"orderedIds\":[\"$chapter_b\",\"$chapter_a\"]}"
check "reorder Chapters (deferred uniqueness, UPDATE position)" "$STATUS/$(field orderedIds.0)" "200/$chapter_b"
call POST "$owned/$authored/chapters/$chapter_a/lessons" "$token_i" '{"title":"L1","contentMarkdown":"Body"}'; check "append a Lesson" "$STATUS/$(field publicationStatus)" "201/hidden"
lesson_a="$(field id)"
call POST "$owned/$authored/chapters/$chapter_a/lessons" "$token_i" '{"title":"L2","contentMarkdown":null}'; lesson_b="$(field id)"
call PUT "$owned/$authored/lessons/$lesson_a" "$token_i" '{"title":"L1 edited","contentMarkdown":"Body 2"}'
check "replace a Lesson (UPDATE title, content_markdown)" "$STATUS/$(field title)" "200/L1 edited"
call PUT "$owned/$authored/chapters/$chapter_a/lessons/order" "$token_i" "{\"orderedIds\":[\"$lesson_b\",\"$lesson_a\"]}"
check "reorder Lessons" "$STATUS/$(field orderedIds.1)" "200/$lesson_a"
call PUT "$owned/$authored/lessons/$lesson_a/publication" "$token_i" '{"publicationStatus":"published"}'
check "publish a Lesson (UPDATE publication_status)" "$STATUS/$(field publicationStatus)" "200/published"
call PUT "$owned/$authored/publication" "$token_i" '{"publicationStatus":"published"}'
check "publish the Course (Course root lock, UPDATE publication_status)" "$STATUS/$(field publicationStatus)" "200/published"
call GET "$owned/$authored" "$token_i"
check "owner read shows the committed structure" "$STATUS/$(field chapters.0.title)/$(field chapters.1.lessons.0.title)" "200/Second/L2"
call GET "$owned" "$token_i"; check "owner list includes the authored Course" "$STATUS/$(field totalElements)" "200/2"
call GET "$owned/$authored/lessons/$lesson_a" "$token_i"; check "owner Lesson read (learning_materials SELECT)" "$STATUS/$(field title)" "200/L1 edited"
call GET "/api/courses/$course/lessons/40000000-0000-0000-0000-0000000000e1" "$token_a"
check "Learner Lesson read with its material (learning_materials SELECT)" "$STATUS/$(field materials.0.title)" "200/Slides"
check "the Learner Lesson body never carries the object key" "$(python3 -c 'import sys; print("object-key" in sys.argv[1] or "slides.pdf" in sys.argv[1])' "$BODY")" "False"
call PUT "$owned/$authored" "$token_a" '{"title":"Hijack","description":"x","prerequisiteDescription":null}'; check "a Learner cannot author" "$STATUS" 403
check "the stored instructor is the authenticated Instructor" "$(psql_admin <<<"SELECT u.email FROM public.courses c JOIN public.users u ON u.id=c.instructor_id WHERE c.id='$authored';")" "instructor.ad07@example.test"

echo "== CO09 relational material writes (r14)"
material_path="$owned/$authored/lessons/$lesson_a/materials"
call POST "$material_path" "$token_i" '{"title":"Text","materialType":"text","textContent":"  code"}'
check "CO09 hidden text creation" "$STATUS/$(field publicationStatus)/$(field textContent)" "201/hidden/  code"
material_id="$(field id)"
call PUT "$material_path/$material_id" "$token_i" '{"title":"Edited","materialType":"text","textContent":" replacement "}'
check "CO09 targeted six-column content replacement" "$STATUS/$(field title)" "200/Edited"
call PUT "$material_path/$material_id/publication" "$token_i" '{"publicationStatus":"published"}'
check "CO09 publication" "$STATUS/$(field publicationStatus)" "200/published"
call POST "$material_path" "$token_i" '{"title":"Video","materialType":"external_video","externalVideoUrl":"https://www.youtube-nocookie.com/embed/test_01"}'
check "CO09 canonical video creation" "$STATUS/$(field materialType)" "201/external_video"

echo "== AS09 assignments and submissions (r12/r16)"
assigned=/api/courses/$authored
call POST "$assigned/assignments" "$token_i" "{\"title\":\"Project\",\"instructions\":\"Submit a ZIP\",\"deadlineAt\":\"9999-12-31T23:59:59.999999Z\",\"lessonId\":\"$lesson_a\"}"
check "create an Assignment at the latest representable deadline (INSERT)" "$STATUS/$(field publicationStatus)/$(field deadlineAt)" "201/hidden/9999-12-31T23:59:59.999999Z"
assignment="$(field id)"
call POST "$assigned/assignments" "$token_i" '{"title":"Late","instructions":"x","deadlineAt":"9999-12-31T23:59:59-05:00","lessonId":null}'
check "a deadline past year 9999 in UTC is a 400 (U6)" "$STATUS" 400
# D2: exercise JDBC INSERT, targeted UPDATE and fresh extraction under the real r12 runtime role.
# The SQL epoch oracle is independent of the application's date conversion.
call POST "$assigned/assignments" "$token_i" '{"title":"Historical","instructions":"I","deadlineAt":"1500-03-10T00:00:00Z","lessonId":null}'
check "historical leap-day INSERT no longer fails with 22008" "$STATUS/$(field deadlineAt)" "201/1500-03-10T00:00:00Z"
historical="$(field id)"
check "historical INSERT stores the exact SQL epoch" "$(psql_admin <<<"SELECT extract(epoch FROM deadline_at) FROM public.assignments WHERE id='$historical';")" "-14825894400.000000"
call GET "$assigned/owned-assignments/$historical" "$token_i"
check "historical owner re-read preserves the UTC instant" "$STATUS/$(field deadlineAt)" "200/1500-03-10T00:00:00Z"
call PUT "$assigned/owned-assignments/$historical" "$token_i" '{"title":"Historical","instructions":"I","deadlineAt":"0000-01-01T00:00:00Z","lessonId":null}'
check "year-zero targeted replacement" "$STATUS/$(field deadlineAt)" "200/0000-01-01T00:00:00Z"
check "year-zero UPDATE stores the exact SQL epoch" "$(psql_admin <<<"SELECT extract(epoch FROM deadline_at) FROM public.assignments WHERE id='$historical';")" "-62167219200.000000"
call GET "$assigned/owned-assignments/$historical" "$token_i"
check "year-zero owner re-read preserves the UTC instant" "$STATUS/$(field deadlineAt)" "200/0000-01-01T00:00:00Z"
psql_admin >/dev/null <<<"DELETE FROM public.assignments WHERE id='$historical';"
call PUT "$assigned/owned-assignments/$assignment" "$token_i" '{"title":"Project 2","instructions":"Submit a PDF","deadlineAt":"2026-12-01T10:00:00.000001+07:00","lessonId":null}'
check "replace under the FOR UPDATE row lock (targeted UPDATE)" "$STATUS/$(field deadlineAt)/$(field lessonId)" "200/2026-12-01T03:00:00.000001Z/None"
call PUT "$assigned/owned-assignments/$assignment/publication" "$token_i" '{"publicationStatus":"published"}'
check "publish (UPDATE publication_status)" "$STATUS/$(field publicationStatus)" "200/published"
psql_admin >/dev/null <<SQL
INSERT INTO public.submissions SELECT gen_random_uuid(), '$assignment', id, 'submissions/' || gen_random_uuid(), 'a.pdf', now(), NULL, NULL, NULL FROM public.users WHERE email='learner.a@example.test';
INSERT INTO public.submissions SELECT gen_random_uuid(), '$assignment', id, 'submissions/' || gen_random_uuid(), 'b.zip', now(), 80, 'Good', now() FROM public.users WHERE email='learner.b@example.test';
SQL
call GET "$assigned/owned-assignments" "$token_i"
check "owner list with submission and graded counts (column SELECT)" "$STATUS/$(field totalElements)/$(field content.0.submissionCount)/$(field content.0.gradedCount)" "200/1/2/1"
call GET "$assigned/owned-assignments/$assignment" "$token_i"; check "owner read" "$STATUS/$(field title)" "200/Project 2"
call GET "$assigned/owned-assignments" "$token_a"; check "a Learner cannot read owner assignments" "$STATUS" 403
# AS09 r16: backend queries execute as the configured non-owner runtime login.
call PUT "$assigned/membership" "$token_a"
check "learner joins the authored Course" "$STATUS" 200
call GET "$assigned/adaptive-guidance" "$token_a"
check "AD16 guidance under runtime role" "$STATUS/$(field adaptiveEnabled)/$(field placementOffered)" "200/False/False"
# Bootstrap-only synthetic stored configuration: AD08 settings writes are a separate capability.
psql_admin >/dev/null <<<"UPDATE public.courses SET adaptive_enabled=true, adaptive_settings='{\"weak\":{\"numerator\":1,\"denominator\":2},\"mastered\":{\"numerator\":4,\"denominator\":5},\"minimumQuestionsPerSkill\":6}'::jsonb WHERE id='$authored';"
call GET "$assigned/adaptive-guidance" "$token_a"
check "AD16 current history SELECT under runtime role" "$STATUS/$(field adaptiveEnabled)" "200/True"
psql_admin >/dev/null <<<"UPDATE public.courses SET adaptive_enabled=false, adaptive_settings=null WHERE id='$authored';"


call GET "$assigned/assignments" "$token_a"
check "learner assignment page (real schema joins)" "$STATUS/$(field totalElements)" "200/1"
call GET "$assigned/assignments/$assignment" "$token_a"
check "own ungraded submission has nullable grade" "$STATUS/$(field submission.graded)/$(field submission.gradePercentage)" "200/False/None"
call GET "$assigned/owned-assignments/$assignment/submissions" "$token_i"
check "owner receives page-bounded historical names" "$STATUS/$(field totalElements)" "200/2"
submission="$(field content.0.id)"; submitted_at="$(field content.0.submittedAt)"
call PUT "$assigned/owned-assignments/$assignment/submissions/$submission/grade" "$token_i" "{\"gradePercentage\":99.99,\"feedback\":\"Reviewed\",\"submittedAt\":\"$submitted_at\"}"
check "locked grade writes only approved runtime columns" "$STATUS/$(field gradePercentage)/$(field feedback)" "200/99.99/Reviewed"
call PUT "$assigned/owned-assignments/$assignment/submissions/$submission/grade" "$token_i" "{\"gradePercentage\":80,\"feedback\":\"Updated\",\"submittedAt\":\"$submitted_at\"}"
check "regrade remains supported" "$STATUS/$(field feedback)" "200/Updated"
psql_admin >/dev/null <<SQL
DELETE FROM public.submissions WHERE assignment_id='$assignment';
DELETE FROM public.assignments WHERE id='$assignment';
DELETE FROM public.enrollments WHERE course_id='$authored';
DELETE FROM public.learning_materials WHERE lesson_id IN (SELECT id FROM public.lessons WHERE chapter_id IN ('$chapter_a','$chapter_b'));
DELETE FROM public.lessons WHERE chapter_id IN ('$chapter_a','$chapter_b');
DELETE FROM public.chapters WHERE course_id='$authored';
DELETE FROM public.courses WHERE id='$authored';
SQL

echo "== IN10 audit read (r10: audit_logs SELECT, INSERT; users SELECT for actor names)"
# The account is registered, then promoted by the bootstrap administrator, which also stands in for the
# audited Administrator actions (IN11/IN20/IN21 add their HTTP mutations and real-backend cases later).
call POST /api/auth/register "" "{\"email\":\"admin.in10@example.test\",\"password\":\"$password\",\"displayName\":\"IN10 admin\"}"
check "register the IN10 administrator" "$STATUS" 201
psql_admin >/dev/null <<<"UPDATE public.users SET role='admin' WHERE email='admin.in10@example.test';"
call POST /api/auth/login "" "{\"email\":\"admin.in10@example.test\",\"password\":\"$password\"}"
check "login the IN10 administrator" "$STATUS" 200
token_adm="$(field accessToken)"
admin_id="$(psql_admin <<<"SELECT id FROM public.users WHERE email='admin.in10@example.test';")"
psql_admin >/dev/null <<SQL
INSERT INTO public.audit_logs VALUES
 (gen_random_uuid(),'$admin_id','user_locked','user','10000000-0000-0000-0000-0000000000a1','2026-11-24T01:00:00.123456Z'),
 (gen_random_uuid(),'$admin_id','course_hidden','course','$hidden','2026-11-24T01:00:01Z');
SQL
call GET "/api/admin/audit-logs?actorId=$admin_id" "$token_adm"
check "administrator reads the audit log newest first (SELECT)" "$STATUS/$(field totalElements)/$(field content.0.action)/$(field content.0.actorDisplayName)" "200/2/course_hidden/IN10 admin"
call GET "/api/admin/audit-logs?actorId=$admin_id&occurredFrom=2026-11-24T01:00:00.123456Z&occurredTo=2026-11-24T01:00:01Z" "$token_adm"
check "inclusive occurredFrom, exclusive occurredTo" "$STATUS/$(field totalElements)/$(field content.0.targetReference)" "200/1/10000000-0000-0000-0000-0000000000a1"
call GET "/api/admin/audit-logs" "$token_a"; check "a learner is refused the audit log" "$STATUS" 403
call GET "/api/admin/audit-logs?size=0" "$token_a"; check "a malformed query is 400 before the role check" "$STATUS" 400
check "the audit read wrote nothing" "$(psql_admin <<<"SELECT count(*) FROM public.audit_logs;")" 2

echo "== IN07 notifications (r13 SELECT, INSERT, UPDATE(is_read))"
notification_path=/api/me/notifications
notice_older=00000000-0000-0000-0000-0000000013a1
notice_newer=00000000-0000-0000-0000-0000000013a2
notice_foreign=00000000-0000-0000-0000-0000000013a3
psql_as "$(env_get DB_USER)" "$(env_get DB_PASSWORD)" >/dev/null <<SQL
INSERT INTO public.notifications (id,user_id,message,is_read,created_at)
SELECT v.id::uuid, u.id, v.message, false, '2026-11-25T01:00:00.123456Z'
FROM (VALUES
  ('$notice_older', 'learner.a@example.test', 'First notice'),
  ('$notice_newer', 'learner.a@example.test', 'Second notice'),
  ('$notice_foreign', 'learner.b@example.test', 'Foreign notice')
) AS v(id,email,message) JOIN public.users u ON u.email=v.email
ON CONFLICT (id) DO NOTHING;
SQL
call GET "$notification_path?size=0" ""
check "notifications authenticate before validation" "$STATUS" 401
call GET "$notification_path?size=1" "$token_a"
check "list only own notifications, newest by id on tied timestamps" "$STATUS/$(field totalElements)/$(field content.0.id)" "200/2/$notice_newer"
call GET "$notification_path?unreadOnly=true&page=2147483647&size=100" "$token_a"
check "large out-of-range page has the exact total" "$STATUS/$(field content)/$(field totalElements)" '200/[]/2'
call PUT "$notification_path/$notice_foreign/read" "$token_a"
check "foreign notification is concealed" "$STATUS" 404
call PUT "$notification_path/$notice_newer/read" "$token_a" '{ignored'
check "mark-read ignores the body and returns the persisted row" "$STATUS/$(field id)/$(field read)" "200/$notice_newer/True"
call PUT "$notification_path/$notice_newer/read" "$token_a"
check "mark-read is idempotent" "$STATUS/$(field read)" "200/True"
call GET "$notification_path/unread-count" "$token_a"
check "unread count is own only" "$STATUS/$(field unreadCount)" 200/1
call PUT "$notification_path/read-all" "$token_a"
check "mark-all changes only remaining own unread rows" "$STATUS/$(field markedCount)" 200/1
call PUT "$notification_path/read-all" "$token_a"
check "mark-all is idempotent" "$STATUS/$(field markedCount)" 200/0
call GET "$notification_path/unread-count" "$token_b"
check "foreign row remains unread" "$STATUS/$(field unreadCount)" 200/1
call GET "$notification_path" "$token_i"
check "Instructor can list own notifications" "$STATUS/$(field totalElements)" 200/0
call GET "$notification_path" "$token_adm"
check "Administrator can list own notifications" "$STATUS/$(field totalElements)" 200/0
check "mark-read and mark-all committed under the runtime role" "$(psql_admin <<<"SELECT count(*) FILTER (WHERE is_read) || '/' || count(*) FROM public.notifications;")" 2/3

echo "== IN06 discussion and reports (r17)"
discussion=/api/lessons/$lesson/discussion
call POST "$discussion" "$token_a" '{"content":"Runtime question"}'
check "create question through CT03 under the runtime role" "$STATUS/$(field authorRole)" "201/Learner"
interaction_question="$(field id)"
call POST "$discussion" "$token_c" "{\"content\":\"Runtime reply\",\"parentPostId\":\"$interaction_question\"}"
check "reply holds parent SHARE and writes its notification" "$STATUS/$(field parentPostId)" "201/$interaction_question"
call GET "$discussion?size=1" "$token_a"
check "question pagination embeds the reply" "$STATUS/$(field totalElements)/$(field content.0.replies.0.content)" "200/1/Runtime reply"
call POST "/api/lessons/$lesson/reports" "$token_a" '{"reason":"Runtime lesson report"}'
check "create Lesson report under runtime role" "$STATUS/$(field status)" "201/open"
call POST "$discussion/$interaction_question/reports" "$token_a" '{"reason":"Runtime post report"}'
check "create post report under runtime role" "$STATUS/$(field status)" "201/open"
call POST "$discussion" "$token_adm" '{'
check "Administrator admission precedes malformed JSON" "$STATUS" 403
check "reply notification committed under runtime role" "$(psql_admin <<<"SELECT count(*) FROM public.notifications WHERE message LIKE 'New reply to your question in %';")" 1

echo "== current account state is enforced (users SELECT on every protected request)"
psql_admin >/dev/null <<<"UPDATE public.users SET account_status='locked' WHERE email='learner.c@example.test';"
call GET "/api/me/courses" "$token_c"; check "locked account's existing token is rejected" "$STATUS" 401
call GET "$notification_path" "$token_c"; check "locked account cannot read notifications" "$STATUS" 401
call POST /api/auth/login "" "{\"email\":\"learner.c@example.test\",\"password\":\"$password\"}"; check "locked account cannot log in" "$STATUS" 401

echo "== AU07 recovery under the runtime role (users UPDATE(password_hash) + row lock; tokens SELECT/INSERT/DELETE)"
call POST /api/auth/password-reset-requests "" '{"email":"learner.b@example.test"}'
check "unconfigured recovery still acknowledges with the generic 202" "$STATUS" 202
check "unconfigured recovery issues no capability" "$(psql_admin <<<"SELECT count(*) FROM public.password_reset_tokens;")" 0
# The bootstrap administrator stands in for the (inactive) request worker; the capability
# is generated here, and only its SHA-256 digest is stored, exactly as the worker does.
read -r capability digest < <(python3 -c 'import base64,hashlib,os; b=os.urandom(32); print(base64.urlsafe_b64encode(b).rstrip(b"=").decode(), hashlib.sha256(b).hexdigest())')
psql_admin >/dev/null <<<"INSERT INTO public.password_reset_tokens SELECT gen_random_uuid(), id, decode('$digest','hex'), now() - interval '1 minute', now() + interval '29 minutes' FROM public.users WHERE email='learner.b@example.test';"
new_password='Reset-Horse-Battery-2026'
call POST /api/auth/password-resets "" "{\"token\":\"$capability\",\"password\":\"$new_password\"}"
check "reset confirmation commits under the runtime role" "$STATUS" 200
check "capability consumed in the same transaction" "$(psql_admin <<<"SELECT count(*) FROM public.password_reset_tokens;")" 0
check "reset revoked every refresh session of the account" "$(psql_admin <<<"SELECT count(*) FROM public.refresh_sessions s JOIN public.users u ON u.id=s.user_id WHERE u.email='learner.b@example.test' AND s.revoked_at IS NULL;")" 0
call GET "/api/me/courses" "$token_b"; check "a bearer issued before the reset is rejected" "$STATUS" 401
call POST /api/auth/password-resets "" "{\"token\":\"$capability\",\"password\":\"$new_password\"}"
check "reused capability is rejected" "$STATUS" 400
call POST /api/auth/login "" "{\"email\":\"learner.b@example.test\",\"password\":\"$password\"}"
check "old password no longer signs in" "$STATUS" 401
call POST /api/auth/login "" "{\"email\":\"learner.b@example.test\",\"password\":\"$new_password\"}"
check "new password signs in (ordinary sign-in, no JWT from reset)" "$STATUS" 200

denials="$(docker logs "$backend" 2>&1 | count_permission_errors)"
check "backend log entries reporting permission denied / SQLSTATE 42501" "$denials" 0

echo "== AU07 request worker under the runtime role (recovery activated; synthetic SMTP host that cannot resolve)"
# Restart the same image against the same migrated database with recovery settings. Every value
# is synthetic: `.invalid` never resolves, so delivery fails after commit (counted, never logged),
# and no provider, credential or deployment binding is involved.
docker rm -f "$backend" >/dev/null
{ cat "$work/backend.env"
  printf 'SMTP_HOST=smtp.invalid\nSMTP_USERNAME=synthetic-test-user\nSMTP_PASSWORD=%s\n' "$(openssl rand -hex 16)"
  printf 'PASSWORD_RESET_EMAIL_FROM=no-reply@example.test\nPASSWORD_RESET_FRONTEND_ORIGIN=https://codemastery.example.test\n'
} > "$work/backend-recovery.env"
start_backend "$work/backend-recovery.env"
token_count() { psql_admin <<<"SELECT count(*) FROM public.password_reset_tokens t JOIN public.users u ON u.id=t.user_id WHERE u.email='$1';"; }
await_token() {  # email -> waits until the single FIFO worker has committed that account's capability
  for _ in $(seq 1 150); do [[ "$(token_count "$1")" == 1 ]] && return 0; sleep 0.2; done
  return 1
}
call POST /api/auth/password-reset-requests "" '{"email":"learner.a@example.test"}'
check "active recovery acknowledges with the same generic 202" "$STATUS" 202
await_token learner.a@example.test || true
check "worker issued one capability (User row lock + token DELETE/INSERT)" "$(token_count learner.a@example.test)" 1
check "PT30M lifetime and only a 32-byte digest stored" "$(psql_admin <<<"SELECT (t.expires_at - t.issued_at)::text || '/' || octet_length(t.token_digest) FROM public.password_reset_tokens t JOIN public.users u ON u.id=t.user_id WHERE u.email='learner.a@example.test';")" "00:30:00/32"
issued="$(psql_admin <<<"SELECT t.issued_at FROM public.password_reset_tokens t JOIN public.users u ON u.id=t.user_id WHERE u.email='learner.a@example.test';")"
# Single FIFO worker: once learner.b's capability exists, the earlier repeat and locked requests were processed.
for email in learner.a@example.test learner.c@example.test nobody@example.test learner.b@example.test; do
  call POST /api/auth/password-reset-requests "" "{\"email\":\"$email\"}"
  check "request acknowledged identically ($email)" "$STATUS/$BODY" '202/{"message":"If an account matches, a password reset link will be sent."}'
done
await_token learner.b@example.test || true
check "later eligible account issued in FIFO order" "$(token_count learner.b@example.test)" 1
check "PT1M repeat left the first capability unchanged" "$(psql_admin <<<"SELECT t.issued_at FROM public.password_reset_tokens t JOIN public.users u ON u.id=t.user_id WHERE u.email='learner.a@example.test';")" "$issued"
check "locked account receives no capability" "$(token_count learner.c@example.test)" 0
check "only the two eligible capabilities exist" "$(psql_admin <<<"SELECT count(*) FROM public.password_reset_tokens;")" 2
check "backend log never contains a recipient address" "$(docker logs "$backend" 2>&1 | grep -ciE 'learner\.[abc]@example\.test|nobody@example\.test' || true)" 0

echo "== no permission failure anywhere, and the matrix is unchanged"
denials="$(docker logs "$backend" 2>&1 | count_permission_errors)"
check "backend log entries reporting permission denied / SQLSTATE 42501" "$denials" 0
verify_runtime_privileges complete >/dev/null
echo "RUNTIME_CONSUMER_TESTS_PASS: real Auth, ADR014 refresh sessions, AU07 request worker and reset confirmation, catalogue/detail, CT22 membership, CO08 progress/resume, the AD07 skill graph, the AS05 question bank, AS06 quiz composition, CO06 Instructor authoring, CO09 relational materials, AS09 owner assignments, the IN10 audit read, IN07 notifications and IN06 discussion/report intake under the matrix"
BASH
PGTEST_CONTAINER="$container" PGTEST_NETWORK="$network" \
  BACKEND_IMAGE="${BACKEND_IMAGE:-codemastery-backend:privilege-test}" \
  bash "$work/exercise-runtime.sh" "$script" "$work/env/.env" "$work/http"

# CT17-P6 r4 upgrade with the real backend and real Flyway on a fresh cluster: Flyway
# stops at V008 (spring.flyway.target), the previous release's r3 grants are applied,
# then the deploy path's pre-migration provisioning, Flyway V009 and r4 hardening run.
cat > "$work/exercise-upgrade.sh" <<'BASH'
set -Eeuo pipefail
source "$1"
ENV_FILE="$2"; work="$3"
pgcompose() {
  [[ "$1" == exec ]] || return 97; shift
  [[ "$1" == -T ]] && shift
  [[ "$1" == postgres ]] && shift
  docker exec -i "$PGTEST_CONTAINER" "$@"
}
COMPOSE=(pgcompose)
backend="${PGTEST_CONTAINER}-backend"
port="${BACKEND_PORT:-18080}"
api="http://127.0.0.1:${port}"
trap 'docker rm -f "$backend" >/dev/null 2>&1 || true' EXIT
check() { [[ "$2" == "$3" ]] || { echo "CHECK FAILED: $1 (expected $3, got $2)" >&2; docker logs "$backend" 2>&1 | tail -20 >&2; exit 1; }; printf '  ok: %-64s %s\n' "$1" "$2"; }
call() {  # method path token body -> sets STATUS and BODY
  local args=(-s -o "$work/body" -w '%{http_code}' -X "$1" "$api$2" -H 'Accept: application/json')
  [[ -z "$3" ]] || args+=(-H "Authorization: Bearer $3")
  [[ -z "${4:-}" ]] || args+=(-H 'Content-Type: application/json' --data-binary "$4")
  STATUS="$(curl "${args[@]}")"; BODY="$(cat "$work/body")"
}
field() { python3 -c 'import json,sys; d=json.loads(sys.argv[1]); [d:=d[int(k)] if isinstance(d,list) else d[k] for k in sys.argv[2].split(".")]; print(d)' "$BODY" "$1"; }
history() { psql_admin <<<"SELECT count(*) FILTER (WHERE success) || '/' || count(*) FROM public.flyway_schema_history WHERE version IS NOT NULL;"; }
has_column() { psql_admin <<<"SELECT count(*) FROM pg_attribute WHERE attrelid='public.enrollments'::regclass AND attname='last_visited_at' AND NOT attisdropped;"; }

echo "== upgrade: provision an empty database, then real Flyway up to V008 only"
provision_db_roles >/dev/null
umask 077
{ printf 'DB_HOST=postgres\nDB_PORT=5432\nDB_NAME=%s\nDB_USER=%s\nDB_PASSWORD=%s\n' "$(env_get DB_NAME)" "$(env_get DB_USER)" "$(env_get DB_PASSWORD)"
  printf 'SPRING_FLYWAY_URL=%s\nSPRING_FLYWAY_USER=%s\nSPRING_FLYWAY_PASSWORD=%s\n' "$(env_get SPRING_FLYWAY_URL)" "$(env_get SPRING_FLYWAY_USER)" "$(env_get SPRING_FLYWAY_PASSWORD)"
  printf 'JWT_SECRET=%s\nCORS_ALLOWED_ORIGINS=https://codemastery.example.test\nSTORAGE_ACCESS_KEY=unused-in-this-test\nSTORAGE_SECRET_KEY=unused-in-this-test\nSPRING_PROFILES_ACTIVE=prod\nAPP_QUIZ_AUTO_SUBMIT_ENABLED=false\nAPP_QUIZ_AUTO_SUBMIT_DELAY_MS=1000\nAPP_QUIZ_AUTO_SUBMIT_BATCH_SIZE=2\n' "$(openssl rand -hex 32)"
} > "$work/upgrade.env"
{ cat "$work/upgrade.env"; printf 'SPRING_FLYWAY_TARGET=8\n'; } > "$work/upgrade-v008.env"
# This build's entities expect V009, so schema validation stops it once Flyway has
# committed V001-V008; only the migrated schema is used.
docker run -d --name "$backend" --network "$PGTEST_NETWORK" --env-file "$work/upgrade-v008.env" "$BACKEND_IMAGE" >/dev/null
for _ in $(seq 1 180); do
  [[ "$(history 2>/dev/null)" == "8/8" ]] && break
  sleep 1
done
docker rm -f "$backend" >/dev/null
check "real Flyway stopped at V008 (8 successful versioned migrations)" "$(history)" "8/8"
check "V008 schema has no enrollments.last_visited_at" "$(has_column)" 0
# The previous release's r3 grants: this release's predecessor grants minus the r4 rows.
application_grants_sql present | psql_admin >/dev/null
psql_admin >/dev/null <<SQL
REVOKE UPDATE (resume_lesson_id) ON public.enrollments FROM "$(env_get DB_USER)";
REVOKE ALL ON public.lesson_progress FROM "$(env_get DB_USER)";
SQL
pre_fix="$( { printf 'BEGIN;\n'; application_grants_sql complete; printf 'ROLLBACK;\n'; } | psql_admin 2>&1 || true)"
check "whole r4 matrix before V009 fails as the pre-fix provisioning did" \
  "$([[ "$pre_fix" == *'column "last_visited_at" of relation "enrollments" does not exist'* ]] && echo reproduced)" reproduced

echo "== a column-present schema is provisioned as current, then real Flyway refuses an untracked state"
# Provisioning checks objects and privileges; migration state stays Flyway's and the deploy
# gate's responsibility (flyway_schema_history must be all successful before hardening).
mig_sql() { psql_as "$(env_get SPRING_FLYWAY_USER)" "$(env_get SPRING_FLYWAY_PASSWORD)" >/dev/null; }
expect_backend_refuses() {  # label: the real backend must stop before 'Started', leaving history unchanged
  local label="$1" before after logs started=no
  before="$(psql_admin <<<"SELECT coalesce(string_agg(version || ':' || success, ',' ORDER BY installed_rank), 'none') FROM public.flyway_schema_history;" 2>/dev/null || echo no-history)"
  docker run -d --name "$backend" --network "$PGTEST_NETWORK" --env-file "$work/upgrade.env" "$BACKEND_IMAGE" >/dev/null
  for _ in $(seq 1 180); do
    [[ "$(docker inspect -f '{{.State.Running}}' "$backend")" == true ]] || break
    logs="$(docker logs "$backend" 2>&1)"; [[ "$logs" != *'Started CodeMasteryApplication'* ]] || { started=yes; break; }
    sleep 1
  done
  logs="$(docker logs "$backend" 2>&1)"
  docker rm -f "$backend" >/dev/null
  after="$(psql_admin <<<"SELECT coalesce(string_agg(version || ':' || success, ',' ORDER BY installed_rank), 'none') FROM public.flyway_schema_history;" 2>/dev/null || echo no-history)"
  check "$label: backend refused to start" "$started/$([[ "$logs" == *FlywayValidateException* || "$logs" == *'Migration V009'*failed* || "$logs" == *'non-empty schema'* || "$logs" == *'already exists'* ]] && echo flyway)" "no/flyway"
  check "$label: Flyway history unchanged" "$after" "$before"
}
mig_sql <<<"ALTER TABLE public.enrollments ADD COLUMN last_visited_at timestamptz NULL;"
provision_db_roles >/dev/null
check "manually added column on V008: provisioning grants the r4 matrix (Copilot's observation)" "$(verify_runtime_privileges present >/dev/null && echo exact)" exact
expect_backend_refuses "manually added column on V008"
# Without any Flyway history (dumped, dropped and later restored by the administrator; a
# renamed or moved table would itself be rejected by provisioning or the role policy).
history_dump="$(docker exec "$PGTEST_CONTAINER" pg_dump -U "$(env_get POSTGRES_USER)" -d "$(env_get POSTGRES_DB)" --table=public.flyway_schema_history)"
psql_admin >/dev/null <<<"DROP TABLE public.flyway_schema_history;"
provision_db_roles >/dev/null
expect_backend_refuses "column present without Flyway history"
printf '%s\n' "$history_dump" | psql_admin >/dev/null
check "Flyway history restored" "$(history)" "8/8"
psql_admin >/dev/null <<<"INSERT INTO public.flyway_schema_history VALUES (9,'009','add enrollment last visited at','SQL','V009__add_enrollment_last_visited_at.sql',0,'$(env_get SPRING_FLYWAY_USER)',now(),1,false);"
provision_db_roles >/dev/null
expect_backend_refuses "column present with a failed V009 history row"
psql_admin >/dev/null <<<"DELETE FROM public.flyway_schema_history WHERE installed_rank=9;"
mig_sql <<<"ALTER TABLE public.enrollments DROP COLUMN last_visited_at;"
application_grants_sql present | psql_admin >/dev/null
psql_admin >/dev/null <<SQL
REVOKE UPDATE (resume_lesson_id) ON public.enrollments FROM "$(env_get DB_USER)";
REVOKE ALL ON public.lesson_progress FROM "$(env_get DB_USER)";
SQL
check "exact V008 predecessor with r3 grants restored" "$(history)/$(has_column)" "8/8/0"

echo "== deploy path: pre-migration provisioning, real Flyway V009, r4 hardening"
provision_db_roles >/dev/null
check "pre-migration verification on the V008 predecessor" "$(verify_runtime_privileges present >/dev/null && echo exact)" exact
docker run -d --name "$backend" --network "$PGTEST_NETWORK" -p "127.0.0.1:${port}:8080" --env-file "$work/upgrade.env" "$BACKEND_IMAGE" >/dev/null
started=no
for _ in $(seq 1 180); do
  logs="$(docker logs "$backend" 2>&1)" && [[ "$logs" == *'Started CodeMasteryApplication'* ]] && { started=yes; break; }
  [[ "$(docker inspect -f '{{.State.Running}}' "$backend")" == true ]] || break
  sleep 1
done
check "backend applied V009 and started" "$started/$(history)/$(has_column)" "yes/9/9/1"
harden_after_migration
verify_role_separation >/dev/null
check "complete r4 verification after the upgrade" "$(verify_runtime_privileges complete >/dev/null && echo exact)" exact

echo "== CO08 visit on the upgraded database under the runtime role"
psql_admin >/dev/null <<'SQL'
INSERT INTO public.users VALUES ('10000000-0000-0000-0000-0000000000a1','instructor@example.test','not-a-login-hash','Test Instructor','instructor','active');
INSERT INTO public.courses VALUES ('20000000-0000-0000-0000-0000000000c1','10000000-0000-0000-0000-0000000000a1','Algorithms','Published course',NULL,'published',false,NULL);
INSERT INTO public.chapters VALUES ('30000000-0000-0000-0000-0000000000d1','20000000-0000-0000-0000-0000000000c1','Basics',1);
INSERT INTO public.lessons VALUES ('40000000-0000-0000-0000-0000000000e1','30000000-0000-0000-0000-0000000000d1','Published lesson','Body',1,'published');
SQL
course=20000000-0000-0000-0000-0000000000c1; lesson=40000000-0000-0000-0000-0000000000e1
password='Correct-Horse-Battery-2026'
call POST /api/auth/register "" "{\"email\":\"upgrade.learner@example.test\",\"password\":\"$password\",\"displayName\":\"Upgrade learner\"}"
check "register" "$STATUS" 201
call POST /api/auth/login "" "{\"email\":\"upgrade.learner@example.test\",\"password\":\"$password\"}"
check "login" "$STATUS" 200
token="$(field accessToken)"
call PUT "/api/courses/$course/membership" "$token"; check "join" "$STATUS/$(field status)" "200/active"
call PUT "/api/courses/$course/resume" "$token" "{\"lessonId\":\"$lesson\"}"
check "visit writes pointer and server time" "$STATUS/$(field resumeLessonId)" "200/$lesson"
check "stored pointer and visit time" "$(psql_admin <<<"SELECT (resume_lesson_id='$lesson' AND last_visited_at IS NOT NULL)::text FROM public.enrollments;")" "true"
call PUT "/api/courses/$course/lessons/$lesson/completion" "$token" '{"completed":true}'
check "completion on the upgraded database" "$STATUS/$(field membershipStatus)" "200/completed"
denials="$(docker logs "$backend" 2>&1 | count_permission_errors)"
check "backend log entries reporting permission denied / SQLSTATE 42501" "$denials" 0
echo "RUNTIME_UPGRADE_TESTS_PASS: real Flyway V008 with r3 grants -> pre-migration provisioning -> V009 -> exact r4 and CO08 writes"
BASH
docker rm -f "${container}-backend" >/dev/null 2>&1 || true
start_postgres
PGTEST_CONTAINER="$container" PGTEST_NETWORK="$network" \
  BACKEND_IMAGE="${BACKEND_IMAGE:-codemastery-backend:privilege-test}" \
  bash "$work/exercise-upgrade.sh" "$script" "$work/env/.env" "$work/http"
