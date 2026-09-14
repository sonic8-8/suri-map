# 제목

[BE] SSE 연결 하나의 실패로 다른 연결의 마커 알림 전송이 중단되는 문제

# 본문

## 문제 배경

수리맵은 업무폰에서 마커를 기록하면 웹 상황판에 변경을 알립니다. 서버는 마커 저장과 별도로 이벤트 전송 작업을 처리하고, 연결된 브라우저에 SSE로 이벤트를 보냅니다. SSE는 HTTP 연결을 유지하면서 서버가 브라우저로 변경 사항을 보내는 방식입니다. 상황판에는 데이터 재조회용 연결과 마커 알림 표시용 연결이 따로 있습니다.

한 연결이 끊어져도 다른 정상 연결에는 이벤트를 계속 보내야 합니다. 그러나 2026-09-14 로컬 Smoke Test를 반복하자 마커는 저장됐지만 새 발견 알림이 나타나지 않았습니다. `PERSON_FOUND`는 사람을 발견했다는 마커 알림 이벤트입니다.

| 확인 항목 | 첫 실행 | 반복 실행 |
|---|---|---|
| 마커 생성 API | HTTP 201, DB 저장 확인 | HTTP 201, DB 저장 확인 |
| `MARKER_CREATED` 전송 작업 | `COMPLETED` | `COMPLETED` |
| `PERSON_FOUND` 전송 작업 | `FAILED` | `FAILED` |
| 브라우저의 새 발견 알림 | 표시됨 | 20초 동안 표시되지 않음 |

20초는 이 테스트의 화면 대기 시간이며, 서비스의 허용 지연 기준은 아닙니다. 반복 실행에서는 이전 알림을 닫고 새 마커 ID로 표시 여부를 확인했습니다. 마커 저장 성공만으로 알림 전달까지 성공했다고 판단하면, 상황판 사용자가 발견 사실을 놓칠 수 있습니다.

### 문제 해결: 체크리스트

- [x] 종료된 연결이 정상 연결보다 먼저 등록된 조건에서, 뒤쪽 연결로의 전송이 중단되는 문제를 재현하고 원인을 확인했는가?
- [x] 사건·계정 구독 모두에서 종료되거나 I/O 오류가 난 연결을 제외하고, 정상 연결에는 계속 전송하는가?
- [x] 메시지 변환 오류는 호출부에 전달하고, 재전송 저장에 실패한 작업은 `FAILED`로 남기는가?
- [ ] 수정 후 실제 브라우저에서 마커 생성·화면 이탈·재연결을 반복해 새 알림 수신과 상황판 갱신을 확인했는가?

## 원인 분석과 선택지

발견 당시 서버 로그에는 이미 오류로 종료된 SSE 연결에 다시 전송하려 했다는 예외가 남았습니다. 초기 가설은 이 예외가 연결 목록의 순회를 중단시켜 다른 연결까지 알림을 받지 못하게 한다는 것이었습니다. 종료된 연결과 정상 연결을 함께 등록하는 테스트로 이 경로를 확인하기로 했습니다.

검토 대상은 실패한 연결만 제외하고 나머지 전송을 계속하는 방식입니다. 다만 메시지 변환이나 재전송 저장처럼 서버 내부의 처리 실패까지 무시하면 작업을 성공으로 잘못 기록할 수 있으므로, 연결 오류와 내부 처리 오류를 구분해야 합니다.

같은 실행에서 `AuthorizationDeniedException: Access Denied`도 관측됐습니다. 이것이 연결 오류의 최초 원인인지는 확인하지 못했습니다. 이 이슈는 연결 실패가 다른 연결의 전송까지 중단시키는 문제를 다루며, 인증 오류의 원인 분석은 별도로 남깁니다. 이후 확인한 원인과 수정·검증 결과는 아래 댓글에 기록합니다.

# 댓글

## 변경 내용과 트레이드오프

- 변경: 전송 중 종료 상태나 I/O 오류가 확인된 연결을 목록에서 제외하고, 나머지 연결에는 계속 전송하도록 수정했습니다.
- 유지: 메시지 변환 오류와 재전송 저장 실패는 성공으로 처리하지 않습니다. 실패 작업의 재시도 정책과 메모리 기반 재전송 저장 방식도 바꾸지 않았습니다.
- 남은 확인: 자동 테스트는 통과했지만, 수정 후 실제 브라우저 반복 검증과 `Access Denied` 원인 분석은 아직 하지 않았습니다.

### 전송 중단 원인: 연결 하나의 예외가 전체 순회를 종료

수정 전 `SseStreamSessionRegistry`는 등록된 연결을 순서대로 돌며 `send()`를 호출했습니다. 이때 연결별 예외 처리가 없어, 이미 종료된 연결에서 예외가 발생하면 뒤쪽 연결의 `send()`는 실행되지 않았습니다. 예외를 받은 `EventDispatchJobDispatcher`는 해당 이벤트 전송 작업을 `FAILED`로 변경했습니다.

실제 Spring `SseEmitter`를 오류로 종료한 뒤 정상 연결보다 먼저 등록하자, 사건·계정 구독 테스트 2개가 모두 실패했습니다. 같은 테스트를 다시 실행해도 실패해, 로그에서 의심한 전송 중단 경로를 재현했습니다.

