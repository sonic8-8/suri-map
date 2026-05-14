# API 구현 현황표

작성일: 2026-05-11
최종 갱신: 2026-05-12 KST

## 목적

`docs/api/api-spec.md`의 public API를 기준으로 현재 backend, frontend, Android 구현 상태를 대조한다. 이 문서는 source of truth가 아니며, 후속 Jira 분할과 검증 범위를 줄이기 위한 작업 현황표다.

## 판정 기준

- Public HTTP URL 기준은 `docs/api/api-spec.md`를 따른다.
- 구현 여부는 실제 controller, frontend runtime 코드, Android runtime 코드 기준으로 판단했다.
- Frontend/Android UI는 별도 작업 중이므로 여기서는 API client, repository, outbox sender, mapper, SSE adapter 같은 headless 연동 상태만 본다.
- `backend/src/main/java/com/surimap/**/Controller.java` 기준 현재 public controller는 Incident, SearchArea(headless MVP), PolicePhone heartbeat, SearchPath, EventStream, Marker/Photo, Sync/Offline, Tiles를 포함한다.

상태 표기:

| 상태 | 의미 |
|---|---|
| 구현 | public endpoint 또는 headless client가 실제 runtime path에 연결되어 있다. |
| 부분 | 일부 골격, 테스트 fixture, slot renderer, local model은 있으나 실제 public 연동이 부족하다. |
| 미구현 | 기준 public contract를 처리하는 runtime 코드가 없다. |
| 불일치 | 구현은 있으나 기준 문서와 header/path/용어가 충돌한다. |

## 핵심 결론

