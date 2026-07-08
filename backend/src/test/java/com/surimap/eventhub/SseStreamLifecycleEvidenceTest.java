package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SSE stream lifecycle evidence")
class SseStreamLifecycleEvidenceTest {

  private static final UUID INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000001");

  private SseStreamSessionRegistry sessionRegistry;

  @BeforeEach
  void setUp() {
    sessionRegistry = new SseStreamSessionRegistry();
  }

  @Test
  @DisplayName("SSE 연결을 100회 열고 닫아도 incident sink가 남지 않는다")
  void repeatedRegisterAndCloseRemovesIncidentSink() throws Exception {
    for (int i = 0; i < 100; i++) {
      var registration = sessionRegistry.register(INCIDENT_ID, new NoopSink());

      assertThat(sessionRegistry.sinks(INCIDENT_ID)).hasSize(1);

      registration.close();

      assertThat(sessionRegistry.sinks(INCIDENT_ID)).isEmpty();
    }
  }

  @Test
  @DisplayName("사건 종료 release가 incident sink를 닫고 registry에서 제거한다")
  void releaseClosesAndRemovesIncidentSinks() {
    var sink = new NoopSink();
    sessionRegistry.register(INCIDENT_ID, sink);

    sessionRegistry.release(INCIDENT_ID);

    assertThat(sink.closed()).isTrue();
    assertThat(sessionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  private static final class NoopSink implements SseLiveEventSink {

    private boolean closed;

    @Override
    public void send(SseEventFrame frame) {}

    @Override
    public void close() {
      closed = true;
    }

    private boolean closed() {
      return closed;
    }
  }
}
