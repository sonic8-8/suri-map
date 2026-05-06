# SC-01 ~ SC-12 시나리오별 종료 조건 정리

상태: 초안. 이 문서는 6번 담당자의 산출물로, 각 시나리오가 통합/E2E 레벨에서 어떤 조건을 만족해야 PASS인지 정리한다.

## 0. 문서 목적

6번 담당자는 테스트를 구현하지 않는다. 이 문서는 `harness-scenarios.md`의 `given/when/then`, `involved_apis`, `e2e_red_test`, `board_merge`를 기준으로 각 SC의 종료 조건, 검증 유형, 필요 데이터/fixture/mock, 선행 필요 작업, 충돌/결정 필요, 금지/주의를 정리한다.

현재 저장소에는 전달 문서의 `docs/tasks` 경로가 없고 실제 task 문서는 `tasks/`에 있으므로, 최종 산출물 경로는 `tasks/scenario-exit-criteria.md`로 둔다.

## 1. 기준 문서와 적용 원칙

- 1차 기준: `기획문서/harness-scenarios.md`, `기획문서/boundaries.md`, `specs/*.json`, `tasks/L1-tasks.md` ~ `tasks/L6-tasks.md`
- 배경 기준: `기획문서/prd.md`, `기획문서/architecture.md`, `기획문서/adr.md`
- API path는 task 문서에 명시된 canonical `/api` path를 우선 사용한다. 현재 저장소에는 `docs/api/api-spec.md`가 없으므로 최종 API spec 대조는 `확인 필요`로 남긴다.
- 기준 문서에 없는 ID, API, fixture 값, task ID는 새로 만들지 않고 `확인 필요`로 둔다.
- 실제 테스트 코드, fixture loader, product API, DB schema, event payload를 이 문서에서 구현하거나 수정하지 않는다.

## 2. 테스트 유형 정의

| 유형 | 의미 |
|---|---|
| Backend 통합 테스트 | API, service, DB, event staging, transaction, rollback, guard 검증 |
| Android 통합/E2E 테스트 | Room, Outbox, WorkManager, GPS/local state, 앱 상태 전이 검증 |
| Web E2E 테스트 | 웹 상황판 사용 흐름, slot 렌더링, CTA 상태, tombstone 상태 검증 |
| Android UI 자동화 | 앱 화면의 로딩, 비활성화, pending, offline, 재시도, 알림 표시 검증 |
| Mock contract 테스트 | mock 112, SSE, FCM, object storage, board refetch, event envelope 계약 검증 |
| 통합 리허설 | 여러 Lane 산출물을 묶어 SC 흐름을 1회 통과시키는 증거 수집 |

## 3. fixture/mock 구분

| 구분 | 의미 | 기본 담당 |
|---|---|---|
| 공통 데이터 | 여러 Lane이 같은 ID와 값으로 써야 하는 seed/catalog | 5번 |
| 공통 loader | 공통 데이터를 테스트 환경에 적재하는 loader/preflight | 5번 |
| mock 112 소유 | mock 112 source/polling/import 동작과 admin/API | 4번 |
| Lane 소유 fixture/mock | 특정 Spec/Lane의 mock adapter, contract fixture, harness runner | 해당 Lane |
| 확인 필요 | 기준 문서 충돌, 소유자 불명확, canonical 값 미정 | owner 확인 |

---

## SC-01 · 배정 사건 가져오기·초동 활성화

### 종료 조건

- `POST /api/incidents/import` 성공 시 사건이 `OPEN`으로 등록된다.
- `missing_person`, `incident_assignment`, 초기 기준 마커, OP1이 같은 import 흐름에서 생성된다.
- OP1 생성 실패 또는 import 실패 시 incident/OP/assignment/event staging이 모두 rollback된다.
- 가져오기 진행 중 웹 버튼은 로딩/비활성 상태이며, 중복 실행해도 사건, OP, assignment, 초기 마커가 중복 생성되지 않는다.
- 완료 화면에서 OP1 생성 완료와 접근 가능한 지구대/파출소 팀 계정·순찰차 계정을 확인할 수 있다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | `POST /api/incidents/import` 성공, rollback, 중복 row 방지 | L1/S1-1 | L1-T01, L1-T02, L1-I01 |
| Backend 통합 테스트 | missing_person 허용 필드와 incident detail/list 응답 검증 | L1/S1-1 | L1-T03, L1-T05A |
| Backend 통합 테스트 | OP1 자동 생성과 `OP_TRANSITIONED(from=null,to=OP1)` publish request 검증 | L3/S8 | L3-T05A, L3-T05B |
| Mock contract 테스트 | mock 112 source incident payload와 sourceIncidentId 조회 계약 검증 | 4번 + L1 | L1-T07, 확인 필요 |
| Mock contract 테스트 | S4 event envelope, publish request, event_dispatch_job 수렴 검증 | L2/S4 | L2-T05, L2-T06, L2-T09B |
| Mock contract 테스트 | 초기 기준 마커 seed port가 import 흐름에서 호출되는지 검증 | L5/S5 | L5-T05A, L5-T05B |
| Web E2E 테스트 | 가져오기 CTA 로딩/비활성/성공/실패 재시도 표시 검증 | L6/S3-2 또는 Web owner | 확인 필요 |
| 통합 리허설 | SC-01 import부터 OP1 생성까지 통합 evidence 수집 | L1 중심 + L2/L3/L5 | L1-D01A |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| `inc-precinct-first-001` 계열 대표 사건 | 공통 데이터 | import 후 내부 incident 기준 ID와 SC 후속 전제 | 5번 |
| mock 112 배정 사건 payload | mock 112 소유 | source incident, 실종자 정보, 초동 배정 입력 | 4번 |
| account/policePhone 기준 데이터 | 공통 데이터 | 지구대/파출소 지휘·팀·순찰차 접근권한 검증 | 5번 |
| account/team/policePhone loader | 공통 loader | 모든 Lane이 같은 계정/단말 ID 사용 | 5번 |
| `ReferenceMarkerSeed.createForIncident` mock | Lane 소유 fixture/mock | 초기 기준 마커 생성 검증 | L5 |
| S4 event envelope mock | Lane 소유 fixture/mock | event_dispatch_job/SSE 수렴 검증 | L2 |
| OP1 자동 생성 mock/contract | Lane 소유 fixture/mock | L1 import가 S8 OP1 생성 계약을 소비 | L3 |

