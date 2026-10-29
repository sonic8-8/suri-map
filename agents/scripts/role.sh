#!/usr/bin/env bash
# 오케스트레이터가 Herdr pane의 역할에게 배정을 보내고 기다릴 때 쓴다.
# 직접 조합하던 순서(보내기, 시작 확인, 기다리기, 승인, 새 대화)를 한곳에 고정한다.
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage:
  role.sh send <agent> <message>            배정을 보내고 역할이 일을 시작했는지 확인한다
  role.sh wait <agent> [minutes]            idle, done, blocked 가운데 하나가 될 때까지 기다린다(기본 30분)
  role.sh approve <agent>                   Codex 승인 화면에 y를 한 번 보내고 다시 기다린다
  role.sh clear <agent>                     Claude 역할의 대화를 /clear로 새로 연다
  role.sh restart-codex <agent> <dir> -- <codex args>
                                            Codex 역할을 끝내고 <dir>에서 다시 띄운다
EOF
  exit 2
}

field() { herdr agent get "$1" 2>/dev/null | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4 || true; }
status() { field "$1" agent_status; }

send() {
  local name=$1 message=$2 out
  out=$(herdr agent prompt "$name" "$message" --wait --timeout 30000 2>&1) && { echo started; return 0; }
  case $out in
    *'"code":"timeout"'*) echo started; return 0 ;;
    # 5초 안에 시작 신호가 잡히지 않았을 뿐, 뒤늦게 시작하는 일이 있다.
    *agent_prompt_stalled*) ;;
    *) echo "send_failed: $out" >&2; return 1 ;;
  esac
  for _ in $(seq 1 "${ROLE_START_CHECKS:-15}"); do
    case $(status "$name") in working | blocked) echo started; return 0 ;; esac
    sleep "${ROLE_POLL_SECONDS:-2}"
  done
  echo "not_started: $(status "$name")" >&2
  return 1
}

# 결과 파일만 기다리면 배정이 전달되지 않았을 때 끝나지 않으므로 상태로 기다린다.
wait_settled() {
  local name=$1 minutes=${2:-30} s deadline
  deadline=$(($(date +%s) + minutes * 60))
  # 승인 화면에 답한 직후에는 상태가 잠시 blocked로 남는다.
  sleep "${ROLE_SETTLE_SECONDS:-10}"
  while :; do
    s=$(status "$name")
    case $s in
      idle | done | blocked) echo "$s"; return 0 ;;
      '') echo gone >&2; return 1 ;;
    esac
    if [ "$(date +%s)" -ge "$deadline" ]; then echo "timeout: $s" >&2; return 1; fi
    herdr agent wait "$name" --timeout 600000 >/dev/null 2>&1 || sleep "${ROLE_POLL_SECONDS:-2}"
  done
}

approve() {
  [ "$(status "$1")" = blocked ] || { echo "not_blocked: $(status "$1")" >&2; return 1; }
  herdr agent send-keys "$1" y >/dev/null
  wait_settled "$1"
}

clear_claude() {
  local pane
  pane=$(field "$1" pane_id)
  [ -n "$pane" ] || { echo "agent_not_found: $1" >&2; return 1; }
  herdr pane run "$pane" "/clear" >/dev/null
  sleep "${ROLE_POLL_SECONDS:-2}"
}

restart_codex() {
  local name=$1 dir=$2 pane
  shift 2
  [ "${1:-}" = -- ] && shift
  pane=$(field "$name" pane_id)
  [ -n "$pane" ] || { echo "agent_not_found: $name" >&2; return 1; }
  # /exit는 agent prompt가 아니라 pane run으로 보내야 Codex가 끝난다.
  herdr pane run "$pane" "/exit" >/dev/null
  for _ in $(seq 1 15); do
    [ -z "$(status "$name")" ] && break
    sleep "${ROLE_POLL_SECONDS:-1}"
  done
  [ -z "$(status "$name")" ] || { echo "still_running: $name" >&2; return 1; }
  herdr pane run "$pane" "cd $(printf %q "$dir")" >/dev/null
  herdr agent start "$name" --kind codex --pane "$pane" -- "$@" >/dev/null
  echo "started in $dir"
}

case ${1:-} in
  send) [ $# -eq 3 ] || usage; send "$2" "$3" ;;
  wait) [ $# -ge 2 ] || usage; wait_settled "$2" "${3:-30}" ;;
  approve) [ $# -eq 2 ] || usage; approve "$2" ;;
  clear) [ $# -eq 2 ] || usage; clear_claude "$2" ;;
  restart-codex) [ $# -ge 3 ] || usage; shift; restart_codex "$@" ;;
  *) usage ;;
esac
