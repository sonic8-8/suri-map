package com.surimap.domain.marker;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.marker.MarkerNotificationMapper.NotificationRow;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MarkerNotificationMapperTest extends PostGisIntegrationTestSupport {

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
  @DisplayName("사건 ID로 알림을 조회하면, 해당 사건의 알림·마커 정보와 최근 이벤트 ID를 반환한다")
  void findNotificationRowsByIncidentId_multipleIncidents_returnsOnlyRequestedIncidentRows() {
    // given: 서로 다른 사건에 알림을 저장하고 조회할 알림의 이벤트를 기록한다.
    insertMarker(MARKER_ID, INCIDENT_ID);
    insertMarker(UUID.fromString("53000000-0000-4000-8000-000000002932"), OTHER_INCIDENT_ID);
    insertNotification(NOTIFICATION_ID, MARKER_ID, CREATED_AT);
    insertNotification(
        UUID.fromString("54000000-0000-4000-8000-000000002932"),
        UUID.fromString("53000000-0000-4000-8000-000000002932"),
        CREATED_AT);
    insertEvent(EVENT_ID, NOTIFICATION_ID, CREATED_AT.plusSeconds(1), CREATED_AT.plusSeconds(1));

    // when: 한 사건의 알림만 조회한다.
    List<NotificationRow> rows = mapper.findNotificationRowsByIncidentId(INCIDENT_ID);

    // then: 다른 사건은 제외하고 SQL 조회 결과의 모든 필드를 매핑한다.
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getNotificationId()).isEqualTo(NOTIFICATION_ID);
              assertThat(row.getMarkerId()).isEqualTo(MARKER_ID);
              assertThat(row.getIncidentId()).isEqualTo(INCIDENT_ID);
              assertThat(row.getOpId()).isEqualTo(OP_ID);
              assertThat(row.getPolicePhoneId()).isEqualTo(POLICE_PHONE_ID);
              assertThat(row.getNotificationType()).isEqualTo("SUPPORT_REQUEST_CREATED");
              assertThat(row.getStatus()).isEqualTo("SNAPSHOT_CREATED");
              assertThat(row.getVersion()).isEqualTo(3L);
              assertThat(row.getCreatedAt()).isEqualTo(CREATED_AT);
              assertThat(row.getLatestEventId()).isEqualTo(EVENT_ID);
            });
  }

  @Test
  @DisplayName("알림에 이벤트가 여러 개면, 발생 시각이 최신인 이벤트를 선택하고 동률이면 생성 시각으로 구분한다")
  void findNotificationRowsByIncidentId_multipleEvents_returnsLatestEventId() {
    // given: 발생 시각이 같은 두 이벤트와 뒤늦게 저장된 과거 이벤트가 있다.
    insertMarker(MARKER_ID, INCIDENT_ID);
    insertNotification(NOTIFICATION_ID, MARKER_ID, CREATED_AT);
    insertEvent(EVENT_ID, NOTIFICATION_ID, CREATED_AT.plusSeconds(2), CREATED_AT.plusSeconds(4));
    insertEvent(
        UUID.randomUUID(), NOTIFICATION_ID, CREATED_AT.plusSeconds(2), CREATED_AT.plusSeconds(3));
    insertEvent(
        UUID.randomUUID(), NOTIFICATION_ID, CREATED_AT.plusSeconds(1), CREATED_AT.plusSeconds(5));

    // when: 알림에 연결된 최근 이벤트를 조회한다.
    List<NotificationRow> rows = mapper.findNotificationRowsByIncidentId(INCIDENT_ID);

    // then: 발생 시각과 생성 시각을 순서대로 비교한 최신 이벤트 하나를 반환한다.
    assertThat(rows)
        .singleElement()
        .extracting(NotificationRow::getLatestEventId)
        .isEqualTo(EVENT_ID);
  }

  @Test
  @DisplayName("이벤트가 없는 알림도 조회되며, 생성 시각과 알림 ID 순서로 반환한다")
  void findNotificationRowsByIncidentId_notificationsWithoutEvents_returnsSortedRows() {
    // given: 저장 순서와 생성 시각·ID 순서가 다른 알림 세 건이 있다.
    UUID secondMarkerId = UUID.fromString("53000000-0000-4000-8000-000000002932");
    UUID thirdMarkerId = UUID.fromString("53000000-0000-4000-8000-000000002933");
    UUID secondNotificationId = UUID.fromString("54000000-0000-4000-8000-000000002932");
    UUID thirdNotificationId = UUID.fromString("54000000-0000-4000-8000-000000002933");
    insertMarker(MARKER_ID, INCIDENT_ID);
    insertMarker(secondMarkerId, INCIDENT_ID);
    insertMarker(thirdMarkerId, INCIDENT_ID);
    insertNotification(NOTIFICATION_ID, MARKER_ID, CREATED_AT.plusSeconds(1));
    insertNotification(thirdNotificationId, thirdMarkerId, CREATED_AT);
    insertNotification(secondNotificationId, secondMarkerId, CREATED_AT);

    // when: 이벤트 유무와 무관하게 사건의 알림을 조회한다.
    List<NotificationRow> rows = mapper.findNotificationRowsByIncidentId(INCIDENT_ID);

    // then: 오래된 알림부터, 같은 시각이면 ID 순서로 반환하며 최근 이벤트 ID는 null이다.
    assertThat(rows)
        .extracting(NotificationRow::getNotificationId)
        .containsExactly(secondNotificationId, thirdNotificationId, NOTIFICATION_ID);
    assertThat(rows)
        .extracting(NotificationRow::getCreatedAt)
        .containsExactly(CREATED_AT, CREATED_AT, CREATED_AT.plusSeconds(1));
    assertThat(rows).allSatisfy(row -> assertThat(row.getLatestEventId()).isNull());
  }

  @Test
  @DisplayName("사건에 알림이 없으면, 빈 목록을 반환한다")
  void findNotificationRowsByIncidentId_noNotifications_returnsEmptyList() {
    // given: 알림 테이블이 비어 있다.

    // when: 사건의 알림을 조회한다.
    List<NotificationRow> rows = mapper.findNotificationRowsByIncidentId(INCIDENT_ID);

    // then: null 대신 빈 목록을 반환한다.
    assertThat(rows).isEmpty();
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
                ST_SetSRID(ST_MakePoint(126.9134, 35.1631), 4326),
                'support requested', ?, ?, ?, 'APP', 'ACTIVE', 2)
        """,
        markerId,
        incidentId,
        OP_ID,
        Timestamp.from(CREATED_AT),
        ACCOUNT_ID,
        POLICE_PHONE_ID);
  }

  private void insertNotification(UUID notificationId, UUID markerId, Instant createdAt) {
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
        Timestamp.from(createdAt));
  }

  private void insertEvent(
      UUID eventId, UUID notificationId, Instant occurredAt, Instant createdAt) {
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
            occurred_at,
            created_at
        )
        VALUES (?, ?, 'SUPPORT_REQUEST_CREATED', 1, '{}'::jsonb, 'marker_notification', ?, ?, ?)
        """,
        eventId,
        INCIDENT_ID,
        notificationId,
        Timestamp.from(occurredAt),
        Timestamp.from(createdAt));
  }
}
