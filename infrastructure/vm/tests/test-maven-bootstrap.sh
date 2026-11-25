#!/usr/bin/env bash
# Exercise the real build-only verifier, without downloading Maven in any RUN.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
platform="${MAVEN_TEST_PLATFORM:-linux/amd64}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
properties=.mvn/wrapper/maven-wrapper.properties
mkdir -p "$scratch/.mvn/wrapper"
sed '/^# END MAVEN DISTRIBUTION VERIFICATION/q' "$root/backend/Dockerfile" > "$scratch/Mavenfile"
cp "$root/backend/$properties" "$scratch/original.properties"

check_build() {
    local name="$1" expected="$2" mutation="$3" rc=0
    MAVEN_TEST_MUTATION="$mutation" awk '
        /^# BEGIN MAVEN DISTRIBUTION VERIFICATION$/ && ENVIRON["MAVEN_TEST_MUTATION"] != "" {
            print "RUN " ENVIRON["MAVEN_TEST_MUTATION"]
        }
        { print }
    ' "$scratch/Mavenfile" > "$scratch/Dockerfile"
    docker build --network none --no-cache --platform "$platform" --progress plain \
        --output type=cacheonly "$scratch" > "$scratch/build.log" 2>&1 || rc=$?
    if [[ "$expected" == 'Apache Maven 3.9.16' ]]; then
        [[ "$rc" -eq 0 ]] && grep -q 'Apache Maven 3.9.16' "$scratch/build.log" || {
            cat "$scratch/build.log" >&2; return 1;
        }
    elif [[ "$rc" -eq 0 ]] || ! grep -Eq "^#[0-9]+ [0-9.]+ .*${expected}" "$scratch/build.log" \
        || grep -Eq '^#[0-9]+ [0-9.]+ (Apache Maven|UNVERIFIED_MAVEN_EXECUTED)' "$scratch/build.log"; then
        cat "$scratch/build.log" >&2; return 1
    fi
    printf 'ok - %s\n' "$name"
}

cp "$scratch/original.properties" "$scratch/$properties"
check_build 'verified official distribution works offline' 'Apache Maven 3.9.16' ''
awk '{ printf "%s\r\n", $0 }' "$scratch/original.properties" > "$scratch/$properties"
check_build 'CRLF wrapper configuration works offline' 'Apache Maven 3.9.16' ''
cp "$scratch/original.properties" "$scratch/$properties"
check_build 'modified executable never runs' 'maven-tree.tar: FAILED' \
    "printf '#!/bin/sh\necho UNVERIFIED_MAVEN_EXECUTED\n' > /opt/maven/bin/mvn"
check_build 'missing JAR is rejected' 'maven-tree.tar: FAILED' \
    'rm /opt/maven/boot/*.jar'
check_build 'unexpected added file is rejected' 'maven-tree.tar: FAILED' \
    'touch /opt/maven/lib/unexpected.jar'
check_build 'changed executable permissions are rejected' 'maven-tree.tar: FAILED' \
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
printf 'MAVEN_BOOTSTRAP_TESTS_PASS\n'
