#!/usr/bin/env bash
# mr-create.sh — glab 으로 GitLab MR 을 생성한다.
#
# Usage:
#   mr-create.sh \
#     --source <branch> \
#     --target <ref> \
#     --title "<title>" \
#     --desc-file <path> \
#     --label "<label-or-csv>" \
#     [--assignee "@me"]
#
# --label : 단일 라벨 또는 콤마 구분 결합. 예: "⌨️ BE" 또는 "⌨️ BE,🖥️ FE"
#           스크립트가 콤마 split 후 각 라벨에 대해 별도 --label 플래그로 glab 에 전달.
#           라벨 이름에 이모지·공백이 포함될 수 있으므로 따옴표로 감싸서 전달할 것.
#
# stdout : MR URL
# stderr : glab 응답·에러
# exit 0 : 성공
# exit 1 : 실패
#
# 옵션 default 정책 (SKILL 와 동기):
#   - Squash               OFF
#   - Delete source branch ON  (--remove-source-branch — 머지 후 원격 자동 삭제)
#   - Draft                OFF
set -euo pipefail

SOURCE=""
TARGET="develop"
TITLE=""
DESC_FILE=""
LABEL=""
ASSIGNEE="@me"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source)    SOURCE="$2"; shift 2 ;;
    --target)    TARGET="$2"; shift 2 ;;
    --title)     TITLE="$2"; shift 2 ;;
    --desc-file) DESC_FILE="$2"; shift 2 ;;
    --label)     LABEL="$2"; shift 2 ;;
    --assignee)  ASSIGNEE="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$SOURCE" || -z "$TITLE" || -z "$DESC_FILE" || -z "$LABEL" ]]; then
  echo "usage: $0 --source <branch> --target <ref> --title <title> --desc-file <path> --label <label> [--assignee @me]" >&2
  exit 1
fi

if [[ ! -f "$DESC_FILE" ]]; then
  echo "desc file not found: $DESC_FILE" >&2
  exit 1
fi

DESCRIPTION=$(cat "$DESC_FILE")

# --label 콤마 split → 각각 별도 --label 플래그 배열로 변환
LABEL_ARGS=()
IFS=',' read -ra LABELS <<< "$LABEL"
for L in "${LABELS[@]}"; do
  L_TRIMMED="${L#"${L%%[![:space:]]*}"}"
  L_TRIMMED="${L_TRIMMED%"${L_TRIMMED##*[![:space:]]}"}"
  if [[ -n "$L_TRIMMED" ]]; then
    LABEL_ARGS+=("--label" "$L_TRIMMED")
  fi
done

# glab mr create — 표준 출력에 MR URL 이 포함된다.
RESPONSE=$(glab mr create \
  --source-branch "$SOURCE" \
  --target-branch "$TARGET" \
  --title "$TITLE" \
  --description "$DESCRIPTION" \
  --assignee "$ASSIGNEE" \
  "${LABEL_ARGS[@]}" \
  --remove-source-branch 2>&1) || {
    echo "glab mr create failed:" >&2
    echo "$RESPONSE" >&2
    exit 1
  }

# 응답에서 https://lab.ssafy.com/.../merge_requests/<num> URL 추출
URL=$(printf '%s' "$RESPONSE" | grep -oE 'https://[^[:space:]]+/merge_requests/[0-9]+' | head -1 || true)

if [[ -z "$URL" ]]; then
  echo "could not parse MR URL from response:" >&2
  echo "$RESPONSE" >&2
  exit 1
fi

printf '%s\n' "$URL"
