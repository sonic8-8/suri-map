package com.surimap.path.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.surimap.support.auth.WithMockAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("L4-T01 path lifecycle integration")
@WithMockAccount(
    accountId = "11111111-1111-1111-1111-111111110002",
    policePhoneId = "50000000-0000-0000-0000-000000000001")
class PathLifecycleIntegrationTest {

  private static final String INCIDENT_ID = "10000000-0000-0000-0000-000000000001";
  private static final String POLICE_PHONE_ID = "50000000-0000-0000-0000-000000000001";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("start then end succeeds through controller and real service path")
  void start_then_end_success() throws Exception {
    AtomicReference<String> searchPathId = new AtomicReference<>();

    String startResponse =
        mockMvc
            .perform(
                post("/api/search-paths")
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                    .header("Idempotency-Key", "idem-path-start-int-001")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "incidentId": "%s",
                          "opId": "%s",
                          "clientTs": "2026-04-28T09:00:00+09:00",
                          "clockOffsetMs": 0
                        }
                        """
                            .formatted(INCIDENT_ID, currentOpIdFor(INCIDENT_ID))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("RECORDING")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = extractId(startResponse);
    searchPathId.set(id);

    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", searchPathId.get())
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-path-end-int-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "END",
                      "clientTs": "2026-04-28T09:05:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(searchPathId.get())))
        .andExpect(jsonPath("$.status", is("ENDED")));
  }

  @Test
  @DisplayName("op mismatch returns 409 op_mismatch")
  void op_mismatch_conflict() throws Exception {
    mockMvc
        .perform(
            post("/api/search-paths")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-path-start-int-002")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId": "%s",
                      "opId": "70000000-0000-0000-0000-000000000099",
                      "clientTs": "2026-04-28T09:00:00+09:00"
                    }
                    """
                        .formatted(INCIDENT_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("op_mismatch")));
  }

  @Test
  @DisplayName("patch without police phone header returns police_phone_required")
  void patch_missing_police_phone_required() throws Exception {
    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", "81000000-0000-0000-0000-000000000001")
                .header("Idempotency-Key", "idem-path-end-int-003")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "END",
                      "clientTs": "2026-04-28T09:10:00+09:00"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"");
    if (start < 0) {
      throw new IllegalStateException("id not found in response: " + json);
    }
    int from = start + 6;
    int to = json.indexOf('"', from);
    return json.substring(from, to);
  }

  private static String currentOpIdFor(String incidentId) {
    UUID incident = UUID.fromString(incidentId);
    return UUID.nameUUIDFromBytes(("current-op:" + incident).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
