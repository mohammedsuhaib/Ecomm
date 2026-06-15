# Multi-stage build for the Town Basket Spring Boot modular monolith.
# Build context is the repo root (see infra/docker-compose.yml).

# ───── build ─────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle wrapper + build scripts first for better layer caching.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY apps/api/build.gradle apps/api/build.gradle
RUN ./gradlew :api:dependencies --no-daemon || true

# Sources
COPY apps/api/src apps/api/src
RUN ./gradlew :api:bootJar --no-daemon -x test

# ───── runtime ─────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=build /workspace/apps/api/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
