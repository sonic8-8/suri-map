# Suri-Map Spec Boundaries

## 0. 문서 성격

이 문서는 Suri-Map Spec-Driven Development의 **소유권·계약·변경 경계**를 정의한다. PRD는 제품 요구, Architecture는 구조 결정을 다루고, 이 문서는 하네스와 작업 분배가 충돌하지 않도록 Spec별 책임을 고정한다.

- 기준 제품 문서: `prd.md` v3
- 기준 아키텍처: `architecture.md`
- 기준 결정 문서: `adr.md`
- 폐기·대체된 결정은 `adr-archive.md`에만 보관하며 신규 Spec 기준으로 쓰지 않는다.

## 0.1 현재 기준

- 사건은 사용자가 직접 만들지 않는다. MVP/시연은 112/실종프로파일링 mock·seed 배정 사건을 가져온다.
- 계정은 팀 계정, 순찰차 계정, 지휘 계정 기준이다.
- GPS 경로의 기록 주체는 팀 업무폰 또는 순찰차 업무폰 Device다.
- 지도 범위는 `map_boundary`로 관리한다. 특정 지형 전용 범위 개념을 만들지 않는다.
- 시스템은 수색 누락을 자동 확정하지 않고, 다음 투입 구역을 자동 지시하지 않는다.
- OP(Operation Period)는 사건 내 수색 차수와 인수인계의 기준이다.
- 운영 로그와 접속기록은 MVP 사용자 화면 요구사항이 아니다. 별도 조회 UI를 만들지 않는다.

## 0.2 채널 경계

| 채널 | 주 역할 | 쓰기 허용 예 |
|---|---|---|
| Android 앱 | 현장 입력, 수색 세션, 경로, 마커, 오프라인 기록 | 세션 시작/종료, 경로 batch, 현장 마커, 패키지 상태, heartbeat |
| Web 상황판 | 지휘·상황 공유, 구역·OP 관리, 인수인계 | 사건 가져오기/종료, 지원 부대 배정, 지도 기준 범위, 구역 분할·완료, OP, 인수인계 메모, AI 요약 |
| System | mock·seed, 동기화, 이벤트, 파기 | bootstrap, OP1 자동 생성, 이벤트 fanout, purge orchestration |

현장 마커 생성은 앱 전용이다. 단, 초기 기준 마커와 지휘용 보정 UI는 Web 상황판 권한 범위에서 별도 취급한다.

## 0.3 SC ID와 Spec ID 관계

- SC ID는 `harness-scenarios.md`의 사용자·운영 흐름 단위다.
- Spec ID는 구현·계약·소유권 경계다.
- SC ID와 Spec ID는 1:1로 대응하지 않는다.
- 하나의 SC는 여러 Spec 계약을 관통할 수 있다.
- 하나의 Spec은 여러 SC에서 반복 사용될 수 있다.
- SC ID를 엔티티명, API path, 이벤트명, schema명에 포함하지 않는다.
- `Harness Mapping`은 SC가 어떤 Spec 계약을 검증하는지 추적하기 위한 역인덱스다.
- 상황판 slot 통합은 S3-2 shell 경유 여부를 별도 열로 분리한다.

---

## 1. Spec 목록

| Spec | 이름 | 핵심 책임 | Lane |
|---|---|---|---:|
| S1-1 | Incident Import & Membership | mock·seed 사건 가져오기, 사건 lifecycle, 실종자 운영 캐시, 사건 참여 계정 | L1 |
| S1-2 | Account, Device & RBAC | 팀/순찰차/지휘 계정, Device, 세션, 채널·역할 권한 | L2 |
| S1-3 | Retention & Operational Records | 파기 오케스트레이션, 위치정보 접근기록, 비사용자 화면 운영 기록 | L2 |
| S2 | Map Boundary & Search Area | 지도 기준 범위, 수색 구역, 구역 상태 이력 | L3 |
| S3-1 | PolicePhone Path Collection | 수색 세션, PolicePhone 경로, 차량·도보 구간 | L4 |
| S3-2 | Situation Board Shell & Projection | 상황판 shell, board projection/snapshot read model, slot merge/rendering, OP 비교 화면 | L6 |
| S4 | Realtime Event Hub | SSE, event envelope, `EventHub.publish`, `EventFanout`, `event_outbox`, `sse_event_log` | L2 |
| S5 | Markers / Photo / Notification Delivery | 현장 마커, 사진, notification payload/recipient 계산, `FcmDispatcher` adapter | L5 |
| S6 | Offline Sync & Local Warnings | Outbox, idempotency, sync status, 로컬 경고 | L4 |
| S7 | Offline Tiles & Incident Package | tileserver, 오프라인 패키지 manifest/status | L6 |
| S8 | Operational Period & Handover | OP, OP assignment, 인수인계 메모, AI 수색 이력 요약 | L3 |

## 1.1 Lane별 작업 경계

| Lane | 담당 |
|---|---|
| L1 | 사건 가져오기, 사건 종료, 실종자 운영 캐시, 사건 참여 계정 |
| L2 | 계정·Device·권한, 운영 기록, S4 이벤트 허브 실구현 |
| L3 | 지도 기준 범위, 수색 구역, OP, 인수인계 |
| L4 | PolicePhone 경로, Outbox, 로컬 경고, 복구 동기화 |
| L5 | 마커, 사진, notification payload/recipient, FCM adapter |
| L6 | 상황판 shell, offline package, tileserver, board snapshot |

S4는 공용 계약이지만 실구현·유지보수·버그 수정은 L2가 주도한다. `event_outbox` row 자체와 outbox 적재 이후 fanout orchestration은 S4가 단일 소유한다. Domain Spec(S1-1/S2/S3-1/S5/S8 등)은 outbox row를 owns하지 않고 해당 이벤트 payload와 발행 요청 생성·검증만 책임진다. 이벤트 payload 변경은 관련 Spec 오너와 S3-2, S4 LGTM이 필요하다.

## 1.2 6인 하네스 운영 기준

6명이 각자 하나의 Lane 하네스를 운영한다는 전제로 Lane을 나눈다. Lane owner는 자기 Lane의 Spec 구현, fixture, mock adapter, red test를 책임진다. 단, SC는 사용자 흐름이므로 여러 Lane을 관통할 수 있고, SC 전체를 한 사람이 독점 구현하지 않는다.

| Lane | 1인 하네스 책임 | 독립 실행 산출물 | 먼저 받아야 하는 공용 계약 |
|---|---|---|---|
| L1 | S1-1 사건 import/lifecycle/membership | SC-01 bootstrap, 사건 목록/상세, 사건 종료 요청 fixture | S1-2 auth fixture, S4 event envelope |
| L2 | S1-2/S1-3/S4 계정·Device·권한·운영 기록·이벤트 허브 | team/device auth harness, SSE/outbox/replay harness, purge orchestration harness | seed account/device ID 목록, event payload owner LGTM |
| L3 | S2/S8 지도 기준 범위·구역·OP·인수인계 | SC-04/SC-10 domain harness, OP/query fixture | S1-2 role guard, S4 publish contract, S3-2 slot props |
| L4 | S3-1/S6 경로·Outbox·로컬 경고·복구 동기화 | SC-05/SC-07/SC-09 path/outbox harness | S1-2 device fixture, S2 boundary query, S4 event envelope |
| L5 | S5 마커·사진·notification delivery | SC-06/SC-08 marker/photo/FCM mock harness | S1-2 auth fixture, S6 idempotency contract, S4 fanout port |
| L6 | S3-2/S7 상황판 shell/projection·offline package | board slot merge harness, SC-03 package harness, snapshot convergence harness | §9.2 slot registry, S4 projector trigger, S2/S5/S8 query contracts |

6인 운영에서는 아래 규칙을 적용한다.

1. 각 Lane 하네스는 다른 Lane의 미완성 구현을 직접 기다리지 않고 `provides` 계약의 mock/fixture로 red test를 먼저 작성한다.
2. 다른 Lane의 entity, API, event payload, board shell 파일을 수정하려면 해당 owner LGTM이 필요하다.
3. 공용 계약은 1주차에 먼저 동결한다: S1-2 account/device fixture, S4 event envelope/outbox, S3-2 board slot registry, S6 idempotency/outbox shape, S2 geometry fixture.
4. 통합 red test는 §10 Harness Mapping 기준으로 연결한다. 개별 Lane PASS는 자기 계약 PASS를 뜻하고, SC 최종 PASS는 관련 Lane 계약과 S3-2/S4 수렴 검증까지 통과해야 한다.
5. L2와 L6은 공용 기반 Lane이다. 두 Lane의 최소 fixture/stub이 없으면 나머지 Lane은 contract mock으로 진행하되, 통합 blocker는 owner Lane에 남긴다.

## 1.3 Spec ID 재검토 결과

Spec ID는 SC ID에서 파생하지 않는다. Spec ID는 구현 소유권, 저장소 생명주기, API namespace, 이벤트/slot 계약이 함께 움직이는 단위를 기준으로 정한다. JSON 분리 전에도 아래 조건이면 Spec ID를 바꿀 수 있다.

- 서로 다른 저장소 생명주기나 배포·테스트 closure를 가진 책임이 한 Spec에 섞인 경우
- `owns`가 둘 이상의 독립 API namespace로 갈라지고 각자 별도 owner review가 필요한 경우
- `provides`/`consumes`가 매번 예외를 만들 정도로 넓어진 경우
- 하네스 SC의 `involved_specs`와 §10 Harness Mapping이 같은 Spec을 계속 다른 의미로 쓰는 경우

이번 검토에서는 현행 ID를 JSON 1차 분리 기준으로 유지한다. 이유는 아래와 같다.

