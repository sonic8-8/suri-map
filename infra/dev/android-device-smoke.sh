#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

BACKEND_PORT="${BACKEND_PORT:-8081}"
APP_PORT="${APP_PORT:-18080}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
MOCK_112_PORT="${MOCK_112_PORT:-18112}"
TILESERVER_PORT="${TILESERVER_PORT:-8082}"
BACKEND_MODE="${BACKEND_MODE:-docker}"
BACKEND_IMAGE="${BACKEND_IMAGE:-suri-map-backend:develop}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-surimap-local-backend}"
TILESERVER_IMAGE="${TILESERVER_IMAGE:-maptiler/tileserver-gl:latest}"
TILESERVER_CONTAINER="${TILESERVER_CONTAINER:-surimap-local-tileserver-gl}"
TILESERVER_RUNTIME_DIR="${TILESERVER_RUNTIME_DIR:-$ROOT_DIR/.agents/scratch/gwangju-tiles-runtime}"

ADB="${ADB:-}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
SKIP_ADB=0
SKIP_TILES=0
SKIP_BACKEND=0
GENERATE_TILES=0
TILE_SOURCE="${TILE_SOURCE:-}"
SKIP_SEED=0
ENSURE_OVERALL=1
RESET_APP=0
BUILD_APK=0
INSTALL_APK=0
LAUNCH_APP=1
STOP_TILESERVER_ON_EXIT=0
REBUILD_BACKEND_IMAGE=0

BACKEND_PID=""
BACKEND_CONTAINER_STARTED=0
BACKEND_LOG="$ROOT_DIR/.agents/scratch/android-device-smoke-backend.log"
BACKEND_IMAGE_DOCKERFILE="$ROOT_DIR/.agents/scratch/android-device-smoke-backend.Dockerfile"
BACKEND_IMAGE_JAR="$ROOT_DIR/.agents/scratch/android-device-smoke-backend.jar"
CLEANED_UP=0
DEV_DOCKER_NETWORK=""

INCIDENT_ID="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
SOURCE_INCIDENT_ID="00000000-0000-0000-0000-000000000001"
OP_ID="88888888-8888-8888-8888-888888880001"

usage() {
  cat <<USAGE
Usage: bash infra/dev/android-device-smoke.sh [options]

Starts the local services used for Android real-device smoke testing:
  - docker postgres and mock-112
  - TileServer GL on 127.0.0.1:${TILESERVER_PORT}
  - backend Docker container on 127.0.0.1:${BACKEND_PORT}
  - adb reverse tcp:${APP_PORT} tcp:${BACKEND_PORT}
  - optional debug APK build/install/reset/launch

Options:
  --tile-source PATH       Generate Gwangju MBTiles from the source topo directory.
  --tiles-dir PATH         Runtime tileserver data directory. Default: .agents/scratch/gwangju-tiles-runtime
                           If missing, the latest .agents/scratch/gwangju-tiles-runtime-* is reused.
  --skip-tiles             Do not start TileServer GL.
  --backend-mode MODE      Backend mode: docker, gradle, or external. Default: ${BACKEND_MODE}
  --backend-image IMAGE    Docker backend image. Default: ${BACKEND_IMAGE}
  --rebuild-backend-image  Rebuild the Docker backend image before starting it.
  --skip-backend           Use an already running backend on --backend-port. Same as --backend-mode external.
  --skip-adb               Do not configure adb or launch the app.
  --device SERIAL          Use a specific adb device serial.
  --backend-port PORT      Backend host port. Default: ${BACKEND_PORT}
  --app-port PORT          Port compiled into the installed Android app. Default: ${APP_PORT}
  --tiles-port PORT        TileServer host port. Default: ${TILESERVER_PORT}
  --no-seed                Skip mock-112 seed and incident import.
  --no-overall             Skip ensuring ACTIVE OVERALL search_area.
  --reset-app              Clear com.surimap app data before launch.
  --build-apk              Build debug APK with suriMapDebugApiBaseUrl for this backend.
  --install-apk            Install app-debug.apk before launch. Implies --build-apk.
  --no-launch              Do not launch com.surimap.
  --stop-tiles-on-exit     Stop the script-owned tileserver container on exit.
  -h, --help               Show this help.

Examples:
  bash infra/dev/android-device-smoke.sh
  bash infra/dev/android-device-smoke.sh --rebuild-backend-image
  bash infra/dev/android-device-smoke.sh --backend-mode gradle --backend-port 8080 --app-port 18080
  bash infra/dev/android-device-smoke.sh --backend-mode external --backend-port 8080 --app-port 18080
  bash infra/dev/android-device-smoke.sh --tile-source "/path/to/광주광역시_연속수치지형도"
  bash infra/dev/android-device-smoke.sh --device R3CT50BD92Y --reset-app --install-apk

Press Ctrl-C to stop script-owned backend work and remove the adb reverse rule.
USAGE
}

