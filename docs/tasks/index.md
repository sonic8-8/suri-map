# Suri-Map Lane 작업 인덱스

상태: 초안. 이 문서는 전체 인덱스, 공통 규칙, Phase 모델, Lane 간 통합 책임만 다룬다. 실제 개발 작업은 Lane별 파일에서 수행한다.

## 기준 문서

- `spec/boundaries.md`
- `spec/harness-scenarios.md`
- `spec/specs/*.json`

Lane task 정합성의 1차 기준은 위 3개 spec 문서다. `prd.md`, `architecture.md`, `adr.md`는 배경 문서로 참고하되, tasks를 확정할 때 public API, event, entity, error, annotation, board slot, SC red test, fixture 값은 `spec/boundaries.md`, `spec/harness-scenarios.md`, `spec/specs/*.json`를 우선한다.

과거 단일 마스터 초안과 파생 뷰/노트는 혼선을 줄이기 위해 제거했다. 기준 문서에 없는 public API, event, entity, error, annotation, board slot은 task로 만들지 않는다. 기준 문서 수정이 필요하면 먼저 보고한다.

## 운영 보조 문서

- [agent-loop-guide.md](./agent-loop-guide.md): tester/constructor 에이전틱 TDD 루프, RED -> GREEN -> 재검증 산출물 기준
- [review-guide.md](./review-guide.md): Lane task 리뷰 관점, 분할 판정, fixture exactness 체크리스트

## Lane 파일

| Lane | 파일 | 담당 Spec | 주요 하네스 |
|---|---|---|---|
| L1 | [L1-tasks.md](./L1-tasks.md) | S1-1 | SC-01, SC-02, SC-12 |
| L2 | [L2-tasks.md](./L2-tasks.md) | S1-2, S1-3, S4 | auth/policePhone, event hub, purge |
| L3 | [L3-tasks.md](./L3-tasks.md) | S2, S8 | SC-04, SC-10, SC-11 backend |
| L4 | [L4-tasks.md](./L4-tasks.md) | S3-1, S6 | SC-05, SC-07, SC-09 |
| L5 | [L5-tasks.md](./L5-tasks.md) | S5 | SC-06, SC-08 |
| L6 | [L6-tasks.md](./L6-tasks.md) | S3-2, S7 | SC-03, SC-11, board convergence |

## 에이전트 작업 규칙

1. 먼저 이 인덱스를 읽고, 그다음 자기 Lane 파일과 참조된 기준 Spec만 읽는다.
2. tester/constructor 분리 루프는 [agent-loop-guide.md](./agent-loop-guide.md)를 따른다. 각 cycle 시작 시 해당 문서의 RED -> GREEN -> 재검증 순서를 적용하고, Phase 1~3 task는 RED test 또는 실패 fixture를 먼저 만들고 GREEN 구현으로 닫는다.
3. Phase 순서대로 진행한다. `Phase -1` 부트스트랩이 먼저 준비되어야 `Phase 0` 계약/fixture/red test 작업을 시작할 수 있고, `Phase 5`는 MR 또는 릴리즈 노트에 통합 증거가 붙어야 완료로 본다.
4. `담당 Spec` 범위만 구현한다. `연관 Spec`은 소비 계약, mock, 통합 blocker로 취급하고, 구현 범위를 넓히려면 해당 Lane 승인을 받는다.
5. 각 Lane 파일의 체크박스를 공식 진행 상태로 사용한다. 체크 조건은 [agent-loop-guide.md](./agent-loop-guide.md)의 `완료 기준`을 따르며, task의 `완료 기준`이 red test, 하네스 fixture, 관찰 가능한 UI/API 결과로 증명될 때만 체크한다.
6. 작업 중 `spec/boundaries.md` 또는 `spec/harness-scenarios.md` 수정이 필요해 보이면 즉시 멈추고, 수정 이유와 영향을 먼저 보고한다.

## 전달 흐름

