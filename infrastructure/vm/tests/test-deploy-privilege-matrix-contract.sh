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
  [[ "$(runtime_matrix_values | wc -l | tr -d " ")" == 25 ]]  # V001-V006 (24) + proposed CT17 r2 password_reset_tokens
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
documented = {}
for rel, privileges in re.findall(r"^\| `([a-z_]+)` \([a-z]+\) \| ([^|]+?) \|", section, re.M):
    assert rel not in documented, f"{rel} documented twice"
    table, columns = set(), set()
    if privileges != "none":
        for part in privileges.split(";"):
            part = part.strip()
            found = re.fullmatch(r"([A-Z]+) \(([a-z_, ]+)\)", part)
            if found:
                columns |= {f"{found.group(1)}:{name.strip()}" for name in found.group(2).split(",")}
            else:
                table |= {name.strip() for name in part.split(",")}
    documented[rel] = normalize(table, columns)
if executable != documented:
    for rel in sorted(set(executable) | set(documented)):
        if executable.get(rel) != documented.get(rel):
            print(f"mismatch for {rel}: script={executable.get(rel)} record={documented.get(rel)}", file=sys.stderr)
    sys.exit("RUNTIME_PRIVILEGE_MATRIX differs from CT17-P6-PRIVILEGES-2026-10-28-r1")
print(f"MATRIX_CONTRACT_SYNC_PASS: {len(executable)} relations match the dated record")
PY
