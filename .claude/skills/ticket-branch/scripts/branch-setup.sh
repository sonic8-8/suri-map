#!/usr/bin/env bash
# branch-setup.sh — GitLab 원격 브랜치 생성 + git fetch + working tree 안전 검사 후 git switch.
#
# Usage:
#   branch-setup.sh \
#     --project <gitlab-project-path> \
#     --branch <full-branch-name> \
#     --ref <source-ref>
#
# stdout : 최종 브랜치명
# stderr : 진행 로그·에러
# exit 0 : 원격 생성 + switch 까지 완료
# exit 1 : 실패 (glab/git 에러)
# exit 2 : working tree 에 무관 tracked 변경이 있어 switch 보류 (변경 파일 목록 stderr)
#          → SKILL 이 사용자 confirm 받고 stash 후 이 스크립트 재호출.
#          → 재호출 시 원격 브랜치는 이미 존재해 자동 skip 된다.
set -euo pipefail

PROJECT=""
BRANCH=""
REF="develop"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --branch)  BRANCH="$2"; shift 2 ;;
    --ref)     REF="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$PROJECT" || -z "$BRANCH" ]]; then
  echo "usage: $0 --project <group/repo> --branch <branch> [--ref develop]" >&2
  exit 1
fi

# URL-encode '/' → '%2F'
PROJECT_ENC="${PROJECT//\//%2F}"

# 1. 원격 브랜치 생성 (이미 있으면 무시)
echo "creating remote branch $BRANCH from $REF..." >&2
CREATE_RESP=$(glab api -X POST "projects/${PROJECT_ENC}/repository/branches" \
  -f "branch=${BRANCH}" \
  -f "ref=${REF}" 2>&1) || {
    if printf '%s' "$CREATE_RESP" | grep -q "Branch already exists"; then
      echo "remote branch already exists — proceeding" >&2
    else
      echo "glab branch create failed:" >&2
      echo "$CREATE_RESP" >&2
      exit 1
    fi
  }

# 2. fetch
git fetch origin 2>&1 | tail -3 >&2 || { echo "git fetch failed" >&2; exit 1; }

# 3. working tree 안전 검사 — tracked 변경이 있으면 exit 2
DIRTY=$(git status --porcelain | grep -v '^??' || true)
if [[ -n "$DIRTY" ]]; then
  echo "working tree has tracked changes — switch aborted:" >&2
  echo "$DIRTY" >&2
  exit 2
fi

# 4. switch (자동 추적 + 체크아웃)
git switch "$BRANCH" 2>&1 | tail -3 >&2 || { echo "git switch failed" >&2; exit 1; }

# 5. switch 후 검증 — 의도치 않은 tracked 변경이 남으면 실패
LEFTOVER=$(git status --porcelain | grep -v '^??' || true)
if [[ -n "$LEFTOVER" ]]; then
  echo "post-switch working tree has unexpected changes:" >&2
  echo "$LEFTOVER" >&2
  exit 1
fi

printf '%s\n' "$BRANCH"
