package com.surimap.operationalperiod;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.api.controller.operationalperiod.OperationalPeriodController;
import com.surimap.api.service.operationalperiod.OperationalPeriodApiService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.config.GuardConfig;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.lifecycle.IncidentLifecycleSnapshot;
import com.surimap.operationalperiod.event.EventPublisherPort;
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

@WebMvcTest(OperationalPeriodController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GuardConfig.class, GuardPortTestStubs.class, OperationalPeriodApiService.class})
@DisplayName("P2-B OperationalPeriod public API contract")
class OperationalPeriodApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OperationalPeriodMapper mapper;
  @MockitoBean private EventPublisherPort eventPublisher;
  @MockitoBean private IncidentLifecycleGuard incidentLifecycleGuard;

  @BeforeEach
  void setUp() {
    when(mapper.findActiveByIncident(INCIDENT_ID)).thenReturn(Optional.of(op1()));
    when(mapper.findAllByIncidentOrderBySequence(INCIDENT_ID)).thenReturn(List.of(op1()));
    when(incidentLifecycleGuard.requireOpen(INCIDENT_ID))
        .thenReturn(new IncidentLifecycleSnapshot(INCIDENT_ID, "OPEN", 1L));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("WEB can create the next operational period through canonical URL")
  void createsNextOperationalPeriod() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-op-transition-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "reason": "RE_SEARCH",
                      "clientTs": "2026-05-11T10:00:00+09:00"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.reason", is("RE_SEARCH")))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.sequenceNumber", is(2)));

    verify(mapper).endActive(eq(OP1_ID), any(UUID.class), any(Instant.class), eq(2L));
    verify(mapper).insert(any(OperationalPeriod.class));
    verify(eventPublisher).publish(any());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("GET returns currentOpId and ordered items")
  void readsOperationalPeriodList() throws Exception {
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/operational-periods", INCIDENT_ID)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentOpId", is(OP1_ID.toString())))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id", is(OP1_ID.toString())))
        .andExpect(jsonPath("$.items[0].status", is("ACTIVE")))
        .andExpect(jsonPath("$.items[0].reason", is("INITIAL")))
        .andExpect(jsonPath("$.items[0].sequenceNumber", is(1)));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("APP channel cannot create operational periods")
  void appChannelCannotCreateOperationalPeriod() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-op-app-rejected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("RE_SEARCH")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("write without Idempotency-Key returns write_conflict")
  void writeRequiresIdempotencyKey() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("AREA_CHANGED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("same Idempotency-Key with different body returns idempotency_mismatch")
  void sameIdempotencyKeyWithDifferentBodyReturnsMismatch() throws Exception {
    mockMvc
        .perform(
            post("/api/operational-periods")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-op-transition-mismatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("RE_SEARCH")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/operational-periods")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-op-transition-mismatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("AREA_CHANGED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("idempotency_mismatch")));

    verify(eventPublisher, times(1)).publish(any());
  }

  private static OperationalPeriod op1() {
    Instant startedAt = Instant.parse("2026-05-11T00:00:00Z");
    return new OperationalPeriod(
        OP1_ID,
        INCIDENT_ID,
        1,
        "ACTIVE",
        "INITIAL",
        null,
        UUID.fromString("11111111-1111-1111-1111-111111110001"),
        null,
        startedAt,
        null,
        1L,
        startedAt,
        startedAt);
  }

  private static String createBody(String reason) {
    return """
        {
          "incidentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
          "reason": "%s",
          "clientTs": "2026-05-11T10:00:00+09:00"
        }
        """
        .formatted(reason);
  }
}
