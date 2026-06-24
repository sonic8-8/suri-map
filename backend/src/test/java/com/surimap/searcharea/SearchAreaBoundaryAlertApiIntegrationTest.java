package com.surimap.searcharea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.common.auth.Channel;
import com.surimap.support.auth.WithMockAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Search area boundary alert API")
@Tag("integration")
@WithMockAccount(
    accountId = "62000000-0000-0000-0000-000000004180",
    channel = Channel.APP,
    policePhoneId = "50000000-0000-0000-0000-000000004180")
class SearchAreaBoundaryAlertApiIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000004180");
  private static final UUID SOURCE_INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000004181");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000004180");
  private static final UUID SEARCH_AREA_ID =
      UUID.fromString("72000000-0000-0000-0000-000000004180");
  private static final UUID ASSIGNMENT_ID =
      UUID.fromString("73000000-0000-0000-0000-000000004180");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000004180");
  private static final UUID COMMANDER_ID =
      UUID.fromString("63000000-0000-0000-0000-000000004180");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000004180");
  private static final UUID INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000004180");
  private static final Instant BASE_TS = Instant.parse("2026-05-19T02:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private MockFcmDispatcher mockFcmDispatcher;

  @BeforeEach
  void seedContext() {
    mockFcmDispatcher.reset();
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          event_dispatch_job,
          idempotency_record,
          fcm_token,
          search_path_lifecycle_event,
          search_path_excluded_point,
          search_path_segment,
          search_path,
          duty_shift,
          search_area_assignment,
          search_area_history,
          search_area,
          operational_period,
          incident_assignment,
          police_phone,
          account,
          incident
        CASCADE
        """);

    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 'Boundary alert incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID.toString(),
        SOURCE_INCIDENT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES
          (?::uuid, 'acct-boundary-app', '{noop}fixture', 'Boundary app account',
           'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?),
          (?::uuid, 'acct-boundary-command', '{noop}fixture', 'Boundary command account',
           'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        """,
        ACCOUNT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (
          id, phone_code, display_name, account_id, status, registered,
          created_at, updated_at, version
        )
        VALUES (?::uuid, 'phone-boundary-418', 'Boundary phone', ?::uuid, 'ACTIVE', TRUE, ?, ?, 1)
        """,
        POLICE_PHONE_ID.toString(),
        ACCOUNT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?)
        """,
        INCIDENT_ASSIGNMENT_ID.toString(),
        INCIDENT_ID.toString(),
        ACCOUNT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?)
        """,
        OP_ID.toString(),
        INCIDENT_ID.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
          id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
          version, created_by_account_id, created_at, updated_at
        )
        VALUES (
          ?::uuid, ?::uuid, NULL, 'A팀 담당 구역', 'TEAM',
          ST_GeomFromText('POLYGON((126.910000 35.160000,126.918000 35.160000,126.918000 35.166000,126.910000 35.166000,126.910000 35.160000))', 4326),
          'ACTIVE', 3, ?::uuid, ?, ?
        )
        """,
        SEARCH_AREA_ID.toString(),
        OP_ID.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO search_area_assignment (
          id, search_area_id, assigned_account_id, assigned_by_account_id, assigned_at, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', ?, ?)
        """,
        ASSIGNMENT_ID.toString(),
        SEARCH_AREA_ID.toString(),
        ACCOUNT_ID.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO fcm_token (
          id, account_id, police_phone_id, app_instance_id, token_hash, token_ciphertext, status,
          created_at, last_registered_at, version
        )
        VALUES (
          '74000000-0000-0000-0000-000000004180'::uuid, ?::uuid, ?::uuid,
          'boundary-app-instance', 'hash-boundary-token', 'cipher:fcm-boundary-token',
          'ACTIVE', ?, ?, 1
        )
        """,
        ACCOUNT_ID.toString(),
        POLICE_PHONE_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  @Test
  @DisplayName("POST /api/search-area-boundary-alerts stores advisory alert and sends FCM data")
  void create_outside_assigned_area_alert_stores_and_dispatches_fcm() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/search-area-boundary-alerts")
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                    .header("Idempotency-Key", "idem-boundary-alert-418")
                    .contentType("application/json")
                    .content(boundaryAlertBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
            .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
            .andExpect(jsonPath("$.searchAreaId", is(SEARCH_AREA_ID.toString())))
            .andExpect(jsonPath("$.policePhoneId", is(POLICE_PHONE_ID.toString())))
            .andExpect(jsonPath("$.alertType", is("OUTSIDE_ASSIGNED_AREA")))
            .andExpect(jsonPath("$.status", is("RECORDED")))
            .andExpect(jsonPath("$.version", is(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String alertId = extract(response, "id");
    String eventId = extract(response, "eventId");

    Integer alertCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM search_area_boundary_alert WHERE id = ?::uuid",
            Integer.class,
            alertId);
    assertThat(alertCount).isEqualTo(1);

    Map<String, Object> eventRow =
        jdbcTemplate.queryForMap(
            """
            SELECT event_type, source_entity_type, source_entity_id
            FROM event_dispatch_job
            WHERE event_id = ?::uuid
            """,
            eventId);
    assertThat(eventRow.get("event_type")).isEqualTo("SEARCH_AREA_BOUNDARY_EXITED");
    assertThat(eventRow.get("source_entity_type")).isEqualTo("search_area_boundary_alert");
    assertThat(eventRow.get("source_entity_id").toString()).isEqualTo(alertId);

    var dispatch = mockFcmDispatcher.findByEventType("SEARCH_AREA_BOUNDARY_EXITED");
    assertThat(dispatch).hasSize(1);
    assertThat(dispatch.get(0).recipients()).containsExactly("fcm-boundary-token");
    assertThat(dispatch.get(0).payload())
        .containsEntry("eventId", eventId)
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("opId", OP_ID.toString())
        .containsEntry("searchAreaId", SEARCH_AREA_ID.toString())
        .containsEntry("policePhoneId", POLICE_PHONE_ID.toString())
        .containsEntry("status", "RECORDED")
        .containsEntry("version", "1");
  }

  @Test
  @DisplayName("same idempotency key replays response without duplicate alert or FCM")
  void same_idempotency_key_replays_without_duplicate_side_effects() throws Exception {
    String first =
        mockMvc
            .perform(
                post("/api/search-area-boundary-alerts")
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                    .header("Idempotency-Key", "idem-boundary-alert-replay-418")
                    .contentType("application/json")
                    .content(boundaryAlertBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        mockMvc
            .perform(
                post("/api/search-area-boundary-alerts")
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                    .header("Idempotency-Key", "idem-boundary-alert-replay-418")
                    .contentType("application/json")
                    .content(boundaryAlertBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(extract(second, "id")).isEqualTo(extract(first, "id"));
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_area_boundary_alert", Integer.class))
        .isEqualTo(1);
    assertThat(mockFcmDispatcher.findByEventType("SEARCH_AREA_BOUNDARY_EXITED")).hasSize(1);
  }

  @Test
  @WithMockAccount(
      accountId = "62000000-0000-0000-0000-000000004180",
      channel = Channel.WEB)
  @DisplayName("WEB POST /api/search-area-boundary-alerts는 channel_not_allowed로 거부한다")
  void web_channel_boundary_alert_is_rejected() throws Exception {
    mockMvc
        .perform(
            post("/api/search-area-boundary-alerts")
                .header("X-Client-Channel", "WEB")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-boundary-alert-web-418")
                .contentType("application/json")
                .content(boundaryAlertBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_area_boundary_alert", Integer.class))
        .isZero();
  }

  @Test
  @WithMockAccount(
      accountId = "63000000-0000-0000-0000-000000004180",
      channel = Channel.APP,
      policePhoneId = "50000000-0000-0000-0000-000000004180")
  @DisplayName("담당 구역에 배정되지 않은 accountId는 policePhoneId만으로 경계 알림을 생성할 수 없다")
  void unassigned_account_cannot_create_boundary_alert_with_assigned_phone() throws Exception {
    mockMvc
        .perform(
            post("/api/search-area-boundary-alerts")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-boundary-alert-unassigned-account-418")
                .contentType("application/json")
                .content(boundaryAlertBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("team_not_assigned")));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_area_boundary_alert", Integer.class))
        .isZero();
  }

  private static String boundaryAlertBody() {
    return """
        {
          "incidentId": "%s",
          "opId": "%s",
          "searchAreaId": "%s",
          "alertType": "OUTSIDE_ASSIGNED_AREA",
          "location": {
            "type": "Point",
            "coordinates": [126.920000, 35.168000]
          },
          "clientTs": "2026-05-19T11:05:00+09:00",
          "clockOffsetMs": 0
        }
        """
        .formatted(INCIDENT_ID, OP_ID, SEARCH_AREA_ID);
  }

  private static String extract(String json, String key) {
    String marker = "\"" + key + "\":\"";
    int start = json.indexOf(marker);
    if (start < 0) {
      throw new IllegalStateException(key + " not found in response: " + json);
    }
    int from = start + marker.length();
    int to = json.indexOf('"', from);
    return json.substring(from, to);
  }
}
