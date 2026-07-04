package com.surimap.handover;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.api.controller.dutyshift.DutyShiftQueryController;
import com.surimap.api.controller.handover.HandoverMemoController;
import com.surimap.api.controller.summary.SearchHistorySummaryController;
import com.surimap.api.service.dutyshift.DutyShiftQueryService;
import com.surimap.api.service.handover.HandoverMemoApiService;
import com.surimap.api.service.summary.SearchHistorySummaryApiService;
import com.surimap.app.controller.dutyshift.AppDutyShiftController;
import com.surimap.app.service.dutyshift.AppDutyShiftCommandService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.config.GuardConfig;
import com.surimap.dutyshift.DutyShift;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.eventhub.port.EventHub;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.lifecycle.IncidentLifecycleSnapshot;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.summary.SearchHistorySummaryGenerationJob;
import com.surimap.summary.SearchHistorySummaryMapper;
import com.surimap.summary.SearchHistorySummaryRow;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  AppDutyShiftController.class,
  DutyShiftQueryController.class,
  HandoverMemoController.class,
  SearchHistorySummaryController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
  GuardConfig.class,
  GuardPortTestStubs.class,
  AppDutyShiftCommandService.class,
  DutyShiftQueryService.class,
  HandoverMemoApiService.class,
  SearchHistorySummaryApiService.class
})
@DisplayName("P2-C S8 duty shift, handover memo, summary public API contract")
class S8HandoverApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID DUTY_SHIFT_ID =
      UUID.fromString("77777777-7777-7777-7777-777777770001");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_REGISTERED_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");
  private static final UUID OTHER_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110002");
  private static final UUID INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("66666666-6666-6666-6666-666666660001");
  private static final UUID OTHER_INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("66666666-6666-6666-6666-666666660002");
  private static final UUID MEMO_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final UUID SUMMARY_READY_ID =
      UUID.fromString("44444444-4444-4444-4444-444444440001");
  private static final UUID SUMMARY_GENERATING_ID =
      UUID.fromString("44444444-4444-4444-4444-444444440002");
  private static final Instant NOW = Instant.parse("2026-05-11T01:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OperationalPeriodMapper operationalPeriodMapper;
  @MockitoBean private DutyShiftMapper dutyShiftMapper;
  @MockitoBean private HandoverMemoMapper handoverMemoMapper;
  @MockitoBean private SearchHistorySummaryMapper searchHistorySummaryMapper;
  @MockitoBean private SearchHistorySummaryGenerationJob searchHistorySummaryGenerationJob;
  @MockitoBean private IncidentLifecycleGuard incidentLifecycleGuard;
  @MockitoBean private EventHub eventHub;

  @BeforeEach
  void setUp() {
    when(operationalPeriodMapper.findActiveByIncident(INCIDENT_ID)).thenReturn(Optional.of(op()));
    when(incidentLifecycleGuard.requireOpen(INCIDENT_ID))
        .thenReturn(new IncidentLifecycleSnapshot(INCIDENT_ID, "OPEN", 1L));
    when(dutyShiftMapper.findActiveAssignmentId(INCIDENT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(INCIDENT_ASSIGNMENT_ID));
    when(dutyShiftMapper.findActiveAssignmentId(INCIDENT_ID, OTHER_ACCOUNT_ID))
        .thenReturn(Optional.of(OTHER_INCIDENT_ASSIGNMENT_ID));
    when(dutyShiftMapper.findById(DUTY_SHIFT_ID)).thenReturn(Optional.of(activeDutyShift()));
    when(dutyShiftMapper.findByFilters(INCIDENT_ID, OP_ID, POLICE_PHONE_ID, null, "ACTIVE"))
        .thenReturn(List.of(activeDutyShift()));
    when(handoverMemoMapper.findByContext(INCIDENT_ID, OP_ID, "OPERATIONAL_PERIOD", OP_ID))
        .thenReturn(List.of(memoRow()));
    when(searchHistorySummaryMapper.findByOp(OP_ID, INCIDENT_ID, null, null, null, null))
        .thenReturn(List.of(readySummary(), generatingSummary()));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP starts a duty shift through canonical URL")
  void appStartsDutyShift() throws Exception {
    mockMvc
        .perform(
            post("/api/duty-shifts")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-start-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "policePhoneId": "00000000-0000-0000-0000-000000000101",
                      "clientTs": "2026-05-11T10:00:00+09:00"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.policePhoneId", is(POLICE_PHONE_ID.toString())))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.version", is(1)));

    verify(dutyShiftMapper).insert(any(DutyShift.class));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("WEB cannot start a duty shift")
  void webCannotStartDutyShift() throws Exception {
    mockMvc
        .perform(
            post("/api/duty-shifts")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-duty-start-web")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dutyShiftStartBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("duty shift write without Idempotency-Key returns write_conflict")
  void dutyShiftWriteRequiresIdempotencyKey() throws Exception {
    mockMvc
        .perform(
            post("/api/duty-shifts")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(dutyShiftStartBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("duty shift write with stale opId returns op_mismatch")
  void dutyShiftWriteRequiresCurrentOp() throws Exception {
    when(operationalPeriodMapper.findActiveByIncident(INCIDENT_ID))
        .thenReturn(Optional.of(opWithId(UUID.fromString("88888888-8888-8888-8888-888888880099"))));

    mockMvc
        .perform(
            post("/api/duty-shifts")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-op-mismatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dutyShiftStartBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("op_mismatch")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP ends a duty shift through canonical URL")
  void appEndsDutyShift() throws Exception {
    mockMvc
        .perform(
            patch("/api/duty-shifts/{dutyShiftId}", DUTY_SHIFT_ID)
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-end-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "action": "END",
                      "clientTs": "2026-05-11T10:30:00+09:00",
                      "memo": "다음 근무자는 동쪽 능선을 우선 확인"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(DUTY_SHIFT_ID.toString())))
        .andExpect(jsonPath("$.status", is("ENDED")))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.endedAt", notNullValue()));

    verify(dutyShiftMapper).end(eq(DUTY_SHIFT_ID), eq(ACCOUNT_ID), any(Instant.class), eq(2L));
    verify(searchHistorySummaryGenerationJob)
        .enqueueForDutyShiftEnd(
            org.mockito.ArgumentMatchers.<DutyShift>argThat(
                dutyShift ->
                    DUTY_SHIFT_ID.equals(dutyShift.getId())
                        && INCIDENT_ID.equals(dutyShift.getIncidentId())
                        && OP_ID.equals(dutyShift.getOpId())),
            eq(ACCOUNT_ID));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      policePhoneId = "50000000-0000-0000-0000-000000000001")
  @DisplayName("APP can end own duty shift from another registered police phone")
  void appEndsOwnDutyShiftFromAnotherRegisteredPolicePhone() throws Exception {
    mockMvc
        .perform(
            patch("/api/duty-shifts/{dutyShiftId}", DUTY_SHIFT_ID)
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OTHER_REGISTERED_POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-end-other-phone-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "action": "END",
                      "clientTs": "2026-05-11T10:35:00+09:00",
                      "memo": "다른 등록 업무폰에서 근무교대 종료"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(DUTY_SHIFT_ID.toString())))
        .andExpect(jsonPath("$.status", is("ENDED")))
        .andExpect(jsonPath("$.version", is(2)));

    verify(dutyShiftMapper).end(eq(DUTY_SHIFT_ID), eq(ACCOUNT_ID), any(Instant.class), eq(2L));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110002",
      policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP cannot end another account's duty shift even from matching police phone")
  void appCannotEndAnotherAccountsDutyShiftFromMatchingPolicePhone() throws Exception {
    mockMvc
        .perform(
            patch("/api/duty-shifts/{dutyShiftId}", DUTY_SHIFT_ID)
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-end-other-account-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "action": "END",
                      "clientTs": "2026-05-11T10:36:00+09:00",
                      "memo": "다른 계정에서 근무교대 종료 시도"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));

    verify(dutyShiftMapper, never()).end(any(UUID.class), any(UUID.class), any(Instant.class), anyLong());
    verify(searchHistorySummaryGenerationJob, never())
        .enqueueForDutyShiftEnd(any(DutyShift.class), any(UUID.class));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("APP and WEB can read duty shifts")
  void readsDutyShifts() throws Exception {
    mockMvc
        .perform(
            get("/api/duty-shifts")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .queryParam("incidentId", INCIDENT_ID.toString())
                .queryParam("opId", OP_ID.toString())
                .queryParam("policePhoneId", POLICE_PHONE_ID.toString())
                .queryParam("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id", is(DUTY_SHIFT_ID.toString())))
        .andExpect(jsonPath("$.items[0].incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.items[0].opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.items[0].policePhoneId", is(POLICE_PHONE_ID.toString())))
        .andExpect(jsonPath("$.items[0].policePhoneLabel", is("무등산 현장팀 폴리폰")))
        .andExpect(jsonPath("$.items[0].status", is("ACTIVE")))
        .andExpect(jsonPath("$.items[0].startedAt", is(NOW.toString())))
        .andExpect(jsonPath("$.items[0].endedAt").doesNotExist());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("WEB can create a handover memo")
  void webCreatesHandoverMemo() throws Exception {
    mockMvc
        .perform(
            post("/api/handover-memos")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-memo-web-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "memoTargetType": "OPERATIONAL_PERIOD",
                      "memoTargetId": "88888888-8888-8888-8888-888888880001",
                      "content": "OP 전환 인수인계",
                      "clientTs": "2026-05-11T10:40:00+09:00"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.memoTargetType", is("OPERATIONAL_PERIOD")))
        .andExpect(jsonPath("$.memoTargetId", is(OP_ID.toString())));

    verify(handoverMemoMapper).insert(any(HandoverMemo.class));
    verify(eventHub).publish(any());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("APP handover memo write requires police phone")
  void appHandoverMemoWriteRequiresPolicePhone() throws Exception {
    mockMvc
        .perform(
            post("/api/handover-memos")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-memo-app-no-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId": "88888888-8888-8888-8888-888888880001",
                      "memoTargetType": "OPERATIONAL_PERIOD",
                      "content": "APP 메모",
                      "clientTs": "2026-05-11T10:40:00+09:00"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("APP and WEB can read handover memos")
  void readsHandoverMemos() throws Exception {
    mockMvc
        .perform(
            get("/api/handover-memos")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .queryParam("incidentId", INCIDENT_ID.toString())
                .queryParam("opId", OP_ID.toString())
                .queryParam("memoTargetType", "OPERATIONAL_PERIOD")
                .queryParam("memoTargetId", OP_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id", is(MEMO_ID.toString())))
        .andExpect(jsonPath("$.items[0].incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.items[0].opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.items[0].memoTargetType", is("OPERATIONAL_PERIOD")))
        .andExpect(jsonPath("$.items[0].content", is("OP 전환 인수인계")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001")
  @DisplayName("summary read API exposes safe read-only states")
  void readsSearchHistorySummaries() throws Exception {
    mockMvc
        .perform(
            get("/api/operational-periods/{operationalPeriodId}/search-history-summaries", OP_ID)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .queryParam("incidentId", INCIDENT_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].summaryId", is(SUMMARY_READY_ID.toString())))
        .andExpect(jsonPath("$.items[0].status", is("READY")))
        .andExpect(jsonPath("$.items[0].displayStatus", is("READY")))
        .andExpect(jsonPath("$.items[0].content", is("수색 이력 요약 본문")))
        .andExpect(jsonPath("$.items[0].sourceReadiness", is("READY")))
        .andExpect(jsonPath("$.items[0].sourceHash", is("a".repeat(64))))
        .andExpect(jsonPath("$.items[1].summaryId", is(SUMMARY_GENERATING_ID.toString())))
        .andExpect(jsonPath("$.items[1].status", is("GENERATING")))
        .andExpect(jsonPath("$.items[1].displayStatus", is("LOADING")))
        .andExpect(jsonPath("$.items[1].content").doesNotExist())
        .andExpect(jsonPath("$.items[1].sourceReadiness", is("PENDING_SYNC")));
  }

  private static String dutyShiftStartBody() {
    return """
        {
          "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
          "opId": "88888888-8888-8888-8888-888888880001",
          "policePhoneId": "00000000-0000-0000-0000-000000000101",
          "clientTs": "2026-05-11T10:00:00+09:00"
        }
        """;
  }

  private static OperationalPeriod op() {
    return opWithId(OP_ID);
  }

  private static OperationalPeriod opWithId(UUID opId) {
    return new OperationalPeriod(
        opId,
        INCIDENT_ID,
        1,
        "ACTIVE",
        "INITIAL",
        null,
        ACCOUNT_ID,
        null,
        NOW,
        null,
        1L,
        NOW,
        NOW);
  }

  private static DutyShift activeDutyShift() {
    return new DutyShift(
        DUTY_SHIFT_ID,
        INCIDENT_ID,
        OP_ID,
        INCIDENT_ASSIGNMENT_ID,
        POLICE_PHONE_ID,
        "PHONE-001",
        "무등산 현장팀 폴리폰",
        "ACTIVE",
        ACCOUNT_ID,
        null,
        NOW,
        null,
        1L,
        NOW,
        NOW);
  }

  private static HandoverMemoRow memoRow() {
    return new HandoverMemoRow(
        MEMO_ID,
        INCIDENT_ID,
        OP_ID,
        DUTY_SHIFT_ID,
        "OPERATIONAL_PERIOD",
        OP_ID,
        "OP 전환 인수인계",
        ACCOUNT_ID,
        NOW,
        1L);
  }

  private static SearchHistorySummaryRow readySummary() {
    return new SearchHistorySummaryRow(
        SUMMARY_READY_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "READY",
        "수색 이력 요약 본문",
        "a".repeat(64),
        "READY",
        NOW,
        1L);
  }

  private static SearchHistorySummaryRow generatingSummary() {
    return new SearchHistorySummaryRow(
        SUMMARY_GENERATING_ID,
        INCIDENT_ID,
        OP_ID,
        DUTY_SHIFT_ID,
        "GENERATING",
        null,
        "b".repeat(64),
        "PENDING_SYNC",
        null,
        1L);
  }
}
