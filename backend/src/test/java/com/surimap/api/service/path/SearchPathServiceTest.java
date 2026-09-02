package com.surimap.api.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.api.service.path.request.SearchPathPointServiceRequest;
import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.api.service.path.response.SearchPathPointsAppendServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import com.surimap.api.service.path.response.SearchPathSegmentCorrectionServiceResponse;
import com.surimap.api.service.path.response.SearchPathSegmentServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
import java.time.Instant;
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
  private static final UUID LEGACY_SEGMENT_ID =
      UUID.fromString("70000000-0000-0000-0000-000000002621");
  private static final UUID CORRECTED_BY_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  @Autowired private SearchPathService searchPathService;

  @Test
  @DisplayName("batch append stages PATH_APPENDED EventHub job")
  void batch_append_stages_event_dispatch_job() {
    SearchPathPointsAppendServiceResponse appended =
        searchPathService.appendPoints(batchRequest("idem-path-event-append"));

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
                   jsonb_exists(payload, 'policePhoneId') AS has_police_phone_id,
                   payload ->> 'accountId' AS payload_account_id,
                   (payload ->> 'version')::bigint AS payload_version,
                   (payload ->> 'sequence')::bigint AS payload_sequence
            FROM event_dispatch_job
            WHERE event_type = 'PATH_APPENDED'
              AND source_entity_id = ?::uuid
            """,
            PATH_ID.toString());

    assertThat(eventRow.get("event_type")).isEqualTo("PATH_APPENDED");
    assertThat(eventRow.get("source_entity_type")).isEqualTo("search_path");
    assertThat(eventRow.get("source_entity_id")).isEqualTo(PATH_ID);
    assertThat(eventRow.get("dispatch_status")).isEqualTo("PENDING");
    assertThat(eventRow.get("payload_id")).isEqualTo(PATH_ID.toString());
    assertThat(eventRow.get("payload_incident_id")).isEqualTo(INCIDENT_ID.toString());
    assertThat(eventRow.get("payload_op_id")).isEqualTo(OP_ID.toString());
    assertThat(eventRow.get("has_police_phone_id")).isEqualTo(false);
    assertThat(eventRow.get("payload_account_id")).isEqualTo(ACCOUNT_ID.toString());
    assertThat(eventRow.get("payload_version")).isEqualTo(appended.getVersion());
    assertThat(eventRow.get("payload_sequence")).isEqualTo(appended.getVersion());
  }

  @Test
  @DisplayName("manual segment correction stages SEARCH_PATH_SEGMENT_UPDATED EventHub job")
  void segment_correction_stages_event_dispatch_job() {
    SearchPathPointsAppendServiceResponse batch =
        searchPathService.appendPoints(batchRequest("idem-path-event-segment-batch"));
    String segmentId = batch.getSegments().get(0).getId().toString();

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
                   jsonb_exists(payload, 'policePhoneId') AS has_police_phone_id,
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
    assertThat(eventRow.get("has_police_phone_id")).isEqualTo(false);
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

    assertThat(replayed).usingRecursiveComparison().isEqualTo(first);
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
    assertThat(rowCount("search_path_gps_point")).isEqualTo(8);
    assertThat(idempotencyStatus("idem-path-batch-db-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("batch append rejects a changed request with the same idempotency key")
  void batch_append_rejects_same_idempotency_key_with_changed_request() {
    String idempotencyKey = "idem-path-batch-mismatch";
    searchPathService.appendPoints(batchRequest(idempotencyKey));
    SearchPathPointsAppendServiceRequest changed =
        batchRequest(idempotencyKey).toBuilder().clockOffsetMs(1L).build();

    assertThatThrownBy(() -> searchPathService.appendPoints(changed))
        .isInstanceOf(IdempotencyMismatchException.class);

    Map<String, Object> pathRow =
        jdbcTemplate.queryForMap(
            """
            SELECT version,
                   ST_NumPoints(geometry) AS point_count
            FROM search_path
            WHERE id = ?::uuid
            """,
            PATH_ID.toString());
    assertThat(pathRow.get("version")).isEqualTo(2L);
    assertThat(pathRow.get("point_count")).isEqualTo(8);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(1);
  }

  @Test
  @DisplayName("segment correction idempotency replay does not increment segment twice")
  void segment_correction_idempotency_replay_does_not_increment_twice() {
    SearchPathPointsAppendServiceResponse batch =
        searchPathService.appendPoints(batchRequest("idem-path-batch-for-correction"));
    String segmentId = batch.getSegments().get(0).getId().toString();
    SearchPathSegmentCorrectionServiceRequest request =
        segmentCorrectionRequest(segmentId, "idem-path-segment-db-replay");

    SearchPathSegmentCorrectionServiceResponse first = searchPathService.correctSegment(request);
    SearchPathSegmentCorrectionServiceResponse replayed = searchPathService.correctSegment(request);

    assertThat(replayed).usingRecursiveComparison().isEqualTo(first);
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
  @DisplayName("segment correction rejects a changed request with the same idempotency key")
  void segment_correction_rejects_same_idempotency_key_with_changed_request() {
    SearchPathPointsAppendServiceResponse batch =
        searchPathService.appendPoints(batchRequest("idem-path-batch-for-mismatch"));
    String segmentId = batch.getSegments().get(0).getId().toString();
    String idempotencyKey = "idem-path-segment-mismatch";
    SearchPathSegmentCorrectionServiceRequest request =
        segmentCorrectionRequest(segmentId, idempotencyKey);
    searchPathService.correctSegment(request);
    SearchPathSegmentCorrectionServiceRequest changed =
        request.toBuilder().movementType(MovementType.VEHICLE).build();

    assertThatThrownBy(() -> searchPathService.correctSegment(changed))
        .isInstanceOf(IdempotencyMismatchException.class);

    Map<String, Object> segmentRow =
        jdbcTemplate.queryForMap(
            """
            SELECT movement_type,
                   version
            FROM search_path_segment
            WHERE id = ?::uuid
            """,
            segmentId);
    assertThat(segmentRow.get("movement_type")).isEqualTo("FOOT");
    assertThat(segmentRow.get("version")).isEqualTo(2L);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(2);
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
    SearchPathSegmentServiceResponse target = response.getSegments().get(0);

    SearchPathSegmentCorrectionServiceResponse corrected =
        searchPathService.correctSegment(
            segmentCorrectionRequest(target.getId().toString(), "idem-path-segment-persist"));

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
  @DisplayName("segment correction leaves other segment rows unchanged")
  void segment_correction_keeps_other_segment_rows_unchanged() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(batchRequest("idem-path-segment-isolation"));
    String correctedSegmentId = response.getSegments().get(0).getId();
    jdbcTemplate.update(
        """
        UPDATE search_path_segment
        SET updated_at = '2000-01-01T00:00:00Z'::timestamptz
        WHERE search_path_id = ?::uuid
        """,
        PATH_ID.toString());

    searchPathService.correctSegment(
        segmentCorrectionRequest(correctedSegmentId, "idem-path-segment-isolation-correction"));

    Integer unchangedSegmentCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_path_segment
            WHERE search_path_id = ?::uuid
              AND updated_at = '2000-01-01T00:00:00Z'::timestamptz
            """,
            Integer.class,
            PATH_ID.toString());
    assertThat(unchangedSegmentCount).isEqualTo(1);
  }

  @Test
  @DisplayName("single-point UNKNOWN segment persists as LineString and keeps query indexes")
  void single_point_segment_persists_and_reconstructs_indexes() {
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(singlePointSegmentRequest());

    assertThat(response.getSegments())
        .extracting(SearchPathSegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE, MovementType.UNKNOWN, MovementType.VEHICLE);
    assertThat(response.getSegments().get(1).getStartIndex()).isEqualTo(3);
    assertThat(response.getSegments().get(1).getEndIndex()).isEqualTo(3);

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
              assertThat(point.getPointId()).isEqualTo("gps-precinct-low-accuracy");
              assertThat(point.getReason()).isEqualTo("low_accuracy");
            });
    Map<String, Object> excludedRow =
        jdbcTemplate.queryForMap(
            """
            SELECT point_id,
                   reason,
                   client_ts,
                   lon,
                   lat,
                   speed_mps,
                   horizontal_accuracy_m,
                   location_provider,
                   elapsed_realtime_nanos
            FROM search_path_excluded_point
            WHERE search_path_id = ?::uuid
            """,
            PATH_ID.toString());
    assertThat(excludedRow.get("point_id")).isEqualTo("gps-precinct-low-accuracy");
    assertThat(excludedRow.get("reason")).isEqualTo("low_accuracy");
    assertThat(excludedRow.get("lon").toString()).isEqualTo("126.913050");
    assertThat(excludedRow.get("lat").toString()).isEqualTo("35.162020");
    assertThat(excludedRow.get("speed_mps").toString()).isEqualTo("1.3");
    assertThat(excludedRow.get("horizontal_accuracy_m")).isEqualTo(80);
    assertThat(excludedRow.get("location_provider")).isEqualTo("network");
    assertThat(excludedRow.get("elapsed_realtime_nanos")).isEqualTo(20_000_000_000L);

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);

    assertThat(queried.getGeometry()).hasSize(2);
    assertThat(queried.getExcludedPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.getPointId()).isEqualTo("gps-precinct-low-accuracy");
              assertThat(point.getReason()).isEqualTo("low_accuracy");
              assertThat(point.getClientTs())
                  .isEqualTo(OffsetDateTime.parse("2026-04-28T09:00:04+09:00"));
            });
  }

  @Test
  @DisplayName("DB에서 다시 읽어도 앱이 보낸 GPS 측정값을 유지한다")
  void gps_measurements_remain_unchanged_after_db_reload() {
    SearchPathPointsAppendServiceRequest request = batchRequest("idem-path-gps-measurement-reload");
    SearchPathPointServiceRequest original = request.getPoints().get(0);

    searchPathService.appendPoints(request);

    var reloaded =
        searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getPoints().get(0);
    assertThat(reloaded.getPointId()).isEqualTo(original.getPointId());
    assertThat(reloaded.getClientTs()).isEqualTo(original.getClientTs());
    assertThat(reloaded.getLon()).isEqualByComparingTo(original.getLon());
    assertThat(reloaded.getLat()).isEqualByComparingTo(original.getLat());
    assertThat(reloaded.getSpeedMps()).isEqualByComparingTo(original.getSpeedMps());
    assertThat(reloaded.getHorizontalAccuracyM()).isEqualTo(original.getHorizontalAccuracyM());
    assertThat(reloaded.getLocationProvider()).isEqualTo(original.getLocationProvider());
    assertThat(reloaded.getElapsedRealtimeNanos()).isEqualTo(original.getElapsedRealtimeNanos());
  }

  @Test
  @DisplayName("GPS row가 없는 기존 경로는 저장된 path와 segment geometry를 조회한다")
  void legacy_path_without_gps_points_uses_persisted_geometry_for_query() {
    insertLegacyPath();

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);

    assertThat(queried.getGeometry())
        .containsExactly(List.of(126.91, 35.16), List.of(126.92, 35.17));
    assertThat(queried.getSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.getGeometry())
                  .containsExactly(List.of(126.91, 35.16), List.of(126.915, 35.165));
              assertThat(segment.getStartedAt())
                  .isEqualTo(OffsetDateTime.parse("2026-04-28T00:00:00Z"));
              assertThat(segment.getEndedAt())
                  .isEqualTo(OffsetDateTime.parse("2026-04-28T00:01:00Z"));
            });
    assertThat(searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getPoints())
        .isEmpty();
  }

  @Test
  @DisplayName("GPS row가 없는 기존 기록 경로에는 새 point를 append하지 않는다")
  void legacy_recording_path_without_gps_points_rejects_append_without_changing_geometry() {
    insertLegacyPath();

    assertThatThrownBy(
            () -> searchPathService.appendPoints(batchRequest("idem-path-legacy-append")))
        .isInstanceOfSatisfying(
            SearchPathApiException.class,
            exception -> assertThat(exception.getMessage()).isEqualTo("write_conflict"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT ST_AsText(geometry) FROM search_path WHERE id = ?::uuid",
                String.class,
                PATH_ID.toString()))
        .isEqualTo("LINESTRING(126.91 35.16,126.92 35.17)");
    assertThat(rowCount("search_path_gps_point")).isZero();
  }

  @Test
  @DisplayName("GPS row가 없는 기존 segment 보정은 저장 geometry와 시간을 유지한다")
  void legacy_segment_correction_preserves_persisted_geometry_and_time() {
    insertLegacyPath();

    searchPathService.correctSegment(
        segmentCorrectionRequest(LEGACY_SEGMENT_ID.toString(), "idem-path-legacy-correction")
            .toBuilder()
            .movementType(MovementType.VEHICLE)
            .build());

    Map<String, Object> pathRow =
        jdbcTemplate.queryForMap(
            """
            SELECT ST_AsText(geometry) AS geometry, version,
                   EXTRACT(EPOCH FROM started_at)::bigint AS started_epoch
            FROM search_path
            WHERE id = ?::uuid
            """,
            PATH_ID.toString());
    Map<String, Object> segmentRow =
        jdbcTemplate.queryForMap(
            """
            SELECT movement_type, movement_type_source, version,
                   ST_AsText(geometry) AS geometry,
                   EXTRACT(EPOCH FROM started_at)::bigint AS started_epoch,
                   EXTRACT(EPOCH FROM ended_at)::bigint AS ended_epoch
            FROM search_path_segment
            WHERE id = ?::uuid
            """,
            LEGACY_SEGMENT_ID.toString());

    assertThat(pathRow.get("geometry")).isEqualTo("LINESTRING(126.91 35.16,126.92 35.17)");
    assertThat(pathRow.get("version")).isEqualTo(2L);
    assertThat(pathRow.get("started_epoch"))
        .isEqualTo(OffsetDateTime.parse("2026-04-28T00:00:00Z").toEpochSecond());
    assertThat(segmentRow.get("movement_type")).isEqualTo("VEHICLE");
    assertThat(segmentRow.get("movement_type_source")).isEqualTo("MANUAL");
    assertThat(segmentRow.get("version")).isEqualTo(2L);
    assertThat(segmentRow.get("geometry")).isEqualTo("LINESTRING(126.91 35.16,126.915 35.165)");
    assertThat(segmentRow.get("started_epoch"))
        .isEqualTo(OffsetDateTime.parse("2026-04-28T00:00:00Z").toEpochSecond());
    assertThat(segmentRow.get("ended_epoch"))
        .isEqualTo(OffsetDateTime.parse("2026-04-28T00:01:00Z").toEpochSecond());
    assertThat(searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getPoints())
        .isEmpty();
  }

  @Test
  @DisplayName("next batch returns new segments while the path query keeps all segments")
  void append_after_reload_returns_only_new_segments() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-first-batch"));

    SearchPathPointsAppendServiceResponse second =
        searchPathService.appendPoints(nextVehicleBatchRequest());

    assertThat(first.getSegments())
        .extracting(SearchPathSegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT);
    assertThat(second.getSegments())
        .extracting(SearchPathSegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE);

    SearchPathQueryRowServiceResponse queried = queryPaths().getPaths().get(0);
    assertThat(queried.getGeometry()).hasSize(11);
    assertThat(queried.getSegments())
        .extracting(SearchPathQuerySegmentServiceResponse::getMovementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT, MovementType.VEHICLE);
    assertThat(queried.getSegments().get(0).getGeometry()).hasSize(4);
    assertThat(queried.getSegments().get(1).getGeometry()).hasSize(4);
    assertThat(queried.getSegments().get(2).getGeometry()).hasSize(3);
  }

  @Test
  @DisplayName("next batch leaves existing segment rows unchanged")
  void append_keeps_existing_segment_rows_unchanged() {
    searchPathService.appendPoints(batchRequest("idem-path-existing-segments"));
    jdbcTemplate.update(
        """
        UPDATE search_path_segment
        SET updated_at = '2000-01-01T00:00:00Z'::timestamptz
        WHERE search_path_id = ?::uuid
        """,
        PATH_ID.toString());

    searchPathService.appendPoints(nextVehicleBatchRequest());

    Integer unchangedSegmentCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM search_path_segment
            WHERE search_path_id = ?::uuid
              AND updated_at = '2000-01-01T00:00:00Z'::timestamptz
            """,
            Integer.class,
            PATH_ID.toString());
    assertThat(unchangedSegmentCount).isEqualTo(2);
  }

  @Test
  @DisplayName("next batch leaves existing excluded point rows unchanged")
  void append_keeps_existing_excluded_point_rows_unchanged() {
    searchPathService.appendPoints(lowQualityPointRequest());
    jdbcTemplate.update(
        """
        UPDATE search_path_excluded_point
        SET updated_at = '2000-01-01T00:00:00Z'::timestamptz
        WHERE search_path_id = ?::uuid
        """,
        PATH_ID.toString());

    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(nextVehicleBatchRequest());

    Instant updatedAt =
        jdbcTemplate.queryForObject(
            """
            SELECT updated_at
            FROM search_path_excluded_point
            WHERE search_path_id = ?::uuid
            """,
            Instant.class,
            PATH_ID.toString());
    assertThat(response.getExcludedPoints()).isEmpty();
    assertThat(updatedAt).isEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
  }

  private SearchPathQueryServiceResponse queryPaths() {
    return searchPathService.query(
        SearchPathQueryServiceRequest.builder().incidentId(INCIDENT_ID).opId(OP_ID).build());
  }

  private void insertLegacyPath() {
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
            id, duty_shift_id, account_id, status, started_at, geometry,
            version, created_at, updated_at
        ) VALUES (
            ?::uuid, ?::uuid, ?::uuid, 'RECORDING', ?::timestamptz,
            ST_GeomFromText('LINESTRING(126.91 35.16,126.92 35.17)', 4326),
            1, ?::timestamptz, ?::timestamptz
        )
        """,
        PATH_ID.toString(),
        DUTY_SHIFT_ID.toString(),
        ACCOUNT_ID.toString(),
        "2026-04-28T00:00:00Z",
        "2026-04-28T00:00:00Z",
        "2026-04-28T00:00:00Z");
    jdbcTemplate.update(
        """
        INSERT INTO search_path_segment (
            id, search_path_id, movement_type, movement_type_source, geometry,
            started_at, ended_at, version, created_at, updated_at
        ) VALUES (
            ?::uuid, ?::uuid,
            'FOOT', 'AUTO',
            ST_GeomFromText('LINESTRING(126.91 35.16,126.915 35.165)', 4326),
            '2026-04-28T00:00:00Z'::timestamptz,
            '2026-04-28T00:01:00Z'::timestamptz,
            1, NOW(), NOW()
        )
        """,
        LEGACY_SEGMENT_ID.toString(),
        PATH_ID.toString());
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
            point(
                "gps-precinct-001",
                "126.913000",
                "35.162000",
                13.5,
                "2026-04-28T09:00:00+09:00",
                5,
                "gps",
                10_000_000_000L),
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
                "gps-precinct-good-002",
                "126.913100",
                "35.162040",
                1.2,
                "2026-04-28T09:00:05+09:00",
                5,
                "gps",
                15_000_000_000L),
            point(
                "gps-precinct-low-accuracy",
                "126.913050",
                "35.162020",
                1.3,
                "2026-04-28T09:00:04+09:00",
                80,
                "network",
                20_000_000_000L)));
  }

  private SearchPathPointsAppendServiceRequest pointsAppendRequest(
      String idempotencyKey, List<SearchPathPointServiceRequest> points) {
    return SearchPathPointsAppendServiceRequest.builder()
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .pathId(PATH_ID)
        .points(points)
        .clockOffsetMs(0L)
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
    return point(pointId, lon, lat, speed, clientTs, accuracyM, null, null);
  }

  private SearchPathPointServiceRequest point(
      String pointId,
      String lon,
      String lat,
      double speed,
      String clientTs,
      int accuracyM,
      String locationProvider,
      Long elapsedRealtimeNanos) {
    return SearchPathPointServiceRequest.builder()
        .pointId(pointId)
        .lon(new BigDecimal(lon))
        .lat(new BigDecimal(lat))
        .speedMps(BigDecimal.valueOf(speed))
        .horizontalAccuracyM(accuracyM)
        .clientTs(OffsetDateTime.parse(clientTs))
        .locationProvider(locationProvider)
        .elapsedRealtimeNanos(elapsedRealtimeNanos)
        .build();
  }
}
