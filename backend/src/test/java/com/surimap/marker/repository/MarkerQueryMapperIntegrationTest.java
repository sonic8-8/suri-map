package com.surimap.marker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerQueryFilters;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** L5-T08 RED integration tests for MarkerQuery.byIncident persistence behavior. */
@DisplayName("L5-T08 MarkerQuery.byIncident mapper contract")
@Tag("integration")
class MarkerQueryMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa7108");
  private static final UUID INCIDENT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb7108");
  private static final UUID OP_A = UUID.fromString("11111111-1111-4111-8111-111111117108");
  private static final UUID OP_B = UUID.fromString("22222222-2222-4222-8222-222222227108");
  private static final UUID ACTIVE_MARKER_ID =
      UUID.fromString("33333333-3333-4333-8333-333333337108");
  private static final UUID UPDATED_MARKER_ID =
      UUID.fromString("44444444-4444-4444-8444-444444447108");
  private static final UUID DELETED_MARKER_ID =
      UUID.fromString("55555555-5555-4555-8555-555555557108");
  private static final UUID OTHER_INCIDENT_MARKER_ID =
      UUID.fromString("66666666-6666-4666-8666-666666667108");
  private static final UUID ACCOUNT_ID = UUID.fromString("77777777-7777-4777-8777-777777777108");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("88888888-8888-4888-8888-888888887108");

  @Autowired private MarkerMapper markerMapper;

  @BeforeEach
  void cleanMarkerQueryTables() {
    jdbcTemplate.execute("TRUNCATE TABLE photo, marker");
  }

  @Test
  @DisplayName("marker table은 byIncident filtering을 위해 incident_id column을 가진다")
  void marker_table_has_incident_id_for_byIncident_filtering() {
    List<String> columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'marker'
            ORDER BY ordinal_position
            """,
            String.class);

    assertThat(columns).contains("incident_id");
  }

  @Test
  @DisplayName("byIncident 기본 조회는 incidentId별 ACTIVE/UPDATED만 반환하고 DELETED는 제외한다")
  void byIncident_defaults_to_incident_active_updated_markers_only() {
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    insertMarker(UPDATED_MARKER_ID, INCIDENT_A, OP_A, "FIELD_CONDITION", "UPDATED", 2L);
    insertMarker(DELETED_MARKER_ID, INCIDENT_A, OP_A, "NOTE", "DELETED", 3L);
    insertMarker(OTHER_INCIDENT_MARKER_ID, INCIDENT_B, OP_B, "CLUE", "ACTIVE", 1L);

    List<UUID> ids =
        markerMapper.findByIncident(INCIDENT_A, MarkerQueryFilters.empty()).stream()
            .map(MarkerRecord::getId)
            .toList();

    assertThat(ids).containsExactly(ACTIVE_MARKER_ID, UPDATED_MARKER_ID);
  }

  @Test
  @DisplayName("byIncident는 opId/type/status optional filter를 적용한다")
  void byIncident_applies_optional_op_type_status_filters() {
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    insertMarker(UPDATED_MARKER_ID, INCIDENT_A, OP_A, "FIELD_CONDITION", "UPDATED", 2L);
    insertMarker(OTHER_INCIDENT_MARKER_ID, INCIDENT_A, OP_B, "CLUE", "ACTIVE", 3L);

    List<UUID> ids =
        markerMapper
            .findByIncident(
                INCIDENT_A,
                new MarkerQueryFilters(OP_A, MarkerType.FIELD_CONDITION, MarkerStatus.UPDATED))
            .stream()
            .map(MarkerRecord::getId)
            .toList();

    assertThat(ids).containsExactly(UPDATED_MARKER_ID);
  }

  @Test
  @DisplayName("photoSummary는 ATTACHED 사진만 attachedAt 오름차순으로 포함한다")
  void photoSummary_includes_attached_photos_only_sorted_by_attachedAt() {
    insertMarker(ACTIVE_MARKER_ID, INCIDENT_A, OP_A, "CLUE", "ACTIVE", 1L);
    UUID laterPhotoId = UUID.fromString("99999999-9999-4999-8999-999999997108");
    UUID pendingPhotoId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeee7108");
    UUID earlierPhotoId = UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffff7108");
    insertPhoto(laterPhotoId, ACTIVE_MARKER_ID, "ATTACHED", "2026-04-28T00:03:00Z", 2L);
    insertPhoto(pendingPhotoId, ACTIVE_MARKER_ID, "PENDING_UPLOAD", null, 1L);
    insertPhoto(earlierPhotoId, ACTIVE_MARKER_ID, "ATTACHED", "2026-04-28T00:01:00Z", 3L);

    List<UUID> photoIds =
        markerMapper.findAttachedPhotoSummariesByMarkerIds(List.of(ACTIVE_MARKER_ID)).stream()
            .map(MarkerPhotoSummaryRow::photoId)
            .toList();

    assertThat(photoIds).containsExactly(earlierPhotoId, laterPhotoId);
  }

  private void insertMarker(
      UUID markerId, UUID incidentId, UUID opId, String type, String status, long version) {
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
        VALUES (?, ?, ?, NULL, ?, NULL, ST_SetSRID(ST_MakePoint(126.9134, 35.1631), 4326),
                ?, ?, ?, ?, 'APP', ?, ?)
        """,
        markerId,
        incidentId,
        opId,
        type,
        "L5-T08 marker for " + incidentId,
        Timestamp.from(Instant.parse("2026-04-28T00:00:00Z").plusSeconds(version)),
        ACCOUNT_ID,
        POLICE_PHONE_ID,
        status,
        version);
  }

  private void insertPhoto(
      UUID photoId, UUID markerId, String status, String attachedAt, long version) {
    jdbcTemplate.update(
        """
        INSERT INTO photo (
            id,
            marker_id,
            object_key,
            status,
            attached_at,
            content_type,
            size_bytes,
            version
        )
        VALUES (?, ?, ?, ?, ?::timestamptz, 'image/jpeg', 1024, ?)
        """,
        photoId,
        markerId,
        "markers/" + INCIDENT_A + "/" + markerId + "/" + photoId + ".jpg",
        status,
        attachedAt,
        version);
  }
}
