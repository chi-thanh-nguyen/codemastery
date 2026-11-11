# Temurin 25.0.5+8 (the October 2026 OpenJDK security release), each tag pinned
# to the index it resolved to on 3 November 2026, so a rebuild cannot silently
# pick up different base content (docs/deployment.md, F11).
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build

WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN ./mvnw package -DskipTests

# Build-only verifier: no compiler, helper class or verification inventory ships.
FROM build AS truststore-verifier
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

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS runtime

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
