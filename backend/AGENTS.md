# Backend Agent Rules

Suri-Map Spring Boot API 전용 규칙이다. 저장소 공통 규칙은 `../AGENTS.md`를 먼저 따른다.

## 기준 문서

| 관심사 | 기준 |
|---|---|
| Public API | `../docs/api/api-spec.md` |
| Spec/Lane ownership | `../docs/spec/boundaries.md` |
| DB entity 의미 | `../docs/db-design/db-design-readable.md` |
| Persistence 결정 | `../docs/adr.md` ADR-0033 |
| Current stack | `build.gradle` |

## 현재 스택

현재 backend stack은 Java 17, Spring Boot 3.5.x, Spring MVC, Spring Security, MyBatis, Flyway, PostgreSQL/PostGIS, JTS, Actuator/Micrometer다.

## 프로젝트 / 플랫폼 개요

- Backend는 REST JSON API, SSE endpoint, domain write transaction, MyBatis mapper, Flyway migration, event staging, purge/audit internal port를 구현한다.
- Android Room entity, Web 상황판 layout, FCM 외부 adapter 세부 구현, tileserver 운영은 각 owner 규칙을 따른다.
- 다른 Spec의 entity/API/event payload를 바꾸려면 `../docs/spec/boundaries.md §1.2` owner LGTM이 필요하다.
- "프로젝트 전반" 정책은 root `AGENTS.md`에 둔다. 이 파일에는 backend 구현 규칙만 둔다.

## 핵심 규칙

- 기존 AGENTS의 `api/controller`, `api/service` 기준을 유지하고 Android 현장 앱 전용 `app/controller`, `app/service`를 추가한다.
- Web/프론트엔드 상황판 관련 controller/request DTO는 기존 `api`에, Android 현장 앱 관련 controller/request DTO는 `app`에 둔다.
- 패키지명 `api`는 URL prefix `/api`와 다르다. `app` 패키지 controller도 public JSON endpoint이면 `/api` prefix를 사용한다.
- Service 계층도 소비 채널 기준으로 나눈다. Web/프론트엔드 use case는 `api/service`, Android 앱 use case는 `app/service`에 둔다.
- Domain model, mapper, SQL, 핵심 정책은 채널별로 복제하지 않는다. 공통 로직은 `domain` 기준으로 공유한다.
- `SuriMapApplication`은 `com.surimap` 루트에 둔다. 같은 레벨의 최상위 패키지는 `api`, `app`, `domain`, `client`, `config`, 기존 공용 기반인 `common`을 사용한다.
- Controller는 HTTP 요청 수신, 입력 검증, channel/principal 해석, Service 호출, Response DTO 반환만 담당한다.
- Service는 use case 실행, transaction 처리, guard orchestration, mapper 조합, event staging을 담당한다.
- Mapper는 SQL 실행과 row/DTO mapping만 담당한다. domain 판단과 transaction 흐름을 넣지 않는다.
- Public response로 DB row나 domain object를 직접 반환하지 않는다. API 계약에 맞는 Response DTO로 변환한다.
- 보호 API를 추가하거나 보안 설정을 바꿀 때는 request parameter보다 인증 principal과 `SecurityContext` 기반 해석을 우선한다.
- 이름은 `Controller`, `Service`, `QueryService`, `CommandService`, `Mapper`, `Request`, `ServiceRequest`, `Response`, `Config`, `Test`, `TestSupport` 접미사를 사용한다.
- `Reader`, `Provider`, `Manager` 같은 넓은 추상화는 구현 교체 필요나 외부 시스템 경계가 분명할 때만 도입한다.

## 패키지 설계 원칙

