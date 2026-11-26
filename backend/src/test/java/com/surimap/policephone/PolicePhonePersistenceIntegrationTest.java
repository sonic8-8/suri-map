package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.OrganizationType;
import com.surimap.global.event.EventPublisher;
import com.surimap.policephone.query.FcmTokenQuery;
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
@DisplayName("S1-2 PolicePhone DB persistence")
class PolicePhonePersistenceIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FcmTokenQuery fcmTokenQuery;
  @MockitoBean private EventPublisher eventHub;

  @BeforeEach
  void resetDbFixtures() {
    jdbcTemplate.update("DELETE FROM fcm_token");
    jdbcTemplate.update("DELETE FROM incident_assignment");
    PolicePhoneDbFixtureSupport.ensureGuardFixtures(jdbcTemplate);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        ) VALUES (?, ?, ?, 'MEMBER', ?, NULL, ?, ?)
        """,
        UUID.fromString("71000000-0000-0000-0000-000000000101"),
        PolicePhoneFixtures.INCIDENT_ID,
        UUID.fromString(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID),
        Instant.parse("2026-05-08T00:00:00Z"),
        Instant.parse("2026-05-08T00:00:00Z"),
        Instant.parse("2026-05-08T00:00:00Z"));
    clearInvocations(eventHub);
  }

  @Test
  @WithMockAccount(
      organizationType = OrganizationType.POLICE_SUBSTATION,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("FCM registration rotates the active DB row per app instance")
  void fcmRegistrationRotatesActiveDbRowPerAppInstance() throws Exception {
    registerFcm("app-instance-db", "fcm-token-db-1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(1));
    registerFcm("app-instance-db", "fcm-token-db-2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(2));

    Integer activeCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM fcm_token
            WHERE police_phone_id = ? AND app_instance_id = ? AND status = 'ACTIVE'
            """,
            Integer.class,
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            "app-instance-db");
    Integer revokedCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM fcm_token
            WHERE police_phone_id = ? AND app_instance_id = ? AND status = 'REVOKED'
            """,
            Integer.class,
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            "app-instance-db");

    assertThat(activeCount).isEqualTo(1);
    assertThat(revokedCount).isEqualTo(1);
    assertThat(fcmTokenQuery.activeByPolicePhone(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.appInstanceId()).isEqualTo("app-instance-db");
              assertThat(row.tokenCiphertext()).isNotEqualTo("fcm-token-db-2");
              assertThat(row.tokenHash()).isNotBlank();
              assertThat(row.version()).isEqualTo(2L);
            });
  }

  private org.springframework.test.web.servlet.ResultActions registerFcm(
      String appInstanceId, String token) throws Exception {
    return mockMvc.perform(
        post("/api/fcm/tokens")
            .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
            .contentType("application/json")
            .content(
                """
                {
                  "appInstanceId": "%s",
                  "token": "%s"
                }
                """
                    .formatted(appInstanceId, token)));
  }
}
