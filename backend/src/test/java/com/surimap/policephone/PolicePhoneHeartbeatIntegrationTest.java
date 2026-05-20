package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.OrganizationType;
import com.surimap.eventhub.port.EventHub;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("L2-T03 police phone heartbeat integration")
class PolicePhoneHeartbeatIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EventHub eventHub;
  @Autowired private PolicePhoneFreshnessQuery freshnessQuery;

  @BeforeEach
  void resetFixtures() {
    clearInvocations(eventHub);
    jdbcTemplate.update("DELETE FROM fcm_token");
    jdbcTemplate.update("DELETE FROM incident_assignment");
    jdbcTemplate.update(
        """
        UPDATE police_phone
        SET registered = FALSE,
            last_heartbeat_at = NULL,
            last_sync_at = NULL,
            heartbeat_sequence = 0,
            last_heartbeat_event_id = NULL,
            version = 1
        """);
    PolicePhoneDbFixtureSupport.ensureGuardFixtures(jdbcTemplate);
    jdbcTemplate.update(
        "UPDATE police_phone SET registered = TRUE WHERE id IN (?, ?)",
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        ) VALUES (?, ?, ?, 'MEMBER', ?, NULL, ?, ?)
        """,
        UUID.fromString("71000000-0000-0000-0000-000000000102"),
        PolicePhoneFixtures.INCIDENT_ID,
        UUID.fromString(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID),
        Instant.parse("2026-05-08T00:00:00Z"),
        Instant.parse("2026-05-08T00:00:00Z"),
        Instant.parse("2026-05-08T00:00:00Z"));
  }

  @Test
  @WithMockAccount(
      organizationType = OrganizationType.POLICE_SUBSTATION,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
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
        .andExpect(
            jsonPath("$.policePhoneId").value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
        .andExpect(jsonPath("$.sequence").value(1))
        .andExpect(jsonPath("$.version").value(2));

    verify(eventHub)
        .publish(
            argThat(
                request ->
                    PolicePhoneHeartbeatUpdatedPublishRequest.TYPE.equals(request.type())
                        && PolicePhoneFixtures.INCIDENT_ID.equals(request.incidentId())));

    var row =
        freshnessQuery.byIncident(PolicePhoneFixtures.INCIDENT_ID).stream()
            .filter(
                item -> item.policePhoneId().equals(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
            .findFirst()
            .orElseThrow();

    assertThat(row.accountId()).isEqualTo(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID);
    assertThat(row.version()).isEqualTo(2L);
    assertThat(row.derivedFreshness()).isEqualTo(PolicePhoneFreshnessStatus.ONLINE);
  }

  @Test
  @WithMockAccount(
      accountId = "11111111-1111-1111-1111-111111110004",
      organizationType = OrganizationType.POLICE_SUBSTATION,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("heartbeat rejects police phone owned by a different account")
  void heartbeatRejectsPolicePhoneOwnedByDifferentAccount() throws Exception {
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
                      "sequence": 1
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("police_phone_not_assigned"));
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
