package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.surimap.api.service.marker.MarkerService;
import com.surimap.api.service.marker.response.MarkersServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse.MarkerServiceResponse;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import com.surimap.domain.marker.MarkerNotificationMapper;
import com.surimap.domain.marker.MarkerNotificationMapper.NotificationRow;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerType;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.dutyshift.DutyShift;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.AssignmentRow;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.policephone.PolicePhoneFreshnessStatus;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import com.surimap.retention.purge.IncidentDataPurgeRun;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.IncidentDataPurgeStore;
import com.surimap.retention.purge.LocalPurgeState;
import com.surimap.retention.purge.PurgeEnvironmentPolicy;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class IncidentBoardSourceRowCollectorIntegrationTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID OP_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID PREVIOUS_OP_ID =
      UUID.fromString("20000000-0000-4000-8000-000000000000");
  private static final UUID FUTURE_OP_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final UUID AREA_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("30000000-0000-4000-8000-000000000099");
  private static final UUID PATH_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("40000000-0000-4000-8000-000000000101");
  private static final UUID MARKER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
  private static final UUID NOTIFICATION_ID =
      UUID.fromString("51000000-0000-4000-8000-000000000001");
  private static final UUID NOTIFICATION_EVENT_ID =
      UUID.fromString("52000000-0000-4000-8000-000000000001");
  private static final UUID PHONE_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
  private static final UUID ACCOUNT_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-4000-8000-000000000001");
  private static final UUID ACTIVE_DUTY_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-4000-8000-000000000002");
  private static final UUID ASSIGNED_BY_ACCOUNT_ID =
      UUID.fromString("70000000-0000-4000-8000-000000000002");
  private static final UUID ASSIGNMENT_ID = UUID.fromString("71000000-0000-4000-8000-000000000001");
  private static final String ACCOUNT_DISPLAY_NAME = "종로 지구대 순찰차";
  private static final UUID MEMO_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
  private static final UUID SUMMARY_ID = UUID.fromString("90000000-0000-4000-8000-000000000001");
  private static final UUID PURGE_RUN_ID = UUID.fromString("91000000-0000-4000-8000-000000000001");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Test
  @DisplayName("여러 상황판 슬롯을 요청하면, 각 조회 결과와 마커 알림 정보를 모아 반환한다")
  void collects_available_source_owner_rows_for_board_slots() {
    // given: 각 슬롯의 조회 결과와 마커 알림 조회 결과를 준비한다.
    MarkerService markerService = createMarkerService();
    MarkerNotificationMapper notificationMapper = createNotificationMapper();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(new FakeSearchAreaQuery()),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            markerService,
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(new FakeIncidentReadMapper()),
            provider(null),
            provider(notificationMapper));

    // when: 마커 알림을 포함한 상황판 슬롯을 조회한다.
    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID,
                List.of(OP_ID),
                List.of(
                    "overall_search_area",
                    "area",
                    "path",
                    "police_phone_freshness",
                    "marker",
                    "marker_notification",
                    "package_badge",
                    "op_toggle",
                    "op_history",
                    "handover_memo",
                    "handover_status",
                    "search_history_summary"),
                null));

    // then: 요청한 슬롯을 모으고 마커 알림의 데이터·식별자·이벤트 정보를 보존한다.
    assertThat(snapshot.activeOpId()).isEqualTo(OP_ID);
    assertThat(snapshot.selectedOpIds()).containsExactly(OP_ID);
    assertThat(snapshot.geometryHash()).isNotBlank();
    assertQueriedMarkerOpIds(markerService, OP_ID);
    assertThat(snapshot.sourceRows())
        .extracting(BoardSourceRow::slot)
        .contains(
            "overall_search_area",
            "area",
            "path",
            "police_phone_freshness",
            "marker",
            "marker_notification",
            "package_badge",
            "op_toggle",
            "op_history",
            "handover_memo",
            "handover_status",
            "search_history_summary");
    assertThat(row(snapshot, "overall_search_area").sourceSpec()).isEqualTo("S2");
    assertThat(row(snapshot, "path").sourceSpec()).isEqualTo("S3-1");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> pathSegments =
        (List<Map<String, Object>>) row(snapshot, "path").payload().get("segments");
    assertThat(pathSegments).hasSize(2);
    assertThat(pathSegments.get(0))
        .containsEntry("movementType", "VEHICLE")
        .containsKey("geometry")
        .containsKey("startedAt")
        .containsKey("endedAt");
    assertThat(row(snapshot, "police_phone_freshness").sourceSpec()).isEqualTo("S1-2");
    assertThat(row(snapshot, "marker").sourceSpec()).isEqualTo("S5");
    BoardSourceRow notification = row(snapshot, "marker_notification");
    assertThat(notification.sourceSpec()).isEqualTo("S5");
    assertThat(notification.sourceResponseId()).isEqualTo(NOTIFICATION_ID.toString());
    assertThat(notification.boardRowId()).isEqualTo("board-toast-" + NOTIFICATION_ID);
    assertThat(notification.status()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(notification.version()).isEqualTo(12L);
    assertThat(notification.sequence()).isEqualTo(12L);
    assertThat(notification.latestEventId()).isEqualTo(NOTIFICATION_EVENT_ID.toString());
    assertThat(notification.sourceHash())
        .isEqualTo("sha256:9351a678d4ff485f72ab12c7cb5b38da7a1cc8010034e15a77313c2c6a5f050c");
    assertThat(notification.payload())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "type", "SUPPORT_REQUEST_CREATED",
                "markerId", MARKER_ID.toString(),
                "incidentId", INCIDENT_ID.toString(),
                "opId", OP_ID.toString(),
                "policePhoneId", PHONE_ID.toString(),
                "createdAt", STARTED_AT));
    verify(notificationMapper).findNotificationRowsByIncidentId(INCIDENT_ID);
    assertThat(row(snapshot, "package_badge").sourceSpec()).isEqualTo("S7");
    assertThat(row(snapshot, "op_toggle").sourceSpec()).isEqualTo("S8");
    assertThat(row(snapshot, "handover_memo").payload())
        .containsEntry("content", "memo for next team");
    assertThat(row(snapshot, "handover_status").payload()).containsEntry("handoverStatus", "READY");
    assertThat(row(snapshot, "search_history_summary").payload())
        .containsEntry("summaryText", "searched ridge trail and checked shelter");
  }

  @Test
  @DisplayName("마커 알림에 이벤트가 없으면, 기존 규칙의 대체 이벤트 ID를 사용한다")
  void collect_markerNotificationWithoutEvent_preservesFallbackEventId() {
    // given: 마커 알림 조회 결과에 최근 이벤트 ID가 없다.
    MarkerNotificationMapper mapper = createNotificationMapper();
    when(mapper.findNotificationRowsByIncidentId(INCIDENT_ID))
        .thenReturn(List.of(createNotificationRow(null)));
    DefaultIncidentBoardSourceRowCollector collector =
        createNotificationCollector(provider(mapper));

    // when: 마커 알림 슬롯을 조회한다.
    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID, List.of(), List.of("marker_notification"), null));

    // then: 슬롯 이름 변경 전과 같은 대체 이벤트 ID를 유지한다.
    assertThat(row(snapshot, "marker_notification").latestEventId())
        .isEqualTo("evt-s5-toast-" + NOTIFICATION_ID + "-v12");
  }

  @Test
  @DisplayName("마커 알림 슬롯을 요청하지 않으면, 알림 Mapper를 호출하지 않는다")
  void collect_markerNotificationSlotExcluded_skipsNotificationQuery() {
    // given: 마커 알림을 조회할 수 있는 Mapper가 있다.
    MarkerNotificationMapper mapper = createNotificationMapper();
    DefaultIncidentBoardSourceRowCollector collector =
        createNotificationCollector(provider(mapper));

    // when: 알림이 아닌 마커 슬롯만 요청한다.
    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(INCIDENT_ID, List.of(), List.of("marker"), null));

    // then: 알림을 조회하지 않으며 반환 슬롯에도 포함하지 않는다.
    verifyNoInteractions(mapper);
    assertThat(snapshot.sourceRows())
        .extracting(BoardSourceRow::slot)
        .doesNotContain("marker_notification");
  }

  @Test
  @DisplayName("마커 알림 조회가 제공되지 않으면, 알림 슬롯 요청에도 빈 결과를 반환한다")
  void collect_markerNotificationMapperMissing_returnsEmptyRows() {
    // given: Provider 자체가 없거나 Provider가 제공하는 Mapper가 없다.
    List<ObjectProvider<MarkerNotificationMapper>> providers =
        java.util.Arrays.asList(null, provider(null));
    for (ObjectProvider<MarkerNotificationMapper> mapperProvider : providers) {
      DefaultIncidentBoardSourceRowCollector collector =
          createNotificationCollector(mapperProvider);

      // when: 마커 알림 슬롯을 요청한다.
      IncidentBoardSourceRowSnapshot snapshot =
          collector.collect(
              new BoardSourceRowContext(
                  INCIDENT_ID, List.of(), List.of("marker_notification"), null));

      // then: 기존과 같이 오류 없이 알림 조회 결과를 비워 둔다.
      assertThat(snapshot.sourceRows()).isEmpty();
    }
  }

  @Test
  @DisplayName("area rows include area level and active assignment context")
  void area_rows_include_area_level_and_active_assignment_context() {
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(new FakeSearchAreaQuery()),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            createMarkerService(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(new FakeIncidentReadMapper()),
            provider(null),
            provider(null),
            provider(new FakeSearchAreaAssignmentQuery()));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(INCIDENT_ID, List.of(OP_ID), List.of("area"), null));

    Map<String, Object> payload = row(snapshot, "area").payload();
    assertThat(payload).containsEntry("areaLevel", "TEAM");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> assignedAccounts =
        (List<Map<String, Object>>) payload.get("assignedAccounts");
    assertThat(assignedAccounts).hasSize(1);
    assertThat(assignedAccounts.get(0))
        .containsEntry("assignmentId", ASSIGNMENT_ID.toString())
        .containsEntry("accountId", ACCOUNT_ID.toString())
        .containsEntry("policePhoneId", POLICE_PHONE_ID.toString())
        .containsEntry("displayName", ACCOUNT_DISPLAY_NAME)
        .containsEntry("assignedByAccountId", ASSIGNED_BY_ACCOUNT_ID.toString())
        .containsEntry("status", "ACTIVE");
  }

  @Test
  @DisplayName("area assignment policePhoneId follows active duty shift for the selected OP")
  void area_assignment_police_phone_id_follows_active_duty_shift_for_selected_op() {
    DutyShiftMapper dutyShiftMapper = mock(DutyShiftMapper.class);
    DutyShift activeShift = new DutyShift();
    activeShift.setPolicePhoneId(ACTIVE_DUTY_POLICE_PHONE_ID);
    when(dutyShiftMapper.findByFilters(INCIDENT_ID, OP_ID, null, ACCOUNT_ID, "ACTIVE"))
        .thenReturn(List.of(activeShift));
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(new FakeSearchAreaQuery()),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            createMarkerService(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(null),
            provider(new FakeIncidentReadMapper()),
            provider(null),
            provider(null),
            provider(dutyShiftMapper),
            provider(new FakeSearchAreaAssignmentQuery()));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(INCIDENT_ID, List.of(OP_ID), List.of("area"), null));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> assignedAccounts =
        (List<Map<String, Object>>) row(snapshot, "area").payload().get("assignedAccounts");
    assertThat(assignedAccounts.get(0))
        .containsEntry("policePhoneId", ACTIVE_DUTY_POLICE_PHONE_ID.toString());
  }

  @Test
  @DisplayName("uses includeSlots before reading optional slot sources")
  void uses_include_slots_before_reading_optional_slot_sources() {
    MarkerService markerService = createMarkerService();
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            markerService,
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper());

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID, List.of(OP_ID), List.of("marker", "package_badge"), null));

    assertThat(snapshot.sourceRows())
        .extracting(BoardSourceRow::slot)
        .containsOnly("marker", "package_badge");
    assertQueriedMarkerOpIds(markerService, OP_ID);
    assertThat(searchAreaQuery.overallCalls()).isZero();
    assertThat(searchAreaQuery.byIncidentCalls()).isZero();
  }

  @Test
  @DisplayName("sinceVersion does not filter rows from a full board snapshot reload")
  void since_version_does_not_filter_rows_from_full_board_snapshot_reload() {
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            createMarkerService(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper());

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(INCIDENT_ID, List.of(OP_ID), List.of("area"), 1200L));

    assertThat(snapshot.sourceRows()).extracting(BoardSourceRow::slot).containsExactly("area");
    assertThat(row(snapshot, "area").version()).isEqualTo(5L);
    assertThat(searchAreaQuery.byOpFilters())
        .singleElement()
        .extracting(SearchAreaFilters::minVersion)
        .isNull();
  }

  @Test
  @DisplayName("area slot requests cancelled parent rows so split TEAM children stay attached")
  void area_slot_requests_cancelled_parent_rows_for_split_tree_rendering() {
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            createMarkerService(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper());

    collector.collect(
        new BoardSourceRowContext(INCIDENT_ID, List.of(OP_ID), List.of("area"), null));

    assertThat(searchAreaQuery.byOpFilters())
        .singleElement()
        .extracting(SearchAreaFilters::includeCancelled)
        .isEqualTo(true);
  }

  @Test
  @DisplayName(
      "default board scope keeps routes and areas on current OP while accumulating markers through current OP")
  void default_board_scope_accumulates_markers_only_through_current_op() {
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery();
    MarkerService markerService = createMarkerService();
    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(searchAreaQuery),
            provider(searchPathService()),
            provider(new FakePolicePhoneFreshnessQuery()),
            markerService,
            new FakePackageQuery(),
            new MultiOpOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper());

    collector.collect(
        new BoardSourceRowContext(INCIDENT_ID, List.of(), List.of("area", "path", "marker"), null));

    assertThat(searchAreaQuery.byOpIds()).containsExactly(OP_ID);
    assertQueriedMarkerOpIds(markerService, PREVIOUS_OP_ID, OP_ID);
  }

  @Test
  @DisplayName("collects sanitized incident_terminal row from closed incident and purge source")
  void collects_sanitized_incident_terminal_row_from_closed_incident_and_purge_source() {
    IncidentMapper incidentMapper = mock(IncidentMapper.class);
    IncidentDataPurgeStore purgeStore = mock(IncidentDataPurgeStore.class);
    IncidentRecord incident = new IncidentRecord();
    incident.setId(INCIDENT_ID);
    incident.setStatus("CLOSED");
    incident.setClosedAt(STARTED_AT);
    incident.setVersion(12L);
    when(incidentMapper.findByIncidentId(INCIDENT_ID)).thenReturn(Optional.of(incident));
    when(purgeStore.findByIncidentId(INCIDENT_ID))
        .thenReturn(
            Optional.of(
                new IncidentDataPurgeRun(
                    PURGE_RUN_ID,
                    INCIDENT_ID,
                    IncidentDataPurgeStatus.COMPLETED,
                    STARTED_AT,
                    STARTED_AT.plusSeconds(60),
                    STARTED_AT.plusSeconds(30),
                    null,
                    13L,
                    PurgeEnvironmentPolicy.PRODUCTION_IMMEDIATE,
                    LocalPurgeState.LOCAL_PURGED)));

    DefaultIncidentBoardSourceRowCollector collector =
        new DefaultIncidentBoardSourceRowCollector(
            provider(null),
            provider(null),
            provider(new FakePolicePhoneFreshnessQuery()),
            createMarkerService(),
            new FakePackageQuery(),
            new FakeOperationalPeriodQuery(),
            new FakeHandoverMemoQuery(),
            new FakeSummaryMapper(),
            provider(incidentMapper),
            provider(null),
            provider(purgeStore),
            provider(null));

    IncidentBoardSourceRowSnapshot snapshot =
        collector.collect(
            new BoardSourceRowContext(
                INCIDENT_ID,
                List.of(OP_ID),
                List.of("incident_terminal", "police_phone_freshness"),
                null));

    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    INCIDENT_ID.toString(),
                    "board-response-test",
                    0L,
                    OffsetDateTime.parse("2026-04-28T09:00:00+09:00"),
                    OP_ID.toString(),
                    List.of(OP_ID.toString()),
                    snapshot.geometryHash(),
                    snapshot.sourceRows()));

    @SuppressWarnings("unchecked")
    Map<String, Object> terminal = (Map<String, Object>) board.slots().get("incident_terminal");
    assertThat(terminal)
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("terminalStatus", "PURGED")
        .containsEntry("writeDisabledReason", "purged")
        .containsEntry("localPurgeState", "completed");
    assertThat(terminal.keySet())
        .doesNotContain("missingPerson", "missing_person", "latestLocation", "packageReloadUrl");
    assertThat(board.slots().get("police_phone_freshness")).isEqualTo(List.of());
    assertThat(board.slotSources().get("incident_terminal")).singleElement();
  }

  private static BoardSourceRow row(IncidentBoardSourceRowSnapshot snapshot, String slot) {
    return snapshot.sourceRows().stream()
        .filter(row -> row.slot().equals(slot))
        .findFirst()
        .orElseThrow();
  }

  private static SearchPathService searchPathService() {
    SearchPathService service = mock(SearchPathService.class);
    when(service.query(any(SearchPathQueryServiceRequest.class))).thenReturn(pathQueryResponse());
    return service;
  }

  private static SearchPathQueryServiceResponse pathQueryResponse() {
    List<List<Double>> geometry =
        List.of(
            List.of(126.910000, 35.162000),
            List.of(126.911000, 35.162100),
            List.of(126.912000, 35.162200),
            List.of(126.913000, 35.162300),
            List.of(126.914000, 35.162400),
            List.of(126.915000, 35.162500));
    return SearchPathQueryServiceResponse.builder()
        .paths(
            List.of(
                SearchPathQueryRowServiceResponse.builder()
                    .id(PATH_ID)
                    .incidentId(INCIDENT_ID)
                    .opId(OP_ID)
                    .dutyShiftId(DUTY_SHIFT_ID)
                    .accountId(ACCOUNT_ID)
                    .status(SearchPathStatus.RECORDING)
                    .startedAt(STARTED_AT)
                    .version(2L)
                    .geometry(geometry)
                    .segments(
                        List.of(
                            pathSegment(
                                "segment-vehicle",
                                MovementType.VEHICLE,
                                geometry.subList(0, 3),
                                "2026-04-28T09:00:00+09:00",
                                "2026-04-28T09:00:10+09:00"),
                            pathSegment(
                                "segment-foot",
                                MovementType.FOOT,
                                geometry.subList(3, 6),
                                "2026-04-28T09:00:15+09:00",
                                "2026-04-28T09:00:25+09:00")))
                    .excludedPoints(List.of())
                    .build()))
        .build();
  }

  private static SearchPathQuerySegmentServiceResponse pathSegment(
      String id,
      MovementType movementType,
      List<List<Double>> geometry,
      String startedAt,
      String endedAt) {
    return SearchPathQuerySegmentServiceResponse.builder()
        .id(id)
        .version(1L)
        .movementType(movementType)
        .movementTypeSource(MovementTypeSource.AUTO)
        .geometry(geometry)
        .startedAt(OffsetDateTime.parse(startedAt))
        .endedAt(OffsetDateTime.parse(endedAt))
        .build();
  }

  private static <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject() {
        return value;
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public Stream<T> stream() {
        return value == null ? Stream.empty() : Stream.of(value);
      }
    };
  }

  private static GeoJsonPolygon polygon() {
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                List.of(new BigDecimal("126.910000"), new BigDecimal("35.162000")),
                List.of(new BigDecimal("126.910000"), new BigDecimal("35.162000")),
                List.of(new BigDecimal("126.910000"), new BigDecimal("35.162000")),
                List.of(new BigDecimal("126.910000"), new BigDecimal("35.162000")),
                List.of(new BigDecimal("126.910000"), new BigDecimal("35.162000")))));
  }

  private static class FakeSearchAreaQuery implements SearchAreaQuery {
    @Override
    public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
      return Optional.of(
          new OverallSearchAreaResult(
              OVERALL_AREA_ID,
              incidentId,
              "ACTIVE",
              3L,
              polygon(),
              List.of(
                  new BigDecimal("126.910000"),
                  new BigDecimal("35.162000"),
                  new BigDecimal("126.910000"),
                  new BigDecimal("35.162000")),
              STARTED_AT));
    }

    @Override
    public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
      SearchAreaRow areaRow = areaRow(incidentId);
      return new SearchAreaCollection(
          incidentId, 5L, matchesMinVersion(areaRow, filters) ? List.of(areaRow) : List.of());
    }

    @Override
    public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
      SearchAreaRow areaRow = areaRow(INCIDENT_ID);
      return new SearchAreaCollection(
          INCIDENT_ID, 5L, matchesMinVersion(areaRow, filters) ? List.of(areaRow) : List.of());
    }

    private static boolean matchesMinVersion(SearchAreaRow row, SearchAreaFilters filters) {
      SearchAreaFilters effectiveFilters = filters == null ? SearchAreaFilters.empty() : filters;
      return effectiveFilters.minVersion() == null
          || row.version() >= effectiveFilters.minVersion();
    }

    private static SearchAreaRow areaRow(UUID incidentId) {
      return new SearchAreaRow(
          AREA_ID,
          incidentId,
          OP_ID,
          null,
          "ACTIVE",
          "TEAM",
          5L,
          polygon(),
          List.of(
              new BigDecimal("126.910000"),
              new BigDecimal("35.162000"),
              new BigDecimal("126.910000"),
              new BigDecimal("35.162000")),
          STARTED_AT,
          1L);
    }
  }

  private static final class FakeSearchAreaAssignmentQuery implements SearchAreaAssignmentQuery {
    @Override
    public List<SearchAreaAssignmentRow> byOp(UUID opId) {
      return List.of(
          new SearchAreaAssignmentRow(
              ASSIGNMENT_ID,
              AREA_ID,
              ACCOUNT_ID,
              ASSIGNED_BY_ACCOUNT_ID,
              STARTED_AT,
              null,
              "ACTIVE",
              4L));
    }

    @Override
    public List<SearchAreaAssignmentRow> byArea(UUID searchAreaId) {
      return List.of();
    }
  }

  private static final class FakeIncidentReadMapper implements IncidentReadMapper {
    @Override
    public List<com.surimap.incident.repository.IncidentReadRows.ListRow> findActiveListByAccountId(
        UUID accountId, String status) {
      return List.of();
    }

    @Override
    public List<com.surimap.incident.repository.IncidentReadRows.ListRow>
        findActiveListByOrganizationType(String organizationType, String status) {
      return List.of();
    }

    @Override
    public Optional<com.surimap.incident.repository.IncidentReadRows.DetailRow>
        findActiveDetailByIncidentIdAndAccountId(UUID incidentId, UUID accountId) {
      return Optional.empty();
    }

    @Override
    public Optional<com.surimap.incident.repository.IncidentReadRows.DetailRow>
        findActiveDetailByIncidentIdAndOrganizationType(UUID incidentId, String organizationType) {
      return Optional.empty();
    }

    @Override
    public Optional<com.surimap.incident.repository.IncidentReadRows.TerminalDetailRow>
        findTerminalDetailByIncidentIdAndAccountId(UUID incidentId, UUID accountId) {
      return Optional.empty();
    }

    @Override
    public Optional<com.surimap.incident.repository.IncidentReadRows.TerminalDetailRow>
        findTerminalDetailByIncidentIdAndOrganizationType(
            UUID incidentId, String organizationType) {
      return Optional.empty();
    }

    @Override
    public int countIncidentById(UUID incidentId) {
      return 0;
    }

    @Override
    public int countActiveAssignmentsByAccountId(UUID accountId) {
      return 0;
    }

    @Override
    public Optional<com.surimap.incident.repository.IncidentReadRows.MissingPersonRow>
        findMissingPersonByIncidentId(UUID incidentId) {
      return Optional.empty();
    }

    @Override
    public List<AssignmentRow> findActiveAssignmentsByIncidentId(UUID incidentId) {
      AssignmentRow row = new AssignmentRow();
      row.setAccountId(ACCOUNT_ID.toString());
      row.setAccountDisplayName(ACCOUNT_DISPLAY_NAME);
      row.setAccountType("PATROL_CAR");
      row.setOrganizationType("POLICE_SUBSTATION");
      row.setIncidentRole("MEMBER");
      row.setAssignedAt(STARTED_AT);
      return List.of(row);
    }

    @Override
    public List<com.surimap.incident.repository.IncidentReadRows.AssignmentTargetRow>
        findActiveAssignmentTargetsByIncidentId(UUID incidentId) {
      com.surimap.incident.repository.IncidentReadRows.AssignmentTargetRow row =
          new com.surimap.incident.repository.IncidentReadRows.AssignmentTargetRow();
      row.setAccountId(ACCOUNT_ID.toString());
      row.setIncidentRole("MEMBER");
      row.setAccountType("PATROL_CAR");
      row.setOrganizationType("POLICE_SUBSTATION");
      row.setPolicePhoneId(POLICE_PHONE_ID.toString());
      return List.of(row);
    }
  }

  private static final class CapturingSearchAreaQuery extends FakeSearchAreaQuery {
    private int overallCalls;
    private int byIncidentCalls;
    private final List<UUID> byOpIds = new ArrayList<>();
    private final List<SearchAreaFilters> byIncidentFilters = new ArrayList<>();
    private final List<SearchAreaFilters> byOpFilters = new ArrayList<>();

    @Override
    public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
      overallCalls++;
      return super.overallOf(incidentId);
    }

    @Override
    public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
      byIncidentCalls++;
      byIncidentFilters.add(filters);
      return super.byIncident(incidentId, filters);
    }

    @Override
    public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
      byOpIds.add(opId);
      byOpFilters.add(filters);
      return super.byOp(opId, filters);
    }

    private int overallCalls() {
      return overallCalls;
    }

    private int byIncidentCalls() {
      return byIncidentCalls;
    }

    private List<SearchAreaFilters> byIncidentFilters() {
      return List.copyOf(byIncidentFilters);
    }

    private List<SearchAreaFilters> byOpFilters() {
      return List.copyOf(byOpFilters);
    }

    private List<UUID> byOpIds() {
      return List.copyOf(byOpIds);
    }
  }

  private static MarkerService createMarkerService() {
    MarkerService service = mock(MarkerService.class);
    when(service.list(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID incidentId = invocation.getArgument(0);
              return MarkersServiceResponse.builder()
                  .incidentId(incidentId)
                  .markers(
                      List.of(
                          MarkerServiceResponse.builder()
                              .id(MARKER_ID)
                              .incidentId(incidentId)
                              .opId(OP_ID)
                              .accountId(ACCOUNT_ID)
                              .policePhoneId(PHONE_ID)
                              .type(MarkerType.CLUE)
                              .source(MarkerSource.APP)
                              .status(MarkerStatus.ACTIVE)
                              .version(6L)
                              .location(
                                  new MarkerGeoJsonPoint(
                                          "Point",
                                          List.of(
                                              new BigDecimal("126.911000"),
                                              new BigDecimal("35.161000")))
                                      .toPoint())
                              .memo("clue memo")
                              .occurredAt(STARTED_AT)
                              .photoSummary(List.of())
                              .build()))
                  .build();
            });
    return service;
  }

  private static void assertQueriedMarkerOpIds(MarkerService service, UUID... expectedOpIds) {
    ArgumentCaptor<UUID> opIds = ArgumentCaptor.forClass(UUID.class);
    verify(service, times(expectedOpIds.length))
        .list(eq(INCIDENT_ID), opIds.capture(), isNull(), isNull());
    assertThat(opIds.getAllValues()).containsExactly(expectedOpIds);
  }

  private static MarkerNotificationMapper createNotificationMapper() {
    MarkerNotificationMapper mapper = mock(MarkerNotificationMapper.class);
    when(mapper.findNotificationRowsByIncidentId(INCIDENT_ID))
        .thenReturn(List.of(createNotificationRow(NOTIFICATION_EVENT_ID)));
    return mapper;
  }

  private static NotificationRow createNotificationRow(UUID latestEventId) {
    return NotificationRow.builder()
        .notificationId(NOTIFICATION_ID)
        .markerId(MARKER_ID)
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .policePhoneId(PHONE_ID)
        .notificationType("SUPPORT_REQUEST_CREATED")
        .status("SNAPSHOT_CREATED")
        .version(12L)
        .createdAt(STARTED_AT)
        .latestEventId(latestEventId)
        .build();
  }

  private static DefaultIncidentBoardSourceRowCollector createNotificationCollector(
      ObjectProvider<MarkerNotificationMapper> mapperProvider) {
    return new DefaultIncidentBoardSourceRowCollector(
        provider(null),
        provider(null),
        provider(null),
        createMarkerService(),
        new FakePackageQuery(),
        new FakeOperationalPeriodQuery(),
        new FakeHandoverMemoQuery(),
        new FakeSummaryMapper(),
        provider(null),
        provider(null),
        provider(null),
        mapperProvider);
  }

  private static final class FakePackageQuery implements OfflinePackageInstallationQuery {
    @Override
    public List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
      return List.of(
          new OfflinePackageInstallationStatus(
              "pkg-status-test-001",
              incidentId,
              PHONE_ID.toString(),
              "phone-test-001",
              "Phone Test 001",
              "READY",
              7L,
              701L,
              1,
              true));
    }
  }

  private static final class FakePolicePhoneFreshnessQuery implements PolicePhoneFreshnessQuery {
    @Override
    public List<PolicePhoneFreshnessRow> byIncident(UUID incidentId) {
      return List.of(
          new PolicePhoneFreshnessRow(
              PHONE_ID,
              ACCOUNT_ID.toString(),
              com.surimap.common.auth.AccountType.TEAM,
              com.surimap.common.auth.OrganizationType.POLICE_SUBSTATION,
              incidentId,
              null,
              UUID.fromString("61000000-0000-0000-0000-000000000001"),
              STARTED_AT,
              STARTED_AT,
              11L,
              111L,
              PolicePhoneFreshnessStatus.ONLINE));
    }
  }

  private static final class FakeOperationalPeriodQuery implements OperationalPeriodQuery {
    @Override
    public Optional<CurrentOpResult> current(UUID incidentId) {
      return Optional.of(
          new CurrentOpResult(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 8L));
    }

    @Override
    public List<OperationalPeriodRow> list(UUID incidentId) {
      return List.of(
          new OperationalPeriodRow(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 8L));
    }
  }

  private static final class MultiOpOperationalPeriodQuery implements OperationalPeriodQuery {
    @Override
    public Optional<CurrentOpResult> current(UUID incidentId) {
      return Optional.of(
          new CurrentOpResult(OP_ID, incidentId, "ACTIVE", 2, STARTED_AT, null, null, 8L));
    }

    @Override
    public List<OperationalPeriodRow> list(UUID incidentId) {
      return List.of(
          new OperationalPeriodRow(
              PREVIOUS_OP_ID,
              incidentId,
              "ENDED",
              1,
              STARTED_AT.minusSeconds(3600),
              STARTED_AT.minusSeconds(60),
              null,
              7L),
          new OperationalPeriodRow(OP_ID, incidentId, "ACTIVE", 2, STARTED_AT, null, null, 8L),
          new OperationalPeriodRow(
              FUTURE_OP_ID, incidentId, "ACTIVE", 3, STARTED_AT.plusSeconds(3600), null, null, 9L));
    }
  }

  private static final class FakeHandoverMemoQuery implements HandoverMemoQuery {
    @Override
    public List<HandoverMemoRow> byContext(
        UUID incidentId, UUID opId, String targetType, UUID targetId) {
      return List.of(
          new HandoverMemoRow(
              MEMO_ID,
              incidentId,
              opId,
              null,
              "OPERATIONAL_PERIOD",
              opId,
              "memo for next team",
              ACCOUNT_ID,
              STARTED_AT,
              9L));
    }
  }

  private static final class FakeSummaryMapper implements SearchHistorySummaryMapper {
    @Override
    public List<SearchHistorySummaryRow> findByOp(
        UUID opId,
        UUID incidentId,
        String scopeType,
        UUID scopeId,
        UUID dutyShiftId,
        String status) {
      return List.of(
          new SearchHistorySummaryRow(
              SUMMARY_ID,
              incidentId,
              opId,
              null,
              "READY",
              "searched ridge trail and checked shelter",
              "summary-source-hash-test",
              "READY",
              STARTED_AT,
              10L));
    }

    @Override
    public String sourceFingerprintForScope(UUID opId, UUID dutyShiftId) {
      return "unused";
    }

    @Override
    public String sourceEvidenceForScope(UUID opId, UUID dutyShiftId) {
      return "unused";
    }

    @Override
    public List<SearchHistorySummaryRow> findReadyOrFailedByScopeWithDifferentHash(
        UUID opId, UUID dutyShiftId, String sourceDataHash) {
      return List.of();
    }

    @Override
    public int insertGenerationRequest(
        UUID summaryId,
        UUID opId,
        UUID dutyShiftId,
        String generationStatus,
        String content,
        String sourceDataHash,
        String sourceReadiness,
        UUID requestedByAccountId,
        Instant generatedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
      return 1;
    }

    @Override
    public void updateGenerationResult(
        UUID summaryId,
        String generationStatus,
        String content,
        String sourceReadiness,
        Instant generatedAt,
        Instant updatedAt) {}

    @Override
    public void markStaleByIds(List<UUID> summaryIds, Instant updatedAt) {}
  }
}