log() {
  printf '[android-smoke] %s\n' "$*"
}

fail() {
  printf '[android-smoke] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

wait_http() {
  local url="$1"
  local label="$2"
  local max_attempts="${3:-60}"

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$label is ready"
      return 0
    fi
    sleep 1
  done

  return 1
}

wait_container_healthy() {
  local container="$1"
  local label="$2"
  local max_attempts="${3:-60}"

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      log "$label is $status"
      return 0
    fi
    sleep 1
  done

  docker logs "$container" --tail 80 >&2 || true
  fail "$label did not become healthy"
}

container_publishing_port() {
  local port="$1"
  docker ps --filter "publish=$port" --format '{{.Names}}' | sed -n '1p'
}

container_first_network() {
  local container="$1"
  docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
    "$container" 2>/dev/null | sed -n '1p'
}

dev_docker_network() {
  if [[ -n "$DEV_DOCKER_NETWORK" ]]; then
    printf '%s' "$DEV_DOCKER_NETWORK"
    return 0
  fi

  DEV_DOCKER_NETWORK="$(container_first_network surimap-postgres)"
  [[ -n "$DEV_DOCKER_NETWORK" ]] || fail "could not resolve docker network from surimap-postgres"
  printf '%s' "$DEV_DOCKER_NETWORK"
}

connect_container_to_network_alias() {
  local container="$1"
  local network="$2"
  local alias="$3"

  [[ -n "$container" ]] || return 1
  [[ -n "$network" ]] || return 1
  docker network connect --alias "$alias" "$network" "$container" >/dev/null 2>&1 || true
}

json_field() {
  local field="$1"
  python3 -c '
import json
import sys

field = sys.argv[1]
data = json.load(sys.stdin)
value = data
for part in field.split("."):
    value = value[part]
print(value)
' "$field"
}

curl_status() {
  local output_file="$1"
  shift
  curl -sS -o "$output_file" -w '%{http_code}' "$@"
}

cleanup() {
  local status=$?
  if [[ "$CLEANED_UP" -eq 1 ]]; then
    exit "$status"
  fi
  CLEANED_UP=1
  trap - EXIT INT TERM

  if [[ -n "$BACKEND_PID" ]]; then
    log "stopping backend"
    kill -- "-$BACKEND_PID" >/dev/null 2>&1 || kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
  fi

  if [[ "$BACKEND_CONTAINER_STARTED" -eq 1 ]]; then
    log "stopping backend container"
    docker rm -f "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
  fi

  if [[ "$SKIP_ADB" -eq 0 && -n "${ADB:-}" && -n "${DEVICE_SERIAL:-}" ]]; then
    "$ADB" -s "$DEVICE_SERIAL" reverse --remove "tcp:$APP_PORT" >/dev/null 2>&1 || true
  fi

  if [[ "$STOP_TILESERVER_ON_EXIT" -eq 1 ]]; then
    docker rm -f "$TILESERVER_CONTAINER" >/dev/null 2>&1 || true
  fi

  exit "$status"
}
trap cleanup EXIT INT TERM

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --tile-source)
        TILE_SOURCE="${2:-}"
        [[ -n "$TILE_SOURCE" ]] || fail "--tile-source requires a path"
        GENERATE_TILES=1
        shift 2
        ;;
      --tiles-dir)
        TILESERVER_RUNTIME_DIR="${2:-}"
        [[ -n "$TILESERVER_RUNTIME_DIR" ]] || fail "--tiles-dir requires a path"
        shift 2
        ;;
      --skip-tiles)
        SKIP_TILES=1
        shift
        ;;
      --skip-backend)
        SKIP_BACKEND=1
        BACKEND_MODE="external"
        shift
        ;;
      --backend-mode)
        BACKEND_MODE="${2:-}"
        case "$BACKEND_MODE" in
          docker|gradle|external) ;;
          *) fail "--backend-mode must be docker, gradle, or external" ;;
        esac
        [[ "$BACKEND_MODE" == "external" ]] && SKIP_BACKEND=1
        shift 2
        ;;
      --backend-image)
        BACKEND_IMAGE="${2:-}"
        [[ -n "$BACKEND_IMAGE" ]] || fail "--backend-image requires an image"
        shift 2
        ;;
      --rebuild-backend-image)
        REBUILD_BACKEND_IMAGE=1
        shift
        ;;
      --skip-adb)
        SKIP_ADB=1
        shift
        ;;
      --device)
        DEVICE_SERIAL="${2:-}"
        [[ -n "$DEVICE_SERIAL" ]] || fail "--device requires a serial"
        shift 2
        ;;
      --backend-port)
        BACKEND_PORT="${2:-}"
        [[ -n "$BACKEND_PORT" ]] || fail "--backend-port requires a port"
        shift 2
        ;;
      --app-port)
        APP_PORT="${2:-}"
        [[ -n "$APP_PORT" ]] || fail "--app-port requires a port"
        shift 2
        ;;
      --tiles-port)
        TILESERVER_PORT="${2:-}"
        [[ -n "$TILESERVER_PORT" ]] || fail "--tiles-port requires a port"
        shift 2
        ;;
      --no-seed)
        SKIP_SEED=1
        shift
        ;;
      --no-overall)
        ENSURE_OVERALL=0
        shift
        ;;
      --reset-app)
        RESET_APP=1
        shift
        ;;
      --build-apk)
        BUILD_APK=1
        shift
        ;;
      --install-apk)
        BUILD_APK=1
        INSTALL_APK=1
        shift
        ;;
      --no-launch)
        LAUNCH_APP=0
        shift
        ;;
      --stop-tiles-on-exit)
        STOP_TILESERVER_ON_EXIT=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "unknown option: $1"
        ;;
    esac
  done
}

