# Suri-Map API 명세

이 문서는 `docs/spec`의 API 계약을 public HTTP API 기준으로 정리한 기준 문서다. 구현 상세의 원천은 `docs/spec/specs/*.json`이고, 이 문서는 클라이언트와 백엔드가 합의할 canonical URL, channel/guard, request, response, error를 한곳에 모은다.

## 1. 작성 기준

- 공개 JSON API의 기본 prefix는 `/api`다.
- 별도 URL version prefix는 두지 않는다. breaking change가 실제로 필요해지는 시점에 ADR로 `/api/vN` 도입 여부를 다시 결정한다.
- `docs/spec/boundaries.md`와 `docs/spec/specs/*.json`의 prefix 없는 path는 app-relative path로 보고, 이 문서가 canonical public URL을 제공한다.
- URL segment는 기존 PRD/spec의 도메인 용어를 우선하고 kebab-case를 사용한다. REST 원칙 때문에 기존 문서에 없는 새 resource 이름을 만들지 않는다.
- Path variable은 가능한 한 entity가 드러나게 쓴다. 예: `{areaId}`보다 `{searchAreaId}`, `{opId}`보다 `{operationalPeriodId}`.
- DTO field는 기존 spec/harness fixture와 맞추기 위해 `opId`, `pathId`처럼 이미 굳어진 이름을 유지한다. 이 문서는 URL canonicalization을 우선한다.
- tileserver 경로는 Spring Boot JSON API가 아니므로 `/api` prefix를 붙이지 않는다.

## 2. 공통 HTTP 계약

### 2.1 Channel과 인증

- `X-Client-Channel`은 `APP` 또는 `WEB`이다. spec에서 header가 빠진 endpoint도 channel guard가 있으면 같은 기준으로 검증한다.
- 공개 API 인증은 Keycloak `suri-map` realm의 OIDC access token을 `Authorization: Bearer {jwt}`로 전달하는 것을 기준으로 한다.
- WEB은 Keycloak Authorization Code + PKCE redirect 흐름으로 로그인하고, APP은 MDM/managed config와 내부망 확인 후 Custom Tabs/AppAuth Authorization Code + PKCE 흐름으로 로그인한다.
- access token에는 `accountId`, `accountType`, `organizationType`이 있어야 한다. APP channel의 폴리폰 식별자는 token claim이 아니라 MDM/managed config에서 읽은 `X-PolicePhone-Id` header로 전달한다.
- 표시용 claim은 `accountCode`, `personName`, `displayName`, `organizationCode`, `organizationName`, `rankCode`, `rankName`을 사용한다. `organizationName`은 `광주경찰청 여성청소년과 실종팀`처럼 시연 계정이 실제로 속한 운용 leaf 조직 경로를 담고, `displayName`은 `소속 + 계급 + 이름` 조합의 UI label이다. `displayName`은 권한 판정에는 쓰지 않는다. 권한 판정은 `accountId`, `accountType`, `organizationType`, `X-PolicePhone-Id`, 사건 배정, backend-derived Suri-Map role 기준으로 수행한다. `MISSING_TEAM_COMMANDER`, `FIELD_COMMANDER`, `MEMBER` 같은 role은 Keycloak/기관 SSO role claim이 아니라 Suri-Map backend가 계정 유형·소속·사건 배정으로 파생하는 API 접근 제어용 authority다.
- 계급, 직책, 소속의 source of truth는 Keycloak/기관 SSO claim이다. Suri-Map 운영 DB의 `account` 값은 사건 배정 FK와 조회 성능을 위한 local projection이며, 기관 계급/직책/전역 권한을 결정하지 않는다. `police_phone`은 MDM/단말 관리 원천의 로컬 투영이며 인증 서버 계정 claim이 아니다.
- APP 전용 write는 `@RequireChannel(APP)`, PolicePhone guard, 사건 배정 guard를 통과해야 한다.
- WEB 지휘 write는 `@RequireChannel(WEB)`, role guard, 사건 접근 guard를 통과해야 한다.
- 서버 내부 호출자와 S1-3 purge/audit/query port는 public API가 아니다.

### 2.2 Idempotency

- Domain write, outbox replay 대상 write, 사건 lifecycle write는 `Idempotency-Key` header를 사용한다.
- 같은 key와 같은 body는 cached response를 재사용한다.
- 같은 key와 다른 body는 `idempotency_mismatch`를 반환하고 새 row나 event를 만들지 않는다.
- `GET`, OIDC login/logout redirect, heartbeat, token 등록, clock sync는 `Idempotency-Key`를 요구하지 않는다.

### 2.3 Error response

공개 error response는 최소 형태를 기본으로 한다.

```json
{
  "error": "incident_closed"
}
```

Field validation 상세 노출 여부는 아직 확정하지 않는다. 현재 spec의 public guard/domain error catalog를 우선한다.

### 2.4 Query와 pagination

- 필터, 정렬, pagination, optional scope는 query string으로 둔다.
- 필수 상위 resource scope는 path에 둔다. 기존 `GET /events?incidentId={incidentId}`처럼 필수 사건 scope를 query string에 둔 표현은 canonical 정리 대상으로 본다.

## 3. URL 용어 결정

