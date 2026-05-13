# L3 Tasks · Search Area, OP, Handover

상태: 초안. 담당 Lane: L3. 담당 Spec: S2, S8. 주요 SC: SC-04, SC-10, SC-11 backend.

## 작업 범위

- `search_area(area_level=OVERALL|UNIT|TEAM)`, `search_area_history`
- OP1 자동 생성/current/list, OP transition, search_area_assignment
- handover memo, handover status, search_history_summary
- FR-23 automatic judgement prohibition in S2/S8 domain outputs

## 외부 계약

- Consumes L2/S1-2 role/channel guard and L2/S4 publish contract.
- Provides geometry query contracts to L4/S3-1, L5/S5, L6/S7/S3-2.
- Provides OP/current/handover query contracts to L1/S1-1, L4/S3-1, L5/S5, L6/S3-2/S7.
- Provides slot-scoped renderer/source contracts to L6/S3-2 without owning board shell.

## Phase -1

- [x] L3-B01 공간 DB와 도형 테스트 데이터 준비
  - 담당 Spec: S2, S8
  - 필수 참조: `architecture.md §5`, `architecture.md §6.1`, `adr.md ADR-0002`, `adr.md ADR-0033`, `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/harness-scenarios.md §6`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-04, SC-05, SC-06, SC-10
  - 구현 산출물: PostGIS extension verification, MyBatis geometry TypeHandler verification, canonical geometry fixture, OP1/OP2 seed fixture, geometry validation test data
  - 예상 작업량: 2d
  - 완료 기준: feature write가 의존하기 전에 PostGIS extension 가정, MyBatis geometry mapping, canonical bbox/polygon fixture, OP1/OP2 seed ID, geometry validation test data가 준비된다.

## Phase 0

- [x] L3-T04A 전체 수색 구역·수색 구역 조회 포트 계약 검증 작성
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 geometry/GPS 기준 좌표`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-03, SC-04, SC-05, SC-06
  - 구현 산출물: `SearchAreaQuery.overallOf` mock contract, `AreaQuery.byIncident` mock contract, `AreaQuery.byOp` mock contract, canonical geometry fixture tests
  - 예상 작업량: 1d
  - 완료 기준: real DB query 구현 없이도 소비 Lane이 canonical geometry, bbox, id/status/version, shared fixture coordinate 기준의 실패 contract test를 실행할 수 있다.

- [x] L3-T05A OP1 시작·현재 OP 모의 계약과 guard 실패 테스트 작성
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S1-1.json`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-1, S3-1, S5, S7
  - 시나리오: SC-01, SC-03, SC-05, SC-06
  - 관련 FR: FR-32, FR-34
  - 구현 산출물: OP1 자동 생성 handler mock, current OP query fixture, `op_required`/`op_mismatch` guard tests
  - 예상 작업량: 1d
  - 완료 기준: L1과 field-write 소비 Lane이 fixture/mock 응답으로 OP1/current OP test와 `op_required`/`op_mismatch` 실패를 실행할 수 있다.

## Phase 1

- [x] L3-T05B OP1 시작과 현재 OP 보호 규칙 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S1-1.json`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-1, S3-1, S5, S7
  - 시나리오: SC-01, SC-03, SC-05, SC-06
  - 관련 FR: FR-32, FR-34
  - 구현 산출물: OP1 자동 생성 handler, `OP_TRANSITIONED(from=null,to=OP1)` PublishRequest contract test, current OP query implementation, `op_required`/`op_mismatch` guard tests
  - 예상 작업량: 1d
  - 완료 기준: incident import가 S8을 통해 OP1을 생성하고, `OP_TRANSITIONED(from=null,to=OP1)`가 안정적인 id/status/version/opId를 포함하며, OP가 필요한 write는 `op_required`/`op_mismatch`로 실패한다.

- [x] L3-T01 전체 수색 구역 생성·수정·조회 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §3 S2`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S7, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-05, FR-07, FR-23
  - 구현 산출물: `search_area(area_level=OVERALL)` create/update/read API, active overall area constraint, `SEARCH_AREA_CHANGED` publish request, no-auto-judgement tests
  - 예상 작업량: 2d
  - 완료 기준: web commander가 incident별 active overall search area 1개를 관리할 수 있고, `SEARCH_AREA_CHANGED`가 발행되며, 자동 누락/추천 구역은 생성되지 않는다.