- 이름: `기존 계층 구조 유지 + Android app adapter 추가 + 애그리거트 중심 도메인 패키징`
- 새 패키지는 Spec/Lane 경계가 드러나게 둔다. 예: `incident`, `account`, `policephone`, `event`, `searcharea`, `path`, `sync`, `marker`, `notification`, `board`, `offline`, `op`, `handover`.
- Web/프론트엔드 controller와 request DTO는 `api/controller/{domain}/...`에 둔다.
- Android 앱 controller와 request DTO는 `app/controller/{domain}/...`에 둔다.
- Web/프론트엔드 service와 Service Request/Response DTO는 `api/service/{domain}/...`에 둔다.
- Android 앱 service와 Service Request/Response DTO는 `app/service/{domain}/...`에 둔다.
- APP/WEB 공용 read라도 Web 상황판 응답 조립이면 `api`, Android 현장 앱 응답 조립이면 `app`에 둔다. 양쪽에서 쓰는 domain 조회/정책/mapper는 `domain`에 둔다.
- `domain` 하위는 DB 테이블 개수보다 애그리거트 경계를 우선한다.
- MyBatis mapper interface는 해당 domain 가까이에 두고, XML은 `resources/mapper/{domain}/...Mapper.xml`처럼 찾기 쉽게 맞춘다.
- `common`, `util`, `misc`처럼 owner가 흐려지는 신규 패키지는 만들지 않는다. 기존 `common`은 error, health, base response처럼 실제 공용 기반에만 쓴다.

좋은 예시:

- `api/controller/searcharea/SearchAreaCommandController`
- `api/controller/searcharea/request/CreateSearchAreaRequest`
- `api/controller/board/IncidentBoardController`
- `app/controller/path/AppSearchPathController`
- `app/controller/path/request/StartSearchPathRequest`
- `app/controller/marker/AppMarkerController`
- `api/service/searcharea/SearchAreaCommandService`
- `app/service/path/AppSearchPathCommandService`
- `app/service/path/request/StartSearchPathServiceRequest`
- `domain/searcharea/SearchArea`, `domain/searcharea/SearchAreaMapper`
- `domain/policephone/PolicePhone`, `domain/path/SearchPath`, `domain/op/OperationalPeriod`, `domain/op/DutyShift`
- `client/fcm/FcmDispatcher`
- `config/SecurityConfig`
- `common/health/HealthController`

지양 예시:

- 독립 애그리거트 근거 없이 `domain/area`, `domain/pathsegment`, `domain/report`를 평평하게 늘리는 구조
- API DTO, mapper row, domain model을 한 클래스에 섞는 구조
- `api`와 `app` service에 같은 domain 정책, SQL, mapper 조합을 복붙하는 구조
- Web/App channel 정책을 controller마다 문자열 조건문으로 흩뿌리는 구조

## Persistence

- MyBatis 단일 persistence layer를 사용한다. JPA, Hibernate, Spring Data JPA를 새로 도입하지 않는다.
- 이유: PostGIS geometry, outbox/idempotency, board query, event staging은 명시적 SQL과 mapper 경계가 더 적합하다.
- 예외가 필요하면 새 ADR을 작성하고 팀 합의를 받은 뒤 반영한다.
- Mapper interface + XML mapper를 기본으로 한다.
- Transaction boundary는 Service layer `@Transactional`에 둔다.
- PostGIS 컬럼은 SRID 4326을 명시한다. 예: `geometry(Point,4326)`, `geometry(Polygon,4326)`.
- geometry 컬럼에는 GIST index를 둔다.
- Geometry 변환은 공용 MyBatis TypeHandler 또는 명시적 mapper DTO 변환으로 처리한다.
- Flyway migration은 `V{n}__name.sql`, forward-only, expand-and-contract 원칙을 따른다.

Do / Don't:

| Do | Don't |
|---|---|
| MyBatis mapper test | JPA slice test |
| `geometry(Point,4326)` / `geometry(Polygon,4326)` | SRID 없는 geometry |
| Service `@Transactional` | Mapper에서 transaction 흐름 숨기기 |
| XML mapper에서 명시적 SQL | ORM entity 전제 추가 |

## DTO / Layer

