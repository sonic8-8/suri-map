# L3 Tasks · Map Boundary, Search Area, OP, Handover

상태: 초안. 담당 Lane: L3. 담당 Spec: S2, S8. 주요 SC: SC-04, SC-10, SC-11 backend.

## 작업 범위

- `map_boundary`, `search_area`, `search_area_history`
- OP bootstrap/current/list, OP transition, OP assignment
- handover memo, handover status, AI history summary
- FR-23 automatic judgement prohibition in S2/S8 domain outputs

## 외부 계약

- Consumes L2/S1-2 role/channel guard and L2/S4 publish contract.
- Provides geometry query contracts to L4/S3-1, L5/S5, L6/S7/S3-2.
- Provides OP/current/handover query contracts to L1/S1-1, L4/S3-1, L5/S5, L6/S3-2/S7.
- Provides slot-scoped renderer/source contracts to L6/S3-2 without owning board shell.

## Phase -1

- [ ] L3-B01 공간 DB와 도형 테스트 데이터 준비
  - 담당 Spec: S2, S8
  - 필수 참조: `architecture.md §5`, `architecture.md §6.1`, `adr.md ADR-0002`, `adr.md ADR-0033`, `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/harness-scenarios.md §6`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-04, SC-05, SC-06, SC-10
  - 구현 산출물: PostGIS extension verification, MyBatis geometry TypeHandler verification, canonical geometry fixture, OP1/OP2 seed fixture, geometry validation test data
  - 예상 작업량: 2d
  - 완료 기준: feature write가 의존하기 전에 PostGIS extension 가정, MyBatis geometry mapping, canonical bbox/polygon fixture, OP1/OP2 seed ID, geometry validation test data가 준비된다.

## Phase 0

- [ ] L3-T04A 지도 기준 범위·수색 구역 조회 포트 계약 검증 작성
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 geometry/GPS 기준 좌표`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-03, SC-04, SC-05, SC-06
  - 구현 산출물: `MapBoundaryQuery.of` mock contract, `AreaQuery.byIncident` mock contract, `AreaQuery.byOp` mock contract, canonical geometry fixture tests
  - 예상 작업량: 1d
  - 완료 기준: real DB query 구현 없이도 소비 Lane이 canonical geometry, bbox, id/status/version, shared fixture coordinate 기준의 실패 contract test를 실행할 수 있다.

- [ ] L3-T05A OP1 시작·현재 OP 모의 계약과 guard 실패 테스트 작성
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S1-1.json`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-1, S3-1, S5, S7
  - 시나리오: SC-01, SC-03, SC-05, SC-06
  - 관련 FR: FR-32, FR-34
  - 구현 산출물: OP1 bootstrap handler mock, current OP query fixture, `op_required`/`op_mismatch` guard tests
  - 예상 작업량: 1d
  - 완료 기준: L1과 field-write 소비 Lane이 fixture/mock 응답으로 OP1/current OP test와 `op_required`/`op_mismatch` 실패를 실행할 수 있다.

## Phase 1

- [ ] L3-T05B OP1 시작과 현재 OP 보호 규칙 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S1-1.json`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-1, S3-1, S5, S7
  - 시나리오: SC-01, SC-03, SC-05, SC-06
  - 관련 FR: FR-32, FR-34
  - 구현 산출물: OP1 bootstrap handler, `OP_TRANSITIONED(from=null,to=OP1)` PublishRequest contract test, current OP query implementation, `op_required`/`op_mismatch` guard tests
  - 예상 작업량: 1d
  - 완료 기준: incident import가 S8을 통해 OP1을 생성하고, `OP_TRANSITIONED(from=null,to=OP1)`가 안정적인 id/status/version/opId를 포함하며, OP가 필요한 write는 `op_required`/`op_mismatch`로 실패한다.

- [ ] L3-T01 지도 기준 범위 생성·수정·조회 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §3 S2`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S7, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-05, FR-07, FR-23
  - 구현 산출물: map_boundary create/update/read API, active boundary constraint, `MAP_BOUNDARY_CHANGED` publish request, no-auto-judgement tests
  - 예상 작업량: 2d
  - 완료 기준: web commander가 incident별 active boundary 1개를 관리할 수 있고, `MAP_BOUNDARY_CHANGED`가 발행되며, 자동 실종/추천 구역은 생성되지 않는다.