select_default_tiles_dir() {
  [[ "$SKIP_TILES" -eq 0 ]] || return 0
  [[ "$GENERATE_TILES" -eq 0 ]] || return 0
  [[ -f "$TILESERVER_RUNTIME_DIR/data/osm-local.mbtiles" ]] && return 0

  local latest_runtime
  latest_runtime="$(
    find "$ROOT_DIR/.agents/scratch" \
      -maxdepth 1 \
      -type d \
      -name 'gwangju-tiles-runtime-*' \
      2>/dev/null | sort | tail -n 1
  )"

  if [[ -n "$latest_runtime" && -f "$latest_runtime/data/osm-local.mbtiles" ]]; then
    TILESERVER_RUNTIME_DIR="$latest_runtime"
    log "using existing tiles dir: $TILESERVER_RUNTIME_DIR"
  fi
}

prepare_tileserver_data() {
  mkdir -p "$TILESERVER_RUNTIME_DIR/data" "$TILESERVER_RUNTIME_DIR/styles/osm-local"
  cp "$ROOT_DIR/infra/docker/tileserver/config.json" "$TILESERVER_RUNTIME_DIR/config.json"
  cp "$ROOT_DIR/infra/docker/tileserver/styles/osm-local/style.json" \
    "$TILESERVER_RUNTIME_DIR/styles/osm-local/style.json"

  if [[ "$GENERATE_TILES" -eq 1 ]]; then
    [[ -d "$TILE_SOURCE" ]] || fail "tile source directory does not exist: $TILE_SOURCE"
    log "generating osm-local.mbtiles"
    bash "$ROOT_DIR/infra/docker/tileserver/scripts/build-gwangju-osm-local.sh" \
      "$TILE_SOURCE" \
      "$TILESERVER_RUNTIME_DIR/data"
    log "generating gwangju-building-labels.mbtiles"
    bash "$ROOT_DIR/infra/docker/tileserver/scripts/build-gwangju-building-labels.sh" \
      "$TILE_SOURCE" \
      "$TILESERVER_RUNTIME_DIR/data"
  fi

  [[ -f "$TILESERVER_RUNTIME_DIR/data/osm-local.mbtiles" ]] ||
    fail "missing $TILESERVER_RUNTIME_DIR/data/osm-local.mbtiles. Pass --tile-source or --tiles-dir."
  [[ -f "$TILESERVER_RUNTIME_DIR/data/gwangju-building-labels.mbtiles" ]] ||
    fail "missing $TILESERVER_RUNTIME_DIR/data/gwangju-building-labels.mbtiles. Pass --tile-source or --tiles-dir."

  if [[ ! -f "$TILESERVER_RUNTIME_DIR/fonts/Pretendard GOV/0-255.pbf" ]]; then
    log "warning: missing Pretendard GOV glyph PBF files; Korean map labels may not render"
  fi
}

