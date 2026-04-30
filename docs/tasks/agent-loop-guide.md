# Agentic TDD Loop Guide

이 문서는 Lane task를 tester/constructor 에이전트 루프로 실행하는 기준이다. 제품 계약의 1차 기준은 계속 `spec/boundaries.md`, `spec/harness-scenarios.md`, `spec/specs/*.json`이며, 이 문서는 그 계약을 구현하는 작업 방식만 고정한다.

## 기본 원칙

- 한 cycle은 한 task의 한 산출물 또는 한 red test 관점만 다룬다.
- tester는 실패 조건과 관찰 지점을 먼저 고정한다.
- constructor는 tester가 만든 RED test 또는 실패 fixture를 GREEN으로 만들 만큼만 구현한다.
- tester는 GREEN 후 같은 관점으로 재검증한다. 새 관점이 보이면 다음 cycle로 분리한다.
- 기준 문서 변경이 필요해 보이면 cycle을 멈추고 영향 범위와 대안을 먼저 보고한다.

## 역할

| 역할 | 책임 | 산출물 |
|---|---|---|
| tester | 기준 문서에서 task의 관찰 가능한 실패 조건을 뽑고 RED test 또는 실패 fixture를 작성한다. | failing test, fixture, expected failure log, AC/SC/task ID 매핑 |
| constructor | RED test를 통과시키는 최소 구현 또는 mock contract를 작성한다. | implementation, mock adapter, migration, UI state, passing test result |
| tester 재검증 | 같은 관점으로 GREEN 결과를 확인하고 evidence를 정리한다. | pass/fail result, regression note, MR evidence 대상 |
| Lane owner | 자기 Lane 밖으로 범위가 번졌는지 확인하고 cross-lane blocker를 남긴다. | LGTM, blocker note, follow-up task |

tester와 constructor는 같은 task 안에서도 분리된 책임으로 동작한다. 같은 사람이 수행해도 산출물 순서는 RED -> GREEN -> 재검증을 유지한다.

## Cycle 절차

1. Intake
   - `tasks/index.md`와 자기 Lane 파일을 읽는다.
   - task의 `담당 Spec`, `필수 참조`, `시나리오`, `구현 산출물`, `완료 기준`을 확인한다.
   - task 본문에 RED 산출물이 반복 기재되어 있지 않아도, 매 cycle 시작 시 이 문서의 RED -> GREEN -> 재검증 순서를 먼저 적용한다.
   - 다른 Lane 구현이 필요하면 실제 구현이 아니라 `provides` mock/fixture로 시작한다.

2. RED
   - tester가 기준 문서의 API, event, entity, error, board slot, fixture ID를 그대로 사용해 실패 test 또는 실패 fixture를 만든다.
   - 실패는 의도한 이유로 실패해야 한다. 환경 미기동, fixture 누락, import 오류 같은 준비 실패는 RED로 보지 않는다.
   - Phase 1~3 task는 구현 전에 최소 하나의 RED test 또는 실패 fixture evidence를 남긴다.

3. GREEN
   - constructor는 RED를 통과시키는 최소 구현을 한다.
   - 관련 없는 refactor, 다른 Lane 소유 파일 수정, 기준 문서에 없는 API/event/entity 추가는 하지 않는다.
   - mock contract로 GREEN을 만든 경우, mock-to-real 교체 조건을 evidence에 남긴다.

4. 재검증
   - tester는 처음 작성한 RED 관점으로 다시 실행한다.
   - 같은 task에서 새 실패 관점이 발견되면 기존 cycle을 확장하지 않고 아래 기록 규칙에 따라 follow-up cycle 또는 blocker로 남긴다.
   - 완료 기준이 자동 test, 하네스 로그, screenshot, MR evidence 중 하나로 증명되어야 체크박스를 완료할 수 있다.

## Follow-up과 blocker 기록

- 같은 `담당 Spec`과 같은 `완료 기준` 안에서 닫을 수 있는 새 실패 관점은 같은 task의 evidence 또는 blocker note에 follow-up cycle로 기록한다.
- task의 `완료 기준`을 넘거나 다른 runtime/surface를 요구하는 실패는 새 task ID 또는 Lane owner follow-up으로 분리한다.
- 다른 Lane 구현이 필요한 실패는 owner Lane, 관련 Spec, SC ID, fixture ID를 포함한 cross-lane blocker로 남긴다.
- 기준 문서 변경이 필요한 실패는 task 안에서 임의 수정하지 않고 cycle을 멈춘 뒤 영향 범위와 대안을 보고한다.

## Phase별 적용

| Phase | 루프 적용 |
|---|---|
| Phase -1 | 런타임·테스트 기반·mock fixture 기동 확인이 GREEN이다. feature/domain behavior는 넣지 않는다. |
| Phase 0 | tester 중심이다. mock contract, fixture, RED test 골격을 만들고 실제 feature 완성은 하지 않는다. |
| Phase 1~3 | 모든 task는 RED test 또는 실패 fixture를 먼저 만든 뒤 GREEN 구현으로 닫는다. |
| Phase 4 | Lane 하네스 closure다. 새 도메인 구현 없이 mock-to-real 교체와 수렴 evidence를 검증한다. |
| Phase 5 | 시연·릴리즈 evidence gate다. 결함 수정은 포함하지 않고 별도 fix task로 분리한다. |

## Handoff Template

tester가 constructor에게 넘길 때 아래 항목을 남긴다.

```markdown
Task:
Spec:
Scenario:
RED evidence:
Expected failure:
Fixture IDs:
Allowed files/surface:
Forbidden ownership:
Green target:
```

constructor가 tester에게 돌려줄 때 아래 항목을 남긴다.

```markdown
Task:
Changed files:
Implementation summary:
Test command/result:
Remaining blocker:
Mock-to-real swap note:
```

## 완료 기준

task 체크박스는 Lane 파일의 체크박스를 공식 진행 상태로 사용한다. 체크는 아래 조건을 모두 만족할 때만 한다.

- task의 `완료 기준`을 관찰 가능한 evidence로 증명했다.
- RED test 또는 실패 fixture가 먼저 있었고, GREEN 후 같은 관점으로 재검증했다.
- 변경 범위가 `담당 Spec`에 머물렀다.
- cross-lane mock을 썼다면 mock-to-real 교체 기준이 남아 있다.
- 실패하거나 보류된 항목은 별도 blocker 또는 follow-up task로 연결했다.
