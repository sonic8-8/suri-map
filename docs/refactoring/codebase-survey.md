# 코드베이스 기능 영역별 조사

2026-11-15에 develop `8edfec92`를 기준으로 조사했다. 사용자는 기능 하나를 Backend, Frontend, Android에서 함께 정리하고, 그 기능의 동작까지 확인한 뒤 다음 기능으로 넘어가기로 했다(2026-11-15 결정). 이 문서는 그 순서를 정할 근거를 모은다. 이번 조사에서 코드는 고치지 않았다.

기능 영역은 `docs/features/`의 기능 정의 문서 11개를 따른다. 어느 기능에도 속하지 않는 코드는 [공통](#공통) 영역에 모았다. 2026-11-15에 사용자가 [정리 순서 추천안](#정리-순서-추천안)대로 순서를 정했다.

## 조사 방법과 읽는 법

- 작업자가 영역을 다섯 묶음으로 나눠 하위 조사(읽기 전용)를 맡겼다. 하위 조사는 파일과 줄을 근거로 보고했다.
- 항목 끝에 "(직접 확인)"이 붙은 것은 작업자가 코드를 직접 열어 대조한 내용이다. 표시가 없는 항목은 하위 조사가 보고한 내용이다. 해당 영역을 정리하기 시작할 때 다시 확인한다.
- 파일 수와 줄 수는 빈 줄을 포함한 어림값이다.
- 2026년 6월 이전 코드는 임시 구현으로 본다. 그 시기 동작은 요구나 설계 의도의 근거로 쓰지 않았다.
- 경로는 저장소 루트 기준이다. Backend 운영 코드는 `backend/src/main/java/com/surimap/` 아래이므로 앞부분을 줄여 `B/`로 쓴다. Backend 테스트는 `BT/`, Frontend 소스는 `F/`(`frontend/src/`), Android 운영 코드는 `A/`(`android/app/src/main/java/com/surimap/`)로 쓴다.

### 읽기 어려운 곳의 판단 기준

"읽기 어려운 곳"은 `mattpocock-skills:codebase-design` 스킬의 기준으로 판단했다. 용어는 스킬의 영어 이름을 그대로 쓴다.

| 용어 | 뜻 |
|---|---|
| module | 모듈. interface와 implementation(구현)을 가진 단위다. 함수, 클래스, 패키지, 여러 플랫폼에 걸친 기능 조각이 모두 module이다. |
| interface | 인터페이스. 호출하는 쪽과 테스트가 module을 바르게 쓰려고 알아야 하는 모든 것이다. 타입 시그니처뿐 아니라 호출 순서, 오류 종류, 필요한 설정, 생성자 인자까지 포함한다. Java의 `interface` 키워드만 뜻하지 않는다. |
| depth | 깊이. interface 하나를 익혔을 때 쓸 수 있는 동작의 양이다. 작은 interface 뒤에 많은 동작이 있으면 deep(깊은) module, interface가 구현만큼 복잡하면 shallow(얕은) module이다. |
| seam | 그 자리를 고치지 않고도 동작을 바꿔 끼울 수 있는 위치다. module의 interface가 놓이는 자리다. |
| adapter | seam에서 interface를 채우는 구체 구현이다. 운영 구현과 테스트 대역이 모두 adapter다. adapter가 하나뿐이면 가정만 있는 seam(hypothetical seam)이고, 운영과 테스트처럼 둘 이상이면 실제로 쓰이는 seam(real seam)이다. Mockito mock도 테스트 adapter로 센다. |
| leverage | 호출하는 쪽이 depth에서 얻는 이득이다. 구현 하나가 여러 호출처와 테스트에 쓰인다. |
| locality | 유지보수하는 쪽이 depth에서 얻는 이득이다. 변경, 버그, 지식, 검증이 한곳에 모여 있어 한 번 고치면 모든 곳이 고쳐진다. |
| deletion test | module을 지운다고 가정하는 검사다. 지우면 복잡도가 사라지면 값을 넘기기만 하던(pass-through) module이다. 지우면 같은 복잡도가 여러 호출처에 다시 생기면 그 module은 제 몫을 한다. |

후보마다 아래 종류 중 하나를 붙였다.

| 종류 | 뜻 |
|---|---|
| shallow | interface가 구현만큼 복잡하다. deletion test 결과를 함께 적었다. |
| hypothetical seam | adapter가 하나뿐인 interface다. deletion test 결과를 함께 적었다. |
| 겹친 seam | adapter가 둘 이상이라 real seam이지만, 바로 아래에 같은 것을 바꿔 끼우는 다른 seam이 있다. 둘 중 하나로 합칠 후보다. 합칠 때 옮겨야 하는 테스트 adapter를 함께 적었다. |
| locality | 한 가지 지식이나 규칙이 여러 곳에 흩어져 있다. |
| interface가 넓다 | 호출하거나 만드는 쪽이 알아야 할 것이 필요보다 많다. 생성자가 여러 개이거나, 쓰지 않는 응답 필드나 인자가 interface에 남아 있다. |
| testing past the interface | interface를 거치지 않는 테스트다. 테스트가 module의 내부 상수나 상태에 기대므로, 구현을 바꾸면 테스트도 바뀐다. 스킬의 "interface가 곧 테스트 표면이다" 원칙에서 온 말이다. |
| 의존성을 직접 만든다 | module이 협력 객체를 생성자나 함수 안에서 직접 만든다. 그래서 테스트에서 그 협력 객체를 바꿔 끼울 seam이 없다. 스킬의 "의존성을 직접 만들지 말고 받는다" 원칙에서 온 말이다. |
| 미사용 | 운영 코드가 부르지 않는 module이다. 테스트에서만 쓰이면 그 사용처를 함께 적었다. deletion test를 하면 운영 복잡도는 그대로 사라진다. |
| 규칙 위반 | codebase-design 기준과 별개로 [Backend 규칙](../../backend/AGENTS.md)이나 [Frontend 규칙](../../frontend/AGENTS.md)에 어긋난다. 위치나 이름의 문제다. module 모양을 바꾸지 않아도 고칠 수 있다. |
| 이름 문제 | 이름이 실제 역할과 다르다. [이름 후보 문서](codebase-naming-candidates.md)에 등록했다. |

1회차 조사의 후보 중 이 기준으로 다시 보니 빠지는 것은 각 영역의 "빠진 후보"에 적었다. 대부분 "구현이 하나뿐인 인터페이스"로 적었던 후보다. 테스트 adapter가 있어 real seam으로 판단했다.

### 실행한 검증

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew test` | 통과했다. 테스트 클래스 224개, 테스트 1,469개이고 실패, 오류, 건너뜀이 모두 0이다(5분 29초). 건수는 `backend/build/test-results/test`의 XML에서 셌다. |
| `cd android && ./gradlew test` | 통과했다. debug와 release 단위 테스트가 각각 클래스 90개, 테스트 492개이고 실패, 오류, 건너뜀이 모두 0이다(9분 58초). |
| `cd frontend && npm run typecheck && npm run build` | 통과했다. `tsc -b`에서 오류가 없었다. `vite build`로 모듈 1,975개를 변환했다(1.02초). `index` JS 청크(1,629 kB)가 500 kB를 넘는다는 경고가 나왔다. 실패는 아니다. |
| `cd frontend && npm test` | 통과했다. `vitest run`에서 테스트 파일 63개, 테스트 381개가 모두 통과했다(9.51초). |

Frontend는 처음에 worktree에 `node_modules`가 없어 typecheck가 실패했다. 오케스트레이터가 `frontend/node_modules`를 기본 저장소의 `node_modules`로 연결한 뒤 다시 실행한 결과가 위 표다.

세 플랫폼 모두 실패하는 테스트가 없었다. 따라서 아래 "동작하지 않는 곳"은 테스트로는 드러나지 않는 연결 누락과 요구 차이다.

## 공통

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/config/`(SecurityConfig, GuardConfig, MyBatisConfig, GeometryConfig, ClockConfig 등) | 12파일, 약 760줄 |
| Backend | `B/global/`(auth, error, event, geometry, sse) | 33파일, 약 2,370줄 |
| Backend | `B/common/`(auth 29파일, health 2파일). guard 애너테이션과 `GuardInterceptor`가 여기 있다. | 31파일, 약 580줄 |
| Backend | `B/sync/idempotency/`(IdempotentResponseCache, IdempotentWriteAspect 등) | 18파일, 약 1,020줄 |
| Frontend | `F/app/`, `F/shared/`, `F/components/`, `F/test/` | 27, 61, 3, 2파일, 합계 약 1만 줄 |
| Android | `A/core/`(network, database, map 등. 기능별 저장소도 섞여 있다) | 70파일, 약 8,300줄 |
| Android | `A/ui/`. `SuriMapApp.kt` 한 파일이 4,366줄이다. | 14파일, 약 5,660줄 |
| 공용 입력 | `test-fixtures/common-fixtures.json`과 로더 | Backend 로더 6곳, Android 로더 1곳 |

Backend 최상위 패키지 중 `api`, `app`, `client`, `config`, `domain`, `global` 밖에 있는 패키지와 main 파일 수는 다음과 같다. 모두 [Backend 패키지 설계 원칙](../../backend/AGENTS.md#패키지-설계-원칙)의 신규 최상위 패키지 목록 밖이다.

| account | board | common | dutyshift | external | handover | incident | maparea | offlinepackage | opcomparison | operationalperiod | policephone | retention | summary | sync |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 7 | 24 | 31 | 2 | 12 | 8 | 70 | 47 | 29 | 28 | 23 | 16 | 28 | 5 | 30 |

### 읽기 어려운 곳

- **locality**: 오류를 HTTP 응답으로 바꾸는 규칙이 15개 `@RestControllerAdvice`에 흩어져 있다(직접 확인). 방식도 `BusinessException`과 `ErrorCode`, `GuardException`과 `GuardExceptionHandler`, 도메인별 `*ApiException` 세 가지다. 오류 코드 하나를 바꾸려면 어느 처리기가 그 예외를 잡는지부터 찾아야 한다. 도메인 처리기를 지우면 그 변환 규칙은 `GlobalExceptionHandler`와 `ErrorCode`로 옮겨 간다. 복잡도가 사라지지는 않지만 한곳에 모인다. 적용 대상이 없는 처리기(`PathExceptionHandler`, `SearchPathExceptionHandler`, `SearchAreaBoundaryAlertExceptionHandler`, `IdempotencyExceptionHandler`, `TileExceptionHandler`, `GuardExceptionHandler`)는 각자 자기 예외 타입만 잡으므로 지금 다른 응답을 바꾸지는 않는다(직접 확인).
- **locality**: 멱등성 처리 규칙이 공용 module과 세 Service에 나뉘어 있다. `IdempotentResponseCache`는 deep module이다. interface가 작다. api와 app 서비스 9개 이상이 써서 leverage가 크다. `OperationalPeriodApiService`, `HandoverMemoApiService`, `SearchAreaApiService`도 `ObjectProvider<IdempotentResponseCache>`로 이 module을 받는다. 빈이 있으면 공용 `replayOrRun`이 호출된다(`OperationalPeriodApiService.java:165`, `HandoverMemoApiService.java:174`, `SearchAreaApiService.java:1454`). 공용 캐시가 `@Service`이므로 운영에서는 공용 경로를 쓴다(직접 확인). 문제는 두 가지다.
  - 빈이 없을 때를 위한 메모리 Map 대체 경로를 세 Service가 각자 갖고 있다.
  - 요청 해시를 `String.valueOf(request)`로 각자 만든다(`HandoverMemoApiService.java:206` 등, 직접 확인). 구조화된 JSON 해시 규칙도 지키지 않는다.

  deletion test: 메모리 대체 경로를 지우면 운영 복잡도는 사라진다. 빈 없이 이 Service를 만드는 테스트가 있다면 그 테스트가 공용 캐시를 받도록 바꿔야 한다(테스트 사용처는 확인하지 않았다). 해시 규칙은 지울 수 없으므로 공용 module 쪽으로 모은다.
- **shallow**: Frontend `apiClient` 객체(`F/shared/api/client.ts:146-152`)는 메서드 5개가 모두 `realApiClient`의 같은 메서드를 그대로 부른다(직접 확인). deletion test: `export const apiClient = realApiClient`로 바꿔도 동작이 같다. 복잡도가 사라지므로 지울 후보다. 테스트는 `vi.spyOn(apiClient, 'get')`을 쓴다. `createApiClient`가 돌려주는 값도 일반 객체라서 같은 방식으로 감시할 수 있을 것으로 본다(추측, 테스트로 확인하지 않음).
- **locality**: Frontend에 오류 타입이 `ApiHttpError`와 `ApiError` 두 개다. `ApiError`는 `ApiHttpError`를 상속한다. `apiRequest`를 거친 오류만 `ApiError`로 바뀐다(`client.ts:31,47,154-164`, 직접 확인). 운영 코드에서 `instanceof ApiHttpError`로 잡는 곳은 12개 파일이다(`AppProviders.tsx:11`, `boardReadRecovery.ts:7`, `SearchMapCanvas.tsx:1430` 등). `instanceof ApiError`로 잡는 곳은 4개 파일이다(`AreaEditPage.tsx:846`, `OperationalPeriodReviewWorkspace.tsx:1968`, `IncidentListPage.tsx:127`, `IncidentClosePage.tsx:421`). 뒤의 네 파일에서는 `apiRequest`를 거친 오류만 잡힌다. 그래서 이 화면을 고치는 사람은 API를 어느 함수로 불렀는지까지 알아야 한다.
- **locality**: `F/shared/model`에 상황판과 수색 구역 정책이 들어 있고 45개 파일이 import한다. 정책을 바꾸려면 shared와 각 feature를 같이 봐야 한다. "shared에 특정 feature 정책을 넣지 않는다"는 Frontend 규칙과도 맞지 않는다.
- **locality**: Android `X-Client-Channel: APP`을 네 곳에서 따로 붙인다(`SuriMapApiClient.kt:78`, `MapLibreTileHttp.kt:55`, `MapTileClient.kt:136`, `RoomOfflinePackageWorkerInstaller.kt:151`).
- **의존성을 직접 만든다**: `SuriMapApp.kt`는 `SuriMapApiClient`를 약 35번 직접 만든다. 그래서 화면 단위 테스트에서 네트워크를 바꿔 끼울 seam이 없다.
- **locality**: Backend가 `common-fixtures.json` 경로를 찾는 코드가 6곳에 따로 있다(`IncidentSeedFixtureLoader`, `IncidentLifecycleFixtureLoader`, `CommonFixtureJson`, `BoardApiSseConvergenceHarnessRedTest`, `Sc02ToSc12BoardConvergenceHarnessRunner`, `Sc03PackageTileHarnessRunner`). 로더 하나로 모으면 fixture 위치를 바꿀 때 한 곳만 고친다.
- **규칙 위반**: `B/global/event/SearchPathEventPublisher.java:3-7`이 `domain/path`를 import한다(직접 확인). 공통 패키지가 도메인 패키지에 기대는 방향이다. deletion test: 이 module을 지우면 경로 이벤트 payload 조립이 `SearchPathService`와 `AppSearchPathService` 두 곳에 다시 생긴다. 제 몫을 하므로 module은 남기고 위치만 옮긴다. `MarkerEventPublisher`, `MarkerEventPayload`, `MarkerEventIds`도 같다.
- **규칙 위반**: `CapturingEventPublisher`는 테스트 adapter인데 main에 있다. `EventPublisher` seam은 `DatabaseEventPublisher`와 이 클래스, 두 adapter가 있는 real seam이라서 interface는 남긴다. 문제는 위치뿐이다. `PolicePhoneHeartbeatConfig`의 `@ConditionalOnMissingBean` 대체 빈 등록은 `DatabaseEventPublisher`가 항상 `@Component`라서 운영에서 만들어지지 않는다(직접 확인). deletion test: 대체 빈 등록을 지우면 복잡도가 그대로 사라진다.
  - 이 판단은 기존 합의와 다르다. [이름 후보 문서의 마커와 알림 절](codebase-naming-candidates.md#마커알림--남은-확인)에 있는 Q18 항목은 "`PolicePhoneHeartbeatConfig`의 대체 등록이 있어 테스트 디렉터리로 바로 옮기거나 삭제하지 않는다"고 정했다. 대체 등록이 main에 있다는 사실은 지금도 맞다. 그러나 운영에서는 그 등록이 동작하지 않는다. 그래서 대체 등록을 지우고 `CapturingEventPublisher`를 테스트로 옮길지는 사용자가 다시 정한다. Q18 항목은 그 결정 뒤에 고친다.
- **규칙 위반**: Service가 Controller 패키지의 DTO를 import하는 곳이 7곳이다(`OperationalPeriodApiService`, `SearchHistorySummaryApiService`, `HandoverTimelineApiService`, `HandoverMemoApiService`, `SearchAreaApiService`, `DutyShiftQueryService`, `OpComparisonApiService`, 직접 확인). [DTO / Layer](../../backend/AGENTS.md#dto--layer) 규칙 위반이다.
- **규칙 위반**: 최상위 패키지 15개가 패키지 원칙 밖이다(위 표).
- 빠진 후보: 1회차의 "`SuriMapAuthenticationResolver`는 구현이 하나뿐이라 불필요한 계층"은 뺐다. 운영 adapter는 `SecurityContextSuriMapAuthenticationResolver` 하나지만, `MarkerControllerTest:106` 등이 `@MockitoBean`으로 테스트 adapter를 끼우므로 real seam이다(직접 확인). 남는 문제는 같은 이름 `SuriMapAuthentication`의 class(`B/common/auth`)와 record(`B/global/auth`)가 함께 있다는 점이다. 호출하는 쪽이 어느 타입을 받는지 알아야 해서 locality가 떨어진다. 이 내용은 [인증과 접근 권한](#인증과-접근-권한)에 적었다.

### 동작하지 않거나 연결되지 않은 곳

- **미사용**: `@IdempotentWrite`는 운영 코드 어디에도 붙어 있지 않다. 그래서 `IdempotentWriteAspect`는 운영에서 한 번도 동작하지 않는다(직접 확인).
- **미사용**: `@RequireDevice`, `@RequireDeviceRegistered`, `@RequireDeviceAssigned`와 `Device*Exception` 3개는 `GuardInterceptor`의 분기 말고는 쓰는 곳이 없다. 운영 코드와 테스트 모두에서 붙인 곳이 없다(직접 확인).
- `@RequireOpenIncident`와 `@RequireCurrentOp`는 애너테이션과 Aspect만 있다. 운영 Controller나 Service에 붙은 곳은 없다(직접 확인). 사건 종료 후 쓰기 차단과 현재 수색 차수 검사는 각 Service가 따로 하거나 하지 않는다. 지울지, 붙여서 locality를 높일지는 영역별로 정한다.
- **미사용**: Frontend `F/features/situationBoard/presentation/components/map/MapControls.tsx`는 import하는 곳이 없다. 실제로는 `F/shared/ui/mapControls`가 쓰인다. `F/components/`의 3개 파일과 `F/test/sc11...HarnessRunner.ts`는 테스트에서만 쓰인다.
- **미사용**: Android `PoliBottomSheet`(`A/ui/components/PoliComponents.kt:403`)는 호출하는 곳이 없다. `A/feature/showcase/ShowcaseScreen.kt`(617줄)는 고정 데이터로 UI 컴포넌트를 보여 주는 화면이다. `SURI_MAP_DEBUG_SHOWCASE`가 release에서 false로 고정되어 release에서는 표시되지 않지만, 코드는 함께 컴파일된다.

### 보존할 동작

- 오류 응답 기본형 `{"error":"<code>"}`, 인증 실패 401 `unauthorized`(`SecurityConfig.java:66`), SSE 재조회가 필요할 때 409 `gone_refetch_required`
- CORS 허용 헤더 `X-Client-Channel`, `X-PolicePhone-Id`, `Idempotency-Key`, `Last-Event-ID`(`SecurityConfig.java:104-113`)
- guard 검사 순서. 채널, 역할, 사건 순서로 검사하고 WEB이면 업무폰 검사를 건너뛴다(`GuardInterceptor.java:85-127`).
- Frontend API client는 `X-Client-Channel` 기본값 `WEB`, 오류 본문이 없을 때 `http_{status}` 코드, `Retry-After` 보존을 유지한다.
- 공통 응답 wrapper는 두지 않는다.

### 정리 규모와 의존

- `common`을 `global`로 옮기는 일은 중간 규모다. `com.surimap.common`을 main 54개 파일이 185번 import한다. 동작은 바뀌지 않으므로 기능 정리 전에 import만 바꾸는 이동으로 한 번에 끝낼 수 있다.
- 오류 처리를 `BusinessException`과 `ErrorCode`로 모으는 일은 크다. 각 기능을 정리할 때 함께 옮기되, `GuardException`을 `ErrorCode`로 합치는 일은 모든 Controller가 걸리므로 공통에서 먼저 정해야 한다.
- 미사용 가드(`@IdempotentWrite`, `@RequireDevice*` 3개)는 지우면 끝나는 작은 일이다.
- 세 Service의 메모리 대체 경로와 따로 만든 해시 규칙을 걷어 내는 일은 중간 규모다. 공용 `IdempotentResponseCache`는 이미 연결되어 있으므로 새로 연결할 필요는 없다. 해당 기능(수색 차수, 인수인계 메모, 수색 구역)을 정리할 때 함께 한다.
- Frontend `shared/model` 정리와 Android `SuriMapApp.kt` 분리는 크다. 기능별로 자기 부분을 떼어 내는 방식이 맞다.
- 검증: 세 플랫폼의 전체 테스트가 통과했다. 이 영역에서 실패한 테스트는 없다.

## 인증과 접근 권한

[인증과 접근 권한 기능 정의 문서](../features/authentication.md)의 영역이다. 로그인, 채널, 사건 접근, 업무폰 최신성, FCM 토큰을 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/account`, `B/common/auth`, `B/policephone`, `B/global/auth`, `B/app/{controller,service}/policephone`. 핵심은 `common/auth/guard/GuardInterceptor.java`(181줄), `policephone/PolicePhonePersistenceService.java`(232줄)다. | 약 65파일, 2,500줄 |
| Backend 테스트 | `BT/account`, `BT/auth`, `BT/policephone` | 약 31파일, 3,400줄 |
| Frontend | `F/features/login`(5파일, 그중 `login.ts` 401줄), `F/shared/api/client.ts` | 약 660줄 |
| Android | `A/feature/bootstrap`(5파일, 약 1,090줄), `A/core/fcm`(9파일, 약 830줄), `A/core/auth`, `A/core/network/AuthPhoneApiClient.kt` | 약 2,000줄 |

### 읽기 어려운 곳

- **locality**: 인증 정보를 나타내는 타입이 `SuriMapAuthentication` 하나의 이름으로 class(`B/common/auth`)와 record(`B/global/auth`) 두 개다. record 쪽은 마커와 사진 22개 파일만 쓴다. 둘은 `SecurityContextSuriMapAuthenticationResolver:16-28`에서 이어진다. 이 파일에서는 두 타입을 구분하려고 패키지 전체 이름을 쓴다. 인증 정보의 필드나 규칙을 바꾸면 두 타입을 함께 고쳐야 한다.
- **locality**: `currentAuthentication()`이 `FcmTokenController:46`, `PolicePhoneHeartbeatController:53`, `IncidentReadController:59`, `RecordLocationAccessAspect:47`에 복사되어 있다. 인증 정보를 꺼내는 규칙이 바뀌면 네 곳을 고쳐야 한다.
- **locality**: 마커와 사진의 쓰기 요청에서 같은 검사가 guard를 포함해 세 곳에 있다(직접 확인).
  - `B/global/auth/MarkerAuthenticationResolver`를 운영에서 부르는 곳은 `MarkerController`와 `AppMarkerController` 두 곳이다. 이 module에서는 채널, 업무폰 ID 존재, `Idempotency-Key` 존재, `Authorization` 존재가 검사된다. 마지막으로 헤더의 업무폰 ID와 인증 정보의 업무폰 ID가 비교된다.
  - 같은 엔드포인트에 `@RequireChannel`, `@RequirePolicePhone`, `@RequirePolicePhoneRegistered`가 붙어 있다(`AppMarkerController:47-50,70-73,93-96`). 그래서 채널과 업무폰 ID 존재 검사는 guard에서도 이뤄진다.
  - 사진의 `B/app/controller/photo/PhotoRequestContextResolver.java:20`에는 같은 검사가 따로 구현되어 있다. 이 파일은 `MarkerAuthenticationResolver`를 부르지 않고 `SuriMapAuthenticationResolver`를 직접 부른다.

  검사 규칙을 바꾸면 guard, `MarkerAuthenticationResolver`, 사진 쪽을 함께 고쳐야 한다. 겹친 검사는 오류 코드가 guard와 다를 수 있으므로, 덜어 내기 전에 응답을 대조한다.
- **규칙 위반**: 최상위 패키지 `account`, `common`, `policephone`이 패키지 원칙 밖이다. main에 있는 테스트 adapter `InMemoryPolicePhoneFixtureStore`(473줄)와 `PolicePhoneFixtures`는 테스트 쪽으로 옮길 대상이다.
- 빠진 후보: 1회차의 "`PolicePhonePersistenceService` 한 클래스가 포트 4개를 구현한다"는 seam 문제로 보지 않는다. 포트 4개(`PolicePhoneHeartbeatRecorder`, `PolicePhoneValidationPort`, `PolicePhoneFreshnessQuery`, `FcmTokenQuery`)는 모두 `InMemoryPolicePhoneFixtureStore`라는 테스트 adapter가 있어 real seam이다(직접 확인). 호출하는 쪽은 자기 포트 하나만 알면 되므로 interface도 작다. 남는 것은 **이름 문제**다. 이름은 저장 서비스인데 heartbeat 기록, 토큰 저장, 배정 검사까지 맡는다. 이름 후보 문서의 기존 `PolicePhonePersistenceService.encryptToken` 항목에 덧붙였다.

### 동작하지 않거나 확인되지 않은 곳

- 인증과 접근 권한 기능 정의 문서에 적힌 차이와 대조한 결과다. 채널과 업무폰 ID를 헤더에서 받는 것, guard 검사 순서와 `MEMBER` 역할만 보는 검사, FCM 토큰 등록에 배정 검사가 없는 것, heartbeat 저장과 이벤트 발행이 다른 트랜잭션인 것(`AppPolicePhoneHeartbeatService`에 `@Transactional`이 없다), 토큰 앞에 `cipher:`만 붙이는 것(`PolicePhonePersistenceService.java:220`), 로그아웃 때 토큰을 폐기하는 메서드가 연결되지 않은 것, 503 `authentication_unavailable` 분리는 지금도 인증과 접근 권한 기능 정의 문서와 같다.
- `MarkerAuthenticationResolver`의 업무폰 ID 비교는 서명된 단말 결합 검사가 아니다(직접 확인). `B/account/security/OidcBearerAuthenticationFilter.java:48`에서 `X-PolicePhone-Id` 헤더가 converter로 넘어간다. `B/account/security/KeycloakJwtAuthenticationConverter.java:35,47`에서는 그 헤더 값으로 인증 정보의 업무폰 ID를 만든다. 따라서 이 비교는 같은 요청 헤더에서 나온 두 값을 맞춰 보는 것이다. JWT claim으로 업무폰을 결합하지 않는다는 점은 위의 기존 차이(채널과 업무폰 ID를 헤더에서 받는 것)와 같다.
- 인증과 접근 권한 기능 정의 문서에 없는 문제다. 업무폰 최신성이 첫 heartbeat 뒤로 갱신되지 않는 구조다(직접 확인). Android는 부팅 때 `{"clientTs":...,"sequence":1}` 하나만 보낸다(`A/feature/bootstrap/data/AuthBootstrapCoordinator.kt:152`). 주기적으로 보내는 코드는 없다. `AuthPhoneApiClient.heartbeat`(`:40`)는 테스트에서만 쓰인다. 받은 순번이 저장된 순번 이하이면 서버에서 갱신 없이 200이 응답된다(`PolicePhonePersistenceService.java:64`). 따라서 마지막 수신 시각이 첫 수신에 머물러, 5분 뒤에는 `LOST`로 보일 것으로 본다. 재부팅 뒤에도 순번 1을 보내므로, 이미 더 높은 순번이 저장되어 있으면 부팅 요청부터 무시될 수 있다. 실기기와 DB에서는 확인하지 않았다.
- `Channel.INTERNAL`은 파싱되지만 main에서 이 enum 값을 직접 쓰는 곳이 없다. mock-112 웹훅은 `permitAll` 뒤 서명 비교로 막는다(`SecurityConfig.java:55`, `Mock112WebhookController.java:43-46`).
- **미사용**: `account/repository/AccountLoginMapper`(테스트에서만 사용)
- 검증: 관련 Backend 테스트와 Android 테스트가 모두 통과했다. 낮은 순번을 무시하는지는 Service 테스트(`AppPolicePhoneHeartbeatServiceTest:27`, 순번 3 다음 2)와 실제 DB 테스트(`PolicePhonePersistenceIntegrationTest:140`, 순번 5 다음 4)에서 확인한다(직접 확인). 그러나 앱이 주기적으로 heartbeat를 보내는지, 재부팅 뒤 순번이 이어지는지는 테스트 범위 밖이다. 그래서 heartbeat 문제는 테스트로 드러나지 않는다.

### 보존할 동작

- `POST /api/fcm/tokens`(`appInstanceId`, `token`), `POST /api/police-phones/{id}/heartbeat`(`clientTs`, `sequence`, `lastSyncAt`)
- 헤더 `Authorization`, `X-Client-Channel`, `X-PolicePhone-Id`
- 오류 코드 `unauthorized`, `authentication_unavailable`, `police_phone_*`. Android가 `AuthBootstrapCoordinator.kt:179-182`에서 해석한다.
- JWT claim `accountId`, `accountType`, `organizationType`
- 이벤트 `POLICE_PHONE_HEARTBEAT_UPDATED`
- 테이블 `account`, `police_phone`, `fcm_token`
- fixture `accountAliases`, `boardAssembly`

### 정리 규모와 의존

큼. guard 애너테이션을 29개 Controller가 쓴다. 모든 영역이 이 영역에 기댄다. `common` 이동을 먼저 끝내면 남는 일은 인증 타입 하나로 합치기, `currentAuthentication()` 모으기, 마커와 사진 쪽에서 guard와 겹치는 검사 덜기, heartbeat 주기 전송 연결이다. heartbeat 주기는 요구를 먼저 정해야 한다.

## 사건 생명주기

[사건 생명주기 기능 정의 문서](../features/incident-lifecycle.md)의 영역이다. 사건 가져오기, 배정, 종료를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/incident`(`IncidentImportService` 347줄, `IncidentCloseService` 257줄, `IncidentReadRows` 322줄), `B/external`(mock-112 연동) | 82파일, 약 4,340줄 |
| Backend 테스트 | `BT/incident` | 약 24파일, 4,200줄 |
| Frontend | `F/features/incident`, `incidents`, `incidentClose`, `incidentImport` | 약 2,950줄 |
| Android | `A/core/incident/IncidentReadRepository.kt`(47줄), `A/feature/incidents`(6파일) | 약 2,100줄 |

### 읽기 어려운 곳

- **겹친 seam**: `IncidentEventPublisher` interface는 운영 adapter `EventHubIncidentEventPublisher`(`@Component`)와 Mockito 테스트 adapter 5곳이 있는 real seam이다(`IncidentImportApiContractTest:103`, `IncidentCloseCommandContractTest:80`, `IncidentAssignmentImportContractTest:63`, `IncidentHandoverSupportAssignmentContractTest:105`, `Mock112WebhookIncidentCloseIntegrationTest:66`의 `@MockitoBean`, 직접 확인). 그런데 그 바로 아래의 `EventPublisher` seam(운영 `DatabaseEventPublisher`, 테스트 `CapturingEventPublisher`)도 같은 발행을 바꿔 끼운다. deletion test: `IncidentEventPublisher` interface를 지우면 운영 쪽에서는 seam 하나가 사라진다. 이벤트를 조립하는 `EventHubIncidentEventPublisher`의 내용은 남는다. 대신 테스트 5곳의 주입과 검증을 `CapturingEventPublisher`로 옮겨야 한다. 복잡도가 사라지지 않고 테스트 쪽으로 옮겨 가므로, 합칠지는 테스트 5곳을 바꾸는 비용과 함께 정한다.
- **미사용**: `BlockingIncidentEventPublisher`와 `IncidentEventPublisherFallbackConfig`는 빈이 없을 때만 등록되는 대체 구현이다. `EventHubIncidentEventPublisher`가 항상 빈이라서 운영에서 만들어지지 않는다. 테스트에서도 쓰지 않는다(직접 확인). deletion test: 지우면 복잡도가 그대로 사라진다.
- **locality**: 권한 검사 위치가 엔드포인트마다 다르다. 가져오기 권한은 애너테이션 없이 Service 안의 `IncidentImportAuthorizer`에서 검사된다. 종료 권한은 애너테이션(`IncidentCloseController:33-34`)과 Service(`IncidentCloseService:175-186`)에서 두 번 검사된다. 사건 권한 규칙을 바꾸면 엔드포인트마다 어디를 고쳐야 하는지 따로 알아야 한다.
- **locality**: Frontend API 호출부가 두 벌이다. `F/features/incident/api`와 각 기능 폴더의 `data/*.ts`다. `data/*.ts` 쪽은 대부분 미사용이다(아래).
- **규칙 위반**: `incident/controller`, `incident/domain/*Record`, `IncidentExceptionHandler`가 패키지 원칙 밖이다. 응답 JSON을 문자열로 직접 만든다(`IncidentCloseService:235`, `Mock112WebhookService:181`).
- **이름 문제**: `IncidentAssignmentView`는 실제로 마커 알림 수신자를 조회하는 데 쓰인다. 사건을 종료할 때도 가져오기 이름의 `insertImportIdempotencyRecord`로 기록된다. 두 이름 모두 이름 후보 문서에 등록했다.
- 빠진 후보: 없음.

### 동작하지 않거나 확인되지 않은 곳

- 사건 생명주기 기능 정의 문서의 차이는 지금도 맞다. 웹훅 `INCIDENT_READY`가 바로 가져오기를 실행한다. 외부 `markImported`를 트랜잭션 안에서 호출한다. 활성 배정이 하나라도 있으면 접근 검사를 통과한다(`IncidentAssignmentAccessGuard:25`). `SUPPORT_ASSIGNMENT` 알림은 신규 배정 여부를 보지 않는다. 배정 FCM을 트랜잭션 안에서 직접 호출한다(`IncidentAssignmentImportService:90`). 종료 때 `missing_person`을 지우지만 파기 조율자는 부르지 않는다(`IncidentCloseService:114,122`).
- **미사용**: `F/features/incidentImport` 전체(`IncidentImportModal.tsx` 등)를 바깥에서 import하는 곳이 없다(직접 확인). `F/features/incidentClose/data/closeIncident.ts`, `F/features/incidents/data/getIncidents.ts`도 쓰이지 않는다. 공개 `POST /api/incidents/import`를 호출하는 화면이 없다. `IncidentListPage.test.tsx:76`에서는 수동 가져오기 버튼이 없는지 확인한다. 사건은 mock-112 웹훅으로만 들어온다는 공통 원칙과 맞으므로, 남은 화면 코드를 지울지 API를 남길지 정하면 된다.
- **미사용**: `MissingPersonQueryService`는 테스트에서만 쓰인다.
- 검증: 관련 Backend 테스트와 Frontend 테스트가 통과했다. 실패한 테스트는 없다.

### 보존할 동작

- `GET /api/incidents?status`(Android `IncidentReadRepository.kt:39`가 사용), `GET /api/incidents/{id}`
- `POST /api/incidents/import` 응답 `id`, `incidentId`, `status`, `version`, `assignmentAccountIds`
- `POST /api/incidents/{id}/close` 요청 `closeReason`, `confirmPersonalDataRemoval`, 응답의 `closedAt`, `writeDisabledReason`
- `POST /api/internal/mock-112/events`, `X-Mock112-Signature`, 202 응답
- 이벤트 `INCIDENT_CREATED`, `INCIDENT_ASSIGNMENT_CHANGED`, `INCIDENT_CLOSED`
- 테이블 `incident`, `missing_person`, `incident_assignment`, `idempotency_record`, `mock112_webhook_event`
- fixture `incidentSeed`, `mock112SourceContract`

### 정리 규모와 의존

중간에서 큼. 미사용 코드 삭제는 작다. 겹친 seam 합치기는 테스트 5곳을 옮겨야 해서 중간이다. 권한 검사 위치 통일은 인증 영역의 결정에 기댄다. 종료 뒤 무엇을 지울지는 데이터 파기 연결 방식을 먼저 정해야 범위가 정해진다. 배정과 종료 FCM은 이벤트 전달 영역과 함께 본다.

## 데이터 보존과 파기

[데이터 보존과 파기 기능 정의 문서](../features/data-retention.md)의 영역이다. 사건 데이터 파기, 위치 조회 감사, 운영 로그를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/retention/purge`(파기 조율, 위치 조회 감사, 운영 로그가 한 패키지에 있다), `B/api/service/photo/MarkerPhotoPurgeHook*`, `B/offlinepackage/purge` | 약 33파일, 1,200줄 |
| Backend 테스트 | `BT/retention/purge`, `BT/harness/sc12/Sc12IncidentCloseDataPurgeIntegrationTest`(605줄) | 약 12파일, 1,430줄 |
| Frontend | `INCIDENT_PURGED` 처리만 있다(`F/app/board/useIncidentBoardEvents.ts:51`, `useIncidentMarkerNotifications.ts:42`). | - |
| Android | `INCIDENT_PURGED` 처리가 없다. 단말 파기는 [오프라인 동기화](#오프라인-동기화)에 있다. | - |

### 읽기 어려운 곳

- **shallow**: 오프라인 패키지 파기는 `PurgeHook` → `PackagePurgeHookAdapter` → `PackagePurgeHook` → `OfflinePackagePurgeService` → `OfflinePackageRepository.purgeIncidentPackage` 순서로 넘어간다(직접 확인). `PackagePurgeHookAdapter`는 요청의 값 4개를 그대로 넘긴다. `OfflinePackagePurgeService`는 Repository를 한 번 부른 뒤 결과를 `PurgeHookResult.succeeded(count, 0)`로 감싼다. 넘겨받은 `purgeRunId`, `closedAt`, `purgeDeadlineTs`는 쓰지도 않는다. `PackagePurgeHook`은 adapter가 하나뿐인 hypothetical seam이다. deletion test: `PackagePurgeHook`과 `PackagePurgeHookAdapter`를 지우고 `OfflinePackagePurgeService`가 `PurgeHook`을 직접 구현하면 복잡도가 사라진다. 남는 것은 `@Transactional` 하나다.
- **locality**: 한 패키지에 성격이 다른 세 기능(파기 조율, 위치 조회 감사, 운영 로그)이 함께 있다. 위치 조회 감사를 고치는 사람도 파기 코드를 같이 보게 된다.
- **규칙 위반**: 같은 파기 실행을 `IncidentDataPurgeRun`, `PurgeRunRecord`, `PurgeHookStepRecord`로 나눠 표현한다. 저장 단계별로 나누지 말라는 원칙과 맞지 않는다. main에 있는 테스트 adapter `InMemoryIncidentDataPurgeStore`는 테스트 쪽으로 옮길 대상이다.
- 빠진 후보: 1회차의 "저장소가 `PurgeRunRepository`, `IncidentDataPurgeStore`, `InMemoryIncidentDataPurgeStore` 세 겹"은 seam 문제로 보지 않는다. `IncidentDataPurgeStore`는 운영 adapter(`PurgeRunRepository`)와 테스트 adapter(`InMemoryIncidentDataPurgeStore`, `PurgeCoordinatorRedTest` 등이 사용)가 있는 real seam이다(직접 확인). `PurgeHook`도 운영 adapter 두 개(`PackagePurgeHookAdapter`, `MarkerPhotoPurgeHookAdapter`)와 테스트 adapter(`MockPurgeHookRegistry`)가 있는 real seam이다. 남는 것은 테스트 adapter가 main에 있다는 위치 문제뿐이다.

### 동작하지 않거나 확인되지 않은 곳

- 사건 종료 뒤 파기가 운영에서 실행되지 않는다(직접 확인). `IncidentClosedPurgeHandler`는 `DomainEventConsumer`를 구현하지 않는다. 같은 종료 이벤트를 받는 `IncidentClosedFcmDispatchConsumer`는 구현한다. `PurgeCoordinator.purgeIncident()`를 운영에서 부르는 곳이 없고, 테스트(`Sc12...:217`, `PurgeCoordinatorRedTest:52`)만 부른다. 데이터 보존과 파기 기능 정의 문서의 차이와 같다.
- 데이터 보존과 파기 기능 정의 문서에 적힌 것보다 위험이 크다(직접 확인). 운영 빈으로 등록된 파기 hook은 `PackagePurgeHookAdapter`(`@Component`) 하나뿐이다. `MarkerPhotoPurgeHookAdapter`에는 빈 애너테이션이 없다. `MarkerPhotoPurgeHook`을 구현한 운영 클래스도 없다. 지금 `purgeIncident()`를 연결하면 패키지만 지우고도 COMPLETED 상태와 `INCIDENT_PURGED`를 발행할 수 있다(`PurgeCoordinator.java:78-113`, 하위 조사 결과).
- 시연용 보관 정책이 고정되어 있다(`IncidentClosedPurgeHandler.java:20`). 운영 로그는 아무것도 하지 않는 `NoopOperationalLogSink`다.
- 위치 조회는 메서드가 반환된 뒤에 AOP로 기록된다. 기록이 실패하면 그 오류가 그대로 전파된다. 보관 기한은 6개월이다. 데이터 보존과 파기 기능 정의 문서와 같다. 다만 `@RecordLocationAccess`가 붙은 곳은 데이터 보존과 파기 기능 정의 문서에 적힌 것보다 많다(`IncidentBoardController:29`, `SearchPathBoardController:34,46`, `MarkerController:49`, `AppMapRevisionController:30`). 데이터 보존과 파기 기능 정의 문서를 고칠 후보다.
- `GET /api/search-paths`에는 `@RecordLocationAccess`가 없다. [수색 경로](#수색-경로)에 적었다.
- **미사용**: `RetentionPurgeStatus`, `RetentionPurgeStatusSnapshot`(호출 0건). Frontend의 `INCIDENT_PURGED` 분기는 서버가 이 이벤트를 발행하지 않으므로 지금은 닿지 않는다.
- 검증: 관련 Backend 테스트가 통과했다. 테스트에서 Handler와 Coordinator를 직접 부르므로, 운영 연결 누락은 테스트로 드러나지 않는다.

### 보존할 동작

- 이벤트 `INCIDENT_PURGED`
- 테이블 `incident_data_purge`, `incident_data_purge_hook_step`, `location_data_access_audit`
- `accessPurpose` 값 `BOARD_VIEW`, `MARKER_READ`, `MAP_REVISION_READ`
- 상황판 수집기가 파기 저장소를 읽는다(`DefaultIncidentBoardSourceRowCollector.java:879`).

### 정리 규모와 의존

중간. 코드는 작지만 연결 방식을 먼저 정해야 한다. 어떤 hook이 필수인지, 사진 파기를 어떻게 구현할지, 종료 직후 실행할지 보관 기간 뒤에 실행할지가 사용자 결정이다. 패키지 파기의 shallow 단계 정리는 결정과 무관하게 할 수 있다. 사건 생명주기(종료 이벤트), 마커와 사진(사진 삭제), 오프라인 패키지, 상황판에 걸친다.

## 이벤트 전달

[이벤트 전달 기능 정의 문서](../features/event-delivery.md)의 영역이다. SSE와 FCM의 전달, 재연결, 실패 복구, 사건 종료 처리를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/global/event`(13파일, 약 770줄), `B/global/sse`(11파일, 약 1,280줄), `B/api/{controller,service}/sse`(3파일, 318줄), `B/client/fcm`(6파일, 350줄), `B/config/ServerSentEventConfig` | 약 34파일, 2,780줄 |
| Backend | 흩어진 발행자 `B/incident/event`(8파일), `B/operationalperiod/event`(4파일) | 12파일 |
| Backend 테스트 | `BT/global/sse`(약 2,360줄), `BT/api/service/sse`(약 1,990줄), 테스트 전용 `BT/eventhub`(10파일, 약 1,130줄) | 약 5,480줄 |
| Frontend | `F/shared/api/eventStream.ts`(202줄), `F/features/board/api/incidentBoardEventStream.ts`(80줄), `F/app/board/useIncidentBoardEvents.ts`(112줄), `F/features/markerNotifications/.../useIncidentMarkerNotifications.ts`(162줄) | 약 560줄 |
| Android | SSE는 없다. FCM 수신은 `A/core/fcm`(9파일, 약 830줄)이고 진입점은 `SuriMapFirebaseMessagingService.kt`다. | 약 830줄 |

SSE 클래스 이름과 배치는 2026-09에 한 번 정리했다([이름 후보 문서의 마커와 알림 절](codebase-naming-candidates.md#마커알림--남은-확인)).

### 읽기 어려운 곳

- **겹친 seam**: 수색 차수의 `EventPublisherPort`는 운영 adapter `EventHubOperationalPeriodEventPublisher`와 테스트 adapter 두 개(`InMemoryEventPublisher`, `OperationalPeriodApiContractTest:58`의 `@MockitoBean`)가 있는 real seam이다(직접 확인). 그런데 바꿔 끼우는 대상이 그 아래 `EventPublisher` seam과 같다. `EventPublisher`에는 이미 테스트 adapter `CapturingEventPublisher`가 있다. deletion test: `EventPublisherPort`와 `InMemoryEventPublisher`를 지우면 운영 쪽에서는 seam 하나가 사라진다. 대신 `InMemoryEventPublisher`를 쓰던 테스트와 `OperationalPeriodApiContractTest`의 주입과 검증을 `CapturingEventPublisher`로 옮겨야 한다. 운영에서 만들어지지 않는 `BlockingOperationalPeriodEventPublisher`와 `OperationalPeriodEventPublisherFallbackConfig`는 **미사용**이라서 그대로 지울 수 있다. 사건의 `IncidentEventPublisher`도 같은 모양이다([사건 생명주기](#사건-생명주기)).
- **locality**: 종료와 파기 이벤트 이름 상수가 세 곳에 있다(`ServerSentEventJobWorker:29`, `ServerSentEventSubscriptionService:18`, `IncidentClosedFcmDispatchConsumer:21`). `validateEventTransmission`은 Worker(`:225`)와 SubscriptionService(`:77`)에 같은 코드로 있다. FCM 토큰의 `cipher:` 제거도 네 곳에 있다(예: `MarkerNotificationService:249`, `IncidentClosedFcmDispatchConsumer:108`). 종료 판정이나 토큰 저장 방식을 바꾸면 모든 곳을 같이 고쳐야 한다.
- **locality**: FCM을 보내는 시점이 기능마다 다르다. 종료는 `publish()` 안에서 동기로, 배정은 트랜잭션 안에서, 마커는 커밋 뒤에 보낸다. 전송 시점 규칙이 한곳에 없어서 결함으로 이어진다(아래).
- **interface가 넓다**: `DatabaseEventPublisher`의 생성자가 4개다(`:39-65`). 생성자도 interface의 일부라서, 이 module을 만드는 쪽이 알아야 할 것이 많아진다. 어느 생성자를 운영이 쓰는지는 확인하지 않았다. 확인한 뒤 운영과 테스트가 쓰는 생성자만 남기면 interface가 좁아진다.
- **testing past the interface**: `ServerSentEventConnectionRegistry`의 인자 2개짜리 `sendToAccount`(`:41`)와 `getSenders`(`:59`)는 테스트만 부른다. 테스트에서 내부 상태를 보려고 interface를 넓힌 경우다. `ServerSentEventControllerTest:105`는 운영에서 이미 지운 메모리 재전송 저장소(`InMemorySseReplayEventStore`)를 아직 쓴다.
- 빠진 후보: `FcmDispatcherPort`는 운영 adapter(`FirebaseFcmDispatcher`)와 대역 adapter(`MockFcmDispatcher`)가 있는 real seam이다(직접 확인). 외부 서비스라서 seam이 필요하다. 이름 문제만 남는다. `LiveServerSentEventSender`도 운영 adapter 하나에 테스트 adapter 둘이 있는 real seam이다. `SEARCH_AREA_CHANGED`를 네 곳에서 만든다는 1회차 후보는, 그중 세 곳이 운영에서 쓰지 않는 maparea 클래스라서 [수색 구역](#수색-구역)의 미사용 삭제로 함께 사라진다.

### 동작하지 않거나 확인되지 않은 곳

- 이미 해소된 차이. 이벤트를 쓰기와 같은 트랜잭션에 저장한다(`DatabaseEventPublisher.java:84`). 순번 재전송과 409 응답이 동작한다. 종료 이벤트 뒤 연결을 닫는다. 서버 heartbeat 주석을 보낸다.
- 지금도 맞는 차이. 파기 때 `event_dispatch_job` 재전송 이력을 지우지 않는다. FCM 호출부가 흩어져 있다. 알림 패널과 알림 확인 상태는 미구현이다. 클라이언트의 30초 무응답 감지와 점점 늘리는 재연결 대기는 미구현이고 고정 3초다(`useIncidentBoardEvents.ts:85`, `useIncidentMarkerNotifications.ts:14`). 계정 SSE는 재연결하지 않는다(`IncidentListPage.tsx:337`).
- 상황판 하나에 사건 SSE 하나를 쓰기로 한 합의가 구현되지 않았다. 마커 알림(`SituationBoardRoute.tsx:73`)과 상황판 갱신이 따로 연결해 두 개를 연다.
- 이벤트 전달 기능 정의 문서에 더 분명히 적을 문제다(직접 확인). 사건 종료 FCM은 `DatabaseEventPublisher.publish()` 안에서 `DomainEventConsumer`로 동기 호출되므로 커밋 전에 나간다(`DatabaseEventPublisher.java:86`). 배정 FCM도 트랜잭션 안에서 나간다. 커밋 뒤에 보내는 것은 마커 FCM뿐이다(`MarkerNotificationService.java:179`). 종료 처리가 롤백되면 앱이 FCM을 받고 로컬 자료를 정리할 수 있다(추측, `SuriMapFirebaseMessagingService.kt:37-38`).
- 종료와 파기가 겹칠 때의 순서는 확인하지 못했다.
- 검증: `BT/global/sse`, `BT/api/service/sse` 테스트와 Frontend 이벤트 수신 테스트(`useIncidentBoardEvents.test.tsx` 등)가 통과했다.

### 보존할 동작

- `GET /api/incidents/{id}/events`, `GET /api/incidents/events`, `Last-Event-ID` 헤더
- 409 `gone_refetch_required`, 403 `channel_not_allowed`
- SSE `id`는 순번이다. envelope 필드는 `F/shared/api/eventStream.ts:4-15`에 있다. heartbeat 주석 `:heartbeat`
- 이벤트 이름 `INCIDENT_CREATED`, `INCIDENT_ASSIGNMENT_CHANGED`, `INCIDENT_CLOSED`, `INCIDENT_PURGED`, `PATH_APPENDED`, `SEARCH_PATH_SEGMENT_UPDATED`, `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED`, `OP_TRANSITIONED`, `HANDOVER_MEMO_CREATED`, `MARKER_UPDATED`, `OFFLINE_PACKAGE_INSTALLATION_CHANGED`, `POLICE_PHONE_HEARTBEAT_UPDATED`, `SEARCH_HISTORY_SUMMARY_CHANGED`, `OP_COMPARISON_ANALYSIS_CHANGED`
- 테이블 `event_dispatch_job`, 설정 키 `surimap.eventhub.dispatch.*`, `fcm.provider`
- FCM data 키 `type`, `eventId`, `incidentId`, `markerId`, `closedAt`, `purgeRunId`
- fixture `confirmed.eventFanout`(`BoardApiSseConvergenceHarnessRedTest`와 `test-fixtures/preflight_common_fixtures.py`가 읽는다)

### 정리 규모와 의존

중간. SSE 핵심은 이미 정리했다. 남은 일은 겹친 seam(`EventPublisherPort`, `IncidentEventPublisher`) 합치기, 흩어진 상수와 검사 모으기, FCM 전송 시점 규칙을 한곳에 두기다. 겹친 seam을 합치려면 테스트 주입을 함께 옮겨야 한다. 마커와 사진, 사건 생명주기, 인수인계의 발행 코드를 함께 건드린다. 상황판이 이 영역(`lastSseSequence`, 재조회)에 기댄다.

## 상황판

[상황판 기능 정의 문서](../features/situation-board.md)의 영역이다. 상황판 조회, 갱신, 종료 표시를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | 최상위 `B/board/`. Controller, Service, Response, 조립 코드가 함께 있다. `DefaultIncidentBoardSourceRowCollector`가 1,154줄이다. | 24파일, 약 2,460줄 |
| Backend | `B/api/controller/path/SearchPathBoardController`(62줄), `B/api/service/path/SearchPathBoardService`(673줄) | 약 740줄 |
| Backend 테스트 | `BT/board/` | 12파일, 약 4,120줄 |
| Frontend | `F/features/board`, `situationBoard`, `F/app/board`, `F/shared/api`, `markerNotifications`. 가장 큰 파일은 `SearchMapCanvas.tsx`(2,343줄)다. | 92파일, 약 1.9만 줄 |
| Android | 없다. 상황판 API는 WEB 전용이다. | - |

### 읽기 어려운 곳

- **미사용**: `BoardRefetchGuard`, `BoardRefetchConvergenceProbe`, `BoardAssemblyLagState`, `BoardReloadReason`, `BoardRefetchLedgerEntry`, `BoardRefetchResult`는 main의 다른 파일에서 참조하지 않는다(직접 확인). 하위 조사는 `BoardRefetchSignal`, `BoardRefetchLedgerStatus`까지 8개 파일(439줄)로 보고했다. 테스트(`BoardRefetchGuardRedTest`, `BoardApiSseConvergenceHarnessRedTest`, `Sc07Sc09OfflineReplayHarnessRunner`)만 쓴다. deletion test: 지우면 이 클래스와 그 테스트의 복잡도가 함께 사라진다.
- **미사용**: `BoardRowCursor` interface는 구현 클래스가 2개다(`BoardSlotRow`, `BoardSourceRowCursor`). 운영 코드에서는 이 타입으로 받는 곳이 없다. 테스트에서는 `BoardApiSseConvergenceHarnessRedTest:316`과 `BoardDtoAssemblyModelRedTest:400`의 `assertCursor`가 이 타입을 인자로 받는다(직접 확인). deletion test: interface를 지우면 운영 복잡도는 사라진다. 대신 두 테스트의 공통 assertion을 구현 타입별로 나누거나, 비교할 값을 꺼내 넘기는 방식으로 바꿔야 한다.
- **interface가 넓다**: `DefaultIncidentBoardSourceRowCollector`(1,154줄)는 작은 interface 뒤에 여러 원본 조회를 숨기는 deep module이다. 이 점은 좋다. 그러나 생성자 4개가 null을 넘기는 방식이라(`:80-204`), 만드는 쪽이 알아야 할 interface가 넓다.
- **의존성을 직접 만든다**: `IncidentBoardQueryService:14`는 `BoardAssembler`를 `new`로 직접 만든다.
- **interface가 넓다**: 응답의 `sourceVersions`, `sourceHashes`, `slotSources`에 같은 객체가 세 번 들어간다(`BoardAssembler.java:46-48`). 웹은 `slotSources`만 읽는다. 공개 응답도 interface이므로, 쓰지 않는 두 필드가 소비자가 알아야 할 것을 늘린다. 줄이려면 응답 계약 합의가 필요하다. `boardResponseId`는 응답에 나가지 않는다.
- **locality**: Frontend `SituationBoardResponseDto`가 세 곳에 정의되어 있다(`getSituationBoard.ts:3`, `incidentBoardApi.ts:82`, `getAreaEditBoard.ts:3`). 응답 필드를 바꾸면 세 곳을 함께 고쳐야 한다.
- **미사용**: Frontend `getSituationBoard()`, `useIncidentBoard`, `mapIncidentBoardResponse`는 운영 호출이 없다. `F/features/board/components/*Slot` 7개는 테스트에서만 쓰인다. `F/components/`와 `F/test/`에는 같은 슬롯의 복사본이 있다.
- **규칙 위반**: `board/`가 최상위 패키지다. 원칙의 예시 위치는 `api/controller/board`다. record가 많고 `BoardDTO`라는 이름도 역할을 드러내지 않는다.
- **이름 문제**: `PackageBadgeBoardAssembler`는 응답을 조립하지 않고 행만 만든다. 안에 `"S7"`, `"evt-s7-package-status-001"`, 고정 해시가 박혀 있다(`:13-15`). deletion test: 지우면 행 생성 로직이 수집기로 옮겨 갈 뿐이라 module은 남긴다. 고정값은 지운다.
- 빠진 후보: 1회차의 "구현이 하나뿐인 `IncidentBoardSourceRowCollector` 인터페이스"는 뺐다. 운영 adapter `DefaultIncidentBoardSourceRowCollector`와 테스트 adapter `CapturingCollector`(`IncidentBoardQueryServiceSourceCollectorTest:47`)가 있는 real seam이다(직접 확인). 상황판이 쓰는 `OfflinePackageInstallationQuery`와 `SearchAreaQuery`도 테스트 adapter가 있는 real seam이다.

### 동작하지 않거나 확인되지 않은 곳

- 지금도 맞는 차이. `sinceVersion`을 받지만 조회에 쓰지 않는다. 종료된 사건도 경로와 마커를 계속 조회하고, 업무폰 최신성만 건너뛴다(수집기 `:218-225`, `:330`). `BoardRefetchGuard`는 운영 호출부가 없다. 클라이언트에서는 순번 간격이 검사되지 않는다(`useIncidentBoardEvents.ts:24,47`). 요청 시간 제한이 없다.
- 웹이 비워진 슬롯을 이전 값으로 유지한다(직접 확인). `F/features/board/model/incidentBoardMerge.ts:79-89`의 `shouldKeepPreviousSlot`은 새 값이 없거나 빈 배열이면 이전 값을 남긴다. 제외되는 슬롯은 `path`뿐이다(`useSituationBoardData.ts:67`). 마지막 마커를 지워도 화면에 마커가 남을 수 있다(추측, 재현하지 않음).
- 이미 해소된 차이. 상황판 기능 정의 문서의 "`retry: false`라서 실패 재시도가 남았다"는 지금 코드와 다르다(`usePagedIncidentBoardQuery.ts:47`, `boardReadRecovery.ts`). 마지막 조회 성공 시각 표시도 구현되어 있다(`useSituationBoardData.ts:190`). 상황판 기능 정의 문서를 고칠 후보다. 브라우저에서 확인하지는 않았다.
- 경로 API는 `surimap.board.search-path.enabled` 조건으로 켜진다(`SearchPathBoardController.java:26`). 실서버 값은 확인하지 않았다.
- 검증: `BT/board` 테스트와 Frontend 상황판 테스트(`usePagedIncidentBoardQuery.test.tsx`, `useSituationBoardDataPathMeasurement.test.ts` 등)가 통과했다.

### 보존할 동작

- `GET /api/incidents/{id}/board`의 요청 파라미터 `opIds`, `includeSlots`, `sinceVersion`과 조회 기록 `BOARD_VIEW`
- 응답 필드 11개(`B/board/IncidentBoardResponse.java:7-18`), 슬롯 13개와 singleton 3개(`BoardSlotRegistry`)
- `includeSlots`를 쓰는 화면 `IncidentClosePage.tsx:37`, `OfflinePackageStatusPage.tsx:155`
- `POST /api/incidents/{id}/board/search-paths/{segments,changes}/query`
- fixture `boardAssembly`, `board-api-assembly-failures.json`

### 정리 규모와 의존

큼. 수색 경로, 마커와 사진, 오프라인 패키지, 인수인계, 데이터 파기, 이벤트 전달의 결과를 모아 보여 주므로 그 영역들의 응답과 슬롯이 바뀌면 함께 바뀐다. 미사용 재조회 클래스 삭제는 작다. `BoardRowCursor` 삭제는 테스트 assertion 두 곳을 함께 바꾼다. 패키지 이동, 생성자 정리, 응답 중복 필드 정리는 웹 병합 코드와 함께 해야 한다.

## 마커와 사진

[마커와 사진 기능 정의 문서](../features/marker-photo.md)의 영역이다. 2026-09-09~22에 한 번 정리했다([이름 후보 문서의 반영 이력](codebase-naming-candidates.md#반영-이력-요약)). 이번에는 남은 문제를 위주로 봤다. 정리 커밋 목록은 `git log`를 실행할 수 없어 확인하지 못했다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/app/service/marker`(AppMarkerService 360줄, MarkerNotificationService 289줄), `B/app/service/photo/PhotoService`(506줄), `B/api/service/marker/MarkerService`(318줄), `B/domain/marker`, `B/domain/photo`, `B/global/event/Marker*` 5파일, mapper XML 4개 | 약 70파일, 4,800줄 |
| Frontend | `F/features/marker/api/markerCommandApi.ts`(164줄), `F/features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications.ts`(162줄) | 운영 2파일, 약 330줄(테스트 2파일 제외) |
| Android | `A/feature/marker`(6파일, 2,630줄), `A/core/marker/MarkerRepository.kt`(411줄) | 약 3,040줄 |

### 읽기 어려운 곳

- **locality**: api와 app이 마커 수정과 삭제 흐름을 복제한다. `MarkerService.java:92,135`와 `AppMarkerService.java:230,262`가 거의 같다. `MarkerMutationServiceResponse`가 패키지만 다르게 두 벌 있다. 수정 규칙 하나를 바꾸면 두 곳을 고쳐야 한다. 공용 흐름을 `domain` 쪽 module 하나로 모으면 두 Service는 채널별 인증과 응답 변환만 남는다. [패키지 설계 원칙](../../backend/AGENTS.md#패키지-설계-원칙)의 지양 예시("api와 app service에 같은 domain 정책을 복붙하는 구조")에도 해당한다.
- **locality**: `MarkerAuthenticationResolver`와 사진의 `PhotoRequestContextResolver`가 guard와 겹치는 검사를 한다. [인증과 접근 권한](#인증과-접근-권한)에 적었다. 덜어 내는 일은 [정리 순서 추천안](#정리-순서-추천안)의 6번(인증과 접근 권한)에서 한다.
- **의존성을 직접 만든다**: Android `MarkerLocalRecorder.kt:82`가 `MarkerRepository`를 생성자 안에서 직접 만든다(직접 확인). `MarkerDetailStateLoader.kt:23`도 `MarkerRepository()`를 직접 만든다. deletion test: `MarkerLocalRecorder`를 지우면 operationId, 멱등 키, 시각 보정 값을 만드는 명령 조립이 Repository나 호출처로 옮겨 간다. 사라지지 않으므로 module은 남긴다. Repository만 받도록 바꿔 테스트에서 바꿔 끼울 seam을 만든다. 테스트에서 지금도 `syncClient`는 바꿔 끼울 수 있으므로, 없는 것은 Repository 자체의 seam이다.
- **규칙 위반**: 마커 전용 코드가 `global/event`(5파일)와 `global/auth`에 있다. 도메인 패키지에 요청 본문 형식(`MarkerMutationLegacyRequestBody`, `MarkerWriteAccessData`)이 있다. `external/ExternalSeedMarker`가 옛 위치에 남아 있다.
- 빠진 후보: 1회차의 `BlockingReferenceMarkerSeed`와 `ReferenceMarkerSeedFallbackConfig`는 운영에서 등록되는지 확인하지 못해 종류를 붙이지 않았다. [이번 조사에서 확인하지 않은 것](#이번-조사에서-확인하지-않은-것)에 남겼다.

### 동작하지 않거나 확인되지 않은 곳

- 마커를 지울 때 사진을 DELETED로 바꾸는 처리가 없다. `PhotoMapper.xml`에는 FAILED와 ATTACHED 전환만 있다. 마커와 사진 기능 정의 문서와 같다.
- 사건 파기 때 사진 삭제는 연결되지 않았다(직접 확인). [데이터 보존과 파기](#데이터-보존과-파기)에 적었다.
- Android `isRetryable`(`NetworkOutboxSender.kt:60`)에 `op_mismatch` 분기가 없어 일반 최종 실패로 처리된다. 마커와 사진 기능 정의 문서와 같다.
- `write_conflict` 뒤 사진을 자동으로 다시 올리는 연결이 없다. 사진 URI는 Compose `remember`로 메모리에만 있다(`SuriMapApp.kt:1932,2721`). 앱을 다시 켜면 사진을 다시 고르게 될 것으로 본다(추측).
- 사진을 임시 보관했다가 다시 보내는 동작은 실기기 검증이 필요해 확인하지 못했다.
- 검증: 관련 Backend 테스트(`AppMarkerServiceTest`, `PhotoControllerTest` 등), Android 테스트, Frontend 마커 알림 테스트가 통과했다.

### 보존할 동작

- `POST /api/markers`, `PATCH /api/markers/{id}`, `DELETE /api/markers/{id}`. 같은 URL이 `X-Client-Channel`에 따라 앱과 웹 Controller로 갈린다(`AppMarkerController.java:70,93`, `MarkerController.java:60,83`).
- `/api/markers/photos/upload-url`, `/api/markers/{id}/photos/upload-url`, `/api/markers/{id}/photos/{photoId}/attach`
- 이벤트 `MARKER_CREATED`, `MARKER_UPDATED`, `MARKER_DELETED`
- 멱등 키 문자열(`AppMarkerService.java:59`)과 `*LegacyRequestBody`의 옛 요청 해시. 이미 저장된 중복 요청 판정이 이 값에 기댄다.
- 테이블 `marker`, `marker_notification`, `photo`
- fixture `markerAndNotification.markerPhotoAttach`

### 정리 규모와 의존

중간. 남은 일은 api와 app의 중복 흐름 모으기, `global`에 있는 마커 코드 옮기기, Android Repository 주입, 사진 삭제 정책, 파기 hook 연결이다. 오프라인 동기화의 `IdempotentResponseCache`와 데이터 파기에 기댄다. 오프라인 패키지가 `api/service/marker/MarkerService`를 읽는다(`OfflinePackageRepository.java:138`).

## 오프라인 동기화

[오프라인 동기화 기능 정의 문서](../features/offline-sync.md)의 영역이다. 앱의 미전송 기록, 중복 요청, 사건 종료 후 정리를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/sync/`(idempotency, outbox, clock). `IdempotentResponseCache` 156줄, `DbIdempotencyRecordRepository` 184줄 | 30파일, 약 1,350줄 |
| Frontend | 해당 없음 | - |
| Android | `A/core/sync/RoomLocalSyncServices.kt`(700줄, 클래스 4개), `A/core/database/OutboxDao`(291줄), `A/core/network/NetworkOutboxSender.kt`(120줄), `SyncApiClient`(92줄), `A/feature/outbox`(400줄) | 약 2,450줄 |

### 읽기 어려운 곳

- **locality**: 재전송기와 전송기를 조립하는 코드가 네 곳에 있다(`SuriMapApp.kt:1211,3507`, `OutboxWorker.kt:145`, `SuriMapFirebaseMessagingService.kt:61`). 전송기 설정을 바꾸면 네 곳을 같이 고쳐야 한다. 조립을 한곳에 두면 locality가 생긴다.
- **locality**: `OutboxDao.statusSummary`(`:182`)와 `observeStatusSummary`(`:209`)의 SQL이 같다. 앞의 것은 suspend 조회이고 뒤의 것은 Flow 조회라서 둘 다 필요하다. 상태 집계 규칙을 바꾸면 두 SQL을 함께 고쳐야 한다. 사용처는 다음과 같다(직접 확인).
  - `statusSummary`: `SuriMapApp.kt:1870,2941`, `BlockedOutboxStateLoader.kt:35`, debug 화면 `DeviceQaActivity.kt:461`. 테스트는 `OutboxDaoStatusSummaryTest:47,60`이다.
  - `observeStatusSummary`: `SuriMapApp.kt:1969,2184,2968`. 이 메서드를 직접 검증하는 테스트는 찾지 못했다.
- **미사용**: `NoopOutboxSender`(`:698`)는 테스트(`MarkerLocalRecorderRoomTest:68`)만 쓰는 테스트 adapter인데 main에 있다. `HarnessStatusMapper`(테스트에서만 사용), `createSyncApiClient`(호출 없음), 쓰기만 하고 읽지 않는 `SyncClockCalibrationRecordStore`는 deletion test에서 복잡도가 그대로 사라진다.
- **규칙 위반**: 도메인별 ExceptionHandler 3개, 같은 대상을 둘로 나눈 `IdempotencyRecord`(record)와 `IdempotencyRecordRow`, `common.auth` 참조가 원칙과 맞지 않는다.
- **이름 문제**: `NetworkOutboxSender.kt` 파일 안의 클래스 이름은 `HttpOutboxSender`다. 운영 이름에 Harness가 남아 있다(`HarnessSyncStatus`를 운영 코드가 31번 쓴다).
- 빠진 후보: 없음. `IdempotentResponseCache` 자체는 deep module로 판단했다([공통](#공통)).

### 동작하지 않거나 확인되지 않은 곳

- 지금도 맞는 차이. 재전송은 선행 요청 확인 없이 순번 정렬만 쓴다. 근무 교대 종료만 따로 앞선 기록을 기다린다(`RoomLocalSyncServices.kt:395-413`). `write_conflict`를 최종 실패로 처리한다. 사건 종료 후 마지막 전송을 시도한다(`handleIncidentClosed`, `:576-604`).
- 응답 복구가 연결되지 않았다. `register` 호출이 운영 코드에 없다. `IdempotentResponseCache.java:56`에서 operationId와 entityId가 null로 넘어가므로, 연결해도 복구에 쓰기 어려울 것으로 본다(추측).
- 진단 API에서는 고정 사건 ID 목록으로 종료 여부가 정해진다(`OutboxRequeue` 쪽 `:24-25`). 앱은 이 서버 응답을 버리고 로컬 재큐만 한다(`SuriMapApp.kt:1377-1394`의 `runCatching` 결과를 쓰지 않음).
- 단말 파기의 `cleanupSteps`는 단계 이름만 돌려주고 실제로 지우지 않는다(`RoomLocalSyncServices.kt:676`).
- **미사용**: Backend의 `@IdempotentWrite`와 Aspect([공통](#공통))
- 실패 메트릭은 DEBUG 로그뿐이어서 확인할 수 없다.
- 검증: Android `RoomLocalSyncServicesTest`, `OutboxStateMachineTest` 등이 통과했다.

### 보존할 동작

- `POST /api/sync/outbox/requeue`(요청 7필드, 응답 7필드), `POST /api/sync/clock`
- 헤더 `Idempotency-Key`, `X-PolicePhone-Id`
- 오류 코드 `idempotency_mismatch`, `write_conflict`, `incident_closed`
- Room 테이블 `android_outbox_row`, `local_write_draft`
- Android Outbox가 URL 문자열로 요청 종류를 구분한다(`OutboxDao.kt:135-136`, `RoomLocalSyncServices.kt:229-230`). 다른 영역의 URL을 바꾸면 여기도 바꿔야 한다.
- fixture `outboxReplay`, `terminalStateRules`, `outboxSharedRules`

### 정리 규모와 의존

미사용 코드 제거, 조립 코드 모으기, Android 파일 나누기는 작다. 응답 복구 연결은 `IdempotentResponseCache`를 쓰는 서비스 전부(수색 차수, 인수인계 메모, 수색 구역의 세 Service 포함)를 건드리므로 크다. 모든 앱 쓰기 영역(마커, 경로, 근무 교대, 인수인계 메모, 패키지 설치 상태)이 이 영역의 재전송에 기댄다.

## 오프라인 패키지

[오프라인 패키지 기능 정의 문서](../features/offline-package.md)의 영역이다. 오프라인 패키지의 다운로드, 준비 완료, 갱신, 파기를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | 최상위 `B/offlinepackage/`(controller, service, dto, exception, purge 하위 폴더). `OfflinePackageRepository` 1,167줄, `LocalTileService` 224줄, `TileserverGlTileService` 201줄 | 29파일, 약 2,830줄 |
| Frontend | `F/features/offlinePackage`(그중 css 1,134줄) | 약 3,630줄 |
| Android | `A/core/offline`(907줄), `A/feature/offline`(930줄) | 약 1,840줄 |

### 읽기 어려운 곳

- **testing past the interface**: `OfflinePackageRepository`(1,167줄)는 manifest 조립, 설치 상태 저장, 파기, 고정 테스트 자료 넣기를 한 module에 담는다. 운영 코드 안의 fixture ID 상수와 별칭표(`:72-131`)를 테스트가 직접 쓴다. 그래서 구현을 바꾸면 테스트도 바뀐다.
- **interface가 넓다**: 같은 `OfflinePackageRepository`는 `@Repository`인데 service 패키지에 있다. 생성자가 4개다(`:142-215`). 원천 조회 6개에 기댄다. 공개 메서드가 모두 `synchronized`다. 이 module은 manifest를 만드는 지식을 한곳에 모아 주므로 지우지 않는다. 고정 테스트 자료 분기를 빼고 생성자를 줄여 interface를 좁힌다.
- **hypothetical seam**: `SearchAreaChangedConsumer` interface는 adapter가 `OfflinePackageSearchAreaChangedConsumer` 하나뿐이다(직접 확인). 이 클래스는 `DomainEventConsumer`도 구현하는데, 실제로 바꿔 끼우는 seam은 그쪽이다. deletion test: `SearchAreaChangedConsumer`를 지우면 운영 복잡도가 사라진다. `OfflinePackageSearchAreaChangedConsumerRedTest:55`가 이 interface 타입으로 주입받으므로, 그 선언은 구현 클래스 타입으로 바꿔야 한다.
- **shallow**: 패키지 파기의 `PackagePurgeHook`, `PackagePurgeHookAdapter`, `OfflinePackagePurgeService` 세 단계는 값을 넘기기만 한다. [데이터 보존과 파기](#데이터-보존과-파기)에 deletion test 결과를 적었다.
- **interface가 넓다**: Android `OfflinePackageItemInstaller`의 콜백 2개를 유일한 운영 호출자가 빈 함수로 넘긴다(`RoomOfflinePackageWorkerInstaller.kt:57-58`). 쓰지 않는 인자가 interface에 남아 있다.
- **locality**: 권한 검사를 Controller의 헤더 문자열 비교로 한다(`OfflinePackageController.java:48-69`, 직접 확인). 다른 Controller는 guard 애너테이션을 쓰므로 권한 규칙이 두 곳에 나뉜다.
- **규칙 위반**: 최상위 패키지 안에 controller, service, dto, exception 계층 폴더를 따로 둔다. 요청 해시를 문자열 이어 붙이기로 만든다(`OfflinePackageService.java:144-167`).
- **미사용**: Frontend의 `fetchOfflinePackageManifest`(`:131`)는 호출이 없다.
- 빠진 후보: 1회차의 "구현이 하나뿐인 `OfflinePackageInstallationQuery`"는 뺐다. 운영 adapter `OfflinePackageService`와 테스트 adapter 2개(`PackageBadgeBoardAssemblyRedTest:234`, `IncidentBoardSourceRowCollectorIntegrationTest:917`)가 있는 real seam이다(직접 확인).

### 동작하지 않거나 확인되지 않은 곳

- 사건 접근 검사가 없다(직접 확인). `GET /api/incidents/{incidentId}/offline-package/manifest`와 `POST .../installations`에는 `@RequireIncidentAccess`가 없다. `SecurityConfig`에서는 로그인 여부만 확인되므로, 로그인한 계정이면 배정되지 않은 사건의 manifest도 받을 수 있는 구조다. 하위 조사에 따르면 manifest 응답에 실종자 정보가 들어간다. 오프라인 패키지 기능 정의 문서에는 "일부 헤더 검사만으로 충족됐다고 판단하지 않는다"고만 적혀 있다. 이번 조사로 범위가 manifest 조회 전체임을 확인했다.
- 운영 경로에서 고정 테스트 사건의 자료를 DB에 넣으려 한다(직접 확인). `saveStatus`(`:513`)와 STALE 전환(`:563`)에서 `ensureFixtureManifest()`가 호출된다. 이 메서드는 고정 `INCIDENT_DB_ID`의 manifest가 없으면 만들고, 고정 업무폰 ID로 설치 상태 5건을 넣는다(`:613-645`). 운영 DB에서 실제로 행이 생겼는지는 확인하지 않았다. `policePhoneId`가 없으면 fixture 업무폰으로 대체된다(`:221`). 웹은 이 값 없이 manifest를 부른다(`offlinePackageApi.ts:124`).
- 지금도 맞는 차이. 실패 항목만 다시 받지 않는다. manifest 조회가 실패하면 상태를 남기지 않고 끝낸다(`:37-39`). 화면은 로컬 기준으로 READY를 표시한다. STALE 전환은 전체 수색 구역 변경에만 반응한다. opId가 있는 이벤트는 무시된다(`OfflinePackageService.java:89`). 수색 차수 전환과 담당 구역 변경은 연결되지 않았다.
- 만료 검사는 웹 화면에만 있다. 웹 상태 화면에서는 `expiresAt`을 현재 시각과 비교해 만료 경고를 표시한다(`offlinePackageStatusPageViewModel.ts:246`, `OfflinePackageStatusPage.tsx:570`, 직접 확인). Backend의 manifest 제공과 Android의 다운로드 및 READY 판단에서는 `expiresAt` 검사를 찾지 못했다.
- 검증: 관련 Backend 테스트(`OfflinePackageServiceTest` 등), Android 테스트, Frontend 패키지 화면 테스트가 통과했다.

### 보존할 동작

- `GET .../offline-package/manifest` 응답 14필드. Frontend 타입(`offlinePackageApi.ts:13-28`)과 Android `OfflinePackageDownloadPlan`이 기댄다.
- `POST .../installations`의 헤더 4개와 응답 6필드
- `/tiles/**` 3개 경로
- 이벤트 `OFFLINE_PACKAGE_INSTALLATION_CHANGED`(payload 7필드)와 `SEARCH_AREA_CHANGED` 소비
- 오류 코드 `package_manifest_not_ready`, `package_purged`(410)
- 상황판 슬롯 `package_badge`
- fixture `tileManifest`, `offlinePackageUi.manifestDownloadPending`, `outboxReplay.sc09PackageReplay`. Repository의 fixture ID 상수는 테스트가 기대므로 옮길 때 함께 옮긴다.

### 정리 규모와 의존

큼. 오프라인 동기화, 마커와 사진, 수색 구역, 인수인계(수색 차수), 데이터 파기, 상황판에 걸친다. shallow 파기 단계와 hypothetical seam 삭제는 작다. 접근 검사와 fixture 분기는 정리와 별개로 먼저 고칠지 정할 수 있다.

## 수색 구역

[수색 구역 기능 정의 문서](../features/search-area.md)의 영역이다. 수색 구역의 도형, 분할, 담당 배정, 완료를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | 최상위 `B/maparea`(47파일, 약 2,580줄), `B/api/*/searcharea`(13파일, 약 1,850줄. `SearchAreaApiService`가 1,560줄), `B/app/*/searcharea`(경계 알림, 8파일, 약 530줄) | 68파일, 약 4,960줄 |
| Frontend | `F/features/areaEdit`, `searchArea`(`AreaEditPage.tsx` 1,320줄, `AreaEditMapCanvas.tsx` 1,278줄). `searchAreas`는 빈 폴더다. | 30파일, 약 5,130줄 |
| Android | `A/core/searcharea`(2파일) | 148줄 |

### 읽기 어려운 곳

- **미사용**: `maparea`의 `SearchAreaService`, `SearchAreaSplitService`, `command/OverallSearchAreaService`에는 빈 애너테이션이 없다(직접 확인). `statemachine/*`, `assignment/*`, `event/SearchAreaEventPublisher`도 하위 조사 결과 빈이 아니다. 이 클래스들은 `BT/maparea` 테스트에서만 `new`로 만들어진다. `GeometryValidationService`와 `PostGisGeometrySpatialAdapter`도 이 미사용 클래스에서만 불린다. deletion test: 지우면 이 클래스와 그 테스트의 복잡도가 함께 사라진다. 운영 동작은 바뀌지 않는다. 이 영역에서 가장 큰 삭제 후보다. 두 곳에 있는 `AreaStateConflictException`, 네 곳 중 세 곳에서 만들던 `SEARCH_AREA_CHANGED`도 함께 정리된다.
- **미사용**: `InMemorySearchAreaQueryAdapter`는 main에 있지만 자기 테스트(`InMemorySearchAreaQueryAdapterTest`)만 쓴다(직접 확인). `SearchAreaQuery` seam은 운영 adapter `SearchAreaApiService`와 테스트 adapter 4개(`SearchAreaQueryMock`, `FakeSearchAreaQuery`, `MockOverallSearchAreaPort`, `MockSearchAreaPort`)가 있는 real seam이다. 상황판과 오프라인 패키지가 이 seam을 써서 leverage도 있다. 따라서 seam은 남기고 이 adapter만 지운다.
- **interface가 넓다**: 실제 운영 경로는 `SearchAreaApiService` 하나다. 이 module 안에 메모리 Map 저장과 DB 저장 두 경로가 함께 있다. Mapper를 null로 받는 생성자(`:232-240`)가 있다. DB 경로가 없으면 메모리로 넘어간다(`:518`). DB에서 읽은 행도 싱글턴 Map에 계속 쌓인다(`:752`, `:876`). 생성자로 메모리 모드를 고를 수 있으므로, 만드는 쪽이 두 모드를 알아야 해서 interface가 넓다. deletion test: 메모리 경로를 지우면, 그 경로에 기대는 테스트가 실제 DB(`PostGisIntegrationTestSupport`)로 옮겨 가야 한다. 운영 복잡도는 사라지므로 테스트를 옮긴 뒤 지울 후보다.
- **locality**: `getActiveOverallSearchArea`가 Frontend 두 곳에 같은 코드로 있다(`getSearchAreas.ts:11`, `areaEditPageHelpers.ts:22`).
- **미사용**: Frontend `F/features/areaEdit/data`의 `createSearchArea`, `splitSearchArea`, `assignSearchArea`, `getSearchAreas`, `getOperationalPeriods`는 서로만 참조하고 화면이 import하지 않는다.
- **규칙 위반**: 이 Service가 Controller DTO를 import한다. 멱등 해시도 `String.valueOf(request)`로 만든다(직접 확인, [공통](#공통) 참고). 같은 대상을 `SearchAreaPersistenceRecord`, `SearchAreaReadRecord`, `SearchAreaHistoryPersistenceRecord`, `SearchAreaAssignmentPersistenceRecord`, `SearchAreaBoundaryAlertPersistenceRecord`로 나눴다. 테스트 패키지가 `BT/searcharea`와 `BT/maparea`로 나뉘어 있다.
- 빠진 후보: 없음. 1회차에 "운영에서 쓰이지 않는 클래스"로 묶었던 `query/InMemorySearchAreaQueryAdapter`는 seam 자체가 real seam이라는 점을 구분해 따로 적었다.

### 동작하지 않거나 확인되지 않은 곳

- 정규화한 좌표를 저장에 쓰지 않는다. `validatePolygon`에서 `validateAndCanonicalizePolygonRing`의 반환값이 버려진다(직접 확인, `SearchAreaApiService.java:1051`). 수색 구역 기능 정의 문서와 같다.
- 면적, 상위 구역 포함, 하위 구역 겹침은 검사되지 않는다. 운영 검증 설정에 `GeometryPolicy.s2HarnessDefault()`를 쓴다(`B/config/GeometryConfig.java:15`). 상태 전이표 없이 허용 문자열인지만 검사된다(`SearchAreaApiService.java:1061`). 수색 구역 기능 정의 문서와 같다.
- 동시 수정 보호가 수색 구역 기능 정의 문서에 적힌 것보다 약하다(직접 확인). `SearchAreaMapper.xml`의 `updateMutation`은 `WHERE id = ...`만 쓰고 버전 조건이 없다. 버전 비교는 잠금 없이 읽은 값으로 Java에서 한다(`:809`). 두 요청이 같은 구역을 동시에 고치면 뒤의 요청이 앞의 결과를 덮어쓸 수 있다(추측, 재현하지 않음).
- 종료된 사건의 쓰기 차단을 찾지 못했다. Service에 `IncidentLifecycleGuard` 주입이 없고 Controller에 `@RequireOpenIncident`도 없다.
- 배정 대상 계정이 사건에 참여하는지는 확인되지 않는다. 배정 요청의 `expectedVersion`도 검사되지 않는다(`:686-711`).
- `PATCH /api/search-areas/{id}`를 호출하는 화면이 없다(`searchAreaApi.ts:207`의 훅만 있다). 구역 완료 UI는 미구현으로 본다.
- 검증: 관련 Backend 테스트와 Frontend 테스트가 통과했다. 그러나 `BT/maparea`의 다수 테스트는 운영에서 쓰지 않는 클래스를 검증한다.

### 보존할 동작

- `POST /api/search-areas`, `GET /api/search-areas`, `PATCH /api/search-areas/{id}`, `POST /api/search-areas/{id}/split`, `POST /api/search-areas/{id}/assignments`, `/api/search-area-boundary-alerts`
- 이벤트 `SEARCH_AREA_CHANGED`, `SEARCH_AREA_ASSIGNMENT_CHANGED`
- 테이블 `search_area`, `search_area_history`, `search_area_assignment`, `search_area_boundary_alert`
- 다른 소비자. 상황판 수집기와 `OfflinePackageRepository`가 `SearchAreaQuery` 계약을 쓴다. Android는 `A/core/searcharea/SearchAreaReadRepository.kt:57`에서 조회한다.
- fixture `geometryReference`, `ownerReferences`, `boardAssembly`, `BoundaryAreaFixtures`, `SearchAreaAssignmentFixtures`

### 정리 규모와 의존

큼. 미사용 클래스 삭제는 운영 동작을 바꾸지 않아 작다. 효과도 빠르게 난다. 반면 메모리 경로 제거와 동시 수정 보호는 동작이 바뀌므로, 실제 DB를 쓰는 Service 테스트를 먼저 보강해야 한다. 인수인계(현재 수색 차수 조회), 상황판, 오프라인 패키지와 걸친다.

## 수색 경로

[수색 경로 기능 정의 문서](../features/search-path.md)의 영역이다. GPS 수집, 묶음 저장, 경로 조회를 다룬다. Backend 계층은 한 번 정리했다([수색 경로 레이어 분리 기록](search-path-layer-separation.md), d7035829).

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | `B/api/controller/path`(약 20파일), `B/api/service/path`(17파일. `SearchPathService` 687줄, `SearchPathBoardService` 673줄), `B/app/*/path`(10파일. `AppSearchPathService` 248줄), `B/domain/path`(22파일, 약 1,110줄), `B/global/event/SearchPathEventPublisher` | 약 70파일 |
| Frontend | `F/features/path` | 6파일, 운영 코드 735줄 |
| Android | `A/core/path/SearchPathRepository.kt`(346줄), `A/core/location/LocationRecorder.kt`(122줄), `A/feature/search/data`의 경로 파일 6개(약 790줄) | 약 1,260줄 |

### 읽기 어려운 곳

- **locality**: 경로 오류를 응답으로 바꾸는 처리기가 세 곳이다(`api/controller/path/SearchPathExceptionHandler`, `app/controller/path/PathExceptionHandler`, `global/error/SearchPathQueryExceptionHandler`, 직접 확인). 도메인 패키지에 `SearchPathApiException`과 `SearchPathGuardException` 두 예외가 있다. 생성 호출은 7개 파일에 52번 있다. 경로 오류 코드를 바꾸려면 세 처리기를 모두 확인해야 한다.
- **미사용**: Android `A/core/location/LocationRecorder.kt:13`의 `interface LocationRecorder`(start, pause, stop)는 구현도 호출도 없다(직접 확인). deletion test: 지워도 아무것도 바뀌지 않는다. 이 파일에서 실제로 쓰이는 것은 `LocationUpdates` interface와 `AndroidLocationUpdates`다. 파일 이름이 실제 내용과 다른 점은 이름 후보로 등록했다.
- **locality**: 좌표 묶음 상한 `MAX_BATCH_POINTS=120`이 두 module에 있다(`SearchPathGpsBatchRecorder.kt:159`, `SearchPathLocalRecorder.kt:212`). 상한을 바꾸면 두 곳을 같이 고쳐야 한다.
- **의존성을 직접 만든다**: `SearchPathLocalRecorder.kt:38`은 `SearchPathRepository`를 생성자 안에서 직접 만든다(직접 확인). `SearchMapStateLoader.kt:49`도 `SearchPathRepository()`를 직접 만든다.
- **미사용**: `SearchPathRepository.endSearchPath`(`:135`)는 호출이 없다. Frontend `searchPathApi.createManualPath`(`:111`)는 WEB 채널로 앱 전용 시작, 묶음 추가, 상태 변경을 부른다. 유일한 화면 호출처는 개발용 경로 편집기다. 이 편집기는 `ENABLE_LOCAL_ROUTE_EDITOR = false`로 꺼져 있다(직접 확인, `routeEditorLayer.ts:5`, `searchMapCanvasLayers.ts:19`). 이 요청은 서버의 `@RequireChannel(APP)`에서 거절될 것으로 본다. 웹에서 앱 전용 write를 만들지 않는다는 원칙과도 맞지 않는다. 같은 파일의 `list`, `correctSegment` 훅도 화면 호출처가 없다.
- **규칙 위반**: 도메인 전용 발행자 `SearchPathEventPublisher`가 `global/event`에 있다. deletion test로는 제 몫을 하므로 위치만 옮긴다([공통](#공통)).
- 빠진 후보: 1회차의 "Android가 `LocationRecorder` → `SearchPathLocationRecorder` → `SearchPathGpsBatchRecorder` → `SearchPathLocalRecorder` → `SearchPathRepository` 5단계를 거친다"는 뺐다. 첫 단계 `LocationRecorder`는 쓰이지 않는 interface다(위). 실제 흐름은 `LocationUpdates`부터 Repository까지다. 단계마다 deletion test를 하면 복잡도가 다른 곳으로 옮겨 갈 뿐이다(직접 확인).
  - `LocationUpdates`: 운영 adapter `AndroidLocationUpdates`와 테스트 adapter `FakeLocationUpdates`가 있는 real seam이다.
  - `SearchPathLocationRecorder`: 기록 시작과 종료, Mutex 순서 보장, 마지막 좌표 기록 대기를 맡는다. 지우면 이 순서 규칙이 `SuriMapApp`과 `SuriMapApplication`으로 옮겨 간다.
  - `SearchPathGpsBatchRecorder`: 묶음 조건(개수와 시간 범위)을 맡는다. 지우면 묶음 규칙이 호출처로 옮겨 간다.
  - `SearchPathLocalRecorder`: 쓰기 맥락 검증, operationId, 멱등 키, 시각 보정 값으로 명령을 조립한다. 지우면 조립이 Repository나 호출처로 옮겨 간다.

  남는 문제는 상한의 중복과 Repository를 직접 만드는 점이다.

### 동작하지 않거나 확인되지 않은 곳

- 경로 조회에 사건 접근 검사가 없다(직접 확인). `GET /api/search-paths`(`SearchPathController.java:54-67`)에는 guard 애너테이션도 `@RecordLocationAccess`도 없다. 로그인한 계정이면 `incidentId`만 바꿔 다른 사건의 경로를 조회할 수 있는 구조다. 위치 조회 기록도 남지 않는다. Android가 이 API를 쓴다(`SearchPathRepository.kt:334`, `SearchMapStateLoader.kt:49`). 수색 경로 기능 정의 문서의 차이와 같지만, 개인 위치 데이터라서 정리 순서와 별개로 먼저 고칠지 정할 대상이다.
- 구간 보정 `PATCH /api/search-path-segments/{id}`도 guard 애너테이션 없이 `X-Account-Id` 헤더를 쓴다(직접 확인, `:69-79`). 웹 화면은 이 API를 부르지 않아, 웹에서 이동 유형을 보정한다는 기존 요구는 미구현이다.
- 시각 차이 기준이 첫 좌표의 `clientTs + 20초`다(`SearchPathService.java:239-241`). 수집 주기 5초, 묶음 120개 또는 10초다. 수색 경로 기능 정의 문서와 같다.
- 종료된 사건의 쓰기 거부는 경로 Service에서 찾지 못했다. 다른 필터가 막는지는 확인하지 않았다.
- `segmentIndexes`의 추정 fallback을 남길지는 아직 정하지 않았다(`SearchPathService.java:171-195`).
- 검증: `SearchPathServiceTest`, `AppSearchPathServiceTest`, `SearchPathMapperTest`, Android 경로 테스트(`SearchPathLocationRecorderTest` 등), Frontend 경로 테스트(`searchPathApi.test.tsx`, `searchPathPagesApi.test.tsx`)가 통과했다.

### 보존할 동작

- `POST /api/search-paths`, `PATCH /api/search-paths/{id}`, `POST /api/search-paths/batch`, `GET /api/search-paths`, `PATCH /api/search-path-segments/{id}`, `POST /api/incidents/{id}/board/search-paths/{segments,changes}/query`
- 이벤트 `PATH_APPENDED`, `SEARCH_PATH_SEGMENT_UPDATED`, `SEARCH_PATH_STARTED`, `SEARCH_PATH_PAUSED`, `SEARCH_PATH_RESUMED`, `SEARCH_PATH_ENDED`
- 테이블 `search_path`, `search_path_segment`, `search_path_gps_point`, `search_path_excluded_point`, `search_path_lifecycle_event`
- fixture `gpsPath`, `pathGuardFixtures`
- Android Outbox의 URL 문자열 구분([오프라인 동기화](#오프라인-동기화) 참고)
- [Android 규칙](../../android/AGENTS.md)이 GPS 수집 주기의 위치로 `LocationRecorder.kt`를 가리킨다. 파일 이름을 바꾸면 이 규칙도 함께 고친다.

### 정리 규모와 의존

작음에서 중간. Backend 계층은 이미 나뉘어 있다. Android 단계도 대부분 제 몫을 한다. 남은 일은 접근 검사와 위치 조회 기록 붙이기, 예외 처리기 세 곳을 `BusinessException`으로 모으기, `SearchPathEventPublisher` 옮기기, 미사용 interface와 웹 미사용 코드 지우기, 상한 중복과 Repository 직접 생성 고치기다. 인수인계의 현재 수색 차수 조회(`AppSearchPathService.java:28`)와 오프라인 동기화에 기댄다. 다만 위의 남은 일은 차수 조회 호출을 바꾸지 않아도 할 수 있다. 상황판이 이 영역의 조회에 기댄다.

## 인수인계와 수색 이력

[인수인계와 수색 이력 기능 정의 문서](../features/handover.md)의 영역이다. 수색 차수, 근무 교대, 인수인계 메모, 수색 이력 요약, 차수 비교를 다룬다.

### 코드 위치

| 플랫폼 | 위치 | 크기 |
|---|---|---|
| Backend | 최상위 `B/operationalperiod`(23파일), `B/dutyshift`(2), `B/handover`(9), `B/summary`(5), `B/opcomparison`(28), `B/domain/summary`(4), `B/client/openai`(4) | 75파일, 약 3,830줄 |
| Backend | `B/api/service`의 `HandoverTimelineApiService`(485줄), `OpComparisonApiService`(398줄), `OperationalPeriodApiService`(264줄), `B/app/service`의 `AppDutyShiftCommandService`(249줄) | - |
| Backend | 차수 Mapper XML이 `resources/mapper/op/`에 있어 Java 패키지 이름 `operationalperiod`와 다르다. | - |
| Frontend | `F/features/handover`(약 5,640줄. `OperationalPeriodReviewWorkspace.tsx` 1,973줄), `F/features/operationalPeriod/api`(약 950줄). 둘 다 테스트 포함 수치다. `operationalPeriods`는 빈 폴더다. | 약 6,590줄 |
| Android | `A/core/operationalperiod`(5파일, 394줄. 근무 교대, 인수인계 메모, 수색 이력 요약 저장소가 섞여 있다), `A/feature/handover`(6파일, 약 2,980줄) | 약 3,370줄 |

### 읽기 어려운 곳

- **미사용**: `ManualOpTransition{Command,Request,Result}`, `OpTransitionedEvent`, `HandoverMemoCreate{Command,Request,Result}`는 운영 adapter가 하나도 없다. 테스트 대역(`ManualOpTransitionMock`, `MockHandoverMemoPort`)만 이 interface를 구현한다. 운영 코드는 이 interface를 거치지 않는다. deletion test: interface와 대역, 대역만 검증하는 테스트(`OpTransitionRedTest` 등)를 지우면 복잡도가 함께 사라진다.
- **겹친 seam**: `EventPublisherPort`가 공용 `EventPublisher`와 겹친다. [이벤트 전달](#이벤트-전달)에 deletion test 결과를 적었다. 그 결과 `OperationalPeriodApiService`는 `OP_TRANSITIONED`를 `EventPublisherPort`로(`:111`), 메모 이벤트를 공용 `EventPublisher`로(`:228`) 보낸다. 한 Service가 발행 경로 두 개를 알아야 한다.
- **locality**: 메모 이벤트 조립 코드가 `HandoverMemoApiService.java:117`과 `OperationalPeriodApiService.java:231-256`에 중복이다. 메모 이벤트 payload를 바꾸면 두 곳을 고쳐야 한다.
- **locality**: 세 Service는 공용 `IdempotentResponseCache`를 받는다. 그러면서도 빈이 없을 때 쓰는 메모리 멱등 Map과 `synchronized`, `String.valueOf` 해시를 Service 세 곳이 따로 갖고 있다(직접 확인). 걷어 낼 대상이다([공통](#공통)).
- **locality**: 한 기능이 최상위 패키지 다섯 개(`operationalperiod`, `dutyshift`, `handover`, `summary`, `opcomparison`)와 Android `core/operationalperiod`에 흩어져 있다. 수색 차수 하나를 이해하려면 여러 패키지를 오가야 한다.
- **미사용**: `@RequireCurrentOp`와 `RequireCurrentOpAspect`는 붙은 곳이 없다(직접 확인). Frontend `F/features/handover/data/handoverApi.ts`는 import처가 없다. S8 슬롯 컴포넌트는 테스트에서만 쓰인다.
- **규칙 위반**: 같은 대상을 두 번 표현한다(`OperationalPeriod`와 `OperationalPeriodRow`, `HandoverMemo`와 `HandoverMemoRow`).
- **이름 문제**: `SearchHistorySummaryGenerationJob`이라는 이름은 영속 작업을 암시한다. 그러나 실제로는 커밋 뒤 콜백에서 동기 호출된다(`:121-133`). 이 이름은 이미 이름 후보로 등록되어 있다.
- 빠진 후보: 없음.

### 동작하지 않거나 확인되지 않은 곳

- 지금도 맞는 차이. 인수인계 메모 대상의 존재와 소속을 조회하지 않는다(`HandoverMemoApiService.java:152-160`). 인수인계 메모 최대 길이를 검사하지 않는다(`@NotBlank`만 있음). 채널, 업무폰, `clientTs`를 인수인계 메모 행에 저장하지 않는다. 차수 전환 때 인수인계 메모는 이전 차수에 저장한다. 수색 이력 요약의 준비 상태는 READY로 고정이다. 수색 이력 요약을 만드는 호출자는 근무 종료와 차수 전환뿐이다. 앱은 미전송 기록을 기다린 뒤 근무 종료를 보낸다. 공용 fixture의 수색 이력 요약 생성 POST(`common-fixtures.json:1941`)와 `retryCta`(`:1953`)가 현재 API와 맞지 않는다.
- 사건당 활성 수색 차수가 2개 이상 생기는 것은 DB 고유 인덱스로 막혀 있다(`V20260428_801__create_operational_period.sql:25-27`). 경합 때 어떤 오류 코드가 나가는지는 확인하지 못했다.
- Service와 DB를 함께 거치는 테스트가 없다. 차수, 근무 교대, 인수인계 메모 API 테스트는 Mapper를 대역으로 둔 WebMvcTest다(`OperationalPeriodApiContractTest.java:45-61`). `OpTransitionRedTest`에는 테스트 대역만 검증하는 테스트가 있다. Mapper를 실제 DB로 검증하는 테스트는 따로 있다. 그래서 테스트가 모두 통과해도 Service와 DB를 함께 거치는 흐름은 확인된 것이 아니다.
- 자동 판단 금지 원칙은 지키는 것으로 본다. 차수 비교에서는 지표 차이와 공통 지역, 다른 지역이라는 사실만 만들어진다(`OpComparisonRegionFactType.java:4-5`). 금지 표현 검사가 있다(`OpComparisonNarrativeValidator.java:18`, `ForbiddenSummaryGuard.java:17-18`). 웹과 앱에 수색 이력 요약 재시도 버튼이 없다. 다만 어떤 차이를 보여 줄지 정하는 고정 임계값(300m, 15% 등, `OpComparisonThresholdFilter.java:15-24`)의 근거는 확인해야 한다.
- 검증: 관련 Backend 테스트, Android 테스트, Frontend 인수인계 화면 테스트가 통과했다.

### 보존할 동작

- `POST /api/operational-periods`, `POST /api/operational-periods/comparisons`, `GET /api/operational-periods/{id}/handover-timeline`, `GET /api/operational-periods/{id}/search-history-summaries`, `GET /api/incidents/{id}/operational-periods`, `/api/duty-shifts`, `/api/handover-memos`
- 이벤트 `OP_TRANSITIONED`, `HANDOVER_MEMO_CREATED`, `SEARCH_HISTORY_SUMMARY_CHANGED`, `OP_COMPARISON_ANALYSIS_CHANGED`
- 테이블 `operational_period`, `duty_shift`, `handover_memo`, `search_history_summary`, `op_comparison_analysis`
- fixture `opTransition`, `searchHistorySummary`

### 정리 규모와 의존

큼. 최상위 패키지 다섯 개와 세 플랫폼에 걸친다. Service와 DB를 함께 거치는 테스트가 없어서 정리 전에 테스트부터 보강해야 한다. 미사용 interface 묶음 삭제는 작다. 겹친 발행 seam 합치기는 테스트 주입을 옮겨야 한다. 수색 구역, 수색 경로, 상황판, 오프라인 패키지가 현재 수색 차수 조회에 기댄다. 그래서 차수 조회의 위치를 먼저 정하면 다른 영역을 옮길 때 덜 흔들린다.

## 영역별 한 줄 요약

| 영역 | 규모 | 한 줄 요약 | 주요 의존 |
|---|---|---|---|
| 공통 | 중간(이동), 큼(예외 모으기) | 오류 변환 규칙과 멱등성 해시 규칙의 locality가 낮다. `apiClient`는 shallow다. 미사용 가드가 4종(`@IdempotentWrite`, `@RequireDevice*` 3개)이다. 붙은 곳이 없는 가드도 2종(`@RequireOpenIncident`, `@RequireCurrentOp`) 있다. | 모든 영역이 기댄다 |
| 인증과 접근 권한 | 큼 | 인증 타입 두 개와 복사된 인증 꺼내기의 locality가 낮다. 마커와 사진 쪽에는 같은 검사가 guard를 포함해 세 곳에 있다. heartbeat를 한 번만 보낸다. | 공통 |
| 사건 생명주기 | 중간에서 큼 | `IncidentEventPublisher`는 `EventPublisher`와 겹친 seam이다. 웹 가져오기 화면 코드는 미사용이다. | 인증, 데이터 파기, 이벤트 전달 |
| 데이터 보존과 파기 | 중간 | 패키지 파기 세 단계는 shallow다. 종료 뒤 파기가 연결되지 않았다. 사진 hook도 빈이 아니다. | 사건 생명주기, 마커와 사진, 오프라인 패키지, 상황판 |
| 이벤트 전달 | 중간 | `EventPublisherPort`가 `EventPublisher`와 겹친 seam이다. 종료 상수와 FCM 전송 시점 규칙이 흩어졌다. | 마커, 사건, 인수인계. 상황판이 기댄다 |
| 상황판 | 큼 | 수집기는 deep module이지만 생성자 interface가 넓다. 운영에서 쓰지 않는 재조회 클래스 묶음과 `BoardRowCursor`가 있다. 웹은 비워진 슬롯을 되살린다. | 거의 모든 영역 |
| 마커와 사진 | 중간 | api와 app의 흐름 복제가 가장 큰 locality 문제다. 사진 삭제와 파기 연결이 남았다. | 오프라인 동기화, 데이터 파기. 오프라인 패키지가 기댄다 |
| 오프라인 동기화 | 작음(미사용, 조립), 큼(응답 복구) | 전송기 조립이 네 곳에 흩어졌다. 응답 복구가 연결되지 않았다. | 모든 앱 쓰기 영역이 기댄다 |
| 오프라인 패키지 | 큼 | Repository의 내부 상수를 테스트가 직접 쓴다. manifest에 사건 접근 검사가 없다. 운영 경로에서 고정 테스트 자료를 넣으려 한다. | 동기화, 마커, 수색 구역, 차수, 파기, 상황판 |
| 수색 구역 | 큼 | maparea 클래스 대부분이 미사용이다. 운영 Service 안에 메모리 경로가 숨어 있다. 동시 수정 보호가 없다. | 인수인계, 상황판, 오프라인 패키지 |
| 수색 경로 | 작음에서 중간 | Android 단계는 대부분 제 몫을 한다. 미사용 `LocationRecorder`와 처리기 세 곳이 남았다. 경로 조회에 사건 접근 검사가 없다. | 인수인계(차수 조회), 동기화. 상황판이 기댄다 |
| 인수인계와 수색 이력 | 큼 | 운영 adapter가 없는 interface 묶음이 있다. 기능이 다섯 패키지에 흩어졌다. Service와 DB를 함께 거치는 테스트가 없다. | 수색 구역, 수색 경로, 상황판, 오프라인 패키지가 기댄다 |

## 정리 순서 추천안

2026-11-15에 사용자가 아래 추천안대로 순서를 정했다.

codebase-design 기준으로 다시 보니, 영역마다 두 종류의 일이 섞여 있다.

- **지우기만 하면 되는 일**: 미사용 module, shallow 단계다. deletion test에서 운영 복잡도가 그대로 사라지므로 동작이 바뀌지 않는다. 기존 테스트로 확인할 수 있다. 테스트에서만 쓰이는 module은 그 테스트도 함께 고친다.
- **locality를 높이는 일**: 흩어진 규칙이나 겹친 seam을 한곳으로 모은다. 동작이 바뀔 수 있으므로 모은 module의 interface에서 테스트를 새로 쓴다.

각 영역은 지우기를 먼저 하고 모으기를 뒤에 하면, 모을 대상이 줄어들어 범위가 작아진다.

순서가 뒤인 영역에 기대는 일은, 앞 단계에서 그 의존을 그대로 두고 남은 일만 한다. 의존을 바꾸는 일은 뒤 단계로 미룬다. 아래 항목마다 무엇을 남기는지 적었다.

1. **공통의 기계적 이동.** `common`을 `global`로 옮기고 미사용 가드(`@IdempotentWrite`, `@RequireDevice*` 3개)를 지운다. 모든 Controller가 이 패키지를 import하므로, 기능마다 나눠 옮기면 같은 import 변경이 여러 PR에 흩어진다. 동작이 바뀌지 않아 기존 테스트로 확인할 수 있다. 오류 변환 규칙을 `GlobalExceptionHandler`와 `ErrorCode`로 모을 기준도 이때 정해 둔다. 그래야 다음 기능들이 같은 방식으로 처리기를 옮긴다.
2. **수색 경로.** 남은 일이 가장 적다. 1회차에는 Android 5단계 위임을 큰 일로 봤지만, deletion test 결과 단계 대부분이 제 몫을 해서 범위가 더 줄었다. 처리기 세 곳을 모으는 일이 1번에서 정한 오류 처리 방식의 첫 적용 사례가 된다. 경로 조회의 사건 접근 검사와 위치 조회 기록 누락도 함께 고칠 수 있다. 수색 경로는 7번 인수인계의 현재 수색 차수 조회(`AppSearchPathService.java:28`)에 기댄다. 2번에서는 이 호출을 그대로 둔다. 차수 조회의 위치를 7번에서 정할 때 이 호출 한 곳을 함께 바꾼다.
3. **마커와 사진.** api와 app 흐름 복제를 한 module로 모으는 일이 이 영역의 중심이다. 효과(locality)는 크고 범위는 한 기능 안에 있다. 3번에서는 `MarkerAuthenticationResolver`와 사진 쪽 검사를 그대로 둔다. guard와 겹치는 검사를 덜어 내는 일은 6번에서 인증 규칙과 함께 한다. 사진 파기 hook은 4번의 결정이 필요하므로, 4번과 묶거나 hook만 남겨 둔다.
4. **사건 생명주기와 데이터 보존과 파기(함께).** 종료 뒤 무엇을 언제 지울지가 한 결정이라 따로 하면 범위가 정해지지 않는다. 파기 연결 방식(필수 hook, 사진 삭제, 실행 시점)은 사용자 결정이 먼저 필요하다. 미사용인 `BlockingIncidentEventPublisher`와 패키지 파기의 shallow 단계는 결정과 무관하게 지울 수 있다. 사건 생명주기의 권한 검사 위치 통일은 6번 인증의 결정에 기대므로, 4번에서는 지금의 검사 위치를 그대로 두고 6번으로 미룬다.
5. **이벤트 전달.** 4번에서 종료 처리가 정해진 뒤 FCM 전송 시점 규칙을 한곳에 모은다. 겹친 seam(`EventPublisherPort`, `IncidentEventPublisher`)을 `EventPublisher`로 합칠지도 이때 정한다. 합치기로 하면 테스트 주입을 `CapturingEventPublisher`로 함께 옮긴다. 옮길 곳은 `IncidentEventPublisher`의 `@MockitoBean` 5곳, `InMemoryEventPublisher`를 쓰는 테스트, `OperationalPeriodApiContractTest`다.
6. **인증과 접근 권한.** 범위는 가장 넓지만, 1번에서 패키지 이동이 끝나면 남는 일은 인증 타입 합치기, `currentAuthentication()` 모으기, 마커와 사진 쪽에서 guard와 겹치는 검사 덜기, heartbeat 연결이다. 4번에서 미룬 사건 권한 검사 위치 통일도 이때 한다. 1회차에 큰 일로 본 `PolicePhonePersistenceService`의 포트 4개는 real seam이라 손댈 필요가 줄었다. heartbeat 주기는 요구를 먼저 정해야 한다. 사용자는 업무폰 heartbeat를 이 단계에서 고치기로 했다(2026-11-15).
7. **인수인계와 수색 이력.** 운영 adapter가 없는 interface 묶음 삭제는 작다. 그러나 Service와 DB를 함께 거치는 테스트를 먼저 보강해야 하므로 준비가 많다. 수색 구역, 상황판, 오프라인 패키지가 차수 조회에 기대므로 그 셋보다 앞에 둔다. 수색 경로도 차수 조회에 기대지만, 2번에서 그 호출을 그대로 두었으므로 이 단계에서 함께 바꾼다.
8. **수색 구역.** maparea 미사용 클래스 삭제는 운영 동작이 바뀌지 않으므로 순서와 무관하게 먼저 할 수 있다. 효과도 크다. 이 삭제를 하면 이벤트 전달 쪽 `SEARCH_AREA_CHANGED` 중복도 함께 줄어든다. Service의 메모리 경로 제거와 동시 수정 보호는, 7번의 차수 조회 위치와 실제 DB 테스트가 정해진 뒤가 안전하다.
9. **오프라인 동기화.** 미사용 코드 제거와 전송기 조립 모으기는 작다. 응답 복구 연결은 `IdempotentResponseCache`를 쓰는 모든 서비스를 건드린다. 앞의 영역들에서 메모리 대체 경로와 따로 만든 해시 규칙을 걷어 낸 뒤가 낫다.
10. **오프라인 패키지.** 의존하는 영역이 가장 많다. 앞 영역의 조회 계약이 정리된 뒤에 Repository의 interface를 좁히는 것이 덜 흔들린다. shallow 파기 단계와 `SearchAreaChangedConsumer` 삭제는 4번에서 미리 할 수 있다.
11. **상황판.** 거의 모든 영역의 결과를 모아 보여 주므로 마지막에 둔다. 미사용 재조회 클래스 삭제는 앞 단계 어디서나 따로 할 수 있다. `BoardRowCursor`는 운영에서 쓰이지 않지만 테스트 assertion 두 곳이 이 타입을 받으므로, 지울 때 그 두 테스트를 함께 고친다.

1회차 추천안에서 순서는 바꾸지 않았다. 다만 수색 경로와 인증의 범위가 줄었다. 수색 구역, 상황판, 오프라인 패키지, 인수인계는 지우기만 하면 되는 부분을 앞 단계에서 미리 떼어 낼 수 있다는 점이 드러났다. 사용자가 기능 하나를 끝까지 정리하는 방식을 유지한다면, 지우기는 해당 기능의 첫 커밋으로 두면 된다.

### 정리 순서와 별개로 처리 시점을 정한 결함

아래는 정리 순서와 별개로 처리 시점을 따로 정한 문제다. 모두 코드를 읽고 내린 판단이다. 실서버나 실기기에서 재현하지 않았다.

2026-11-15에 사용자가 처리 시점을 다음과 같이 정했다.

- 경로 조회와 오프라인 패키지의 사건 접근 검사 누락은 보안 문제라서 정리 순서와 관계없이 먼저 고친다. 앱 쪽 Controller 네 개의 사건 범위 검사도 이때 확인한다.
- 업무폰 heartbeat, 수색 구역의 버전 조건, 고정 테스트 자료, 사건 종료 FCM의 전송 시점은 해당 영역의 차례에 고친다.
- 사건 종료 뒤 파기는 4번에서 파기 연결 방식을 정할 때 함께 다룬다.

| 문제 | 영향 | 근거 |
|---|---|---|
| `GET /api/search-paths`에 사건 접근 검사와 위치 조회 기록이 없다 | 로그인한 계정이면 다른 사건의 개인 위치 경로를 조회할 수 있는 구조다 | `SearchPathController.java:54-67`(직접 확인) |
| 오프라인 패키지 manifest와 설치 보고에 사건 접근 검사가 없다 | 배정되지 않은 사건의 manifest(실종자 정보 포함)를 받을 수 있는 구조다 | `OfflinePackageController.java:29-46`(직접 확인) |
| 업무폰 heartbeat를 `sequence:1`로 한 번만 보낸다 | 상황판의 업무폰 최신성이 첫 수신 뒤 갱신되지 않을 것으로 본다 | `AuthBootstrapCoordinator.kt:152`, `PolicePhonePersistenceService.java:64`(직접 확인) |
| 사건 종료 뒤 파기가 연결되지 않았다 | 종료 사건의 마커, 사진, 경로, 재전송 이력이 남는다 | `IncidentClosedPurgeHandler`, `MarkerPhotoPurgeHookAdapter`(직접 확인) |
| 수색 구역 수정에 버전 조건이 없다 | 동시에 고치면 앞의 결과를 덮어쓸 수 있다 | `SearchAreaMapper.xml:84-91`(직접 확인) |
| 오프라인 패키지 운영 경로에서 고정 테스트 자료를 넣으려 한다 | 운영 DB에 테스트용 manifest와 설치 상태가 생길 수 있다 | `OfflinePackageRepository.java:613-645`(직접 확인) |
| 사건 종료 FCM이 커밋 전에 나간다 | 종료가 롤백돼도 앱이 종료 알림을 받을 수 있다 | `DatabaseEventPublisher.java:86`(직접 확인) |

## 이번 조사에서 확인하지 않은 것

- `git log --grep=refactor`. Bash 권한 범위 밖이라 실행하지 못했다. 마커 정리 이력은 이름 후보 문서의 반영 이력으로 대신했다.
- 실서버, 실기기, 브라우저 동작. 위의 결함은 모두 코드를 읽고 내린 판단이다.
- 앱 쪽 Controller(`AppMarkerController`, `AppSearchPathController`, `PhotoController`, `SearchAreaBoundaryAlertController`)의 사건 범위 검사. 이 Controller들에는 `@RequireIncidentAccess`가 없지만 업무폰 배정 가드로 대신하는지는 확인하지 않았다.
- 이벤트 전달 기능 정의 문서의 145~239줄과 상황판 기능 정의 문서 전체 줄 단위 대조. 하위 조사가 일부만 읽었다.
- `DatabaseEventPublisher`의 생성자 4개 중 운영이 쓰는 생성자. 확인하지 않아 interface가 넓다는 판단까지만 했다.
- `incident/adapter/BlockingReferenceMarkerSeed`와 `ReferenceMarkerSeedFallbackConfig`가 운영에서 등록되는지. 사건과 수색 차수의 `Blocking*` 대체 구현과 모양이 같지만 확인하지 않아 종류를 붙이지 않았다.
- 세 Service(수색 차수, 인수인계 메모, 수색 구역)의 메모리 멱등 대체 경로를 쓰는 테스트가 있는지.
- Frontend `apiClient`를 `realApiClient`로 바꿨을 때 기존 `vi.spyOn` 테스트가 그대로 통과하는지. 테스트를 실행해 확인하지 않았다.
