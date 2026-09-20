# 수리맵 시스템 구조

구성 요소와 데이터가 오가는 경로를 찾기 위한 문서다. 2026-09-21 저장소의 코드·설정을 대조했으며, 현재 서버의 배포 상태나 실제 단말·브라우저 동작을 검증한 결과는 아니다.

제품이 제공해야 할 동작은 [PRD](prd.md), 선택 이유는 [ADR](adr/README.md), 기능별 요구와 구현 차이는 아래 연결 문서에서 확인한다.

## 구성 요소

| 구성 요소 | 역할과 확인 위치 |
|---|---|
| Android 앱 | 현장 기록·지도 표시. Kotlin·Compose 화면과 Room·WorkManager를 사용한다. 경로 기록의 연결은 [SuriMapApplication](../android/app/src/main/java/com/surimap/SuriMapApplication.kt), 로컬 저장·전송은 [core/sync](../android/app/src/main/java/com/surimap/core/sync/)에서 확인한다. |
| 웹 상황판 | React·TypeScript 화면. TanStack Query로 서버 데이터를 조회하고 MapLibre로 표시한다. 진입점은 [SituationBoardPage](../frontend/src/features/situationBoard/presentation/pages/SituationBoardPage.tsx), 통신은 [shared/api](../frontend/src/shared/api/)다. |
| Backend | 하나의 Spring Boot 애플리케이션이 HTTP API·SSE·업무 처리를 제공한다. [MyBatis SQL](../backend/src/main/resources/mapper/)과 [Flyway migration](../backend/src/main/resources/db/migration/)으로 PostgreSQL·PostGIS를 사용한다. |
| 인증 | Keycloak이 발급한 JWT를 [SecurityConfig](../backend/src/main/java/com/surimap/config/SecurityConfig.java)와 인증 필터에서 검증한다. 사건·채널·업무폰 권한의 남은 차이는 [인증 기록](authentication.md)에 있다. |
| 사진 저장 | DB에는 메타데이터, S3-compatible 저장소에는 파일을 둔다. [저장소 구현](../backend/src/main/java/com/surimap/client/storage/)은 mock과 실제 HTTP 연동을 구분한다. Runtime Compose에는 MinIO가 포함돼 있다. |
| 지도 타일 | 앱·웹의 `/tiles` 요청을 [TileController](../backend/src/main/java/com/surimap/offlinepackage/controller/TileController.java)가 인증·채널 확인 후 처리한다. 설정에 따라 고정 시험 타일 또는 TileServer GL을 사용한다. |
| 시연 원천 | [mock-112](../mock-112/)가 사건·배정 원천을 제공한다. Backend의 [polling](../backend/src/main/java/com/surimap/external/mock112/Mock112PollingJob.java)·[webhook](../backend/src/main/java/com/surimap/incident/controller/Mock112WebhookController.java)과 가져오기 흐름은 [사건 기록](incident-lifecycle.md)에서 확인한다. 실제 경찰 시스템과의 연동은 아니다. |

의존성·버전은 [Backend 빌드](../backend/build.gradle), [Frontend package](../frontend/package.json), [Android 빌드](../android/app/build.gradle.kts)에서 확인한다.

## 경로 기록부터 상황판 표시까지

