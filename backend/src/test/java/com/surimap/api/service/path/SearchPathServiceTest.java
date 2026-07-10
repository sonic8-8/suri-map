package com.surimap.api.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.api.controller.path.SearchPathController;
import com.surimap.api.controller.path.SearchPathSegmentController;
import com.surimap.api.controller.path.request.PathBatchAppendRequest;
import com.surimap.api.controller.path.request.PathBatchPointRequest;
import com.surimap.api.controller.path.request.PathSegmentCorrectionRequest;
import com.surimap.api.controller.path.response.PathBatchAppendResponse;
import com.surimap.api.controller.path.response.PathQueryRow;
import com.surimap.api.controller.path.response.PathQuerySegmentRow;
import com.surimap.api.controller.path.response.PathSegmentCorrectionResponse;
import com.surimap.app.service.path.AppSearchPathService;
import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import com.surimap.app.service.path.request.PatchSearchPathServiceRequest;
import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("SearchPath service")
@Tag("integration")
@Sql(scripts = "/sql/path/search-path-context.sql")
class SearchPathServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID POLICE_PHONE_ID = SearchPathFixtures.POLICE_PHONE_ID;
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID CORRECTED_BY_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  private static final UUID OTHER_INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_DUTY_SHIFT_ID =
      UUID.fromString("71000000-0000-0000-0000-000000002622");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private AppSearchPathService appSearchPathService;
  @Autowired private OperationalPeriodQuery operationalPeriodQuery;
  @Autowired private SearchPathEventPublisher searchPathEventPublisher;
  @Autowired private SearchPathMapper searchPathMapper;
  @Autowired private SearchPathService searchPathService;
  @Autowired private SearchPathController searchPathController;
  @Autowired private SearchPathSegmentController searchPathSegmentController;
  @Autowired private ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider;

  @Test
  @DisplayName("start creates UUID search_path row tied to active duty_shift")
  void start_persists_search_path_row() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                null,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-262"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT sp.id,
                   sp.duty_shift_id,
                   ds.operational_period_id,
                   ds.police_phone_id,
                   sp.status,
                   sp.version,
                   sp.started_at,
                   sp.geometry
            FROM search_path sp
            JOIN duty_shift ds ON ds.id = sp.duty_shift_id
            WHERE sp.id = ?::uuid
            """,
            created.getId().toString());

    assertThat(row.get("id")).isEqualTo(created.getId());
    assertThat(row.get("duty_shift_id")).isEqualTo(DUTY_SHIFT_ID);
    assertThat(row.get("operational_period_id")).isEqualTo(OP_ID);
    assertThat(row.get("police_phone_id")).isEqualTo(POLICE_PHONE_ID);
    assertThat(row.get("status")).isEqualTo("RECORDING");
    assertThat(row.get("version")).isEqualTo(1L);
    assertThat(row.get("geometry")).isNull();
  }

  @Test
  @DisplayName("start uses client-generated path id")
  void start_uses_client_generated_path_id() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-client-id"));

    assertThat(created.getId()).isEqualTo(PATH_ID);
    assertThat(rowCount("search_path")).isEqualTo(1);
  }

  @Test
  @DisplayName("start rejects missing account id without police phone fallback")
  void start_rejects_missing_account_id_without_police_phone_fallback() {
    assertThatThrownBy(
            () ->
                appSearchPathService.start(
                    new StartSearchPathServiceRequest(
                        null,
                        INCIDENT_ID,
                        OP_ID,
                        POLICE_PHONE_ID,
                        null,
                        STARTED_AT,
                        "idem-path-start-missing-account")))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("channel_not_allowed"));
  }

  @Test
  @DisplayName("start rejects when current op is missing")
  void start_rejects_when_current_op_is_missing() {
    assertThatThrownBy(
            () ->
                appSearchPathService.start(
                    new StartSearchPathServiceRequest(
                        null,
                        UUID.randomUUID(),
                        OP_ID,
                        POLICE_PHONE_ID,
                        ACCOUNT_ID,
                        STARTED_AT,
                        "idem-path-start-op-required")))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_required"));
  }

  @Test
  @DisplayName("start rejects when requested op differs from current op")
  void start_rejects_when_requested_op_differs_from_current_op() {
    assertThatThrownBy(
            () ->
                appSearchPathService.start(
                    new StartSearchPathServiceRequest(
                        null,
                        INCIDENT_ID,
                        BoundaryAreaFixtures.OP2_ID,
                        POLICE_PHONE_ID,
                        ACCOUNT_ID,
                        STARTED_AT,
                        "idem-path-start-op-mismatch")))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_mismatch"));
  }

  @Test
  @Sql(scripts = {"/sql/path/search-path-context.sql", "/sql/path/search-path-other-phone.sql"})
  @DisplayName("start는 사건에 배치된 계정이면 다른 등록 업무폰 요청도 허용한다")
  void start_allows_registered_phone_when_account_is_assigned_to_incident() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                null,
                INCIDENT_ID,
                OP_ID,
                OTHER_POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-phone-owner-mismatch"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT sp.id,
                   sp.duty_shift_id,
                   ds.police_phone_id
            FROM search_path sp
            JOIN duty_shift ds ON ds.id = sp.duty_shift_id
            WHERE sp.id = ?::uuid
            """,
            created.getId().toString());

    assertThat(row.get("id")).isEqualTo(created.getId());
    assertThat(row.get("duty_shift_id")).isEqualTo(DUTY_SHIFT_ID);
    assertThat(row.get("police_phone_id")).isEqualTo(POLICE_PHONE_ID);

    PathQueryRow queried =
        searchPathService
            .query(INCIDENT_ID, OP_ID, OTHER_POLICE_PHONE_ID, ACCOUNT_ID)
            .paths()
            .get(0);
    assertThat(queried.id()).isEqualTo(created.getId());
    assertThat(queried.policePhoneId()).isEqualTo(OTHER_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("end updates persisted path even after in-memory active state is gone")
  void end_loads_persisted_path_and_marks_ended() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                null,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-262"));
    AppSearchPathService restartedService =
        new AppSearchPathService(
            operationalPeriodQuery, searchPathEventPublisher, searchPathMapper);

    var ended =
        restartedService.end(
            created.getId(),
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new EndSearchPathServiceRequest(STARTED_AT.plusSeconds(60), "idem-path-end-262"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT status,
                   ended_at,
                   version
            FROM search_path
            WHERE id = ?::uuid
            """,
            created.getId().toString());

    assertThat(ended.getStatus().name()).isEqualTo("ENDED");
    assertThat(row.get("status")).isEqualTo("ENDED");
    assertThat(row.get("ended_at")).isNotNull();
    assertThat(row.get("version")).isEqualTo(2L);
  }

  @Test
  @DisplayName("pause and resume update persisted status and leave lifecycle event audit")
  void pause_resume_persist_status_and_lifecycle_events() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                null,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-lifecycle"));
    AppSearchPathService restartedService =
        new AppSearchPathService(
            operationalPeriodQuery, searchPathEventPublisher, searchPathMapper);

    var paused =
        restartedService.patch(
            created.getId(),
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new PatchSearchPathServiceRequest(
                SearchPathLifecycleAction.PAUSE,
                STARTED_AT.plusSeconds(30),
                "idem-path-pause-lifecycle"));
    var resumed =
        restartedService.patch(
            created.getId(),
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new PatchSearchPathServiceRequest(
                SearchPathLifecycleAction.RESUME,
                STARTED_AT.plusSeconds(45),
                "idem-path-resume-lifecycle"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT status,
                   ended_at,
                   version
            FROM search_path
            WHERE id = ?::uuid
            """,
            created.getId().toString());
    List<Map<String, Object>> lifecycleRows =
        jdbcTemplate.queryForList(
            """
            SELECT event_type,
                   version
            FROM search_path_lifecycle_event
            WHERE search_path_id = ?::uuid
            ORDER BY version ASC
            """,
            created.getId().toString());

    assertThat(paused.getStatus().name()).isEqualTo("PAUSED");
    assertThat(resumed.getStatus().name()).isEqualTo("RECORDING");
    assertThat(row.get("status")).isEqualTo("RECORDING");
    assertThat(row.get("ended_at")).isNull();
    assertThat(row.get("version")).isEqualTo(3L);
    assertThat(lifecycleRows)
        .extracting(lifecycle -> lifecycle.get("event_type"))
        .containsExactly("STARTED", "PAUSED", "RESUMED");
    assertThat(lifecycleRows)
        .extracting(lifecycle -> lifecycle.get("version"))
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  @Sql(scripts = {"/sql/path/search-path-context.sql", "/sql/path/search-path-other-phone.sql"})
  @DisplayName("pause는 요청한 등록 업무폰을 lifecycle actor로 남긴다")
  void pause_records_request_police_phone_as_lifecycle_actor() {
    var created =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                null,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-start-lifecycle-actor"));
    AppSearchPathService restartedService =
        new AppSearchPathService(
            operationalPeriodQuery, searchPathEventPublisher, searchPathMapper);

    var paused =
        restartedService.patch(
            created.getId(),
            OTHER_POLICE_PHONE_ID,
            ACCOUNT_ID,
            new PatchSearchPathServiceRequest(
                SearchPathLifecycleAction.PAUSE,
                STARTED_AT.plusSeconds(30),
                "idem-path-pause-lifecycle-actor"));

    UUID pausedActorPhoneId =
        jdbcTemplate.queryForObject(
            """
            SELECT actor_police_phone_id
            FROM search_path_lifecycle_event
            WHERE search_path_id = ?::uuid
              AND event_type = 'PAUSED'
            """,
            UUID.class,
            created.getId().toString());

    assertThat(paused.getPolicePhoneId()).isEqualTo(OTHER_POLICE_PHONE_ID);
    assertThat(pausedActorPhoneId).isEqualTo(OTHER_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("path lifecycle and batch append stage EventHub jobs with converged versions")
  void path_writes_stage_event_dispatch_jobs_and_use_latest_version() {
    var started =
        appSearchPathService.start(
            new StartSearchPathServiceRequest(
                PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                STARTED_AT,
                "idem-path-event-start"));
    PathBatchAppendResponse batch =
        appendBatchThroughController("idem-path-event-batch", batchRequest());

    var paused =
        appSearchPathService.patch(
            PATH_ID,
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new PatchSearchPathServiceRequest(
                SearchPathLifecycleAction.PAUSE,
                STARTED_AT.plusSeconds(60),
                "idem-path-event-pause"));
    var resumed =
        appSearchPathService.patch(
            PATH_ID,
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new PatchSearchPathServiceRequest(
                SearchPathLifecycleAction.RESUME,
                STARTED_AT.plusSeconds(90),
                "idem-path-event-resume"));
    var ended =
        appSearchPathService.end(
            PATH_ID,
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            new EndSearchPathServiceRequest(STARTED_AT.plusSeconds(120), "idem-path-event-end"));

    assertThat(started.getVersion()).isEqualTo(1L);
    assertThat(batch.version()).isEqualTo(2L);
    assertThat(paused.getVersion()).isEqualTo(3L);
    assertThat(resumed.getVersion()).isEqualTo(4L);
    assertThat(ended.getVersion()).isEqualTo(5L);

    List<Map<String, Object>> eventRows =
        jdbcTemplate.queryForList(
            """
            SELECT event_type,
                   source_entity_type,
                   source_entity_id,
                   dispatch_status,
                   payload ->> 'id' AS payload_id,
                   payload ->> 'incidentId' AS payload_incident_id,
                   payload ->> 'opId' AS payload_op_id,
                   payload ->> 'policePhoneId' AS payload_police_phone_id,
                   payload ->> 'status' AS payload_status,
                   (payload ->> 'version')::bigint AS payload_version,
                   jsonb_exists(payload, 'sequence') AS has_sequence
            FROM event_dispatch_job
            WHERE source_entity_id = ?::uuid
              AND event_type IN (
                'SEARCH_PATH_STARTED',
                'PATH_APPENDED',
                'SEARCH_PATH_PAUSED',
                'SEARCH_PATH_RESUMED',
                'SEARCH_PATH_ENDED'
              )
            ORDER BY (payload ->> 'version')::bigint
            """,
            PATH_ID.toString());

    assertThat(eventRows)
        .extracting(row -> row.get("event_type"))
        .containsExactly(
            "SEARCH_PATH_STARTED",
            "PATH_APPENDED",
            "SEARCH_PATH_PAUSED",
            "SEARCH_PATH_RESUMED",
            "SEARCH_PATH_ENDED");
    assertThat(eventRows)
        .extracting(row -> row.get("payload_version"))
        .containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(eventRows)
        .allSatisfy(
            row -> {
              assertThat(row.get("source_entity_type")).isEqualTo("search_path");
              assertThat(row.get("source_entity_id")).isEqualTo(PATH_ID);
              assertThat(row.get("dispatch_status")).isEqualTo("PENDING");
              assertThat(row.get("payload_id")).isEqualTo(PATH_ID.toString());
              assertThat(row.get("payload_incident_id")).isEqualTo(INCIDENT_ID.toString());
              assertThat(row.get("payload_op_id")).isEqualTo(OP_ID.toString());
              assertThat(row.get("payload_police_phone_id")).isEqualTo(POLICE_PHONE_ID.toString());
              assertThat(row.get("has_sequence")).isEqualTo(true);
            });
    assertThat(eventRows)
        .extracting(row -> row.get("payload_status"))
        .containsExactly("RECORDING", "RECORDING", "PAUSED", "RECORDING", "ENDED");
  }

  @Test
  @DisplayName("manual segment correction stages SEARCH_PATH_SEGMENT_UPDATED EventHub job")
  void segment_correction_stages_event_dispatch_job() {
    PathBatchAppendResponse batch =
        appendBatchThroughController("idem-path-event-segment-batch", batchRequest());
    String segmentId = batch.segments().get(0).id();

    searchPathSegmentController
        .correctSegment(
            segmentId,
            CORRECTED_BY_ACCOUNT_ID.toString(),
            "idem-path-event-segment-correction",
            new PathSegmentCorrectionRequest(MovementType.FOOT, "manual correction"))
        .getBody();

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
  @DisplayName("start idempotency replay survives service recreation without duplicate path rows")
  void start_idempotency_replay_survives_service_recreation() {
    StartSearchPathServiceRequest request =
        new StartSearchPathServiceRequest(
            null,
            INCIDENT_ID,
            OP_ID,
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            STARTED_AT,
            "idem-path-start-db-replay");
    var created = appSearchPathService.start(request);
    AppSearchPathService restartedService =
        new AppSearchPathService(
            operationalPeriodQuery,
            searchPathEventPublisher,
            searchPathMapper,
            idempotentResponseCacheProvider);

    var replayed = restartedService.start(request);

    assertThat(replayed).isEqualTo(created);
    assertThat(rowCount("search_path")).isEqualTo(1);
    assertThat(idempotencyStatus("idem-path-start-db-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("same start idempotency key with different body is rejected")
  void start_same_key_different_body_is_rejected() {
    appSearchPathService.start(
        new StartSearchPathServiceRequest(
            null,
            INCIDENT_ID,
            OP_ID,
            POLICE_PHONE_ID,
            ACCOUNT_ID,
            STARTED_AT,
            "idem-path-start-mismatch"));

    assertThatThrownBy(
            () ->
                appSearchPathService.start(
                    new StartSearchPathServiceRequest(
                        null,
                        INCIDENT_ID,
                        OP_ID,
                        POLICE_PHONE_ID,
                        ACCOUNT_ID,
                        STARTED_AT.plusSeconds(1),
                        "idem-path-start-mismatch")))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(rowCount("search_path")).isEqualTo(1);
  }

  @Test
  @DisplayName("batch append idempotency replay does not append geometry twice")
  void batch_append_idempotency_replay_does_not_append_twice() {
    PathBatchAppendResponse first =
        appendBatchThroughController("idem-path-batch-db-replay", batchRequest());

    PathBatchAppendResponse replayed =
        appendBatchThroughController("idem-path-batch-db-replay", batchRequest());

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
    PathBatchAppendResponse batch =
        appendBatchThroughController("idem-path-batch-for-correction", batchRequest());
    String segmentId = batch.segments().get(0).id();
    PathSegmentCorrectionRequest request =
        new PathSegmentCorrectionRequest(MovementType.FOOT, "manual correction");

    PathSegmentCorrectionResponse first =
        searchPathSegmentController
            .correctSegment(
                segmentId,
                CORRECTED_BY_ACCOUNT_ID.toString(),
                "idem-path-segment-db-replay",
                request)
            .getBody();
    PathSegmentCorrectionResponse replayed =
        searchPathSegmentController
            .correctSegment(
                segmentId,
                CORRECTED_BY_ACCOUNT_ID.toString(),
                "idem-path-segment-db-replay",
                request)
            .getBody();

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
    PathBatchAppendResponse response =
        searchPathService.appendBatch(batchRequest(), POLICE_PHONE_ID, ACCOUNT_ID);

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

    assertThat(response.dutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("id")).isEqualTo(PATH_ID);
    assertThat(pathRow.get("duty_shift_id")).isEqualTo(DUTY_SHIFT_ID);
    assertThat(pathRow.get("status")).isEqualTo("RECORDING");
    assertThat(pathRow.get("version")).isEqualTo(response.version());
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
    PathBatchAppendResponse response =
        searchPathService.appendBatch(batchRequest(), POLICE_PHONE_ID, ACCOUNT_ID);
    SearchPathSegment target = response.segments().get(0);

    SegmentCorrectionResult corrected =
        searchPathService.correctSegment(target.id(), MovementType.FOOT, CORRECTED_BY_ACCOUNT_ID);

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

    assertThat(corrected.segment().movementType()).isEqualTo(MovementType.FOOT);
    assertThat(row.get("movement_type")).isEqualTo("FOOT");
    assertThat(row.get("movement_type_source")).isEqualTo("MANUAL");
    assertThat(row.get("corrected_by_account_id")).isEqualTo(CORRECTED_BY_ACCOUNT_ID);
    assertThat(row.get("corrected_at")).isNotNull();
    assertThat(row.get("version")).isEqualTo(2L);
  }

  @Test
  @DisplayName("single-point UNKNOWN segment persists as LineString and keeps query indexes")
  void single_point_segment_persists_and_reconstructs_indexes() {
    PathBatchAppendResponse response =
        searchPathService.appendBatch(singlePointSegmentRequest(), POLICE_PHONE_ID, ACCOUNT_ID);

    assertThat(response.segments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.UNKNOWN, MovementType.VEHICLE);
    assertThat(response.segments().get(1).startIndex()).isEqualTo(3);
    assertThat(response.segments().get(1).endIndex()).isEqualTo(3);

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

    PathQueryRow queried =
        searchPathService.query(INCIDENT_ID, OP_ID, POLICE_PHONE_ID).paths().get(0);
    assertThat(queried.segments())
        .extracting(PathQuerySegmentRow::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.UNKNOWN, MovementType.VEHICLE);
    assertThat(queried.segments().get(1).geometry()).containsExactly(List.of(126.91485, 35.16254));
  }

  @Test
  @DisplayName("low-quality excluded point remains in query after DB reload")
  void excluded_point_persists_and_reloads_for_query() {
    PathBatchAppendResponse response =
        searchPathService.appendBatch(lowQualityPointRequest(), POLICE_PHONE_ID, ACCOUNT_ID);

    assertThat(response.acceptedPointCount()).isEqualTo(2);
    assertThat(response.excludedPointCount()).isEqualTo(1);
    assertThat(response.excludedPoints())
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

    PathQueryRow queried =
        searchPathService.query(INCIDENT_ID, OP_ID, POLICE_PHONE_ID).paths().get(0);

    assertThat(queried.geometry()).hasSize(2);
    assertThat(queried.excludedPoints())
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
    PathBatchAppendResponse first =
        searchPathService.appendBatch(batchRequest(), POLICE_PHONE_ID, ACCOUNT_ID);

    PathBatchAppendResponse second =
        searchPathService.appendBatch(nextVehicleBatchRequest(), POLICE_PHONE_ID, ACCOUNT_ID);

    assertThat(first.segments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT);
    assertThat(second.segments())
        .extracting(SearchPathSegment::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT, MovementType.VEHICLE);
    assertThat(second.segments().get(0).id()).isEqualTo(first.segments().get(0).id());
    assertThat(second.segments().get(1).id()).isEqualTo(first.segments().get(1).id());

    PathQueryRow queried =
        searchPathService.query(INCIDENT_ID, OP_ID, POLICE_PHONE_ID).paths().get(0);
    assertThat(queried.geometry()).hasSize(11);
    assertThat(queried.segments())
        .extracting(PathQuerySegmentRow::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT, MovementType.VEHICLE);
    assertThat(queried.segments().get(0).geometry()).hasSize(4);
    assertThat(queried.segments().get(1).geometry()).hasSize(4);
    assertThat(queried.segments().get(2).geometry()).hasSize(3);
  }

  private PathBatchAppendResponse appendBatchThroughController(
      String idempotencyKey, PathBatchAppendRequest request) {
    return appendBatchThroughController(idempotencyKey, request, POLICE_PHONE_ID);
  }

  private PathBatchAppendResponse appendBatchThroughController(
      String idempotencyKey, PathBatchAppendRequest request, UUID policePhoneId) {
    var previousContext = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new SuriMapAuthentication(
            ACCOUNT_ID.toString(),
            AccountType.PATROL_CAR,
            OrganizationType.POLICE_SUBSTATION,
            Channel.APP,
            policePhoneId.toString(),
            List.of(new SimpleGrantedAuthority(Role.MEMBER.name()))));
    try {
      SecurityContextHolder.setContext(context);
      return searchPathController
          .appendBatch(policePhoneId.toString(), idempotencyKey, request)
          .getBody();
    } finally {
      SecurityContextHolder.setContext(previousContext);
    }
  }

  private PathBatchAppendRequest batchRequest() {
    return new PathBatchAppendRequest(
        INCIDENT_ID,
        OP_ID,
        PATH_ID,
        List.of(
            point("gps-precinct-001", "126.913000", "35.162000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-002", "126.913650", "35.162180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-003", "126.914300", "35.162360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-004", "126.914850", "35.162540", 9.8, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-005", "126.915000", "35.162700", 1.6, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-006", "126.915080", "35.162880", 1.3, "2026-04-28T09:00:25+09:00"),
            point("gps-precinct-007", "126.915160", "35.163050", 1.1, "2026-04-28T09:00:30+09:00"),
            point("gps-precinct-008", "126.915250", "35.163120", 1.4, "2026-04-28T09:00:35+09:00")),
        0L);
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

  private PathBatchAppendRequest singlePointSegmentRequest() {
    return new PathBatchAppendRequest(
        INCIDENT_ID,
        OP_ID,
        PATH_ID,
        List.of(
            point("gps-precinct-101", "126.913000", "35.162000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-102", "126.913650", "35.162180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-103", "126.914300", "35.162360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-104", "126.914850", "35.162540", 1.6, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-105", "126.915000", "35.162700", 13.0, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-106", "126.915080", "35.162880", 12.5, "2026-04-28T09:00:25+09:00"),
            point(
                "gps-precinct-107", "126.915160", "35.163050", 11.8, "2026-04-28T09:00:30+09:00")),
        0L);
  }

  private PathBatchAppendRequest nextVehicleBatchRequest() {
    return new PathBatchAppendRequest(
        INCIDENT_ID,
        OP_ID,
        PATH_ID,
        List.of(
            point("gps-precinct-201", "126.915400", "35.163400", 13.2, "2026-04-28T09:01:00+09:00"),
            point("gps-precinct-202", "126.916000", "35.163600", 12.9, "2026-04-28T09:01:05+09:00"),
            point(
                "gps-precinct-203", "126.916600", "35.163800", 12.1, "2026-04-28T09:01:10+09:00")),
        0L);
  }

  private PathBatchAppendRequest lowQualityPointRequest() {
    return new PathBatchAppendRequest(
        INCIDENT_ID,
        OP_ID,
        PATH_ID,
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
                "2026-04-28T09:00:10+09:00")),
        0L);
  }

  private PathBatchPointRequest point(
      String pointId, String lon, String lat, double speed, String clientTs) {
    return point(pointId, lon, lat, speed, clientTs, 5);
  }

  private PathBatchPointRequest point(
      String pointId, String lon, String lat, double speed, String clientTs, int accuracyM) {
    return new PathBatchPointRequest(
        pointId,
        new BigDecimal(lon),
        new BigDecimal(lat),
        BigDecimal.valueOf(speed),
        accuracyM,
        OffsetDateTime.parse(clientTs));
  }
}
