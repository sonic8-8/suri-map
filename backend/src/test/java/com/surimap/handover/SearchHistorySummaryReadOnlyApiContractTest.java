package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.api.controller.summary.SearchHistorySummaryController;
import com.surimap.api.service.summary.SearchHistorySummaryApiService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.config.GuardConfig;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(SearchHistorySummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GuardConfig.class, GuardPortTestStubs.class, SearchHistorySummaryApiService.class})
@DisplayName("S8 search history summary read-only API contract")
class SearchHistorySummaryReadOnlyApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID DUTY_SHIFT_ID =
      UUID.fromString("77777777-7777-7777-7777-777777770001");
  private static final UUID PENDING_SUMMARY_ID =
      UUID.fromString("44444444-4444-4444-4444-444444440010");
  private static final UUID READY_SUMMARY_ID =
      UUID.fromString("44444444-4444-4444-4444-444444440011");
  private static final UUID STALE_SUMMARY_ID =
      UUID.fromString("44444444-4444-4444-4444-444444440012");
  private static final Instant GENERATED_AT = Instant.parse("2026-05-11T01:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchHistorySummaryMapper searchHistorySummaryMapper;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("GET exposes PENDING_SYNC, READY, and STALE as read-only source readiness states")
  void readApiExposesSourceReadinessStatesWithoutClientCommands() throws Exception {
    when(searchHistorySummaryMapper.findByOp(OP_ID, INCIDENT_ID, null, null, null, null))
        .thenReturn(List.of(pendingSyncSummary(), readySummary(), staleSummary()));

    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/operational-periods/{operationalPeriodId}/search-history-summaries",
                        OP_ID)
                    .header("Authorization", "Bearer commander")
                    .header("X-Client-Channel", "WEB")
                    .queryParam("incidentId", INCIDENT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(3)))
            .andExpect(jsonPath("$.items[0].summaryId", is(PENDING_SUMMARY_ID.toString())))
            .andExpect(jsonPath("$.items[0].status", is("GENERATING")))
            .andExpect(jsonPath("$.items[0].displayStatus", is("LOADING")))
            .andExpect(jsonPath("$.items[0].content").doesNotExist())
            .andExpect(jsonPath("$.items[0].sourceReadiness", is("PENDING_SYNC")))
            .andExpect(jsonPath("$.items[0].sourceHash", is("p".repeat(64))))
            .andExpect(jsonPath("$.items[0].scopeType", is("DUTY_SHIFT")))
            .andExpect(jsonPath("$.items[0].scopeId", is(DUTY_SHIFT_ID.toString())))
            .andExpect(jsonPath("$.items[0].dutyShiftId", is(DUTY_SHIFT_ID.toString())))
            .andExpect(jsonPath("$.items[1].summaryId", is(READY_SUMMARY_ID.toString())))
            .andExpect(jsonPath("$.items[1].status", is("READY")))
            .andExpect(jsonPath("$.items[1].displayStatus", is("READY")))
            .andExpect(jsonPath("$.items[1].content", is("수색 이력 요약 본문")))
            .andExpect(jsonPath("$.items[1].sourceReadiness", is("READY")))
            .andExpect(jsonPath("$.items[1].sourceHash", is("r".repeat(64))))
            .andExpect(jsonPath("$.items[1].scopeType", is("OP")))
            .andExpect(jsonPath("$.items[1].scopeId", is(OP_ID.toString())))
            .andExpect(jsonPath("$.items[1].dutyShiftId").doesNotExist())
            .andExpect(jsonPath("$.items[2].summaryId", is(STALE_SUMMARY_ID.toString())))
            .andExpect(jsonPath("$.items[2].status", is("READY")))
            .andExpect(jsonPath("$.items[2].displayStatus", is("LOADING")))
            .andExpect(jsonPath("$.items[2].content").doesNotExist())
            .andExpect(jsonPath("$.items[2].sourceReadiness", is("STALE")))
            .andExpect(jsonPath("$.items[2].sourceHash", is("s".repeat(64))))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(
            "generateUrl",
            "generationUrl",
            "retryUrl",
            "retryEndpoint",
            "canGenerate",
            "canRetry",
            "actions",
            "sourcePrompt",
            "providerSecret",
            "recommendation",
            "missingAreaConclusion",
            "riskLevel");
    verify(searchHistorySummaryMapper).findByOp(OP_ID, INCIDENT_ID, null, null, null, null);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("APP/WEB cannot call public summary generation or retry commands")
  void publicSummaryGenerationAndRetryRoutesDoNotExist() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods/{operationalPeriodId}/search-history-summaries", OP_ID)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .queryParam("incidentId", INCIDENT_ID.toString()))
        .andExpect(status().isMethodNotAllowed());

    mockMvc
        .perform(
            post(
                    "/api/operational-periods/{operationalPeriodId}/search-history-summaries/retry",
                    OP_ID)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .queryParam("incidentId", INCIDENT_ID.toString()))
        .andExpect(status().isNotFound());

    verifyNoInteractions(searchHistorySummaryMapper);
  }

  private static SearchHistorySummaryRow pendingSyncSummary() {
    return new SearchHistorySummaryRow(
        PENDING_SUMMARY_ID,
        INCIDENT_ID,
        OP_ID,
        DUTY_SHIFT_ID,
        "GENERATING",
        null,
        "p".repeat(64),
        "PENDING_SYNC",
        null,
        3L);
  }

  private static SearchHistorySummaryRow readySummary() {
    return new SearchHistorySummaryRow(
        READY_SUMMARY_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "READY",
        "수색 이력 요약 본문",
        "r".repeat(64),
        "READY",
        GENERATED_AT,
        4L);
  }

  private static SearchHistorySummaryRow staleSummary() {
    return new SearchHistorySummaryRow(
        STALE_SUMMARY_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "READY",
        "늦은 source row 반영 전 요약",
        "s".repeat(64),
        "STALE",
        GENERATED_AT,
        5L);
  }
}
