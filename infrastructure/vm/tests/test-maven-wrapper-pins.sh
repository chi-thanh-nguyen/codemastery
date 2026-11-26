#!/usr/bin/env bash
# Host Maven Wrapper archive-pin regression (IR293-SEC-01).
#
# Runs the committed backend/mvnw, and the PowerShell body of backend/mvnw.cmd
# when pwsh is available, against a synthetic distribution served from a local
# file mirror. The fixture is not Apache Maven: these cases check the Wrappers'
# pin enforcement, archive verification and installation logic, not the
# provenance of the official archives. Nothing is downloaded from the network
# and no real Maven cache is used.
#
# MAVEN_WRAPPER_TEST_SHELLS        shells that run mvnw (default: /bin/sh)
# MAVEN_WRAPPER_TEST_REQUIRE_PWSH  "true" fails instead of skipping the
#                                  PowerShell cases when pwsh is unavailable
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
backend="$repo/backend"
committed="$backend/.mvn/wrapper/maven-wrapper.properties"
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/maven-wrapper-pins.XXXXXXXX")"
trap 'rm -rf -- "$work"' EXIT
work="$(cd "$work" && pwd -P)"

top=apache-maven-3.9.16
url="$(sed -n 's/^distributionUrl=//p' "$committed" | tr -d '\r')"
unix_cases=0
ps_cases=0
out=''

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  [ -z "$out" ] || printf '%s\n' "$out" | sed 's/^/  | /' >&2
  exit 1
}
pass() {
  printf 'PASS: %s\n' "$1"
  if [ "${2-}" = ps ]; then ps_cases=$((ps_cases + 1)); else unix_cases=$((unix_cases + 1)); fi
}
sha256() {
  if command -v sha256sum >/dev/null; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi
}

