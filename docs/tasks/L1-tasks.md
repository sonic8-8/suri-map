# L1 Tasks · Incident Import & Assignment

상태: 초안. 담당 Lane: L1. 담당 Spec: S1-1. 주요 SC: SC-01, SC-02, SC-12.

## 작업 범위

- mock 112 배정 사건 가져오기
- incident lifecycle: `OPEN`, terminal close
- missing_person domain data
- 112/mock polling/import 기반 incident_assignment 반영
- incident list/detail DTO
- close request handoff to purge and terminal board API response

## 외부 계약

- Consumes L2/S1-2 auth, role, police_phone, incident access fixture.
- Publishes through L2/S4 event envelope and `EventHub.publish`.
- Calls L5/S5 initial marker seed port.
- Creates OP1 through the S8 OP1 자동 생성 contract during incident import.
- Hands close/purge to L2/S1-3 and package/missing_person removal hooks.

## Phase -1

- [x] L1-B01 배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/harness-scenarios.md §6`, `spec/boundaries.md §10`
  - 연관 Spec: S1-2, S5, S8, S7
  - 시나리오: SC-01, SC-02, SC-10, SC-12
  - 관련 FR: FR-01, FR-21, FR-22
  - 구현 산출물: 배정 사건 fixture, incident_assignment fixture, missing_person seed, OP/marker/path/memo seed 연결값
  - 예상 작업량: 1d
  - 완료 기준: `spec/harness-scenarios.md`의 지구대/파출소 초동 사건 seed와 실종팀 인계·112/mock 지원 배정 incident_assignment가 모든 Lane에서 같은 incident, incident_assignment, marker, path, OP, memo, police_phone ID로 사용 가능하다.

## Phase 0

- [x] L1-T07 SC-01/02/12 사건 흐름 모의 어댑터와 시드 ID 로더 작성
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/harness-scenarios.md §2 SC-01`, `spec/harness-scenarios.md §2 SC-02`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Spec: S1-2, S4, S5, S8
  - 시나리오: SC-01, SC-02, SC-12
  - 관련 FR: FR-01, FR-21, FR-22
  - 구현 산출물: L1 red test, auth/event/marker/OP/purge mock adapter, seed ID fixture loader
  - 예상 작업량: 1d
  - 완료 기준: `spec/harness-scenarios.md`의 seed ID가 fixture로 적재되고, L1이 auth, event, marker seed, OP1 자동 생성, purge hook mock으로 실행된다.

## Phase 1

- [x] L1-T01 배정 사건 가져오기 API 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/boundaries.md §3 S1-1`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-2, S4, S5, S8
  - 시나리오: SC-01
  - 관련 FR: FR-01
  - 구현 산출물: `POST /api/incidents/import`, incident/incident_assignment/missing_person/OP1 write path, `INCIDENT_CREATED` publish request, OP1 자동 생성 rollback test
  - 예상 작업량: 2d
  - 완료 기준: `POST /api/incidents/import`가 중복 row 없이 incident, missing_person, incident_assignment, OP1, `INCIDENT_CREATED`를 생성하고 실패 시 전체 rollback한다.

- [x] L1-T03 실종자 도메인 데이터와 소비 계약 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/specs/S7.json`, `spec/boundaries.md §6 FR-21`, `spec/boundaries.md §6 FR-22`
  - 연관 Spec: S7, S3-2
  - 시나리오: SC-01, SC-03
  - 관련 FR: FR-21, FR-22
  - 구현 산출물: missing_person entity/DTO, allowlist contract test, 금지 필드 노출 negative test, offline package input fixture validation
  - 예상 작업량: 1d
  - 완료 기준: incident detail과 offline package input의 allowlist test가 통과하고, 금지 필드 노출 test가 실패하며, S7/S3-2가 S1-1 허용 필드만 소비함이 fixture로 검증된다.

