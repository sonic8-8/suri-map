# AGENTS 컨벤션 가드레일 분류표

이 문서는 `AGENTS.md` 규칙 중 자동화로 강제할 수 있는 것과 리뷰로 검증해야 하는 것을 나눈다.

| 구분 | 예시 | 1차 강제 수단 | 남는 검증 |
|---|---|---|---|
| 코드 포맷 | Java indent, Kotlin style, TS/React formatting | 로컬 Spotless/ktlint/ESLint/Prettier, agent hook quickcheck | 없음. formatter diff 확인 |
| 타입 안정성 | TypeScript `any` 사용 | ESLint `@typescript-eslint/no-explicit-any` | 타입 우회 패턴 리뷰 |
| 위험 명령 | `rm -rf`, `git push --force`, production data command | `.agents/scripts/guard-bash-command.py` + Codex/Claude hook wrapper | 우회 환경변수 사용 사유 리뷰 |
| fixture 계약 | `harness-scenarios.md §6` fixture ID 변경 | `check-fixture-contract.py`, CODEOWNERS | owner LGTM, 통합 SC 영향 리뷰 |
| Lane 경계 | 다른 Lane entity/API/event/slot 변경 | CODEOWNERS, MR template | owner LGTM, 관련 Spec 영향 리뷰 |
| API 계약 | URL, request/response, error body | API spec diff review, 로컬 test | `docs/api/api-spec.md` 기준 수동 확인 |
| Persistence 규칙 | MyBatis 기준, JPA 도입 금지 | dependency/code search, reviewer prompt | 예외 ADR 존재 여부 리뷰 |
| Transaction Rule | `boundaries.md §4.3` 9단계 순서 | reviewer prompt | 테스트가 순서/rollback을 증명하는지 리뷰 |
| 채널 경계 | Android app write, Web command 분리 | reviewer prompt, MR template | 화면/API 흐름 의미 검증 |
| 자동 판단 금지 | 위험도 판단, 다음 구역 추천, 확인 누락 확정 | reviewer prompt | UX 문구와 API 의미 리뷰 |

## 운영 원칙

- 자동화는 형식·위험 명령·명확한 계약 파일 변경을 먼저 막는다.
- 의미 규칙은 reviewer subagent와 CODEOWNERS 리뷰로 확인한다.
- Hook을 쓰지 않는 사용자도 MR template의 검증 명령 기록과 CODEOWNERS review로 같은 최종 게이트를 통과해야 한다.
- GitLab CI는 runner 대기 때문에 merge 병목이 되므로 현재 가드레일에서 제외한다. 재도입 시에는 path-based job으로만 추가한다.
- `quickcheck-on-stop.sh`는 빠른 smoke check다. 전체 플랫폼 검증이 필요하면 `AGENT_QUICKCHECK_FULL=1`로 실행하거나 AGENTS.md의 명령을 직접 실행한다.
- Codex/Claude hook payload는 런타임별 adapter 계약이다. CLI version이나 hook matcher를 바꾸면 stdin sample을 확인한 뒤 parser를 갱신한다.

## Q8 Outcome Contract 검증 기록

검증 범위는 현재 도입된 공통 core script와 Codex/Claude hook wrapper의 동일 outcome이다. 모델별 자연어 출력 동일성은 검증 대상이 아니며, exit code와 guardrail decision이 같은지를 본다.

| 항목 | 검증 결과 | 증거 |
|---|---|---|
| 기대 파일 생성·수정 여부 | PASS | 현재 검증 대상인 hook wrapper 입력에서는 파일 생성이 기대되지 않는다. no-op post-edit 입력에서 Codex/Claude 모두 파일 변경 없음 |
| 금지 파일 미수정 | PASS | 검증 전후 `git status --short`에서 기존 untracked 피드백 문서 외 새 변경 없음 |
| 테스트 exit code 일치 | PASS | Codex/Claude `pre_tool_use_guard.py`, `post_edit_format.py`, `stop_quickcheck.py` wrapper 모두 동일 입력에서 `rc=0` |
| destructive command 차단 | PASS | `git reset --hard` payload에 대해 Codex/Claude wrapper 모두 `permissionDecision=deny` 반환 |
| fixture ID 변경 감지 | PASS | `python3 .agents/scripts/check-fixture-contract.py` `rc=0`; 변경 없음 기준 통과 |
| quickcheck smoke | PASS | `bash .agents/scripts/quickcheck-on-stop.sh` `rc=0` |
| RED/GREEN/VERIFY 단계 보존 | PASS | `.agents/workflows/tdd-loop-flow.md`와 `.agents/skills/tdd-loop/SKILL.md`가 RED -> GREEN -> VERIFY -> REVIEW 순서를 단일 shared workflow로 고정 |
| diff 책임 범위 | PASS | `.agents/workflows/jira-gitlab-flow.md`가 Jira/TODO/Lane task 범위 제한과 커밋 전 `git status --short` 확인을 요구 |
| escalate 조건 동일 | PASS | Codex/Claude wrapper는 공통 `.agents/scripts/`를 호출하며, tool별 차이는 hook 응답 포맷 변환에만 둔다 |
| owner 침범 없음 | PASS | 검증 실행 중 제품 코드, spec, fixture 파일 변경 없음. owner review는 CODEOWNERS와 MR template에서 최종 확인 |

실행 명령:

```bash
printf '%s' '{"tool_input":{"cmd":"git status --short"}}' | .codex/hooks/pre_tool_use_guard.py
printf '%s' '{"tool_input":{"cmd":"git status --short"}}' | .claude/hooks/pre_tool_use_guard.py
printf '%s' '{"tool_input":{"cmd":"git reset --hard"}}' | .codex/hooks/pre_tool_use_guard.py
printf '%s' '{"tool_input":{"cmd":"git reset --hard"}}' | .claude/hooks/pre_tool_use_guard.py
printf '%s' '{}' | .codex/hooks/post_edit_format.py
printf '%s' '{}' | .claude/hooks/post_edit_format.py
AGENT_QUICKCHECK_SKIP=1 .codex/hooks/stop_quickcheck.py
AGENT_QUICKCHECK_SKIP=1 .claude/hooks/stop_quickcheck.py
python3 .agents/scripts/check-fixture-contract.py
bash .agents/scripts/quickcheck-on-stop.sh
```

Hook 미사용자는 현재 GitLab CI가 아닌 MR template 검증 기록과 CODEOWNERS review로 같은 최종 게이트를 통과한다. GitLab CI 재도입은 runner 병목이 해소된 뒤 path-based job으로 별도 진행한다.