1. 앱의 [묶음 기록기](../android/app/src/main/java/com/surimap/feature/search/data/SearchPathGpsBatchRecorder.kt)가 GPS 좌표를 모아 로컬 기록을 요청한다. Room에 기록과 Outbox를 저장하고 전송을 예약한다. 개별 좌표는 묶음이 만들어지기 전까지 메모리에 있으므로, 수집 즉시 모두 영속 저장된다고 설명하지 않는다.
2. [OutboxWorker](../android/app/src/main/java/com/surimap/core/sync/OutboxWorker.kt)가 네트워크 조건에 맞춰 HTTP 요청을 보낸다. 로컬 저장 성공·서버 반영 성공·응답 확인은 서로 다른 단계다. 순서·중복·복구 한계는 [오프라인 동기화](offline-sync.md)를 참고한다.
3. [SearchPathService](../backend/src/main/java/com/surimap/api/service/path/SearchPathService.java)가 새 GPS 좌표를 순서와 함께 저장한다. 이전 경로 전체를 매번 다시 저장하지 않고, 조회할 때 좌표를 연결해 경로 도형을 만든다. [수색 경로 기록](search-path.md)에 과거 도형 호환·남은 조회 비용·주기 구분을 남겼다.
4. [DbEventHub](../backend/src/main/java/com/surimap/eventhub/adapter/DbEventHub.java)가 이벤트 전송 작업을 DB에 저장한다. [Dispatcher](../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobDispatcher.java)는 커밋 후 전달 요청이나 대기 작업 polling을 통해 SSE로 전송한다.
5. 웹은 [fetch 기반 SSE 수신기](../frontend/src/shared/api/eventStream.ts)로 이벤트를 받는다. [상황판 구독](../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts)이 조회 캐시를 무효화하면 API를 다시 조회한다. 서버는 [조회 서비스](../backend/src/main/java/com/surimap/board/IncidentBoardQueryService.java)에서 여러 원본 데이터를 모아 응답을 조립한다.

이 흐름은 호출 관계의 설명이다. DB 전송 작업의 완료가 화면 표시 완료를 뜻하지 않는다. SSE 재전송 이력은 [메모리 저장소](../backend/src/main/java/com/surimap/eventhub/stream/EventStreamConfig.java)를 사용하므로 서버 재시작 후 복구를 보장하지 않는다. 최신 화면 복구와 놓친 알림 복구의 차이는 [이벤트 전달](event-delivery.md)·[상황판 기록](situation-board.md)에서 확인한다.

## 채널과 코드 배치

