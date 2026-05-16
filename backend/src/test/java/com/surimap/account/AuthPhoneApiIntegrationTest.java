package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.surimap.eventhub.port.EventHub;
import com.surimap.policephone.PolicePhoneFixtures;
import com.surimap.policephone.PolicePhoneHeartbeatUpdatedPublishRequest;
import com.surimap.policephone.query.FcmTokenQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("P1-E auth phone API integration")
class AuthPhoneApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FcmTokenQuery fcmTokenQuery;
  @MockitoBean private EventHub eventHub;

  @Test
  @DisplayName("APP login creates bearer session bound to PolicePhone security context")
  void appLoginCreatesBearerSessionBoundToPolicePhoneSecurityContext() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Client-Channel", "APP")
                .contentType("application/json")
                .content(
                    """
                    {
                      "accountCode": "acct-precinct-team",
                      "password": "fixture",
                      "channel": "APP",
                      "policePhoneCode": "dev-precinct-phone-01"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").isString())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(
            jsonPath("$.securityContext.accountId")
                .value(AccountIdentityCatalog.PRECINCT_TEAM_ID.toString()))
        .andExpect(jsonPath("$.securityContext.accountType").value("TEAM"))
        .andExpect(jsonPath("$.securityContext.organizationType").value("POLICE_SUBSTATION"))
        .andExpect(jsonPath("$.securityContext.channel").value("APP"))
        .andExpect(
            jsonPath("$.securityContext.policePhoneId")
                .value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
        .andExpect(jsonPath("$.securityContext.authorities[0]").value("MEMBER"));
  }

  @Test
  @DisplayName("APP login rejects PolicePhone owned by another account")
  void appLoginRejectsPolicePhoneOwnedByAnotherAccount() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Client-Channel", "APP")
                .contentType("application/json")
                .content(
                    """
                    {
                      "accountCode": "acct-precinct-team",
                      "password": "fixture",
                      "channel": "APP",
                      "policePhoneCode": "dev-support-phone-01"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @DisplayName("bearer APP session registers FCM token without exposing token material")
  void bearerAppSessionRegistersFcmTokenWithoutExposingTokenMaterial() throws Exception {
    String accessToken = loginAppAccessToken();

    mockMvc
        .perform(
            post("/api/fcm/tokens")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "appInstanceId": "app-instance-p1e",
                      "token": "fcm-token-p1e"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.tokenCiphertext").doesNotExist())
        .andExpect(jsonPath("$.tokenHash").doesNotExist());

    var activeTokens = fcmTokenQuery.activeByPolicePhone(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
    assertThat(activeTokens)
        .anySatisfy(
            row -> {
              assertThat(row.appInstanceId()).isEqualTo("app-instance-p1e");
              assertThat(row.tokenCiphertext()).isNotEqualTo("fcm-token-p1e");
              assertThat(row.tokenHash()).isNotBlank();
            });
  }

  @Test
  @DisplayName("registered but unassigned APP session can register FCM token before assignment")
  void unassignedRegisteredAppSessionRegistersFcmTokenBeforeAssignment() throws Exception {
    String accessToken =
        loginAppAccessToken("acct-unassigned-phone", "dev-unassigned-phone-01");

    mockMvc
        .perform(
            post("/api/fcm/tokens")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "appInstanceId": "app-instance-unassigned",
                      "token": "fcm-token-unassigned"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.version").value(1));

    assertThat(fcmTokenQuery.activeByPolicePhone(PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.appInstanceId()).isEqualTo("app-instance-unassigned");
              assertThat(row.tokenCiphertext()).isNotEqualTo("fcm-token-unassigned");
              assertThat(row.tokenHash()).isNotBlank();
            });
  }

  @Test
  @DisplayName("bearer APP session sends heartbeat through app-police-phone guard")
  void bearerAppSessionSendsHeartbeatThroughAppPolicePhoneGuard() throws Exception {
    String accessToken = loginAppAccessToken();

    mockMvc
        .perform(
            post(
                    "/api/police-phones/{policePhoneId}/heartbeat",
                    PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
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
        .andExpect(jsonPath("$.sequence").value(1));

    verify(eventHub)
        .publish(
            argThat(
                request ->
                    PolicePhoneHeartbeatUpdatedPublishRequest.TYPE.equals(request.type())
                        && PolicePhoneFixtures.INCIDENT_ID.equals(request.incidentId())));
  }

  @Test
  @DisplayName("WEB session cannot call APP FCM token API")
  void webSessionCannotCallAppFcmTokenApi() throws Exception {
    String accessToken = loginWebAccessToken();

    mockMvc
        .perform(
            post("/api/fcm/tokens")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "WEB")
                .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "appInstanceId": "app-instance-web",
                      "token": "fcm-token-web"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @DisplayName("logout revokes bearer session")
  void logoutRevokesBearerSession() throws Exception {
    String accessToken = loginAppAccessToken();

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOGGED_OUT"));

    mockMvc
        .perform(
            post("/api/fcm/tokens")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "appInstanceId": "app-instance-after-logout",
                      "token": "fcm-token-after-logout"
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  private String loginAppAccessToken() throws Exception {
    return loginAppAccessToken("acct-precinct-team", "dev-precinct-phone-01");
  }

  private String loginAppAccessToken(String accountCode, String policePhoneCode) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .header("X-Client-Channel", "APP")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "accountCode": "%s",
                          "password": "fixture",
                          "channel": "APP",
                          "policePhoneCode": "%s"
                        }
                        """
                            .formatted(accountCode, policePhoneCode)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  private String loginWebAccessToken() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .header("X-Client-Channel", "WEB")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "accountCode": "acct-cmd-alpha",
                          "password": "fixture",
                          "channel": "WEB"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }
}
