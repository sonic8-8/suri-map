# L4 Tasks · Path, Outbox, Offline Sync

상태: 초안. 담당 Lane: L4. 담당 Spec: S3-1, S6. 주요 SC: SC-05, SC-07, SC-09.

## 작업 범위

- search_path lifecycle
- GPS batch, search_path, search_path_segment, vehicle/foot split
- local store, Android Outbox, idempotency, retry/replay
- clock sync, local warning, offline recovery

## 외부 계약

- Consumes L2/S1-2 auth/policePhone and L3/S2 overall_search_area query.
- Consumes L3/S8 current OP query.
- Publishes through L2/S4 event contract.
- Provides path read/query data to L6/S3-2 and L3/S8.
- Provides local warning from L6/S7 offline_package_installation status without owning package domain.

## Phase -1

- [x] L4-B01 Android 실행 기반과 오프라인 테스트 기반 준비
  - 담당 Spec: S3-1, S6
  - 필수 참조: `architecture.md §3`, `adr.md ADR-0003`, `adr.md ADR-0035`, `spec/specs/S3-1.json`, `spec/specs/S6.json`
  - 연관 Spec: S1-2, S7
  - 시나리오: SC-05, SC-07, SC-09
  - 구현 산출물: Gradle Kotlin DSL 기반 AGP 8.13.x Android project base, minSdk 31/targetSdk 34 설정, Room schema test harness, Robolectric test baseline, WorkManager deterministic mode, real hardware smoke checklist, mock network state fixture, empty local write interface stubs
  - 예상 작업량: 3d+
  - 완료 기준: feature behavior를 구현하지 않은 상태에서 Gradle Kotlin DSL Android project base, Room schema test harness, Robolectric baseline, WorkManager deterministic mode, network state fixture, 빈 local write interface가 server integration 전에 실행되고 real hardware smoke 실행 경로가 문서화된다.

## Phase 0