- [ ] L3-T02A 수색 구역 생성·수정·조회 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S8, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-06, FR-07
  - 구현 산출물: search_area create/update APIs, `AREA_CREATED` PublishRequest contract test, invalid geometry rejection tests, area query DTO, board slot source fixture
  - 예상 작업량: 1d
  - 완료 기준: 유효한 polygon은 area를 생성/수정하고, `AREA_CREATED`는 안정적인 id/status/version/opId를 포함하며, invalid geometry는 공통 규칙으로 실패하고 board 소비자는 안정적인 query DTO를 받는다.

- [ ] L3-T04B 지도 기준 범위·수색 구역 조회 포트 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 geometry/GPS 기준 좌표`
  - 연관 Spec: S3-1, S5, S7, S3-2
  - 시나리오: SC-03, SC-04, SC-05, SC-06
  - 구현 산출물: `MapBoundaryQuery.of`, `AreaQuery.byIncident`, `AreaQuery.byOp`, canonical geometry query tests
  - 예상 작업량: 1d
  - 완료 기준: `MapBoundaryQuery.of`, `AreaQuery.byIncident`, `AreaQuery.byOp`가 canonical geometry, bbox, id/status/version, shared fixture coordinate를 사용한다.

- [ ] L3-T02B 수색 구역 분할과 분할 이력 검증 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S8, S3-2
  - 시나리오: SC-04
  - 관련 FR: FR-12, FR-13
  - 구현 산출물: search_area split API, split geometry validation tests, parent/child area relation fixture, `AREA_STATE_CHANGED` and `AREA_CREATED` split PublishRequest tests
  - 예상 작업량: 1d
  - 완료 기준: 유효한 split은 추적 가능한 parent relation을 가진 child area를 만들고, archived parent의 `AREA_STATE_CHANGED`와 child의 `AREA_CREATED`가 안정적인 id/status/version/opId를 포함하며, invalid split geometry는 실패한다.

## Phase 2

- [ ] L3-T03 구역 상태 전이와 이력 구현
  - 담당 Spec: S2
  - 필수 참조: `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/boundaries.md §10 SC-10`
  - 연관 Spec: S8, S4, S3-2
  - 시나리오: SC-10
  - 관련 FR: FR-06, FR-07, FR-12, FR-13
  - 구현 산출물: area state transition API, search_area_history write path, `AREA_STATE_CHANGED` publish request, history/count tests
  - 예상 작업량: 2d
  - 완료 기준: 상태 전이가 previous/next state, OP, account, time, memo, count/history를 기록하고 `AREA_STATE_CHANGED`를 발행한다.

- [ ] L3-T06A OP2+ 전환 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §3 S8`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S2, S1-2, S4, S3-2, S7
  - 시나리오: SC-04, SC-10
  - 관련 FR: FR-12, FR-32
  - 구현 산출물: OP2+ create/transition API, previous active OP close policy, `OP_TRANSITIONED` publish request, transition history tests
  - 예상 작업량: 1d
  - 완료 기준: web commander가 reason과 함께 OP2+를 열고 이전 active OP를 닫으며, transition history를 보존하고 `OP_TRANSITIONED`를 발행한다.

- [ ] L3-T06B OP 담당 구역 배정과 이력 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §3 S8`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S2, S1-2, S4, S3-2, S7
  - 시나리오: SC-04, SC-10
  - 관련 FR: FR-12, FR-32
  - 구현 산출물: op_assignment write/query API, assignment history tests, SC-04 담당 구역 배정 fixture, `OP_ASSIGNMENT_CHANGED` publish request, board source fixture
  - 예상 작업량: 1d
  - 완료 기준: SC-04 담당 구역 배정과 SC-10 OP area assignment write가 assignment history를 보존하고 board 소비자용 query data를 노출하며 `OP_ASSIGNMENT_CHANGED`를 발행한다.

- [ ] L3-T07 인수인계 메모 생성·조회 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/specs/S6.json`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Spec: S1-2, S6, S3-2
  - 시나리오: SC-10, SC-11
  - 관련 FR: FR-15, FR-37
  - 구현 산출물: handover_memo create/query API, context binding tests, `HANDOVER_MEMO_CREATED` PublishRequest contract test, S6 outbox-compatible write operation fixture
  - 예상 작업량: 1d
  - 완료 기준: 허용된 app/web account가 OP/path/area memo를 생성하고, `HANDOVER_MEMO_CREATED`가 안정적인 id/status/version/opId를 포함하며, offline app memo는 S6 outbox를 사용하고 S3-2는 context별로 조회할 수 있다.

