package com.surimap.app.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.surimap.api.service.path.SearchPathService;
import com.surimap.api.service.path.request.SearchPathPointServiceRequest;
import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.SearchPathStartServiceRequest;
import com.surimap.app.service.path.request.SearchPathStatusUpdateServiceRequest;
import com.surimap.app.service.path.response.SearchPathStartServiceResponse;
import com.surimap.app.service.path.response.SearchPathStatusUpdateServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.global.event.SearchPathEventPublisher;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Sql(scripts = "/sql/path/search-path-context.sql")
class AppSearchPathServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID OTHER_PATH_ID = UUID.fromString("71000000-0000-0000-0000-000000002622");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private AppSearchPathService appSearchPathService;
  @Autowired private OperationalPeriodQuery operationalPeriodQuery;
  @Autowired private SearchPathEventPublisher searchPathEventPublisher;
  @Autowired private SearchPathMapper searchPathMapper;
  @Autowired private IdempotentResponseCache idempotentResponseCache;
  @Autowired private SearchPathService searchPathService;
  @Autowired private PlatformTransactionManager transactionManager;

  @ParameterizedTest(name = "상태 변경={0}, 좌표 묶음={2}")
  @CsvSource({"PAUSE,PAUSED,1,3", "PAUSE,PAUSED,2,4", "END,ENDED,1,3", "END,ENDED,2,4"})
  @DisplayName("좌표 저장 중 상태를 변경해도 각 변경의 버전과 좌표를 보존한다")
  void update_status_during_appends_preserves_each_committed_version(
      SearchPathLifecycleAction action,
      SearchPathStatus expectedStatus,
      int batchCount,
      int expectedVersion)
      throws Exception {
    // given: 시작 버전 1의 경로와 별도 멱등성 키를 가진 상태 변경 요청이다.
    appSearchPathService.start(startRequest("start-concurrent"));
    SearchPathStatusUpdateServiceRequest request =
        statusRequest(action, STARTED_AT.plusSeconds(30), "status-concurrent");

    // when: 좌표 저장이 끝나기 전에 상태 변경을 시작한다.
    SearchPathStatusUpdateServiceResponse response =
        commitFirstWriteWhileSecondWaits(
            () -> {
              for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
                assertThat(
                        searchPathService
                            .appendPoints(batchRequest(batchIndex * 2))
                            .getAcceptedPointCount())
                    .isEqualTo(2);
              }
            },
            () -> appSearchPathService.updateStatus(request));

    // then: 시작·각 좌표 추가·상태 변경의 버전과 좌표·이벤트가 모두 보존된다.
    assertThat(response.getVersion()).isEqualTo(expectedVersion);
    assertThat(response.getStatus()).isEqualTo(expectedStatus);
    SearchPathQueryRowServiceResponse saved = queryPath();
    assertThat(saved.getVersion()).isEqualTo(expectedVersion);
    assertThat(saved.getStatus()).isEqualTo(expectedStatus);
    assertThat(saved.getGeometry()).hasSize(batchCount * 2);
    assertThat(searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments())
        .extracting(segment -> segment.getLastChangedPathVersion())
        .containsExactlyElementsOf(LongStream.rangeClosed(2, batchCount + 1).boxed().toList());
    if (action == SearchPathLifecycleAction.END) {
      assertThat(saved.getEndedAt()).isEqualTo(request.getClientTs());
    } else {
      assertThat(saved.getEndedAt()).isNull();
    }
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT (payload->>'version')::bigint FROM event_dispatch_job ORDER BY insertion_order",
                Long.class))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, expectedVersion).boxed().toList());
    assertThat(appSearchPathService.updateStatus(request))
        .usingRecursiveComparison()
        .isEqualTo(response);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(expectedVersion);
    assertThat(idempotencyStatus("status-concurrent")).isEqualTo("COMPLETED");
  }

  @ParameterizedTest(name = "상태 변경={0}")
  @CsvSource({"PAUSE,PAUSED,4", "RESUME,RECORDING,5", "END,ENDED,4"})
  @DisplayName("구간 보정 중 상태를 변경해도 보정 내용과 두 변경의 경로 버전을 보존한다")
  void update_status_during_segment_correction_preserves_both_changes(
      SearchPathLifecycleAction action, SearchPathStatus expectedStatus, int expectedVersion)
      throws Exception {
    // given: 좌표를 저장한 경로이며 재개를 검증할 때는 먼저 일시정지한다.
    appSearchPathService.start(startRequest("start-correction"));
    String segmentId =
        searchPathService.appendPoints(batchRequest(0)).getSegments().get(0).getId().toString();
    if (action == SearchPathLifecycleAction.RESUME) {
      appSearchPathService.updateStatus(
          statusRequest(
              SearchPathLifecycleAction.PAUSE,
              STARTED_AT.plusSeconds(10),
              "pause-before-correction"));
    }
    SearchPathStatusUpdateServiceRequest request =
        statusRequest(action, STARTED_AT.plusSeconds(30), "status-after-correction");

    // when: 실제 보정 저장과 상태 변경이 같은 경로에서 겹친다.
    SearchPathStatusUpdateServiceResponse response =
        commitFirstWriteWhileSecondWaits(
            () ->
                searchPathService.correctSegment(
                    SearchPathSegmentCorrectionServiceRequest.builder()
                        .searchPathSegmentId(segmentId)
                        .movementType(MovementType.VEHICLE)
                        .correctedByAccountId(ACCOUNT_ID)
                        .reason("이동 유형 보정")
                        .idempotencyKey("concurrent-correction")
                        .build()),
            () -> appSearchPathService.updateStatus(request));

    // then: 구간 자체의 버전과 경로 버전을 구분하며 실제 보정·상태·이벤트를 보존한다.
    SearchPathQueryRowServiceResponse saved = queryPath();
    assertThat(response.getVersion()).isEqualTo(expectedVersion);
    assertThat(saved.getVersion()).isEqualTo(expectedVersion);
    assertThat(saved.getStatus()).isEqualTo(expectedStatus);
    assertThat(saved.getGeometry()).hasSize(2);
    assertThat(searchPathService.findByQuery(INCIDENT_ID, OP_ID, ACCOUNT_ID).get(0).getSegments())
        .singleElement()
        .satisfies(
            segment ->
                assertThat(segment.getLastChangedPathVersion())
                    .isEqualTo((long) expectedVersion - 1));
    assertThat(saved.getSegments())
        .singleElement()
        .satisfies(
            segment -> {
              assertThat(segment.getId()).isEqualTo(segmentId);
              assertThat(segment.getVersion()).isEqualTo(2);
              assertThat(segment.getMovementType()).isEqualTo(MovementType.VEHICLE);
            });
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT (payload->>'version')::bigint FROM event_dispatch_job ORDER BY insertion_order",
                Long.class))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, expectedVersion).boxed().toList());
    assertThat(idempotencyStatus("concurrent-correction")).isEqualTo("COMPLETED");
    assertThat(idempotencyStatus("status-after-correction")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("일시정지와 재개가 겹치면 일시정지 완료 후 최신 상태에서 재개한다")
  void resume_during_pause_uses_committed_status() throws Exception {
    // given: 기록 중인 경로에 서로 다른 일시정지·재개 요청을 보낸다.
    appSearchPathService.start(startRequest("start-pause-resume"));

    // when: 일시정지가 커밋되기 전에 재개를 시작한다.
    SearchPathStatusUpdateServiceResponse response =
        commitFirstWriteWhileSecondWaits(
            () ->
                appSearchPathService.updateStatus(
                    statusRequest(
                        SearchPathLifecycleAction.PAUSE,
                        STARTED_AT.plusSeconds(10),
                        "concurrent-pause")),
            () ->
                appSearchPathService.updateStatus(
                    statusRequest(
                        SearchPathLifecycleAction.RESUME,
                        STARTED_AT.plusSeconds(20),
                        "concurrent-resume")));

    // then: 오래된 RECORDING 상태를 보고 거절하지 않고 버전 3으로 재개한다.
    assertThat(response.getVersion()).isEqualTo(3);
    assertThat(response.getStatus()).isEqualTo(SearchPathStatus.RECORDING);
    assertThat(queryPath().getVersion()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM search_path_lifecycle_event ORDER BY version",
                String.class))
        .containsExactly("STARTED", "PAUSED", "RESUMED");
  }

  @Test
  @DisplayName("종료와 일시정지가 겹쳐도 종료된 경로를 일시정지 상태로 되돌리지 않는다")
  void pause_during_end_keeps_terminal_status_and_rolls_back_rejected_request() {
    // given: 기록 중인 경로에 종료·일시정지 요청이 겹친다.
    appSearchPathService.start(startRequest("start-end-pause"));

    // when: 먼저 시작한 종료가 완료되면 대기한 일시정지 요청은 거부된다.
    assertThatThrownBy(
            () ->
                commitFirstWriteWhileSecondWaits(
                    () ->
                        appSearchPathService.updateStatus(
                            statusRequest(
                                SearchPathLifecycleAction.END,
                                STARTED_AT.plusSeconds(10),
                                "concurrent-end")),
                    () ->
                        appSearchPathService.updateStatus(
                            statusRequest(
                                SearchPathLifecycleAction.PAUSE,
                                STARTED_AT.plusSeconds(20),
                                "rejected-pause"))))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            SearchPathGuardException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("write_conflict"));

    // then: 종료 상태·시각을 유지하고 거부한 요청의 이력·이벤트·멱등성 예약은 남기지 않는다.
    SearchPathQueryRowServiceResponse saved = queryPath();
    assertThat(saved.getStatus()).isEqualTo(SearchPathStatus.ENDED);
    assertThat(saved.getVersion()).isEqualTo(2);
    assertThat(saved.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(10));
    assertThat(rowCount("search_path_lifecycle_event")).isEqualTo(2);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(2);
    assertThat(rowCount("idempotency_record")).isEqualTo(2);
  }

  @ParameterizedTest(name = "좌표 저장보다 먼저 처리할 상태 변경={0}")
  @EnumSource(
      value = SearchPathLifecycleAction.class,
      names = {"PAUSE", "END"})
  @DisplayName("일시정지나 종료가 먼저 완료되면 대기 중인 좌표 저장을 거부한다")
  void append_during_status_change_rechecks_recording_status(SearchPathLifecycleAction action) {
    // given: 좌표 저장보다 상태 변경이 먼저 경로를 변경한다.
    appSearchPathService.start(startRequest("start-status-append"));

    // when: 상태 변경의 커밋을 기다린 좌표 요청은 write_conflict로 거부된다.
    assertThatThrownBy(
            () ->
                commitFirstWriteWhileSecondWaits(
                    () ->
                        appSearchPathService.updateStatus(
                            statusRequest(action, STARTED_AT.plusSeconds(10), "status-first")),
                    () -> searchPathService.appendPoints(batchRequest(0))))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            SearchPathApiException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("write_conflict"));

    // then: 거부한 좌표·이벤트·멱등성 예약이 저장되지 않는다.
    assertThat(queryPath().getVersion()).isEqualTo(2);
    assertThat(rowCount("search_path_gps_point")).isZero();
    assertThat(rowCount("event_dispatch_job")).isEqualTo(2);
    assertThat(rowCount("idempotency_record")).isEqualTo(2);
  }

  @Test
  @DisplayName("수색 경로를 시작하면 계정의 근무교대에 연결해 저장한다")
  void start_persists_path_for_account_duty_shift() {
    // given/when: 계정의 진행 중인 근무교대에서 수색 경로를 시작한다.
    SearchPathStartServiceResponse response =
        appSearchPathService.start(startRequest("idem-path-start"));

    // then: 경로 소유자·근무교대·기록 상태를 저장한다.
    SearchPath found = searchPathMapper.findPathById(PATH_ID).orElseThrow();
    assertThat(response.getId()).isEqualTo(PATH_ID);
    assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(found.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(found.getStatus()).isEqualTo(SearchPathStatus.RECORDING);
  }

  @Test
  @DisplayName("accountId가 없으면 업무폰을 사용자 식별자로 대신 사용하지 않는다")
  void start_without_account_rejects_request() {
    // given: 기록 주체인 계정이 빠진 요청이다.
    SearchPathStartServiceRequest request =
        SearchPathStartServiceRequest.builder()
            .searchPathId(PATH_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .startedAt(STARTED_AT)
            .idempotencyKey("idem-path-start-missing-account")
            .build();

    // when/then: 경로를 시작하지 않고 기존 오류 코드로 거부한다.
    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("channel_not_allowed"));
  }

  @Test
  @DisplayName("현재 수색 차수가 없으면 수색 경로 시작을 거부한다")
  void start_without_current_operational_period_rejects_request() {
    // given: 현재 수색 차수가 없는 사건이다.
    SearchPathStartServiceRequest request =
        startRequest("idem-path-start-op-required").toBuilder()
            .incidentId(UUID.randomUUID())
            .build();

    // when/then: 현재 차수가 없으므로 시작을 거부한다.
    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_required"));
  }

  @Test
  @DisplayName("요청한 수색 차수가 현재 수색 차수와 다르면 시작을 거부한다")
  void start_with_other_operational_period_rejects_request() {
    // given: 현재 수색 차수와 다른 차수로 시작을 요청한다.
    SearchPathStartServiceRequest request =
        startRequest("idem-path-start-op-mismatch").toBuilder()
            .opId(BoundaryAreaFixtures.OP2_ID)
            .build();

    // when/then: 요청 차수가 현재 차수와 다르므로 시작을 거부한다.
    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_mismatch"));
  }

  @Test
  @DisplayName("일시정지와 재개 및 종료를 생명주기 이력으로 저장한다")
  void update_status_persists_lifecycle_history_and_events() {
    // given: 시작한 경로를 새 서비스 인스턴스에서도 읽는다.
    appSearchPathService.start(startRequest("idem-path-start-lifecycle"));
    AppSearchPathService restartedService = restartedService();

    // when: 경로를 일시정지·재개·종료한다.
    restartedService.updateStatus(
        statusRequest(
            SearchPathLifecycleAction.PAUSE, STARTED_AT.plusSeconds(30), "idem-path-pause"));
    restartedService.updateStatus(
        statusRequest(
            SearchPathLifecycleAction.RESUME, STARTED_AT.plusSeconds(60), "idem-path-resume"));
    restartedService.updateStatus(
        statusRequest(SearchPathLifecycleAction.END, STARTED_AT.plusSeconds(90), "idem-path-end"));

    // then: 각 상태 변경의 이력과 전송할 이벤트를 저장한다.
    SearchPath found = searchPathMapper.findPathById(PATH_ID).orElseThrow();
    List<Map<String, Object>> lifecycleRows =
        jdbcTemplate.queryForList(
            """
            SELECT event_type, version
            FROM search_path_lifecycle_event
            WHERE search_path_id = ?::uuid
            ORDER BY version
            """,
            PATH_ID.toString());
    List<String> stagedEventTypes =
        jdbcTemplate.queryForList(
            """
            SELECT event_type
            FROM event_dispatch_job
            WHERE source_entity_id = ?::uuid
            ORDER BY created_at, event_type
            """,
            String.class,
            PATH_ID.toString());

    assertThat(found.getStatus()).isEqualTo(SearchPathStatus.ENDED);
    assertThat(found.getVersion()).isEqualTo(4L);
    assertThat(lifecycleRows)
        .extracting(row -> row.get("event_type"))
        .containsExactly("STARTED", "PAUSED", "RESUMED", "ENDED");
    assertThat(stagedEventTypes)
        .containsExactly(
            "SEARCH_PATH_STARTED",
            "SEARCH_PATH_PAUSED",
            "SEARCH_PATH_RESUMED",
            "SEARCH_PATH_ENDED");
  }

  @Test
  @DisplayName("같은 멱등성 요청을 다시 보내면 경로를 한 번만 저장한다")
  void repeated_start_reuses_response_without_duplicate_path() {
    // given: 경로 시작을 완료한 멱등성 요청이다.
    SearchPathStartServiceRequest request = startRequest("idem-path-start-replay");
    SearchPathStartServiceResponse first = appSearchPathService.start(request);

    // when: 새 서비스 인스턴스에서 같은 요청을 다시 처리한다.
    SearchPathStartServiceResponse replayed = restartedService().start(request);

    // then: 기존 응답을 반환하고 경로는 한 번만 저장한다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(first);
    assertThat(rowCount("search_path")).isEqualTo(1);
    assertThat(idempotencyStatus("idem-path-start-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("같은 멱등성 키로 다른 요청을 보내면 거부한다")
  void start_with_reused_key_and_changed_body_rejects_request() {
    // given: 완료한 시작 요청과 같은 키에 다른 시작 시각을 사용한다.
    appSearchPathService.start(startRequest("idem-path-start-mismatch"));
    SearchPathStartServiceRequest changed =
        startRequest("idem-path-start-mismatch").toBuilder()
            .startedAt(STARTED_AT.plusSeconds(1))
            .build();

    // when/then: 변경된 요청을 거부하고 기존 경로만 유지한다.
    assertThatThrownBy(() -> appSearchPathService.start(changed))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(rowCount("search_path")).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 계정에 진행 중인 경로가 있으면 다른 경로 시작을 거부한다")
  void start_with_another_active_path_rejects_request() {
    // given: 같은 계정에 이미 진행 중인 경로가 있다.
    appSearchPathService.start(startRequest("idem-path-start-first"));
    SearchPathStartServiceRequest anotherPath =
        startRequest("idem-path-start-another").toBuilder().searchPathId(OTHER_PATH_ID).build();

    // when/then: 다른 경로의 시작을 거부하고 기존 경로만 유지한다.
    assertThatThrownBy(() -> appSearchPathService.start(anotherPath))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("write_conflict"));
    assertThat(rowCount("search_path")).isEqualTo(1);
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
                // SQL 문자열이나 sleep 시간 대신 실제 두 DB 연결의 잠금 대기로 실행 순서를 고정한다.
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

  private SearchPathQueryRowServiceResponse queryPath() {
    return searchPathService
        .query(
            SearchPathQueryServiceRequest.builder()
                .incidentId(INCIDENT_ID)
                .opId(OP_ID)
                .accountId(ACCOUNT_ID)
                .build())
        .getPaths()
        .get(0);
  }

  private SearchPathPointsAppendServiceRequest batchRequest(int offset) {
    return SearchPathPointsAppendServiceRequest.builder()
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .accountId(ACCOUNT_ID)
        .pathId(PATH_ID)
        .clockOffsetMs(0L)
        .idempotencyKey("concurrent-append-" + offset)
        .points(
            IntStream.range(offset, offset + 2)
                .mapToObj(
                    i ->
                        SearchPathPointServiceRequest.builder()
                            .pointId("concurrent-point-" + i)
                            .lon(
                                new BigDecimal("126.913000")
                                    .add(
                                        new BigDecimal("0.000010").multiply(BigDecimal.valueOf(i))))
                            .lat(new BigDecimal("35.162000"))
                            .speedMps(BigDecimal.ONE)
                            .horizontalAccuracyM(5)
                            .locationProvider("gps")
                            .clientTs(
                                OffsetDateTime.parse("2026-04-28T09:00:00+09:00")
                                    .plusNanos(i * 2_500_000_000L))
                            .elapsedRealtimeNanos(10_000_000_000L + i * 2_500_000_000L)
                            .build())
                .toList())
        .build();
  }

  private SearchPathStartServiceRequest startRequest(String idempotencyKey) {
    return SearchPathStartServiceRequest.builder()
        .searchPathId(PATH_ID)
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .accountId(ACCOUNT_ID)
        .startedAt(STARTED_AT)
        .clockOffsetMs(0)
        .idempotencyKey(idempotencyKey)
        .build();
  }

  private SearchPathStatusUpdateServiceRequest statusRequest(
      SearchPathLifecycleAction action, Instant clientTs, String idempotencyKey) {
    return SearchPathStatusUpdateServiceRequest.builder()
        .searchPathId(PATH_ID)
        .accountId(ACCOUNT_ID)
        .action(action)
        .clientTs(clientTs)
        .clockOffsetMs(0)
        .idempotencyKey(idempotencyKey)
        .build();
  }

  private AppSearchPathService restartedService() {
    return new AppSearchPathService(
        operationalPeriodQuery,
        searchPathEventPublisher,
        searchPathMapper,
        idempotentResponseCache);
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
}
