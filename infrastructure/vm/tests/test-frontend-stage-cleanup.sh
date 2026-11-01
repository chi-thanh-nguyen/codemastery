#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY="$(cd "$(dirname "$0")/.." && pwd)/deploy.sh"
test_fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

validate_events() {
  local events="$1"
  [[ -f "$events" && -r "$events" && -s "$events" ]] \
    || test_fail "Missing, unreadable or empty event log: $events"
  awk '
    !/^(BACKEND_START|PROBE_START|TLS_VERIFIED|HEALTH_VERIFIED|FLYWAY_VERIFIED|PRIVILEGES_VERIFIED|PROBE_STOP|PROBE_PRESENT|PROBE_ABSENT|INVENTORY_ERROR|INVENTORY_INVALID|PUBLIC_START|PUBLIC_START_FAILURE|APPLICATION_STOP|CID_RETAINED|CID_CLEARED|DEPLOYMENT_RECORD|FRONTEND_VERIFIED|TIMEOUT|SIGNAL)$/ { bad=1 }
    END { exit bad ? 1 : 0 }
  ' "$events" || test_fail "Unreadable or malformed event log: $events"
}

assert_no_public() {
  local status
  validate_events "$1"
  if grep -Fxq PUBLIC_START "$1"; then
    test_fail "Unexpected PUBLIC_START in $1"
  else
    status=$?
    [[ "$status" -eq 1 ]] || test_fail "Could not read public-start evidence: $1"
  fi
}

if [[ "${1:-}" == --assert-no-public ]]; then
  assert_no_public "$2"
  exit 0
fi
if [[ "${1:-}" == --assert-read-error ]]; then
  awk() { return 2; }
  assert_no_public "$2"
  exit 0
fi