1. 구현 전에 Jira issue를 만들거나 기존 issue를 확인한다.
2. Jira key와 구현할 Lane task ID를 연결한다.
3. 별도 작업공간이 필요하면 Jira key가 드러나는 repository, fork, worktree를 만든다.
4. 올바른 base branch에서 Git Flow branch를 만든다.
5. 할당된 task 범위만 구현하고 commit을 Jira issue와 연결한다.
6. MR을 열기 전에 변경한 Lane과 관련된 test/harness를 확인한다.
7. GitLab MR에는 Jira key, 영향 area tag, 완료한 task ID, test 결과, cross-lane 영향을 적는다.
8. MR에 task `완료 기준`과 [agent-loop-guide.md](./agent-loop-guide.md)의 체크 조건 증거가 없으면 task checkbox를 완료 처리하지 않는다.

Jira/GitLab 작업 도구는 `acli`와 `glab`을 전제로 한다. 이 문서는 필요한 산출물과 이름 규칙만 고정하고, 정확한 CLI 명령어는 별도 script 또는 runbook에 둔다.

## 예상 작업량 기준

예상 작업량은 일부러 거칠게 잡는다. `0.5d`, `1d`, `2d`, `3d+`만 사용한다.

| 값 | 의미 |
|---|---|
| `0.5d` | fixture, adapter, 문서, 좁은 red test |
| `1d` | 작은 vertical slice 또는 독립 계약 구현 |
| `2d` | 여러 계층을 건드리는 구현과 test |
| `3d+` | Lane 간 통합, runtime 기반, demo/release gate |

예상 작업량은 계획용 정보일 뿐 완료 신호가 아니다. checkbox 완료 여부는 `완료 기준`으로만 판단한다.

## Task 필드 규칙

각 Lane task는 구현자가 추측하지 않도록 아래 필드를 유지한다.

체크박스 제목은 `Lane-ID + 구현할 기능/산출물 + 동사` 형태로 쓴다. `red tests`, `skeleton`, `suite`, `closure`처럼 내부 진행 단계만 말하는 제목은 쓰지 않는다. 테스트 단계나 하네스 방식은 제목이 아니라 `구현 산출물`과 `완료 기준`에 적는다.

| 필드 | 의미 |
|---|---|
| `담당 Spec` | 직접 구현 책임을 지는 Spec. 이 범위 밖 entity/API/event/slot은 구현하지 않는다. |
| `필수 참조` | 구현 전에 반드시 읽을 기준 문서와 섹션. tasks는 spec 내용을 복사하지 않고 참조 위치만 고정한다. |
| `연관 Spec` / `연관 Lane` | 소비해야 하는 계약, mock, 통합 blocker. 직접 구현 범위가 아니다. |
| `시나리오` | 이 task가 닫아야 하는 harness scenario 또는 공통 경로. |
| `관련 FR` | 담당 Spec에 mapping된 FR. 다른 Spec의 FR은 `지원 FR`로 적는다. |
| `구현 산출물` | MR에 실제로 포함되어야 하는 코드, test, fixture, 문서 산출물. |
| `완료 기준` | checkbox를 완료 처리할 수 있는 관찰 가능 조건. |

`필수 참조`와 `구현 산출물`이 비어 있으면 task를 구현 가능한 상태로 보지 않는다.

`관련 FR`은 기능 구현 task에만 필수다. bootstrap, harness, integration, demo/release gate, 순수 contract/fixture task처럼 특정 FR을 직접 구현하지 않는 task는 `관련 FR`을 생략할 수 있으며, 이 경우 FR 누락이 아니라 비대상으로 본다. 다른 Spec의 FR을 지원만 하면 `지원 FR`로 적고 담당 범위로 해석하지 않는다.

## 브랜치 규칙

Git Flow branch prefix를 사용한다.

| 목적 | 형식 | 예시 |
|---|---|---|
| 기능 개발 | `feature/<JIRA-KEY>-<scope-slug>` | `feature/SURI-123-incident-import` |
| 릴리즈 전 버그 수정 | `fix/<JIRA-KEY>-<scope-slug>` | `fix/SURI-124-sync-replay` |
| 릴리즈 branch | `release/<version>` | `release/0.1.0` |
| 운영 hotfix | `hotfix/<JIRA-KEY>-<scope-slug>` | `hotfix/SURI-125-event-replay` |

작업 repository/worktree 이름은 물리 작업공간 식별에 사용한다. branch slug에는 repository 이름만 쓰지 말고 기능 또는 버그 범위가 드러나게 쓴다.

