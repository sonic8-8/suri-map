package com.surimap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.health.HealthController;
import com.surimap.config.SecurityConfig;
import com.surimap.support.auth.WithMockAccount;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, SecurityFilterBaselineTest.AuthHarnessController.class})
class SecurityFilterBaselineTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsAccessibleWithoutAuth() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  @Test
  void unauthenticatedApiCallIsBlocked() throws Exception {
    mockMvc.perform(get("/api/auth-harness/protected")).andExpect(status().isUnauthorized());
  }

  @Test
  void productionWebOriginIsAllowedForApiCorsPreflight() throws Exception {
    mockMvc
        .perform(
            options("/api/auth/login")
                .header("Origin", "https://k14c106.p.ssafy.io")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-client-channel"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://k14c106.p.ssafy.io"));
  }

  @Test
  void basicPrincipalIsForbiddenFromProtectedApi() throws Exception {
    mockMvc
        .perform(get("/api/auth-harness/protected").with(httpBasic("dev", "dev-password")))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "dev-precinct-phone-01")
  void mockAppAccountCarriesTypedSecurityContext() throws Exception {
    mockMvc
        .perform(get("/api/auth-harness/context"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("11111111-1111-1111-1111-111111110003"))
        .andExpect(jsonPath("$.accountType").value("TEAM"))
        .andExpect(jsonPath("$.organizationType").value("MISSING_TEAM"))
        .andExpect(jsonPath("$.channel").value("APP"))
        .andExpect(jsonPath("$.policePhoneId").value("dev-precinct-phone-01"))
        .andExpect(jsonPath("$.authorities[0]").value("MEMBER"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  void mockWebCommandAccountPassesForbiddenBaseline() throws Exception {
    mockMvc.perform(get("/api/auth-harness/field-command")).andExpect(status().isOk());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MEMBER)
  void mockMemberAccountIsForbiddenFromFieldCommandEndpoint() throws Exception {
    mockMvc.perform(get("/api/auth-harness/field-command")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  void mockAppFieldCommanderIsForbiddenFromFieldCommandEndpoint() throws Exception {
    mockMvc.perform(get("/api/auth-harness/field-command")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.FIELD_COMMANDER)
  void mockTeamFieldCommanderIsForbiddenFromFieldCommandEndpoint() throws Exception {
    mockMvc.perform(get("/api/auth-harness/field-command")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.SUPPORT_UNIT,
      roles = Role.FIELD_COMMANDER)
  void mockSupportUnitFieldCommanderIsForbiddenFromFieldCommandEndpoint() throws Exception {
    mockMvc.perform(get("/api/auth-harness/field-command")).andExpect(status().isForbidden());
  }

  @RestController
  public static class AuthHarnessController {

    // Test-only fail-closed baseline until S1-1 incident_assignment access is available.
    @GetMapping("/api/auth-harness/protected")
    String protectedApi() {
      return "ok";
    }

    @GetMapping("/api/auth-harness/context")
    AuthHarnessResponse context(Authentication authentication) {
      var auth = (SuriMapAuthentication) authentication;
      return new AuthHarnessResponse(
          auth.getAccountId(),
          auth.getAccountType(),
          auth.getOrganizationType(),
          auth.getChannel(),
          auth.getPolicePhoneId(),
          auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @GetMapping("/api/auth-harness/field-command")
    @PreAuthorize(
        "authentication instanceof T(com.surimap.common.auth.SuriMapAuthentication) "
            + "and authentication.channel == T(com.surimap.common.auth.Channel).WEB "
            + "and authentication.accountType == T(com.surimap.common.auth.AccountType).COMMAND "
            + "and authentication.organizationType == "
            + "T(com.surimap.common.auth.OrganizationType).POLICE_SUBSTATION "
            + "and hasAuthority('FIELD_COMMANDER')")
    AuthHarnessResponse fieldCommand(Authentication authentication) {
      return context(authentication);
    }
  }

  record AuthHarnessResponse(
      String accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Channel channel,
      String policePhoneId,
      List<String> authorities) {}
}
