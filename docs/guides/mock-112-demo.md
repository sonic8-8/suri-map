# mock 112 역할과 시연 입력

mock 112는 합성 사건·배정을 제공하는 별도 Spring Boot 서비스다. 수리맵 사용자에게 사건 생성 기능을 추가하는 것이 아니다. 이 문서는 긴 구현 계획 대신 현재 코드의 확인 위치와 시연 시 주의할 점을 남긴다.

## 무엇을 담당하는가

| 역할 | 코드·자료 |
|---|---|
| 원천 사건·배정 입력 | [사건 Controller](../../mock-112/src/main/java/com/mock112/controller/MockIncidentController.java)·[등록 Service](../../mock-112/src/main/java/com/mock112/service/MockIncidentRegistrationService.java). 등록·배정 추가·종료 시 웹훅 전송을 요청한다. |
| 독립 관리 화면·인증 | [화면 진입점](../../mock-112/src/main/java/com/mock112/controller/Mock112UiController.java)·[인증 설정](../../mock-112/src/main/java/com/mock112/config/SecurityConfig.java)·[내부 요청 인증](../../mock-112/src/main/java/com/mock112/config/InternalApiTokenAuthenticationFilter.java). 브라우저 로그인과 허용 경로의 내부 호출을 구분한다. |
| 시연 입력 준비 | [시나리오 Controller](../../mock-112/src/main/java/com/mock112/controller/MockScenarioController.java)·[원천 seed](../../mock-112/src/main/resources/seed/precinct-first-scenario.json). 초동 배정 → 실종팀 인계 → 지원 부대 합류의 합성 입력을 제공한다. |
| 웹훅 전송·재시도 | [전송기](../../mock-112/src/main/java/com/mock112/webhook/SuriMapWebhookDispatcher.java)·[재시도 실행기](../../mock-112/src/main/java/com/mock112/webhook/WebhookOutboxRetryJob.java)·[상태 조회](../../mock-112/src/main/java/com/mock112/controller/WebhookOutboxController.java). 전송 성공·대기·최종 실패를 확인한다. |
| 수리맵의 수신·저장 | [사건 가져오기·배정·종료](../features/incident-lifecycle.md). 원천 조회·캐시·웹훅 가져오기·수동 재처리와 알림 연결의 차이를 확인한다. mock 112가 수리맵 DB를 직접 수정하는 방식으로 우회하지 않는다. |

## 시연 전에 확인할 것

1. 실제 개인정보·얼굴 사진·연락처 대신 합성 입력만 사용한다. 사진 키도 시험용 자료를 가리켜야 하며 로그에 실제 개인정보나 인증값을 남기지 않는다.
2. [공용 테스트 입력](../../test-fixtures/README.md)의 검사로 seed와 공용 JSON이 일치하는지 확인한다. 원천 `sourceIncidentId`, 수리맵 내부 UUID, 계정 별칭·코드는 서로 다른 값이다. 문서에 JSON 사본을 새로 만들지 않는다.
3. [실행 설정](../../mock-112/src/main/resources/application.yml)의 대상 서버·웹훅 활성화·인증을 확인한다. 기본 웹훅은 비활성화돼 있으며, 비활성화 또는 URL 누락이면 전송을 건너뛰고 웹훅 Outbox에도 넣지 않는다.
4. 시험 환경에서 사건 준비·배정 추가·종료 중 필요한 흐름만 수행한다. 원천의 처리 결과, 웹훅 상태, 수리맵 저장 결과, 클라이언트 재조회를 따로 확인한다. 웹훅 HTTP 성공만으로 화면 반영까지 성공했다고 판단하지 않는다.

`/mock-112/reset`은 mock 112 저장소와 웹훅 Outbox를 초기화한다. 수리맵 DB까지 되돌리지는 않는다. 기존 시험 입력·증거를 지우므로 대상과 초기화 범위에 합의한 시험에서만 사용한다.

## 과거 계획과의 차이

- 초기 문서는 수동 가져오기를 중심으로 설명했지만 이후 웹훅 자동 가져오기를 추가했다. 두 흐름과 원천 폴링을 같은 동작으로 설명하지 않는다.
- 원천 assignment key 중심 중복 방지 제안과 실제 활성 배정의 사건·계정 기준은 다르다. 배정 수정·철회까지 지원한다고 가정하지 않고 [현재 코드 대조](../features/incident-lifecycle.md#배정-반영과-사건-접근)를 확인한다.
- 별도 React 관리 앱, 문서의 엔드포인트 후보·DTO·컬럼 추가안은 필수 구현 목록이 아니다. URL·입력·인증은 위 Controller와 실제 소비자를 대조한 뒤 바꾼다.

## 원문

[시연·구현 계획과 예시 JSON](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/mock-112-demo-guidance.md)에서 당시 제안·선택 이유를 복원한다. 공용 fixture의 `meta.sourceDocs`와 출처 설명도 이 문서의 새 위치로 갱신했으며 입력값·ID는 유지했다. 정리 전 미커밋 보완이었던 입력 검사 위치와 수동·자동 가져오기의 구분도 위에 반영했다.