### 선행 필요 작업

- mock 112 서버 baseUrl, sourceIncidentId 입력 방식 확정
- account/policePhone 공통 fixture catalog 확정
- S8 OP1 자동 생성 contract와 rollback 경계 확정
- S4 event envelope와 event_dispatch_job 최소 계약 확정

### 충돌/결정 필요

- 현재 저장소에 `docs/api/api-spec.md`가 없어 `/api` canonical path 최종 대조 필요
- mock 112 원천 payload 최종 shape 미정
- 없는 sourceIncidentId를 public error로 볼지 fixture preflight 실패로 볼지 결정 필요

### 금지/주의

- Suri-Map 내부 사건 직접 생성 UI/API를 만들지 않는다.
- `POST /api/incidents/import` 외 별도 import API를 6번 문서에서 정의하지 않는다.
- mock 112 payload 값을 6번 문서에서 임의 확정하지 않는다.

---

## SC-02 · 실종팀 인계·지원 부대 배정

### 종료 조건

- mock 112 인계 fixture가 같은 사건에 실종팀 지휘·팀 계정을 `incident_assignment`로 추가한다.
- mock 112 지원 배정 fixture가 같은 사건에 지원 부대 지휘·팀·순찰차 계정을 추가한다.
- 기존 지구대/파출소 assignment와 OP1 경로·마커·메모 seed ID는 revoke 없이 보존된다.
- 신규 배정 계정은 사건 목록/상세/상황판 접근 권한을 얻고, 미배정 계정은 `403 team_not_assigned`를 받는다.
- `INCIDENT_ASSIGNMENT_CHANGED` 이후 FCM mock dispatcher에 PII 없는 배정 payload가 신규 배정 활성 PolicePhone recipient로 기록된다.
- 상황판은 `초동 OP1`, `실종팀 인계 완료`, 기존 OP1 기록 보존 상태를 같은 사건 안에서 구분하고 중복 사건 카드로 표시하지 않는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | 112/mock polling/import로 assignment 추가, revoke 없음, 중복 import 방지 | L1/S1-1 | L1-T04, L1-I02 |
| Backend 통합 테스트 | 배정/미배정 계정의 incident access guard 검증 | L2/S1-2 | L2-T01, L2-T02, L2-T09A |
| Mock contract 테스트 | `INCIDENT_ASSIGNMENT_CHANGED` envelope, eventId/version 수렴 검증 | L2/S4 | L2-T05, L2-T06, L2-T07A, L2-T09B |
| Mock contract 테스트 | 신규 배정 PolicePhone FCM recipient와 PII 없는 payload 검증 | L5/S5 + L2/S1-2 | L2-T04, L5-T09D |
| Web E2E 테스트 | path/marker/handover/op_history slot에서 OP1 기록 보존과 인계 완료 표시 검증 | L6/S3-2 | L6-T03B, L6-T04A, L6-T10B |
| 통합 리허설 | 인계·지원 배정 후 OP1 기록, FCM, board 수렴 evidence 수집 | L1 중심 + L5/L6 | L1-D01A |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| 지구대/파출소 초동 사건 seed | 공통 데이터 | 인계 전 OP1 기록과 기존 assignment 기준 | 5번 |
| 실종팀 인계 fixture | mock 112 소유 | 실종팀 지휘·팀 계정 추가 | 4번 |
| 지원 부대 배정 fixture | mock 112 소유 | 지원 부대 지휘·팀·순찰차 추가 | 4번 |
| OP1 path/marker/memo seed | 공통 데이터 | 인계 후 기존 기록 보존 검증 | 5번 |
| FCM token fixture | Lane 소유 fixture/mock | 활성 PolicePhone recipient 계산 | L2 |
| FCM dispatcher mock | Lane 소유 fixture/mock | payload/recipient/eventId/version capture | L5 |
| board refetch/assembly mock | Lane 소유 fixture/mock | path/marker/handover/op_history slot 수렴 | L6 |

### 선행 필요 작업

- mock 112 인계와 지원 배정 event source 구분 확정
- account/policePhone/FCM token 공통 fixture 확정
- S3-2 `path`, `marker`, `handover_status`, `op_history` slot 계약 확정
- OP1 path/marker/memo seed의 사건/OP 귀속 ID 확정

### 충돌/결정 필요

- assignment 중복 기준은 문서에 있으나 DB의 `external_assignment_key` 같은 canonical 컬럼명은 확인 필요
- mock 112 인계 처리 API를 웹/앱이 직접 호출할 때의 canonical error payload 확인 필요
- 지휘 계정 policePhoneId를 Android FCM recipient에서 제외하는 기준을 fixture catalog에 명시해야 함

### 금지/주의

- Suri-Map 내부 지원 부대 배정 workflow나 후보 선택 CTA를 만들지 않는다.
- 웹·앱 채널이 mock 112 인계 처리 API를 직접 호출하도록 만들지 않는다.
- 인계 완료 표시를 단순 웹 조회 반복만으로 전환하지 않는다.

---

## SC-03 · 사건 오프라인 패키지 사전 적재

### 종료 조건

