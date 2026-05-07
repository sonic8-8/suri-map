# Common Fixture Catalog

`docs/spec/fixtures`는 L1~L6가 같은 기준 데이터로 SC-01~SC-12 하네스/통합/E2E 테스트를 시작할 수 있도록 만든 공통 fixture 카탈로그와 중립 데이터 세트다.

이 디렉터리는 Lane 전용 테스트 코드나 mock adapter를 두지 않는다. 기준 문서에 이미 있는 fixture ID와 payload만 모으고, 문서끼리 충돌하거나 미정인 값은 `pending-confirmation.json`으로 분리한다.

## Source Of Truth

- `common-fixtures.json`은 **확정된 공통 값만** 담는다.
- `pending-confirmation.json`은 문서 간 충돌값과 미정값을 담는다.
- lane 전용 fixture 코드는 이 값을 소비하거나 재표현할 수는 있어도, 새로운 기준값을 만들면 안 된다.
- `boardAssembly.latestEventId`는 S3-2 board probe source를 그대로 유지하고, S8 owner fixture의 domain event 기대값은 `opTransition`/`searchHistorySummary.expectedS4Events`에 분리해 둔다.

## 파일 구성

- `common-fixtures.json`: 확정된 공통 fixture 실행 데이터 세트
- `pending-confirmation.json`: 기준 문서에 값이 없거나 source 간 충돌이 남아 확정 못 한 항목
- `preflight_common_fixtures.py`: JSON 파싱, catalog/README 동기화, 필수 section, fixture ID owner path, pending guard, cross-reference, status/error/failure key 검증

## Confirmed Catalog