| 후보 | 판단 | 이유 | 향후 분리 조건 |
|---|---|---|---|
| S1-1 Incident Import & Membership | 유지 | 사건 lifecycle, import, membership은 사건 접근권한과 `OPEN/CLOSED` guard를 같이 만든다. 분리하면 초기 bootstrap과 권한 캐시가 더 자주 왕복한다. | 부대·팀 편제 관리 UI나 별도 membership admin workflow가 생기면 `Incident Lifecycle`과 `Incident Membership` 분리 |
| S3-2 Situation Board Shell & Projection | 유지 | shell, slot mount, projection, snapshot은 최종 통합 owner가 하나여야 merge 충돌을 줄인다. | projection worker와 Web shell이 서로 다른 배포 단위가 되면 `Board Shell`과 `Board Projection` 분리 |
| S5 Markers / Photo / Notification Delivery | 유지 | 지원 요청·실종자 발견 알림은 marker write에서 파생되고, S5는 payload/recipient/adapter만 소유한다. Fanout orchestration은 S4로 분리되어 있다. | marker와 무관한 일반 알림·읽음 관리·개인별 notification inbox가 생기면 `Marker/Photo`와 `Notification Delivery` 분리 |
| S7 Offline Tiles & Incident Package | 유지 | tileserver와 incident package manifest/status는 오프라인 패키지 다운로드 흐름에서 함께 검증된다. | tileserver가 독립 인프라 서비스가 되거나 incident package가 tile 없이 별도 배포되면 `Offline Tiles`와 `Incident Package` 분리 |
| S8 Operational Period & Handover | 유지 | OP, assignment, handover memo, AI summary는 OP 이력이라는 같은 도메인 입력을 공유한다. | AI 요약이 별도 모델 운영·프롬프트 버전·비동기 job product로 커지면 `Operational Period/Handover`와 `AI Summary` 분리 |

따라서 `spec/specs/*.json` 1차 분리는 현재 Spec ID를 사용한다. 향후 위 조건으로 ID를 바꾸면 `harness-scenarios.md`의 `involved_specs`, 이 문서 §10 Harness Mapping, `spec/specs/<spec-id>.json` 파일명을 같은 변경 단위로 갱신한다.

---

## 2. 공통 의존 규칙

1. 모든 domain write API는 `incidentId`, `accountId`, `policePhoneId` 필요 여부, `opId` 귀속 여부를 명시한다.
2. 사건 `OPEN` 전 쓰기는 `409 incident_bootstrapping`을 반환한다.
3. 사건 종료 후 쓰기는 `409 incident_closed`를 반환한다.
4. 앱 전용 쓰기 API는 Web 요청을 `403 channel_not_allowed`로 거부한다.
5. Web 전용 지휘 API는 앱 요청을 `403 channel_not_allowed`로 거부한다.
6. System/internal caller 전용 API 또는 system 보조 write path를 앱·웹이 직접 호출하면 `403 channel_not_allowed`로 거부한다.
7. 이벤트 발행 요청은 domain transaction 안에서 S4 `event_outbox`로 stage하고, commit 이후 fanout은 S4 `EventFanout`이 수행한다.
8. S3-2 상황판 shell/slot 규칙은 §9.2 기준 적용.
9. 자동 판단 금지 원칙을 깨는 신규 API, 배치, UI 문구는 PRD/ADR 재검토 없이는 추가하지 않는다.

---

## 3. Spec 상세

### S1-1 · Incident Import & Membership

**owns**

- `incident`
- `missing_person_cache`
- `incident_membership`
- mock·seed import adapter
- 사건 lifecycle state machine
- `POST /incidents/import-from-seed`
- `GET /incidents`
- `GET /incidents/{incidentId}`
- `POST /incidents/{incidentId}/memberships`
- `POST /incidents/{incidentId}/commanders`
- `POST /incidents/{incidentId}/close`

**provides**

- `MembershipView.accounts(incidentId)`
- `MembershipView.commanders(incidentId)`
- `MembershipView.notificationTargets(incidentId, targetPolicy)`
- `IncidentAccessResolver`
- `IncidentTerminalSnapshot.closed(incidentId)`
- `INCIDENT_CREATED`
- `INCIDENT_MEMBERSHIP_CHANGED`
- `INCIDENT_CLOSED`
- `events/incident.payload.schema.json` for `INCIDENT_CREATED`
- `events/incident_membership.payload.schema.json` for `INCIDENT_MEMBERSHIP_CHANGED`
- `events/incident_terminal.payload.schema.json` for `INCIDENT_CLOSED`
- `PublishRequest.INCIDENT_CREATED`
- `PublishRequest.INCIDENT_MEMBERSHIP_CHANGED`
- `PublishRequest.INCIDENT_CLOSED`
- `incident.schema.json`
- `missing_person_cache.schema.json`
- `incident_membership.schema.json`

**consumes**

- S1-2: account, role, channel, session
- S1-3: purge orchestration
- S4: `EventHub.publish`
- S5: `ReferenceMarkerSeed.createForIncident(incidentId, seedMarkers)`
- S8: OP1 bootstrap

**fr**

- FR-01, FR-22

**common_rule_refs**

- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.5 Purge Order`
- `spec/boundaries.md §4.6 Channel / Role Matrix`

**acceptance_hints**

- `POST /incidents/import-from-seed` creates `incident`, `missing_person_cache`, memberships, and emits `INCIDENT_CREATED` only after the domain write is committed.
- Incident lifecycle rejects writes before `OPEN` with `incident_bootstrapping` and after close with `incident_closed`.
- `POST /incidents/{incidentId}/memberships` and `/commanders` update `MembershipView.*` and emit `INCIDENT_MEMBERSHIP_CHANGED`.
- `POST /incidents/{incidentId}/close` is terminal, exposes `IncidentTerminalSnapshot.closed`, and triggers purge orchestration without re-open support.

**excluded**

- 실제 경찰 시스템 직접 연동
- 부대·팀 편제 관리 UI
- 지도 기준 범위와 구역 편집
- 경로·마커 작성
- 사용자 화면용 운영 기록 조회

**nfr**

- import 중 상태: `CREATING` → `BOOTSTRAPPING` → `OPEN`
- `INCIDENT_CREATED` 후 S8이 OP1을 생성해야 `OPEN` 전이가 완료된다.
- 사건 종료는 terminal이다. 재오픈 API 없음.
- 종료 시 실종자 운영 캐시는 active DB와 API 응답에서 제거한다.

---

### S1-2 · Account, Device & RBAC

**owns**

- `account`
- `account_session`
- `device`
- `fcm_token`
- account type: `TEAM`, `PATROL_CAR`, `COMMAND`
- affiliation: `MISSING_TEAM`, `SUPPORT_UNIT`, `LOCAL_POLICE`
- role: `MISSING_TEAM_COMMANDER`, `FIELD_COMMANDER`, `MEMBER`
- `POST /auth/login`
- `POST /auth/logout`
- `POST /fcm/tokens`
- `POST /police-phones/{policePhoneId}/heartbeat`

**provides**

- `SecurityContext.accountId`
- `SecurityContext.accountType`
- `SecurityContext.affiliation`
- `SecurityContext.roles`
- `SecurityContext.channel`
- `SecurityContext.policePhoneId`
- `@RequireIncidentAccess`
- `@RequireRole`
- `@RequireChannel`
- `@RequirePolicePhone`
- `@RequirePolicePhoneRegistered`
- `@RequirePolicePhoneAssigned`
- `PolicePhoneFreshnessQuery.byIncident(incidentId)`
- `FcmTokenQuery.activeByPolicePhone(policePhoneId)`
- `POLICE_PHONE_HEARTBEAT_UPDATED`
- `events/police_phone_heartbeat.payload.schema.json` for `POLICE_PHONE_HEARTBEAT_UPDATED`
- `PublishRequest.POLICE_PHONE_HEARTBEAT_UPDATED`
- `account.schema.json`
- `police_phone.schema.json`

**consumes**

- S1-1: `incident_membership`
- S4: `EventHub.publish`

**fr**

- FR-01, FR-02, FR-24, FR-25

**common_rule_refs**

- `spec/boundaries.md §4.1 Error Codes`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.6 Channel / Role Matrix`

**acceptance_hints**

- Login/logout establishes `SecurityContext` fields used by `@RequireIncidentAccess`, `@RequireRole`, and `@RequireChannel`.
- App-only PolicePhone APIs reject missing, unregistered, or unassigned police phones with the matching guard error code.
- `POST /police-phones/{policePhoneId}/heartbeat` updates the single freshness source and emits `POLICE_PHONE_HEARTBEAT_UPDATED`.
- `POST /fcm/tokens` stores tokens for authenticated app police phones without granting notification routing ownership.

**excluded**

- 자체 회원가입 UI
- 비밀번호 재설정 UI
- 부대·팀 편제 관리 UI
- 사람 단위 현장 로그인 강제

**nfr**

- 폴리폰 앱은 팀 계정 또는 순찰차 계정 장기 로그인 유지가 가능하다.
- Web 상황판은 지휘·상황 공유 목적의 표준 세션을 사용한다.
- 동일 채널 중복 로그인 정책은 운영 전 확정 전까지 `same account multi-session allowed, device heartbeat wins`로 둔다.
- Device heartbeat는 상황판 단말 최신성 인코딩의 단일 기준이다.

---

### S1-3 · Retention & Operational Records

**owns**

- `location_access_log` (internal/system-only)
- `purge_run` (internal/system-only)
- 운영 로그 sink 연동 (internal/system-only)
- purge orchestration

**provides**

- `PurgeCoordinator.closeIncident(incidentId)`
- `PurgeCoordinator.purgeIncident(incidentId)`
- `LocationAccessRecorder.record(accountId, incidentId, purpose, serverTs)`
- `IncidentTombstoneSnapshot.byIncident(incidentId)`
- `INCIDENT_PURGED`
- `events/incident_purge.payload.schema.json` for `INCIDENT_PURGED`
- `PublishRequest.INCIDENT_PURGED`

**internal/system-only provides**

- `RetentionPurgeStatus.byIncident(incidentId)`
- `purge_run.schema.json`
- `location_access_log.schema.json`

**consumes**

- S1-1: incident close state
- S3-1: `PathPurgeHook`
- S5: `MarkerPhotoPurgeHook`
- S6: `LocalSyncPurgeHook`
- S7: `PackagePurgeHook`
- S4: `INCIDENT_CLOSED`, `EventHub.publish`

**fr**

- FR-22, NFR-04, PRD §8.5, PRD §8.7

**common_rule_refs**