1. 백엔드 public URL prefix는 `S14P31C106-206`에서 정렬됐다. JSON API는 `/api`, tiles는 `/tiles`로 노출된다.
2. 백엔드는 S1-1 Incident, S2 SearchArea headless MVP, S3-1 SearchPath, S4 SSE, S5 Marker/Photo, S7 Offline/Tiles, S8 OperationalPeriod/DutyShift/Handover/SearchHistorySummary headless MVP가 구현되어 있다.
3. 백엔드 S3-2 Board read controller는 `DefaultIncidentBoardSourceRowCollector`로 S2/S3-1/S5/S7/S8의 현재 구현된 query source를 읽는다. 다만 아직 source owner가 없는 `toast`, `police_phone_freshness`, `incident_terminal` 세부 source와 S8 summary provider READY/STALE 완성도는 후속 보강이 필요하다.
4. 백엔드 S6 `POST /api/sync/clock`, `POST /api/sync/outbox/requeue`는 `X-PolicePhone-Id`/`police_phone_*` 계약으로 정렬됐다.
5. Frontend는 공통 API client, Board API query/hook, fetch 기반 SSE adapter, board response mapper, Vite `/api`·`/tiles` proxy가 구현되어 있다. 실제 화면 연결은 별도 UI 작업물과 합치는 단계가 남아 있다.
6. Android는 OkHttp 기반 공통 API client, real `HttpOutboxSender`, Sync/Auth/Incident/Offline/SearchArea/OperationalPeriod/DutyShift/Handover/Summary/Path/Marker repository 또는 operation builder, MapLibre tile client/header adapter가 구현되어 있다.
7. 남은 큰 위험은 실제 PostGIS 런타임 smoke다. 현재 로컬 WSL에는 Docker가 없고 `localhost:5432` PostgreSQL도 떠 있지 않아 `bootRun` smoke는 환경 준비 후 재실행해야 한다.

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
| `POST /api/search-areas` | S2 | 부분 | `SearchAreaController`, `SearchAreaApiService`가 WEB guard/idempotency header/headless response를 제공하나 MyBatis persistence는 미연결 | search_area/op DB 계약 정렬 후 persistence 전환 |
| `GET /api/search-areas` | S2 | 부분 | `SearchAreaController`, Web client, Android read repository 추가 | board/offline source provider와 MyBatis query adapter 연결 |
| `PATCH /api/search-areas/{searchAreaId}` | S2 | 부분 | `SearchAreaController`, Web command client 추가 | search_area_history/MyBatis persistence 연결 |
| `POST /api/search-areas/{searchAreaId}/split` | S2 | 부분 | `SearchAreaController`, Web command client 추가 | split spatial validation/history persistence 연결 |
| `POST /api/search-areas/{searchAreaId}/assignments` | S2 | 부분 | `SearchAreaController`, Web command client 추가 | `search_area_assignment` migration/owner 정렬 필요 |
| `POST /api/search-paths` | S3-1 | 구현 | `PathController` | Android write operation builder 필요 |
| `PATCH /api/search-paths/{searchPathId}` | S3-1 | 구현 | `PathController` | Android write operation builder 필요 |
| `POST /api/search-paths/batch` | S3-1 | 구현 | `SearchPathController` | Android real outbox replay 필요 |
| `GET /api/search-paths` | S3-1 | 구현 | `SearchPathController` | FE board mapper와 Android read repository 필요 |
| `PATCH /api/search-path-segments/{searchPathSegmentId}` | S3-1 | 구현 | `SearchPathSegmentController` | Web correction client 필요 |
| `GET /api/incidents/{incidentId}/board` | S3-2 | 부분 | `IncidentBoardController`가 `BoardAssembler` 기반 response shape, WEB/incident guard, `@RecordLocationAccess` audit, S2/S3-1/S5/S7/S8의 현재 구현된 query source row collector를 제공. `sinceVersion`은 full snapshot reload watermark로만 취급하며 source-owner delta/minVersion 필터로 쓰지 않는다. | 아직 source owner가 없는 `toast`/`handover_status`/`police_phone_freshness` 세부 source 정리 |
| `GET /api/incidents/{incidentId}/events` | S4 | 구현 | `EventStreamController` | FE fetch 기반 SSE adapter 추가됨 |
| `GET /api/markers` | S5 | 구현 | `MarkerController` | Android live marker overlay 연결됨 |
| `POST /api/markers` | S5 | 구현 | `MarkerController` | Android write operation builder 필요 |
| `PATCH /api/markers/{markerId}` | S5 | 구현 | `MarkerController` | Android/Web policy client 필요 |
| `DELETE /api/markers/{markerId}` | S5 | 구현 | `MarkerController` | Android/Web policy client 필요 |
| `POST /api/markers/{markerId}/photos/upload-url` | S5 | 구현 | `PhotoController` | Android photo upload flow 필요 |
| `POST /api/markers/{markerId}/photos/{photoId}/attach` | S5 | 구현 | `PhotoController` | Android photo attach flow 필요 |
| `POST /api/sync/clock` | S6 | 구현 | `SyncClockController`가 `X-PolicePhone-Id`/`police_phone_*` 계약 사용 | Android sync clock client 추가 |
| `POST /api/sync/outbox/requeue` | S6 | 구현 | `OutboxRequeueController`가 `X-PolicePhone-Id`/`police_phone_*` 계약 사용 | Android requeue client 추가 |
| `GET /api/incidents/{incidentId}/offline-package/manifest` | S7 | 구현 | `OfflinePackageController` | Android package repository 필요 |
| `POST /api/incidents/{incidentId}/offline-package/installations` | S7 | 구현 | `OfflinePackageController` | Android outbox replay 연결 필요 |
| `POST /api/operational-periods` | S8 | 부분 | `OperationalPeriodController`, MyBatis `operational_period` write, Web command client, previous OP summary generation enqueue 추가 | idempotency durable record, handoverMemo 저장, provider 실행/STALE 재생성 보강 |
| `GET /api/incidents/{incidentId}/operational-periods` | S8 | 부분 | `OperationalPeriodController`, `OperationalPeriodQuery`, Web client, Android read repository 추가 | board/offline source provider 연결 |
| `POST /api/duty-shifts` | S8 | 부분 | `AppDutyShiftController`, MyBatis `duty_shift` write, Android outbox repository 추가 | durable idempotency, assignment 정책 보강 |
| `PATCH /api/duty-shifts/{dutyShiftId}` | S8 | 부분 | `AppDutyShiftController`, Android duty shift END outbox repository, 서버 summary generation enqueue, Android lower-sequence barrier 추가 | provider 실행/STALE 재생성 보강 |
| `GET /api/duty-shifts` | S8 | 부분 | `DutyShiftQueryController`, Web API client, Android read repository 추가 | board slot source provider 연결 |
| `POST /api/handover-memos` | S8 | 부분 | `HandoverMemoController`, MyBatis `handover_memo` write, EventHub publish, Web client, Android outbox repository 추가 | durable idempotency와 board source provider 연결 |
| `GET /api/handover-memos` | S8 | 부분 | `HandoverMemoController`, `HandoverMemoMapper`, Web/Android read client 추가 | S3-2 handover slot source provider 연결 |
| `GET /api/operational-periods/{operationalPeriodId}/search-history-summaries` | S8 | 부분 | `SearchHistorySummaryController`, MyBatis read mapper, Web/Android read client, 서버 내부 generation enqueue 추가 | provider 실행과 READY/FAILED 전환, STALE 재생성 계산 보강 |
| `GET /tiles/styles/{styleId}.json` | S7 | 구현 | `TileController` | FE `/tiles` proxy와 Android MapLibre style client 추가됨 |
| `GET /tiles/{style}/{z}/{x}/{y}.pbf` | S7 | 구현 | `TileController` | Android MapLibre tile request header adapter 추가됨 |

