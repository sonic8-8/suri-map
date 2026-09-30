package com.surimap.global.sse;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSentEventConnectionRegistry {

  private static final Logger log =
      LoggerFactory.getLogger(ServerSentEventConnectionRegistry.class);

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<LiveServerSentEventSender>>
      sendersByIncident = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CopyOnWriteArrayList<LiveServerSentEventSender>>
      sendersByAccount = new ConcurrentHashMap<>();

  public AutoCloseable registerForIncident(UUID incidentId, LiveServerSentEventSender sender) {
    return register(sendersByIncident, incidentId, sender);
  }

  public void sendToIncident(UUID incidentId, ServerSentEventMessage message) {
    sendToRegisteredSenders(sendersByIncident, incidentId, message, sender -> sender.send(message));
  }

  public void sendToIncident(
      UUID incidentId, ServerSentEventMessage message, Runnable validateBeforeSend) {
    sendToRegisteredSenders(
        sendersByIncident, incidentId, message, sender -> sender.send(message, validateBeforeSend));
  }

  public AutoCloseable registerForAccount(UUID accountId, LiveServerSentEventSender sender) {
    return register(sendersByAccount, accountId, sender);
  }

  public void sendToAccount(UUID accountId, ServerSentEventMessage message) {
    sendToRegisteredSenders(sendersByAccount, accountId, message, sender -> sender.send(message));
  }

  public void sendToAccount(
      UUID accountId, ServerSentEventMessage message, Runnable validateBeforeSend) {
    sendToRegisteredSenders(
        sendersByAccount, accountId, message, sender -> sender.send(message, validateBeforeSend));
  }

  public void closeIncidentConnections(UUID incidentId) {
    var senders = sendersByIncident.remove(incidentId);
    if (senders == null) {
      return;
    }
    senders.forEach(this::closeSender);
  }

  public List<LiveServerSentEventSender> getSenders(UUID incidentId) {
    return List.copyOf(sendersByIncident.getOrDefault(incidentId, new CopyOnWriteArrayList<>()));
  }

  private AutoCloseable register(
      ConcurrentMap<UUID, CopyOnWriteArrayList<LiveServerSentEventSender>> registeredSenders,
      UUID subscriptionTargetId,
      LiveServerSentEventSender sender) {
    registeredSenders.compute(
        subscriptionTargetId,
        (ignored, senders) -> {
          if (senders == null) {
            senders = new CopyOnWriteArrayList<>();
          }
          senders.add(sender);
          return senders;
        });
    return () -> unregister(registeredSenders, subscriptionTargetId, sender);
  }

  private void sendToRegisteredSenders(
      ConcurrentMap<UUID, CopyOnWriteArrayList<LiveServerSentEventSender>> registeredSenders,
      UUID subscriptionTargetId,
      ServerSentEventMessage message,
      Consumer<LiveServerSentEventSender> send) {
    var senders = registeredSenders.get(subscriptionTargetId);
    if (senders == null) {
      return;
    }
    for (var sender : senders) {
      try {
        send.accept(sender);
      } catch (IllegalStateException | UncheckedIOException exception) {
        // Spring emitter의 내부 처리 실패는 원인 예외를 감싼다. 종료된 연결과 구분한다.
        if (exception instanceof IllegalStateException && exception.getCause() != null) {
          throw exception;
        }
        unregister(registeredSenders, subscriptionTargetId, sender);
        log.warn(
            "SSE connection removed after send failure. eventId={}, sequence={}, failureType={}",
            message.getData().getEventId(),
            message.getId(),
            exception.getClass().getSimpleName());
        // I/O 오류의 HTTP 연결 정리는 Servlet 컨테이너가 담당한다.
        if (!(exception instanceof UncheckedIOException)) {
          closeSender(sender);
        }
      }
    }
  }

  private void closeSender(LiveServerSentEventSender sender) {
    try {
      sender.close();
    } catch (RuntimeException exception) {
      log.warn("SSE connection close failed. failureType={}", exception.getClass().getSimpleName());
    }
  }

  private void unregister(
      ConcurrentMap<UUID, CopyOnWriteArrayList<LiveServerSentEventSender>> registeredSenders,
      UUID subscriptionTargetId,
      LiveServerSentEventSender sender) {
    registeredSenders.computeIfPresent(
        subscriptionTargetId,
        (ignored, senders) -> {
          senders.remove(sender);
          return senders.isEmpty() ? null : senders;
        });
  }
}