| fixture ID | 용도 | 사용 SC | 사용 Lane | 기준 문서 | 데이터 파일 | 로더/사용 규칙 | 소유 구분 | 비고 |
|---|---|---|---|---|---|---|---|---|
| `inc-precinct-first-001` | 대표 초동 사건 seed | SC-01, SC-02, SC-10, SC-12 | L1~L6 | `harness-scenarios.md §6`, `S1-1.json` | `common-fixtures.json` | incident seed를 먼저 로드 | 공통 데이터 | mock 112 payload shape는 pending으로 분리 |
| `op-precinct-001-op1` | 대표 현재 차수(OP1) | SC-01, SC-05, SC-07, SC-10 | L1, L3, L4, L5, L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | incident seed 다음에 로드 | 공통 데이터 | board probe row와 같이 검증 |
| `op-precinct-001-op2` | OP 전환 후 차수(OP2) | SC-10, SC-11 | L1, L3, L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | OP transition fixture 단계에서 로드 | 공통 데이터 | board projection/latestEventId와 domain event 기대값을 분리 유지 |
| `gps-path-normal-001` | 차량/도보 혼합 GPS 경로 | SC-05, SC-07, SC-09, SC-11 | L3, L4, L6 | `harness-scenarios.md §6`, `S3-1.json` | `common-fixtures.json` | geometry seed 후 로드 | 공통 데이터 | point/segment ID를 바꾸지 않는다 |
| `tile-manifest-inc-precinct-001-v1` | 오프라인 타일 manifest 식별자 | SC-03, SC-04, SC-09 | L4, L6 | `harness-scenarios.md §6`, `S7.json` | `common-fixtures.json` | tile fixture 단계에서 로드 | 공통 데이터 | `overallAreaHash`는 pending으로 분리 |
| `net-script-manifest-001` | manifest 복구 스크립트 | SC-03 | L4, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | `MANIFEST_READY`/`MANIFEST_FAILED_RETRYABLE` 기대값 포함 |
| `net-script-tile-blob-001` | tile blob fetch/복구 스크립트 | SC-03, SC-04 | L4, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | `READY`/`FAILED_RETRYABLE`/`FAILED_CORRUPT` 기대값 포함 |
| `net-script-domain-write-001` | 오프라인 domain write/복구 스크립트 | SC-07, SC-09 | L4, L5, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | source spec path와 canonical API path를 분리 |
| `net-script-heartbeat-001` | heartbeat 온라인/오프라인/복구 스크립트 | SC-07 | L2, L4, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | police phone freshness board fixture와 연결 |
| `net-script-outbox-flush-001` | Outbox flush/부분 복구/중복 재전송 스크립트 | SC-09 | L2, L4, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | SC-09 package replay 본문 충돌은 pending |
| `outbox-path-001` | SC-05 path replay 기준 row | SC-05, SC-09 | L4, L6 | `S6.json` | `common-fixtures.json` | outbox fixture 그룹으로 로드 | 공통 데이터 | `sourceSpecEndpoint`와 `canonicalApiEndpoint`를 분리 |
| `outbox-marker-001` | SC-06 marker write replay 기준 row | SC-06, SC-09 | L4, L6 | `S6.json` | `common-fixtures.json` | outbox fixture 그룹으로 로드 | 공통 데이터 | photo attach와 같이 쓴다 |
| `outbox-photo-001` | SC-06 photo attach replay 기준 row | SC-06, SC-09 | L4, L6 | `S6.json` | `common-fixtures.json` | outbox fixture 그룹으로 로드 | 공통 데이터 | marker/photo/event 기대값과 연결 |
| `mk-precinct-clue-001` | clue marker seed | SC-01, SC-06, SC-10 | L1, L5, L6 | `harness-scenarios.md §6`, `S5.json` | `common-fixtures.json` | incident seed와 함께 로드 | 공통 데이터 | photo attach fixture와 같이 쓴다 |
| `photo-precinct-clue-001` | SC-06 photo upload/attach 대표 photo ID | SC-06 | L5, L6 | `harness-scenarios.md §6`, `S5.json` | `common-fixtures.json` | object storage fixture 단계에서 로드 | 공통 데이터 | upload URL/object storage 기준값과 연결 |
| `evt-s1-assignment-support-assigned-001` | SC-02 지원 배정 notification event | SC-02 | L2, L5, L6 | `S5.json` | `common-fixtures.json` | notification fixture 단계에서 로드 | 공통 데이터 | support assignment recipient fixture와 같이 검증 |
| `evt-s3-path-appended-001` | path append event 기준값 | SC-05, SC-09 | L2, L4, L6 | `S3-1.json`, `S3-2.json` | `common-fixtures.json` | event fixture 단계에서 로드 | 공통 데이터 | board path row와 cross-check |
| `evt-s5-marker-updated-photo-001` | photo attach 완료 event | SC-06, SC-09 | L2, L5, L6 | `S5.json` | `common-fixtures.json` | event fixture 단계에서 로드 | 공통 데이터 | marker/photo version 기대값과 연결 |
| `evt-s5-support-request-001` | 지원 요청 notification event | SC-08 | L2, L5, L6 | `S5.json` | `common-fixtures.json` | event fixture 단계에서 로드 | 공통 데이터 | toast row/Fcm recipient fixture와 연결 |
| `evt-s5-person-found-001` | 인원 발견 notification event | SC-08 | L2, L5, L6 | `S5.json` | `common-fixtures.json` | event fixture 단계에서 로드 | 공통 데이터 | all assigned recipient fixture와 연결 |
| `bs-inc-precinct-first-001-v1200` | board API 응답 기준 row 집합 | SC-02, SC-03, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12 | L2, L3, L5, L6 | `S3-2.json`, `S2.json`, `S1-2.json`, `S8.json` | `common-fixtures.json` | board probe 단계에서 로드 | 공통 데이터 | overall_search_area, area, police_phone_freshness, handover, summary rows 포함 |
| `rr-precinct-001` | SC-10 radio report fixture | SC-10 | L1, L3, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | command flow 단계에서 로드 | 공통 데이터 | `decisionId`와 함께 검증 |
| `memo-precinct-handover-001` | OP1 handover memo seed | SC-10 | L1, L3, L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | incident seed와 함께 로드 | 공통 데이터 | board handover row와 연결 |
| `memo-precinct-op2-001` | SC-11 handover memo fixture | SC-11 | L3, L6 | `S8.json` | `common-fixtures.json` | OP2 fixture 단계에서 로드 | 공통 데이터 | owner-spec S4 event 기대값 포함 |
| `summary-precinct-op2-001` | SC-11 AI summary fixture | SC-11 | L3, L6 | `S8.json` | `common-fixtures.json` | OP2 fixture 단계에서 로드 | 공통 데이터 | 금지 문구는 `negativeOnlyInputs.forbiddenPhraseResponse`로만 제공 |

## Pending Confirmation

| key | 용도 | blocking SC | owner | 근거 문서 | 비고 |
|---|---|---|---|---|---|
| `mock112.sourceIncidentId` | mock 112 원천 사건 식별자 | SC-01, SC-02, SC-12 | mock 112 / S1-1 | `S1-1.json` open issue | 확정 전 로드 금지 |
| `mock112.missingPersonPayload` | 실종자 세부 payload | SC-01, SC-12 | mock 112 / S1-1 | `S1-1.json` open issue | 개인정보/마지막 위치/진술 미확정 |
| `mock112.assignmentPayloadShape` | mock 112 배정 payload shape | SC-01, SC-02, SC-10, SC-12 | mock 112 / S1-1 | `S1-1.json` open issue | account/policePhone mapping 최종 형식 미확정 |
| `sc02.handoverFixtureHandling` | SC-02 인계 fixture 처리 방식 | SC-02 | S1-1/S8 | `S1-1.json` open issue | import adapter 재실행인지 하네스 경로인지 미확정 |
| `mock112.missingSourceIncidentErrorHandling` | missing sourceIncidentId 처리 방식 | SC-01 | S1-1 | `S1-1.json` open issue | public error vs preflight failure 미확정 |
| `tileManifest.overallAreaHash` | tile manifest hash canonical 값 | SC-03, SC-04 | S7 + harness | `harness-scenarios.md §6`, `S7.json` | `overall-area-hash-precinct-v1` vs `overall_search_area-hash-precinct-v1` 충돌 |
| `sc09.packageReplayCanonicalization` | SC-09 package replay bodyHash/payloadId/eventId | SC-09 | S6 + S7 + S3-2 | `S6.json`, `S7.json`, `S3-2.json` | 충돌 전까지 `outbox-package-001` replay는 canonical화하지 않음 |

