## 범위
- 이 규칙은 Suri-Map 프로젝트 전반의 코드/문서 작업에 적용한다.
- 구조 예시와 테스트 예시는 주로 `backend` Java/Spring 기준으로 적되, 도메인 규칙과 채널 경계는 `backend`, `frontend`, `android`에 공통 적용한다.

## 핵심 규칙
- 패키지는 계층을 먼저 나누고, 계층 안에서 도메인별로 나눈다.
- 최상위 패키지는 `api`, `domain`, `client`, `config`를 사용하고, 애플리케이션 시작 클래스는 루트 패키지에 둔다.
- Controller는 `api/controller/{domain}`, Controller Request DTO는 `api/controller/{domain}/request`에 둔다.
- Service는 `api/service/{domain}`, Service Request/Response DTO는 `api/service/{domain}/request`, `api/service/{domain}/response`에 둔다.
- `domain` 하위는 애그리거트 루트 기준으로 먼저 묶는다. Entity / Enum / Repository는 기본적으로 같은 애그리거트 패키지에 두고, `entity`, `repository`, `enum` 같은 기술 분류용 하위 패키지는 만들지 않는다.
- 외부 시스템 연동은 `client`에 두고, 스프링 설정만 `config`에 둔다.
- 애그리거트 내부 클래스가 많을 때만 해당 애그리거트 디렉토리 안에서 의미별 하위 패키지로 나눈다.
- 독립 생명주기, 독립 트랜잭션 경계, 독립 조회 요구가 생기면 별도 도메인 패키지로 승격한다. 그렇지 않으면 상위 애그리거트 내부에 둔다.
- Controller는 HTTP 요청 수신, 입력 검증, Service 호출, `ApiResponse` 반환만 담당한다.
- Service는 유스케이스 실행, 트랜잭션 처리, Repository 조합, Domain과 DTO 연결을 담당한다.
- Service 명명은 기본적으로 `...Service`를 사용하고, 조회/변경 책임 분리가 필요해질 때 `...QueryService`, `...CommandService`로 구체화한다.
- Domain은 핵심 상태와 비즈니스 규칙을 가진다. Repository는 조회/저장 책임에 집중한다.
- Controller Request DTO와 Service DTO는 분리한다.
- 보호 API를 추가하거나 보안 설정을 변경할 때는, 요청 파라미터보다 인증 principal과 `SecurityContext` 기반 해석을 우선 검토한다.
- API는 엔티티를 직접 반환하지 않고 Response DTO로 변환한 뒤 공통 `ApiResponse`로 감싼다.
- 단, `text/event-stream` 기반 SSE 엔드포인트는 `SseEmitter` 또는 스트림 전용 응답을 반환할 수 있다.
- 예외 응답은 공통 `ErrorResponse`로 반환한다.
- Validation 예외는 `ErrorResponse`의 `errors` 목록에 필드별 상세를 포함하는 것을 우선 검토한다.
- 새 코드는 기존 구조와 네이밍을 우선 따르고, 과한 추상화보다 명확한 구현을 우선한다.
- `Reader`, `Provider` 같은 추가 추상화는 기본 규칙으로 도입하지 않고, 구현 교체 필요나 외부 시스템 경계가 분명할 때만 예외적으로 검토한다.
- 이름은 `Controller`, `Service`, `Client`, `Repository`, `Request`, `ServiceRequest`, `Response`, `Config`, `Test`, `TestSupport` 접미사를 사용한다.
- Value Object는 기본값으로 만들지 않고, 도메인 의미, 불변성/생성 검증, 값 비교 규칙이 분명할 때만 도입한다.

### 패키지 설계 원칙
- 이름: `계층 분리 + 애그리거트 중심 도메인 패키징`
- 최상위는 `api`, `domain`, `client`, `config`처럼 계층으로 나눈다.
- `domain` 하위는 DB 테이블 개수보다 애그리거트 경계를 우선한다.
- 하위 개념은 `domain` 바로 아래에 평평하게 두지 말고, 가능하면 상위 애그리거트 내부 하위 패키지에 둔다.

좋은 예시
- `domain/user/User`, `domain/user/UserRepository`, `domain/user/auth/RefreshToken`
- `domain/gamesession/GameSession`, `domain/gamesession/turn/GameTurnSlot`, `domain/gamesession/report/GameReport`

