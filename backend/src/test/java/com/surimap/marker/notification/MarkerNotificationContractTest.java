package com.surimap.marker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.port.NotificationTargetPort;
import com.surimap.marker.notification.service.NotificationRecipientResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S14P31C106-71 L5-T06A marker_notification contract tests. */
@DisplayName("L5-T06A marker notification contract")
class MarkerNotificationContractTest {

  @Test
  @DisplayName("SUPPORT_REQUEST_CREATED는 지휘라인 recipient policy를 사용한다")
  void supportRequestUsesCommandAndFieldCommanderPolicy() {
    CapturingNotificationTargetPort targetPort = new CapturingNotificationTargetPort();
    NotificationRecipientResolver resolver = new NotificationRecipientResolver(targetPort);
    UUID incidentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

    NotificationRecipients recipients =
        resolver.resolve(incidentId, NotificationType.SUPPORT_REQUEST_CREATED);

    assertThat(targetPort.capturedIncidentId).isEqualTo(incidentId);
    assertThat(targetPort.capturedPolicy)
        .isEqualTo(NotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS);
    assertThat(recipients.policy())
        .isEqualTo(NotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS);
  }

  @Test
  @DisplayName("marker_notification migration은 snapshot 저장과 중복 방지 계약을 가진다")
  void markerNotificationMigrationHasSnapshotAndDuplicateSafeContract() throws Exception {
    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V15__create_marker_notification.sql"));

    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS marker_notification");
    assertThat(migration).contains("marker_id UUID NOT NULL");
    assertThat(migration).contains("notification_payload JSONB NOT NULL");
    assertThat(migration).contains("status VARCHAR(32) NOT NULL");
    assertThat(migration).contains("version BIGINT NOT NULL");
    assertThat(migration).contains("ux_marker_notification_marker");
    assertThat(migration).contains("ON marker_notification (marker_id)");
    assertThat(migration).contains("SUPPORT_REQUEST_CREATED");
    assertThat(migration).contains("SNAPSHOT_CREATED");
  }

  private static final class CapturingNotificationTargetPort implements NotificationTargetPort {

    private UUID capturedIncidentId;
    private NotificationRecipientPolicy capturedPolicy;

    @Override
    public NotificationRecipients notificationTargets(
        UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
      capturedIncidentId = incidentId;
      capturedPolicy = recipientPolicy;
      return new NotificationRecipients(recipientPolicy, List.of("acct-cmd-alpha"), List.of());
    }
  }
}