| URL 용어 | 결정 |
|---|---|
| `auth/login`, `auth/logout` | 자체 session API는 Keycloak/OIDC 전환 이후 canonical public API가 아니다. 로그인/로그아웃은 Keycloak realm endpoint와 client redirect 흐름으로 처리한다. |
| `incidents/import` | mock 112 사건 가져오기 command로 유지한다. `incident-imports` resource를 만들지 않는다. |
| `incidents/{incidentId}/close` | 사건 종료 command로 유지한다. `closure` resource를 만들지 않는다. |
| `search-areas` + `areaLevel=OVERALL` | DB 설계상 `overall_search_area`는 별도 table/resource가 아니라 `search_area.area_level = OVERALL`이다. 전체 수색 구역도 `/api/search-areas`에서 생성·조회·수정한다. |
| `search-areas/{searchAreaId}/split` | 기존 S2 split command를 유지한다. `search-area-splits` resource를 만들지 않는다. |
| `search-areas/{searchAreaId}` status 변경 | 별도 `/state` 또는 `/status` endpoint를 만들지 않고 `PATCH /api/search-areas/{searchAreaId}` body로 처리한다. |
| `search-paths/batch` | REST만 보면 `/api/search-paths/{searchPathId}/points`도 후보지만, S3-1/S6/harness가 `APPEND_PATH_BATCH`와 request path를 기준으로 outbox replay를 맞추고 있어 유지한다. |
| `incidents/{incidentId}/events` | S4 SSE endpoint는 incident 하위 events resource로 둔다. `events/stream`은 만들지 않는다. |
| `photos/upload-url`, `photos/{photoId}/attach` | API 용어는 upload URL/attach로 유지한다. 여기서 `uploadUrl`은 S3-compatible object storage presigned URL for upload다. harness의 `presign`/`finalize` 표현은 endpoint 이름으로 쓰지 않는다. |
| `sync/outbox/requeue` | Android local Outbox 진단·재큐잉 동기화 endpoint로 유지한다. `sync-requeue-requests` resource를 만들지 않는다. |
| `offline-package/manifest`, `offline-package/installations` | S7 offline package 하위 resource로 유지한다. flat `offline-package-manifest` 형태로 바꾸지 않는다. |

이번 검토에서 제거한 새 표현: `sessions`, `incident-imports`, `closure`, `overall-search-area`, `search-area-splits`, `point-batches`, `events/stream`, `photo-upload-urls`, `attachment`, `clock-syncs`, `sync-requeue-requests`, `offline-package-manifest`, `offline-package-installations`.

## 4. API Index

### 4.1 Auth / Account / PolicePhone

#### Keycloak/OIDC login/logout

- Owner: S1-2
- Consumer: APP, WEB
- Login entrypoint: `/keycloak/realms/suri-map/protocol/openid-connect/auth`
- Token endpoint: `/keycloak/realms/suri-map/protocol/openid-connect/token`
- Logout endpoint: `/keycloak/realms/suri-map/protocol/openid-connect/logout`
- Client: `suri-map-web`, `suri-map-android`
- Flow: Authorization Code + PKCE
- API credential: `Authorization: Bearer {Keycloak access token}`
- Required claims: `accountId`, `accountType`, `organizationType`
- Display claims: `accountCode`, `personName`, `displayName`, `organizationCode`, `organizationName`, `rankCode`, `rankName`
- APP PolicePhone binding: MDM/managed config -> `X-PolicePhone-Id`; Keycloak access token does not carry `policePhoneId`/`policePhoneCode`.
- Note: legacy `/api/auth/login` and `/api/auth/logout` are removed from the public API after OIDC cutover.

#### POST `/api/fcm/tokens`

- Owner: S1-2
- Source spec: `POST /fcm/tokens`
- Consumer: APP
- Headers: `Authorization`, `X-Client-Channel`, `X-PolicePhone-Id`
- Guard: `app-police-phone`
- Idempotency-Key: no
- Request: `appInstanceId`, `token`
- Response: `200 {id, status, version}`
- Errors: `police_phone_required`, `channel_not_allowed`, `police_phone_not_registered`, `police_phone_not_assigned`

#### POST `/api/police-phones/{policePhoneId}/heartbeat`

- Owner: S1-2
- Source spec: `POST /police-phones/{policePhoneId}/heartbeat`
- Consumer: APP
- Headers: `Authorization`, `X-Client-Channel`, `X-PolicePhone-Id`
- Guard: `app-police-phone`
- Idempotency-Key: no
- Request: `clientTs`, `sequence`, optional `lastSyncAt`, `batteryPercent`
- Response: `200 {id, status, version, policePhoneId, sequence, lastHeartbeatAt, lastSyncAt}`
- Errors: `police_phone_required`, `channel_not_allowed`, `police_phone_not_registered`, `police_phone_not_assigned`

### 4.2 Incident

#### POST `/api/incidents/import`

- Owner: S1-1
- Source spec: `POST /incidents/import`
- Consumer: WEB command, INTERNAL webhook fallback
- Headers: `Authorization`, `Idempotency-Key`, `X-Client-Channel`
- Guard: `@RequireChannel(WEB)`, missing-team commander 또는 경찰 지구대/파출소 field commander
- Idempotency-Key: yes
- Request: `sourceIncidentId`
- Response: `201 {id, incidentId, status, version, assignmentAccountIds}`
- Errors: `channel_not_allowed`, `role_denied`, `idempotency_mismatch`, `write_conflict`
- Note: mock 112 배정 사건을 내부 `incident`, `missing_person`, `incident_assignment`, OP1로 가져온다. 내부 배정 생성 API는 별도로 만들지 않는다. 운영 기본 흐름은 `POST /api/internal/mock-112/events` webhook 자동 import이며, 이 WEB command는 재처리/운영 보조 경로다.

#### POST `/api/internal/mock-112/events`

- Owner: S1-1
- Source spec: `POST /api/internal/mock-112/events`
- Consumer: mock-112 internal webhook
- Headers: `X-Client-Channel: INTERNAL`, `X-Mock112-Signature`, `Idempotency-Key`
- Guard: internal secret or HMAC signature, webhook idempotency
- Idempotency-Key: yes
- Request: `{eventId, eventType, sourceIncidentId, occurredAt, incident?}` where `eventType` is `INCIDENT_READY` or `INCIDENT_ASSIGNMENT_CHANGED`
- Response: `202 {eventId, eventType, sourceIncidentId, incidentId, status, version}`
- Errors: `channel_not_allowed`, `invalid_signature`, `idempotency_mismatch`, `write_conflict`, `mock112_event_invalid`
- Note: mock-112는 Suri-Map DB에 직접 쓰지 않고 이 internal webhook만 호출한다. `INCIDENT_READY`는 자동 import를 수행하고, `INCIDENT_ASSIGNMENT_CHANGED`는 기존 사건의 `incident_assignment`를 반영한다. 같은 `eventId` 또는 같은 112 assignment key 재전송은 중복 row 없이 같은 결과로 수렴해야 한다.

