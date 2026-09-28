package com.surimap.eventhub.stream;

public interface SseLiveEventSink {

  void send(SseEventFrame frame);

  default void send(SseEventFrame frame, Runnable validateBeforeSend) {
    validateBeforeSend.run();
    send(frame);
  }

  default void close() {
    // Test sinks do not need a close hook; SseEmitter-backed sinks complete the HTTP stream.
  }
}
