package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.GuardConfig;
import com.surimap.marker.controller.MarkerController;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.exception.MarkerExceptionHandler;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.service.MarkerReadService;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.marker.service.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;
import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import com.surimap.support.auth.WithMockAccount;
import java.math.BigDecimal;
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

@WebMvcTest(MarkerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MarkerExceptionHandler.class, MarkerRequestContextResolver.class, GuardConfig.class})
@WithMockAccount(
    accountId = "11111111-1111-1111-1111-111111110071",
    policePhoneId = "22222222-2222-2222-2222-222222220071")
class MarkerControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final String AUTHORIZATION = "Bearer marker-create-app";

  @Autowired private MockMvc mockMvc;

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
    when(markerUpdateDeleteService.update(any(MarkerUpdateServiceRequest.class)))
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
        .andExpect(jsonPath("$.version", is(2)));

    ArgumentCaptor<MarkerUpdateServiceRequest> requestCaptor =
        ArgumentCaptor.forClass(MarkerUpdateServiceRequest.class);
    verify(markerUpdateDeleteService).update(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).usingRecursiveComparison().isEqualTo(serviceRequest);
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
    when(markerUpdateDeleteService.delete(any(MarkerDeleteServiceRequest.class)))
        .thenReturn(serviceResponse);

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
    verify(markerUpdateDeleteService).delete(requestCaptor.capture());
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

    verifyNoInteractions(markerUpdateDeleteService);
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
    verifyNoInteractions(markerUpdateDeleteService);
  }
}