## Source Coverage

| source | coverage |
|---|---|
| `S1-1.json` | incident seed ID, incident terminal board row, mock112 pending 항목 수집 |
| `S1-2.json` | police phone freshness board row, alias/account type 기준 수집 |
| `S1-3.json` | tombstone reference만 수집, 추가 고정 fixture ID 없음 |
| `S2.json` | overall_search_area/area board row와 areaId 기준 수집 |
| `S3-1.json` | GPS path, segment, path replay, `read_only_geometry_hash`, `stale_version`, `restart_no_bridge` 수집 |
| `S3-2.json` | board slot row 전체, board failure fixture 수집 |
| `S4.json` | `sc08_empty_fcm_recipient_skip`, `sc09_outbox_replay_convergence`, `duplicate_event_dedupe`, `replay_gap_refetch_required`, `closed_and_purged_stream`, `fanout_failure_injection` 수집 |
| `S5.json` | marker/photo/notification/event fixture 수집 |
| `S6.json` | path replay, marker/photo replay, `sc12_close_requeue`, `failure_category_rows`, shared outbox rules 수집 |
| `S7.json` | tile manifest ID, `sc03_manifest_download_pending`, package badge board row 수집; hash/package replay는 pending |
| `S8.json` | `current_op_consumer_contract`, OP transition/search history summary, `s8_idempotent_write_replay` 수집 |

## Load Order

1. `incidentSeed`
2. `gpsPath`, `geometryReference`
3. `tileManifest`, `networkScripts`
4. `outboxReplay`
5. `objectStorageUpload`, `offlinePackageUi`
6. `markerAndNotification`, `eventRegistry`, `eventFanout`
7. `boardAssembly`
8. `commandFlow`, `terminalStateRules`, `opTransition`, `searchHistorySummary`

같은 fixture를 여러 번 로드해도 결과는 deterministic해야 한다. fixture ID를 바꾸거나 축약하지 않는다.

## Loader 사용 규칙

- backend는 `docs/spec/fixtures/common-fixtures.json`을 test resource로 복사하거나 읽기 전용 참조로 사용한다.
- android는 `app/src/androidTest/assets/fixtures` 또는 동등한 읽기 전용 asset 경로로 가져간다.
- frontend/e2e는 Playwright fixture source로 이 JSON을 읽고, lane 전용 probe/mock adapter만 따로 구현한다.
- 공통 fixture는 외부 네트워크를 호출하지 않는다.
- `sourceSpecEndpoint`는 source spec path 검증에만 쓰고, 실제 public HTTP 호출 비교는 `canonicalApiEndpoint`를 쓴다.
- `negativeOnlyInputs.forbiddenPhraseResponse`는 저장/표시용 summary fixture가 아니라 차단 검증 입력이다.
- lane 전용 loader가 필요해도 이 디렉터리의 JSON 구조와 fixture ID를 바꾸지 않는다.

## Reset / Cleanup

- 각 Lane loader는 load 전에 자기 테스트 저장소를 비운다.
- purge/cleanup은 fixture JSON을 수정하지 말고 Lane 저장소를 reset하는 식으로 처리한다.
- 사건 종료/삭제 테스트는 `common-fixtures.json` 값을 지우지 않고 각 Lane 저장소에서만 purge한다.

## Preflight

실행:

```bash
python3 docs/spec/fixtures/preflight_common_fixtures.py
```

검증 범위:

- JSON 파싱
- README confirmed catalog와 `catalogIndex` 일치
- `catalogIndex.ownerPath`/`pendingConfirmation` guard 필드
- required confirmed section 존재
- `fixtureId` 중복
- 내부 event/ID cross-reference
- status, failure key, error code 검증

## 제외 범위

- Lane 전용 Java/Kotlin/TypeScript fixture 코드
- Backend API 통합 테스트
- Android E2E 테스트
- Web Playwright 시나리오 구현
- mock 112 서버 구현
- 새로운 fixture ID 창작
