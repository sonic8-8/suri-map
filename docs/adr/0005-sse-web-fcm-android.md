# 웹은 SSE, 앱은 FCM으로 알림 수신

웹 상황판에는 서버의 단방향 갱신 알림이 필요하고, 현장 앱에는 불안정한 통신·배터리·백그라운드 수신 제약이 있다. 모든 채널에 상시 WebSocket을 연결하는 대신 웹 수신은 SSE, 앱 수신은 FCM, 앱 기록 전송은 REST와 재시도로 나눴다. 이후 ADR-0035에서는 WebFlux를 추가할 근거가 부족하다고 보고 기존 Spring MVC의 `SseEmitter`를 선택했다.

현재 확인: 저장·전달·재전송의 차이는 [이벤트 전달 기록](../features/event-delivery.md)에 남겼다. SSE 재전송 이력은 메모리 저장이라 재시작 복구를 보장하지 않으며, FCM도 실제 연결 설정·단말 수신 검증과 구분한다. 원문의 푸시 도달 보장 주장은 검증 결과로 사용하지 않는다. 내부망 웹이라는 초기 가정은 ADR-0023에서 공개 HTTPS 접근으로 바뀌었다.

기록: 2026-04-21 · [ADR-0005 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0005-실시간-채널-분리-sse-for-web-fcm-for-android). 후속 도구 선택은 [ADR 인덱스](README.md#요약으로-남긴-이력)의 0035를 참고한다.
