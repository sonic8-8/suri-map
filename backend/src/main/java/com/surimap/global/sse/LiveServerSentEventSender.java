package com.surimap.global.sse;

public interface LiveServerSentEventSender {

  void send(ServerSentEventMessage message);

  default void send(ServerSentEventMessage message, Runnable validateBeforeSend) {
    validateBeforeSend.run();
    send(message);
  }

  default void close() {
    // 기록용 테스트 대역은 종료할 응답이 없다. 실제 연결은 HTTP 스트림을 종료한다.
  }
}
