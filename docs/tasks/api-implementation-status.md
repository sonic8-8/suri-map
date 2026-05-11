# API 구현 현황표

작성일: 2026-05-11

## 목적

`docs/api/api-spec.md`의 public API를 기준으로 현재 backend, frontend, Android 구현 상태를 대조한다. 이 문서는 source of truth가 아니며, 후속 Jira 분할과 검증 범위를 줄이기 위한 작업 현황표다.

## 판정 기준

- Public HTTP URL 기준은 `docs/api/api-spec.md`를 따른다.
- 구현 여부는 실제 controller, frontend runtime 코드, Android runtime 코드 기준으로 판단했다.
- Frontend/Android UI는 별도 작업 중이므로 여기서는 API client, repository, outbox sender, mapper, SSE adapter 같은 headless 연동 상태만 본다.
- `backend/src/main/java/com/surimap/**/Controller.java` 기준 현재 public controller는 Incident, PolicePhone heartbeat, SearchPath, EventStream, Marker/Photo, Sync/Offline, Tiles만 있다.

상태 표기:

| 상태 | 의미 |
|---|---|
| 구현 | public endpoint 또는 headless client가 실제 runtime path에 연결되어 있다. |
| 부분 | 일부 골격, 테스트 fixture, slot renderer, local model은 있으나 실제 public 연동이 부족하다. |
| 미구현 | 기준 public contract를 처리하는 runtime 코드가 없다. |
| 불일치 | 구현은 있으나 기준 문서와 header/path/용어가 충돌한다. |

## 핵심 결론

1. 백엔드 public URL prefix는 `S14P31C106-206`에서 정렬됐다. JSON API는 `/api`, tiles는 `/tiles`로 노출된다.
2. 백엔드는 S1-1 Incident, S3-1 SearchPath, S4 SSE, S5 Marker/Photo, S7 Offline/Tiles 일부가 구현되어 있다.
3. 백엔드는 S3-2 Board read controller shell이 추가됐지만 실제 slot source row provider 연결은 남아 있다. S2 SearchArea public controller와 S8 OperationalPeriod/DutyShift/Handover/SearchHistorySummary public controller는 아직 없다.
4. 백엔드 S6 `POST /api/sync/clock`, `POST /api/sync/outbox/requeue`는 `X-PolicePhone-Id`/`police_phone_*` 계약으로 정렬됐다.
5. Frontend는 TanStack Query provider와 MapLibre `/tiles` 렌더링만 있고, board API query나 SSE `EventSource` adapter가 없다.
6. Frontend Vite dev proxy는 `/api`만 있고 `/tiles` proxy가 없어 로컬 백엔드 타일 endpoint와 개발 서버 연동이 끊길 수 있다.
7. Android는 공통 HTTP client와 real `OutboxSender`가 추가됐지만, domain별 repository/write operation builder는 아직 없다.

## Backend API 현황

