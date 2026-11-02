package com.surimap.api.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
import com.surimap.app.service.path.AppSearchPathService;
import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.SearchPathStartServiceRequest;
import com.surimap.app.service.path.request.SearchPathStatusUpdateServiceRequest;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Sql(scripts = "/sql/path/search-path-context.sql")
@Import(SearchPathServiceTest.QueryPauseConfig.class)
class SearchPathServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID PAST_OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002622");
  private static final UUID PAST_DUTY_SHIFT_ID =
      UUID.fromString("60000000-0000-0000-0000-000000002622");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID POLICE_PHONE_ID = SearchPathFixtures.POLICE_PHONE_ID;
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID LEGACY_SEGMENT_ID =
      UUID.fromString("70000000-0000-0000-0000-000000002621");
  private static final UUID CORRECTED_BY_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  private static final OffsetDateTime TRACK_STARTED_AT =
      OffsetDateTime.parse("2026-04-28T09:00:00+09:00");
  private static final double FOOT_SPEED = 1.2;
  private static final double VEHICLE_SPEED = 12.0;
  @Autowired private SearchPathService searchPathService;
  @Autowired private AppSearchPathService appSearchPathService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private PathQueryPause queryPause;

  @Test
  @DisplayName("좌표 추가와 구간 보정이 롤백되면 순번·변경 버전도 함께 되돌린다")
  void rolled_back_append_and_correction_preserve_segment_progress() {
    // given: 기존 두 구간이 경로 버전 2에서 저장되어 있다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("progress-before-rollback"));

    // when: 추가·보정 저장 후 바깥 트랜잭션의 실패로 전체 작업을 되돌린다.
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          searchPathService.appendPoints(nextVehicleBatchRequest());
                          searchPathService.correctSegment(
                              segmentCorrectionRequest(
                                  first.getSegments().get(0).getId(),
                                  "progress-rollback-correction"));
                          throw new IllegalStateException("rollback");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("rollback");

    // then: 새 구간이 남지 않고 기존 구간·경로의 버전도 변경 전과 같다.
    List<SearchPathSegment> segments =
        searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments();
    assertThat(segments)
        .extracting(SearchPathSegment::getLastChangedPathVersion)
        .containsExactly(2L, 2L);
    assertThat(segments).extracting(SearchPathSegment::getVersion).containsExactly(1L, 1L);
    assertThat(segments).extracting(SearchPathSegment::getStartIndex).containsExactly(0, 4);
    assertThat(segments).extracting(SearchPathSegment::getEndIndex).containsExactly(3, 7);
    assertThat(queryPaths().getPaths().get(0).getVersion()).isEqualTo(2L);
    assertThat(queryPaths().getPaths().get(0).getGeometry()).hasSize(8);
  }

  @Test
  @DisplayName("좌표와 측정 시각이 같아도 별도로 수집한 두 점의 저장 순번을 유지한다")
  void identical_coordinates_and_timestamps_keep_both_stored_point_orders() {
    // given: 좌표·측정 시각은 같지만 서로 다른 수집 기록이다.
    SearchPathPointsAppendServiceRequest request =
        pointsAppendRequest(
            "same-location",
            List.of(
                point("first", "126.913000", "35.162000", 1.2, "2026-04-28T09:00:00+09:00"),
                point("second", "126.913000", "35.162000", 1.2, "2026-04-28T09:00:00+09:00")));

    // when: 묶음을 저장한 후 다시 조회한다.
    searchPathService.appendPoints(request);
    List<SearchPathSegment> segments =
        searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments();

    // then: 도형이 같다는 이유로 단일 좌표 구간으로 축소하지 않는다.
    assertThat(segments)
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.getStartIndex()).isZero();
              assertThat(segment.getEndIndex()).isEqualTo(1);
              assertThat(segment.getStartPointId()).isEqualTo("first");
              assertThat(segment.getEndPointId()).isEqualTo("second");
            });
  }

  @Test
  @DisplayName("좌표를 추가하면 새 구간에만 해당 경로 버전을 기록하고 재전송해도 유지한다")
  void append_records_path_version_only_on_new_segments_and_preserves_it_on_replay() {
    // given: 버전 2에서 생성한 두 구간이 있는 경로다.
    searchPathService.appendPoints(batchRequest("idem-path-progress-first"));

    // when: 다음 묶음을 추가한 뒤 같은 요청을 재전송한다.
    searchPathService.appendPoints(nextVehicleBatchRequest());
    searchPathService.appendPoints(nextVehicleBatchRequest());

    // then: 기존 구간의 추적값은 2이고 새 구간만 3이며 재전송으로 증가하지 않는다.
    assertThat(searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments())
        .extracting(SearchPathSegment::getLastChangedPathVersion)
        .containsExactly(2L, 2L, 3L);
    assertThat(queryPaths().getPaths().get(0).getVersion()).isEqualTo(3L);
  }

  @Test
  @DisplayName("구간을 보정하면 대상의 변경 경로 버전만 갱신하고 GPS 범위와 다른 구간은 유지한다")
  void correction_updates_only_target_path_version_and_keeps_point_ranges() {
    // given: 서로 다른 경로 버전에서 생성한 세 구간이다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-progress-correction"));
    searchPathService.appendPoints(nextVehicleBatchRequest());
    SearchPathSegmentCorrectionServiceRequest correction =
        segmentCorrectionRequest(first.getSegments().get(0).getId(), "idem-progress-correction");

    // when: 첫 구간을 보정하고 같은 요청을 재전송한다.
    searchPathService.correctSegment(correction);
    searchPathService.correctSegment(correction);

    // then: 보정 대상만 경로 버전 4를 기록하고 자체 버전은 2로 증가한다.
    List<SearchPathSegment> segments =
        searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments();
    assertThat(segments)
        .extracting(SearchPathSegment::getLastChangedPathVersion)
        .containsExactly(4L, 2L, 3L);
    assertThat(segments).extracting(SearchPathSegment::getVersion).containsExactly(2L, 1L, 1L);
    assertThat(segments).extracting(SearchPathSegment::getStartIndex).containsExactly(0, 4, 8);
    assertThat(segments).extracting(SearchPathSegment::getEndIndex).containsExactly(3, 7, 10);
    assertThat(queryPaths().getPaths().get(0).getVersion()).isEqualTo(4L);
  }

  @ParameterizedTest(name = "전체 조회={0}, 기존 트랜잭션={1}")
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  @DisplayName("경로 조회 중 좌표가 저장되어도 버전과 좌표·구간은 같은 저장 상태를 유지한다")
  void query_during_append_keeps_version_and_path_details_consistent(
      boolean queryAll, boolean existingTransaction) throws Exception {
    // given: 실제 서비스로 저장한 경로의 버전·좌표·구간을 기준값으로 보관한다.
    searchPathService.appendPoints(batchRequest("idem-path-snapshot-first"));
    Supplier<Object> query = queryAll ? () -> searchPathService.findAll() : this::queryPaths;
    Object before = query.get();
    TransactionTemplate read = new TransactionTemplate(transactionManager);
    read.setReadOnly(true);
    ExecutorService reader = Executors.newSingleThreadExecutor();
    queryPause.listRead = new CountDownLatch(1);
    queryPause.writeCommitted = new CountDownLatch(1);
    Object observed;
    try {
      // when: 실제 목록 SQL이 반환된 직후 다른 연결에서 다음 묶음을 저장하고 조회를 재개한다.
      Future<Object> pending =
          reader.submit(
              () -> {
                queryPause.readerThread = Thread.currentThread();
                return existingTransaction ? read.execute(status -> query.get()) : query.get();
              });
      assertThat(queryPause.listRead.await(15, TimeUnit.SECONDS)).as("목록 조회 완료").isTrue();
      SearchPathPointsAppendServiceResponse appended =
          searchPathService.appendPoints(nextVehicleBatchRequest());
      assertThat(appended.getAcceptedPointCount()).isEqualTo(3);
      queryPause.writeCommitted.countDown();
      observed = pending.get(15, TimeUnit.SECONDS);
    } finally {
      queryPause.writeCommitted.countDown();
      queryPause.readerThread = null;
      reader.shutdownNow();
      assertThat(reader.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
    }
    // then: 진행 중이던 조회는 기존 저장 상태, 이후 새 조회는 새 저장 상태를 반환한다.
    assertThat(observed)
        .usingRecursiveComparison()
        .withEqualsForType(
            (left, right) -> left.getSRID() == right.getSRID() && left.equalsExact(right),
            Geometry.class)
        .isEqualTo(before);
    SearchPathQueryRowServiceResponse latest = queryPaths().getPaths().get(0);
    assertThat(latest.getVersion()).isEqualTo(3L);
    assertThat(latest.getGeometry()).hasSize(11);
    assertThat(latest.getSegments()).hasSize(3);
  }

  @TestConfiguration
  static class QueryPauseConfig {
    @Bean
    PathQueryPause pathQueryPause() {
      return new PathQueryPause();
    }
  }

  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "query",
          args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
  static class PathQueryPause implements Interceptor {
    volatile Thread readerThread;
    CountDownLatch listRead;
    CountDownLatch writeCommitted;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      Object result = invocation.proceed();
      String statementId = ((MappedStatement) invocation.getArgs()[0]).getId();
      if (Thread.currentThread() == readerThread
          && (statementId.endsWith("SearchPathMapper.findPaths")
              || statementId.endsWith("SearchPathMapper.findAllPaths"))) {
        listRead.countDown();
        if (!writeCommitted.await(15, TimeUnit.SECONDS))
          throw new IllegalStateException("write_not_committed");
      }
      return result;
    }
  }

  @Test
  @DisplayName("batch append stages PATH_APPENDED EventPublisher job")
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
  @DisplayName("manual segment correction stages SEARCH_PATH_SEGMENT_UPDATED EventPublisher job")
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
  @DisplayName("같은 좌표 묶음을 재전송해도 GPS 좌표를 중복 저장하지 않는다")
  void batch_append_idempotency_replay_does_not_append_twice() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-batch-db-replay"));

    SearchPathPointsAppendServiceResponse replayed =
        searchPathService.appendPoints(batchRequest("idem-path-batch-db-replay"));

    assertThat(replayed).usingRecursiveComparison().isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT geometry IS NULL FROM search_path WHERE id = ?::uuid",
                Boolean.class,
                PATH_ID.toString()))
        .isTrue();
    assertThat(rowCount("search_path_gps_point")).isEqualTo(8);
    assertThat(idempotencyStatus("idem-path-batch-db-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("같은 멱등성 키로 변경된 요청이 오면 기존 좌표와 버전을 유지한다")
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
                   geometry IS NULL AS geometry_is_null
            FROM search_path
            WHERE id = ?::uuid
            """,
            PATH_ID.toString());
    assertThat(pathRow.get("version")).isEqualTo(2L);
    assertThat(pathRow.get("geometry_is_null")).isEqualTo(true);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(8);
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
  @DisplayName("좌표 묶음을 추가하면 경로 도형 대신 GPS 좌표와 새 구간을 저장한다")
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
                   sp.geometry IS NULL AS geometry_is_null
            FROM search_path sp
            WHERE sp.id = ?::uuid
            """,
            PATH_ID.toString());

    assertThat(response.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("id")).isEqualTo(PATH_ID);
    assertThat(pathRow.get("duty_shift_id")).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("status")).isEqualTo("RECORDING");
    assertThat(pathRow.get("version")).isEqualTo(response.getVersion());
    assertThat(pathRow.get("geometry_is_null")).isEqualTo(true);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(8);

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
  @DisplayName("구간을 보정하면 다른 구간의 수정 시각은 유지된다")
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
  @DisplayName("두 번째 좌표 묶음을 추가하면 응답에는 새 구간만, 경로 조회에는 누적 구간을 반환한다")
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
  @DisplayName("새 좌표 묶음을 추가하면 기존 구간의 수정 시각은 유지된다")
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
  @DisplayName("새 좌표 묶음을 추가하면 기존 제외 좌표의 수정 시각은 유지된다")
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

  @Test
  @DisplayName("근무가 종료된 계정이 좌표 묶음을 추가하면 police_phone_not_assigned로 거부하고 경로를 유지한다")
  void append_rejects_inactive_account_duty_shift() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-active-duty-shift"));
    jdbcTemplate.update(
        "UPDATE duty_shift SET status = 'ENDED' WHERE id = ?::uuid", DUTY_SHIFT_ID.toString());

    assertThatThrownBy(() -> searchPathService.appendPoints(nextVehicleBatchRequest()))
        .isInstanceOfSatisfying(
            SearchPathApiException.class,
            exception -> assertThat(exception.getMessage()).isEqualTo("police_phone_not_assigned"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version FROM search_path WHERE id = ?::uuid",
                Long.class,
                PATH_ID.toString()))
        .isEqualTo(first.getVersion());
    assertThat(rowCount("search_path_gps_point")).isEqualTo(8);
    assertThat(rowCount("search_path_segment")).isEqualTo(2);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(1);
  }

  @Test
  @DisplayName("요청 OP와 경로 OP가 다르면 op_mismatch로 거부하고 경로를 유지한다")
  void append_rejects_path_from_another_operational_period() {
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(batchRequest("idem-path-current-op"));
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, started_by_account_id,
            started_at, ended_at, version, created_at, updated_at
        ) VALUES (
            ?::uuid, ?::uuid, 2, 'ENDED', 'MANUAL', ?::uuid,
            '2026-04-27T00:00:00Z', '2026-04-27T08:00:00Z', 1,
            '2026-04-27T00:00:00Z', '2026-04-27T08:00:00Z'
        )
        """,
        PAST_OP_ID.toString(),
        INCIDENT_ID.toString(),
        ACCOUNT_ID.toString());
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
            id, operational_period_id, incident_assignment_id, police_phone_id, status,
            started_by_account_id, started_at, ended_at, version, created_at, updated_at
        )
        SELECT ?::uuid, ?::uuid, incident_assignment_id, police_phone_id, 'ENDED',
               started_by_account_id, started_at, started_at + INTERVAL '8 hours',
               1, created_at, updated_at
        FROM duty_shift
        WHERE id = ?::uuid
        """,
        PAST_DUTY_SHIFT_ID.toString(),
        PAST_OP_ID.toString(),
        DUTY_SHIFT_ID.toString());
    jdbcTemplate.update(
        "UPDATE search_path SET duty_shift_id = ?::uuid WHERE id = ?::uuid",
        PAST_DUTY_SHIFT_ID.toString(),
        PATH_ID.toString());

    try {
      assertThatThrownBy(() -> searchPathService.appendPoints(nextVehicleBatchRequest()))
          .isInstanceOfSatisfying(
              SearchPathApiException.class,
              exception -> assertThat(exception.getMessage()).isEqualTo("op_mismatch"));

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT version FROM search_path WHERE id = ?::uuid",
                  Long.class,
                  PATH_ID.toString()))
          .isEqualTo(first.getVersion());
      assertThat(rowCount("search_path_gps_point")).isEqualTo(8);
      assertThat(rowCount("search_path_segment")).isEqualTo(2);
      assertThat(rowCount("event_dispatch_job")).isEqualTo(1);
    } finally {
      jdbcTemplate.update(
          "UPDATE search_path SET duty_shift_id = ?::uuid WHERE id = ?::uuid",
          DUTY_SHIFT_ID.toString(),
          PATH_ID.toString());
      jdbcTemplate.update(
          "DELETE FROM duty_shift WHERE id = ?::uuid", PAST_DUTY_SHIFT_ID.toString());
      jdbcTemplate.update(
          "DELETE FROM operational_period WHERE id = ?::uuid", PAST_OP_ID.toString());
    }
  }

  @Test
  @DisplayName("같은 이동 유형의 묶음을 다시 올리면 첫 구간의 ID를 유지한 채 마지막 구간을 늘린다")
  void same_movement_type_batch_extends_last_segment_and_keeps_its_id() {
    // given: 도보 구간 하나를 만든 첫 업로드다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(trackBatch("extend-first", 0, 6, FOOT_SPEED));
    SavedSegment before = savedSegments().get(0);

    // when: 같은 이동 유형의 다음 묶음을 다른 멱등성 키로 올린다.
    SearchPathPointsAppendServiceResponse second =
        searchPathService.appendPoints(trackBatch("extend-second", 6, 6, FOOT_SPEED));

    // then: 구간은 하나로 남고 12개 좌표 범위로 늘어나며 두 버전이 오른다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.id().toString()).isEqualTo(first.getSegments().get(0).getId());
              assertThat(segment.range()).containsExactly(0, 11);
              assertThat(segment.movementType()).isEqualTo("FOOT");
              assertThat(segment.source()).isEqualTo("AUTO");
              assertThat(segment.pointCount()).isEqualTo(12);
              assertThat(segment.matchesGpsRange()).isTrue();
              assertThat(segment.version()).isGreaterThan(before.version());
              assertThat(segment.lastChangedPathVersion())
                  .isGreaterThan(before.lastChangedPathVersion());
            });
    assertThat(second.getSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.getId()).isEqualTo(first.getSegments().get(0).getId());
              assertThat(segment.getStartIndex()).isZero();
              assertThat(segment.getEndIndex()).isEqualTo(11);
              assertThat(segment.getStartPointId()).isEqualTo("track-0");
              assertThat(segment.getEndPointId()).isEqualTo("track-11");
            });
  }

  @Test
  @DisplayName("이동 유형이 바뀐 묶음은 마지막 구간을 늘리지 않고 새 구간을 만든다")
  void changed_movement_type_batch_creates_new_segment() {
    // given: 도보 구간 하나가 있다.
    searchPathService.appendPoints(trackBatch("type-foot", 0, 6, FOOT_SPEED));

    // when: 차량 속도의 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("type-vehicle", 6, 6, VEHICLE_SPEED));

    // then: 기존 도보 구간은 그대로이고 차량 구간이 새로 생긴다.
    List<SavedSegment> segments = savedSegments();
    assertThat(segments)
        .extracting(SavedSegment::range)
        .containsExactly(List.of(0, 5), List.of(6, 11));
    assertThat(segments).extracting(SavedSegment::movementType).containsExactly("FOOT", "VEHICLE");
    assertThat(segments).extracting(SavedSegment::version).containsExactly(1L, 1L);
    assertThat(segments).extracting(SavedSegment::lastChangedPathVersion).containsExactly(2L, 3L);
  }

  @Test
  @DisplayName("좌표 120개 묶음은 구간 하나가 되고, 늘리면 120개를 넘는 다음 묶음은 새 구간을 만든다")
  void batch_exceeding_120_points_after_extension_creates_new_segment() {
    // given: 구간이 없는 경로에 도보 좌표 120개 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("cap-full", 0, 120, FOOT_SPEED));
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 119);
              assertThat(segment.pointCount()).isEqualTo(120);
            });

    // when: 같은 이동 유형의 묶음을 더 올린다.
    searchPathService.appendPoints(trackBatch("cap-next", 120, 6, FOOT_SPEED));

    // then: 120개 구간은 그대로이고 새 구간이 생긴다.
    List<SavedSegment> segments = savedSegments();
    assertThat(segments)
        .extracting(SavedSegment::range)
        .containsExactly(List.of(0, 119), List.of(120, 125));
    assertThat(segments).extracting(SavedSegment::movementType).containsExactly("FOOT", "FOOT");
    assertThat(segments).extracting(SavedSegment::version).containsExactly(1L, 1L);
  }

  @Test
  @DisplayName("일부 좌표가 제외된 묶음은 통과한 좌표 수로 상한과 순번을 계산하고 통과한 좌표만 구간 도형에 넣는다")
  void partially_excluded_batch_extends_segment_with_accepted_points_only() {
    // given: 115개 중 1개가 제외되어 통과 좌표가 114개인 도보 구간이다.
    searchPathService.appendPoints(trackBatch("partial-first", 0, 115, FOOT_SPEED, 3));

    // when: 8개 중 2개가 제외되는 묶음을 올린다. 통과 좌표만 세면 120개다.
    searchPathService.appendPoints(
        trackBatch("partial-second", 115, 8, FOOT_SPEED, 116, 120));

    // then: 통과 좌표 120개가 순서대로 구간 하나에 들어가고, 원본 GPS와 제외 좌표 행이 남는다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 119);
              assertThat(segment.pointCount()).isEqualTo(120);
              assertThat(segment.matchesGpsRange()).isTrue();
            });
    assertThat(rowCount("search_path_gps_point")).isEqualTo(120);
    assertThat(excludedPointIds()).containsExactlyInAnyOrder("track-3", "track-116", "track-120");
  }

  @Test
  @DisplayName("마지막 구간이 보정한 구간이면 새 구간을 만들고 보정한 구간은 바꾸지 않는다")
  void manual_last_segment_is_kept_and_new_segment_is_created() {
    // given: 사용자가 보정한 도보 구간이 마지막 구간이다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(trackBatch("manual-first", 0, 6, FOOT_SPEED));
    searchPathService.correctSegment(
        segmentCorrectionRequest(first.getSegments().get(0).getId(), "manual-correction"));
    SavedSegment corrected = savedSegments().get(0);

    // when: 같은 이동 유형의 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("manual-next", 6, 6, FOOT_SPEED));

    // then: 보정한 구간은 그대로이고 자동 분류 구간이 새로 생긴다.
    List<SavedSegment> segments = savedSegments();
    assertThat(segments).hasSize(2);
    assertThat(segments.get(0)).isEqualTo(corrected);
    assertThat(segments.get(0).source()).isEqualTo("MANUAL");
    assertThat(segments.get(1).range()).containsExactly(6, 11);
    assertThat(segments.get(1).source()).isEqualTo("AUTO");
    assertThat(segments.get(1).version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("UNKNOWN 구간끼리 이어 붙여 좌표가 3개 이상이 되어도 이동 유형은 UNKNOWN으로 남는다")
  void unknown_segments_stay_unknown_after_extension() {
    // given: 도보 속도지만 연속 2개뿐이라 UNKNOWN으로 분류된 구간이다.
    searchPathService.appendPoints(trackBatch("unknown-first", 0, 2, FOOT_SPEED));

    // when: 같은 방식으로 UNKNOWN이 되는 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("unknown-second", 2, 2, FOOT_SPEED));

    // then: 구간에 도보 속도 좌표가 4개 들어 있어도 이동 유형은 다시 분류되지 않는다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 3);
              assertThat(segment.movementType()).isEqualTo("UNKNOWN");
              assertThat(segment.pointCount()).isEqualTo(4);
            });
  }

  @Test
  @DisplayName("유효 좌표가 없는 묶음은 구간과 원본 GPS를 바꾸지 않고 제외 좌표와 경로 버전만 저장한다")
  void batch_without_accepted_points_keeps_segments_and_gps_rows() {
    // given: 도보 구간 하나가 있다.
    searchPathService.appendPoints(trackBatch("empty-first", 0, 6, FOOT_SPEED));
    List<SavedSegment> before = savedSegments();
    long versionBefore = pathVersion();

    // when: 좌표가 모두 제외되는 묶음을 올린다.
    SearchPathPointsAppendServiceResponse response =
        searchPathService.appendPoints(trackBatch("empty-second", 6, 2, FOOT_SPEED, 6, 7));

    // then: 구간과 원본 GPS는 그대로이고 제외 좌표와 경로 버전만 바뀐다.
    assertThat(response.getSegments()).isEmpty();
    assertThat(savedSegments()).isEqualTo(before);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(6);
    assertThat(excludedPointIds()).containsExactlyInAnyOrder("track-6", "track-7");
    assertThat(pathVersion()).isEqualTo(versionBefore + 1);
  }

  @Test
  @DisplayName("유효 좌표가 1개뿐인 묶음은 구간이 없으면 UNKNOWN 구간을 만들고, 다음에는 그 UNKNOWN 구간에 이어 붙는다")
  void single_accepted_point_creates_then_extends_unknown_segment() {
    // given/when: 구간이 없는 경로에 2개 중 1개만 통과하는 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("single-first", 0, 2, FOOT_SPEED, 1));

    // then: 좌표 하나짜리 UNKNOWN 구간이 생긴다.
    SavedSegment created = savedSegments().get(0);
    assertThat(savedSegments()).hasSize(1);
    assertThat(created.range()).containsExactly(0, 0);
    assertThat(created.movementType()).isEqualTo("UNKNOWN");
    assertThat(created.source()).isEqualTo("AUTO");

    // when: 다시 1개만 통과하는 묶음을 올린다.
    searchPathService.appendPoints(trackBatch("single-second", 2, 2, FOOT_SPEED, 3));

    // then: 좌표 하나짜리 구간에 이어 붙어, 구간에 원본 좌표 2개가 순서대로 들어간다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.id()).isEqualTo(created.id());
              assertThat(segment.range()).containsExactly(0, 1);
              assertThat(segment.movementType()).isEqualTo("UNKNOWN");
              assertThat(segment.pointCount()).isEqualTo(2);
              assertThat(segment.matchesGpsRange()).isTrue();
            });
  }

  @Test
  @DisplayName("일시정지 중이거나 종료한 경로의 좌표는 거절하고, 재개한 뒤 올린 같은 이동 유형 묶음은 마지막 구간에 이어 붙는다")
  void resumed_path_extends_last_segment_and_paused_or_ended_path_rejects_points() {
    // given: 앱에서 시작한 경로에 도보 구간 하나를 올리고 일시정지한다.
    appSearchPathService.start(
        SearchPathStartServiceRequest.builder()
            .searchPathId(PATH_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(TRACK_STARTED_AT.toInstant())
            .clockOffsetMs(0)
            .idempotencyKey("resume-start")
            .build());
    searchPathService.appendPoints(trackBatch("resume-first", 0, 6, FOOT_SPEED));
    appSearchPathService.updateStatus(statusRequest(SearchPathLifecycleAction.PAUSE, 10));

    // when/then: 일시정지 중에 올린 좌표는 거절된다.
    assertThatThrownBy(
            () -> searchPathService.appendPoints(trackBatch("resume-paused", 6, 6, FOOT_SPEED)))
        .isInstanceOfSatisfying(
            SearchPathApiException.class,
            exception -> assertThat(exception.getMessage()).isEqualTo("write_conflict"));

    // when: 재개한 뒤 같은 이동 유형의 묶음을 올린다.
    appSearchPathService.updateStatus(statusRequest(SearchPathLifecycleAction.RESUME, 20));
    searchPathService.appendPoints(trackBatch("resume-after", 6, 6, FOOT_SPEED));

    // then: 일시정지 전 구간에 이어 붙는다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 11);
              assertThat(segment.pointCount()).isEqualTo(12);
            });

    // when/then: 종료한 경로에 올린 좌표는 거절된다.
    appSearchPathService.updateStatus(statusRequest(SearchPathLifecycleAction.END, 30));
    assertThatThrownBy(
            () -> searchPathService.appendPoints(trackBatch("resume-ended", 12, 6, FOOT_SPEED)))
        .isInstanceOfSatisfying(
            SearchPathApiException.class,
            exception -> assertThat(exception.getMessage()).isEqualTo("write_conflict"));
    assertThat(rowCount("search_path_gps_point")).isEqualTo(12);
  }

  @Test
  @DisplayName("이어 붙인 묶음을 같은 멱등성 키로 다시 보내면 구간, 제외 좌표, 경로 버전을 다시 바꾸지 않는다")
  void replayed_extension_does_not_extend_segment_again() {
    // given: 제외 좌표가 하나 있는 묶음으로 도보 구간을 늘렸다.
    searchPathService.appendPoints(trackBatch("replay-first", 0, 6, FOOT_SPEED));
    SearchPathPointsAppendServiceRequest second =
        trackBatch("replay-second", 6, 7, FOOT_SPEED, 9);
    SearchPathPointsAppendServiceResponse appended = searchPathService.appendPoints(second);
    List<SavedSegment> afterFirstSend = savedSegments();
    long versionAfterFirstSend = pathVersion();

    // when: 같은 멱등성 키로 같은 묶음을 다시 보낸다.
    SearchPathPointsAppendServiceResponse replayed = searchPathService.appendPoints(second);

    // then: 처음과 같은 응답을 받고 저장 상태는 바뀌지 않는다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(appended);
    assertThat(afterFirstSend)
        .singleElement()
        .extracting(SavedSegment::range)
        .isEqualTo(List.of(0, 11));
    assertThat(savedSegments()).isEqualTo(afterFirstSend);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(12);
    assertThat(excludedPointIds()).containsExactly("track-9");
    assertThat(pathVersion()).isEqualTo(versionAfterFirstSend);
  }

  @Test
  @DisplayName("좌표 추가 두 건이 경합해도 두 묶음의 좌표와 구간 버전, 경로 버전을 모두 보존한다")
  void concurrent_appends_extend_last_segment_without_losing_points_or_versions()
      throws Exception {
    // given: 도보 구간 하나가 경로 버전 2에서 저장됐다.
    searchPathService.appendPoints(trackBatch("race-first", 0, 6, FOOT_SPEED));

    // when: 앞 묶음의 트랜잭션이 커밋되기 전에 다음 묶음 저장을 시작한다.
    SearchPathPointsAppendServiceResponse waited =
        commitFirstWriteWhileSecondWaits(
            () -> searchPathService.appendPoints(trackBatch("race-second", 6, 6, FOOT_SPEED)),
            () -> searchPathService.appendPoints(trackBatch("race-third", 12, 6, FOOT_SPEED)));

    // then: 두 묶음 모두 같은 구간에 이어 붙고 버전이 한 번씩 오른다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 17);
              assertThat(segment.pointCount()).isEqualTo(18);
              assertThat(segment.matchesGpsRange()).isTrue();
              assertThat(segment.version()).isEqualTo(3L);
              assertThat(segment.lastChangedPathVersion()).isEqualTo(4L);
            });
    assertThat(waited.getVersion()).isEqualTo(4L);
    assertThat(pathVersion()).isEqualTo(4L);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(18);
  }

  @Test
  @DisplayName("구간 보정이 먼저 커밋되면 대기한 좌표 추가는 보정한 구간을 늘리지 않고 새 구간을 만든다")
  void append_waiting_for_correction_creates_new_segment_after_manual_segment() throws Exception {
    // given: 도보 구간 하나가 있다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(trackBatch("race-correct-first", 0, 6, FOOT_SPEED));

    // when: 보정이 커밋되기 전에 같은 이동 유형의 묶음 저장을 시작한다.
    commitFirstWriteWhileSecondWaits(
        () ->
            searchPathService.correctSegment(
                segmentCorrectionRequest(first.getSegments().get(0).getId(), "race-correction")),
        () -> searchPathService.appendPoints(trackBatch("race-after-correction", 6, 6, FOOT_SPEED)));

    // then: 보정 내용과 새 구간, 두 변경의 경로 버전이 모두 남는다.
    List<SavedSegment> segments = savedSegments();
    assertThat(segments)
        .extracting(SavedSegment::range)
        .containsExactly(List.of(0, 5), List.of(6, 11));
    assertThat(segments).extracting(SavedSegment::source).containsExactly("MANUAL", "AUTO");
    assertThat(segments).extracting(SavedSegment::version).containsExactly(2L, 1L);
    assertThat(segments).extracting(SavedSegment::lastChangedPathVersion).containsExactly(3L, 4L);
    assertThat(pathVersion()).isEqualTo(4L);
    assertThat(rowCount("search_path_gps_point")).isEqualTo(12);
  }

  @Test
  @DisplayName("좌표 추가가 먼저 커밋되면 대기한 구간 보정은 늘어난 구간의 최신 버전에 적용된다")
  void correction_waiting_for_append_applies_to_extended_segment() throws Exception {
    // given: 도보 구간 하나가 있다.
    SearchPathPointsAppendServiceResponse first =
        searchPathService.appendPoints(trackBatch("race-append-first", 0, 6, FOOT_SPEED));

    // when: 좌표 추가가 커밋되기 전에 같은 구간의 보정을 시작한다.
    commitFirstWriteWhileSecondWaits(
        () -> searchPathService.appendPoints(trackBatch("race-append-second", 6, 6, FOOT_SPEED)),
        () ->
            searchPathService.correctSegment(
                segmentCorrectionRequest(
                    first.getSegments().get(0).getId(), "race-late-correction")));

    // then: 늘어난 구간 전체가 보정되고 두 변경의 버전이 모두 남는다.
    assertThat(savedSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.range()).containsExactly(0, 11);
              assertThat(segment.pointCount()).isEqualTo(12);
              assertThat(segment.source()).isEqualTo("MANUAL");
              assertThat(segment.version()).isEqualTo(3L);
              assertThat(segment.lastChangedPathVersion()).isEqualTo(4L);
            });
    assertThat(pathVersion()).isEqualTo(4L);
  }

  private <T> T commitFirstWriteWhileSecondWaits(Runnable firstWrite, Supplier<T> secondWrite)
      throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicInteger secondConnectionId = new AtomicInteger();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      Future<T> pending =
          transaction.execute(
              status -> {
                firstWrite.run();
                int firstConnectionId =
                    jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                Future<T> waiting =
                    executor.submit(
                        () ->
                            transaction.execute(
                                secondStatus -> {
                                  secondConnectionId.set(
                                      jdbcTemplate.queryForObject(
                                          "SELECT pg_backend_pid()", Integer.class));
                                  return secondWrite.get();
                                }));
                // 두 DB 연결의 실제 잠금 대기로 실행 순서를 고정한다.
                await()
                    .atMost(Duration.ofSeconds(10))
                    .until(
                        () ->
                            jdbcTemplate.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))",
                                Boolean.class,
                                firstConnectionId,
                                secondConnectionId.get()));
                return waiting;
              });
      return pending.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private SearchPathStatusUpdateServiceRequest statusRequest(
      SearchPathLifecycleAction action, long secondsAfterStart) {
    return SearchPathStatusUpdateServiceRequest.builder()
        .searchPathId(PATH_ID)
        .accountId(ACCOUNT_ID)
        .action(action)
        .clientTs(TRACK_STARTED_AT.toInstant().plusSeconds(secondsAfterStart))
        .clockOffsetMs(0)
        .idempotencyKey("status-" + action)
        .build();
  }

  private long pathVersion() {
    return jdbcTemplate.queryForObject(
        "SELECT version FROM search_path WHERE id = ?::uuid", Long.class, PATH_ID.toString());
  }

  private List<String> excludedPointIds() {
    return jdbcTemplate.queryForList(
        "SELECT point_id FROM search_path_excluded_point WHERE search_path_id = ?::uuid",
        String.class,
        PATH_ID.toString());
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

  // 측정 시각 0.25초, 수집 순서 0.25초 간격으로 120개까지 시각 차이 검사를 통과하는 묶음을 만든다.
  private SearchPathPointsAppendServiceRequest trackBatch(
      String idempotencyKey, int from, int count, double speed, Integer... lowAccuracyIndexes) {
    List<Integer> excluded = List.of(lowAccuracyIndexes);
    return pointsAppendRequest(
        idempotencyKey,
        IntStream.range(from, from + count)
            .mapToObj(
                i ->
                    SearchPathPointServiceRequest.builder()
                        .pointId("track-" + i)
                        .lon(
                            new BigDecimal("126.913000")
                                .add(new BigDecimal("0.000010").multiply(BigDecimal.valueOf(i))))
                        .lat(new BigDecimal("35.162000"))
                        .speedMps(BigDecimal.valueOf(speed))
                        .horizontalAccuracyM(excluded.contains(i) ? 80 : 5)
                        .clientTs(TRACK_STARTED_AT.plusNanos(i * 250_000_000L))
                        .elapsedRealtimeNanos(10_000_000_000L + i * 250_000_000L)
                        .build())
            .toList());
  }

  private List<SavedSegment> savedSegments() {
    return jdbcTemplate.query(
        """
        SELECT s.id, s.start_point_order, s.end_point_order, s.movement_type,
               s.movement_type_source, s.version, s.last_changed_path_version,
               ST_NumPoints(s.geometry) AS point_count,
               ST_AsText(s.geometry) = (
                   SELECT ST_AsText(ST_MakeLine(
                       ST_SetSRID(ST_MakePoint(g.lon::float8, g.lat::float8), 4326)
                       ORDER BY g.point_order))
                   FROM search_path_gps_point g
                   WHERE g.search_path_id = s.search_path_id
                     AND g.point_order BETWEEN s.start_point_order AND s.end_point_order
               )
               AND s.started_at = (
                   SELECT client_ts FROM search_path_gps_point
                   WHERE search_path_id = s.search_path_id AND point_order = s.start_point_order)
               AND s.ended_at = (
                   SELECT client_ts FROM search_path_gps_point
                   WHERE search_path_id = s.search_path_id AND point_order = s.end_point_order)
                   AS matches_gps_range
        FROM search_path_segment s
        WHERE s.search_path_id = ?::uuid
        ORDER BY s.start_point_order
        """,
        (rs, rowNum) ->
            new SavedSegment(
                rs.getObject("id", UUID.class),
                List.of(rs.getInt("start_point_order"), rs.getInt("end_point_order")),
                rs.getString("movement_type"),
                rs.getString("movement_type_source"),
                rs.getLong("version"),
                rs.getLong("last_changed_path_version"),
                rs.getInt("point_count"),
                rs.getBoolean("matches_gps_range")),
        PATH_ID.toString());
  }

  // 저장된 구간 행이다. 도형, 시작 시각, 끝 시각이 모두 순번 범위의 원본 GPS와 같으면 matchesGpsRange가 true다.
  private record SavedSegment(
      UUID id,
      List<Integer> range,
      String movementType,
      String source,
      long version,
      long lastChangedPathVersion,
      int pointCount,
      boolean matchesGpsRange) {}

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
