package com.surimap.api.service.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.api.service.path.request.SearchPathPointServiceRequest;
import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.api.service.path.response.SearchPathPointsAppendServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import com.surimap.api.service.path.response.SearchPathSegmentCorrectionServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("SearchPath service")
@Tag("integration")
@Sql(scripts = "/sql/path/search-path-context.sql")
class SearchPathServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID POLICE_PHONE_ID = SearchPathFixtures.POLICE_PHONE_ID;
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID CORRECTED_BY_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  @Autowired private SearchPathService searchPathService;

  @Test
  @DisplayName("manual segment correction stages SEARCH_PATH_SEGMENT_UPDATED EventHub job")
  void segment_correction_stages_event_dispatch_job() {
    SearchPathPointsAppendServiceResponse batch =
        searchPathService.appendPoints(batchRequest("idem-path-event-segment-batch"));
    String segmentId = batch.getSegments().get(0).id();

    searchPathService.correctSegment(
        segmentCorrectionRequest(segmentId, "idem-path-event-segment-correction"));

    Map<String, Object> eventRow =
        jdbcTemplate.queryForMap(
            """
            SELECT event_type,
                   source_entity_type,
                   source_entity_id,
                   dispatch_status,
                   payload ->> 'id' AS payload_id,
                   payload ->> 'incidentId' AS payload_incident_id,
                   payload ->> 'opId' AS payload_op_id,
                   payload ->> 'policePhoneId' AS payload_police_phone_id,
                   payload ->> 'segmentId' AS payload_segment_id,
                   payload ->> 'movementType' AS payload_movement_type,
                   payload ->> 'movementTypeSource' AS payload_movement_type_source,
                   (payload ->> 'version')::bigint AS payload_version,
                   jsonb_exists(payload, 'sequence') AS has_sequence
            FROM event_dispatch_job
            WHERE event_type = 'SEARCH_PATH_SEGMENT_UPDATED'
              AND source_entity_id = ?::uuid
            """,
            segmentId);

    assertThat(eventRow.get("event_type")).isEqualTo("SEARCH_PATH_SEGMENT_UPDATED");
    assertThat(eventRow.get("source_entity_type")).isEqualTo("search_path_segment");
    assertThat(eventRow.get("source_entity_id").toString()).isEqualTo(segmentId);
    assertThat(eventRow.get("dispatch_status")).isEqualTo("PENDING");
    assertThat(eventRow.get("payload_id")).isEqualTo(PATH_ID.toString());
    assertThat(eventRow.get("payload_incident_id")).isEqualTo(INCIDENT_ID.toString());
    assertThat(eventRow.get("payload_op_id")).isEqualTo(OP_ID.toString());
    assertThat(eventRow.get("payload_police_phone_id")).isEqualTo(POLICE_PHONE_ID.toString());
    assertThat(eventRow.get("payload_segment_id")).isEqualTo(segmentId);
    assertThat(eventRow.get("payload_movement_type")).isEqualTo("FOOT");
    assertThat(eventRow.get("payload_movement_type_source")).isEqualTo("MANUAL");
    assertThat(eventRow.get("payload_version")).isEqualTo(3L);
    assertThat(eventRow.get("has_sequence")).isEqualTo(true);
  }

  @Test
  @DisplayName("batch append idempotency replay does not append geometry twice")
  void batch_append_idempotency_replay_does_not_append_twice() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-batch-db-replay"));

    SearchPathPointsAppendServiceResponse replayed =
        searchPathService.appendPoints(batchRequest("idem-path-batch-db-replay"));

    assertThat(replayed).isEqualTo(first);
    Integer pointCount =
        jdbcTemplate.queryForObject(
            """
            SELECT ST_NumPoints(geometry)
            FROM search_path
            WHERE id = ?::uuid
            """,
            Integer.class,
            PATH_ID.toString());
    assertThat(pointCount).isEqualTo(8);
    assertThat(idempotencyStatus("idem-path-batch-db-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("segment correction idempotency replay does not increment segment twice")
  void segment_correction_idempotency_replay_does_not_increment_twice() {
    SearchPathPointsAppendServiceResponse batch =
        searchPathService.appendPoints(batchRequest("idem-path-batch-for-correction"));
    String segmentId = batch.getSegments().get(0).id();
    SearchPathSegmentCorrectionServiceRequest request =
        segmentCorrectionRequest(segmentId, "idem-path-segment-db-replay");

    SearchPathSegmentCorrectionServiceResponse first = searchPathService.correctSegment(request);
    SearchPathSegmentCorrectionServiceResponse replayed = searchPathService.correctSegment(request);

    assertThat(replayed).isEqualTo(first);
    Long segmentVersion =
        jdbcTemplate.queryForObject(
            """
            SELECT version
            FROM search_path_segment
            WHERE id = ?::uuid
            """,
            Long.class,
            segmentId);
    assertThat(segmentVersion).isEqualTo(2L);
    assertThat(idempotencyStatus("idem-path-segment-db-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("batch append persists path geometry, version, and UUID segment rows")
  void batch_append_persists_path_and_segments() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(batchRequest("idem-path-persist"));

    Map<String, Object> pathRow =
        jdbcTemplate.queryForMap(
            """
            SELECT sp.id,
                   sp.duty_shift_id,
                   sp.status,
                   sp.version,
                   ST_SRID(sp.geometry) AS srid,
                   ST_NumPoints(sp.geometry) AS point_count
            FROM search_path sp
            WHERE sp.id = ?::uuid
            """,
            PATH_ID.toString());

    assertThat(response.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("id")).isEqualTo(PATH_ID);
    assertThat(pathRow.get("duty_shift_id")).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("status")).isEqualTo("RECORDING");
    assertThat(pathRow.get("version")).isEqualTo(response.getVersion());
    assertThat(pathRow.get("srid")).isEqualTo(4326);
    assertThat(pathRow.get("point_count")).isEqualTo(8);

    List<Map<String, Object>> segments =
        jdbcTemplate.queryForList(
            """
            SELECT id,
                   movement_type,
                   movement_type_source,
                   ST_SRID(geometry) AS srid,
                   ST_NumPoints(geometry) AS point_count,
                   version
            FROM search_path_segment
            WHERE search_path_id = ?::uuid
            ORDER BY started_at, id
            """,
            PATH_ID.toString());

    assertThat(segments).hasSize(2);
    assertThat(segments)
        .extracting(row -> row.get("movement_type"))
        .containsExactly("VEHICLE", "FOOT");
    assertThat(segments)
        .allSatisfy(
            row -> {
              assertThat(row.get("id")).isInstanceOf(UUID.class);
              assertThat(row.get("movement_type_source")).isEqualTo("AUTO");
              assertThat(row.get("srid")).isEqualTo(4326);
              assertThat(row.get("version")).isEqualTo(1L);
            });
  }

  @Test
  @DisplayName("segment correction persists MANUAL movement source and increments segment version")
  void segment_correction_persists_manual_update() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(batchRequest("idem-path-segment-update"));
    SearchPathSegment target = response.getSegments().get(0);

    SearchPathSegmentCorrectionServiceResponse corrected =
        searchPathService.correctSegment(
            segmentCorrectionRequest(target.id(), "idem-path-segment-persist"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT movement_type,
                   movement_type_source,
                   corrected_by_account_id,
                   corrected_at,
                   version
            FROM search_path_segment
            WHERE search_path_id = ?::uuid
              AND movement_type_source = 'MANUAL'
            """,
            PATH_ID.toString());

    assertThat(corrected.getMovementType()).isEqualTo(MovementType.FOOT);
    assertThat(row.get("movement_type")).isEqualTo("FOOT");
    assertThat(row.get("movement_type_source")).isEqualTo("MANUAL");
    assertThat(row.get("corrected_by_account_id")).isEqualTo(CORRECTED_BY_ACCOUNT_ID);
    assertThat(row.get("corrected_at")).isNotNull();
    assertThat(row.get("version")).isEqualTo(2L);
  }

  @Test
  @DisplayName("single-point UNKNOWN segment persists as LineString and keeps query indexes")
  void single_point_segment_persists_and_reconstructs_indexes() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(singlePointSegmentRequest());

    assertThat(response.getSegments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.UNKNOWN, MovementType.VEHICLE);
    assertThat(response.getSegments().get(1).startIndex()).isEqualTo(3);
    assertThat(response.getSegments().get(1).endIndex()).isEqualTo(3);

    List<Map<String, Object>> segmentRows =
        jdbcTemplate.queryForList(
            """
            SELECT movement_type,
                   ST_NumPoints(geometry) AS point_count
            FROM search_path_segment
            WHERE search_path_id = ?::uuid
            ORDER BY started_at, id
            """,
            PATH_ID.toString());

    assertThat(segmentRows).hasSize(3);
    assertThat(segmentRows.get(1).get("movement_type")).isEqualTo("UNKNOWN");
    assertThat(segmentRows.get(1).get("point_count")).isEqualTo(2);

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);
    assertThat(queried.getSegments())
        .extracting(SearchPathQuerySegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE, MovementType.UNKNOWN, MovementType.VEHICLE);
    assertThat(queried.getSegments().get(1).getGeometry())
        .containsExactly(List.of(126.91485, 35.16254));
  }

  @Test
  @DisplayName("low-quality excluded point remains in query after DB reload")
  void excluded_point_persists_and_reloads_for_query() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(lowQualityPointRequest());

    assertThat(response.getAcceptedPointCount()).isEqualTo(2);
    assertThat(response.getExcludedPointCount()).isEqualTo(1);
    assertThat(response.getExcludedPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.pointId()).isEqualTo("gps-precinct-low-accuracy");
              assertThat(point.reason()).isEqualTo("low_accuracy");
            });
    Map<String, Object> excludedRow =
        jdbcTemplate.queryForMap(
            """
            SELECT point_id,
                   reason,
                   client_ts
            FROM search_path_excluded_point
            WHERE search_path_id = ?::uuid
            """,
            PATH_ID.toString());
    assertThat(excludedRow.get("point_id")).isEqualTo("gps-precinct-low-accuracy");
    assertThat(excludedRow.get("reason")).isEqualTo("low_accuracy");

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);

    assertThat(queried.getGeometry()).hasSize(2);
    assertThat(queried.getExcludedPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.pointId()).isEqualTo("gps-precinct-low-accuracy");
              assertThat(point.reason()).isEqualTo("low_accuracy");
              assertThat(point.clientTs())
                  .isEqualTo(OffsetDateTime.parse("2026-04-28T09:00:05+09:00"));
            });
  }

  @Test
  @DisplayName("next batch after DB reload preserves existing movement segments")
  void append_after_reload_keeps_existing_segments() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-first-batch"));

    SearchPathPointsAppendServiceResponse second =
        searchPathService.appendPoints(nextVehicleBatchRequest());

    assertThat(first.getSegments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT);
    assertThat(second.getSegments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT, MovementType.VEHICLE);
    assertThat(second.getSegments().get(0).id()).isEqualTo(first.getSegments().get(0).id());
    assertThat(second.getSegments().get(1).id()).isEqualTo(first.getSegments().get(1).id());

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);
    assertThat(queried.getGeometry()).hasSize(11);
    assertThat(queried.getSegments())
        .extracting(SearchPathQuerySegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT, MovementType.VEHICLE);
    assertThat(queried.getSegments().get(0).getGeometry()).hasSize(4);
    assertThat(queried.getSegments().get(1).getGeometry()).hasSize(4);
    assertThat(queried.getSegments().get(2).getGeometry()).hasSize(3);
  }

  private SearchPathQueryServiceResponse queryPaths() {
    return searchPathService.query(
        SearchPathQueryServiceRequest.builder()
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .policePhoneId(POLICE_PHONE_ID)
            .build());
  }

  private SearchPathSegmentCorrectionServiceRequest segmentCorrectionRequest(
      String segmentId, String idempotencyKey) {
    return SearchPathSegmentCorrectionServiceRequest.builder()
        .searchPathSegmentId(segmentId)
        .movementType(MovementType.FOOT)
        .reason("manual correction")
        .correctedByAccountId(CORRECTED_BY_ACCOUNT_ID)
        .idempotencyKey(idempotencyKey)
        .build();
  }

  private SearchPathPointsAppendServiceRequest batchRequest(String idempotencyKey) {
    return pointsAppendRequest(
        idempotencyKey,
        List.of(
            point("gps-precinct-001", "126.913000", "35.162000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-002", "126.913650", "35.162180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-003", "126.914300", "35.162360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-004", "126.914850", "35.162540", 9.8, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-005", "126.915000", "35.162700", 1.6, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-006", "126.915080", "35.162880", 1.3, "2026-04-28T09:00:25+09:00"),
            point("gps-precinct-007", "126.915160", "35.163050", 1.1, "2026-04-28T09:00:30+09:00"),
            point(
                "gps-precinct-008", "126.915250", "35.163120", 1.4, "2026-04-28T09:00:35+09:00")));
  }

  private int rowCount(String tableName) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    return count == null ? 0 : count;
  }

  private String idempotencyStatus(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        """
        SELECT idempotency_status
        FROM idempotency_record
        WHERE idempotency_key = ?
        """,
        String.class,
        idempotencyKey);
  }

  private SearchPathPointsAppendServiceRequest singlePointSegmentRequest() {
    return pointsAppendRequest(
        "idem-path-single-point-segment",
        List.of(
            point("gps-precinct-101", "126.913000", "35.162000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-102", "126.913650", "35.162180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-103", "126.914300", "35.162360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-104", "126.914850", "35.162540", 1.6, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-105", "126.915000", "35.162700", 13.0, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-106", "126.915080", "35.162880", 12.5, "2026-04-28T09:00:25+09:00"),
            point(
                "gps-precinct-107", "126.915160", "35.163050", 11.8, "2026-04-28T09:00:30+09:00")));
  }

  private SearchPathPointsAppendServiceRequest nextVehicleBatchRequest() {
    return pointsAppendRequest(
        "idem-path-next-vehicle-batch",
        List.of(
            point("gps-precinct-201", "126.915400", "35.163400", 13.2, "2026-04-28T09:01:00+09:00"),
            point("gps-precinct-202", "126.916000", "35.163600", 12.9, "2026-04-28T09:01:05+09:00"),
            point(
                "gps-precinct-203", "126.916600", "35.163800", 12.1, "2026-04-28T09:01:10+09:00")));
  }

  private SearchPathPointsAppendServiceRequest lowQualityPointRequest() {
    return pointsAppendRequest(
        "idem-path-low-quality-point",
        List.of(
            point(
                "gps-precinct-good-001",
                "126.913000",
                "35.162000",
                1.4,
                "2026-04-28T09:00:00+09:00"),
            point(
                "gps-precinct-low-accuracy",
                "126.913050",
                "35.162020",
                1.3,
                "2026-04-28T09:00:05+09:00",
                80),
            point(
                "gps-precinct-good-002",
                "126.913100",
                "35.162040",
                1.2,
                "2026-04-28T09:00:10+09:00")));
  }

  private SearchPathPointsAppendServiceRequest pointsAppendRequest(
      String idempotencyKey, List<SearchPathPointServiceRequest> points) {
    return SearchPathPointsAppendServiceRequest.builder()
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .pathId(PATH_ID)
        .points(points)
        .clockOffsetMs(0L)
        .policePhoneId(POLICE_PHONE_ID)
        .accountId(ACCOUNT_ID)
        .idempotencyKey(idempotencyKey)
        .build();
  }

  private SearchPathPointServiceRequest point(
      String pointId, String lon, String lat, double speed, String clientTs) {
    return point(pointId, lon, lat, speed, clientTs, 5);
  }

  private SearchPathPointServiceRequest point(
      String pointId, String lon, String lat, double speed, String clientTs, int accuracyM) {
    return SearchPathPointServiceRequest.builder()
        .pointId(pointId)
        .lon(new BigDecimal(lon))
        .lat(new BigDecimal(lat))
        .speedMps(BigDecimal.valueOf(speed))
        .horizontalAccuracyM(accuracyM)
        .clientTs(OffsetDateTime.parse(clientTs))
        .build();
  }
}
