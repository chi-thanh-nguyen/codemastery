#!/usr/bin/env bash
# Exercise the real build-only verifier, without downloading Maven in any RUN.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
platform="${MAVEN_TEST_PLATFORM:-linux/amd64}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
properties=.mvn/wrapper/maven-wrapper.properties
mkdir -p "$scratch/.mvn/wrapper"
cp "$root/backend/$properties" "$scratch/original.properties"

check_build() {
    local name="$1" expected="$2" mutation="$3" rc=0
    if ! MAVEN_TEST_MUTATION="$mutation" awk '
        toupper($1) == "FROM" {
            in_bootstrap = tolower($NF) == "maven-bootstrap"
            if (in_bootstrap) stages++
            if (begins && !ends) invalid = 1
        }
        /^# BEGIN MAVEN DISTRIBUTION VERIFICATION$/ {
            begins++
            if (!in_bootstrap || ends) invalid = 1
            if (ENVIRON["MAVEN_TEST_MUTATION"] != "") print "RUN " ENVIRON["MAVEN_TEST_MUTATION"]
        }
        /^# END MAVEN DISTRIBUTION VERIFICATION$/ {
            if (!in_bootstrap || !begins) invalid = 1
            if (!ends) print
            ends++
        }
        !ends { print }
        END { if (invalid || stages != 1 || begins != 1 || ends != 1) exit 1 }
    ' "$root/backend/Dockerfile" > "$scratch/Dockerfile"; then
        echo 'Expected one verifier block inside the maven-bootstrap target' >&2
        return 1
    fi
    docker build --target maven-bootstrap --network none --no-cache --platform "$platform" --progress plain \
        --output type=cacheonly "$scratch" > "$scratch/build.log" 2>&1 || rc=$?
    # Plain progress echoes RUN commands and reports other build steps in the same stream.
    awk '
        /^#[0-9]+ \[maven-bootstrap [0-9]+\/[0-9]+\] RUN --network=none / { step = $1; next }
        $1 == step && $2 ~ /^[0-9]+\.[0-9]+$/ { sub(/^#[0-9]+ [0-9.]+ /, ""); print }
    ' "$scratch/build.log" > "$scratch/verifier.log"
    if [[ -z "$expected" ]]; then
        if [[ "$rc" -ne 0 ]] || ! grep -Fxq '/tmp/maven-tree.tar: OK' "$scratch/verifier.log" \
            || ! grep -Eq '^Apache Maven 3\.9\.16([[:space:]]|$)' "$scratch/verifier.log"; then
            cat "$scratch/build.log" >&2
            return 1
        fi
    elif [[ "$rc" -eq 0 ]] || ! grep -Fxq "$expected" "$scratch/verifier.log" \
        || grep -Eq '^#[0-9]+ [0-9.]+ (Apache Maven|UNVERIFIED_MAVEN_EXECUTED)' "$scratch/build.log"; then
        cat "$scratch/build.log" >&2
        return 1
    fi
    printf 'ok - %s\n' "$name"
}

cp "$scratch/original.properties" "$scratch/$properties"
check_build 'verified official distribution works offline' '' ''
awk '{ printf "%s\r\n", $0 }' "$scratch/original.properties" > "$scratch/$properties"
check_build 'CRLF wrapper configuration works offline' '' ''
sed -e '/^distributionUrl=/s/=/=  /' -e '/^distributionUrl=/s/$/  /' \
    "$scratch/original.properties" > "$scratch/$properties"
printf '# distributionUrl=https://example.invalid/ignored.zip\n' >> "$scratch/$properties"
check_build 'surrounding value whitespace and comments work offline' '' ''
cp "$scratch/original.properties" "$scratch/$properties"
check_build 'modified executable never runs' '/tmp/maven-tree.tar: FAILED' \
    "printf '#!/bin/sh\necho UNVERIFIED_MAVEN_EXECUTED\n' > /opt/maven/bin/mvn"
check_build 'missing JAR is rejected' '/tmp/maven-tree.tar: FAILED' \
    'rm /opt/maven/boot/*.jar'
check_build 'unexpected added file is rejected' '/tmp/maven-tree.tar: FAILED' \
    'touch /opt/maven/lib/unexpected.jar'
check_build 'changed executable permissions are rejected' '/tmp/maven-tree.tar: FAILED' \
    'chmod 0644 /opt/maven/bin/mvn'
sed 's/3\.9\.16/3.9.15/g' "$scratch/original.properties" > "$scratch/$properties"
check_build 'stale pin with a different requested version is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's|repo.maven.apache.org|example.invalid|' "$scratch/original.properties" > "$scratch/$properties"
check_build 'alternate distribution source is rejected' 'Maven distribution pin and wrapper URL differ' ''
cat "$scratch/original.properties" "$scratch/original.properties" > "$scratch/$properties"
check_build 'duplicate distribution URLs are rejected' 'Maven distribution pin and wrapper URL differ' ''
cp "$scratch/original.properties" "$scratch/$properties"
printf 'distributionUrl=\n' >> "$scratch/$properties"
check_build 'empty duplicate distribution URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=.*/distributionUrl=/' "$scratch/original.properties" > "$scratch/$properties"
check_build 'empty distribution URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed '/^distributionUrl=/s/^/ /' "$scratch/original.properties" > "$scratch/$properties"
check_build 'leading whitespace on the key is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=/distributionUrl =/' "$scratch/original.properties" > "$scratch/$properties"
check_build 'space before the separator is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed 's/^distributionUrl=/distributionUrl:/' "$scratch/original.properties" > "$scratch/$properties"
check_build 'unsupported property separator is rejected' 'Maven distribution pin and wrapper URL differ' ''
sed '/^distributionUrl=/s|-bin.zip|-bin .zip|' "$scratch/original.properties" > "$scratch/$properties"
check_build 'embedded whitespace in the URL is rejected' 'Maven distribution pin and wrapper URL differ' ''
awk '{ if (NR > 1) printf "\n"; printf "%s", $0 }' "$scratch/original.properties" > "$scratch/$properties"
check_build 'unterminated distribution declaration is rejected' 'Maven wrapper properties must end with a newline' ''
cp "$scratch/original.properties" "$scratch/$properties"
printf 'DistributionURL=https://example.invalid/maven.zip\n' >> "$scratch/$properties"
check_build 'case-insensitive URL key collision is rejected' 'Maven distribution pin and wrapper URL differ' ''
printf 'MAVEN_BOOTSTRAP_TESTS_PASS\n'