- 앱은 현장 입력, 웹은 지휘 상황판을 맡는다. 공통 기능과 채널별 권한은 실제 Controller·소비자를 함께 확인한다.
- 마커·사진은 Backend의 `app`·`api` Controller/Service와 `domain`으로 정리돼 있다. `incident`, `board`, `eventhub`, `offlinepackage` 등 기존 최상위 패키지도 남아 있으므로 저장소 전체가 같은 구조로 정리됐다고 보지 않는다. 신규·리팩토링 기준은 [Backend 지침](../backend/AGENTS.md#패키지-설계-원칙)을 따른다.
- Android의 Room Outbox는 미전송 HTTP 요청을, 서버의 SSE 이력은 재연결한 웹에 다시 보낼 이벤트를 저장한다. 저장 대상과 전송 방향이 다르다. 경로 기록 주체도 현재 코드는 계정이며, 옛 문서의 업무폰 중심 설명과의 차이는 [수색 경로 기록](search-path.md#경로의-주체와-생명주기)에 남긴다.
- FCM은 마커·사건 관련 호출부에서 별도로 전송한다. 공통 SSE 작업이 FCM 재시도까지 담당하지 않는다. OpenAI 연동은 수색 이력 요약·차수 비교에 사용하도록 구현돼 있다. 둘 다 [환경 설정](../backend/src/main/resources/application.yml)에 따라 활성화되며, 기본값은 FCM mock·OpenAI 비활성이다. 실제 전달·요약 생성 완료 여부는 [이벤트 전달](event-delivery.md)·[인수인계 기록](handover.md)과 실행 결과를 대조한다.

## 배포와 관측

| 확인할 대상 | 저장소에서 확인한 구성 |
|---|---|
| 애플리케이션 배포 | [Runtime Compose](../infra/docker/docker-compose.runtime.yml)에 Frontend·Backend·PostGIS·MinIO·mock-112·Keycloak과 원천·인증용 별도 PostgreSQL이 있다. TileServer GL은 `tiles` profile이다. 호스트 주소·비밀값·활성 profile은 배포 환경에서 주입한다. |
| HTTP 진입점 | [Frontend Nginx](../frontend/nginx.conf)가 정적 파일을 제공하고 API·타일·인증·사진·mock-112 요청을 중계한다. 별도 호스트 Nginx의 [SSE 설정](../infra/nginx/suri-map-sse.locations.conf)과 [타일 예시](../infra/nginx/tileserver-gl.locations.example.conf)도 있다. 호스트의 전체 설정은 이 저장소만으로 확정할 수 없다. |
| 타일 실행 데이터 | [TileServer 설정](../infra/docker/tileserver/config.json)과 스타일은 저장소에 있다. 실제 MBTiles·glyph는 별도로 준비해야 한다. [준비 안내](../infra/docker/tileserver/README.md)의 EC2 경로는 과거 환경 예시이며, 현재 위치는 `TILESERVER_DATA_DIR`과 배포 파일을 확인한다. |
| 빌드·배포 파이프라인 | [Jenkinsfile](../infra/Jenkinsfile)에 Backend 시험·이미지 빌드·배포·기동 확인·k6 smoke 단계가 있다. SonarQube는 건너뜀으로 기록하고 k6는 배포 뒤 실행한다. 이를 모든 품질 검사가 배포 전에 차단하는 구조로 설명하지 않는다. 현재 작업의 실제 실행 여부는 CI 기록으로 확인한다. |
| 메트릭 | [관측 설정](../infra/observability/README.md)은 Hetzner App 서버의 Actuator·호스트·컨테이너·PostgreSQL 지표를 Ops 서버의 Prometheus·Grafana에서 보는 구성이다. k6 시험 절차는 [부하 테스트 안내](../infra/k6/README.md)에서 확인한다. |
| 로그·백업 | Runtime Compose는 Docker 로그 회전을 설정한다. [부하 시험 전 백업](../infra/observability/app/backup-before-load-test.sh)은 수동 실행 스크립트다. 중앙 로그 수집·정기 백업·복원 검증을 완료했다는 뜻은 아니다. |

## 확인이 남은 운영 계획

옛 문서에 적힌 단일 EC2, AWS 비밀값 관리, S3 암호화, Loki 수집·Alertmanager 알림, 정기 백업·자동 롤백은 현재 운영 보장으로 옮기지 않는다. 변경 작업 전에 실제 배포 설정·권한·실행 증거를 확인한다.

- 백업의 주기·보관 위치·복원 가능 여부, 비밀값 주입·회전, 관리 도구의 접근 제어와 파일 저장소 암호화.
- 중앙 로그의 수집 범위·실패 기록·보존과 장애 알림. 도메인 파기 로그의 미연결 부분은 [데이터 파기 기록](data-retention.md)에 있다.
- GPS 수집·묶음·HTTP 전송·상황판 지연·복구 시간은 별도 기준이다. 옛 5초·10초·3초를 확정 운영 기준으로 채택하지 않으며 [Issue #8](issues/8-gps-collection-transmission-basis.md)의 측정·합의를 따른다.
- 서버·앱의 사건 종료 후 미전송 기록 처리와 개인정보 파기 조건. [데이터 파기](data-retention.md), [오프라인 동기화](offline-sync.md), [사진 복구](marker-photo.md)를 함께 확인한다.

## 과거 설계와 출처

[축약 전 Architecture (`7f2ea69a`)](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/architecture.md)에 팀 프로젝트 당시의 배포·엔티티 표·운영 가정·미정 항목을 보존한다. 옛 ADR·시험 기록의 Architecture 절 번호는 이 원문을 가리킨다. 원문에 적힌 요구사항·인터뷰 문서의 사본은 현재 저장소에 없다.

옛 팀 일정·Git Flow·Lane 분담은 현재 작업 절차로 사용하지 않는다. 변경 절차는 [AGENTS.md](../AGENTS.md), 기존 선택 이유와 대체 관계는 [ADR 기록](adr/README.md)에서 확인한다.
