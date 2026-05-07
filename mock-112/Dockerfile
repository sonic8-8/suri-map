FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="suri-map-team"
LABEL description="mock 112 시뮬레이터 서버"

WORKDIR /app

COPY build/libs/mock-112-*.jar app.jar

EXPOSE 18112

ENTRYPOINT ["java", "-jar", "app.jar"]
