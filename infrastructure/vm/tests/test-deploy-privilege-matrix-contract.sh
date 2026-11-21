#!/usr/bin/env bash
# CI regression test for job "VM deployment script" (deployment-script),
# step "Check the runtime privilege matrix and its contract record" in .github/workflows/ci-vm.yml.
# Body moved verbatim from that step's former inline `run:` script.
# CI runs it from the repository root, where its relative paths resolve:
#   bash infrastructure/vm/tests/test-deploy-privilege-matrix-contract.sh
set -euo pipefail
script=infrastructure/vm/deploy.sh
bash -c '
  set -euo pipefail
  source "$1"
  runtime_matrix_values >/dev/null
  [[ "$(runtime_matrix_values | wc -l | tr -d " ")" == 27 ]]  # CT17 r1 (24, V001-V006) + r2 password_reset_tokens + r3 refresh_sessions/refresh_tokens (V008); r4 (V009), r5 and r6 change rows only
  approved="$RUNTIME_PRIVILEGE_MATRIX"
  reject() {
    RUNTIME_PRIVILEGE_MATRIX="$2"
    if runtime_matrix_values >/dev/null 2>&1; then
      echo "Malformed matrix accepted: $1" >&2; exit 1
    fi
    if (ENV_FILE=/dev/null; application_grants_sql) >/dev/null 2>&1; then
      echo "Grant SQL produced from a malformed matrix: $1" >&2; exit 1
    fi
    echo "  rejected malformed matrix: $1"
  }
  reject "duplicate relation" "$approved
users SELECT - id"
  reject "privilege outside SELECT/INSERT/UPDATE/DELETE" "users SELECT,TRUNCATE - id"
  reject "column privilege on an unreviewed column" "users SELECT UPDATE(role) id,email"
  reject "column privilege on an unpinned relation" "flyway_schema_history - SELECT(version) *"
  reject "DELETE as a column privilege" "users - DELETE(id) id"
  reject "extra field" "users SELECT - id extra"
  reject "non-identifier relation" "Users SELECT - id"
  reject "empty matrix" ""
  RUNTIME_PRIVILEGE_MATRIX="$approved"
  added="$RUNTIME_MIGRATION_ADDED_COLUMNS"
  [[ "$(runtime_deferred_columns complete)" == "ARRAY[]::text[]" ]]  # never deferred after migration
  for bad in "enrollments last_visited_at" "enrollments last_visited_at 0" "Enrollments last_visited_at 9" "enrollments last_visited_at 9 extra" "enrollments last-visited 9"; do
    RUNTIME_MIGRATION_ADDED_COLUMNS="$bad"
    if (runtime_deferred_columns complete) >/dev/null 2>&1; then
      echo "Malformed migration-added column list accepted: $bad" >&2; exit 1
    fi
    if (ENV_FILE=/dev/null; application_grants_sql) >/dev/null 2>&1; then
      echo "Grant SQL produced from a malformed migration-added column list: $bad" >&2; exit 1
    fi
  done
  RUNTIME_MIGRATION_ADDED_COLUMNS="$added"
  echo "  rejected malformed migration-added column lists"
' _ "$script"
# The executable matrix must equal the dated contract record exactly.
python3 - "$script" docs/deployment.md <<'PY'
import re, sys
script, record = (open(path, encoding="utf-8").read() for path in sys.argv[1:3])
block = re.search(r"^RUNTIME_PRIVILEGE_MATRIX='\n(.*?)^'$", script, re.M | re.S).group(1)
# CT17-P6 r4: only V009's enrollments.last_visited_at may be deferred, and only if reviewed.
added = re.search(r"^RUNTIME_MIGRATION_ADDED_COLUMNS='\n(.*?)^'$", script, re.M | re.S).group(1)
added_rows = [tuple(line.split()) for line in added.splitlines() if line.strip()]
assert added_rows == [("enrollments", "last_visited_at", "9")], f"unexpected migration-added columns: {added_rows}"
reviewed = {line.split()[0]: line.split()[3].split(",") for line in block.splitlines() if line.strip()}
assert all(col in reviewed[rel] for rel, col, _ in added_rows), "a migration-added column must be a reviewed matrix column"
def normalize(table_privs, column_privs):
    return frozenset(table_privs), frozenset(column_privs)
executable = {}
for line in block.splitlines():
    if not line.strip():
        continue
    rel, tprivs, cprivs, _cols = line.split()
    columns = set()
    if cprivs != "-":
        for group in cprivs.split("+"):
            priv, names = re.fullmatch(r"([A-Z]+)\(([a-z_,]+)\)", group).groups()
            columns |= {f"{priv}:{name}" for name in names.split(",")}
    executable[rel] = normalize(set() if tprivs == "-" else set(tprivs.split(",")), columns)
