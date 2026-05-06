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
- APP 전용 write는 `@RequireChannel(APP)`, PolicePhone guard, 사건 배정 guard를 통과해야 한다.
- WEB 지휘 write는 `@RequireChannel(WEB)`, role guard, 사건 접근 guard를 통과해야 한다.
- 서버 내부 호출자와 S1-3 purge/audit/query port는 public API가 아니다.

### 2.2 Idempotency

- Domain write, outbox replay 대상 write, 사건 lifecycle write는 `Idempotency-Key` header를 사용한다.
- 같은 key와 같은 body는 cached response를 재사용한다.
- 같은 key와 다른 body는 `idempotency_mismatch`를 반환하고 새 row나 event를 만들지 않는다.
- `GET`, login/logout, heartbeat, token 등록, clock sync는 `Idempotency-Key`를 요구하지 않는다.

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
| `auth/login`, `auth/logout` | S1-2의 login/logout API를 유지한다. `sessions` resource를 새로 만들지 않는다. |
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

#### POST `/api/auth/login`

- Owner: S1-2
- Source spec: `POST /auth/login`
- Consumer: APP, WEB
- Headers: `X-Client-Channel`
- Guard: `public-session`, `@RequireChannel(APP,WEB)`
- Idempotency-Key: no
- Request: `accountCode`, `password`, `channel`, optional `policePhoneCode`
- Response: `200 {sessionId, accessToken, securityContext}`
- Errors: `channel_not_allowed`
- Note: 인증 세션을 발급한다. URL은 `sessions`로 바꾸지 않는다.

#### POST `/api/auth/logout`

- Owner: S1-2
- Source spec: `POST /auth/logout`
- Consumer: APP, WEB
- Headers: `Authorization`, `X-Client-Channel`
- Guard: `@RequireChannel(APP,WEB)`
- Idempotency-Key: no
- Request: optional `sessionId`
- Response: `200 {status}`
- Errors: `channel_not_allowed`

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
- Consumer: WEB command
- Headers: `Authorization`, `Idempotency-Key`, `X-Client-Channel`
- Guard: `@RequireChannel(WEB)`, missing-team commander 또는 경찰 지구대/파출소 field commander
- Idempotency-Key: yes
- Request: `sourceIncidentId`
- Response: `201 {id, incidentId, status, version, assignmentAccountIds}`
- Errors: `channel_not_allowed`, `role_denied`, `idempotency_mismatch`, `write_conflict`
- Note: mock 112 배정 사건을 내부 `incident`, `missing_person`, `incident_assignment`, OP1로 가져온다. 내부 배정 생성 API는 별도로 만들지 않는다.

#### GET `/api/incidents`

- Owner: S1-1
- Source spec: `GET /incidents`
- Consumer: APP, WEB, S3-2
- Headers: `Authorization`, `X-Client-Channel`
- Guard: `@RequireChannel(APP,WEB)`
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
- Idempotency-Key: no
- Response: `200 {id, incidentId, status, version, missingPerson, assignments}`
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
- Request: `incidentId`, `opId`, `clientTs`, optional `clockOffsetMs`
- Response: `201 {id, incidentId, opId, policePhoneId, version, status}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`

#### PATCH `/api/search-paths/{searchPathId}`

- Owner: S3-1
- Source spec: `PATCH /search-paths/{searchPathId}`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `action`, `clientTs`, optional `clockOffsetMs`
- Response: `200 {id, version, status}`
- Errors: `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`

#### POST `/api/search-paths/batch`