## Phase 3

- [ ] L3-T08 AI 수색 이력 요약과 FR-23 보호 규칙 구현
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/boundaries.md §6 FR-23`, `spec/harness-scenarios.md §2 SC-11`, `adr.md ADR-0034`, `architecture.md §6.5`
  - 연관 Spec: S3-1, S2, S5, S3-2, S4
  - 시나리오: SC-11
  - 관련 FR: FR-23, FR-39
  - 구현 산출물: `AiSummaryPort`, `OpenAiSummaryAdapter`, OpenAI structured output schema validation, `TemplateSummaryAdapter` fallback, forbidden recommendation/risk wording guard, `AI_SUMMARY_READY` PublishRequest contract test, failure fallback tests
  - 예상 작업량: 2d
  - 완료 기준: OpenAI happy path가 최소화된 내부 OP/path/marker/area/memo evidence만 사용하고, `AI_SUMMARY_READY`가 안정적인 id/status/version/opId를 포함하며, OpenAI 실패/timeout/schema 실패 시 fallback이 동작하고 추천/위험도 판단/누락 구역 확정은 거부되거나 숨겨진다.

## Phase 4

- [ ] L3-T09A SC-04 지도 범위·구역 하네스 작성
  - 담당 Spec: S2, S8
  - 필수 참조: `spec/specs/S2.json`, `spec/specs/S8.json`, `spec/harness-scenarios.md §2 SC-04`
  - 연관 Spec: S1-2, S4, S3-2, S7
  - 시나리오: SC-04
  - 선행 task: L3-T02A, L3-T02B, L3-T06B
  - 구현 산출물: S2/S8 harness runner, geometry mocks, boundary/area red tests, op_assignment/`OP_ASSIGNMENT_CHANGED` red tests, board area/assignment convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: SC-04 domain harness가 L2/S4/S3-2 mock으로 통과하고 map boundary, search area, op_assignment, 담당 구역 공유, board convergence evidence를 생성한다.

- [ ] L3-T09B SC-10 OP 전환·인수인계 하네스 작성
  - 담당 Spec: S8
  - 필수 참조: `spec/specs/S8.json`, `spec/harness-scenarios.md §2 SC-10`
  - 연관 Spec: S1-2, S4, S3-2
  - 시나리오: SC-10
  - 구현 산출물: S8 harness runner, OP transition mocks, assignment/handover red tests, mock radio report/commander decision fixture, `harness_execution_log` sequence tests, board OP convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: SC-10 domain harness가 L2/S4/S3-2 mock으로 통과하고, radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_snapshot_projected 순서와 OP/handover board convergence evidence를 생성한다.

- [ ] L3-T10 시연용 도형·OP·인수인계 fixture 작성
  - 담당 Spec: S2, S8
  - 필수 참조: `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Lane: L1, L4, L5, L6
  - 시나리오: SC-04, SC-10, SC-11
  - 구현 산출물: demo boundary/area/OP/handover fixture, AI summary OpenAI success/fallback input fixture, manual data repair checklist
  - 예상 작업량: 1d
  - 완료 기준: demo fixture가 live manual data repair에 의존하지 않고 valid boundary, assigned area, OP transition, handover memo, AI-summary OpenAI success/fallback input을 포함한다.

## Phase 5

- [ ] L3-D01 시연용 도형·OP·인수인계 데이터 검증
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
- S4 event outbox rows and SSE dispatch
