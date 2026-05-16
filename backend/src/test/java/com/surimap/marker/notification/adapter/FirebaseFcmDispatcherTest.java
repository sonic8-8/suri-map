package com.surimap.marker.notification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.notification.port.FcmDispatcherPort.DispatchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FirebaseFcmDispatcher")
class FirebaseFcmDispatcherTest {

  @Test
  @DisplayName("FCM data message는 문자열 payload와 deduplicated token으로 전송된다")
  void sendsStringDataPayloadToFirebaseTokens() {
    CapturingFirebaseFcmSender sender =
        new CapturingFirebaseFcmSender(new FirebaseFcmBatchResult(2, 0, List.of()));
    FirebaseFcmDispatcher dispatcher = dispatcher(sender, true);

    DispatchResult result =
        dispatcher.send(
            List.of(" token-a ", "token-b", "token-a", " "),
            Map.of(
                "type",
                "INCIDENT_ASSIGNMENT_CHANGED",
                "incidentId",
                "incident-1",
                "version",
                2,
                "recipientAccountIds",
                List.of("account-a", "account-b"),
                "pii",
                false),
            "event-1");

    assertThat(result.isFullySuccessful()).isTrue();
    assertThat(sender.tokens).containsExactly("token-a", "token-b");
    assertThat(sender.dryRun).isTrue();
    assertThat(sender.data)
        .containsEntry("eventId", "event-1")
        .containsEntry("type", "INCIDENT_ASSIGNMENT_CHANGED")
        .containsEntry("incidentId", "incident-1")
        .containsEntry("version", "2")
        .containsEntry("recipientAccountIds", "[\"account-a\",\"account-b\"]")
        .containsEntry("pii", "false");
  }

  @Test
  @DisplayName("Firebase 부분 실패는 FcmDispatcherPort 결과에 실패 token으로 반영된다")
  void returnsPartialFailureResult() {
    CapturingFirebaseFcmSender sender =
        new CapturingFirebaseFcmSender(new FirebaseFcmBatchResult(1, 1, List.of("token-b")));
    FirebaseFcmDispatcher dispatcher = dispatcher(sender, false);

    DispatchResult result =
        dispatcher.send(List.of("token-a", "token-b"), Map.of("type", "PERSON_FOUND"), "event-2");

    assertThat(result.eventId()).isEqualTo("event-2");
    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.failureCount()).isEqualTo(1);
    assertThat(result.failedRecipients()).containsExactly("token-b");
    assertThat(result.isFullySuccessful()).isFalse();
    assertThat(sender.dryRun).isFalse();
  }

  @Test
  @DisplayName("공백 token만 있으면 발송하지 않는다")
  void rejectsBlankTokens() {
    FirebaseFcmDispatcher dispatcher =
        dispatcher(
            new CapturingFirebaseFcmSender(new FirebaseFcmBatchResult(0, 0, List.of())), false);

    assertThatThrownBy(
            () -> dispatcher.send(List.of(" ", ""), Map.of("type", "PERSON_FOUND"), "event-3"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recipients");
  }

  private static FirebaseFcmDispatcher dispatcher(
      CapturingFirebaseFcmSender sender, boolean dryRun) {
    FirebaseFcmProperties properties = new FirebaseFcmProperties();
    properties.setDryRun(dryRun);
    return new FirebaseFcmDispatcher(sender, properties, new ObjectMapper());
  }

  private static final class CapturingFirebaseFcmSender implements FirebaseFcmSender {

    private final FirebaseFcmBatchResult result;
    private List<String> tokens = new ArrayList<>();
    private Map<String, String> data = Map.of();
    private boolean dryRun;

    private CapturingFirebaseFcmSender(FirebaseFcmBatchResult result) {
      this.result = result;
    }

    @Override
    public FirebaseFcmBatchResult sendMulticast(
        List<String> tokens, Map<String, String> data, boolean dryRun) {
      this.tokens = List.copyOf(tokens);
      this.data = Map.copyOf(data);
      this.dryRun = dryRun;
      return result;
    }
  }
}