start_tileserver() {
  [[ "$SKIP_TILES" -eq 0 ]] || return 0
  require_cmd docker
  prepare_tileserver_data

  if wait_http "http://127.0.0.1:$TILESERVER_PORT/styles/osm-local/style.json" "existing tileserver" 2; then
    if [[ "$BACKEND_MODE" == "docker" ]]; then
      local existing_container
      existing_container="$(container_publishing_port "$TILESERVER_PORT")"
      [[ -n "$existing_container" ]] ||
        fail "tileserver is reachable on $TILESERVER_PORT, but no docker container publishes that port"
      connect_container_to_network_alias "$existing_container" "$(dev_docker_network)" "tileserver-gl"
      log "connected existing tileserver container $existing_container to docker backend network"
    fi
    return 0
  fi

  docker rm -f "$TILESERVER_CONTAINER" >/dev/null 2>&1 || true
  log "starting TileServer GL on 127.0.0.1:$TILESERVER_PORT"
  local network_args=()
  if [[ "$BACKEND_MODE" == "docker" ]]; then
    network_args=(--network "$(dev_docker_network)" --network-alias tileserver-gl)
  fi
  docker run -d \
    --name "$TILESERVER_CONTAINER" \
    "${network_args[@]}" \
    -p "127.0.0.1:$TILESERVER_PORT:8080" \
    -v "$TILESERVER_RUNTIME_DIR:/data:ro" \
    "$TILESERVER_IMAGE" \
    --config /data/config.json >/dev/null

  wait_http "http://127.0.0.1:$TILESERVER_PORT/styles/osm-local/style.json" "tileserver" 60 ||
    fail "tileserver did not become ready"
}

start_infra() {
  require_cmd docker
  log "starting postgres"
  docker compose -f "$ROOT_DIR/infra/docker-compose.yml" up -d postgres
  wait_container_healthy surimap-postgres postgres 60

  if [[ "$BACKEND_MODE" == "docker" ]]; then
    if wait_http "http://127.0.0.1:$MOCK_112_PORT/mock-112/health" "existing mock-112 HTTP" 2; then
      local existing_mock
      existing_mock="$(container_publishing_port "$MOCK_112_PORT")"
      [[ -n "$existing_mock" ]] ||
        fail "mock-112 is reachable on $MOCK_112_PORT, but no docker container publishes that port"
      connect_container_to_network_alias "$existing_mock" "$(dev_docker_network)" "mock-112"
      log "connected existing mock-112 container $existing_mock to docker backend network"
      return 0
    fi

    log "starting mock-112"
    docker compose -f "$ROOT_DIR/infra/docker-compose.yml" up -d mock-112
    wait_container_healthy mock-112 mock-112 60
    wait_http "http://127.0.0.1:$MOCK_112_PORT/mock-112/health" "mock-112 HTTP" 30 ||
      fail "mock-112 HTTP health check failed"
    return 0
  fi

  if wait_http "http://127.0.0.1:$MOCK_112_PORT/mock-112/health" "existing mock-112 HTTP" 2; then
    return 0
  fi

  log "starting mock-112"
  docker rm -f mock-112 >/dev/null 2>&1 || true
  docker compose -f "$ROOT_DIR/infra/docker-compose.yml" up -d mock-112
  wait_container_healthy mock-112 mock-112 60
  wait_http "http://127.0.0.1:$MOCK_112_PORT/mock-112/health" "mock-112 HTTP" 30 ||
    fail "mock-112 HTTP health check failed"
}

