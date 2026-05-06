#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.gradle}"

TIMEOUT_SECONDS="${AGENT_QUICKCHECK_TIMEOUT_SECONDS:-60}"
FULL="${AGENT_QUICKCHECK_FULL:-0}"

run_limited() {
  local label="$1"
  shift
  echo "agent quickcheck: $label" >&2
  if command -v timeout >/dev/null 2>&1; then
    timeout "${TIMEOUT_SECONDS}s" "$@"
  else
    "$@"
  fi
}

run_gradle_limited() {
  local label="$1"
  shift
  local output
  local rc
  set +e
  output="$(run_limited "$label" "$@" 2>&1)"
  rc="$?"
  set -e
  if [ "$rc" -eq 0 ]; then
    printf '%s\n' "$output" >&2
    return 0
  fi
  if printf '%s\n' "$output" | grep -q "Could not determine a usable wildcard IP"; then
    printf '%s\n' "$output" >&2
    echo "agent quickcheck: Gradle skipped because the current sandbox blocks network interface lookup" >&2
    return 0
  fi
  printf '%s\n' "$output" >&2
  return "$rc"
}

has_npm_script() {
  local script="$1"
  python3 - "$script" <<'PY'
import json
import sys
from pathlib import Path

script = sys.argv[1]
path = Path("frontend/package.json")
if not path.exists():
    sys.exit(1)
data = json.loads(path.read_text(encoding="utf-8"))
sys.exit(0 if script in data.get("scripts", {}) else 1)
PY
}

has_gradle_token() {
  local file="$1"
  local token="$2"
  [ -f "$file" ] && grep -q "$token" "$file"
}

changed_files="$(
  {
    git diff --name-only HEAD -- .
    git ls-files --others --exclude-standard
  } | sort -u
)"

if [ -z "$changed_files" ]; then
  echo "agent quickcheck: no changed files" >&2
  exit 0
fi

python3 .agents/scripts/check-fixture-contract.py

if printf '%s\n' "$changed_files" | grep -q '^frontend/'; then
  if [ "$FULL" = "1" ]; then
    has_npm_script typecheck && run_limited "frontend typecheck" bash -lc 'cd frontend && npm run typecheck'
    has_npm_script build && run_limited "frontend build" bash -lc 'cd frontend && npm run build'
  elif has_npm_script lint; then
    run_limited "frontend lint" bash -lc 'cd frontend && npm run lint'
    has_npm_script format:check && run_limited "frontend format:check" bash -lc 'cd frontend && npm run format:check'
  elif has_npm_script typecheck; then
    run_limited "frontend typecheck" bash -lc 'cd frontend && npm run typecheck'
  else
    echo "frontend smoke check not configured; skipped" >&2
  fi
fi

if printf '%s\n' "$changed_files" | grep -q '^backend/'; then
  if [ "$FULL" = "1" ]; then
    run_gradle_limited "backend test" bash -lc 'cd backend && ./gradlew --no-daemon test'
  elif has_gradle_token backend/build.gradle "spotless"; then
    run_gradle_limited "backend spotlessCheck" bash -lc 'cd backend && ./gradlew --no-daemon spotlessCheck'
  elif has_gradle_token backend/build.gradle "checkstyle"; then
    run_gradle_limited "backend checkstyle" bash -lc 'cd backend && ./gradlew --no-daemon checkstyleMain checkstyleTest'
  else
    echo "backend lint not configured; skipped full Gradle check" >&2
  fi
fi

if printf '%s\n' "$changed_files" | grep -q '^android/'; then
  if [ "$FULL" = "1" ]; then
    run_gradle_limited "android assembleDebug" bash -lc 'cd android && ./gradlew --no-daemon :app:assembleDebug'
  elif has_gradle_token android/build.gradle.kts "ktlint" || has_gradle_token android/gradle/libs.versions.toml "ktlint"; then
    run_gradle_limited "android ktlintCheck" bash -lc 'cd android && ./gradlew --no-daemon ktlintCheck'
  else
    echo "android lint not configured; skipped assembleDebug" >&2
  fi
fi
