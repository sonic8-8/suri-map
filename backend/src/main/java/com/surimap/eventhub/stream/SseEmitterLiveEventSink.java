package com.surimap.eventhub.stream;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseEmitterLiveEventSink implements SseLiveEventSink {

  private final SseEmitter emitter;

  SseEmitterLiveEventSink(SseEmitter emitter) {
    this.emitter = emitter;
  }

  @Override
  public void send(SseEventFrame frame) {
    try {
      emitter.send(SseEmitter.event().id(frame.id()).name(frame.event()).data(frame.data()));
    } catch (IOException e) {
      // 연결 목록에서는 즉시 제외하되 HTTP 오류 완료 처리는 컨테이너에 맡긴다.
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    emitter.complete();
  }
}
