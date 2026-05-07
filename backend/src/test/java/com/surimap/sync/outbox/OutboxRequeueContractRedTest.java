package com.surimap.sync.outbox;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("L4-T07B Outbox requeue diagnostics contract RED test")
class OutboxRequeueContractRedTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void appRequeueRequestReturnsAcceptedDiagnosticContract() throws Exception {
    mockMvc
        .perform(
            post(OutboxRetryDiagnosticsFixtures.API_PATH)
                .header("Authorization", "Bearer app-token-sync")
                .header("X-Client-Channel", "APP")
                .header(
                    OutboxRetryDiagnosticsFixtures.POLICE_PHONE_HEADER,
                    OutboxRetryDiagnosticsFixtures.HARNESS_POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.json()))
        .andExpect(status().isAccepted())
        .andExpect(
            jsonPath(
                    "$.operationId",
                    is(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.operationId())))
        .andExpect(jsonPath("$.accepted", is(true)))
        .andExpect(jsonPath("$.serverTs").exists());
  }
}
