package com.surimap.app.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.app.controller.map.response.AppMapRevisionResponse;
import com.surimap.app.controller.map.response.AppMapRevisionResponse.SourceRevision;
import com.surimap.app.service.map.AppMapRevisionQueryService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.retention.purge.LocationAccessRecorder;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
@DisplayName("APP map revision API contract")
class AppMapRevisionApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa5171");
  private static final UUID OP_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb5171");
  private static final UUID ACCOUNT_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd5171");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeee5171");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private LocationAccessRecorder locationAccessRecorder;
  @MockitoBean private AppMapRevisionQueryService service;

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountId = "dddddddd-dddd-4ddd-8ddd-dddddddd5171",
      policePhoneId = "eeeeeeee-eeee-4eee-8eee-eeeeeeee5171",
      roles = Role.MEMBER)
  @DisplayName("APP GET /api/incidents/{incidentId}/map-revisions는 지도 source revision 목록을 반환한다")
  void appCanReadMapSourceRevisions() throws Exception {
    when(service.revisions(INCIDENT_ID, OP_ID, POLICE_PHONE_ID))
        .thenReturn(
            new AppMapRevisionResponse(
                INCIDENT_ID,
                OP_ID,
                List.of(
                    new SourceRevision("incident_detail", "1:1:1000:incident"),
                    new SourceRevision("search_paths", "2:7:2000:paths"))));

    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/map-revisions", INCIDENT_ID)
                .param("opId", OP_ID.toString())
                .param("policePhoneId", POLICE_PHONE_ID.toString())
                .header("Authorization", "Bearer app-map-revisions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
        .andExpect(jsonPath("$.opId").value(OP_ID.toString()))
        .andExpect(jsonPath("$.sources[0].source").value("incident_detail"))
        .andExpect(jsonPath("$.sources[0].revision").value("1:1:1000:incident"))
        .andExpect(jsonPath("$.sources[1].source").value("search_paths"));

    verify(incidentAccessPort).checkAccess(any());
    verify(service).revisions(INCIDENT_ID, OP_ID, POLICE_PHONE_ID);
    verify(locationAccessRecorder)
        .record(
            eq(ACCOUNT_ID),
            eq(INCIDENT_ID),
            eq(POLICE_PHONE_ID),
            eq("APP"),
            eq("MAP_REVISION_READ"),
            any(Instant.class));
  }
}
