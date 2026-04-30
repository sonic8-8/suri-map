# Lane Tasks 리뷰 가이드

이 문서는 Lane task 리뷰의 세부 기준이다. 진행 상태는 각 Lane 파일의 체크박스를 공식 기록으로 사용한다.

## 기준 문서

- `spec/boundaries.md`: Spec/Lane 소유권, API/Event/Entity/Slot 계약 기준
- `spec/harness-scenarios.md`: 사용자 흐름, SC red test, fixture 기준
- `spec/specs/*.json`: 각 Spec의 실행 가능한 계약, AC, harness fixture 기준

Lane task 정합성의 1차 기준은 위 3개 spec 문서다. `prd.md`, `architecture.md`, `adr.md`, `adr-archive.md`는 배경 문서로만 사용하고, spec 3종과 충돌하거나 범위를 넓히는 근거로 쓰지 않는다.

## 금지 사항

- `spec/boundaries.md`, `spec/harness-scenarios.md`, `spec/specs/*.json`를 tasks 문서에 맞추기 위해 수정하지 않는다.
- 다른 Lane 소유 entity/API/event/board shell/domain write를 자기 Lane task로 가져오지 않는다.
- `FR-23`을 독립 기능 task로 만들지 않는다. 자동 누락 판단·추천 금지 검증 조건으로만 배치한다.
- 기준 문서 수정이 필요해 보이면 작업을 멈추고 사용자에게 사유, 영향 범위, 대안을 보고한다.

## 하네스 루프 1회

하네스 루프의 운영 절차, tester/constructor 역할 분담, handoff 형식의 기준 문서는 [agent-loop-guide.md](./agent-loop-guide.md)다. 이 문서는 task가 그 루프에 맞는 크기인지 리뷰하는 기준만 둔다.

하네스 루프 1회는 한 사람이 1~2일 안에 RED evidence, GREEN 구현 또는 mock contract, 재검증 evidence, MR evidence 대상을 끝낼 수 있는 단위다.

하나의 task가 이 단위를 넘어서면 분할 후보로 본다. 단, `B`, `D`, `T09/T10` 계열은 feature 구현 task가 아니라 gate task일 수 있으므로 별도 규칙을 적용한다.

Phase 1~3 task는 `구현 산출물`에 별도로 쓰여 있지 않더라도 [agent-loop-guide.md](./agent-loop-guide.md)의 RED-first 규칙을 적용한다.

## task 분할 판정

- `3d+` task는 원칙적으로 2개 이상으로 분할한다.
- 예외는 `B` bootstrap gate, `D` demo/release gate, 명시적인 Lane closure task뿐이다.
- `구현 산출물`이 4개 이상이면 소비 Spec, runtime surface, API/event, board slot 중 하나의 축으로 분할한다.
- `2d` task라도 API + DB + event + UI/board + 실패 주입이 동시에 들어가면 분할한다.
- 분할 후 task 하나는 최소 `0.5d`, 보통 `1d~2d` 범위여야 한다.
- 분할로 생긴 task는 각자 `필수 참조`, `구현 산출물`, `예상 작업량`, `완료 기준`을 가져야 한다.

## 특수 task 계열

- `B` 계열: Phase -1 bootstrap gate다. 외부 의존성 셋업, runtime skeleton, mock fixture 1세트까지만 허용하고 feature/domain behavior를 넣지 않는다.
- `T09/T10` 계열: Phase 4 harness gate다. 가능하면 SC 또는 검증 surface 단위로 나눈다.
- `D` 계열: Phase 5 demo/release evidence gate다. 실제 defect 수정은 포함하지 않고, 발견된 결함은 별도 task로 만든다.
- `I` 계열: 통합 검증 task다. 도메인 구현을 새로 추가하지 않고 mock-to-real 교체와 evidence 수집만 다룬다.

## 검증 루프

각 리뷰는 tester가 한 관점만 검토하고, constructor가 해당 관점 지적사항만 반영한 뒤, tester가 같은 관점으로 재검증한다. 한 사이클에서 여러 관점을 동시에 고치지 않는다.

