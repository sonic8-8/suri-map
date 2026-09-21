# API 경로 검사와 문서화 전환

API 문서와 이 표는 Spring REST Docs 전환 전까지 임시 보존한다. 과거 구현 판정과 현재 경로 등록 검사를 구분하며, 전체 API 기능의 완료표로 사용하지 않는다.

## 남기는 내용과 이유

| 대상 | 이번 정리 |
|---|---|
| [API 참고 문서](../api/api-spec.md) | HTTP 제목 46개와 요청·응답·오류 설명을 유지했다. 과거 Spec 담당자·중복 URL 변환표는 Git 원문으로 남겼다. |
| 아래 상태표 | 검사 입력인 URL·옛 구분·상태를 유지했다. 오래된 구현 근거·후속 MR 순서와 UI 연결 표는 원문으로 남겼다. |
| [ApiImplementationStatusCoverageTest](../../backend/src/test/java/com/surimap/architecture/ApiImplementationStatusCoverageTest.java) | 대체 문서와 검사를 검증할 때까지 유지한다. 이번에는 Java 코드·검증 조건을 바꾸지 않았다. |

## 현재 검사가 확인하는 범위

1. API 문서의 ``#### METHOD `path` `` 형식 제목을 읽어, 모두 상태표에 있는지 확인한다.
2. 상태가 `구현`·`부분`·`불일치`인 행의 HTTP method·path가 Spring에 등록됐는지 확인한다. `미구현` 행은 경로가 없어도 허용한다.

상태표의 URL은 첫 번째 열, 상태는 세 번째 열에서 읽는다. 이 형식과 API 제목을 바꾸려면 소비 테스트도 함께 전환해야 한다.

이 검사는 HTTP 요청을 보내지 않는다. 입력·응답 필드, 인증·권한, 채널별 헤더 조건, 오류·멱등성, DB 저장, SSE 재전송, 타일 제공, 화면 연결은 검증하지 않는다. 코드의 모든 경로가 문서에 실렸는지도 역으로 확인하지 않으며, Keycloak/OIDC 로그인 행은 Spring 경로 검사에서 제외된다.

2026-09-21 정리 전후 검사 2개가 각각 통과했다. 정리 후에는 `cd backend && ./gradlew cleanTest test --tests com.surimap.architecture.ApiImplementationStatusCoverageTest`로 재실행했다. 이는 test profile의 경로 등록 확인이며 PostGIS·실제 서버·업무폰 검증이 아니다. 아래의 과거 판정을 현재 기능 완료로 갱신하지 않았다.

## 과거 상태표 — 2026-05-19

`구현`은 당시 경로·클라이언트 연결, `부분`은 일부 연결, `미구현`은 경로 부재, `불일치`는 당시 문서와의 차이를 뜻했다. 옛 S1~S8 표기는 현재 담당자·승인 규칙이 아니다.

| API | 옛 구분 | 당시 판정 |
|---|---|---|
| Keycloak/OIDC login/logout | S1-2 | 구현 |
| `POST /api/fcm/tokens` | S1-2 | 구현 |
| `POST /api/police-phones/{policePhoneId}/heartbeat` | S1-2 | 구현 |
| `POST /api/incidents/import` | S1-1 | 구현 |
| `POST /api/internal/mock-112/events` | S1-1 | 구현 |
| `GET /api/incidents` | S1-1 | 구현 |
| `GET /api/incidents/{incidentId}` | S1-1 | 구현 |
| `GET /api/incidents/{incidentId}/map-revisions` | APP map cache aggregate | 구현 |
| `POST /api/incidents/{incidentId}/close` | S1-1 | 구현 |
| `POST /api/search-areas` | S2 | 부분 |
| `GET /api/search-areas` | S2 | 부분 |
| `PATCH /api/search-areas/{searchAreaId}` | S2 | 부분 |
| `POST /api/search-areas/{searchAreaId}/split` | S2 | 부분 |
| `POST /api/search-areas/{searchAreaId}/assignments` | S2 | 부분 |
| `POST /api/search-paths` | S3-1 | 구현 |
| `PATCH /api/search-paths/{searchPathId}` | S3-1 | 구현 |
| `POST /api/search-paths/batch` | S3-1 | 구현 |
| `POST /api/search-area-boundary-alerts` | S3-1 | 구현 |
| `GET /api/search-paths` | S3-1 | 구현 |
| `PATCH /api/search-path-segments/{searchPathSegmentId}` | S3-1 | 구현 |
| `GET /api/incidents/{incidentId}/board` | S3-2 | 부분 |
| `GET /api/incidents/{incidentId}/events` | S4 | 구현 |
| `GET /api/incidents/events` | S4 | 구현 |
| `GET /api/markers` | S5 | 구현 |
| `POST /api/markers` | S5 | 구현 |
| `PATCH /api/markers/{markerId}` | S5 | 구현 |
| `DELETE /api/markers/{markerId}` | S5 | 구현 |
| `POST /api/markers/photos/upload-url` | S5 | 구현 |
| `POST /api/markers/{markerId}/photos/upload-url` | S5 | 구현 |
| `POST /api/markers/{markerId}/photos/{photoId}/attach` | S5 | 구현 |
| `POST /api/sync/clock` | S6 | 구현 |
| `POST /api/sync/outbox/requeue` | S6 | 구현 |
| `GET /api/incidents/{incidentId}/offline-package/manifest` | S7 | 구현 |
| `POST /api/incidents/{incidentId}/offline-package/installations` | S7 | 구현 |
| `POST /api/operational-periods` | S8 | 부분 |
| `GET /api/incidents/{incidentId}/operational-periods` | S8 | 부분 |
| `POST /api/operational-periods/comparisons` | S8 | 부분 |
| `POST /api/duty-shifts` | S8 | 부분 |
| `PATCH /api/duty-shifts/{dutyShiftId}` | S8 | 부분 |
| `GET /api/duty-shifts` | S8 | 부분 |
| `POST /api/handover-memos` | S8 | 부분 |
| `GET /api/handover-memos` | S8 | 부분 |
| `GET /api/operational-periods/{operationalPeriodId}/handover-timeline` | S8 | 구현 |
| `GET /api/operational-periods/{operationalPeriodId}/search-history-summaries` | S8 | 부분 |
| `GET /tiles/styles/{styleId}.json` | S7 | 구현 |
| `GET /tiles/{style}/{z}/{x}/{y}.pbf` | S7 | 구현 |
| `GET /tiles/fonts/{fontStack}/{range}.pbf` | S7 | 구현 |

