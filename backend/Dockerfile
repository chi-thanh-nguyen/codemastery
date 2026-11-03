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
RUN groupadd --system codemastery \
    && useradd --system --gid codemastery --no-create-home \
        --shell /usr/sbin/nologin codemastery \
    && mkdir -p /app \
    && find / -xdev -type f -perm /6000 -exec chmod a-s {} +
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 \
    /build/target/codemastery-backend.jar /app/codemastery-backend.jar

USER codemastery:codemastery
# Metadata for the current SERVER_PORT default; no host port is published.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/codemastery-backend.jar"]
