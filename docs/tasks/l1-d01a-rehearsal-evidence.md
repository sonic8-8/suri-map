# L1-D01A 리허설 실행과 시연 증거 수집

## 범위

이 문서는 `L1-D01A` / `S14P31C106-201`의 Phase 5 evidence 문서다. `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md`를 실행 기준으로 삼고, PRD §2.3과 PRD §5.1의 12단계 중 SC-01~03에 해당하는 배정 사건 가져오기, 실종팀 인계·지원 부대 배정, 사건 오프라인 패키지 사전 적재 증거를 기록한다.

증거는 backend 하네스와 fixture preflight 결과 기준이다. 실제 경찰 112/실종프로파일링, 실제 FCM, 실제 외부 타일 네트워크, 실제 단말 장시간 안정성은 이 문서에서 주장하지 않는다.

## 실행 메타

| 항목 | 값 |
|---|---|
| Task | `L1-D01A` |
| Jira | `S14P31C106-201` |
| Branch | `feature/S14P31C106-201-rehearsal-evidence` |
| Base SHA | `a8208c9` |
| Evidence timestamp | `2026-05-11T08:25:25+09:00` |
| 기준 runbook | `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md` |
| 기준 문서 | PRD §2.3, PRD §5.1, `docs/spec/harness-scenarios.md` |
| Test XML directory | `backend/build/test-results/test/` |

## 실행한 증거 명령

| # | 명령 | 목적 | 결과 | 산출물 |
|---|---|---|---|---|
| 1 | `python3 docs/spec/fixtures/preflight_common_fixtures.py` | SC-01~03 공통 fixture ID가 기준 문서와 맞는지 사전 확인 | PASS, `common fixture preflight passed` | 콘솔 출력 |
| 2 | `cd backend && ./gradlew test --tests com.surimap.harness.sc01.Sc01IncidentStartIntegrationTest --tests com.surimap.harness.sc02.Sc02HandoverSupportAssignmentIntegrationTest --tests com.surimap.harness.sc02.Sc02SupportAssignmentFcmHarnessRedTest --tests com.surimap.offlinepackage.Sc03PackageTileHarnessRedTest --tests com.surimap.board.BoardApiSseConvergenceHarnessRedTest` | SC-01 import, SC-02 인계·지원 배정/FCM, SC-03 package/tile, SC-02~12 board 수렴 하네스 증거 수집 | PASS, `BUILD SUCCESSFUL in 30s` | `backend/build/test-results/test/` |

선택 XML 증거:

| Test suite | 결과 |
|---|---|
| `TEST-com.surimap.harness.sc01.Sc01IncidentStartIntegrationTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc02.Sc02HandoverSupportAssignmentIntegrationTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc02.Sc02SupportAssignmentFcmHarnessRedTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.offlinepackage.Sc03PackageTileHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.BoardApiSseConvergenceHarnessRedTest.xml` | 5 tests, 0 failures, 0 errors |

## 공통 fixture ID 증거

| 항목 | 결과 |
|---|---|
| 대표 사건 | PASS. `incidentId=inc-precinct-first-001` |
| mock 112 원천 | PASS. `sourceIncidentId=00000000-0000-0000-0000-000000000001` |
| OP1 | PASS. `opId=op-precinct-001-op1` |
| 초기 마커 | PASS. `mk-precinct-clue-001` |
| 초동 차량 경로 | PASS. `path-precinct-car-001` |
| 초동 인수인계 메모 | PASS. `memo-precinct-handover-001` |
| board response | PASS. `bs-inc-precinct-first-001` |

## SC-01 배정 사건 가져오기 증거

| 확인 항목 | 결과 |
|---|---|
| 사건 가져오기 | PASS. `00000000-0000-0000-0000-000000000001` 원천 import가 `inc-precinct-first-001` 사건을 `OPEN` 상태로 구성한다. |
| OP1 자동 생성 | PASS. 같은 import 흐름에서 `op-precinct-001-op1` 생성과 `OP_TRANSITIONED(from=null)` publish request stage가 검증됐다. |
| 참여 계정/초기 마커 | PASS. 지구대/파출소 지휘·순찰차·팀 계정 배정과 `mk-precinct-clue-001` 초기 마커 fixture가 유지된다. |
| 중복 import 방지 | PASS. 같은 sourceIncidentId와 idempotency 기준으로 사건, OP, assignment가 중복 생성되지 않는 계약을 검증했다. |

## SC-02 실종팀 인계·지원 부대 배정 증거