ensure_backend_image() {
  require_cmd docker
  if [[ "$REBUILD_BACKEND_IMAGE" -eq 1 ]] ||
    ! docker image inspect "$BACKEND_IMAGE" >/dev/null 2>&1; then
    require_cmd java
    mkdir -p "$ROOT_DIR/.agents/scratch"
    log "building backend bootJar for $BACKEND_IMAGE"
    (
      cd "$ROOT_DIR/backend"
      ./gradlew --no-daemon bootJar -x test
    )

    local jar
    jar="$(
      find "$ROOT_DIR/backend/build/libs" \
        -maxdepth 1 \
        -type f \
        -name '*.jar' \
        ! -name '*plain*' \
        | sort \
        | sed -n '1p'
    )"
    [[ -n "$jar" ]] || fail "backend bootJar was not created"
    cp "$jar" "$BACKEND_IMAGE_JAR"

    cat >"$BACKEND_IMAGE_DOCKERFILE" <<'DOCKERFILE'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY .agents/scratch/android-device-smoke-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
DOCKERFILE

    log "building backend image $BACKEND_IMAGE"
    docker build -f "$BACKEND_IMAGE_DOCKERFILE" -t "$BACKEND_IMAGE" "$ROOT_DIR"
  fi
}

start_backend_external() {
  if [[ "$SKIP_BACKEND" -eq 1 || "$BACKEND_MODE" == "external" ]]; then
    log "using existing backend on 127.0.0.1:$BACKEND_PORT"
    wait_http "http://127.0.0.1:$BACKEND_PORT/api/health" "existing backend" 30 ||
      fail "existing backend is not reachable on 127.0.0.1:$BACKEND_PORT"
    return 0
  fi
}

start_backend_docker() {
  ensure_backend_image
  local network
  network="$(dev_docker_network)"

  docker rm -f "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
  log "starting backend container on 127.0.0.1:$BACKEND_PORT"
  docker run -d \
    --name "$BACKEND_CONTAINER" \
    --network "$network" \
    -p "127.0.0.1:$BACKEND_PORT:8080" \
    -e SPRING_PROFILES_ACTIVE="prod" \
    -e SERVER_PORT="8080" \
    -e TZ="Asia/Seoul" \
    -e JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul" \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/surimap" \
    -e SPRING_DATASOURCE_USERNAME="surimap" \
    -e SPRING_DATASOURCE_PASSWORD="surimap" \
    -e MOCK_112_ENABLED="true" \
    -e MOCK_112_BASE_URL="http://mock-112:18112" \
    -e TILESERVER_MODE="tileserver-gl" \
    -e TILESERVER_BASE_URL="http://tileserver-gl:8080" \
    "$BACKEND_IMAGE" >/dev/null
  BACKEND_CONTAINER_STARTED=1

  for ((attempt = 1; attempt <= 90; attempt++)); do
    if curl -fsS "http://127.0.0.1:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
      log "backend container is ready"
      return 0
    fi
    if [[ -z "$(docker ps -q -f "name=^/${BACKEND_CONTAINER}$")" ]]; then
      docker logs "$BACKEND_CONTAINER" --tail 120 >&2 || true
      fail "backend container exited before health check"
    fi
    sleep 1
  done

  docker logs "$BACKEND_CONTAINER" --tail 120 >&2 || true
  fail "backend container did not become ready"
}

