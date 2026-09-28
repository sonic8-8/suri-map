package com.surimap.eventhub.stream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

final class SseEmitterLiveEventSink implements SseLiveEventSink {

  // 기존 재전송 대기 한도를 실시간에도 적용한다. 운영 적정값은 부하·연결 수를 측정한 뒤 조정한다.
  private static final int MAX_PENDING_EVENT_COUNT = 1000;
  private static final int MAX_PENDING_BYTES = 1024 * 1024;
  private static final Logger log = LoggerFactory.getLogger(SseEmitterLiveEventSink.class);

  private final SseStreamEmitter emitter;
  private final boolean incidentStream;
  private final NavigableMap<Long, PendingEvent> pendingEvents = new TreeMap<>();
  private int pendingBytes;
  private PendingEvent writingEvent;
  private long writingSequence;
  private boolean replaying;
  private boolean draining;
  private boolean terminalReceived;
  private boolean closing;
  private long lastSentSequence;
  private long nextAccountEvent;

  SseEmitterLiveEventSink(SseStreamEmitter emitter) {
    this(emitter, false);
  }

  SseEmitterLiveEventSink(SseStreamEmitter emitter, boolean incidentStream) {
    this.emitter = emitter;
    this.incidentStream = incidentStream;
    this.replaying = incidentStream;
  }

  @Override
  public void send(SseEventFrame frame) {
    send(frame, () -> {});
  }

  @Override
  public void send(SseEventFrame frame, Runnable validateBeforeSend) {
    boolean overflow = false;
    boolean startDrain = false;
    synchronized (pendingEvents) {
      if (emitter.isClosed() || closing) {
        throw new IllegalStateException("SSE connection is closed");
      }
      boolean terminal =
          "INCIDENT_CLOSED".equals(frame.event()) || "INCIDENT_PURGED".equals(frame.event());
      if (incidentStream && terminal) {
        terminalReceived = true;
        discardPendingEvents();
      } else if (incidentStream && terminalReceived) {
        return;
      }
      // 계정 구독은 여러 사건의 같은 순번을 받을 수 있으므로 수신 순서로 대기시킨다.
      long sequence = incidentStream ? Long.parseLong(frame.id()) : ++nextAccountEvent;
      if (incidentStream
          && (sequence <= lastSentSequence
              || pendingEvents.containsKey(sequence)
              || (writingEvent != null && writingSequence == sequence))) {
        return;
      }
      byte[] serialized = SseEventFrameFormatter.format(frame).getBytes(StandardCharsets.UTF_8);
      int pendingCount = pendingEvents.size() + (writingEvent == null ? 0 : 1);
      if (pendingCount >= MAX_PENDING_EVENT_COUNT
          || serialized.length > MAX_PENDING_BYTES - pendingBytes) {
        log.warn(
            "SSE backlog exceeded. pendingEvents={}, pendingBytes={}, incomingBytes={}",
            pendingCount,
            pendingBytes,
            serialized.length);
        discardPendingEvents();
        overflow = true;
      } else {
        pendingEvents.put(sequence, new PendingEvent(serialized, validateBeforeSend, terminal));
        pendingBytes += serialized.length;
        if (!replaying && !draining) {
          draining = true;
          startDrain = true;
        }
      }
    }
    if (overflow) {
      emitter.disconnect();
    } else if (startDrain) {
      emitter.submitTransmission(this::drainPendingEvents);
    }
  }

  @Override
  public void close() {
    synchronized (pendingEvents) {
      closing = true;
      if (replaying || draining) {
        return;
      }
    }
    emitter.disconnect();
  }

  void sendReplay(SseEventFrame frame, Runnable validateBeforeSend) {
    if (isReplayStopped()) {
      return;
    }
    validateBeforeSend.run();
    if (isReplayStopped()) {
      return;
    }
    writeSerializedFrame(SseEventFrameFormatter.format(frame).getBytes(StandardCharsets.UTF_8));
    removeSentEvents(Long.parseLong(frame.id()));
  }

  void finishReplay() {
    synchronized (pendingEvents) {
      replaying = false;
      draining = true;
    }
    drainPendingEvents();
  }

  void discardPendingEvents() {
    synchronized (pendingEvents) {
      pendingEvents.clear();
      // 이미 쓰기 중인 데이터는 쓰기가 끝날 때까지 한도에 포함한다.
      pendingBytes = writingEvent == null ? 0 : writingEvent.serialized.length;
    }
  }

  boolean isReplayStopped() {
    synchronized (pendingEvents) {
      return emitter.isClosed() || terminalReceived;
    }
  }

  private void drainPendingEvents() {
    while (!emitter.isClosed()) {
      long sequence;
      PendingEvent pending;
      boolean closeWhenDrained;
      synchronized (pendingEvents) {
        var entry = pendingEvents.pollFirstEntry();
        if (entry == null) {
          draining = false;
          closeWhenDrained = closing;
          pending = null;
          sequence = 0;
        } else {
          sequence = entry.getKey();
          pending = entry.getValue();
          writingEvent = pending;
          writingSequence = sequence;
          closeWhenDrained = false;
        }
      }
      if (pending == null) {
        if (closeWhenDrained) {
          emitter.complete();
        }
        return;
      }
      try {
        if (shouldSkipPendingEvent(pending)) {
          continue;
        }
        try {
          pending.validateBeforeSend.run();
        } catch (GoneRefetchRequiredException exception) {
          // 상태 조회 중 도착한 종료 알림은 다음 차례에 보내고 닫는다.
          if (isReplayStopped()) {
            continue;
          }
          throw exception;
        }
        if (shouldSkipPendingEvent(pending)) {
          continue;
        }
        writeSerializedFrame(pending.serialized);
        if (incidentStream) {
          removeSentEvents(sequence);
        }
      } finally {
        synchronized (pendingEvents) {
          pendingBytes -= pending.serialized.length;
          writingEvent = null;
        }
      }
    }
  }

  private boolean shouldSkipPendingEvent(PendingEvent pending) {
    synchronized (pendingEvents) {
      return emitter.isClosed() || (incidentStream && terminalReceived && !pending.terminal);
    }
  }

  private void writeSerializedFrame(byte[] serialized) {
    try {
      emitter.send(
          Set.of(new ResponseBodyEmitter.DataWithMediaType(serialized, MediaType.TEXT_PLAIN)));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private void removeSentEvents(long sequence) {
    synchronized (pendingEvents) {
      lastSentSequence = sequence;
      var sent = pendingEvents.headMap(sequence, true);
      for (PendingEvent pending : sent.values()) {
        pendingBytes -= pending.serialized.length;
      }
      sent.clear();
    }
  }

  @RequiredArgsConstructor
  private static final class PendingEvent {
    private final byte[] serialized;
    private final Runnable validateBeforeSend;
    private final boolean terminal;
  }
}
