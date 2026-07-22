# ==============================================================================
# Stage 1: Build
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy only the POM first so dependency resolution is cached in its own layer
# and isn't invalidated by source-code-only changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user/group to run the application. Alpine's busybox tools
# (addgroup/adduser), not the Debian-style groupadd/useradd.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Uploads directory must exist with the right ownership before the JAR ever
# tries to write to it. Path matches app.storage.local-path's default
# (STORAGE_LOCAL_PATH) — if you change that env var, update this too.
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# EXPOSE is documentation only — it doesn't publish the port by itself.
# Actual host<->container port publishing happens in docker-compose.yml via
# SERVER_PORT from .env. This ARG/EXPOSE pair just keeps the image's declared
# port in sync with the same variable, and defaults sanely if built standalone
# (e.g. `docker build --build-arg SERVER_PORT=9090 .`).
ARG SERVER_PORT=8080
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]