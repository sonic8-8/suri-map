# L1-I04 12단계 리허설 체크리스트와 복구 절차서

## 문서 범위

이 문서는 `L1-I04` / `S14P31C106-200`의 Phase 4 준비 산출물이다. PRD §2.3의 시범 시나리오 검증 방법과 PRD §5.1의 12단계를 기준으로, Phase 5 실행 전에 사용할 준비 체크리스트, 데모 시드 데이터 표, 배정 사건 가져오기·인계·종료 재시도/복구 절차를 고정한다.

최종 pass/fail evidence, 스크린샷, 로그, defect 링크, 통과/실패 판정은 이 문서에 기록하지 않는다. 최종 증거는 `L1-D01A~D` 및 각 Lane의 Phase 5 evidence 문서에 기록한다.

## 기준 문서

| 기준 | 사용 범위 |
|---|---|
| `docs/prd.md` PRD §2.3 | 시범 시나리오 통과 기준: PRD §5.1의 12단계 전부 체크리스트 통과 |
| `docs/prd.md` PRD §5.1 | 12단계 시범 시나리오 단계, actor, 행동, 확인 결과 |
| `docs/spec/harness-scenarios.md` | SC-01~SC-12 하네스 전제, 관여 Spec, API/event, board merge |
| `docs/spec/fixtures/common-fixtures.json` | 공통 fixture ID, 계정, PolicePhone, network script, board row |
| `docs/spec/fixtures/README.md` | fixture ID 소유권과 사용 범위 |
| `docs/tasks/index.md` | Phase 5 증거 분리와 Lane별 시연 책임 |

## 운영 원칙

- Suri-Map은 112 공식 시스템과 무전을 대체하지 않는다. 시연은 112/실종프로파일링 mock·seed를 사용한다.
- 사용자가 사건을 직접 생성하지 않는다. 사건은 `mock-112-incident-001` 원천을 가져와 `inc-precinct-first-001`로 소비한다.
- 지원 부대 배정과 실종팀 인계는 Suri-Map 내부 후보 선택이 아니라 mock 112 `incident_assignment` polling/import 결과로 반영한다.
- 사건 종료는 terminal 상태다. 사용자 재오픈 UI/API는 없다. 종료 실수 복구는 관리자 운영 절차로만 다룬다.
- 자동 판단 금지 원칙을 유지한다. 다음 투입 구역 자동 추천, 수색 누락 확정, 위험도 판단 문구나 CTA를 만들지 않는다.
- 이 문서는 준비 절차다. Phase 5 실행 중 발견한 pass/fail, defect, 보완 결과는 이 문서가 아니라 evidence 문서에 남긴다.

## 리허설 역할

| 역할 | 담당 |
|---|---|
| 리허설 진행자 | 12단계 순서를 호출하고 각 Lane 담당자에게 준비 상태를 확인한다. |
| 웹 지휘 사용자 | 실종팀 간부 또는 현장 지휘관 역할 계정으로 상황판과 사건 종료 흐름을 수행한다. |
| 앱 현장 사용자 | 사건 배정 계정으로 PolicePhone을 사용해 패키지, 경로, 마커, 오프라인/복구 흐름을 수행한다. |
| 관찰자 | Phase 5 evidence 문서에만 결과와 defect를 기록한다. 이 준비 문서는 실행 중 수정하지 않는다. |
| Lane 담당자 | 자기 Lane slot/API/event 준비 상태와 실패 시 복구 절차를 설명한다. |

## 데모 시드 데이터 표

### 사건과 OP

| 항목 | 값 | 사용 단계 |
|---|---|---|
| 대표 사건 | `inc-precinct-first-001` | SC-01~SC-12 공통 사건 |
| mock 112 원천 | `mock-112-incident-001` | SC-01 가져오기, SC-02 인계/지원 배정 |
| 현재 OP | `op-precinct-001-op1` | SC-01~SC-10, 초동 OP 기록 보존 |
| 다음 OP | `op-precinct-001-op2` | SC-10 OP 전환, SC-11 OP 비교 |
| 초동 clue marker | `mk-precinct-clue-001` | SC-01, SC-06, SC-10 |
| 차량 경로 | `path-precinct-car-001` | SC-05, SC-07, SC-09, SC-11 |
| 도보 경로 | `path-precinct-foot-001` | SC-05, SC-07, SC-09, SC-11 |
| 인수인계 메모 | `memo-precinct-handover-001` | SC-10, SC-11 |

