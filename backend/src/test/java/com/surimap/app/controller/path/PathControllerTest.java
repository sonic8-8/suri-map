package com.surimap.app.controller.path;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.app.controller.path.PathController;
import com.surimap.app.controller.path.PathExceptionHandler;
import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.common.auth.Channel;
import com.surimap.config.GuardConfig;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PathController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({PathExceptionHandler.class, GuardConfig.class, GuardPortTestStubs.class})
@WithMockAccount(
    accountId = "30000000-0000-0000-0000-000000000001",
    policePhoneId = "50000000-0000-0000-0000-000000000001")
@DisplayName("L4-T01 search_path lifecycle API contract")
class PathControllerTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID SEARCH_PATH_ID =
      UUID.fromString("81000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AppSearchPathCommandService service;

  @Test
  @DisplayName("POST /api/search-paths returns 201 and lifecycle response shape")
  void start_path_contract() throws Exception {
    when(service.start(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new SearchPath(
                SEARCH_PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                SearchPathStatus.RECORDING,
                1L,
                Instant.parse("2026-04-28T00:00:00Z"),
                null));

    mockMvc
        .perform(
            post("/api/search-paths")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-start-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId": "10000000-0000-0000-0000-000000000001",
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "searchPathId": "81000000-0000-0000-0000-000000000001",
                      "clientTs": "2026-04-28T09:00:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(SEARCH_PATH_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.policePhoneId", is(POLICE_PHONE_ID.toString())))
        .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID.toString())))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.status", is("RECORDING")));

    verify(service)
        .start(
            argThat(
                request ->
                    SEARCH_PATH_ID.equals(request.searchPathId())
                        && ACCOUNT_ID.equals(request.accountId())));
  }

  @Test
  @WithMockAccount(accountId = "30000000-0000-0000-0000-000000000099", channel = Channel.WEB)
  @DisplayName("WEB POST /api/search-paths는 channel_not_allowed로 거부한다")
  void web_start_path_is_rejected_with_channel_not_allowed() throws Exception {
    mockMvc
        .perform(
            post("/api/search-paths")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-web-path-start-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId": "10000000-0000-0000-0000-000000000001",
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "searchPathId": "81000000-0000-0000-0000-000000000001",
                      "clientTs": "2026-04-28T09:00:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("PATCH /api/search-paths/{searchPathId} action END returns 200")
  void end_path_contract() throws Exception {
    when(service.patch(
            org.mockito.ArgumentMatchers.eq(SEARCH_PATH_ID),
            org.mockito.ArgumentMatchers.eq(POLICE_PHONE_ID),
            org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new SearchPath(
                SEARCH_PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                SearchPathStatus.ENDED,
                2L,
                Instant.parse("2026-04-28T00:00:00Z"),
                Instant.parse("2026-04-28T00:10:00Z")));

    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", SEARCH_PATH_ID)
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-end-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "END",
                      "clientTs": "2026-04-28T09:10:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(SEARCH_PATH_ID.toString())))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.status", is("ENDED")));
  }

  @Test
  @DisplayName("PATCH /api/search-paths/{searchPathId} action PAUSE returns 200")
  void pause_path_contract() throws Exception {
    when(service.patch(
            org.mockito.ArgumentMatchers.eq(SEARCH_PATH_ID),
            org.mockito.ArgumentMatchers.eq(POLICE_PHONE_ID),
            org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new SearchPath(
                SEARCH_PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                SearchPathStatus.PAUSED,
                2L,
                Instant.parse("2026-04-28T00:00:00Z"),
                null));

    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", SEARCH_PATH_ID)
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-pause-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "PAUSE",
                      "clientTs": "2026-04-28T09:05:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(SEARCH_PATH_ID.toString())))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.status", is("PAUSED")));
  }

  @Test
  @DisplayName("PATCH /api/search-paths/{searchPathId} action RESUME returns 200")
  void resume_path_contract() throws Exception {
    when(service.patch(
            org.mockito.ArgumentMatchers.eq(SEARCH_PATH_ID),
            org.mockito.ArgumentMatchers.eq(POLICE_PHONE_ID),
            org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new SearchPath(
                SEARCH_PATH_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                ACCOUNT_ID,
                SearchPathStatus.RECORDING,
                3L,
                Instant.parse("2026-04-28T00:00:00Z"),
                null));

    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", SEARCH_PATH_ID)
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-resume-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "RESUME",
                      "clientTs": "2026-04-28T09:06:00+09:00",
                      "clockOffsetMs": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(SEARCH_PATH_ID.toString())))
        .andExpect(jsonPath("$.version", is(3)))
        .andExpect(jsonPath("$.status", is("RECORDING")));
  }

  @Test
  @DisplayName("missing X-PolicePhone-Id returns police_phone_required")
  void missing_police_phone_header() throws Exception {
    mockMvc
        .perform(
            post("/api/search-paths")
                .header("Idempotency-Key", "idem-path-start-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId": "10000000-0000-0000-0000-000000000001",
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "clientTs": "2026-04-28T09:00:00+09:00"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  @Test
  @DisplayName("unsupported action returns write_conflict")
  void unsupported_patch_action() throws Exception {
    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", SEARCH_PATH_ID)
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-end-001")
                .contentType("application/json")
                .content(
                    """
                    {
                      "action": "HOLD",
                      "clientTs": "2026-04-28T09:10:00+09:00"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @DisplayName("PATCH missing X-PolicePhone-Id returns police_phone_required")
  void missing_police_phone_header_on_patch() throws Exception {
    mockMvc
        .perform(
            patch("/api/search-paths/{searchPathId}", SEARCH_PATH_ID)
                .header("Idempotency-Key", "idem-path-end-001")
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

  @Test
  @DisplayName("service op_required is mapped to 409")
  void op_required_is_conflict() throws Exception {
    when(service.start(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new SearchPathGuardException("op_required"));

    mockMvc
        .perform(
            post("/api/search-paths")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-path-start-002")
                .contentType("application/json")
                .content(
                    """
                    {
                      "incidentId": "10000000-0000-0000-0000-000000000001",
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "clientTs": "2026-04-28T09:00:00+09:00"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("op_required")));
  }
}