# Synthetic ZIP and TAR.GZ archives: the reviewed mirror and a tampered one.
read -r zip_pin tgz_pin tampered_zip_pin < <(python3 -I - "$work" "$top" <<'PY'
import hashlib, io, os, sys, tarfile, zipfile
work, top = sys.argv[1], sys.argv[2]
def launcher(tag):
    # One append per run, so concurrent runs cannot interleave within a line.
    return ("#!/bin/sh\n"
            "line='exec %s'\n"
            "for a in \"$@\"; do line=\"$line [$a]\"; done\n"
            "printf '%%s\\n' \"$line\" >>\"$PIN_TEST_LOG\"\n"
            "case \"${1-}\" in exit=*) exit \"${1#exit=}\" ;; esac\n" % tag).encode()
def entries(tag):
    return [("bin/mvn", launcher(tag), 0o755),
            ("bin/mvn.cmd", b"@echo synthetic\r\n", 0o644),
            ("lib/fixture.txt", ("synthetic %s\n" % tag).encode(), 0o644)]
digests = {}
for dest, tag in (("mirror", "fixture"), ("tampered", "TAMPERED")):
    d = os.path.join(work, dest, "org/apache/maven/apache-maven/3.9.16")
    os.makedirs(d)
    zpath = os.path.join(d, top + "-bin.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for name in ("", "bin/", "lib/"):
            info = zipfile.ZipInfo(top + "/" + name, (2000, 1, 1, 0, 0, 0))
            info.create_system, info.external_attr = 3, (0o40755 << 16) | 0x10
            z.writestr(info, b"")
        for name, data, mode in entries(tag):
            info = zipfile.ZipInfo(top + "/" + name, (2000, 1, 1, 0, 0, 0))
            info.create_system, info.external_attr = 3, (0o100000 | mode) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    tpath = os.path.join(d, top + "-bin.tar.gz")
    with tarfile.open(tpath, "w:gz") as t:
        for name in ("", "/bin", "/lib"):
            info = tarfile.TarInfo(top + name)
            info.type, info.mode = tarfile.DIRTYPE, 0o755
            t.addfile(info)
        for name, data, mode in entries(tag):
            info = tarfile.TarInfo(top + "/" + name)
            info.size, info.mode = len(data), mode
            t.addfile(info, io.BytesIO(data))
    for kind, path in (("zip", zpath), ("tgz", tpath)):
        with open(path, "rb") as f:
            digests[dest + kind] = hashlib.sha256(f.read()).hexdigest()
print(digests["mirrorzip"], digests["mirrortgz"], digests["tamperedzip"])
PY
)
[ "${#zip_pin}" -eq 64 ] && [ "${#tgz_pin}" -eq 64 ] || fail "fixture archives were not created"
[ "$tampered_zip_pin" != "$zip_pin" ] || fail "tampered fixture is identical"

# The committed properties with only the two pin values replaced. This also
# requires the committed file to keep exactly one exact line for each pin.
fixture_props() {
  awk -v z="$1" -v t="$2" '
    /^distributionSha256Sum=/ { print "distributionSha256Sum=" z; zc++; next }
    /^distributionTarGzSha256Sum=/ { print "distributionTarGzSha256Sum=" t; tc++; next }
    { print }
    END { if (zc != 1 || tc != 1) exit 1 }' "$committed" \
    || fail "committed Wrapper properties must hold one exact line for each archive pin"
}
valid="$(fixture_props "$zip_pin" "$tgz_pin")"
without() { printf '%s\n' "$valid" | grep -v "^$1="; }
replace() { printf '%s\n' "$valid" | sed "s|^$1=.*|$2|"; }

# Minimal PATH toolsets. curl, unzip and tar are shims that record each call.
make_tools() { # dir [no-unzip] [no-sha256sum]
  local dir=$1 cmd real
  shift
  mkdir -p "$dir"
  for cmd in uname dirname basename tr awk mktemp mkdir rm mv cat ls gzip sha256sum shasum; do
    case " $* " in *" no-$cmd "*) continue ;; esac
    real="$(command -v "$cmd" || true)"
    case "$real" in /*) ln -s "$real" "$dir/$cmd" ;; esac
  done
  for cmd in curl unzip tar; do
    case " $* " in *" no-$cmd "*) continue ;; esac
    real="$(command -v "$cmd")" || fail "required tool $cmd is unavailable"
    printf '#!/bin/sh\necho %s >>"$PIN_TEST_LOG"\nexec "%s" "$@"\n' "$cmd" "$real" >"$dir/$cmd"
    chmod +x "$dir/$cmd"
  done
  [ -e "$dir/sha256sum" ] || [ -e "$dir/shasum" ] || fail "sha256sum or shasum is required"
}
tools_zip="$work/tools-zip"
tools_tgz="$work/tools-tgz"
make_tools "$tools_zip"
make_tools "$tools_tgz" no-unzip
tools_shasum=''
if command -v shasum >/dev/null && command -v sha256sum >/dev/null; then
  tools_shasum="$work/tools-shasum"
  make_tools "$tools_shasum" no-sha256sum
fi

mirror="file://$work/mirror"
tampered="file://$work/tampered"
empty_mirror="file://$work/no-mirror"
case_no=0
new_case() { # [m2 to copy]
  case_no=$((case_no + 1))
  case_dir="$work/cases/$case_no with space"
  project="$case_dir/project"
  m2="$case_dir/m2"
  log="$case_dir/log"
  mkdir -p "$project/.mvn/wrapper" "$case_dir/home"
  cp "$backend/mvnw" "$backend/mvnw.cmd" "$project/"
  [ -z "${1-}" ] || cp -R "$1" "$m2"
  : >"$log"
}
props() { printf '%s\n' "$1" >"$project/.mvn/wrapper/maven-wrapper.properties"; }
snapshot() { if [ -e "$m2" ]; then (cd "$m2" && find . | LC_ALL=C sort); fi; }
installs() { ls -A "$m2/wrapper/dists/$top" 2>/dev/null || true; }

run_unix() { # toolset mirror [VAR=value ...] -- args
  local tools=$1 repo_url=$2 envs=()
  shift 2
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  before="$(snapshot)"
  set +e
  out="$(env -i PATH="$tools" HOME="$case_dir/home" MAVEN_USER_HOME="$m2" MVNW_REPOURL="$repo_url" \
    PIN_TEST_LOG="$log" ${envs[@]+"${envs[@]}"} "$shell" "$project/mvnw" "$@" </dev/null 2>&1)"
  rc=$?
  set -e
}
config_rejected() { # name
  [ "$rc" -ne 0 ] || fail "$1: accepted"
  case "$out" in *"Invalid Maven Wrapper archive pins"*) ;; *) fail "$1: not rejected for its pins" ;; esac
  [ ! -s "$log" ] || fail "$1: download, extraction or Maven ran: $(tr '\n' ' ' <"$log")"
  [ "$(snapshot)" = "$before" ] || fail "$1: the Maven user home changed"
  pass "$1"
}
digest_rejected() { # name format
  [ "$rc" -ne 0 ] || fail "$1: accepted"
  case "$out" in *"Failed to validate Maven distribution SHA-256 for $2."*) ;; *) fail "$1: not rejected for its digest" ;; esac
  [ "$(cat "$log")" = curl ] || fail "$1: expected only the download, saw: $(tr '\n' ' ' <"$log")"
  [ -z "$(installs)" ] || fail "$1: left an installation or staging directory: $(installs)"
  pass "$1"
}
accepted() { # name expected-log pin
  [ "$rc" -eq 0 ] || fail "$1: exit $rc"
  [ "$(cat "$log")" = "$2" ] || fail "$1: expected log '$2', saw '$(cat "$log")'"
  [ "$(installs)" = "$(installs | grep -- "-$3\$")" ] && [ "$(installs | wc -l | tr -d ' ')" -eq 1 ] \
    || fail "$1: expected one installation bound to its pin, saw: $(installs | tr '\n' ' ')"
  pass "$1"
}

for shell in ${MAVEN_WRAPPER_TEST_SHELLS:-/bin/sh}; do
  echo "== mvnw under $shell"

  new_case
  props "$(cat "$committed")"
  run_unix "$tools_zip" "$empty_mirror" -- --version
  [ "$rc" -ne 0 ] && [ "$(cat "$log")" = curl ] && [ -z "$(installs)" ] \
    || fail "P00 committed pins: the Wrapper did not proceed to the pinned download"
  case "$out" in *"Invalid Maven Wrapper archive pins"*) fail "P00 committed pins rejected" ;; esac
  pass "P00 committed pins are accepted and lead to the download"

  new_case
  props "$valid"
  run_unix "$tools_zip" "$mirror" -- verify "two words" ""
  accepted "P01 ZIP cold install" "$(printf 'curl\nunzip\nexec fixture [verify] [two words] []')" "$zip_pin"
  zip_m2="$m2"
  zip_install="$(installs)"
  : >"$log"
  run_unix "$tools_zip" "$mirror" -- -q
  accepted "P02 ZIP warm reuse without download" "exec fixture [-q]" "$zip_pin"
  : >"$log"
  run_unix "$tools_zip" "$mirror" -- exit=7
  [ "$rc" -eq 7 ] && [ "$(cat "$log")" = "exec fixture [exit=7]" ] || fail "P03 exit status not propagated ($rc)"
  pass "P03 Maven exit status propagates"

  new_case
  props "$valid"
  run_unix "$tools_tgz" "$mirror" -- validate
  accepted "P04 TAR.GZ cold install without unzip" "$(printf 'curl\ntar\nexec fixture [validate]')" "$tgz_pin"
  : >"$log"
  run_unix "$tools_tgz" "$mirror" -- validate
  accepted "P05 TAR.GZ warm reuse without download" "exec fixture [validate]" "$tgz_pin"

  if [ -n "$tools_shasum" ]; then
    new_case
    props "$valid"
    run_unix "$tools_shasum" "$mirror" -- validate
    accepted "P06 ZIP cold install verified by shasum" "$(printf 'curl\nunzip\nexec fixture [validate]')" "$zip_pin"
  fi

  new_case
  props "$(printf '%s\n' "$valid" | sed -e 's/^\(distribution[A-Za-z0-9]*Sha256Sum=\)\(.*\)$/\1 \2 	/' -e 's/$/\r/')"
  run_unix "$tools_zip" "$mirror" -- validate
  accepted "P07 CRLF properties with spaces around pin values" "$(printf 'curl\nunzip\nexec fixture [validate]')" "$zip_pin"

  new_case
  props "$valid"
  before="$(snapshot)"
  pids=()
  for i in 1 2; do
    env -i PATH="$tools_zip" HOME="$case_dir/home" MAVEN_USER_HOME="$m2" MVNW_REPOURL="$mirror" \
      PIN_TEST_LOG="$log" "$shell" "$project/mvnw" "run$i" </dev/null >"$case_dir/out$i" 2>&1 &
    pids+=("$!")
  done
  rc1=0 rc2=0
  wait "${pids[0]}" || rc1=$?
  wait "${pids[1]}" || rc2=$?
  out="$(cat "$case_dir/out1" "$case_dir/out2")"
  [ "$rc1" -eq 0 ] && [ "$rc2" -eq 0 ] || fail "P08 concurrent cold installs exited $rc1 and $rc2"
  [ "$(grep -c '^exec fixture \[run[12]\]$' "$log")" -eq 2 ] || fail "P08 concurrent runs did not both start Maven"
  [ "$(installs)" = "$(installs | grep -- "-$zip_pin\$")" ] && [ "$(installs | wc -l | tr -d ' ')" -eq 1 ] \
    || fail "P08 expected one published installation, saw: $(installs | tr '\n' ' ')"
  pass "P08 concurrent cold installs publish one installation"

  while IFS='|' read -r id description content; do
    new_case
    props "$(eval "$content")"
    run_unix "$tools_zip" "$mirror" -- validate
    config_rejected "$id $description"
  done <<EOF
H01|both pin declarations absent|printf '%s\n' "\$valid" | grep -v '^distribution[A-Za-z]*Sha256Sum='
H02|both pin values blank|printf '%s\n' "\$valid" | sed 's/^\(distribution[A-Za-z]*Sha256Sum=\).*/\1/'
H03|ZIP pin absent|without distributionSha256Sum
H04|TAR.GZ pin absent|without distributionTarGzSha256Sum
H05|TAR.GZ pin blank|replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum='
H06a|ZIP pin of 63 characters|replace distributionSha256Sum "distributionSha256Sum=\${zip_pin%?}"
H06b|ZIP pin of 65 characters|replace distributionSha256Sum "distributionSha256Sum=\${zip_pin}0"
H06c|ZIP pin in uppercase|replace distributionSha256Sum "distributionSha256Sum=\$(printf %s "\$zip_pin" | tr a-f A-F)"
H06d|ZIP pin with a non-hex character|replace distributionSha256Sum "distributionSha256Sum=g\${zip_pin#?}"
H06e|ZIP pin with an inner space|replace distributionSha256Sum "distributionSha256Sum=\${zip_pin%????????} \${zip_pin#????????????????????????????????????????????????????????}"
H06f|malformed TAR.GZ pin while ZIP is selected|replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum=XYZ'
H07a|identical duplicate ZIP declaration|printf '%s\ndistributionSha256Sum=%s\n' "\$valid" "\$zip_pin"
H07b|wrong then correct ZIP declaration|printf 'distributionSha256Sum=%s\n%s\n' "\$tgz_pin" "\$valid"
H08|identical duplicate TAR.GZ declaration|printf '%s\ndistributionTarGzSha256Sum=%s\n' "\$valid" "\$tgz_pin"
H09a|ZIP pin with leading whitespace only|replace distributionSha256Sum " distributionSha256Sum=\$zip_pin"
H09b|ZIP pin with space before the separator|replace distributionSha256Sum "distributionSha256Sum = \$zip_pin"
H09c|ZIP pin with a colon separator|replace distributionSha256Sum "distributionSha256Sum:\$zip_pin"
H09d|ZIP pin with a whitespace separator|replace distributionSha256Sum "distributionSha256Sum \$zip_pin"
H09e|additional case-variant ZIP declaration|printf '%s\nDISTRIBUTIONSHA256SUM=%s\n' "\$valid" "\$zip_pin"
EOF

  new_case
  props "$(printf '%s\n' "$valid" | grep -v '^distribution[A-Za-z]*Sha256Sum=')"
  run_unix "$tools_zip" "$mirror" distributionSha256Sum="$zip_pin" distributionTarGzSha256Sum="$tgz_pin" -- validate
  config_rejected "H14 absent pins supplied only by inherited variables"

  new_case
  props "$valid"
  run_unix "$tools_zip" "$tampered" -- validate
  digest_rejected "H10 tampered ZIP from the mirror" zip
  new_case
  props "$(replace distributionSha256Sum "distributionSha256Sum=$tampered_zip_pin")"
  run_unix "$tools_zip" "$mirror" -- validate
  digest_rejected "H10b wrong well-formed ZIP pin" zip
  new_case
  props "$valid"
  run_unix "$tools_tgz" "$tampered" -- validate
  digest_rejected "H11 tampered TAR.GZ from the mirror" tar.gz

  while IFS='|' read -r id description content; do
    new_case "$zip_m2"
    props "$(eval "$content")"
    run_unix "$tools_zip" "$mirror" -- validate
    config_rejected "$id $description with a warm installation"
  done <<EOF
H12a|malformed TAR.GZ pin|replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum=XYZ'
H12b|wrong then correct ZIP declaration|printf 'distributionSha256Sum=%s\n%s\n' "\$tgz_pin" "\$valid"
H12c|absent TAR.GZ pin|without distributionTarGzSha256Sum
EOF

  # The unpinned upstream namespace is the pinned name without its suffix.
  new_case
  legacy="$m2/wrapper/dists/$top/${zip_install%-"$zip_pin"}"
  mkdir -p "$legacy/bin"
  printf '#!/bin/sh\necho "exec LEGACY $*" >>"$PIN_TEST_LOG"\n' >"$legacy/bin/mvn"
  chmod +x "$legacy/bin/mvn"
  props "$(printf '%s\n' "$valid" | sed 's/^\(distribution[A-Za-z]*Sha256Sum=\).*/\1/')"
  run_unix "$tools_zip" "$mirror" -- validate
  config_rejected "H13 blank pins with an unpinned installation present"
done

if command -v pwsh >/dev/null; then
  echo "== mvnw.cmd PowerShell body under pwsh (not Windows PowerShell or cmd.exe)"
  ps_name() { printf '%s#%s' "$1/org/apache/maven/apache-maven/3.9.16/$top-bin.zip" "$2" | sha256; }
  run_ps() { # mirror [VAR=value ...]
    local repo_url=$1
    shift
    before="$(snapshot)"
    set +e
    env "$@" HOME="$case_dir/home" MAVEN_USER_HOME="$m2" MVNW_REPOURL="$repo_url" NO_COLOR=1 \
      PIN_TEST_DIR="$project/" PIN_TEST_WRAPPER="$project/mvnw.cmd" \
      pwsh -NoLogo -NoProfile -NonInteractive -Command '& {
        $scriptDir = $env:PIN_TEST_DIR; $script = "mvnw.cmd"
        icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw $env:PIN_TEST_WRAPPER))) -NoNewScope
      }' </dev/null >"$case_dir/ps.out" 2>&1
    rc=$?
    set -e
    out="$(sed 's/\x1b\[[0-9;]*m//g' "$case_dir/ps.out")"
  }
  ps_rejected() { # name phrase
    [ "$rc" -ne 0 ] || fail "$1: accepted"
    case "$out" in *"$2"*) ;; *) fail "$1: not rejected for the expected reason" ;; esac
    case "$out" in *MVN_CMD=*) fail "$1: emitted a launcher" ;; esac
    pass "$1" ps
  }
  ps_config_rejected() {
    ps_rejected "$1" "Invalid Maven Wrapper archive pins"
    [ "$(snapshot)" = "$before" ] || fail "$1: the Maven user home changed"
  }
  # The batch ignores PowerShell's exit status (upstream's warm path exits 1)
  # and starts Maven only from an emitted MVN_CMD line.
  ps_accepted() { # name pin
    local launcher
    launcher="$m2/wrapper/dists/$top/$(ps_name "$mirror" "$2")/bin/mvn.cmd"
    [ "$out" = "MVN_CMD=$launcher" ] || fail "$1: unexpected launcher output"
    [ -f "$launcher" ] && grep -q 'synthetic fixture' "${launcher%/bin/mvn.cmd}/lib/fixture.txt" \
      || fail "$1: the published installation is not the reviewed fixture"
    [ "$(installs | wc -l | tr -d ' ')" -eq 1 ] || fail "$1: unexpected cache entries: $(installs | tr '\n' ' ')"
    pass "$1" ps
  }

  new_case
  props "$valid"
  run_ps "$mirror"
  ps_accepted "W01 ZIP cold install" "$zip_pin"
  ps_m2="$m2"
  mv "$work/mirror" "$work/mirror.off"
  run_ps "$mirror"
  mv "$work/mirror.off" "$work/mirror"
  ps_accepted "W02 ZIP warm reuse with the mirror withdrawn" "$zip_pin"

  new_case
  props "$(printf '%s\n' "$valid" | sed -e 's/^\(distribution[A-Za-z0-9]*Sha256Sum=\)\(.*\)$/\1 \2 	/' -e 's/$/\r/')"
  run_ps "$mirror"
  ps_accepted "W03 CRLF properties with spaces around pin values" "$zip_pin"

  while IFS='|' read -r id description content; do
    new_case
    props "$(eval "$content")"
    run_ps "$mirror"
    ps_config_rejected "$id $description"
  done <<EOF
