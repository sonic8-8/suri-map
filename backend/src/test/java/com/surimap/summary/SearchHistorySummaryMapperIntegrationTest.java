package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.api.service.path.request.SearchPathPointServiceRequest;
import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.api.service.path.response.SearchPathPointsAppendServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@DisplayName("S8 search_history_summary MyBatis evidence mapper")
class SearchHistorySummaryMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0111");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888881111");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000001111");
  private static final UUID PATH_ID = UUID.fromString("70000000-0000-0000-0000-000000001111");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000001111");
  private static final Instant BASE_TIME = Instant.parse("2026-05-20T00:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  // 아래 값은 sql/path/search-path-context.sql이 만드는 기록 중인 경로의 맥락이다.
  private static final UUID TRACK_INCIDENT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID TRACK_OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID TRACK_ACCOUNT_ID =
      UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID TRACK_PATH_ID =
      UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
  private static final OffsetDateTime TRACK_STARTED_AT =
      OffsetDateTime.parse("2026-04-28T09:00:00+09:00");
  private static final double FOOT_SPEED = 1.2;
  private static final double VEHICLE_SPEED = 12.0;

  @Autowired private SearchHistorySummaryMapper mapper;
  @Autowired private SearchPathService searchPathService;

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

  @Test
  @DisplayName("같은 구간으로 이어 붙인 업로드 경계의 선은 도보 거리에 들어간다")
  void walking_distance_includes_line_between_uploads_joined_into_one_segment() throws Exception {
    // given: 멀리 떨어진 두 도보 묶음을 같은 구간으로 이어 붙였다.
    seedRecordingPath();
    appendTrack("joined-first", 0, 6, FOOT_SPEED);
    appendTrack("joined-second", 6, 6, FOOT_SPEED);

    // when: 요약 입력을 새로 계산한다.
    JsonNode metrics =
        OBJECT_MAPPER.readTree(mapper.sourceEvidenceForScope(TRACK_OP_ID, null)).path("metrics");

    // then: 경계의 선까지 들어가 도보 거리가 전체 거리와 같다.
    assertThat(metrics.path("walkingDistanceMeters").asInt())
        .isEqualTo(metrics.path("totalDistanceMeters").asInt());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(SplitBoundary.class)
  @DisplayName("상한, 이동 유형 변경, 보정 구간 때문에 나뉜 경계의 선은 도보 거리와 차량 거리에 들어가지 않는다")
  void walking_and_driving_distances_exclude_line_between_split_segments(SplitBoundary boundary)
      throws Exception {
    // given: 앞 묶음과 멀리 떨어진 다음 묶음이 새 구간으로 나뉘었다.
    seedRecordingPath();
    SearchPathPointsAppendServiceResponse first =
        appendTrack("split-first", 0, boundary.firstCount, FOOT_SPEED);
    if (boundary == SplitBoundary.MANUAL) {
      searchPathService.correctSegment(
          SearchPathSegmentCorrectionServiceRequest.builder()
              .searchPathSegmentId(first.getSegments().get(0).getId())
              .movementType(MovementType.FOOT)
              .reason("이동 유형 보정")
              .correctedByAccountId(TRACK_ACCOUNT_ID)
              .idempotencyKey("split-correction")
              .build());
    }
    appendTrack("split-second", boundary.firstCount, 6, boundary.secondSpeed);

    // when: 요약 입력을 새로 계산한다.
    JsonNode metrics =
        OBJECT_MAPPER.readTree(mapper.sourceEvidenceForScope(TRACK_OP_ID, null)).path("metrics");

    // then: 두 구간의 거리를 더해도 경계의 선만큼 전체 거리보다 짧다.
    int splitDistance =
        metrics.path("walkingDistanceMeters").asInt()
            + metrics.path("drivingDistanceMeters").asInt();
    assertThat(metrics.path("totalDistanceMeters").asInt() - splitDistance)
        .isCloseTo(boundaryLineMeters(boundary.firstCount - 1), within(2));
  }

  enum SplitBoundary {
    POINT_LIMIT(120, FOOT_SPEED),
    MOVEMENT_TYPE_CHANGE(6, VEHICLE_SPEED),
    MANUAL(6, FOOT_SPEED);

    private final int firstCount;
    private final double secondSpeed;

    SplitBoundary(int firstCount, double secondSpeed) {
      this.firstCount = firstCount;
      this.secondSpeed = secondSpeed;
    }
  }

  // 좌표 추가 Service가 쓰는 수색 차수, 근무 교대, 사건 배정을 기존 경로 테스트 입력으로 준비한다.
  private void seedRecordingPath() {
    new ResourceDatabasePopulator(new ClassPathResource("sql/path/search-path-context.sql"))
        .execute(jdbcTemplate.getDataSource());
  }

  private SearchPathPointsAppendServiceResponse appendTrack(
      String idempotencyKey, int from, int count, double speed) {
    return searchPathService.appendPoints(
        SearchPathPointsAppendServiceRequest.builder()
            .incidentId(TRACK_INCIDENT_ID)
            .opId(TRACK_OP_ID)
            .pathId(TRACK_PATH_ID)
            .accountId(TRACK_ACCOUNT_ID)
            .clockOffsetMs(0L)
            .idempotencyKey(idempotencyKey)
            .points(
                IntStream.range(from, from + count)
                    .mapToObj(
                        i ->
                            SearchPathPointServiceRequest.builder()
                                .pointId("summary-track-" + i)
                                .lon(trackLon(i))
                                .lat(new BigDecimal("35.162000"))
                                .speedMps(BigDecimal.valueOf(speed))
                                .horizontalAccuracyM(5)
                                .clientTs(TRACK_STARTED_AT.plusNanos(i * 250_000_000L))
                                .elapsedRealtimeNanos(10_000_000_000L + i * 250_000_000L)
                                .build())
                    .toList())
            .build());
  }

  // 묶음 안에서는 0.000001도씩, 묶음 사이에서는 0.001도(약 91m) 떨어지게 둔다.
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