지양 예시
- `domain/turn`, `domain/settlement`, `domain/report`를 독립 애그리거트 근거 없이 `domain` 바로 아래에 평평하게 두는 구조

### 구현 시 반드시 유지할 도메인 규칙
- 사건은 사용자가 직접 생성하지 않는다. 사건 생성 진입은 `mock·seed import` 기준으로 본다.
- 계정 모델은 `팀 계정`, `순찰차 계정`, `지휘 계정` 기준이다. 개인 계정 전제를 기본값으로 두지 않는다.
- GPS 경로의 기록 주체는 `Device`다. 조작 주체와 경로 주체를 혼동하지 않는다.
- `OP(Operation Period)`는 사건 내 수색 차수와 인수인계의 기준 단위다.
- `OP1`은 bootstrap에서 자동 생성되고, 이후 OP는 수동 생성이라는 전제를 유지한다.
- 지도 범위 기준 용어는 `map_boundary`를 사용한다.
- 자동 판단 금지 원칙을 따른다. 확인 누락 확정, 다음 수색 구역 추천, 위험도 판단 같은 기능이나 문구를 임의로 추가하지 않는다.
- 사건 종료는 `terminal`이며 재오픈 사용자 API/UI를 전제하지 않는다.
- 실종자 정보는 장기 보존 원본이 아니라 운영 캐시로 다룬다.

### 채널 경계 규칙
- Android 앱은 현장 입력 채널이다.
- Web 상황판은 지휘·상황 공유 채널이다.
- 앱 전용 쓰기를 웹에서 허용하지 않는다.
- 웹 전용 지휘 쓰기를 앱에서 허용하지 않는다.
- 현장 마커 생성은 앱 전용으로 본다. 단, 초기 기준점 마커는 웹에서 추가·수정 가능하다는 현재 기준을 유지한다.
- 구역 완료, OP 생성, 지도 기준 범위 변경, 차량/도보 구간 보정은 웹 전용으로 본다.
- 인수인계 메모는 앱/웹 공통으로 본다.


## 권장 사항

### 코드 컨벤션 (BE)
- indent depth는 가급적 2 이하로 유지한다.
- `else`, `switch/case`, 삼항 연산자 사용은 지양하고 Early return 패턴을 우선 검토한다.
- 가급적 1메서드 1기능 원칙을 지향한다.
- 예외는 특별한 이유가 없으면 static factory보다 `throw new ExceptionType(...)` 형태를 우선 사용한다.
- 핵심 도메인의 원시값과 문자열은 VO 후보로 먼저 검토한다.
- 비즈니스 로직이 포함된 컬렉션은 일급 컬렉션으로 포장할지 검토한다.
- 객체 생성 시 정적 팩토리 메서드 패턴을 우선 검토한다.
- Controller는 `@RestController`, `@RequiredArgsConstructor`, Service는 `@Service`, `@RequiredArgsConstructor`를 기본으로 검토한다.
- 엔티티는 `@Getter`, `@Entity`, `@NoArgsConstructor(access = PROTECTED)` 패턴을 우선 검토하고, 생성은 Builder 또는 정적 팩토리 메서드를 우선 검토한다.
- Validation 메시지는 DTO에 하드코딩하지 않고 메시지 키를 사용하며, 실제 문구는 `ValidationMessages.properties`에서 관리하는 것을 우선 검토한다.

### DTO 컨벤션
- 기준 예시는 `ImportIncidentRequest`, `ImportIncidentServiceRequest`, `IncidentResponse`를 따른다.
- Controller Request DTO는 HTTP 입력 검증과 테스트용 요청 객체 생성을 위한 DTO다.
- Controller Request DTO는 기본적으로 `class`, `@Getter`, `@NoArgsConstructor`를 사용하고, 생성은 `private` 생성자에 `@Builder`를 붙이는 패턴을 우선 사용한다.
- Controller Request DTO는 Bean Validation annotation으로 입력을 검증하고, Service DTO가 필요하면 `toServiceRequest()` 메서드로 변환한다.
- Service Request DTO는 Controller 계층과 분리된 서비스 입력 DTO다.
- Service Request DTO는 기본적으로 `class`, `@Getter`, `@NoArgsConstructor`를 사용하고, 생성은 `private` 생성자에 `@Builder`를 붙이는 패턴을 우선 사용한다.
- Controller Request DTO와 구분이 필요할 때 이름은 `...ServiceRequest`를 사용한다.
- Service Response DTO는 도메인 객체나 조회 결과를 반환 형태로 변환하는 DTO다.
- Service Response DTO는 기본적으로 `class`, `@Getter`를 사용하고, 생성은 `private` 생성자에 `@Builder`를 붙이는 패턴을 우선 사용한다.
- Service Response DTO는 외부에서 builder를 직접 조합하기보다 `of(...)` 또는 `from(...)` 정적 팩토리 메서드로 생성하는 것을 우선 사용한다.
- DTO는 기본적으로 `record` 대신 `class`를 사용한다.


