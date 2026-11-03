#!/usr/bin/env bash
# Exercise the actual Dockerfile verification RUN with real stores and build faults.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
IMAGE="${1:?Usage: test-java-truststore.sh BACKEND_IMAGE}"
PLATFORM="${TRUSTSTORE_TEST_PLATFORM:-linux/amd64}"
WORK="$(mktemp -d)"
VERIFIER="codemastery-test/truststore-verifier:$$-${RANDOM}"
cleanup() { docker image rm "${VERIFIER}" >/dev/null 2>&1 || true; rm -rf "${WORK}"; }
trap cleanup EXIT
base="$(sed -n 's/^FROM \(eclipse-temurin:.*\) AS runtime$/\1/p' "${ROOT}/backend/Dockerfile")"
[[ "${base}" == *@sha256:* ]] || { echo 'Runtime base is not pinned' >&2; exit 1; }
docker build -q --platform "${PLATFORM}" --target truststore-verifier -t "${VERIFIER}" "${ROOT}/backend" >/dev/null
python3 - "${ROOT}/backend/Dockerfile" "${WORK}/guard" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text().split('# BEGIN TRUSTSTORE VERIFICATION\n',1)[1].split('# END TRUSTSTORE VERIFICATION',1)[0]
assert s.startswith('RUN --mount=from=truststore-verifier,')
Path(sys.argv[2]).write_text(s)
PY
# Public fixtures from the exact OpenJDK release named by the verifier; each
# certificate's DER digest is checked, so tag/network substitution fails closed.
for row in \
  comodoaaaca:d7a7a0fb5d7e2731d771e9484ebcdef71d5f0c3e0a2948782bc83ee0ea699ef4 \
  emsigneccrootcag3:86a1ecba089c4a8d3bbe2734c612ba341d813e043cf9e8a862cd5c57a36bbe6b \
  emsignrootcag1:40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367 \
  emsignrootcag2:1aa0c2709e831bd6e3b5129a00ba41f7eeef020872f1e6504bf0f6c3f24f3af3; do
  cert="${row%%:*}"; sha="${row#*:}"
  curl -fsSL "https://raw.githubusercontent.com/openjdk/jdk25u/jdk-25.0.4.1%2B1/src/java.base/share/data/cacerts/${cert}" -o "${WORK}/${cert}"
  actual="$(openssl x509 -in "${WORK}/${cert}" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr 'A-F' 'a-f')"
  [[ "${actual}" == "${sha}" ]] || { echo "Wrong public certificate fixture: ${cert}" >&2; exit 1; }
done
passed=0
check_build() {  # accept|reject name base expected-log [Dockerfile setup on stdin]
  local outcome="$1" name="$2" from="$3" expected="$4" rc=0
  { printf 'FROM %s AS truststore-verifier\nFROM %s\nUSER 0:0\n' "${VERIFIER}" "${from}"
    cat
    cat "${WORK}/guard"
  } > "${WORK}/Dockerfile"
  docker build --no-cache --platform "${PLATFORM}" --progress=plain -f "${WORK}/Dockerfile" "${WORK}" >"${WORK}/build.log" 2>&1 || rc=$?
  if { [[ "${outcome}" == accept && ${rc} -ne 0 ]] || [[ "${outcome}" == reject && ${rc} -eq 0 ]]; } \
      || ! grep -Fq "${expected}" "${WORK}/build.log"; then
    tail -40 "${WORK}/build.log" >&2
    echo "FAIL: ${name}" >&2; exit 1
  fi
  passed=$((passed + 1)); printf 'ok %d - %s\n' "${passed}" "${name}"
}
check_build reject 'published Temurin contains prohibited G1' "${base}" 'Prohibited root certificate' </dev/null
check_build accept 'final backend: complete inventory of 117 entries' "${IMAGE}" 'Verified 117 X.509' </dev/null
for cert in comodoaaaca emsigneccrootcag3 emsignrootcag1 emsignrootcag2; do
  check_build reject "introduced ${cert}" "${IMAGE}" 'Prohibited root certificate' <<EOF
COPY ${cert} /fixture.pem
RUN keytool -importcert -noprompt -cacerts -storepass changeit -alias injected-root -file /fixture.pem
EOF
done
printf '#!/bin/sh\necho INJECTED_KEYTOOL_FAILURE >&2\nexit 42\n' > "${WORK}/keytool"
chmod 755 "${WORK}/keytool"
check_build reject 'keytool enumeration error fails build' "${IMAGE}" INJECTED_KEYTOOL_FAILURE <<'EOF'
COPY keytool /fault/keytool
ENV PATH=/fault:/opt/java/openjdk/bin:/usr/bin:/bin
EOF
printf '#!/bin/sh\nprintf "unexpected certificate output\\n"\n' > "${WORK}/keytool"
check_build reject 'unexpected successful keytool output fails validation' "${IMAGE}" 'Exception' <<'EOF'
COPY keytool /fault/keytool
ENV PATH=/fault:/opt/java/openjdk/bin:/usr/bin:/bin
EOF
printf '#!/bin/sh\ncat /fixture.pem\n' > "${WORK}/keytool"
check_build reject 'incomplete successful certificate inventory fails validation' "${IMAGE}" 'Incomplete or invalid' <<'EOF'
COPY emsignrootcag1 /fixture.pem
COPY keytool /fault/keytool
ENV PATH=/fault:/opt/java/openjdk/bin:/usr/bin:/bin
EOF
printf '#!/bin/sh\necho INJECTED_VALIDATOR_FAILURE >&2\nexit 43\n' > "${WORK}/java"
chmod 755 "${WORK}/java"
check_build reject 'validator execution error fails build' "${IMAGE}" INJECTED_VALIDATOR_FAILURE <<'EOF'
COPY java /fault/java
ENV PATH=/fault:/opt/java/openjdk/bin:/usr/bin:/bin
EOF
check_build reject 'invalid binary trust store fails build' "${IMAGE}" 'exit code' <<'EOF'
RUN printf invalid > "$JAVA_HOME/lib/security/cacerts"
EOF
check_build reject 'unreadable trust store fails build' "${IMAGE}" 'exit code' <<'EOF'
RUN chmod 000 "$JAVA_HOME/lib/security/cacerts"
USER 999:999
EOF
check_build accept 'unrelated certificate with the same CN is accepted' "${IMAGE}" 'Verified 118 X.509' <<'EOF'
RUN keytool -genkeypair -alias unrelated -dname 'CN=emSign Root CA - G1,O=CodeMastery Synthetic Test' -keyalg EC -keystore /tmp/unrelated.p12 -storepass changeit -validity 1 \
    && keytool -exportcert -alias unrelated -keystore /tmp/unrelated.p12 -storepass changeit -file /tmp/unrelated.crt \
    && keytool -importcert -noprompt -cacerts -storepass changeit -alias unrelated -file /tmp/unrelated.crt
EOF
printf 'TRUSTSTORE_REGRESSIONS_PASS: %d build cases\n' "${passed}"