1. Source Fidelity: 기준 문서에 없는 작업이 생겼는지 확인한다.
2. Lane Ownership: Lane 소유권 침범과 중복 구현을 확인한다.
3. Phase & Dependency: Phase 순서와 선행 blocker가 실제 작업 순서와 맞는지 확인한다.
4. Harness Work Unit: task 하나가 한 번의 하네스 루프로 처리 가능한 크기인지 확인한다.
5. Harness Executability: red test, fixture, AC가 실제 실행 가능한지 확인한다.
6. Task Naming: task 이름만 보고 구현할 기능·산출물이 보이는지 확인한다.
7. Cross-Lane: Lane 간 누락, 중복, blocker, fixture 불일치를 확인한다.
8. Documentation Hygiene: 문서 구조, 링크, 라벨, task ID, index/Lane 파일 정합성을 확인한다.
9. Scenario Coverage Trace: SC-01~SC-12의 `then`, `involved_apis`, `e2e_red_test`, `board_merge`, mock fixture가 task ID로 닫히는지 확인한다.
10. Fixture Exactness: fixture ID, status enum, event name, error code, DTO field, tile range/URL이 기준 문서와 정확히 일치하는지 확인한다.

## 검증 체크리스트

### Source Fidelity

- [ ] task의 API, event, entity, error, board slot이 `spec/boundaries.md` 또는 `spec/specs/*.json`에 존재한다.
- [ ] task의 시나리오와 fixture가 `spec/harness-scenarios.md`의 SC 또는 mock 기준과 맞는다.
- [ ] spec 3종에 없는 PRD/architecture/ADR 내용을 근거로 task 범위를 넓히지 않는다.
- [ ] 기준 문서와 충돌하는 경우 task를 고치고, 기준 문서는 사용자 승인 없이 수정하지 않는다.

### Lane Ownership

- [ ] `담당 Spec`이 실제 구현 책임을 가진 Spec만 포함한다.
- [ ] 다른 Lane의 API, entity, event, board shell, domain write를 직접 구현하지 않는다.
- [ ] 다른 Lane 결과가 필요하면 `연관 Spec` 또는 `연관 Lane`으로 표시하고 mock/fixture 소비로 제한한다.
- [ ] `관련 FR`은 담당 Spec의 FR만 적고, 다른 Spec의 FR은 `지원 FR`로 분리한다.

### Phase & Dependency

- [ ] Phase -1은 실행 기반만 다루고 feature/domain behavior를 포함하지 않는다.
- [ ] Phase 0은 계약, fixture, red test 골격을 만들고 실제 feature 완성과 섞이지 않는다.
- [ ] Phase 1~3은 최소 happy path, 도메인 완성도, 통합/실패 경로 순서로 진행된다.
- [ ] Phase 4는 Lane 하네스 closure만 다루고 도메인 구현을 새로 추가하지 않는다.
- [ ] Phase 5는 시연/릴리즈 evidence gate이며, 발견된 결함 수정은 별도 task로 분리한다.
- [ ] 선행 task가 없으면 다음 task를 시작할 수 없는 dependency가 드러난다.

### Harness Work Unit

- [ ] task 하나가 한 사람이 한 번의 하네스 루프로 맡아 구현·검증·증거 첨부까지 끝낼 수 있다.
- [ ] task 하나가 서로 다른 런타임, 저장소, UI/API surface를 과하게 묶지 않는다.
- [ ] `3d+`, 산출물 4개 이상, 또는 2d 복합 surface task는 분할 판정 룰에 따라 처리된다.
- [ ] 통합 검증 task는 도메인 구현 task와 섞지 않고 별도 task로 유지한다.
- [ ] `B`, `D`, `I`, `T09/T10` 계열은 특수 task 계열 룰에 맞는다.

### Harness Executability

- [ ] `완료 기준`이 자동 test, 하네스 실행, 로그, screenshot, MR evidence 중 하나로 증명 가능하다.
- [ ] Phase 1~3 task는 구현 전에 RED test 또는 실패 fixture evidence가 남는다.
- [ ] red test 또는 실패 fixture가 어떤 조건에서 실패해야 하는지 드러난다.
- [ ] mock에서 real contract로 교체하는 기준이 모호하지 않다.
- [ ] 완료 기준이 선언이 아니라 관찰 가능한 결과다.
- [ ] evidence-only gate는 실제 defect 수정과 섞이지 않는다.

### Task Naming