- 앱이 `GET /api/incidents/{incidentId}/offline-package/manifest`로 사건 메타, 실종자, OP, 담당 구역, 초기 마커, 전체 수색 구역, tile manifest를 받는다.
- 앱은 항목별 진행률, 실패 항목명, 실패 항목 재시도 CTA를 표시한다.
- 전체 성공 시 package installation 상태가 서버에 보고되고 `OFFLINE_PACKAGE_INSTALLATION_CHANGED`가 발행된다.
- 상황판 `package_badge`는 미완료 단말 경고를 표시하고, 완료 후 해제된다.
- 담당 구역이 없는 지구대/파출소 순찰차 계정도 OP1, 전체 수색 구역, 초기 기준 마커, 단말 식별 정보를 받아 초동 수색 준비가 가능하다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | missing_person allowlist와 package manifest 입력 필드 검증 | L1/S1-1 | L1-T03 |
| Backend 통합 테스트 | offline package manifest/installation API와 event 발행 검증 | L6/S7 | L6-T05, L6-T06A, L6-T10A |
| Android 통합/E2E 테스트 | manifest/tile 다운로드, local package state, 실패 항목 재시도 검증 | L4/S6 + L6/S7 | L4-B01, L4-T05A, L4-T05B, L6-T10A |
| Android UI 자동화 | 진행률, 실패 항목명, 지도 미다운로드 경고, 준비 완료 UI 검증 | Android owner + L4/L6 | 확인 필요 |
| Mock contract 테스트 | local tile fixture, 외부 tile host 차단, checksum/timeout/404 실패 주입 | L6/S7 | L6-T08A, L6-T08B, L6-T10A |
| Web E2E 테스트 | `package_badge` slot의 미완료/완료/재적재 필요 상태 표시 검증 | L6/S3-2 | L6-T06B |
| 통합 리허설 | SC-03 package/tile 준비와 badge 수렴 evidence 수집 | L1/L6 중심 | L1-D01A, L6-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| `tile-manifest-inc-precinct-001-v1` | 공통 데이터 | tile manifest 기준 ID | 5번 |
| local tile blob catalog | Lane 소유 fixture/mock | 외부 tile host 없이 tile cache 검증 | L6 |
| manifest/tile network script | Lane 소유 fixture/mock | timeout, 404, checksum mismatch, corrupt blob 주입 | L6/L4 |
| missing_person/package allowlist fixture | 공통 데이터 | package에 포함 가능한 개인정보 필드 제한 | 5번 + L1 확인 |
| initial marker seed fixture | Lane 소유 fixture/mock | manifest에 초기 기준 마커 포함 | L5 |
| offline_package_installation fixture | Lane 소유 fixture/mock | package_badge와 local warning 입력 | L6 |
| PolicePhone assignment fixture | 공통 데이터 | 미등록/미배정 단말 차단 | 5번 |

### 선행 필요 작업

- tile catalog와 local blob URI 기준 확정
- offline package manifest field allowlist 확정
- package installation status enum과 앱 local state 매핑 확정
- `package_badge` slot props 확정

### 충돌/결정 필요

- 전달 문서의 `docs/api/api-spec.md`가 없어 manifest/report canonical response shape 확인 필요
- 담당 구역이 없는 순찰차 계정 manifest에서 `assignedArea`를 null로 둘지 빈 배열로 둘지 결정 필요
- stale package 판정 기준이 S2 overall area version인지 manifest revision인지 최종 확인 필요

### 금지/주의

- 외부 OSM/Mapbox/Google tile host를 호출하지 않는다.
- 패키지 미완료, 만료, stale 상태를 오프라인 사용 준비 완료로 표시하지 않는다.
- S7이 marker write를 소유하지 않는다. 초기 마커는 S5 seed 계약을 소비한다.

---

## SC-04 · 전체 수색 구역·구역 분할·할당

### 종료 조건

- 웹 현장 지휘관이 overall_search_area를 유효 Polygon으로 생성/수정한다.
- search_area 생성, 분할, assignment가 현재 OP 기준으로 저장된다.
- 저장 성공 시 좌표는 EPSG:4326 `[lon, lat]`, ring 폐합, 중복점 제거, 소수 6자리 precision의 canonical geometry로 REST/DB/board response가 일치한다.
- `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED`가 발행되고 상황판 `overall_search_area`, `area` slot이 수렴한다.
- overall_search_area 변경은 package manifest stale 처리로 이어지지만 기존 search_area row를 임의 변경하지 않는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | overall_search_area 생성/수정, no-auto-judgement 검증 | L3/S2 | L3-T01 |
| Backend 통합 테스트 | search_area 생성/분할/assignment와 geometry validation 검증 | L3/S2/S8 | L3-T02A, L3-T02B, L3-T06B, L3-T09A |
| Backend 통합 테스트 | role/channel guard, 앱 write 차단, 권한 실패 no-write 검증 | L2/S1-2 | L2-T02 |
| Mock contract 테스트 | `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED` event와 board refetch 수렴 검증 | L2/S4 + L6/S3-2 | L2-T06, L6-T02A, L6-T02B, L6-T02C |
| Web E2E 테스트 | 저장 CTA 로딩/비활성, draft 유지, 오류 사유, 영역 강조 표시 검증 | L6/S3-2 | L6-T03A, L6-T10B |
| Mock contract 테스트 | overall area 변경 후 package stale/re-download 상태 검증 | L6/S7 | L6-T07 |
| 통합 리허설 | SC-04 구역 생성/분할/배정과 board convergence evidence 수집 | L3 중심 + L6 | L1-D01B, L3-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| canonical geometry fixture | 공통 데이터 | 정상/실패 Polygon, bbox, precision 기준 | 5번 + L3 확인 |
| `SearchAreaQuery.overallOf` mock | Lane 소유 fixture/mock | L4/L5/L6 validation input | L3 |
| role/channel guard fixture | Lane 소유 fixture/mock | field commander 권한과 앱 write 차단 | L2 |
| board area slot mock | Lane 소유 fixture/mock | overall_search_area/area slot 수렴 | L6 |
| manifest stale fixture | Lane 소유 fixture/mock | overall area 변경 후 package stale 검증 | L6 |

### 선행 필요 작업

- geometry fixture catalog 확정
- S2 common geometry rule과 canonical precision 확정
- S3-2 `overall_search_area`, `area` slot props 확정
- S7 manifest revision/stale 처리 기준 확정

### 충돌/결정 필요

- `POST /api/search-areas/{searchAreaId}/split` canonical path 최종 확인 필요
- 전체 수색 구역과 수색 구역을 같은 table `search_area(area_level=OVERALL|...)`로 둘 때 API response naming 기준 확인 필요
- invalid geometry error payload의 detail code 수준 확인 필요