section = record.split("## CT17 runtime database privileges and operation gates — 28 October 2026", 1)[1]
# CT17-P6-PRIVILEGES-2026-10-28-r1 stays authentic history; the Leader-approved
# CT17-P6-PRIVILEGES-2026-11-13-r2 section and the delegated-approved
# CT17-P6-PRIVILEGES-2026-11-16-r3 section (ADR014), the CO08
# CT17-P6-PRIVILEGES-2026-11-17-r4 section, the AD07 CT17-P6-PRIVILEGES-2026-11-22-r5 section and the
# AS05 CT17-P6-PRIVILEGES-2026-11-22-r6 section (all within the ADR012 §19.2 ceiling) replace/add only their own rows.
r1_part, r2_part = section.split("### CT17-P6-PRIVILEGES-2026-11-13-r2", 1)
r2_part = r2_part.split("\n## ", 1)[0].split("\n### ", 1)[0]
r3_part = record.split("### CT17-P6-PRIVILEGES-2026-11-16-r3", 1)[1].split("\n## ", 1)[0].split("\n### ", 1)[0]
r4_part = record.split("### CT17-P6-PRIVILEGES-2026-11-17-r4", 1)[1].split("\n## ", 1)[0].split("\n### ", 1)[0]
r5_part = record.split("### CT17-P6-PRIVILEGES-2026-11-22-r5", 1)[1].split("\n## ", 1)[0].split("\n### ", 1)[0]
r6_part = record.split("### CT17-P6-PRIVILEGES-2026-11-22-r6", 1)[1].split("\n## ", 1)[0].split("\n### ", 1)[0]
def documented_rows(text):
    rows = {}
    for rel, privileges in re.findall(r"^\| `([a-z_]+)` \([a-z]+\) \| ([^|]+?) \|", text, re.M):
        assert rel not in rows, f"{rel} documented twice"
        table, columns = set(), set()
        if privileges != "none":
            for part in privileges.split(";"):
                part = part.strip()
                found = re.fullmatch(r"([A-Z]+) \(([a-z_, ]+)\)", part)
                if found:
                    columns |= {f"{found.group(1)}:{name.strip()}" for name in found.group(2).split(",")}
                else:
                    table |= {name.strip() for name in part.split(",")}
        rows[rel] = normalize(table, columns)
    return rows
r1 = documented_rows(r1_part)
r2 = documented_rows(r2_part)
r3 = documented_rows(r3_part)
r4 = documented_rows(r4_part)
r5 = documented_rows(r5_part)
r6 = documented_rows(r6_part)
assert len(r1) == 24 and "password_reset_tokens" not in r1, "CT17 r1 history must stay the reviewed V001-V006 matrix"
assert r1["users"] == normalize({"SELECT", "INSERT"}, set()), "CT17 r1 users row must stay SELECT, INSERT"
assert r2 == {"users": normalize({"SELECT", "INSERT"}, {"UPDATE:password_hash"}),
              "password_reset_tokens": normalize({"SELECT", "INSERT", "DELETE"}, set())}, "CT17 r2 rows differ from the approved extension"
assert r3 == {"refresh_sessions": normalize({"SELECT", "INSERT", "DELETE"}, {"UPDATE:revoked_at"}),
              "refresh_tokens": normalize({"SELECT", "INSERT", "DELETE"}, {"UPDATE:consumed_at"})}, "CT17 r3 rows differ from the ADR014 extension"
assert r4 == {"enrollments": normalize({"SELECT", "INSERT"}, {"UPDATE:status", "UPDATE:resume_lesson_id", "UPDATE:last_visited_at"}),
              "lesson_progress": normalize({"SELECT", "INSERT"}, {"UPDATE:completed"})}, "CT17 r4 rows differ from the CO08 extension"
assert r5 == {"skills": normalize({"SELECT", "INSERT", "DELETE"}, {"UPDATE:name", "UPDATE:description"}),
              "skill_prerequisites": normalize({"SELECT", "INSERT", "DELETE"}, set()),
              "lesson_skills": normalize({"SELECT", "INSERT", "DELETE"}, set())}, "CT17 r5 rows differ from the AD07 extension"
assert r6 == {"questions": normalize({"SELECT", "INSERT", "DELETE"}, {f"UPDATE:{c}" for c in
                  ("skill_id", "prompt", "kind", "difficulty", "options", "correct_options")}),
              "quiz_questions": normalize(set(), {"SELECT:id", "SELECT:question_id"}),
              "attempt_answers": normalize(set(), {"SELECT:id", "SELECT:question_id"})}, "CT17 r6 rows differ from the AS05 extension"
documented = {**r1, **r2, **r3, **r4, **r5, **r6}
if executable != documented:
    for rel in sorted(set(executable) | set(documented)):
        if executable.get(rel) != documented.get(rel):
            print(f"mismatch for {rel}: script={executable.get(rel)} record={documented.get(rel)}", file=sys.stderr)
    sys.exit("RUNTIME_PRIVILEGE_MATRIX differs from CT17-P6-PRIVILEGES r1 plus the r2, r3, r4, r5 and r6 rows")
print(f"MATRIX_CONTRACT_SYNC_PASS: {len(executable)} relations match r1 ({len(r1)} rows, history intact) plus approved r2 ({len(r2)} rows), r3 ({len(r3)} rows) and ceiling-bounded r4 ({len(r4)} rows) r5 ({len(r5)} rows) and r6 ({len(r6)} rows)")
PY
