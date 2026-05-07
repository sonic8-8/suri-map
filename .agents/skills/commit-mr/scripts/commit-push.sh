#!/usr/bin/env bash
# commit-push.sh — git add + commit (또는 push) sub-command.
#
# Usage:
#   commit-push.sh commit --file <path> [--file <path>...] --message <message>
#       단일 또는 다중 파일 커밋. message 는 heredoc 으로 multi-line 가능.
#       성공 시 commit SHA 를 stdout 으로 출력.
#
#   commit-push.sh push [--branch <branch>] [--upstream]
#       --branch 미입력 시 현재 브랜치 사용.
#       --upstream 지정 시 git push -u origin <branch>.
#
# Constraints:
#   .agents/scratch/와 legacy .claude/scratch/ 아래 파일은 절대 add 하지 않는다.
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 {commit|push} [args...]" >&2
  exit 1
fi

require_value() {
  local opt="$1"
  local value="${2-}"
  if [[ -z "$value" || "$value" == --* ]]; then
    echo "missing value for $opt" >&2
    exit 64
  fi
}

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

repo_relative() {
  local path="$1"
  case "$path" in
    "$REPO_ROOT"/*) path="${path#"$REPO_ROOT"/}" ;;
  esac
  path="${path#./}"
  printf '%s\n' "$path"
}

is_scratch_path() {
  local path
  path=$(repo_relative "$1")
  [[ "$path" == .agents/scratch/* || "$path" == .claude/scratch/* ]]
}

CMD="$1"; shift

case "$CMD" in
  commit)
    FILES=()
    MESSAGE=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --file)
          require_value "$1" "${2-}"
          FILES+=("$2")
          shift 2
          ;;
        --message)
          require_value "$1" "${2-}"
          MESSAGE="$2"
          shift 2
          ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
      esac
    done
    if [[ "${#FILES[@]}" -eq 0 || -z "$MESSAGE" ]]; then
      echo "usage: $0 commit --file <path> [--file <path>...] --message <msg>" >&2
      exit 1
    fi
    declare -A ALLOWED_FILES=()
    for file in "${FILES[@]}"; do
      if is_scratch_path "$file"; then
        echo "refused: $file is scratch metadata (excluded from commits)" >&2
        exit 1
      fi
      ALLOWED_FILES["$(repo_relative "$file")"]=1
    done
    git add -- "${FILES[@]}" >&2 || { echo "git add failed" >&2; exit 1; }
    while IFS= read -r staged; do
      [[ -z "$staged" ]] && continue
      if is_scratch_path "$staged"; then
        echo "refused: scratch metadata is staged: $staged" >&2
        exit 1
      fi
      if [[ -z "${ALLOWED_FILES[$staged]+x}" ]]; then
        echo "refused: unrelated staged file would be committed: $staged" >&2
        echo "unstage it or include it with another --file after user confirmation" >&2
        exit 1
      fi
    done < <(git diff --cached --name-only)
    git commit -m "$MESSAGE" --only -- "${FILES[@]}" >&2 || { echo "git commit failed" >&2; exit 1; }
    git rev-parse HEAD
    ;;

  push)
    BRANCH=""
    UPSTREAM=0
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --branch)
          require_value "$1" "${2-}"
          BRANCH="$2"
          shift 2
          ;;
        --upstream) UPSTREAM=1; shift ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
      esac
    done
    if [[ -z "$BRANCH" ]]; then
      BRANCH=$(git branch --show-current)
    fi
    if [[ "$UPSTREAM" -eq 1 ]]; then
      git push -u origin "$BRANCH" >&2 || { echo "git push -u failed" >&2; exit 1; }
    else
      git push origin "$BRANCH" >&2 || { echo "git push failed" >&2; exit 1; }
    fi
    printf '%s\n' "$BRANCH"
    ;;

  *)
    echo "unknown sub-command: $CMD (expected commit|push)" >&2
    exit 1
    ;;
esac
