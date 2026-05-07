FROM gradle:8.14.3-jdk17 AS base

WORKDIR /workspace
ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon --build-cache help

FROM base AS source
COPY backend/src ./src
COPY docs /docs

FROM source AS tester
RUN ./gradlew --no-daemon --build-cache test

FROM tester AS builder
RUN ./gradlew --no-daemon --build-cache bootJar -x test

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
