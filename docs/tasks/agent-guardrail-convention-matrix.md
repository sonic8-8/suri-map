# AGENTS 컨벤션 가드레일 분류표

이 문서는 `AGENTS.md` 규칙 중 자동화로 강제할 수 있는 것과 리뷰로 검증해야 하는 것을 나눈다.

| 구분 | 예시 | 1차 강제 수단 | 남는 검증 |
|---|---|---|---|
| 코드 포맷 | Java indent, Kotlin style, TS/React formatting | Spotless, ktlint, ESLint, Prettier, CI lint job | 없음. formatter diff 확인 |
| 타입 안정성 | TypeScript `any` 사용 | ESLint `@typescript-eslint/no-explicit-any` | 타입 우회 패턴 리뷰 |
| 위험 명령 | `rm -rf`, `git push --force`, production data command | `.agents/scripts/guard-bash-command.py` + Codex/Claude hook wrapper | 우회 환경변수 사용 사유 리뷰 |
| fixture 계약 | `harness-scenarios.md §6` fixture ID 변경 | `check-fixture-contract.py`, CODEOWNERS | owner LGTM, 통합 SC 영향 리뷰 |
| Lane 경계 | 다른 Lane entity/API/event/slot 변경 | CODEOWNERS, MR template | owner LGTM, 관련 Spec 영향 리뷰 |
| API 계약 | URL, request/response, error body | API spec diff review, CI test | `docs/api/api-spec.md` 기준 수동 확인 |
| Persistence 규칙 | MyBatis 기준, JPA 도입 금지 | dependency/code search, reviewer prompt | 예외 ADR 존재 여부 리뷰 |
| Transaction Rule | `boundaries.md §4.3` 9단계 순서 | reviewer prompt | 테스트가 순서/rollback을 증명하는지 리뷰 |
| 채널 경계 | Android app write, Web command 분리 | reviewer prompt, MR template | 화면/API 흐름 의미 검증 |
| 자동 판단 금지 | 위험도 판단, 다음 구역 추천, 확인 누락 확정 | reviewer prompt | UX 문구와 API 의미 리뷰 |

## 운영 원칙

- 자동화는 형식·위험 명령·명확한 계약 파일 변경을 먼저 막는다.
- 의미 규칙은 reviewer subagent와 CODEOWNERS 리뷰로 확인한다.
- Hook을 쓰지 않는 사용자도 CI lint job, MR template, CODEOWNERS review로 같은 최종 게이트를 통과해야 한다.
- `quickcheck-on-stop.sh`는 빠른 smoke check다. 전체 플랫폼 검증이 필요하면 `AGENT_QUICKCHECK_FULL=1`로 실행하거나 AGENTS.md의 명령을 직접 실행한다.
- Codex/Claude hook payload는 런타임별 adapter 계약이다. CLI version이나 hook matcher를 바꾸면 stdin sample을 확인한 뒤 parser를 갱신한다.
