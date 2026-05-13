package com.surimap.marker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.notification.query.MarkerNotificationToastRow;
import com.surimap.marker.notification.repository.MarkerNotificationMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("marker notification toast query")
@Tag("integration")
class MarkerNotificationToastQueryIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("51000000-0000-4000-8000-000000002931");
  private static final UUID OTHER_INCIDENT_ID =
      UUID.fromString("51000000-0000-4000-8000-000000002932");
  private static final UUID OP_ID = UUID.fromString("52000000-0000-4000-8000-000000002931");
  private static final UUID MARKER_ID = UUID.fromString("53000000-0000-4000-8000-000000002931");
  private static final UUID NOTIFICATION_ID =
      UUID.fromString("54000000-0000-4000-8000-000000002931");
  private static final UUID EVENT_ID = UUID.fromString("55000000-0000-4000-8000-000000002931");
  private static final UUID ACCOUNT_ID = UUID.fromString("56000000-0000-4000-8000-000000002931");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("57000000-0000-4000-8000-000000002931");
  private static final Instant CREATED_AT = Instant.parse("2026-05-13T00:00:00Z");

  @Autowired private MarkerNotificationMapper mapper;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.execute("TRUNCATE TABLE event_dispatch_job, marker_notification, marker");
  }

  @Test
  @DisplayName("byIncident returns marker notification rows with event cursor for toast board slot")
  void byIncidentReturnsToastRowsWithEventCursor() {
    insertMarker(MARKER_ID, INCIDENT_ID);
    insertMarker(UUID.fromString("53000000-0000-4000-8000-000000002932"), OTHER_INCIDENT_ID);
    insertNotification(NOTIFICATION_ID, MARKER_ID);
    insertNotification(
        UUID.fromString("54000000-0000-4000-8000-000000002932"),
        UUID.fromString("53000000-0000-4000-8000-000000002932"));
    insertEvent(EVENT_ID, INCIDENT_ID, NOTIFICATION_ID);

    List<MarkerNotificationToastRow> rows = mapper.byIncident(INCIDENT_ID);

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.notificationId()).isEqualTo(NOTIFICATION_ID);
              assertThat(row.markerId()).isEqualTo(MARKER_ID);
              assertThat(row.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(row.opId()).isEqualTo(OP_ID);
              assertThat(row.policePhoneId()).isEqualTo(POLICE_PHONE_ID);
              assertThat(row.notificationType()).isEqualTo("SUPPORT_REQUEST_CREATED");
              assertThat(row.status()).isEqualTo("SNAPSHOT_CREATED");
              assertThat(row.version()).isEqualTo(3L);
              assertThat(row.latestEventId()).isEqualTo(EVENT_ID);
            });
  }

  private void insertMarker(UUID markerId, UUID incidentId) {
    jdbcTemplate.update(
        """
        INSERT INTO marker (
            id,
            incident_id,
            operational_period_id,
            duty_shift_id,
            marker_type,
            support_request_type,
            location,
            memo,
            occurred_at,
            created_by_account_id,
            police_phone_id,
            marker_source,
            status,
            version
        )
        VALUES (?, ?, ?, NULL, 'SUPPORT_REQUEST', 'DRONE',
                ST_SetSRID(ST_MakePoint(126.9565, 37.5712), 4326),
                'support requested', ?, ?, ?, 'APP', 'ACTIVE', 2)
        """,
        markerId,
        incidentId,
        OP_ID,
        Timestamp.from(CREATED_AT),
        ACCOUNT_ID,
        POLICE_PHONE_ID);
  }

  private void insertNotification(UUID notificationId, UUID markerId) {
    jdbcTemplate.update(
        """
        INSERT INTO marker_notification (
            id,
            marker_id,
            notification_type,
            recipient_rule,
            recipient_account_ids,
            recipient_police_phone_ids,
            notification_payload,
            status,
            version,
            created_at
        )
        VALUES (?, ?, 'SUPPORT_REQUEST_CREATED', 'COMMANDERS_AND_FIELD_COMMANDERS',
                ARRAY[?]::uuid[], ARRAY[?]::uuid[], '{}'::jsonb, 'SNAPSHOT_CREATED', 3, ?)
        """,
        notificationId,
        markerId,
        ACCOUNT_ID,
        POLICE_PHONE_ID,
        Timestamp.from(CREATED_AT));
  }

  private void insertEvent(UUID eventId, UUID incidentId, UUID notificationId) {
    jdbcTemplate.update(
        """
        INSERT INTO event_dispatch_job (
            event_id,
            incident_id,
            event_type,
            payload_format_version,
            payload,
            source_entity_type,
            source_entity_id,
            occurred_at
        )
        VALUES (?, ?, 'SUPPORT_REQUEST_CREATED', 1, '{}'::jsonb, 'marker_notification', ?, ?)
        """,
        eventId,
        incidentId,
        notificationId,
        Timestamp.from(CREATED_AT.plusSeconds(1)));
  }
}