- `spec/boundaries.md §4.2 Time, Limits, Retention`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.5 Purge Order`

**acceptance_hints**

- `PurgeCoordinator.closeIncident` and `purgeIncident` call registered purge hooks with `incidentId`, `purgeRunId`, `closedAt`, and `purgeDeadlineTs`.
- `INCIDENT_PURGED` is published only after every purge hook succeeds; failed hooks leave retryable `purge_run` state.
- `LocationAccessRecorder.record` writes internal/system-only access records and never exposes them through user-facing DTOs.
- `IncidentTombstoneSnapshot.byIncident` returns only the sanitized terminal summary fields.

**excluded**

- 사용자 화면용 조회 UI
- 사건 운영 화면 내 장기 이력 탭
- 공식 수사 기록 보관

**nfr**

- 위치정보 접근기록, 업무폰·순찰차 위치·경로 좌표, SSAFY 시연·개발 데이터 보관·파기는 `spec/boundaries.md §4.2 Time, Limits, Retention` 기준 적용.
- 운영 로그와 접속기록은 관찰·보안 목적의 백오피스성 데이터다. MVP 사용자 기능으로 노출하지 않는다.
- `RetentionPurgeStatus`, `purge_run`, `location_access_log`, 운영 로그 sink는 internal/system-only 계약이다. 사용자 UI, 앱 캐시, 오프라인 패키지, S3-2 상황판 조회에 노출하지 않는다.
- S1-3 public tombstone DTO는 sanitized read-only summary로 제한한다. 허용 필드는 `incidentId`, `terminalStatus`/`closedStatus`, `closedAt`, `writeDisabledReason`, `localPurgeState`뿐이다.
- Internal retention DTO의 purge run detail, access log detail, operator/system trace는 user-facing DTO에 포함하지 않는다.
- purge hooks는 S1-3가 `INCIDENT_CLOSED` 이후 purge job에서 호출한다. 입력은 `incidentId`, `purgeRunId`, `closedAt`, `purgeDeadlineTs`이고 출력은 `{status, purgedCount, retainedCount, errorCode}`다.
- purge hook은 idempotent해야 한다. 실패 시 `purge_run`을 failed/retryable로 남기고 `INCIDENT_PURGED`는 모든 hook 성공 후에만 발행한다.

---

### S2 · Map Boundary & Search Area

**owns**

- `map_boundary`
- `search_area`
- `search_area_history`
- `POST /incidents/{incidentId}/map-boundary`
- `PATCH /incidents/{incidentId}/map-boundary`
- `GET /incidents/{incidentId}/map-boundary`
- `POST /search-areas`
- `PATCH /search-areas/{areaId}`
- `POST /search-areas/{areaId}/split`
- `PATCH /search-areas/{areaId}/state`

**provides**

- `MapBoundaryQuery.of(incidentId)`
- `AreaQuery.byIncident(incidentId, filters)`
- `AreaQuery.byOp(opId, filters)`
- `SEARCH_AREA_CHANGED`
- `SEARCH_AREA_ASSIGNMENT_CHANGED`
- `events/search_area.payload.schema.json` for `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED`
- `PublishRequest.SEARCH_AREA_CHANGED`
- `PublishRequest.SEARCH_AREA_ASSIGNMENT_CHANGED`
- `search_area.schema.json`
- `search_area_history.schema.json`

**consumes**

- S1-1: incident lifecycle, membership
- S1-2: `@RequireIncidentAccess`, `@RequireRole`, `@RequireChannel`, `spec/boundaries.md §4.6 Channel / Role Matrix` 지도·구역 행
- S4: `EventHub.publish`
- S6: `@IdempotentWrite`, `IdempotentWrite.reserveAndReplay(idempotencyKey, bodyHash)`
- S8: active OP and OP assignment

**fr**

- FR-06, FR-07, FR-12, FR-13, FR-23

**common_rule_refs**

- `spec/boundaries.md §4.1 Error Codes`
- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.6 Channel / Role Matrix`

**acceptance_hints**

- Map boundary write APIs enforce one active boundary per incident, valid Polygon geometry, and Web command authorization.
- Search area create/split/state APIs persist `search_area_history` with account, time, OP, previous state, next state, and memo.
- Boundary and area writes publish the matching `PublishRequest.*` contract with the declared event payload schema.
- Area state changes reject invalid transitions and never auto-confirm missing search coverage.

**excluded**

- 자동 누락 확정
- 다음 투입 구역 자동 지시
- 상황판 shell 조립
- OP 생성 API
- 마커

**nfr**

- 지도 기준 범위는 사건별 1개 active boundary를 가진다.
- `map_boundary`와 `search_area`는 유효한 Polygon만 허용한다. 빈 geometry, 자기 교차, SRID 불일치는 `invalid_geometry`로 거부한다.
- 구역 완료 처리는 `spec/boundaries.md §4.6 Channel / Role Matrix` `구역 완료` 행과 S1-2 `@RequireChannel`, `@RequireRole` 기준 적용.
- `search_area_history`에는 처리 계정, 처리 시각, OP, 이전 상태, 다음 상태, 메모를 기록한다.
- `접근 곤란`은 구역 상태가 아니라 S5 지형 상태 마커로 표현한다.

---

### S3-1 · PolicePhone Path Collection

**owns**

- `search_session`
- `search_path`
- `path_segment`
- Android foreground location service
- `POST /search-sessions`
- `PATCH /search-sessions/{sessionId}`
- `POST /search-paths/batch`
- `GET /search-paths`
- `PATCH /path-segments/{segmentId}`

**provides**

- `PathQuery.byIncident(incidentId, filters)`
- `PathQuery.byOp(opId, filters)`
- `PathQuery.byPolicePhone(policePhoneId, filters)`
- `PATH_APPENDED`
- `PATH_SEGMENT_UPDATED`
- `SEARCH_SESSION_STARTED`
- `SEARCH_SESSION_ENDED`
- `events/search_session.payload.schema.json` for `SEARCH_SESSION_STARTED`, `SEARCH_SESSION_ENDED`
- `events/search_path.payload.schema.json` for `PATH_APPENDED`
- `events/path_segment.payload.schema.json` for `PATH_SEGMENT_UPDATED`
- `PublishRequest.SEARCH_SESSION_STARTED`
- `PublishRequest.SEARCH_SESSION_ENDED`
- `PublishRequest.PATH_APPENDED`
- `PublishRequest.PATH_SEGMENT_UPDATED`
- `PathPurgeHook.purgeIncidentPaths(incidentId, purgeRunId, closedAt, purgeDeadlineTs)`
- `search_session.schema.json`
- `search_path.schema.json`
- `path_segment.schema.json`

**consumes**

- S1-1: incident lifecycle, membership
- S1-2: PolicePhone, `@RequirePolicePhone`, `@RequireChannel`, `spec/boundaries.md §4.6 Channel / Role Matrix` 수색 세션·경로 행
- S4: `EventHub.publish`
- S6: `SyncClient.enqueue(writeOperation)`, `@IdempotentWrite`, `IdempotentWrite`, `POST /sync/clock`
- S8: current OP

**fr**

- FR-02, FR-03, FR-04, FR-25, FR-33, FR-34

**common_rule_refs**

- `spec/boundaries.md §4.2 Time, Limits, Retention`
- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.6 Channel / Role Matrix`

**acceptance_hints**

- Search session start/end APIs require app channel, assigned PolicePhone, open incident, idempotency key, and current OP.
- `POST /search-paths/batch` records PolicePhone-based path points with `accountId`, `policePhoneId`, `opId`, sequence, and timestamps.
- Path and session writes publish the matching `PublishRequest.*` contract and can be replayed from S6 Outbox without duplication.
- `PATCH /path-segments/{segmentId}` applies only the allowed channel policy and emits `PATH_SEGMENT_UPDATED`.

**excluded**

- 경로 포인트 임의 수정·삭제
- 상황판 shell 조립
- OP 생성
- 지도 타일 다운로드

**nfr**

- GPS 수집 및 경로 서버 전송 주기는 `spec/boundaries.md §4.2 Time, Limits, Retention` 기준 적용.
- 경로는 Device 기준으로 기록하고, 조작 계정은 write payload에 함께 남긴다.
- 차량·도보 구간은 GPS 속도 기반으로 자동 분리한다.
- 구간 유형 수동 보정 채널은 S1-2 `@RequireChannel` 기준 적용.
- `opId`는 수집 시점 current OP 기준이다. 서버 current OP와 불일치하면 `409 op_mismatch`를 반환한다.

---

### S3-2 · Situation Board Shell & Projection

**owns**

- Web 상황판 route/layout
- MapLibre root
- board shell slot mounting
- shared state wiring
- board projection/snapshot read model
- slot-scoped rendering implementation
- final merge
- marker detail/edit/delete panel
- OP 비교 화면
- 단말 최신성 인코딩
- `GET /incidents/{incidentId}/board/snapshot`

**provides**

- `BoardAssembler`
- `BoardSnapshotProjector`
- `BoardDTO`
- `board.schema.json`
- `spec/boundaries.md §9.2 Board Shell Slots` registry

**consumes**

- S1-1: incident, missing person cache, membership
- S1-2: account, Device heartbeat
- S2: map boundary, search area
- S3-1: path, segment
- S4: SSE stream
- S5: marker, notification events
- S7: offline package status, tile server health
- S8: OP, assignment, handover memo, AI summary

**fr**

- FR-05, FR-07, FR-08, FR-09, FR-11, FR-18, FR-19, FR-23, FR-24, FR-25, FR-26, FR-27, FR-37, FR-39

**common_rule_refs**

- `spec/boundaries.md §9.1 Event Envelope`
- `spec/boundaries.md §9.2 Board Shell Slots`

**acceptance_hints**

- `GET /incidents/{incidentId}/board/snapshot` returns `BoardDTO` from the projection/read model within the board snapshot performance target.
- Each slot is mounted only through the S3-2 shell and consumes the source contract declared in `spec/boundaries.md §9.2 Board Shell Slots`.
- SSE updates are applied idempotently by `eventId`; stale replay recovery falls back to snapshot reload.
- Marker detail/edit/delete panel changes stay inside S3-2 layout ownership while domain writes remain with S5.

**excluded**

- domain write API
- 이벤트 발행
- FCM 발송
- Android 현장 입력 UI
- 자동 판단

**nfr**

- board snapshot p95 < 2s를 목표로 한다.
- S3-2 shell/slot 소유권과 납품 규칙은 `spec/boundaries.md §9.2 Board Shell Slots` 기준 적용.
- S4 `EventFanout`이 board projector trigger를 orchestration하고, S3-2는 trigger 이후 projection 적용, snapshot read model 조회, 화면 렌더링 구현만 소유한다.
- `BoardSnapshotProjector`는 mockable port다. 외부 연동 없이 fixture projector로 대체할 수 있어야 한다.
- `lastHeartbeatAt`/`lastSyncAt` 경과는 위치 점 색·외곽선·라벨로 표시한다. 별도 알림은 만들지 않는다.
- 운용 중 PolicePhone 경로는 현재 세션의 `policePhoneId` 기준으로 강조한다.
- 초기 뷰포트 fallback 순서: map boundary → 최근 활동 위치 → 기본 지역.
- FR-09는 필터 기능이 아니라 최신 수색 현황의 기록 시각 표시를 의미한다. OP/팀/마커/구간 필터는 S3-2 display-only UX로만 취급한다.
- FR-18은 알림 표시가 아니라 팀/경로/구역/마커/실종자 정보를 한 화면에서 정리해 보는 공용 상황판 reference view를 의미한다.

---

### S4 · Realtime Event Hub

**owns**

- `event_outbox`
- `events/_base.schema.json`
- `sse_event_log`
- SSE endpoint
- `EventHub.publish`
- `EventFanout`
- Last-Event-ID replay
- event outbox dispatch loop

**provides**

- `EventHub.publish(event)`
- `EventFanout.dispatch(outboxRow)`
- `GET /events?incidentId={incidentId}`
- `BaseEvent`
- `event_outbox.schema.json`
- `sse_event_log.schema.json`

**consumes**

- S1-2: authenticated account and incident access
- S3-2: `BoardSnapshotProjector` trigger port
- S5: `FcmDispatcher` delivery port

**fr**

- 모든 실시간 반영 FR의 공통 기반

**common_rule_refs**

- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §9.1 Event Envelope`

