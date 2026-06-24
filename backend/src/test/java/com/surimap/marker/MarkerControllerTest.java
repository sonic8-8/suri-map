package com.surimap.marker;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.GuardConfig;
import com.surimap.marker.controller.MarkerController;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerDeleteRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerMutationResponse;
import com.surimap.marker.dto.MarkerMutationResult;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerPublishRequestPayload;
import com.surimap.marker.dto.MarkerUpdateRequest;
import com.surimap.marker.exception.MarkerExceptionHandler;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerReadService;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.support.auth.WithMockAccount;
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
@Import({MarkerExceptionHandler.class, MarkerRequestContextResolver.class, GuardConfig.class})
@DisplayName("L5-T01A marker create API")
@WithMockAccount(
    accountId = "11111111-1111-1111-1111-111111110071",
    policePhoneId = "22222222-2222-2222-2222-222222220071")
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
  @MockitoBean private MarkerUpdateDeleteService markerUpdateDeleteService;
  @MockitoBean private MarkerReadService markerReadService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
    when(authenticationResolver.resolve(AUTHORIZATION, "WEB"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "WEB", null));
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
                "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100"))),
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
                        + "\"coordinates\":[126.913400,35.163100]},"
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
  @DisplayName("APP POST /api/markers는 미등록 업무폰이면 서비스 호출 전에 거부한다")
  void appCreateUnregisteredPolicePhoneRejectedBeforeService() throws Exception {
    doThrow(new PolicePhoneNotRegisteredException())
        .when(policePhoneValidationPort)
        .checkRegistered(POLICE_PHONE_ID);

    mockMvc
        .perform(
            post("/api/markers")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-unregistered")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"incidentId\":\""
                        + INCIDENT_ID
                        + "\",\"opId\":\""
                        + OP_ID
                        + "\",\"type\":\"CLUE\","
                        + "\"location\":{\"type\":\"Point\","
                        + "\"coordinates\":[126.913400,35.163100]},"
                        + "\"clientTs\":\"2026-04-28T00:05:00Z\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));

    verifyNoInteractions(markerCreateService);
  }

  @Test
  @DisplayName("APP PATCH /api/markers/{markerId}는 200 canonical response를 반환한다")
  void appUpdateReturnsCanonicalResponse() throws Exception {
    MarkerUpdateRequest serviceRequest =
        new MarkerUpdateRequest(
            1L,
            new MarkerGeoJsonPoint(
                "Point", List.of(new BigDecimal("126.913700"), new BigDecimal("35.163400"))),
            "S3-2 detail panel memo",
            "NOTE");
    MarkerMutationResult serviceResult =
        new MarkerMutationResult(
            new MarkerMutationResponse(MARKER_ID, "UPDATED", 2L),
            new MarkerPublishRequest(
                "MARKER_UPDATED",
                new MarkerPublishRequestPayload(
                    MARKER_ID,
                    INCIDENT_ID,
                    OP_ID,
                    POLICE_PHONE_ID,
                    "UPDATED",
                    2L,
                    "NOTE",
                    serviceRequest.location(),
                    null,
                    SERVER_TS)));
    when(markerUpdateDeleteService.update(
            eq(MARKER_ID),
            eq(serviceRequest),
            argThat(
                context ->
                    context.authentication().channel().equals("APP")
                        && context.authentication().policePhoneId().equals(POLICE_PHONE_ID)
                        && context.idempotencyKey().equals("idem-marker-update-001"))))
        .thenReturn(serviceResult);

    mockMvc
        .perform(
            patch("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-update-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":1,"
                        + "\"location\":{\"type\":\"Point\","
                        + "\"coordinates\":[126.913700,35.163400]},"
                        + "\"memo\":\"S3-2 detail panel memo\","
                        + "\"type\":\"NOTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.status", is("UPDATED")))
        .andExpect(jsonPath("$.version", is(2)));

    verify(markerUpdateDeleteService)
        .update(
            eq(MARKER_ID),
            eq(serviceRequest),
            argThat(context -> context.authentication().channel().equals("APP")));
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("WEB DELETE /api/markers/{markerId}는 PolicePhone 헤더 없이 200 canonical response를 반환한다")
  void webDeleteReturnsCanonicalResponse() throws Exception {
    MarkerDeleteRequest serviceRequest = new MarkerDeleteRequest(2L, "board cleanup");
    MarkerMutationResult serviceResult =
        new MarkerMutationResult(
            new MarkerMutationResponse(MARKER_ID, "DELETED", 3L),
            new MarkerPublishRequest(
                "MARKER_DELETED",
                new MarkerPublishRequestPayload(
                    MARKER_ID,
                    INCIDENT_ID,
                    OP_ID,
                    POLICE_PHONE_ID,
                    "DELETED",
                    3L,
                    null,
                    null,
                    null,
                    SERVER_TS)));
    when(markerUpdateDeleteService.delete(
            eq(MARKER_ID),
            eq(serviceRequest),
            argThat(
                context ->
                    context.authentication().channel().equals("WEB")
                        && context.authentication().policePhoneId() == null
                        && context.idempotencyKey().equals("idem-marker-delete-001"))))
        .thenReturn(serviceResult);

    mockMvc
        .perform(
            delete("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-marker-delete-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"reason\":\"board cleanup\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.status", is("DELETED")))
        .andExpect(jsonPath("$.version", is(3)));

    verify(markerUpdateDeleteService)
        .delete(
            eq(MARKER_ID),
            eq(serviceRequest),
            argThat(context -> context.authentication().channel().equals("WEB")));
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("WEB POST /api/markers는 channel_not_allowed로 거부한다")
  void webCreateRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/markers")
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
                        + "\"coordinates\":[126.913400,35.163100]},"
                        + "\"clientTs\":\"2026-04-28T00:05:00Z\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(authenticationResolver, markerCreateService);
  }

  @Test
  @DisplayName("APP PATCH /api/markers/{markerId}에서 PolicePhone 헤더가 없으면 police_phone_required다")
  void appUpdateMissingPolicePhoneRejected() throws Exception {
    mockMvc
        .perform(
            patch("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-marker-update-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"memo\":\"missing police phone\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    verifyNoInteractions(markerUpdateDeleteService);
  }
}
