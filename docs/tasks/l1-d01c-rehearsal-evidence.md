# L1-D01C 리허설 실행과 시연 증거 수집

## 범위

이 문서는 `L1-D01C` / `S14P31C106-296`의 Phase 5 evidence 문서다. `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md`를 실행 기준으로 삼고, PRD §2.3과 PRD §5.1의 12단계 중 SC-07~09에 해당하는 통신 단절 중 로컬 기록, 지원 요청·실종자 발견 알림, 통신 복구·동기화 증거를 기록한다.

증거는 backend 하네스, Android unit/Room local sync 테스트, fixture preflight 결과 기준이다. 실제 단말 장시간 네트워크 on/off 반복, 실제 Firebase delivery receipt, 실제 OS notification 화면, 실제 브라우저 상황판 스크린샷은 이 문서에서 주장하지 않는다.

## 실행 메타

| 항목 | 값 |
|---|---|
| Task | `L1-D01C` |
| Jira | `S14P31C106-296` |
| Branch | `feature/S14P31C106-296-l1-d01c-rehearsal-evidence` |
| Base SHA | `b0a690b` |
| Evidence timestamp | `2026-05-13T15:09:17+09:00` |
| 기준 runbook | `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md` |
| 기준 문서 | PRD §2.3, PRD §5.1, `docs/spec/harness-scenarios.md` |
| Backend Test XML directory | `backend/build/test-results/test/` |
| Android Test XML directory | `android/app/build/test-results/testDebugUnitTest/` |

## 실행한 증거 명령

| # | 명령 | 목적 | 결과 | 산출물 |
|---|---|---|---|---|
| 1 | `python3 docs/spec/fixtures/preflight_common_fixtures.py` | SC-07~09 공통 fixture ID와 alias가 기준 문서와 맞는지 사전 확인 | PASS, `common fixture preflight passed` | 콘솔 출력 |
| 2 | `cd backend && ./gradlew --no-daemon test --tests com.surimap.harness.sc09.Sc07Sc09OfflineReplayHarnessRedTest --tests com.surimap.harness.sc08.Sc08NotificationHarnessRedTest --tests com.surimap.board.BoardApiSseConvergenceHarnessRedTest --console=plain` | SC-07 offline local capture, SC-08 notification/FCM/toast, SC-09 recovery replay/dedupe/board 수렴 하네스 증거 수집 | PASS, `BUILD SUCCESSFUL in 9s` | `backend/build/test-results/test/` |
| 3 | `cd android && GRADLE_USER_HOME=/home/seaung13/workspace/S14P31C106/.gradle-cache ./gradlew :app:testDebugUnitTest --tests com.surimap.testing.AndroidHarnessFixtureCatalogTest --tests com.surimap.testing.NetworkStateFixturesTest --tests com.surimap.feature.search.SearchPathLocalRecorderRoomTest --tests com.surimap.feature.marker.MarkerLocalRecorderRoomTest --tests com.surimap.core.sync.RoomLocalSyncServicesTest --tests com.surimap.feature.offline.OfflinePackageUiStateTest --console=plain` | Android fixture catalog, offline network fixture, Room-backed path/marker local recorder, Room outbox replay, package ready/stale UI state 증거 수집 | PASS, `BUILD SUCCESSFUL in 11s` | `android/app/build/test-results/testDebugUnitTest/` |

선택 XML 증거:

| Test suite | 결과 |
|---|---|
| `TEST-com.surimap.harness.sc09.Sc07Sc09OfflineReplayHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc08.Sc08NotificationHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.BoardApiSseConvergenceHarnessRedTest.xml` | 5 tests, 0 failures, 0 errors |
| `TEST-com.surimap.testing.AndroidHarnessFixtureCatalogTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.testing.NetworkStateFixturesTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.feature.search.SearchPathLocalRecorderRoomTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.feature.marker.MarkerLocalRecorderRoomTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.core.sync.RoomLocalSyncServicesTest.xml` | 11 tests, 0 failures, 0 errors |
| `TEST-com.surimap.feature.offline.OfflinePackageUiStateTest.xml` | 7 tests, 0 failures, 0 errors |

## 공통 fixture ID 증거

| 항목 | 결과 |
|---|---|
| 대표 사건 | PASS. fixture alias는 `inc-precinct-first-001`, 실제 DB/API 식별자 기준 UUID는 `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001`이다. |
| OP1 | PASS. fixture alias는 `op-precinct-001-op1`, 실제 UUID는 `88888888-8888-8888-8888-888888880001`이다. |
| offline network script | PASS. SC-07은 `net-script-domain-write-001`, SC-09는 `net-script-outbox-flush-001`를 사용한다. |
| outbox replay | PASS. path replay는 `outbox-path-001`, marker replay는 `outbox-marker-001`, photo replay는 `outbox-photo-001` fixture group에 연결된다. |
| 지원 요청 알림 | PASS. marker alias `mk-precinct-support-001`, marker UUID `55555555-5555-5555-5555-555555550801`, notification UUID `66666666-6666-6666-6666-666666660801`, event `evt-s5-support-request-001`가 유지된다. |
| 실종자 발견 알림 | PASS. marker alias `mk-precinct-person-found-001`, marker UUID `55555555-5555-5555-5555-555555550802`, notification UUID `66666666-6666-6666-6666-666666660802`, event `evt-s5-person-found-001`가 유지된다. |
| board response | PASS. `bs-inc-precinct-first-001` board response에서 `marker`, `toast`, `path`, `police_phone_freshness` slot이 수렴한다. |

