#!/usr/bin/env bash
# role.sh 자기 점검. 가짜 herdr로 상태 변화를 흉내 내므로 Herdr 없이 실행된다.
set -u
here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"
cat >"$work/bin/herdr" <<'EOF'
#!/usr/bin/env bash
# get은 statuses의 첫 줄을 꺼내 돌려준다. 마지막 한 줄은 계속 돌려준다.
f="$FAKE/statuses"
echo "$*" >>"$FAKE/log"
case "$1 $2" in
  "agent get")
    s=$(head -1 "$f")
    [ "$(wc -l <"$f")" -gt 1 ] && sed -i 1d "$f"
    if [ "$s" = gone ]; then echo '{"error":{"code":"agent_not_found"}}'; exit 0; fi
    echo "{\"result\":{\"agent\":{\"agent_status\":\"$s\",\"pane_id\":\"w1:p9\"}}}" ;;
  "agent prompt") cat "$FAKE/prompt_out"; exit "$(cat "$FAKE/prompt_exit")" ;;
  "agent read") cat "$FAKE/screen" ;;
  *) exit 0 ;;
esac
EOF
chmod +x "$work/bin/herdr"
export PATH="$work/bin:$PATH" FAKE="$work" ROLE_SETTLE_SECONDS=0 ROLE_POLL_SECONDS=0 ROLE_START_CHECKS=3
failed=0

case_() { # name, statuses, prompt_exit, prompt_out, expected exit, expected stdout, role.sh args...
  local name=$1 statuses=$2 pexit=$3 pout=$4 want_exit=$5 want_out=$6
  shift 6
  printf '%b\n' "$statuses" >"$work/statuses"
  echo "$pexit" >"$work/prompt_exit"
  printf '%s' "$pout" >"$work/prompt_out"
  : >"$work/log"
  printf '%s' "${SCREEN:-}" >"$work/screen"
  # 기다리는 조건이 틀리면 role.sh가 끝나지 않으므로 경우마다 시간 제한을 둔다.
  out=$(timeout 10 "$here/role.sh" "$@" 2>/dev/null)
  code=$?
  if [ "$code" = "$want_exit" ] && [ "$out" = "$want_out" ]; then echo "PASS $name"; else
    echo "FAIL $name: exit=$code out=$out"; failed=1; fi
}

case_ "send counts a settled prompt as started" "done" 0 '{}' 0 started send a msg
case_ "send counts a prompt still running at its timeout as started" "working" 1 '{"error":{"code":"timeout"}}' 0 started send a msg
case_ "send waits after a stalled prompt until the agent starts" 'idle\nidle\nworking' 1 '{"error":{"code":"agent_prompt_stalled"}}' 0 started send a msg
case_ "send fails when the agent never starts" 'idle' 1 '{"error":{"code":"agent_prompt_stalled"}}' 1 '' send a msg
[ "$(grep -c 'agent prompt' "$work/log")" = 2 ] && ! grep -q 'send-keys' "$work/log" || { echo "FAIL send did not resend exactly once"; failed=1; }
case_ "send resends once when the first message was dropped" 'idle\nidle\nidle\nworking' 1 '{"error":{"code":"agent_prompt_stalled"}}' 0 started send a msg
SCREEN='> msg' case_ "send presses Enter once when the message is left in the input box" 'idle\nidle\nidle\nworking' 1 '{"error":{"code":"agent_prompt_stalled"}}' 0 started send a msg
[ "$(grep -cx 'agent send-keys a enter' "$work/log")" = 1 ] || { echo "FAIL send did not press Enter exactly once"; failed=1; }
SCREEN='> msg' case_ "send fails when Enter does not start the agent" 'idle' 1 '{"error":{"code":"agent_prompt_stalled"}}' 1 '' send a msg
[ "$(grep -c 'agent prompt' "$work/log")" = 1 ] || { echo "FAIL send resent the message"; failed=1; }
case_ "send fails when the prompt is refused" 'blocked' 1 '{"error":{"code":"agent_blocked"}}' 1 '' send a msg
case_ "wait ends on done" 'working\nworking\ndone' 0 '' 0 done wait a
case_ "wait ends on idle without a result file" 'idle' 0 '' 0 idle wait a
case_ "wait fails when the agent is gone" 'gone' 0 '' 1 '' wait a
case_ "approve sends y once and waits again" 'blocked\nworking\nidle' 0 '' 0 idle approve a
grep -qx 'agent send-keys a y' "$work/log" || { echo "FAIL approve did not send y"; failed=1; }
case_ "approve refuses when no approval screen is open" 'working' 0 '' 1 '' approve a
grep -q 'send-keys' "$work/log" && { echo "FAIL approve sent keys without an approval screen"; failed=1; }
codex_start='agent start a --kind codex --pane w1:p9 --timeout 60000 -- -c check_for_update_on_startup=false -s workspace-write'
case_ "restart-codex exits, changes directory and starts again" 'working\ngone' 0 '' 0 "started in /tmp/v x: herdr $codex_start " restart-codex a '/tmp/v x' -- -s workspace-write
grep -qx 'pane run w1:p9 /exit' "$work/log" && grep -qx 'pane run w1:p9 cd /tmp/v\\ x' "$work/log" &&
  grep -qxF "$codex_start" "$work/log" || { echo "FAIL restart-codex commands"; cat "$work/log"; failed=1; }