- [x] L3-T02A 수색 구역 생성·수정·조회 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S8, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-07, FR-12
  - 구현 산출물: search_area create/update APIs, `SEARCH_AREA_CHANGED` PublishRequest contract test, invalid geometry rejection tests, area query DTO, board slot source fixture
  - 예상 작업량: 1d
  - 완료 기준: 유효한 polygon은 area를 생성/수정하고, `SEARCH_AREA_CHANGED`는 안정적인 id/status/version/opId를 포함하며, invalid geometry는 공통 규칙으로 실패하고 board 소비자는 안정적인 query DTO를 받는다.

- [x] L3-T04B 전체 수색 구역·수색 구역 조회 포트 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 geometry/GPS 기준 좌표`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-03, SC-04, SC-05, SC-06
  - 구현 산출물: `SearchAreaQuery.overallOf`, `AreaQuery.byIncident`, `AreaQuery.byOp`, canonical geometry query tests
  - 예상 작업량: 1d
  - 완료 기준: `SearchAreaQuery.overallOf`, `AreaQuery.byIncident`, `AreaQuery.byOp`가 canonical geometry, bbox, id/status/version, shared fixture coordinate를 사용한다.

- [x] L3-T02B 수색 구역 분할과 분할 이력 검증 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S8, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-12, FR-13
  - 구현 산출물: search_area split API, split geometry validation tests, parent/child area relation fixture, `SEARCH_AREA_CHANGED` and `SEARCH_AREA_CHANGED` split PublishRequest tests
  - 예상 작업량: 1d
  - 완료 기준: 유효한 split은 추적 가능한 parent relation을 가진 child area를 만들고, cancelled parent의 `SEARCH_AREA_CHANGED`와 child의 `SEARCH_AREA_CHANGED`가 안정적인 id/status/version/opId를 포함하며, invalid split geometry는 실패한다.

## Phase 2

- [x] L3-T03 구역 상태 전이와 이력 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/boundaries.md §10 SC-10`
  - 연관 Spec: S8, S4, S3-2
  - 시나리오: SC-10
  - 관련 FR: FR-06, FR-07, FR-13
  - 구현 산출물: area state transition API, search_area_history write path, `SEARCH_AREA_CHANGED` publish request, history-derived count tests
  - 예상 작업량: 2d
  - 완료 기준: 상태 전이가 previous/next status, account, time, memo, history-derived count를 기록하고 `SEARCH_AREA_CHANGED`를 발행한다.

- [x] L3-T06A OP2+ 전환 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §3 S8`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S2, S1-2, S4, S3-2, S7
  - 시나리오: SC-04, SC-10
  - 관련 FR: FR-12, FR-32
  - 구현 산출물: OP2+ create/transition API, previous active OP close policy, `OP_TRANSITIONED` publish request, transition history tests
  - 예상 작업량: 1d
  - 완료 기준: web commander가 reason과 함께 OP2+를 열고 이전 active OP를 닫으며, transition history를 보존하고 `OP_TRANSITIONED`를 발행한다.

- [x] L3-T06B OP 담당 구역 배정과 이력 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §3 S8`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S2, S1-2, S4, S3-2, S7
  - 시나리오: SC-04, SC-10
  - 관련 FR: FR-12, FR-32
  - 구현 산출물: search_area_assignment write/query API, assignment history tests, SC-04 담당 구역 배정 fixture, `SEARCH_AREA_ASSIGNMENT_CHANGED` publish request, board source fixture
  - 예상 작업량: 1d
  - 완료 기준: SC-04 담당 구역 배정과 SC-10 OP area assignment write가 assignment history를 보존하고 board 소비자용 query data를 노출하며 `SEARCH_AREA_ASSIGNMENT_CHANGED`를 발행한다.