#### GET `/api/incidents`

- Owner: S1-1
- Source spec: `GET /incidents`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`, `X-Client-Channel`
- Guard: `@RequireChannel(APP,WEB)`. APP와 WEB 일반 계정은 현재 계정의 active `incident_assignment` 범위만 조회한다. WEB `COMMAND` 계정은 지휘 상황판 기본 목록에서 같은 `organizationType`의 active 배정이 있는 OPEN 사건을 조회한다.
- Idempotency-Key: no
- Query: optional `status`
- Response: `200 {items...}` from S1-1 incident list schema
- Errors: `channel_not_allowed`

#### GET `/api/incidents/{incidentId}`

- Owner: S1-1
- Source spec: `GET /incidents/{incidentId}`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`, `X-Client-Channel`
- Guard: `@RequireChannel(APP,WEB)`, `@RequireIncidentAccess`
- Scope: APP와 WEB 일반 계정은 현재 계정의 active `incident_assignment`를 요구한다. WEB `COMMAND` 계정은 같은 `organizationType`의 active 배정이 있는 사건까지 상세 조회할 수 있다.
- Idempotency-Key: no
- Response: `200 {id, incidentId, title, status, openedAt, version, missingPerson, assignments}`
- `missingPerson`: OPEN 사건에서 실종자 기본 정보를 반환한다. CLOSED 사건은 실종자 PII를 제거해 `missingPerson`을 반환하지 않거나 `null`로 둔다.
- `missingPerson.incidentId`: 사건 ID
- `missingPerson.displayName`: 실종자 이름/표시명
- `missingPerson.photoObjectKey`: 실종자 사진 object key. 사진 pointer가 없으면 `null`
- `missingPerson.photoUrl`: 화면 표시용 사진 URL. 사진 pointer가 없으면 `null`
- `missingPerson.appearanceText`: 인상착의/외형 설명
- `missingPerson.lastSeenLocationText`: 마지막 목격 위치
- `missingPerson.lastSeenAt`: 마지막 목격 시각
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

#### POST `/api/incidents/{incidentId}/close`

- Owner: S1-1
- Source spec: `POST /incidents/{incidentId}/close`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`, `X-Client-Channel`
- Guard: `web-command`, `incident-read`, `write-common`, `@RequireOpenIncident`, `@RequireRole(MISSING_TEAM_COMMANDER)`
- Idempotency-Key: yes
- Request: `closeReason`, `confirmPersonalDataRemoval`
- Response: `200 {id, incidentId, status, version, closedAt, terminalSnapshot, writeDisabledReason}`
- Errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

### 4.3 Search Area

#### POST `/api/search-areas`

- Owner: S2
- Source spec: `POST /search-areas`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request for overall area: `incidentId`, `areaLevel=OVERALL`, `geometry`, `clientTs`
- Request for normal area: `incidentId`, `opId`, `areaLevel=UNIT|TEAM`, `geometry`, optional `memo`, `clientTs`
- Response: `201 {id, incidentId, opId?, status, historyCount?, version, geometry}`
- Errors: `invalid_geometry`, `overall_search_area_required`, `area_state_conflict`, `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`
- Note: DB 기준으로 전체 수색 구역은 `search_area.area_level = OVERALL`이다. 별도 `/overall` resource를 만들지 않는다.

#### GET `/api/search-areas`

- Owner: S2
- Source spec: `GET /search-areas`, `SearchAreaQuery.overallOf(incidentId)`
- Consumer: APP, WEB, S3-2, S7
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, optional `opId`, `areaLevel`, `status`
- Response for active overall query: `200 {id, incidentId, geometry, status, version}`
- Response for not found overall query: `409 {error: "overall_search_area_required"}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `overall_search_area_required`
- Canonical active overall query: `GET /api/search-areas?incidentId={incidentId}&areaLevel=OVERALL&status=ACTIVE`

#### PATCH `/api/search-areas/{searchAreaId}`

- Owner: S2
- Source spec: `PATCH /search-areas/{searchAreaId}`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request for geometry/memo update: `opId?`, `geometry`, optional `memo`, `expectedVersion`, `clientTs`
- Request for status update: `opId`, `nextStatus`, optional `memo`, `clientTs`
- Response: `200 {id, incidentId?, opId?, status, historyCount?, version, geometry?}`
- Errors: `invalid_geometry`, `overall_search_area_required`, `area_state_conflict`, `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`
- Note: status 변경도 `search_area.status`와 `search_area_history` 변경으로 처리한다. `/state`와 `/status` endpoint는 canonical에서 제외한다.

#### POST `/api/search-areas/{searchAreaId}/split`

- Owner: S2
- Source spec: `POST /search-areas/{searchAreaId}/split`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `opId`, `children`, `expectedVersion`, `clientTs`, optional `memo`
- Response: `200 {parentAreaId, parent, createdAreaIds, children}`
- Errors: `invalid_geometry`, `overall_search_area_required`, `area_state_conflict`, `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### POST `/api/search-areas/{searchAreaId}/assignments`

- Owner: S2
- Source spec: `POST /search-areas/{searchAreaId}/assignments`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `opId`, assignee account list, `clientTs`
- Response: `201 {assignmentIds, searchAreaId, opId, version}`
- Errors: use S2 write-common errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

### 4.4 Search Path

#### POST `/api/search-paths`

- Owner: S3-1
- Source spec: `POST /search-paths`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `clientTs`, optional `searchPathId`, `clockOffsetMs`
- Response: `201 {id, incidentId, opId, policePhoneId, version, status}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: 전체 수색구역은 초동 경로 시작의 선행조건이 아니다. 전체 수색구역이 있으면 구역 밖 좌표는 정책에 따라 검토/제외 대상으로 다룰 수 있지만, 수색구역 미지정 상태의 APP 초동 기록을 막지 않는다.

