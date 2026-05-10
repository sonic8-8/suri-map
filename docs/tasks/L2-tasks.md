# L2 Tasks · Account, PolicePhone, RBAC, Retention, Event Hub

상태: 초안. 담당 Lane: L2. 담당 Spec: S1-2, S1-3, S4. 주요 하네스: auth/policePhone, event hub, purge orchestration.

## 작업 범위

- account, role, team, police_phone, session, channel guard
- FCM token registration and police_phone freshness
- event envelope, event_dispatch_job, SSE replay, `EventHub.publish`
- retention purge orchestration and internal operational records

## 외부 계약

- Provides auth/policePhone fixture to all lanes.
- Provides S4 event contract to all domain lanes.
- Provides purge orchestration hooks to L1, L4, L5, L6.
- Provides police_phone freshness input to L6/S3-2.

## Phase -1

- [x] L2-B01 백엔드 실행 기반과 마이그레이션 러너 준비
  - 담당 Spec: S1-2, S1-3, S4
  - 필수 참조: `architecture.md §2`, `architecture.md §5`, `architecture.md §6.1`, `adr.md ADR-0001`, `adr.md ADR-0013`, `adr.md ADR-0033`, `adr.md ADR-0035`
  - 연관 Lane: All backend-consuming lanes
  - 시나리오: SC-01 through SC-12
  - 구현 산출물: Spring Boot base, Gradle Groovy DSL build scripts, PostgreSQL migration runner, MyBatis configuration, mapper scan/package convention, base test profile, 빈 domain module wiring
  - 예상 작업량: 1d
  - 완료 기준: domain behavior를 만들지 않은 상태에서 Spring Boot app, Gradle Groovy DSL build, PostgreSQL migration runner, MyBatis mapper scan이 test profile로 기동된다.

- [x] L2-B02 인증 필터 테스트 기반 준비
  - 담당 Spec: S1-2
  - 필수 참조: `architecture.md §2`, `spec/specs/S1-2.json`, `spec/boundaries.md §7`
  - 연관 Lane: All lanes
  - 시나리오: SC-01 through SC-12
  - 구현 산출물: security filter test harness, auth mock entrypoint, unauthorized/forbidden baseline test
  - 예상 작업량: 1d
  - 완료 기준: Lane별 domain write가 실행되기 전에 auth/security filter test가 mock account로 실행되고 fail-closed를 검증한다.

