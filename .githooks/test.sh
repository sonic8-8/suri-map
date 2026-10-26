#!/bin/sh
# .githooks의 훅을 임시 저장소에서 실행해 통과와 차단을 확인한다. 실행: sh .githooks/test.sh
hooks=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failed=0

# 사례마다 빈 저장소를 새로 만든다.
reset() { rm -rf "$tmp/r"; git init -q "$tmp/r"; }

# 인자로 받은 경로에 파일을 만들어 스테이징한다.
stage() {
  for f; do
    mkdir -p "$tmp/r/$(dirname "$f")"
    echo x > "$tmp/r/$f"
    git -C "$tmp/r" add "$f"
  done
}

# 기대(pass 또는 block), 사례 이름, 메시지를 받아 commit-msg 훅의 결과와 비교한다.
expect() {
  printf '%s\n' "$3" > "$tmp/msg"
  if (cd "$tmp/r" && sh "$hooks/commit-msg" "$tmp/msg") > /dev/null 2>&1; then got=pass; else got=block; fi
  if [ "$got" = "$1" ]; then echo "ok   $2"; else echo "FAIL $2 (기대 $1, 결과 $got)"; failed=1; fi
}

reset; stage docs/a.md
expect pass "문서만 바꾸고 area 없이 docs" "docs: 문서 정리"

reset; stage docs/a.md
expect block "목록에 없는 type" "wip: 문서 정리"

reset; stage docs/a.md
expect block "없는 area" "[Docs] docs: 문서 정리"

reset; stage backend/A.java infra/a.yml
expect block "순서가 틀린 area" "[Infra/BE] fix: 설정 수정"

reset; stage backend/A.java
expect block "backend 코드에 area 없음" "fix: 오류 수정"
expect pass "backend 코드에 BE" "[BE] fix: 오류 수정"

reset; stage backend/AGENTS.md
expect pass "backend 아래 문서만 바꿈" "docs: 지침 정리"

reset; stage mock-112/A.java
expect block "mock-112에 area 없음" "fix: 오류 수정"
expect pass "mock-112에 BE" "[BE] fix: 오류 수정"

reset; stage test-fixtures/a.json
expect block "test-fixtures에 BE만" "[BE] test: 입력 수정"
expect pass "test-fixtures에 BE/Android" "[BE/Android] test: 입력 수정"

reset; stage frontend/a.ts android/A.kt infra/a.yml
expect block "frontend, android, infra에 FE/Android만" "[FE/Android] fix: 오류 수정"
expect pass "frontend, android, infra에 FE/Android/Infra" "[FE/Android/Infra] fix: 오류 수정"

reset; stage backend/Dockerfile
expect pass "area를 더 붙임" "[BE/Infra] fix(build): Docker 빌드 수정"

reset; stage backend/A.java
expect pass "Merge 메시지" "Merge branch 'feature' into develop"
expect pass "Revert 메시지" 'Revert "[BE] fix: 오류 수정"'
expect pass "fixup! 메시지" "fixup! [BE] fix: 오류 수정"
expect pass "squash! 메시지" "squash! [BE] fix: 오류 수정"

reset; stage docs/a.md
expect block "제목의 가운뎃점" "docs: 설계·구현 정리"
expect block "본문의 가운뎃점" "docs: 문서 정리

- 설계·구현 정리"
expect pass "주석 줄의 가운뎃점" "docs: 문서 정리
# 설계·구현"
expect block "Merge 메시지의 가운뎃점" "Merge branch 'a·b'"
expect pass "git commit -v가 붙인 diff의 가운뎃점" "docs: 문서 정리
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
diff --git a/a.md b/a.md
 a·b"

reset; stage "backend/한글.java"
expect block "한글 파일명의 backend 코드에 area 없음" "fix: 오류 수정"

# core.hooksPath로 연결한 훅이 실제 커밋을 막는지 확인한다.
reset; stage backend/A.java
git -C "$tmp/r" config core.hooksPath "$hooks"
if git -C "$tmp/r" -c user.name=t -c user.email=t@t commit -q -m "fix: 오류 수정" > /dev/null 2>&1; then
  echo "FAIL 실제 커밋에서 빠진 area (기대 block, 결과 pass)"; failed=1
else
  echo "ok   실제 커밋에서 빠진 area"
fi
if git -C "$tmp/r" -c user.name=t -c user.email=t@t commit -q -m "[BE] fix: 오류 수정" > /dev/null 2>&1; then
  echo "ok   실제 커밋에서 맞는 area"
else
  echo "FAIL 실제 커밋에서 맞는 area (기대 pass, 결과 block)"; failed=1
fi

exit $failed
