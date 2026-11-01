package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

  @ParameterizedTest(name = "{0}")
  @CsvSource({"FOOT,walkingDistanceMeters", "VEHICLE,drivingDistanceMeters"})
  @DisplayName("같은 구간으로 이어 붙인 업로드 경계의 선은 그 이동 유형의 거리에 들어간다")
  void distance_of_movement_type_includes_line_between_uploads_joined_into_one_segment(
      String movementType, String distanceField) throws Exception {
    // given: 멀리 떨어진 두 업로드의 좌표 12개를 구간 하나로 이어 붙여 저장했다.
    seedPathWithSegments(12, new SegmentRow(0, 11, movementType, "AUTO"));

    // when: 요약 입력을 새로 계산한다.
    JsonNode metrics = OBJECT_MAPPER.readTree(mapper.sourceEvidenceForScope(OP_ID, null));

    // then: 경계의 선까지 들어가 그 이동 유형의 거리가 전체 거리와 같다.
    assertThat(metrics.path("metrics").path(distanceField).asInt())
        .isEqualTo(metrics.path("metrics").path("totalDistanceMeters").asInt())
        .isGreaterThan(boundaryLineMeters(5));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(SplitBoundary.class)
  @DisplayName("상한, 이동 유형 변경, 보정 구간 때문에 나뉜 경계의 선은 도보 거리와 차량 거리에 들어가지 않는다")
  void walking_and_driving_distances_exclude_line_between_split_segments(SplitBoundary boundary)
      throws Exception {
    // given: 멀리 떨어진 두 업로드의 좌표를 서로 다른 구간으로 나눠 저장했다.
    seedPathWithSegments(
        boundary.firstCount + 6,
        new SegmentRow(0, boundary.firstCount - 1, "FOOT", boundary.firstSource),
        new SegmentRow(
            boundary.firstCount, boundary.firstCount + 5, boundary.secondMovementType, "AUTO"));

    // when: 요약 입력을 새로 계산한다.
    JsonNode metrics =
        OBJECT_MAPPER.readTree(mapper.sourceEvidenceForScope(OP_ID, null)).path("metrics");

    // then: 두 구간의 거리를 더해도 경계의 선만큼 전체 거리보다 짧다.
    int splitDistance =
        metrics.path("walkingDistanceMeters").asInt()
            + metrics.path("drivingDistanceMeters").asInt();
    assertThat(metrics.path("totalDistanceMeters").asInt() - splitDistance)
        .isCloseTo(boundaryLineMeters(boundary.firstCount - 1), within(2));
  }

  // 각 값은 그 경계에서 구간이 나뉠 때 저장되는 앞 구간의 좌표 수, 앞 구간의 분류 출처, 뒤 구간의 이동 유형이다.
  enum SplitBoundary {
    POINT_LIMIT(120, "AUTO", "FOOT"),
    MOVEMENT_TYPE_CHANGE(6, "AUTO", "VEHICLE"),
    MANUAL(6, "MANUAL", "FOOT");

    private final int firstCount;
    private final String firstSource;
    private final String secondMovementType;

    SplitBoundary(int firstCount, String firstSource, String secondMovementType) {
      this.firstCount = firstCount;
      this.firstSource = firstSource;
      this.secondMovementType = secondMovementType;
    }
  }

  private record SegmentRow(int start, int end, String movementType, String source) {}

  // 구간 도형에는 순번 범위의 원본 GPS 좌표를 순서대로 이어 넣는다.
  private void seedPathWithSegments(int pointCount, SegmentRow... segments) {
    seedDutyShift();
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
          id, duty_shift_id, account_id, status, started_at, ended_at,
          version, created_at, updated_at
        )
        VALUES (?, ?, ?, 'ENDED', ?, ?, 2, ?, ?)
        """,
        PATH_ID,
        DUTY_SHIFT_ID,
        ACCOUNT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(pointCount)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO search_path_gps_point (
          search_path_id, point_order, point_id, client_ts, lon, lat, speed_mps, created_at
        ) VALUES (?, ?, ?, ?, ?, 35.162, 1, ?)
        """,
        IntStream.range(0, pointCount)
            .mapToObj(
                i ->
                    new Object[] {
                      PATH_ID,
                      i,
                      "summary-track-" + i,
                      timestamp(BASE_TIME.plusSeconds(i)),
                      trackLon(i),
                      timestamp(BASE_TIME)
                    })
            .toList());
    for (SegmentRow segment : segments) {
      jdbcTemplate.update(
          """
          INSERT INTO search_path_segment (
            id, search_path_id, start_point_order, end_point_order, last_changed_path_version,
            movement_type, movement_type_source, geometry, started_at, ended_at,
            version, created_at, updated_at
          )
          SELECT gen_random_uuid(), ?, ?, ?, 2, ?, ?,
                 ST_MakeLine(
                     ST_SetSRID(ST_MakePoint(lon::float8, lat::float8), 4326)
                     ORDER BY point_order),
                 min(client_ts), max(client_ts), 1, ?, ?
          FROM search_path_gps_point
          WHERE search_path_id = ? AND point_order BETWEEN ? AND ?
          """,
          PATH_ID,
          segment.start(),
          segment.end(),
          segment.movementType(),
          segment.source(),
          timestamp(BASE_TIME),
          timestamp(BASE_TIME),
          PATH_ID,
          segment.start(),
          segment.end());
    }
  }

  // 업로드 안에서는 0.000001도씩, 업로드 경계(순번 6과 120)에서는 0.001도(약 91m) 떨어지게 둔다.
  private static BigDecimal trackLon(int index) {
    return new BigDecimal("126.913000")
        .add(new BigDecimal("0.000001").multiply(BigDecimal.valueOf(index)))
        .add(index >= 6 ? new BigDecimal("0.001000") : BigDecimal.ZERO)
        .add(index >= 120 ? new BigDecimal("0.001000") : BigDecimal.ZERO);
  }

  private int boundaryLineMeters(int lastIndexBeforeBoundary) {
    return jdbcTemplate.queryForObject(
        """
        SELECT ROUND(ST_Length(ST_MakeLine(
            ST_SetSRID(ST_MakePoint(?, 35.162), 4326),
            ST_SetSRID(ST_MakePoint(?, 35.162), 4326))::geography)::numeric)::int
        """,
        Integer.class,
        trackLon(lastIndexBeforeBoundary).doubleValue(),
        trackLon(lastIndexBeforeBoundary + 1).doubleValue());
  }

  private void seedPointBackedPath() {
    seedDutyShift();
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

  private void seedDutyShift() {
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
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }
}