- [x] L1-T05A 진행 중 사건 목록·상세 조회 DTO 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/boundaries.md §3 S1-1`, `spec/boundaries.md §5 Entity Ownership`
  - 연관 Spec: S1-2, S3-2
  - 시나리오: SC-01, SC-02
  - 관련 FR: FR-01
  - 구현 산출물: active incident list DTO, active incident detail DTO, active 상태 허용 필드 test
  - 예상 작업량: 1d
  - 완료 기준: active list/detail 응답이 OPEN 사건의 허용 필드만 노출하고 terminal/purge 상태 로직을 포함하지 않는다.

## Phase 2

- [x] L1-T02 사건 상태 전이 보호 규칙 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/boundaries.md §4.5`, `spec/boundaries.md §7`
  - 연관 Spec: S1-2, S2, S3-1, S5, S7, S8
  - 시나리오: SC-01, SC-12
  - 관련 FR: FR-01, FR-22
  - 구현 산출물: `OPEN`/terminal guard policy, import rollback/closed error tests, consumer fixture states
  - 예상 작업량: 1d
  - 완료 기준: import 실패는 incident row를 남기지 않고, 종료 후 write는 closed/terminal guard로 실패하며, 소비 Lane fixture가 두 상태를 검증할 수 있다.

- [x] L1-T04 인계 후 기존 기록 조회와 112/mock 지원 배정 반영 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/harness-scenarios.md §1.1`, `spec/boundaries.md §10 SC-02`
  - 연관 Spec: S1-2, S3-1, S5, S8, S3-2
  - 시나리오: SC-02
  - 관련 FR: FR-01
  - 구현 산출물: 112/mock polling/import incident_assignment 반영 handler, `INCIDENT_ASSIGNMENT_CHANGED` PublishRequest contract test, handover incident_assignment fixture, OP1 record continuity test
  - 예상 작업량: 1d
  - 완료 기준: 지구대/파출소 OP1 기록이 계속 조회되고, 기존 row를 revoke하지 않은 채 112/mock 지원 배정 incident_assignment가 추가되며, `INCIDENT_ASSIGNMENT_CHANGED`가 안정적인 id/status/version을 포함한다.

## Phase 3

- [x] L1-T06 사건 종료 명령과 데이터 삭제 인계 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/boundaries.md §10 SC-12`
  - 연관 Spec: S1-3, S4, S6, S7, S3-2
  - 시나리오: SC-12
  - 관련 FR: FR-22
  - 구현 산출물: incident close command, `INCIDENT_CLOSED` publish request, active missing_person hard delete, purge handoff contract test
  - 예상 작업량: 2d
  - 완료 기준: close가 `INCIDENT_CLOSED`를 발행하고 신규 write를 차단하며 active missing_person를 삭제하고 S3-2용 sanitized terminal state를 제공한다.

- [x] L1-T05B 종료 사건 조회와 개인정보 제거 검증 구현
  - 담당 Spec: S1-1
  - 필수 참조: `spec/specs/S1-1.json`, `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/boundaries.md §5 Entity Ownership`
  - 연관 Spec: S1-3, S3-2
  - 시나리오: SC-12
  - 관련 FR: FR-22
  - 선행 task: L1-T06, L2-T08
  - 구현 산출물: terminal/sanitized response test, purged PII leak test, terminal incident detail DTO
  - 예상 작업량: 1d
  - 완료 기준: terminal incident 응답이 sanitized state만 노출하고 close/purge handoff 이후 삭제된 실종자 개인정보를 노출하지 않는다.

## Phase 4

- [x] L1-I01 SC-01 사건 시작 흐름 통합 검증
  - 담당 Spec: S1-1
  - 필수 참조: `spec/harness-scenarios.md §2 SC-01`, `spec/boundaries.md §10 SC-01`
  - 연관 Spec: S1-2, S4, S5, S8
  - 시나리오: SC-01
  - 선행 task: L1-T01, L2-T02, L2-T05, L2-T06, L3-T05B, L5-T05B
  - 구현 산출물: SC-01 integration test, mock-to-real contract 교체 evidence, OP1 자동 생성 convergence check
  - 예상 작업량: 1d
  - 완료 기준: 배정 사건 import, OP1 자동 생성, `OPEN` 사건 응답이 SC-01 e2e red test를 통과한다.

