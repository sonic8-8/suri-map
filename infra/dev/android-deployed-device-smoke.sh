#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

BACKEND_URL="${BACKEND_URL:-https://k14c106.p.ssafy.io}"
POLICE_PHONE_ID="${SURI_MAP_DEBUG_POLICE_PHONE_ID:-00000000-0000-0000-0000-000000000101}"
ADB="${ADB:-}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
SKIP_ADB=0
BUILD_APK=1
INSTALL_APK=1
RESET_APP=0
LAUNCH_APP=1
ALLOW_GLYPH_FAILURE=0

usage() {
  cat <<USAGE
Usage: bash infra/dev/android-deployed-device-smoke.sh [options]

Builds and installs the Android debug APK against an already deployed Suri-Map
backend. This does not start local Postgres, mock-112, TileServer, backend
containers, or adb reverse.

Options:
  --backend-url URL      Deployed backend base URL. Default: ${BACKEND_URL}
  --device SERIAL        Use a specific adb device serial.
  --skip-adb             Do not install, grant permissions, or launch the app.
  --no-build             Reuse the existing app-debug.apk.
  --no-install           Do not install app-debug.apk.
  --reset-app            Clear com.surimap app data before launch.
  --no-launch            Do not launch com.surimap.
  --allow-glyph-failure  Continue when deployed Pretendard GOV glyph PBF is missing.
  -h, --help             Show this help.
USAGE
}

log() {
  printf '[android-deployed-smoke] %s\n' "$*"
}

fail() {
  printf '[android-deployed-smoke] ERROR: %s\n' "$*" >&2
  exit 1
}

http_status() {
  curl -sS -o /dev/null -w '%{http_code}' "$1" || true
}

require_reachable_or_auth_protected() {
  local url="$1"
  local label="$2"
  local status
  status="$(http_status "$url")"
  case "$status" in
    2*|401)
      log "$label route is reachable (HTTP $status)"
      ;;
    *)
      fail "$label is not reachable: $url (HTTP ${status:-curl_failed})"
      ;;
  esac
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --backend-url)
        BACKEND_URL="${2:-}"
        [[ -n "$BACKEND_URL" ]] || fail "--backend-url requires a URL"
        shift 2
        ;;
      --device)
        DEVICE_SERIAL="${2:-}"
        [[ -n "$DEVICE_SERIAL" ]] || fail "--device requires a serial"
        shift 2
        ;;
      --skip-adb)
        SKIP_ADB=1
        INSTALL_APK=0
        LAUNCH_APP=0
        shift
        ;;
      --no-build)
        BUILD_APK=0
        shift
        ;;
      --no-install)
        INSTALL_APK=0
        shift
        ;;
      --reset-app)
        RESET_APP=1
        shift
        ;;
      --no-launch)
        LAUNCH_APP=0
        shift
        ;;
      --allow-glyph-failure)
        ALLOW_GLYPH_FAILURE=1
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

  BACKEND_URL="${BACKEND_URL%/}"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
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

  for candidate in "${candidates[@]}"; do
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

  fail "adb not found. Set ADB=/path/to/adb or pass --skip-adb."
}

select_device() {
  [[ "$SKIP_ADB" -eq 0 ]] || return 0
  [[ -n "$DEVICE_SERIAL" ]] && return 0

  local devices
  devices="$("$ADB" devices | awk 'NR > 1 {gsub(/\r/, ""); if ($2 == "device") print $1}')"
  local count
  count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  [[ "$count" != "0" ]] || fail "no adb device is connected"
  if [[ "$count" != "1" ]]; then
    printf '%s\n' "$devices" >&2
    fail "multiple adb devices are connected. Pass --device SERIAL."
  fi
  DEVICE_SERIAL="$(printf '%s\n' "$devices" | sed -n '1p')"
}

validate_backend() {
  log "checking deployed backend $BACKEND_URL"
  curl -fsS "$BACKEND_URL/api/health" >/dev/null ||
    fail "backend health is not reachable: $BACKEND_URL/api/health"

  curl -fsS "$BACKEND_URL/keycloak/realms/suri-map/.well-known/openid-configuration" >/dev/null ||
    fail "Keycloak OIDC discovery is not reachable through deployed route"

  require_reachable_or_auth_protected "$BACKEND_URL/tiles/styles/osm-local.json" "tile style"
  require_reachable_or_auth_protected "$BACKEND_URL/tiles/osm-local/13/6984/3172.pbf" "vector tile"

  local glyph_status
  glyph_status="$(http_status "$BACKEND_URL/tiles/fonts/Pretendard%20GOV/0-255.pbf")"
  if [[ "$glyph_status" != 2* && "$glyph_status" != "401" ]]; then
    [[ "$ALLOW_GLYPH_FAILURE" -eq 1 ]] ||
      fail "Pretendard GOV glyph PBF is not reachable through deployed backend (HTTP ${glyph_status:-curl_failed})"
    log "warning: deployed Pretendard GOV glyph PBF is missing or not routed; map labels may not render"
  else
    log "Pretendard GOV glyph route is reachable (HTTP $glyph_status)"
  fi

  log "deployed backend, OIDC discovery, tile style, and vector tile are ready"
}

build_apk() {
  [[ "$BUILD_APK" -eq 1 ]] || return 0
  log "building debug APK for $BACKEND_URL"
  (
    cd "$ROOT_DIR/android"
    ./gradlew :app:assembleDebug \
      -PsuriMapDebugApiBaseUrl="$BACKEND_URL" \
      -PsuriMapKeycloakIssuerUrl="$BACKEND_URL/keycloak/realms/suri-map" \
      -PsuriMapDebugBootstrapPolicePhoneId="$POLICE_PHONE_ID"
  )
}

configure_device() {
  [[ "$SKIP_ADB" -eq 0 ]] || return 0
  resolve_adb
  select_device
  log "using adb device $DEVICE_SERIAL"

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
  validate_backend
  build_apk
  configure_device
  log "ready"
  log "backend: $BACKEND_URL/api/health"
  log "apk:     $ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
}

main "$@"