- [x] L2-B03A 이벤트 테스트 기반과 모의 발행 후크 작성
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/boundaries.md §4.4`
  - 연관 Lane: All event-consuming lanes
  - 시나리오: SC-01 through SC-12 event paths
  - 구현 산출물: event test base, mock publish hook, event fixture entrypoint
  - 예상 작업량: 1d
  - 완료 기준: real event_dispatch_job 또는 SSE dispatch 구현 전에 안정적인 mock publish hook 기준으로 event test를 작성할 수 있다.

- [x] L2-B03B 삭제 테스트 기반과 모의 삭제 후크 등록기 작성
  - 담당 Spec: S1-3
  - 필수 참조: `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`
  - 연관 Lane: L1, L4, L5, L6
  - 시나리오: SC-12
  - 구현 산출물: purge test base, mock purge hook registry, purge lifecycle fixture entrypoint
  - 예상 작업량: 1d
  - 완료 기준: real purge orchestration 구현 전에 안정적인 mock hook registry 기준으로 purge test를 작성할 수 있다.

- [x] L2-B04 백엔드 테스트 CI 작업과 아티팩트 업로드 경로 구성
  - 담당 Spec: S1-2, S1-3, S4
  - 필수 참조: `architecture.md §9`
  - 연관 Lane: All lanes
  - 시나리오: quality gate baseline
  - 구현 산출물: backend test CI job base, coverage/Sonar placeholder wiring, artifact upload path
  - 예상 작업량: 0.5d
  - 완료 기준: release gate check를 붙이기 전에 CI가 backend test command를 실행하고 placeholder artifact를 게시할 수 있다.

## Phase 0

- [x] L2-T01 계정·역할·단말 테스트 데이터 기준 준비
  - 담당 Spec: S1-2
  - 필수 참조: `spec/specs/S1-2.json`, `spec/harness-scenarios.md §6`, `spec/boundaries.md §4.6`
  - 연관 Lane: All lanes
  - 시나리오: SC-01 through SC-12
  - 관련 FR: FR-01, FR-02, FR-24, FR-25
  - 구현 산출물: account/team/police_phone fixture, role/channel matrix fixture, test seed loader, common auth mock
  - 예상 작업량: 1d
  - 완료 기준: 실종팀, 순찰차, 지휘관, 지원 부대, 지구대/파출소 account와 police_phone ID가 `spec/harness-scenarios.md` fixture ID와 일치한다.

## Phase 1

- [x] L2-T02 채널·역할 권한 별칭 구현
  - 담당 Spec: S1-2
  - 필수 참조: `spec/specs/S1-2.json`, `spec/boundaries.md §7`, `spec/boundaries.md §4.6`
  - 연관 Spec: S1-1, S2, S3-1, S5, S7, S8
  - 시나리오: SC-01, SC-04, SC-05, SC-06, SC-10
  - 관련 FR: FR-01
  - 구현 산출물: guard alias definition, channel/role/incident access test, guard failure no-write test
  - 예상 작업량: 1d
  - 완료 기준: `public-session`, `incident-read`, `web-command`, `app-police-phone`, `field-or-web-write`가 `spec/boundaries.md §7` guard shorthand와 일치하고, `write-common`은 S1-1 incident lifecycle과 S6 idempotency 계약으로 위임되며 guard 실패 시 domain row를 만들지 않는다.

- [x] L2-T03 단말 배정 fixture와 heartbeat 상태 보고 구현
  - 담당 Spec: S1-2
  - 필수 참조: `spec/specs/S1-2.json`, `spec/boundaries.md §3 S1-2`, `spec/boundaries.md §9.2 police_phone_freshness`
  - 연관 Spec: S3-1, S6, S7, S3-2
  - 시나리오: SC-03, SC-05, SC-07, SC-09
  - 관련 FR: FR-02, FR-24, FR-25
  - 구현 산출물: police_phone assignment fixture state, assignment guard/service, `POST /api/police-phones/{policePhoneId}/heartbeat` write path, `POLICE_PHONE_HEARTBEAT_UPDATED` publish request, PolicePhoneFreshnessQuery fixture
  - 예상 작업량: 2d
  - 완료 기준: 배정된 police_phone fixture는 package 조회와 현장 기록 write가 가능하고, 미배정 police_phone는 assignment guard로 실패하며, 새 public police_phone registration API 없이 heartbeat가 police_phone_freshness DTO에 반영된다.

- [x] L2-T05 S4 기본 이벤트 포맷 검증 구현
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/boundaries.md §4.4`, `spec/boundaries.md §9.1`, `spec/boundaries.md §10`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S5, S7, S8
  - 시나리오: SC-01 through SC-12 event paths
  - 관련 FR: FR-04, FR-10, FR-18, FR-24
  - 주의: S4 owns realtime/event delivery foundation only. Domain payload semantics stay with the payload owner Specs.
  - 구현 산출물: BaseEvent validator, schema version check, publish request mock contract, owner payload schema validation test, invalid envelope red test
  - 예상 작업량: 1d
  - 완료 기준: real `event_dispatch_job` 구현 전에도 모든 domain publish request가 mock contract에서 base envelope, schema version, id/status/version 필드, owner payload schema 기준으로 검증된다.

## Phase 2