### 금지/주의

- 시스템이 수색 누락을 자동 확정하거나 다음 구역을 추천하지 않는다.
- overall_search_area 변경으로 search_area/assignment를 임의 생성 또는 변경하지 않는다.
- 앱에서 전체 수색 구역, 수색 구역, OP 담당 구역 배정 write를 허용하지 않는다.

---

## SC-05 · 수색 경로·PolicePhone GPS 경로

### 종료 조건

- 앱 PolicePhone이 현재 OP에서 수색을 시작하고 `search_path`가 생성된다.
- GPS batch는 10초 단위 전송 기준을 지키며 `search_path`와 `search_path_segment`에 누적된다.
- 차량/도보 segment가 fixture 속도 기준으로 자동 분리되고, 웹 수동 보정은 기존 segment type만 수정한다.
- `PATH_APPENDED`, `SEARCH_PATH_SEGMENT_UPDATED` 이후 상황판 `path`, `police_phone_freshness` slot이 수렴한다.
- 앱은 수색 시작, 일시정지, 재개, 종료, 기록 중 PolicePhone, pending queue, GPS 품질 경고를 표시한다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | search_path lifecycle, current OP/policePhone guard 검증 | L4/S3-1 | L4-T01 |
| Backend 통합 테스트 | GPS batch 저장, LineString validation, path/segment persistence 검증 | L4/S3-1 | L4-T02, L4-T04A, L4-T04B |
| Backend 통합 테스트 | 차량/도보 자동 분리와 웹 segment 보정 검증 | L4/S3-1 | L4-T03 |
| Android 통합/E2E 테스트 | 5초 수집, 10초 batch, pending queue, GPS 품질 저하 local state 검증 | L4/S6/S3-1 | L4-B01, L4-T05A, L4-T05B |
| Mock contract 테스트 | event_dispatch_job, SSE, board refetch 수렴과 duplicate/stale event 검증 | L2/S4 + L6/S3-2 | L2-T06, L2-T07A, L2-T07B, L6-T10B |
| Web E2E 테스트 | path와 police_phone_freshness slot 표시, 차량/도보 스타일 구분 검증 | L6/S3-2 | L6-T03B, L6-T03C |
| 통합 리허설 | path/freshness board convergence와 1시간 안정성 evidence 수집 | L4 중심 | L1-D01B, L4-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| `gps-path-normal-001` | 공통 데이터 | 차량/도보 자동 분리 기준 경로 | 5번 + L4 확인 |
| GPS 품질 저하 fixture | Lane 소유 fixture/mock | accuracy, timestamp skew, speed, jump 실패 검증 | L4 |
| PolicePhone assignment fixture | 공통 데이터 | 미등록/미배정 단말 guard | 5번 |
| current OP fixture | Lane 소유 fixture/mock | path OP 귀속 검증 | L3 |
| overall area query fixture | Lane 소유 fixture/mock | bbox/envelope validation input | L3 |
| Outbox/idempotency fixture | Lane 소유 fixture/mock | batch 재전송 중복 방지 | L4 |
| board path/freshness mock | Lane 소유 fixture/mock | slot 수렴 검증 | L6 |

### 선행 필요 작업

- GPS path fixture ID와 segment index 확정
- PolicePhone freshness DTO와 board slot props 확정
- path batch 최대치와 low-quality point 처리 기준 확정
- S6 idempotency key/bodyHash 기준 확정

### 충돌/결정 필요

- 수색 시작 API canonical path가 `POST /api/search-paths`인지 task/API spec 최종 확인 필요
- 수동 보정 API `PATCH /api/search-path-segments/{id}`의 web-only guard error shape 확인 필요
- low-quality GPS를 저장 제외로 볼지 별도 excluded row로 남길지 최종 확인 필요

### 금지/주의

- 경로 기록 주체를 개인 계정으로 바꾸지 않는다. PolicePhone 기준이다.
- 경로 LineString과 완료 구역 Polygon을 같은 의미로 렌더링하지 않는다.
- 웹에서 app-only path start/batch API를 호출하지 않는다.

---

## SC-06 · 현장 마커 생성

### 종료 조건

- 앱 바텀시트에서 마커 유형 선택만으로 현재 위치, 시간, 계정, PolicePhone, OP 기준 marker가 생성된다.
- 온라인 기준 생성 후 3초 안에 상황판 `marker` slot에 표시된다.
- 사진이 있으면 upload-url 발급, object storage upload, attach가 단계별로 성공/실패 처리된다.
- 오프라인 생성은 앱 지도와 큐에 pending으로 표시되고, 복구 후 pending이 해제된다.
- invalid Point, 웹 생성, 미등록/미배정 PolicePhone, 사진 제한 초과는 row/event/board를 변경하지 않는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | `POST /api/markers`, marker geometry, OP/account/PolicePhone binding 검증 | L5/S5 | L5-T01A, L5-T03A, L5-T03B |
| Android UI 자동화 | 바텀시트 입력, 유형 원탭 저장, 저장 중/완료/실패 재시도 UI 검증 | L5/S5 + Android owner | L5-T01B |
| Backend 통합 테스트 | photo upload-url, upload, attach, duplicate/orphan rejection 검증 | L5/S5 | L5-B01A, L5-T04A, L5-T04B |
| Android 통합/E2E 테스트 | offline marker/photo pending, Outbox 연동, 복구 후 pending 해제 검증 | L4/S6 + L5/S5 | L4-T05A, L4-T05B, L5-T04B |
| Mock contract 테스트 | `MARKER_CREATED`/photo event, SSE, board marker 수렴 검증 | L2/S4 + L6/S3-2 | L2-T06, L2-T07A, L6-T10B |
| Web E2E 테스트 | marker slot 표시와 source-owner immutability 검증 | L6/S3-2 | L6-T03B |
| 통합 리허설 | SC-06 marker/photo 하네스와 board evidence 수집 | L5 중심 | L1-D01B, L5-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| marker Point fixture | 공통 데이터 | 정상/실패 Point geometry 검증 | 5번 + L5 확인 |
| marker type fixture | Lane 소유 fixture/mock | 단서, NOTE, 지원 요청, 발견 등 유형 정책 | L5 |
| mock object storage/upload URL | Lane 소유 fixture/mock | 외부 S3 없이 upload/attach 검증 | L5 |
| Outbox marker/photo fixture | Lane 소유 fixture/mock | 오프라인 pending과 복구 검증 | L4/L5 |
| current OP fixture | Lane 소유 fixture/mock | marker OP 귀속 검증 | L3 |
| board marker mock | Lane 소유 fixture/mock | marker slot 수렴 | L6 |

