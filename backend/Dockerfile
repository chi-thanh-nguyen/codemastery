# Temurin 25.0.5+8 (the October 2026 OpenJDK security release), each tag pinned
# to the index it resolved to on 3 November 2026, so a rebuild cannot silently
# pick up different base content (docs/deployment.md, F11).
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build

WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN ./mvnw package -DskipTests

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
        -alias 'cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]' \
    && ! keytool -list -v -cacerts -storepass changeit \
        | grep -E '^Owner: CN=(emSign Root CA - G1|emSign Root CA - G2|emSign ECC Root CA - G3|AAA Certificate Services),'
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 \
    /build/target/codemastery-backend.jar /app/codemastery-backend.jar

USER codemastery:codemastery
# Metadata for the current SERVER_PORT default; no host port is published.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/codemastery-backend.jar"]
