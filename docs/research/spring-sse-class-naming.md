# Spring의 SSE 클래스 명명 사례와 수리맵 적용 후보

- 조사일: 2026-09-30
- 범위: 현재 작업 트리의 연결 등록소·연결별 대기열·Servlet 응답 처리 클래스와 Spring 공식 자료의 역할 비교.
- 상태: 조사·추천만 수행했다. 클래스·테스트 이름, 구현, 패키지, 합의 용어는 변경하지 않았다.
- 범위 정정: 사용자가 요청한 것은 국내 기업의 Spring SSE 구현 명명 사례다. 아래는 프레임워크 비교 자료이며, 추천 채택은 보류한다. 이후 [기업 공개 사례 조사](korean-company-sse-naming.md)에서 우아한형제들의 Session·토스의 ChannelHandler 사용을 확인했으며 실제 역할을 구분해 비교한다.
- 한계: 공식 `current` 자료에 나타난 이름과 계약을 비교했다. Spring 전체의 보편적 명명 규정이나 사용 빈도를 입증한 조사가 아니며, 저장소의 Spring Boot 3.5.14 실행 호환성 검증도 아니다.

## 현재 클래스가 실제로 맡은 역할

| 클래스 | 현재 책임과 경계 |
| --- | --- |
| [SseConnectionRegistry](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnectionRegistry.java) | 사건·계정별 `SseLiveEventSink`를 두 맵에 등록·해제하고, 대상별 전달·전송 실패 연결 제외·사건 연결 종료를 수행한다. 단순 목록 반환 객체는 아니다. |
| [SseConnection](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnection.java) | 단일 구독의 재전송·실시간 이벤트 순서, 중복 제외, 대기열 한도, 종료 이벤트 처리를 담당한다. 프레임을 직렬화하고 아래 클래스에 쓰기를 요청한다. |
| [SseStreamResponse](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventStream.java) | `DeferredResult<Void>`의 하위 타입이다. `AsyncContext` 작업 예약, `WriteListener`·`isReady()` 기반 쓰기·flush·재개, MVC 응답 종료와 등록 해제를 담당한다. DTO도 Spring `SseEmitter`의 하위 타입도 아니다. |

[Service](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java)는 응답 객체·연결·등록 해제 동작을 연결한다. [Controller](../../backend/src/main/java/com/surimap/api/controller/sse/ServerSentEventController.java)는 이를 MVC에 반환하며, [Config](../../backend/src/main/java/com/surimap/config/ServerSentEventConfig.java)의 비동기 처리 콜백이 Servlet 처리를 시작한다. 등록소가 직접 Servlet 소켓에 쓰는 구조는 아니다.

## Spring 공식 자료에서 확인한 역할

