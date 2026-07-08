# SSE 연결 생명주기 검증

## 목적

SSE는 클라이언트와 서버 사이의 HTTP 연결을 오래 유지한다. 연결이 끊겼는데도 서버 sink가 남아 있으면, 서버는 이미 끊긴 연결에도 이벤트를 보내려고 한다. 이런 상태가 반복되면 메모리 누수로 이어질 수 있다.

이번 검증의 목적은 heap dump로 메모리 누수 자체를 증명하는 것이 아니다. 연결이 끊긴 뒤에도 SSE sink가 registry에 남아 있는지 확인해, 누수로 이어질 수 있는 지점을 테스트로 확인하는 것이다.

## 확인한 코드 흐름

- `SseStreamService.openStream(...)`에서 사건별 SSE sink를 `SseStreamSessionRegistry`에 등록한다.
- `SseEmitter`의 completion, timeout, error callback에서 등록 해제 handle을 호출한다.
- `SseStreamSessionRegistry`는 incident id별 sink 목록을 `ConcurrentHashMap`과 `CopyOnWriteArrayList`로 관리한다.
- `INCIDENT_CLOSED` 이벤트가 dispatch되면 `sessionRegistry.release(incidentId)`로 해당 사건의 sink를 닫고 registry에서 제거한다.

## 검증한 내용

### 1. 연결 등록과 해제 반복

테스트:

- `SseStreamLifecycleEvidenceTest.repeatedRegisterAndCloseRemovesIncidentSink`

확인:

- 같은 사건 id로 SSE sink를 등록한다.
- 등록 직후 `sessionRegistry.sinks(incidentId)`가 1개인지 확인한다.
- registration을 close한다.
- close 이후 `sessionRegistry.sinks(incidentId)`가 비어 있는지 확인한다.
- 이 과정을 100회 반복한다.

의미:

연결을 열고 닫는 과정이 반복되어도 registry에 sink가 남지 않아야 한다. 이 검증은 사용자가 사건 화면을 나가거나 다른 사건 화면으로 이동했을 때, 이전 사건 SSE 연결이 계속 남는 상황을 막기 위한 것이다.

### 2. 사건 종료 시 sink release

테스트:

- `SseStreamLifecycleEvidenceTest.releaseClosesAndRemovesIncidentSinks`
- 기존 `SseIncidentClosureReplayStopTest`의 사건 종료 SSE 검증

확인:

- 사건에 sink를 등록한다.
- `sessionRegistry.release(incidentId)`를 호출한다.
- 등록된 sink의 `close()`가 호출되는지 확인한다.
- release 이후 `sessionRegistry.sinks(incidentId)`가 비어 있는지 확인한다.

의미:

사건 종료는 terminal 상태다. 사건이 종료된 뒤에도 SSE 연결이 남아 있으면, 닫힌 사건에 이벤트를 보내려는 시도가 계속될 수 있다. 사건 종료 시점에 해당 사건의 sink를 한 번에 정리하는지 확인했다.

## 면접에서 사용할 표현

메모리 누수 자체를 heap dump로 증명한 것은 아니지만, SSE 연결이 registry에 남아 누수로 이어질 수 있는 지점을 확인했습니다. 연결 등록과 해제를 100회 반복하는 테스트를 추가했고, 반복 후 incident sink count가 0으로 돌아오는 것을 확인했습니다. 사건 종료 이벤트에서도 해당 incident의 sink가 release되는지 함께 확인했습니다.
