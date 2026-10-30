#!/bin/sh
# 측정 스크립트 테스트(check-*.cjs)를 모두 돌린다. Jenkins의 Path Display Timing Checks CI 단계와 로컬이 함께 쓴다.
# 하나가 실패해도 나머지를 돌리고, 하나라도 실패하면 종료 코드 1로 끝난다.
set -eu
cd "$(dirname "$0")/../../.."
total=0
failed=""
for check in infra/k6/path-display-timing/check-*.cjs; do
  total=$((total + 1))
  if node "${check}"; then
    echo "Path display timing check passed: ${check}"
  else
    echo "Path display timing check FAILED: ${check}"
    failed="${failed} ${check}"
  fi
done
echo "Path display timing checks: total=${total}, failed=$(echo ${failed} | wc -w)"
[ -z "${failed}" ] || { echo "Failed path display timing checks:${failed}" >&2; exit 1; }