- Controller Request DTO와 Service DTO는 분리한다.
- Controller Request DTO는 HTTP 입력 검증과 테스트 요청 객체 생성을 담당한다.
- Controller Request DTO는 Bean Validation으로 입력을 검증하고, 필요한 경우 `toServiceRequest(...)`로 변환한다.
- Service Request DTO는 Controller 계층과 분리된 use case 입력이다.
- Service Response DTO는 domain object, mapper row, 조회 결과를 API 반환 형태로 변환한다.
- DTO는 API fixture field 이름을 보존한다. 하네스 필드명을 임의로 축약하거나 재명명하지 않는다.
- DTO는 기본적으로 `record`보다 `class`를 우선한다. 단순 projection에는 `record`를 쓸 수 있으나 API 계약 안정성을 먼저 본다.

```java
@PostMapping("/api/search-paths")
SearchPathResponse start(
        @RequestHeader("X-PolicePhone-Id") Long policePhoneId,
        @Valid @RequestBody StartSearchPathRequest request
) {
    return searchPathCommandService.start(request.toServiceRequest(policePhoneId));
}
```

## API / Transaction Rule

모든 domain write는 `../docs/spec/boundaries.md §4.3` 순서를 따른다.

1. authorization
2. incident lifecycle guard
3. channel guard
4. idempotency key reserve
5. domain table write
6. `EventHub.publish(PublishRequest)`로 `event_dispatch_job` stage
7. transaction commit
8. `EventFanout`/worker fanout
9. idempotency response cache

- Public JSON API는 `/api` prefix를 사용한다.
- Tileserver는 Spring Boot JSON API가 아니므로 `/tiles`를 사용한다.
- SSE endpoint는 `text/event-stream` 계약을 따르며 `SseEmitter` 또는 스트림 전용 응답을 쓸 수 있다.
- Error response 기본형은 `{ "error": "incident_closed" }`다.
- Validation 상세 응답을 확장하려면 API spec과 테스트를 먼저 맞춘다.

## Guard / Security

- Channel은 `X-Client-Channel` 기준으로 `APP`, `WEB`, `INTERNAL`을 구분한다.
- Guard 의미는 `../docs/spec/boundaries.md §4.6` Channel/Role Matrix와 §9 API 표를 따른다.
- `@RequireIncidentAccess`, `@RequireRole`, `@RequireChannel`, `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`, `@RequirePolicePhoneAssigned`, `@RequireOpenIncident`, `@RequireCurrentOp`, `@IdempotentWrite`의 책임을 섞지 않는다.
- 앱 전용 write를 웹에서 허용하지 않는다. 웹 전용 command를 앱에서 허용하지 않는다.
- 위치 데이터 조회 API는 `@RecordLocationAccess` 적용 여부를 확인한다.
- 경로 주체는 개인 계정이 아니라 `PolicePhone`이다. 조작 주체와 경로 주체를 구분한다.

## Test

- 기본 검증: `./gradlew test`
- 테스트는 JUnit 5, AssertJ, Spring Security Test, MyBatis Spring Boot Test 기준으로 작성한다.
- Controller: channel/role guard, request/response, error body를 검증한다.
- Mapper: MyBatis mapper test와 PostGIS geometry 변환을 검증한다.
- Service: transaction rule, idempotency, event staging, rollback을 검증한다.
- Event/SSE: envelope, Last-Event-ID replay, `gone_refetch_required`를 검증한다.
- Parser, mapper DTO 변환, policy처럼 순수 로직 중심 클래스는 Spring 컨텍스트 없이 unit test를 우선한다.
- 현재 의존성에 Testcontainers가 없으므로 PostGIS Testcontainers는 강제하지 않는다. 도입이 필요하면 별도 build 변경과 근거를 남긴다.
- Spring REST Docs와 `ValidationMessages.properties`는 현재 의존성 기준 강제하지 않는다.
- RED test는 기준 문서의 API, event, error, fixture ID를 문자열 그대로 사용한다.

## Commit

- 커밋 메시지와 area tag는 `../docs/tasks/index.md`를 따른다.
- Backend 단독 변경은 `[BE]`, Backend와 다른 영역을 함께 바꾸면 `[BE/FE/Android/Infra]`처럼 slash 구분 area tag를 사용한다.
