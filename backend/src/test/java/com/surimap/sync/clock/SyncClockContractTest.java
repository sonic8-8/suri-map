package com.surimap.sync.clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc(addFilters = false)
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
  @DisplayName("POST /sync/clock는 client/server clock offset contract를 반환한다")
  void post_sync_clock_returns_clock_offset_contract() throws Exception {
    mockMvc
        .perform(
            post("/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header("X-Device-Id", SyncClockContractFixtures.DEVICE_ID)
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
  @DisplayName("POST /sync/clock는 clock skew 초과 시 409 clock_skew_exceeded를 반환한다")
  void post_sync_clock_rejects_clock_skew_exceeded() throws Exception {
    mockMvc
        .perform(
            post("/sync/clock")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header("X-Device-Id", SyncClockContractFixtures.DEVICE_ID)
                .content(SyncClockContractFixtures.skewedRequestBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("clock_skew_exceeded"));
  }
}
