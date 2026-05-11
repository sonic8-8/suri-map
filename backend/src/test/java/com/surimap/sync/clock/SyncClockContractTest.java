package com.surimap.sync.clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@DisplayName("L4-T07A sync clock contract")
class SyncClockContractTest {

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
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = SyncClockContractFixtures.ASSIGNED_POLICE_PHONE_ID)
  @DisplayName("POST /api/sync/clock는 client/server clock offset contract를 반환한다")
  void post_sync_clock_returns_clock_offset_contract() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header(
                    SyncClockContractFixtures.POLICE_PHONE_HEADER,
                    SyncClockContractFixtures.ASSIGNED_POLICE_PHONE_ID)
                .content(SyncClockContractFixtures.requestBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientTs").value(SyncClockContractFixtures.CLIENT_TS))
        .andExpect(jsonPath("$.serverTs").value(SyncClockContractFixtures.SERVER_TS))
        .andExpect(jsonPath("$.clockOffsetMs").value(SyncClockContractFixtures.CLOCK_OFFSET_MS))
        .andExpect(jsonPath("$.clockSyncedAt").isNotEmpty())
        .andExpect(
            jsonPath("$.maxAllowedSkewMs").value(SyncClockContractFixtures.MAX_ALLOWED_SKEW_MS));
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = SyncClockContractFixtures.ASSIGNED_POLICE_PHONE_ID)
  @DisplayName("POST /api/sync/clock는 clock skew 초과 시 409 clock_skew_exceeded를 반환한다")
  void post_sync_clock_rejects_clock_skew_exceeded() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header(
                    SyncClockContractFixtures.POLICE_PHONE_HEADER,
                    SyncClockContractFixtures.ASSIGNED_POLICE_PHONE_ID)
                .content(SyncClockContractFixtures.skewedRequestBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("clock_skew_exceeded"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("POST /api/sync/clock는 WEB channel 요청을 channel_not_allowed로 거부한다")
  void post_sync_clock_rejects_web_channel() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SyncClockContractFixtures.requestBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM)
  @DisplayName("POST /api/sync/clock는 APP PolicePhone 식별자가 없으면 police_phone_required로 거부한다")
  void post_sync_clock_rejects_when_police_phone_missing() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SyncClockContractFixtures.requestBody()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("police_phone_required"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = SyncClockContractFixtures.UNREGISTERED_POLICE_PHONE_ID)
  @DisplayName("POST /api/sync/clock는 미등록 PolicePhone을 police_phone_not_registered로 거부한다")
  void post_sync_clock_rejects_unregistered_police_phone() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                    SyncClockContractFixtures.POLICE_PHONE_HEADER,
                    SyncClockContractFixtures.UNREGISTERED_POLICE_PHONE_ID)
                .content(SyncClockContractFixtures.requestBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_registered"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = SyncClockContractFixtures.UNASSIGNED_POLICE_PHONE_ID)
  @DisplayName("POST /api/sync/clock는 미배정 PolicePhone을 police_phone_not_assigned로 거부한다")
  void post_sync_clock_rejects_unassigned_police_phone() throws Exception {
    mockMvc
        .perform(
            post("/api/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                    SyncClockContractFixtures.POLICE_PHONE_HEADER,
                    SyncClockContractFixtures.UNASSIGNED_POLICE_PHONE_ID)
                .content(SyncClockContractFixtures.requestBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_assigned"));
  }
}
