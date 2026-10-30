#!/bin/sh
# 지금 측정 모드(run.cjs)와 가벼운 모드(light-run.cjs)를 같은 자리에서 번갈아 잰다.
# 첫 회차는 데우기(가벼운 모드)라 판정에서 뺀다. 순서: warm F1 L1 F2 L2 ...
set -u

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
  echo "usage: sh $0 CONFIG.json NEW-OUTPUT-ROOT [PAIRS=3]" >&2
  exit 2
fi
config=$1
root=$2
pairs=${3:-3}
dir=$(cd "$(dirname "$0")" && pwd)

case $pairs in ''|*[!0-9]*|0) echo "PAIRS must be a positive integer: $pairs" >&2; exit 2 ;; esac
[ -f "$config" ] || { echo "config not found: $config" >&2; exit 2; }
[ ! -e "$root" ] || { echo "output root already exists: $root" >&2; exit 2; }
mkdir -p "$root" && chmod 700 "$root" || exit 1

names=warm
i=1
while [ "$i" -le "$pairs" ]; do names="$names F$i L$i"; i=$((i + 1)); done

failed=0
for name in $names; do
  case $name in F*) script=run.cjs ;; *) script=light-run.cjs ;; esac
  # 페이지에 기록을 두는 key는 회차마다 새로 정해야 하므로 회차 이름을 붙인다.
  node -e 'const fs=require("fs");const c=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));c.key+=process.argv[3];fs.writeFileSync(process.argv[2],JSON.stringify(c,null,2)+"\n")' \
    "$config" "$root/config-$name.json" "$name" || exit 1
  node "$dir/$script" "$root/config-$name.json" "$root/$name" > "$root/$name.stdout" 2>&1
  rc=$?
  [ "$rc" -eq 0 ] || failed=$((failed + 1))
  echo "$(date -u +%H:%M:%S) $name $script exit=$rc"
done
[ "$failed" -eq 0 ]