if [[ "${1:-}" == --scenario ]]; then
  SCENARIO="$2"
  source "$DEPLOY"
  WAIT_TIMEOUT=4
  COMPOSE=(mock_compose)
  PROBE_CID=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  PUBLIC_CID=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  EXTRA_CID=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  [[ "$SCENARIO" != invalid_identity ]] || PROBE_CID=aaaaaaaaaaaa
  STATE="${EVENTS}.state"
  mkdir "$STATE"
  record() { printf '%s\n' "$1" >> "$EVENTS"; }
  log() {
    if [[ "$*" == 'Release stopped.'* ]]; then
      if [[ -n "$STAGED_FRONTEND_CID" ]]; then record CID_RETAINED; else record CID_CLEARED; fi
    fi
  }
  sleep() { SECONDS=$(( SECONDS + $1 )); }
  timeout() {
    [[ "$1" == --signal=KILL ]] || test_fail "Docker deadline cannot force termination"
    shift
    [[ "$1" =~ ^[1-9][0-9]*$ && "$1" -le "$WAIT_TIMEOUT" ]] \
      || test_fail "Unbounded Docker call"
    shift
    if [[ "$SCENARIO" == stop_timeout && "$1 $2" == 'docker stop' ]]; then
      record TIMEOUT; return 124
    fi
    if [[ "$SCENARIO" == inventory_timeout && "$1 $2 $3" == 'docker container ls' && -f "$STATE/stopped" ]]; then
      record TIMEOUT; return 124
    fi
    "$@"
  }
  mock_compose() {
    case "$1" in
      run)
        [[ "$*" == 'run -d --rm --no-deps --pull never frontend' ]] \
          || test_fail "Unexpected probe command or published ports"
        : > "$STATE/probe"
        record PROBE_START
        printf '%s\n' "$PROBE_CID"
        ;;
      up)
        case "${@: -1}" in
          backend) record BACKEND_START ;;
          frontend)
            # Real Compose does not enforce our cleanup prerequisite.
            record PUBLIC_START
            : > "$STATE/public"
            if [[ "$SCENARIO" == frontend_failure ]]; then
              record PUBLIC_START_FAILURE; return 1
            fi
            ;;
          *) test_fail "Unexpected startup" ;;
        esac
        ;;
      ps)
        [[ "$2 $3" == '-a -q' ]] || test_fail "Unexpected Compose inventory"
        case "$4" in
          backend) printf 'backend-id\n' ;;
          frontend)
            [[ ! -f "$STATE/probe" ]] || printf '%s\n' "$PROBE_CID"
            [[ ! -f "$STATE/public" || "$SCENARIO" == missing_frontend ]] || printf '%s\n' "$PUBLIC_CID"
            [[ "$SCENARIO" != duplicate_frontend ]] || printf '%s\n' "$EXTRA_CID"
            ;;
          *) test_fail "Unexpected service inventory" ;;
        esac
        ;;
      stop)
        [[ "$*" == "stop --timeout $WAIT_TIMEOUT frontend backend" ]] \
          || test_fail "Unexpected containment targets"
        record APPLICATION_STOP
        rm -f "$STATE/public"
        ;;
      *) test_fail "Unexpected Compose command: $*" ;;
    esac
  }
  docker() {
    case "$1" in
      stop)
        [[ "$*" == "stop --timeout $WAIT_TIMEOUT $PROBE_CID" ]] \
          || test_fail "Stop targeted another container"
        record PROBE_STOP
        if [[ "$SCENARIO" == stop_failure ]]; then return 1; fi
        if [[ "$SCENARIO" == interrupt || "$SCENARIO" == terminate ]]; then
          if [[ ! -f "$STATE/signalled" ]]; then
            : > "$STATE/signalled"; record SIGNAL
            if [[ "$SCENARIO" == interrupt ]]; then kill -INT "$$"; else kill -TERM "$$"; fi
          fi
        fi
        : > "$STATE/stopped"
        case "$SCENARIO" in
          removal_failure|delayed|inventory_error|inventory_timeout|ambiguous|truncated|duplicate_inventory) ;;
          *) rm -f "$STATE/probe" ;;
        esac
        ;;
      container)
        if [[ "$SCENARIO" == initial_inventory_error ]]; then record INVENTORY_ERROR; return 1; fi
        [[ "$*" == "container ls --all --quiet --no-trunc --filter id=$PROBE_CID" ]] \
          || test_fail "Inventory did not use the exact full ID and all states"
        if [[ -f "$STATE/stopped" ]]; then
          case "$SCENARIO" in
            inventory_error) record INVENTORY_ERROR; return 1 ;;
            ambiguous) record INVENTORY_INVALID; printf '%s\n' "$EXTRA_CID"; return 0 ;;
            truncated) record INVENTORY_INVALID; printf 'aaaaaaaaaaaa\n'; return 0 ;;
            duplicate_inventory) record INVENTORY_INVALID; printf '%s\n%s\n' "$PROBE_CID" "$PROBE_CID"; return 0 ;;
            delayed)
              if [[ -f "$STATE/delayed" ]]; then rm -f "$STATE/probe"; else : > "$STATE/delayed"; fi
              ;;
          esac
        fi
        if [[ -f "$STATE/probe" ]]; then
          record PROBE_PRESENT; printf '%s\n' "$PROBE_CID"
        else
          record PROBE_ABSENT
        fi
        ;;
      exec)
        [[ "$*" == "exec $PROBE_CID nginx -t" ]] || test_fail "Unexpected probe executable"
        ;;
      inspect)
        [[ "${@: -1}" == "$PROBE_CID" ]] || test_fail "Wrong probe inspected"
        case "$3" in
          '{{json .NetworkSettings.Ports}}') printf '{}\n' ;;
          '{{.State.Running}}:{{.State.Health.Status}}') printf 'true:healthy\n' ;;
          *) test_fail "Unexpected inspection" ;;
        esac
        ;;
      *) test_fail "Unexpected Docker command: $*" ;;
    esac
  }
  # Keep production main/traps, staging, cleanup and exact-one inventory checks.
  parse_args() { SUBCOMMAND=deploy; }
  require_inputs() { :; }
  run_preflight() { :; }
  verify_image_coverage() { :; }
  verify_archive() { :; }
  guard_update() { :; }
  verify_images() { :; }
  verify_private_dependencies() { :; }
  provision_db_roles() { :; }
  assert_release_configuration() { :; }
  verify_published_ports() { :; }
  verify_service_container() {
    if [[ "$1" == frontend && "$2" == "$PUBLIC_CID" ]]; then
      [[ -z "$STAGED_FRONTEND_CID" ]] || test_fail "Staged CID was not cleared after verified removal"
      record CID_CLEARED
      record FRONTEND_VERIFIED
    elif [[ "$1" == frontend ]]; then
      [[ "$2" == "$PROBE_CID" ]] || test_fail "Wrong staged identity"
    fi
  }
  verify_role_separation() { record PRIVILEGES_VERIFIED; }
  harden_after_migration() { :; }
  wait_backend_health() { record HEALTH_VERIFIED; }
  psql_admin() { record FLYWAY_VERIFIED; printf 'ok\n'; }
  print_record() { record DEPLOYMENT_RECORD; }
  verify_private_tls() {
    record TLS_VERIFIED
    if [[ "$SCENARIO" == already_absent && -f "$STATE/tls" ]]; then rm -f "$STATE/probe"; fi
    : > "$STATE/tls"
  }
  main
  exit 0
