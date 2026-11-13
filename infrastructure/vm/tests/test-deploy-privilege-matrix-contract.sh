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
  [[ "$(runtime_matrix_values | wc -l | tr -d " ")" == 25 ]]  # CT17 r1 (24, V001-V006) + approved r2 password_reset_tokens
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
' _ "$script"
# The executable matrix must equal the dated contract record exactly.
python3 - "$script" docs/deployment.md <<'PY'
import re, sys
script, record = (open(path, encoding="utf-8").read() for path in sys.argv[1:3])
block = re.search(r"^RUNTIME_PRIVILEGE_MATRIX='\n(.*?)^'$", script, re.M | re.S).group(1)
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
# CT17-P6-PRIVILEGES-2026-11-13-r2 section replaces/adds only its own rows.
r1_part, r2_part = section.split("### CT17-P6-PRIVILEGES-2026-11-13-r2", 1)
r2_part = r2_part.split("\n## ", 1)[0]
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
assert len(r1) == 24 and "password_reset_tokens" not in r1, "CT17 r1 history must stay the reviewed V001-V006 matrix"
assert r1["users"] == normalize({"SELECT", "INSERT"}, set()), "CT17 r1 users row must stay SELECT, INSERT"
assert r2 == {"users": normalize({"SELECT", "INSERT"}, {"UPDATE:password_hash"}),
              "password_reset_tokens": normalize({"SELECT", "INSERT", "DELETE"}, set())}, "CT17 r2 rows differ from the approved extension"
documented = {**r1, **r2}
if executable != documented:
    for rel in sorted(set(executable) | set(documented)):
        if executable.get(rel) != documented.get(rel):
            print(f"mismatch for {rel}: script={executable.get(rel)} record={documented.get(rel)}", file=sys.stderr)
    sys.exit("RUNTIME_PRIVILEGE_MATRIX differs from CT17-P6-PRIVILEGES r1 plus the approved r2 rows")
print(f"MATRIX_CONTRACT_SYNC_PASS: {len(executable)} relations match r1 ({len(r1)} rows, history intact) plus approved r2 ({len(r2)} rows)")
PY
