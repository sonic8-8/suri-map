package com.surimap.app.controller.photo;

import static com.surimap.domain.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.app.service.photo.request.MarkerCreatePhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.request.PhotoAttachServiceRequest;
import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.GuardConfig;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.auth.SuriMapAuthenticationResolver;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.error.GlobalExceptionHandler;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PhotoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, PhotoRequestContextResolver.class, GuardConfig.class})
@WithMockAccount(
    accountId = "00000000-0000-0000-0000-000000000501",
    policePhoneId = "00000000-0000-0000-0000-000000000601")
class PhotoControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID PHOTO_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000601");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000602");
  private static final Instant EXPIRES_AT = Instant.parse("2026-04-28T00:15:00Z");
  private static final String AUTHORIZATION = "Bearer app-token-photo";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PhotoService photoService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
  }

  @ParameterizedTest(name = "파일 형식: {0}, 체크섬: {1}")
  @CsvSource({"image/jpeg,", "image/png," + CHECKSUM_SHA256, "image/webp," + CHECKSUM_SHA256})
  @DisplayName("앱에서 사진 업로드 주소를 요청하면, 주소·만료 시각·사진 정보를 응답한다")
  void createUploadUrl_appRequest_returnsUploadUrlAndPhotoDetails(
      String contentType, String checksumSha256) throws Exception {
    // given: 사진 서비스가 업로드 주소와 대기 사진 정보를 반환한다.
    PhotoUploadUrlServiceResponse response =
        PhotoUploadUrlServiceResponse.builder()
            .photoId(PHOTO_ID)
            .uploadUrl("http://127.0.0.1:18080/mock-upload/" + PHOTO_ID)
            .expiresAt(EXPIRES_AT)
            .maxSizeBytes(10_485_760L)
            .version(1L)
            .build();
    when(photoService.createUploadUrl(any(PhotoUploadUrlServiceRequest.class)))
        .thenReturn(response);
    String checksumField = "";
    if (checksumSha256 != null) {
      checksumField = ",\"checksumSha256\":\"" + checksumSha256 + "\"";
    }

    // when & then: 기존 URL·상태 코드·응답 필드를 유지한다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"contentType\":\"%s\",\"sizeBytes\":1048576%s}"
                        .formatted(contentType, checksumField)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.*", hasSize(5)))
        .andExpect(jsonPath("$.photoId", is(PHOTO_ID.toString())))
        .andExpect(jsonPath("$.uploadUrl", is("http://127.0.0.1:18080/mock-upload/" + PHOTO_ID)))
        .andExpect(jsonPath("$.expiresAt", is("2026-04-28T00:15:00Z")))
        .andExpect(jsonPath("$.maxSizeBytes", is(10_485_760)))
        .andExpect(jsonPath("$.version", is(1)));

    // then: URL의 마커 ID와 해석한 인증·요청 키를 파일 정보와 함께 서비스로 전달한다.
    ArgumentCaptor<PhotoUploadUrlServiceRequest> captured =
        ArgumentCaptor.forClass(PhotoUploadUrlServiceRequest.class);
    verify(photoService).createUploadUrl(captured.capture());
    assertThat(captured.getValue())
        .usingRecursiveComparison()
        .isEqualTo(
            PhotoUploadUrlServiceRequest.builder()
                .markerId(MARKER_ID)
                .contentType(contentType)
                .sizeBytes(1_048_576L)
                .checksumSha256(checksumSha256)
                .context(
                    new PhotoRequestContext(
                        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
                        "idem-photo-upload-url-001"))
                .build());
  }

  @ParameterizedTest(name = "파일 크기: {0}")
  @ValueSource(longs = {-1L, 0L, 10_485_761L})
  @DisplayName("사진 크기가 허용 범위를 벗어나면, 서비스 호출 전에 업로드 주소 발급을 거부한다")
  void createUploadUrl_invalidSize_rejectsBeforeService(long sizeBytes) throws Exception {
    // given: 앱 인증과 요청 키는 유효하지만 파일 크기가 양수가 아니거나 상한을 넘는다.
    // when & then: HTTP 입력 검증에서 기존 크기 제한 오류를 반환한다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":%s}".formatted(sizeBytes)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error", is("photo_limit_exceeded")));

    verifyNoInteractions(photoService);
  }

  @ParameterizedTest(name = "파일 형식: {0}")
  @ValueSource(strings = {"image/gif", "IMAGE/JPEG", "", " "})
  @DisplayName("사진 형식과 크기가 모두 잘못되면, 크기 오류보다 형식 오류를 먼저 반환한다")
  void createUploadUrl_invalidContentTypeAndSize_rejectsTypeBeforeSize(String contentType)
      throws Exception {
    // given: 지원하지 않는 파일 형식과 0바이트 크기를 함께 보낸다.
    // when & then: 서비스에서 사용하던 형식 우선의 오류 순서를 유지한다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"%s\",\"sizeBytes\":0}".formatted(contentType)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("invalid_photo_content_type")));

    verifyNoInteractions(photoService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"sizeBytes\":1048576}",
        "{\"contentType\":null,\"sizeBytes\":1048576}",
        "{\"sizeBytes\":0}",
        "{\"contentType\":null,\"sizeBytes\":10485761}"
      })
  @DisplayName("사진 형식이 누락되거나 null이면, 크기 검사보다 먼저 400 입력 오류를 반환한다")
  void createUploadUrl_missingContentType_rejectsBeforeSizeAndService(String requestBody)
      throws Exception {
    // given: 파일 형식 필드를 생략하거나 명시적으로 null을 보내며, 크기도 잘못될 수 있다.
    // when & then: 서비스에 위임하지 않고 사진 형식 입력 오류를 응답한다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.*", hasSize(1)))
        .andExpect(jsonPath("$.error", is("invalid_photo_content_type")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("미등록 업무폰이 사진 업로드 주소를 요청하면, 서비스 호출 전에 거부한다")
  void createUploadUrl_unregisteredPolicePhone_rejectsBeforeService() throws Exception {
    // given: 업무폰 등록 검사에서 미등록 오류를 반환한다.
    doThrow(new PolicePhoneNotRegisteredException())
        .when(policePhoneValidationPort)
        .checkRegistered(POLICE_PHONE_ID);

    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-unregistered")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));

    verifyNoInteractions(photoService);
  }

  @Test
  @WithMockAccount(accountId = "00000000-0000-0000-0000-000000000501", channel = Channel.WEB)
  @DisplayName("웹 채널에서 사진 업로드 주소를 요청하면, 채널 오류로 거부한다")
  void createUploadUrl_webChannel_rejectsBeforeService() throws Exception {
    // given: 인증과 요청 헤더가 웹 채널을 가리킨다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("채널 헤더 없이 사진 업로드 주소를 요청하면, 채널 오류로 거부한다")
  void createUploadUrl_missingChannel_rejectsBeforeService() throws Exception {
    // given: 다른 헤더는 유효하지만 채널 헤더는 보내지 않는다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("업무폰 헤더 없이 사진 업로드 주소를 요청하면, 업무폰 필수 오류로 거부한다")
  void createUploadUrl_missingPolicePhone_rejectsBeforeService() throws Exception {
    // given: 업무폰 ID 헤더를 보내지 않는다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("업무폰 ID가 UUID 형식이 아니면, 인증 해석 전에 거부한다")
  void createUploadUrl_invalidPolicePhoneId_rejectsBeforeAuthentication() throws Exception {
    // given: 업무폰 ID로 UUID가 아닌 문자열을 보낸다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", "dev-precinct-phone-01")
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    verifyNoInteractions(authenticationResolver, photoService);
  }

  @Test
  @DisplayName("헤더의 업무폰이 인증된 업무폰과 다르면, 사진 업로드 주소 발급을 거부한다")
  void createUploadUrl_mismatchedPolicePhone_rejectsAfterAuthentication() throws Exception {
    // given: 형식은 유효하지만 인증된 업무폰과 다른 ID를 보낸다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OTHER_POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("police_phone_not_registered")));

    verify(authenticationResolver).resolve(AUTHORIZATION, "APP");
    verifyNoInteractions(photoService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}",
        "{\"contentType\":\"image/jpeg\",\"sizeBytes\":10485761}",
        "{\"contentType\":null,\"sizeBytes\":1048576}",
        "{\"sizeBytes\":0}"
      })
  @DisplayName("인증 헤더가 없으면, 사진 형식·크기 검사보다 먼저 업로드 권한 오류를 반환한다")
  void createUploadUrl_missingAuthorization_rejectsBeforeBodyValidation(String requestBody)
      throws Exception {
    // given: 사진 형식·크기의 유효 여부와 관계없이 인증 헤더를 보내지 않는다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("인증 정보를 해석할 수 없으면, 사진 업로드 주소 발급을 거부한다")
  void createUploadUrl_invalidAuthorization_rejectsBeforeService() throws Exception {
    // given: 인증 해석기가 알 수 없는 토큰을 거부한다.
    when(authenticationResolver.resolve("Bearer unknown", "APP"))
        .thenThrow(new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED));

    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", "Bearer unknown")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("요청 키 없이 사진 업로드 주소를 요청하면, 쓰기 충돌 오류로 거부한다")
  void createUploadUrl_missingIdempotencyKey_rejectsBeforeService() throws Exception {
    // given: 인증·채널·업무폰 헤더는 있고 요청 키만 없다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("앱에서 사진 첨부를 요청하면, 첨부 상태와 사진·마커 버전을 응답한다")
  void attach_appRequest_returnsPhotoStatusAndVersions() throws Exception {
    // given: 사진 서비스가 첨부를 마치고 사진·마커 버전을 반환한다.
    PhotoAttachServiceResponse response =
        PhotoAttachServiceResponse.builder()
            .photoId(PHOTO_ID)
            .status("ATTACHED")
            .version(2L)
            .markerId(MARKER_ID)
            .markerVersion(2L)
            .build();
    when(photoService.attach(any(PhotoAttachServiceRequest.class))).thenReturn(response);

    // when & then: 앱의 사진 첨부 요청에 기존 HTTP 상태와 응답 필드를 유지한다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/{photoId}/attach", MARKER_ID, PHOTO_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-attach-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sizeBytes\":1048576,\"contentType\":\"image/jpeg\","
                        + "\"width\":640,\"height\":480}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photoId", is(PHOTO_ID.toString())))
        .andExpect(jsonPath("$.status", is("ATTACHED")))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.markerId", is(MARKER_ID.toString())))
        .andExpect(jsonPath("$.markerVersion", is(2)));

    ArgumentCaptor<PhotoAttachServiceRequest> captured =
        ArgumentCaptor.forClass(PhotoAttachServiceRequest.class);
    verify(photoService).attach(captured.capture());
    assertThat(captured.getValue())
        .usingRecursiveComparison()
        .isEqualTo(
            PhotoAttachServiceRequest.builder()
                .markerId(MARKER_ID)
                .photoId(PHOTO_ID)
                .sizeBytes(1_048_576L)
                .contentType("image/jpeg")
                .width(640)
                .height(480)
                .context(
                    new PhotoRequestContext(
                        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
                        "idem-photo-attach-001"))
                .build());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"{\"sizeBytes\":1048576}", "{\"contentType\":null,\"sizeBytes\":1048576}"})
  @DisplayName("첨부할 사진 형식이 누락되거나 null이면, 서비스 호출 전에 400 입력 오류를 응답한다")
  void attach_missingContentType_returnsBadRequest(String requestBody) throws Exception {
    // given: 사진 형식 필드를 생략하거나 명시적으로 null을 보낸다.
    // when & then: 인증을 확인한 뒤 형식 입력 오류를 응답하고 서비스를 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/{photoId}/attach", MARKER_ID, PHOTO_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-attach-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.*", hasSize(1)))
        .andExpect(jsonPath("$.error", is("invalid_photo_content_type")));

    verifyNoInteractions(photoService);
  }

  @Test
  @DisplayName("요청 키 없이 사진 첨부를 요청하면, 쓰기 충돌 오류로 거부한다")
  void attach_missingIdempotencyKey_rejectsBeforeService() throws Exception {
    // given: 사진 첨부 본문은 유효하지만 요청 키를 보내지 않는다.
    // when & then: HTTP 오류를 응답하고 사진 서비스는 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/{photoId}/attach", MARKER_ID, PHOTO_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sizeBytes\":1048576,\"contentType\":\"image/jpeg\","
                        + "\"width\":640,\"height\":480}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));

    verifyNoInteractions(photoService);
  }

  @ParameterizedTest(name = "contentType null 필드 포함: {0}")
  @ValueSource(booleans = {false, true})
  @DisplayName("마커 생성 전 업로드할 사진 형식이 누락되거나 null이면, 서비스 호출 전에 400 입력 오류를 응답한다")
  void createUploadUrlBeforeMarkerCreation_missingContentType_returnsBadRequest(
      boolean includeNullContentType) throws Exception {
    // given: 사건·마커·수색 차수는 지정했지만 사진 형식을 보내지 않는다.
    ObjectNode request =
        objectMapper
            .createObjectNode()
            .put("markerId", MARKER_ID.toString())
            .put("incidentId", INCIDENT_ID.toString())
            .put("opId", OP1_ID.toString())
            .put("sizeBytes", 1_048_576L);
    if (includeNullContentType) {
      request.putNull("contentType");
    }

    // when & then: 인증 확인 뒤 사진 형식 입력 오류를 응답하고 서비스를 호출하지 않는다.
    mockMvc
        .perform(
            post("/api/markers/photos/upload-url")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-photo-upload-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.*", hasSize(1)))
        .andExpect(jsonPath("$.error", is("invalid_photo_content_type")));

    verifyNoInteractions(photoService);
  }

  @ParameterizedTest(name = "파일 형식: {0}, 체크섬: {1}")
  @CsvSource({"image/jpeg,", "image/png," + CHECKSUM_SHA256, "image/webp," + CHECKSUM_SHA256})
  @DisplayName("마커 생성 전 업로드 주소를 요청하면, 앱이 지정한 마커·사건·수색 차수를 서비스로 전달한다")
  void createUploadUrlBeforeMarkerCreation_appRequest_returnsUploadUrlAndPassesContext(
      String contentType, String checksumSha256) throws Exception {
    // given: 아직 저장하지 않은 마커 ID로 사진 업로드 주소를 요청한다.
    PhotoUploadUrlServiceResponse response =
        PhotoUploadUrlServiceResponse.builder()
            .photoId(PHOTO_ID)
            .uploadUrl("http://127.0.0.1:18080/mock-upload/" + PHOTO_ID)
            .expiresAt(EXPIRES_AT)
            .maxSizeBytes(10_485_760L)
            .version(1L)
            .build();
    when(photoService.createUploadUrlBeforeMarkerCreation(
            any(MarkerCreatePhotoUploadUrlServiceRequest.class)))
        .thenReturn(response);
    ObjectNode request =
        objectMapper
            .createObjectNode()
            .put("markerId", MARKER_ID.toString())
            .put("incidentId", INCIDENT_ID.toString())
            .put("opId", OP1_ID.toString())
            .put("contentType", contentType)
            .put("sizeBytes", 1_048_576L);
    if (checksumSha256 != null) {
      request.put("checksumSha256", checksumSha256);
    }

    // when & then: 생성 전 전용 URL과 기존의 5개 응답 필드를 유지한다.
    mockMvc
        .perform(
            post("/api/markers/photos/upload-url")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-photo-upload-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.*", hasSize(5)))
        .andExpect(jsonPath("$.photoId", is(PHOTO_ID.toString())))
        .andExpect(jsonPath("$.uploadUrl", is(response.getUploadUrl())))
        .andExpect(jsonPath("$.expiresAt", is("2026-04-28T00:15:00Z")))
        .andExpect(jsonPath("$.maxSizeBytes", is(10_485_760)))
        .andExpect(jsonPath("$.version", is(1)));

    // then: HTTP 본문과 인증·요청 키를 Service Request로 변환한다.
    ArgumentCaptor<MarkerCreatePhotoUploadUrlServiceRequest> captured =
        ArgumentCaptor.forClass(MarkerCreatePhotoUploadUrlServiceRequest.class);
    verify(photoService).createUploadUrlBeforeMarkerCreation(captured.capture());
    assertThat(captured.getValue())
        .usingRecursiveComparison()
        .isEqualTo(
            MarkerCreatePhotoUploadUrlServiceRequest.builder()
                .markerId(MARKER_ID)
                .incidentId(INCIDENT_ID)
                .opId(OP1_ID)
                .contentType(contentType)
                .sizeBytes(1_048_576L)
                .checksumSha256(checksumSha256)
                .context(
                    new PhotoRequestContext(
                        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
                        "idem-marker-create-photo-upload-001"))
                .build());
  }

  @ParameterizedTest(name = "누락된 식별자: {0}")
  @ValueSource(strings = {"markerId", "incidentId", "opId"})
  @DisplayName("생성 전 업로드의 식별자가 없으면, 사진 형식·크기 검사보다 먼저 쓰기 충돌 오류를 응답한다")
  void createUploadUrlBeforeMarkerCreation_missingId_rejectsBeforePhotoValidation(
      String missingField) throws Exception {
    // given: 필수 식별자 하나와 사진 형식을 빠뜨리고 잘못된 크기를 보낸다.
    ObjectNode request =
        objectMapper
            .createObjectNode()
            .put("markerId", MARKER_ID.toString())
            .put("incidentId", INCIDENT_ID.toString())
            .put("opId", OP1_ID.toString())
            .put("sizeBytes", 0);
    request.remove(missingField);

    // when & then: 원래 서비스가 검사하던 식별자 우선 순서와 409 응답을 유지한다.
    mockMvc
        .perform(
            post("/api/markers/photos/upload-url")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-marker-create-photo-upload-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
    verifyNoInteractions(photoService);
  }
}