## 커밋 및 MR 규칙

Angular-style Conventional Commits에 area tag와 Jira issue suffix를 붙인다.

형식:

`[Area] type(scope): 한글 요약 (<JIRA-KEY>)`

허용 area tag:

| Tag | 사용할 때 |
|---|---|
| `[BE]` | backend Spring Boot, DB migration, backend test |
| `[FE]` | Web 상황판, React, Web MapLibre UI |
| `[Android]` | Android app, Room/Outbox/WorkManager, MapLibre Native |
| `[Infra]` | Docker, CI/CD, 배포, tile server 운영, observability |
| `[Docs]` | ADR, architecture, spec, task, workflow 문서 |

하나의 MR이 실제로 여러 runtime area를 건드리면 제목 앞에 `[BE][FE][Android][Infra]` 순서로 여러 tag를 붙인다. 순수 문서·계약 변경은 `[Docs]` 하나만 사용한다.

계약 또는 문서 변경은 별도 `[Contract]`, `[Spec]` tag를 만들지 않고 `[Docs]`를 사용한다. 실제 Docker, CI/CD, 배포, observability 설정 변경은 `[Infra]`를 사용한다.

허용 type:

`feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`

허용 scope:

`incident`, `auth`, `police_phone`, `retention`, `event`, `overall_search_area`, `area`, `path`, `sync`, `marker`, `photo`, `notification`, `board`, `package`, `tiles`, `op`, `handover`, `search_history_summary`, `contract`, `infra`, `docs`

예시:

- `[BE] feat(incident): 배정 사건 가져오기 API 추가 (SURI-123)`
- `[FE] fix(board): 단말 최신성 표시 상태 정렬 (SURI-124)`
- `[Android] test(sync): 오프라인 재전송 하네스 추가 (SURI-125)`
- `[Infra] chore(tiles): 로컬 타일 서버 설정 정리 (SURI-126)`
- `[BE][FE] refactor(contract): BaseEvent 스키마 필드명 정렬 (SURI-127)`
- `[Docs] docs(tasks): Lane 작업 규칙 보강 (SURI-128)`

MR 제목은 commit 제목과 같은 형식을 사용한다. MR 설명에는 Jira key, 완료한 task ID, 수정한 기준 문서, 검증 명령/결과, 영향받는 Lane을 적는다. `spec/boundaries.md` 또는 `spec/harness-scenarios.md` 수정이 필요하면 편집 전에 보고하고, 승인된 변경 내용을 MR에 남긴다.

## Phase 모델

| Phase | 목표 | 규칙 |
|---|---|---|
| Phase -1 | 프로젝트 부트스트랩 | 개발 서버, DB, 앱/웹/백엔드 기본 실행, CI skeleton, local tile/mock infra처럼 모든 Lane을 막는 실행 기반을 먼저 만든다. |
| Phase 0 | 계약·fixture·red test 골격 | 다른 Lane 없이 mock으로 실패 테스트를 먼저 만들 수 있어야 한다. |
| Phase 1 | 최소 happy path | 가장 작은 end-to-end 흐름을 통과시키되 예외·재시도·board API refetch lag는 아직 최소화한다. |
| Phase 2 | 도메인 완성도 | 권한, 상태 전이, 버전, 이력, idempotency 등 핵심 규칙을 붙인다. |
| Phase 3 | 통합·오프라인·종료·실패 경로 | S4 event, S3-2 board, S6 offline, purge/stale/failure injection을 붙인다. |
| Phase 4 | Lane 하네스 closure | Lane 단독 PASS 후 관련 SC 최종 PASS blocker를 제거한다. |
| Phase 5 | 시연·릴리즈 closure | PRD §2.2/§2.3 안정성·12단계 시범 시나리오·k6/CI gate·장애 대응 체크리스트를 통합 증거로 닫는다. |

## 프로젝트 부트스트랩 책임