### 선행 필요 작업

- marker type enum과 bottom sheet 최소 입력 contract 확정
- object storage mock endpoint와 object key 규칙 확정
- marker/photo size/count 제한 fixture 확정
- S3-2 marker slot props 확정

### 충돌/결정 필요

- 사진 attach 성공 시 `MARKER_CREATED`와 `MARKER_UPDATED.photoDelta` 중 어떤 event로 board 수렴을 검증할지 확인 필요
- Web의 마커 조회/수정/삭제 정책과 현장 마커 생성 금지 경계 확인 필요
- offline 상태에서 사진 임시 파일 경로/수명 기준 확인 필요

### 금지/주의

- 현장 마커 생성은 앱 전용이다.
- 실제 S3 endpoint를 호출하지 않는다.
- S3-2 marker detail/edit/delete panel을 SC-06 하네스가 직접 수정하지 않는다.

---

## SC-07 · 통신 단절 중 로컬 기록

### 종료 조건

- SC-03 package ready 상태에서 네트워크가 30분 이상 단절되어도 앱은 로컬 사건 정보와 오프라인 지도를 사용한다.
- 경로 포인트, 마커, 사진 임시 파일이 Room/SQLite와 Outbox에 저장된다.
- 앱은 `오프라인 / 기록 중`, 미전송 큐 수량, pending 항목, GPS/배터리/지도 미다운로드 로컬 경고를 표시한다.
- 서버 row, event_dispatch_job, SSE, board response는 생성되지 않는다.
- 앱 재시작 후에도 미전송 큐가 유지된다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Android 통합/E2E 테스트 | Room local mirror, Outbox row, 앱 재시작 후 큐 유지 검증 | L4/S6 | L4-B01, L4-T05A, L4-T05B |
| Android 통합/E2E 테스트 | network offline script, heartbeat offline, package ready/stale 상태 전이 검증 | L4/S6 + L6/S7 | L4-T07A, L4-T07B, L6-T06B |
| Android UI 자동화 | offline banner, queue count, pending 지도 표시, local warning UI 검증 | L4/S6 + Android owner | L4-T08 |
| Mock contract 테스트 | offline 중 owner endpoint 미호출, event/SSE/board 미생성 검증 | L4/S6 + L2/S4 + L6/S3-2 | L4-T10B, L2-T09B |
| 통합 리허설 | SC-07 local survival evidence와 네트워크 전환 프로토콜 수집 | L4 중심 | L1-D01C, L4-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| SC-03 package ready seed | 공통 데이터 | 오프라인 사용 가능 전제 | 5번 + L6 확인 |
| package unavailable/stale fixture | Lane 소유 fixture/mock | 오프라인 사용 불가/재적재 경고 검증 | L6/L4 |
| mock network offline script | Lane 소유 fixture/mock | 30분 offline, heartbeat 실패, domain write pending 재현 | L4 |
| Room/Outbox local fixture | Lane 소유 fixture/mock | path/marker/photo pending local persistence | L4 |
| local warning detector fixture | Lane 소유 fixture/mock | GPS stopped, battery low, tile missing 경고 | L4 |

### 선행 필요 작업

- Android local schema와 Outbox 상태 enum 확정
- SC-03 package ready 상태와 stale/unavailable 상태 매핑 확정
- network 상태 fixture와 30분 offline 시간 제어 방식 확정

### 충돌/결정 필요

- offline 중 package installation status report를 Outbox에 넣는지, 네트워크 복구 후 별도 report로 처리하는지 확인 필요
- 로컬 저장 실패 시 retry 가능/기록 불가 최종 UI 문구 확인 필요
- Android UI 자동화 도구(Maestro/Compose/UI Automator) 선택은 확인 필요

### 금지/주의

- SC-07은 서버 반영을 검증하지 않는다. 서버 반영은 SC-09에서 검증한다.
- offline 중 서버 event나 board response가 생성되면 안 된다.
- package 미완료/만료/stale 상태를 offline ready로 표시하지 않는다.

---

## SC-08 · 지원 요청·실종자 발견 알림

### 종료 조건

- 앱에서 지원 요청 또는 실종자 발견 marker가 생성된다.
- 지원 요청은 실종팀 지휘 계정과 현장 지휘관 역할 계정에 우선 알림이 도달한다.
- 실종자 발견은 사건 배정 단말 전체에 강조 알림이 도달한다.
- 웹 toast, 앱 인앱 배너, Android background notification data message가 같은 eventId와 marker id/status/version을 참조한다.
- 동일 이벤트 재수신 시 중복 toast/banner/notification이 쌓이지 않는다.
- 오프라인 생성 항목은 수신 알림으로 표시하지 않고 생성 단말에 pending/전송 대기 상태로 남는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | 지원 요청 marker policy와 recipient 계산 검증 | L5/S5 | L5-T06A |
| Backend 통합 테스트 | 실종자 발견 marker policy와 high priority notification 검증 | L5/S5 | L5-T07 |
| Mock contract 테스트 | FCM dispatcher recipient/payload/eventId/version capture 검증 | L5/S5 + L2/S1-2 | L5-B01B, L5-T06B, L2-T04 |
| Mock contract 테스트 | `SUPPORT_REQUEST_CREATED`, `PERSON_FOUND`, SSE, board toast 수렴 검증 | L2/S4 + L6/S3-2 | L2-T06, L2-T07A, L2-T07B, L6-T10B |
| Android UI 자동화 | 앱 배너, background notification local 생성, pending/전파 완료 피드백 검증 | L5/S5 + Android owner | 확인 필요 |
| Web E2E 테스트 | marker와 toast slot 분리 표시, 중복 toast 방지 검증 | L6/S3-2 | L6-T02A, L6-T02B, L6-T02C |
| 통합 리허설 | marker/notification/FCM/board toast evidence 수집 | L5 중심 | L1-D01C, L5-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| support request marker fixture | Lane 소유 fixture/mock | 지원 요청 marker와 알림 payload 기준 | L5 |
| person found marker fixture | Lane 소유 fixture/mock | 실종자 발견 강조 알림 기준 | L5 |
| FCM token/recipient fixture | Lane 소유 fixture/mock | 수신 대상 계산 | L2 |
| mock FCM dispatcher | Lane 소유 fixture/mock | foreground/background/duplicate capture | L5 |
| board toast mock | Lane 소유 fixture/mock | toast slot 수렴 | L6 |
| Outbox notification pending fixture | Lane 소유 fixture/mock | 오프라인 생성 pending 상태 | L4/L5 |

