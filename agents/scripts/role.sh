#!/usr/bin/env bash
# 오케스트레이터가 Herdr pane의 역할에게 배정을 보내고 기다릴 때 쓴다.
# 직접 조합하던 순서(띄우기, 보내기, 시작 확인, 기다리기, 승인, 새 대화)를 한곳에 고정한다.
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage:
  role.sh send <agent> <message>            배정을 보내고 역할이 일을 시작했는지 확인한다
  role.sh wait <agent> [minutes]            idle, done, blocked 가운데 하나가 될 때까지 기다린다(기본 30분)
  role.sh approve <agent>                   Codex 승인 화면에 y를 한 번 보내고 다시 기다린다
  role.sh clear <agent>                     Claude 역할의 대화를 /clear로 새로 연다
  role.sh start-reviewer <agent> <pane> <worktree> <run dir>
                                            리뷰어를 <worktree>에서 띄운다. 쓰기는 <run dir> 안에만 허용한다
  role.sh start-codex <agent> <pane> <dir> -- <codex args>
                                            Codex 역할을 <dir>에서 띄운다
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
  wait_started "$name" && { echo started; return 0; }
  # 막 띄운 역할은 idle로 보여도 아직 입력을 받지 못해 Enter나 문장 전체를 버릴 때가 있다.
  # 문장이 화면에 남아 있으면 Enter만 누르고, 없으면 한 번 다시 보낸다. 어느 쪽도 배정이 두 번 들어가지 않는다.
  if herdr agent read "$name" --source visible 2>/dev/null | grep -qF -- "${message:0:30}"; then
    herdr agent send-keys "$name" enter >/dev/null
  else
    herdr agent prompt "$name" "$message" >/dev/null 2>&1 || true
  fi
  wait_started "$name" && { echo started; return 0; }
  echo "not_started: $(status "$name")" >&2
  return 1
}

wait_started() {
  for _ in $(seq 1 "${ROLE_START_CHECKS:-15}"); do
    case $(status "$1") in working | blocked) return 0 ;; esac
    sleep "${ROLE_POLL_SECONDS:-2}"
  done
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

# 빈 shell pane을 <dir>로 옮긴 뒤 역할을 띄운다. 실행 기록에 남길 시작 인자는 실제로 넘긴 배열에서 출력한다.
start_in() {
  local name=$1 kind=$2 pane=$3 dir=$4
  shift 4
  local cmd=(herdr agent start "$name" --kind "$kind" --pane "$pane" --timeout 60000 -- "$@")
  herdr pane run "$pane" "cd $(printf %q "$dir")" >/dev/null
  "${cmd[@]}" >/dev/null
  echo "started in $dir: $(printf '%q ' "${cmd[@]}")"
}

# 파일 쓰기는 Write가 아니라 Edit 규칙으로 허용해야 Write 도구도 쓸 수 있다.
start_reviewer() {
  local name=$1 pane=$2 worktree=$3 run=${4%/}
  # worktree는 pane을 옮긴 뒤에도 같은 경로를 가리켜야 한다. 실행 기록 폴더는 Edit(//...) 규칙이 돼야 한다. 그래서 둘 다 절대 경로만 받는다.
  case $worktree in /*) ;; *) echo "not_absolute: $worktree" >&2; return 1 ;; esac
  case $run in /*) ;; *) echo "not_absolute: $run" >&2; return 1 ;; esac
  start_in "$name" claude "$pane" "$worktree" \
    --append-system-prompt-file "$worktree/agents/reviewer.md" --add-dir "$run" --permission-mode dontAsk \
    --allowedTools Read Grep Glob Skill 'Bash(git diff *)' 'Bash(git log *)' 'Bash(git status *)' 'Bash(git show *)' \
    "Edit(/$run/**)"
}

# 업데이트 안내 화면이 뜨면 agent start가 agent_not_ready로 멈추므로 시작할 때 확인을 끈다.
start_codex() {
  local name=$1 pane=$2 dir=$3
  shift 3
  [ "${1:-}" = -- ] && shift
  start_in "$name" codex "$pane" "$dir" -c check_for_update_on_startup=false "$@"
}

restart_codex() {
  local name=$1 dir=$2 pane
  shift 2
  [ "${1:-}" = -- ] && shift
  pane=$(field "$name" pane_id)
  [ -n "$pane" ] || { echo "agent_not_found: $name" >&2; return 1; }
  # /exit는 agent prompt가 아니라 pane run으로 보내야 Codex가 끝난다.
  herdr pane run "$pane" "/exit" >/dev/null
  # 검수를 마친 Codex가 15초 대기 뒤에도 Shutting down 화면에 남아 있던 적이 있다.
  for _ in $(seq 1 "${ROLE_EXIT_CHECKS:-90}"); do
    [ -z "$(status "$name")" ] && break
    sleep "${ROLE_POLL_SECONDS:-1}"
  done
  # 아직 살아 있는 Codex에 cd나 배정을 입력하면 그 입력은 이전 회차 폴더에서 실행된다.
  # Codex가 끝나면 이름이 지워져 restart-codex를 다시 쓸 수 없다. 그래서 이어서 실행할 start-codex 명령도 함께 출력된다.
  if [ -n "$(status "$name")" ]; then
    echo "still_running: $name. pane에 입력하지 않는다. herdr agent get $name 이 agent_not_found가 되면 다음을 실행한다:" >&2
    echo "  $(printf '%q ' "$0" start-codex "$name" "$pane" "$dir" -- "$@")" >&2
    return 1
  fi
  start_codex "$name" "$pane" "$dir" "$@"
}

case ${1:-} in
  send) [ $# -eq 3 ] || usage; send "$2" "$3" ;;
  wait) [ $# -ge 2 ] || usage; wait_settled "$2" "${3:-30}" ;;
  approve) [ $# -eq 2 ] || usage; approve "$2" ;;
  clear) [ $# -eq 2 ] || usage; clear_claude "$2" ;;
  start-reviewer) [ $# -eq 5 ] || usage; shift; start_reviewer "$@" ;;
  start-codex) [ $# -ge 4 ] || usage; shift; start_codex "$@" ;;
  restart-codex) [ $# -ge 3 ] || usage; shift; restart_codex "$@" ;;
  *) usage ;;
esac
