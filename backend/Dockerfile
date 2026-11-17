# syntax=docker/dockerfile:1

# Build from the repository root so the backend source is the only application input copied into
# this image. Maven's reproducible wrapper remains the build authority.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace/backend

COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY backend/src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system trustlayer \
    && useradd --system --gid trustlayer --home-dir /app --shell /usr/sbin/nologin trustlayer

WORKDIR /app
COPY --from=build --chown=trustlayer:trustlayer /workspace/backend/target/trustlayer-backend-*.jar /app/app.jar

USER trustlayer:trustlayer
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
