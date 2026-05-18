package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.eventhub.port.EventHub;
import com.surimap.policephone.PolicePhoneFixtures;
import com.surimap.policephone.PolicePhoneHeartbeatUpdatedPublishRequest;
import com.surimap.policephone.query.FcmTokenQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("P1-E auth phone API integration")
class AuthPhoneApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FcmTokenQuery fcmTokenQuery;
  @MockitoBean private EventHub eventHub;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  @DisplayName("bearer APP OIDC session registers FCM token without exposing token material")
  void bearerAppOidcSessionRegistersFcmTokenWithoutExposingTokenMaterial() throws Exception {
    String accessToken = appAccessToken("fcm");

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
  @DisplayName("registered but unassigned APP OIDC session can register FCM token before assignment")
  void unassignedRegisteredAppOidcSessionRegistersFcmTokenBeforeAssignment() throws Exception {
    String accessToken =
        appAccessToken(
            "unassigned",
            AccountIdentityCatalog.UNASSIGNED_PHONE_ID,
            PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID);

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
  @DisplayName("bearer APP OIDC session sends heartbeat through app-police-phone guard")
  void bearerAppOidcSessionSendsHeartbeatThroughAppPolicePhoneGuard() throws Exception {
    String accessToken = appAccessToken("heartbeat");

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
  @DisplayName("APP OIDC heartbeat accepts a trusted police phone independently from operator account")
  void appHeartbeatAcceptsTrustedPolicePhoneIndependentlyFromOperatorAccount() throws Exception {
    String accessToken =
        appAccessToken(
            "commander-phone",
            AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
            AccountType.COMMAND,
            OrganizationType.POLICE_SUBSTATION,
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);

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
                      "clientTs": "2026-05-08T09:01:00+09:00",
                      "sequence": 2,
                      "lastSyncAt": "2026-05-08T09:00:30+09:00"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ONLINE"))
        .andExpect(
            jsonPath("$.policePhoneId").value(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID.toString()))
        .andExpect(jsonPath("$.sequence").value(2));
  }

  @Test
  @DisplayName("WEB OIDC session cannot call APP FCM token API")
  void webOidcSessionCannotCallAppFcmTokenApi() throws Exception {
    String accessToken = webAccessToken();

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

  private String appAccessToken(String suffix) {
    return appAccessToken(
        suffix, AccountIdentityCatalog.PRECINCT_TEAM_ID, PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
  }

  private String appAccessToken(String suffix, UUID accountId, UUID policePhoneId) {
    return appAccessToken(
        suffix, accountId, AccountType.TEAM, OrganizationType.POLICE_SUBSTATION, policePhoneId);
  }

  private String appAccessToken(
      String suffix,
      UUID accountId,
      AccountType accountType,
      OrganizationType organizationType,
      UUID policePhoneId) {
    String accessToken = "header.app-" + suffix + ".signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", accountId.toString())
                .claim("accountType", accountType.name())
                .claim("organizationType", organizationType.name())
                .claim("policePhoneId", policePhoneId.toString())
                .claim("realm_access", Map.of("roles", List.of("MEMBER")))
                .build());
    return accessToken;
  }

  private String webAccessToken() {
    String accessToken = "header.web-fcm.signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", AccountIdentityCatalog.ALPHA_COMMANDER_ID.toString())
                .claim("accountType", "COMMAND")
                .claim("organizationType", "MISSING_TEAM")
                .claim("realm_access", Map.of("roles", List.of("FIELD_COMMANDER")))
                .build());
    return accessToken;
  }
}
