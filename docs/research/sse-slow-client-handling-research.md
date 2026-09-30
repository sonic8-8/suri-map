# SSE의 느린 연결 처리: 국내 기업 공개 사례 비교

조사일: 2026-09-30. 질문은 **수리맵의 직접 Servlet 비차단 전송이 최선인가, 기업들은 비슷한 문제를 어떻게 해결했는가**다. 회사 공식 기술글·발표 자료를 근거로 삼으며, 공개된 한 팀의 구현을 회사 전체 표준으로 일반화하지 않는다.

## 먼저 확인한 결론

공개 사례만으로 직접 Servlet 구현이 최선이라고 결론 낼 수 없다. 우아한형제들 안에서도 장기 알림에는 WebFlux·Coroutine, 짧은 화면 조회에는 MVC·가상 스레드·SseEmitter를 사용한다. **SSE라는 프로토콜이 같아도 연결 수명·전송량·복구 요구에 따라 구현은 달랐다.** [주문 알림 사례](https://techblog.woowahan.com/23199/), [화면 조회 사례](https://techblog.woowahan.com/26507/)

또한 비차단 프레임워크 사용과 느린 소비자로부터 다른 연결을 보호하는 정책은 별개다. 우아한형제들은 WebFlux 환경에서도 세션의 소비 지연이 Kafka 소비에 전파되는 문제를 겪었다. 따라서 프레임워크 이름보다 **어디서 기다리는지, 얼마나 쌓을지, 넘치면 어떻게 복구할지**를 비교해야 한다. 이는 아래 사례로부터의 적용 추론이다. [운영 문제와 개선](https://techblog.woowahan.com/23199/)

## 비교할 만한 운영 사례

| 팀·사례 | 확인한 구현·대응 | 수리맵과 비교할 때의 한계 |
|---|---|---|
| 우아한형제들 주문접수채널팀, 2025-10-24 | WebFlux·Coroutine의 Flow로 SSE를 반환한다. 세션별 Channel의 버퍼가 0이라 소비 지연 시 생산 코루틴이 대기했고, Kafka 최대 poll 간격을 초과했다. 버퍼 크기와 BufferOverflow 정책을 변경했다. | 정확한 크기·초과 정책은 공개하지 않았다. Servlet 소켓 쓰기의 스레드 고갈을 재현한 사례는 아니다. [공식 글](https://techblog.woowahan.com/23199/) |
| 우아한형제들 가게목록 BFF, 2026-07-28 | 운영 조합으로 MVC·Kotlin·가상 스레드·SseEmitter를 명시한다. 준비된 화면 데이터를 차례로 전달하고 종료한다. | 약 300ms의 유한 응답이다. 장시간 SSE에서 느린 TCP 연결이 전송 스레드를 점유하는 문제까지 해결했다고 볼 수 없다. [공식 글](https://techblog.woowahan.com/26507/) |
| 토스증권 SLASH 24 | SSE로 재조회 이벤트를 전달한다. 공식 PDF 27쪽은 Flux 기반 Controller, 23쪽은 multicast의 directAllOrNothing, 36쪽은 unicast의 onBackpressureBuffer 사용을 보여 준다. | 발표 코드의 일부다. 하위 send 처리·전체 버퍼 상한·느린 연결 종료 정책은 확인하지 못했다. 연산자 이름만으로 무손실·무제한 연결 격리를 보장한다고 해석하지 않는다. [공식 세션](https://toss.im/slash-24/sessions/12), [발표 PDF](https://static.toss.im/slash24/QR/slash24-11.pdf) |

우아한형제들 주문 알림 사례는 연결이 끊겼을 때의 이력 보관·Last-Event-ID 재전송도 별도로 다룬다. 이는 버퍼 정책을 설정했다고 복구까지 자동 완성되는 것이 아님을 보여 준다. 다만 해당 글에서 수정한 BufferOverflow가 어떤 이벤트를 버리거나 재접속시키는지는 공개하지 않았으므로 수리맵에 같은 정책을 적용할 수는 없다. [공식 글](https://techblog.woowahan.com/23199/)

가게목록 BFF 글의 nginx 실험도 유한 응답의 도착 지연을 측정한 결과다. 이 결과를 근거로 수리맵의 장기 스트림에서 버퍼링 설정을 그대로 복사하지 않는다. 글 자체도 끝나는 응답과 끝나지 않는 알림 스트림을 구분한다. [프록시 실험과 적용 범위](https://techblog.woowahan.com/26507/)

## 다른 기업 자료에서 확인한 범위

| 기업 | 이번 조사에서 채택한 근거 | 확인하지 못한 것 |
|---|---|---|
| 네이버 | D2의 [WebFlux·Armeria 설명](https://d2.naver.com/helloworld/6080222)은 Reactive HTTP·RPC 구성과 SSE 예제를 설명한다. | 특정 운영 서비스에서 SseEmitter의 느린 쓰기를 어떤 방식으로 해결했는지 직접 근거는 확보하지 못했다. |
| LINE | LINE 개발자의 [Reactive Streams 설명](https://engineering.linecorp.com/ko/blog/reactive-streams-with-armeria-1)과 프로젝트가 보존한 [Armeria 후속 글](https://armeria.dev/blog/ko/2020/02/19/reactive-streams-armeria-2/)은 비차단 흐름 제어·HTTP 스트리밍·WebFlux 통합을 다룬다. | 라이브러리 기능·교육 예제를 LINE 전체의 Spring SSE 운영 방식으로 볼 수 없다. |
| 카카오 | [카리브 개발기](https://tech.kakao.com/posts/678)는 Go 기반이며 연결마다 Redis를 조회하던 구조에서 서버 내부 전달로 바꾸고 반복 측정한 사례다. [메시징 부하검증 글](https://tech.kakao.com/posts/822)은 WebFlux나 가상 스레드를 이름만 보고 고르지 않고 실제 IO 대기·CPU 부하 조건으로 비교한다고 설명한다. | 카리브는 Spring 구현 사례가 아니다. 이 자료에서 직접 Servlet 비차단 구현과 SseEmitter를 동일한 느린 TCP 조건으로 비교한 결과는 확인하지 못했다. |
| 쿠팡 | 공식 엔지니어링 공개 자료 범위에서 SSE·SseEmitter·느린 소비자 관련 검색을 수행했다. | 이번 범위에서 이 질문에 직접 답하는 운영 근거를 확보하지 못했다. 사용하지 않는다는 뜻은 아니다. |
| 당근 | 공식 기술 자료 범위에서 SSE·SseEmitter 관련 검색을 수행했다. | 같은 문제의 Spring 서버 해결 사례를 확보하지 못했다. 개인·교육 프로젝트 글로 대신하지 않았다. |

## 수리맵에 적용하기 전의 비교 기준

아래는 기업의 정답을 복사하는 권고가 아니라 조사에 따른 비교 관점이다.

1. **전송 방식**: 느린 소켓 쓰기가 스레드를 점유하는가, 대기 중 정상 SSE·일반 HTTP가 계속 처리되는가.
2. **적체 정책**: 연결마다 쌓이는 이벤트의 개수·바이트 상한, 오래 기다린 연결의 종료, 재접속 뒤 이력 복구가 연결되는가.
3. **유지보수 비용**: 직접 관리하는 쓰기 준비·재개·종료 상태를 줄이면서 같은 검증을 통과할 수 있는가.
4. **전환 비용**: 현재 MVC·MyBatis와 JDK를 유지해야 하는가. 프레임워크나 JDK를 바꾸는 비용까지 비교했는가.

현재 구현을 곧바로 되돌리거나 WebFlux로 전환하는 것은 이 조사에서 결정하지 않았다. 같은 느린 연결 조건에서 기존 구현과 더 단순한 대안을 비교해야 최선 여부를 판단할 수 있다.

## 현재 수리맵 구현과 대안

수리맵은 [빌드 설정](../../backend/build.gradle)상 Java 17·Spring Boot 3.5.14·MVC·MyBatis다. [로컬 이슈 13](../issues/local/13-slow-sse-connection-blocks-live-delivery.md)에서 같은 스레드의 순차 `SseEmitter.send()` 지연과, 연결별 작업으로 나눈 뒤에도 느린 소켓이 Tomcat 실행기를 차지하는 문제를 재현했다. 현재 [ServerSentEventStream](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventStream.java)은 Servlet `isReady()`·`WriteListener`를 사용한다. 이는 실제 문제를 해결한 근거지만 다른 설계보다 유지보수 비용까지 우수하다는 근거는 아니다. 재현 조건의 스레드 4개·느린 연결 4개·진단 payload는 운영 한계치가 아니다.

아래는 공식 기능과 현 코드에 따른 설계 비교이며, 채택·성능 측정 결과가 아니다.

| 대안 | 얻는 것 | 남는 비용·확인점 |
|---|---|---|
| 현재 Servlet 비차단 구현 유지 | Java 17·MVC·MyBatis를 유지하며 검증한 느린 소켓 격리를 보존 | 쓰기 준비·재개·종료 상태와 MVC 연결 코드를 직접 관리 |
| `SseEmitter` + 연결별 순서를 지키는 가상 스레드 전송 | Spring의 스트림 전송·완료 기능을 재사용하고 저수준 쓰기 코드를 줄일 가능성 | JDK 변경 필요. 실제 send 실행 경로·JDK/라이브러리의 pinning·연결별 대기 상한·종료 시 자원 회수 검증 필요 |
| WebFlux 기반 SSE | 프레임워크의 비차단 HTTP 전송·흐름 제어 활용 | MVC와 실행 구성을 구분해야 하며 MyBatis/JDBC 조회를 이벤트 루프에서 실행하면 안 됨. 연결별 버퍼·재전송·종료 정책도 여전히 필요 |

Spring Boot 3.5의 가상 스레드 지원은 Java 21 이상이 필요하다. 다만 설정을 켠다고 직접 만든 worker가 자동으로 가상 스레드가 되거나 순차 전송이 병렬로 바뀌지는 않는다. 기존 공용 worker에서 `send()` 완료를 기다리는 구조를 그대로 두지 않고 연결별 전송 흐름을 비교해야 한다. [Boot 실행기 설정](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html), [SseEmitter의 직접 send 호출](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.java)

가상 스레드는 소켓·메모리·DB 커넥션을 무제한으로 만드는 장치가 아니다. Java 21의 synchronized 내부 대기는 carrier 스레드를 붙잡을 수 있고, JDK 24에서는 이 monitor 관련 제약이 개선됐다. 현재 SSE 경로에 pinning이 재현됐다는 뜻은 아니며, 비교할 JDK·Tomcat·Spring 버전에서 확인할 항목이다. [JEP 444](https://openjdk.org/jeps/444), [Oracle JDK 24 변경 사항](https://www.oracle.com/java/technologies/javase/24-relnote-issues.html)

또한 MVC Controller를 `Flux<ServerSentEvent<...>>` 반환형으로 바꾸는 것만으로 WebFlux의 비차단 쓰기가 되지는 않는다. Spring MVC는 reactive 반환형도 실제 응답 쓰기는 별도 스레드의 blocking 방식으로 처리한다고 명시한다. WebFlux에서도 생산자를 늦출 수 없으면 버퍼링·폐기·실패 정책을 선택해야 한다. [MVC reactive 반환형](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#webmvc-ann-async-reactive-types), [WebFlux 흐름 제어·적용 조건](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/new-framework.html)

### 추천하는 다음 판단

**현재 구현을 비교 기준으로 보존하고, JDK 변경을 수용할 수 있다면 `SseEmitter + 가상 스레드`를 먼저 소규모로 대조한다.** MVC·MyBatis와 익숙한 Spring 전송 API를 유지하면서 직접 작성한 전송 코드를 줄일 수 있는지 확인하려는 추천이다. 기업이 사용한다는 이유로 즉시 되돌리거나, 이 대안이 이미 더 낫다고 결론 낸 것은 아니다. Java 17 유지가 필수라면 이 대안은 제외하고 현재 구현 유지와 비차단 프레임워크 도입 비용을 비교한다.

기존 느린 TCP 재현 조건과 실제 상황판의 연결 수·이벤트 크기·빈도로 정상 SSE/HTTP 지연, 연결별 순서, DB 이어받기, 종료 처리, 메모리·작업 적체를 함께 확인한다. 같은 요구를 만족하면서 직접 관리할 코드가 줄어들 때 교체할 근거가 생긴다. WebFlux 전환이나 Kafka·Redis 도입을 이 조사만으로 승인된 작업으로 취급하지 않는다. 브로커 변경은 마지막 HTTP 쓰기가 느린 문제를 직접 해결하지 않는다.

## 조사 한계

- 기업 운영을 직접 재현하거나 벤치마크하지 않았다. 회사별 공개 자료의 유무와 내용만 확인했다.
- 토스 PDF의 쪽수는 1부터 센 값이다. 텍스트 추출로 코드 API를 확인했으며 이미지·도표만으로 성능 수치를 추정하지 않았다.
- 개인 블로그·AI 영상 요약은 검색 단서로만 사용하고 결론의 출처에서 제외했다.
- 이 문서 추가는 제품 동작 검증이나 SSE 구현 변경이 아니다.
