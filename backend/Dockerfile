# -----------------------------------------------------------------------------
# Stage 1: build
# -----------------------------------------------------------------------------
FROM docker.io/library/gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src

# Copy ONLY the build descriptors first so the dependency-resolution layer is
# cached and reused as long as build.gradle / settings.gradle don't change.
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradle ./gradle
# Copy the wrapper bash script too. It lives at the project root (not inside
# ./gradle), and is what the wrapper system expects to find. The chmod is
# redundant on Mac/Linux (the bit is preserved by COPY) but makes the
# executable intent explicit and immune to CRLF line-ending corruption if
# anyone ever builds this on Windows.
COPY --chown=gradle:gradle gradlew ./
RUN chmod +x ./gradlew
# Use ./gradlew (the wrapper) instead of the system `gradle` binary. The wrapper
# downloads whatever version gradle/wrapper/gradle-wrapper.properties says
# (currently 9.5.1) on first run, which decouples the build from the Gradle
# version that happens to be installed in the base image.
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# Now copy the source and build the executable jar.
COPY --chown=gradle:gradle src ./src
RUN ./gradlew bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: runtime
# -----------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user for defense in depth.
RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid app --no-create-home --shell /sbin/nologin app

USER app

# Copy the bootJar produced in stage 1.
COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar

EXPOSE 8080

# Exec form so signals (SIGTERM from `docker stop`, Ctrl+C) reach the JVM
# and it shuts down gracefully instead of being killed after 10s.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
