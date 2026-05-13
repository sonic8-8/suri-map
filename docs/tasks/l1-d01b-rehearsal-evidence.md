# L1-D01B 리허설 실행과 시연 증거 수집

## 범위

이 문서는 `L1-D01B` / `S14P31C106-295`의 Phase 5 evidence 문서다. `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md`를 실행 기준으로 삼고, PRD §2.3과 PRD §5.1의 12단계 중 SC-04~06에 해당하는 전체 수색 구역·구역 할당, 수색 경로·PolicePhone GPS 경로, 현장 마커 생성 증거를 기록한다.

증거는 backend 하네스와 fixture preflight 결과 기준이다. 실제 Android UI 입력, 실제 브라우저 상황판 스크린샷, 실제 1시간 안정성, 실제 네트워크 on/off 반복 검증은 이 문서에서 주장하지 않는다.

## 실행 메타

| 항목 | 값 |
|---|---|
| Task | `L1-D01B` |
| Jira | `S14P31C106-295` |
| Branch | `feature/S14P31C106-295-l1-d01b-rehearsal-evidence` |
| Base SHA | `fd8c513` |
| Evidence timestamp | `2026-05-13T14:58:10+09:00` |
| 기준 runbook | `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md` |
| 기준 문서 | PRD §2.3, PRD §5.1, `docs/spec/harness-scenarios.md` |
| Test XML directory | `backend/build/test-results/test/` |

## 실행한 증거 명령

| # | 명령 | 목적 | 결과 | 산출물 |
|---|---|---|---|---|
| 1 | `python3 docs/spec/fixtures/preflight_common_fixtures.py` | SC-04~06 공통 fixture ID와 alias가 기준 문서와 맞는지 사전 확인 | PASS, `common fixture preflight passed` | 콘솔 출력 |
| 2 | `cd backend && ./gradlew --no-daemon test --tests com.surimap.harness.sc04.Sc04SearchAreaHarnessTest --tests com.surimap.harness.sc05.Sc05PathSegmentHarnessRedTest --tests com.surimap.harness.sc06.Sc06MarkerPhotoHarnessRedTest --tests com.surimap.board.BoardApiSseConvergenceHarnessRedTest --console=plain` | SC-04 구역/배정, SC-05 path/freshness, SC-06 marker/photo, SC-04~06 board 수렴 하네스 증거 수집 | PASS, `BUILD SUCCESSFUL in 11s` | `backend/build/test-results/test/` |

선택 XML 증거:

