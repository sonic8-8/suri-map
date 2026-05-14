package com.surimap.board;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.TeamNotAssignedException;
import com.surimap.retention.purge.LocationAccessRecorder;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("S3-2 incident board API contract")
class IncidentBoardApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final String COMMAND_ACCOUNT_ID_VALUE = "11111111-1111-1111-1111-111111110004";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private IncidentBoardSourceRowCollector boardSourceRowCollector;
  @MockitoBean private BoardAreaPopupQueryService areaPopupQueryService;
  @MockitoBean private LocationAccessRecorder locationAccessRecorder;

  @BeforeEach
  void setUp() {
    when(boardSourceRowCollector.collect(any()))
        .thenReturn(new IncidentBoardSourceRowSnapshot(null, List.of(), "hash-board-empty", List.of()));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_ID_VALUE,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("WEB can read area popup response split by incomplete/completed summary")
  void webCanReadAreaPopup() throws Exception {
    UUID searchAreaId = UUID.fromString("30000000-0000-4000-8000-000000000001");
    UUID opId = UUID.fromString("20000000-0000-4000-8000-000000000001");
    UUID assignmentId = UUID.fromString("31000000-0000-4000-8000-000000000001");
    UUID assignedAccountId = UUID.fromString("11111111-1111-1111-1111-111111110003");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    when(areaPopupQueryService.getAreaPopup(eq(INCIDENT_ID), eq(searchAreaId)))
        .thenReturn(
            Optional.of(
                new BoardAreaPopupResponse(
                    new BoardAreaPopupResponse.Common(
                        searchAreaId,
                        "A구역",
                        "TEAM",
                        "ACTIVE",
                        opId,
                        1,
                        "ACTIVE",
                        Instant.parse("2026-04-28T00:00:00Z"),
                        null,
                        "ASSIGNED",
                        List.of(
                            new BoardAreaPopupResponse.Assignment(
                                assignmentId,
                                assignedAccountId,
                                UUID.fromString(COMMAND_ACCOUNT_ID_VALUE),
                                Instant.parse("2026-04-28T00:10:00Z"),
                                null,
                                "ACTIVE",
                                AccountType.TEAM,
                                OrganizationType.POLICE_SUBSTATION,
                                new BoardAreaPopupResponse.PolicePhone(
                                    policePhoneId,
                                    "NORMAL",
                                    Instant.parse("2026-04-28T00:12:00Z"),
                                    Instant.parse("2026-04-28T00:11:00Z")))),
                        Instant.parse("2026-04-28T00:20:00Z"),
                        5L,
                        1L),
                    new BoardAreaPopupResponse.IncompleteSummary(
                        Instant.parse("2026-04-28T00:20:00Z"), 1L),
                    null)));

    mockMvc
        .perform(
            get(
                "/api/incidents/{incidentId}/board/search-areas/{searchAreaId}/popup",
                INCIDENT_ID,
                searchAreaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.common.areaName").value("A구역"))
        .andExpect(jsonPath("$.common.areaLevel").value("TEAM"))
        .andExpect(jsonPath("$.common.areaStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.common.opSequence").value(1))
        .andExpect(jsonPath("$.common.assignmentStatus").value("ASSIGNED"))
        .andExpect(jsonPath("$.common.assignments[0].assignedAccountId").value(assignedAccountId.toString()))
        .andExpect(jsonPath("$.common.assignments[0].policePhone.policePhoneId").value(policePhoneId.toString()))
        .andExpect(jsonPath("$.common.assignments[0].policePhone.freshness").value("NORMAL"))
        .andExpect(jsonPath("$.incompleteSummary.statusUpdatedAt").isString())
        .andExpect(jsonPath("$.completedSummary").doesNotExist());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_ID_VALUE,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("WEB area popup returns 404 when area source row does not exist")
  void areaPopupReturnsNotFound() throws Exception {
    UUID searchAreaId = UUID.fromString("30000000-0000-4000-8000-000000009999");
    when(areaPopupQueryService.getAreaPopup(eq(INCIDENT_ID), eq(searchAreaId)))
        .thenReturn(Optional.empty());

    mockMvc
        .perform(
            get(
                "/api/incidents/{incidentId}/board/search-areas/{searchAreaId}/popup",
                INCIDENT_ID,
                searchAreaId))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_ID_VALUE,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("WEB can read board response from GET /api/incidents/{incidentId}/board")
  void webCanReadIncidentBoard() throws Exception {
    when(boardSourceRowCollector.collect(any()))
        .thenReturn(
            new IncidentBoardSourceRowSnapshot(
                null,
                List.of(),
                "hash-board-marker-test",
                List.of(
                    new BoardSourceRow(
                        "marker",
                        "S5",
                        "marker-source-001",
                        "board-marker-source-001",
                        "ACTIVE",
                        33L,
                        601L,
                        "evt-s5-marker-source-001-v33",
                        "hash-s5-marker-source-001-v33",
                        Map.of("markerType", "CLUE")))));

    mockMvc
        .perform(get("/api/incidents/{incidentId}/board", INCIDENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
        .andExpect(jsonPath("$.boardResponseVersion").isNumber())
        .andExpect(jsonPath("$.serverTs").isString())
        .andExpect(jsonPath("$.selectedOpIds").isArray())
        .andExpect(jsonPath("$.geometryHash").isString())
        .andExpect(jsonPath("$.slots.path").isArray())
        .andExpect(jsonPath("$.slots.marker").isArray())
        .andExpect(jsonPath("$.slots.marker[0].id").value("marker-source-001"))
        .andExpect(jsonPath("$.slots.marker[0].sourceSpec").value("S5"))
        .andExpect(jsonPath("$.slots.marker[0].markerType").value("CLUE"))
        .andExpect(jsonPath("$.slots.overall_search_area").isMap())
        .andExpect(jsonPath("$.slotSources.path").isArray())
        .andExpect(jsonPath("$.slotSources.marker[0].id").value("marker-source-001"))
        .andExpect(jsonPath("$.sourceVersions.path").isArray())
        .andExpect(jsonPath("$.sourceHashes.path").isArray())
        .andExpect(jsonPath("$.location_data_access_audit").doesNotExist())
        .andExpect(jsonPath("$.locationAccessAudit").doesNotExist());

    verify(locationAccessRecorder)
        .record(
            eq(UUID.fromString(COMMAND_ACCOUNT_ID_VALUE)),
            eq(INCIDENT_ID),
            isNull(),
            eq("WEB"),
            eq("BOARD_VIEW"),
            any(Instant.class));
  }

  @Test
  @WithMockAccount(channel = Channel.APP, policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP cannot read board API because board is a WEB consumer")
  void appCannotReadIncidentBoard() throws Exception {
    mockMvc
        .perform(get("/api/incidents/{incidentId}/board", INCIDENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));

    verifyNoInteractions(locationAccessRecorder);
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_ID_VALUE,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("WEB board API keeps incident assignment guard")
  void webWithoutIncidentAssignmentCannotReadIncidentBoard() throws Exception {
    doThrow(new TeamNotAssignedException()).when(incidentAccessPort).checkAccess(any());

    mockMvc
        .perform(get("/api/incidents/{incidentId}/board", INCIDENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("team_not_assigned"));

    verifyNoInteractions(locationAccessRecorder);
  }
}