- [x] L1-I02 SC-02 인계·지원 배정 통합 검증
  - 담당 Spec: S1-1
  - 필수 참조: `spec/harness-scenarios.md §2 SC-02`, `spec/boundaries.md §10 SC-02`
  - 연관 Spec: S1-2, S3-1, S5, S8, S3-2
  - 시나리오: SC-02
  - 선행 task: L1-T04, L2-T02, L2-T05, L2-T06, L2-T07A, L3-T06B, L3-T07, L4-T02, L5-T08, L5-T09D, L6-T03B, L6-T04A, L6-T10B
  - 구현 산출물: SC-02 integration test, 112/mock support assignment fixture, `INCIDENT_ASSIGNMENT_CHANGED` fanout/SSE evidence, FCM recipient evidence, board slot convergence evidence
  - 예상 작업량: 2d
  - 완료 기준: 실종팀 인계와 112/mock 지원 배정 후에도 OP1 기록이 보존되고, 배정 단말 FCM evidence가 남으며, S4 fanout/SSE와 S3-2 board API가 수렴한다.

- [x] L1-I03 SC-12 사건 종료·데이터 파기 통합 검증
  - 담당 Spec: S1-1
  - 필수 참조: `spec/harness-scenarios.md §2 SC-12`, `spec/boundaries.md §10 SC-12`
  - 연관 Spec: S1-2, S1-3, S4, S5, S6, S7, S3-2
  - 시나리오: SC-12
  - 선행 task: L1-T06, L2-T07C, L2-T08, L4-T09, L5-T04C, L6-T09A, L6-T09B, L6-T10B
  - 구현 산출물: SC-12 integration test, close/write-block/purge-start evidence, `INCIDENT_CLOSED`/`INCIDENT_PURGED` event/SSE evidence, sanitized terminal board API evidence
  - 완료 근거: close API 응답, 이후 배정 쓰기 차단, 파기 실행 상태 전이, 종료 SSE replay marker, 개인정보 제거 상황판 행을 한 테스트에서 함께 검증한다.
  - 예상 작업량: 2d
  - 완료 기준: close가 write를 차단하고 active PII 노출을 제거하며 purge orchestration을 시작하고, `INCIDENT_CLOSED`/`INCIDENT_PURGED` event/SSE payload와 S3-2 incident_terminal board API row가 같은 terminal/closed 상태로 수렴한다.

- [x] L1-I04 12단계 리허설 체크리스트와 복구 절차서 작성
  - 담당 Spec: S1-1
  - 필수 참조: `prd.md §2.3`, `prd.md §5.1`, `spec/harness-scenarios.md`
  - 연관 Lane: All lanes
  - 시나리오: SC-01 through SC-12
  - 관련 FR: FR-01, FR-21, FR-22
  - 구현 산출물: 12-step rehearsal checklist, demo seed data sheet, import/handover/close retry runbook
  - 예상 작업량: 1d
  - 완료 기준: Phase 5 실행 전에 rehearsal checklist, seed sheet, retry/rollback runbook이 준비되며 demo pass/fail evidence는 기록하지 않는다.

## Phase 5

- [x] L1-D01A SC-01~03 리허설 실행과 시연 증거 수집
  - 담당 Spec: S1-1
  - 필수 참조: `prd.md §2.3`, `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-01`, `spec/harness-scenarios.md §2 SC-02`, `spec/harness-scenarios.md §2 SC-03`
  - 연관 Lane: L2, L3, L5, L6
  - 시나리오: SC-01, SC-02, SC-03
  - 관련 FR: FR-01, FR-21
  - 선행 task: L1-I01, L1-I02, L1-I04
  - 구현 산출물: SC-01~03 rehearsal result, fixture ID evidence, import/handover/package failure log, follow-up fix task ID
  - 예상 작업량: 1d
  - 완료 기준: 배정 사건 가져오기, 실종팀 인계·지원 부대 배정, 오프라인 패키지 사전 적재 흐름을 fixture ID와 함께 리허설하고 pass/fail evidence를 첨부하며, 실패는 별도 follow-up fix task로 연결한다.

