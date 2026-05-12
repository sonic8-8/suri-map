FROM eclipse-temurin:17-jdk-alpine AS builder

LABEL maintainer="suri-map-team"
LABEL description="mock 112 simulation server"

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

RUN chmod +x gradlew
RUN sed -i 's/\r$//' gradlew

COPY src src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="suri-map-team"
LABEL description="mock 112 simulation server"

WORKDIR /app

COPY --from=builder /workspace/build/libs/mock-112-*.jar app.jar

EXPOSE 18112

ENTRYPOINT ["java", "-jar", "app.jar"]