### 빌드/테스트
- 테스트 실행은 `./gradlew test`를 우선 사용한다.
- 프론트엔드 패키지 매니저는 `npm` 사용을 우선한다.
- 테스트는 JUnit 5, AssertJ를 사용하고, `@DisplayName` 한글 문장과 `given / when / then` 구조를 우선 따른다.
- 테스트는 계층 책임에 맞춰 분리한다.
- Controller 테스트는 `@WebMvcTest` 기반 슬라이스 테스트를 기본으로 사용한다.
- Spring REST Docs를 사용하는 Controller 테스트는 `@WebMvcTest`, `@AutoConfigureRestDocs`, `RestDocsTestSupport` 패턴을 기본으로 검토한다.
- 문서 대상 Controller 테스트는 `document(...)`로 request/response snippet을 남기고, 인증 헤더와 path/query/body, `ApiResponse` 또는 `ErrorResponse` 필드를 함께 문서화하는 것을 우선 검토한다.
- Service 테스트는 `@SpringBootTest` 기반 통합 테스트를 기본으로 사용한다.
- Repository 테스트도 `@SpringBootTest` 기반 통합 테스트를 기본으로 사용한다.
- Repository 테스트는 `@ActiveProfiles("test")`, `@Transactional` 조합을 기본으로 검토한다.
- `@DataJpaTest`는 기본 선택지가 아니며, 매핑 조사나 제한적 실험이 필요할 때만 예외적으로 사용한다.
- 외부 HTTP Client 테스트는 `@RestClientTest`와 `MockRestServiceServer` 기반 슬라이스 테스트를 우선 검토한다.
- Config 테스트는 기본적으로 `ApplicationContextRunner`로 bean 생성/조건부 등록/properties binding을 검증하는 방식을 우선 검토한다.
- Security, MVC, Filter Chain처럼 실제 애플리케이션 동작 결과까지 검증해야 하는 설정은 `@SpringBootTest` 기반 통합 테스트를 우선 검토한다.
- Parser, Mapper, Policy, Calculator처럼 순수 로직 중심 클래스는 Spring 컨텍스트 없이 unit test를 우선 검토한다.
- `*TestSupport`는 fixture, setup, 공통 assertion이 반복될 때 추출을 검토한다.
- 시나리오 테스트에서는 경로 주체가 `Device`인지, 조작 주체가 `Account`인지 구분해서 fixture를 만든다.
- red test는 "현재 실패할 수 있는 최종 기대 동작"을 적는 문서로 해석한다.

### 커밋 메시지
- 백엔드 형식: `[BE] type(scope): 설명 (Jira 티켓번호)`
- 프론트엔드 형식: `[FE] type(scope): 설명 (Jira 티켓번호)`
- Android 형식: `[ANDROID] type(scope): 설명 (Jira 티켓번호)`
- 공통 문서 형식: `[DOCS] type(scope): 설명 (Jira 티켓번호)`
- type 목록: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

## 도메인 용어 사전

> 코드에서 사용하는 클래스명, 메서드명, DB 컬럼명은 아래 영문 명칭을 우선 참고한다.

### 1. 사건 / 조직 / 권한
| 한글 | 영문 (코드용) | 설명 |
| :--- | :--- | :--- |
| 사건 | `Incident` | 실종 신고 1건 단위 |
| 실종자 운영 캐시 | `MissingPersonCache` / `MissingPerson` | 사건 진행 중 유지되는 운영 캐시 |
| 부대 | `Unit` | 조직 단위 |
| 팀 | `Team` | 부대 하위 운영 단위 |
| 사건 참여 | `IncidentMembership` | 사건 수준 접근/참여 관계 |
| 계정 | `Account` | 공통 계정 엔티티 |
| 지휘 계정 | `Account(type=COMMAND)` | 웹 지휘 기능 수행 계정 |
| 팀 계정 | `Account(type=TEAM)` | 팀 업무폰 운용 계정 |
| 순찰차 계정 | `Account(type=PATROL_CAR)` | 순찰차 업무폰 운용 계정 |
| 현장 지휘관 | `FieldCommander` | 사건당 N명 가능한 역할 |
| 소속 | `Affiliation` | `MISSING_TEAM`, `SUPPORT_UNIT`, `LOCAL_POLICE` |
| 역할 | `Role` | `MISSING_TEAM_COMMANDER`, `FIELD_COMMANDER`, `MEMBER` 등 |