ROLE_EXIT_CHECKS=3 case_ "restart-codex fails when Codex does not exit" 'working' 0 '' 1 '' restart-codex a /tmp/v -- -s x
grep -q 'pane run w1:p9 cd\|agent start' "$work/log" && { echo "FAIL restart-codex typed into a running Codex"; failed=1; }
hint=$(printf 'working\n' >"$work/statuses"; ROLE_EXIT_CHECKS=1 "$here/role.sh" restart-codex a '/tmp/v x' -- -s x 2>&1 >/dev/null | tail -1)
case $hint in *"start-codex a w1:p9 /tmp/v\\ x -- -s x "*) echo "PASS restart-codex prints the start-codex command to run after Codex exits" ;;
  *) echo "FAIL restart-codex hint: $hint"; failed=1 ;; esac
ROLE_EXIT_CHECKS=5 case_ "restart-codex waits while Codex is shutting down" 'working\nworking\nworking\ngone' 0 '' 0 "started in /tmp/v: herdr $codex_start " restart-codex a /tmp/v -- -s workspace-write
case_ "start-codex starts in the directory without the update prompt" 'idle' 0 '' 0 "started in /tmp/v: herdr agent start a --kind codex --pane w1:p5 --timeout 60000 -- -c check_for_update_on_startup=false -s workspace-write " start-codex a w1:p5 /tmp/v -- -s workspace-write
case_ "start-reviewer allows writes only in the run folder with an Edit rule" 'idle' 0 '' 0 "started in /w: herdr agent start r --kind claude --pane w1:p5 --timeout 60000 -- --append-system-prompt-file /w/agents/reviewer.md --add-dir /run/1 --permission-mode dontAsk --allowedTools Read Grep Glob Skill Bash\\(git\\ diff\\ \\*\\) Bash\\(git\\ log\\ \\*\\) Bash\\(git\\ status\\ \\*\\) Bash\\(git\\ show\\ \\*\\) Edit\\(//run/1/\\*\\*\\) " start-reviewer r w1:p5 /w /run/1/
grep -qx 'pane run w1:p5 cd /w' "$work/log" && grep -q "Edit(//run/1/\\*\\*)\$" "$work/log" && ! grep -q 'Write(' "$work/log" || { echo "FAIL start-reviewer commands"; cat "$work/log"; failed=1; }
case_ "start-reviewer refuses a relative run folder" 'idle' 0 '' 1 '' start-reviewer r w1:p5 /w run/1
case_ "start-reviewer refuses a relative worktree" 'idle' 0 '' 1 '' start-reviewer r w1:p5 w /run/1
grep -q 'agent start' "$work/log" && { echo "FAIL start-reviewer started with a relative run folder"; failed=1; }
exit $failed
