package com.surimap.global.sse;

import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 한 연결의 DB 재전송·실시간 대기열을 순서대로 비차단 응답에 전달한다. */
public final class ServerSentEventConnection implements LiveServerSentEventSender {
  // 기존 시험 초기값이다. 전체 JVM·소켓 버퍼 메모리 상한은 아니다.
  private static final int MAX_PENDING_EVENT_COUNT = 1000;
  private static final int MAX_PENDING_BYTES = 1024 * 1024;
  private static final byte[] CONNECTED = ":connected\n\n".getBytes(StandardCharsets.UTF_8);
  private static final Logger log = LoggerFactory.getLogger(ServerSentEventConnection.class);

  private final ServerSentEventStream response;
  private final boolean incidentStream;
  private final NavigableMap<Long, PendingEvent> pendingEvents = new TreeMap<>();
  private int pendingBytes;
  private PendingEvent writingEvent;
  private boolean terminalReceived;
  private boolean closing;
  private long lastSentSequence;
  private long nextAccountEvent;
  private Supplier<ServerSentEventMessage> nextHistoryMessage;
  private Consumer<String> validateHistoryEvent;
  private boolean connectedPending;

  public ServerSentEventConnection(ServerSentEventStream response, boolean incidentStream) {
    this.response = response;
    this.incidentStream = incidentStream;
  }

  @Override
  public void send(ServerSentEventMessage message) {
    send(message, () -> {});
  }

