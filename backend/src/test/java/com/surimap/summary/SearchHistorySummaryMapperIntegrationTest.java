package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("S8 search_history_summary MyBatis evidence mapper")
class SearchHistorySummaryMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0111");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888881111");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000001111");
  private static final UUID PATH_ID = UUID.fromString("70000000-0000-0000-0000-000000001111");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000001111");
  private static final Instant BASE_TIME = Instant.parse("2026-05-20T00:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private SearchHistorySummaryMapper mapper;

  @BeforeEach
  void cleanAndSeed() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          search_path_lifecycle_event,
          search_path_excluded_point,
          search_path_segment,
          search_path,
          duty_shift,
          handover_memo,
          search_history_summary,
          operational_period,
          incident
        CASCADE
        """);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1111'::uuid, 'summary evidence fixture', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_at, ended_at,
          version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ENDED', 'OTHER', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(3600)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  @Test
  @DisplayName("OP scope evidence supports null dutyShiftId after OP transition")
  void sourceEvidenceForOpScopeSupportsNullDutyShiftId() throws Exception {
    String evidence = mapper.sourceEvidenceForScope(OP_ID, null);

    JsonNode root = OBJECT_MAPPER.readTree(evidence);
    assertThat(root.path("briefingKind").asText()).isEqualTo("OP_SUMMARY");
    assertThat(root.path("sourceRefs").path("opId").asText()).isEqualTo(OP_ID.toString());
    assertThat(root.path("sourceRefs").path("dutyShiftId").isNull()).isTrue();
  }

  @Test
  @DisplayName("기존 경로 도형보다 GPS 좌표를 우선해 수색 거리를 계산한다")
  void sourceEvidencePrefersOrderedGpsPointsToStoredPathGeometry() throws Exception {
    seedPointBackedPath();

    JsonNode root = OBJECT_MAPPER.readTree(mapper.sourceEvidenceForScope(OP_ID, null));

    assertThat(root.path("metrics").path("totalDistanceMeters").asInt()).isEqualTo(111);
    assertThat(root.path("paths").get(0).path("distanceMeters").asInt()).isEqualTo(111);
  }

  private void seedPointBackedPath() {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type,
          organization_type, status, created_at, updated_at
        )
        VALUES (?, 'summary-point-account', '{noop}fixture', '요약 좌표 계정',
                'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        ACCOUNT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, police_phone_id, status,
          started_by_account_id, started_at, ended_at, version, created_at, updated_at
        )
        VALUES (?, ?, gen_random_uuid(), gen_random_uuid(), 'ENDED', ?, ?, ?, 1, ?, ?)
        """,
        DUTY_SHIFT_ID,
        OP_ID,
        ACCOUNT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(60)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
          id, duty_shift_id, account_id, status, started_at, ended_at,
          geometry, version, created_at, updated_at
        )
        VALUES (
          ?, ?, ?, 'ENDED', ?, ?,
          ST_GeomFromText('LINESTRING(0 0, 0.01 0)', 4326),
          2, ?, ?
        )
        """,
        PATH_ID,
        DUTY_SHIFT_ID,
        ACCOUNT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(60)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO search_path_gps_point (
          search_path_id, point_order, point_id, client_ts, lon, lat, speed_mps, created_at
        ) VALUES
          (?, 0, 'summary-point-1', ?, 0, 0, 1, ?),
          (?, 1, 'summary-point-2', ?, 0.001, 0, 1, ?)
        """,
        PATH_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME),
        PATH_ID,
        timestamp(BASE_TIME.plusSeconds(5)),
        timestamp(BASE_TIME));
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }
}