start_backend_gradle() {
  require_cmd java
  require_cmd curl
  require_cmd python3
  mkdir -p "$(dirname "$BACKEND_LOG")"
  : > "$BACKEND_LOG"

  local tileserver_base_url="http://127.0.0.1:$TILESERVER_PORT"
  if [[ "$SKIP_TILES" -eq 1 ]]; then
    tileserver_base_url="http://127.0.0.1:$TILESERVER_PORT"
  fi

  log "starting backend on 127.0.0.1:$BACKEND_PORT"
  setsid bash -c '
    cd "$1"
    SERVER_PORT="$2" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:'"$POSTGRES_PORT"'/surimap" \
    SPRING_DATASOURCE_USERNAME="surimap" \
    SPRING_DATASOURCE_PASSWORD="surimap" \
    MOCK_112_ENABLED="true" \
    MOCK_112_BASE_URL="http://localhost:'"$MOCK_112_PORT"'" \
    TILESERVER_MODE="tileserver-gl" \
    TILESERVER_BASE_URL="$3" \
    ./gradlew bootRun
  ' _ "$ROOT_DIR/backend" "$BACKEND_PORT" "$tileserver_base_url" >"$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!

  for ((attempt = 1; attempt <= 90; attempt++)); do
    if curl -fsS "http://127.0.0.1:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
      log "backend is ready"
      return 0
    fi
    if ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
      tail -120 "$BACKEND_LOG" >&2 || true
      fail "backend process exited before health check"
    fi
    sleep 1
  done

  tail -120 "$BACKEND_LOG" >&2 || true
  fail "backend did not become ready"
}

start_backend() {
  case "$BACKEND_MODE" in
    docker) start_backend_docker ;;
    gradle) start_backend_gradle ;;
    external) start_backend_external ;;
    *) fail "unsupported backend mode: $BACKEND_MODE" ;;
  esac
}

login_web() {
  local body
  body="$(
    curl -fsS \
      -H 'Content-Type: application/json' \
      -H 'X-Client-Channel: WEB' \
      -d '{"accountCode":"acct-precinct-cmd","password":"fixture","channel":"WEB"}' \
      "http://127.0.0.1:$BACKEND_PORT/api/auth/login"
  )"
  printf '%s' "$body" | json_field accessToken
}

seed_runtime_data() {
  [[ "$SKIP_SEED" -eq 0 ]] || return 0

  log "seeding mock-112 precinct scenario"
  local seed_body
  seed_body="$(mktemp)"
  local seed_status
  seed_status="$(
    curl_status "$seed_body" \
      -X POST \
      "http://127.0.0.1:$MOCK_112_PORT/mock-112/scenarios/precinct-first"
  )"
  if [[ "$seed_status" == 2* ]]; then
    log "mock-112 scenario seeded"
  else
    log "mock-112 scenario seed returned HTTP $seed_status; continuing with existing mock data"
  fi
  rm -f "$seed_body"

  local token
  token="$(login_web)"

  log "importing fixture incident"
  local import_body
  import_body="$(mktemp)"
  local import_status
  import_status="$(
    curl_status "$import_body" \
      -X POST \
      -H 'Content-Type: application/json' \
      -H 'X-Client-Channel: WEB' \
      -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: dev-import-$SOURCE_INCIDENT_ID-$(date +%Y%m%d%H%M%S)" \
      -d '{"sourceIncidentId":"'"$SOURCE_INCIDENT_ID"'"}' \
      "http://127.0.0.1:$BACKEND_PORT/api/incidents/import"
  )"
  if [[ "$import_status" != "201" && "$import_status" != "200" ]]; then
    cat "$import_body" >&2 || true
    rm -f "$import_body"
    fail "incident import failed with HTTP $import_status"
  fi
  rm -f "$import_body"

  [[ "$ENSURE_OVERALL" -eq 1 ]] || return 0
  ensure_overall_area "$token"
}

