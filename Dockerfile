# Render has no built-in Java runtime, so the service is deployed as a container.

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies are their own layer: Render then reuses the download cache on every
# deploy that does not change pom.xml, which is most of them.
COPY pom.xml .
# Cache warming only: if it cannot pre-resolve everything, the package step below downloads
# the rest and is the real gate on the build succeeding.
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
# *.jar matches only the boot jar; Maven also leaves a pcmania-1.0.0.jar.original beside it.
COPY --from=build /build/target/*.jar app.jar

# Uploaded photos and the Android build are kept in the database, not on disk, because
# the container filesystem is rebuilt on every deploy. No volume is needed.
USER app

# Render injects PORT; application.yml reads it (server.port: ${PORT:8070}).
ENV PORT=8070
EXPOSE 8070

# Render's smallest instances are 512 MB. Without this the JVM sizes its heap from
# the host's memory, not the container limit, and gets killed under load.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