### 선행 필요 작업

- 지원 요청/실종자 발견 marker type과 notification payload factory 확정
- FCM recipient policy와 지휘 계정 PolicePhone 제외 기준 확정
- board `toast` slot과 marker slot 책임 분리 확정

### 충돌/결정 필요

- Android background notification local 생성 책임이 앱 UI task에 있는지 L5 contract에 있는지 확인 필요
- 중복 이벤트 dedupe 기준을 eventId 단독으로 볼지 entity version까지 포함할지 확인 필요
- 지원 요청 위치 요약 format 확인 필요

### 금지/주의

- 드론·경찰견 실제 출동 요청, 승인, 장비 연동을 만들지 않는다.
- 실제 외부 FCM 인프라 호출을 PASS 조건에 넣지 않는다.
- marker 표시와 알림 표시를 같은 UI 책임으로 섞지 않는다.

---

## SC-09 · 통신 복구·동기화

### 종료 조건

- SC-07의 Outbox 항목이 네트워크 복구 후 순차 전송된다.
- 경로 포인트, 마커, 사진 첨부, 지원 요청, 실종자 발견 알림이 유실 0건으로 서버에 반영된다.
- 동일 idempotency key 재전송은 기존 응답 재생 또는 중복 제거로 처리되어 중복 row/event가 0건이다.
- 앱은 미전송 큐가 줄어드는 과정과 최종 `미전송 0`, pending 해제, 복구 완료를 표시한다.
- SSE replay, duplicate delivery, out-of-order event, stale version/sequence에도 board response가 최신 상태로 수렴한다.
- heartbeat 재개 후 상황판 PolicePhone freshness가 normal로 돌아온다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Android 통합/E2E 테스트 | Outbox flush, partial failure, retry state, local mirror sync 검증 | L4/S6 | L4-T05A, L4-T05B, L4-T07C, L4-T07D, L4-T10B |
| Backend 통합 테스트 | idempotency key/bodyHash, cached response replay, 중복 row 방지 검증 | L4/S6 | L4-T06 |
| Backend 통합 테스트 | path batch/marker/photo owner endpoint 재사용 검증 | L4/S3-1 + L5/S5 | L4-T02, L5-T04B |
| Mock contract 테스트 | SSE Last-Event-ID replay, duplicate, reordered, stale event guard 검증 | L2/S4 | L2-T07A, L2-T07B |
| Web E2E 테스트 | marker/path/freshness slot 복구 수렴과 stale 덮어쓰기 방지 검증 | L6/S3-2 | L6-T02A, L6-T02B, L6-T02C, L6-T03B, L6-T03C |
| 통합 리허설 | 30분 offline 후 복구, 유실 0건, 중복 0건 evidence 수집 | L4 중심 | L1-D01C, L4-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| `net-script-outbox-flush-001` | Lane 소유 fixture/mock | PENDING_SEND -> SENDING -> ACKED 전이 | L4 |
| duplicate idempotency fixture | Lane 소유 fixture/mock | 중복 재전송 row/event 0건 검증 | L4 |
| partial failure fixture | Lane 소유 fixture/mock | 성공 항목 완료, 실패 항목 재시도 유지 | L4 |
| mock SSE client | Lane 소유 fixture/mock | Last-Event-ID, duplicate, reordered, stale event 주입 | L2 |
| board refetch/assembly mock | Lane 소유 fixture/mock | STALE_REFETCH와 최신 version 수렴 | L6 |
| heartbeat recovery fixture | Lane 소유 fixture/mock | freshness stale -> recovering -> normal 전이 | L2/L4 |

### 선행 필요 작업

- S6 Outbox 상태 enum과 UI 상태 매핑 확정
- idempotency key/bodyHash/cache policy 확정
- SSE replay와 board refetch 수렴 기준 확정
- SC-07 local pending seed를 materialize하는 방법 확정

### 충돌/결정 필요

- 전달 문서 예시의 `/api/sync/outbox/requeue`가 실제 product API인지 harness diagnostic API인지 확인 필요
- 권한 실패 Outbox 항목을 재시도 대기와 어떻게 구분할지 status 값 확인 필요
- stale package와 복구 완료 UI를 동시에 표시하는 최종 UX 기준 확인 필요

### 금지/주의

- 중복 재전송을 새 domain row 생성으로 처리하지 않는다.
- stale event가 최신 board response를 덮지 못하게 해야 한다.
- SC-09에서 offline package 재다운로드를 자동 성공 처리하지 않는다. stale이면 재적재 필요 상태로 남긴다.

---

## SC-10 · 구역 완료·새 OP 열기

### 종료 조건

