#!/usr/bin/env bash
# ticket-create.sh — acli 로 Jira 티켓을 발급하고 issue key 를 stdout 으로 출력한다.
#
# Usage:
#   ticket-create.sh \
#     --project <PROJECT-KEY> \
#     --type "<TYPE>" \
#     --summary "<SUMMARY>" \
#     --desc-file <PATH> \
#     --assignee "@me"
#
# stdout : 발급된 issue key (예: S14P31C106-77)
# stderr : acli 응답·에러 메시지
# exit 0 : 성공
# exit 1 : 실패
set -euo pipefail

PROJECT="S14P31C106"
TYPE=""
SUMMARY=""
DESC_FILE=""
ASSIGNEE="@me"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project)   PROJECT="$2"; shift 2 ;;
    --type)      TYPE="$2"; shift 2 ;;
    --summary)   SUMMARY="$2"; shift 2 ;;
    --desc-file) DESC_FILE="$2"; shift 2 ;;
    --assignee)  ASSIGNEE="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$TYPE" || -z "$SUMMARY" || -z "$DESC_FILE" ]]; then
  echo "usage: $0 --project KEY --type TYPE --summary TEXT --desc-file PATH [--assignee @me]" >&2
  exit 1
fi

if [[ ! -f "$DESC_FILE" ]]; then
  echo "desc file not found: $DESC_FILE" >&2
  exit 1
fi

# acli 호출 (--json 으로 응답 받기)
RESPONSE=$(acli jira workitem create \
  --project "$PROJECT" \
  --type "$TYPE" \
  --summary "$SUMMARY" \
  --description-file "$DESC_FILE" \
  --assignee "$ASSIGNEE" \
  --json 2>&1) || {
    echo "acli create failed:" >&2
    echo "$RESPONSE" >&2
    exit 1
  }

# root level "key" 추출 (python3 사용 — 안정적)
KEY=$(printf '%s' "$RESPONSE" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get("key", ""))
except Exception as e:
    sys.stderr.write(f"json parse failed: {e}\n")
    sys.exit(2)
') || {
  # fallback: search 로 가장 최근 발급한 티켓 조회
  echo "primary key parse failed, falling back to search..." >&2
  SEARCH_RESPONSE=$(acli jira workitem search \
    --jql "project = $PROJECT AND summary ~ \"$SUMMARY\" ORDER BY created DESC" \
    --limit 1 --json 2>&1) || {
      echo "fallback search failed:" >&2
      echo "$SEARCH_RESPONSE" >&2
      exit 1
    }
  KEY=$(printf '%s' "$SEARCH_RESPONSE" | grep -m1 -oE '"key":\s*"'"$PROJECT"'-[0-9]+"' | grep -oE "$PROJECT-[0-9]+" || true)
}

if [[ -z "$KEY" ]]; then
  echo "could not parse issue key" >&2
  exit 1
fi

printf '%s\n' "$KEY"