fi

TMP="$(mktemp -d)"
trap 'chmod -R u+rwX "$TMP"; rm -rf "$TMP"' EXIT
require_event() { grep -Fxq "$2" "$1" || test_fail "Missing event $2 in $1"; }
assert_before() {
  local first second
  first="$(grep -n -m 1 -Fx "$2" "$1" | cut -d: -f1)" || test_fail "Missing $2"
  second="$(grep -n -Fx "$3" "$1" | tail -1 | cut -d: -f1)" || test_fail "Missing $3"
  [[ "$first" -lt "$second" ]] || test_fail "$2 did not precede $3"
}
run_case() {
  local scenario="$1" expected="$2" actual=0
  EVENTS="$TMP/$scenario.events" bash "$0" --scenario "$scenario" >"$TMP/$scenario.log" 2>&1 || actual=$?
  [[ "$actual" -eq "$expected" ]] || { cat "$TMP/$scenario.log" >&2; test_fail "$scenario exited $actual, expected $expected"; }
  validate_events "$TMP/$scenario.events"
  assert_before "$TMP/$scenario.events" BACKEND_START PROBE_START
  assert_before "$TMP/$scenario.events" PROBE_START HEALTH_VERIFIED
  assert_before "$TMP/$scenario.events" HEALTH_VERIFIED FLYWAY_VERIFIED
  assert_before "$TMP/$scenario.events" FLYWAY_VERIFIED PRIVILEGES_VERIFIED
  assert_before "$TMP/$scenario.events" PRIVILEGES_VERIFIED TLS_VERIFIED
}
expect_rejected() {
  local label="$1" expected="$2" actual=0; shift 2
  "$@" >"$TMP/rejected.log" 2>&1 || actual=$?
  [[ "$actual" -ne 0 ]] || test_fail "$label was accepted"
  grep -Fq "$expected" "$TMP/rejected.log" || test_fail "$label failed for the wrong reason"
}

for scenario in success delayed already_absent; do
  run_case "$scenario" 0
  events="$TMP/$scenario.events"
  assert_before "$events" PRIVILEGES_VERIFIED PROBE_ABSENT
  assert_before "$events" PROBE_ABSENT PUBLIC_START
  assert_before "$events" PUBLIC_START CID_CLEARED
  assert_before "$events" FRONTEND_VERIFIED DEPLOYMENT_RECORD
  if [[ "$scenario" == already_absent ]]; then
    if grep -Fxq PROBE_STOP "$events"; then test_fail "Absent probe was stopped"; fi
  else
    assert_before "$events" PROBE_STOP PROBE_ABSENT
  fi
  if [[ "$scenario" == delayed ]]; then
    [[ "$(grep -Fxc PROBE_PRESENT "$events")" -eq 2 ]] || test_fail "Delayed removal was not retried"
  fi
  echo "PASS: $scenario"
done