## Frontend Headless 현황

| 영역 | 상태 | 근거 | 후속 작업 |
|---|---|---|---|
| 공통 API base | 구현 | `shared/api` fetch wrapper, auth/channel header, `{error}` parser | 화면별 client 주입 확산 |
| TanStack Query | 구현 | `QueryClientProvider`, `useIncidentBoard`, `useIncidentBoardQuery` | UI 작업물과 query state 연결 |
| Board read | 구현 | `fetchIncidentBoard`, board mapper | slot UI에 API mapper 결과 주입 |
| SSE | 구현 | `incidentBoardEventStream` fetch streaming adapter, `Last-Event-ID`, eventId dedupe | 실제 서버 stream smoke |
| Board slots | 부분 | slot component/test와 API mapper가 있음 | fixture rows 대신 API mapper 결과 주입 |
| Tiles | 구현 | MapLibre style URL `/tiles/styles/osm-local.json`, Vite `/api`·`/tiles` proxy | 실제 tile endpoint smoke |
| Web commands | 부분 | incident/search-area/operational-period/handover command client 추가 | UI 작업물과 합칠 나머지 headless command API 선행 |

## Android Headless 현황

| 영역 | 상태 | 근거 | 후속 작업 |
|---|---|---|---|
| 네트워크 의존성 | 구현 | OkHttp 기반 `SuriMapApiClient`, `HttpOutboxSender`, MapLibre tile call factory | 실제 device/runtime 주입 |
| API client | 구현 | base URL, auth, `X-Client-Channel: APP`, `X-PolicePhone-Id`, JSON body 처리 | DI 구성과 UI/ViewModel 연결 |
| Outbox local model | 구현 | Room `OutboxEntity`, DAO, WorkManager, state machine 있음 | 실제 sender와 sequence barrier 연결 |
| Outbox sender | 구현 | `HttpOutboxSender`, `SuriMapNetwork.createOutboxSender`, ACK/retry/final failure test | production DI 기본값 연결 |
| Incident/offline/search-area/operational-period/duty-shift/handover/summary read repository | 부분 | 각 headless repository 추가 | UI/ViewModel 연결 |
| SearchPath/Marker write builder | 구현 | `SearchPathRepository`, `MarkerRepository`, outbox operation builder tests | UI/ViewModel 연결 |
| DutyShift/Handover/Summary | 부분 | duty shift/handover write outbox builder, duty shift END lower-sequence barrier, summary read repository 추가 | UI/ViewModel 연결 |
| Tiles | 구현 | `MapTileClient`, `MapLibreTileSourceFactory`, `MapLibreTileCallFactory`, style validator tests | Map 화면 교체 또는 DI 연결 |

## 기준 문서 충돌 또는 주의 지점

- `docs/spec/boundaries.md`의 public API 표와 Spec ownership은 `docs/api/api-spec.md` 기준으로 정렬됐다. S5 photo endpoint는 `upload-url`/`attach`를 사용하고, search area assignment는 S2 `POST /api/search-areas/{searchAreaId}/assignments`가 owner다.
- `docs/spec/specs/*.json` 안에는 아직 source-spec endpoint 표기가 `/api` prefix 없이 남은 곳이 있다. 이는 canonical public URL 재정의가 아니라 source spec의 축약 표기로 취급하되, 후속 Spec 정리 MR에서 필요한 경우 명시적으로 정렬한다.
- S6 controller와 S6 spec fixture의 sync API 용어는 `PolicePhone`/`X-PolicePhone-Id` 기준으로 정렬됐다.
- Search history summary 생성은 public retry/command API가 아니다. 서버는 duty shift 종료 또는 OP 전환 후 job으로 생성하고, Web/App은 read-only endpoint로만 확인해야 한다.

## 권장 후속 MR 순서

1. `[Runtime]` PostGIS를 준비한 뒤 backend를 실제 기동하고 `/api/health`, `/tiles/styles/osm-local.json`, board REST, SSE stream smoke를 수행
2. `[BE]` S2 SearchArea MyBatis persistence 정렬 및 assignment URL/테이블 충돌 정리
3. `[BE]` S8 summary provider 실행/READY/FAILED/STALE 재생성, Board S8 source provider 완성도 보강
4. `[BE]` Board `toast`, `police_phone_freshness`, `incident_terminal` 세부 source provider 정리
5. `[FE/Android]` 별도 UI 작업물과 headless client/repository 연결
6. `[E2E]` 대표 fixture `inc-precinct-first-001` 기준으로 Web board, Android outbox replay, tiles, SSE 수렴 smoke를 한 번에 실행
