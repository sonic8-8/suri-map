#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: render-load-test-dashboard.sh TARGET_RPS FROM TO OUTPUT.png

Example:
  render-load-test-dashboard.sh \
    453 \
    2026-09-01T03:48:30Z \
    2026-09-01T03:56:30Z \
    /srv/ops/k6/results/issue-8-server-breakpoint-test.png
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if (( $# != 4 )); then
  usage >&2
  exit 2
fi

TARGET_RPS="$1"
FROM="$2"
TO="$3"
OUTPUT_PATH="$4"

if [[ ! "$TARGET_RPS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'TARGET_RPS must be a positive integer: %s\n' "$TARGET_RPS" >&2
  exit 2
fi

OPS_DIR=/srv/ops
ENV_FILE="$OPS_DIR/.env"
BASE_COMPOSE_FILE="$OPS_DIR/docker-compose.yml"
RENDERER_COMPOSE_FILE="$OPS_DIR/docker-compose.image-renderer.yml"
DASHBOARD_FILE="$OPS_DIR/grafana/provisioning/dashboards/json/suri-map-load-test.json"
EXPECTED_DASHBOARD_SHA256=91ea4373fa0dd17d28b8ab4b1b19e70ef6dcbe07697e7a3be3bc3d45ebedb7df

for required_file in "$ENV_FILE" "$BASE_COMPOSE_FILE" "$RENDERER_COMPOSE_FILE" "$DASHBOARD_FILE"; do
  if [[ ! -f "$required_file" ]]; then
    printf 'required file not found: %s\n' "$required_file" >&2
    exit 1
  fi
done

ACTUAL_DASHBOARD_SHA256=$(sha256sum "$DASHBOARD_FILE" | cut -d' ' -f1)
if [[ "$ACTUAL_DASHBOARD_SHA256" != "$EXPECTED_DASHBOARD_SHA256" ]]; then
  printf 'Grafana dashboard does not match this renderer: %s\n' "$DASHBOARD_FILE" >&2
  exit 1
fi

read_env() {
  local key="$1"
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN="${GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN:-$(read_env GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN)}"
if [[ -z "$GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN" ]]; then
  printf 'GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN is missing from %s\n' "$ENV_FILE" >&2
  exit 1
fi

GRAFANA_RENDERER_TOKEN="${GRAFANA_RENDERER_TOKEN:-$(read_env GRAFANA_RENDERER_TOKEN)}"
if [[ -z "$GRAFANA_RENDERER_TOKEN" ]]; then
  printf 'GRAFANA_RENDERER_TOKEN is missing from %s\n' "$ENV_FILE" >&2
  exit 1
fi
export GRAFANA_RENDERER_TOKEN

if ! FROM_SECONDS=$(date -u -d "$FROM" +%s 2>/dev/null); then
  printf 'FROM must be an ISO 8601 date: %s\n' "$FROM" >&2
  exit 2
fi
if ! TO_SECONDS=$(date -u -d "$TO" +%s 2>/dev/null); then
  printf 'TO must be an ISO 8601 date: %s\n' "$TO" >&2
  exit 2
fi
if (( FROM_SECONDS >= TO_SECONDS )); then
  printf 'FROM must be earlier than TO\n' >&2
  exit 2
fi

FROM_MS=$((FROM_SECONDS * 1000))
TO_MS=$((TO_SECONDS * 1000))
OUTPUT_DIR=$(dirname -- "$OUTPUT_PATH")
mkdir -p -- "$OUTPUT_DIR"
OUTPUT_TMP=$(mktemp "${OUTPUT_PATH}.tmp.XXXXXX")
AUTH_HEADER_FILE=$(mktemp)
umask 077
printf 'Authorization: Bearer %s\n' "$GRAFANA_RENDER_SERVICE_ACCOUNT_TOKEN" > "$AUTH_HEADER_FILE"

COMPOSE=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$BASE_COMPOSE_FILE"
  -f "$RENDERER_COMPOSE_FILE"
  --profile render
)

cleanup() {
  "${COMPOSE[@]}" stop grafana-image-renderer >/dev/null 2>&1 || true
  rm -f -- "$OUTPUT_TMP" "$AUTH_HEADER_FILE"
}
trap cleanup EXIT

"${COMPOSE[@]}" config >/dev/null
"${COMPOSE[@]}" up -d --no-deps grafana-image-renderer

curl \
  --fail \
  --silent \
  --show-error \
  --retry 10 \
  --retry-all-errors \
  --retry-delay 1 \
  --retry-max-time 55 \
  --max-time 45 \
  --header "@$AUTH_HEADER_FILE" \
  --get 'http://127.0.0.1:3000/render/d/suri-map-load-test/suri-map-load-test' \
  --data-urlencode "from=$FROM_MS" \
  --data-urlencode "to=$TO_MS" \
  --data-urlencode "var-target_rps=$TARGET_RPS" \
  --data-urlencode 'width=2560' \
  --data-urlencode 'height=1440' \
  --data-urlencode 'tz=Asia/Seoul' \
  --data-urlencode 'kiosk=1' \
  --data-urlencode 'theme=dark' \
  --output "$OUTPUT_TMP"

PNG_SIGNATURE=$(od -An -tx1 -N8 "$OUTPUT_TMP" | tr -d '[:space:]')
if [[ "$PNG_SIGNATURE" != "89504e470d0a1a0a" ]]; then
  printf 'Grafana did not return a PNG image\n' >&2
  exit 1
fi

mv -f -- "$OUTPUT_TMP" "$OUTPUT_PATH"
printf 'rendered=%s\n' "$OUTPUT_PATH"
printf 'sha256=%s\n' "$(sha256sum "$OUTPUT_PATH" | cut -d' ' -f1)"