- 웹 현장 지휘관이 무전 보고를 확인하고 지휘관 판단을 기록한 뒤 구역 완료, OP2 생성, 인수인계 메모 저장을 수행한다.
- area status/history는 처리 계정, 시각, 현재 OP, 메모를 포함한다.
- 새 OP 생성 시 사유가 필수이며 현재 OP가 OP2로 전환되고 `OP_TRANSITIONED`가 발행된다.
- 앱/웹에 완료 구역 상태와 현재 OP/완료 OP 구분이 반영된다.
- 실행 로그는 `radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_api_refetched` 순서를 만족한다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | area state transition, search_area_history, no duplicate history 검증 | L3/S2 | L3-T03 |
| Backend 통합 테스트 | OP2+ transition, reason required, current OP 전환 검증 | L3/S8 | L3-T06A |
| Backend 통합 테스트 | handover memo create/query, app/web 허용 경계 검증 | L3/S8 | L3-T07 |
| Backend 통합 테스트 | role/channel guard, 앱 구역 완료/OP 생성 차단 검증 | L2/S1-2 | L2-T02 |
| Mock contract 테스트 | `SEARCH_AREA_CHANGED`, `OP_TRANSITIONED`, `HANDOVER_MEMO_CREATED`와 board 수렴 검증 | L2/S4 + L6/S3-2 | L2-T06, L2-T07A, L6-T10B |
| Web E2E 테스트 | area/op_toggle/op_history/handover slots, CTA 로딩/실패/재시도 표시 검증 | L6/S3-2 | L6-T04A |
| Mock contract 테스트 | mock radio report와 commander decision flow sequence 검증 | L3/S8 | L3-T09B |
| 통합 리허설 | SC-10 OP 전환과 인수인계 evidence 수집 | L3 중심 | L1-D01D, L3-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| mock radio report `rr-precinct-001` | 공통 데이터 | 구역 완료 판단 입력 | 5번 + L3 확인 |
| commander decision fixture | Lane 소유 fixture/mock | 지휘관 판단 기록과 OP 전환 흐름 | L3 |
| area/OP/handover fixture | 공통 데이터 | OP1 완료, OP2 생성, handover memo 검증 | 5번 + L3 확인 |
| `harness_execution_log` fixture | Lane 소유 fixture/mock | 실행 순서 검증 | L3 |
| board op/area/handover mock | Lane 소유 fixture/mock | area/op_toggle/op_history/handover 수렴 | L6 |

### 선행 필요 작업

- SC-02 인계·지원 배정 완료 상태 또는 materialized seed 준비
- SC-04 area assignment seed 준비
- mock SC-10 command flow와 execution log schema 확정
- S3-2 OP/handover slot props 확정

### 충돌/결정 필요

- `OTHER` 사유와 인수인계 메모 동시 저장 API transaction 경계 확인 필요
- 앱에서 handover memo 저장은 허용되지만 OP 생성/구역 완료는 금지되는 guard matrix 확인 필요
- 무전 보고 seed가 product entity인지 harness-only log인지 확인 필요

### 금지/주의

- 무전 보고 seed만으로 자동 구역 완료 또는 OP2 생성을 처리하지 않는다.
- 앱에서 구역 완료 또는 새 OP 생성을 허용하지 않는다.
- 구역 완료를 공식 수색 기록 확정으로 표현하지 않는다.

---

## SC-11 · 인수인계·OP 비교·수색 이력 요약

### 종료 조건

- SC-10 산출물(OP1 완료, OP2 전환, 인수인계 메모, 지원 합류 완료)이 있는 사건에서만 실행된다.
- 상황판은 OP1/OP2 경로, 차량/도보 segment, 완료 구역, NOTE marker, handover memo를 비교 표시한다.
- 선택 OP, 현재 OP, 완료 OP가 배지, 범례, 레이어 토글에서 구분된다.
- 수색 이력 요약은 OP1 초동 경로, 마커, 메모를 근거로 생성되며 원본 근거 링크/하이라이트를 제공한다.
- 요약 실패 시에도 OP 비교와 수동 메모는 계속 표시된다.
- 요약은 다음 구역 추천, 자동 누락 확정, 위험도 판단 문장을 생성하거나 표시하지 않는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Web E2E 테스트 | OP 비교 토글, selected/current/completed OP 배지, 원본 geometry 불변 검증 | L6/S3-2 | L6-T04A, L6-T10C |
| Backend 통합 테스트 | handover memo 조회와 summary input evidence 구성 검증 | L3/S8 | L3-T07, L3-T10 |
| Backend 통합 테스트 | search history summary 생성, 실패 상태, 금지 문장 guard 검증 | L3/S8 | L3-T08 |
| Web E2E 테스트 | search_history_summary slot, loading/success/failure/unavailable UI 검증 | L6/S3-2 | L6-T04B, L6-T10C |
| Mock contract 테스트 | `SEARCH_HISTORY_SUMMARY_CHANGED`, SSE, board refetch 수렴 검증 | L2/S4 + L6/S3-2 | L2-T06, L2-T07A, L6-T10B |
| Mock contract 테스트 | path/marker/area evidence read model 소비 계약 검증 | L4/S3-1 + L5/S5 + L3/S2 | L4-T03, L5-T08, L3-T04B |
| 통합 리허설 | OP 비교와 요약, FR-23 금지 렌더링 evidence 수집 | L6/L3 중심 | L1-D01D, L3-D01, L6-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| SC-10 materialized seed | 공통 데이터 | 독립 실행 시 선행 산출물 대체 | 5번 + L3 확인 |
| OP1 path/marker/memo seed | 공통 데이터 | 초동 대응 이력 요약 입력 | 5번 |
| OP2 transition seed | 공통 데이터 | 현재 OP와 완료 OP 구분 | 5번 + L3 확인 |
| search history summary provider mock | Lane 소유 fixture/mock | 성공, 실패, 금지 문장 포함 응답 재현 | L3 |
| board OP/summary mock | Lane 소유 fixture/mock | OP 비교와 summary slot 수렴 | L6 |
| access/role guard fixture | Lane 소유 fixture/mock | 미배정/권한 없는 계정 차단 | L2 |

### 선행 필요 작업

- SC-10 완료 산출물 또는 materialized seed 준비
- OP comparison board slot과 source-owner immutability 기준 확정
- search history summary provider 성공/실패/금지 문장 fixture 확정
- 원본 근거 링크가 path/marker/memo 중 어떤 route를 열지 확인

