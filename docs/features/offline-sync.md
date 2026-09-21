# 오프라인 저장과 재전송

앱의 미전송 기록, 재시도, 중복 요청, 사건 종료 후 정리를 바꿀 때 확인할 기록이다. 옛 S6의 필요한 요구와 구현 차이를 남겼다. **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-18 정적 코드 대조 결과다. 앱이 서버로 쓰기를 다시 보내는 Outbox와 서버가 상황판에 이벤트를 다시 보내는 [SSE 재전송](./event-delivery.md)은 구분한다.

## 먼저 저장하고, 서버 반영을 확인한 뒤 정리

- 로컬 저장 우선·유실 및 중복 방지·서버 반영 전 원본 보존은 [PRD의 동기화 요구](../prd.md#오프라인-사용동기화)를 따른다. [RoomSyncClient](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt)는 Outbox와 로컬 쓰기 데이터를 트랜잭션으로 저장하고, 별도 전송 처리가 성공 응답을 확인한 뒤 ACK를 기록한다.
- 복구 중 일부만 성공하면 항목별 결과를 남긴다. 프로세스 종료, 서버 저장 후 응답 유실, 응답 수신 후 로컬 ACK 저장 전 종료를 각각 검증해야 한다. 로컬 저장 테스트의 통과만으로 실제 서버·상황판까지 전달됐다고 판단하지 않는다.
- 재전송 시 작성자가 바뀌는 문제와 계정 전환 조건은 [Issue #6](../issues/6-outbox-account-ownership.md)을 따른다. 옛 S6의 업무폰 단위 큐 설명을 작성자 보존 정책으로 채택하지 않는다.

## 재전송 순서와 부분 실패

- **기존 요구**: 마커 생성이 실패하면 그 마커에 의존하는 사진만 대기하고, 관련 없는 경로·패키지 요청은 계속 처리한다. 선행 요청 확인과 요청 순번을 함께 사용한다.
- **현재 차이**: [OutboxDao](../../android/app/src/main/java/com/surimap/core/database/OutboxDao.kt)는 사건·업무폰별 후보를 `sequence`로 정렬한다. `parentOperationId`는 저장하지만 일반적인 선행 요청 ACK 검사에는 사용하지 않는다. [RoomOutboxReplay](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt)의 별도 대기 검사는 근무 교대 종료 요청에 한정된다. 순번 정렬만으로 모든 의존 관계가 보장되지는 않는다.

## 중복 요청과 응답 복구

- **기존 요구**: 같은 요청은 저장된 응답을 돌려주고, 같은 멱등키에 다른 본문이 오면 거부한다. 서버 저장은 끝났지만 응답 캐시가 없으면 원래 결과를 복원해 업무 데이터를 다시 만들지 않는다.
- **현재 차이**: [IdempotentWriteService](../../backend/src/main/java/com/surimap/sync/idempotency/IdempotentWriteService.java)에 응답 재사용·복구 호출이 있다. 다만 [복구 등록부](../../backend/src/main/java/com/surimap/sync/idempotency/IdempotencyReplayRecoveryRegistry.java)에 기능별 복구 구현을 등록하는 운영 호출은 찾지 못했다. 복구용 대역을 쓰는 테스트와 실제 복구 연결을 구분한다.
- **다시 정할 조건**: S6는 응답 복구 실패의 `write_conflict`를 재시도 대상으로 설명하면서, 다른 절에서는 최종 실패로 분류했다. 현재 [HTTP 전송기](../../android/app/src/main/java/com/surimap/core/network/NetworkOutboxSender.kt)는 이 응답을 최종 실패로 처리한다. 재시도 여부를 문서 한쪽에 맞춰 변경하지 않고 오류 원인과 실제 소비자를 확인한다.

## 사건 종료 후 남은 기록

- **기존 요구**: 종료 전에 기록한 요청은 마지막으로 한 번 전송하고, 이후의 새 기록·수동 재시도는 막는다. 서버 반영이 확인된 기록만 정리하고 미전송·최종 실패 기록은 삭제 대기로 남긴다. 보존 중인 기록의 최종 삭제 조건은 미정이었다.
- **현재 차이**: [앱 종료 처리](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt)는 종료 상태를 기록하고 마지막 전송을 호출한다. 그러나 [Android 지침](../../android/AGENTS.md#offline-first)은 종료 후 새 전송을 막도록 설명하고, [마커 API](../api/api-spec.md#46-marker--photo)도 종료 사건의 쓰기를 거부한다. 종료 전 기록의 반영 허용 조건과 개인정보 파기 시점은 서버·앱을 함께 확인해 합의한다.
- **진단 API의 한계**: [OutboxRequeueController](../../backend/src/main/java/com/surimap/sync/outbox/OutboxRequeueController.java)는 실제 사건 상태를 조회하지 않고 고정된 사건 ID 두 개로 종료를 판정한다. 응답을 반환하는 것과 앱의 큐를 다시 예약하거나 서버에 진단 기록을 저장하는 것도 별개다. 배정 권한·종료 판정·호출 연결을 후속 작업에서 확인한다.

## 시간 보정·재시도·상태 표시

- [기록 시각과 서버 수신 시각을 나눈 이유](../adr/0012-recorded-and-received-time.md)를 참고하되, 오래전에 기록한 좌표와 틀어진 단말 시계를 구분한다. 현재 전송 후보 쿼리는 **기록 시각과 보정 시각의 차이**를 사용한다. S6의 전송 시점 기준 설명을 그대로 적용해 오래된 오프라인 기록을 거부하지 않는다.
- 재시도 간격·상한·횟수·시간 제한은 [RoomOutboxReplay](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt), 오류 분류는 HTTP 전송기와 대조한다. 현재 코드나 옛 fixture의 수치를 새 서비스 기준으로 승인한 것은 아니다. 복구 목표 시간은 [Issue #8](../issues/8-gps-collection-transmission-basis.md)에서 별도로 측정하고 정한다.
- **기존 표시·관측 요구**: 미전송 수, 항목별 실패와 재시도 가능 여부를 구분하고 내부 오류 문자열 대신 사용자가 이해할 안내를 제공한다. GPS·배터리·지도·오프라인 경고는 서버 없이 동작해야 한다. 기준값은 [LocalWarningMonitor](../../android/app/src/main/java/com/surimap/core/sync/LocalWarningMonitor.kt)와 대조하고, 실패·재시도 로그와 메트릭의 실제 연결도 별도로 확인한다.
- 사진 업로드 복구와 수색 차수 불일치의 미연결 부분은 [마커·사진의 복구와 삭제](./marker-photo.md), 공개 요청·응답은 [Sync API 참고](../api/api-spec.md#47-sync--offline)를 따른다.

## 테스트 입력과 원문

- [공용 fixture](../../test-fixtures/common-fixtures.json)의 `confirmed.outboxReplay`, `terminalStateRules`, `outboxSharedRules`는 그대로 사용한다. S6 전체 복사본은 아니며, 종료 재시도 사례와 실패 분류 5개는 원문과 같다.
- 원문에는 공용 데이터와 다른 `PACKAGE_STATUS` 및 사진·알림의 옛 기대 상태 `FINALIZED`, `READY_FOR_FANOUT`도 있다. 이를 현재 실행 입력에 덮어쓰지 않는다. 원문에만 있는 기대값은 아래 Git 기록에 보존한다.
- 문서의 schema 이름·문장 순서만 검사하던 테스트 2개는 제거했다. 남은 데이터 검사 2개는 기존 [Outbox fixture 테스트](../../backend/src/test/java/com/surimap/sync/outbox/OutboxRetryDiagnosticsFixtureTest.java)에 합쳤다. 실제 Room 저장·재전송 처리는 [Android 테스트](../../android/app/src/test/java/com/surimap/core/sync/RoomLocalSyncServicesTest.kt)에서 확인하며, 이 테스트의 HTTP 전송은 대역이다.
- 과거 요구·미정 사항·구현 계획과 전체 fixture: [S6 원문 (`28f8d4df`)](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/specs/S6.json). 기존 주석·시험 기록의 S6 표기는 이 원문을 가리킨다.