## SC-07 통신 단절 중 로컬 기록 증거

| 확인 항목 | 결과 |
|---|---|
| offline capture | PASS. `net-script-domain-write-001` 단절 상태에서 path/marker local status가 `PENDING_LOCAL`, outbox status가 `PENDING_SEND`로 남는다. |
| 서버 side effect 차단 | PASS. offline 중 `serverPathRows=0`, `serverMarkerRows=0`, `boardRows=0`으로 서버 row/event/board 생성이 없다. |
| Android local persistence | PASS. Room-backed `SearchPathLocalRecorderRoomTest`와 `MarkerLocalRecorderRoomTest`가 path/marker local recorder의 pending outbox 저장을 검증했다. |
| Android network/package state | PASS. `NetworkStateFixturesTest`가 offline/restored 상태를 구분하고, `OfflinePackageUiStateTest`가 ready/partial/manifest changed/unavailable 상태를 offline-ready와 구분한다. |
| local warning boundary | PASS. 서버 의존 없이 Android local state와 Outbox 적재 상태를 분리하는 Room local sync 테스트가 통과했다. |

## SC-08 지원 요청·실종자 발견 알림 증거

| 확인 항목 | 결과 |
|---|---|
| 지원 요청 marker | PASS. `SUPPORT_REQUEST` marker 생성이 `SUPPORT_REQUEST_CREATED` event, FCM payload, board `toast` row로 수렴한다. |
| 실종자 발견 marker | PASS. `PERSON_FOUND` marker 생성이 `PERSON_FOUND` event, FCM payload, board `toast` row로 수렴한다. |
| FCM recipient | PASS. 지원 요청은 `COMMANDERS_AND_FIELD_COMMANDERS`, 실종자 발견은 `ALL_INCIDENT_ASSIGNED` recipient policy를 사용한다. |
| foreground/background | PASS. mock FCM dispatcher가 foreground/background data message capture와 duplicate event suppression을 검증한다. |
| board toast | PASS. board `marker` slot과 `toast` slot이 같은 eventId와 marker id/status/version을 기준으로 수렴하고 stale/duplicate toast ledger를 거부한다. |
| external FCM boundary | PASS. 실제 외부 FCM adapter 주입은 mock boundary에서 거부되고 `externalFcmCalled=false`가 유지된다. |

## SC-09 통신 복구·동기화 증거

| 확인 항목 | 결과 |
|---|---|
| outbox flush | PASS. `net-script-outbox-flush-001` 복구 후 path/marker outbox status가 `ACKED`, local status가 `SYNCED`로 전환된다. |
| owner endpoint replay | PASS. path owner와 marker owner는 각각 1회만 호출되고 event job도 각각 1건만 생성된다. |
| duplicate replay | PASS. 동일 idempotency key/bodyHash 재전송은 cached response replay로 처리되어 domain row와 event job이 중복 생성되지 않는다. |
| board convergence | PASS. board `path`, `marker`, `police_phone_freshness` slot이 source version 이상으로 수렴한다. |
| stale refetch | PASS. board refetch lag는 `STALE_REFETCH`로 노출되고, 최신 version 수렴 전 stale response가 최신 row를 덮지 못한다. |
| Android Room replay | PASS. `RoomLocalSyncServicesTest`가 pending -> acked, mismatch final failure, pending local replay block, requeue/purge 경계를 검증했다. |

## 실패 로그와 follow-up

| 구분 | failure log | follow-up fix task ID | 처리 |
|---|---|---|---|
| SC-07 failure log | 없음 | None | offline local capture와 Android Room local evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-08 failure log | 없음 | None | notification/FCM/toast evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-09 failure log | 없음 | None | outbox recovery/dedupe/board convergence evidence가 PASS라 별도 결함 task를 만들지 않았다. |

## 결함 task 목록

| 결함 | Jira | 영향 증거 | 상태 |
|---|---|---|---|
| 없음 | N/A | SC-07~09 전체 | No defect task created |

## 남은 리스크

- 이 증거는 하네스/test-report evidence다. 실제 단말 2대와 상황판 1대의 1시간 안정성 및 네트워크 on/off 10회 검증은 `L4-D01` 범위다.
- Android OS notification 화면, 실제 FCM delivery receipt, 실제 background process 동작은 이 문서에서 주장하지 않는다.
- 실제 브라우저 상황판 toast/path/marker 렌더링 스크린샷은 이 문서에서 주장하지 않는다.
- SC-07/09의 30분 경과는 하네스 fixture와 Room 상태 전이 증거이며, 실제 30분 wall-clock 테스트는 `L4-D01` 범위다.

## 완료 판정

Verdict: PASS.

완료 근거:

- SC-07 통신 단절 중 local path/marker/outbox 생존성과 서버 side effect 없음 증거가 fixture ID와 함께 남았다.
- SC-08 지원 요청·실종자 발견 marker, mock FCM, board `marker`/`toast` 수렴 증거가 fixture ID와 함께 남았다.
- SC-09 outbox flush, duplicate replay, stale refetch, board `path`/`marker`/`police_phone_freshness` 수렴 증거가 fixture ID와 함께 남았다.
- Android Room local recorder/outbox replay/package state의 핵심 테스트 evidence가 함께 남았다.
- SC-07~09 failure log가 모두 없음으로 기록됐고 follow-up fix task ID는 `None`이다.
- 결함 수정은 이 task에 섞지 않았고, 별도 결함 task는 생성하지 않았다.
