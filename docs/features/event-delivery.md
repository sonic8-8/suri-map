# 이벤트 전달과 재전송

SSE·FCM의 재연결, 실패 복구, 사건 종료 처리를 변경할 때 확인할 기록이다. 옛 S4의 동작 요구와 현재 구현의 차이를 보존한다. 아래의 **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 문서 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-18 코드 대조 결과다. 서버 재시작·실제 FCM 전달·브라우저 복구를 실행해 검증한 결과와 구분한다.

## 이벤트 저장과 전달

- **기존 요구**: 업무 데이터와 전송할 이벤트를 같은 트랜잭션에서 저장한다. 이벤트 검증·저장 실패 시 업무 데이터만 남기지 않으며, 커밋 이후 전달한다. 전달 과정에서 같은 `eventId`와 원본 데이터의 ID·상태·버전을 추적할 수 있어야 한다.
- 옛 공통 envelope는 `eventId`, `incidentId`, `type`, `schemaVersion`, `serverTs`, `payload`를 구분했다. 구체적인 payload는 이벤트별 생산자·소비자와 대조한다. 필드 이름이 같아도 업무 데이터의 버전, SSE 순번, 상황판 응답 버전이 같은 카운터라는 뜻은 아니다. 전체 옛 표는 [경계 원문](../guides/feature-verification.md#과거-문서의-차이와-복원)에서 확인한다.
- **현재 구현**: [DbEventHub](../../backend/src/main/java/com/surimap/eventhub/adapter/DbEventHub.java)는 전송 작업을 DB에 저장한다. [EventDispatchJobDispatcher](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobDispatcher.java)는 대기 작업을 SSE로 전달하고 완료·실패를 기록한다. 이 완료 상태는 브라우저 표시나 FCM 전달 성공을 뜻하지 않는다.
- **확인할 차이**: 재전송 이력은 [메모리 저장소](../../backend/src/main/java/com/surimap/eventhub/stream/EventStreamConfig.java)를 사용한다. 전송 작업이 DB에 남는다는 사실만으로 재시작 후 재전송까지 보장되지는 않는다.

## SSE 재연결과 중복 처리

- **식별자 구분**: SSE의 `id`와 재연결 헤더 `Last-Event-ID`는 사건별 숫자 순번이다. `data.eventId`는 같은 이벤트인지 구분하는 UUID다. 재전송 위치와 중복 판정을 같은 값으로 취급하지 않는다.
- **기존 요구**: 이벤트는 한 번만 이력에 추가하고, 마지막 수신 순번 이후의 이벤트를 순서대로 재전송한다. 소비자는 `eventId`로 중복 적용을 막는다. 순번 누락이나 재전송 불가 시 상황판 전체 조회로 복구한다.
- **현재 한계**: [SseReplayService](../../backend/src/main/java/com/surimap/eventhub/stream/SseReplayService.java)는 과거 순번을 받더라도 이력이 비어 있으면 항상 `409 gone_refetch_required`를 반환하지는 않는다. 따라서 이력 소실을 모두 감지한다고 설명할 수 없다.
- **복구 범위 구분**: [상황판 구독](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts)은 연결 성공·이벤트·오류에 재조회를 요청한다. [마커 알림 구독](../../frontend/src/features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications.ts)은 별도로 동작하며, 재전송 불가 시 순번을 초기화한다. 상황판 재조회가 놓친 알림 팝업까지 복원하는 것은 아니다. 현재 두 소비자 모두 순번 간격을 직접 비교하는 누락 검사는 없다.

## 연결 정리의 검증 범위

- [SseConnectionRegistryTest](../../backend/src/test/java/com/surimap/eventhub/stream/SseConnectionRegistryTest.java)는 테스트용 연결을 100회 등록한 뒤 직접 해제해 목록이 비워지는지 검사한다. [SseStreamServiceTest](../../backend/src/test/java/com/surimap/eventhub/SseStreamServiceTest.java)는 사건 종료 이벤트를 전달한 뒤 연결을 닫고 등록을 제거하는지 검사한다.
- 이 검사들은 실제 HTTP 연결 종료 시 콜백이 호출되는지, 브라우저 화면 이동 후 자동 정리가 되는지, 메모리 누수가 없는지까지 증명하지 않는다. 실제 초기 전송 중 연결이 끊긴 뒤 다음 이벤트까지 참조가 남는 조건은 [로컬 이슈 5의 진단](../issues/local/5-sse-initial-send-disconnect-cleanup.md)에 별도로 기록했다.
- 2026-09-21에는 위 테스트 코드와 [옛 검증 설명](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/evidence/backend/sse-stream-lifecycle.md)을 대조해 검증 범위를 이곳에 통합했다. 제품 테스트나 실제 연결 시험을 다시 실행한 결과는 아니다.

## 사건 종료와 데이터 파기

- **기존 요구**: 종료됐지만 이력이 보존된 사건은 `INCIDENT_CLOSED`까지 재전송한 뒤 연결을 정상 종료한다. 이후 실시간 연결은 유지하지 않는다. 파기된 사건은 재전송 이력·대기 작업의 사건 데이터를 제거하고 다시 전송하지 않는다.
- **현재 한계**: [메모리 저장소](../../backend/src/main/java/com/surimap/eventhub/stream/InMemorySseReplayEventStore.java)가 관측한 종료·파기 이벤트를 기준으로 처리한다. DB의 사건 상태를 직접 확인하거나 전송 작업의 payload까지 파기하는 흐름은 별도 확인이 필요하다. 메모리 상태만으로 재시작 이후의 종료·파기 처리를 보장하지 않는다.

## 전송 실패와 관측

- **기존 요구**: 재시도할 대상을 구분해 이미 성공한 전달을 반복하지 않는다. 실시간 SSE 연결 실패는 재전송 이력으로 복구하고, FCM만 재시도할 때 SSE 이력을 중복 추가하지 않는다. FCM 수신자가 없으면 해당 전송만 생략하고 SSE 전달은 이어간다.
- **현재 차이**: SSE 작업의 실패 상태를 자동으로 다시 처리하거나 대상별 재시도를 관리하는 구현은 없다. FCM도 공통 SSE 전달 작업이 아니라 마커·사건 배정·사건 종료의 호출부에서 별도로 전송한다. [FCM 실패 반환 기록](../issues/local/4-fcm-delivery-failure-not-recorded.md)과 전달 시점·재시도 보장은 구분한다.
- **기존 관측 요구**: 이벤트 저장, 재전송 이력 추가, 실시간 전송, FCM, 상황판 재조회 단계의 실패를 구분한다. 이벤트 형식이 잘못되면 실패로 남기고 원인을 추적한다. 내부 전달 상태를 사용자 알림 상태와 혼용하지 않으며, payload에 정의되지 않은 개인정보를 덧붙이지 않는다.
- **기존 접근 요구**: 사건 접근권을 가진 WEB 연결만 허용하고 권한 만료 시 연결을 종료한다. Android는 상시 SSE 대신 FCM과 REST·미전송 기록 동기화를 사용한다. 연결 도중 권한 만료 처리가 실제로 보장되는지는 별도로 검증한다.

## 후속 작업에서 정할 기준

- 재전송 이력의 저장 방식, 보존 기간·최대 개수와 서버 재시작 후 복구 범위. 원문에도 보존 기간·최대 개수는 미정이었다.
- 상황판 최신 상태 복구와 놓친 알림 복구를 각각 어디까지 보장할지, 재전송 이력이 없을 때 어떻게 복구할지.
- 실패 대상별 재시도, 중복 방지, 사건 종료·파기 시 정리 범위. 기존 DB 설계를 그대로 구현하는 것으로 미리 확정하지 않는다.
- 지연 목표. 원문의 ‘3초 이내’를 확정 기준으로 채택하지 않으며, [Issue #8](../issues/8-gps-collection-transmission-basis.md)의 측정·합의와 구분한다.
- 연결 확인 신호와 초기 전송 중 연결 종료 처리. [로컬 이슈 5](../issues/local/5-sse-initial-send-disconnect-cleanup.md)의 재현 결과를 이어서 검토한다.

## 기존 문서와 원문

- 공개 URL·헤더·응답·오류: [API 참고 문서](../api/api-spec.md). DB 관계·컬럼 의미: [DB 참고 문서](../db-design/db-design-readable.md). 각각 실제 Controller·소비자·Mapper·migration과 대조한다.
- 채널 선택 이유: [ADR-0005 · 웹은 SSE, 앱은 FCM](../adr/0005-sse-web-fcm-android.md).
- 과거 소유권·구현 계획·전체 조건: [S4 원문 (`28f8d4df`)](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/specs/S4.json). 기존 주석·시험 기록의 S4 절 번호는 이 원문을 가리킨다.

## 테스트 입력

S4의 여섯 fixture는 [공용 fixture](../../test-fixtures/common-fixtures.json)의 `confirmed.eventFanout`에 보존돼 있다. 새 복제본을 만들지 않고 기존 소비자가 이 데이터를 사용한다. fixture의 기대값은 현재 제품 동작을 검증했다는 뜻이 아니다.

원문에만 있던 `fanout_failure_injection.expectedParentStatus`의 `PARTIAL_FAILED_THEN_DISPATCHED`는 직접 읽는 테스트가 없으므로 Git 원문에 보존한다. 이를 현재 상태값으로 채택하거나 새 fixture 필드로 추가하지 않는다. 일부 실패 후 복구 요구는 위 전송 실패 항목에서 이어서 확인한다.
