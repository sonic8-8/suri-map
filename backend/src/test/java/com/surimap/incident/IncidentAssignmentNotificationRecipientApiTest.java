package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.client.fcm.MockFcmDispatcher;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("배정 변경 알림은 지금 그 업무폰에 토큰을 등록한 계정에게만 간다")
class IncidentAssignmentNotificationRecipientApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000005901");
  private static final String SOURCE_INCIDENT_ID = "20000000-0000-0000-0000-000000005901";
  private static final UUID ACCOUNT_A_ID = UUID.fromString("62000000-0000-0000-0000-00000000590a");
  private static final UUID ACCOUNT_B_ID = UUID.fromString("62000000-0000-0000-0000-00000000590b");
  private static final UUID POLICE_PHONE_P_ID =
      UUID.fromString("50000000-0000-0000-0000-000000005901");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000005902");
  private static final String APP_INSTANCE_ID = "app-instance-5901";
  private static final String TOKEN_ON_P = "fcm-token-on-phone-p";
  private static final Instant BASE_TS = Instant.parse("2026-11-26T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private MockFcmDispatcher fcmDispatcher;
  @MockitoBean private ExternalIncidentAdapter externalIncidentAdapter;

  @BeforeEach
  void seedIncident() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          event_dispatch_job, mock112_webhook_event, fcm_token, incident_assignment, account,
          incident
        CASCADE
        """);
    fcmDispatcher.reset();
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, ?, 'Assignment notification recipient incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        UUID.fromString(SOURCE_INCIDENT_ID),
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
          (?, 'acct-5901-a', '{noop}fixture', '팀원 A', 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?),
          (?, 'acct-5901-b', '{noop}fixture', '팀원 B', 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?)
        """,
        ACCOUNT_A_ID,
        base,
        base,
        ACCOUNT_B_ID,
        base,
        base);
  }

  @Test
  @DisplayName("A가 로그아웃해 토큰 연결을 해제하면 A의 배정 변경 알림은 업무폰 P로 가지 않는다")
  void token_revoked_on_logout_stops_assignment_notifications_to_police_phone() throws Exception {
    // given: A가 업무폰 P에서 토큰을 등록한 뒤 로그아웃한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID).andExpect(status().isOk());
    perform(delete("/api/fcm/tokens"), ACCOUNT_A_ID).andExpect(status().isNoContent());

    // when: A가 사건에 새로 배정된다.
    receiveAssignmentChanged("v2", ACCOUNT_A_ID);

    // then: A에게는 ACTIVE 토큰이 없으므로 업무폰 P로 알림이 가지 않는다.
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P)).isEmpty();
  }

  @Test
  @DisplayName("같은 업무폰과 앱 설치에서 B로 다시 등록하면 A의 배정 변경 알림은 P로 가지 않고 B의 알림은 P로 간다")
  void token_reregistered_by_other_account_moves_assignment_notifications_to_that_account()
      throws Exception {
    // given: A로 쓰던 업무폰 P에서 B로 로그인해 같은 앱 설치의 토큰을 다시 등록한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID).andExpect(status().isOk());
    perform(fcmTokenRegister(), ACCOUNT_B_ID).andExpect(status().isOk());

    // when: A가 사건에 새로 배정된다.
    receiveAssignmentChanged("v2", ACCOUNT_A_ID);

    // then: A의 알림은 업무폰 P로 가지 않는다.
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P)).isEmpty();

    // when: B가 사건에 새로 배정된다.
    receiveAssignmentChanged("v3", ACCOUNT_A_ID, ACCOUNT_B_ID);

    // then: B의 알림은 업무폰 P로 간다.
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P))
        .singleElement()
        .satisfies(
            dispatch -> {
              assertThat(dispatch.recipients()).containsExactly(TOKEN_ON_P);
              assertThat(dispatch.recipientAccountIds()).containsExactly(ACCOUNT_B_ID.toString());
            });
  }

  @Test
  @DisplayName("같은 업무폰에서 B가 연결 해제를 요청해도 A가 등록한 토큰은 해제되지 않는다")
  void revoke_by_other_account_keeps_token_registered_by_account() throws Exception {
    // given: A가 업무폰 P에서 토큰을 등록한다. B는 토큰을 등록하지 않은 채 같은 업무폰에서 로그아웃한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID).andExpect(status().isOk());
    perform(delete("/api/fcm/tokens"), ACCOUNT_B_ID).andExpect(status().isNoContent());

    // when: A가 사건에 새로 배정된다.
    receiveAssignmentChanged("v2", ACCOUNT_A_ID);

    // then: A의 토큰이 남아 있으므로 A의 알림은 업무폰 P로 간다.
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P))
        .singleElement()
        .satisfies(
            dispatch ->
                assertThat(dispatch.recipientAccountIds())
                    .containsExactly(ACCOUNT_A_ID.toString()));
  }

  @Test
  @DisplayName("인증 정보와 다른 업무폰 ID 헤더로 연결 해제를 요청하면 police_phone_required로 거절되고 토큰은 남는다")
  void revoke_with_police_phone_header_different_from_authentication_is_rejected()
      throws Exception {
    // given: A가 업무폰 P에서 토큰을 등록한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID).andExpect(status().isOk());

    // when: 인증 정보는 P인데 헤더에 다른 업무폰 ID를 넣어 연결 해제를 요청한다.
    perform(delete("/api/fcm/tokens"), ACCOUNT_A_ID, OTHER_POLICE_PHONE_ID)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    // then: 토큰이 해제되지 않았으므로 A의 배정 변경 알림은 업무폰 P로 간다.
    receiveAssignmentChanged("v2", ACCOUNT_A_ID);
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P)).hasSize(1);
  }

  @Test
  @DisplayName("같은 계정이 연결 해제를 두 번 보내도 두 번 모두 204이고 토큰 상태는 첫 해제 뒤와 같다")
  void revoke_sent_twice_by_same_account_returns_no_content_with_same_result() throws Exception {
    // given: A가 업무폰 P에서 토큰을 등록하고 연결을 해제한다.
    perform(fcmTokenRegister(), ACCOUNT_A_ID).andExpect(status().isOk());
    perform(delete("/api/fcm/tokens"), ACCOUNT_A_ID).andExpect(status().isNoContent());
    List<Map<String, Object>> rowsAfterFirstRevoke = fcmTokenRows();
    assertThat(rowsAfterFirstRevoke)
        .singleElement()
        .satisfies(row -> assertThat(row.get("status")).isEqualTo("REVOKED"));

    // when: 응답을 받지 못한 A가 같은 연결 해제를 다시 보낸다.
    perform(delete("/api/fcm/tokens"), ACCOUNT_A_ID).andExpect(status().isNoContent());

    // then: 토큰의 상태와 해제 시각은 첫 해제 뒤와 같고, A의 배정 변경 알림은 업무폰 P로 가지 않는다.
    assertThat(fcmTokenRows()).isEqualTo(rowsAfterFirstRevoke);
    receiveAssignmentChanged("v2", ACCOUNT_A_ID);
    assertThat(fcmDispatcher.findByRecipient(TOKEN_ON_P)).isEmpty();
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, UUID accountId)
      throws Exception {
    return perform(request, accountId, POLICE_PHONE_P_ID);
  }

  // 운영 인증 정보에는 X-PolicePhone-Id 헤더 값이 포함된다. 이 테스트에서는 인증 정보에 업무폰 P의 ID를 넣었다.
  // 헤더 값만 바꿔 보낼 수 있도록 헤더의 업무폰 ID는 따로 받는다.
  private ResultActions perform(
      MockHttpServletRequestBuilder request, UUID accountId, UUID policePhoneIdHeader)
      throws Exception {
    request
        .header("X-Client-Channel", "APP")
        .header("Authorization", "Bearer app-token")
        .header("X-PolicePhone-Id", policePhoneIdHeader.toString());
    TestSecurityContextHolder.setAuthentication(
        new SuriMapAuthentication(
            accountId.toString(),
            AccountType.TEAM,
            OrganizationType.MISSING_TEAM,
            Channel.APP,
            POLICE_PHONE_P_ID.toString(),
            List.of(new SimpleGrantedAuthority(Role.MEMBER.name()))));
    return mockMvc.perform(request);
  }

  private static MockHttpServletRequestBuilder fcmTokenRegister() {
    return post("/api/fcm/tokens")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            { "appInstanceId": "%s", "token": "%s" }
            """
                .formatted(APP_INSTANCE_ID, TOKEN_ON_P));
  }

  private void receiveAssignmentChanged(String eventSuffix, UUID... assignedAccountIds)
      throws Exception {
    List<ExternalAssignment> assignments =
        Arrays.stream(assignedAccountIds)
            .map(
                accountId ->
                    new ExternalAssignment(
                        "assignment-5901-" + accountId,
                        accountId.toString(),
                        "MEMBER",
                        OffsetDateTime.ofInstant(BASE_TS, ZoneOffset.UTC)))
            .toList();
    when(externalIncidentAdapter.fetchIncident(SOURCE_INCIDENT_ID))
        .thenReturn(
            new ExternalIncident(
                SOURCE_INCIDENT_ID,
                "Assignment notification recipient incident",
                OffsetDateTime.ofInstant(BASE_TS, ZoneOffset.UTC),
                "OPEN",
                null,
                assignments,
                List.of()));
    mockMvc
        .perform(
            post("/api/internal/mock-112/events")
                .header("X-Client-Channel", "INTERNAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "eventId": "mock112:INCIDENT_ASSIGNMENT_CHANGED:%s:%s",
                      "eventType": "INCIDENT_ASSIGNMENT_CHANGED",
                      "sourceIncidentId": "%s",
                      "occurredAt": "2026-11-26T09:00:00+09:00"
                    }
                    """
                        .formatted(SOURCE_INCIDENT_ID, eventSuffix, SOURCE_INCIDENT_ID)))
        .andExpect(status().isAccepted());
  }

  private List<Map<String, Object>> fcmTokenRows() {
    return jdbcTemplate.queryForList(
        "SELECT id, account_id, status, revoked_at FROM fcm_token ORDER BY id");
  }
}