#### PATCH `/api/search-paths/{searchPathId}`

- Owner: S3-1
- Source spec: `PATCH /search-paths/{searchPathId}`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `action` (`PAUSE`, `RESUME`, `END`), `clientTs`, optional `clockOffsetMs`
- Response: `200 {id, version, status}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: `PAUSE`는 `RECORDING -> PAUSED`, `RESUME`은 `PAUSED -> RECORDING`, `END`는 `RECORDING|PAUSED -> ENDED` 전이만 허용한다. 모든 전이는 `search_path_lifecycle_event`에 `STARTED`/`PAUSED`/`RESUMED`/`ENDED` 감사 이력으로 남긴다.

#### POST `/api/search-paths/batch`

- Owner: S3-1
- Source spec: `POST /search-paths/batch`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `pathId`, `points[]`, optional `clockOffsetMs`
- Request limit: `points` min 2, max 120
- Response: `200 {id, dutyShiftId, opId, policePhoneId, acceptedPointCount, excludedPointCount, excludedPoints[{pointId, reason, clientTs}], geometry, segments, version, status}`
- `excludedPoints.reason`: `low_accuracy`, `clock_skew`, `invalid_speed`, `distance_jump`
- Errors: `invalid_geometry`, `clock_skew_exceeded`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: S6 Outbox `request_path`와 harness가 이 path를 기준으로 replay한다. 전체 수색구역이 없으면 하네스/운영 허용 범위 내 좌표를 초동 경로로 수신한다.

#### POST `/api/search-area-boundary-alerts`

- Owner: S3-1 with S2 search_area assignment read and S4 FCM fanout
- Source spec: `POST /search-area-boundary-alerts`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `searchAreaId`, `alertType` (`OUTSIDE_ASSIGNED_AREA`, `REENTERED_ASSIGNED_AREA`), `location {type:"Point", coordinates:[lon,lat]}`, `clientTs`, optional `pathId`, `clockOffsetMs`
- Response: `201 {id, incidentId, opId, searchAreaId, policePhoneId, alertType, version, status}`
- Errors: `invalid_geometry`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- FCM: successful `OUTSIDE_ASSIGNED_AREA` write emits advisory data payload `type=SEARCH_AREA_BOUNDARY_EXITED`, `incidentId`, `opId`, `searchAreaId`, `policePhoneId`, `status`, `version`, `eventId`. Payload must not include missing-person PII.
- Note: 이 API는 Android 로컬 경계 확인 안내를 서버 운영 참고/FCM 흐름에 반영하는 경로다. 앱의 즉시 진동/안내는 서버 응답을 기다리지 않는다. 전체 수색구역 밖 좌표를 `invalid_geometry`로 거부하는 정책과 assigned TEAM search_area 경계 확인 안내는 별도 정책이며, 이를 자동 위반 판단이나 다음 수색 구역 추천으로 사용하지 않는다.

#### GET `/api/search-paths`

- Owner: S3-1
- Source spec: `GET /search-paths`
- Consumer: APP, WEB, S3-2, S8
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`, `@RecordLocationAccess`
- Idempotency-Key: no
- Query: `incidentId`, `opId`, `policePhoneId`, `includeGeometry`, `geometryMode`, `sinceVersion`, `limit`, `sort`, `movementType`
- Response: `200 {paths[{id, incidentId, opId, dutyShiftId, policePhoneId, status, startedAt, endedAt, version, geometry, segments, excludedPoints}]}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

#### PATCH `/api/search-path-segments/{searchPathSegmentId}`

- Owner: S3-1
- Source spec: `PATCH /search-path-segments/{searchPathSegmentId}`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `movementType`, optional `reason`
- Response: `200 {id, movementType, movementTypeSource, opId, policePhoneId, correctedByAccountId, correctedAt, version}`
- Errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

### 4.5 Situation Board / Event

#### GET `/api/incidents/{incidentId}/board`

- Owner: S3-2
- Source spec: `GET /incidents/{incidentId}/board`
- Consumer: WEB
- Headers: `Authorization`
- Guard: `@RequireChannel(WEB)`, `@RequireIncidentAccess`, `@RecordLocationAccess`
- Idempotency-Key: no
- Query: `opIds`, `includeSlots`, `sinceVersion`
- `sinceVersion`: optional previous `boardResponseVersion` reload watermark. The response remains a full snapshot for the requested `opIds`/`includeSlots`; S3-2 must not use this aggregate value as a source-owner delta or `minVersion` filter.
- Response: `200 {incidentId, boardResponseVersion, serverTs, activeOpId, selectedOpIds, slots, sourceVersions, geometryHash, sourceHashes, slotSources}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`
- Note: `gone_refetch_required` is emitted by the event stream replay path; clients recover by reloading this full board snapshot and replacing the requested slot state.

#### GET `/api/incidents/{incidentId}/events`

- Owner: S4
- Source spec: `GET /incidents/{incidentId}/events`
- Consumer: WEB, S3-2
- Headers: `Authorization`, optional `Last-Event-ID`
- Guard: `public-session`, `incident-read`, `@RequireChannel(WEB)`
- Idempotency-Key: no
- Request: path `incidentId`, optional `lastEventId` or `Last-Event-ID`
- Response: `200 text/event-stream`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `gone_refetch_required`
- Note: Android product client는 EventSource를 만들지 않고 FCM data message와 REST/Outbox 복구 경로를 사용한다.

#### GET `/api/incidents/events`

- Owner: S4
- Source spec: `GET /incidents/events`
- Consumer: WEB incident list / command dashboard
- Headers: `Authorization`, optional `Last-Event-ID`
- Guard: `public-session`, account assignment scope, `@RequireChannel(WEB)`
- Idempotency-Key: no
- Request: optional `lastEventId` or `Last-Event-ID`
- Response: `200 text/event-stream`
- Events: `INCIDENT_CREATED`, `INCIDENT_ASSIGNMENT_CHANGED`, `INCIDENT_CLOSED`
- Errors: `channel_not_allowed`, `gone_refetch_required`
- Note: incident 목록 단위 refetch signal이다. payload는 PII를 싣지 않고 `incidentId`, `eventId`, `type`, `version`, optional `assignmentAccountIds`처럼 REST refetch에 필요한 최소 필드만 포함한다. 상세 화면/상황판은 기존 `GET /api/incidents/{incidentId}/events`를 계속 사용한다.

### 4.6 Marker / Photo

#### GET `/api/markers`

- Owner: S5
- Source spec: `GET /markers`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`, `@RecordLocationAccess`
- Idempotency-Key: no
- Request: query `incidentId`, optional `opId`, `type`, `status`
- Response: `200 {incidentId, markers:[{id, incidentId, opId, accountId, policePhoneId, type, supportRequestType, source, status, version, location, memo, occurredAt, photoSummary:[{photoId, status, version, contentType, sizeBytes, attachedAt}]}]}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`
- Note: 기본 조회는 `ACTIVE`, `UPDATED` marker만 반환한다. `photoSummary`는 `ATTACHED` 사진만 `attachedAt` 오름차순으로 포함한다.