- [x] L2-T06 이벤트 저장·발행 트랜잭션 경계 구현
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/boundaries.md §4.3`, `spec/boundaries.md §4.4`
  - 연관 Spec: S1-1, S2, S3-1, S5, S7, S8
  - 시나리오: SC-01, SC-04, SC-05, SC-06, SC-08, SC-10, SC-11, SC-12
  - 관련 FR: FR-04, FR-10, FR-18, FR-24
  - 선행 task: L2-T05
  - 구현 산출물: event_dispatch_job entity/migration, `EventHub.publish` port, publish transaction tests, outbox ownership tests
  - 예상 작업량: 2d
  - 완료 기준: domain transaction은 publish request를 원자적으로 stage할 수 있고, `event_dispatch_job` row와 dispatch state 소유권은 S4에 남는다.

- [x] L2-T07A SSE endpoint와 Last-Event-ID 재전송 구현
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`, `spec/harness-scenarios.md §6 mock SSE client`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S5, S6, S7, S8, S3-2
  - 시나리오: SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12
  - 관련 FR: FR-04, FR-10, FR-18, FR-24
  - 구현 산출물: Spring MVC `SseEmitter` 기반 `GET /api/incidents/{incidentId}/events` SSE endpoint, `sse_replay_event`, Last-Event-ID replay tests
  - 예상 작업량: 1d
  - 완료 기준: `GET /api/incidents/{incidentId}/events`가 Web/S3-2 consumer에 대해 live send와 `sse_replay_event` 기반 Last-Event-ID replay를 지원하고, Android APP channel은 SSE consumer가 아니다.

## Phase 3

- [x] L2-T07B SSE 중복·순서 뒤바뀜·board API refetch 복구 실패 주입 구현
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`, `spec/harness-scenarios.md §6 mock SSE client`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S5, S6, S7, S8, S3-2
  - 시나리오: SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12
  - 관련 FR: FR-04, FR-10, FR-18, FR-24
  - 선행 task: L2-T07A
  - 구현 산출물: duplicate event failure injection test, reordered event test, gone board API refetch recovery test
  - 예상 작업량: 1d
  - 완료 기준: duplicate/reordered event test와 gone board API refetch recovery check가 SSE replay path 기준으로 통과한다.

- [x] L2-T04 FCM 토큰 등록 fixture와 활성 토큰 조회 구현
  - 담당 Spec: S1-2
  - 필수 참조: `spec/specs/S1-2.json`, `spec/harness-scenarios.md §6 mock FCM recipient`
  - 연관 Spec: S5, S4
  - 시나리오: SC-02, SC-08
  - 관련 FR: FR-24
  - 지원 FR: FR-10, FR-17 through S5 marker_notification
  - 구현 산출물: FCM token registration fixture, `FcmTokenQuery.activeByPolicePhone(policePhoneId)` fixture, logout/token lifecycle fixture
  - 예상 작업량: 1d
  - 완료 기준: S5가 marker_notification를 위해 `FcmTokenQuery.activeByPolicePhone(policePhoneId)` fixture를 소비할 수 있고, L2는 incident-scoped unsubscribe/invalidate를 소유하지 않는다.

- [x] L2-T08 데이터 삭제 오케스트레이션과 운영 기록 구현
  - 담당 Spec: S1-3
  - 필수 참조: `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/boundaries.md §10 SC-12`
  - 연관 Spec: S1-1, S3-1, S5, S6, S7, S3-2
  - 시나리오: SC-12
  - 관련 FR: FR-22
  - 구현 산출물: incident_data_purge entity/migration, purge hook coordinator, location_data_access_audit/internal log sink contract, `INCIDENT_PURGED` publish request tests
  - 예상 작업량: 2d
  - 완료 기준: `INCIDENT_CLOSED`가 incident_data_purge를 시작하고 `spec/boundaries.md §4.5` lifecycle 순서로 hook을 조율하며, internal-only 운영 기록을 저장하고 모든 hook 성공 후에만 sanitized purge/tombstone state를 발행한다.

- [x] L2-T07C 사건 종료 SSE 재전송 중지와 stream 해제 검증 구현
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/harness-scenarios.md §2 SC-12`, `spec/harness-scenarios.md §6 mock SSE client`
  - 연관 Spec: S1-1, S1-3, S3-2
  - 시나리오: SC-12
  - 관련 FR: FR-04, FR-18, FR-22, FR-24
  - 선행 task: L2-T07A, L2-T08
  - 구현 산출물: incident stream unsubscribe test, post-purge replay stop test, `INCIDENT_CLOSED`/`INCIDENT_PURGED` SSE convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: `INCIDENT_CLOSED` 후 같은 사건 SSE stream은 해제되고, `INCIDENT_PURGED` 후 old replay data가 재전송되지 않으며, S3-2 board API response 수렴 evidence가 남는다.

## Phase 4