이 구조에서는 앞쪽 연결에 전송한 뒤 다른 연결에서 실패할 수도 있습니다. 따라서 첫 실행처럼 알림이 표시됐어도 작업은 `FAILED`로 남을 수 있으며, 작업 상태와 브라우저 수신 결과를 따로 확인해야 합니다. 당시 예외와 이벤트 ID는 [서버 로그 발췌](../../../_workspace/marker-sse-smoke-20260914.yJUQJb/backend-error-excerpt.log)에 남겼습니다.

### 연결별 오류 처리: 실패한 연결을 제외하고 나머지 전송을 계속

[SseStreamSessionRegistry.java](../../../backend/src/main/java/com/surimap/eventhub/stream/SseStreamSessionRegistry.java)의 사건·계정 전송이 같은 오류 처리 메서드를 사용하도록 변경했습니다. 종료 상태나 I/O 오류가 확인되면 해당 연결을 즉시 제외합니다. 연결을 닫는 도중 다시 오류가 나더라도 다른 연결의 전송·종료는 계속합니다. 전송 실패 로그에는 이벤트 ID·순번·예외 타입을 남깁니다.

[SseEmitterLiveEventSink.java](../../../backend/src/main/java/com/surimap/eventhub/stream/SseEmitterLiveEventSink.java)는 `IOException`을 `UncheckedIOException`으로 전달해 연결 목록에서 실패를 처리하도록 변경했습니다. I/O 오류 이후 HTTP 연결을 완료하는 처리는 Servlet 컨테이너에 맡기고, 직접 호출하던 `completeWithError()`는 제거했습니다. 이는 [Spring MVC의 스트리밍 오류 처리 지침](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-objects)에 따른 변경입니다.

또한 `IllegalStateException`을 모두 연결 종료로 처리하지는 않습니다. 현재 사용하는 [Spring 6.2.18 구현](https://github.com/spring-projects/spring-framework/blob/v6.2.18/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyEmitter.java#L191-L205)은 메시지 변환 등의 내부 오류도 이 예외로 감싸므로, 원인 예외가 들어 있으면 호출부에 그대로 전달합니다. 이를 무시하던 초기 수정은 추가 테스트에서 실패했고, 오류 구분을 보완한 뒤 통과했습니다.

이 변경은 정상 연결의 전송을 보호하는 것이지, 끊어진 브라우저까지 수신을 보장하는 것은 아닙니다. 해당 브라우저의 복구는 기존 재연결·재전송 처리에 의존합니다. 현재 재전송 저장소는 메모리 기반이고 `FAILED` 작업을 자동 재시도하지 않으므로, 서버 재시작이나 실패 작업의 복구까지 해결했다고 보지는 않습니다.

## 검증 결과

2026-09-14 로컬 코드에서 다음을 확인했습니다. 브라우저 관측은 수정 전 결과이며, 수정 후 결과는 자동 테스트 범위입니다.

| 검증 대상 | 확인한 동작 | 결과 |
|---|---|---|
| [연결 관리 테스트](../../../backend/src/test/java/com/surimap/eventhub/stream/SseStreamSessionRegistryTest.java) | 사건·계정의 실패 연결 제외, 정상 연결 전송, 종료 오류 격리, 내부 처리 오류 전달 | 8개 통과 |
| [전송 작업 DB 연동 테스트](../../../backend/src/test/java/com/surimap/eventhub/EventDispatchJobSseFanoutIntegrationTest.java) | 정상 수신 대상의 `PERSON_FOUND` 수신·재전송 저장·작업 완료, 재전송 저장 거부 시 작업 실패 | 3개 통과 |
| 전체 Backend 테스트 | `./gradlew test` | 1,227개 통과, 222개 클래스, 실패·오류·건너뜀 0건 |

전체 테스트는 3분 29초가 걸렸습니다. 변경 Java 파일 4개의 포맷과 공백 검사도 통과했습니다. 테스트 종료 중 닫힌 DB 커넥션 경고 1건이 있었으며 테스트 실패는 없었습니다. 통합 테스트에서 확인한 `COMPLETED`는 서버의 전송 처리 완료이지, 실제 브라우저의 알림 표시 확인은 아닙니다.

관련 테스트는 Docker가 실행 중인 환경에서 `backend`로 이동한 뒤 다음 명령으로 다시 실행할 수 있습니다. Docker가 없으면 DB 연동 테스트는 건너뛰므로, 명령 성공 여부뿐 아니라 실행·건너뜀 개수도 확인해야 합니다.

```bash
./gradlew test \
  --tests 'com.surimap.eventhub.stream.SseStreamSessionRegistryTest' \
  --tests 'com.surimap.eventhub.EventDispatchJobSseFanoutIntegrationTest'
```

최초 Smoke Test는 격리된 로컬 PostGIS DB, 현재 Backend·Frontend 개발 서버와 실제 브라우저를 연결해 수행했습니다. 업무폰 요청은 `APP` 헤더를 넣은 직접 HTTP 요청으로 대체했으며 실제 Android 앱이나 합성 SSE 프레임은 사용하지 않았습니다. [당시 실행 기록](../../../_workspace/marker-sse-smoke-20260914.yJUQJb/RESULTS.md)에 준비 방법과 관측 결과가 있습니다. `_workspace` 자료는 로컬 전용이며 Git에는 포함되지 않습니다.

작성 시점의 기준 커밋은 `aedd4510`이며, 이 댓글의 Backend 수정은 아직 커밋하지 않은 상태입니다. 독립 코드 리뷰·실제 브라우저 재시험·푸시·배포·부하테스트는 수행하지 않았습니다. 체크리스트의 브라우저 검증을 마치기 전까지 전체 문제 해결은 미완료로 둡니다.