- [x] L3-T07 인수인계 메모 생성·조회 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S6.json`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Spec: S1-2, S6, S3-2
  - 시나리오: SC-10, SC-11
  - 관련 FR: FR-15, FR-37
  - 구현 산출물: handover_memo create/query API, context binding tests, `HANDOVER_MEMO_CREATED` PublishRequest contract test, S6 outbox-compatible write operation fixture
  - 예상 작업량: 1d
  - 완료 기준: 허용된 app/web account가 OP/path/area memo를 생성하고, `HANDOVER_MEMO_CREATED`가 안정적인 id/status/version/opId를 포함하며, offline app memo는 S6 outbox를 사용하고 S3-2는 context별로 조회할 수 있다.

## Phase 3

- [x] L3-T08 수색 이력 요약과 FR-23 보호 규칙 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §6 FR-23`, `spec/harness-scenarios.md §2 SC-11`, `adr.md ADR-0034`, `architecture.md §6.5`
  - 연관 Spec: S3-1, S2, S5, S3-2, S4
  - 시나리오: SC-11
  - 관련 FR: FR-23, FR-39
  - 구현 산출물: `SearchHistorySummaryPort`, `OpenAiSearchHistorySummaryAdapter`, OpenAI structured output schema validation, forbidden recommendation/risk wording guard, `SEARCH_HISTORY_SUMMARY_CHANGED` PublishRequest contract test, failure FAILED-state tests
  - 예상 작업량: 2d
  - 완료 기준: OpenAI happy path가 최소화된 내부 OP/path/marker/area/memo evidence만 사용하고, `SEARCH_HISTORY_SUMMARY_CHANGED`가 안정적인 id/status/version/opId를 포함하며, OpenAI 실패/timeout/schema 실패 시 `generation_status=FAILED`로 남고 추천/위험도 판단/누락 구역 확정은 거부되거나 숨겨진다.

## Phase 4

- [x] L3-T09A SC-04 전체 수색 구역·구역 하네스 작성
  - 담당 Spec: S2, S8
  - 필수 참조: `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S3-2, S7
  - 시나리오: SC-04
  - 선행 task: L3-T02A, L3-T02B, L3-T06B
  - 구현 산출물: S2/S8 harness runner, geometry mocks, overall_search_area/area red tests, search_area_assignment/`SEARCH_AREA_ASSIGNMENT_CHANGED` red tests, board area/assignment convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: SC-04 domain harness가 L2/S4/S3-2 mock으로 통과하고 overall_search_area, search_area, search_area_assignment, 담당 구역 공유, board convergence evidence를 생성한다.

- [x] L3-T09B SC-10 OP 전환·인수인계 하네스 작성
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S1-2, S4, S3-2
  - 시나리오: SC-10
  - 구현 산출물: S8 harness runner, OP transition mocks, assignment/handover red tests, mock radio report/commander decision fixture, `harness_execution_log` sequence tests, board OP convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: SC-10 domain harness가 L2/S4/S3-2 mock으로 통과하고, radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_api_refetched 순서와 OP/handover board convergence evidence를 생성한다.

- [x] L3-T10 시연용 도형·OP·인수인계 fixture 작성
  - 담당 Spec: S2, S8
  - 필수 참조: `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Lane: L1, L4, L5, L6
  - 시나리오: SC-04, SC-10, SC-11
  - 구현 산출물: demo overall_search_area/area/OP/handover fixture, search_history_summary OpenAI success/failure input fixture, manual data repair checklist
  - 예상 작업량: 1d
  - 완료 기준: demo fixture가 live manual data repair에 의존하지 않고 valid overall_search_area, assigned area, OP transition, handover memo, search_history_summary OpenAI success/failure input을 포함한다.

## Phase 5

- [x] L3-D01 시연용 도형·OP·인수인계 데이터 검증
  - 담당 Spec: S2, S8
  - 필수 참조: `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Lane: L1, L4, L5, L6
  - 시나리오: SC-04, SC-10, SC-11
  - 선행 task: L3-T10
  - 구현 산출물: fixture validation result, demo rehearsal evidence, linked defect task list
  - 예상 작업량: 1d
  - 완료 기준: 준비된 demo fixture가 SC-04, SC-10, SC-11 기준으로 검증되고, 발견된 defect는 별도 task로 기록된다.

## 담당하지 않음

- S3-2 board shell, global state, route, final merge
- S7 offline package manifest construction
- S3-1 path storage
- S5 marker/photo storage
- S4 event_dispatch_job rows and SSE dispatch
