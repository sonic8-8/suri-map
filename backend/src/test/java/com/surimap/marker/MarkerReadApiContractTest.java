package com.surimap.marker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerListResponse;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerReadService;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.retention.purge.LocationAccessRecorder;
import com.surimap.support.auth.WithMockAccount;
import java.math.BigDecimal;
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
@DisplayName("S5 marker read API contract")
class MarkerReadApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa5702");
  private static final UUID OP_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb5702");
  private static final UUID MARKER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc5702");
  private static final UUID ACCOUNT_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd5702");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeee5702");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private LocationAccessRecorder locationAccessRecorder;
  @MockitoBean private MarkerCreateService markerCreateService;
  @MockitoBean private MarkerUpdateDeleteService markerUpdateDeleteService;
  @MockitoBean private MarkerReadService markerReadService;

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountId = "dddddddd-dddd-4ddd-8ddd-dddddddd5702",
      policePhoneId = "eeeeeeee-eeee-4eee-8eee-eeeeeeee5702",
      roles = Role.MEMBER)
  @DisplayName("APP GET /api/markers는 live marker list와 location access audit을 반환한다")
  void appCanReadLiveMarkers() throws Exception {
    when(markerReadService.list(eq(INCIDENT_ID), eq(OP_ID), eq("CLUE"), isNull()))
        .thenReturn(
            new MarkerListResponse(
                INCIDENT_ID,
                List.of(
                    new MarkerListResponse.MarkerResponse(
                        MARKER_ID,
                        INCIDENT_ID,
                        OP_ID,
                        ACCOUNT_ID,
                        POLICE_PHONE_ID,
                        "CLUE",
                        null,
                        "APP",
                        "ACTIVE",
                        7L,
                        new MarkerGeoJsonPoint(
                            "Point",
                            List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
                        "등산로 입구 제보",
                        Instant.parse("2026-05-14T00:00:01Z"),
                        List.of()))));

    mockMvc
        .perform(
            get("/api/markers")
                .param("incidentId", INCIDENT_ID.toString())
                .param("opId", OP_ID.toString())
                .param("type", "CLUE")
                .header("Authorization", "Bearer app-marker-read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
        .andExpect(jsonPath("$.markers[0].id").value(MARKER_ID.toString()))
        .andExpect(jsonPath("$.markers[0].incidentId").value(INCIDENT_ID.toString()))
        .andExpect(jsonPath("$.markers[0].opId").value(OP_ID.toString()))
        .andExpect(jsonPath("$.markers[0].policePhoneId").value(POLICE_PHONE_ID.toString()))
        .andExpect(jsonPath("$.markers[0].type").value("CLUE"))
        .andExpect(jsonPath("$.markers[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.markers[0].location.type").value("Point"))
        .andExpect(jsonPath("$.markers[0].location.coordinates[0]").value(126.9565))
        .andExpect(jsonPath("$.markers[0].photoSummary").isArray());

    verify(incidentAccessPort).checkAccess(any());
    verify(markerReadService).list(INCIDENT_ID, OP_ID, "CLUE", null);
    verify(locationAccessRecorder)
        .record(
            eq(ACCOUNT_ID),
            eq(INCIDENT_ID),
            eq(POLICE_PHONE_ID),
            eq("APP"),
            eq("MARKER_READ"),
            any(Instant.class));
  }
}
