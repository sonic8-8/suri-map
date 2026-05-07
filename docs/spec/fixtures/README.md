# Common Fixture Catalog

`docs/spec/fixtures`는 L1~L6가 같은 기준 데이터로 SC-01~SC-12 하네스/통합/E2E 테스트를 시작할 수 있도록 만든 공통 fixture 카탈로그와 중립 데이터 세트다.

이 디렉터리는 Lane 전용 테스트 코드나 mock adapter를 두지 않는다. 기준 문서에 이미 있는 fixture ID와 payload만 모으고, 미정 값은 `확인 필요`로 남긴다.

## 파일 구성

- `common-fixtures.json`: 확정된 공통 fixture 실행 데이터 세트
- `pending-confirmation.json`: 기준 문서에 값이 없어서 확정 못 한 항목
- `preflight_common_fixtures.py`: fixture JSON 파싱, 필수 필드, fixture ID 중복, enum/error 문자열 smoke 검증

## 공통 Catalog

| fixture ID | 용도 | 사용 SC | 사용 Lane | 기준 문서 | 데이터 파일 | 로더/사용 규칙 | 소유 구분 | 비고 |
|---|---|---|---|---|---|---|---|---|
| `inc-precinct-first-001` | 대표 초동 사건 seed | SC-01, SC-02, SC-10, SC-12 | L1~L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | incident seed를 먼저 로드 | 공통 데이터 | OP1/OP2, assignment 기대값과 같이 쓴다 |
| `op-precinct-001-op1` | 대표 현재 차수(OP1) | SC-01, SC-05, SC-07, SC-10 | L1, L3, L4, L5, L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | incident seed 다음에 로드 | 공통 데이터 | `current_op_consumer_contract` 기준 |
| `op-precinct-001-op2` | OP 전환 후 차수(OP2) | SC-10, SC-11 | L1, L3, L6 | `harness-scenarios.md §6`, `S8.json` | `common-fixtures.json` | OP transition fixture와 같이 로드 | 공통 데이터 | `evt-s8-op-transitioned-001`과 연결 |
| `gps-path-normal-001` | 차량/도보 혼합 GPS 경로 | SC-05, SC-07, SC-09, SC-11 | L3, L4, L6 | `harness-scenarios.md §6`, `S3-1.json` | `common-fixtures.json` | geometry seed 후 로드 | 공통 데이터 | point/segment ID를 바꾸지 않는다 |
| `tile-manifest-inc-precinct-001-v1` | 오프라인 타일 manifest | SC-03, SC-04, SC-09 | L4, L6 | `harness-scenarios.md §6`, `S7.json` | `common-fixtures.json` | tile fixture 단계에서 로드 | 공통 데이터 | 외부 tile host 호출 금지 |
| `net-script-manifest-001` | manifest 복구 스크립트 | SC-03 | L4, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | 상태 전이는 스크립트 설명 그대로 사용 |
| `net-script-domain-write-001` | 오프라인 domain write/복구 스크립트 | SC-07, SC-09 | L4, L5, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | network script 단계에서 로드 | 공통 데이터 | `PENDING_LOCAL`, `PENDING_SEND`, `SYNCED` 기대값 고정 |
| `outbox-path-001` | SC-05 path replay 기준 row | SC-05, SC-09 | L4, L6 | `S6.json` | `common-fixtures.json` | outbox fixture 그룹으로 로드 | 공통 데이터 | Java/Kotlin test fixture로 재가공하지 말고 이 값을 기준으로 사용 |
| `outbox-package-001` | package status replay 기준 row | SC-09 | L4, L6 | `S6.json`, `S7.json` | `common-fixtures.json` | outbox fixture 그룹으로 로드 | 공통 데이터 | package/event/board 기대값과 연결 |
| `mk-precinct-clue-001` | clue marker seed | SC-01, SC-06, SC-10 | L1, L5, L6 | `harness-scenarios.md §6`, `S5.json` | `common-fixtures.json` | incident seed와 함께 로드 | 공통 데이터 | photo attach fixture와 같이 쓴다 |
| `evt-s3-path-appended-001` | path append event 기준값 | SC-05, SC-09 | L2, L4, L6 | `S6.json`, `S3-2.json` | `common-fixtures.json` | event fixture 단계에서 로드 | 공통 데이터 | SSE/board probe 기대값과 연결 |
| `bs-inc-precinct-first-001-v1200` | board API 응답 기준 row 집합 | SC-02, SC-03, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12 | L2, L3, L5, L6 | `S3-2.json` | `common-fixtures.json` | board probe 단계에서 로드 | 공통 데이터 | slot/source/version 기준 |
| `rr-precinct-001` | SC-10 radio report fixture | SC-10 | L1, L3, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | command flow 단계에서 로드 | 공통 데이터 | `decisionId`와 함께 검증 |
| `memo-precinct-handover-001` | OP1 handover memo seed | SC-10 | L1, L3, L6 | `harness-scenarios.md §6` | `common-fixtures.json` | incident seed와 함께 로드 | 공통 데이터 | OP 전환 후에도 같은 사건 소속 유지 |
| `memo-precinct-op2-001` | SC-11 handover memo fixture | SC-11 | L3, L6 | `S8.json` | `common-fixtures.json` | OP2 fixture 단계에서 로드 | 공통 데이터 | AI summary fixture와 같이 사용 |
| `summary-precinct-op2-001` | SC-11 AI summary fixture | SC-11 | L3, L6 | `S8.json` | `common-fixtures.json` | OP2 fixture 단계에서 로드 | 공통 데이터 | 성공/실패/금지 문장 응답과 연결 |
| `미정(mock 112 원천 사건 payload)` | mock 112 원천 사건 payload | SC-01, SC-02, SC-12 | L1, L2, L6 | `S1-1.json` open issue | `pending-confirmation.json` | 확정 전 로드 금지 | 확인 필요 | 문서에 고정된 fixture ID가 없어서 `sourceIncidentId`, 실종자 개인정보, 배정 payload shape를 먼저 확정해야 한다 |

## Load Order

1. `incidentSeed`
2. `gpsPath`, `geometryReference`
3. `tileManifest`, `networkScripts`
4. `outboxReplay`
5. `markerAndNotification`
6. `boardAssembly`
7. `commandFlow`, `opTransition`, `searchHistorySummary`

같은 fixture를 여러 번 로드해도 결과는 deterministic해야 한다. fixture ID를 바꾸거나 축약하지 않는다.

## Loader 사용 규칙

- backend는 `docs/spec/fixtures/common-fixtures.json`을 test resource로 복사하거나 읽기 전용 참조로 사용한다.
- android는 `app/src/androidTest/assets/fixtures` 또는 동등한 읽기 전용 asset 경로로 가져간다.
- frontend/e2e는 Playwright fixture source로 이 JSON을 읽고, lane 전용 probe/mock adapter만 따로 구현한다.
- 공통 fixture는 외부 네트워크를 호출하지 않는다.
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
- `catalogIndex`, `confirmed`, `pendingConfirmation` 필수 구조
- `fixtureId` 중복
- status/error enum smoke 검증

## 제외 범위

- Lane 전용 Java/Kotlin/TypeScript fixture 코드
- Backend API 통합 테스트
- Android E2E 테스트
- Web Playwright 시나리오 구현
- mock 112 서버 구현
- 새로운 fixture ID 창작
