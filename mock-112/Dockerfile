FROM gradle:8.14.3-jdk17 AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon --build-cache help

COPY src ./src

RUN ./gradlew --no-daemon --build-cache bootJar

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="suri-map-team"
LABEL description="mock 112 시뮬레이터 서버"

WORKDIR /app

COPY --from=builder /workspace/build/libs/mock-112-*.jar app.jar

EXPOSE 18112

ENTRYPOINT ["java", "-jar", "app.jar"]
