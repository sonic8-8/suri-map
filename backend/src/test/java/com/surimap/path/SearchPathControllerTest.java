package com.surimap.path;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.Channel;
import com.surimap.config.GuardConfig;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchPathController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SearchPathExceptionHandler.class, GuardConfig.class, GuardPortTestStubs.class})
class SearchPathControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SearchPathService searchPathService;

  @Test
  @WithMockAccount(
      accountId = "30000000-0000-0000-0000-000000000001",
      policePhoneId = "50000000-0000-0000-0000-000000000001")
  @DisplayName("POST /api/search-paths/batch returns 200 with append response")
  void appendBatchContract() throws Exception {
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    UUID dutyShiftId = UUID.fromString("60000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    when(searchPathService.appendBatch(any(), eq(policePhoneId), eq(accountId)))
        .thenReturn(
            new PathBatchAppendResponse(
                pathId,
                dutyShiftId,
                opId,
                policePhoneId,
                accountId,
                2,
                0,
                List.of(),
                List.of(List.of(126.913, 35.162), List.of(126.914, 35.163)),
                List.of(
                    new SearchPathSegment(
                        "seg-001",
                        1L,
                        MovementType.VEHICLE,
                        MovementTypeSource.AUTO,
                        0,
                        1,
                        "p1",
                        "p2",
                        null,
                        null)),
                2L,
                SearchPathStatus.RECORDING));

    mockMvc
        .perform(
            post("/api/search-paths/batch")
                .header("X-PolicePhone-Id", policePhoneId)
                .header("Idempotency-Key", "idem-path-batch-contract")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId":"10000000-0000-0000-0000-000000000001",
                      "opId":"70000000-0000-0000-0000-000000000001",
                      "pathId":"81000000-0000-0000-0000-000000000001",
                      "points":[
                        {"pointId":"p1","lon":126.913000,"lat":35.162000,"speedMps":3.0,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:00+09:00"},
                        {"pointId":"p2","lon":126.914000,"lat":35.163000,"speedMps":3.1,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:05+09:00"}
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(pathId.toString())))
        .andExpect(jsonPath("$.dutyShiftId", is(dutyShiftId.toString())))
        .andExpect(jsonPath("$.opId", is(opId.toString())))
        .andExpect(jsonPath("$.policePhoneId", is(policePhoneId.toString())))
        .andExpect(jsonPath("$.accountId", is(accountId.toString())))
        .andExpect(jsonPath("$.acceptedPointCount", is(2)))
        .andExpect(jsonPath("$.geometry.type", is("LineString")))
        .andExpect(jsonPath("$.geometry.coordinates[0][0]", is(126.913)))
        .andExpect(jsonPath("$.geometry.coordinates[0][1]", is(35.162)))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.status", is("RECORDING")));
  }

  @Test
  @WithMockAccount(accountId = "30000000-0000-0000-0000-000000000099", channel = Channel.WEB)
  @DisplayName("WEB POST /api/search-paths/batch는 channel_not_allowed로 거부한다")
  void appendBatchWebChannelRejected() throws Exception {
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");

    mockMvc
        .perform(
            post("/api/search-paths/batch")
                .header("X-PolicePhone-Id", policePhoneId)
                .header("Idempotency-Key", "idem-web-path-batch-contract")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId":"10000000-0000-0000-0000-000000000001",
                      "opId":"70000000-0000-0000-0000-000000000001",
                      "pathId":"81000000-0000-0000-0000-000000000001",
                      "points":[
                        {"pointId":"p1","lon":126.913000,"lat":35.162000,"speedMps":3.0,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:00+09:00"},
                        {"pointId":"p2","lon":126.914000,"lat":35.163000,"speedMps":3.1,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:05+09:00"}
                      ]
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(searchPathService);
  }

  @Test
  @WithMockAccount(
      accountId = "30000000-0000-0000-0000-000000000001",
      policePhoneId = "50000000-0000-0000-0000-000000000001")
  @DisplayName("POST /api/search-paths/batch missing Idempotency-Key returns write_conflict")
  void appendBatchRequiresIdempotencyKey() throws Exception {
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");

    mockMvc
        .perform(
            post("/api/search-paths/batch")
                .header("X-PolicePhone-Id", policePhoneId)
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId":"10000000-0000-0000-0000-000000000001",
                      "opId":"70000000-0000-0000-0000-000000000001",
                      "pathId":"81000000-0000-0000-0000-000000000001",
                      "points":[
                        {"pointId":"p1","lon":126.913000,"lat":35.162000,"speedMps":3.0,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:00+09:00"}
                      ]
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountId = "30000000-0000-0000-0000-000000000001",
      policePhoneId = "00000000-0000-0000-0000-000000000201")
  @DisplayName(
      "POST /api/search-paths/batch unregistered PolicePhone returns police_phone_not_registered")
  void appendBatchUnregisteredPolicePhoneRejected() throws Exception {
    UUID policePhoneId = UUID.fromString("00000000-0000-0000-0000-000000000201");
    mockMvc
        .perform(
            post("/api/search-paths/batch")
                .header("X-PolicePhone-Id", policePhoneId)
                .header("Idempotency-Key", "idem-unregistered-path-batch")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId":"10000000-0000-0000-0000-000000000001",
                      "opId":"70000000-0000-0000-0000-000000000001",
                      "pathId":"81000000-0000-0000-0000-000000000001",
                      "points":[
                        {"pointId":"p1","lon":126.913000,"lat":35.162000,"speedMps":3.0,"horizontalAccuracyM":5,"clientTs":"2026-04-28T09:00:00+09:00"}
                      ]
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));

    verifyNoInteractions(searchPathService);
  }

  @Test
  @DisplayName("GET /api/search-paths returns filtered paths")
  void getContract() throws Exception {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    Instant startedAt = Instant.parse("2026-05-18T04:53:12.331Z");
    when(searchPathService.query(eq(incidentId), eq(opId), eq(policePhoneId), isNull()))
        .thenReturn(
            new PathQueryResponse(
                List.of(
                    new PathQueryRow(
                        pathId,
                        incidentId,
                        opId,
                        null,
                        policePhoneId,
                        accountId,
                        SearchPathStatus.RECORDING,
                        startedAt,
                        null,
                        2L,
                        List.of(List.of(126.913, 35.162)),
                        List.of(),
                        List.of()))));

    mockMvc
        .perform(
            get("/api/search-paths")
                .param("incidentId", incidentId.toString())
                .param("opId", opId.toString())
                .param("policePhoneId", policePhoneId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths[0].id", is(pathId.toString())))
        .andExpect(jsonPath("$.paths[0].incidentId", is(incidentId.toString())))
        .andExpect(jsonPath("$.paths[0].opId", is(opId.toString())))
        .andExpect(jsonPath("$.paths[0].policePhoneId", is(policePhoneId.toString())))
        .andExpect(jsonPath("$.paths[0].accountId", is(accountId.toString())))
        .andExpect(jsonPath("$.paths[0].startedAt", is("2026-05-18T04:53:12.331Z")))
        .andExpect(jsonPath("$.paths[0].geometry.type", is("LineString")))
        .andExpect(jsonPath("$.paths[0].geometry.coordinates[0][0]", is(126.913)))
        .andExpect(jsonPath("$.paths[0].geometry.coordinates[0][1]", is(35.162)));
  }
}
