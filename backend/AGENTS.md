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

- 이름: `채널별 adapter + 도메인 중심 패키징 + 전역 기반 패키지 분리`
- `SuriMapApplication`은 `com.surimap` 루트에 둔다. 신규 최상위 패키지는 `api`, `app`, `client`, `config`, `domain`, `global`만 사용한다.
- 새 도메인 패키지는 Spec/Lane 경계가 드러나게 둔다. 예: `incident`, `account`, `policephone`, `event`, `searcharea`, `path`, `sync`, `marker`, `notification`, `board`, `offline`, `op`, `handover`.
- Web/프론트엔드 controller와 request DTO는 `api/controller/{domain}/...`에 둔다.
- Android 앱 controller와 request DTO는 `app/controller/{domain}/...`에 둔다.
- Web/프론트엔드 service와 Service Request/Response DTO는 `api/service/{domain}/...`에 둔다.
- Android 앱 service와 Service Request/Response DTO는 `app/service/{domain}/...`에 둔다.
- APP/WEB 공용 read라도 Web 상황판 응답 조립이면 `api`, Android 현장 앱 응답 조립이면 `app`에 둔다. 양쪽에서 쓰는 domain 조회/정책/mapper는 `domain`에 둔다.
- `domain` 하위는 DB 테이블 개수보다 애그리거트 경계를 우선한다.
- 도메인 객체는 기본적으로 `class`로 작성한다. 값 전달만 하는 객체처럼 보이더라도 상태 변경, 검증, 계산 로직이 들어갈 가능성이 있으면 `record`로 만들지 않는다.
- 도메인 객체 필드는 `private`으로 선언하고 기본적으로 `final`을 붙이지 않는다. 외부 변경은 setter가 아니라 의미 있는 도메인 메서드로 통제한다.
- 도메인 객체는 Lombok `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 기본으로 사용한다. MyBatis와 프레임워크가 객체를 만들 수 있게 열어두되, 애플리케이션 코드가 빈 객체를 직접 만들지 못하게 한다.
- `@Setter`는 사용하지 않는다. 상태 변경은 `start`, `end`, `append...`, `correct...`처럼 업무 의미가 드러나는 메서드로 만든다.
- 생성 경로가 필요하면 `@Builder`나 정적 팩터리를 사용한다. UUID와 시간이 많은 생성자는 public all-args 생성자로 열지 않는다.
- MyBatis 매핑은 도메인 객체를 직접 사용하는 것을 기본으로 한다. 같은 의미의 `ReadRecord`, `PersistenceRecord`, `Aggregate`, `Model`을 습관적으로 만들지 않는다. 복잡한 조회 projection, 조인 결과, API 전용 응답처럼 도메인과 모양이 실제로 다를 때만 별도 객체를 둔다.
- 컬렉션 필드도 처음에는 Lombok getter로 단순하게 노출한다. 컬렉션 자체에 변경 규칙이 생기거나 외부 임의 변경이 실제 문제가 되면 그때 일급 컬렉션으로 분리한다.
- MyBatis mapper interface는 해당 domain 가까이에 두고, XML은 `resources/mapper/{domain}/...Mapper.xml`처럼 찾기 쉽게 맞춘다.
- `common`, `util`, `misc`처럼 owner가 흐려지는 신규 패키지는 만들지 않는다. 기존 `common` 코드는 수정할 때 `global` 또는 더 구체적인 패키지로 옮긴다.

좋은 예시:

- `api/controller/searcharea/SearchAreaCommandController`
- `api/controller/searcharea/request/CreateSearchAreaRequest`
- `api/controller/board/IncidentBoardController`
- `app/controller/path/AppSearchPathController`
- `app/controller/path/request/StartSearchPathRequest`
- `app/controller/marker/AppMarkerController`
- `api/service/searcharea/SearchAreaCommandService`
- `app/service/path/AppSearchPathService`
- `app/service/path/request/StartSearchPathServiceRequest`
- `domain/searcharea/SearchArea`, `domain/searcharea/SearchAreaMapper`
- `domain/policephone/PolicePhone`, `domain/path/SearchPath`, `domain/op/OperationalPeriod`, `domain/op/DutyShift`
- `client/fcm/FcmDispatcher`
- `config/SecurityConfig`
- `global/health/HealthController`

지양 예시:

- 독립 애그리거트 근거 없이 `domain/area`, `domain/pathsegment`, `domain/report`를 평평하게 늘리는 구조
- API DTO, mapper row, domain model을 한 클래스에 섞는 구조
- `api`와 `app` service에 같은 domain 정책, SQL, mapper 조합을 복붙하는 구조
- Web/App channel 정책을 controller마다 문자열 조건문으로 흩뿌리는 구조

## 예외 처리 기준

- 비즈니스 규칙 위반은 `global/error/BusinessException` 하나로 표현하고, 세부 내용은 `global/error/ErrorCode`로 분류한다.
- `ErrorCode`는 HTTP status와 API error code를 함께 가진다. 응답 body는 `docs/api/api-spec.md` 기준에 맞춰 `{ "error": "<code>" }` 형태를 유지한다.
- `GlobalExceptionHandler`는 `BusinessException`을 공통으로 처리한다. controller 또는 도메인마다 같은 모양의 exception handler를 새로 만들지 않는다.
- 특정 도메인 예외 타입은 catch 타입을 다르게 잡아 복구해야 하는 실제 이유가 있을 때만 추가한다.
- 예상하지 못한 시스템 예외, DB 장애, 외부 API 장애는 `BusinessException`으로 감싸지 않는다.

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
- Service는 Controller 패키지의 Request/Response DTO를 import하지 않는다.
- Service Response DTO는 domain object, mapper row, 조회 결과를 Service 반환 형태로 변환한다.
- Controller Response DTO는 `from(ServiceResponse)`으로 Public API 응답을 만든다. Service Response를 HTTP 응답으로 직접 반환하지 않는다.
- DTO는 API fixture field 이름을 보존한다. 하네스 필드명을 임의로 축약하거나 재명명하지 않는다.
- 새로 작성하거나 리팩토링하는 DTO는 기본적으로 `class`와 Lombok `@Getter`, `@NoArgsConstructor`, `@Builder`를 사용한다. `@Setter`는 사용하지 않는다. 단순 projection에만 `record`를 예외적으로 사용할 수 있다.
- Request/Response 이름은 `도메인 + 동작 + 역할` 순서로 짓는다. 예: `SearchPathStartRequest`, `SearchPathStartServiceRequest`, `SearchPathStartResponse`.
- Controller 응답은 `ResponseEntity<계약 Response DTO>`를 기본으로 사용한다. 공통 `ApiResponse`
  wrapper를 만들거나 사용하지 않는다. 응답 body는 `docs/api/api-spec.md`의 JSON shape와 직접 일치해야 한다.

```java
@PostMapping("/api/search-paths")
ResponseEntity<SearchPathStartResponse> start(
        @RequestHeader("X-PolicePhone-Id") UUID policePhoneId,
        @Valid @RequestBody SearchPathStartRequest request
) {
    SearchPathStartServiceResponse serviceResponse =
            searchPathService.start(request.toServiceRequest(policePhoneId));
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(SearchPathStartResponse.from(serviceResponse));
}
```

## 테스트 기준

- 테스트 이름에 `Red`, `RED`, `Failing`처럼 TDD 진행 단계를 남기지 않는다. TDD 단계는 작업 과정이고, 최종 테스트 이름은 검증하는 동작을 설명해야 한다.
- Domain 규칙은 Spring 없이 단위 테스트로 확인한다. 예: 계산, 상태 변경, 값 검증.
- `*MapperTest`는 `SpringBootTest`로 실제 MyBatis mapper, PostgreSQL/PostGIS, SQL result mapping을 확인한다.
- `*ServiceTest`는 `SpringBootTest`로 실제 mapper, DB, transaction, event staging이 함께 동작하는지 확인한다.
- `*ControllerTest`는 `WebMvcTest`로 HTTP request/response, header, validation, status code, error body를 확인한다. 이 레이어에서는 service mocking을 허용한다.
- 일반 기능 테스트는 Domain Test, `*MapperTest`, `*ServiceTest`, `*ControllerTest` 네 종류를 기본으로 한다. `ContractTest`, `HarnessRunner`는 기준 문서에 별도 계약이나 하네스가 있을 때만 사용한다.
- Service Test는 Controller를 호출하지 않고 Mapper와 DB를 Fake나 Mock으로 바꾸지 않는다. Mapper Test도 Service를 호출하지 않는다.
- `Publisher`는 이벤트 발행 책임이 명확할 때만 사용한다. 이벤트 저장소에 stage하는 구현은 `EventHub...Publisher`, 테스트에서 이벤트를 기록만 하는 구현은 `Capturing...Publisher`처럼 무엇을 발행하거나 기록하는지 이름에 드러낸다.

## Service 분리 기준

- 서비스 계층 로직은 먼저 `{Domain}Service`에 작성한다. 메서드 이름으로 동작을 명확히 구분하고, 처음부터 `CommandService`, `QueryService`, `UseCaseService`로 쪼개지 않는다.
- 메서드 안에서 책임이 여러 개로 갈라지면 SRP 기준의 분리 신호로 본다. 단, 실제 신호가 보일 때만 진행한다. 예: 변경 이유가 둘 이상으로 갈라짐, 서로 관련 없는 의존성이 많아짐, 테스트 준비가 메서드별로 크게 달라짐, 조회 성능 최적화가 쓰기 흐름과 충돌함, 멱등성/이벤트 저장/재시도 같은 부수효과가 한쪽에만 커짐.
- 애플리케이션 흐름 조율 책임이면 `{Domain}{Action/Responsibility}Service`로 분리하고, 순수 계산/판단/검증 같은 도메인 로직이면 도메인 객체나 `Calculator`, `Policy`, `Validator` 같은 이름으로 분리한다.
- 분리할 때도 추상적인 모듈 이름보다 현재 업무 이름을 우선한다. 기능이 작으면 유지하고, 책임이 커진 뒤에만 더 구체적인 이름으로 나눈다.

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
- Controller는 `Idempotency-Key`와 인증 정보를 Service Request에 담아 전달한다. 요청 해시 생성, 응답 재사용, 임시 `Map` 관리는 Service와 `IdempotentResponseCache` 경계에서 처리한다.
- 멱등성 요청 해시는 DTO의 `toString()`이 아니라 구조화된 JSON 직렬화 결과로 계산한다.

## Guard / Security

- Channel은 `X-Client-Channel` 기준으로 `APP`, `WEB`, `INTERNAL`을 구분한다.
- Guard 의미는 `../docs/spec/boundaries.md §4.6` Channel/Role Matrix와 §9 API 표를 따른다.
- `@RequireIncidentAccess`, `@RequireRole`, `@RequireChannel`, `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`, `@RequirePolicePhoneAssigned`, `@RequireOpenIncident`, `@RequireCurrentOp`, `@IdempotentWrite`의 책임을 섞지 않는다.
- 앱 전용 write를 웹에서 허용하지 않는다. 웹 전용 command를 앱에서 허용하지 않는다.
- 위치 데이터 조회 API는 `@RecordLocationAccess` 적용 여부를 확인한다.
- 경로 기록 주체는 개인 `accountId`다. `PolicePhone`은 앱 단말 인증, 배정 guard, 전송 컨텍스트로만 함께 남긴다.

## Test

- 기본 검증: `./gradlew test`
- 테스트는 JUnit 5, AssertJ, Spring Security Test, MyBatis Spring Boot Test 기준으로 작성한다.
- Controller: channel/role guard, request/response, error body를 검증한다.
- Mapper: MyBatis mapper test와 PostGIS geometry 변환을 검증한다.
- Service: transaction rule, idempotency, event staging, rollback을 검증한다.
- Event/SSE: envelope, Last-Event-ID replay, `gone_refetch_required`를 검증한다.
- Parser, mapper DTO 변환, policy처럼 순수 로직 중심 클래스는 Spring 컨텍스트 없이 unit test를 우선한다.
- PostGIS 통합 테스트는 기존 `PostGisIntegrationTestSupport`와 Testcontainers 구성을 재사용한다. 도메인마다 별도 컨테이너 기반 클래스를 만들지 않는다.
- Spring REST Docs와 `ValidationMessages.properties`는 현재 의존성 기준 강제하지 않는다.
- RED test는 기준 문서의 API, event, error, fixture ID를 문자열 그대로 사용한다.

## Commit

- 커밋 메시지와 area tag는 `../docs/tasks/index.md`를 따른다.
- Backend 단독 변경은 `[BE]`, Backend와 다른 영역을 함께 바꾸면 `[BE/FE/Android/Infra]`처럼 slash 구분 area tag를 사용한다.
