#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY="$(cd "$(dirname "$0")/.." && pwd)/deploy.sh"

if [[ "${1:-}" == "--scenario" ]]; then
  SCENARIO="$2"
  source "$DEPLOY"

  WAIT_TIMEOUT=2
  COMPOSE=(mock_compose)
  STAGED_FRONTEND_CID=""
  PROBE_PRESENT=no

  record() { printf '%s\n' "$1" >> "$EVENTS"; }
  log() { :; }
  fail() { echo "ERROR: $*" >&2; exit 1; }
  sleep() { :; }

  mock_compose() {
    [[ "${1:-}" == up ]] || return 0
    local service="${@: -1}"

    case "$service" in
      backend)
        record BACKEND_START
        ;;
      frontend)
        [[ "$PROBE_PRESENT" == no &&
           -z "$STAGED_FRONTEND_CID" ]] || {
          echo "PUBLIC_BEFORE_CLEANUP" >&2
          return 1
        }
        record PUBLIC_START
        ;;
    esac
  }

  docker() {
    if [[ "${1:-}" == stop ]]; then
      record PROBE_STOP
      [[ "$SCENARIO" != stop_failure ]] || return 1
      [[ "$SCENARIO" == removal_failure ]] ||
        PROBE_PRESENT=no
      return 0
    fi

    if [[ "${1:-}" == container &&
          "${2:-}" == inspect ]]; then
      record PROBE_INSPECT
      [[ "$PROBE_PRESENT" == yes ]]
      return
    fi

    echo "Unexpected Docker command: $1" >&2
    return 1
  }

  verify_images() { :; }
  verify_private_dependencies() { :; }
  provision_db_roles() { :; }
  assert_release_configuration() { :; }
  verify_container_images() { :; }
  verify_published_ports() { :; }
  verify_role_separation() { :; }
  harden_after_migration() { :; }
  wait_backend_health() { :; }
  psql_admin() { printf 'ok\n'; }
  print_record() { record DEPLOYMENT_RECORD; }

  stage_frontend() {
    STAGED_FRONTEND_CID=probe-123
    PROBE_PRESENT=yes
    record PROBE_START
  }

  verify_private_tls() {
    record TLS_VERIFIED
  }

  run_deploy
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "=== Test successful cleanup ==="

EVENTS="$TMP/success.events" \
  bash "$0" --scenario success >"$TMP/success.log" 2>&1

grep -Fxq PROBE_STOP "$TMP/success.events"
grep -Fxq PUBLIC_START "$TMP/success.events"
grep -Fxq DEPLOYMENT_RECORD "$TMP/success.events"

STOP_LINE="$(grep -n '^PROBE_STOP$' "$TMP/success.events" | head -1 | cut -d: -f1)"
PUBLIC_LINE="$(grep -n '^PUBLIC_START$' "$TMP/success.events" | head -1 | cut -d: -f1)"

test "$STOP_LINE" -lt "$PUBLIC_LINE"
echo "PASS: Probe removed before public ingress"

echo "=== Test Docker stop failure ==="

if EVENTS="$TMP/stop.events" \
  bash "$0" --scenario stop_failure >"$TMP/stop.log" 2>&1; then
  echo "FAIL: Deployment continued after stop failure"
  exit 1
fi

grep -Fq 'Could not stop private frontend probe' "$TMP/stop.log"
! grep -Fxq PUBLIC_START "$TMP/stop.events"
echo "PASS: Stop failure blocks public ingress"

echo "=== Test incomplete container removal ==="

if EVENTS="$TMP/removal.events" \
  bash "$0" --scenario removal_failure >"$TMP/removal.log" 2>&1; then
  echo "FAIL: Deployment continued with remaining probe"
  exit 1
fi

grep -Fq 'Private frontend probe was not removed' "$TMP/removal.log"
! grep -Fxq PUBLIC_START "$TMP/removal.events"
echo "PASS: Incomplete cleanup blocks public ingress"

echo "DP01_FRONTEND_REGRESSION_TESTS_PASS"