- [x] L4-T04A GPS 품질 실패 고정 데이터와 검증 기준 작성
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 mock GPS 경로`
  - 연관 Spec: S2
  - 시나리오: SC-05
  - 구현 산출물: batch limit red tests, bbox/coordinate/timestamp/speed/jump failure fixtures, GPS quality contract fixture
  - 예상 작업량: 1d
  - 완료 기준: validator 구현 전에 GPS max batch, bbox, coordinate order, timestamp skew, accuracy, speed, jump 실패 조건이 failing test 또는 fixture로 고정된다.

- [x] L4-T07A 클라이언트-서버 시각 보정 API 계약 테스트 작성
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-07, SC-09
  - 구현 산출물: `POST /api/sync/clock` contract test, client/server time offset fixture, time skew red tests
  - 예상 작업량: 1d
  - 완료 기준: endpoint behavior를 구현하지 않은 상태에서 sync clock 기대 조건이 failing contract test로 고정된다.

- [x] L4-T07B Outbox 재시도 진단 고정 데이터와 실패 테스트 작성
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-07, SC-09
  - 구현 산출물: retryable/terminal state red tests, diagnostic fixture, requeue diagnostics contract fixture
  - 예상 작업량: 1d
  - 완료 기준: diagnostic 구현 전에 retryable/terminal diagnostic 기대 조건이 failing test 또는 fixture로 고정된다.

## Phase 1

- [x] L4-T01 수색 경로 상태 흐름 구현
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `spec/specs/S8.json`, `spec/harness-scenarios.md §2 SC-05`
  - 연관 Spec: S1-2, S8, S4, S3-2
  - 시나리오: SC-05
  - 관련 FR: FR-02, FR-34
  - 구현 산출물: search_path lifecycle API, current OP/police_phone guard tests, `SEARCH_PATH_STARTED` and `SEARCH_PATH_ENDED` PublishRequest contract tests
  - 예상 작업량: 2d
  - 완료 기준: app PolicePhone이 current OP search_path를 start/end할 수 있고, invalid OP/police_phone는 실패하며, search_path lifecycle PublishRequest가 안정적인 id/status/version/opId/policePhoneId를 포함한다.

- [x] L4-T04B 경로 도형과 GPS 품질 검증 구현
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `spec/boundaries.md §4.1.1`, `spec/harness-scenarios.md §6 mock GPS 경로`
  - 연관 Spec: S2
  - 시나리오: SC-05
  - 구현 산출물: GPS quality validator, batch limit tests, bbox/coordinate/timestamp/speed/jump validation tests
  - 예상 작업량: 1d
  - 완료 기준: GPS max batch, bbox, coordinate order, timestamp skew, accuracy, speed, jump filter가 `spec/harness-scenarios.md` fixture를 따른다.

- [x] L4-T02 GPS 일괄 저장과 경로 조회 구현
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `spec/boundaries.md §10 SC-05`, `spec/boundaries.md §10 SC-09`
  - 연관 Spec: S2, S8, S4, S3-2
  - 시나리오: SC-05, SC-09
  - 관련 FR: FR-02, FR-04, FR-25
  - 구현 산출물: `POST /api/search-paths/batch`, search_path/search_path_segment persistence, `GET /api/search-paths`, `PATH_APPENDED` publish request tests
  - 예상 작업량: 2d
  - 완료 기준: 유효한 batch가 id/status/version를 가진 LineString/path point를 append하고, `GET /api/search-paths`가 incident/OP/police_phone filter 결과를 반환한다.

## Phase 2

- [x] L4-T03 차량·도보 구간 분류와 보정 구현
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `prd.md FR-33`, `prd.md FR-35`, `spec/harness-scenarios.md §6 mock GPS 경로`
  - 연관 Spec: S1-2, S3-2
  - 시나리오: SC-05, SC-11
  - 관련 FR: FR-33, FR-35
  - 구현 산출물: segment classifier, web correction API for existing segment type, `SEARCH_PATH_SEGMENT_UPDATED` PublishRequest contract test, low-quality GPS exclusion tests
  - 예상 작업량: 1d
  - 완료 기준: speed fixture가 VEHICLE/FOOT segment를 만들고, low-quality GPS는 승격되지 않으며, web correction은 기존 movement_type만 수정하고, `SEARCH_PATH_SEGMENT_UPDATED`는 안정적인 id/status/version/opId/policePhoneId를 포함한다.

- [x] L4-T05A Android 로컬 저장소와 경로·마커·패키지 복제 스키마 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S3-1, S5, S7
  - 시나리오: SC-07, SC-09
  - 관련 FR: FR-03, FR-28
  - 구현 산출물: Room local store schema, local path/marker/package mirror schema, local mirror tests
  - 예상 작업량: 1d
  - 완료 기준: offline path/marker/offline_package_installation status write가 replay 전 server row를 만들지 않고 local mirror table에 저장된다.
  - 완료 근거: `backend/src/test/java/com/surimap/sync/localstore/LocalMirrorSchemaContractTest.java` (S6 local schema, SC-07/09 outbox replay fixture, offline `PENDING_LOCAL`/`PENDING_SEND` 고정 검증)

- [x] L4-T05B Android Outbox 스키마와 재시도 상태 흐름 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S3-1, S5, S7
  - 시나리오: SC-07, SC-09
  - 관련 FR: FR-03, FR-28
  - 구현 산출물: Android Outbox schema, `PENDING`/`SENDING`/`ACKED`/`FAILED_RETRYABLE`/`FAILED_FINAL`/`PURGED` retry state machine, `PENDING_LOCAL`/`PENDING_SEND`/`SENDING`/`SYNCED`/`FAILED`/`PURGED` UI 상태 test
  - 예상 작업량: 1d
  - 완료 기준: local write가 S6 기준 Outbox 상태와 harness UI 상태를 그대로 거치며, 중복 server row 없이 replay 가능하다.
  - 완료 근거: `android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt`, `android/app/src/main/java/com/surimap/core/sync/OutboxStateMachine.kt`, `android/app/src/test/java/com/surimap/core/sync/RoomLocalSyncServicesTest.kt`, `android/app/src/test/java/com/surimap/core/sync/OutboxStateMachineTest.kt`, `android/app/src/test/java/com/surimap/core/sync/OutboxIdempotencyReplayGateTest.kt`

## Phase 3

- [x] L4-T07C 클라이언트-서버 시각 보정 API 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-07, SC-09
  - 구현 산출물: `POST /api/sync/clock`, client/server time offset record, time skew tests
  - 예상 작업량: 1d
  - 완료 기준: client/server time offset이 기록되고 owner endpoint를 우회하지 않은 채 offline retry logic에 노출된다.
  - 완료 근거: `backend/src/main/java/com/surimap/sync/clock/SyncClockController.java`, `backend/src/test/java/com/surimap/sync/clock/SyncClockContractTest.java` (`channel_not_allowed`/`police_phone_required`/`police_phone_not_registered`/`police_phone_not_assigned` guard 포함), `backend/AGENTS.md` 기본 검증 `./gradlew test` 통과

- [x] L4-T07D Outbox 재시도 진단 상태 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/harness-scenarios.md §6 mock network 상태`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-07, SC-09
  - 구현 산출물: requeue diagnostics endpoint/state, retryable/terminal state tests, diagnostic fixture
  - 예상 작업량: 1d
  - 완료 기준: requeue diagnostics가 owner endpoint를 우회하지 않고 retryable/terminal local state를 노출한다.
  - 완료 근거: `backend/src/main/java/com/surimap/sync/outbox/OutboxRequeueController.java`, `backend/src/main/java/com/surimap/sync/outbox/OutboxRequeueExceptionHandler.java`, `backend/src/test/java/com/surimap/sync/outbox/OutboxRequeueContractRedTest.java`, `backend/src/test/java/com/surimap/sync/outbox/OutboxRetryDiagnosticsFixtureTest.java`

