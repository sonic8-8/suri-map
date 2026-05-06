#!/usr/bin/env bash
# commit-push.sh — git add + commit (또는 push) sub-command.
#
# Usage:
#   commit-push.sh commit --file <path> --message <message>
#       단일 커밋. message 는 heredoc 으로 multi-line 가능.
#       성공 시 commit SHA 를 stdout 으로 출력.
#
#   commit-push.sh push [--branch <branch>] [--upstream]
#       --branch 미입력 시 현재 브랜치 사용.
#       --upstream 지정 시 git push -u origin <branch>.
#
# Constraints:
#   .claude/scratch/ 아래 파일은 절대 add 하지 않는다 (인자로 들어와도 거부).
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 {commit|push} [args...]" >&2
  exit 1
fi

CMD="$1"; shift

case "$CMD" in
  commit)
    FILE=""
    MESSAGE=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --file)    FILE="$2"; shift 2 ;;
        --message) MESSAGE="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
      esac
    done
    if [[ -z "$FILE" || -z "$MESSAGE" ]]; then
      echo "usage: $0 commit --file <path> --message <msg>" >&2
      exit 1
    fi
    # scratch 보호
    if [[ "$FILE" == .claude/scratch/* || "$FILE" == */\.claude/scratch/* ]]; then
      echo "refused: $FILE is under .claude/scratch/ (excluded from commits)" >&2
      exit 1
    fi
    git add -- "$FILE" >&2 || { echo "git add failed" >&2; exit 1; }
    git commit -m "$MESSAGE" >&2 || { echo "git commit failed" >&2; exit 1; }
    git rev-parse HEAD
    ;;

  push)
    BRANCH=""
    UPSTREAM=0
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --branch)   BRANCH="$2"; shift 2 ;;
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