| 확인 항목 | 결과 |
|---|---|
| 실종팀 인계 | PASS. `00000000-0000-0000-0000-000000000001:cmd-alpha`, `00000000-0000-0000-0000-000000000001:team-alpha`가 같은 사건에 추가되고 기존 OP1 기록이 보존된다. |
| 지원 부대 배정 | PASS. `00000000-0000-0000-0000-000000000001:support-cmd`, `00000000-0000-0000-0000-000000000001:support-car`, `00000000-0000-0000-0000-000000000001:support-team` 반영 후 `ia-precinct-support-car-001=ACTIVE`를 포함한 지원 배정 row가 활성 상태로 남는다. |
| OP1 기록 보존 | PASS. `path-precinct-car-001`, `path-precinct-foot-001`, `memo-precinct-handover-001`가 인계 후에도 같은 사건·OP1 소속으로 유지된다. |
| FCM evidence | PASS. mock FCM recipient `fcm:dev-support-car-01`, `fcm:dev-support-phone-01`, `fcm:dev-alpha-phone-01` 경계에서 개인정보 없는 배정 알림 payload가 검증됐다. |
| board 수렴 | PASS. `bs-inc-precinct-first-001` board response에서 `handover_status`, `op_history`, `marker`, `path` 소비 경계가 최신 source evidence를 따른다. |

## SC-03 사건 오프라인 패키지 사전 적재 증거

| 확인 항목 | 결과 |
|---|---|
| manifest | PASS. `tileManifestId=tile-manifest-inc-precinct-001`가 사건 메타, OP, 실종자 기본 정보, 전체 수색 구역, 초기 마커, 타일 목록을 포함한다. |
| 전체 수색 구역 hash | PASS. `overall-area-hash-precinct-current` 기준으로 stale manifest와 현재 manifest가 구분된다. |
| local tile URI | PASS. `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf` 경로와 `z=15..16`, `x=27925..27960`, `y=12680..12720` 타일 범위를 사용한다. |
| 재시도 script | PASS. `net-script-manifest-001`, `net-script-tile-blob-001`로 manifest/tile 실패와 재시도 흐름을 재현한다. |
| package_badge | PASS. `pkg-inc-precinct-first-001`, `board-package-inc-precinct-first-001`가 board `package_badge` evidence로 수렴한다. |
| 외부 타일 경계 | PASS. 하네스 증거는 local fixture만 사용하며 `*.tile.openstreetmap.org`, `*.mapbox.com`, `*.googleapis.com` 외부 타일 호출을 필요로 하지 않는다. |

## 실패 로그와 follow-up

| 구분 | failure log | follow-up fix task ID | 처리 |
|---|---|---|---|
| import failure log | 없음 | None | SC-01 import/OP1/assignment evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| handover failure log | 없음 | None | SC-02 인계·지원 배정/FCM/board evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| package failure log | 없음 | None | SC-03 manifest/tile/package_badge evidence가 PASS라 별도 결함 task를 만들지 않았다. |

## 결함 task 목록

| 결함 | Jira | 영향 증거 | 상태 |
|---|---|---|---|
| 없음 | N/A | SC-01~03 전체 | No defect task created |

## 남은 리스크

- 이 증거는 하네스/test-report evidence다. 실제 단말 2대와 상황판 1대의 1시간 안정성, 네트워크 on/off 10회 검증은 L4-D01 범위다.
- SC-03의 실제 앱 다운로드 화면, 브라우저/프록시 기반 네트워크 캡처, 실제 외부 지도 차단 packet capture는 이 문서에서 주장하지 않는다. 이 문서는 fixture와 하네스 경계가 외부 타일 호출 없이 구성됐음을 기록한다.
- SC-02의 실제 Firebase delivery receipt는 범위 밖이다. mock FCM dispatcher capture만 증거로 사용했다.

## 완료 판정

Verdict: PASS.

완료 근거:

- SC-01 배정 사건 가져오기와 OP1 자동 생성 증거가 fixture ID와 함께 남았다.
- SC-02 실종팀 인계·지원 부대 배정, OP1 기록 보존, FCM recipient 증거가 fixture ID와 함께 남았다.
- SC-03 오프라인 패키지 manifest, local tile, package_badge 증거가 fixture ID와 함께 남았다.
- import/handover/package failure log가 모두 없음으로 기록됐고 follow-up fix task ID는 `None`이다.
- 결함 수정은 이 task에 섞지 않았고, 별도 결함 task는 생성하지 않았다.