**acceptance_hints**

- `EventHub.publish(PublishRequest)` stages `event_outbox` inside the caller domain transaction and preserves the `BaseEvent` envelope. Commit 이후 전송·재시도는 `EventFanout.dispatch`/worker가 수행한다.
- `GET /events?incidentId={incidentId}` authenticates incident access, supports Last-Event-ID replay, and deduplicates by `eventId`.
- `EventFanout.dispatch` owns SSE delivery, FCM delivery port calls, board projector triggers, retries, and failure injection points.
- `INCIDENT_PURGED` removes replay data for the incident and prevents later SSE replay from old logs.

**excluded**

- domain event payload 임의 변경
- notification payload/recipient 계산
- FCM adapter 구현
- board snapshot 조립
- domain transaction commit

**nfr**

- event envelope 구조는 `spec/boundaries.md §9.1 Event Envelope` 기준 적용.
- `event_outbox` 적재 후 fanout orchestration은 S4 `EventFanout` 경계다. SSE 송신, FCM delivery 호출, board projector trigger의 순서, 재시도, 실패 주입 지점은 S4가 정의한다.
- `eventId`는 사건 내 중복 수신 제거의 기준이다.
- SSE replay는 event log 순서를 보존해야 하며, 소비자는 이미 적용한 `eventId`를 다시 적용하지 않는다.
- 인증 실패 시 SSE stream을 종료한다.
- Last-Event-ID가 너무 오래됐으면 `409 gone_snapshot_required`를 반환하고 S3-2 snapshot 재조회로 복구한다.
- `INCIDENT_PURGED` 수신 후 해당 사건의 SSE replay data를 파기한다.
- 실패 주입 지점은 outbox 적재 후 SSE 미송신, `FcmDispatcher` adapter 실패, board projector trigger 실패/지연을 포함한다.

---

### S5 · Markers / Photo / Notification Delivery

**owns**

- `marker`
- `photo`
- `notification_delivery`
- S3 presigned upload
- notification payload/recipient 계산
- `FcmDispatcher` adapter 구현
- Android marker bottom sheet
- `POST /markers`
- `PATCH /markers/{markerId}`
- `DELETE /markers/{markerId}`
- `POST /markers/{markerId}/photos/presign`
- `POST /markers/{markerId}/photos/{photoId}/finalize`

**provides**

- `MarkerQuery.byIncident(incidentId, filters)`
- `FcmDispatcher.send(recipients, payload)`
- `NotificationRecipientResolver.resolve(event)`
- `NotificationPayloadFactory.from(event)`
- `MARKER_CREATED`
- `MARKER_UPDATED`
- `MARKER_DELETED`
- `SUPPORT_REQUEST_CREATED`
- `PERSON_FOUND`
- `events/marker.payload.schema.json` for `MARKER_CREATED`, `MARKER_UPDATED`, `MARKER_DELETED`
- `events/notification_delivery.payload.schema.json` for `SUPPORT_REQUEST_CREATED`, `PERSON_FOUND`
- `PublishRequest.MARKER_CREATED`
- `PublishRequest.MARKER_UPDATED`
- `PublishRequest.MARKER_DELETED`
- `PublishRequest.SUPPORT_REQUEST_CREATED`
- `PublishRequest.PERSON_FOUND`
- `ReferenceMarkerSeed.createForIncident(incidentId, seedMarkers)`
- `MarkerPhotoPurgeHook.purgeIncidentMarkerPhotos(incidentId, purgeRunId, closedAt, purgeDeadlineTs)`
- `marker.schema.json`
- `photo.schema.json`
- `notification_delivery.schema.json`

**consumes**

- S1-1: membership and notification targets
- S1-2: account, Device, FCM token, `@RequireChannel`
- S4: `EventHub.publish`
- S6: `SyncClient.enqueue(writeOperation)`, `@IdempotentWrite`, `IdempotentWrite`, `POST /sync/clock`
- S8: current OP

**fr**

- FR-10, FR-14, FR-15, FR-16, FR-17, FR-20, FR-30

**common_rule_refs**

- `spec/boundaries.md §4.2 Time, Limits, Retention`
- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.6 Channel / Role Matrix`

**acceptance_hints**

- `POST /markers` accepts app-channel marker writes with current OP, assigned Device, idempotency, and valid marker location.
- Photo presign/finalize APIs enforce count, size, and TTL limits while keeping official evidence storage out of scope.
- Marker create/update/delete publishes the matching `PublishRequest.*` contract and updates `MarkerQuery.byIncident`.
- `NotificationRecipientResolver` and `NotificationPayloadFactory` produce marker-derived delivery rows or assignment FCM payload input without owning fanout orchestration.

**excluded**

- 상황판 shell
- marker detail/edit/delete panel layout
- 외부 수색 자산 실제 출동 요청·승인·연동
- 공식 증거 편입

**nfr**

- 현장 마커 생성 채널은 `spec/boundaries.md §4.6 Channel / Role Matrix` `현장 마커 생성` 행과 S1-2 `@RequireChannel` 기준 적용.
- 마커 조회·수정·삭제 UI와 초기 기준 마커 보정 권한은 `spec/boundaries.md §4.6 Channel / Role Matrix` 관련 행 및 S1-2 `@RequireChannel`, `@RequireRole` 기준 적용.
- 마커는 `incidentId`, `opId`, `accountId`, `policePhoneId`, `clientTs`, `serverTs`, `location`, `type`, `memo`, `photos`를 가진다.
- 사진 제한은 `spec/boundaries.md §4.2 Time, Limits, Retention` 기준 적용.
- `notification_delivery`는 알림 저장 엔티티이며 pending/status/version을 기록한다.
- 지원 요청 알림은 실종팀 지휘 계정과 현장 지휘관 역할 계정 우선이다.
- 실종자 발견 알림은 사건 배정 계정·단말 전체 대상이다.
- 지원 부대 배정 알림은 `INCIDENT_MEMBERSHIP_CHANGED` fanout 시 신규 배정된 `TEAM_PHONE`, `PATROL_CAR_PHONE` 단말에만 FCM data message로 전달한다. 이 알림은 `notification_delivery` row를 만들지 않으며, 지휘 계정 policePhoneId는 Android FCM recipient로 고정하지 않는다.
- Web toast와 FCM push는 별도 저장 엔티티가 아니라 S4 `EventFanout`과 S5 notification payload/recipient 및 `FcmDispatcher` adapter의 전달 계약이다.
- FCM fanout orchestration은 S5가 소유하지 않는다. S5는 S4가 호출할 수 있는 `FcmDispatcher` port와 fixture/mock adapter를 제공하며, 실제 외부 FCM 없이 대체 가능해야 한다.

---

### S6 · Offline Sync & Local Warnings

**owns**

- `sync_event`
- Android Room/SQLite local store
- Android Outbox
- WorkManager retry policy
- idempotency middleware
- local warning monitor
- `POST /sync/clock`
- `POST /sync/outbox/requeue`

**provides**

- `SyncClient.enqueue(writeOperation)`
- `@IdempotentWrite`
- `IdempotentWrite.reserveAndReplay(idempotencyKey, bodyHash)`
- `POST /sync/clock`
- `OutboxRow(outboxId, operationId, incidentId, policePhoneId, sequence, method, endpoint, bodyHash, idempotencyKey, status, attemptCount, nextAttemptAt, clientTs, serverAckTs, lastError)`
- `OutboxReplay.flushPending(policePhoneId, incidentId)`
- `OutboxRequeue.requeue(operationId, reason)`
- `LocalSyncPurgeHook.purgeIncidentLocalSync(incidentId, purgeRunId, closedAt, purgeDeadlineTs)`
- `IdempotencyKeyGenerator.next()`
- `LocalWarningMonitor`
- `sync_event.schema.json`
- `write_operation.schema.json`
- `sync_status.schema.json`

**consumes**

- S1-1: incident close/purge events
- S1-2: auth session, Device
- S3-1: path write operations
- S5: marker/photo write operations
- S7: package status

**fr**

- FR-03, FR-28, FR-29

**common_rule_refs**

- `spec/boundaries.md §4.1 Error Codes`
- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.5 Purge Order`

**acceptance_hints**

- `@IdempotentWrite` reserves and replays responses by `idempotencyKey` and `bodyHash`, returning `idempotency_mismatch` on conflicting bodies.
- Android Outbox rows follow the declared status set and flush only failed pending items after partial success.
- `POST /sync/clock` returns `clientTs`, `serverTs`, and `clockOffsetMs` for later write contracts.
- `LocalWarningMonitor` shows GPS, battery, and map-package warnings without requiring server connectivity.

**excluded**

- domain CRUD ownership
- 지도 타일 다운로드
- SSE fanout
- FCM

**nfr**

- 서버 write는 idempotency key를 요구한다.
- 동일 key 재전송은 기존 응답 replay 또는 중복 제거로 처리한다.
- clock sync는 `clientTs`, `serverTs`, `clockOffsetMs`를 계산한다.
- Outbox flush 중 일부 항목만 실패하면 성공 항목은 ack 처리하고 실패 항목만 큐에 남긴다.
- Outbox 실패 누적 > 10건이면 앱 배너를 표시한다.
- Outbox row status는 `PENDING`, `SENDING`, `ACKED`, `FAILED_RETRYABLE`, `FAILED_FINAL`, `PURGED` 중 하나다.
- `write_operation.schema.json`은 REST body, S6 Outbox row, replay/requeue local contract가 공유하는 `operationId`, `incidentId`, `policePhoneId`, `sequence`, `opId`, `idempotencyKey`, `clientTs`, `bodyHash`, `endpoint`, `method`, `payload` 필드를 정의한다.
- GPS 중단, 배터리 저하, 지도 미다운로드 경고는 서버 연결 없이 표시한다.
- 사건 purge 수신 시 flush 완료 항목부터 로컬 삭제한다.

