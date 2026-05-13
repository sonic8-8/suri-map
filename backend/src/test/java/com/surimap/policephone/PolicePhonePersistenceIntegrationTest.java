package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.OrganizationType;
import com.surimap.eventhub.port.EventHub;
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
  @MockitoBean private EventHub eventHub;

  @BeforeEach
  void resetDbFixtures() {
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
        """
        UPDATE police_phone
        SET registered = TRUE
        WHERE id IN (?, ?, ?)
        """,
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        PolicePhoneFixtures.ASSIGNED_PATH_POLICE_PHONE_ID,
        PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID);
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
  @DisplayName("heartbeat persists sequence, timestamps, version and publishes one event")
  void heartbeatPersistsSequenceTimestampsVersionAndPublishesEvent() throws Exception {
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
                      "sequence": 3,
                      "lastSyncAt": "2026-05-08T08:59:30+09:00",
                      "batteryPercent": 88
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ONLINE"))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.sequence").value(3))
        .andExpect(
            jsonPath("$.policePhoneId").value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()));

    var row =
        jdbcTemplate.queryForMap(
            """
            SELECT heartbeat_sequence, version, last_heartbeat_event_id, last_heartbeat_at, last_sync_at
            FROM police_phone
            WHERE id = ?
            """,
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
    assertThat(row.get("HEARTBEAT_SEQUENCE")).isEqualTo(3L);
    assertThat(row.get("VERSION")).isEqualTo(2L);
    assertThat(row.get("LAST_HEARTBEAT_EVENT_ID")).isNotNull();
    assertThat(row.get("LAST_HEARTBEAT_AT")).isNotNull();
    assertThat(row.get("LAST_SYNC_AT")).isNotNull();

    verify(eventHub)
        .publish(
            argThat(
                request ->
                    PolicePhoneHeartbeatUpdatedPublishRequest.TYPE.equals(request.type())
                        && PolicePhoneFixtures.INCIDENT_ID.equals(request.incidentId())
                        && PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.equals(request.sourceEntityId())
                        && request.payload().get("sequence").equals(3L)
                        && request.payload().get("version").equals(2L)));
  }

  @Test
  @WithMockAccount(
      organizationType = OrganizationType.POLICE_SUBSTATION,
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("stale heartbeat sequence keeps the accepted DB state and does not publish")
  void staleHeartbeatSequenceKeepsAcceptedDbStateAndDoesNotPublish() throws Exception {
    heartbeat(5);
    heartbeat(4)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sequence").value(5))
        .andExpect(jsonPath("$.version").value(2));

    var row =
        jdbcTemplate.queryForMap(
            "SELECT heartbeat_sequence, version FROM police_phone WHERE id = ?",
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
    assertThat(row.get("HEARTBEAT_SEQUENCE")).isEqualTo(5L);
    assertThat(row.get("VERSION")).isEqualTo(2L);
    verify(eventHub, times(1)).publish(argThat(request -> PolicePhoneHeartbeatUpdatedPublishRequest.TYPE.equals(request.type())));
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

  private org.springframework.test.web.servlet.ResultActions heartbeat(long sequence) throws Exception {
    return mockMvc.perform(
        post(
                "/api/police-phones/{policePhoneId}/heartbeat",
                PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
            .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
            .contentType("application/json")
            .content(
                """
                {
                  "clientTs": "2026-05-08T09:00:00+09:00",
                  "sequence": %d,
                  "lastSyncAt": "2026-05-08T08:59:30+09:00"
                }
                """
                    .formatted(sequence)));
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
