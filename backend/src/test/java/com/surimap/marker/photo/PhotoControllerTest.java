package com.surimap.marker.photo;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.GuardConfig;
import com.surimap.marker.photo.controller.PhotoController;
import com.surimap.marker.photo.controller.PhotoRequestContextResolver;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.dto.PhotoAttachResult;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.exception.PhotoExceptionHandler;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.photo.service.PhotoService;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PhotoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({PhotoExceptionHandler.class, PhotoRequestContextResolver.class, GuardConfig.class})
@DisplayName("사진 upload-url/attach API")
@WithMockAccount(
    accountId = "00000000-0000-0000-0000-000000000501",
    policePhoneId = "00000000-0000-0000-0000-000000000601")
class PhotoControllerTest {

  private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID PHOTO_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID OP_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000601");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000602");
  private static final Instant EXPIRES_AT = Instant.parse("2026-04-28T00:15:00Z");
  private static final String AUTHORIZATION = "Bearer app-token-photo";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PhotoService photoService;
  @MockitoBean private SuriMapAuthenticationResolver authenticationResolver;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;

  @BeforeEach
  void setUp() {
    when(authenticationResolver.resolve(AUTHORIZATION, "APP"))
        .thenReturn(new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID));
  }

  @Test
  @DisplayName("APP upload-url 요청은 201과 canonical response field를 반환한다")
  void appUploadUrlReturnsCanonicalResponse() throws Exception {
    var response =
        new PhotoUploadUrlResponse(
            PHOTO_ID,
            "http://127.0.0.1:18080/mock-upload/" + PHOTO_ID,
            EXPIRES_AT,
            10_485_760L,
            1L);
    when(photoService.createUploadUrl(
            eq(MARKER_ID),
            eq(new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null)),
            argThat(
                context ->
                    context.authentication().accountId().equals(ACCOUNT_ID)
                        && context.authentication().policePhoneId().equals(POLICE_PHONE_ID)
                        && context.idempotencyKey().equals("idem-photo-upload-url-001"))))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID.toString())
                .header("Idempotency-Key", "idem-photo-upload-url-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1048576}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.photoId", is(PHOTO_ID.toString())))
        .andExpect(jsonPath("$.uploadUrl", is("http://127.0.0.1:18080/mock-upload/" + PHOTO_ID)))
        .andExpect(jsonPath("$.expiresAt", is("2026-04-28T00:15:00Z")))
        .andExpect(jsonPath("$.maxSizeBytes", is(10_485_760)))
        .andExpect(jsonPath("$.version", is(1)));

    verify(photoService)
        .createUploadUrl(
            eq(MARKER_ID),
            eq(new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null)),
            argThat(
                context ->
                    context.authentication().channel().equals("APP")
                        && context.authentication().policePhoneId().equals(POLICE_PHONE_ID)));
  }

  @Test
  @DisplayName("APP upload-url은 미등록 업무폰이면 서비스 호출 전에 거부한다")
  void unregisteredPolicePhoneRejectedBeforePhotoService() throws Exception {
    doThrow(new PolicePhoneNotRegisteredException())
        .when(policePhoneValidationPort)
        .checkRegistered(POLICE_PHONE_ID);

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
  @DisplayName("WEB upload-url 요청은 channel_not_allowed로 거부한다")
  void webUploadUrlRejected() throws Exception {
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
  @DisplayName("채널 헤더가 없는 upload-url 요청은 channel_not_allowed로 거부한다")
  void missingChannelRejected() throws Exception {
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
  @DisplayName("업무폰 헤더가 없는 upload-url 요청은 police_phone_required로 거부한다")
  void missingPolicePhoneRejected() throws Exception {
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
  @DisplayName("UUID가 아닌 PolicePhone 헤더는 parse 단계에서 거부한다")
  void arbitraryPolicePhoneStringRejected() throws Exception {
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
  @DisplayName("인증된 PolicePhone과 다른 유효 UUID 헤더는 세션 미연결 업무폰으로 거부한다")
  void mismatchedPolicePhoneUuidRejectedAfterAuthentication() throws Exception {
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

  @Test
  @DisplayName("Authorization 헤더가 없는 upload-url 요청은 incident_access_denied로 거부한다")
  void missingAuthorizationRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/markers/{markerId}/photos/upload-url", MARKER_ID)
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
  @DisplayName("SuriMapAuthentication 해석에 실패한 Authorization은 거부한다")
  void invalidAuthorizationRejected() throws Exception {
    when(authenticationResolver.resolve("Bearer unknown", "APP"))
        .thenThrow(new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN));

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
  @DisplayName("Idempotency-Key가 없는 upload-url 요청은 write_conflict로 거부한다")
  void missingIdempotencyKeyRejected() throws Exception {
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
  @DisplayName("APP attach 요청은 ATTACHED response를 반환한다")
  void appAttachReturnsCanonicalResponse() throws Exception {
    var result =
        new PhotoAttachResult(
            new PhotoAttachResponse(PHOTO_ID, "ATTACHED", 2L, MARKER_ID, 2L),
            new PublishRequest(
                "MARKER_UPDATED",
                new PublishRequestPayload(
                    MARKER_ID,
                    INCIDENT_ID,
                    OP_ID,
                    POLICE_PHONE_ID,
                    "UPDATED",
                    2L,
                    new PhotoDelta(PHOTO_ID, "ATTACHED", 2L))));
    when(photoService.attach(
            eq(MARKER_ID),
            eq(PHOTO_ID),
            eq(new PhotoAttachRequest(1_048_576L, "image/jpeg", 640, 480, null)),
            argThat(context -> context.idempotencyKey().equals("idem-photo-attach-001"))))
        .thenReturn(result);

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

    verify(photoService)
        .attach(
            eq(MARKER_ID),
            eq(PHOTO_ID),
            eq(new PhotoAttachRequest(1_048_576L, "image/jpeg", 640, 480, null)),
            argThat(context -> context.authentication().accountId().equals(ACCOUNT_ID)));
  }

  @Test
  @DisplayName("Idempotency-Key가 없는 attach 요청은 write_conflict로 거부한다")
  void missingAttachIdempotencyKeyRejected() throws Exception {
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
}