- [x] L1-D01B SC-04~06 리허설 실행과 시연 증거 수집
  - 담당 Spec: S1-1
  - 필수 참조: `prd.md §2.3`, `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-04`, `spec/harness-scenarios.md §2 SC-05`, `spec/harness-scenarios.md §2 SC-06`
  - 연관 Lane: L2, L3, L4, L5, L6
  - 시나리오: SC-04, SC-05, SC-06
  - 관련 FR: FR-01
  - 선행 task: L1-I01, L1-I02, L1-I04, L3-D01, L5-D01
  - 구현 산출물: SC-04~06 rehearsal result, overall_search_area/area/path/marker fixture evidence, board convergence capture target, follow-up fix task ID
  - 예상 작업량: 1d
  - 완료 기준: 전체 수색 구역·구역 할당, 수색 경로·PolicePhone GPS 경로, 현장 마커 생성 흐름을 fixture ID와 함께 리허설하고 pass/fail evidence를 첨부하며, 실패는 별도 follow-up fix task로 연결한다.

- [x] L1-D01C SC-07~09 리허설 실행과 시연 증거 수집
  - 담당 Spec: S1-1
  - 필수 참조: `prd.md §2.3`, `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §2 SC-08`, `spec/harness-scenarios.md §2 SC-09`
  - 연관 Lane: L2, L4, L5, L6
  - 시나리오: SC-07, SC-08, SC-09
  - 관련 FR: FR-01
  - 선행 task: L1-I01, L1-I02, L1-I04, L4-D01, L5-D01
  - 구현 산출물: SC-07~09 rehearsal result, offline/outbox/notification/recovery fixture evidence, duplicate row verification result, follow-up fix task ID
  - 예상 작업량: 1d
  - 완료 기준: 통신 단절 중 로컬 기록, 지원 요청·실종자 발견 알림, 통신 복구·동기화 흐름을 fixture ID와 함께 리허설하고 pass/fail evidence를 첨부하며, 실패는 별도 follow-up fix task로 연결한다.

- [x] L1-D01D SC-10~12 리허설 실행과 시연 증거 수집
  - 담당 Spec: S1-1
  - 필수 참조: `prd.md §2.3`, `prd.md §5.1`, `spec/harness-scenarios.md §2 SC-10`, `spec/harness-scenarios.md §2 SC-11`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Lane: L2, L3, L4, L5, L6
  - 시나리오: SC-10, SC-11, SC-12
  - 관련 FR: FR-01, FR-22
  - 선행 task: L1-I01, L1-I02, L1-I03, L1-I04, L3-D01, L6-D01
  - 구현 산출물: SC-10~12 rehearsal result, OP/handover/search_history_summary/terminal fixture evidence, sanitized terminal state capture, follow-up fix task ID
  - 예상 작업량: 1d
  - 완료 기준: 구역 완료·새 OP 열기, 인수인계·OP 비교·수색 이력 요약, 사건 종료·데이터 파기 흐름을 fixture ID와 함께 리허설하고 pass/fail evidence를 첨부하며, 실패는 별도 follow-up fix task로 연결한다.

## 담당하지 않음

- Role/channel policy implementation: L2/S1-2
- Event dispatch job/SSE dispatch implementation: L2/S4
- Marker domain writes: L5/S5
- OP lifecycle implementation after OP1 자동 생성 request: L3/S8
- Package/missing_person purge hook internals: L2/S1-3, L4/S6, L6/S7