- Owner: S3-1
- Source spec: `POST /search-paths/batch`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `app-police-phone`, `incident-read`, `write-common`, `@RequireCurrentOp`
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `pathId`, `points[]`, optional `clockOffsetMs`
- Request limit: `points` min 2, max 120
- Response: `200 {id, dutyShiftId, opId, policePhoneId, acceptedPointCount, excludedPointCount, excludedPoints, geometry, segments, version, status}`
- Errors: `invalid_geometry`, `clock_skew_exceeded`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`
- Note: S6 Outbox `request_path`와 harness가 이 path를 기준으로 replay한다.

#### GET `/api/search-paths`

- Owner: S3-1
- Source spec: `GET /search-paths`
- Consumer: APP, WEB, S3-2, S8
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`, `@RecordLocationAccess`
- Idempotency-Key: no
- Query: `incidentId`, `opId`, `policePhoneId`, `includeGeometry`, `geometryMode`, `sinceVersion`, `limit`, `sort`, `movementType`
- Response: `200 {paths}`
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
- Response: `200 {incidentId, boardResponseVersion, serverTs, activeOpId, selectedOpIds, slots, sourceVersions, geometryHash, sourceHashes, slotSources}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `gone_refetch_required`

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

### 4.6 Marker / Photo

#### POST `/api/markers`

- Owner: S5
- Source spec: `POST /markers`
- Consumer: APP
- Headers: `Authorization`, `Idempotency-Key`, `X-PolicePhone-Id`
- Guard: `@RequireChannel(APP)`, PolicePhone registered/assigned, incident access, current OP, idempotent write
- Idempotency-Key: yes
- Request: `incidentId`, `opId`, `type`, `location`, `clientTs`, optional `supportRequestType`, `memo`, `clockOffsetMs`
- Response: `201 {id, incidentId, opId, policePhoneId, status, version}`
- Errors: `invalid_geometry`, `channel_not_allowed`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`, `op_required`, `op_mismatch`

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
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`, `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `overall_search_area_required`, `tile_unavailable`

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
- Response: `201 {id, incidentId, status, reason, version, sequenceNumber}`
- Errors: `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

#### GET `/api/incidents/{incidentId}/operational-periods`

- Owner: S8
- Source spec: `GET /incidents/{incidentId}/operational-periods`
- Consumer: APP, WEB, S3-2, S7
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Response: `200 {currentOpId, items}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

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
- Query: `incidentId`, optional `opId`, `policePhoneId`, `accountId`, `status`
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
- Consumer: WEB, S3-2
- Headers: `Authorization`
- Guard: `public-session`, `incident-read`
- Idempotency-Key: no
- Query: `incidentId`, `opId`, `memoTargetType`, `memoTargetId`
- Response: `200 {items}`
- Errors: `channel_not_allowed`, `incident_access_denied`, `team_not_assigned`

#### POST `/api/operational-periods/{operationalPeriodId}/search-history-summaries`

- Owner: S8
- Source spec: `POST /operational-periods/{operationalPeriodId}/search-history-summaries`
- Consumer: WEB
- Headers: `Authorization`, `Idempotency-Key`
- Guard: `web-command`, `incident-read`, `write-common`
- Idempotency-Key: yes
- Request: `incidentId`, `clientTs`, optional `scopeType`, `scopeId`, `dutyShiftId`
- Response: `202 {summaryId, status, version}`
- Errors: `summary_unavailable`, `channel_not_allowed`, `role_denied`, `incident_access_denied`, `team_not_assigned`, `incident_closed`, `idempotency_mismatch`, `write_conflict`

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
| `POST /auth/login` | `/api` prefix 없음 | `POST /api/auth/login` |
| `POST /auth/logout` | `/api` prefix 없음 | `POST /api/auth/logout` |
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
| `POST /markers/{markerId}/photos/upload-url` | `/api` prefix 없음 | `POST /api/markers/{markerId}/photos/upload-url` |
| `POST /markers/{markerId}/photos/presign` | S5 기준 용어가 아님 | `POST /api/markers/{markerId}/photos/upload-url` |
| `POST /markers/{markerId}/photos/{photoId}/attach` | `/api` prefix 없음 | `POST /api/markers/{markerId}/photos/{photoId}/attach` |
| `POST /markers/{markerId}/photos/{photoId}/finalize` | S5 기준 용어가 아님 | `POST /api/markers/{markerId}/photos/{photoId}/attach` |
| `POST /sync/clock` | `/api` prefix 없음 | `POST /api/sync/clock` |
| `POST /sync/outbox/requeue` | `/api` prefix 없음 | `POST /api/sync/outbox/requeue` |
| `GET /incidents/{incidentId}/offline-package/manifest` | `/api` prefix 없음 | `GET /api/incidents/{incidentId}/offline-package/manifest` |
| `POST /incidents/{incidentId}/offline-package/installations` | `/api` prefix 없음 | `POST /api/incidents/{incidentId}/offline-package/installations` |
| `POST /operational-periods/{opId}/search-history-summaries` | path variable 축약 | `POST /api/operational-periods/{operationalPeriodId}/search-history-summaries` |

## 7. docs/spec 반영 상태

- `docs/spec/specs/S2.json`: `POST /search-areas/{searchAreaId}/assignments` 상세 contract와 `SEARCH_AREA_ASSIGNMENT_CHANGED` 소유권을 반영했다.
- `docs/spec/specs/S8.json`: `POST/PATCH/GET /duty-shifts` 상세 contract를 반영하고, `search_area_assignment`는 S2 read-only 소비로 정리했다.
- `docs/spec/boundaries.md`, `docs/spec/harness-scenarios.md`: canonical URL과 photo `upload-url`/`attach` 표현을 반영했다.
- `docs/tasks/*.md`: 구현 산출물 endpoint 문자열을 canonical URL로 반영했다.

## 8. 남은 반영 순서

1. backend, frontend, android `AGENTS.md`는 이 문서를 API 기준 계약으로 참조한다.
