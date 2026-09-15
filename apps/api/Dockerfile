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

# Heap is sized RELATIVE TO THE CONTAINER'S memory limit, not the host's RAM.
# This matters because the whole stack (API + three Next.js apps + Caddy) shares
# one droplet: with no limit and no -Xmx, the JVM claims 25% of the DROPLET as
# max heap and the frontends are left to fit in whatever remains — the first
# symptom under a crowd is an OOM-killed container, which reads like a
# performance problem but is a budgeting one. docker-compose.prod.yml sets the
# per-container limit; 70% of it leaves room for metaspace, code cache, thread
# stacks and direct buffers, which live OUTSIDE the heap.
#
# ExitOnOutOfMemoryError: die immediately instead of limping along with a wedged
# JVM — compose's `restart: unless-stopped` then gives a clean process back.
#
# Override JAVA_OPTS in the environment to retune without rebuilding.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
# `exec` so java is PID 1 and receives SIGTERM directly — Boot's graceful
# shutdown depends on it (a shell parent would swallow the signal).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
