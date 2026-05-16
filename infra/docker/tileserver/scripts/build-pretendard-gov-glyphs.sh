#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
DEFAULT_FONT_SOURCE="$ROOT_DIR/android/app/src/main/res/font/pretendard_gov_variable.ttf"
DEFAULT_OUTPUT_DIR="${TILESERVER_DIR:-/home/ubuntu/infra/tileserver}/fonts/Pretendard GOV"
FONT_SOURCE="${1:-$DEFAULT_FONT_SOURCE}"
OUTPUT_DIR="${2:-$DEFAULT_OUTPUT_DIR}"
FONT_STACK_NAME="${FONT_STACK_NAME:-Pretendard GOV}"
FONTNIK_IMAGE="${FONTNIK_IMAGE:-node:20-bookworm}"
FONTNIK_VERSION="${FONTNIK_VERSION:-0.7.4}"

log() {
  printf '[build-pretendard-gov-glyphs] %s\n' "$*"
}

fail() {
  printf '[build-pretendard-gov-glyphs] ERROR: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "docker is required"
[[ -f "$FONT_SOURCE" ]] || fail "font source not found: $FONT_SOURCE"
[[ "$FONT_SOURCE" == *.ttf || "$FONT_SOURCE" == *.otf ]] ||
  fail "fontnik build-glyphs requires a TTF/OTF font source: $FONT_SOURCE"
[[ -n "$OUTPUT_DIR" && "$OUTPUT_DIR" != "/" ]] || fail "invalid output dir: $OUTPUT_DIR"

output_parent="$(dirname "$OUTPUT_DIR")"
tmp_dir="$output_parent/.${FONT_STACK_NAME// /-}.tmp.$$"
container_font_file="source-font.${FONT_SOURCE##*.}"

cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

mkdir -p "$output_parent"
rm -rf "$tmp_dir"
mkdir -p "$tmp_dir"
cp "$FONT_SOURCE" "$tmp_dir/$container_font_file"

log "generating glyph PBF files from $FONT_SOURCE"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e npm_config_prefix=/tmp/npm \
  -v "$tmp_dir:/glyph-work" \
  "$FONTNIK_IMAGE" \
  sh -lc "mkdir -p /glyph-work/out && npm_config_loglevel=error npm install -g --no-audit --no-fund fontnik@$FONTNIK_VERSION >/tmp/fontnik-install.log && /tmp/npm/bin/build-glyphs \"/glyph-work/$container_font_file\" /glyph-work/out"

[[ -f "$tmp_dir/out/0-255.pbf" ]] ||
  fail "glyph generation did not produce 0-255.pbf"

rm -rf "$OUTPUT_DIR"
mv "$tmp_dir/out" "$OUTPUT_DIR"
trap - EXIT
rm -rf "$tmp_dir"

glyph_count="$(find "$OUTPUT_DIR" -type f -name '*.pbf' | wc -l | tr -d ' ')"
log "ready: $OUTPUT_DIR ($glyph_count pbf files)"
