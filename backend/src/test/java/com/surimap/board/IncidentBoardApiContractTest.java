package com.surimap.board;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.TeamNotAssignedException;
import com.surimap.support.auth.WithMockAccount;
import java.util.List;
import java.util.Map;
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

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private IncidentBoardSourceRowCollector boardSourceRowCollector;

  @BeforeEach
  void setUp() {
    when(boardSourceRowCollector.collect(any()))
        .thenReturn(new IncidentBoardSourceRowSnapshot(null, List.of(), "hash-board-empty", List.of()));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
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
        .andExpect(jsonPath("$.sourceHashes.path").isArray());
  }

  @Test
  @WithMockAccount(channel = Channel.APP, policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP cannot read board API because board is a WEB consumer")
  void appCannotReadIncidentBoard() throws Exception {
    mockMvc
        .perform(get("/api/incidents/{incidentId}/board", INCIDENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
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
  }
}