### 계정과 PolicePhone

| 구분 | 계정 | PolicePhone | 사용 단계 |
|---|---|---|---|
| 지구대/파출소 지휘 | `acct-precinct-cmd` | `dev-precinct-cmd-phone-01` | SC-01 초동 가져오기, SC-10 구역 완료 |
| 지구대/파출소 순찰차 | `acct-precinct-car` | `dev-precinct-car-01` | SC-03, SC-05, SC-07, SC-09 |
| 지구대/파출소 팀 | `acct-precinct-team` | `dev-precinct-phone-01` | SC-03, SC-05~SC-09 |
| 실종팀 지휘 | `acct-cmd-alpha` | `dev-alpha-cmd-phone-01` | SC-02 인계 확인, SC-12 종료 |
| 실종팀 팀 | `acct-team-alpha` | `dev-alpha-phone-01` | SC-02 이후 사건 접근, SC-03~SC-09 |
| 지원 지휘 | `acct-support-cmd` | `dev-support-cmd-phone-01` | SC-02 지원 지휘관 권한 |
| 지원 순찰차 | `acct-support-car` | `dev-support-car-01` | SC-02 FCM, SC-05 경로 |
| 지원 팀 | `acct-support-team` | `dev-support-phone-01` | SC-02 FCM, SC-06/08 마커 |

### mock 112 배정 키

| 단계 | externalAssignmentKey | accountId | incidentRole |
|---|---|---|---|
| 초동 지휘 | `mock-112-incident-001:precinct-cmd` | `acct-precinct-cmd` | `FIELD_COMMANDER` |
| 초동 순찰차 | `mock-112-incident-001:precinct-car` | `acct-precinct-car` | `MEMBER` |
| 초동 팀 | `mock-112-incident-001:precinct-team` | `acct-precinct-team` | `MEMBER` |
| 실종팀 지휘 인계 | `mock-112-incident-001:cmd-alpha` | `acct-cmd-alpha` | `INCIDENT_COMMANDER` |
| 실종팀 팀 인계 | `mock-112-incident-001:team-alpha` | `acct-team-alpha` | `MEMBER` |
| 지원 지휘 | `mock-112-incident-001:support-cmd` | `acct-support-cmd` | `FIELD_COMMANDER` |
| 지원 순찰차 | `mock-112-incident-001:support-car` | `acct-support-car` | `MEMBER` |
| 지원 팀 | `mock-112-incident-001:support-team` | `acct-support-team` | `MEMBER` |

### 패키지·상황판·네트워크

| 항목 | 값 | 사용 단계 |
|---|---|---|
| 오프라인 manifest | `tile-manifest-inc-precinct-001` | SC-03, SC-04, SC-09 |
| 전체 수색 구역 hash | `overall-area-hash-precinct-current` | SC-03, SC-04, SC-09 |
| 타일 URI | `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf` | SC-03, SC-09 |
| 타일 범위 | `z=15..16`, `x=27925..27960`, `y=12680..12720` | SC-03 |
| board response | `bs-inc-precinct-first-001` | SC-02~SC-12 수렴 확인 |
| package badge row | `pkg-inc-precinct-first-001`, `board-package-inc-precinct-first-001` | SC-03, SC-09, SC-12 |
| handover status row | `handover-status-inc-precinct-first-001`, `board-handover-status-inc-precinct-first-001` | SC-02, SC-10 |
| search history summary row | `ai-summary-op-precinct-001-op2`, `board-ai-summary-op-precinct-001-op2` | SC-11 |
| terminal tombstone | `tombstone-inc-precinct-first-001` | SC-12 |
| terminal board row | `board-incident-terminal-inc-precinct-first-001` | SC-12 |
| manifest script | `net-script-manifest-001` | SC-03 재시도 준비 |
| tile blob script | `net-script-tile-blob-001` | SC-03 재시도 준비 |
| heartbeat script | `net-script-heartbeat-001` | SC-07 통신 단절 |
| outbox flush script | `net-script-outbox-flush-001` | SC-09 통신 복구 |

## 12단계 리허설 체크리스트

