package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.client.fcm.MockFcmDispatcher;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("마커 알림 수신 업무폰은 수신 계정의 ACTIVE FCM 토큰으로 정한다")
class MarkerNotificationRecipientApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000005701");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000005701");
  private static final UUID AREA_ID = UUID.fromString("72000000-0000-0000-0000-000000005701");
  private static final UUID COMMANDER_ID = UUID.fromString("63000000-0000-0000-0000-000000005701");
  // A는 현장 지휘관이라 지원 요청 알림과 발견 알림을 모두 받는다.
  private static final UUID ACCOUNT_A_ID = UUID.fromString("62000000-0000-0000-0000-00000000570a");
  // B는 일반 팀원이라 발견 알림만 받는다.
  private static final UUID ACCOUNT_B_ID = UUID.fromString("62000000-0000-0000-0000-00000000570b");
  // C는 마커를 남기는 사람이다.
  private static final UUID AUTHOR_ID = UUID.fromString("62000000-0000-0000-0000-00000000570c");
  private static final UUID AUTHOR_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-00000000570c");
  private static final UUID POLICE_PHONE_P_ID =
      UUID.fromString("50000000-0000-0000-0000-000000005701");
  private static final UUID AUTHOR_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000005702");
  private static final String APP_INSTANCE_ID = "app-instance-5701";
  private static final String TOKEN_ON_P = "fcm-token-on-phone-p";
  private static final Instant BASE_TS = Instant.parse("2026-11-25T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private MockFcmDispatcher fcmDispatcher;

  @BeforeEach
  void seedIncident() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          event_dispatch_job, idempotency_record, fcm_token, marker_notification, photo, marker,
          duty_shift, search_area_assignment, search_area, operational_period,
          incident_assignment, police_phone, account, incident
        CASCADE
        """);
    fcmDispatcher.reset();
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, gen_random_uuid(), 'Marker notification recipient incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES
          (?, 'acct-5701-a', '{noop}fixture', '현장 지휘 A', 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?),
          (?, 'acct-5701-b', '{noop}fixture', '팀원 B', 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?),
          (?, 'acct-5701-c', '{noop}fixture', '작성자 C', 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?),
          (?, 'acct-5701-cmd', '{noop}fixture', '지휘 계정', 'COMMAND', 'POLICE_SUBSTATION',
           'ACTIVE', ?, ?)
        """,
        ACCOUNT_A_ID,
        base,
        base,
        ACCOUNT_B_ID,
        base,
        base,
        AUTHOR_ID,
        base,
        base,
        COMMANDER_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES
          (gen_random_uuid(), ?, ?, 'FIELD_COMMANDER', ?, ?, ?),
          (gen_random_uuid(), ?, ?, 'MEMBER', ?, ?, ?),
          (?, ?, ?, 'MEMBER', ?, ?, ?)
        """,
        INCIDENT_ID,
        ACCOUNT_A_ID,
        base,
        base,
        base,
        INCIDENT_ID,
        ACCOUNT_B_ID,
        base,
        base,
        base,
        AUTHOR_ASSIGNMENT_ID,
        INCIDENT_ID,
        AUTHOR_ID,
        base,
        base,
        base);
    // 업무폰 P가 A에 연결돼 있어도 수신 업무폰은 이 연결로 정하지 않는다.
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (
          id, phone_code, display_name, account_id, status, registered,
          created_at, updated_at, version
        )
        VALUES (?, 'phone-5701-p', '업무폰 P', ?, 'ACTIVE', TRUE, ?, ?, 1)
        """,
        POLICE_PHONE_P_ID,
        ACCOUNT_A_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL_IMPORT', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ID,
        COMMANDER_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
          id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
          version, created_by_account_id, created_at, updated_at
        )
        VALUES
          (?, ?, NULL, '작성자 담당 구역', 'TEAM',
           ST_GeomFromText('POLYGON((126.910000 35.160000,126.918000 35.160000,126.918000 35.166000,126.910000 35.166000,126.910000 35.160000))', 4326),
           'ACTIVE', 1, ?, ?, ?)
        """,
        AREA_ID,
        OP_ID,
        COMMANDER_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO search_area_assignment (
          id, search_area_id, assigned_account_id, assigned_by_account_id, assigned_at, status,
          created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        AREA_ID,
        AUTHOR_ID,
        COMMANDER_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, status,
          started_by_account_id, started_at, version, created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?, ?, 'ACTIVE', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        AUTHOR_ASSIGNMENT_ID,
        AUTHOR_ID,
        base,
        base,
        base);
  }

  @Test
  @DisplayName("업무폰 P의 토큰을 계정 B로 다시 등록하면 A의 알림은 P로 가지 않고 B의 알림은 P로 간다")
  void token_reregistered_by_other_account_moves_marker_notifications_to_that_account()
      throws Exception {
    // given: A로 쓰던 업무폰 P에서 B로 로그인해 같은 앱 설치의 토큰을 다시 등록한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID, POLICE_PHONE_P_ID).andExpect(status().isOk());
    perform(fcmTokenRegister(), ACCOUNT_B_ID, POLICE_PHONE_P_ID).andExpect(status().isOk());

    // when: 작성자가 A만 받는 지원 요청 마커를 남긴다.
    perform(markerCreate("SUPPORT_REQUEST", "\"DRONE\""), AUTHOR_ID, AUTHOR_POLICE_PHONE_ID)
        .andExpect(status().isCreated());

    // then: A에게는 ACTIVE 토큰이 없으므로 업무폰 P로 알림이 가지 않는다.
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P)).isEmpty();

    // when: 작성자가 B도 받는 발견 마커를 남긴다.
    perform(markerCreate("PERSON_FOUND", "null"), AUTHOR_ID, AUTHOR_POLICE_PHONE_ID)
        .andExpect(status().isCreated());

    // then: B의 ACTIVE 토큰이 있는 업무폰 P로 발견 알림이 간다.
    assertThat(fcmDispatcher.findByEventType("PERSON_FOUND"))
        .singleElement()
        .satisfies(dispatch -> assertThat(dispatch.recipients()).containsExactly(TOKEN_ON_P));
  }

  @Test
  @DisplayName("마커 알림 FCM payload에는 업무폰 ID 필드가 없고 전송이 실패하지 않는다")
  void marker_notification_fcm_payload_has_no_police_phone_fields() throws Exception {
    perform(fcmTokenRegister(), ACCOUNT_B_ID, POLICE_PHONE_P_ID).andExpect(status().isOk());

    perform(markerCreate("PERSON_FOUND", "null"), AUTHOR_ID, AUTHOR_POLICE_PHONE_ID)
        .andExpect(status().isCreated());

    assertThat(fcmDispatcher.findByEventType("PERSON_FOUND"))
        .singleElement()
        .satisfies(
            dispatch ->
                assertThat(dispatch.payload())
                    .doesNotContainKeys(
                        "policePhoneId", "recipientPolicePhoneIds", "policePhoneName")
                    .containsEntry("type", "PERSON_FOUND"));
  }

  // 운영 인증 정보에는 X-PolicePhone-Id 헤더 값이 포함된다. 테스트도 같은 값을 넣는다.
  private ResultActions perform(
      MockHttpServletRequestBuilder request, UUID accountId, UUID policePhoneId) throws Exception {
    request
        .header("X-Client-Channel", "APP")
        .header("Authorization", "Bearer app-token")
        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
        .header("X-PolicePhone-Id", policePhoneId.toString());
    TestSecurityContextHolder.setAuthentication(
        new SuriMapAuthentication(
            accountId.toString(),
            AccountType.TEAM,
            OrganizationType.MISSING_TEAM,
            Channel.APP,
            policePhoneId.toString(),
            List.of(new SimpleGrantedAuthority(Role.MEMBER.name()))));
    return mockMvc.perform(request);
  }

  private static MockHttpServletRequestBuilder fcmTokenRegister() {
    return json(
        post("/api/fcm/tokens"),
        """
        { "appInstanceId": "%s", "token": "%s" }
        """
            .formatted(APP_INSTANCE_ID, TOKEN_ON_P));
  }

  private static MockHttpServletRequestBuilder markerCreate(
      String type, String supportRequestTypeJson) {
    return json(
        post("/api/markers"),
        """
        {
          "incidentId": "%s",
          "opId": "%s",
          "type": "%s",
          "supportRequestType": %s,
          "location": { "type": "Point", "coordinates": [126.913400, 35.163100] },
          "clientTs": "%s",
          "clockOffsetMs": 0
        }
        """
            .formatted(INCIDENT_ID, OP_ID, type, supportRequestTypeJson, Instant.now()));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
