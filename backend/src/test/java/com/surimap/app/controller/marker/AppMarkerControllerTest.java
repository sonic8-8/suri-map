package com.surimap.app.controller.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.marker.MarkerController;
import com.surimap.api.service.marker.MarkerService;
import com.surimap.app.controller.marker.request.MarkerCreatePhotoRequest;
import com.surimap.app.controller.marker.request.MarkerCreateRequest;
import com.surimap.app.service.marker.AppMarkerService;
import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.GuardConfig;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.auth.SuriMapAuthenticationResolver;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.error.GlobalExceptionHandler;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.service.MarkerRequestContext;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({AppMarkerController.class, MarkerController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, MarkerRequestContextResolver.class, GuardConfig.class})
@WithMockAccount(
    accountId = "11111111-1111-1111-1111-111111110071",
    policePhoneId = "22222222-2222-2222-2222-222222220071")
class AppMarkerControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final String AUTHORIZATION = "Bearer marker-create-app";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AppMarkerService appMarkerService;
  @MockitoBean private MarkerService markerService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
  }

  @Test
  @DisplayName("앱 마커 생성 중 좌표 오류가 발생하면, HTTP 400과 오류 코드만 반환한다")
  void createMarker_invalidGeometry_returnsBadRequestWithoutInternalDetails() throws Exception {
    // given: 앱 서비스에서 좌표 오류와 내부 진단 메시지를 전달한다.
    when(appMarkerService.create(any(MarkerCreateServiceRequest.class)))
        .thenThrow(
            new BusinessException(ErrorCode.INVALID_GEOMETRY, "latitude out of range: 91.0"));

    // when & then: 공통 예외 처리로 기존 오류 응답을 유지하며 진단 메시지는 노출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId":"88888888-8888-8888-8888-888888880001",
                      "type":"CLUE",
                      "location":{"type":"Point","coordinates":[126.9,91]},
                      "clientTs":"2026-04-28T00:05:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_geometry"))
        .andExpect(jsonPath("$.*").value(hasSize(1)));
  }

  @Test
  @DisplayName("마커 생성 중 사진 형식 오류가 발생하면, HTTP 400과 invalid_photo_content_type을 반환한다")
  void createMarker_invalidPhotoContentType_returnsBadRequest() throws Exception {
    // given: 서비스가 사진 형식 오류로 마커 생성을 거부한다.
    when(appMarkerService.create(any(MarkerCreateServiceRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.INVALID_PHOTO_CONTENT_TYPE));

    // when: 사진의 contentType이 null인 마커 생성 요청을 보낸다.
    mockMvc
        .perform(
            post("/api/markers")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "id":"55555555-5555-5555-5555-555555550071",
                      "incidentId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
                      "opId":"88888888-8888-8888-8888-888888880001",
                      "type":"CLUE",
                      "location":{"type":"Point","coordinates":[126.9134,35.1631]},
                      "clientTs":"2026-04-28T00:05:00Z",
                      "photos":[{
                        "photoId":"55555555-5555-5555-5555-555555550172",
                        "sizeBytes":1048576,
                        "contentType":null
                      }]
                    }
                    """))
        // then: 기존 공통 예외 처리로 사진 형식 오류를 HTTP 응답에 전달한다.
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_photo_content_type"))
        .andExpect(jsonPath("$.*").value(hasSize(1)));
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("웹 인증으로 앱 채널을 가장하면, 수정·삭제 서비스를 호출하지 않고 거부한다")
  void changeMarker_webPrincipalWithAppHeader_rejectsBeforeCallingService() throws Exception {
    // given: 헤더만 APP으로 바꿔도 인증된 계정의 채널은 WEB이다.
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "WEB", null));

    // when & then: 앱 Controller로 라우팅되더라도 인증 채널 불일치를 거부한다.
    for (MockHttpServletRequestBuilder request :
        List.of(
            patch("/api/markers/{markerId}", MARKER_ID),
            delete("/api/markers/{markerId}", MARKER_ID))) {
      mockMvc
          .perform(
              request
                  .header("Authorization", AUTHORIZATION)
                  .header("X-Client-Channel", "APP")
                  .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                  .header("Idempotency-Key", "idem-mismatched-channel")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error").value("channel_not_allowed"));
    }
    verifyNoInteractions(markerService, appMarkerService);
  }

  @Test
  @DisplayName("앱에서 마커를 삭제하면, 앱 서비스에 요청을 전달하고 삭제 결과를 반환한다")
  void deleteMarker_appChannel_returnsDeletedMarker() throws Exception {
    // given: 앱 서비스가 삭제 결과를 반환한다.
    when(appMarkerService.delete(any(MarkerDeleteServiceRequest.class)))
        .thenReturn(
            MarkerMutationServiceResponse.builder()
                .id(MARKER_ID)
                .status("DELETED")
                .version(2L)
                .build());

    // when & then: 기존 URL과 응답을 유지하면서 앱 서비스에 전달한다.
    mockMvc
        .perform(
            delete("/api/markers/{markerId}", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-app-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"reason\":\"wrong marker\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(MARKER_ID.toString()))
        .andExpect(jsonPath("$.status").value("DELETED"))
        .andExpect(jsonPath("$.version").value(2));
    ArgumentCaptor<MarkerDeleteServiceRequest> captured =
        ArgumentCaptor.forClass(MarkerDeleteServiceRequest.class);
    verify(appMarkerService).delete(captured.capture());
    assertThat(captured.getValue().getMarkerId()).isEqualTo(MARKER_ID);
    assertThat(captured.getValue().getVersion()).isEqualTo(1L);
    assertThat(captured.getValue().getReason()).isEqualTo("wrong marker");
    assertThat(captured.getValue().getContext().authentication().channel()).isEqualTo("APP");
    assertThat(captured.getValue().getContext().authentication().policePhoneId())
        .isEqualTo(POLICE_PHONE_ID);
    assertThat(captured.getValue().getContext().idempotencyKey()).isEqualTo("idem-app-delete");
    verifyNoInteractions(markerService);
  }

  @Test
  @DisplayName("앱에서 마커 생성을 요청하면, 생성된 마커와 HTTP 201 응답을 반환한다")
  void createMarker_appChannel_returnsCreatedMarker() throws Exception {
    // given: 앱 계정의 마커 생성 요청에 대해 서비스가 생성된 마커 정보를 반환한다.
    MarkerCreateServiceResponse serviceResponse =
        MarkerCreateServiceResponse.builder()
            .id(MARKER_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .policePhoneId(POLICE_PHONE_ID)
            .status("ACTIVE")
            .version(1L)
            .build();
    when(appMarkerService.create(any(MarkerCreateServiceRequest.class)))
        .thenReturn(serviceResponse);

    // when: 마커 생성 API를 호출한다.
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
        // then: 생성된 마커의 식별자·상태·버전을 HTTP 201 응답으로 반환한다.
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.policePhoneId", is(POLICE_PHONE_ID.toString())))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.photos").isEmpty());

    ArgumentCaptor<MarkerCreateServiceRequest> serviceRequest =
        ArgumentCaptor.forClass(MarkerCreateServiceRequest.class);
    verify(appMarkerService).create(serviceRequest.capture());
    MarkerCreateServiceRequest captured = serviceRequest.getValue();
    assertThat(captured.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(captured.getOpId()).isEqualTo(OP_ID);
    assertThat(captured.getType()).isEqualTo("CLUE");
    assertThat(captured.getMemo()).isEqualTo("S14P31C106-71 field clue");
    assertThat(captured.getClientTs()).isEqualTo(CLIENT_TS);
    assertThat(captured.getClockOffsetMs()).isZero();
    assertThat(captured.getLocation().type()).isEqualTo("Point");
    assertThat(captured.getLocation().coordinates())
        .containsExactly(new BigDecimal("126.913400"), new BigDecimal("35.163100"));
    assertThat(captured.getPhotos()).isEmpty();
    assertThat(captured.getContext().authentication().accountId()).isEqualTo(ACCOUNT_ID);
    assertThat(captured.getContext().authentication().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(captured.getContext().authentication().channel()).isEqualTo("APP");
    assertThat(captured.getContext().idempotencyKey()).isEqualTo("idem-marker-create-001");
  }

  @Test
  @DisplayName("사진을 포함해 마커를 생성하면, 사진 정보를 서비스에 전달하고 첨부 결과를 응답한다")
  void createMarker_withPhoto_preservesRequestAndResponseFields() throws Exception {
    // given: 업로드한 사진 정보가 담긴 HTTP 요청과 서비스의 첨부 결과가 있다.
    UUID photoId = UUID.fromString("55555555-5555-5555-5555-555555550172");
    MarkerCreatePhotoRequest photo =
        MarkerCreatePhotoRequest.builder()
            .photoId(photoId)
            .sizeBytes(1024)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256("sha256:fixture")
            .build();
    MarkerCreateRequest request =
        MarkerCreateRequest.builder()
            .id(MARKER_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .type("CLUE")
            .location(
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100"))))
            .clientTs(CLIENT_TS)
            .photos(List.of(photo))
            .build();
    MarkerCreateServiceResponse serviceResponse =
        MarkerCreateServiceResponse.builder()
            .id(MARKER_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .policePhoneId(POLICE_PHONE_ID)
            .status("UPDATED")
            .version(2L)
            .photos(
                List.of(
                    PhotoAttachServiceResponse.builder()
                        .photoId(photoId)
                        .status("ATTACHED")
                        .version(2L)
                        .markerId(MARKER_ID)
                        .markerVersion(2L)
                        .build()))
            .build();
    when(appMarkerService.create(any(MarkerCreateServiceRequest.class)))
        .thenReturn(serviceResponse);

    // when: 사진을 포함한 마커 생성 API를 호출한다.
    mockMvc
        .perform(
            post("/api/markers")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        // then: 첨부된 사진과 마커의 상태·버전을 기존 JSON 필드로 응답한다.
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("UPDATED")))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.photos[0].photoId", is(photoId.toString())))
        .andExpect(jsonPath("$.photos[0].status", is("ATTACHED")))
        .andExpect(jsonPath("$.photos[0].version", is(2)))
        .andExpect(jsonPath("$.photos[0].markerId", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.photos[0].markerVersion", is(2)));
    ArgumentCaptor<MarkerCreateServiceRequest> captured =
        ArgumentCaptor.forClass(MarkerCreateServiceRequest.class);
    verify(appMarkerService).create(captured.capture());
    assertThat(captured.getValue().getId()).isEqualTo(MARKER_ID);
    assertThat(captured.getValue().getPhotos())
        .usingRecursiveComparison()
        .isEqualTo(List.of(photo));
  }

  @Test
  @DisplayName("필수 마커 정보가 빠지면, 서비스를 호출하지 않고 기존 write_conflict 오류를 반환한다")
  void createMarker_missingRequiredFields_rejectsBeforeCallingService() throws Exception {
    // given: 인증된 앱 요청이지만 마커 생성에 필요한 필드가 없다.
    // when: 빈 요청 본문으로 마커 생성 API를 호출한다.
    mockMvc
        .perform(
            post("/api/markers")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        // then: 입력 오류를 Controller에서 거부하며 기존 오류 상태와 본문을 유지한다.
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
    verifyNoInteractions(appMarkerService);
  }

  @Test
  @DisplayName("미등록 업무폰에서 마커 생성을 요청하면, 서비스를 호출하지 않고 거부한다")
  void createMarker_unregisteredPolicePhone_rejectsBeforeCallingService() throws Exception {
    // given: 요청한 업무폰이 등록되지 않았다.
    doThrow(new PolicePhoneNotRegisteredException())
        .when(policePhoneValidationPort)
        .checkRegistered(POLICE_PHONE_ID);

    // when: 마커 생성 API를 호출한다.
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
        // then: HTTP 403 오류를 반환하고 생성 서비스를 호출하지 않는다.
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));

    verifyNoInteractions(appMarkerService);
  }

  @Test
  @WithMockAccount(accountId = "11111111-1111-1111-1111-111111110071", channel = Channel.WEB)
  @DisplayName("웹에서 마커 생성을 요청하면, 인증 정보를 해석하거나 서비스를 호출하지 않고 거부한다")
  void createMarker_webChannel_rejectsBeforeCallingService() throws Exception {
    // given: 업무폰에 배정된 앱 계정이 아닌 웹 계정으로 요청한다.
    // when: 마커 생성 API를 호출한다.
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

    verifyNoInteractions(authenticationResolver, appMarkerService);
  }

  @Test
  @DisplayName("도메인에서 수정·삭제 충돌이 발생하면, 기존 409 상태와 write_conflict 응답을 반환한다")
  void changeMarker_domainConflict_returnsConflictError() throws Exception {
    // given: 도메인의 버전·상태 검증에서 비즈니스 오류가 발생한다.
    when(appMarkerService.update(any(MarkerUpdateServiceRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.WRITE_CONFLICT));
    when(appMarkerService.delete(any(MarkerDeleteServiceRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.WRITE_CONFLICT));

    // when & then: 공통 예외 처리 후에도 수정·삭제의 HTTP 응답 형식은 유지된다.
    for (MockHttpServletRequestBuilder request :
        List.of(
            patch("/api/markers/{markerId}", MARKER_ID),
            delete("/api/markers/{markerId}", MARKER_ID))) {
      mockMvc
          .perform(
              request
                  .header("Authorization", AUTHORIZATION)
                  .header("X-Client-Channel", "APP")
                  .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                  .header("Idempotency-Key", "idem-marker-conflict")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error", is("write_conflict")));
    }
  }

  @Test
  @DisplayName("앱에서 마커를 수정하면, 수정 결과와 HTTP 200 응답을 반환한다")
  void updateMarker_appChannel_returnsUpdatedMarker() throws Exception {
    // given: 앱의 수정 요청과 서비스 응답을 준비한다.
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
                    new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
                    "idem-marker-update-001"))
            .build();
    MarkerMutationServiceResponse serviceResponse =
        MarkerMutationServiceResponse.builder().id(MARKER_ID).status("UPDATED").version(2L).build();
    when(appMarkerService.update(any(MarkerUpdateServiceRequest.class)))
        .thenReturn(serviceResponse);

    // when & then: 수정 요청을 보내고 응답 본문과 상태 코드를 확인한다.
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
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(handler().handlerType(AppMarkerController.class));

    ArgumentCaptor<MarkerUpdateServiceRequest> requestCaptor =
        ArgumentCaptor.forClass(MarkerUpdateServiceRequest.class);
    verify(appMarkerService).update(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).usingRecursiveComparison().isEqualTo(serviceRequest);
  }

  @Test
  @DisplayName("앱에서 업무폰 헤더 없이 마커 수정을 요청하면, police_phone_required 오류로 거부한다")
  void updateMarker_missingAppPolicePhone_rejectsBeforeCallingService() throws Exception {
    // given: 업무폰 헤더가 없는 앱 요청이다.
    // when & then: 요청을 거부하고 수정 서비스를 호출하지 않는다.
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

    verifyNoInteractions(appMarkerService);
  }

  @Test
  @DisplayName("수정·삭제 요청에 양의 버전이 없으면, 서비스를 호출하지 않고 write_conflict 오류로 거부한다")
  void updateOrDeleteMarker_missingOrNonPositiveVersion_rejectsBeforeCallingService()
      throws Exception {
    // given: 본문이 없거나 버전이 누락·0·음수인 수정과 삭제 요청이다.
    for (String body : List.of("", "null", "{}", "{\"version\":0}", "{\"version\":-1}")) {
      for (MockHttpServletRequestBuilder request :
          List.of(
              patch("/api/markers/{markerId}", MARKER_ID),
              delete("/api/markers/{markerId}", MARKER_ID))) {
        // when & then: 기존 오류 코드와 HTTP 409를 유지하고 서비스를 호출하지 않는다.
        mockMvc
            .perform(
                request
                    .header("Authorization", AUTHORIZATION)
                    .header("X-Client-Channel", "APP")
                    .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                    .header("Idempotency-Key", "idem-marker-invalid-version")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", is("write_conflict")));
      }
    }
    verifyNoInteractions(appMarkerService);
  }
}
