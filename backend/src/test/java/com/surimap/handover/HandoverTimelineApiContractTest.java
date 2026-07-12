package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.api.controller.handover.HandoverTimelineController;
import com.surimap.api.service.handover.HandoverTimelineApiService;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.config.GuardConfig;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathPoint;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HandoverTimelineController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GuardConfig.class, GuardPortTestStubs.class, HandoverTimelineApiService.class})
@DisplayName("S8 handover timeline read API contract")
class HandoverTimelineApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID PATH_ID = UUID.fromString("81000000-0000-0000-0000-000000000001");
  private static final UUID MARKER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc5701");
  private static final UUID MEMO_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final UUID SUMMARY_ID = UUID.fromString("44444444-4444-4444-4444-444444440001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final Instant SUMMARY_GENERATED_AT = Instant.parse("2026-05-18T01:00:00Z");
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchPathService searchPathService;
  @MockitoBean private MarkerQuery markerQuery;
  @MockitoBean private HandoverMemoQuery handoverMemoQuery;
  @MockitoBean private SearchHistorySummaryMapper searchHistorySummaryMapper;
  @MockitoBean private DutyShiftMapper dutyShiftMapper;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("APP/WEB reads timeline evidence without exposing accountId or policePhoneId")
  void readsTimelineEvidenceWithoutPiiIdentifiers() throws Exception {
    when(searchPathService.findAll()).thenReturn(List.of(path()));
    when(markerQuery.byIncident(INCIDENT_ID, new MarkerQueryFilters(OP_ID, null, null)))
        .thenReturn(new MarkerQueryResult(INCIDENT_ID, List.of(marker())));
    when(handoverMemoQuery.byContext(INCIDENT_ID, OP_ID, null, null)).thenReturn(List.of(memo()));
    when(searchHistorySummaryMapper.findByOp(OP_ID, INCIDENT_ID, "OP", OP_ID, null, null))
        .thenReturn(List.of(summary()));

    var result =
        mockMvc
            .perform(
                get("/api/operational-periods/{operationalPeriodId}/handover-timeline", OP_ID)
                    .header("Authorization", "Bearer commander")
                    .header("X-Client-Channel", "WEB")
                    .queryParam("incidentId", INCIDENT_ID.toString())
                    .queryParam("scopeType", "OP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
            .andExpect(jsonPath("$.operationalPeriodId", is(OP_ID.toString())))
            .andExpect(jsonPath("$.scope.scopeType", is("OP")))
            .andExpect(jsonPath("$.paths", hasSize(1)))
            .andExpect(jsonPath("$.paths[0].pathId", is(PATH_ID.toString())))
            .andExpect(jsonPath("$.paths[0].points", hasSize(3)))
            .andExpect(jsonPath("$.events", hasSize(4)))
            .andExpect(jsonPath("$.events[0].type", is("PATH_START")))
            .andExpect(jsonPath("$.events[1].type", is("PATH_SEGMENT")))
            .andExpect(jsonPath("$.events[2].type", is("MARKER")))
            .andExpect(jsonPath("$.events[3].type", is("HANDOVER_MEMO")))
            .andExpect(jsonPath("$.metrics.markerCount", is(1)))
            .andExpect(jsonPath("$.metrics.handoverMemoCount", is(1)))
            .andExpect(jsonPath("$.metrics.syncStatus", is("READY")))
            .andExpect(jsonPath("$.summary.summaryId", is(SUMMARY_ID.toString())))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(
            "accountId", "policePhoneId", ACCOUNT_ID.toString(), POLICE_PHONE_ID.toString());

    verify(markerQuery).byIncident(INCIDENT_ID, new MarkerQueryFilters(OP_ID, null, null));
    verify(handoverMemoQuery).byContext(INCIDENT_ID, OP_ID, null, null);
  }

  private static SearchPath path() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .build();
    path.appendAcceptedPoints(
        List.of(
            point("p1", "126.913000", "35.162000", "2026-05-18T09:00:00+09:00"),
            point("p2", "126.913650", "35.162180", "2026-05-18T09:00:05+09:00"),
            point("p3", "126.914300", "35.162360", "2026-05-18T09:00:10+09:00")));
    path.replaceSegments(
        List.of(
            SearchPathSegment.builder()
                .id(UUID.fromString("71000000-0000-0000-0000-000000000001"))
                .movementType(MovementType.FOOT)
                .movementTypeSource(MovementTypeSource.AUTO)
                .startIndex(0)
                .endIndex(2)
                .startPointId("p1")
                .endPointId("p3")
                .build()));
    return path;
  }

  private static SearchPathPoint point(String id, String lng, String lat, String at) {
    return SearchPathPoint.builder()
        .pointId(id)
        .clientTs(OffsetDateTime.parse(at))
        .lon(new BigDecimal(lng))
        .lat(new BigDecimal(lat))
        .speedMps(BigDecimal.ONE)
        .horizontalAccuracyM(5)
        .build();
  }

  private static MarkerView marker() {
    return new MarkerView(
        MARKER_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        ACCOUNT_ID,
        POLICE_PHONE_ID,
        MarkerType.CLUE,
        null,
        MarkerSource.APP,
        MarkerStatus.ACTIVE,
        1L,
        GEOMETRY_FACTORY.createPoint(new Coordinate(126.9143, 35.16236)),
        "족적 발견",
        Instant.parse("2026-05-18T00:00:07Z"),
        List.of());
  }

  private static HandoverMemoRow memo() {
    return new HandoverMemoRow(
        MEMO_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "OPERATIONAL_PERIOD",
        OP_ID,
        "인수인계 메모",
        ACCOUNT_ID,
        Instant.parse("2026-05-18T00:00:12Z"),
        1L);
  }

  private static SearchHistorySummaryRow summary() {
    return new SearchHistorySummaryRow(
        SUMMARY_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "READY",
        "수색 이력 요약",
        "a".repeat(64),
        "READY",
        SUMMARY_GENERATED_AT,
        1L);
  }
}