### 2. 단말 / 인증 / 동기화
| 한글 | 영문 (코드용) | 설명 |
| :--- | :--- | :--- |
| 폴리폰 | `Device` | 현장 업무 단말 |
| 팀 업무폰 | `TeamDevice` | 팀 단위 운용 Device |
| 순찰차 업무폰 | `PatrolCarDevice` | 차량 단위 운용 Device |
| 단말 최신성 | `DeviceFreshness` | heartbeat/sync 기반 상태 |
| 동기화 이벤트 | `SyncEvent` | 동기화 기록 |
| 미전송 큐 | `Outbox` / `OutboxRow` | 서버 미전송 작업 큐 |
| 멱등 키 | `IdempotencyKey` | 중복 전송 방지 키 |
| 시계 보정 | `ClockOffset` | `clientTs`와 `serverTs` 차이 |

### 3. 수색 / 지도 / OP
| 한글 | 영문 (코드용) | 설명 |
| :--- | :--- | :--- |
| 지도 기준 범위 | `MapBoundary` | 사건별 기준 Polygon |
| 수색 구역 | `SearchArea` | 폴리곤 기반 구역 |
| 수색 구역 이력 | `SearchAreaHistory` | 구역 상태 변경 이력 |
| 수색 차수 | `OperationalPeriod` | 사건 내 OP 레이어 |
| OP 배정 | `OpAssignment` / `OperationalPeriodAssignment` | OP 수준 담당 배정 |
| 수색 세션 | `SearchSession` | 시작/일시정지/재개/종료 단위 |
| 수색 경로 | `SearchPath` | Device 주체 LineString 경로 |
| 경로 구간 | `PathSegment` | `VEHICLE`, `FOOT`, `UNKNOWN` 구간 |

### 4. 마커 / 인수인계 / AI
| 한글 | 영문 (코드용) | 설명 |
| :--- | :--- | :--- |
| 마커 | `Marker` | 현장 운영 Point 기록 |
| 단서 마커 | `ClueMarker` | 단서 위치 표시 |
| 실종자 발견 | `PersonFoundMarker` | 발견 위치 표시 |
| 지형 상태 마커 | `TerrainMarker` | 지형 상태 표시 |
| 지원 요청 마커 | `SupportRequestMarker` | 지원 요청 위치 기록 |
| 재확인 필요 마커 | `RecheckMarker` | 재확인 필요 위치 |
| 사진 | `Photo` / `MarkerPhoto` | 마커 첨부 이미지 |
| 인수인계 메모 | `HandoverMemo` | OP/경로/구역 단위 메모 |
| AI 수색 이력 요약 | `AiSummary` | OP 기반 기록 요약 |

### 5. 실시간 / 패키지 / 종료
| 한글 | 영문 (코드용) | 설명 |
| :--- | :--- | :--- |
| 실시간 이벤트 허브 | `EventHub` / `EventFanout` | 실시간 fanout |
| 이벤트 아웃박스 | `EventOutbox` | 도메인 이벤트 적재 저장소 |
| 상황판 스냅샷 | `BoardSnapshot` | 상황판 read model |
| 상황판 슬롯 | `BoardSlot` | shell mount 단위 |
| 오프라인 패키지 | `OfflinePackage` | 사건 메타+실종자+OP+타일 묶음 |
| 패키지 매니페스트 | `OfflinePackageManifest` | 다운로드 대상 계약 |
| 패키지 상태 | `OfflinePackageStatus` | 적재 진행 상태 |
| 사건 종료 | `IncidentClosed` / `IncidentTerminal` | terminal 종료 상태 |
| tombstone | `IncidentTombstone` | 종료 후 sanitized 요약 응답 |
| 파기 실행 | `PurgeRun` | purge orchestration 기록 |
| 위치정보 접근 기록 | `LocationAccessLog` | 내부 보존용 접근 로그 |
