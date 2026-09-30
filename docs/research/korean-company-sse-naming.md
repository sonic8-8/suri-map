# 국내 기업의 Spring SSE 명명 공개 근거 조사

조사일: 2026-09-30. 대상: 네이버·카카오·LINE·쿠팡·우아한형제들·당근·토스의 공식 기술 자료와 공개 코드.

## 결론

초기 검색에서는 자체 명칭을 확보하지 못했으나, `aside.exe`로 브라우저·공식 발표 자료를 추가 조사해 우아한형제들과 토스의 자체 타입·메서드명을 확인했다. 카카오 자료에서는 샘플 메서드를 확인했다. 아래는 공개된 코드의 이름이며 회사 전체의 규칙이나 현재 운영 저장소 원문을 전수 확인한 결과는 아니다.

`NonBlockingSseEmitter`·`ServletSseEmitter`의 기업 사용 근거는 여전히 확보하지 못했다. 수리맵의 이름 선택은 미확정이다.

## Aside로 추가 확인한 이름

### 우아한형제들: 연결과 세션의 역할 구분

[Server-Sent Events로 실시간 알림 전달하기](https://techblog.woowahan.com/23199/), 2025-10-24. 주문접수채널팀의 운영 사례이며 Spring WebFlux·Coroutine 기반이다. 글의 코드에서 확인한 범위는 다음과 같다.

| 확인한 이름 | 근거와 역할 |
|---|---|
| `ServerSentEventController` | 클래스 선언. SSE 연결 요청을 받는다. |
| `ServerSentEventConnectUseCase`, `connect()` | 생성자 파라미터 타입과 호출, 연결 메서드 선언. 연결 흐름을 수행한다. |
| `ServerSentEventSession` | 파라미터 타입 사용. 연결의 이벤트 전송·취소 대상이다. 클래스 선언 전체는 없다. |
| `serverSentEventSessionUseCase.createSession()`·`closeSession()` | 세션 생성·정리 호출. 변수명만으로 선언되지 않은 클래스명을 확정하지 않는다. |
| `session.emit()`·`cancel()` | 연결에 이벤트 전달·취소 호출. |

`UseCase`라는 계층을 수리맵에도 추가해야 한다는 근거는 아니다. 단일 연결의 수명을 `Session`으로 표현한 사례로 참고한다.

### 토스: 전달 방식에 따른 채널 처리

[SLASH 24 공식 PDF](https://static.toss.im/slash24/QR/slash24-11.pdf), [공식 영상](https://www.youtube.com/watch?v=ovGgdPPUZ2I&t=331s). 토스증권의 운영 사례를 설명하는 자료이며 영상 5:31경 WebFlux 구현임을 명시한다. 아래 페이지 번호는 PDF의 1부터 시작하는 쪽수다.

| 확인한 이름 | 근거와 역할 |
|---|---|
| `BroadcastEventController`, `eventConnect()` | 27쪽 선언. 연결 요청을 받아 채널 처리 객체에 위임한다. |
| `BroadcastChannelHandler`, `connect()` | 27쪽 주입 타입·호출. 브로드캐스트 채널에 연결한다. 타입의 전체 선언은 이 페이지에 없다. |
| `UnicastChannelHandler`, `connect()` | 36쪽 클래스·메서드 선언. 입력 스트림과 단일 구독용 채널을 연결한다. |
| `ChannelOutBoundHandler` | 36쪽 상위 타입 사용. 선언·전체 책임은 공개된 이 코드만으로 확정하지 않는다. |
| `createInboundStreamFromRedisPubSub()` | 40쪽 선언. Redis 메시지 수신을 스트림으로 연결하고 해제 시 구독·리스너를 정리한다. |

`ChannelMeterRegistry`도 코드에 나오지만 지표 기록에 사용된다. 이를 SSE 연결 등록소의 명명 선례로 사용하지 않는다.

### 카카오: 샘플 메서드에서 동작 표현

[if(kakao)25 세션](https://if.kakao.com/2025/session?sessionId=26), [공식 PDF](https://t1.kakaocdn.net/service_if_kakao_prod/admin/752993a9019900001.pdf?download), [영상 20:34 이후](https://www.youtube.com/watch?v=vEyrAWafm64&t=1234s). Spring WebFlux·Redis Pub/Sub 기반 운영 사례이며 발표자는 해당 코드를 샘플이라고 소개한다.

- `subscribe()`: SSE 구독 처리 메서드. 영상 21:02의 선언과 `StockCount` 타입 사용을 확인했다. 이를 담는 서버 클래스명은 확인하지 못했다.
- `publish()`: PDF 66쪽 선언. 재고 변경을 관찰해 샘플링한 이벤트를 Redis 채널로 전달한다.
- `Sinks`, `ServerSentEvent`는 라이브러리 타입이다. 카카오가 직접 만든 클래스 이름으로 분류하지 않는다.

## 확인한 공식 자료와 범위

| 기업 | 공개 자료에서 확인한 사실 | 명명 근거의 한계 |
|---|---|---|
| 우아한형제들 | [BFF 서버에 SSE를 도입한 이유](https://techblog.woowahan.com/26507/), 2026-07-28. 가게목록 BFF의 SSE 적용과 운영 조합인 Spring Boot MVC·Kotlin·가상 스레드·`SseEmitter`를 명시한다. | `SseEmitter`는 사용하는 Spring 클래스다. 이 글에서 자체 백엔드 클래스·메서드 선언 및 구현 저장소 링크는 확인하지 못했다. |
| 네이버 | [Spring WebFlux와 Armeria를 이용하여 Microservice에 필요한 Reactive + RPC 동시에 잡기](https://d2.naver.com/helloworld/6080222), 2020-02-19. `Flux<String>`와 `MediaType.TEXT_EVENT_STREAM_VALUE`의 SSE 반환형 사용 가능성을 설명한다. | 일반 WebFlux 설명과 예제다. 이 내용을 네이버 운영 서비스의 SSE 등록소·전송 클래스 명명으로 해석하지 않는다. |
| 카카오 | 초기에는 공식 세션 목록만 확인했다. | 이후 Aside에서 공식 영상·PDF를 확인했다. 위 추가 조사로 갱신하며 제3자 AI 요약은 근거로 사용하지 않는다. |
| LINE | [Armeria로 Reactive Streams와 놀자! - 2](https://engineering.linecorp.com/ko/blog/reactive-streams-with-armeria-2)에 `ServerSentEvents.fromPublisher(...)` 코드가 나온다. | Armeria의 내장 API 명칭이다. Spring 기반 애플리케이션의 자체 클래스·메서드 명명 사례와 구분해 제외한다. |
| 토스 | [공식 기술글](https://toss.tech/article/41789)은 Spring AI 기반 MCP 사례다. | 해당 글에서 명명 근거를 확보하지 못했지만, 별도의 SLASH 24 자료에서 위 자체 이름을 확인했다. |
| 쿠팡 | 공식 기술블로그 범위에서 SSE·Spring·`SseEmitter` 관련 자료를 검색했다. | 이번 조사에서 Spring SSE 사용자 정의 명칭의 직접 근거를 확인하지 못했다. |
| 당근 | 공식 공개자료 범위에서 Spring SSE 명명 사례를 검색했다. | 이번 조사에서 Spring SSE 사용자 정의 명칭의 직접 근거를 확인하지 못했다. |

## 추가 확인과 제외 기준

- 네이버페이·카카오페이 공식 기술블로그 및 LINE의 이전·현재 기술블로그도 검색했지만 채택 가능한 자체 Spring SSE 명칭을 확보하지 못했다.
- [Pinpoint 공개 저장소](https://github.com/pinpoint-apm/pinpoint)의 `master` 파일 경로 및 `web/realtime` 디렉터리를 GitHub API로 좁게 확인했다. SSE 구현의 클래스 선언을 확보하지 못했으며 저장소 전체 내용·과거 이력을 조사한 결과는 아니다.
- 초기 카카오 영상 접근 제한은 Aside 브라우저로 해소했다. 공식 세션에서 연결한 PDF와 영상 화면을 대조했다.
- 개인 블로그·개인 예제·교육 프로젝트의 `SseEmitterRepository`, `NotificationService` 등을 회사 운영 코드나 회사 전체 관행으로 대체하지 않는다.
- 공식 글에서 라이브러리 타입을 사용하는 것과 회사가 직접 정의한 클래스·메서드 이름을 분리한다. 향후 공개 OSS에서 이름을 확인하더라도 내부 제품 코드·회사 전체 관행으로 일반화하지 않는다.

## 검증 상태와 다음 확인점

문서 조사만 수행했다. 제품 코드·API·테스트·이름은 변경하지 않았으며 제품 테스트를 실행하지 않았다.
다음은 확인한 `Session`·`ChannelHandler`의 역할을 수리맵의 연결 상태·연결 등록소·Servlet 전송 객체와 비교하는 일이다. WebFlux 기반 사례를 이유로 MVC·MyBatis를 전환하거나 `UseCase`·채널 추상화를 새로 도입하지 않는다. 회사 사례만으로 `Registry`가 틀렸다거나 `Handler`가 정답이라고 판단하지 않는다.

Aside 조사 중 카카오 자료받기 버튼이 PDF를 Windows 다운로드 폴더에 저장했다. 저장소에는 원본 PDF·영상·슬라이드를 복제하지 않고 출처와 요약만 남겼다.
