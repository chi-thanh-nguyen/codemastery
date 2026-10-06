FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build

WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:25.0.4.1_1-jre-noble AS runtime

RUN groupadd --system codemastery \
    && useradd --system --gid codemastery --no-create-home \
        --shell /usr/sbin/nologin codemastery \
    && mkdir -p /app
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 \
    /build/target/codemastery-backend.jar /app/codemastery-backend.jar

USER codemastery:codemastery
# Metadata for the current SERVER_PORT default; no host port is published.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/codemastery-backend.jar"]
