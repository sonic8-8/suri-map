package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.app.controller.policephone.PolicePhoneHeartbeatController;
import com.surimap.app.service.policephone.PolicePhoneHeartbeatConfig;
import com.surimap.config.ClockConfig;
import com.surimap.config.GuardConfig;
import com.surimap.config.SecurityConfig;
import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.support.auth.WithMockAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicePhoneHeartbeatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, GuardConfig.class, ClockConfig.class, PolicePhoneHeartbeatConfig.class})
@DisplayName("L2-T03 police phone heartbeat integration")
class PolicePhoneHeartbeatIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockEventHub eventHub;
  @Autowired private PolicePhoneFreshnessQuery freshnessQuery;

  @BeforeEach
  void resetEventHub() {
    eventHub.reset();
  }

  @Test
  @WithMockAccount(policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("assigned police phone heartbeat updates freshness and publishes event")
  void assignedHeartbeatUpdatesFreshnessAndPublishesEvent() throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/police-phones/{policePhoneId}/heartbeat",
                    PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "clientTs": "2026-05-08T09:00:00+09:00",
                      "sequence": 1,
                      "lastSyncAt": "2026-05-08T08:59:30+09:00",
                      "batteryPercent": 88
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ONLINE"))
        .andExpect(jsonPath("$.policePhoneId").value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
        .andExpect(jsonPath("$.sequence").value(1))
        .andExpect(jsonPath("$.version").value(1));

    assertThat(eventHub.findByType(PolicePhoneHeartbeatUpdatedPublishRequest.TYPE)).hasSize(1);

    var row =
        freshnessQuery.byIncident(PolicePhoneFixtures.INCIDENT_ID).stream()
            .filter(
                item -> item.policePhoneId().equals(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
            .findFirst()
            .orElseThrow();

    assertThat(row.accountId()).isEqualTo(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID);
    assertThat(row.version()).isEqualTo(1L);
    assertThat(row.derivedFreshness()).isEqualTo(PolicePhoneFreshnessStatus.ONLINE);
  }

  @Test
  @WithMockAccount(policePhoneId = "00000000-0000-0000-0000-000000000301")
  @DisplayName("registered but unassigned police phone is rejected by assignment guard")
  void unassignedPolicePhoneRejected() throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/police-phones/{policePhoneId}/heartbeat",
                    PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID)
                .header(
                    "X-PolicePhone-Id",
                    PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "clientTs": "2026-05-08T09:00:00+09:00",
                      "sequence": 1
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_assigned"));
  }
}
