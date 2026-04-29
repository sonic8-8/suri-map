# Suri-Map Backend

Spring Boot API for Suri-Map.

## Stack

- Java 17
- Spring Boot 3.5.x
- PostgreSQL + PostGIS
- Flyway
- Spring Security
- Actuator + Prometheus metrics

## Local Run

Start PostgreSQL from the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

Then run the API from `backend/` with the project wrapper:

```bash
./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

Health check:

```bash
curl http://localhost:8080/api/health
```

The initial development account is `dev / dev-password`.