| API | Owner | Backend | 근거 | 후속 작업 |
|---|---:|---|---|---|
| `POST /api/auth/login` | S1-2 | 구현 | `AuthController`, `AuthSessionAuthenticationFilter` | FE/Android headless client 추가됨 |
| `POST /api/auth/logout` | S1-2 | 구현 | `AuthController` | FE/Android headless client 추가됨 |
| `POST /api/fcm/tokens` | S1-2 | 구현 | `FcmTokenController` | Android headless client 추가됨 |
| `POST /api/police-phones/{policePhoneId}/heartbeat` | S1-2 | 구현 | `PolicePhoneHeartbeatController` | Android headless client 추가됨 |
| `POST /api/incidents/import` | S1-1 | 구현 | `IncidentImportController` | Web command client 필요 |
| `GET /api/incidents` | S1-1 | 구현 | `IncidentReadController` | FE/Android read repository 필요 |
| `GET /api/incidents/{incidentId}` | S1-1 | 구현 | `IncidentReadController` | FE/Android read repository 필요 |
| `POST /api/incidents/{incidentId}/close` | S1-1 | 구현 | `IncidentCloseController` | Web command client 필요 |
| `POST /api/search-areas` | S2 | 미구현 | `maparea` service/query는 있으나 controller 없음 | S2 public controller 구현 |
| `GET /api/search-areas` | S2 | 미구현 | controller 없음 | board/offline/Android read path 우선 |
| `PATCH /api/search-areas/{searchAreaId}` | S2 | 미구현 | controller 없음 | Web command client와 함께 구현 |
| `POST /api/search-areas/{searchAreaId}/split` | S2 | 미구현 | controller 없음 | 분할 정책 확인 후 구현 |
| `POST /api/search-areas/{searchAreaId}/assignments` | S2 | 미구현 | controller 없음 | `boundaries.md`의 S8 assignment 경로와 충돌 확인 필요 |
| `POST /api/search-paths` | S3-1 | 구현 | `PathController` | Android write operation builder 필요 |
| `PATCH /api/search-paths/{searchPathId}` | S3-1 | 구현 | `PathController` | Android write operation builder 필요 |
| `POST /api/search-paths/batch` | S3-1 | 구현 | `SearchPathController` | Android real outbox replay 필요 |
| `GET /api/search-paths` | S3-1 | 구현 | `SearchPathController` | FE board mapper와 Android read repository 필요 |
| `PATCH /api/search-path-segments/{searchPathSegmentId}` | S3-1 | 구현 | `SearchPathSegmentController` | Web correction client 필요 |
| `GET /api/incidents/{incidentId}/board` | S3-2 | 부분 | `IncidentBoardController`가 `BoardAssembler` 기반 response shape와 WEB guard를 노출 | S2/S3-1/S5/S7/S8 source row provider 연결, location access audit guard 정리 |
| `GET /api/incidents/{incidentId}/events` | S4 | 구현 | `EventStreamController` | FE SSE adapter 필요 |
| `POST /api/markers` | S5 | 구현 | `MarkerController` | Android write operation builder 필요 |
| `PATCH /api/markers/{markerId}` | S5 | 구현 | `MarkerController` | Android/Web policy client 필요 |
| `DELETE /api/markers/{markerId}` | S5 | 구현 | `MarkerController` | Android/Web policy client 필요 |
| `POST /api/markers/{markerId}/photos/upload-url` | S5 | 구현 | `PhotoController` | Android photo upload flow 필요 |
| `POST /api/markers/{markerId}/photos/{photoId}/attach` | S5 | 구현 | `PhotoController` | Android photo attach flow 필요 |
| `POST /api/sync/clock` | S6 | 구현 | `SyncClockController`가 `X-PolicePhone-Id`/`police_phone_*` 계약 사용 | Android sync clock client 추가 |
| `POST /api/sync/outbox/requeue` | S6 | 구현 | `OutboxRequeueController`가 `X-PolicePhone-Id`/`police_phone_*` 계약 사용 | Android requeue client 추가 |
| `GET /api/incidents/{incidentId}/offline-package/manifest` | S7 | 구현 | `OfflinePackageController` | Android package repository 필요 |
| `POST /api/incidents/{incidentId}/offline-package/installations` | S7 | 구현 | `OfflinePackageController` | Android outbox replay 연결 필요 |
| `POST /api/operational-periods` | S8 | 미구현 | command/query는 있으나 public controller 없음 | S8 backend controller 우선 구현 |
| `GET /api/incidents/{incidentId}/operational-periods` | S8 | 미구현 | `OperationalPeriodQuery`만 있음 | board/offline/Android read path 우선 |
| `POST /api/duty-shifts` | S8 | 미구현 | public controller 없음 | Android duty shift write 필요 |
| `PATCH /api/duty-shifts/{dutyShiftId}` | S8 | 미구현 | public controller 없음 | summary server job trigger 포함 |
| `GET /api/duty-shifts` | S8 | 미구현 | public controller 없음 | Web/App read repository 필요 |
| `POST /api/handover-memos` | S8 | 미구현 | handover command/query 패키지는 있으나 controller 없음 | App/Web field-or-web write 구현 |
| `GET /api/handover-memos` | S8 | 미구현 | public controller 없음 | board slot source로 필요 |
| `GET /api/operational-periods/{operationalPeriodId}/search-history-summaries` | S8 | 미구현 | `domain/summary`만 있고 public read 없음 | App/Web read-only 구현 |
| `GET /tiles/styles/{styleId}.json` | S7 | 구현 | `TileController` | FE `/tiles` proxy 필요 |
| `GET /tiles/{style}/{z}/{x}/{y}.pbf` | S7 | 구현 | `TileController` | Android MapLibre tile wiring 필요 |