---

### S7 · Offline Tiles & Incident Package

**owns**

- `tileserver-gl`
- `offline_package_manifest`
- `offline_package_installation`
- MapLibre style
- `GET /tiles/{style}/{z}/{x}/{y}.pbf`
- `GET /tiles/styles/{styleId}.json`
- `GET /incidents/{incidentId}/offline-package/manifest`
- `POST /incidents/{incidentId}/offline-package/installations`

**provides**

- `OfflinePackageManifest`
- `OfflinePackageInstallationQuery.byIncident(incidentId)`
- `OFFLINE_PACKAGE_INSTALLATION_CHANGED`
- `events/offline_package_installation.payload.schema.json` for `OFFLINE_PACKAGE_INSTALLATION_CHANGED`
- `PublishRequest.OFFLINE_PACKAGE_INSTALLATION_CHANGED`
- `PackagePurgeHook.purgeIncidentPackage(incidentId, purgeRunId, closedAt, purgeDeadlineTs)`
- `offline_package_manifest.schema.json`
- `offline_package_installation.schema.json`

**consumes**

- S1-1: incident, missing person
- S1-2: account, PolicePhone
- S2: overall search area, assigned areas
- S4: `EventHub.publish`
- S5: initial markers
- S6: `SyncClient.enqueue(writeOperation)`, `@IdempotentWrite`
- S8: OP and assignments

**fr**

- FR-19, FR-21, FR-31

**common_rule_refs**

- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §9.2 Board Shell Slots`

**acceptance_hints**

- Manifest fetch returns incident metadata, missing person data, OP, assigned areas, initial markers, overall search area, and tile references as one package contract.
- Package status writes require app PolicePhone authorization and publish `OFFLINE_PACKAGE_INSTALLATION_CHANGED`.
- Overall search area changes increase manifest revision and make stale package installation status observable through `OfflinePackageInstallationQuery.byIncident`.
- Tile and style endpoints keep OSM attribution available to app and Web MapLibre consumers.

**excluded**

- 현장 마커 작성
- 경로 수집
- 구역 편집
- 상황판 shell 전체 소유

**nfr**

- 오프라인 패키지는 타일 단독 다운로드가 아니다. 사건 메타, 실종자 정보, OP, 담당 구역, 초기 마커, 지도 기준 범위, 타일을 한 흐름으로 적재한다.
- map boundary 변경 후 manifest revision이 증가해야 한다.
- 미완료 단말은 S3-2 `package_badge` slot에서 표시한다.
- OSM attribution을 표시한다.
- S7에서 FR-21을 다룰 때의 의미는 실종자 기본 정보의 오프라인 패키지 포함이다. 타일 서버 자체는 FR-19/FR-31 및 ADR-0024/ADR-0028 구현 근거로 취급한다.

---

### S8 · Operational Period & Handover

**owns**

- `operational_period`
- `op_assignment`
- `handover_memo`
- `search_history_summary`
- `POST /operational-periods`
- `GET /incidents/{incidentId}/operational-periods`
- `POST /operational-periods/{opId}/assignments`
- `POST /handover-memos`
- `GET /handover-memos`
- `GET /operational-periods/{operationalPeriodId}/search-history-summaries`
- `SearchHistorySummaryGenerationJob` (server-side trigger only)

**provides**

- `OperationalPeriodQuery.current(incidentId)`
- `OperationalPeriodQuery.list(incidentId)`
- `AssignmentQuery.byOp(opId)`
- `HandoverMemoQuery.byContext(context)`
- `HandoverStatusSnapshot.byIncident(incidentId)`
- `AiSummaryQuery.byOp(opId)`
- `OP_TRANSITIONED`
- `OP_ASSIGNMENT_CHANGED`
- `HANDOVER_MEMO_CREATED`
- `AI_SUMMARY_READY`
- `events/operational_period.payload.schema.json` for `OP_TRANSITIONED`
- `events/op_assignment.payload.schema.json` for `OP_ASSIGNMENT_CHANGED`
- `events/handover_memo.payload.schema.json` for `HANDOVER_MEMO_CREATED`
- `events/search_history_summary.payload.schema.json` for `AI_SUMMARY_READY`
- `PublishRequest.OP_TRANSITIONED`
- `PublishRequest.OP_ASSIGNMENT_CHANGED`
- `PublishRequest.HANDOVER_MEMO_CREATED`
- `PublishRequest.AI_SUMMARY_READY`
- `operational_period.schema.json`
- `op_assignment.schema.json`
- `handover_memo.schema.json`
- `search_history_summary.schema.json`

**consumes**

- S1-1: incident lifecycle and membership
- S1-2: role and channel
- S2: search area
- S3-1: path summary input
- S4: `EventHub.publish`
- S5: marker summary input

**fr**

- FR-12, FR-13, FR-23, FR-32, FR-35, FR-37, FR-39

**common_rule_refs**

- `spec/boundaries.md §4.3 Transaction Rule`
- `spec/boundaries.md §4.4 Event Catalog`
- `spec/boundaries.md §4.6 Channel / Role Matrix`
- `spec/boundaries.md §9.2 Board Shell Slots`

**acceptance_hints**

- OP1 is created during incident bootstrap; OP2 and later require Web command authorization and an allowed creation reason.
- Assignment writes persist `op_assignment`, update `AssignmentQuery.byOp`, and emit `OP_ASSIGNMENT_CHANGED`.
- Handover memo writes support app and Web channels, preserve context, and emit `HANDOVER_MEMO_CREATED`.
- AI summary generation uses only OP/path/marker/memo history and leaves manual memo and OP comparison usable on failure.

**excluded**

- 자동 수색 구역 추천
- 공식 보고서 초안 생성
- 무전 음성 인식
- 과거 사례/RAG 위험 구역 조회

**nfr**

- OP1은 사건 bootstrap 중 자동 생성된다.
- OP2 이후는 수동 생성하며, 생성 권한·채널은 `spec/boundaries.md §4.6 Channel / Role Matrix` `OP 생성` 행과 S1-2 `@RequireChannel`, `@RequireRole` 기준 적용.
- OP 생성 사유는 `SHIFT_CHANGE`, `RE_SEARCH`, `NEW_AREA`, `OTHER` 중 하나다.
- `OTHER` 사유는 사유 메모 또는 인수인계 메모를 함께 남긴다.
- AI 요약은 OP/경로/마커/메모 기반 이력 요약만 한다.
- AI 요약 실패 시 수동 메모와 OP 비교 화면은 계속 동작해야 한다.

---

## 4. 공통 계약 카탈로그

### 4.1 Error Codes

| code | 의미 | 기본 HTTP |
|---|---|---:|
| `incident_bootstrapping` | 사건 초기화 완료 전 쓰기 요청 | 409 |
| `incident_closed` | 종료된 사건에 쓰기 요청 | 409 |
| `incident_access_denied` | 사건 배정 계정이 아님 | 403 |
| `team_not_assigned` | 팀 계정이 사건·OP·구역 배정 대상이 아님 | 403 |
| `role_denied` | 필요한 역할 없음 | 403 |
| `channel_not_allowed` | 허용되지 않은 채널의 API 호출 또는 쓰기 | 403 |
| `police_phone_required` | PolicePhone이 필요한 앱 요청에 PolicePhone 없음 | 400 |
| `police_phone_not_registered` | 등록되지 않았거나 세션과 연결되지 않은 PolicePhone | 403 |
| `police_phone_not_assigned` | PolicePhone이 사건·팀·OP 배정에 연결되지 않음 | 403 |
| `op_required` | current OP가 없음 | 409 |
| `op_mismatch` | payload OP와 서버 current OP 불일치 | 409 |
| `area_state_conflict` | 구역 상태 전이 불가 | 409 |
| `map_boundary_required` | 지도 기준 범위 필요 | 409 |
| `idempotency_mismatch` | 같은 key에 다른 body 재전송 | 409 |
| `write_conflict` | 서버 수신 시각 기준 충돌 | 409 |
| `clock_skew_exceeded` | 단말 시각 차이 허용치 초과 | 409 |
| `invalid_geometry` | 유효하지 않은 공간 geometry | 400 |
| `photo_limit_exceeded` | 사진 수량 또는 크기 제한 초과 | 413 |
| `tile_unavailable` | 타일 서버 또는 manifest 오류 | 503 |
| `gone_snapshot_required` | SSE replay 불가, snapshot 재조회 필요 | 409 |
| `summary_unavailable` | AI 요약 생성 실패 | 503 |

#### 4.1.1 Common Geometry Rule

공간 geometry를 받는 domain write는 공통으로 빈 geometry, SRID 불일치, geometry type 불일치를 `invalid_geometry`로 거부하되, 각 geometry 필드의 도메인 소유권은 해당 Spec의 owns 기준을 따른다. S2는 `map_boundary`/`search_area` Polygon 도메인 검증과 `MapBoundaryQuery.of(incidentId)` 제공만 소유한다. S3-1은 `search_path`/`path_segment`, S5는 `marker`/`photo` 위치 필드 검증을 이 공통 규칙과 S2의 boundary query를 입력으로 수행한다.

### 4.2 Time, Limits, Retention

| 항목 | 기준 |
|---|---|
| GPS 수집 | 5초 |
| 경로 서버 전송 | 10초 batch |
| 단말 stale 표시 | 60초 이후 stale, 5분 이후 lost |
| 사진 제한 | 마커당 10장, 파일당 10MB |
| presigned URL TTL | 15분 |
| 위치정보 접근기록 | 최소 6개월, 실제 운영 전 법무 확인 |
| 업무폰·순찰차 위치·경로 좌표 | 사건 종료 후 동기화 완료 확인 뒤 파기 |
| SSAFY 시연·개발 데이터 | 복구 확인용 24시간 soft delete 후 파기 |

### 4.3 Transaction Rule

모든 domain write는 아래 순서를 따른다.

1. authorization
2. incident lifecycle guard
3. channel guard
4. idempotency key reserve
5. domain table write
6. S4 `EventHub.publish(PublishRequest)` 호출과 `event_outbox` stage
7. transaction commit
8. `EventFanout.dispatch`/worker fanout
9. idempotency response cache

### 4.4 Event Catalog

| event | publisher | 주요 소비자 |
|---|---|---|
| `INCIDENT_CREATED` | S1-1 | S8, S3-2 |
| `INCIDENT_MEMBERSHIP_CHANGED` | S1-1 | S1-2, S3-2, S5 |
| `INCIDENT_CLOSED` | S1-1 | S1-3, S3-2, S6, S7, S4 EventFanout -> S5 `FcmDispatcher` |
| `INCIDENT_PURGED` | S1-3 | S4, S6, S7 |
| `POLICE_PHONE_HEARTBEAT_UPDATED` | S1-2 | S3-2 |
| `SEARCH_AREA_CHANGED` | S2 | S3-2, S7 |
| `SEARCH_AREA_ASSIGNMENT_CHANGED` | S2 | S3-2, S7 |
| `SEARCH_SESSION_STARTED` | S3-1 | S3-2 |
| `SEARCH_SESSION_ENDED` | S3-1 | S3-2, S8 |
| `PATH_APPENDED` | S3-1 | S3-2 |
| `PATH_SEGMENT_UPDATED` | S3-1 | S3-2, S8 |
| `MARKER_CREATED` | S5 | S3-2, S8 |
| `MARKER_UPDATED` | S5 | S3-2, S8 |
| `MARKER_DELETED` | S5 | S3-2 |
| `SUPPORT_REQUEST_CREATED` | S5 | S3-2, S4 EventFanout -> S5 `FcmDispatcher` |
| `PERSON_FOUND` | S5 | S3-2, S4 EventFanout -> S5 `FcmDispatcher` |
| `OFFLINE_PACKAGE_INSTALLATION_CHANGED` | S7 | S3-2 |
| `OP_TRANSITIONED` | S8 | S3-1, S3-2, S7 |
| `OP_ASSIGNMENT_CHANGED` | S8 | S3-2, S7 |
| `HANDOVER_MEMO_CREATED` | S8 | S3-2 |
| `AI_SUMMARY_READY` | S8 | S3-2 |

Event payload는 REST response DTO, S6 `write_operation.schema.json`, S4 outbox/SSE, S3-2 `board.schema.json`, S5 FCM payload가 같은 필드명을 공유한다. Payload root의 공통 비교 필드는 `id`, `status`, `version`이며, 적용 가능한 이벤트는 `opId`, `policePhoneId`, `sequence`도 같은 이름으로 포함한다. SSE envelope는 §9.1 기준 적용. FCM은 동일 payload subset에 §9.1 envelope 중 알림 전달에 필요한 메타만 얹는다.

| event | payload schema | schemaVersion | common payload fields |
|---|---|---:|---|
| `INCIDENT_CREATED` | `events/incident.payload.schema.json` | 1 | `id`, `status`, `version` |
| `INCIDENT_MEMBERSHIP_CHANGED` | `events/incident_membership.payload.schema.json` | 1 | `id`, `status`, `version` |
| `INCIDENT_CLOSED` | `events/incident_terminal.payload.schema.json` | 1 | `id`, `status`, `version` |
| `INCIDENT_PURGED` | `events/incident_purge.payload.schema.json` | 1 | `id`, `status`, `version` |
| `POLICE_PHONE_HEARTBEAT_UPDATED` | `events/police_phone_heartbeat.payload.schema.json` | 1 | `id`, `status`, `version`, `policePhoneId`, `sequence` |
| `SEARCH_AREA_CHANGED` | `events/search_area.payload.schema.json` | 1 | `id`, `incidentId`, `status`, `version`, `geometry`, `serverTs` |
| `SEARCH_AREA_ASSIGNMENT_CHANGED` | `events/search_area.payload.schema.json` | 1 | `id`, `incidentId`, `status`, `version` |
| `SEARCH_SESSION_STARTED` | `events/search_session.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId`, `sequence` |
| `SEARCH_SESSION_ENDED` | `events/search_session.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId`, `sequence` |
| `PATH_APPENDED` | `events/search_path.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId`, `sequence` |
| `PATH_SEGMENT_UPDATED` | `events/path_segment.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId`, `sequence` |
| `MARKER_CREATED` | `events/marker.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `MARKER_UPDATED` | `events/marker.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `MARKER_DELETED` | `events/marker.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `SUPPORT_REQUEST_CREATED` | `events/notification_delivery.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `PERSON_FOUND` | `events/notification_delivery.payload.schema.json` | 1 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `OFFLINE_PACKAGE_INSTALLATION_CHANGED` | `events/offline_package_installation.payload.schema.json` | 1 | `id`, `status`, `version`, `policePhoneId`, `sequence` |
| `OP_TRANSITIONED` | `events/operational_period.payload.schema.json` | 1 | `id`, `status`, `version`, `opId` |
| `OP_ASSIGNMENT_CHANGED` | `events/op_assignment.payload.schema.json` | 1 | `id`, `status`, `version`, `opId` |
| `HANDOVER_MEMO_CREATED` | `events/handover_memo.payload.schema.json` | 1 | `id`, `status`, `version`, `opId` |
| `AI_SUMMARY_READY` | `events/search_history_summary.payload.schema.json` | 1 | `id`, `status`, `version`, `opId` |

### 4.5 Purge Order

1. terminal 상태 전환과 신규 쓰기 차단
2. `missing_person_cache`와 실종자 `photo_object_key` pointer를 active DB/API 응답에서 제거
3. PII 없는 `INCIDENT_CLOSED` 발행
4. 단말에 local purge 대상 표시
5. 단말 Outbox flush/requeue 중지. ACKED 항목만 purge 대상으로 삼고, 미ACK 항목은 closed final 상태 또는 sanitized tombstone으로 전이
6. 경로 좌표와 로컬 사건 패키지 파기
7. S1-3가 `INCIDENT_PURGED` 발행

### 4.6 Channel / Role Matrix

| 기능 | APP | WEB | 필요 역할 |
|---|---|---|---|
| 사건 가져오기 | - | 허용 | 실종팀 지휘, 지구대/파출소 지휘 |
| 사건 종료 | - | 허용 | 실종팀 지휘 |
| 지원 부대 배정 | - | 허용 | 실종팀 지휘 |
| 지도 기준 범위 조정 | - | 허용 | 현장 지휘관 |
| 구역 분할·할당 | - | 허용 | 현장 지휘관 |
| 구역 완료 | - | 허용 | 현장 지휘관 |
| OP 생성 | - | 허용 | 현장 지휘관 |
| 수색 세션 | 허용 | - | 사건 배정 계정 |
| 경로 batch | 허용 | - | 사건 배정 계정 + Device |
| 현장 마커 생성 | 허용 | - | 사건 배정 계정 |
| 마커 수정·삭제 | 허용 | 허용 | 사건 배정 계정, 세부 정책은 S5 |
| 인수인계 메모 | 허용 | 허용 | 사건 배정 계정 |
| AI 요약 생성 | - | 허용 | 현장 지휘관 |
| 상황판 조회 | - | 허용 | 사건 배정 계정 |

---

## 5. Entity Ownership

| entity | owner |
|---|---|
| `incident` | S1-1 |
| `missing_person_cache` | S1-1 |
| `incident_membership` | S1-1 |
| `account` | S1-2 |
| `account_session` | S1-2 |
| `device` | S1-2 |
| `fcm_token` | S1-2 |
| `location_access_log` | S1-3 |
| `purge_run` | S1-3 |
| `map_boundary` | S2 |
| `search_area` | S2 |
| `search_area_history` | S2 |
| `search_session` | S3-1 |
| `search_path` | S3-1 |
| `path_segment` | S3-1 |
| `board_snapshot` | S3-2 |
| `board_event_application` | S3-2 |
| `board_projection_cursor` | S3-2 |
| `event_outbox` | S4 |
| `sse_event_log` | S4 |
| `marker` | S5 |
| `photo` | S5 |
| `notification_delivery` | S5 |
| `sync_event` | S6 |
| `offline_package_manifest` | S7 |
| `offline_package_installation` | S7 |
| `operational_period` | S8 |
| `op_assignment` | S8 |
| `handover_memo` | S8 |
| `search_history_summary` | S8 |

---

## 6. FR Index

| FR | Primary Spec | Notes |
|---|---|---|
| FR-01 사건 관리 | S1-1 | mock·seed import, OP1 bootstrap |
| FR-02 Device 위치·경로 | S3-1 | S1-2 Device 기준 |
| FR-03 오프라인 기록 | S6 | local store + Outbox |
| FR-04 실시간 위치 반영 | S3-1/S4/S3-2 | path event + board |
| FR-05 경로·완료 구역 표시 | S3-2 | S2/S3-1 데이터 소비 |
| FR-06 구역 완료 처리 | S2 | Web 지휘 |
| FR-07 구역 상태 | S2/S3-2 | 자동 판단 없음 |
| FR-08 팀·단말별 수색 현황 | S3-2 | color/style은 overview 구현 요소 |
| FR-09 최신 기록 시각 | S1-2/S3-1/S3-2 | path time, record status, last sync display |
| FR-10 단서 마커 | S5 | 앱 생성 |
| FR-11 OP별 레이어 선택·비교 | S8/S3-2 | board consumes OP path/assignment/marker/memo |
| FR-12 담당 구역 배정 | S2/S8 | op assignment |
| FR-13 구역 상태·수색 횟수 | S2/S8 | search_area_history, search_count |
| FR-14 GPS 기반 마커 위치 기록 | S5 | 앱 마커 입력, 필요 시 수동 조정 |
| FR-15 마커 필드 | S5/S8 | 메모 확장 |
| FR-16 지형 상태 레이어 | S5/S3-2 | marker layer |
| FR-17 지원 요청 | S5 | request marker + notification |
| FR-18 공용 상황판 reference view | S3-2 | team/path/area/marker/missing person integrated view |
| FR-19 오프라인 지도 | S7 | tiles + package |
| FR-20 사진 업로드 | S5 | S3 presigned |
| FR-21 실종자 기본 정보 | S1-1/S7 | missing_person cache + offline package |
| FR-22 개인정보 파기 | S1-1/S1-3 | close/purge |
| FR-23 자동 누락 판단 금지 | S2/S3-2/S8 | OP 경로·완료 구역·재확인 마커·메모로 사람 판단 보조 |
| FR-24 단말 최신성 | S1-2/S3-2 | heartbeat |
| FR-25 운용 중 PolicePhone 궤도 강조 | S3-1/S3-2 | policePhoneId 기준 |
| FR-26 단순 지도 보기 | S3-2 | overlay toggle |
| FR-27 초기 뷰포트 | S3-2 | boundary fallback |
| FR-28 미전송 큐 | S6 | Android dashboard |
| FR-29 로컬 경고 | S6 | S6가 S7 package status를 입력으로 소비해 local warning을 표시한다. S7은 package manifest/status owner이고 local warning monitor owner가 아니다. |
| FR-30 바텀시트 마킹 | S5 | Android |
| FR-31 사건 오프라인 패키지 | S7 | manifest/status, map boundary 기반 tile/revision/stale |
| FR-32 OP 히스토리 레이어 | S8 | OP별 경로·마커·완료 구역·인수인계 메모 |
| FR-33 차량·도보 구간 | S3-1 | path segment |
| FR-34 수색 세션 | S3-1/S8 | current OP |
| FR-35 순찰차 경로 레이어 | S3-1/S3-2 | device type |
| FR-37 인수인계 메모 | S8/S3-2 | OP·구역·경로 context |
| FR-39 AI 수색 이력 요약 | S8/S3-2 | 자동 판단 금지 |

---

## 7. API Index

Consumers는 앱·웹·단말·S3-2 등 제품 흐름에서 직접 호출하는 주체만 적는다. Bootstrap, worker, replay, fanout 같은 server-side 호출자는 `internal caller`에만 적고 public consumer로 취급하지 않는다.

Guard shorthand:

- `public-session`: `@RequireChannel(APP,WEB)` -> `channel_not_allowed`
- `incident-read`: `@RequireIncidentAccess` -> `incident_access_denied`, `team_not_assigned`
- `web-command`: `@RequireChannel(WEB)`, `@RequireRole` -> `channel_not_allowed`, `role_denied`
- `app-police-phone`: `@RequireChannel(APP)`, `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`, `@RequirePolicePhoneAssigned` -> `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`
- `field-or-web-write`: `@RequireChannel(APP,WEB)`, APP 요청의 `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`, `@RequirePolicePhoneAssigned` -> `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`
- `write-common`: `@RequireOpenIncident`, `@IdempotentWrite` -> `incident_bootstrapping`, `incident_closed`, `idempotency_mismatch`, `write_conflict`
- `internal-caller`: `@RequireChannel(INTERNAL)` -> `channel_not_allowed`

| API | Owner | consumers | channel | guards / failure codes | internal caller |
|---|---|---|---|---|---|
| `POST /auth/login` | S1-2 | 앱, 웹 | HTTPS | `public-session` | - |
| `POST /auth/logout` | S1-2 | 앱, 웹 | HTTPS | `public-session` | - |
| `POST /fcm/tokens` | S1-2 | 앱 | HTTPS | `app-police-phone` | - |
| `POST /police-phones/{policePhoneId}/heartbeat` | S1-2 | 앱 | HTTPS | `app-police-phone` | - |
| `POST /incidents/import-from-seed` | S1-1 | 웹 지휘 계정 | HTTPS | `web-command` | `internal-caller`: seed/mock bootstrap |
| `GET /incidents` | S1-1 | 앱, 웹, S3-2 | HTTPS | `public-session` | - |
| `GET /incidents/{incidentId}` | S1-1 | 앱, 웹, S3-2 | HTTPS | `public-session`, `incident-read` | - |
| `POST /incidents/{incidentId}/memberships` | S1-1 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | `internal-caller`: seed/mock bootstrap |
| `POST /incidents/{incidentId}/commanders` | S1-1 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | `internal-caller`: seed/mock bootstrap |
| `POST /incidents/{incidentId}/close` | S1-1 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | `internal-caller`: purge orchestration trigger |
| `POST /incidents/{incidentId}/map-boundary` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `PATCH /incidents/{incidentId}/map-boundary` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `GET /incidents/{incidentId}/map-boundary` | S2 | 앱, 웹, S3-2, S7 | HTTPS | `public-session`, `incident-read` | - |
| `POST /search-areas` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `PATCH /search-areas/{areaId}` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `POST /search-areas/{areaId}/split` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `PATCH /search-areas/{areaId}/state` | S2 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `POST /search-sessions` | S3-1 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | `internal-caller`: outbox replay |
| `PATCH /search-sessions/{sessionId}` | S3-1 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | `internal-caller`: outbox replay |
| `POST /search-paths/batch` | S3-1 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | `internal-caller`: outbox replay |
| `GET /search-paths` | S3-1 | 앱, 웹, S3-2, S8 | HTTPS | `public-session`, `incident-read`, `@RecordLocationAccess` | - |
| `PATCH /path-segments/{segmentId}` | S3-1 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `GET /incidents/{incidentId}/board/snapshot` | S3-2 | 웹 | HTTPS | `public-session`, `incident-read`, `@RecordLocationAccess` | - |
| `GET /events?incidentId={incidentId}` | S4 | 웹, S3-2 | SSE/HTTPS | `public-session`, `incident-read`, `@RequireChannel(WEB)` | `internal-caller`: event fanout replay |
| `POST /markers` | S5 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | `internal-caller`: outbox replay |
| `PATCH /markers/{markerId}` | S5 | 앱, 웹 | HTTPS | `field-or-web-write`, `incident-read`, `write-common`, S5 marker policy | `internal-caller`: outbox replay |
| `DELETE /markers/{markerId}` | S5 | 앱, 웹 | HTTPS | `field-or-web-write`, `incident-read`, `write-common`, S5 marker policy | `internal-caller`: outbox replay |
| `POST /markers/{markerId}/photos/presign` | S5 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common` | - |
| `POST /markers/{markerId}/photos/{photoId}/finalize` | S5 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common` | `internal-caller`: outbox replay |
| `POST /sync/clock` | S6 | 앱 | HTTPS | `app-police-phone` | - |
| `POST /sync/outbox/requeue` | S6 | 앱 local retry scheduler | HTTPS | `app-police-phone` | - |
| `GET /tiles/{style}/{z}/{x}/{y}.pbf` | S7 | 앱, 웹 MapLibre | tile HTTPS | `public-session` | - |
| `GET /tiles/styles/{styleId}.json` | S7 | 앱, 웹 MapLibre | tile HTTPS | `public-session` | - |
| `GET /incidents/{incidentId}/offline-package/manifest` | S7 | 앱, S3-2 | HTTPS | `public-session`, `incident-read`; 앱 package fetch는 `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`, `@RequirePolicePhoneAssigned` -> `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned` | - |
| `POST /incidents/{incidentId}/offline-package/installations` | S7 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common` | `internal-caller`: outbox replay |
| `POST /operational-periods` | S8 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | `internal-caller`: OP bootstrap |
| `GET /incidents/{incidentId}/operational-periods` | S8 | 앱, 웹, S3-2, S7 | HTTPS | `public-session`, `incident-read` | - |
| `POST /duty-shifts` | S8 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | - |
| `PATCH /duty-shifts/{dutyShiftId}` | S8 | 앱 | HTTPS | `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp` | `internal-caller`: search history summary generation job trigger after `action=END` commit |
| `GET /duty-shifts` | S8 | 앱, 웹, S3-2 | HTTPS | `public-session`, `incident-read` | - |
| `POST /operational-periods/{opId}/assignments` | S8 | 웹 | HTTPS | `web-command`, `incident-read`, `write-common` | - |
| `POST /handover-memos` | S8 | 앱, 웹 | HTTPS | `field-or-web-write`, `incident-read`, `write-common` | - |
| `GET /handover-memos` | S8 | 앱, 웹, S3-2 | HTTPS | `public-session`, `incident-read` | - |
| `GET /operational-periods/{operationalPeriodId}/search-history-summaries` | S8 | 앱, 웹, S3-2 | HTTPS | `public-session`, `incident-read` | - |