#### POST `/api/markers`

- Owner: S5
- Source spec: `POST /markers`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `@RequireChannel(APP)`, PolicePhone registered/assigned, incident access, current OP, idempotent write
- Idempotency-Key: yes
- Request: optional `id`, `incidentId`, `opId`, `type`, `location`, `clientTs`, optional `supportRequestType`, `memo`, `clockOffsetMs`, `photos:[{photoId, sizeBytes, contentType, optional width, height, checksumSha256}]`
- Response: `201 {id, incidentId, opId, policePhoneId, status, version, photos:[{photoId, status, version, markerId, markerVersion}]}`
- Errors: `invalid_geometry`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: `photos`가 있으면 `id`는 클라이언트가 미리 생성한 markerId여야 한다. 앱은 먼저 `POST /api/markers/photos/upload-url`로 object storage 업로드를 끝낸 뒤 같은 markerId와 photoId를 `POST /api/markers`에 포함해 marker create와 photo attach를 한 write로 확정한다. 전체 수색구역은 현장 마커 생성의 선행조건이 아니며, 전체 수색구역이 있으면 좌표 포함 여부를 추가 검증한다.

#### PATCH `/api/markers/{markerId}`

- Owner: S5
- Source spec: `PATCH /markers/{markerId}`
- Consumer: APP, WEB according to S5 marker policy
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `field-or-web-write`, `incident-read`, `write-common`, S5 marker policy
- Idempotency-Key: yes
- Request: `version`, optional `location`, `memo`, `type`
- Response: `200 {id, status, version}`
- Errors: `invalid_geometry`, `role_denied`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### DELETE `/api/markers/{markerId}`

- Owner: S5
- Source spec: `DELETE /markers/{markerId}`
- Consumer: APP, WEB according to S5 marker policy
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `field-or-web-write`, `incident-read`, `write-common`, S5 marker policy
- Idempotency-Key: yes
- Request: `version`, optional `reason`
- Response: `200 {id, status, version}`
- Errors: `role_denied`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### POST `/api/markers/{markerId}/photos/upload-url`

- Owner: S5
- Source spec: `POST /markers/{markerId}/photos/upload-url`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `contentType`, `sizeBytes`, optional `checksumSha256`
- Response: `201 {photoId, uploadUrl, expiresAt, maxSizeBytes, version}`
- Errors: `photo_limit_exceeded`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`
- Note: response의 `uploadUrl`은 S3/MinIO-compatible presigned URL for upload다. API endpoint 이름은 storage 구현 용어인 `presign`이 아니라 클라이언트 동작인 `upload-url`로 둔다.

#### POST `/api/markers/photos/upload-url`

- Owner: S5
- Source spec: `POST /markers/photos/upload-url`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `@RequireChannel(APP)`, PolicePhone registered/assigned, incident access, current OP, idempotent write
- Idempotency-Key: yes
- Request: `markerId`, `incidentId`, `opId`, `contentType`, `sizeBytes`, optional `checksumSha256`
- Response: `201 {photoId, uploadUrl, expiresAt, maxSizeBytes, version}`
- Errors: `photo_limit_exceeded`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: 마커 생성 화면에서 사진을 먼저 업로드하기 위한 staged upload-url이다. 응답 photo row는 `PENDING_UPLOAD`이고, `POST /api/markers`의 `photos` 배열에 같은 `photoId`를 포함해야 `ATTACHED`로 확정된다.

#### POST `/api/markers/{markerId}/photos/{photoId}/attach`

- Owner: S5
- Source spec: `POST /markers/{markerId}/photos/{photoId}/attach`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `sizeBytes`, `contentType`, optional `width`, `height`, `checksumSha256`
- Response: `200 {photoId, status, version, markerId, markerVersion}`
- Errors: `photo_limit_exceeded`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`
- Note: object storage 업로드 완료 후 photo row를 marker에 연결·확정하는 단계다. API endpoint 이름은 `finalize`가 아니라 `attach`로 둔다.