| # | SC | PRD 단계 | 준비 확인 | 관찰 대상 | 실패 시 이동 |
|---|---|---|---|---|---|
| 1 | SC-01 | 배정 사건 가져오기 | `mock-112-incident-001` 원천과 초동 배정 3건 준비 | 사건 `inc-precinct-first-001` OPEN, `op-precinct-001-op1` 생성, 중복 import 방지 | 배정 사건 가져오기 재시도 절차 |
| 2 | SC-02 | 지원 부대 배정 결과 반영 | 실종팀 인계 2건과 지원 배정 3건의 `externalAssignmentKey` 준비 | 기존 OP1 보존, 신규 계정 접근권한, `INCIDENT_ASSIGNMENT_CHANGED`, FCM 대상 | 인계·지원 배정 재시도 절차 |
| 3 | SC-03 | 사건 오프라인 패키지 사전 적재 | `tile-manifest-inc-precinct-001`, local tile, `package_badge` 준비 | 패키지 진행률, 실패 항목 재시도, 상황판 경고 배지 | 패키지/타일 실패는 L6 절차로 넘김 |
| 4 | SC-04 | 구역 분할 / 할당 | `overall-area-hash-precinct-current`와 geometry fixture 준비 | `overall_search_area`, `area`, 배정 상태, manifest stale 여부 | L3/L6 구역·패키지 복구 절차로 넘김 |
| 5 | SC-05 | 수색 시작 및 GPS 기록 | `dev-precinct-car-01`, `dev-precinct-phone-01`, 경로 fixture 준비 | 차량/도보 경로, `police_phone_freshness`, board `path` slot | L4 경로/PolicePhone 절차로 넘김 |
| 6 | SC-06 | 단서 또는 실종자 발견 | 앱 marker flow, mock object storage, marker slot 준비 | marker 저장, 사진 attach, board marker 수렴 | L5 marker/photo 절차로 넘김 |
| 7 | SC-07 | 통신 단절 | `net-script-heartbeat-001` 준비, 앱 로컬 저장 확인 | 미전송 큐, 로컬 경로/마커 유지, 서버 미반영 상태 | L4 오프라인 절차로 넘김 |
| 8 | SC-08 | 지원 요청 마커 생성 | 지원 요청 marker type과 mock FCM capture 준비 | `SUPPORT_REQUEST`/`PERSON_FOUND`, toast, recipient | L5 notification 절차로 넘김 |
| 9 | SC-09 | 통신 복구 | `net-script-outbox-flush-001` 준비, outbox 재전송 fixture 준비 | 큐 소진, 중복 생성 0건, 최신 위치/패키지 상태 | 통신 복구 재시도 절차 |
| 10 | SC-10 | 구역 완료 처리 | 현장 지휘관 계정, OP 전환 준비 | area 완료, `op-precinct-001-op2`, handover memo, 이력 | L3/S8 OP 절차로 넘김 |
| 11 | SC-11 | 인수인계 / 다음 투입 구역 판단 | OP1/OP2 비교와 `ai-summary-op-precinct-001-op2` 준비 | OP 비교, 인수인계 메모, 수색 이력 요약, 자동 판단 문구 없음 | L6/S8 summary 절차로 넘김 |
| 12 | SC-12 | 사건 종료 | 실종팀 지휘 계정과 종료 확인 다이얼로그 준비 | 개인정보 제거, `INCIDENT_CLOSED`/`INCIDENT_PURGED`, tombstone, `incident_terminal` | 사건 종료 재시도·복구 절차 |

## 재시도·복구 절차서

### 공통 판단 순서

1. 사용자가 같은 버튼을 다시 눌러도 되는 상태인지 먼저 확인한다.
2. `Idempotency-Key`, `externalAssignmentKey`, PolicePhone, OP, incident 상태가 기준 fixture와 일치하는지 확인한다.
3. REST 응답, DB row, `event_dispatch_job`, SSE replay, board response가 같은 `id/status/version`을 말하는지 비교한다.
4. 실패가 다른 Lane 소유 계약이면 여기서 임의 수정하지 않고 해당 Lane evidence 또는 defect task로 넘긴다.
5. 최종 pass/fail은 기록하지 않고, 재시도 가능 여부와 필요한 담당 Lane만 Phase 5 evidence 문서에 남긴다.

### 배정 사건 가져오기 import retry