- [x] L4-T06 서버 멱등 처리와 응답 재사용 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/boundaries.md §4.3`, `spec/harness-scenarios.md §2 SC-09`
  - 연관 Spec: S2, S3-1, S5, S7, S8
  - 시나리오: SC-09
  - 관련 FR: FR-03, FR-28
  - 구현 산출물: `@IdempotentWrite` middleware, idempotency response cache, bodyHash mismatch tests, committed-cache-missing recovery tests
  - 예상 작업량: 2d
  - 완료 기준: 같은 idempotency key/body는 cached response를 replay하고, body가 바뀌면 mismatch를 반환하며, committed-cache-missing 복구는 owner port를 호출한다.
  - 완료 근거: `backend/src/main/java/com/surimap/sync/idempotency/IdempotentWrite.java`, `backend/src/main/java/com/surimap/sync/idempotency/IdempotentWriteAspect.java`, `backend/src/main/java/com/surimap/sync/idempotency/IdempotentWriteService.java`, `backend/src/main/java/com/surimap/sync/idempotency/InMemoryIdempotencyRecordRepository.java`, `backend/src/main/java/com/surimap/sync/idempotency/IdempotencyReplayRecoveryRegistry.java`, `backend/src/test/java/com/surimap/sync/idempotency/IdempotentWriteServiceBehaviorRedTest.java`, `./gradlew test --tests '*IdempotentWriteServiceBehaviorRedTest'` 통과

- [x] L4-T08 로컬 경고 감시 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/specs/S7.json`, `prd.md FR-29`
  - 연관 Spec: S1-2, S7
  - 시나리오: SC-07
  - 관련 FR: FR-29
  - 구현 산출물: local warning monitor, package unavailable/stale input adapter, Android warning UI state tests
  - 예상 작업량: 1d
  - 완료 기준: GPS stopped, battery low, offline, package unavailable/stale warning이 server round trip 없이 local에서 동작한다.
  - 완료 증거: `android/app/src/main/java/com/surimap/core/sync/LocalWarningMonitor.kt`, `android/app/src/test/java/com/surimap/core/sync/LocalWarningMonitorTest.kt`, `android/app/src/test/java/com/surimap/core/sync/LocalWarningUiStateTest.kt`; `cmd.exe /c gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.surimap.core.sync.LocalWarningMonitorTest --tests com.surimap.core.sync.LocalWarningUiStateTest` PASS, `cmd.exe /c gradlew.bat --no-daemon :app:assembleDebug` PASS.

- [x] L4-T09 사건 종료·삭제 로컬 정리 정책 구현
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Spec: S1-1, S1-3, S7
  - 시나리오: SC-12
  - 관련 FR: FR-03, FR-28
  - 지원 FR: FR-22 through local close/purge cleanup
  - 선행 task: L1-T06, L2-T08
  - 구현 산출물: local close/purge state policy, post-close requeue rejection, incident-scoped local cleanup test, ack-only deletion, tombstone retention tests
  - 예상 작업량: 2d
  - 완료 기준: pre-close pending row, post-close requeue rejection, incident-scoped local cleanup, ack-only deletion, tombstone retention, package/missing_person cleanup 순서가 S1-3 handoff와 일치한다.
  - 완료 근거: Android `LocalSyncPurgeHookAdapter`/`RoomSyncClient`/`RoomOutboxReplay`에 close cutoff, post-close write/requeue rejection, ACKED-only purge, retained tombstone reporting, package/missing_person handoff order를 구현했고 `IncidentLocalCleanupPolicyTest`로 검증했다. Windows SDK 기준 `cmd.exe /c gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.surimap.core.sync.IncidentLocalCleanupPolicyTest` 및 `cmd.exe /c gradlew.bat --no-daemon :app:assembleDebug` 통과.