| 영역 | 담당 | 기대 산출물 |
|---|---|---|
| Backend runtime과 migration runner | L2 | Spring Boot base, PostgreSQL migration runner, MyBatis configuration, auth/event/purge test skeleton |
| 공간 DB와 geometry fixture | L3 | PostGIS extension 전제 확인, MyBatis geometry TypeHandler 검증, canonical overall_search_area/area fixture |
| Android runtime과 offline local test harness | L4 | Android project base, Room/WorkManager deterministic test harness |
| Object storage와 FCM mock adapter | L5 | MinIO-compatible dev adapter, mock object storage/presign endpoint, FCM dispatcher fixture |
| Web, MapLibre, tileserver, compose entrypoint | L6 | React/MapLibre base, local `/tiles` route, Docker Compose service map |
| Seed/demo incident data | L1 | 모든 Lane이 사용할 수 있는 mock·seed incident import data |

이 표는 새 제품 Spec이 아니라 task 책임 분배다. 부트스트랩 항목 때문에 `spec/boundaries.md` 또는 `spec/harness-scenarios.md` 수정이 필요하면 편집 전에 보고한다.

## Phase -1 -> Phase 0 진입 그래프

| 선행 task | 완료 후 시작 가능한 Phase 0 | 비고 |
|---|---|---|
| `L1-B01` | L1 Phase 0, L3/L5/L6의 incident fixture 소비 test | 공용 mock·seed incident ID와 incident_assignment seed가 고정되어야 한다. |
| `L2-B01` | L1/L2/L3/L5 backend Phase 0 | Spring Boot test profile, migration runner, MyBatis mapper scan이 준비되어야 backend red test를 안정적으로 실행한다. |
| `L2-B02` | 모든 backend API guard red test | auth/security filter baseline이 없으면 각 Lane은 L2 mock guard로만 시작한다. |
| `L2-B03A` | 모든 event-producing Phase 0 | real event_dispatch_job 전에는 mock publish hook으로 event RED test를 시작한다. |
| `L2-B03B` | L1/L4/L5/L6 close/purge Phase 0 | real purge 전에는 mock purge hook registry로 SC-12 RED test를 시작한다. |
| `L3-B01` | L3 Phase 0, L4/L5 geometry validation RED test, L6 package/board geometry fixture | PostGIS geometry mapping과 canonical bbox/polygon fixture가 준비되어야 geometry exactness를 맞출 수 있다. |
| `L4-B01` | L4 Phase 0, Android local/offline RED test | Android runtime 없이 backend-only mock으로 시작한 task는 Android evidence 전까지 완료하지 않는다. |
| `L5-B01A` | L5 photo Phase 0, L6 package fixture의 initial marker/photo 참조 test | 실제 S3 없이 MinIO-compatible dev adapter와 mock object storage로 시작한다. |
| `L5-B01B` | L5 notification Phase 0, L1/L2/L6 FCM evidence fixture | 실제 FCM 없이 mock dispatcher로 시작한다. |
| `L6-B01` | L6 Phase 0, board shell/slot/tile fixture RED test | React/MapLibre base와 compose service map이 준비되어야 board evidence를 수집한다. |

## 시연 및 릴리즈 종료 책임

| Gate | 담당 | 협업 Lane | 필요한 증거 |
|---|---|---|---|
| 12단계 리허설 | L1 | 전체 Lane | `L1-D01A~D`로 SC-01~12를 나눠 PRD §5.1 흐름이 fixture ID로 1회 통과하고 실패/수정 기록이 남는다 |
| k6/CI quality gate | L2 | L4, L6 | Sonar/test/coverage/k6 결과가 MR 또는 release note에 첨부된다 |
| 1시간 안정성 및 on/off 10회 테스트 | L4 | L2, L5, L6 | Android 단말 2대와 board 1대가 네트워크 반복 변경 후에도 정합성을 유지한다 |
| Board/package/tile demo smoke | L6 | L2, L3, L5 | board slot, package badge, local tile, terminal state가 수렴한다 |
| Demo runbook checklist | L1 | 전체 Lane | 운영 절차, rollback/retry 절차, 열린 risk가 문서화된다 |

## 핵심 선행 경로