### 4.7 Sync / Offline

#### POST `/api/sync/clock`

- Owner: S6
- Source spec: `POST /sync/clock`
- Consumer: APP
- Headers: `Authorization`, `X-PolicePhone-Id`
- Guard: `@RequireChannel(APP)`, PolicePhone registered/assigned for `incidentId`
- Idempotency-Key: no
- Request: `incidentId`, `clientTs`
- Response: `200 {clientTs, serverTs, clockOffsetMs, clockSyncedAt, maxAllowedSkewMs}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `clock_skew_exceeded`

#### POST `/api/sync/outbox/requeue`

- Owner: S6
- Source spec: `POST /sync/outbox/requeue`
- Consumer: APP
- Headers: `Authorization`, `X-PolicePhone-Id`
- Guard: `@RequireChannel(APP)`, PolicePhone registered/assigned for `incidentId`
- Idempotency-Key: no. S6 local operation uses `operationId` instead.
- Request: `operationId`, `incidentId`, `reason`, `clientTs`, `clockOffsetMs`, `clockSyncedAt`, optional `attemptCount`
- Response: `202 {operationId, accepted, serverTs}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_closed`

#### GET `/api/incidents/{incidentId}/offline-package/manifest`

- Owner: S7
- Source spec: `GET /incidents/{incidentId}/offline-package/manifest`
- Consumer: APP, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`; APP package fetch additionally requires PolicePhone registered/assigned
- Idempotency-Key: no
- Query: optional `policePhoneId`, `knownManifestRevision`
- Response: `200 {manifestId, incidentId, manifestVersion, expiresAt, packageHash, incident, missingPerson, operationalPeriods, assignedAreas, initialMarkers, overallSearchArea, tileItems, packageItems}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `package_manifest_not_ready`, `overall_search_area_required`, `tile_unavailable`
- Note: 오프라인 패키지는 전체 수색구역과 타일 기준 범위가 준비된 뒤 생성된다. 수색구역 미지정 상태의 manifest 요청은 `409 {error: "package_manifest_not_ready"}` 또는 기존 호환 오류 `overall_search_area_required`로 응답할 수 있으며, APP은 이를 사건 진입 실패가 아닌 패키지 대기 상태로 표시한다.

#### POST `/api/incidents/{incidentId}/offline-package/installations`

- Owner: S7
- Source spec: `POST /incidents/{incidentId}/offline-package/installations`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `policePhoneId`, `manifestId`, `manifestVersion`, `status`, `totalItems`, `completedItems`, `failedItems`, `version`, `clientTs`, optional `readyForOfflineUse`, `failedItemKeys`, `lastError`, `sequence`, `clockOffsetMs`
- Response: `200 {id, status, version, manifestVersion, readyForOfflineUse, serverTs}`
- Errors: `tile_unavailable`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

### 4.8 Operational Period / Duty Shift / Handover

#### POST `/api/operational-periods`

- Owner: S8
- Source spec: `POST /operational-periods`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `incidentId`, `reason`, `clientTs`, optional `reasonMemo`, `handoverMemo`
- Response: `201 {id, incidentId, status, reason, version, sequenceNumber, openedAt, endedAt}`
- Errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### GET `/api/incidents/{incidentId}/operational-periods`

- Owner: S8
- Source spec: `GET /incidents/{incidentId}/operational-periods`
- Consumer: APP, WEB, S3-2, S7
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Response: `200 {currentOpId, items[{id, status, reason, sequenceNumber, openedAt, endedAt, version}]}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

#### POST `/api/operational-periods/comparisons`

- Owner: S8
- Source spec: `POST /operational-periods/comparisons`
- Consumer: WEB, S3-2
- Headers: `Authorization`, `Idempotency-Key`, `X-Client-Channel: WEB`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `incidentId`, `operationalPeriodIds[]` (minimum 2 OP IDs in the same incident)
- Response: `202 {comparisonId, incidentId, operationalPeriodIds, sourceHash, status, narrativeStatus, metrics, diffFacts, regionFacts, observations, failureReason, requestedAt, generatedAt, version}`.
  - `metrics`, `diffFacts`, `regionFacts` are deterministic server facts from already committed OP/path/marker/memo rows.
  - `observations` is present only when the configured narrative provider returns validated evidence-grounded observations in `{observations:[{sentence, factIds[]}]}` shape.
  - `observations[].factIds[]` must reference `diffFacts[].factId` or `regionFacts[].factId`; provider output must not reconstruct `source`, `key`, `value`, or `operationalPeriodId`.
  - `narrativeStatus=SKIPPED` means deterministic thresholds found no material fact requiring narrative generation.
  - `failureReason` values include `provider_failure`, `empty_output`, `schema_invalid`, `forbidden_phrase`, `unsupported_fact_id`, `validation_rejected`.
- Event: `OP_COMPARISON_ANALYSIS_CHANGED {id, comparisonId, incidentId, operationalPeriodIds, status, narrativeStatus, sourceHash, version}`
- Errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `invalid_operational_period_comparison`
- Channel rule: WEB command only. This endpoint writes only `op_comparison_analysis` and the analysis event. It must not mutate `overall_search_area`, `search_area`, `search_path`, `marker`, `handover_memo`, or `operational_period` source rows, and it must not generate recommendations, missing-area conclusions, or risk judgments.

#### POST `/api/duty-shifts`

- Owner: S8
- Source spec: `boundaries.md` API Index, DB `duty_shift`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, current OP
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `policePhoneId`, `clientTs`
- Response: `201 {id, incidentId, opId, policePhoneId, status, version}`
- Errors: S8/S1-2 write-common errors
- Gap: `docs/spec/specs/S8.json`에는 상세 `api_contracts`가 아직 없다. `boundaries.md`와 DB 설계 기준으로 유지한다.

#### PATCH `/api/duty-shifts/{dutyShiftId}`

