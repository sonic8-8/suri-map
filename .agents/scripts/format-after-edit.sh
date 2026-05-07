#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <changed-file>..." >&2
  exit 64
}

if [ "$#" -eq 0 ]; then
  usage
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.gradle}"

normalize_path() {
  local input="${1#./}"
  case "$input" in
    "$ROOT"/*) input="${input#"$ROOT"/}" ;;
    /*) return 1 ;;
  esac
  input="${input#./}"
  case "$input" in
    "" | ../* | */../*) return 1 ;;
  esac
  printf '%s\n' "$input"
}

has_gradle_token() {
  local file="$1"
  local token="$2"
  [ -f "$file" ] && grep -q "$token" "$file"
}

frontend_prettier_files=()
frontend_eslint_files=()
backend_changed=0
android_changed=0

for file in "$@"; do
  rel="$(normalize_path "$file" || true)"
  [ -n "${rel:-}" ] || continue
  [ -f "$rel" ] || continue

  case "$rel" in
    frontend/*)
      frontend_path="${rel#frontend/}"
      case "$rel" in
        *.css | *.html | *.js | *.json | *.jsx | *.md | *.ts | *.tsx | *.yaml | *.yml)
          frontend_prettier_files+=("$frontend_path")
          ;;
      esac
      case "$rel" in
        *.js | *.jsx | *.ts | *.tsx)
          frontend_eslint_files+=("$frontend_path")
          ;;
      esac
      ;;
    backend/*) backend_changed=1 ;;
    android/*) android_changed=1 ;;
  esac
done

if [ "${#frontend_prettier_files[@]}" -gt 0 ]; then
  if [ -x frontend/node_modules/.bin/prettier ]; then
    (cd frontend && ./node_modules/.bin/prettier --write -- "${frontend_prettier_files[@]}")
  else
    echo "frontend prettier is not installed; skipped" >&2
  fi
fi

if [ "${#frontend_eslint_files[@]}" -gt 0 ]; then
  if [ -x frontend/node_modules/.bin/eslint ]; then
    (cd frontend && ./node_modules/.bin/eslint --fix -- "${frontend_eslint_files[@]}")
  else
    echo "frontend eslint is not installed; skipped" >&2
  fi
fi

if [ "$backend_changed" -eq 1 ]; then
  if [ "${AGENT_FORMAT_BROAD:-0}" = "1" ] && has_gradle_token backend/build.gradle "spotless"; then
    (cd backend && ./gradlew --no-daemon spotlessApply)
  else
    echo "backend broad formatter skipped; run AGENT_FORMAT_BROAD=1 $0 <file> or ./gradlew spotlessApply explicitly" >&2
  fi
fi

if [ "$android_changed" -eq 1 ]; then
  if [ "${AGENT_FORMAT_BROAD:-0}" = "1" ] && { has_gradle_token android/build.gradle.kts "ktlint" || has_gradle_token android/gradle/libs.versions.toml "ktlint"; }; then
    (cd android && ./gradlew --no-daemon ktlintFormat)
  else
    echo "android broad formatter skipped; run AGENT_FORMAT_BROAD=1 $0 <file> or ./gradlew ktlintFormat explicitly" >&2
  fi
fi
