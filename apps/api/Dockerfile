# Multi-stage build for the Town Basket Spring Boot modular monolith.
# Build context is the repo root (see infra/docker-compose.yml).

# ───── build ─────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/apps/api

# Maven wrapper + pom first for better layer caching.
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw mvnw
COPY apps/api/pom.xml pom.xml
RUN ./mvnw -B -ntp dependency:go-offline || true

# Sources
COPY apps/api/src src
RUN ./mvnw -B -ntp -DskipTests clean package

# ───── runtime ─────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=build /workspace/apps/api/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
