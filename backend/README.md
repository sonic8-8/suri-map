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

No extra environment variables are required for the default local setup.
The backend uses these auth defaults:

- Keycloak issuer at `https://k14c106.p.ssafy.io/keycloak/realms/suri-map`
- Keycloak JWK set at `https://k14c106.p.ssafy.io/keycloak/realms/suri-map/protocol/openid-connect/certs`
- TileServer GL at `http://localhost:8082`

If you want to point at a different auth or tile endpoint, override the matching environment variables before running.

Run tests:

```bash
./gradlew test
```

Health check:

```bash
curl http://localhost:8080/api/health
```

The initial development account is `dev / dev-password`.
