package com.surimap.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.health.HealthController;
import com.surimap.config.SecurityConfig;
import com.surimap.support.auth.WithMockAccount;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, S1_2HarnessAuthMockRedTest.AuthMockHarnessController.class})
@DisplayName("L2-T01 S1-2 common auth mock RED")
class S1_2HarnessAuthMockRedTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @WithMockAccount(
      accountId = "acct-precinct-team",
      policePhoneId = "dev-precinct-phone-01",
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.MEMBER)
  @DisplayName("common auth mock accepts canonical harness account and policePhone IDs")
  void common_auth_mock_accepts_canonical_harness_account_and_police_phone_ids() throws Exception {
    mockMvc
        .perform(get("/api/s1-2-red/auth-context"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(AccountIdentityCatalog.PRECINCT_TEAM_ID.toString()))
        .andExpect(jsonPath("$.policePhoneId").value("dev-precinct-phone-01"))
        .andExpect(jsonPath("$.accountType").value("TEAM"))
        .andExpect(jsonPath("$.organizationType").value("POLICE_SUBSTATION"))
        .andExpect(jsonPath("$.channel").value("APP"))
        .andExpect(jsonPath("$.authorities[0]").value("MEMBER"));
  }

  @RestController
  public static class AuthMockHarnessController {

    @GetMapping("/api/s1-2-red/auth-context")
    AuthMockHarnessResponse context(Authentication authentication) {
      var auth = (SuriMapAuthentication) authentication;
      return new AuthMockHarnessResponse(
          auth.getAccountId().toString(),
          auth.getPolicePhoneId().toString(),
          auth.getAccountType().name(),
          auth.getOrganizationType().name(),
          auth.getChannel().name(),
          auth.getAuthorities().stream().map(Object::toString).toList());
    }
  }

  record AuthMockHarnessResponse(
      String accountId,
      String policePhoneId,
      String accountType,
      String organizationType,
      String channel,
      List<String> authorities) {}
}
