package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.surimap.account.AccountIdentityCatalog;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.policephone.PolicePhoneFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** S14P31C106-303 runtime marker create guard integration test. */
@AutoConfigureMockMvc
@DisplayName("S14P31C106-303 APP marker create runtime guard")
class MarkerCreateRuntimeGuardIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = PolicePhoneFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID ASSIGNMENT_ID =
      UUID.fromString("71000000-0000-0000-0000-000000000303");
  private static final UUID COMMANDER_ASSIGNMENT_ID =
      UUID.fromString("71000000-0000-0000-0000-000000000304");
  private static final UUID DUTY_SHIFT_ID =
      UUID.fromString("b340b075-e784-474e-9e2b-d131dcc00303");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("32000000-0000-0000-0000-000000000303");
  private static final String MARKER_MEMO = "S14P31C106-303 runtime marker";
  private static final String COMMANDER_MARKER_MEMO =
      "S14P31C106-400 commander runtime marker";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUpRuntimeFixture() {
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE event_type = 'MARKER_CREATED'");
    jdbcTemplate.update("DELETE FROM marker WHERE memo = ?", MARKER_MEMO);
    jdbcTemplate.update("DELETE FROM marker WHERE memo = ?", COMMANDER_MARKER_MEMO);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?",
        "idem-marker-runtime-303");
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?",
        "idem-marker-runtime-400-commander");

    jdbcTemplate.update(
        """
        INSERT INTO incident (
            id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id,
            version, created_at, updated_at
        ) VALUES (
            ?, '30000000-0000-0000-0000-000000000303', 'Runtime marker fixture', 'OPEN',
            '2026-04-28T09:00:00+09:00', NULL, NULL, 1,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            status = 'OPEN',
            closed_at = NULL,
            closed_by_account_id = NULL,
            updated_at = EXCLUDED.updated_at
        """,
        INCIDENT_ID);
    jdbcTemplate.update(
        """
        UPDATE police_phone
        SET registered = TRUE, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """,
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        ) VALUES (
            ?, ?, ?, 'MEMBER',
            '2026-04-28T09:00:00+09:00', NULL,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            incident_id = EXCLUDED.incident_id,
            account_id = EXCLUDED.account_id,
            revoked_at = NULL,
            updated_at = EXCLUDED.updated_at
        """,
        ASSIGNMENT_ID,
        INCIDENT_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        ) VALUES (
            ?, ?, ?, 'MEMBER',
            '2026-04-28T09:00:00+09:00', NULL,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            incident_id = EXCLUDED.incident_id,
            account_id = EXCLUDED.account_id,
            incident_role = EXCLUDED.incident_role,
            revoked_at = NULL,
            updated_at = EXCLUDED.updated_at
        """,
        COMMANDER_ASSIGNMENT_ID,
        INCIDENT_ID,
        AccountIdentityCatalog.PRECINCT_COMMANDER_ID);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, reason_memo, started_by_account_id,
            ended_by_account_id, started_at, ended_at, version, created_at, updated_at
        ) VALUES (
            ?, ?, 1, 'ACTIVE', 'INITIAL', NULL, ?, NULL,
            '2026-04-28T09:00:00+09:00', NULL, 1,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            incident_id = EXCLUDED.incident_id,
            status = 'ACTIVE',
            ended_at = NULL,
            updated_at = EXCLUDED.updated_at
        """,
        OP_ID,
        INCIDENT_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_ID);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
            id, operational_period_id, incident_assignment_id, police_phone_id, status,
            started_by_account_id, ended_by_account_id, started_at, ended_at, version,
            created_at, updated_at
        ) VALUES (
            ?, ?, ?, ?, 'ACTIVE', ?, NULL,
            '2026-04-28T09:00:00+09:00', NULL, 1,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            operational_period_id = EXCLUDED.operational_period_id,
            incident_assignment_id = EXCLUDED.incident_assignment_id,
            police_phone_id = EXCLUDED.police_phone_id,
            status = 'ACTIVE',
            ended_at = NULL,
            updated_at = EXCLUDED.updated_at
        """,
        DUTY_SHIFT_ID,
        OP_ID,
        ASSIGNMENT_ID,
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_ID);
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
            id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
            version, created_by_account_id, created_at, updated_at
        ) VALUES (
            ?, ?, NULL, 'Runtime overall search area', 'OVERALL',
            ST_GeomFromText(
                'POLYGON((126.904000 35.158000,126.923000 35.158000,126.923000 35.173000,126.904000 35.173000,126.904000 35.158000))',
                4326
            ),
            'ACTIVE', 1, ?,
            '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'
        )
        ON CONFLICT (id) DO UPDATE SET
            operational_period_id = EXCLUDED.operational_period_id,
            geometry = EXCLUDED.geometry,
            status = 'ACTIVE',
            updated_at = EXCLUDED.updated_at
        """,
        OVERALL_AREA_ID,
        OP_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_ID);
  }

  @Test
  @DisplayName("APP OIDC bearer can create marker with production runtime guards")
  void appOidcBearerCanCreateMarkerWithProductionRuntimeGuards() throws Exception {
    String accessToken = loginAppAccessToken();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/markers")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Client-Channel", "APP")
                    .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                    .header("Idempotency-Key", "idem-marker-runtime-303")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "incidentId": "%s",
                          "opId": "%s",
                          "type": "CLUE",
                          "location": {
                            "type": "Point",
                            "coordinates": [126.913400, 35.163100]
                          },
                          "memo": "%s",
                          "clientTs": "2026-04-28T09:05:00+09:00",
                          "clockOffsetMs": 0
                        }
                        """
                            .formatted(INCIDENT_ID, OP_ID, MARKER_MEMO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
            .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
            .andExpect(
                jsonPath("$.policePhoneId")
                    .value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.version", is(1)))
            .andReturn();

    String markerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Integer markerRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM marker WHERE id = ?::uuid AND memo = ?",
            Integer.class,
            markerId,
            MARKER_MEMO);
    Integer eventRows =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM event_dispatch_job
            WHERE event_type = 'MARKER_CREATED'
              AND source_entity_type = 'marker'
              AND source_entity_id = ?::uuid
              AND payload->>'id' = ?
            """,
            Integer.class,
            markerId,
            markerId);

    assertThat(markerRows).isEqualTo(1);
    assertThat(eventRows).isEqualTo(1);
  }

  @Test
  @DisplayName("배정 계정은 다른 계정에 연결된 등록 PolicePhone에서도 marker를 생성할 수 있다")
  void assignedAccountCanCreateMarkerFromRegisteredPolicePhoneBoundToAnotherAccount()
      throws Exception {
    String accessToken = loginCommanderAccessTokenOnAssignedPhone();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/markers")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Client-Channel", "APP")
                    .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                    .header("Idempotency-Key", "idem-marker-runtime-400-commander")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "incidentId": "%s",
                          "opId": "%s",
                          "type": "CLUE",
                          "location": {
                            "type": "Point",
                            "coordinates": [126.913450, 35.163150]
                          },
                          "memo": "%s",
                          "clientTs": "2026-04-28T09:06:00+09:00",
                          "clockOffsetMs": 0
                        }
                        """
                            .formatted(INCIDENT_ID, OP_ID, COMMANDER_MARKER_MEMO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
            .andExpect(
                jsonPath("$.policePhoneId")
                    .value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
            .andReturn();

    String markerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Integer markerRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM marker WHERE id = ?::uuid AND memo = ?",
            Integer.class,
            markerId,
            COMMANDER_MARKER_MEMO);

    assertThat(markerRows).isEqualTo(1);
  }

  private String loginAppAccessToken() {
    String accessToken = "header.marker-runtime.signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", AccountIdentityCatalog.PRECINCT_TEAM_ID.toString())
                .claim("accountType", "TEAM")
                .claim("organizationType", "POLICE_SUBSTATION")
                .claim("policePhoneId", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString())
                .claim("realm_access", Map.of("roles", List.of("MEMBER")))
                .build());
    return accessToken;
  }

  private String loginCommanderAccessTokenOnAssignedPhone() {
    String accessToken = "header.marker-runtime-commander.signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", AccountIdentityCatalog.PRECINCT_COMMANDER_ID.toString())
                .claim("accountType", "COMMAND")
                .claim("organizationType", "POLICE_SUBSTATION")
                .claim("policePhoneId", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString())
                .claim("realm_access", Map.of("roles", List.of("COMMANDER")))
                .build());
    return accessToken;
  }
}
