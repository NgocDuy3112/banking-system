FROM docker.io/library/gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src


COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradle ./gradle

COPY --chown=gradle:gradle gradlew ./
RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY --chown=gradle:gradle src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid app --no-create-home --shell /sbin/nologin app

USER app

COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]