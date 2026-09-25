# Render has no built-in Java runtime, so the service is deployed as a container.

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies are their own layer: Render then reuses the download cache on every
# deploy that does not change pom.xml, which is most of them.
COPY pom.xml .
# Cache warming only: if it cannot pre-resolve everything, the package step below downloads
# the rest and is the real gate on the build succeeding.
# '!local-db' leaves out the embedded Postgres the tests use, and maven.test.skip skips compiling
# the tests that need it: the image never runs them.
RUN mvn -B -q -P '!local-db' dependency:go-offline || true
COPY src ./src
RUN mvn -B -P '!local-db' -Dmaven.test.skip=true package

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
# *.jar matches only the boot jar; Maven also leaves a pcmania-1.0.0.jar.original beside it.
COPY --from=build /build/target/*.jar /tmp/app.jar

# Faster cold starts. The free plan puts the service to sleep after 15 minutes, and the next
# visitor waits while the JVM loads and verifies thousands of classes on a tenth of a CPU.
# A class data sharing (CDS) archive lets it map them in ready-made instead - about 30% off
# the startup time. Spring Boot's recipe: unpack the jar, start the application once up to the
# point where the context is ready, and let the JVM record the classes it loaded.
#
# This training run has no database, so Flyway is off and Hibernate is told the dialect
# rather than asking the server. It runs in this stage, with the same JVM and flags as the
# ENTRYPOINT, because an archive only works with the exact JVM build that wrote it. The heap
# is pinned small because a builder with a lot of memory would otherwise size it past 32 GB,
# switch off compressed pointers and make the archive unusable at runtime. If the run fails
# the build still succeeds: the JVM then logs one line about the missing archive and starts
# the ordinary way.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/application \
 && rm /tmp/app.jar \
 && (java -Xmx512m -XX:+UseSerialGC \
        -XX:ArchiveClassesAtExit=/app/application/app.jsa \
        -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/cds-training \
        -jar /app/application/app.jar > /tmp/cds-training.log 2>&1 \
     || (echo "CDS training run failed - the service will start without the archive:" \
         && grep -v '\[cds\]' /tmp/cds-training.log | tail -40)) \
 && rm -f /tmp/cds-training.log

# Uploaded photos and the Android build are kept in the database, not on disk, because
# the container filesystem is rebuilt on every deploy. No volume is needed.
USER app

# Render injects PORT; application.yml reads it (server.port: ${PORT:8070}).
ENV PORT=8070
EXPOSE 8070

# Render's smallest instances are 512 MB. Without MaxRAMPercentage the JVM sizes its heap from
# the host's memory, not the container limit, and gets killed under load.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", \
            "-XX:SharedArchiveFile=/app/application/app.jsa", \
            "-jar", "/app/application/app.jar"]