| 이름 | 공식 계약에서 확인한 사실 | 수리맵과 비교할 때의 한계 |
| --- | --- | --- |
| Registry (등록소): `SessionRegistry` | `SessionInformation`의 등록·제거, principal별 세션 조회, 마지막 요청 시각 갱신을 제공한다. [공식 API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/session/SessionRegistry.html) | 연결을 묶어 등록·조회하는 역할의 근거다. SSE 전송·연결 종료를 담당하는 클래스의 직접 선례는 아니다. |
| Registry (등록소): `SimpUserRegistry` | 현재 연결된 사용자를 이름으로 조회하고 사용자 집합·수·일치하는 구독을 조회한다. [공식 API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/user/SimpUserRegistry.html) | 이 인터페이스에는 직접 등록·전송하는 메서드가 없다. `Registry`라는 이름에서 모든 메서드 구성을 기계적으로 추론할 수 없다. |
| Emitter (응답 스트림 전송 객체): `ResponseBodyEmitter`, `SseEmitter` | 전자는 여러 객체를 `HttpMessageConverter`로 직렬화해 응답에 보내고, 후자는 그 하위 타입으로 SSE 형식을 지원한다. 예제는 `send(...)`와 `complete()`를 사용한다. [공식 MVC 설명](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#webmvc-ann-async-http-streaming) | 수리맵은 이미 직렬화한 바이트를 Servlet에 직접 쓴다. 같은 문서가 설명하는 MVC 스트리밍의 개별 blocking write와도 구현 방식이 다르다. 역할의 유사성이지 타입·처리 계약의 동일성이 아니다. |
| Session (단일 연결 세션): `WebSocketSession` | 연결의 식별자·속성·인증 주체·열림 상태와 `sendMessage(...)`·`close()`를 제공한다. [공식 API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/WebSocketSession.html) | 한 연결의 송신·수명을 포괄한다. 수리맵에는 이미 `SseConnection`이 있어 응답 처리 객체만 Session (단일 연결 세션)으로 바꾸면 둘의 경계가 더 흐려질 수 있다. |
| TransportHandler (전송 방식별 요청 처리기) | SockJS의 전송 방식에 맞는 요청을 처리하고, 별도 `SockJsSession`과 `WebSocketHandler`를 전달받는다. [공식 API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/sockjs/transport/TransportHandler.html) | 단일 열린 응답의 상태를 가진 객체와 역할이 다르다. 이 사례만으로 `SseTransport`나 `SseHandler`가 Spring 표준 이름이라고 주장할 수 없다. |

위 자료는 각 클래스·인터페이스의 역할을 정의한다. 사용자 정의 SSE 클래스에 특정 접미사를 강제하는 명명 규정은 이 자료들에서 확인하지 못했다. 아래 추천은 이 사례와 현재 코드에서 유추한 판단이다.

## `SseConnections`는 일급 컬렉션을 뜻하는가?

`SseConnections`는 연결 컬렉션을 감싼 객체로 읽히기 쉽다. 하지만 현재 구현에서 확인되는 필요는 컬렉션을 감싸는 패턴 자체보다 사건·계정별 등록과 해제, 해당 연결에 대한 전달·종료다. 컬렉션을 캡슐화했다는 형태와 클래스의 주된 역할은 구분해야 한다.

따라서 `SseConnectionRegistry` 유지를 추천한다. Registry (등록소)는 Spring의 실제 용례가 있고 대상별 연결 등록이라는 책임을 더 직접적으로 드러낸다. 다만 수리맵은 Spring의 위 등록소보다 전달·종료 책임이 넓으므로 완전히 같은 설계라고 설명하지 않는다. 이름을 맞추려고 별도 서비스나 컬렉션 계층을 추가할 근거는 없다.

## `SseStreamResponse`의 이름 후보

이 비교는 기존 구현을 그대로 둔다는 조건이다. Emitter (응답 스트림 전송 객체)는 결과 데이터를 담은 객체보다 실제 전송·완료를 수행하는 객체임을 드러낼 수 있다. 다만 현재 클래스 자체는 이벤트 객체의 조립·직렬화가 아니라 준비된 프레임 쓰기와 응답 수명을 맡는다.

| 프로젝트 후보 | 예시와 기대되는 해석 | 주의점 |
| --- | --- | --- |
| `NonBlockingSseEmitter` (비차단 쓰기 기반 SSE 전송 객체) | `new NonBlockingSseEmitter(initialize)` — 쓰기 가능 알림에 맞춰 SSE 전송을 재개하고 완료하는 객체 | Spring 정식 클래스명·하위 타입이 아니다. 비차단은 소켓 쓰기에 한정된다. 예약된 동작에는 재전송 DB 조회도 포함되므로 전체 처리가 비차단이라는 뜻은 아니다. |
| `ServletSseEmitter` (Servlet 기반 SSE 전송 객체) | `new ServletSseEmitter(initialize)` — Servlet의 쓰기·비동기 응답 수명에 연결된 전송 객체 | 역시 프로젝트 후보다. Spring `SseEmitter`도 Servlet MVC에서 사용하므로 그 클래스와의 구현 차이를 드러내는 힘은 상대적으로 약하다. |

둘 중에서는 `NonBlockingSseEmitter` (비차단 쓰기 기반 SSE 전송 객체)를 추천한다. 현재 구현의 중요한 차이를 드러내고 `Response`를 DTO로 읽는 혼동을 줄일 수 있다. 클래스 설명에는 `DeferredResult<Void>`와 Servlet 쓰기 기반임을 유지해야 한다. 이름만 보고 Spring `SseEmitter` 계약을 기대하게 만들 수 있다는 단점은 남는다.

`SseSession`·`SseConnection`으로 응답 처리 객체만 바꾸는 안은 추천하지 않는다. 이미 존재하는 `SseConnection`이 단일 구독을 관리해 의미가 겹친다. `Writer`만으로 좁히면 작업 예약·응답 종료·등록 해제 책임이 드러나지 않는다. `SseResponseStream`은 어순만 바뀌어 `Response`에 대한 사용자의 우려를 해소하지 못한다.

## 변경·검증 상태와 다음 결정

- 이 조사 문서만 추가했다. 후보는 미합의이며 코드·테스트·`CONTEXT.md`에는 적용하지 않았다.
- 현재 파일과 호출 관계, 공식 링크, 문서 diff를 확인했다. 제품 테스트는 실행하지 않았으며 동작 검증 결과로 보고하지 않는다.
- 다음 결정은 `SseConnectionRegistry` 유지와 응답 처리 객체의 후보 채택 여부다. Spring `SseEmitter`로 런타임을 교체하거나 새 추상화를 만드는 일은 이 명명 비교의 범위가 아니다.
