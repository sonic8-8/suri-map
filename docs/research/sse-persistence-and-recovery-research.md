# SSE 전달 흐름과 재접속 복구 사례

조사일: 2026-09-22. 수리맵의 현재 마커 생성·SSE 전달·재접속 흐름을 확인하고, ntfy·Mercure와 Spring Modulith의 복구 방식을 비교한다. 로컬 코드는 `626c2ff1` 이후 작업 트리 기준이며 배포 서버를 확인한 결과가 아니다. 외부 코드 링크는 조사한 커밋에 고정했다. 아래 내용은 새 구현의 완료 기록이나 도입 결정이 아니다.

2026-09-26 후속 변경: Dispatcher·PollingWorker는 Service·Worker로 교체했다. 아래는 조사 당시 설명이며, 현재 구현 범위는 [기능 기록](../features/event-delivery.md#sse-재연결과-중복-처리)을 따른다. 옛 두 파일은 커밋 `626c2ff1`에 보존돼 있다.

2026-09-28 후속 변경: 재접속도 DB 페이지 조회로 연결했다. 아래 메모리 저장소 설명은 조사 당시 상태이며 실제 서버 재시작 검증 완료를 뜻하지 않는다.

## 수리맵의 현재 흐름

핵심 차이는 **이벤트 내용은 DB에 남지만, SSE 재접속은 메모리 이력만 조회한다**는 점이다. 지원 요청·발견 알림을 만드는 마커의 새 요청을 기준으로 보면 다음과 같다.

```text
업무폰의 마커 생성 요청
  ↓
[하나의 업무 DB 트랜잭션]
권한·중복 요청 확인
  → 마커 저장 + 미리 업로드된 사진 연결
  → event_dispatch_job에 MARKER_CREATED 저장
  → marker_notification에 지원 요청·발견 알림 저장
  → event_dispatch_job에 해당 알림 이벤트도 저장
  → 같은 요청 재처리에 쓸 응답 저장
  → COMMIT
  ↓
afterCommit에서 전송 처리 호출 / 별도 worker도 PENDING 작업 조회
  → 메모리에 사건별 순번과 재전송 이력 추가
  → 연결된 브라우저에 SSE 전송
  → 전송 작업의 완료·실패 상태 기록
```

마커·사진의 순서는 [AppMarkerService](../../backend/src/main/java/com/surimap/app/service/marker/AppMarkerService.java), 별도 알림은 [MarkerNotificationService](../../backend/src/main/java/com/surimap/app/service/marker/MarkerNotificationService.java), 응답 저장은 [IdempotentWriteService](../../backend/src/main/java/com/surimap/sync/idempotency/IdempotentWriteService.java)에서 확인했다. 일반 마커는 지원 요청·발견 알림 단계를 생략한다. 사진 파일의 업로드와 첨부 관계의 DB 저장은 별개다.

`publish()`는 이 시점에 브라우저로 바로 보내는 메서드가 아니다. [DbEventHub](../../backend/src/main/java/com/surimap/global/event/DatabaseEventPublisher.java)는 이벤트를 INSERT하고 해당 이벤트의 동기 consumer를 실행한 뒤 커밋 후 SSE 전달을 예약한다. 마커 알림의 FCM도 별도 커밋 후 콜백이다. `afterCommit` 자체가 비동기 실행을 뜻하지 않으며, 조사 당시 `EventDispatchJobDispatcher`는 콜백에서 직접 전송을 호출했다.

별도 `EventDispatchJobPollingWorker`도 미처리 작업을 조회했다. [Mapper SQL](../../backend/src/main/resources/mapper/sse/ServerSentEventJobMapper.xml)의 대상은 `PENDING`뿐이다. `FAILED` 자동 재시도나 `COMPLETED` 이력 재구성과는 다르다. 전송 대상이 없거나 끊긴 연결을 제외한 경우에도 완료 처리에 도달할 수 있으므로, `COMPLETED`는 브라우저 수신·화면 표시 확인이 아니다. [연결별 전송 처리](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnectionRegistry.java)

조사 당시 재접속은 브라우저가 `Last-Event-ID: 42`처럼 이전 위치를 보내면 `SseReplayService`가 **메모리에서 42 이후 이벤트**를 읽는 흐름이었다. 저장소는 [당시 InMemorySseReplayEventStore](https://github.com/sonic8-8/suri-map/blob/070dfc90f4f82be4773d573617c787e542831f66/backend/src/main/java/com/surimap/eventhub/stream/InMemorySseReplayEventStore.java)였다. 현재 연결은 위 기능 기록을 따른다.

따라서 서버가 재시작되면 DB의 이벤트 내용은 남아도 메모리의 순번·이력을 잃는다. 현재 조회 경로는 DB 기록으로 그 이력을 복원하지 않는다. 브라우저의 마지막 위치도 두 구독 함수 안의 지역 변수여서 페이지 새로고침에 유지되지 않는다. 상황판 재조회와 놓친 알림 팝업 복구는 별개다. [상황판 갱신 구독](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts), [알림 구독](../../frontend/src/features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications.ts)

### 구현 전에 검증할 부분

- Dispatcher의 커밋 후 콜백은 같은 인스턴스의 `dispatchJob()`을 호출한다. 기본 Spring 프록시 방식에서 이 호출에는 메서드의 `REQUIRES_NEW`가 적용되지 않는다. 새 트랜잭션이 적용되는 worker 경로와 구분해 상태 기록의 실제 동작을 검증해야 한다. 이번 조사에서 상태 저장 실패를 재현한 것은 아니다. [Spring의 내부 호출 설명](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html)
- [SseStreamService](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java)는 과거 이력을 조회한 뒤 실시간 연결을 등록한다. 그 사이의 이벤트와 과거 이력 전송 중 새 이벤트를 어떻게 처리할지 검증해야 한다. 메모리의 순번 증가만으로 동시 실시간 전달 순서가 보장되는 것도 아니다.
- 업무·이벤트를 함께 저장하는 동작, 미처리 작업 재시도, 완료 이벤트의 재전송, 화면별 수신 위치 복원은 서로 다른 검증 대상이다. DB 저장으로 바꾸는 합의만으로 네 가지가 모두 완성되지는 않는다.

## 먼저 구분할 것

- 이벤트 식별자는 “어느 이벤트인가”, 재개 위치는 “어디 다음부터 읽는가”를 나타낸다. 같은 값을 두 용도로 사용할 수 있지만 숫자 순번일 필요는 없다. SSE의 `Last-Event-ID`는 문자열이며, 브라우저가 재연결할 때 마지막으로 기억한 값을 보낸다. [WHATWG SSE](https://html.spec.whatwg.org/multipage/server-sent-events.html#the-last-event-id-header)
- 브라우저는 이벤트 파싱 과정에서 마지막 ID를 갱신하고 이후 이벤트 핸들러 실행을 예약한다. 따라서 이 값도 애플리케이션의 처리·화면 반영 완료 확인은 아니다. 새 `EventSource`의 마지막 ID는 빈 문자열에서 시작한다. [WHATWG 처리 규칙](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)
- 서버의 쓰기 성공, 브라우저의 수신, 화면 반영은 다른 단계다. 아래 서비스의 저장·처리 완료 상태를 브라우저별 수신 확인으로 읽지 않는다. SSE 표준에는 화면 반영 성공을 서버에 돌려주는 절차가 없다. [WHATWG SSE 처리 모델](https://html.spec.whatwg.org/multipage/server-sent-events.html#processing-model-9)

## ntfy: 알림 캐시로 끊긴 구간 다시 읽기

즉시 알림 경로는 `POST → 메시지 생성 → 실시간 전달 요청 → 캐시 저장 → POST 응답`이다. `handlePublishInternal()`은 `dispatch()` 뒤에 `AddMessage()`를 호출하며, 구독자 전달은 별도 goroutine으로 진행한다. 따라서 “DB 저장 완료 후에만 실시간 전송”하는 사례로 인용할 수 없다. ntfy는 외부 발행자의 업무 DB 트랜잭션까지 함께 커밋하는 서비스도 아니다. [발행 코드](https://github.com/binwiederhier/ntfy/blob/10cb6506f836dbb00bb77e3b52669f6ace37f555/server/server.go#L935-L965), [구독자 전달 코드](https://github.com/binwiederhier/ntfy/blob/10cb6506f836dbb00bb77e3b52669f6ace37f555/server/topic.go#L106-L129)

기본 캐시는 메모리의 12시간 보관이다. `cache-file`로 SQLite 파일을 사용하면 보관된 메시지가 서버 재시작 뒤에도 남으며, 현재는 `database-url`로 PostgreSQL도 선택할 수 있다. `cache-duration=0`이면 재조회할 캐시가 없고, 비동기 배치 저장 옵션을 켜면 캐시 쓰기 완료 시점도 달라진다. 영속 저장은 무기한 보관을 뜻하지 않는다. [공식 캐시 설정](https://docs.ntfy.sh/config/#message-cache)

재연결은 `since=<message ID>`·시각·기간으로 캐시를 읽는 방식이다. `poll=1`은 조회 후 종료하고 일반 구독은 스트림을 유지한다. 현재 문서는 재조회 크기 제한과 잘렸음을 알리는 `X-Messages-Truncated`도 명시한다. **보관된 범위만 복구**하며, 연결이 끊긴 동안의 모든 이벤트를 영구 복구하는 보장은 아니다. [구독·캐시 조회 API](https://docs.ntfy.sh/subscribe/api/#fetch-cached-messages)

공개 SSE 인코더는 메시지 ID를 JSON 데이터에 넣지만 SSE의 `id:` 필드는 쓰지 않는다. 따라서 ntfy의 `since=` 복구를 브라우저의 자동 `Last-Event-ID` 재개와 동일시하면 안 된다. 또한 구독자별 비동기 전달 자체는 발행 호출 순서와 전송 순서의 일치를 보장하는 장치가 아니다. [SSE 인코더](https://github.com/binwiederhier/ntfy/blob/10cb6506f836dbb00bb77e3b52669f6ace37f555/server/server.go#L1397-L1408), [전달 코드](https://github.com/binwiederhier/ntfy/blob/10cb6506f836dbb00bb77e3b52669f6ace37f555/server/topic.go#L106-L129)

## Mercure: 영속 history와 SSE 재개 위치 연결

공개 BoltDB transport 경로는 `발행 요청 → ID 부여 → BoltDB에 이벤트 저장 → 구독자에게 전달 → 재연결 시 history 조회`다. `Dispatch()`는 잠금을 유지하며 저장한 다음 구독자에게 넘긴다. 저장 트랜잭션에서 내부 순번을 얻어 `순번 + 이벤트 ID`를 키로 사용한다. 외부 UUID/ID는 이벤트를 찾는 값이고 내부 순번은 보관 순서를 구성한다. [BoltDB 구현](https://github.com/dunglas/mercure/blob/0ce390af83b1f8414374bc7ddf28e9fe39c7bb24/bolt.go#L114-L168)

구독자를 등록할 때 같은 잠금 아래 history의 상한 순번을 잡고, 그 상한까지만 과거 이벤트를 전달한다. 이는 이 transport 안에서 history와 실시간 전달의 경계를 다루는 구현이다. 외부 업무 DB의 커밋 순서까지 보장한다는 뜻은 아니다. [구독 등록·history 조회](https://github.com/dunglas/mercure/blob/0ce390af83b1f8414374bc7ddf28e9fe39c7bb24/bolt.go#L147-L168), [상한 검사](https://github.com/dunglas/mercure/blob/0ce390af83b1f8414374bc7ddf28e9fe39c7bb24/bolt.go#L218-L258)

재연결은 `Last-Event-ID` 이후의 history를 요청한다. 보관에서 제거된 ID 등으로 공백이 생길 수 있으므로 요청·응답의 `Last-Event-ID`를 비교하고 원본 리소스를 다시 조회하는 절차가 있다. 기본 브라우저 `EventSource`는 응답 헤더를 노출하지 않는 제약도 있다. [Mercure 재연결 명세](https://mercure.rocks/spec#reconnection-state-reconciliation-and-event-sourcing-reconciliation)

보존 범위는 transport 설정에 달린다. 조사한 BoltDB 코드는 `size=0`이면 크기 기반 정리를 하지 않고, 양수이면 오래된 순번을 정리한다. 요청 ID를 찾는 탐색 상한도 있으며 못 찾으면 `earliest`를 반환한다. “파일 DB이므로 언제나 원하는 위치까지 복구된다”는 보장은 아니다. [history 탐색·누락 처리](https://github.com/dunglas/mercure/blob/0ce390af83b1f8414374bc7ddf28e9fe39c7bb24/bolt.go#L260-L329), [순번 저장·정리](https://github.com/dunglas/mercure/blob/0ce390af83b1f8414374bc7ddf28e9fe39c7bb24/bolt.go#L347-L410)

## Spring Modulith: 업무와 함께 처리할 일을 남기기

기본 커밋 후 리스너 흐름은 `업무 트랜잭션에서 이벤트 발행 + 리스너별 publication 기록 → 커밋 후 리스너 실행 → 성공 시 완료 기록`이다. 원래 업무 트랜잭션에 publication log를 포함하고, 실패·미완료 publication을 재처리할 수 있게 남긴다. 재시작 시 재발행은 설정으로 활성화한다. 이것은 SSE history 서비스가 아니라 **서버 측 이벤트 처리 복구**의 사례다. [공식 Event Publication Registry 설명](https://docs.spring.io/spring-modulith/reference/events.html#publication-registry)

조사한 2.x 계열 공개 구현도 리스너 호출 전에 publication을 저장한다. 처리 상태의 `markProcessing()`·`markCompleted()`·`markFailed()`에는 `REQUIRES_NEW`가 있지만, 이는 이미 기록한 publication의 상태 변경이며 사건별 SSE 순번 발급 방식의 근거가 아니다. 이 상세 상태 모델을 1.4 계열과 동일하다고 가정하지 않는다. [발행 구현](https://github.com/spring-projects/spring-modulith/blob/ce7cd2500b503a56486af4d547aad515d80d31d1/spring-modulith-events/spring-modulith-events-core/src/main/java/org/springframework/modulith/events/support/PersistentApplicationEventMulticaster.java#L85-L115), [상태 기록 구현](https://github.com/spring-projects/spring-modulith/blob/ce7cd2500b503a56486af4d547aad515d80d31d1/spring-modulith-events/spring-modulith-events-core/src/main/java/org/springframework/modulith/events/core/DefaultEventPublicationRegistry.java)

완료는 리스너가 성공했다는 뜻이다. 기본 완료 모드는 기록을 남기므로 정리가 필요하고, 삭제·보관 모드도 제공한다. 이것만으로 브라우저별 커서, SSE 재전송 이력, 동시 업무의 전송 순서가 생기지는 않는다. [완료 의미·보관 모드](https://docs.spring.io/spring-modulith/reference/events.html#publication-registry.completion)

## 수리맵에 대조할 때의 추론 — 조사 당시 미합의

위 사례를 수리맵에 적용할 때는 다음 역할을 따로 확인해야 한다. 아래는 조사로 얻은 비교 관점이지 새 구현 요구나 제품 도입 권고가 아니다.

1. 업무가 커밋됐을 때 처리할 이벤트가 함께 남는가: Spring Modulith가 보여 주는 경계다.
2. 이미 전송을 시도한 이벤트도 재연결 때 다시 읽을 수 있는가: ntfy·Mercure의 보관 이력이 다루는 경계다. 처리 작업의 완료 표시와 역할이 다르다.
3. 재개 위치가 어떤 보관 순서를 가리키며, 보존 기간·용량을 넘으면 어떻게 알아차리는가: ID·순번만 추가한다고 해결되는 항목이 아니다.
4. 업무 커밋 순서·history 저장 순서·실시간 전송 순서를 어느 범위에서 맞출 것인가: 각 서비스의 잠금·비동기 처리와 별개로 수리맵 요구를 정해야 한다.

조사 자료는 **“업무 커밋 후 별도 PostgreSQL 트랜잭션에서 사건별 순번을 붙이는 것이 업계 표준”이라는 주장을 뒷받침하지 않는다.** Mercure는 자신의 history 저장 중 순번을 붙이고, Spring Modulith는 업무 트랜잭션에 처리 기록을 포함한다. 어느 사례도 수리맵의 순번 확정 시점을 대신 결정하지 않는다. Kafka·Redis·외부 허브 도입 역시 이 조사에서 결정하지 않았다.

## 조사 검증 범위

수리맵의 코드·SQL·설정·관련 테스트 내용과 외부 공식 문서·고정 커밋의 공개 코드를 읽어 대조했다. 제품 코드를 변경하거나 테스트·서버 재시작·실제 브라우저 복구를 실행하지 않았다. 외부 서비스도 직접 구동하거나 장애·동시성·처리량을 측정하지 않았다. 위의 복구 한계와 경쟁 조건은 코드에서 확인한 구조와 검증할 위험을 구분한 설명이다.

## 지도 갱신 방식과 브로커 선택

추가 조사일: 2026-09-22. 아래는 외부 공식 근거와 수리맵에 대조할 추론이다. Kafka는 4.2 문서를 기준으로 읽었으며 도입 버전을 정한 것이 아니다. SSE·WebSocket은 브라우저 전달 방식이고, Kafka·Redis Streams는 서버 쪽 이벤트 보관·소비 수단이므로 서로 대체재가 아니다.

### 수리맵의 실제 경로 갱신 흐름

```text
업무폰의 GPS 묶음 요청
  → GPS 좌표·측정 시각·저장 순서와 PATH_APPENDED 이벤트를 DB에 저장
  → 커밋 후 SSE로 경로 변경 통지
  → 브라우저가 상황판 query를 무효화하고 REST 재조회 요청
  → 서버가 조회 범위의 경로별 전체 좌표·구간·제외 좌표를 읽어 응답 조립
  → 브라우저가 경로 GeoJSON을 만들고 MapLibre setData로 지도 갱신
```

- [SearchPathService.appendPointsOnce](../../backend/src/main/java/com/surimap/api/service/path/SearchPathService.java)는 수용한 좌표를 묶어서 저장하고 이벤트를 발행한다. [SearchPathEventPublisher](../../backend/src/main/java/com/surimap/global/event/SearchPathEventPublisher.java)의 `PATH_APPENDED`에는 경로 ID·상태·버전·서버 시각이 들어가지만 좌표는 없다. payload의 `sequence`도 경로 버전이며 SSE 재전송 순번과 다르다.
- [GPS Mapper](../../backend/src/main/resources/mapper/path/SearchPathMapper.xml)는 `client_ts`, `elapsed_realtime_nanos`, `point_order`를 저장하고 조회 시 `point_order`로 정렬한다. [마커 알림](../../backend/src/main/java/com/surimap/app/service/marker/MarkerNotificationService.java)도 현장 기록 시각을 `clientTs`로 보존한다. 시각 보존 자체가 현재 DB에 없는 기능은 아니다.
- [화면 구독](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts)은 처음 연결하거나 새 이벤트를 받을 때 `detail({ incidentId })`를 무효화한다. [조회 함수](../../frontend/src/features/board/api/incidentBoardApi.ts)에 슬롯·경로 변경분 필터를 전달하지 않는다. [공통 설정](../../frontend/src/app/AppProviders.tsx)의 `staleTime: 10_000`은 이벤트 재조회 간격 제한이 아니다. query 함수의 `AbortSignal`도 HTTP 요청으로 전달하지 않는다. 실제 요청 수·동시 실행·취소 비용은 아직 측정하지 않았으므로 이벤트 수와 HTTP 요청 수가 정확히 같다고 단정하지 않는다.
- [상황판 수집기](../../backend/src/main/java/com/surimap/board/DefaultIncidentBoardSourceRowCollector.java)는 현재·선택 수색 차수 범위의 경로를 조회한다. 조회 서비스는 경로마다 GPS·구간·제외 좌표를 읽고 전체 좌표를 응답에 담는다. 사건 하나의 변경이 모든 사건을 재조회한다는 뜻은 아니다. [지도](../../frontend/src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx)는 변경된 경로 feature collection을 `setData()`로 넘긴다.

따라서 **브로커만 Kafka로 바꿔도 반복 조회·경로 조립·전송·렌더링 비용은 그대로 남는다**는 것이 코드에 근거한 판단이다. 이는 현재 부하의 지배적인 병목을 측정했다는 결론이 아니다. 경로 길이·변경 빈도·동시 상황판 수를 바꿔 단계별 비용을 비교해야 한다.

### 브라우저 전달 방식: 무엇이 필요한가

| 방식 | 제공하는 기능 | 이 기능만으로 해결하지 않는 것 |
|---|---|---|
| SSE | 서버→브라우저 이벤트 스트림, `EventSource` 재연결·`Last-Event-ID` 전달 | 서버의 영속 history, 페이지 새로고침 후 커서 복원, 화면 반영 확인. [WHATWG SSE](https://html.spec.whatwg.org/multipage/server-sent-events.html) |
| WebSocket | 한 연결에서 양방향 텍스트·바이너리 송수신 | 재접속 시 history 조회·커서·업무 처리 확인은 별도 프로토콜이 필요하다. [WHATWG WebSocket](https://websockets.spec.whatwg.org/) |
| 주기적 HTTP 조회 | 정한 간격으로 현재 상태 확인, 조건부 조회로 변경 없는 응답 생략 가능 | 다음 조회 전의 지연과 조회 비용, 모든 중간 이벤트의 보존. GitHub Notifications API는 `Last-Modified`·304·`X-Poll-Interval`을 사용한다. [GitHub 공식 API](https://docs.github.com/en/rest/activity/notifications) |

비교 관점의 추론: 서버가 지도 변경을 알려 주고 쓰기는 기존 HTTP API로 수행한다면 SSE는 맞는 후보다. 같은 연결로 빈번한 양방향 통신이 필요하면 WebSocket, 조회 간격만큼의 지연을 허용하면 polling을 비교할 수 있다. 어느 방식이든 지도 데이터의 크기·갱신 빈도·연결 수를 측정하지 않고 더 빠르다고 단정할 수 없다. GitHub API 사례를 GitHub 웹 화면 전체의 내부 구현으로 일반화하지 않는다.

### 변경 알림 뒤 재조회와 변경 데이터 직접 반영

TanStack Query는 필요한 query만 무효화하고 사용 중인 query를 백그라운드에서 다시 읽는 흐름을 명시한다. 따라서 `변경 알림 → invalidateQueries → 조회 결과 교체`는 라이브러리가 직접 지원하는 방식이다. 다만 이 근거가 “이벤트마다 모든 상황판 데이터를 다시 읽는 것이 최적”임을 뜻하지는 않는다. [Query Invalidation](https://tanstack.com/query/latest/docs/framework/react/guides/query-invalidation)

무효화는 query를 stale로 표시하므로 숫자 `staleTime`만으로 이벤트에 따른 재조회 빈도가 제한되지는 않는다. 또한 query 함수에 제공되는 `AbortSignal`을 실제 `fetch` 등에 연결해야 요청 취소에 사용할 수 있다. query 상태의 취소와 이미 진행 중인 네트워크 요청의 중단은 구분해서 확인한다. [무효화 동작](https://tanstack.com/query/latest/docs/framework/react/guides/query-invalidation), [Query Cancellation](https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation)

MapLibre의 `setData()`는 GeoJSON을 설정하고 다시 그리며, `updateData()`는 변경분을 반영한다. 후자는 기존 feature마다 고유 ID가 필요하고 존재하지 않는 ID 갱신은 오류 없이 무시될 수 있다. 지도 렌더러의 변경분 API가 네트워크 재전송·중복 제거·공백 복구까지 제공하지는 않는다. [GeoJSONSource API](https://maplibre.org/maplibre-gl-js/docs/API/classes/GeoJSONSource/)

다음은 위 기능과 Mercure의 복구 규칙을 바탕으로 한 비교이며 채택안이 아니다. 여기서 snapshot은 특정 조회 시점의 상태, delta는 변경분을 뜻한다.

| 패턴 | 비교할 이점 | 함께 확인할 비용·정합성 |
|---|---|---|
| 변경 알림→해당 경로 snapshot 재조회 | 조회 API의 정렬·권한·현재 상태를 재사용 | 반복 조회량, 넓은 query 무효화, 조회 중 추가 변경 |
| delta 직접 반영 | 매번 전체 경로를 다시 보내지 않을 수 있음 | 중복·삭제·수정·순서 뒤집힘, 늦게 도착한 좌표의 삽입 위치 |
| delta 반영+필요 시 snapshot 재조회 | 평상시 변경분 처리와 누락 후 상태 복원을 결합 | snapshot 기준 위치와 이후 delta 경계, 공백 감지·재조회 조건 |

Mercure는 부분 갱신을 하나 놓치면 다음 갱신이 잘못될 수 있다고 설명하고, history 공백을 감지하면 원본을 다시 조회하도록 안내한다. 따라서 delta를 택해도 초기 조회와 누락 후 재조회는 별도 문제다. 반대로 최신 지도 snapshot을 복원했다고 과거 알림의 개별 수신·확인까지 복원한 것은 아니라는 점은 수리맵에 적용할 추론이다. [Mercure 재연결·history](https://mercure.rocks/docs/concepts/reconnection-and-history)

### “시간을 기억한다”의 네 가지 의미

| 값 | 답하는 질문 | 혼동하면 안 되는 값 |
|---|---|---|
| GPS 측정 시각(event time) | 현장에서 실제로 언제 측정했는가 | 서버에 늦게 도착한 시각 |
| 서버 수신·처리 시각 | 서버·브로커가 언제 받거나 처리했는가 | 현장의 이동 순서 |
| Kafka offset·Redis stream ID·SSE 커서 | 해당 로그나 스트림의 어디까지 읽었는가 | 좌표의 측정 시각, 사용자 확인 상태 |
| 알림의 읽음·확인 상태 | 특정 사용자가 무엇을 확인했는가 | 서버 consumer의 처리 완료 |

Kafka Streams 공식 문서도 GPS 센서를 예로 들어 event time, broker ingestion time, processing time을 구분한다. 오프라인에서 과거 좌표가 늦게 들어오면 로그 뒤쪽에 저장되면서도 측정 시각은 앞설 수 있다. 이 설명에서의 추론은 **높은 전송 순번을 곧 더 최근 위치로 해석할 수 없다**는 것이다. [Kafka Streams의 시간 개념](https://kafka.apache.org/42/streams/core-concepts/)

### Kafka가 제공하는 것과 남는 일

- Kafka topic은 소비 후 즉시 지우는 큐가 아니라 설정된 보존 범위에서 반복해서 읽는 로그다. 순서 보장은 topic 전체가 아닌 partition 안의 기록 순서다. 같은 key를 같은 partition으로 보내는 구성은 관련 기록을 모으지만 GPS 측정 시각이나 외부 DB 커밋 순서로 자동 정렬하지 않는다. [Kafka 개념](https://kafka.apache.org/42/getting-started/introduction/)
- 일반 consumer group 안에서는 partition을 소비자에게 나눠 맡기고 partition별 offset으로 진행 위치를 기록한다. 다른 group은 독립적으로 읽는다. SSE 서버의 group offset은 그 서버의 처리 위치이지 각 브라우저의 마지막 수신 위치가 아니다. [Kafka 소비자 설계](https://kafka.apache.org/42/design/design/)
- 보존 기간·용량을 넘은 기록은 다시 읽을 수 없다. Compaction은 같은 key의 오래된 값을 제거하고 최신 상태를 남기는 정책이므로 모든 변경 이력을 보존하는 것과 다르다. [Kafka 보존·compaction](https://kafka.apache.org/42/design/design/)
- Kafka record의 timestamp 의미는 설정과 추출 방식에 달린다. Kafka Streams는 event-time window·집계·늦은 데이터의 grace period를 제공하지만, grace를 지난 데이터는 해당 window에서 처리하지 않는다. 브로커 설치만으로 오프라인 GPS의 정렬·수정 규칙이 완성되지 않는다. [Streams 시간·window 규칙](https://kafka.apache.org/42/streams/core-concepts/)
- Kafka 내부의 offset·출력 topic을 묶는 트랜잭션도 브라우저 표시까지 하나의 트랜잭션으로 만들지는 않는다. 공식 문서도 외부 시스템의 exactly-once 처리는 그 시스템과의 협력이 필요하다고 한정한다. [처리 보장 범위](https://kafka.apache.org/42/design/design/)

적용 추론: 여러 서버 기능이 같은 이벤트를 각자 재처리하거나 긴 이력에서 집계·분석을 반복해야 한다면 Kafka의 기능을 비교할 이유가 생긴다. “시간 필드가 있다” 또는 “브라우저가 재접속한다”는 사실만으로 Kafka가 필수라는 근거는 아니다. 브로커를 추가해도 마지막 브라우저 구간은 SSE일 수 있다.

### Redis Streams와의 비교

Redis Streams도 항목을 보관하고 ID 이후를 읽을 수 있다. 자동 ID의 시간 부분은 Redis 노드에서 ID를 생성한 시각이며 이전 ID보다 커지도록 유지된다. 이것은 GPS 측정 시각이 아니다. 명시적 ID도 기존 마지막 ID보다 커야 하므로 과거 GPS 시각을 그대로 ID로 삼아 늦게 끼워 넣는 방식과 다르다. [XADD 규칙](https://redis.io/docs/latest/commands/xadd/)

`XREAD`는 같은 범위를 기다리는 소비자 모두에게 데이터를 줄 수 있고, `XREADGROUP`은 그룹 소비·pending 목록·`XACK`을 제공한다. 소비자 장애 후 pending 처리와 다른 소비자로의 재할당은 서버 작업 복구 수단이며 브라우저별 수신 확인은 아니다. [XREAD](https://redis.io/docs/latest/commands/xread/), [XREADGROUP](https://redis.io/docs/latest/commands/xreadgroup/)

Streams의 trim으로 지운 payload는 pending ID가 남아 있어도 복구할 수 없다. 재시작·장애 시 내구성도 RDB/AOF와 fsync·복제 설정을 함께 확인해야 한다. 단순히 “Redis Streams니까 영속·무손실”이라고 할 수 없다. [삭제된 pending 항목](https://redis.io/docs/latest/commands/xreadgroup/), [Redis 영속화](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

비교 관점: Kafka의 partition·독립 consumer group·보존 로그와 Redis의 stream key·ID·pending/ack 모델은 모두 재처리 수단이지만 운영·분할·보존 방식이 다르다. 수리맵의 요구 보존 기간, 이벤트량, 독립 소비자 수, 이미 운영하는 기반을 확인하기 전에는 어느 쪽이 더 단순하거나 빠르다고 결론 내리지 않는다.

### DB outbox와 브라우저 복구는 별도 경계

아래는 역할을 구분하는 비교용 흐름이지 수리맵의 새 설계가 아니다.

```text
업무 저장 + outbox 기록을 같은 DB 트랜잭션으로 커밋
  → 전달기가 Kafka 또는 Redis Streams로 전달·재시도
  → SSE 서버가 읽고 권한에 맞는 브라우저로 전달
  → 브라우저가 자신의 재개 위치로 복구하거나 snapshot 재조회
```

Outbox의 근거는 업무 변경과 보내야 할 이벤트의 DB 기록을 함께 커밋하는 것이다. 별도 전달 단계에서는 중복이 생길 수 있어 멱등 처리가 필요하다. 위 흐름의 Kafka·Redis 선택은 이 원칙을 적용한 비교이며 AWS가 해당 구성을 그대로 권고한다는 뜻은 아니다. [Transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)

브로커가 기록을 받았다는 확인, SSE 서버의 소비 완료, 브라우저의 수신·화면 반영은 다르다. 여러 partition이나 stream을 묶는 화면이라면 브라우저 재개 위치를 무엇으로 표현할지도 추가로 정해야 한다. 저장소를 바꾸는 것만으로 이 대응 관계와 history 보존 이후의 복구 규칙이 자동 생성되지는 않는다. [Kafka offset](https://kafka.apache.org/42/design/design/), [Mercure 복구 규칙](https://mercure.rocks/docs/concepts/reconnection-and-history)

### 이번 조사에서의 판단 한계

SSE로 변경을 통지하고 필요한 조회를 갱신하는 것은 근거 있는 후보지만 보편적인 최적안으로 확정할 수는 없다. 먼저 비교할 항목은 허용 갱신 지연, 조회 응답 크기·빈도, 재접속 때 복원할 상태와 개별 알림, 보존 범위, 늦은 GPS 처리다. 이는 조사에서 도출한 비교 기준이며 새 요구를 합의한 것은 아니다.

이번 검토의 추천은 **SSE 유지, 합의한 PostgreSQL 재전송 이력과 화면별 수신 위치 복구를 구체화하고, 지도 재조회 범위·빈도를 따로 검증하는 것**이다. 기존 업무 트랜잭션과 이벤트 DB 기록을 재사용할 수 있다는 이유이며 PostgreSQL의 처리량이 충분하다고 측정한 결과는 아니다. 빠른 연속 변경의 조회 요청을 합치더라도 GPS 원본이나 개별 지원 요청·발견 알림을 버리는 방식으로 구현하지 않는다.

Kafka는 SSE·FCM·분석 등이 같은 이력을 독립적으로 다시 처리해야 하거나, 분산 로그·시간 구간 집계를 실제 요구로 채택하면 도입을 비교할 근거가 있다. 성능 문제가 발생한 뒤에만 검토할 기술은 아니다. 다만 Kafka·Redis 모두 추가 저장소의 운영, DB에서 브로커로의 누락 없는 전달, 보존·사건별 파기, 브라우저별 복구를 설계해야 한다. 여러 SSE 서버를 같은 consumer group에 넣는 것만으로 모든 서버가 모든 이벤트를 받는 것도 아니므로 연결 소유 서버까지의 전달을 확인해야 한다.

조사 뒤 Q6의 순번 확정 시점은 [기능 문서의 합의](../features/event-delivery.md#sse-재연결과-중복-처리)로 이어졌다. delta 전환과 Kafka·Redis 도입은 선택하지 않았다. 외부 공식 기능을 확인했을 뿐 프로젝트 설치 버전의 적용 가능성, 실제 지도 부하·네트워크 지연·장애 복구를 실행 검증하지 않았다.
