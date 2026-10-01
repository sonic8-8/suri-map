# 코드베이스 이름 변경 후보

2026-09-09 1차 탐색. Backend·Frontend·Android·mock-112·Infra의 파일명·선언명을 검색하고, 역할이 모호한 후보는 구현·사용처를 확인했다. 메서드·변수 전수 검토는 아니다.

남은 후보를 먼저 나열하고, 완료한 변경은 마지막의 [반영 이력](#반영-이력-요약)에 요약한다. 후보의 이름은 해당 영역을 정리할 때 실제 역할·사용처를 확인해 확정한다. API·이벤트·DB 필드·fixture ID 같은 계약 이름은 이 목록만으로 바꾸지 않는다.

## 발견 시 후보 등록

문서·코드를 검토하는 영역에서 아래 이름을 발견하면 별도 요청 없이 후보로 등록한다(2026-09-18 사용자 합의). 매번 저장소 전체를 다시 조사하지 않고, 현재 대상과 연결된 파일을 확인한다.

- 파일 경로와 후보로 보는 이유를 해당 영역에 짧게 기록한다. 이미 등록된 파일은 기존 항목을 갱신한다.
- 실제 검증 대상·대역 사용·중복을 확인해 이름 변경·통합·제거를 정한다. 후보 등록만으로 코드를 바꾸거나 필요한 검증을 제거하지 않는다.
- 시나리오 번호·fixture ID 등 데이터 식별자는 유지한다. 문서 정리 중 파일·클래스를 일괄 재명명하지 않는다.

| 대상 패턴 | 확인할 대표 파일 | 정리 방향 |
|---|---|---|
| 시나리오·Spec 번호가 붙은 이름 | `Sc03PackageTileHarnessRunner`, `S8HandoverApiContractTest` | 번호 대신 검증하는 동작이나 데이터 준비 역할을 드러낸다. `Sc`/`sc` + 번호 등 표기 차이도 확인 |
| `*HarnessRunner`·역할이 불명확한 `*Runner` | `Sc03PackageTileHarnessRunner`, `EventHubHarnessRunner` | 무엇을 실행하는지와 실제 호출 필요성을 확인. 프레임워크 실행기 등 역할이 명확한 이름까지 일괄 제거하지 않음 |
| `*RedTest` | `Sc03PackageTileHarnessRedTest`, `OpTransitionRedTest` | TDD 단계 대신 실제 검증 대상을 드러내거나 기존 테스트에 통합 |
| `*ContractTest` | `OfflinePackageManifestContractTest`, `OutboxRequeueContractTest`, `PolicePhoneNavigationContractTest`, `KeycloakThemeContractTest` | Backend·Android·mock-112를 포함해 실제 검증 대상과 연결 범위를 확인한 뒤 `<검증 대상>Test`로 정리 |
| `*FixtureTest` | `OutboxRetryDiagnosticsFixtureTest`, `HandoverMemoOutboxWriteOperationFixtureTest` | 데이터 자체의 형식·참조 검사와 제품 동작 검증을 구분하고, 필요한 검사만 적절한 대상의 테스트에 유지 |

## 마커·알림 — 남은 확인

2026-09-30 표기 합의: 프로젝트가 직접 짓는 SSE 관련 클래스·파일·변수·메서드 이름은 `Sse`·`sse` 대신 `ServerSentEvent`·`serverSentEvent`로 풀어 쓴다. 외부 타입인 `SseEmitter`, 기존 공개 API·설정 키와 앞서 합의한 패키지 경로는 이 결정만으로 바꾸지 않는다. 역할 이름의 확정 여부는 아래 항목별로 구분한다. Q26 승인으로 이름·배치를 코드에 반영했다. 표의 옛 이름은 변경 전 대상이며 링크는 이동한 현재 파일이다. Docker 연결 복구 후 전체 테스트 1,348개와 CI 결과 검증을 통과했다(실패·오류·건너뜀 0, 기본 성능 태그 제외).

| 대상 | 확인할 점 |
|---|---|
| [기존 eventhub 패키지 → SSE 구현](../../backend/src/main/java/com/surimap/global/sse/) | Q1 확정: 상황판 구독·재연결 흐름은 `api/service/sse`, 공용 연결·전송 구현은 `global/sse`로 분리한다. Q9에서 실시간 전달은 기존 Worker로, Q16에서 DB 전송 작업·Service·Worker·Mapper는 `global/sse`로 확정했다. 공용 발행·Controller·Spring 설정 등의 남은 배치는 아래 개별 항목에서 정한다. `port`·`adapter`는 실제 사용처로 판단하며 `MockEventHub`의 운영 fallback을 확인하기 전에 인터페이스·대역을 일괄 제거하지 않는다. 합의한 책임별로 이동·분리했다. |
| [SseConnectionRegistry](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnectionRegistry.java)·[SseConnectionRegistryTest](../../backend/src/test/java/com/surimap/global/sse/ServerSentEventConnectionRegistryTest.java) | 2026-09-30 사용자 선택으로 `ServerSentEventConnectionRegistry`·`ServerSentEventConnectionRegistryTest` 확정, 구현에 반영했다. 사건·계정별 등록된 연결을 보유하며 등록·해제·대상별 전달·실패 연결 제외·종료를 처리한다. `Handler`보다 연결 보유·등록 역할을 명확히 드러내는 `Registry`를 유지한다. `global/sse` 배치 방향을 유지하며 일급 컬렉션 전환이나 위임 계층 추가는 하지 않는다. |
| [SseConnection](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnection.java) | 2026-09-30 사용자 선택으로 `ServerSentEventConnection` 확정, 구현에 반영했다. 연결 하나의 재전송·실시간 이벤트 순서, 중복 제외, 대기 한도와 종료 상태를 관리한다. 이력은 콜백으로 받고 소켓 쓰기는 `SseStreamResponse`에 위임한다. 새 구독에는 새 객체를 만들므로 연결 하나에 대응하는 `Connection`을 유지하고 `Session`으로 바꾸지 않는다. |
| [SseStreamResponse](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventStream.java)·[SseStreamResponseTest](../../backend/src/test/java/com/surimap/global/sse/ServerSentEventStreamTest.java) | 2026-09-30 사용자 선택으로 `ServerSentEventStream`·`ServerSentEventStreamTest` 확정, 구현에 반영했다. 준비된 바이트를 Servlet 응답에 쓰고 전송 재개·응답 종료·등록 해제를 처리하는 통로다. `DeferredResult<Void>`의 하위 타입이며 DTO나 Spring `SseEmitter`의 하위 타입이 아니다. 이벤트 순서·대기열은 별도 연결 객체가 관리한다. |
| [SseLiveEventSink](../../backend/src/main/java/com/surimap/global/sse/LiveServerSentEventSender.java) | 2026-09-30 사용자 선택으로 `LiveServerSentEventSender` 확정, 구현에 반영했다. 운영 구현은 연결 객체 하나이며 registry가 실시간 이벤트 전달·종료에 사용한다. worker 재시도도 이 경로를 쓰지만 DB 이력 재전송은 별도 콜백을 사용하므로 `Live`를 유지한다. 운영 `send` 반환은 전송 완료가 아니라 대기열 수용·전송 요청이다. 이번 결정은 이름 변경이며 인터페이스 구조 변경은 아니다. |
| [SseEventFrame](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventMessage.java)·[SseEventFrameFormatter](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventFormatter.java) | 2026-09-30 사용자 선택으로 `ServerSentEventMessage`·`ServerSentEventFormatter` 확정, 구현에 반영했다. 메시지는 `id`·`event`·직렬화 전 `PublishRequest data`를 담는다. Formatter가 data의 JSON 직렬화와 SSE 줄 형식을 조립하고, UTF-8 바이트 변환은 연결 객체가 맡는다. 직렬화 전 값임을 드러내도록 `Frame` 대신 `Message`를 사용한다. |
| [SseReplayService](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventHistoryService.java)·[SseReplayServiceTest](../../backend/src/test/java/com/surimap/api/service/sse/ServerSentEventHistoryServiceTest.java) | 2026-09-30 사용자 선택으로 `ServerSentEventHistoryService` 확정, 대응 테스트는 `ServerSentEventHistoryServiceTest`로 정리하며 구현에 반영했다. 최초 연결·재접속에 공급할 DB 이벤트 이력을 조회하고, 재개 가능 범위를 검증해 페이지를 조립한다. 직접 전송하거나 이력을 저장·삭제하지 않는다. Q25에서 Q7의 read 동사를 get으로 수정해 `replayResultAfter` → `getFirstPageAfter`, `replayNextPage` → `getNextPage`로 확정했다. 내부 `ReplayResult` → `HistoryPage`, `frames` → `messages`는 Q7 합의를 유지한다. `First`는 지정한 순번 이후 조회의 첫 페이지이고, 이 객체는 HTTP 응답 DTO가 아니다. |
| [SseStreamService](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java) | Q8에서 웹 구독 역할은 `api/service/sse/ServerSentEventSubscriptionService`로 확정했다. Q9에서 운영 호출자가 하나인 `dispatchLive` 흐름은 기존 `EventDispatchJobWorker`의 private 메서드로 옮기고 별도 공용 전송 서비스를 만들지 않기로 했다. 구현에 반영했으며, 전체 클래스를 그대로 재명명하는 결정이 아니다. 이동한 호출부에 필요한 진입점만 public으로 열었다. 기존 `LiveServerSentEventSender`는 연결 하나의 전송 인터페이스이며 Worker의 전체 전달 흐름과 구분한다. |
| [전송 직전 검사](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java)·[EventDispatchJobService](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobService.java) | Q10에서 Worker·구독 서비스에 작은 종료 예외 조건을 각각 두고 기존 DB 검사 메서드를 공유하기로 확정했다. 별도 Validator는 만들지 않으며 구현에 반영했다. 일반 이벤트는 대기열 추가 전·실제 쓰기 직전에 사건 상태를 검사하고 종료·파기 알림은 이 DB 검사를 건너뛴다. 종료 예외 분기를 `REQUIRES_NEW` 메서드 안으로 옮기면 조기 반환 전에도 트랜잭션이 시작되므로 기존 경계를 유지한다. |
| [EventStreamController](../../backend/src/main/java/com/surimap/api/controller/sse/ServerSentEventController.java)·[EventStreamConfig](../../backend/src/main/java/com/surimap/config/ServerSentEventConfig.java) | Q11에서 `ServerSentEventController`·`ServerSentEventConfig`로, Q22에서 Controller는 `api/controller/sse`, Config는 `config` 배치로 확정했다. 대응 Controller 테스트는 `ServerSentEventControllerTest`로 정리하며 구현에 반영했다. Controller는 사건·계정의 WEB SSE 구독 요청을 받고, Config는 MVC 비동기 응답 시작과 연결 등록소 빈을 설정한다. 스트림 클래스·REQUEST_ATTRIBUTE·생성자·attachRegistration·start·complete·completeWithError 등 실제 외부 호출 진입점만 public으로 열고 내부 쓰기 메서드는 package-private을 유지한다. 별도 facade는 만들지 않으며 기존 URL·헤더·오류 응답·빈 등록 동작을 유지한다. |
| [Controller 구독 메서드](../../backend/src/main/java/com/surimap/api/controller/sse/ServerSentEventController.java)·[서비스 진입 메서드](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java) | Q12에서 Controller의 `stream` → `subscribeToIncidentEvents`, `streamAssignedIncidents` → `subscribeToAccountEvents`; 서비스의 `openStream` → `openIncidentStream`, `openAccountStream` 유지를 확정했다. 구현에 반영했다. Account는 이벤트 수신 계정이며 계정 정보 변경만 뜻하지 않는다. 계정 스트림은 배정된 모든 사건의 모든 이벤트가 아니라 payload에서 해당 계정을 수신 대상으로 지정한 이벤트를 받는다. 구독은 연결 수명 동안의 메모리 등록이며 DB 구독 저장·사건 배정 변경이 아니다. |
| [구독 초기화·이력 순회 객체](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java) | Q13에서 `sendIncidentReplay` → `startIncidentSubscription`, 내부 `IncidentReplay` → `IncidentHistoryCursor`, `nextFrame` → `nextMessage`, `nextIndex` → `nextMessageIndex`를 확정했다. 구현에 반영했다. 초기화 메서드는 연결 등록·해제 콜백·첫 이력 조회·전송 시작을 조율한다. `IncidentHistoryCursor`는 클래스명이며 기존 지역 변수 `cursor`가 이 객체를 참조한다. 객체는 현재 페이지와 다음 위치를 기억하며 필요할 때 다음 페이지를 조회한다. DB cursor나 열린 트랜잭션을 보유하지 않는다. 이력 소진 시 null을 반환하고 종료 페이지 소진 시에는 연결 종료도 요청한다. 내부 클래스 구조를 유지하는 이름 변경이며 새 계층·파일을 만들지 않는다. |
| [Worker로 옮길 전송·수신 계정 추출 메서드](../../backend/src/main/java/com/surimap/api/service/sse/ServerSentEventSubscriptionService.java) | Q14에서 `dispatchLive` → `dispatchLiveEvent`, `assignedAccountIds` → `extractRecipientAccountIds`를 확정했다. 구현에 반영했다. 전송 메서드는 이미 부여된 순번으로 메시지를 구성·검증해 사건·계정 연결에 전달하고 종료 이벤트이면 사건 연결 종료를 요청한다. DB 저장·순번 발급·브라우저 수신 확인은 하지 않으며 Worker 재시도도 같은 경로다. 계정 추출은 payload의 UUID 문자열을 해석할 뿐 DB의 현재 배정을 조회하지 않는다. 동작·공개 payload 필드는 유지한다. |
| [EventDispatchJob](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJob.java)·[Service](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobService.java)·[Worker](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobWorker.java)·[Mapper](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobMapper.java) | Q15에서 `ServerSentEventJob`·`ServerSentEventJobService`·`ServerSentEventJobWorker`·`ServerSentEventJobMapper`로 확정했다. 대응 테스트도 대상 이름에 맞추며 구현에 반영했다. 작업 실행·재시도·처리 상태는 현재 SSE 전송만 관리하며 FCM·로컬 소비자 처리는 포함하지 않는다. 이벤트 내용 외에 별도 작업 ID·상태를 보유하므로 `Job`을 유지한다. 완료 행도 이력 조회에 사용하며 완료 상태는 클라이언트 수신 확인이 아니다. 이름 결정으로 DB 테이블·설정 키를 바꾸거나 새 클래스를 추가하지 않는다. 패키지 배치는 아래 Q16 결정을 따른다. |
| SSE 전송 작업의 패키지 배치 | Q16에서 `ServerSentEventJob`·Service·Worker·Mapper를 `global/sse`에 함께 두고 XML을 `resources/mapper/sse/ServerSentEventJobMapper.xml`로 옮기기로 확정했다. 구현에 반영했다. 사건·마커 자체가 아닌 기술적 전송 작업이라는 책임을 기준으로 묶는다. 기존 Mapper scan과 XML 검색 범위는 새 위치를 포함하며 XML namespace·resultMap type·parameterType과 Java·테스트 참조를 함께 수정했다. DB 테이블·열·설정 키는 유지한다. Service의 사건 상태·파기 조회 의존성은 남으므로 업무 독립적인 범용 도구라고 설명하지 않는다. |
| [DB 이력 조회·검증 메서드](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobService.java) | Q25에서 `getHistoryEndSequence`·`getContiguousHistoryPage`·`getRetainedHistoryPage`로 확정했다. Q7의 첫 페이지·다음 페이지 조회도 `getFirstPageAfter`·`getNextPage`로 수정하며 구현에 반영했다. [국내 기업 조회 동사](../research/korean-company-query-method-naming.md)를 참고한 프로젝트 선택이며 업계 공통 규칙은 아니다. `validateHistoryContinuity`와 Mapper의 find 계열·SQL·실패 계약은 유지한다. 상한은 남은 행의 최댓값이 아니라 사건의 마지막 확정 순번이다. Contiguous 조회는 페이지의 건수·순번 누락을 검사하고 Retained 조회는 누락을 허용하되 범위·사건 상태 검사는 유지한다. Continuity 검사는 응답 시작 전 전체 재개 구간을 검사한다. |
| [EventHub](../../backend/src/main/java/com/surimap/global/event/EventPublisher.java)·[DbEventHub](../../backend/src/main/java/com/surimap/global/event/DatabaseEventPublisher.java) | Q17에서 `EventPublisher`·`DatabaseEventPublisher`로 확정했다. 구현에 반영했다. `publish` 단일 진입점으로 SSE 작업 저장 → 로컬 소비자 동기 호출 → 커밋 후 Worker 깨우기 등록을 조율한다. 트랜잭션 동기화가 없으면 Worker를 즉시 깨운다. SSE 전용 발행자가 아니며 모든 소비자의 전달·재시도까지 영속화하는 것도 아니다. 현재 사건 종료 FCM 소비자는 즉시 발송을 호출하므로 커밋 이후 발송으로 설명하지 않는다. 인터페이스·운영 fallback은 이번 이름 선택으로 제거하지 않는다. |
| [MockEventHub](../../backend/src/main/java/com/surimap/global/event/CapturingEventPublisher.java) | Q18에서 `CapturingEventPublisher`로 확정했다. 구현에 반영했다. 입력 검증 후 발행 내용을 메모리에 기록하고 조회·실패 주입을 지원하지만 DB 저장·SSE 전송·로컬 소비자 실행은 하지 않는다. [PolicePhoneHeartbeatConfig](../../backend/src/main/java/com/surimap/app/service/policephone/PolicePhoneHeartbeatConfig.java)의 발행자 빈 부재 시 대체 등록이 있어 테스트 디렉터리로 바로 옮기거나 삭제하지 않는다. 이는 DB 장애 시 자동 전환하는 기능이 아니다. 이번 선택은 이름에 한정하며 등록 조건과 동작을 유지한다. |
| [PublishRequest](../../backend/src/main/java/com/surimap/global/event/EventPublishRequest.java)·[CapturedPublish](../../backend/src/main/java/com/surimap/global/event/CapturingEventPublisher.java) | Q19에서 CapturedPublish를 제거하고 발행 요청 객체를 그대로 기록하기로 확정했다. 구현에 반영했다. 두 record의 8개 필드 이름·타입·순서가 같고 CapturedPublish에는 추가 상태·동작이 없다. 현재도 payload Map은 공유하므로 깊은 복사나 발행 시점 스냅샷을 보장하지 않는다. 반환 타입·객체 동일성이 바뀌는 구조 변경이며 확인한 소비 테스트의 명시 타입·메서드 참조를 함께 수정한다. 기록 조회·개수·실패 주입과 대체 빈 등록 동작은 유지한다. |
| [발행 입력](../../backend/src/main/java/com/surimap/global/event/EventPublishRequest.java)·[BaseEventValidator](../../backend/src/main/java/com/surimap/global/event/EventPublishRequestValidator.java) | Q20에서 `EventPublishRequest`·`EventPublishRequestValidator`로 확정했다. 구현에 반영했다. 실제 BaseEvent 타입은 없으며 공통 필드·payload의 id/status/version 형식을 검사한다. 사건 상태·권한·도메인별 내용 전체를 검증하는 객체는 아니다. 입력 객체는 HTTP RequestBody가 아니라 내부 발행 요청이고 SSE data에도 직렬화된다. 기존 JSON 필드와 검증 규칙·호출 시점은 유지한다. 현재 검증기 호출은 기록용 대역·실시간 분배이며 DB 발행 자체에서는 호출하지 않는다. Q10에서 추가하지 않기로 한 전송 직전 Validator와 다른 기존 클래스다. |
| [DomainEventConsumer](../../backend/src/main/java/com/surimap/global/event/DomainEventConsumer.java)·공용 발행 패키지 | Q21에서 EventPublisher·DatabaseEventPublisher·CapturingEventPublisher·EventPublishRequest·EventPublishRequestValidator·DomainEventConsumer를 기존 `global/event`에 함께 두기로 확정했다. 역할별 하위 패키지는 만들지 않으며 구현에 반영했다. `DomainEventConsumer`·`supports`·`consume` 이름을 유지한다. 두 소비자 구현은 기존 업무 영역에 두고 참조를 맞춘다. 사건 종료 FCM과 전체 수색 구역 변경에 따른 오프라인 패키지 상태 처리를 발행자가 동기 호출하며, SSE 클라이언트·MQ 소비자가 아니다. |
| [GoneRefetchRequiredException](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventRefetchRequiredException.java)·[InvalidEventEnvelopeException](../../backend/src/main/java/com/surimap/global/event/InvalidEventPublishRequestException.java) | Q23에서 `ServerSentEventRefetchRequiredException`·`InvalidEventPublishRequestException` 이름과 별도 RuntimeException 타입 유지를 확정했다. BusinessException·ErrorCode나 IllegalArgumentException으로 합치지 않으며 구현에 반영했다. 전자는 잘못된 수신 순번·이력 누락·사건 종료나 파기 등에서 발생하고, 별도 catch로 종료 알림 복구나 연결 종료를 처리한다. HTTP 응답 전의 409·gone_refetch_required를 유지하며 모든 발생이 재조회 응답으로 이어지는 것은 아니다. 후자는 내부 발행 요청의 공통 필드·payload 형식 검사 실패로 사용자 업무 거부와 구분한다. |
| [EventStreamExceptionHandler](../../backend/src/main/java/com/surimap/global/error/GlobalExceptionHandler.java) | Q24에서 재조회 예외의 HTTP 변환 메서드를 기존 GlobalExceptionHandler로 옮기고 전용 Advice와 Controller의 같은 예외 catch를 제거하기로 확정했다. 구현에 반영했다. 예외 타입은 유지하며 409·application/json·gone_refetch_required를 명시하고 응답 전에 SSE 전송이 시작되지 않는지 검증한다. Controller의 채널 거부 응답과 서비스·연결·스트림의 복구 catch는 유지한다. Config·테스트의 Advice 등록 참조도 함께 수정하며, 이미 시작한 SSE 응답을 JSON으로 바꾸지 않는다. |
| [MockEventHubContract](../../backend/src/test/java/com/surimap/eventhub/harness/MockEventHubContract.java) | 옛 테스트 지원 객체다. 운영 발행자와 별개로 `Hook`·`Contract`·`Harness` 대신 실제 지원 역할을 드러내도록 검토한다. CapturingEventPublisherTest와 EventPublishRequestValidatorTest의 대상별 이름 변경은 완료했다. |
| [IncidentHandoverSupportAssignmentScenarioTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentScenarioTest.java) | `ScenarioTest`를 별도 분류로 쓰지 않기로 한 기준에 맞춰 검증 대상별 분리·통합과 이름을 정한다. 실제 DB·FCM 호출 검사와 SSE 직접 호출·고정 자료 조립·probe 행 검사를 구분한다. |
| [MarkerPhotoPurgeHook](../../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHook.java)·[MarkerPhotoPurgeHookAdapter](../../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHookAdapter.java) | 패키지 이동은 끝났지만 실제 삭제 구현·빈 등록은 별도 확인 대상이다. 이름 변경만으로 연결 누락을 해결한 것으로 보지 않는다. |
| [남은 마커 테스트·fixture](../../backend/src/test/java/com/surimap/marker/) | 운영 `com/surimap/marker` 디렉터리는 제거했지만 테스트 디렉터리까지 정리한 것은 아니다. 각 검증의 대상·중복·대역 사용을 확인한다. |

[Spring 명명 조사](../research/spring-sse-class-naming.md)는 프레임워크 클래스의 역할 비교다. 이후 Aside로 [국내 기업의 공개 코드](../research/korean-company-sse-naming.md)를 확인했다. 이 자료의 후보는 회사 전체 관행이나 변경 승인이 아니며, 최신 결정은 위 항목별 상태를 따른다.

FCM의 실패 기록과 재시도·배정 알림의 전송 시점은 별개다. 마커·배정의 실패 로그 보완은 [로컬 이슈 4](../issues/local/4-fcm-delivery-failure-not-recorded.md), 남은 연결·복구·전달 문제는 [이벤트 전달 문서](../features/event-delivery.md)에서 확인한다. 이름 변경 이력만으로 이 동작들이 검증됐다고 판단하지 않는다.

## Backend — Red 테스트

남아 있는 `RedTest`도 정리 대상이다. 실제 검증 대상과 중복 여부를 확인한 뒤 `<검증 대상>Test`로 변경하거나 기존 테스트에 합친다. 마커·알림 대상의 처리 내역은 마지막의 반영 이력에서 확인한다.

S4·S3-2 문서 정리에서 수정한 `BoardApiSseConvergenceHarnessRedTest`도 포함한다. 실행 입력은 공용 fixture와 실패 사례 테스트 리소스로 전환했다. 이름·검증 책임 정리는 상황판 테스트를 다룰 때 진행한다. 기존 5개 검증은 조립·대역 기반이며 실제 SSE 수신·브라우저 표시 시험이 아니다.

| 영역 | 변경 대상 |
|---|---|
| account | [AuthPolicePhoneHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/account/AuthPolicePhoneHarnessRunnerRedTest.java) |
| account | [S1_2HarnessAuthMockRedTest](../../backend/src/test/java/com/surimap/account/S1_2HarnessAuthMockRedTest.java) |
| account | [S1_2HarnessSeedLoaderRedTest](../../backend/src/test/java/com/surimap/account/S1_2HarnessSeedLoaderRedTest.java) |
| account | [S1_2RoleChannelMatrixBoundaryRedTest](../../backend/src/test/java/com/surimap/account/S1_2RoleChannelMatrixBoundaryRedTest.java) |
| auth/guard | [GuardAliasRedTest](../../backend/src/test/java/com/surimap/auth/guard/GuardAliasRedTest.java) |
| board | [BoardApiSseConvergenceHarnessRedTest](../../backend/src/test/java/com/surimap/board/BoardApiSseConvergenceHarnessRedTest.java) |
| board | [BoardDtoAssemblyModelRedTest](../../backend/src/test/java/com/surimap/board/BoardDtoAssemblyModelRedTest.java) |
| board | [BoardRefetchConvergenceLagRedTest](../../backend/src/test/java/com/surimap/board/BoardRefetchConvergenceLagRedTest.java) |
| board | [BoardRefetchGuardRedTest](../../backend/src/test/java/com/surimap/board/BoardRefetchGuardRedTest.java) |
| board | [IncidentTerminalPackageBadgePrivacyRedTest](../../backend/src/test/java/com/surimap/board/IncidentTerminalPackageBadgePrivacyRedTest.java) |
| board | [PackageBadgeBoardAssemblyRedTest](../../backend/src/test/java/com/surimap/board/PackageBadgeBoardAssemblyRedTest.java) |
| eventhub | [EventDispatchJobTransactionRedTest](../../backend/src/test/java/com/surimap/eventhub/EventDispatchJobTransactionRedTest.java) |
| eventhub | [EventHubHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/eventhub/EventHubHarnessRunnerRedTest.java) |
| eventhub | [OwnerPayloadSchemaValidationRedTest](../../backend/src/test/java/com/surimap/eventhub/OwnerPayloadSchemaValidationRedTest.java) |
| handover | [HandoverMemoContextBindingRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoContextBindingRedTest.java) |
| handover | [HandoverMemoCreatePublishRequestRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoCreatePublishRequestRedTest.java) |
| harness/sc09 | [Sc07Sc09OfflineReplayHarnessRedTest](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRedTest.java) |
| maparea | [SearchAreaAssignmentWriteRedTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaAssignmentWriteRedTest.java) |
| offlinepackage | [OfflinePackageInstallationApiRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageInstallationApiRedTest.java) |
| offlinepackage | [OfflinePackagePurgeHookRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackagePurgeHookRedTest.java) |
| offlinepackage | [OfflinePackageSearchAreaChangedConsumerRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageSearchAreaChangedConsumerRedTest.java) |
| offlinepackage | [Sc03PackageTileHarnessRedTest](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRedTest.java) |
| operationalperiod | [OpTransitionRedTest](../../backend/src/test/java/com/surimap/operationalperiod/OpTransitionRedTest.java) |
| policephone | [FcmTokenQueryRedTest](../../backend/src/test/java/com/surimap/policephone/FcmTokenQueryRedTest.java) |
| policephone | [PolicePhoneFreshnessQueryRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneFreshnessQueryRedTest.java) |
| policephone | [PolicePhoneHeartbeatApiRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatApiRedTest.java) |
| policephone | [PolicePhoneHeartbeatPublishRequestRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatPublishRequestRedTest.java) |
| retention/purge | [PurgeCoordinatorRedTest](../../backend/src/test/java/com/surimap/retention/purge/PurgeCoordinatorRedTest.java) |
| retention/purge | [PurgeOrchestrationHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/retention/purge/PurgeOrchestrationHarnessRunnerRedTest.java) |
| searcharea | [SearchAreaApiServicePersistenceRedTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaApiServicePersistenceRedTest.java) |
| searcharea | [SearchAreaAssignmentPersistenceRedTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaAssignmentPersistenceRedTest.java) |
| summary | [SearchHistorySummaryPublishRequestRedTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryPublishRequestRedTest.java) |

## Backend — Mapper·테스트 지원 코드

[Backend 테스트 기준](../../backend/AGENTS.md#테스트-기준)에 따라 검증 대상과 실행 역할을 드러낸다.

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [GeometrySpatialMapperIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/GeometrySpatialMapperIntegrationTest.java) | `GeometrySpatialMapperTest` |
| [CoreRuntimeSchemaMigrationIntegrationTest](../../backend/src/test/java/com/surimap/database/CoreRuntimeSchemaMigrationIntegrationTest.java) | 실제 PostgreSQL의 Flyway schema와 옛 ID 전환을 검사한다. `Integration` 분류를 덜고 migration 검증 대상으로 이름을 정리할 후보이며, 이번 SSE 순번 구현에서는 기존 파일을 변경하지 않았다. |
| [OpComparisonAnalysisMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonAnalysisMapperIntegrationTest.java) | `OpComparisonAnalysisMapperTest` |
| [OpComparisonRegionFactMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonRegionFactMapperIntegrationTest.java) | `OpComparisonRegionFactMapperTest` |
| [SearchHistorySummaryMapperIntegrationTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryMapperIntegrationTest.java) | `SearchHistorySummaryMapperTest` |
| [AuthPolicePhoneHarnessRunner](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc02ToSc12BoardConvergenceHarnessRunner](../../backend/src/test/java/com/surimap/board/Sc02ToSc12BoardConvergenceHarnessRunner.java) | 공용 데이터로 응답을 조립하고 고정 시나리오 표를 반환함. 실제 시나리오·SSE 실행기로 오해되지 않도록 필요한 보조 역할부터 정리 |
| [EventHubHarnessRunner](../../backend/src/test/java/com/surimap/eventhub/harness/EventHubHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [InMemoryS4EventHubContract](../../backend/src/test/java/com/surimap/eventhub/harness/InMemoryS4EventHubContract.java) | `S4`·`Contract` 대신 테스트 입력·기록 대역이라는 역할을 드러낼 후보다. 2026-09-28 메모리 저장소를 `src/test`로 옮겨 이 대역에서만 재사용했으며 실제 DB 재전송 검증과 구분한다. |
| [Sc04SearchAreaHarnessTest](../../backend/src/test/java/com/surimap/harness/sc04/Sc04SearchAreaHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [Sc07Sc09OfflineReplayHarnessRunner](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc10OpHandoverHarnessTest](../../backend/src/test/java/com/surimap/harness/sc10/Sc10OpHandoverHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [IncidentFlowHarnessMockContractTest](../../backend/src/test/java/com/surimap/incident/IncidentFlowHarnessMockContractTest.java) | 공용 JSON 로딩과 인증·OP1·이벤트 대역 간 호출을 검사함. 실제 가져오기·DB 검증과 구분해 필요한 검사·이름을 결정 |
| [Sc03PackageTileHarnessRunner](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [PurgeOrchestrationHarnessRunner](../../backend/src/test/java/com/surimap/retention/purge/harness/PurgeOrchestrationHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |

## Backend — 시나리오 번호 파일

위 표에 있는 `Sc*` 파일과 함께 정리한다. 최종 이름은 검증 내용과 사용처를 확인한 뒤 정한다.

| 영역 | 변경 대상 |
|---|---|
| harness/sc01 | [Sc01IncidentStartIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartIntegrationTest.java) / [Sc01IncidentStartBridgeTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartBridgeTest.java) |
| harness/sc04 | [Sc04Fixtures](../../backend/src/test/java/com/surimap/harness/sc04/fixture/Sc04Fixtures.java) |
| harness/sc10 | [Sc10Fixtures](../../backend/src/test/java/com/surimap/harness/sc10/fixture/Sc10Fixtures.java) |
| harness/sc12 | [Sc12IncidentCloseDataPurgeIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc12/Sc12IncidentCloseDataPurgeIntegrationTest.java) |

## 인수인계·수색 차수

2026-09-18 다음 문서 정리 대상의 파일명을 확인했다. 아래 파일은 `ContractTest`라는 분류만으로 검증 대상·범위를 알기 어려워 추가했다. 최종 이름과 통합 여부는 테스트 내용을 확인한 뒤 정한다. 기존 목록의 `Sc10`·`Sc11`·`RedTest`·`Runner`·`FixtureTest`는 중복 등록하지 않았다.

| 영역 | 추가 후보 |
|---|---|
| handover | [HandoverMemoQueryContractTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoQueryContractTest.java) |
| handover | [SearchHistorySummaryReadOnlyApiContractTest](../../backend/src/test/java/com/surimap/handover/SearchHistorySummaryReadOnlyApiContractTest.java) |
| handover | [S8HandoverApiContractTest](../../backend/src/test/java/com/surimap/handover/S8HandoverApiContractTest.java) |
| handover | [HandoverTimelineApiContractTest](../../backend/src/test/java/com/surimap/handover/HandoverTimelineApiContractTest.java) |
| operationalperiod | [OperationalPeriodQueryCurrentContractTest](../../backend/src/test/java/com/surimap/operationalperiod/OperationalPeriodQueryCurrentContractTest.java) |
| operationalperiod | [CurrentOpGuardMockContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpGuardMockContractTest.java) |
| operationalperiod | [CurrentOpConsumerContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpConsumerContractTest.java) |
| operationalperiod | [InitialOperationalPeriodCreatorMockContractTest](../../backend/src/test/java/com/surimap/operationalperiod/InitialOperationalPeriodCreatorMockContractTest.java) |
| operationalperiod | [CurrentOpGuardContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpGuardContractTest.java) |
| operationalperiod | [Op1BootstrapContractTest](../../backend/src/test/java/com/surimap/operationalperiod/Op1BootstrapContractTest.java) |
| operationalperiod | [OperationalPeriodApiContractTest](../../backend/src/test/java/com/surimap/operationalperiod/OperationalPeriodApiContractTest.java) |

S8 내용·연결 검토 중 다음 후보도 추가했다. 이름만 바꾸면 실제 동작을 숨길 수 있는 경우에는 구현 범위부터 확인한다.

| 추가 후보 | 확인한 이유 |
|---|---|
| [OpComparisonApiContractTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonApiContractTest.java) | `Contract` 분류 대신 실제 Controller·Service 등 검증 대상을 드러낼 필요가 있음 |
| [SearchHistorySummaryGenerationJob](../../backend/src/main/java/com/surimap/summary/SearchHistorySummaryGenerationJob.java)의 `enqueue*` | 영속 작업 큐에 넣지 않고 커밋 후 콜백에서 동기 호출함. `Job`·`enqueue`가 암시하는 비동기 실행·복구와 다름 |
| [features/board/components/s8OpHandoverSlotTypes.tsx](../../frontend/src/features/board/components/s8OpHandoverSlotTypes.tsx) | Spec 번호 대신 상황판의 차수·인수인계 표시 타입과 보조 컴포넌트라는 역할을 드러낼 필요가 있음 |
| [src/components/s8OpHandoverSlotTypes.ts](../../frontend/src/components/s8OpHandoverSlotTypes.ts) | 위 타입 파일과의 사용 관계·중복을 확인한 뒤 Spec 번호를 제거 |
| [S8OpHandoverSlots.test.tsx](../../frontend/src/features/board/components/S8OpHandoverSlots.test.tsx) | Spec 번호 대신 검증하는 표시 동작을 드러낼 필요가 있음 |
| [S8IdempotencyFixtures](../../backend/src/test/java/com/surimap/operationalperiod/fixture/S8IdempotencyFixtures.java) | Spec 번호 대신 차수 전환의 중복 요청·충돌 입력이라는 역할을 드러낼 필요가 있음 |
| [OperationalPeriodFixtureExactnessTest](../../backend/src/test/java/com/surimap/operationalperiod/fixturetest/OperationalPeriodFixtureExactnessTest.java) | 고정값 대조와 실제 차수 전환 검증을 구분. 옛 상태·URL을 고정한 검사의 필요성을 함께 확인 |
| [IncidentHandoverSupportAssignmentContractTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentContractTest.java) | `Contract` 분류 대신 인계·지원 배정의 실제 검증 대상을 드러낼 필요가 있음 |

## 사건 가져오기·배정·종료

2026-09-21 S1-1의 구현·소비 테스트를 확인하며 추가했다. 아래 테스트는 `Contract`·`Fixture`·`Integration` 분류 대신 검증 대상을 드러낼 필요가 있다. 최종 이름·통합 여부는 실제 연결 범위와 중복을 확인한 뒤 정한다. 기존 `Sc01`·`Sc12`·사건 흐름·인계 테스트는 위 항목을 유지하거나 갱신했다.

| 추가 후보 | 확인할 점 |
|---|---|
| [IncidentActiveReadDtoContractTest](../../backend/src/test/java/com/surimap/incident/IncidentActiveReadDtoContractTest.java) | 진행 중 사건의 DTO 필드 검사와 실제 조회 검증을 구분 |
| [IncidentTerminalReadDtoContractTest](../../backend/src/test/java/com/surimap/incident/IncidentTerminalReadDtoContractTest.java) | 종료 응답의 개인정보 제외 검사와 실제 정보 파기 검증을 구분 |
| [MissingPersonConsumerAllowlistContractTest](../../backend/src/test/java/com/surimap/incident/MissingPersonConsumerAllowlistContractTest.java) | 응답·조회 모델의 허용 필드 검사와 실제 개인정보 접근 제어 검증을 구분 |
| [IncidentAssignmentImportContractTest](../../backend/src/test/java/com/surimap/incident/IncidentAssignmentImportContractTest.java) | 배정 반영 Service의 검증 내용·연결 범위를 확인해 명명 |
| [IncidentLifecycleFixtureContractTest](../../backend/src/test/java/com/surimap/incident/IncidentLifecycleFixtureContractTest.java) | 공용 데이터의 종료 후 재전송 기대값 검사이며 실제 사건 종료 시험이 아님 |
| [IncidentCloseCommandContractTest](../../backend/src/test/java/com/surimap/incident/IncidentCloseCommandContractTest.java) | 종료 Controller·Service·DB 검증을 구분해 명명 |
| [IncidentImportApiContractTest](../../backend/src/test/java/com/surimap/incident/IncidentImportApiContractTest.java) | 가져오기 Controller·Service·DB 검증을 구분해 명명 |
| [Mock112WebhookIncidentCloseIntegrationTest](../../backend/src/test/java/com/surimap/incident/Mock112WebhookIncidentCloseIntegrationTest.java) | 웹훅 종료 처리의 실제 검증 대상·외부 대역 범위를 드러내도록 명명 |
| [IncidentAssignmentAccessGuard](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentAccessGuard.java) | 계정에 활성 배정이 하나라도 있는지만 검사함. 선택한 사건의 접근 권한까지 확인하는 이름으로 오해되지 않도록 책임부터 확인 |

## 인증·파기·수색 구역·경로·상황판

2026-09-21 S1-2 → S1-3 → S2 → S3-1 → S3-2를 확인하며 추가했다. 아래 테스트·지원 파일은 이름 패턴으로 찾은 후보이며, 이번에 모두 실행하거나 내부 검증을 검토한 것은 아니다. 기존 `RedTest`·`Sc*`·Runner 항목은 중복 등록하지 않았다. 테스트 지원 환경이 분명한 `PostGisIntegrationTestSupport`까지 일괄 변경하지 않는다.

| 영역 | 추가 후보·확인할 점 |
|---|---|
| 인증 | [WithMockAccountFixtureContractTest](../../backend/src/test/java/com/surimap/account/WithMockAccountFixtureContractTest.java) — `Fixture`·`Contract` 대신 인증 설정의 검증 대상 확인 |
| 인증 | [AuthPhoneApiIntegrationTest](../../backend/src/test/java/com/surimap/account/AuthPhoneApiIntegrationTest.java) — API·인증·DB의 실제 검증 경계를 확인해 명명 |
| 인증 | [AccountIdentityPersistenceIntegrationTest](../../backend/src/test/java/com/surimap/account/AccountIdentityPersistenceIntegrationTest.java) — 저장·Mapper 검증 대상 확인 |
| 인증 | [AuthPolicePhoneHarnessFixtures](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessFixtures.java) — `Harness` 대신 인증·업무폰 테스트 데이터라는 역할 검토 |
| 인증 | [RealS1_2AuthPolicePhoneContract](../../backend/src/test/java/com/surimap/account/harness/RealS1_2AuthPolicePhoneContract.java) — `Real`·Spec 번호·`Contract` 대신 실제 서비스 연결 범위 확인 |
| 업무폰 | [PolicePhonePersistenceIntegrationTest](../../backend/src/test/java/com/surimap/policephone/PolicePhonePersistenceIntegrationTest.java) — Service·Mapper 중 검증 책임 확인 |
| 업무폰 | [PolicePhoneHeartbeatIntegrationTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatIntegrationTest.java) — heartbeat 저장·이벤트 검증 범위 확인 |
| Android 인증 | [AuthBootstrapContractTest](../../android/app/src/test/java/com/surimap/feature/bootstrap/AuthBootstrapContractTest.kt) — 인증 초기화의 검증 동작 기준으로 명명 |
| Android 업무폰 | [PolicePhoneNavigationContractTest](../../android/app/src/test/java/com/surimap/ui/navigation/PolicePhoneNavigationContractTest.kt) — 기존 대표 패턴의 실제 경로 등록. 탐색·복귀 동작의 검증 대상 확인 |
| 데이터 파기 | [InMemoryS1_3PurgeOrchestrationContract](../../backend/src/test/java/com/surimap/retention/purge/harness/InMemoryS1_3PurgeOrchestrationContract.java) — Spec 번호·`Contract` 대신 대역 역할 확인 |
| 데이터 파기 | [PurgeOrchestrationHarnessFixtures](../../backend/src/test/java/com/surimap/retention/purge/harness/PurgeOrchestrationHarnessFixtures.java) — 파기 단계·실패 입력을 준비하는 범위 확인 |
| 데이터 파기 | [MockPurgeHookRegistryContractTest](../../backend/src/test/java/com/surimap/retention/purge/MockPurgeHookRegistryContractTest.java) — 대역 등록 검사와 실제 파기 연결 검증을 구분 |
| 수색 구역 | [SearchAreaBoundaryAlertApiIntegrationTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaBoundaryAlertApiIntegrationTest.java) — 구역 경계 API의 실제 검증 대상 확인 |
| 수색 구역 | [SearchAreaApiContractTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaApiContractTest.java) — Controller·Service 검증 책임 확인 |
| 수색 구역 | [SearchAreaQueryByOpContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryByOpContractTest.java) — 차수별 구역 조회의 대상·대역 범위 확인 |
| 수색 구역 | [SearchAreaAssignmentQueryMockContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaAssignmentQueryMockContractTest.java) — 배정 조회 대역과 실제 SQL 검증 구분 |
| 수색 구역 | [SearchAreaQueryByIncidentContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryByIncidentContractTest.java) — 사건별 조회 대상에 맞춰 명명 |
| 수색 구역 | [SearchAreaQueryOverallOfContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryOverallOfContractTest.java) — 전체 수색 범위 조회 대상에 맞춰 명명 |
| 도형 저장 | [PostGisExtensionIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/PostGisExtensionIntegrationTest.java) — 확장 설치·실제 공간 SQL의 검증 범위 확인 |
| 도형 저장 | [JtsGeometryTypeHandlerIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/JtsGeometryTypeHandlerIntegrationTest.java) — 타입 변환·DB 왕복 검증 범위 확인 |
| 수색 경로 | [GpsPointValidationFixturesTest](../../backend/src/test/java/com/surimap/domain/path/validation/GpsPointValidationFixturesTest.java) — 데이터 일치와 GPS 품질 동작 검증을 구분 |
| 상황판 | [IncidentBoardApiContractTest](../../backend/src/test/java/com/surimap/board/IncidentBoardApiContractTest.java) — HTTP 응답·조립 검증 경계 확인 |
| 상황판 | [IncidentBoardSourceRowCollectorIntegrationTest](../../backend/src/test/java/com/surimap/board/IncidentBoardSourceRowCollectorIntegrationTest.java) — 여러 원본 조회·조립의 실제 연결 범위 확인 |
| Frontend 상황판 | [S3PathMarkerSlots.test.tsx](../../frontend/src/features/board/components/S3PathMarkerSlots.test.tsx) — Spec 번호 대신 경로·마커 표시의 검증 동작 확인 |

다음 이름은 코드를 읽고 예상 동작과의 차이를 확인했다. 이름만 바꿔 구현 차이를 숨기지 않는다.

| 후보 | 확인한 차이 |
|---|---|
| [SearchPathMapperTest](../../backend/src/test/java/com/surimap/domain/path/SearchPathMapperTest.java)의 `findPathsFiltersByIncidentOpPolicePhoneAndAccount` | 2026-10-02 확인: 실제 조회 조건은 사건·차수·계정이며 이름의 PolicePhone 조건은 없다. 해당 테스트 정리 때 실제 검증 조건·밑줄 메서드명으로 맞추고, 클래스의 영문 DisplayName도 기존 규칙에 맞춰 제거한다. 이번에는 후보만 등록했다. |
| [PolicePhonePersistenceService.encryptToken](../../backend/src/main/java/com/surimap/policephone/PolicePhonePersistenceService.java) | 암호화하지 않고 `cipher:`만 붙임. 필요한 토큰 보호 방식을 먼저 정하고 이름을 맞출 것 |
| [GeometryPolicy.s2HarnessDefault](../../backend/src/main/java/com/surimap/maparea/geometry/policy/GeometryPolicy.java) | 운영 검증 설정에서도 쓰는 값에 Spec 번호·시험용 기본값 이름이 남음. 적용 범위 확인 |
| [DefaultIncidentBoardSourceRowCollector](../../backend/src/main/java/com/surimap/board/DefaultIncidentBoardSourceRowCollector.java)의 `geometryHash`·`latestEventId` 생성 | 좌표 해시·실제 이벤트 조회로 오해할 수 있으나 ID·상태·버전으로 값을 만듦. 공개 필드 호환성과 실제 추적 요구를 함께 검토 |
| [OfflinePackageRepositoryReadOnlyQueryTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageRepositoryReadOnlyQueryTest.java) | 읽기 전용 조회뿐 아니라 manifest의 타일 계산·담당 구역 필터도 검증한다. 실제 검증 대상에 맞춘 이름·통합과 영문 DisplayName을 검토한다. 커넥션 대기 회귀는 실제 DB를 쓰는 `OfflinePackageServiceTest`로 구분했으며 기존 테스트 이름은 바꾸지 않았다. |

## 지도·AI 연동·GPS 수집

2026-09-21 Architecture를 코드와 대조하며 발견했다. 후보만 등록하며 실제 이름·동작은 바꾸지 않았다.

| 후보 | 확인한 이유 |
|---|---|
| [LocalTileService](../../backend/src/main/java/com/surimap/offlinepackage/service/LocalTileService.java)·[LocalTileServiceTest](../../backend/src/test/java/com/surimap/offlinepackage/LocalTileServiceTest.java) | 로컬 지도 파일을 읽는 구현이 아니라 고정 시험 타일을 제공한다. `Local` 대신 시험 데이터 제공 역할이 드러나도록 함께 검토 |
| [OpenAiComparisonConfig](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonConfig.java)·[OpenAiComparisonProperties](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonProperties.java) | 차수 비교뿐 아니라 수색 이력 요약에도 쓰는 공통 연동 설정이다. `Comparison`이 책임을 좁혀 보이게 하므로 실제 사용 범위에 맞춰 검토 |
| [SearchPathGpsBatchRecorder.FLUSH_INTERVAL_MS](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathGpsBatchRecorder.kt) | 고정 HTTP 전송 주기가 아니라 모인 좌표의 측정 시간 범위를 검사하는 값이다. 시간 범위 조건이 드러나도록 검토 |
| [TileManifestFixtureExactnessTest](../../backend/src/test/java/com/surimap/offlinepackage/TileManifestFixtureExactnessTest.java) | 고정 시험 데이터의 값·참조를 검사한다. 실제 타일 준비·서빙 검증과 구분하고 필요한 검사·이름을 함께 정리 |
| [OfflinePackageManifestSourceIntegrationTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageManifestSourceIntegrationTest.java) | `Integration` 분류 대신 검증 대상에 맞춰 정리할 후보다. 사건·차수·구역·마커 조회와 이벤트가 대역인 구성을 실제 전체 연동 시험과 구분 |

## 이벤트 전송 처리 클래스

2026-09-25 [역할 분리 합의](../features/event-delivery.md#sse-재연결과-중복-처리)에 따라 정한 이름을 2026-09-26 구현에 반영했다. DB 재전송 조회·중단 복구까지 완료한 것은 아니다.

| 변경 | 현재 책임 |
|---|---|
| `EventDispatchJobDispatcher` → [EventDispatchJobService](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobService.java) | 작업 선점·순번 확정·처리 결과를 DB 트랜잭션으로 저장한다. SSE 전송이나 FCM 성공 판정은 맡지 않는다. |
| `EventDispatchJobPollingWorker` → [EventDispatchJobWorker](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJobWorker.java) | 커밋 후 깨우기·주기 조회를 받고 서비스 커밋 → SSE 전송 → 결과 저장을 실행한다. 기존 Dispatcher 테스트도 `EventDispatchJobWorkerTest`로 옮겼다. |

## API 문서 검사

| API 문서 검사 후보 | 확인한 이유 |
|---|---|
| [ApiImplementationStatusCoverageTest](../../backend/src/test/java/com/surimap/architecture/ApiImplementationStatusCoverageTest.java) | 이름과 달리 API 동작 전체가 아니라 Markdown URL 포함 관계와 Spring 경로 등록만 확인한다. REST Docs 전환에서 대체·제거 여부를 먼저 정하고, 남길 경우 검증 대상을 드러내는 이름·한글 DisplayName·밑줄 메서드명으로 정리한다. 현재 Java 코드는 유지했다. |

## Backend — 기존 호환 이름

| 현재 이름 | 확인할 점 |
|---|---|
| [RequireDevice](../../backend/src/main/java/com/surimap/common/auth/RequireDevice.java) / [RequireDeviceRegistered](../../backend/src/main/java/com/surimap/common/auth/RequireDeviceRegistered.java) / [RequireDeviceAssigned](../../backend/src/main/java/com/surimap/common/auth/RequireDeviceAssigned.java) | 기존 PolicePhone 가드와 호환 관계 확인; 애너테이션·오류 코드 변경은 별도 합의 |
| [DeviceRequiredException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceRequiredException.java) / [DeviceNotRegisteredException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceNotRegisteredException.java) / [DeviceNotAssignedException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceNotAssignedException.java) | 위 별칭 가드의 `device_*` 오류 응답과 함께 확인 |

## Frontend

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [nginx.conf.test.ts의 `frontend nginx runtime routing`·`EC2 TLS reverse proxy`](../../frontend/nginx.conf.test.ts) | 실제 HTTP가 아니라 설정 문자열을 검사하며, 상대 redirect 검사는 EC2 전용이 아니다. 검증 범위와 한글 동작명이 드러나도록 후속 정리한다. 주소 변경의 실제 HTTP 검사는 별도 Docker 검사와 구분한다. |
| [useMarkerNotificationQueue의 shownMarkerNotificationIdsRef](../../frontend/src/app/useMarkerNotificationQueue.ts) | 팝업 표시를 확인하지 않고 알림을 받는 즉시 ID를 기록한다. `shown`은 표시 완료로 오해하게 하므로 수신 중복 방지 역할에 맞춰 명명한다. 알림 수신과 표시를 구분하는 복구 정책은 [이벤트 전달 문서](../features/event-delivery.md)에서 정하며, 이름만 바꿔 복구 동작을 해결한 것으로 보지 않는다. |
| [상황판의 `재연결` 버튼](../../frontend/src/features/situationBoard/presentation/pages/SituationBoardPage.tsx)·[isInitialReconnecting](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts) | 2026-09-24 확인: 실제 동작은 최초 실패 후 `boardQuery.refetch()` 호출이다. SSE 연결 재시작으로 오해할 수 있으므로 최초 조회 재시도에 맞춰 버튼·상태·CSS 이름을 함께 검토한다. 후보만 등록하며 이름·동작은 변경하지 않았다. |
| [Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx](../../frontend/src/features/board/components/Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx) | `Red` 제거, 수색 이력 요약 렌더링 검증 기준으로 명명 |
| [features/board/test/sc11OpSearchHistorySummaryHarnessRunner.ts](../../frontend/src/features/board/test/sc11OpSearchHistorySummaryHarnessRunner.ts) | 렌더링 시나리오 실행 역할 기준으로 명명 |
| [src/test/sc11OpSearchHistorySummaryHarnessRunner.ts](../../frontend/src/test/sc11OpSearchHistorySummaryHarnessRunner.ts) | 위 파일과의 사용 관계 확인 후 결정 |
| [mockAreaEdit.ts / MOCK_PAGE_STATE](../../frontend/src/features/areaEdit/presentation/constants/mockAreaEdit.ts) | 실사용 타입·상수와 예제 데이터 구분; 기본 화면 상태는 `DEFAULT_PAGE_STATE` 후보 |
| [mockSituationBoard.ts](../../frontend/src/features/situationBoard/presentation/constants/mockSituationBoard.ts) | 실제 뷰모델 재수출 파일; `situationBoardViewModel` 직접 참조 검토 |
| [assignedAreaIds / unassignedAreaCount](../../frontend/src/features/areaEdit/presentation/pages/AreaEditPage.tsx) | 담당자 배정이 아닌 구역 도형 작성 완료·미완료에 맞춰 명명 |
| [unassignedPhoneCount](../../frontend/src/features/areaEdit/presentation/components/AreaHierarchyPanel.tsx) | `missingAreaGeometryCount` 후보 — 업무폰 수가 아닌 도형 미지정 구역 수 |
| [resolveInitialMapView의 overall-ready·overallSearchArea](../../frontend/src/features/situationBoard/presentation/components/map/searchMapCanvasData.ts) | 전체 구역이 없어도 다른 Polygon 범위가 있으면 이 상태·필드로 반환한다. 실제 포함 구역과 초기 지도 표시 상태의 의미를 확인해 명명. 공개 API 필드 변경과 구분 |
| [createDeviceTitle / createDeviceMeta](../../frontend/src/features/offlinePackage/presentation/model/offlinePackageStatusPageViewModel.ts) | `createPolicePhoneTitle` / `createPolicePhoneMeta` |
| [errorCode()](../../frontend/src/shared/api/client.ts) | `resolveApiErrorCode` — 응답 본문 또는 HTTP 상태로 오류 코드 결정 |
| [incidentBoardQueryKeyParams() / emptyCursor()](../../frontend/src/features/board/api/incidentBoardApi.ts) | `buildIncidentBoardQueryKeyParams` / `createEmptyBoardSourceCursor` |
| [geometryUtils.ts](../../frontend/src/features/areaEdit/presentation/utils/geometryUtils.ts)·[AreaEditMapCanvas.tsx](../../frontend/src/features/areaEdit/presentation/components/AreaEditMapCanvas.tsx)의 signedArea() / isBetween() | 각각 삼각형 부호 면적의 2배 계산·좌표 범위 검사에 맞춰 명명 |

## Android

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [AndroidHarnessFixtureCatalog / AndroidHarnessFixtureEntry](../../android/app/src/test/java/com/surimap/testing/AndroidHarnessFixtureCatalog.kt) | 공용 JSON의 catalog를 읽는 테스트 지원 코드다. 시나리오 실행기처럼 보이는 `Harness`를 덜고 데이터 조회 역할 기준으로 명명 검토 |
| [AndroidHarnessFixtureCatalogTest](../../android/app/src/test/java/com/surimap/testing/AndroidHarnessFixtureCatalogTest.kt) | 리소스 로딩·ID·사용 시나리오 검증이다. 위 로더 이름과 함께 정리하며 실제 앱 흐름 검증과 구분 |
| [HarnessSyncStatus / EnqueueResult.harnessStatus](../../android/app/src/main/java/com/surimap/core/sync/LocalWriteOperation.kt) | `LocalSyncStatus` / `localSyncStatus` 후보 — 실제 앱의 로컬 동기화 상태 |
| [initialHarnessStatus](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt) | `initialLocalSyncStatus` 후보 — 위 상태 이름과 함께 변경 |
| [HarnessStatusMapper](../../android/app/src/main/java/com/surimap/core/sync/OutboxStateMachine.kt) | 상태 변환 역할·실사용 여부 확인 후 결정 |
| [MarkerPhotoPayloadReader / read()](../../android/app/src/main/java/com/surimap/feature/marker/data/MarkerPhotoPayloadReader.kt) | 읽기뿐 아니라 이미지 변환·압축을 하는 역할이 드러나도록 명명 |
| [OutboxHarnessIntegrationTest](../../android/app/src/test/java/com/surimap/core/sync/OutboxHarnessIntegrationTest.kt) | 공용 catalog와 Outbox 대역 흐름을 확인한다. `Harness`·`Integration` 대신 실제 검증 대상을 확인해 `<대상>Test`로 정리 |

## mock-112

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [api()](../../mock-112/src/main/resources/static/app.js) | `sendApiRequest` |
| [resetAll()](../../mock-112/src/main/resources/static/app.js) | `resetMock112Data` — 초기화 대상 범위 표시 |
| [KeycloakThemeContractTest](../../mock-112/src/test/java/com/mock112/config/KeycloakThemeContractTest.java) | S1-1 연결 범위 탐색에서 경로 등록. `Contract` 분류 대신 테마의 실제 검증 대상·범위를 확인해 명명 |
| [S3CompatibleMissingPersonPhotoStorageIT](../../mock-112/src/test/java/com/mock112/photo/S3CompatibleMissingPersonPhotoStorageIT.java) | 같은 탐색에서 발견. `IT` 분류 대신 저장 동작의 검증 대상을 드러내되 빌드의 테스트 선택 규칙도 함께 확인 |

## 시연 도구·CI 검사

2026-09-21 문서 정리에서 찾았다. 테스트 4개는 이름·경로를 확인한 후보이며 내부 검증을 모두 검토하거나 실행한 것은 아니다. 이름 변경은 각 대상의 동작·사용처를 정리할 때 진행한다.

| 추가 후보 | 확인할 점 |
|---|---|
| [AndroidUiScenarioCoverageTest](../../android/app/src/test/java/com/surimap/feature/AndroidUiScenarioCoverageTest.kt) | `ScenarioCoverage`가 실제 화면 흐름 검증을 뜻하는지 확인하고 대상·검증 범위로 명명 |
| [PoliComponentVariantContractTest](../../android/app/src/test/java/com/surimap/ui/components/PoliComponentVariantContractTest.kt) | `Contract` 분류 대신 컴포넌트 상태·표시 중 실제 검증 대상을 확인 |
| [NetworkStateFixturesTest](../../android/app/src/test/java/com/surimap/testing/NetworkStateFixturesTest.kt) | 입력 값 검사와 네트워크 상태 전이 검증을 구분 |
| [DemoScenarioFixturesTest](../../backend/src/test/java/com/surimap/demo/DemoScenarioFixturesTest.java) | 고정 입력 검사와 실제 시연 흐름 실행을 구분 |

| 후속 후보 | 확인할 점 |
|---|---|
| [infra/ci/verify-evidence.sh](../../infra/ci/verify-evidence.sh)·[사용 안내](../../infra/ci/README.md) | `RED`·`L2-D01` 표현과 포괄적인 `evidence` 이름이 남아 있다. 현재 Jenkinsfile의 직접 호출은 찾지 못했으며 CI 결과 파일 존재·문구 검사와 실제 품질 판정을 구분해 필요성·이름·안내를 함께 검토 |
| [collect-s4-evidence.sh](../../infra/ci/collect-s4-evidence.sh)·[verify-s4-evidence.sh](../../infra/ci/verify-s4-evidence.sh) | Jenkins에서 호출한다. 2026-09-28 DB 재전송·실시간 전송 테스트 참조를 갱신하고 고정 성공률을 제거했으며 건너뜀·0개 실행·누락 결과를 통과로 처리하지 않게 했다. `S4`·`evidence` 파일명은 CI 호출·산출물 참조와 함께 정리할 후보로 남긴다. |

이미 합의한 k6 이름은 유지한다.

## 반영 이력 요약

- 2026-09-30 SSE 운영 코드의 이름·패키지·Worker 책임 분리를 반영했다. `EventDispatchJobOwnershipRedTest`는 옛 패키지명·접두어·자체 문자열·메서드 존재만 검사하므로 제거했다. 실제 대체 빈 등록은 `PolicePhoneHeartbeatConfigTest`, DB 저장·트랜잭션·재전송은 기존 동작 테스트로 확인한다. 발행 입력 검증·기록용 발행자 테스트는 `EventPublishRequestValidatorTest`·`CapturingEventPublisherTest`로 정리했다. Docker에서 최종 전체 테스트 1,348개가 통과했다(4분 45초, 실패·오류·건너뜀 0, 기본 성능 태그 제외). 변경된 테스트 이름의 CI 결과 수집·검증도 통과했다. 배포·브라우저 검증은 별도다.

2026-09-29 사용자 합의로 `ControllerResponseEntityContractTest`를 삭제했다. 반환 타입 전수 검사를 이름만 바꿔 유지하지 않고, [EventStreamControllerTest](../../backend/src/test/java/com/surimap/api/controller/sse/ServerSentEventControllerTest.java)에서 실제 SSE·JSON 응답의 상태·Content-Type·본문·스트림 시작 여부를 검증한다. 일반 JSON Controller의 `ResponseEntity` 작성 규칙은 유지하며 삭제한 코드는 Git 이력에서 확인할 수 있다.

2026-09-29 비차단 전송 구현에서 `SseEmitterLiveEventSink`를 [SseConnection](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventConnection.java)으로 교체했다. 실시간 전용 수신기가 아니라 한 연결의 재전송·실시간 순서·대기 한도를 관리한다. `SseStreamEmitter`도 [SseStreamResponse](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventStream.java)로 교체해 Servlet 쓰기와 MVC 응답 종료를 맡겼다. 공개 SSE 이름·필드는 바꾸지 않았다.

2026-09-28: `SseSequenceEnvelopeReplayTest`의 형식·순번 검사를 실제 DB를 사용하는 `SseReplayServiceTest`로 통합하고 옛 파일은 제거했다. 중복 재시도는 `SseStreamServiceTest`에서 DB 이력 하나·같은 순번 재전송으로 확인한다. 메모리 저장소 3개는 옛 테스트 입력으로만 남겨 `src/test`로 이동했다. 삭제·이동 전 코드는 Git 이력에서 복원할 수 있다.

2026-09-09~15에 정리한 마커·SSE 이름과 테스트의 요약이다. 파일별 이전·이후 이름과 검증 내역은 [마커·알림 원문](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/refactoring/codebase-naming-candidates.md#마커알림--먼저-진행), [SSE·인증 원문](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/refactoring/codebase-naming-candidates.md#sse인증--승인한-이름-반영)에서 확인한다. 2026-09-22 문서 축약 이후의 코드 변경은 아래 날짜를 붙여 구분한다.

| 정리한 대상 | 반영 결과 |
|---|---|
| SSE 전송 실패 검사 (2026-09-29) | [SseConnectionRegistryTest](../../backend/src/test/java/com/surimap/global/sse/ServerSentEventConnectionRegistryTest.java)의 `without_completing_emitter`를 `without_calling_close`로 바꿨다. 표시명·주석도 실패 연결 제외·close() 미호출·정상 연결 전달로 맞췄으며 실제 HTTP 정리를 검증했다는 표현은 제거했다. |
| 종료 사건 재접속 검사 (2026-09-29) | [EventStreamControllerTest](../../backend/src/test/java/com/surimap/api/controller/sse/ServerSentEventControllerTest.java)의 `terminal_replay_completes_response_without_registering_live_connection`을 `terminal_replay_completes_response_and_unregisters_connection`으로 바꿨다. 등록하지 않는 동작이 아니라 종료 이력 처리 후 응답 완료·등록 해제를 드러낸다. |
| 느린 연결의 전송 재개 검사 (2026-09-29) | [SseStreamResponseTest](../../backend/src/test/java/com/surimap/global/sse/ServerSentEventStreamTest.java)의 3조건에 한글 기대 결과를 각각 붙였다. 실시간 전송 재개, 다음 이력 페이지·실시간 전송, 남은 이력 대신 종료 알림을 받고 응답 종료를 구분한다. 실행 입력과 검증문은 유지했다. |
| SSE HTTP 응답 검증 (2026-09-27) | `EventStreamControllerRedTest`를 [EventStreamControllerTest](../../backend/src/test/java/com/surimap/api/controller/sse/ServerSentEventControllerTest.java)로 변경했다. 한글 메서드 `DisplayName`·밑줄 메서드명·given/when/then 설명을 적용하고, 응답 준비 후 전송과 시작 실패·종료 처리를 MockMvc에서 검증한다. 종료 사건 재접속 검사는 전송 작업을 실행하도록 기존 Service 테스트에서 옮겼다. |
| 이벤트 전송 작업의 저장·조회 객체 (2026-09-26) | `EventDispatchJobRow`·`EventDispatchJobDispatchRecord`를 [EventDispatchJob](../../backend/src/main/java/com/surimap/global/sse/ServerSentEventJob.java) class 하나로 통합했다. 같은 이벤트 내용과 전송 상태·SSE 순번을 저장·조회한다. 이후 같은 날 Service·worker 책임을 분리했으며 DB 재전송 조회 연결은 남아 있다. |
| 마커 생성 검증·사진 첨부 구분 (2026-09-22) | `validateCreateRequest`·`validateAppAuthentication`·`validateIdempotencyKey`로 검사 대상을 구분했다. `attachPhotosForMarkerCreation`은 초기 사진 첨부, `attachPhotoToExistingMarker`는 생성 후 마커 수정까지 담당한다. 파일 확인·사진 저장은 `attachUploadedPhoto`로 공유한다. [반영 동작과 검증 범위](../features/marker-photo.md#사진을-포함한-마커-생성과-생성-후-사진-추가). |
| 마커 생성·사진·알림의 `RedTest`·`Sc06`·`Sc08`·`ContractTest` | 필요한 검증을 `MarkerTest`·`MarkerWriteAccessValidatorTest`·`AppMarkerServiceTest`·Mapper 테스트에 모았다. SQL 문자열·가짜 표시 결과 검사는 실제 서비스·DB 검증과 구분해 제거했다. |
| 알림 전달·조회 | 전용 FCM 전달 로직은 `MarkerNotificationService`, 조회는 `MarkerNotificationMapper`로 모았다. 미사용 지원 요청·표시 코드는 제거하고, 알림 전용 타입에는 `MarkerNotification`을 드러냈다. 상황판 슬롯은 `marker_notification`을 사용한다. |
| 마커 조회·응답 | `MarkerQuery`·중복 결과 객체를 제거하고 `api/service/marker/MarkerService`로 통합했다. 응답은 `MarkersServiceResponse`의 내부 class로 모으고 응답 변환을 도메인에서 DTO로 옮겼다. |
| 오류·인증·좌표 | 공통 예외 처리로 통합하고 `MarkerAuthenticationResolver`·`GeoJsonPoint.roundToSixDecimals()`로 역할을 드러냈다. 공개 오류·좌표 형식·기존 요청 해시 비교는 유지했다. |
| 기준 마커·운영 패키지 | 초기 등록은 `ReferenceMarkerSeedService`에 모았다. 공용 FCM은 `client/fcm`, 설정은 `config/fcm`, 사진 파기 계약은 `api/service/photo`로 옮기고 운영 `com/surimap/marker` 디렉터리를 제거했다. |
| SSE·인증 이름 | 연결 관리는 `SseConnectionRegistry`, 동작은 `registerForIncident`·`sendToIncident` 등으로 정리했다. 인증 필터와 테스트 메서드도 동작을 나타내는 이름으로 바꿨다. |
| SSE 테스트 | 전달·연결·저장·재전송을 대상별로 모으고 밑줄 메서드명·한글 `DisplayName`·given/when/then 설명을 적용했다. 이후 `EventDispatchJobDispatcherTest`는 `EventDispatchJobWorkerTest`로 변경하고 DB 트랜잭션 검증을 `EventDispatchJobServiceTest`에 추가했다. |
| 옛 단말 복구 도구 | `check_l4_d01_runtime_preflight.py`·`run_l4_d01_deployed_stability.py`는 호출자가 없어 제거했다. 실행하지 않았으며 [복원 근거와 주의점](../test-results/android-network-recovery-2026-05-14/README.md#옛-기록도구의-복원)을 남겼다. |

당시 마커 서비스 검증은 실제 DB와 외부 FCM 대역을 사용했고, SSE 서비스 검증은 실제 Spring 빈과 메모리 재전송 저장소를 사용했다. 이 테스트로 DB 재전송 내구성·실제 네트워크 수신·업무폰 알림 표시를 증명한 것은 아니다. 이름·통합 변경 뒤 관련 검증 88개 통과는 당시 결과이며 이번 문서 정리의 실행 결과가 아니다.

후속 인증 보관 수정은 [로컬 이슈 3](../issues/local/3-authenticated-sse-access-denied-on-disconnect.md)에, 실제 브라우저의 알림·지도 검증과 남은 실패 복구 문제는 [로컬 이슈 2](../issues/local/2-marker-notification-interrupted-by-sse-connection-failure.md)에 기록했다. 사진·실제 Android/FCM·배포 빌드·부하 검증과 구분한다.
