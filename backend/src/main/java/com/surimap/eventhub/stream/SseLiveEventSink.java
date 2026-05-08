package com.surimap.eventhub.stream;

public interface SseLiveEventSink {

  void send(SseEventFrame frame);
}