W-H01|both pin declarations absent|printf '%s\n' "\$valid" | grep -v '^distribution[A-Za-z]*Sha256Sum='
W-H02|both pin values blank|printf '%s\n' "\$valid" | sed 's/^\(distribution[A-Za-z]*Sha256Sum=\).*/\1/'
W-H03|ZIP pin absent|without distributionSha256Sum
W-H04|TAR.GZ pin absent|without distributionTarGzSha256Sum
W-H05|TAR.GZ pin blank|replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum='
W-H06a|ZIP pin in uppercase|replace distributionSha256Sum "distributionSha256Sum=\$(printf %s "\$zip_pin" | tr a-f A-F)"
W-H06b|malformed TAR.GZ pin|replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum=XYZ'
W-H07|wrong then correct ZIP declaration|printf 'distributionSha256Sum=%s\n%s\n' "\$tgz_pin" "\$valid"
W-H08|identical duplicate TAR.GZ declaration|printf '%s\ndistributionTarGzSha256Sum=%s\n' "\$valid" "\$tgz_pin"
W-H09a|ZIP pin with space before the separator|replace distributionSha256Sum "distributionSha256Sum = \$zip_pin"
W-H09b|ZIP pin with a colon separator|replace distributionSha256Sum "distributionSha256Sum:\$zip_pin"
W-H09c|additional case-variant ZIP declaration|printf '%s\nDISTRIBUTIONSHA256SUM=%s\n' "\$valid" "\$zip_pin"
EOF

  new_case
  props "$(printf '%s\n' "$valid" | grep -v '^distribution[A-Za-z]*Sha256Sum=')"
  run_ps "$mirror" distributionSha256Sum="$zip_pin" distributionTarGzSha256Sum="$tgz_pin"
  ps_config_rejected "W-H14 absent pins supplied only by environment variables"

  new_case
  props "$valid"
  run_ps "$tampered"
  ps_rejected "W-H10 tampered ZIP from the mirror" "Failed to validate Maven distribution SHA-256"
  [ -z "$(installs)" ] || fail "W-H10 left an installation or staging directory: $(installs)"

  new_case "$ps_m2"
  props "$(replace distributionTarGzSha256Sum 'distributionTarGzSha256Sum=XYZ')"
  run_ps "$mirror"
  ps_config_rejected "W-H12 malformed TAR.GZ pin with a warm installation"

  # A blank ZIP pin previously selected the name derived from "<url>#".
  new_case
  mkdir -p "$m2/wrapper/dists/$top/$(ps_name "$mirror" '')/bin"
  : >"$m2/wrapper/dists/$top/$(ps_name "$mirror" '')/bin/mvn.cmd"
  props "$(printf '%s\n' "$valid" | sed 's/^\(distribution[A-Za-z]*Sha256Sum=\).*/\1/')"
  run_ps "$mirror"
  ps_config_rejected "W-H13 blank pins with an unpinned installation present"
elif [ "${MAVEN_WRAPPER_TEST_REQUIRE_PWSH-}" = true ]; then
  out=''
  fail "pwsh is required for the mvnw.cmd PowerShell cases"
else
  echo "SKIP: mvnw.cmd PowerShell cases (pwsh unavailable)"
fi

echo "MAVEN_WRAPPER_PIN_TESTS_PASS mvnw=$unix_cases powershell=$ps_cases"