ensure_overall_area() {
  local token="$1"
  local response_file
  response_file="$(mktemp)"

  local status
  status="$(
    curl_status "$response_file" \
      -H 'X-Client-Channel: WEB' \
      -H "Authorization: Bearer $token" \
      "http://127.0.0.1:$BACKEND_PORT/api/search-areas?incidentId=$INCIDENT_ID&areaLevel=OVERALL&status=ACTIVE"
  )"

  if [[ "$status" == "200" ]]; then
    log "ACTIVE OVERALL search_area already exists"
    rm -f "$response_file"
    return 0
  fi

  log "creating ACTIVE OVERALL search_area"
  local client_ts
  client_ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  local payload
  payload="$(
    cat <<JSON
{"incidentId":"$INCIDENT_ID","opId":"$OP_ID","areaLevel":"OVERALL","geometry":{"type":"Polygon","coordinates":[[[126.904000,35.158000],[126.923000,35.158000],[126.923000,35.173000],[126.904000,35.173000],[126.904000,35.158000]]]},"memo":"local Android smoke overall","clientTs":"$client_ts"}
JSON
  )"

  status="$(
    curl_status "$response_file" \
      -X POST \
      -H 'Content-Type: application/json' \
      -H 'X-Client-Channel: WEB' \
      -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: dev-overall-$INCIDENT_ID-$(date +%Y%m%d%H%M%S)" \
      -d "$payload" \
      "http://127.0.0.1:$BACKEND_PORT/api/search-areas"
  )"
  if [[ "$status" != "201" && "$status" != "200" ]]; then
    cat "$response_file" >&2 || true
    rm -f "$response_file"
    fail "overall search_area creation failed with HTTP $status"
  fi
  rm -f "$response_file"
}

validate_tile_proxy() {
  [[ "$SKIP_TILES" -eq 0 ]] || return 0

  local token
  token="$(login_web)"
  local response_file
  response_file="$(mktemp)"

  local style_status
  style_status="$(
    curl_status "$response_file" \
      -H 'X-Client-Channel: WEB' \
      -H "Authorization: Bearer $token" \
      "http://127.0.0.1:$BACKEND_PORT/tiles/styles/osm-local.json"
  )"
  if [[ "$style_status" != "200" ]]; then
    cat "$response_file" >&2 || true
    rm -f "$response_file"
    fail "backend tile proxy failed with HTTP $style_status. Check backend mode=$BACKEND_MODE and TILESERVER_BASE_URL."
  fi

  local tile_status
  tile_status="$(
    curl_status "$response_file" \
      -H 'X-Client-Channel: WEB' \
      -H "Authorization: Bearer $token" \
      "http://127.0.0.1:$BACKEND_PORT/tiles/osm-local/16/55870/25920.pbf"
  )"
  if [[ "$tile_status" != "200" ]]; then
    cat "$response_file" >&2 || true
    rm -f "$response_file"
    fail "backend vector tile proxy failed with HTTP $tile_status. Check that the backend can reach tileserver-gl."
  fi

  rm -f "$response_file"
  log "backend tile proxy is ready"
}

resolve_adb() {
  [[ "$SKIP_ADB" -eq 0 ]] || return 0

  if [[ -n "$ADB" ]]; then
    [[ -x "$ADB" || -f "$ADB" ]] || fail "ADB path does not exist: $ADB"
    return 0
  fi

  local candidates=()
  if command -v adb >/dev/null 2>&1; then
    candidates+=("$(command -v adb)")
  fi

  local windows_adb="/mnt/c/Users/SSAFY/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  if [[ -f "$windows_adb" ]]; then
    candidates+=("$windows_adb")
  fi

  local fallback_adb=""
  for candidate in "${candidates[@]}"; do
    [[ -z "$fallback_adb" ]] && fallback_adb="$candidate"
    if [[ -n "$DEVICE_SERIAL" ]]; then
      if [[ "$("$candidate" -s "$DEVICE_SERIAL" get-state 2>/dev/null | tr -d '\r')" == "device" ]]; then
        ADB="$candidate"
        return 0
      fi
    elif "$candidate" devices 2>/dev/null |
      awk 'NR > 1 {gsub(/\r/, ""); if ($2 == "device") found = 1} END {exit found ? 0 : 1}'; then
      ADB="$candidate"
      return 0
    fi
  done

  if [[ -n "$fallback_adb" ]]; then
    ADB="$fallback_adb"
    return 0
  fi

  fail "adb not found. Set ADB=/path/to/adb or pass --skip-adb."
}

