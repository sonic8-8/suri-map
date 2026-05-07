package com.surimap.marker;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.marker.controller.MarkerController;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.exception.MarkerExceptionHandler;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.service.MarkerCreateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

/** S14P31C106-71 L5-T01A marker create API RED/GREEN tests. */
@WebMvcTest(MarkerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MarkerExceptionHandler.class, MarkerRequestContextResolver.class})
@DisplayName("L5-T01A marker create API")
class MarkerControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:05:03Z");
  private static final String AUTHORIZATION = "Bearer marker-create-app";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MarkerCreateService markerCreateService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
  }

  @Test
  @DisplayName("APP POST /api/markers는 201 canonical response를 반환한다")
  void appCreateReturnsCanonicalResponse() throws Exception {
    MarkerCreateRequest serviceRequest =
        new MarkerCreateRequest(
            INCIDENT_ID,
            OP_ID,
            "CLUE",
            new MarkerGeoJsonPoint(
                "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
            null,
            "S14P31C106-71 field clue",
            CLIENT_TS,
            0L);
    MarkerCreateResult serviceResult =
        new MarkerCreateResult(
            new MarkerCreateResponse(MARKER_ID, INCIDENT_ID, OP_ID, POLICE_PHONE_ID, "ACTIVE", 1L),
            new MarkerPublishRequest(
                "MARKER_CREATED",
                new MarkerPublishRequestPayload(
                    MARKER_ID,
                    INCIDENT_ID,
                    OP_ID,
                    POLICE_PHONE_ID,
                    "ACTIVE",
                    1L,
                    "CLUE",
                    serviceRequest.location(),
                    CLIENT_TS,
                    SERVER_TS)));
    when(markerCreateService.create(
            eq(serviceRequest),
            argThat(
                context ->
                    context.authentication().accountId().equals(ACCOUNT_ID)
                        && context.authentication().policePhoneId().equals(POLICE_PHONE_ID)
                        && context.idempotencyKey().equals("idem-marker-create-001"))))
        .thenReturn(serviceResult);

    mockMvc
        .perform(
            post("/api/markers")
                .contextPath("/api")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"incidentId\":\""
                        + INCIDENT_ID
                        + "\",\"opId\":\""
                        + OP_ID
                        + "\",\"type\":\"CLUE\","
                        + "\"location\":{\"type\":\"Point\","
                        + "\"coordinates\":[126.956500,37.571200]},"
                        + "\"memo\":\"S14P31C106-71 field clue\","
                        + "\"clientTs\":\"2026-04-28T00:05:00Z\","
                        + "\"clockOffsetMs\":0}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.policePhoneId", is(POLICE_PHONE_ID.toString())))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.version", is(1)));

    verify(markerCreateService)
        .create(
            eq(serviceRequest),
            argThat(context -> context.authentication().channel().equals("APP")));
  }

  @Test
  @DisplayName("WEB POST /api/markers는 channel_not_allowed로 거부한다")
  void webCreateRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/markers")
                .contextPath("/api")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"incidentId\":\""
                        + INCIDENT_ID
                        + "\",\"opId\":\""
                        + OP_ID
                        + "\",\"type\":\"CLUE\","
                        + "\"location\":{\"type\":\"Point\","
                        + "\"coordinates\":[126.956500,37.571200]},"
                        + "\"clientTs\":\"2026-04-28T00:05:00Z\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(authenticationResolver, markerCreateService);
  }
}