---

## 8. Annotation Index

| annotation | owner | 실패 코드 | 의미 |
|---|---|---|---|
| `@RequireIncidentAccess` | S1-2 | `incident_access_denied`, `team_not_assigned` | 사건 배정 팀 계정 확인 |
| `@RequireRole` | S1-2 | `role_denied` | 역할 확인 |
| `@RequireChannel` | S1-2 | `channel_not_allowed` | APP/WEB/INTERNAL 허용 채널 확인 |
| `@RequirePolicePhone` | S1-2 | `police_phone_required` | 앱 요청의 PolicePhone 식별자 확인 |
| `@RequirePolicePhoneRegistered` | S1-2 | `police_phone_not_registered` | 등록된 업무폰·순찰차 PolicePhone 확인 |
| `@RequirePolicePhoneAssigned` | S1-2 | `police_phone_not_assigned` | 사건·팀·OP 배정과 PolicePhone 연결 확인 |
| `@RequireOpenIncident` | S1-1 | `incident_bootstrapping`, `incident_closed` | 사건 OPEN 상태 확인 |
| `@RequireCurrentOp` | S8 | `op_required`, `op_mismatch` | current OP 확인 |
| `@IdempotentWrite` | S6 | `idempotency_mismatch`, `write_conflict` | idempotency key 처리 |
| `@PublishEvent` | S4 | - | event outbox publish hook |
| `@RecordLocationAccess` | S1-3 | - | 위치정보 접근기록 |

신규 annotation은 해당 owner와 S4/S6 영향 여부를 확인한 뒤 추가한다. API별 적용 guard는 §7 `guards / failure codes` 열에서 추적한다.

---

## 9. Contract Registry

### 9.1 Event Envelope

```json
{
  "eventId": "uuid",
  "incidentId": "uuid",
  "type": "PATH_APPENDED",
  "schemaVersion": 1,
  "serverTs": "2026-04-27T00:00:00Z",
  "payload": {}
}
```

### 9.2 Board Shell Slots