select_device() {
  [[ "$SKIP_ADB" -eq 0 ]] || return 0

  if [[ -n "$DEVICE_SERIAL" ]]; then
    return 0
  fi

  local devices
  devices="$("$ADB" devices | awk 'NR > 1 {gsub(/\r/, ""); if ($2 == "device") print $1}')"
  local count
  count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$count" == "0" ]]; then
    fail "no adb device is connected"
  fi
  if [[ "$count" != "1" ]]; then
    local physical_devices
    physical_devices="$(printf '%s\n' "$devices" | sed '/^$/d; /^emulator-/d')"
    local physical_count
    physical_count="$(printf '%s\n' "$physical_devices" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$physical_count" == "1" ]]; then
      DEVICE_SERIAL="$(printf '%s\n' "$physical_devices" | sed -n '1p')"
      return 0
    fi
    printf '%s\n' "$devices" >&2
    fail "multiple adb devices are connected. Pass --device SERIAL."
  fi
  DEVICE_SERIAL="$(printf '%s\n' "$devices" | sed -n '1p')"
}

build_apk_if_requested() {
  [[ "$BUILD_APK" -eq 1 ]] || return 0
  log "building debug APK for http://127.0.0.1:$APP_PORT"
  (
    cd "$ROOT_DIR/android"
    ./gradlew :app:assembleDebug -PsuriMapDebugApiBaseUrl="http://127.0.0.1:$APP_PORT"
  )
}

configure_device() {
  [[ "$SKIP_ADB" -eq 0 ]] || return 0

  resolve_adb
  select_device
  log "using adb device $DEVICE_SERIAL"

  "$ADB" -s "$DEVICE_SERIAL" reverse "tcp:$APP_PORT" "tcp:$BACKEND_PORT"
  log "adb reverse tcp:$APP_PORT -> tcp:$BACKEND_PORT configured"

  if [[ "$INSTALL_APK" -eq 1 ]]; then
    local apk="$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
    [[ -f "$apk" ]] || fail "debug APK not found: $apk"
    log "installing debug APK"
    "$ADB" -s "$DEVICE_SERIAL" install -r "$apk"
  fi

  if [[ "$RESET_APP" -eq 1 ]]; then
    log "clearing com.surimap app data"
    "$ADB" -s "$DEVICE_SERIAL" shell pm clear com.surimap >/dev/null || true
  fi

  for permission in \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.ACCESS_COARSE_LOCATION \
    android.permission.CAMERA \
    android.permission.POST_NOTIFICATIONS \
    android.permission.READ_MEDIA_IMAGES
  do
    "$ADB" -s "$DEVICE_SERIAL" shell pm grant com.surimap "$permission" >/dev/null 2>&1 || true
  done

  if [[ "$LAUNCH_APP" -eq 1 ]]; then
    log "launching com.surimap"
    "$ADB" -s "$DEVICE_SERIAL" shell monkey -p com.surimap -c android.intent.category.LAUNCHER 1 >/dev/null || true
  fi
}

main() {
  parse_args "$@"
  require_cmd curl
  require_cmd python3

  select_default_tiles_dir
  start_infra
  start_tileserver
  start_backend
  seed_runtime_data
  validate_tile_proxy
  build_apk_if_requested
  configure_device

  log "ready"
  log "backend mode: $BACKEND_MODE"
  log "backend: http://127.0.0.1:$BACKEND_PORT/api/health"
  if [[ "$SKIP_TILES" -eq 0 ]]; then
    log "tiles:   http://127.0.0.1:$BACKEND_PORT/tiles/styles/osm-local.json"
  fi
  if [[ -n "$BACKEND_PID" ]]; then
    log "backend log: $BACKEND_LOG"
    log "press Ctrl-C to stop backend"
    wait "$BACKEND_PID"
  elif [[ "$BACKEND_CONTAINER_STARTED" -eq 1 ]]; then
    log "backend container: $BACKEND_CONTAINER"
    log "press Ctrl-C to stop backend container and remove adb reverse"
    while true; do
      sleep 3600
    done
  else
    log "press Ctrl-C to remove adb reverse and exit"
    while true; do
      sleep 3600
    done
  fi

}

main "$@"