### 충돌/결정 필요

- OpenAI provider를 실제로 쓰는 구현 task가 있으나 하네스에서는 mock provider 기준인지 확인 필요
- 앱에서 저장한 handover memo가 summary input에 들어가는 정확한 field 기준 확인 필요
- summary 생성 CTA unlock 조건의 canonical API guard 확인 필요

### 금지/주의

- SC-10 산출물 없이 SC-11을 성공 처리하지 않는다.
- OP 비교 화면의 스타일/필터/하이라이트가 원본 geometry/status/version을 변경하지 않는다.
- 추천, 위험도 판단, 자동 누락 확정 문장이나 CTA를 PASS 조건에 넣지 않는다.

---

## SC-12 · 사건 종료·데이터 파기

### 종료 조건

- 실종팀 지휘 계정이 종료 확인 다이얼로그를 승인하면 사건은 terminal closed 상태가 되고 재오픈되지 않는다.
- active missing_person/사진/위치/패키지/로컬 데이터는 사용자 조회 응답과 앱 로컬 저장소에서 제거 또는 tombstone 처리된다.
- `INCIDENT_CLOSED`, `INCIDENT_PURGED`가 발행되고 board `incident_terminal`, `package_badge` 상태가 sanitized terminal/tombstone으로 수렴한다.
- 종료 후 domain write와 sync write는 `409 incident_closed` 또는 명시된 closed error를 반환하고 row/event/board를 변경하지 않는다.
- 앱은 `INCIDENT_CLOSED` 수신 후 Outbox flush/requeue를 거부하고 local purge 완료/대기/실패 상태를 표시한다.
- 웹은 같은 사건 SSE stream을 닫고 이후 이벤트를 렌더링하지 않는다.

### 검증 유형

| 유형 | 검증 내용 | 담당/소유자 | 관련 task ID |
|---|---|---|---|
| Backend 통합 테스트 | `POST /api/incidents/{incidentId}/close`, terminal guard, active PII 삭제 handoff 검증 | L1/S1-1 | L1-T06, L1-T05B, L1-I03 |
| Backend 통합 테스트 | purge orchestration, internal-only operational records, `INCIDENT_PURGED` 검증 | L2/S1-3 | L2-T08, L2-T09C |
| Mock contract 테스트 | `INCIDENT_CLOSED`/`INCIDENT_PURGED`, SSE stream 해제와 replay stop 검증 | L2/S4 | L2-T07C |
| Android 통합/E2E 테스트 | post-close requeue 거부, incident-scoped local cleanup, tombstone retention 검증 | L4/S6 | L4-T09 |
| Backend 통합 테스트 | marker/photo closed write guard와 purge hook 검증 | L5/S5 | L5-T04C |
| Backend 통합/Web E2E 테스트 | package purge hook, incident_terminal/package_badge board state, 개인정보 제거 검증 | L6/S7/S3-2 | L6-T09A, L6-T09B |
| Web E2E 테스트 | 종료 다이얼로그, CTA 로딩/비활성, 종료 완료/쓰기 불가/tombstone 표시 검증 | L6/S3-2 | L6-T09B |
| 통합 리허설 | 종료, purge, local cleanup, terminal board evidence 수집 | L1 중심 + L2/L4/L5/L6 | L1-D01D, L6-D01 |

### 필요 데이터/fixture/mock

| 항목 | 구분 | 필요 이유 | 담당 |
|---|---|---|---|
| close request fixture | 공통 데이터 | 실종팀 지휘 계정 종료 승인 입력 | 5번 + L1 확인 |
| terminal/tombstone response fixture | Lane 소유 fixture/mock | 개인정보 제거 조회 검증 | L1/L6 |
| purge orchestration mock | Lane 소유 fixture/mock | S1-3 purge hook 조율 검증 | L2 |
| local purge hook fixture | Lane 소유 fixture/mock | 앱 local package/path/marker/photo/PII 삭제 검증 | L4/L6/L5 |
| post-close Outbox fixture | Lane 소유 fixture/mock | flush/requeue 거부 검증 | L4 |
| SSE unsubscribe/replay stop mock | Lane 소유 fixture/mock | 종료 후 실시간 이벤트 미수신 검증 | L2 |
| board terminal/package mock | Lane 소유 fixture/mock | incident_terminal/package_badge 수렴 | L6 |

### 선행 필요 작업

- L1 close command와 L2 purge orchestration contract 확정
- L4/L5/L6 purge hook 등록 순서와 idempotency 기준 확정
- terminal/tombstone response allowlist 확정
- 종료 후 write guard error code와 sync write 처리 기준 확정

### 충돌/결정 필요

- 24시간 soft delete 후 파기 정책이 하네스에서 시간 이동 fixture로 검증되는지 확인 필요
- missing_person 조회 실패를 `404`로 볼지 마스킹 응답으로 볼지 확인 필요
- heartbeat 성공/실패와 무관하게 terminal response에 freshness를 노출하지 않는 기준 확인 필요

### 금지/주의

- 사건 재오픈 API/UI를 만들지 않는다.
- 내부 보존 기록을 사용자 UI, 앱 로컬 데이터, 오프라인 패키지, 상황판 응답에 노출하지 않는다.
- 종료 후 FCM data message를 새 작업으로 처리하지 않는다.

---

## 4. 완료 기준

- SC-01~SC-12가 모두 문서에 있다.
- 각 SC에 종료 조건이 있다.
- 각 종료 조건에 테스트 유형이 매핑되어 있다.
- 관련 Lane과 가능한 task ID가 표시되어 있다.
- 필요한 데이터/fixture/mock이 `공통 데이터`, `공통 loader`, `mock 112 소유`, `Lane 소유 fixture/mock`, `확인 필요`로 구분되어 있다.
- 선행 필요 작업이 정리되어 있다.
- 충돌/결정 필요 항목이 정리되어 있다.
- 금지/주의가 정리되어 있다.
- 기준 문서에 없는 값은 새로 만들지 않고 `확인 필요`로 남겼다.
- 테스트 코드, fixture loader, product API 변경은 포함하지 않았다.