| slot | feature owner | mounted by | source contract | purpose |
|---|---|---|---|---|
| `overall_search_area` | S2 | S3-2 | `SearchAreaQuery.overallOf` | 지도 기준 범위 표시 |
| `area` | S2 | S3-2 | `AreaQuery.byIncident`, `AreaQuery.byOp` | 구역 폴리곤·상태 표시 |
| `path` | S3-1 | S3-2 | `PathQuery.byIncident`, `PathQuery.byOp` | PolicePhone 경로·구간 표시 |
| `police_phone_freshness` | S1-2 | S3-2 | `PolicePhoneFreshnessQuery.byIncident` | 위치 점 최신성 표시 |
| `marker` | S5 | S3-2 | `MarkerQuery.byIncident` | 마커 레이어 |
| `toast` | S5 | S3-2 | `SUPPORT_REQUEST_CREATED`, `PERSON_FOUND` | 지원 요청·발견 알림 |
| `package_badge` | S7 | S3-2 | `OfflinePackageInstallationQuery.byIncident` | 오프라인 패키지 상태 |
| `op_toggle` | S8 | S3-2 | `OperationalPeriodQuery.list` | OP 레이어 토글 |
| `op_history` | S8 | S3-2 | `OperationalPeriodQuery.list`, `OP_TRANSITIONED`, `OP_ASSIGNMENT_CHANGED` | OP 전환·배정 이력 표시 |
| `handover_memo` | S8 | S3-2 | `HandoverMemoQuery.byContext` | 인수인계 메모 표시 |
| `handover_status` | S8 | S3-2 | `HandoverStatusSnapshot.byIncident`, `HANDOVER_MEMO_CREATED`, `OP_TRANSITIONED` | 인수인계 준비·완료 상태 표시 |
| `search_history_summary` | S8 | S3-2 | `SearchHistorySummaryQuery.byOp` | AI 수색 이력 요약 |
| `incident_terminal` | S1-1 terminal close, S1-3 sanitized tombstone/delete summary | S3-2 | `INCIDENT_CLOSED`, `IncidentTerminalSnapshot.closed`, `IncidentTombstoneSnapshot.byIncident` | 사건 종료·sanitized tombstone read-only summary·사용자 노출 가능한 `localPurgeState` 표시 |

S3-2는 shell routing, page layout, slot mounting, shared state wiring의 owner다. Feature owner는 slot-scoped renderer와 source contract만 납품한다. Slot component는 shell routing, page layout, global store를 직접 수정하지 않는다.

### 9.3 Schema Freeze List

1주차에 아래 schema를 먼저 동결한다.

- `incident.schema.json`
- `incident_assignment.schema.json`
- `account.schema.json`
- `police_phone.schema.json`
- `search_area.schema.json`
- `operational_period.schema.json`
- `search_area_assignment.schema.json`
- `search_path.schema.json`
- `search_path_segment.schema.json`
- `duty_shift.schema.json`
- `marker.schema.json`
- `marker_notification.schema.json`
- `offline_package_manifest.schema.json`
- `offline_package_installation.schema.json`
- `events/_base.schema.json`
- `board.schema.json`

---

## 10. Harness Mapping

| Scenario | involved Specs | API/Event | S3-2 slot | provider -> consumer |
|---|---|---|---|---|
| SC-01 배정 사건 가져오기·초동 활성화 | S1-1, S1-2, S4, S5, S8 | `POST /incidents/import`, `INCIDENT_CREATED`, `INCIDENT_ASSIGNMENT_CHANGED`, `OP_TRANSITIONED(from=null)` | - | S1-1 mock 112 import -> `incident_assignment` 반영 -> S8 OP1 자동 생성 -> S4 `EventFanout`; S1-1 assignment -> S1-2 access guard; S5 `ReferenceMarkerSeed.createForIncident(incidentId, seedMarkers)` |
| SC-02 실종팀 인계·지원 부대 배정 | S1-1, S1-2, S3-1, S3-2, S4, S5, S8 | 112/mock polling import, `GET /search-paths`, `GET /handover-memos`, `INCIDENT_ASSIGNMENT_CHANGED` | `path`, `marker`, `handover_status`, `op_history` | S1-1 `incident_assignment` 반영 -> S4 `EventFanout` -> S3-2 handover/status slots; `INCIDENT_ASSIGNMENT_CHANGED` fanout은 S1-2 `FcmTokenQuery.activeByPolicePhone(policePhoneId)`와 S5 resolver/payload factory/`FcmDispatcher` adapter를 통해 신규 배정 계정이 운용 중인 활성 `policePhoneId`에 PII 없는 배정 FCM을 보낸다 |
| SC-03 사건 오프라인 패키지 사전 적재 | S7, S1-1, S1-2, S2, S5, S8, S4, S3-2 | §7 `GET /incidents/{incidentId}/offline-package/manifest`, `POST /incidents/{incidentId}/offline-package/installations`, §4.4 `OFFLINE_PACKAGE_INSTALLATION_CHANGED` | §9.2 `package_badge` | S2 overall area, S5 `ReferenceMarkerSeed.createForIncident(incidentId, seedMarkers)`, S8 OP/duty shift context -> S7 manifest/installation; S7 `OfflinePackageInstallationQuery`/`OFFLINE_PACKAGE_INSTALLATION_CHANGED` -> S3-2 `package_badge` |
| SC-04 지도 기준 범위·구역 분할·할당 | S2, S8, S1-1, S1-2, S4, S7 | `POST /search-areas`, `PATCH /search-areas/{searchAreaId}`, `POST /search-areas/{searchAreaId}/split`, `POST /search-areas/{searchAreaId}/assignments`, `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED` | `overall_search_area`, `area` | S2 geometry + search area assignment -> S4 `EventFanout` -> S3-2 map/area slots, S7 package builder |
| SC-05 수색 경로·PolicePhone GPS 경로 | S3-1, S1-2, S6, S8, S4, S2 | `POST /search-paths`, `POST /search-paths/batch`, `PATCH /search-path-segments/{searchPathSegmentId}`, `SEARCH_PATH_STARTED`, `PATH_APPENDED`, `SEARCH_PATH_SEGMENT_UPDATED`, `SearchAreaQuery.overallOf(incidentId)` | `path`, `police_phone_freshness` | S3-1 owns `search_path`/`search_path_segment` and applies `spec/boundaries.md §4.1.1 Common Geometry Rule`; S2 provides `SearchAreaQuery.overallOf(incidentId)` as validation input only; path writes through S6 Outbox -> S4 `EventFanout` -> S3-2 path slot, S8 current OP/duty shift context |
| SC-06 현장 마커 생성 | S5, S1-2, S6, S8, S4, S2 | `POST /markers`, `POST /markers/{markerId}/photos/upload-url`, `POST /markers/{markerId}/photos/{photoId}/attach`, `MARKER_CREATED`, `MARKER_UPDATED`, `SearchAreaQuery.overallOf(incidentId)` | `marker` | S5 owns `marker`/`photo` and applies `spec/boundaries.md §4.1.1 Common Geometry Rule` to marker location; S2 provides `SearchAreaQuery.overallOf(incidentId)` as validation input only; marker/photo writes through S6 Outbox -> S4 `EventFanout` -> S3-2 marker slot, S8 OP context |
| SC-07 통신 단절 중 로컬 기록 | S6, S1-2, S3-1, S5, S7 | `POST /sync/outbox/requeue`, local Outbox rows for `POST /search-paths/batch` and `POST /markers`, package availability from S7 manifest | - | S6 local store/Outbox -> S3-1/S5 pending writes after recovery, S7 offline package -> app local renderer |
| SC-08 지원 요청·실종자 발견 알림 | S5, S1-1, S1-2, S4, S6, S8 | `POST /markers`, `SUPPORT_REQUEST_CREATED`, `PERSON_FOUND`, fixture `FcmDispatcher` | `marker`, `toast` | S5 marker/notification payload through S6 Outbox -> S4 `EventFanout` -> S3-2 marker/toast; S1-2 `FcmTokenQuery.activeByPolicePhone(policePhoneId)` -> S5 resolver/`FcmDispatcher` adapter -> app banner |
| SC-09 통신 복구·동기화 | S6, S3-1, S5, S1-2, S3-2, S4, S7 | `POST /sync/outbox/requeue`, `POST /search-paths/batch`, `POST /markers`, `POST /incidents/{incidentId}/offline-package/installations`, `GET /incidents/{incidentId}/events`, `PATH_APPENDED`, `MARKER_CREATED`, `OFFLINE_PACKAGE_INSTALLATION_CHANGED` | `marker`, `path`, `police_phone_freshness`, `package_badge` | S6 Outbox flush -> S3-1/S5/S7 server rows -> S4 replay/dedupe -> S3-2 recovered board state including `package_badge` |
| SC-10 구역 완료·새 OP 열기 | S2, S8, S1-2, S4, S1-1 | `PATCH /search-areas/{searchAreaId}`, `POST /operational-periods`, `POST /handover-memos`, `SEARCH_AREA_CHANGED`, `OP_TRANSITIONED`, `HANDOVER_MEMO_CREATED` | `area`, `op_toggle`, `op_history`, `handover_memo`, `handover_status` | S2 area state + S8 OP/handover writes -> S4 `EventFanout` -> S3-2 area/op_history/handover_status/handover_memo display, S1-1 open-incident guard |
| SC-11 인수인계·OP 비교·수색 이력 요약 | S3-2, S1-2, S8, S3-1, S2, S5, S4, S1-1 | `GET /incidents/{incidentId}/board`, `GET /search-paths`, `GET /handover-memos`, `PATCH /duty-shifts/{dutyShiftId}`, `POST /operational-periods`, `GET /operational-periods/{operationalPeriodId}/search-history-summaries`, `HANDOVER_MEMO_CREATED`, `SEARCH_HISTORY_SUMMARY_CHANGED` | `op_toggle`, `handover_memo`, `search_history_summary` | DutyShift 종료 또는 OP 전환 commit 이후 S8 서버 job이 이전 근무/OP source snapshot으로 summary를 생성하고 S4 `EventFanout` refetch signal을 발행한다. APP duty shift end는 S6 Outbox sequence barrier 뒤에 서버로 전송되어야 하며, 서버는 `sourceHash`/`sourceReadiness`로 늦게 반영된 경로·마커·사진 finalize·메모 누락을 stale/regeneration으로 처리한다. Web/App은 생성된 summary를 read-only로 확인한다; S1-1/S1-2 access guard |
| SC-12 사건 종료·도메인 데이터 파기 | S1-1, S1-2, S1-3, S6, S7, S4, S3-1, S3-2, S5 | §7 `POST /incidents/{incidentId}/close`, `GET /incidents/{incidentId}/events` web unsubscribe/replay stop, §4.4 `INCIDENT_CLOSED`, `INCIDENT_PURGED`, local package purge, app local close cleanup | §9.2 `incident_terminal`, `package_badge` | S1-1 close -> S1-3 purge orchestration -> S6/S7 purge hooks -> sanitized terminal/tombstone status -> S3-2 `incident_terminal`; S4 carries `INCIDENT_CLOSED`/`INCIDENT_PURGED` fanout, while S6/S7 handle local/package removal and app local close cleanup |