| 계약 | 담당 | 막는 범위 |
|---|---|---|
| Account/PolicePhone/Auth fixture | L2/S1-2 | 전체 Lane |
| Event envelope, EventHub, SSE replay | L2/S4 | 모든 write/event path |
| Geometry fixture, `SearchAreaQuery.overallOf` | L3/S2 | L4 path, L5 marker, L6 package/board |
| OP query/current OP | L3/S8 | L1 bootstrap, L4 path, L5 marker, L6 package/board |
| Idempotency/outbox contract | L4/S6 | L3/S2/S8, L5/S5, L6/S7 app writes |
| Board slot registry and board API convergence | L6/S3-2 | SC-02 ~ SC-12 board merge |

## SC 통합 책임

| SC | 통합 담당 | 협업 Lane | 필요한 Board Merge |
|---|---|---|---|
| SC-01 배정 사건 가져오기·초동 활성화 | L1 | L2, L3, L5 | - |
| SC-02 실종팀 인계·112/mock 지원 배정 | L1 | L2, L3, L4, L5, L6 | `path`, `marker`, `handover_status`, `op_history` |
| SC-03 사건 오프라인 패키지 사전 적재 | L6 | L1, L2, L3, L5 | `package_badge` |
| SC-04 전체 수색 구역·구역 분할·할당 | L3 | L1, L2, L6 | `overall_search_area`, `area` |
| SC-05 수색 경로·PolicePhone GPS 경로 | L4 | L2, L3, L6 | `path`, `police_phone_freshness` |
| SC-06 현장 마커 생성 | L5 | L2, L3, L4, L6 | `marker` |
| SC-07 통신 단절 중 로컬 기록 | L4 | L2, L5, L6 | - |
| SC-08 지원 요청·실종자 발견 알림 | L5 | L1, L2, L3, L4, L6 | `marker`, `toast` |
| SC-09 통신 복구·동기화 | L4 | L2, L5, L6 | `path`, `marker`, `police_phone_freshness` |
| SC-10 구역 완료·새 OP 열기 | L3 | L1, L2, L6 | `area`, `op_toggle`, `op_history`, `handover_memo`, `handover_status` |
| SC-11 인수인계·OP 비교·수색 이력 요약 | L6 | L1, L2, L3, L4, L5 | `op_toggle`, `handover_memo`, `search_history_summary` |
| SC-12 사건 종료·데이터 파기 | L1 | L2, L4, L5, L6 | `incident_terminal`, `package_badge` |

## 공통 규칙

- Lane 담당자는 자기 Lane의 implementation, fixture, mock adapter, red test를 책임진다.
- 다른 Lane 구현은 `provides` 계약 mock/fixture로 대체할 수 있어야 한다.
- 다른 Lane의 entity, API, event payload, board shell 파일을 수정하려면 해당 담당자의 LGTM이 필요하다.
- S4 event envelope/event_dispatch_job/SSE 계약은 L2가 실구현 담당이다. Domain Spec은 event payload와 publish request만 책임진다.
- S3-2 board shell, routing, layout, slot mounting, shared state, final merge는 L6/S3-2가 단독 소유한다.
- Lane task의 `관련 FR`은 반드시 담당 Spec에 mapping된 FR을 뜻한다. 다른 Spec의 FR을 지원만 하는 경우에는 담당 범위를 넓히지 말고 `지원 FR`로 적는다.
- `FR-23`은 독립 기능 task가 아니다. 자동 누락 확정, 다음 구역 추천, 위험도 판단 금지 조건으로 S2/S3-2/S8 task에 붙인다.
- `FR-18`은 알림 표시가 아니라 공용 상황판 reference view다.
- `FR-21`은 타일 서버가 아니라 실종자 기본 정보다. 타일 서버는 `FR-19/FR-31` 구현 계약으로 다룬다.

## 리뷰 루프

1. Source Fidelity: task가 기준 문서와 다른 의미를 만들지 않는지 확인한다.
2. Lane Ownership: 구현 책임과 소비 책임이 섞이지 않았는지 확인한다.
3. Harness Executability: 각 task가 red test, fixture, observable result로 검증 가능한지 확인한다.
4. Cross-Lane: 중복, 누락, fixture ID 불일치, SC final PASS blocker를 확인한다.

## 리뷰할 열린 이슈

- 없음. 새 이슈가 생기면 PRD 정의, 영향받는 Lane, 영향받는 Spec, 제안 해결책을 함께 기록한다.