## Frontend Headless 현황

| 영역 | 상태 | 근거 | 후속 작업 |
|---|---|---|---|
| 공통 API base | 부분 | `getApiBaseUrl()`와 Vite `/api` proxy만 있음 | fetch wrapper, auth header, `{error}` parser 추가 |
| TanStack Query | 부분 | `QueryClientProvider`만 있음 | `useIncidentBoard`, incident query hooks 추가 |
| Board read | 미구현 | `fetchIncidentBoard` 없음 | `GET /api/incidents/{incidentId}/board` client/mapper 작성 |
| SSE | 미구현 | `EventSource` 사용 없음 | `GET /api/incidents/{incidentId}/events` adapter 작성 |
| Board slots | 부분 | slot component/test는 있음 | fixture rows 대신 API mapper 결과 주입 |
| Tiles | 부분 | MapLibre style URL은 `/tiles/styles/osm-local.json` | Vite `/tiles` proxy 추가 또는 tile base config 결정 |
| Web commands | 미구현 | import/close/search-area/op/handover command client 없음 | UI 작업물과 합칠 headless command API만 선행 |

## Android Headless 현황

| 영역 | 상태 | 근거 | 후속 작업 |
|---|---|---|---|
| 네트워크 의존성 | 미구현 | Gradle catalog에 OkHttp/Retrofit/Ktor 없음 | OkHttp 기반 generic client 또는 Retrofit 선택 |
| API client | 미구현 | runtime HTTP client 코드 없음 | base URL, auth, `X-Client-Channel: APP`, `X-PolicePhone-Id` 처리 |
| Outbox local model | 구현 | Room `OutboxEntity`, DAO, WorkManager, state machine 있음 | 실제 sender와 sequence barrier 연결 |
| Outbox sender | 부분 | `OutboxSender` interface는 있으나 기본값이 `NoopOutboxSender` | production 기본 sender를 real HTTP로 교체 |
| Incident/offline read repository | 미구현 | repository/API layer 없음 | incident list/detail, manifest repository 추가 |
| SearchPath/Marker write builder | 미구현 | tests에 sample path만 있음 | write operation builder와 payload mapper 추가 |
| DutyShift/Handover/Summary | 미구현 | API client/repository 없음 | S8 backend controller 이후 연결 |
| Tiles | 부분 | MapLibre dependency만 있음 | local `/tiles` style/tile source wiring 추가 |

## 기준 문서 충돌 또는 주의 지점

- `docs/api/api-spec.md`는 S5 photo endpoint를 `upload-url`/`attach`로 확정했지만, `docs/spec/boundaries.md` 일부 표에는 `presign`/`finalize`가 남아 있다. 구현은 `docs/api/api-spec.md` 기준을 따른다.
- `docs/api/api-spec.md`는 `POST /api/search-areas/{searchAreaId}/assignments`를 S2로 둔다. `docs/spec/boundaries.md`에는 `POST /operational-periods/{opId}/assignments`가 S8로 남아 있어 후속 구현 전 owner/URL 정리가 필요하다.
- S6 controller와 S6 spec fixture의 sync API 용어는 `PolicePhone`/`X-PolicePhone-Id` 기준으로 정렬됐다.
- Search history summary 생성은 public retry/command API가 아니다. 서버는 duty shift 종료 또는 OP 전환 후 job으로 생성하고, Web/App은 read-only endpoint로만 확인해야 한다.

## 권장 후속 MR 순서

1. `[BE]` S3-2 Board source row provider 연결과 `GET /api/incidents/{incidentId}/board` 데이터 충실도 보강
2. `[BE]` S2 SearchArea public controller 구현 및 assignment URL 충돌 정리
3. `[BE]` S8 OperationalPeriod/DutyShift/Handover/SearchHistorySummary controller 구현
4. `[FE]` 공통 API client, board query, SSE adapter, board mapper 추가
5. `[FE]` Vite `/tiles` proxy 또는 tile base URL 설정 정리
6. `[Android]` incident/offline/search-path/marker/duty-shift/handover repository와 write operation builder 추가