## Phase 4

- [x] L4-T10A 경로·구간 고정 데이터 하네스 작성
  - 담당 Spec: S3-1
  - 필수 참조: `spec/specs/S3-1.json`, `spec/specs/S1-2.json`, `spec/specs/S3-2.json`, `spec/harness-scenarios.md §2 SC-05`, `spec/harness-scenarios.md §6 mock GPS 경로`
  - 연관 Spec: S1-2, S2, S4, S8, S3-2
  - 시나리오: SC-05
  - 구현 산출물: path harness runner, path/segment fixture tests, GPS quality fixture tests, `PolicePhoneFreshnessQuery.byIncident` fixture consumption, S3-2 `path`/`police_phone_freshness` convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: path/segment fixture test가 auth, geometry, OP, event mock contract로 통과하고, S1-2 freshness DTO의 lastHeartbeatAt/lastSyncAt/derivedFreshness가 S3-2 `police_phone_freshness` slot row와 수렴한다.

- [x] L4-T10B 오프라인 재전송·중복 방지 하네스 작성
  - 담당 Spec: S6
  - 필수 참조: `spec/specs/S6.json`, `spec/specs/S1-2.json`, `spec/specs/S3-2.json`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §2 SC-09`
  - 연관 Spec: S1-2, S4, S7, S3-2
  - 시나리오: SC-07, SC-09
  - 구현 산출물: offline/recovery harness runner, duplicate replay tests, board API refetch lag checks, mocked owner endpoint fixtures, `police_phone_freshness` recovery convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: offline, recovery, duplicate replay, board API refetch lag check가 mocked owner endpoint로 통과하고, 복구 후 S3-2 `path`/`marker`/`police_phone_freshness` slot이 기대 version로 수렴한다.
  - 완료 근거: `backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRedTest.java`, `backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRunner.java` (SC-07 offline pending, SC-09 recovery flush, duplicate replay dedupe, board refetch lag, mocked owner recovery convergence)

- [x] L4-T10C 네트워크 전환 안정성 검증 프로토콜 작성
  - 담당 Spec: S3-1, S6
  - 필수 참조: `prd.md §2.2`, `prd.md §2.3`, `spec/harness-scenarios.md §2 SC-05`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §2 SC-09`
  - 연관 Spec: S1-2, S4, S5, S7
  - 시나리오: SC-05, SC-07, SC-09
  - 구현 산출물: 1-hour stability checklist, network on/off 10-cycle script or manual protocol, duplicate row verification query
  - 예상 작업량: 1d
  - 완료 기준: Phase 5 실행 전에 stability checklist, network switch protocol, duplicate row verification query가 준비된다.
  - 완료 근거: `docs/tasks/l4-network-switch-stability-protocol.md`, `docs/tasks/check_l4_network_switch_stability_protocol.py`

## Phase 5

- [ ] L4-D01 1시간 안정성과 네트워크 전환 검증
  - 담당 Spec: S3-1, S6
  - 필수 참조: `prd.md §2.2`, `prd.md §2.3`, `spec/harness-scenarios.md §2 SC-05`, `spec/harness-scenarios.md §2 SC-07`, `spec/harness-scenarios.md §2 SC-09`
  - 연관 Lane: L2, L5, L6
  - 시나리오: SC-05, SC-07, SC-09
  - 선행 task: L4-T10C
  - 구현 산출물: 1-hour stability run result, network on/off 10-cycle result, duplicate row verification result, convergence evidence
  - 예상 작업량: 3d+
  - 완료 기준: Android PolicePhone 2대와 board 1개가 1시간 실행되고, network on/off가 10회 반복되며, path/marker/package local state가 수렴하고 duplicate server row가 0건으로 유지된다.

## 담당하지 않음

- Overall search area/search area domain writes
- OP lifecycle and assignment
- Marker/photo domain writes
- Offline package manifest/installation owner logic
- S4 event dispatch implementation
- S3-2 board rendering