for scenario in stop_failure removal_failure inventory_error initial_inventory_error inventory_timeout stop_timeout ambiguous truncated duplicate_inventory invalid_identity interrupt terminate; do
  expected=1
  case "$scenario" in interrupt) expected=130 ;; terminate) expected=143 ;; esac
  run_case "$scenario" "$expected"
  events="$TMP/$scenario.events"
  assert_no_public "$events"
  require_event "$events" CID_RETAINED
  assert_before "$events" CID_RETAINED APPLICATION_STOP
  [[ "$(grep -Fxc APPLICATION_STOP "$events")" -eq 2 ]] || test_fail "Containment was not attempted"
  case "$scenario" in
    stop_failure|stop_timeout) message='Could not stop private frontend probe' ;;
    removal_failure)
      message='Private frontend probe was not removed'
      [[ "$(grep -Fxc PROBE_PRESENT "$events")" -le 5 ]] || test_fail "Removal polling was unbounded"
      ;;
    inventory_error|initial_inventory_error|inventory_timeout) message='Cannot verify private frontend probe inventory' ;;
    ambiguous|truncated|duplicate_inventory) message='Private frontend probe inventory is inconsistent' ;;
    invalid_identity) message='Private frontend probe identity is not a full container ID' ;;
    *) message='Release stopped' ;;
  esac
  if [[ "$scenario" != interrupt && "$scenario" != terminate ]]; then
    grep -Fq "$message" "$TMP/$scenario.log" || test_fail "Wrong failure for $scenario"
  fi
  echo "PASS: $scenario blocks ingress and contains failure"
done

for scenario in frontend_failure duplicate_frontend missing_frontend; do
  run_case "$scenario" 1
  events="$TMP/$scenario.events"
  assert_before "$events" PROBE_ABSENT PUBLIC_START
  assert_before "$events" PUBLIC_START CID_CLEARED
  assert_before "$events" CID_CLEARED APPLICATION_STOP
  [[ "$(grep -Fxc APPLICATION_STOP "$events")" -eq 2 ]] || test_fail "Public startup failure was not contained"
  if grep -Fxq DEPLOYMENT_RECORD "$events"; then test_fail "Failed release was recorded as complete"; fi
  if [[ "$scenario" == frontend_failure ]]; then
    grep -Fq 'Frontend startup failed' "$TMP/$scenario.log" || test_fail "Wrong public startup failure"
  else
    grep -Fq 'Expected exactly one frontend container' "$TMP/$scenario.log" || test_fail "Container invariant was weakened"
  fi
  echo "PASS: $scenario is contained"
done

for scenario in stop_failure removal_failure; do
  injected="$TMP/injected-$scenario.events"
  cp "$TMP/$scenario.events" "$injected"
  printf 'PUBLIC_START\n' >> "$injected"
  expect_rejected "forbidden event in $scenario" 'Unexpected PUBLIC_START' bash "$0" --assert-no-public "$injected"
done
expect_rejected 'missing log' 'Missing, unreadable or empty event log' bash "$0" --assert-no-public "$TMP/missing.events"
printf 'PROBE_START\n' > "$TMP/unreadable.events"
chmod 000 "$TMP/unreadable.events"
if [[ -r "$TMP/unreadable.events" ]]; then
  # Root can read mode-000 files; inject the reader error deterministically.
  expect_rejected 'read error' 'Unreadable or malformed event log' bash "$0" --assert-read-error "$TMP/stop_failure.events"
else
  expect_rejected 'unreadable log' 'Missing, unreadable or empty event log' bash "$0" --assert-no-public "$TMP/unreadable.events"
fi
: > "$TMP/empty.events"
expect_rejected 'empty log' 'Missing, unreadable or empty event log' bash "$0" --assert-no-public "$TMP/empty.events"
printf 'PROBE_START\nPUBLIC_START garbage\n' > "$TMP/malformed.events"
expect_rejected 'malformed log' 'Unreadable or malformed event log' bash "$0" --assert-no-public "$TMP/malformed.events"
echo 'PASS: forbidden-event mutations and invalid evidence fail closed'
echo 'DP01_FRONTEND_REGRESSION_TESTS_PASS'
