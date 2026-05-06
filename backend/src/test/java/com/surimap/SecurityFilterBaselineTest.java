package com.surimap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.health.HealthController;
import com.surimap.config.SecurityConfig;
import com.surimap.support.auth.WithMockAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class SecurityFilterBaselineTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsAccessibleWithoutAuth() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
  }

  @Test
  void unauthenticatedApiCallIsBlocked() throws Exception {
    mockMvc.perform(get("/incidents")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockAccount(channel = Channel.APP, accountType = AccountType.TEAM)
  void mockAppAccountIsAuthenticated() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
  }

  @Test
  @WithMockAccount(channel = Channel.WEB, accountType = AccountType.COMMAND)
  void mockWebCommandAccountIsAuthenticated() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
  }
}
