package com.surimap.opcomparison;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.opcomparison.OpComparisonController;
import com.surimap.api.controller.opcomparison.response.OpComparisonResponse;
import com.surimap.api.service.opcomparison.OpComparisonApiException;
import com.surimap.api.service.opcomparison.OpComparisonApiService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpComparisonController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GuardConfig.class, GuardPortTestStubs.class})
@DisplayName("AI-CMP-8 OP comparison API contract")
class OpComparisonApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final UUID COMPARISON_ID =
      UUID.fromString("99000000-0000-0000-0000-000000000101");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private OpComparisonApiService service;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("WEB commander creates an OP comparison analysis request")
  void createsComparisonAnalysis() throws Exception {
    when(service.create(any(), eq("idem-op-comparison-001"), eq(ACCOUNT_ID)))
        .thenReturn(response());

    mockMvc
        .perform(
            post("/api/operational-periods/comparisons")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-op-comparison-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.comparisonId", is(COMPARISON_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.operationalPeriodIds", hasSize(2)))
        .andExpect(jsonPath("$.operationalPeriodIds[0]", is(OP1_ID.toString())))
        .andExpect(jsonPath("$.operationalPeriodIds[1]", is(OP2_ID.toString())))
        .andExpect(jsonPath("$.status", is("READY")))
        .andExpect(jsonPath("$.narrativeStatus", is("SKIPPED")))
        .andExpect(jsonPath("$.metrics", hasSize(0)))
        .andExpect(jsonPath("$.diffFacts", hasSize(0)))
        .andExpect(jsonPath("$.regionFacts", hasSize(0)))
        .andExpect(jsonPath("$.sourceHash", is("a".repeat(64))))
        .andExpect(jsonPath("$.version", is(2)));

    verify(service).create(any(), eq("idem-op-comparison-001"), eq(ACCOUNT_ID));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("APP channel cannot create OP comparison analysis")
  void appCannotCreateComparisonAnalysis() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods/comparisons")
                .header("Authorization", "Bearer app")
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-op-comparison-app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verify(service, never()).create(any(), any(), any());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("write without Idempotency-Key returns write_conflict")
  void requiresIdempotencyKey() throws Exception {
    when(service.create(any(), isNull(), eq(ACCOUNT_ID)))
        .thenThrow(OpComparisonApiException.writeConflict());

    mockMvc
        .perform(
            post("/api/operational-periods/comparisons")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  private OpComparisonResponse response() throws Exception {
    return new OpComparisonResponse(
        COMPARISON_ID,
        INCIDENT_ID,
        List.of(OP1_ID, OP2_ID),
        "a".repeat(64),
        "READY",
        "SKIPPED",
        objectMapper.readTree("[]"),
        objectMapper.readTree("[]"),
        objectMapper.readTree("[]"),
        null,
        null,
        Instant.parse("2026-05-19T00:00:00Z"),
        Instant.parse("2026-05-19T00:00:03Z"),
        2L);
  }

  private static String requestBody() {
    return """
        {
          "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
          "operationalPeriodIds": [
            "88888888-8888-8888-8888-888888880001",
            "88888888-8888-8888-8888-888888880002"
          ]
        }
        """;
  }
}
