package com.surimap.eventhub.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.surimap.eventhub.dto.PublishRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionRegistryTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110003");
  private final SseConnectionRegistry registry = new SseConnectionRegistry();
  private final SseEventFrame frame =
      new SseEventFrame(
          "901",
          "PERSON_FOUND",
          new PublishRequest(
              UUID.fromString("40000000-0000-4000-8000-000000000901"),
              INCIDENT_ID,
              "PERSON_FOUND",
              1,
              "marker",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              Instant.parse("2026-05-08T00:00:00Z"),
              Map.of()));

  @Test
  @DisplayName("사건 연결을 100회 등록하고 해제해도, 연결 목록에 남지 않는다")
  void repeatedly_registering_and_closing_connection_leaves_no_registration() throws Exception {
    // given: 전송하지 않고 등록·해제만 반복할 연결이다.
    var sink = mock(SseLiveEventSink.class);
    for (int count = 0; count < 100; count++) {
      var registration = registry.registerForIncident(INCIDENT_ID, sink);
      assertThat(registry.sinks(INCIDENT_ID)).containsExactly(sink);

      // when: 반환받은 등록을 해제한다.
      registration.close();

      // then: 매번 사건의 연결 목록이 비워진다.
      assertThat(registry.sinks(INCIDENT_ID)).isEmpty();
    }
  }

  @ParameterizedTest(name = "구독 대상: {0}")
  @ValueSource(strings = {"incident", "account"})
  @DisplayName("마지막 기존 연결 해제와 새 연결 등록이 겹쳐도 새 연결에는 이벤트를 전달한다")
  void concurrent_registration_and_unregistration_keeps_new_connection(String subscriptionType)
      throws Exception {
    // given: 같은 구독 대상에서 연결 교체가 겹치도록 실제 스레드 두 개를 사용한다.
    var executor = Executors.newFixedThreadPool(2);
    try {
      for (int attempt = 0; attempt < 1_000; attempt++) {
        var previous = register(subscriptionType, ignored -> {});
        var received = new AtomicInteger();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var disconnect =
            executor.submit(
                () -> {
                  ready.countDown();
                  assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                  previous.close();
                  return null;
                });
        var reconnect =
            executor.submit(
                () -> {
                  ready.countDown();
                  assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                  return register(subscriptionType, ignored -> received.incrementAndGet());
                });

        // when: 기존 등록 해제와 새 등록을 동시에 시작하고 둘 다 끝난 뒤 전송한다.
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        disconnect.get(10, TimeUnit.SECONDS);
        try (var registration = reconnect.get(10, TimeUnit.SECONDS)) {
          send(subscriptionType);

          // then: 성공적으로 등록한 새 연결이 정리 대상에 섞여 사라지지 않는다.
          assertThat(received.get()).as("연결 교체 회차 %s", attempt).isEqualTo(1);
        }
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("사건 연결을 종료하면, 연결을 닫고 목록에서 제거한다")
  void close_incident_connections_closes_connection_and_removes_registration() {
    // given: 정상 연결이 사건에 등록돼 있다.
    var sink = mock(SseLiveEventSink.class);
    registry.registerForIncident(INCIDENT_ID, sink);

    // when: 사건의 연결을 종료한다.
    registry.closeIncidentConnections(INCIDENT_ID);

    // then: 연결 종료가 호출되고 등록도 남지 않는다.
    verify(sink).close();
    assertThat(registry.sinks(INCIDENT_ID)).isEmpty();
  }

  @ParameterizedTest(name = "구독 대상: {0}")
  @ValueSource(strings = {"incident", "account"})
  @DisplayName("종료된 연결이 남아 있으면, 그 연결을 제거하고 정상 연결에는 계속 전송한다")
  void send_when_emitter_has_completed_removes_failed_connection_and_sends_to_healthy_connection(
      String subscriptionType) {
    // given: 실제 Spring emitter가 오류로 종료됐지만 연결 목록에는 아직 남아 있다.
    var emitter = new SseStreamEmitter(ignored -> {});
    emitter.completeWithError(
        new AsyncRequestNotUsableException("Response not usable after response errors."));
    var disconnected = spy(new SseEmitterLiveEventSink(emitter));
    var connected = mock(SseLiveEventSink.class);
    register(subscriptionType, disconnected);
    register(subscriptionType, connected);

    // when: 같은 구독 대상의 연결에 이벤트를 전송한다.
    send(subscriptionType);
    send(subscriptionType);

    // then: 종료된 연결은 첫 실패 뒤 제외되며 정상 연결은 두 번 모두 수신한다.
    verify(disconnected).send(frame);
    verify(connected, times(2)).send(frame);
    if (subscriptionType.equals("incident")) {
      assertThat(registry.sinks(INCIDENT_ID)).containsExactly(connected);
    }
  }

  @ParameterizedTest(name = "구독 대상: {0}")
  @ValueSource(strings = {"incident", "account"})
  @DisplayName("쓰기 중 I/O 오류가 나면, 해당 연결을 제외하고 HTTP 정리는 컨테이너에 맡긴다")
  void send_when_io_fails_removes_connection_without_completing_emitter(String subscriptionType)
      throws IOException {
    // given: 실제 전송 경계에서 응답 쓰기가 실패하는 연결이 먼저 등록돼 있다.
    var emitter = mock(SseStreamEmitter.class);
    doThrow(new IOException("private-network-details"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    register(subscriptionType, new SseEmitterLiveEventSink(emitter));
    var connected = mock(SseLiveEventSink.class);
    register(subscriptionType, connected);

    // when: 연결 목록으로 두 번 전송한다.
    send(subscriptionType);
    send(subscriptionType);

    // then: 실패한 연결은 재사용하지 않고, 정상 연결은 계속 수신한다.
    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter, never()).complete();
    verify(emitter, never()).completeWithError(any());
    verify(connected, times(2)).send(frame);
  }

  @Test
  @DisplayName("실패한 연결의 종료 처리도 실패하면, 정상 연결의 전송은 계속한다")
  void send_when_failed_connection_cannot_close_still_sends_to_healthy_connection() {
    // given: 전송과 종료에서 모두 예외를 던지는 연결이다.
    var disconnected = mock(SseLiveEventSink.class);
    doThrow(new IllegalStateException("completed")).when(disconnected).send(frame);
    doThrow(new IllegalStateException("already closed")).when(disconnected).close();
    var connected = mock(SseLiveEventSink.class);
    registry.registerForIncident(INCIDENT_ID, disconnected);
    registry.registerForIncident(INCIDENT_ID, connected);

    // when: 사건 이벤트를 전송한다.
    registry.sendToIncident(INCIDENT_ID, frame);

    // then: 정리 실패가 다시 전송 순회를 중단시키지 않는다.
    verify(connected).send(frame);
    assertThat(registry.sinks(INCIDENT_ID)).containsExactly(connected);
  }

  @Test
  @DisplayName("연결 종료 중 하나가 실패하면, 나머지도 닫고 사건의 연결 목록을 비운다")
  void close_incident_connections_when_one_close_fails_closes_remaining_connections() {
    // given: 먼저 닫을 연결의 종료 처리가 실패한다.
    var disconnected = mock(SseLiveEventSink.class);
    doThrow(new IllegalStateException("already closed")).when(disconnected).close();
    var connected = mock(SseLiveEventSink.class);
    registry.registerForIncident(INCIDENT_ID, disconnected);
    registry.registerForIncident(INCIDENT_ID, connected);

    // when: 사건 연결을 모두 종료한다.
    registry.closeIncidentConnections(INCIDENT_ID);

    // then: 뒤에 있는 연결도 종료되고 등록은 남지 않는다.
    verify(connected).close();
    assertThat(registry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("메시지 변환 실패가 IllegalStateException으로 감싸지면, 연결 종료로 무시하지 않는다")
  void send_when_message_conversion_fails_propagates_failure() throws IOException {
    // given: Spring은 메시지 변환 등의 내부 오류를 원인 예외와 함께 감싼다.
    var emitter = mock(SseStreamEmitter.class);
    var failure =
        new IllegalStateException(
            "Failed to send", new HttpMessageNotWritableException("conversion failed"));
    doThrow(failure).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
    registry.registerForIncident(INCIDENT_ID, new SseEmitterLiveEventSink(emitter));

    // when / then: 전송 작업이 내부 오류를 실패로 기록할 수 있도록 전달한다.
    assertThatThrownBy(() -> registry.sendToIncident(INCIDENT_ID, frame)).isSameAs(failure);
  }

  @Test
  @DisplayName("잘못된 입력으로 IllegalArgumentException이 발생하면, 호출부에 실패를 전달한다")
  void send_when_invalid_argument_occurs_propagates_failure() {
    // given: 전송 구현에서 예상하지 못한 입력 오류가 발생한다.
    var sink = mock(SseLiveEventSink.class);
    var failure = new IllegalArgumentException("invalid event");
    doThrow(failure).when(sink).send(frame);
    registry.registerForIncident(INCIDENT_ID, sink);

    // when / then: 상위 전송 작업에서 실패를 처리할 수 있어야 한다.
    assertThatThrownBy(() -> registry.sendToIncident(INCIDENT_ID, frame)).isSameAs(failure);
  }

  private AutoCloseable register(String subscriptionType, SseLiveEventSink sink) {
    if (subscriptionType.equals("account")) {
      return registry.registerForAccount(ACCOUNT_ID, sink);
    }
    return registry.registerForIncident(INCIDENT_ID, sink);
  }

  private void send(String subscriptionType) {
    if (subscriptionType.equals("account")) {
      registry.sendToAccount(ACCOUNT_ID, frame);
    } else {
      registry.sendToIncident(INCIDENT_ID, frame);
    }
  }
}