  @Override
  public void send(ServerSentEventMessage message, Runnable validateBeforeSend) {
    boolean overflow = false;
    synchronized (pendingEvents) {
      if (response.isClosed() || closing) {
        throw new IllegalStateException("SSE connection is closed");
      }
      boolean terminal = isTerminal(message);
      if (incidentStream && terminal) {
        terminalReceived = true;
        discardPendingEvents();
        // 소켓이 다시 준비될 때까지 남은 재전송 페이지의 원문을 붙잡지 않는다.
        nextHistoryMessage = null;
        validateHistoryEvent = null;
      } else if (incidentStream && terminalReceived) {
        return;
      }
      long sequence = incidentStream ? Long.parseLong(message.getId()) : ++nextAccountEvent;
      if (incidentStream
          && (sequence <= lastSentSequence
              || pendingEvents.containsKey(sequence)
              || (writingEvent != null && writingEvent.sequence == sequence))) {
        return;
      }
      byte[] serialized = serialize(message);
      int pendingCount =
          pendingEvents.size() + (writingEvent != null && !writingEvent.history ? 1 : 0);
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
        pendingEvents.put(
            sequence, new PendingEvent(sequence, serialized, validateBeforeSend, terminal, false));
        pendingBytes += serialized.length;
      }
    }
    if (overflow) {
      response.complete();
    } else {
      response.requestTransmission();
    }
  }

  @Override
  public void close() {
    synchronized (pendingEvents) {
      closing = true;
    }
    response.requestTransmission();
  }

  public void start(
      boolean sendConnected,
      Supplier<ServerSentEventMessage> history,
      Consumer<String> validateHistory) {
    synchronized (pendingEvents) {
      if (response.isClosed()) {
        return;
      }
      connectedPending = sendConnected;
      if (!terminalReceived) {
        nextHistoryMessage = history;
        validateHistoryEvent = validateHistory;
      }
    }
    response.continueTransmission(this::sendAvailableEvents);
  }

  public void clear() {
    synchronized (pendingEvents) {
      pendingEvents.clear();
      pendingBytes = 0;
      writingEvent = null;
      nextHistoryMessage = null;
      validateHistoryEvent = null;
    }
  }

  public boolean isHistoryStopped() {
    synchronized (pendingEvents) {
      return response.isClosed() || terminalReceived;
    }
  }

  private boolean isTerminal(ServerSentEventMessage message) {
    return "INCIDENT_CLOSED".equals(message.getEvent())
        || "INCIDENT_PURGED".equals(message.getEvent());
  }

  private void discardPendingEvents() {
    pendingEvents.clear();
    // 쓰기 중인 실시간 이벤트는 완료될 때까지 기존 대기 한도에 포함한다.
    pendingBytes =
        writingEvent != null && !writingEvent.history ? writingEvent.serialized.length : 0;
  }

  private byte[] serialize(ServerSentEventMessage message) {
    return ServerSentEventFormatter.format(message).getBytes(StandardCharsets.UTF_8);
  }

  private void sendAvailableEvents() {
    while (!response.isClosed()) {
      if (connectedPending) {
        if (!response.writeFrame(CONNECTED)) {
          return;
        }
        connectedPending = false;
      }
      if (!response.isReady()) {
        return;
      }
      var pending = selectNextEvent();
      if (pending == null) {
        boolean closeWhenDrained;
        synchronized (pendingEvents) {
          closeWhenDrained = closing;
        }
        if (closeWhenDrained) {
          response.complete();
        }
        return;
      }
      if (!response.hasFrameStarted()) {
        if (shouldSkip(pending)) {
          finishCurrentEvent(pending, false);
          continue;
        }
        try {
          pending.validateBeforeSend.run();
        } catch (ServerSentEventRefetchRequiredException exception) {
          if (!isHistoryStopped()) {
            throw exception;
          }
          finishCurrentEvent(pending, false);
          continue;
        }
        if (shouldSkip(pending)) {
          finishCurrentEvent(pending, false);
          continue;
        }
      }
      if (!response.writeFrame(pending.serialized)) {
        return;
      }
      finishCurrentEvent(pending, true);
    }
  }

  private PendingEvent selectNextEvent() {
    Supplier<ServerSentEventMessage> history;
    Consumer<String> validate;
    synchronized (pendingEvents) {
      if (writingEvent != null) {
        return writingEvent;
      }
      history = nextHistoryMessage;
      validate = validateHistoryEvent;
    }
    if (history != null) {
      ServerSentEventMessage message = null;
      try {
        // DB 조회는 대기열 잠금 밖에서, 이전 전송이 끝난 뒤에만 수행한다.
        message = history.get();
      } catch (ServerSentEventRefetchRequiredException exception) {
        if (!isHistoryStopped()) {
          throw exception;
        }
      }
      synchronized (pendingEvents) {
        if (response.isClosed()) {
          return null;
        }
        if (message != null && !terminalReceived) {
          String eventType = message.getEvent();
          writingEvent =
              new PendingEvent(
                  Long.parseLong(message.getId()),
                  serialize(message),
                  () -> validate.accept(eventType),
                  isTerminal(message),
                  true);
          return writingEvent;
        }
        nextHistoryMessage = null;
        validateHistoryEvent = null;
      }
    }
    synchronized (pendingEvents) {
      var entry = pendingEvents.pollFirstEntry();
      writingEvent = entry == null ? null : entry.getValue();
      return writingEvent;
    }
  }

  private boolean shouldSkip(PendingEvent pending) {
    synchronized (pendingEvents) {
      return response.isClosed() || (incidentStream && terminalReceived && !pending.terminal);
    }
  }

  private void finishCurrentEvent(PendingEvent pending, boolean sent) {
    synchronized (pendingEvents) {
      if (writingEvent != pending) {
        return; // 연결 종료에서 이미 정리했다.
      }
      if (!pending.history) {
        pendingBytes -= pending.serialized.length;
      }
      writingEvent = null;
      if (sent && incidentStream) {
        lastSentSequence = pending.sequence;
        var sentEvents = pendingEvents.headMap(lastSentSequence, true);
        for (PendingEvent duplicate : sentEvents.values()) {
          pendingBytes -= duplicate.serialized.length;
        }
        sentEvents.clear();
      }
    }
  }

  @RequiredArgsConstructor
  private static final class PendingEvent {
    private final long sequence;
    private final byte[] serialized;
    private final Runnable validateBeforeSend;
    private final boolean terminal;
    private final boolean history;
  }
}