| Test suite | 결과 |
|---|---|
| `TEST-com.surimap.harness.sc04.Sc04SearchAreaHarnessTest$OverallSearchAreaStep.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc04.Sc04SearchAreaHarnessTest$SearchAreaStep.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc04.Sc04SearchAreaHarnessTest$AssignmentStep.xml` | 6 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc04.Sc04SearchAreaHarnessTest$BoardConvergenceStep.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc05.Sc05PathSegmentHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc06.Sc06MarkerPhotoHarnessRedTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.BoardApiSseConvergenceHarnessRedTest.xml` | 5 tests, 0 failures, 0 errors |

## 공통 fixture ID 증거

| 항목 | 결과 |
|---|---|
| 대표 사건 | PASS. 사람이 읽는 fixture alias는 `inc-precinct-first-001`, 실제 DB/API 식별자 기준 UUID는 `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001`이다. |
| OP1 | PASS. fixture alias는 `op-precinct-001-op1`, 실제 UUID는 `88888888-8888-8888-8888-888888880001`이다. |
| 전체 수색 구역 | PASS. alias `osa-precinct-001-v1`, UUID `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001`, version `2`가 유지된다. |
| 단위 수색 구역 | PASS. alias `area-precinct-a1`, UUID `cccccccc-cccc-cccc-cccc-cccccccc0001`, version `1`이 유지된다. |
| 수색 경로 | PASS. alias `gps-path-normal-001` / `path-precinct-mixed-001` 기준으로 차량·도보 segment fixture가 고정된다. |
| 현장 마커 | PASS. marker alias `mk-precinct-clue-001`, marker UUID `55555555-5555-5555-5555-555555550001`, photo UUID `55555555-5555-5555-5555-555555550101`가 검증됐다. |
| board response | PASS. `bs-inc-precinct-first-001` board response에서 `overall_search_area`, `area`, `path`, `police_phone_freshness`, `marker` slot이 수렴한다. |

## SC-04 전체 수색 구역·구역 분할·할당 증거

| 확인 항목 | 결과 |
|---|---|
| overall_search_area | PASS. `overall_search_area` 생성 후 `ACTIVE`, version `2`로 조회되고 `SEARCH_AREA_CHANGED` event fixture `evt-s2-overall-area-001`와 연결된다. |
| search_area | PASS. `area-precinct-a1` 단위 구역이 현재 OP1 아래 `ACTIVE`, version `1`로 조회된다. |
| assignment | PASS. `saa-precinct-a1-001` 배정이 현재 OP 기준 `ACTIVE`로 조회되고 `SEARCH_AREA_ASSIGNMENT_CHANGED` event fixture `evt-s2-assignment-001`가 발행된다. |
| board 수렴 | PASS. `overall_search_area`와 `area` slot row가 source version 이상 board response version으로 수렴한다. |
| 금지 조건 | PASS. 이 증거는 수색 누락 자동 확정, 다음 구역 추천, 위험도 판단을 만들지 않는다. |

## SC-05 수색 경로·PolicePhone GPS 경로 증거

| 확인 항목 | 결과 |
|---|---|
| GPS batch fixture | PASS. `gps-path-normal-001`의 8개 point가 5초 간격 timestamp와 6자리 좌표 precision을 유지한다. |
| 차량·도보 자동 분리 | PASS. `seg-precinct-vehicle-001`은 index `0..3` `VEHICLE`, `seg-precinct-foot-001`은 index `4..7` `FOOT`으로 분리된다. |
| event/SSE | PASS. `evt-s3-path-started-001`, `evt-s3-path-appended-001`, `evt-s3-segment-updated-001`가 version `1`, `2`, `3` 순서로 SSE replay와 중복 억제 증거를 남긴다. |
| low-quality GPS | PASS. accuracy, clock skew, invalid speed, distance jump, invalid geometry fixture가 저장 완료 경로로 승격되지 않고 앱·상황판 low-quality/excluded state로 남는다. |
| police_phone_freshness | PASS. `dev-precinct-car-01` freshness row가 `ONLINE`, `STALE`, `LOST` 경계로 계산되고 board `police_phone_freshness` slot으로 수렴한다. |
| negative contract | PASS. 미등록 PolicePhone, invalid geometry, OP mismatch, event dispatch 누락, stale board response 실패 주입이 row/event/board 변경을 막거나 수렴 실패로 노출된다. |

## SC-06 현장 마커 생성 증거

| 확인 항목 | 결과 |
|---|---|
| marker 생성 | PASS. APP channel에서 marker UUID `55555555-5555-5555-5555-555555550001`가 OP1, account, PolicePhone, Point geometry 기준으로 생성된다. |
| photo upload/attach | PASS. mock object storage `mock://object-storage/suri-map-harness`와 upload URL `http://127.0.0.1:18080/mock-upload/{photoId}`만 사용하고 외부 S3 호출은 없다. |
| event/outbox/SSE | PASS. `MARKER_CREATED` 후 `MARKER_UPDATED` photo attach event `evt-s5-marker-updated-photo-001`가 event dispatch job과 SSE payload로 관찰된다. |
| board 수렴 | PASS. board `marker` slot이 marker/photo 상태 `ATTACHED`, marker version `2`, source hash `S5:marker:mk-precinct-clue-001:v2`로 수렴한다. |
| invalid Point | PASS. `coord-outside-envelope`는 `400 invalid_geometry`로 거부되고 marker row, photo row, event dispatch job, board response를 변경하지 않는다. |

## 실패 로그와 follow-up

| 구분 | failure log | follow-up fix task ID | 처리 |
|---|---|---|---|
| SC-04 failure log | 없음 | None | 구역/배정/board evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-05 failure log | 없음 | None | path/freshness/negative contract evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-06 failure log | 없음 | None | marker/photo/mock storage/board evidence가 PASS라 별도 결함 task를 만들지 않았다. |

## 결함 task 목록

| 결함 | Jira | 영향 증거 | 상태 |
|---|---|---|---|
| 없음 | N/A | SC-04~06 전체 | No defect task created |

## 남은 리스크

- 이 증거는 하네스/test-report evidence다. 실제 Android marker 입력 UI는 `L5-T01B`, 연결된 실기기 화면 검증은 `AUI-T14` 범위다.
- 실제 단말 2대와 상황판 1대의 1시간 안정성 및 네트워크 on/off 10회 검증은 `L4-D01` 범위다.
- 실제 브라우저 상황판 스크린샷과 사용자가 보는 지도 레이어 배치는 이 문서에서 주장하지 않는다.
- SC-06의 실제 S3 bucket 또는 Firebase delivery receipt는 범위 밖이다. mock object storage와 backend event/SSE/board 수렴만 증거로 사용했다.

## 완료 판정

Verdict: PASS.

완료 근거:

- SC-04 전체 수색 구역, 단위 구역, 담당 구역 배정, board `overall_search_area`/`area` 수렴 증거가 fixture ID와 함께 남았다.
- SC-05 수색 경로, 차량·도보 segment, PolicePhone freshness, event/SSE/board 수렴 증거가 fixture ID와 함께 남았다.
- SC-06 marker/photo 생성, mock object storage, event/SSE/board marker 수렴 증거가 fixture ID와 함께 남았다.
- SC-04~06 failure log가 모두 없음으로 기록됐고 follow-up fix task ID는 `None`이다.
- 결함 수정은 이 task에 섞지 않았고, 별도 결함 task는 생성하지 않았다.
