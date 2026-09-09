package com.surimap.api.controller.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.marker.response.MarkerListResponse;
import com.surimap.api.service.marker.MarkerService;
import com.surimap.api.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.api.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.api.service.marker.response.MarkerListServiceResponse;
import com.surimap.api.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.app.controller.marker.AppMarkerController;
import com.surimap.app.service.marker.AppMarkerService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.ClockConfig;
import com.surimap.config.GuardConfig;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.exception.MarkerExceptionHandler;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.retention.purge.LocationAccessRecorder;
import com.surimap.retention.purge.RecordLocationAccessAspect;
import com.surimap.support.auth.WithMockAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({MarkerController.class, AppMarkerController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({
  MarkerExceptionHandler.class,
  MarkerRequestContextResolver.class,
  GuardConfig.class,
  AopAutoConfiguration.class,
  RecordLocationAccessAspect.class,
  ClockConfig.class
})
@WithMockAccount(
    accountId = "11111111-1111-1111-1111-111111110071",
    policePhoneId = "22222222-2222-2222-2222-222222220071")
class MarkerControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final String AUTHORIZATION = "Bearer marker-create-app";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa5702");
  private static final UUID OP_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb5702");
  private static final UUID READ_MARKER_ID =
      UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc5702");
  private static final UUID READ_ACCOUNT_ID =
      UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd5702");
  private static final UUID READ_POLICE_PHONE_ID =
      UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeee5702");
  private static final UUID PHOTO_ID = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffff5701");
  private static final String PHOTO_URL = "https://photo.example/marker-photo.jpg";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private MarkerService markerService;
  @MockitoBean private AppMarkerService appMarkerService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;
  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private LocationAccessRecorder locationAccessRecorder;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
    when(authenticationResolver.resolve(AUTHORIZATION, "WEB"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "WEB", null));
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("웹 마커 수정 중 좌표 오류가 발생하면, HTTP 400과 오류 코드만 반환한다")
  void updateMarker_invalidGeometry_returnsBadRequestWithoutInternalDetails() throws Exception {
    // given: 웹 서비스에서 좌표 오류와 내부 진단 메시지를 전달한다.
    when(markerService.update(any(MarkerUpdateServiceRequest.class)))
        .thenThrow(
            new BusinessException(ErrorCode.INVALID_GEOMETRY, "latitude out of range: 91.0"));

    // when & then: 공통 예외 처리로 기존 오류 응답을 유지하며 진단 메시지는 노출하지 않는다.
    mockMvc
        .perform(
            patch("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-marker-update-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"location":{"type":"Point","coordinates":[126.9,91]}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_geometry"))
        .andExpect(jsonPath("$.*").value(hasSize(1)));
  }

  @Test
  @DisplayName("채널 헤더가 없거나 지원하지 않는 값이면, 수정·삭제를 기존 오류로 거부한다")
  void changeMarker_missingOrUnsupportedChannel_rejectsBeforeCallingService() throws Exception {
    // given: 같은 URL로 들어온 요청이지만 채널 헤더가 없거나 잘못되어 있다.
    for (String channel : List.of("", "INTERNAL", "app", "UNKNOWN")) {
      for (MockHttpServletRequestBuilder request :
          List.of(
              patch("/api/markers/{markerId}", MARKER_ID),
              delete("/api/markers/{markerId}", MARKER_ID))) {
        if (!channel.isEmpty()) {
          request.header("X-Client-Channel", channel);
        }
        // when & then: 라우팅 실패인 404·405로 바뀌지 않고 기존 403 오류를 유지한다.
        mockMvc
            .perform(
                request
                    .header("Authorization", AUTHORIZATION)
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                    .header("Idempotency-Key", "idem-invalid-channel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":1}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("channel_not_allowed"));
      }
    }
    verifyNoInteractions(markerService, appMarkerService);
  }

  @Test
  @DisplayName("앱 인증으로 웹 채널을 가장하면, 수정·삭제 서비스를 호출하지 않고 거부한다")
  void changeMarker_appPrincipalWithWebHeader_rejectsBeforeCallingService() throws Exception {
    // given: 헤더만 WEB으로 바꿔도 인증된 계정의 채널은 APP이다.
    when(authenticationResolver.resolve(AUTHORIZATION, "WEB"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));

    // when & then: 웹 Controller로 라우팅되더라도 인증 채널 불일치를 거부한다.
    for (MockHttpServletRequestBuilder request :
        List.of(
            patch("/api/markers/{markerId}", MARKER_ID),
            delete("/api/markers/{markerId}", MARKER_ID))) {
      mockMvc
          .perform(
              request
                  .header("Authorization", AUTHORIZATION)
                  .header("X-Client-Channel", "WEB")
                  .header("Idempotency-Key", "idem-mismatched-channel")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error").value("channel_not_allowed"));
    }
    verifyNoInteractions(markerService, appMarkerService);
  }

  @Test
  @WithMockAccount(
      channel = Channel.APP,
      accountId = "dddddddd-dddd-4ddd-8ddd-dddddddd5702",
      policePhoneId = "eeeeeeee-eeee-4eee-8eee-eeeeeeee5702",
      roles = Role.MEMBER)
  @DisplayName("앱에서 마커를 조회하면, 마커·사진 정보를 반환하고 위치 조회 기록을 남긴다")
  void listMarkers_appChannel_returnsMarkersAndRecordsLocationAccess() throws Exception {
    // given: 서비스가 반환할 마커와 첨부 사진 정보를 준비한다.
    MarkerView marker =
        new MarkerView(
            READ_MARKER_ID,
            INCIDENT_ID,
            OP_ID,
            null,
            READ_ACCOUNT_ID,
            READ_POLICE_PHONE_ID,
            MarkerType.CLUE,
            null,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            7L,
            new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100")))
                .toPoint(),
            "등산로 입구 제보",
            Instant.parse("2026-05-14T00:00:01Z"),
            List.of(
                new MarkerPhotoSummary(
                    PHOTO_ID,
                    "ATTACHED",
                    3L,
                    "image/jpeg",
                    1024L,
                    Instant.parse("2026-05-14T00:00:02Z"),
                    PHOTO_URL,
                    PHOTO_URL)));
    when(markerService.list(INCIDENT_ID, OP_ID, "CLUE", null))
        .thenReturn(
            MarkerListServiceResponse.builder()
                .incidentId(INCIDENT_ID)
                .markers(List.of(marker))
                .build());

    // when & then: 조회 결과를 기존 JSON 응답 형태로 반환한다.
    MvcResult result =
        mockMvc
            .perform(
                get("/api/markers")
                    .param("incidentId", INCIDENT_ID.toString())
                    .param("opId", OP_ID.toString())
                    .param("type", "CLUE")
                    .header("Authorization", "Bearer app-marker-read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
            .andExpect(jsonPath("$.markers[0].id").value(READ_MARKER_ID.toString()))
            .andExpect(jsonPath("$.markers[0].incidentId").value(INCIDENT_ID.toString()))
            .andExpect(jsonPath("$.markers[0].opId").value(OP_ID.toString()))
            .andExpect(jsonPath("$.markers[0].accountId").value(READ_ACCOUNT_ID.toString()))
            .andExpect(
                jsonPath("$.markers[0].policePhoneId").value(READ_POLICE_PHONE_ID.toString()))
            .andExpect(jsonPath("$.markers[0].type").value("CLUE"))
            .andExpect(jsonPath("$.markers[0].source").value("APP"))
            .andExpect(jsonPath("$.markers[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.markers[0].version").value(7))
            .andExpect(jsonPath("$.markers[0].location.type").value("Point"))
            .andExpect(jsonPath("$.markers[0].location.coordinates[0]").value(126.9134))
            .andExpect(jsonPath("$.markers[0].location.coordinates[1]").value(35.1631))
            .andExpect(jsonPath("$.markers[0].memo").value("등산로 입구 제보"))
            .andExpect(jsonPath("$.markers[0].occurredAt").value("2026-05-14T00:00:01Z"))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].photoId").value(PHOTO_ID.toString()))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].status").value("ATTACHED"))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].version").value(3))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].sizeBytes").value(1024))
            .andExpect(
                jsonPath("$.markers[0].photoSummary[0].attachedAt").value("2026-05-14T00:00:02Z"))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].photoUrl").value(PHOTO_URL))
            .andExpect(jsonPath("$.markers[0].photoSummary[0].thumbnailUrl").value(PHOTO_URL))
            .andReturn();

    // then: 좌표는 소수점 6자리로 정규화된 표현을 유지한다.
    MarkerListResponse response =
        objectMapper.readValue(result.getResponse().getContentAsString(), MarkerListResponse.class);
    assertThat(response.getMarkers().get(0).getLocation().coordinates())
        .extracting(BigDecimal::toPlainString)
        .containsExactly("126.913400", "35.163100");

    // then: 사건 접근 권한을 확인하고 요청자의 위치 조회 기록을 남긴다.
    verify(incidentAccessPort).checkAccess(any());
    verify(markerService).list(INCIDENT_ID, OP_ID, "CLUE", null);
    verify(locationAccessRecorder)
        .record(
            eq(READ_ACCOUNT_ID),
            eq(INCIDENT_ID),
            eq(READ_POLICE_PHONE_ID),
            eq("APP"),
            eq("MARKER_READ"),
            any(Instant.class));
  }

  @Test
  @WithMockAccount(channel = Channel.WEB, accountId = "dddddddd-dddd-4ddd-8ddd-dddddddd5702")
  @DisplayName("웹에서 업무폰 정보 없이 마커를 조회하면, 조회 결과와 웹의 위치 조회 기록을 남긴다")
  void listMarkers_webChannelWithoutPolicePhone_returnsMarkersAndRecordsLocationAccess()
      throws Exception {
    // given: 해당 사건에는 조회할 마커가 없다.
    when(markerService.list(INCIDENT_ID, null, null, null))
        .thenReturn(
            MarkerListServiceResponse.builder().incidentId(INCIDENT_ID).markers(List.of()).build());

    // when & then: 업무폰 헤더 없이 웹에서 빈 목록을 조회한다.
    mockMvc
        .perform(get("/api/markers").param("incidentId", INCIDENT_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
        .andExpect(jsonPath("$.markers").isEmpty());

    // then: 사건 접근 권한을 확인하고 업무폰 정보 없이 웹 조회를 기록한다.
    verify(incidentAccessPort).checkAccess(any());
    verify(locationAccessRecorder)
        .record(
            eq(READ_ACCOUNT_ID),
            eq(INCIDENT_ID),
            isNull(),
            eq("WEB"),
            eq("MARKER_READ"),
            any(Instant.class));
  }

  @Test
  @DisplayName("사건 접근 권한이 없으면, 마커를 조회하거나 위치 조회 기록을 남기지 않고 거부한다")
  void listMarkers_incidentAccessDenied_rejectsBeforeCallingService() throws Exception {
    // given: 요청자에게 해당 사건의 접근 권한이 없다.
    doThrow(new IncidentAccessDeniedException()).when(incidentAccessPort).checkAccess(any());

    // when & then: 서비스 조회 전에 기존 권한 오류로 거부한다.
    mockMvc
        .perform(get("/api/markers").param("incidentId", INCIDENT_ID.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("incident_access_denied"));

    verifyNoInteractions(markerService, locationAccessRecorder);
  }

  @Test
  @DisplayName("서비스가 잘못된 조회 조건을 거부하면, HTTP 400과 invalid_marker_filter 오류를 반환한다")
  void listMarkers_invalidFilter_returnsBadRequestWithoutLocationAccessRecord() throws Exception {
    // given: 서비스가 지원하지 않는 마커 유형을 거부한다.
    when(markerService.list(INCIDENT_ID, null, "bad-type", null))
        .thenThrow(new MarkerApiException("invalid_marker_filter", HttpStatus.BAD_REQUEST));

    // when & then: 오류 응답을 반환하고 성공한 위치 조회로 기록하지 않는다.
    mockMvc
        .perform(
            get("/api/markers")
                .param("incidentId", INCIDENT_ID.toString())
                .param("type", "bad-type"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_marker_filter"));

    verifyNoInteractions(locationAccessRecorder);
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("웹에서 업무폰 헤더 없이 마커를 삭제하면, 삭제 결과와 HTTP 200 응답을 반환한다")
  void deleteMarker_webChannelWithoutPolicePhone_returnsDeletedMarker() throws Exception {
    // given: 웹의 삭제 요청과 서비스 응답을 준비한다.
    MarkerDeleteServiceRequest serviceRequest =
        MarkerDeleteServiceRequest.builder()
            .markerId(MARKER_ID)
            .version(2L)
            .reason("board cleanup")
            .context(
                new MarkerRequestContext(
                    new SuriMapAuthentication(ACCOUNT_ID, "WEB", null), "idem-marker-delete-001"))
            .build();
    MarkerMutationServiceResponse serviceResponse =
        MarkerMutationServiceResponse.builder().id(MARKER_ID).status("DELETED").version(3L).build();
    when(markerService.delete(any(MarkerDeleteServiceRequest.class))).thenReturn(serviceResponse);

    // when & then: 업무폰 헤더 없이 삭제를 요청하고 응답을 확인한다.
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

    ArgumentCaptor<MarkerDeleteServiceRequest> requestCaptor =
        ArgumentCaptor.forClass(MarkerDeleteServiceRequest.class);
    verify(markerService).delete(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).usingRecursiveComparison().isEqualTo(serviceRequest);
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("웹에서 마커를 수정하면, 수정 결과와 HTTP 200 응답을 반환한다")
  void updateMarker_webChannelWithoutPolicePhone_returnsUpdatedMarker() throws Exception {
    // given: 웹의 수정 요청과 서비스 응답을 준비한다.
    MarkerUpdateServiceRequest serviceRequest =
        MarkerUpdateServiceRequest.builder()
            .markerId(MARKER_ID)
            .version(1L)
            .location(
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.913700"), new BigDecimal("35.163400"))))
            .memo("S3-2 detail panel memo")
            .type("NOTE")
            .context(
                new MarkerRequestContext(
                    new SuriMapAuthentication(ACCOUNT_ID, "WEB", null), "idem-marker-update-001"))
            .build();
    MarkerMutationServiceResponse serviceResponse =
        MarkerMutationServiceResponse.builder().id(MARKER_ID).status("UPDATED").version(2L).build();
    when(markerService.update(any(MarkerUpdateServiceRequest.class))).thenReturn(serviceResponse);

    // when & then: 수정 요청을 보내고 응답 본문과 상태 코드를 확인한다.
    mockMvc
        .perform(
            patch("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
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
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(handler().handlerType(MarkerController.class));

    ArgumentCaptor<MarkerUpdateServiceRequest> requestCaptor =
        ArgumentCaptor.forClass(MarkerUpdateServiceRequest.class);
    verify(markerService).update(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).usingRecursiveComparison().isEqualTo(serviceRequest);
  }
}
