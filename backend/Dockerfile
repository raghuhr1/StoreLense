# ─── StoreLense Backend — Generic multi-service Dockerfile ───────────────────
# Usage (docker-compose passes these as build args):
#   SERVICE_NAME  e.g. auth-service
#   SERVICE_PORT  e.g. 8081
#
# The build context must be the backend/ directory.
# ─────────────────────────────────────────────────────────────────────────────

ARG SERVICE_NAME=auth-service
ARG SERVICE_PORT=8081

# ── Stage 1: dependency cache ─────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS deps
WORKDIR /workspace

# Copy root POM + all module POMs so Maven can resolve the full reactor offline.
COPY pom.xml .
COPY common/pom.xml                  common/
COPY auth-service/pom.xml            auth-service/
COPY store-service/pom.xml           store-service/
COPY product-service/pom.xml         product-service/
COPY inventory-service/pom.xml       inventory-service/
COPY soh-service/pom.xml             soh-service/
COPY refill-service/pom.xml          refill-service/
COPY rfid-ingest-service/pom.xml     rfid-ingest-service/
COPY rfid-processing-service/pom.xml rfid-processing-service/
COPY reporting-service/pom.xml       reporting-service/
COPY erp-integration-service/pom.xml erp-integration-service/
COPY notification-service/pom.xml    notification-service/

ARG SERVICE_NAME
RUN mvn dependency:go-offline -pl "common,${SERVICE_NAME}" -am -q

# ── Stage 2: compile & package ────────────────────────────────────────────────
FROM deps AS build

ARG SERVICE_NAME
COPY common/src common/src
COPY ${SERVICE_NAME}/src ${SERVICE_NAME}/src
RUN mvn package -pl "common,${SERVICE_NAME}" -am -DskipTests -q

# ── Stage 3: minimal JRE runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

ARG SERVICE_NAME
COPY --from=build /workspace/${SERVICE_NAME}/target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

ARG SERVICE_PORT=8081
EXPOSE ${SERVICE_PORT}

# Container-aware JVM flags: honour cgroup memory limits
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