- [ ] task 이름만 보고 구현할 기능 또는 산출물을 이해할 수 있다.
- [ ] `red test`, `skeleton`, `suite`, `closure`처럼 내부 진행 단계만 설명하는 제목을 쓰지 않는다.
- [ ] `준비`, `검증`, `구현`만 있고 대상 산출물이 모호한 제목은 구체화한다.
- [ ] 제목에 여러 기능이 병렬로 나열되면 분할 후보로 표시하되, 실제 분할 판단은 Harness Work Unit 리뷰에서 확정한다.
- [ ] 영어 약어는 Spec ID, SC ID, FR ID, API path, event name처럼 계약 식별자인 경우에만 유지한다.

### Cross-Lane

- [ ] 같은 API, event, entity, board slot을 두 Lane이 중복 구현하지 않는다.
- [ ] SC final PASS에 필요한 Lane blocker가 누락되지 않는다.
- [ ] fixture ID, event name, error code, DTO 필드 이름이 Lane 간 불일치하지 않는다.
- [ ] 한 Lane task 완료가 다른 Lane의 mock/fixture 교체 시점을 막지 않는다.

### Documentation Hygiene

- [ ] 모든 task ID가 Lane 파일 안에서 중복되지 않는다.
- [ ] 모든 task가 `필수 참조`, `구현 산출물`, `예상 작업량`, `완료 기준`을 가진다.
- [ ] `tasks/index.md`의 Lane/Spec/SC 요약과 각 Lane 파일의 실제 task가 어긋나지 않는다.
- [ ] markdown 링크와 상대 경로가 실제 파일을 가리킨다.
- [ ] 한국어 라벨과 영어 계약 식별자 사용 규칙이 일관적이다.

### Scenario Coverage Trace

- [ ] `spec/harness-scenarios.md`의 SC-01~SC-12 `then` 항목이 하나 이상의 task `구현 산출물` 또는 `완료 기준`으로 닫힌다.
- [ ] 각 SC의 `involved_apis`와 event가 담당 Lane task에 존재하고, 통합 task의 `선행 task`에 필요한 blocker가 드러난다.
- [ ] 각 SC의 `e2e_red_test`가 domain task, harness task, integration task, demo/evidence gate 중 하나에 매핑된다.
- [ ] 각 SC의 `board_merge` slot은 S3-2 task와 통합 task에서 snapshot/SSE 수렴 evidence로 확인된다.
- [ ] SC final PASS에 필요한 FCM, SSE, outbox, board snapshot, mock fixture 실패 주입이 누락되지 않는다.

### Fixture Exactness

- [ ] fixture ID, account/device/team ID, event name, payload field, error code가 기준 문서와 문자열 단위로 일치한다.
- [ ] 상태 enum은 `spec/specs/*.json` 또는 `spec/harness-scenarios.md`에 있는 값을 그대로 사용한다.
- [ ] tile manifest ID, zoom range, tile URL/URI, object storage URI, mock endpoint 값이 기준 문서와 일치한다.
- [ ] 기준 문서끼리 값이 충돌하면 tasks에서 임의로 선택하지 않고 사용자에게 충돌 내용과 선택지를 보고한다.
- [ ] fixture exactness가 필요한 task는 완료 기준에 fixture 검증 evidence를 포함한다.

## 평가 루프

검증 루프가 끝난 뒤 아래 3개만 적용한다.

### 실행 가능성

- [ ] Lane owner가 자기 Lane 파일을 받고 30분 안에 첫 RED test 또는 fixture 작업을 시작할 수 있다.
- [ ] 각 task의 `필수 참조`와 `구현 산출물`이 작업 시작에 충분하다.
- [ ] 외부 의존 mock, fixture, blocker가 task 안에서 보인다.

### 인지 부담

- [ ] 한 task 본문이 과하게 길지 않고 핵심 필드가 묻히지 않는다.
- [ ] Lane 파일 하나를 읽고 자기 책임 범위와 하지 말아야 할 범위를 빠르게 파악할 수 있다.
- [ ] index.md와 Lane 파일 사이에 불필요한 중복 설명이 많지 않다.

### 소비자 적합성

- [ ] Lane owner가 작업 배정과 진행 상태를 파악하기 쉽다.
- [ ] 에이전트가 task ID, Spec ID, SC ID, FR ID, 완료 기준을 구조적으로 읽기 쉽다.
- [ ] 리뷰어가 MR evidence와 task 완료 기준을 연결하기 쉽다.