| 실패 지점 | 확인 | 재시도/복구 |
|---|---|---|
| mock 112 원천 없음 | `mock-112-incident-001`이 fixture preflight에 존재하는지 확인 | 원천 fixture를 먼저 로드한다. public API error를 새로 만들지 않는다. |
| import 중복 클릭 | 같은 `Idempotency-Key`와 sourceIncidentId인지 확인 | 같은 요청이면 기존 결과를 재사용하고 사건/OP 중복 생성이 없어야 한다. |
| import rollback | incident, missing_person, assignment, OP1, outbox가 부분 생성됐는지 확인 | 부분 생성이 있으면 실패로 보고 defect로 넘긴다. 수동 DB 보정은 시연 중 하지 않는다. |
| OP1 미생성 | `op-precinct-001-op1`과 `OP_TRANSITIONED(from=null)` stage 확인 | OP1 자동 생성 blocker로 L3/S8 담당자에게 넘긴다. |

### 실종팀 인계·지원 배정 retry

| 실패 지점 | 확인 | 재시도/복구 |
|---|---|---|
| 인계 배정 미반영 | `mock-112-incident-001:cmd-alpha`, `mock-112-incident-001:team-alpha` 확인 | mock 112 polling/import를 다시 실행한다. 같은 `externalAssignmentKey`로 중복 row가 생기면 실패다. |
| 지원 배정 미반영 | `mock-112-incident-001:support-cmd`, `support-car`, `support-team` 확인 | polling/import 재시도 후 신규 지원 계정 접근권한과 FCM capture를 다시 확인한다. |
| 기존 OP1 손실 | `path-precinct-car-001`, `path-precinct-foot-001`, `memo-precinct-handover-001` 확인 | 인계는 OP1 보존이 필수다. 손실이 있으면 재시도하지 않고 defect로 넘긴다. |
| FCM 미수신 | `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01` capture 확인 | payload에 개인정보가 없어야 한다. 실제 Firebase 전송으로 우회하지 않는다. |

### 통신 복구 retry

| 실패 지점 | 확인 | 재시도/복구 |
|---|---|---|
| 미전송 큐 유지 | `net-script-outbox-flush-001`과 outbox 상태 확인 | 네트워크 복구 script를 다시 실행하고 같은 operationId 중복 생성 여부를 확인한다. |
| 패키지 상태 stale | `tile-manifest-inc-precinct-001`, `overall-area-hash-precinct-current` 확인 | manifest 재조회와 package badge 수렴을 L6 절차로 넘긴다. |
| PolicePhone 접근 실패 | `police_phone_not_registered`, `police_phone_not_assigned` 여부 확인 | 단말 등록/배정 fixture 문제로 분리한다. 앱에서 강제 완료 처리하지 않는다. |

### 사건 종료 close retry / rollback

| 실패 지점 | 확인 | 재시도/복구 |
|---|---|---|
| 종료 요청 전 미전송 존재 | `op-outbox-preclose-path-001` 같은 종료 전 outbox가 drain 가능한지 확인 | 종료 전 기록은 한 번 drain을 시도한다. 종료 후 write 재시도는 허용하지 않는다. |
| 종료 API 실패 | incident 상태가 아직 OPEN인지, `Idempotency-Key`가 같은지 확인 | 같은 close 요청은 재시도 가능하다. 다른 body로 재시도하면 idempotency conflict로 본다. |
| 종료 후 write 재시도 | `incident_closed` 또는 `CLOSED_NO_RETRY` 분류 확인 | 사용자에게 재시도 CTA를 제공하지 않는다. post-close write는 최종 실패로 둔다. |
| 개인정보 잔존 | missing_person, tombstone, board `incident_terminal`, package state 확인 | active DB/API/오프라인 패키지에 남으면 SC-12 blocker다. 즉시 defect로 넘긴다. |
| 종료 실수 | 사건은 terminal이며 사용자 재오픈 없음 | 운영 관리자 절차로만 복구한다. 사용자 UI/API에 재오픈을 만들지 않는다. |
| 개발 데이터 확인 | 24시간 soft delete 정책 확인 | SSAFY 시연·개발 데이터는 복구 확인 후 hard delete 관리자 절차로 넘긴다. |

## Phase 5 인계 메모

- `L1-D01A~D`는 이 문서를 실행 순서의 기준으로 삼고 실제 결과만 evidence 문서에 기록한다.
- L5/L6 smoke procedure처럼 이 문서도 준비 절차와 capture target만 유지한다.
- Phase 5에서 결함이 나오면 이 문서에 pass/fail을 덧붙이지 말고 defect task 또는 해당 Lane evidence 문서에 링크한다.
- L1-I04 완료 판단은 이 문서와 `docs/tasks/check_l1_rehearsal_runbook.py`가 준비되고, `docs/tasks/L1-tasks.md`에 완료 근거가 남는 것으로 제한한다.
