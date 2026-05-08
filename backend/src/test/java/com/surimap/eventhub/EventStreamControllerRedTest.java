package com.surimap.eventhub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.GuardExceptionHandler;
import com.surimap.config.GuardConfig;
import com.surimap.config.SecurityConfig;
import com.surimap.eventhub.stream.EventStreamConfig;
import com.surimap.eventhub.stream.EventStreamController;
import com.surimap.eventhub.stream.EventStreamExceptionHandler;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventStreamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  SecurityConfig.class,
  GuardConfig.class,
  GuardExceptionHandler.class,
  GuardPortTestStubs.class,
  EventStreamConfig.class,
  EventStreamExceptionHandler.class
})
@DisplayName("L2-T07A event stream controller")
class EventStreamControllerRedTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000901");
  private static final UUID DISPATCH_JOB_ID =
      UUID.fromString("70000000-0000-4000-8000-000000000901");

  @Autowired private MockMvc mockMvc;
  @Autowired private SseReplayEventStore replayStore;

  @BeforeEach
  void resetReplayStore() {
    replayStore.clear();
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("WEB session opens incident SSE stream as text/event-stream")
  void webSessionConnectsToIncidentEventsAsTextEventStream() throws Exception {
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  @Test
  @WithMockAccount(channel = Channel.APP, policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("APP session is rejected because Android is not an SSE consumer")
  void appSessionIsRejectedAsSseConsumer() throws Exception {
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("missing replay sequence after Last-Event-ID returns gone_refetch_required")
  void replayGapReturnsGoneRefetchRequired() throws Exception {
    replayStore.save(
        SseReplayEvent.active(
            UUID.fromString("80000000-0000-4000-8000-000000000046"),
            DISPATCH_JOB_ID,
            INCIDENT_ID,
            46L,
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));

    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .header("Last-Event-ID", "44")
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("gone_refetch_required"));
  }
}
