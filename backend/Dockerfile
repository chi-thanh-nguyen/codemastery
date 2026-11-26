# Use only the Apache distribution directory from the Docker Official Image.
# The checked-in tree digest below is derived independently from Apache's archive.
FROM docker.io/library/maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS maven-distribution

# Temurin 25.0.5+8 (the October 2026 OpenJDK security release), each tag pinned
# to the index it resolved to on 3 November 2026, so a rebuild cannot silently
# pick up different base content (docs/deployment.md, F11).
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS jdk

FROM jdk AS maven-bootstrap
WORKDIR /build
COPY --from=maven-distribution --chown=0:0 /usr/share/maven/ /opt/maven/
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
# BEGIN MAVEN DISTRIBUTION VERIFICATION
RUN --network=none set -eu; \
    properties=.mvn/wrapper/maven-wrapper.properties; \
    [ "$(tail -c 1 "$properties" | wc -l)" -eq 1 ] \
        || { echo 'Maven wrapper properties must end with a newline' >&2; exit 1; }; \
    expected_url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip'; \
    wrapper_url="$(awk 'tolower($0) ~ /^[[:space:]]*distributionurl([[:space:]]*[:=]|[[:space:]]|$)/ { \
        count++; \
        if (sub(/^distributionUrl=/, "")) { \
            sub(/^[[:space:]]+/, ""); sub(/[[:space:]]+$/, ""); url = $0; \
        } \
    } END { if (count == 1) print url }' "$properties")"; \
    [ "$wrapper_url" = "$expected_url" ] || { echo 'Maven distribution pin and wrapper URL differ' >&2; exit 1; }; \
    rm -rf /opt/maven/ref; \
    tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner --format=gnu \
        -C /opt/maven -cf /tmp/maven-tree.tar .; \
    echo 'a05d2e6ca8d235aaeb66d8c68f2f6a84506774355b52c14ce3431073ddad513a6adf8fb4d5a0f3f52d5bf33205fbc3855d37977766dcf7a71fe83e7e226e48a9  /tmp/maven-tree.tar' \
        | sha512sum -c -; \
    rm /tmp/maven-tree.tar; \
    /opt/maven/bin/mvn --version
# END MAVEN DISTRIBUTION VERIFICATION

FROM maven-bootstrap AS build
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY src/ src/
# Give Resolver's existing 429/503 retries more time on cold runners.
# Without Retry-After the three delays are 15, 30 and 45 seconds.
# Resolver declines a retry when the requested delay exceeds 60 seconds.
RUN /opt/maven/bin/mvn package -DskipTests \
    -Daether.connector.http.retryHandler.interval=15000 \
    -Daether.connector.http.retryHandler.intervalMax=60000

# Build-only verifier: no compiler, helper class or verification inventory ships.
FROM jdk AS truststore-verifier
COPY <<'JAVA' /truststore-verifier/VerifyTrustStore.java
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class VerifyTrustStore {
    // Exact DER SHA-256 identities of OpenJDK's four eMudhra-related roots:
    // openjdk/jdk25u, jdk-25.0.4.1+1, src/java.base/share/data/cacerts/
    // comodoaaaca, emsigneccrootcag3, emsignrootcag1, emsignrootcag2.
    private static final Set<String> PROHIBITED = Set.of(
        "d7a7a0fb5d7e2731d771e9484ebcdef71d5f0c3e0a2948782bc83ee0ea699ef4",
        "86a1ecba089c4a8d3bbe2734c612ba341d813e043cf9e8a862cd5c57a36bbe6b",
        "40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367",
        "1aa0c2709e831bd6e3b5129a00ba41f7eeef020872f1e6504bf0f6c3f24f3af3");

    private static String identity(Certificate certificate) throws Exception {
        if (!(certificate instanceof X509Certificate)) {
            throw new IllegalStateException("Non-X.509 trust entry");
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(certificate.getEncoded()));
    }

    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance(new File(args[0]), "changeit".toCharArray());
        List<String> actual = new ArrayList<>();
        for (var aliases = store.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            if (!store.isCertificateEntry(alias)) {
                throw new IllegalStateException("Non-certificate trust entry");
            }
            String sha = identity(store.getCertificate(alias));
            if (PROHIBITED.contains(sha)) {
                throw new IllegalStateException("Prohibited root certificate: " + sha);
            }
            actual.add(sha);
        }
        List<String> enumerated = new ArrayList<>();
        try (var input = Files.newInputStream(Path.of(args[1]))) {
            for (Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                enumerated.add(identity(cert));
            }
        }
        Collections.sort(actual);
        Collections.sort(enumerated);
        if (actual.isEmpty() || !actual.equals(enumerated)) {
            throw new IllegalStateException("Incomplete or invalid keytool certificate inventory");
        }
        System.out.println("Verified " + actual.size() + " X.509 trust entries; no prohibited roots");
    }
}
JAVA
RUN javac -d /truststore-verifier /truststore-verifier/VerifyTrustStore.java

# The runtime base was re-pinned on 23 November 2026 to the same tag's
# 20 November index: the same Temurin 25.0.5+8 JRE (identical cacerts file) on
# refreshed Ubuntu 24.04 packages, which fix the previous base's two HIGH APT
# findings (apt and libapt-pkg6.0t64 2.8.4ubuntu0.1) and its libc6, libpcre2
# and perl-base MEDIUM findings (docs/deployment.md, "DP01 release image
# security remediation"). The build stage does not ship and keeps its pin.
FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:23481dc33a759b79d62814ceb1ec27a0d1a84da9d7aab0609577427176fbb1b7 AS runtime

# The non-root runtime needs no setuid/setgid program, so the base's su, mount,
# passwd and similar binaries lose those bits.
# The Java trust store must hold none of the four roots OpenJDK lists for the
# compromised eMudhra intermediates (CVE-2026-91403, CVE-2026-114820;
# https://openjdk.org/emudhra). Temurin 25.0.5+8 still ships "emSign Root CA -
# G1", which OpenJDK's fix also removes; the check fails the build if any of the
# four is present (docs/deployment.md, "DP01 final security remediation", F11).
RUN groupadd --system codemastery \
    && useradd --system --gid codemastery --no-create-home \
        --shell /usr/sbin/nologin codemastery \
    && mkdir -p /app \
    && find / -xdev -type f -perm /6000 -exec chmod a-s {} + \
    && keytool -delete -cacerts -storepass changeit \
        -alias 'cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]'
# Successful keytool enumeration AND independent binary-keystore validation are
# required. No text search, pipeline negation or ignored tool status is involved.
# BEGIN TRUSTSTORE VERIFICATION
RUN --mount=from=truststore-verifier,source=/truststore-verifier,target=/truststore-verifier,ro \
    set -eu; \
    keytool -list -rfc -cacerts -storepass changeit > /tmp/cacerts.pem; \
    java -cp /truststore-verifier VerifyTrustStore "$JAVA_HOME/lib/security/cacerts" /tmp/cacerts.pem; \
    rm /tmp/cacerts.pem
# END TRUSTSTORE VERIFICATION
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 \
    /build/target/codemastery-backend.jar /app/codemastery-backend.jar

USER codemastery:codemastery
# Metadata for the current SERVER_PORT default; no host port is published.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/codemastery-backend.jar"]