- Owner: S8
- Source spec: `boundaries.md` API Index, DB `duty_shift`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, current OP
- Idempotency-Key: yes
- Request: `action`, `clientTs`, optional `memo`
- Response: `200 {id, status, version, endedAt}`
- Errors: S8/S1-2 write-common errors
- Gap: `docs/spec/specs/S8.json`에는 상세 `api_contracts`가 아직 없다.

#### GET `/api/duty-shifts`

- Owner: S8
- Source spec: `boundaries.md` API Index, DB `duty_shift`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, optional `opId`, `policePhoneId`, `accountId(UUID)`, `status`
- Response: `200 {items}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`
- Gap: `docs/spec/specs/S8.json`에는 상세 `api_contracts`가 아직 없다.

#### POST `/api/handover-memos`

- Owner: S8
- Source spec: `POST /handover-memos`
- Consumer: APP, WEB according to field-or-web-write policy
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `field-or-web-write`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `memoTargetType`, `content`, `clientTs`, optional `memoTargetId`
- Response: `201 {id, opId, version, memoTargetType, memoTargetId}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### GET `/api/handover-memos`

- Owner: S8
- Source spec: `GET /handover-memos`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, `opId`, `memoTargetType`, `memoTargetId`
- Response: `200 {items}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

#### Search history summary generation

- Owner: S8
- Public client endpoint: none
- Trigger: server-side after successful handover boundary writes:
  - `PATCH /api/duty-shifts/{dutyShiftId}` with `action=END`
  - `POST /api/operational-periods` when the previous OP is ended and the next OP is opened
- Worker: S8 summary generation job builds a minimized source snapshot from OP, duty shift, path, marker, area, and handover memo records, calls the configured provider, then stores `GENERATING` -> `READY` or `FAILED`.
- Retry: server-managed job retry/requeue only. APP and WEB do not call summary generation or retry APIs.
- Event: `SEARCH_HISTORY_SUMMARY_CHANGED` after `READY`/`FAILED` state is stored or `sourceReadiness=STALE` is detected.
- Source readiness:
  - APP duty shift end is a handover boundary write. Android/S6 replay must not send it before lower-sequence path, marker, photo finalize, and handover memo writes for the same `incidentId`/`policePhoneId`/`opId` are `ACKED` or `FAILED_FINAL`/`PURGED`.
  - The server computes a summary `sourceHash` from committed OP, duty shift, path, marker, area, and handover memo source rows. While the handover boundary is not ready, the public read response remains `GENERATING` with `sourceReadiness=PENDING_SYNC`.
  - If a late committed source row changes `sourceHash` after a summary is `READY` or `FAILED`, the existing summary is treated as stale and server-side regeneration is enqueued. Clients still do not call a retry endpoint.

#### GET `/api/operational-periods/{operationalPeriodId}/handover-timeline`

- Owner: S8
- Source spec: `GET /operational-periods/{operationalPeriodId}/handover-timeline`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, optional `scopeType` (`DUTY_SHIFT`, `OP`, `RANGE`), optional `dutyShiftId`, optional `startAt`, `endAt`, optional `includeOtherActors`
- Response: `200 {incidentId, operationalPeriodId, scope, actors, paths, events, metrics, summary}`.
  - `actors[]` exposes only replay-local `actorId`, `displayName`, and `colorKey`; it must not expose `accountId` or `policePhoneId`.
  - `paths[]` includes `pathId`, replay `actorId`, movement `mode`, `startedAt`, `endedAt`, and raw points `{at, lat, lng, accuracyMeters}` for interpolation.
  - `events[]` merges path start/segment/end, marker, and handover memo records in `occurredAt` order.
  - `metrics` includes deterministic `distanceMeters`, `walkingDistanceMeters`, `drivingDistanceMeters`, `averageSpeedKmh`, `stoppedSegmentCount`, `markerCount`, `handoverMemoCount`, `syncStatus`.
  - `summary` is the safe `search-history-summaries` item for the selected OP or duty shift when available.
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `write_conflict`
- Channel rule: APP and WEB are read-only. This endpoint does not trigger AI generation or retry and does not expose source prompts, provider secrets, account IDs, or police phone IDs.

#### GET `/api/operational-periods/{operationalPeriodId}/search-history-summaries`