- [x] L2-T09A 인증·단말 모의 계약 하네스 작성
  - 담당 Spec: S1-2
  - 필수 참조: `spec/specs/S1-2.json`, `spec/harness-scenarios.md §6`, `spec/boundaries.md §7`
  - 연관 Lane: All lanes
  - 시나리오: auth/policePhone paths across SCs
  - 구현 산출물: auth/policePhone harness runner, role/channel mocks, police_phone assignment mocks, mock-to-real auth contract swap tests
  - 예상 작업량: 1d
  - 완료 기준: 다른 Lane이 L2 mock으로 auth/policePhone path를 실행하고 fixture ID 변경 없이 real S1-2 contract로 교체할 수 있다.

- [x] L2-T09B 이벤트 허브 모의 계약 하네스 작성
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`, `spec/harness-scenarios.md §6 mock event_dispatch_job`
  - 연관 Lane: All event-producing lanes
  - 시나리오: event paths across SCs
  - 구현 산출물: event hub harness runner, publish request mocks, event envelope swap tests, SSE mock replay evidence
  - 예상 작업량: 1d
  - 완료 기준: event-producing Lane이 S4 mock으로 실행되고 이후 real event_dispatch_job/SSE contract로 교체할 수 있다.

- [x] L2-T09C 삭제 오케스트레이션 모의 계약 하네스 작성
  - 담당 Spec: S1-3
  - 필수 참조: `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Lane: L1, L4, L5, L6
  - 시나리오: SC-12
  - 구현 산출물: purge orchestration harness runner, purge hook mocks, tombstone state fixture, mock-to-real purge swap tests
  - 예상 작업량: 1d
  - 완료 기준: close/purge 소비 Lane이 mock purge hook으로 실행되고 fixture ID 변경 없이 real S1-3 orchestration으로 교체할 수 있다.

- [x] L2-T10 인증·이벤트 부하 스모크 시나리오 작성
  - 담당 Spec: S1-2, S4
  - 필수 참조: `architecture.md §9`, `prd.md §2.2`, `spec/harness-scenarios.md`
  - 연관 Lane: L4, L6
  - 시나리오: SC-01 through SC-12 auth/event paths
  - 구현 산출물: k6 smoke scenario, auth/event path target list, threshold configuration
  - 예상 작업량: 1d
  - 완료 기준: k6 scenario와 threshold가 commit되어 합의된 auth/event path에 실행 가능하며, release evidence는 이 task에서 추가하지 않는다.

## Phase 5

- [x] L2-D01 CI 품질 게이트 결과 수집
  - 담당 Spec: S1-2, S1-3, S4
  - 필수 참조: `architecture.md §9`, `prd.md §2.2`
  - 연관 Lane: All lanes
  - 시나리오: release quality gate
  - 구현 산출물: CI quality gate job result, test/coverage/Sonar evidence, release evidence attachment
  - 예상 작업량: 1d
  - 완료 기준: CI가 test, coverage, Sonar gate 결과를 기록하고 MR 또는 release note에 evidence를 첨부한다.

- [ ] L2-D02 k6 부하 스모크 검증
  - 담당 Spec: S1-2, S4
  - 필수 참조: `architecture.md §9`, `prd.md §2.2`
  - 연관 Lane: L4, L6
  - 시나리오: SC-01 through SC-12 auth/event paths
  - 선행 task: L2-T10
  - 구현 산출물: load test result artifact, threshold summary, release evidence attachment
  - 예상 작업량: 1d
  - 완료 기준: 준비된 k6 smoke가 합의된 auth/event path에 대해 실행되고 pass/fail threshold evidence를 기록한다.

- [ ] L2-D03 S4 재전송·팬아웃 릴리즈 증거 작성
  - 담당 Spec: S4
  - 필수 참조: `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`
  - 연관 Lane: All event-consuming lanes
  - 시나리오: event paths across SCs
  - 구현 산출물: S4 replay smoke result, fanout smoke result, release evidence note
  - 예상 작업량: 1d
  - 완료 기준: S4 replay와 fanout smoke evidence가 event ID와 consumer convergence 결과와 함께 MR 또는 release note에 첨부된다.

## 담당하지 않음

- Domain entity writes outside S1-2/S1-3/S4
- Board shell or slot rendering
- Marker/photo/notification payload domain creation
- Offline package manifest/installation domain logic