## 과거 표와 현재 코드의 차이

- **마커·사진**: 생성은 [AppMarkerController](../../backend/src/main/java/com/surimap/app/controller/marker/AppMarkerController.java), 조회·웹 수정/삭제는 [MarkerController](../../backend/src/main/java/com/surimap/api/controller/marker/MarkerController.java), 생성 전 사진 업로드는 [PhotoController](../../backend/src/main/java/com/surimap/app/controller/photo/PhotoController.java)에 있다. 수정·삭제는 같은 URL을 채널 헤더 조건으로 나눈다. 예전 클래스 목록이나 URL 존재만으로 두 채널을 모두 검증했다고 볼 수 없다.
- **수색 구역**: [SearchAreaApiService](../../backend/src/main/java/com/surimap/api/service/searcharea/SearchAreaApiService.java)에 Mapper·이력 저장 경로와 메모리 대체 경로가 함께 있다. 옛 “MyBatis 미연결” 설명은 현재와 다르지만, 경로 등록 검사는 실제 SQL 실행이나 [도형·담당 배정 요구](../search-area.md)의 충족을 검증하지 않는다.
- 나머지 필요한 요구·코드 차이는 [기능별 확인 위치](../api/api-spec.md#필요한-항목-찾기)에서 확인한다. 이번 두 예시의 정적 대조를 모든 API·소비자의 재검증으로 확대하지 않는다.

## REST Docs 전환 조건

도입 방식은 합의했지만 아직 구현·검증하지 않았다. [Backend 빌드](../../backend/build.gradle)에는 REST Docs·OpenAPI 문서 생성 의존성이 없다.

1. 기존 Controller 테스트의 API 하나에서 Spring REST Docs → `restdocs-api-spec` → OpenAPI·Swagger UI 표시를 로컬 검증한다. 프로덕션 Controller·DTO에는 문서용 Swagger 애노테이션을 추가하지 않는다. 선택 이유와 비용은 도입 작업의 ADR에 남긴다.
2. 테스트가 보내고 받는 요청·응답, 헤더·필수값, 권한 거부·오류·중복 요청을 실제 소비자와 대조한다. 기존 문서와 다르면 원하는 동작부터 합의하며, 코드나 옛 문서 한쪽을 자동으로 정답으로 삼지 않는다.
3. SSE·타일·내부 webhook·OIDC처럼 JSON Controller 문서화만으로 확인되지 않는 경로는 각 검증 범위를 구분한다. 필요한 미구현 요구·호환 이유는 관련 기능 문서에 남긴다.
4. 생성 문서의 범위·재생성 명령·링크·필요한 검사 전환을 확인한 뒤 수기 명세·상태표와 이 검사를 정리한다. 단순 URL 존재 검사를 API 동작 테스트의 대체물로 남기지 않는다.

REST Docs 도입은 현재 문서 정리의 완료 조건이 아니다. 배포·문서 공개·실제 API 실행 권한도 이번에 변경하지 않는다.

## 원문

[정리 전 상태표](https://github.com/sonic8-8/suri-map/blob/f5182b739b0ccc58e5a72080a74dae87e927519b/docs/tasks/api-implementation-status.md)에 당시 클래스·Frontend/Android 연결 상태, 환경 설명, 후속 순서를 보존했다. “Docker 부재”·“headless 연결 대기” 등의 과거 문장을 지금의 장애나 작업 순서로 다시 적용하지 않는다.
