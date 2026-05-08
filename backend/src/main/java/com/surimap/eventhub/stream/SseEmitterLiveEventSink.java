package com.surimap.eventhub.stream;

import java.io.IOException;
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
      emitter.completeWithError(e);
    }
  }
}
