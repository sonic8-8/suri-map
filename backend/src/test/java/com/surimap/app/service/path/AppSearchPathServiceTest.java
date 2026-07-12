package com.surimap.app.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.SearchPathStartServiceRequest;
import com.surimap.app.service.path.request.SearchPathStatusUpdateServiceRequest;
import com.surimap.app.service.path.response.SearchPathStartServiceResponse;
import com.surimap.domain.path.SearchPath;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("AppSearchPath service")
@Sql(scripts = "/sql/path/search-path-context.sql")
class AppSearchPathServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final UUID OTHER_PATH_ID =
      UUID.fromString("71000000-0000-0000-0000-000000002622");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private AppSearchPathService appSearchPathService;
  @Autowired private OperationalPeriodQuery operationalPeriodQuery;
  @Autowired private SearchPathEventPublisher searchPathEventPublisher;
  @Autowired private SearchPathMapper searchPathMapper;
  @Autowired private IdempotentResponseCache idempotentResponseCache;

  @Test
  @DisplayName("수색 경로를 시작하면 계정의 근무교대에 연결해 저장한다")
  void startPersistsPathForAccountDutyShift() {
    SearchPathStartServiceResponse response =
        appSearchPathService.start(startRequest("idem-path-start"));

    SearchPath found = searchPathMapper.findPathById(PATH_ID).orElseThrow();
    assertThat(response.getId()).isEqualTo(PATH_ID);
    assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(found.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(found.getStatus()).isEqualTo(SearchPathStatus.RECORDING);
  }

  @Test
  @DisplayName("accountId가 없으면 업무폰을 사용자 식별자로 대신 사용하지 않는다")
  void startRejectsMissingAccountId() {
    SearchPathStartServiceRequest request =
        SearchPathStartServiceRequest.builder()
            .searchPathId(PATH_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .startedAt(STARTED_AT)
            .idempotencyKey("idem-path-start-missing-account")
            .build();

    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("channel_not_allowed"));
  }

  @Test
  @DisplayName("현재 수색 차수가 없으면 수색 경로 시작을 거부한다")
  void startRejectsMissingCurrentOperationalPeriod() {
    SearchPathStartServiceRequest request =
        startRequest("idem-path-start-op-required").toBuilder()
            .incidentId(UUID.randomUUID())
            .build();

    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_required"));
  }

  @Test
  @DisplayName("요청한 수색 차수가 현재 수색 차수와 다르면 시작을 거부한다")
  void startRejectsOperationalPeriodMismatch() {
    SearchPathStartServiceRequest request =
        startRequest("idem-path-start-op-mismatch").toBuilder()
            .opId(BoundaryAreaFixtures.OP2_ID)
            .build();

    assertThatThrownBy(() -> appSearchPathService.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("op_mismatch"));
  }

  @Test
  @DisplayName("일시정지와 재개 및 종료를 생명주기 이력으로 저장한다")
  void updateStatusPersistsLifecycleEvents() {
    appSearchPathService.start(startRequest("idem-path-start-lifecycle"));
    AppSearchPathService restartedService = restartedService();

    restartedService.updateStatus(
        statusRequest(
            SearchPathLifecycleAction.PAUSE, STARTED_AT.plusSeconds(30), "idem-path-pause"));
    restartedService.updateStatus(
        statusRequest(
            SearchPathLifecycleAction.RESUME, STARTED_AT.plusSeconds(60), "idem-path-resume"));
    restartedService.updateStatus(
        statusRequest(SearchPathLifecycleAction.END, STARTED_AT.plusSeconds(90), "idem-path-end"));

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
  void startReplaysSameIdempotentRequest() {
    SearchPathStartServiceRequest request = startRequest("idem-path-start-replay");
    SearchPathStartServiceResponse first = appSearchPathService.start(request);

    SearchPathStartServiceResponse replayed = restartedService().start(request);

    assertThat(replayed).usingRecursiveComparison().isEqualTo(first);
    assertThat(rowCount("search_path")).isEqualTo(1);
    assertThat(idempotencyStatus("idem-path-start-replay")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("같은 멱등성 키로 다른 요청을 보내면 거부한다")
  void startRejectsSameIdempotencyKeyWithDifferentBody() {
    appSearchPathService.start(startRequest("idem-path-start-mismatch"));
    SearchPathStartServiceRequest changed =
        startRequest("idem-path-start-mismatch").toBuilder()
            .startedAt(STARTED_AT.plusSeconds(1))
            .build();

    assertThatThrownBy(() -> appSearchPathService.start(changed))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(rowCount("search_path")).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 계정에 진행 중인 경로가 있으면 다른 경로 시작을 거부한다")
  void startRejectsAnotherActivePathForSameAccount() {
    appSearchPathService.start(startRequest("idem-path-start-first"));
    SearchPathStartServiceRequest anotherPath =
        startRequest("idem-path-start-another").toBuilder().searchPathId(OTHER_PATH_ID).build();

    assertThatThrownBy(() -> appSearchPathService.start(anotherPath))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            exception ->
                assertThat(((SearchPathGuardException) exception).errorCode())
                    .isEqualTo("write_conflict"));
    assertThat(rowCount("search_path")).isEqualTo(1);
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
