package com.surimap.sync.outbox;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.support.auth.WithMockAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("L4-T07D Outbox requeue diagnostics contract")
class OutboxRequeueContractTest {

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-04-28T00:00:41Z"), ZoneId.of("Asia/Seoul"));
    }
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
  void appRequeueRequestReturnsAcceptedDiagnosticContract() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header("Authorization", "Bearer app-token-sync")
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isAccepted())
        .andExpect(
            jsonPath(
                    "$.operationId",
                    is(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.operationId())))
        .andExpect(jsonPath("$.accepted", is(true)))
        .andExpect(jsonPath("$.serverTs").exists())
        .andExpect(jsonPath("$.outboxStatus", is("PENDING")))
        .andExpect(jsonPath("$.retryable", is(true)))
        .andExpect(jsonPath("$.diagnosticState", is("RETRYABLE")))
        .andExpect(jsonPath("$.userSafeFailureCategory", is("RETRYABLE_NETWORK")))
        .andExpect(jsonPath("$.lastError").doesNotExist());
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
  void staleClockRequeueReturnsRetryableClockDiagnosticWithoutRawInternalError() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.STALE_CLOCK_REQUEUE.json()))
        .andExpect(status().isAccepted())
        .andExpect(
            jsonPath(
                "$.operationId",
                is(OutboxRetryDiagnosticsFixtures.STALE_CLOCK_REQUEUE.operationId())))
        .andExpect(jsonPath("$.accepted", is(false)))
        .andExpect(jsonPath("$.outboxStatus", is("FAILED_RETRYABLE")))
        .andExpect(jsonPath("$.retryable", is(true)))
        .andExpect(jsonPath("$.diagnosticState", is("RETRYABLE")))
        .andExpect(jsonPath("$.userSafeFailureCategory", is("CLOCK_RESYNC_REQUIRED")))
        .andExpect(jsonPath("$.lastError").doesNotExist())
        .andExpect(content().string(not(containsString("clock_skew_exceeded"))));
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
  void closedIncidentRequeueReturnsTerminalDiagnosticWithoutPostCloseInternalError()
      throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.CLOSED_INCIDENT_REQUEUE.json()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("incident_closed")))
        .andExpect(jsonPath("$.outboxStatus", is("FAILED_FINAL")))
        .andExpect(jsonPath("$.retryable").doesNotExist())
        .andExpect(jsonPath("$.diagnosticState").doesNotExist())
        .andExpect(jsonPath("$.userSafeFailureCategory").doesNotExist())
        .andExpect(jsonPath("$.lastError").doesNotExist())
        .andExpect(content().string(not(containsString("post_close_requeue_rejected"))));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  void webRequeueRequestIsRejectedWithChannelNotAllowed() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  void appRequeueRequestWithoutPolicePhoneSessionIsRejectedWithPolicePhoneRequired()
      throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.ASSIGNED_AUTH_POLICE_PHONE_ID)
  void appRequeueRequestWithoutPolicePhoneHeaderIsRejectedWithPolicePhoneRequired()
      throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.UNREGISTERED_AUTH_POLICE_PHONE_ID)
  void appRequeueRequestWithUnregisteredPolicePhoneIsRejected() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.UNREGISTERED_AUTH_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));
  }

  @Test
  @WithMockAccount(policePhoneId = OutboxRetryDiagnosticsFixtures.UNASSIGNED_AUTH_POLICE_PHONE_ID)
  void appRequeueRequestWithUnassignedPolicePhoneIsRejected() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.UNASSIGNED_AUTH_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_assigned")));
  }
}