- Owner: S8
- Source spec: `GET /operational-periods/{operationalPeriodId}/search-history-summaries`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, optional `scopeType`, `scopeId`, `dutyShiftId`, `status`
- Response: `200 {items}`. `READY` items may include safe `content`; `GENERATING`/`FAILED` items expose status/displayStatus without source prompt, provider secret, recommendation, missing-area conclusion, or risk wording. Each item includes `sourceReadiness` (`PENDING_SYNC`, `READY`, `STALE`) and `sourceHash`.
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`
- Channel rule: APP and WEB are read-only for this resource. Summary generation/retry is server-side and is triggered by duty shift end or OP transition.

### 4.9 Tiles

Tileserver는 Spring Boot JSON API가 아니므로 `/api` prefix를 붙이지 않는다.

#### GET `/tiles/styles/{styleId}.json`

- Owner: S7
- Source spec: `GET /tiles/styles/{styleId}.json`
- Consumer: APP, WEB MapLibre
- Headers: `Authorization`
- Guard: `public-session` over tile HTTPS
- Response: `200 {version, sources, layers, metadata}`
- Errors: `channel_not_allowed`, `tile_unavailable`

#### GET `/tiles/{style}/{z}/{x}/{y}.pbf`

- Owner: S7
- Source spec: `GET /tiles/{style}/{z}/{x}/{y}.pbf`
- Consumer: APP, WEB MapLibre
- Headers: `Authorization`
- Guard: `public-session` over tile HTTPS
- Response: `200 application/x-protobuf`
- Errors: `channel_not_allowed`, `tile_unavailable`

#### GET `/tiles/fonts/{fontStack}/{range}.pbf`

- Owner: S7
- Source spec: `GET /tiles/fonts/{fontStack}/{range}.pbf`
- Consumer: APP, WEB MapLibre
- Headers: `Authorization`
- Guard: `public-session` over tile HTTPS
- Response: `200 application/x-protobuf`
- Errors: `channel_not_allowed`, `tile_unavailable`

## 5. Public API 제외 항목

`docs/spec/specs/S1-3.json`의 아래 계약은 public HTTP API가 아니다.

- `PurgeCoordinator.closeIncident(incidentId)`
- `PurgeCoordinator.purgeIncident(incidentId)`
- `LocationAccessRecorder.record(accountId, incidentId, access_purpose, serverTs)`
- `IncidentTombstoneSnapshot.byIncident(incidentId)`
- `RetentionPurgeStatus.byIncident(incidentId)`

이 계약은 backend internal port 또는 server-side API assembly 전용으로 유지한다.

## 6. 기존 URL 정리 필요 목록

| 현재 표현 | 문제 | Canonical URL |
|---|---|---|
| `POST /fcm/tokens` | `/api` prefix 없음 | `POST /api/fcm/tokens` |
| `POST /police-phones/{policePhoneId}/heartbeat` | `/api` prefix 없음 | `POST /api/police-phones/{policePhoneId}/heartbeat` |
| `POST /incidents/import` | `/api` prefix 없음 | `POST /api/incidents/import` |
| `POST /incidents/{incidentId}/close` | `/api` prefix 없음 | `POST /api/incidents/{incidentId}/close` |
| `POST /incidents/{incidentId}/search-areas/overall` | `overall_search_area`를 별도 resource처럼 보이게 함 | `POST /api/search-areas` body `areaLevel=OVERALL` |
| `PATCH /incidents/{incidentId}/search-areas/overall` | `overall_search_area`를 별도 resource처럼 보이게 함 | `PATCH /api/search-areas/{searchAreaId}` |
| `GET /incidents/{incidentId}/search-areas/overall` | `overall_search_area`를 별도 resource처럼 보이게 함 | `GET /api/search-areas?incidentId={incidentId}&areaLevel=OVERALL&status=ACTIVE` |
| `PATCH /search-areas/{areaId}` | path variable 축약 | `PATCH /api/search-areas/{searchAreaId}` |
| `POST /search-areas/{areaId}/split` | path variable 축약 | `POST /api/search-areas/{searchAreaId}/split` |
| `PATCH /search-areas/{areaId}/state` | `state`/`status` 표현 혼재와 상태 전용 endpoint | `PATCH /api/search-areas/{searchAreaId}` |
| `PATCH /search-areas/{areaId}/status` | 상태 전용 endpoint | `PATCH /api/search-areas/{searchAreaId}` |
| `PATCH /search-paths/{pathId}` | path variable 축약 | `PATCH /api/search-paths/{searchPathId}` |
| `PATCH /search-path-segments/{segmentId}` | path variable 축약 | `PATCH /api/search-path-segments/{searchPathSegmentId}` |
| `POST /search-paths/batch` | `/api` prefix 없음 | `POST /api/search-paths/batch` |
| `GET /events?incidentId={incidentId}` | 필수 사건 scope가 query string에 있음 | `GET /api/incidents/{incidentId}/events` |
| `GET /markers` | `/api` prefix 없음 | `GET /api/markers` |
| `POST /markers/{markerId}/photos/upload-url` | `/api` prefix 없음 | `POST /api/markers/{markerId}/photos/upload-url` |
| `POST /markers/photos/upload-url` | `/api` prefix 없음 | `POST /api/markers/photos/upload-url` |
| `POST /markers/{markerId}/photos/presign` | S5 기준 용어가 아님 | `POST /api/markers/{markerId}/photos/upload-url` |
| `POST /markers/{markerId}/photos/{photoId}/attach` | `/api` prefix 없음 | `POST /api/markers/{markerId}/photos/{photoId}/attach` |
| `POST /markers/{markerId}/photos/{photoId}/finalize` | S5 기준 용어가 아님 | `POST /api/markers/{markerId}/photos/{photoId}/attach` |
| `POST /sync/clock` | `/api` prefix 없음 | `POST /api/sync/clock` |
| `POST /sync/outbox/requeue` | `/api` prefix 없음 | `POST /api/sync/outbox/requeue` |
| `GET /incidents/{incidentId}/offline-package/manifest` | `/api` prefix 없음 | `GET /api/incidents/{incidentId}/offline-package/manifest` |
| `POST /incidents/{incidentId}/offline-package/installations` | `/api` prefix 없음 | `POST /api/incidents/{incidentId}/offline-package/installations` |
| `GET /operational-periods/{opId}/search-history-summaries` | path variable 축약 | `GET /api/operational-periods/{operationalPeriodId}/search-history-summaries` |

## 7. docs/spec 반영 상태

- `docs/spec/specs/S2.json`: `POST /search-areas/{searchAreaId}/assignments` 상세 contract와 `SEARCH_AREA_ASSIGNMENT_CHANGED` 소유권을 반영했다.
- `docs/spec/specs/S8.json`: `POST/PATCH/GET /duty-shifts` 상세 contract와 `search_history_summary` APP/WEB read 계약을 반영하고, `search_area_assignment`는 S2 read-only 소비로 정리했다.
- `docs/spec/boundaries.md`, `docs/spec/harness-scenarios.md`: canonical URL과 photo `upload-url`/`attach` 표현을 반영했다.
- `docs/tasks/*.md`: 구현 산출물 endpoint 문자열을 canonical URL로 반영했다.

## 8. 남은 반영 순서

1. backend, frontend, android `AGENTS.md`는 이 문서를 API 기준 계약으로 참조한다.
