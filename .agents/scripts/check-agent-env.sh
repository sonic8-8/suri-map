#!/usr/bin/env bash
set -euo pipefail

missing=0

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "missing command: $name" >&2
    missing=1
  fi
}

require_command git
require_command glab
require_command curl
require_command python3

if command -v jq >/dev/null 2>&1; then
  echo "optional command available: jq" >&2
fi

if [ -z "${GITLAB_TOKEN:-}" ]; then
  if command -v glab >/dev/null 2>&1; then
    if command -v timeout >/dev/null 2>&1; then
      glab_auth_cmd=(timeout 5 glab auth status)
    else
      glab_auth_cmd=(glab auth status)
    fi
    if ! "${glab_auth_cmd[@]}" >/dev/null 2>&1; then
      echo "missing GitLab auth: set GITLAB_TOKEN or run glab auth login" >&2
      missing=1
    fi
  fi
fi

if [ -z "${JIRA_API_TOKEN:-}" ]; then
  if ! command -v acli >/dev/null 2>&1; then
    echo "missing Jira auth path: set JIRA_API_TOKEN or install/authenticate acli" >&2
    missing=1
  fi
fi

if [ "$missing" -ne 0 ]; then
  exit 1
fi

exit 0
