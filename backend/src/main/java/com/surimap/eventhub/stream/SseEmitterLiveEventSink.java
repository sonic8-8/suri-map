package com.surimap.eventhub.stream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseEmitterLiveEventSink implements SseLiveEventSink {

  // 로컬 전환 검증용 초기값이며 운영 적정값은 부하·연결 수를 측정한 뒤 조정한다.
  private static final int MAX_PENDING_EVENT_COUNT = 1000;
  private static final int MAX_PENDING_BYTES = 1024 * 1024;
  private static final Logger log = LoggerFactory.getLogger(SseEmitterLiveEventSink.class);

  private final SseStreamEmitter emitter;
  private final NavigableMap<Long, byte[]> pendingEvents = new TreeMap<>();
  private int pendingBytes;
  private boolean replaying;
  private boolean terminalReceived;
  private boolean closeAfterReplay;
  private long lastSentSequence;

  SseEmitterLiveEventSink(SseStreamEmitter emitter) {
    this.emitter = emitter;
  }

  SseEmitterLiveEventSink(SseStreamEmitter emitter, boolean replaying) {
    this(emitter);
    this.replaying = replaying;
  }

  @Override
  public void send(SseEventFrame frame) {
    boolean overflow = false;
    synchronized (pendingEvents) {
      if (emitter.isClosed()) {
        throw new IllegalStateException("SSE connection is closed");
      }
      if (replaying) {
        if ("INCIDENT_CLOSED".equals(frame.event()) || "INCIDENT_PURGED".equals(frame.event())) {
          terminalReceived = true;
          discardPendingEvents();
        } else if (terminalReceived) {
          return;
        }
        long sequence = Long.parseLong(frame.id());
        if (sequence <= lastSentSequence || pendingEvents.containsKey(sequence)) {
          return;
        }
        byte[] serialized = SseEventFrameFormatter.format(frame).getBytes(StandardCharsets.UTF_8);
        if (pendingEvents.size() >= MAX_PENDING_EVENT_COUNT
            || serialized.length > MAX_PENDING_BYTES - pendingBytes) {
          log.warn(
              "SSE replay backlog exceeded. pendingEvents={}, pendingBytes={}, incomingBytes={}",
              pendingEvents.size(),
              pendingBytes,
              serialized.length);
          discardPendingEvents();
          overflow = true;
        } else {
          pendingEvents.put(sequence, serialized);
          pendingBytes += serialized.length;
          return;
        }
      }
    }
    if (overflow) {
      emitter.disconnect();
      return;
    }
    sendNow(frame);
  }

  @Override
  public void close() {
    synchronized (pendingEvents) {
      if (replaying) {
        closeAfterReplay = true;
        return;
      }
    }
    emitter.complete();
  }

  void sendReplay(SseEventFrame frame) {
    synchronized (pendingEvents) {
      if (emitter.isClosed() || terminalReceived) {
        return;
      }
    }
    sendNow(frame);
    removeSentEvents(Long.parseLong(frame.id()));
  }

  void finishReplay() {
    while (!emitter.isClosed()) {
      long sequence;
      byte[] serialized;
      synchronized (pendingEvents) {
        var entry = pendingEvents.firstEntry();
        if (entry == null) {
          replaying = false;
          break;
        }
        sequence = entry.getKey();
        serialized = entry.getValue();
      }
      try {
        emitter.send(
            Set.of(new ResponseBodyEmitter.DataWithMediaType(serialized, MediaType.TEXT_PLAIN)));
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
      removeSentEvents(sequence);
    }
    if (closeAfterReplay) {
      emitter.complete();
    }
  }

  void discardPendingEvents() {
    synchronized (pendingEvents) {
      pendingEvents.clear();
      pendingBytes = 0;
    }
  }

  boolean isReplayStopped() {
    synchronized (pendingEvents) {
      return emitter.isClosed() || terminalReceived;
    }
  }

  private void sendNow(SseEventFrame frame) {
    try {
      emitter.send(SseEmitter.event().id(frame.id()).name(frame.event()).data(frame.data()));
    } catch (IOException e) {
      // 연결 목록에서는 즉시 제외하되 HTTP 오류 완료 처리는 컨테이너에 맡긴다.
      throw new UncheckedIOException(e);
    }
  }

  private void removeSentEvents(long sequence) {
    synchronized (pendingEvents) {
      lastSentSequence = sequence;
      var sent = pendingEvents.headMap(sequence, true);
      for (byte[] serialized : sent.values()) {
        pendingBytes -= serialized.length;
      }
      sent.clear();
    }
  }
}
