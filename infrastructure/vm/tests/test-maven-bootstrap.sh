#!/usr/bin/env bash
# Exercise the real build-only verifier, without downloading Maven in any RUN.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
platform="${MAVEN_TEST_PLATFORM:-linux/amd64}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
dockerfile="$root/backend/Dockerfile"
context="$scratch/context"
properties=.mvn/wrapper/maven-wrapper.properties
mkdir -p "$context/.mvn/wrapper"
cp "$root/backend/$properties" "$scratch/original.properties"

# Test-only SHELL for the marked verifier RUN. It runs the unchanged command
# with /bin/sh -c, as the default shell would, and records the exit status.
# Exiting 0 lets BuildKit commit the layer, so the observation survives an
# expected verifier failure; check_build then enforces the recorded status.
cat > "$scratch/run.sh" <<'SH'
observation=/maven-bootstrap-observation
[ ! -e "$observation/started" ] || exit 97
: > "$observation/started" || exit 97
status=0
/bin/sh -c "$1" || status=$?
printf '%s\n' "$status" > "$observation/status"
SH

# Copy the Dockerfile through the end of the maven-bootstrap stage, refusing any
# structure the harness cannot fully exercise. The only RUN in that stage must be
# the marked verifier. After the begin marker it inserts the observation
# wrapper and the case mutation, so they come after every distribution COPY.
extract_stage() {
    MAVEN_TEST_MUTATION="$1" awk '
        function reject(message) {
            if (!failed) printf "Unsupported maven-bootstrap verifier structure: %s (Dockerfile line %d)\n", message, NR > "/dev/stderr"
            failed = 1
        }
        BEGIN { emit = 1 }
        /MAVEN DISTRIBUTION VERIFICATION/ && $0 != "# BEGIN MAVEN DISTRIBUTION VERIFICATION" \
            && $0 != "# END MAVEN DISTRIBUTION VERIFICATION" { reject("malformed verifier marker") }
        continued {
            if ($0 ~ /MAVEN DISTRIBUTION VERIFICATION/) reject("verifier marker inside an instruction")
            else if ($0 !~ /^[[:space:]]*(#.*)?$/) {
                continued = $0 ~ /\\[[:space:]]*$/
                if (!continued && in_target && keyword == "RUN") verifier_end = NR
            }
            if (emit) print
            next
        }
        $0 == "# BEGIN MAVEN DISTRIBUTION VERIFICATION" {
            if (!in_target) reject("begin marker outside the maven-bootstrap stage")
            else if (begins || ends) reject("duplicate begin marker")
            begins++
            expect_run = 1
            print
            print "COPY maven-bootstrap-observation/ /maven-bootstrap-observation/"
            if (ENVIRON["MAVEN_TEST_MUTATION"] != "") print "RUN " ENVIRON["MAVEN_TEST_MUTATION"]
            print "SHELL [\"/bin/sh\", \"/maven-bootstrap-observation/run.sh\"]"
            next
        }
        $0 == "# END MAVEN DISTRIBUTION VERIFICATION" {
            if (!in_target || ends || !begins || runs != 1 || verifier_end != NR - 1)
                reject("end marker must directly follow the single verifier RUN")
            ends++
            print
            next
        }
        /^[[:space:]]*(#.*)?$/ {
            if (expect_run) reject("begin marker must directly precede the verifier RUN")
            if (emit) print
            next
        }
        {
            keyword = toupper($1)
            continued = $0 ~ /\\[[:space:]]*$/
            if (keyword == "FROM") {
                if (in_target) { in_target = 0; emit = 0 }
                if (NF >= 4 && toupper($(NF - 1)) == "AS" && tolower($NF) == "maven-bootstrap") {
                    stages++
                    in_target = stages == 1
                }
            } else if (in_target) {
                if (expect_run && keyword != "RUN") reject("begin marker must directly precede the verifier RUN")
                else if (ends) reject("instruction after the verifier end marker")
                else if (keyword == "RUN") {
                    if (!expect_run) reject("RUN outside the verifier block")
                    else if ($2 != "--network=none" || NF < 3 || $3 == "\\" || $3 ~ /^--/)
                        reject("verifier RUN must explicitly disable networking")
                    runs++
                    if (!continued) verifier_end = NR
                } else if (keyword != "WORKDIR" && keyword != "COPY") reject("unsupported " keyword " instruction")
                if ($0 ~ /<</) reject("heredoc instructions are not supported")
                expect_run = 0
            }
            if (emit) print
        }
        END {
            if (stages != 1) reject("expected exactly one maven-bootstrap stage")
            else if (begins != 1 || ends != 1 || runs != 1) reject("expected one marked verifier RUN")
            exit failed
        }
    ' "$dockerfile"
}

check_build() {
    local name="$1" expected="$2" mutation="$3" rc=0 status
    rm -rf "$scratch/observed" "$context/maven-bootstrap-observation"
    mkdir "$context/maven-bootstrap-observation"
    cp "$scratch/run.sh" "$context/maven-bootstrap-observation/run.sh"
    if ! extract_stage "$mutation" > "$context/Dockerfile"; then
        echo "Expected one verifier block inside the maven-bootstrap target: $name" >&2
        return 1
    fi
    printf '%s\n' 'FROM scratch AS maven-bootstrap-observation' \
        'COPY --from=maven-bootstrap /maven-bootstrap-observation/ /' >> "$context/Dockerfile"
    docker build --target maven-bootstrap-observation --network none --no-cache --platform "$platform" \
        --progress plain --output "type=local,dest=$scratch/observed" "$context" > "$scratch/build.log" 2>&1 || rc=$?
    if [[ "$rc" -ne 0 ]]; then
        cat "$scratch/build.log" >&2
        echo "Docker build failed before the verifier recorded its exit status: $name" >&2
        return 1
    fi
    if [[ ! -f "$scratch/observed/started" || ! -f "$scratch/observed/status" ]] \
        || ! status="$(cat "$scratch/observed/status")" || [[ ! "$status" =~ ^[0-9]+$ ]]; then
        cat "$scratch/build.log" >&2
        echo "Verifier observation evidence is missing or malformed: $name" >&2
        return 1
    fi
    # The sentinel launcher marks the committed layer; its output is irrelevant.
    if [[ -e "$scratch/observed/executed" ]]; then
        cat "$scratch/build.log" >&2
        echo "Unverified Maven launcher executed before verification: $name" >&2
        return 1
    fi
    # Plain progress echoes RUN commands and reports other build steps in the same
    # stream. Only the output lines of the final maven-bootstrap step, which the
    # extraction guarantees is the marked verifier RUN, are evidence.
    if ! awk '
        match($0, /^#[0-9]+ \[maven-bootstrap [0-9]+\/[0-9]+\] /) {
            split(substr($0, 1, RLENGTH - 2), part, " ")
            split(part[3], position, "/")
            if (position[1] == position[2]) {
                if (substr($0, RLENGTH + 1, 4) != "RUN ") invalid = 1
                if (!(part[1] in seen)) { seen[part[1]] = 1; steps++ }
                step = part[1]
            }
            next
        }
        step != "" && $1 == step && $2 ~ /^[0-9]+\.[0-9]+$/ { sub(/^#[0-9]+ [0-9.]+ /, ""); print }
        END { if (steps != 1 || invalid) exit 1 }
    ' "$scratch/build.log" > "$scratch/verifier.log"; then
        cat "$scratch/build.log" >&2
        echo "Verifier output is not attributable to exactly one final maven-bootstrap RUN: $name" >&2
        return 1
    fi
    if [[ -z "$expected" ]]; then
        if [[ "$status" -ne 0 ]] || ! grep -Fxq '/tmp/maven-tree.tar: OK' "$scratch/verifier.log" \
            || ! grep -Eq '^Apache Maven 3\.9\.16([[:space:]]|$)' "$scratch/verifier.log"; then
            cat "$scratch/build.log" >&2
            echo "Verifier exited with status $status without accepting the distribution: $name" >&2
            return 1
        fi
    elif [[ "$status" -ne 1 ]]; then
        cat "$scratch/build.log" >&2
        echo "Verifier exited with status $status; expected 1: $name" >&2
        return 1
    elif ! grep -Fxq "$expected" "$scratch/verifier.log" \
        || grep -Eq '^#[0-9]+ [0-9.]+ (Apache Maven|UNVERIFIED_MAVEN_EXECUTED)' "$scratch/build.log"; then
        cat "$scratch/build.log" >&2
        echo "Verifier failed without the expected diagnostic, or Maven output appeared: $name" >&2
        return 1
    fi
    printf 'ok - %s\n' "$name"
}

sentinel="printf '#!/bin/sh\n: > /maven-bootstrap-observation/executed\necho UNVERIFIED_MAVEN_EXECUTED\n' > /opt/maven/bin/mvn"
cp "$scratch/original.properties" "$context/$properties"
check_build 'verified official distribution works offline' '' ''
awk '{ printf "%s\r\n", $0 }' "$scratch/original.properties" > "$context/$properties"
check_build 'CRLF wrapper configuration works offline' '' ''
sed -e '/^distributionUrl=/s/=/=  /' -e '/^distributionUrl=/s/$/  /' \
    "$scratch/original.properties" > "$context/$properties"
printf '# distributionUrl=https://example.invalid/ignored.zip\n' >> "$context/$properties"
check_build 'surrounding value whitespace and comments work offline' '' ''
cp "$scratch/original.properties" "$context/$properties"
check_build 'modified executable never runs' '/tmp/maven-tree.tar: FAILED' "$sentinel"
check_build 'missing JAR is rejected' '/tmp/maven-tree.tar: FAILED' \
    'rm /opt/maven/boot/*.jar'
check_build 'unexpected added file is rejected' '/tmp/maven-tree.tar: FAILED' \
    'touch /opt/maven/lib/unexpected.jar'
check_build 'changed executable permissions are rejected' '/tmp/maven-tree.tar: FAILED' \
    'chmod 0644 /opt/maven/bin/mvn'
sed 's/3\.9\.16/3.9.15/g' "$scratch/original.properties" > "$context/$properties"
check_build 'stale pin with a different requested version is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's|repo.maven.apache.org|example.invalid|' "$scratch/original.properties" > "$context/$properties"
check_build 'alternate distribution source is rejected' 'Maven distribution pin and wrapper URL differ' ''
cat "$scratch/original.properties" "$scratch/original.properties" > "$context/$properties"
check_build 'duplicate distribution URLs are rejected' 'Maven distribution pin and wrapper URL differ' ''
cp "$scratch/original.properties" "$context/$properties"
printf 'distributionUrl=\n' >> "$context/$properties"
check_build 'empty duplicate distribution URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=.*/distributionUrl=/' "$scratch/original.properties" > "$context/$properties"
check_build 'empty distribution URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed '/^distributionUrl=/s/^/ /' "$scratch/original.properties" > "$context/$properties"
check_build 'leading whitespace on the key is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=/distributionUrl =/' "$scratch/original.properties" > "$context/$properties"
check_build 'space before the separator is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=/distributionUrl:/' "$scratch/original.properties" > "$context/$properties"
check_build 'unsupported property separator is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed '/^distributionUrl=/s|-bin.zip|-bin .zip|' "$scratch/original.properties" > "$context/$properties"
check_build 'embedded whitespace in the URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
awk '{ if (NR > 1) printf "\n"; printf "%s", $0 }' "$scratch/original.properties" > "$context/$properties"
check_build 'unterminated distribution declaration is rejected' 'Maven wrapper properties must end with a newline' ''
cp "$scratch/original.properties" "$context/$properties"
printf 'DistributionURL=https://example.invalid/maven.zip\n' >> "$context/$properties"
check_build 'case-insensitive URL key collision is rejected' 'Maven distribution pin and wrapper URL differ' ''

# Harness controls: Dockerfile regressions the cases above must not accept.
# Each runs one case against a disposable Dockerfile copy and requires that
# case to fail for the stated reason.
expect_rejection() {
    local name="$1" reason="$2" expected="$3" mutation="$4" transform="$5"
    awk "$transform" "$root/backend/Dockerfile" > "$scratch/control.Dockerfile"
    if [[ "$transform" != '{ print }' ]] && cmp -s "$root/backend/Dockerfile" "$scratch/control.Dockerfile"; then
        echo "Harness control did not change the Dockerfile: $name" >&2
        exit 1
    fi
    if (dockerfile="$scratch/control.Dockerfile"; check_build "$name" "$expected" "$mutation") \
        > "$scratch/control.log" 2>&1; then
        cat "$scratch/control.log" >&2
        echo "Harness control was accepted: $name" >&2
        exit 1
    fi
    if ! grep -Fq -- "$reason" "$scratch/control.log"; then
        cat "$scratch/control.log" >&2
        echo "Harness control failed for another reason: $name" >&2
        exit 1
    fi
    printf 'ok - harness rejects %s\n' "$name"
}

cp "$scratch/original.properties" "$context/$properties"
expect_rejection 'a silent launcher run before the digest check' \
    'Unverified Maven launcher executed before verification' '/tmp/maven-tree.tar: FAILED' "$sentinel" '
        /^    rm -rf \/opt\/maven\/ref; \\$/ { print "    /opt/maven/bin/mvn --version >/dev/null 2>&1 || true; \\" }
        { print }'
expect_rejection 'a masked verifier exit status' 'Verifier exited with status 0; expected 1' \
    '/tmp/maven-tree.tar: FAILED' 'touch /opt/maven/lib/unexpected.jar' '
        { sub(/\| sha512sum -c -; \\$/, "| sha512sum -c - || true; \\"); print }'
expect_rejection 'the expected diagnostic from a failed setup step' \
    'Docker build failed before the verifier recorded its exit status' '/tmp/maven-tree.tar: FAILED' \
    "echo '/tmp/maven-tree.tar: FAILED' >&2; exit 1" '{ print }'
expect_rejection 'a second offline RUN supplying the Maven version' 'RUN outside the verifier block' '' '' '
        /^    rm \/tmp\/maven-tree.tar; \\$/ { print "    rm /tmp/maven-tree.tar"; next }
        /^    \/opt\/maven\/bin\/mvn --version$/ { print "RUN --network=none echo '"'"'Apache Maven 3.9.16'"'"'"; next }
        { print }'
expect_rejection 'an offline RUN before the verifier block' 'RUN outside the verifier block' '' '' '
        /^# BEGIN MAVEN DISTRIBUTION VERIFICATION$/ { print "RUN --network=none true" }
        { print }'
expect_rejection 'an instruction after the end marker' 'instruction after the verifier end marker' '' '' '
        { print }
        /^# END MAVEN DISTRIBUTION VERIFICATION$/ { print "RUN --network=none printf '"'"'#!/bin/sh\\n'"'"' > /opt/maven/bin/mvn" }'
expect_rejection 'a missing begin marker' 'RUN outside the verifier block' '' '' '
        !/^# BEGIN MAVEN DISTRIBUTION VERIFICATION$/'
expect_rejection 'a duplicate end marker' 'end marker must directly follow the single verifier RUN' '' '' '
        { print } /^# END MAVEN DISTRIBUTION VERIFICATION$/ { print }'
expect_rejection 'an end marker inside the verifier RUN' 'verifier marker inside an instruction' '' '' '
        /^    rm \/tmp\/maven-tree.tar; \\$/ { print "# END MAVEN DISTRIBUTION VERIFICATION" }
        { print }'
expect_rejection 'markers outside the maven-bootstrap target' 'begin marker outside the maven-bootstrap stage' '' '' '
        { sub(/^FROM jdk AS maven-bootstrap$/, "FROM jdk AS maven-prepare"); print }'
expect_rejection 'a second maven-bootstrap stage' 'expected exactly one maven-bootstrap stage' '' '' '
        { print } END { print "FROM jdk AS maven-bootstrap" }'
expect_rejection 'a verifier RUN without an explicit network mode' \
    'verifier RUN must explicitly disable networking' '' '' '
        { sub(/^RUN --network=none /, "RUN "); print }'
expect_rejection 'a verifier RUN overriding its network mode' \
    'verifier RUN must explicitly disable networking' '' '' '
        { sub(/^RUN --network=none /, "RUN --network=none --network=default "); print }'
printf 'MAVEN_BOOTSTRAP_TESTS_PASS\n'
