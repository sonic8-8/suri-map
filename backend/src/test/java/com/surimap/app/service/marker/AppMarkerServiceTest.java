package com.surimap.app.service.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_COMMANDER_ID;
import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.account.AccountIdentityCatalog.SUPPORT_TEAM_ID;
import static com.surimap.domain.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.maparea.fixture.BoundaryAreaFixtures.OVERALL_AREA_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.HARNESS_OVERALL_SEARCH_AREA;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static com.surimap.policephone.PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.app.service.photo.request.MarkerCreatePhotoServiceRequest;
import com.surimap.app.service.photo.request.PhotoAttachServiceRequest;
import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.client.fcm.MockFcmDispatcher;
import com.surimap.client.storage.MockObjectStorageAdapter;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerType;
import com.surimap.domain.photo.MarkerPhoto;
import com.surimap.domain.photo.PhotoMapper;
import com.surimap.domain.photo.PhotoStatus;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.geometry.GeoJsonPoint;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.policephone.PolicePhonePersistenceService;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {"surimap.object-storage.provider=mock", "fcm.provider=mock"})
class AppMarkerServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID MUTATION_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID PHOTO_ID = UUID.fromString("55555555-5555-5555-5555-555555550172");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333330071");
  // Flyway가 생성하는 지휘 계정과 현장 지휘관 계정의 업무폰이다.
  private static final UUID COMMANDER_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID FIELD_COMMANDER_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000208");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final String IDEMPOTENCY_KEY = "idem-marker-create-001";
  private static final String PHOTO_UPLOAD_IDEMPOTENCY_KEY = "idem-sc06-photo-upload-001";
  private static final String PHOTO_ATTACH_IDEMPOTENCY_KEY = "idem-sc06-photo-attach-001";
  private static final String MARKER_MEMO = "field clue";
  private static final String TEAM_FCM_TOKEN = "token-marker-team";
  private static final String COMMANDER_FCM_TOKEN = "token-marker-commander";
  private static final String FIELD_COMMANDER_FCM_TOKEN = "token-marker-field-commander";
  // 이전 DTO 문자열 형식으로 계산해 둔 값이다. 운영 코드의 해시 함수를 기대값 생성에 사용하지 않는다.
  private static final String LEGACY_UPDATE_REQUEST_HASH =
      "5071d0522889dba1ae1cc2d04aaa4321bc3ec01a86b8cdc1b4eececcfb272a93";
  private static final String LEGACY_DELETE_REQUEST_HASH =
      "ceec70afabd12682afa1687e5c92946715fd369056003191189b1b2bcba3689a";

  @Autowired private AppMarkerService appMarkerService;
  @Autowired private PhotoService photoService;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private PhotoMapper photoMapper;
  @Autowired private MockObjectStorageAdapter objectStorage;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MockFcmDispatcher fcmDispatcher;
  @Autowired private PolicePhonePersistenceService policePhonePersistenceService;
  @Autowired private PlatformTransactionManager transactionManager;

  private SuriMapAuthentication authentication;

  @BeforeEach
  void setUp() {
    // Testcontainers의 테스트 DB에서 이 사건과 요청 키로 만든 데이터만 정리한다.
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM marker_notification WHERE marker_id IN (SELECT id FROM marker WHERE"
            + " incident_id = ?)",
        INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM photo WHERE marker_id = ? OR marker_id IN (SELECT id FROM marker WHERE incident_id ="
            + " ?)",
        MARKER_ID,
        INCIDENT_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key IN (?, ?, ?)",
        IDEMPOTENCY_KEY,
        PHOTO_UPLOAD_IDEMPOTENCY_KEY,
        PHOTO_ATTACH_IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
    objectStorage.clear();
    fcmDispatcher.reset();
    jdbcTemplate.update(
        "DELETE FROM fcm_token WHERE police_phone_id IN (?, ?, ?)",
        ASSIGNED_POLICE_PHONE_ID,
        COMMANDER_PHONE_ID,
        FIELD_COMMANDER_PHONE_ID);
    // 기본 시드의 지휘 업무폰은 미등록 상태다. 이 테스트는 등록과 토큰 발급을 마친 수신자를 가정한다.
    jdbcTemplate.update(
        "UPDATE police_phone SET registered = TRUE WHERE id = ?", COMMANDER_PHONE_ID);
    policePhonePersistenceService.registerFcmToken(
        ASSIGNED_POLICE_PHONE_ID, PRECINCT_TEAM_ID.toString(), "marker-test", TEAM_FCM_TOKEN);
    policePhonePersistenceService.registerFcmToken(
        COMMANDER_PHONE_ID, PRECINCT_COMMANDER_ID.toString(), "marker-test", COMMANDER_FCM_TOKEN);
    policePhonePersistenceService.registerFcmToken(
        FIELD_COMMANDER_PHONE_ID,
        SUPPORT_TEAM_ID.toString(),
        "marker-test",
        FIELD_COMMANDER_FCM_TOKEN);

    jdbcTemplate.update(
        """
        INSERT INTO incident (id, source_incident_id, title, status, opened_at, version, created_at, updated_at)
        VALUES (?, ?, 'Marker create fixture', 'OPEN', NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'OPEN', closed_at = NULL, closed_by_account_id = NULL
        """,
        INCIDENT_ID,
        INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (id, incident_id, sequence_number, status, reason,
            started_by_account_id, started_at, version, created_at, updated_at)
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL', ?, NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', ended_at = NULL
        """,
        OP1_ID,
        INCIDENT_ID,
        PRECINCT_TEAM_ID);

    // 같은 사건에 일반 대원, 지휘 계정, 현장 지휘관을 각각 배정한다.
    UUID memberAssignmentId = assignAccount(PRECINCT_TEAM_ID, "MEMBER");
    assignAccount(PRECINCT_COMMANDER_ID, "MEMBER");
    assignAccount(SUPPORT_TEAM_ID, "FIELD_COMMANDER");
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (id, operational_period_id, incident_assignment_id, police_phone_id,
            status, started_by_account_id, started_at, version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW(), 1, NOW(), NOW())
        """,
        DUTY_SHIFT_ID,
        OP1_ID,
        memberAssignmentId,
        ASSIGNED_POLICE_PHONE_ID,
        PRECINCT_TEAM_ID);
    authentication = new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("앱의 생성·수정·삭제 요청을 직렬화하면, 인증 정보와 멱등키는 본문에서 제외한다")
  void serializeMarkerRequests_appWrites_excludesAuthenticationAndIdempotencyKey() {
    // given: 인증 정보와 멱등키가 있는 앱의 서비스 요청을 준비한다.
    List<Object> requests = List.of(createRequest("CLUE", null), updateRequest(), deleteRequest());

    for (Object request : requests) {
      // when: 멱등성 비교에 사용하는 요청 본문을 JSON으로 변환한다.
      JsonNode body = objectMapper.valueToTree(request);

      // then: 요청 본문은 남기고, 헤더에서 받은 인증 정보와 멱등키는 제외한다.
      assertThat(body.isEmpty()).isFalse();
      assertThat(body.has("authentication")).isFalse();
      assertThat(body.has("idempotencyKey")).isFalse();
    }
  }

  @Test
  @DisplayName("사진 없이 마커를 생성하면, 활성 상태의 마커와 생성 이벤트를 저장한다")
  void createMarker_withoutPhotos_savesActiveMarkerAndCreationEvent() throws Exception {
    // given: 현재 OP에서 근무 중인 업무폰이 사진 없이 단서 마커 생성을 요청한다.
    MarkerCreateServiceRequest request = createRequest("CLUE", null);
    Instant startedAt = Instant.now();

    // when: 실제 생성 서비스를 호출한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);
    Instant completedAt = Instant.now();

    // then: 응답과 DB에 활성 마커가 남고, 생성 이벤트에 업무폰 기록 시각과 서버 처리 시각이 담긴다.
    assertThat(response.getId()).isNotNull();
    assertThat(response.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(response.getOpId()).isEqualTo(OP1_ID);
    assertThat(response.getPolicePhoneId()).isEqualTo(ASSIGNED_POLICE_PHONE_ID);
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(response.getPhotos()).isEmpty();

    Marker marker = markerMapper.findById(response.getId()).orElseThrow();
    assertThat(marker.getId()).isEqualTo(response.getId());
    assertThat(marker.getOperationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getMarkerSource()).isEqualTo("APP");
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(marker.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(marker.getCreatedByAccountId()).isEqualTo(PRECINCT_TEAM_ID);
    assertThat(marker.getPolicePhoneId()).isEqualTo(ASSIGNED_POLICE_PHONE_ID);
    assertThat(marker.getOccurredAt()).isEqualTo(CLIENT_TS);
    assertThat(marker.getMemo()).isEqualTo(MARKER_MEMO);
    assertThat(marker.getLocation().getSRID()).isEqualTo(4326);
    assertThat(marker.getLocation().getX()).isEqualTo(126.9134);
    assertThat(marker.getLocation().getY()).isEqualTo(35.1631);

    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    JsonNode event = readEventPayload("MARKER_CREATED");
    assertThat(event.path("id").asText()).isEqualTo(response.getId().toString());
    assertThat(event.path("incidentId").asText()).isEqualTo(INCIDENT_ID.toString());
    assertThat(event.path("opId").asText()).isEqualTo(OP1_ID.toString());
    assertThat(event.path("policePhoneId").asText()).isEqualTo(ASSIGNED_POLICE_PHONE_ID.toString());
    assertThat(event.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(event.path("version").asLong()).isEqualTo(1L);
    assertThat(event.path("type").asText()).isEqualTo("CLUE");
    assertThat(event.path("location").path("type").asText()).isEqualTo("Point");
    assertThat(event.path("location").path("coordinates").path(0).asDouble()).isEqualTo(126.9134);
    assertThat(event.path("location").path("coordinates").path(1).asDouble()).isEqualTo(35.1631);
    assertThat(event.path("clientTs").asText()).isEqualTo(CLIENT_TS.toString());
    assertThat(Instant.parse(event.path("serverTs").asText())).isBetween(startedAt, completedAt);
  }

  @Test
  @DisplayName("좌표가 소수점 아래 7자리이면, 6자리로 반올림해 마커와 이벤트에 기록한다")
  void createMarker_sevenDecimalPlaceCoordinates_roundsToSixDecimalPlaces() throws Exception {
    // given: 경도와 위도의 소수점 아래가 각각 7자리인 좌표를 보낸다.
    MarkerCreateServiceRequest request =
        MarkerCreateServiceRequest.builder()
            .incidentId(INCIDENT_ID)
            .opId(OP1_ID)
            .type("CLUE")
            .location(
                new GeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9134007"), new BigDecimal("35.1631007"))))
            .memo("precision-over-6dp")
            .clientTs(CLIENT_TS)
            .clockOffsetMs(0L)
            .authentication(authentication)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

    // when: 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 저장된 좌표와 이벤트 좌표가 모두 소수점 아래 6자리로 반올림된다.
    Marker marker = markerMapper.findById(response.getId()).orElseThrow();
    assertThat(marker.getLocation().getX()).isEqualTo(126.913401);
    assertThat(marker.getLocation().getY()).isEqualTo(35.163101);
    JsonNode coordinates = readEventPayload("MARKER_CREATED").path("location").path("coordinates");
    assertThat(coordinates.path(0).asDouble()).isEqualTo(126.913401);
    assertThat(coordinates.path(1).asDouble()).isEqualTo(35.163101);
  }

  @Test
  @DisplayName("업로드된 사진을 포함해 마커를 생성하면, 초기 상태의 마커와 첨부 사진·생성 이벤트 하나를 저장한다")
  void createMarker_uploadedPhoto_attachesPhoto() throws Exception {
    // given: 사진 업로드가 완료됐고, 첨부 기한이 남아 있는 사진을 생성 요청에 포함한다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();

    // when: 업로드된 사진과 함께 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 사진 첨부는 최초 생성에 포함되며, 마커를 수정 상태로 바꾸거나 수정 이벤트를 만들지 않는다.
    assertThat(response.getId()).isEqualTo(MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(response.getPhotos())
        .singleElement()
        .satisfies(
            photo -> {
              assertThat(photo.getPhotoId()).isEqualTo(PHOTO_ID);
              assertThat(photo.getStatus()).isEqualTo("ATTACHED");
              assertThat(photo.getMarkerVersion()).isEqualTo(1L);
            });
    MarkerPhoto photo = photoMapper.findById(PHOTO_ID).orElseThrow();
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(photo.getVersion()).isEqualTo(2L);
    assertThat(photo.getWidth()).isEqualTo(640);
    assertThat(photo.getHeight()).isEqualTo(480);
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    JsonNode createdEvent = readEventPayload("MARKER_CREATED");
    assertThat(createdEvent.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(createdEvent.path("version").asLong()).isEqualTo(1L);
  }

  @Test
  @DisplayName("사진 여러 장을 포함해 마커를 생성하면, 모든 사진을 첨부하고 마커 버전과 생성 이벤트는 하나로 유지한다")
  void createMarker_multipleUploadedPhotos_savesInitialMarkerWithOneCreationEvent() {
    // given: 같은 마커에 첨부할 사진 두 장의 업로드가 완료됐다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    MarkerCreatePhotoServiceRequest secondPhoto = prepareUploadedPhoto(UUID.randomUUID());
    request = request.toBuilder().photos(List.of(request.getPhotos().get(0), secondPhoto)).build();

    // when: 두 사진을 포함해 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 사진 수와 관계없이 마커는 최초 상태이고, 각 사진의 첨부 결과를 응답한다.
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(response.getPhotos())
        .hasSize(2)
        .allSatisfy(
            photo -> {
              assertThat(photo.getStatus()).isEqualTo("ATTACHED");
              assertThat(photo.getMarkerId()).isEqualTo(MARKER_ID);
              assertThat(photo.getMarkerVersion()).isEqualTo(1L);
              assertThat(photoMapper.findById(photo.getPhotoId()).orElseThrow().getStatus())
                  .isEqualTo(PhotoStatus.ATTACHED);
            });
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
  }

  @Test
  @DisplayName("여러 사진 중 뒤의 사진이 만료됐으면, 앞의 첨부도 롤백하고 만료된 사진만 실패로 남긴다")
  void createMarker_laterPhotoExpired_rollsBackEarlierAttachmentAndKeepsFailedPhoto() {
    // given: 첫 사진은 유효하지만, 두 번째 사진의 첨부 기한은 지났다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    MarkerCreatePhotoServiceRequest expiredPhoto = prepareUploadedPhoto(UUID.randomUUID());
    jdbcTemplate.update(
        "UPDATE photo SET upload_url_expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?",
        expiredPhoto.getPhotoId());
    MarkerCreateServiceRequest invalidRequest =
        request.toBuilder().photos(List.of(request.getPhotos().get(0), expiredPhoto)).build();

    // when: 사진을 포함한 마커 생성이 두 번째 사진에서 실패한다.
    assertThatThrownBy(() -> appMarkerService.create(invalidRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 앞의 사진 첨부와 마커·이벤트·요청 기록은 취소하고, 만료된 사진의 실패만 보존한다.
    MarkerPhoto first = photoMapper.findById(PHOTO_ID).orElseThrow();
    assertThat(first.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(first.getVersion()).isEqualTo(1L);
    MarkerPhoto expired = photoMapper.findById(expiredPhoto.getPhotoId()).orElseThrow();
    assertThat(expired.getStatus()).isEqualTo(PhotoStatus.FAILED);
    assertThat(expired.getVersion()).isEqualTo(2L);
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
    assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"null", "\"\"", "\" \"", "\"image/gif\""})
  @DisplayName("마커 생성 사진의 형식이 없거나 지원하지 않으면, 요청을 거부하고 마커와 이벤트를 저장하지 않는다")
  void createMarker_invalidPhotoContentType_rejectsRequestAndRollsBackMarker(String contentTypeJson)
      throws Exception {
    // given: contentType 필드를 생략하거나 null·빈 값·공백·미지원 형식으로 보낸다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    ObjectNode photoJson = objectMapper.valueToTree(request.getPhotos().get(0));
    if (contentTypeJson == null) {
      photoJson.remove("contentType");
    } else {
      photoJson.set("contentType", objectMapper.readTree(contentTypeJson));
    }
    MarkerCreateServiceRequest invalidRequest =
        request.toBuilder()
            .photos(
                List.of(objectMapper.treeToValue(photoJson, MarkerCreatePhotoServiceRequest.class)))
            .build();

    // when: 사진 형식이 잘못된 요청으로 마커를 생성하려 한다.
    Throwable failure = catchThrowable(() -> appMarkerService.create(invalidRequest));

    // then: 기존 사진은 첨부 대기 상태를 유지하고, 마커·이벤트·요청 처리 기록은 남지 않는다.
    MarkerPhoto photo = photoMapper.findById(PHOTO_ID).orElseThrow();
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.getVersion()).isEqualTo(1L);
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
    assertThat(failure)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("invalid_photo_content_type", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("만료된 사진을 포함해 마커를 생성하면, 사진은 실패 상태로 남기고 마커와 이벤트는 저장하지 않는다")
  void createMarker_expiredPhoto_savesFailedPhotoAndRollsBackMarker() {
    // given: 마커 생성 전에 업로드한 사진의 첨부 기한이 지났다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    jdbcTemplate.update(
        "UPDATE photo SET upload_url_expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?",
        PHOTO_ID);

    // when: 만료된 사진을 포함해 마커를 생성하려 한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 사용할 수 없는 사진만 실패 상태로 남고 마커·이벤트·요청 처리 기록은 롤백된다.
    MarkerPhoto photo = photoMapper.findById(PHOTO_ID).orElseThrow();
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.FAILED);
    assertThat(photo.getVersion()).isEqualTo(2L);
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("마커 생성 후 사진을 첨부하면, 사진과 변경된 마커·수정 이벤트를 함께 저장한다")
  void createMarker_photoAttachedAfterCreation_savesPhotoAndUpdatedEvent(boolean withInitialPhoto)
      throws Exception {
    // given: 처음 사진을 포함했는지와 관계없이, 생성된 마커에 새 사진을 추가한다.
    MarkerCreateServiceRequest creationRequest = createRequest("CLUE", null);
    if (withInitialPhoto) {
      creationRequest = prepareMarkerRequestWithUploadedPhoto();
    }
    MarkerCreateServiceResponse created = appMarkerService.create(creationRequest);
    UUID markerId = created.getId();
    PhotoUploadUrlServiceResponse upload =
        photoService.createUploadUrl(
            PhotoUploadUrlServiceRequest.builder()
                .markerId(markerId)
                .contentType("image/jpeg")
                .sizeBytes(1_048_576L)
                .checksumSha256(CHECKSUM_SHA256)
                .context(new PhotoRequestContext(authentication, PHOTO_UPLOAD_IDEMPOTENCY_KEY))
                .build());
    MarkerPhoto pendingPhoto = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    assertThat(created.getStatus()).isEqualTo("ACTIVE");
    assertThat(created.getVersion()).isEqualTo(1L);
    assertThat(pendingPhoto.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(upload.getUploadUrl()).endsWith(pendingPhoto.getObjectKey());
    objectStorage.simulateUpload(pendingPhoto.getObjectKey());

    // when: 생성 요청과 별개의 사진 첨부 요청을 실제 사진 서비스로 처리한다.
    PhotoAttachServiceResponse attached =
        photoService.attach(
            PhotoAttachServiceRequest.builder()
                .sizeBytes(1_048_576L)
                .contentType("image/jpeg")
                .width(640)
                .height(480)
                .checksumSha256(CHECKSUM_SHA256)
                .markerId(markerId)
                .photoId(upload.getPhotoId())
                .context(new PhotoRequestContext(authentication, PHOTO_ATTACH_IDEMPOTENCY_KEY))
                .build());

    // then: 사진과 부모 마커의 상태·버전이 바뀌고, DB에 생성·수정 이벤트가 하나씩 남는다.
    assertThat(attached.getPhotoId()).isEqualTo(upload.getPhotoId());
    assertThat(attached.getStatus()).isEqualTo("ATTACHED");
    assertThat(attached.getVersion()).isEqualTo(2L);
    assertThat(attached.getMarkerId()).isEqualTo(markerId);
    assertThat(attached.getMarkerVersion()).isEqualTo(2L);
    MarkerPhoto photo = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    assertThat(photo.getMarkerId()).isEqualTo(markerId);
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(photo.getVersion()).isEqualTo(2L);
    assertThat(photo.getWidth()).isEqualTo(640);
    assertThat(photo.getHeight()).isEqualTo(480);
    assertThat(photo.getAttachedAt()).isNotNull();
    Marker marker = markerMapper.findById(markerId).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("UPDATED");
    assertThat(marker.getVersion()).isEqualTo(2L);
    assertThat(readMarkerIds()).containsExactly(markerId);
    List<UUID> expectedPhotoIds = new ArrayList<>();
    if (withInitialPhoto) {
      expectedPhotoIds.add(PHOTO_ID);
    }
    expectedPhotoIds.add(upload.getPhotoId());
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, markerId))
        .containsExactlyInAnyOrderElementsOf(expectedPhotoIds);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", "MARKER_UPDATED");
    JsonNode createdEvent = readEventPayload("MARKER_CREATED");
    assertThat(createdEvent.path("id").asText()).isEqualTo(markerId.toString());
    assertThat(createdEvent.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(createdEvent.path("version").asLong()).isEqualTo(1L);
    JsonNode updatedEvent = readEventPayload("MARKER_UPDATED");
    assertThat(updatedEvent.path("id").asText()).isEqualTo(markerId.toString());
    assertThat(updatedEvent.path("incidentId").asText()).isEqualTo(INCIDENT_ID.toString());
    assertThat(updatedEvent.path("opId").asText()).isEqualTo(OP1_ID.toString());
    assertThat(updatedEvent.path("policePhoneId").asText())
        .isEqualTo(ASSIGNED_POLICE_PHONE_ID.toString());
    assertThat(updatedEvent.path("status").asText()).isEqualTo("UPDATED");
    assertThat(updatedEvent.path("version").asLong()).isEqualTo(2L);
    JsonNode photoDelta = updatedEvent.path("photoDelta");
    assertThat(photoDelta.path("photoId").asText()).isEqualTo(upload.getPhotoId().toString());
    assertThat(photoDelta.path("status").asText()).isEqualTo("ATTACHED");
    assertThat(photoDelta.path("version").asLong()).isEqualTo(2L);
  }

  @Test
  @DisplayName("활성 근무교대가 없으면, 마커 생성을 거부하고 마커·이벤트·요청 처리 기록을 남기지 않는다")
  void createMarker_withoutActiveDutyShift_rejectsWithoutSavingMarkerEventOrRequest() {
    // given: 사건에는 배정되어 있지만 현재 수색 차수의 활성 근무교대는 없다.
    jdbcTemplate.update("DELETE FROM duty_shift WHERE id = ?", DUTY_SHIFT_ID);
    MarkerCreateServiceRequest request = createRequest("CLUE", null);

    // when: 해당 계정의 업무폰에서 마커 생성을 요청한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POLICE_PHONE_NOT_ASSIGNED);
              assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            });

    // then: 마커와 이벤트를 저장하지 않고 예약했던 요청 키도 롤백한다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("웹 채널에서 마커 생성을 요청하면, 요청을 거부하고 마커·이벤트를 저장하지 않는다")
  void createMarker_webChannel_rejectsWithoutSavingMarkerOrEvent() {
    // given: 앱이 아닌 웹 채널에서 단서 마커 생성을 요청한다.
    SuriMapAuthentication webAuthentication =
        new SuriMapAuthentication(PRECINCT_TEAM_ID, "WEB", ASSIGNED_POLICE_PHONE_ID);
    MarkerCreateServiceRequest request = createRequest("CLUE", null);

    // when: 웹 채널의 생성 요청을 처리한다.
    assertThatThrownBy(
            () ->
                appMarkerService.create(
                    request.toBuilder()
                        .authentication(webAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("channel_not_allowed");

    // then: 마커와 생성 이벤트가 DB에 남지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
  }

  @ParameterizedTest(name = "경도: {0}, 위도: {1}")
  @CsvSource({"126.9,91", "35.163100,126.913400"})
  @DisplayName("경위도 범위를 벗어난 좌표로 마커를 생성하면, 오류를 반환하고 마커·이벤트를 저장하지 않는다")
  void createMarker_invalidLocation_rejectsWithoutSavingMarkerOrEvent(
      String longitude, String latitude) {
    // given: 현재 수색 차수와 앱 권한은 유효하지만 위도 범위를 벗어난 좌표를 보낸다.
    MarkerCreateServiceRequest request =
        createRequest("CLUE", null).toBuilder()
            .id(MARKER_ID)
            .location(
                new GeoJsonPoint(
                    "Point", List.of(new BigDecimal(longitude), new BigDecimal(latitude))))
            .build();

    // when: 실제 서비스에서 좌표 검증에 실패한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);

    // then: 위도 초과와 경위도를 뒤집은 요청 모두 마커·사진·이벤트·요청 키를 남기지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM photo WHERE marker_id = ?", Integer.class, MARKER_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
  }

  @Test
  @DisplayName("요청의 OP가 현재 OP와 다르면, 요청을 거부하고 마커·이벤트를 저장하지 않는다")
  void createMarker_mismatchedOp_rejectsWithoutSavingMarkerOrEvent() {
    // given: 현재 OP는 OP1인데 생성 요청은 OP2를 가리킨다.
    MarkerCreateServiceRequest request =
        MarkerCreateServiceRequest.builder()
            .incidentId(INCIDENT_ID)
            .opId(OP2_ID)
            .type("CLUE")
            .location(createMarkerLocation())
            .memo(MARKER_MEMO)
            .clientTs(CLIENT_TS)
            .clockOffsetMs(0L)
            .authentication(authentication)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

    // when: 현재 OP와 다른 OP로 마커 생성을 요청한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_MISMATCH);

    // then: 마커와 생성 이벤트가 DB에 남지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
  }

  @Test
  @DisplayName("전체 수색구역 밖의 유효한 좌표로 마커를 생성해도, 마커와 생성 이벤트를 저장한다")
  void createMarker_outsideOverallSearchArea_savesMarkerAndCreationEvent() throws Exception {
    // given: 실제 전체 수색구역을 저장하고, 그 구역 밖의 유효한 좌표를 보낸다.
    jdbcTemplate.update(
        """
        INSERT INTO search_area (id, operational_period_id, name, area_level, geometry, status, version, created_by_account_id)
        VALUES (?, ?, 'Overall search area', 'OVERALL', ST_GeomFromText(?, 4326), 'ACTIVE', 1, ?)
        """,
        OVERALL_AREA_ID,
        OP1_ID,
        HARNESS_OVERALL_SEARCH_AREA.toText(),
        PRECINCT_TEAM_ID);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT ST_Covers(geometry, ST_SetSRID(ST_MakePoint(127.2, 35.1631), 4326)) FROM"
                    + " search_area WHERE id = ?",
                Boolean.class,
                OVERALL_AREA_ID))
        .isFalse();
    MarkerCreateServiceRequest request =
        MarkerCreateServiceRequest.builder()
            .incidentId(INCIDENT_ID)
            .opId(OP1_ID)
            .type("CLUE")
            .location(
                new GeoJsonPoint(
                    "Point", List.of(new BigDecimal("127.200000"), new BigDecimal("35.163100"))))
            .memo("outside overall search area")
            .clientTs(CLIENT_TS)
            .clockOffsetMs(0L)
            .authentication(authentication)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

    // when: 구역 밖의 좌표로 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 구역 밖이라는 이유로 거부하지 않고 마커와 생성 이벤트를 저장한다.
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(readMarkerIds()).containsExactly(response.getId());
    Marker marker = markerMapper.findById(response.getId()).orElseThrow();
    assertThat(marker.getLocation().getX()).isEqualTo(127.2);
    assertThat(marker.getLocation().getY()).isEqualTo(35.1631);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    JsonNode coordinates = readEventPayload("MARKER_CREATED").path("location").path("coordinates");
    assertThat(coordinates.path(0).asDouble()).isEqualTo(127.2);
    assertThat(coordinates.path(1).asDouble()).isEqualTo(35.1631);
  }

  @Test
  @DisplayName("지원 요청 마커를 생성하면, 지휘 계정·현장 지휘관 대상 알림을 저장하고 커밋 후 FCM으로 전달한다")
  void createMarker_supportRequest_savesNotificationAndDispatchesAfterCommit() throws Exception {
    // given: 일반 대원·지휘 계정·현장 지휘관이 배정된 사건에서 드론 지원을 요청한다.
    MarkerCreateServiceRequest request = createRequest("SUPPORT_REQUEST", "DRONE");
    Instant startedAt = Instant.now();

    // when: 실제 트랜잭션 안에서 생성하며, 커밋 전에는 FCM 전달이 일어나지 않는다.
    MarkerCreateServiceResponse response =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  MarkerCreateServiceResponse created = appMarkerService.create(request);
                  assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
                  return created;
                });
    Instant completedAt = Instant.now();

    // then: 일반 대원은 제외하고 지휘 계정과 현장 지휘관의 계정·업무폰만 알림 대상으로 저장한다.
    Marker marker = markerMapper.findById(response.getId()).orElseThrow();
    assertThat(marker.getSupportRequestType()).isEqualTo("DRONE");
    assertNotificationStored(
        response,
        "SUPPORT_REQUEST",
        "SUPPORT_REQUEST_CREATED",
        "COMMANDERS_AND_FIELD_COMMANDERS",
        List.of(PRECINCT_COMMANDER_ID, SUPPORT_TEAM_ID),
        List.of(COMMANDER_PHONE_ID, FIELD_COMMANDER_PHONE_ID),
        List.of(COMMANDER_FCM_TOKEN, FIELD_COMMANDER_FCM_TOKEN),
        startedAt,
        completedAt);
  }

  @Test
  @DisplayName("지원 요청을 받을 계정이 없으면, DB에는 빈 수신자 목록을 저장하고 이벤트에서는 생략하며 FCM은 보내지 않는다")
  void createMarker_withoutNotificationRecipients_savesEmptyListsWithoutSendingFcm()
      throws Exception {
    // given: 지원 요청을 작성할 일반 대원만 남기고 지휘 계정·현장 지휘관의 배정을 해제한다.
    jdbcTemplate.update(
        "UPDATE incident_assignment SET revoked_at = NOW() WHERE incident_id = ? AND account_id IN (?, ?)",
        INCIDENT_ID,
        PRECINCT_COMMANDER_ID,
        SUPPORT_TEAM_ID);
    MarkerCreateServiceRequest request = createRequest("SUPPORT_REQUEST", "DRONE");

    // when: 수신자가 없는 상태에서 지원 요청 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 알림 자체는 남기되, 빈 수신자 목록의 저장·전달 규칙을 구분한다.
    JsonNode snapshot =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT notification_payload::text FROM marker_notification WHERE marker_id = ?",
                String.class,
                response.getId()));
    assertThat(snapshot.get("recipientAccountIds")).isEqualTo(objectMapper.createArrayNode());
    assertThat(snapshot.get("recipientPolicePhoneIds")).isEqualTo(objectMapper.createArrayNode());
    ObjectNode expectedEvent = snapshot.deepCopy();
    expectedEvent.remove(List.of("recipientAccountIds", "recipientPolicePhoneIds"));
    assertThat(readEventPayload("SUPPORT_REQUEST_CREATED")).isEqualTo(expectedEvent);
    assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
  }

  @Test
  @DisplayName("발견 마커를 생성하면, 사건에 배정된 계정·업무폰 대상 알림을 저장하고 커밋 후 FCM으로 전달한다")
  void createMarker_personFound_savesNotificationAndDispatchesAfterCommit() throws Exception {
    // given: 일반 대원·지휘 계정·현장 지휘관이 배정된 사건에서 발견 마커 생성을 요청한다.
    MarkerCreateServiceRequest request = createRequest("PERSON_FOUND", null);
    Instant startedAt = Instant.now();

    // when: 실제 트랜잭션 안에서 생성하며, 커밋 전에는 FCM 전달이 일어나지 않는다.
    MarkerCreateServiceResponse response =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  MarkerCreateServiceResponse created = appMarkerService.create(request);
                  assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
                  return created;
                });
    Instant completedAt = Instant.now();

    // then: 역할에 관계없이 이 사건에 배정된 세 계정과 각 업무폰을 알림 대상으로 저장한다.
    assertNotificationStored(
        response,
        "PERSON_FOUND",
        "PERSON_FOUND",
        "ALL_INCIDENT_ASSIGNED",
        List.of(PRECINCT_TEAM_ID, PRECINCT_COMMANDER_ID, SUPPORT_TEAM_ID),
        List.of(ASSIGNED_POLICE_PHONE_ID, COMMANDER_PHONE_ID, FIELD_COMMANDER_PHONE_ID),
        List.of(TEAM_FCM_TOKEN, COMMANDER_FCM_TOKEN, FIELD_COMMANDER_FCM_TOKEN),
        startedAt,
        completedAt);
  }

  @ParameterizedTest(name = "마커 유형: {0}")
  @CsvSource({"SUPPORT_REQUEST,DRONE,SUPPORT_REQUEST_CREATED", "PERSON_FOUND,,PERSON_FOUND"})
  @DisplayName("사진을 포함해 마커를 생성하면, 별도 알림도 버전 1과 마커의 기록 시각·좌표로 생성한다")
  void createMarker_withPhoto_createsNotificationWithIndependentVersion(
      String markerType, String supportRequestType, String eventType) throws Exception {
    // given: 업로드된 사진과 소수점 아래 7자리인 좌표를 지원 요청·발견 마커에 포함한다.
    MarkerCreateServiceRequest request =
        prepareMarkerRequestWithUploadedPhoto().toBuilder()
            .type(markerType)
            .supportRequestType(supportRequestType)
            .location(
                new GeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9134004"), new BigDecimal("35.1631004"))))
            .build();

    // when: 같은 트랜잭션에서 마커 생성, 사진 첨부, 알림 생성을 마친 뒤 커밋한다.
    MarkerCreateServiceResponse response =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  MarkerCreateServiceResponse created = appMarkerService.create(request);
                  assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
                  return created;
                });

    // then: 사진을 포함한 마커와 별도 알림은 각각 최초 버전으로 저장되고, FCM은 커밋 후 전달된다.
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(photoMapper.findById(PHOTO_ID).orElseThrow().getStatus())
        .isEqualTo(PhotoStatus.ATTACHED);
    JsonNode notification =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT to_jsonb(n)::text FROM marker_notification n WHERE marker_id = ?",
                String.class,
                MARKER_ID));
    assertThat(notification.path("status").asText()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(notification.path("version").asLong()).isEqualTo(1L);
    JsonNode snapshot = notification.path("notification_payload");
    assertThat(snapshot.path("markerId").asText()).isEqualTo(MARKER_ID.toString());
    assertThat(snapshot.path("markerType").asText()).isEqualTo(markerType);
    assertThat(snapshot.path("status").asText()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(snapshot.path("version").asLong()).isEqualTo(1L);
    assertThat(snapshot.path("clientTs").asText()).isEqualTo(CLIENT_TS.toString());
    assertThat(snapshot.path("locationLabel").asText()).isEqualTo("126.913400,35.163100");
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", eventType);
    assertThat(readEventPayload(eventType)).isEqualTo(snapshot);
    assertThat(fcmDispatcher.getAllDispatches())
        .singleElement()
        .satisfies(
            dispatch -> {
              assertThat(dispatch.eventId()).isEqualTo(readEventId(eventType));
              assertThat(dispatch.payload())
                  .containsEntry("type", eventType)
                  .containsEntry("markerId", MARKER_ID.toString())
                  .containsEntry("status", "SNAPSHOT_CREATED")
                  .containsEntry("version", 1L)
                  .containsEntry("clientTs", CLIENT_TS.toString())
                  .containsEntry("locationLabel", "126.913400,35.163100");
            });
  }

  @ParameterizedTest(name = "마커 유형: {0}")
  @CsvSource({"SUPPORT_REQUEST,DRONE,SUPPORT_REQUEST_CREATED", "PERSON_FOUND,,PERSON_FOUND"})
  @DisplayName("마커 생성 트랜잭션이 롤백되면, 마커·알림·이벤트를 남기지 않고 FCM도 전달하지 않는다")
  void createMarker_transactionRolledBack_discardsChangesWithoutSendingFcm(
      String markerType, String supportRequestType, String eventType) {
    // given: 유효한 수신 토큰이 있고, 지원 요청 또는 발견 마커를 생성할 수 있다.
    MarkerCreateServiceRequest request =
        createRequest(markerType, supportRequestType).toBuilder().id(MARKER_ID).build();

    // when: 마커와 알림을 저장한 뒤, 같은 트랜잭션을 커밋하지 않고 롤백한다.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              appMarkerService.create(request);
              assertThat(readMarkerIds()).containsExactly(MARKER_ID);
              assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", eventType);
              assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
              status.setRollbackOnly();
            });

    // then: 저장과 요청 처리 기록을 되돌리고, 예약했던 외부 FCM 전달도 실행하지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker_notification WHERE marker_id = ?",
                Integer.class,
                MARKER_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
    assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
  }

  @ParameterizedTest(name = "마커 유형: {0}")
  @CsvSource({"SUPPORT_REQUEST,DRONE", "PERSON_FOUND,"})
  @DisplayName("알림을 만드는 마커 요청의 멱등성 키가 비어 있으면, 저장하거나 FCM으로 전달하지 않는다")
  void createMarker_blankIdempotencyKey_rejectsWithoutSavingOrDispatching(
      String markerType, String supportRequestType) {
    // given: 지원 요청·발견 마커를 만들지만 필수 요청 키는 비어 있다.
    MarkerCreateServiceRequest request =
        createRequest(markerType, supportRequestType).toBuilder()
            .id(MARKER_ID)
            .authentication(authentication)
            .idempotencyKey("")
            .build();

    // when: 실제 앱 서비스가 빈 멱등성 키를 거부한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 마커·알림·이벤트를 저장하지 않고 FCM도 전달하지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker_notification WHERE marker_id = ?",
                Integer.class,
                MARKER_ID))
        .isZero();
    assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
  }

  @ParameterizedTest(name = "마커 유형: {0}")
  @CsvSource({"SUPPORT_REQUEST,DRONE,SUPPORT_REQUEST_CREATED", "PERSON_FOUND,,PERSON_FOUND"})
  @DisplayName("커밋 후 FCM 전달이 실패해도, 저장된 마커·알림·이벤트와 요청 처리 결과는 유지한다")
  void createMarker_fcmDeliveryFails_preservesMarkerNotificationAndEvents(
      String markerType, String supportRequestType, String eventType) {
    // given: 마커 저장은 허용하고 외부 FCM 전달만 실패하게 한다.
    MarkerCreateServiceRequest request = createRequest(markerType, supportRequestType);

    // when: 커밋 전에 저장된 이벤트 ID를 확인해 해당 FCM 전달에 실패를 주입한다.
    MarkerCreateServiceResponse response =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  MarkerCreateServiceResponse created = appMarkerService.create(request);
                  fcmDispatcher.injectFailureFor(readEventId(eventType));
                  return created;
                });

    // then: FCM 성공 기록은 없지만 실제 DB의 마커·알림·이벤트는 삭제되거나 롤백되지 않는다.
    assertThat(fcmDispatcher.getAllDispatches()).isEmpty();
    assertThat(readMarkerIds()).containsExactly(response.getId());
    Marker marker = markerMapper.findById(response.getId()).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker_notification WHERE marker_id = ?",
                Integer.class,
                response.getId()))
        .isEqualTo(1);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", eventType);
    assertCompletedRequest();
  }

  @Test
  @DisplayName("같은 멱등성 키와 본문으로 지원 요청을 다시 보내면, 기존 응답을 반환하고 마커·알림·이벤트를 추가로 만들지 않는다")
  void createSupportRequestMarker_sameKeyAndBody_returnsStoredResponseWithoutDuplicates()
      throws Exception {
    // given: 마커 ID를 지정하지 않은 지원 요청을 처리해 응답과 알림이 이미 저장돼 있다.
    MarkerCreateServiceResponse firstResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));

    // when: 같은 키와 본문으로 지원 요청을 다시 보낸다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));

    // then: 저장된 응답을 반환하고 마커·알림·각 이벤트를 하나씩만 유지한다.
    assertStoredResponseWithoutDuplicates(
        firstResponse, repeatedResponse, "SUPPORT_REQUEST_CREATED");
  }

  @Test
  @DisplayName("JSON 기준으로 기록된 지원 요청을 다시 보내면, 기존 응답을 반환하고 중복 생성하지 않는다")
  void createSupportRequestMarker_jsonRequestHash_returnsStoredResponseWithoutDuplicates()
      throws Exception {
    // given: 새 요청의 처리 기록은 DTO 문자열이 아닌 JSON 본문 기준으로 저장한다.
    MarkerCreateServiceResponse firstResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));
    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT request_body_hash FROM idempotency_record WHERE idempotency_key = ? AND"
                        + " request_path = '/api/markers' AND request_method = 'POST'",
                    String.class,
                    IDEMPOTENCY_KEY)
                .trim())
        .isEqualTo("4b70226afcae3e9595f4a460af5bb4835a1fa284309945c1acf3bc2b74599ce6");

    // when: 같은 키와 본문을 가진 새 요청 객체로 다시 생성한다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));

    // then: 기존 마커·알림·이벤트와 응답을 재사용한다.
    assertStoredResponseWithoutDuplicates(
        firstResponse, repeatedResponse, "SUPPORT_REQUEST_CREATED");
  }

  @Test
  @DisplayName("변경 전 방식으로 기록된 지원 요청을 다시 보내면, 기존 응답을 반환하고 중복 생성하지 않는다")
  void createSupportRequestMarker_legacyRequestHash_returnsStoredResponseWithoutDuplicates()
      throws Exception {
    // given: 변경 전 서버가 같은 요청을 처리하고 저장한 해시가 남아 있다.
    MarkerCreateServiceResponse firstResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));
    jdbcTemplate.update(
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ? AND"
            + " request_path = '/api/markers' AND request_method = 'POST'",
        "05ce05736900922dd9ab918703177999876d4489f6ccc9c682531b54246864a2",
        IDEMPOTENCY_KEY);

    // when: 변경 후 서버에 같은 키와 본문으로 재전송한다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));

    // then: 기존 응답을 돌려주고 저장된 마커·알림·이벤트를 유지한다.
    assertStoredResponseWithoutDuplicates(
        firstResponse, repeatedResponse, "SUPPORT_REQUEST_CREATED");
  }

  @Test
  @DisplayName("변경 전 요청 키에 다른 본문을 보내면, 요청을 거부하고 기존 마커·알림·이벤트를 유지한다")
  void createSupportRequestMarker_legacyKeyWithDifferentBody_rejectsWithoutDuplicates() {
    // given: 과거 해시로 저장된 요청과 같은 키를 사용하되 메모는 다르다.
    MarkerCreateServiceResponse firstResponse =
        appMarkerService.create(createRequest("SUPPORT_REQUEST", "DRONE"));
    jdbcTemplate.update(
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ? AND"
            + " request_path = '/api/markers' AND request_method = 'POST'",
        "05ce05736900922dd9ab918703177999876d4489f6ccc9c682531b54246864a2",
        IDEMPOTENCY_KEY);
    MarkerCreateServiceRequest changedRequest =
        createRequest("SUPPORT_REQUEST", "DRONE").toBuilder().memo("changed memo").build();

    // when: 같은 키로 다른 본문을 전송한다.
    assertThatThrownBy(() -> appMarkerService.create(changedRequest))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 기존 마커를 덮어쓰거나 이벤트·알림을 추가하지 않는다.
    assertThat(readMarkerIds()).containsExactly(firstResponse.getId());
    assertThat(markerMapper.findById(firstResponse.getId()).orElseThrow().getMemo())
        .isEqualTo(MARKER_MEMO);
    assertThat(readEventTypes())
        .containsExactlyInAnyOrder("MARKER_CREATED", "SUPPORT_REQUEST_CREATED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker_notification WHERE marker_id = ?",
                Integer.class,
                firstResponse.getId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("같은 멱등성 키와 본문으로 발견 요청을 다시 보내면, 기존 응답을 반환하고 마커·알림·이벤트를 추가로 만들지 않는다")
  void createPersonFoundMarker_sameKeyAndBody_returnsStoredResponseWithoutDuplicates()
      throws Exception {
    // given: 마커 ID를 지정하지 않은 발견 요청을 처리해 응답과 알림이 이미 저장돼 있다.
    MarkerCreateServiceResponse firstResponse =
        appMarkerService.create(createRequest("PERSON_FOUND", null));

    // when: 같은 키와 본문으로 발견 요청을 다시 보낸다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(createRequest("PERSON_FOUND", null));

    // then: 저장된 응답을 반환하고 마커·알림·각 이벤트를 하나씩만 유지한다.
    assertStoredResponseWithoutDuplicates(firstResponse, repeatedResponse, "PERSON_FOUND");
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("사진을 포함한 처리 완료 요청을 재전송하면, 사진을 다시 첨부하지 않고 기존 응답을 반환한다")
  void createMarker_sameRequestWithPhoto_returnsStoredAttachmentResponse(String storedHashFormat) {
    // given: 사진 첨부까지 완료된 요청을 현재 또는 과거 방식의 해시로 기록한다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    MarkerCreateServiceResponse firstResponse = appMarkerService.create(request);
    assertThat(readRequestBodyHash())
        .isEqualTo("ab6dacb1e5226f2068add5326859e21e5dfa9e456bcf1f65cf65a32ebc3ed0a1");
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash("d11455f257e00d3b325694535c9593ff511458fe5f506c911d287fe76ba4f55d");
    }

    // when: 이미 첨부된 사진을 포함한 같은 요청을 재전송한다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(request.toBuilder().build());

    // then: 최초 마커와 첨부 사진의 버전, 생성 이벤트 하나를 그대로 유지한다.
    assertThat(repeatedResponse).usingRecursiveComparison().isEqualTo(firstResponse);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(photoMapper.findById(PHOTO_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
  }

  @Test
  @DisplayName("같은 단서 마커 생성 요청을 재전송하면, 기존 응답을 반환하고 마커·이벤트를 추가하지 않는다")
  void createMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    // given: 마커 ID를 지정하지 않은 생성 요청을 한 번 처리했다.
    MarkerCreateServiceResponse first = appMarkerService.create(createRequest("CLUE", null));

    // when: 같은 키와 본문으로 마커 생성을 다시 요청한다.
    MarkerCreateServiceResponse repeated = appMarkerService.create(createRequest("CLUE", null));

    // then: 최초 응답과 마커 하나·생성 이벤트 하나만 유지한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(readMarkerIds()).containsExactly(first.getId());
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("처리한 생성 요청 키에 다른 본문을 보내면, 기존 마커와 이벤트를 유지하고 거부한다")
  void createMarker_sameKeyWithDifferentBody_rejectsWithoutChangingMarkerOrEvents() {
    // given: 단서 마커 생성에 사용한 키로 메모와 유형을 바꿔 보낸다.
    MarkerCreateServiceResponse first = appMarkerService.create(createRequest("CLUE", null));
    MarkerCreateServiceRequest changed =
        createRequest("NOTE", null).toBuilder().memo("changed memo").build();

    // when: 같은 키로 다른 본문의 생성을 요청한다.
    assertThatThrownBy(() -> appMarkerService.create(changed))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 최초 마커의 내용과 버전, 생성 이벤트가 유지된다.
    assertThat(readMarkerIds()).containsExactly(first.getId());
    Marker saved = markerMapper.findById(first.getId()).orElseThrow();
    assertThat(saved.getMarkerType()).isEqualTo("CLUE");
    assertThat(saved.getMemo()).isEqualTo(MARKER_MEMO);
    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    assertCompletedRequest();
  }

  private MarkerCreateServiceRequest prepareMarkerRequestWithUploadedPhoto() {
    MarkerCreatePhotoServiceRequest photo = prepareUploadedPhoto(PHOTO_ID);
    return MarkerCreateServiceRequest.builder()
        .id(MARKER_ID)
        .incidentId(INCIDENT_ID)
        .opId(OP1_ID)
        .type("CLUE")
        .location(createMarkerLocation())
        .memo("photo evidence")
        .clientTs(CLIENT_TS)
        .clockOffsetMs(0L)
        .photos(List.of(photo))
        .authentication(authentication)
        .idempotencyKey(IDEMPOTENCY_KEY)
        .build();
  }

  private MarkerCreatePhotoServiceRequest prepareUploadedPhoto(UUID photoId) {
    String objectKey = "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + photoId + ".jpg";
    objectStorage.generatePresignedUrl(
        objectKey, "image/jpeg", 1_048_576L, CHECKSUM_SHA256, Duration.ofMinutes(15));
    objectStorage.simulateUpload(objectKey);
    photoMapper.upsert(
        MarkerPhoto.builder()
            .id(photoId)
            .markerId(MARKER_ID)
            .objectKey(objectKey)
            .contentType("image/jpeg")
            .sizeBytes(1_048_576L)
            .checksumSha256(CHECKSUM_SHA256)
            .uploadUrlExpiresAt(Instant.now().plus(Duration.ofMinutes(15)))
            .build());
    return MarkerCreatePhotoServiceRequest.builder()
        .photoId(photoId)
        .sizeBytes(1_048_576L)
        .contentType("image/jpeg")
        .width(640)
        .height(480)
        .checksumSha256(CHECKSUM_SHA256)
        .build();
  }

  private UUID assignAccount(UUID accountId, String incidentRole) {
    UUID assignmentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, NOW(), NOW(), NOW())
        """,
        assignmentId,
        INCIDENT_ID,
        accountId,
        incidentRole);
    return assignmentId;
  }

  private MarkerCreateServiceRequest createRequest(String markerType, String supportRequestType) {
    return MarkerCreateServiceRequest.builder()
        .incidentId(INCIDENT_ID)
        .opId(OP1_ID)
        .type(markerType)
        .location(createMarkerLocation())
        .supportRequestType(supportRequestType)
        .memo(MARKER_MEMO)
        .clientTs(CLIENT_TS)
        .clockOffsetMs(0L)
        .authentication(authentication)
        .idempotencyKey(IDEMPOTENCY_KEY)
        .build();
  }

  private static GeoJsonPoint createMarkerLocation() {
    return new GeoJsonPoint(
        "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100")));
  }

  private List<UUID> readMarkerIds() {
    return jdbcTemplate.queryForList(
        "SELECT id FROM marker WHERE incident_id = ?", UUID.class, INCIDENT_ID);
  }

  private List<String> readEventTypes() {
    return jdbcTemplate.queryForList(
        "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
        String.class,
        INCIDENT_ID);
  }

  private JsonNode readEventPayload(String eventType) throws Exception {
    return objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "SELECT payload::text FROM event_dispatch_job WHERE incident_id = ? AND event_type = ?",
            String.class,
            INCIDENT_ID,
            eventType));
  }

  private void assertNotificationStored(
      MarkerCreateServiceResponse response,
      String markerType,
      String eventType,
      String recipientPolicy,
      List<UUID> expectedAccountIds,
      List<UUID> expectedPhoneIds,
      List<String> expectedFcmTokens,
      Instant startedAt,
      Instant completedAt)
      throws Exception {
    UUID markerId = response.getId();
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(readMarkerIds()).containsExactly(markerId);
    assertThat(markerMapper.findById(markerId).orElseThrow().getMarkerType()).isEqualTo(markerType);

    JsonNode notification =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT to_jsonb(n)::text FROM marker_notification n WHERE marker_id = ?",
                String.class,
                markerId));
    assertThat(notification.path("notification_type").asText()).isEqualTo(eventType);
    assertThat(notification.path("recipient_rule").asText()).isEqualTo(recipientPolicy);
    assertThat(notification.path("status").asText()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(notification.path("version").asLong()).isEqualTo(1L);
    assertThat(objectMapper.convertValue(notification.path("recipient_account_ids"), UUID[].class))
        .containsExactlyInAnyOrderElementsOf(expectedAccountIds);
    assertThat(
            objectMapper.convertValue(
                notification.path("recipient_police_phone_ids"), UUID[].class))
        .containsExactlyInAnyOrderElementsOf(expectedPhoneIds);
    assertThat(OffsetDateTime.parse(notification.path("created_at").asText()).toInstant())
        .isBetween(startedAt.truncatedTo(ChronoUnit.MICROS), completedAt);

    JsonNode snapshot = notification.path("notification_payload");
    assertThat(snapshot.path("id").asText()).isEqualTo(notification.path("id").asText());
    assertThat(snapshot.path("markerId").asText()).isEqualTo(markerId.toString());
    assertThat(snapshot.path("incidentId").asText()).isEqualTo(INCIDENT_ID.toString());
    assertThat(snapshot.path("opId").asText()).isEqualTo(OP1_ID.toString());
    assertThat(snapshot.path("policePhoneId").asText())
        .isEqualTo(ASSIGNED_POLICE_PHONE_ID.toString());
    // 알림을 기록한 업무폰의 표시 이름도 실제 DB 조회 결과를 사용한다.
    String expectedPolicePhoneName =
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM police_phone WHERE id = ?",
            String.class,
            ASSIGNED_POLICE_PHONE_ID);
    assertThat(expectedPolicePhoneName).isNotBlank();
    assertThat(snapshot.path("policePhoneName").asText()).isEqualTo(expectedPolicePhoneName);
    assertThat(snapshot.path("status").asText()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(snapshot.path("version").asLong()).isEqualTo(1L);
    assertThat(snapshot.path("type").asText()).isEqualTo(eventType);
    assertThat(snapshot.path("markerType").asText()).isEqualTo(markerType);
    assertThat(snapshot.path("recipientPolicy").asText()).isEqualTo(recipientPolicy);
    assertThat(objectMapper.convertValue(snapshot.path("recipientAccountIds"), UUID[].class))
        .containsExactlyInAnyOrderElementsOf(expectedAccountIds);
    assertThat(objectMapper.convertValue(snapshot.path("recipientPolicePhoneIds"), UUID[].class))
        .containsExactlyInAnyOrderElementsOf(expectedPhoneIds);
    assertThat(snapshot.path("locationLabel").asText()).isEqualTo("126.913400,35.163100");
    assertThat(snapshot.path("clientTs").asText()).isEqualTo(CLIENT_TS.toString());
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", eventType);
    assertThat(readEventPayload(eventType)).isEqualTo(snapshot);

    // 실제 DB에서 조회한 토큰으로 전달하며, FCM에서도 같은 알림·마커·기록 시각을 사용한다.
    assertThat(fcmDispatcher.getAllDispatches())
        .singleElement()
        .satisfies(
            dispatch -> {
              assertThat(dispatch.eventId()).isEqualTo(readEventId(eventType));
              assertThat(dispatch.recipients())
                  .containsExactlyInAnyOrderElementsOf(expectedFcmTokens);
              assertThat(dispatch.recipientAccountIds())
                  .containsExactlyInAnyOrderElementsOf(
                      expectedAccountIds.stream().map(UUID::toString).toList());
              assertThat(dispatch.recipientPolicePhoneIds())
                  .containsExactlyInAnyOrderElementsOf(
                      expectedPhoneIds.stream().map(UUID::toString).toList());
              assertThat(dispatch.payload())
                  .containsOnlyKeys(
                      "type",
                      "id",
                      "markerId",
                      "incidentId",
                      "opId",
                      "policePhoneId",
                      "status",
                      "version",
                      "recipientPolicy",
                      "recipientAccountIds",
                      "recipientPolicePhoneIds",
                      "markerType",
                      "locationLabel",
                      "clientTs")
                  .containsEntry("type", eventType)
                  .containsEntry("id", notification.path("id").asText())
                  .containsEntry("markerId", markerId.toString())
                  .containsEntry("incidentId", INCIDENT_ID.toString())
                  .containsEntry("opId", OP1_ID.toString())
                  .containsEntry("policePhoneId", ASSIGNED_POLICE_PHONE_ID.toString())
                  .containsEntry("status", "SNAPSHOT_CREATED")
                  .containsEntry("version", 1L)
                  .containsEntry("recipientPolicy", recipientPolicy)
                  .containsEntry("markerType", markerType)
                  .containsEntry("locationLabel", "126.913400,35.163100")
                  .containsEntry("clientTs", CLIENT_TS.toString());
            });
  }

  private String readEventId(String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT event_id::text FROM event_dispatch_job WHERE incident_id = ? AND event_type = ?",
        String.class,
        INCIDENT_ID,
        eventType);
  }

  private void assertStoredResponseWithoutDuplicates(
      MarkerCreateServiceResponse firstResponse,
      MarkerCreateServiceResponse repeatedResponse,
      String notificationEventType)
      throws Exception {
    UUID markerId = firstResponse.getId();
    assertThat(repeatedResponse).usingRecursiveComparison().isEqualTo(firstResponse);
    assertThat(readMarkerIds()).containsExactly(markerId);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT n.marker_id FROM marker_notification n JOIN marker m ON m.id = n.marker_id"
                    + " WHERE m.incident_id = ?",
                UUID.class,
                INCIDENT_ID))
        .containsExactly(markerId);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", notificationEventType);

    // 같은 생성 요청을 재전송해도 외부 FCM 전달은 최초 한 번만 수행한다.
    assertThat(fcmDispatcher.getAllDispatches())
        .singleElement()
        .satisfies(
            dispatch -> {
              assertThat(dispatch.eventId()).isEqualTo(readEventId(notificationEventType));
              assertThat(dispatch.payload()).containsEntry("markerId", markerId.toString());
            });

    JsonNode storedResponse =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT to_jsonb(r)::text FROM idempotency_record r WHERE idempotency_key = ? AND"
                    + " request_path = '/api/markers' AND request_method = 'POST'",
                String.class,
                IDEMPOTENCY_KEY));
    assertThat(storedResponse.path("idempotency_status").asText()).isEqualTo("COMPLETED");
    assertThat(storedResponse.path("response_status_code").asInt()).isEqualTo(201);
    assertThat(storedResponse.path("result_entity_id").asText()).isEqualTo(markerId.toString());
    assertThat(
            objectMapper.readValue(
                storedResponse.path("response_body_json").asText(),
                MarkerCreateServiceResponse.class))
        .usingRecursiveComparison()
        .isEqualTo(firstResponse);
  }

  @Test
  @DisplayName("마커를 수정하면, 변경된 내용과 증가한 버전 및 수정 이벤트를 함께 저장한다")
  void updateMarker_currentVersion_savesChangesAndUpdatedEvent() throws Exception {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 현재 버전과 변경할 좌표·메모·유형을 준비한다.
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder()
            .location(
                new GeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9137007"), new BigDecimal("35.1634007"))))
            .memo("updated clue memo")
            .type("NOTE")
            .build();
    Instant startedAt = Instant.now();

    // when: 실제 권한 검사, 도메인 변경, SQL과 이벤트 저장을 실행한다.
    MarkerMutationServiceResponse response = appMarkerService.update(request);
    Instant completedAt = Instant.now();

    // then: 응답·마커·이벤트에 같은 수정 내용과 버전이 남는다.
    assertResponse(response, "UPDATED");
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getMarkerType()).isEqualTo("NOTE");
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getLocation().getSRID()).isEqualTo(4326);
    assertThat(saved.getLocation().getX()).isEqualTo(126.913701);
    assertThat(saved.getLocation().getY()).isEqualTo(35.163401);
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(saved.getStatus()).isEqualTo("UPDATED");
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    JsonNode event = readMutationEventPayload();
    assertEvent(event, "UPDATED", ASSIGNED_POLICE_PHONE_ID);
    assertThat(event.path("type").asText()).isEqualTo("NOTE");
    assertThat(event.path("location").path("coordinates").get(0).decimalValue())
        .isEqualByComparingTo("126.913701");
    assertThat(event.path("location").path("coordinates").get(1).decimalValue())
        .isEqualByComparingTo("35.163401");
    assertThat(Instant.parse(event.path("serverTs").asText())).isBetween(startedAt, completedAt);
  }

  @Test
  @DisplayName("같은 작성 계정이 다른 업무폰에서 마커를 수정하면, 수정 이벤트에 요청한 업무폰을 기록한다")
  void updateMarker_sameAuthorOnAnotherPhone_savesChangesAndEventWithRequestPhone()
      throws Exception {
    // given: 마커를 기록한 계정이 다른 업무폰에서 같은 마커의 수정을 요청한다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder()
            .authentication(createAuthentication("APP", REGISTERED_UNASSIGNED_POLICE_PHONE_ID))
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

    // when: 실제 서비스의 권한 검사와 마커 수정을 실행한다.
    MarkerMutationServiceResponse response = appMarkerService.update(request);

    // then: 최초 작성 계정·업무폰은 유지하고 이번 수정에 사용한 업무폰을 이벤트에 기록한다.
    assertResponse(response, "UPDATED");
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getStatus()).isEqualTo("UPDATED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(saved.getCreatedByAccountId()).isEqualTo(PRECINCT_TEAM_ID);
    assertThat(saved.getPolicePhoneId()).isEqualTo(ASSIGNED_POLICE_PHONE_ID);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertEvent(readMutationEventPayload(), "UPDATED", REGISTERED_UNASSIGNED_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("현장 마커를 삭제하면, 행은 남기고 삭제 상태·다음 버전·삭제 이벤트를 저장한다")
  void deleteMarker_currentVersion_savesDeletedStatusAndEvent() throws Exception {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 버전이 1인 현장 마커와 작성 계정의 요청이다.
    // when: 현재 버전으로 삭제한다.
    MarkerMutationServiceResponse response = appMarkerService.delete(deleteRequest());

    // then: 기존 내용은 남기고 삭제 결과를 응답과 이벤트에 기록한다.
    assertResponse(response, "DELETED");
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getMemo()).isEqualTo("initial clue");
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    JsonNode event = readMutationEventPayload();
    assertEvent(event, "DELETED", ASSIGNED_POLICE_PHONE_ID);
    assertThat(event.has("type")).isFalse();
    assertThat(event.has("location")).isFalse();
  }

  @Test
  @DisplayName("수정할 메모가 2,000자를 넘으면, 마커와 이벤트를 변경하지 않는다")
  void updateMarker_memoTooLong_preservesMarkerAndEvents() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 유형 변경과 허용 길이를 넘는 메모를 요청한다.
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder().type("NOTE").memo("m".repeat(2001)).build();

    // when & then: 검증 오류로 전체 요청을 거부한다.
    assertThatThrownBy(() -> appMarkerService.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("요청 버전이 저장된 버전과 다르면, 마커와 이벤트를 변경하지 않는다")
  void updateMarker_versionMismatch_preservesMarkerAndEvents() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 저장된 버전은 1인데 요청 버전은 99이다.
    MarkerUpdateServiceRequest request = updateRequest().toBuilder().version(99L).build();

    // when & then: 다른 버전의 수정 요청을 거부한다.
    assertThatThrownBy(() -> appMarkerService.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("채널에서 변경할 수 없는 마커이면, 수정과 삭제를 모두 거부한다")
  void changeMarker_disallowedSource_preservesMarkerAndEvents() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 앱에서 기준 마커를 변경하도록 요청한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MUTATION_MARKER_ID);
    insertMarker(MarkerSource.MOCK_SEED, ASSIGNED_POLICE_PHONE_ID);
    SuriMapAuthentication requestAuthentication =
        createAuthentication("APP", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 실제 DB의 마커 출처로 수정·삭제 권한을 판단한다.
    assertThatThrownBy(
            () ->
                appMarkerService.update(
                    updateRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(
            () ->
                appMarkerService.delete(
                    deleteRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("업무폰 정보가 없으면, 앱의 수정·삭제 요청을 마커 조회보다 먼저 거부한다")
  void changeMarker_missingPolicePhone_rejectsBeforeLookingUpMarker() {
    // given: 요청할 마커가 저장되어 있지 않고, 앱 인증에도 업무폰 정보가 없다.
    SuriMapAuthentication requestAuthentication = createAuthentication("APP", null);

    // when: 마커를 수정하거나 삭제하려 한다.
    assertThatThrownBy(
            () ->
                appMarkerService.update(
                    updateRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("police_phone_required", HttpStatus.BAD_REQUEST);
    assertThatThrownBy(
            () ->
                appMarkerService.delete(
                    deleteRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("police_phone_required", HttpStatus.BAD_REQUEST);

    // then: 마커 없음 오류보다 업무폰 누락 오류가 우선하며, 기록도 남지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
  }

  @Test
  @DisplayName("이미 삭제된 마커에 새 요청을 보내면, 앱에서 수정·삭제를 거부하고 기록을 유지한다")
  void changeMarker_deletedMarker_rejectsNewRequestWithoutChangingRecords() {
    // given: 삭제 상태의 현장 마커에 새로운 요청 키로 변경을 요청한다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    jdbcTemplate.update("UPDATE marker SET status = 'DELETED' WHERE id = ?", MUTATION_MARKER_ID);

    // when & then: 버전 충돌 검사에 앞서 기존 권한 오류로 수정·삭제를 거부한다.
    assertThatThrownBy(() -> appMarkerService.update(updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_access_denied", HttpStatus.FORBIDDEN);
    assertThatThrownBy(() -> appMarkerService.delete(deleteRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_access_denied", HttpStatus.FORBIDDEN);
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("다른 계정이 기록한 현장 마커이면, 앱에서 수정과 삭제를 거부한다")
  void changeMarker_differentAuthor_preservesMarkerAndEvents() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 요청 계정과 다른 계정이 마커를 기록했다.
    jdbcTemplate.update(
        "UPDATE marker SET created_by_account_id = ? WHERE id = ?",
        com.surimap.account.AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
        MUTATION_MARKER_ID);

    // when & then: 같은 사건에 배정되어 있어도 작성자가 아니면 거부한다.
    assertThatThrownBy(() -> appMarkerService.update(updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(() -> appMarkerService.delete(deleteRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @ParameterizedTest(name = "마커 유형: {0}")
  @CsvSource({"CLUE,", "SUPPORT_REQUEST,DRONE"})
  @DisplayName("종료된 사건에 마커 생성을 요청하면, 마커·알림·이벤트를 저장하지 않고 거부한다")
  void createMarker_closedIncident_rejectsWithoutSavingMarkerNotificationOrEvent(
      String markerType, String supportRequestType) {
    // given: 사건이 종료되었고 단서 또는 지원 요청 마커를 새로 기록하려 한다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);
    MarkerCreateServiceRequest request =
        createRequest(markerType, supportRequestType).toBuilder().id(MARKER_ID).build();

    // when: 실제 서비스의 사건 상태 조회로 생성 요청을 거부한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_closed", HttpStatus.CONFLICT);

    // then: 마커와 지원 요청 알림·이벤트가 생기지 않고 요청 키도 남기지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marker_notification WHERE marker_id = ?",
                Integer.class,
                MARKER_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
  }

  @Test
  @DisplayName("사건이 종료되었으면, 마커 수정과 삭제를 거부한다")
  void changeMarker_closedIncident_preservesMarkerAndEvents() {
    // given: 마커가 속한 사건이 종료되어 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);

    // when & then: 수정·삭제 모두 사건 종료 오류를 반환한다.
    assertThatThrownBy(() -> appMarkerService.update(updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_closed", HttpStatus.CONFLICT);
    assertThatThrownBy(() -> appMarkerService.delete(deleteRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_closed", HttpStatus.CONFLICT);
    assertUnchangedMarker();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                IDEMPOTENCY_KEY))
        .isZero();
  }

  @Test
  @DisplayName("사건이 존재하지 않으면, 차수 검사보다 먼저 접근을 거부하고 마커·이벤트·요청 기록을 남기지 않는다")
  void createMarker_missingIncident_rejectsAccessWithoutSavingRecords() {
    // given: 실제 DB에 없는 사건 ID로 마커 생성을 요청한다.
    MarkerCreateServiceRequest request =
        createRequest("CLUE", null).toBuilder().incidentId(UUID.randomUUID()).build();

    // when & then: 빈 사건 조회 결과를 허용하지 않고 기존 접근 거부 오류로 처리한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INCIDENT_ACCESS_DENIED);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM marker WHERE incident_id = ?", UUID.class, request.getIncidentId()))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                request.getIncidentId()))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("사건 종료와 현재 수색 차수 부재가 겹치면, 마커 생성은 사건 종료 오류를 먼저 반환한다")
  void createMarker_closedIncidentWithoutCurrentOp_rejectsIncidentBeforeOp() {
    // given: 사건이 종료되었고 활성 수색 차수도 없다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE incident_id = ?",
        INCIDENT_ID);
    MarkerCreateServiceRequest request = createRequest("CLUE", null);

    // when & then: 조회 결과를 한 번에 전달해도 기존 생성 검사의 오류 순서를 유지한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INCIDENT_CLOSED);
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("사건 배정이 유지되면, 현재 수색 차수와 근무교대가 끝나도 작성자는 기존 마커를 수정할 수 있다")
  void updateMarker_opAndDutyShiftEnded_allowsAssignedAuthorToUpdate() {
    // given: 사건은 열려 있고 작성자 배정은 유효하지만 수색 차수와 근무교대는 끝났다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE id = ?", DUTY_SHIFT_ID);
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE incident_id = ?",
        INCIDENT_ID);

    // when: 새 마커를 생성하는 것이 아니라 기존 마커를 수정한다.
    MarkerMutationServiceResponse response = appMarkerService.update(updateRequest());

    // then: 생성에만 필요한 현재 차수·근무 조건을 수정 권한에 추가하지 않는다.
    assertResponse(response, "UPDATED");
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getMemo())
        .isEqualTo("updated clue memo");
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
  }

  @Test
  @DisplayName("같은 수정 요청을 재전송하면, 저장된 응답을 반환하고 중복 변경하지 않는다")
  void updateMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    // given: 수정이 한 번 완료된 요청이다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerMutationServiceResponse first = appMarkerService.update(updateRequest());
    assertThat(readRequestBodyHash())
        .isEqualTo("92b85e147eb6e41bcdd56e6332bb3c00423ffd391bf11a9f91eea2aa47749235");

    // when: 같은 키와 본문으로 다시 요청한다.
    MarkerMutationServiceResponse repeated = appMarkerService.update(updateRequest());

    // then: DB 버전과 이벤트는 한 번만 증가한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("과거 방식으로 기록된 수정 요청을 재전송하면, 기존 응답을 반환하고 중복 수정하지 않는다")
  void updateMarker_legacyRequestHash_returnsStoredResponseWithoutDuplicates() {
    // given: 변경 전 서버가 수정 요청을 처리하고 저장한 해시와 응답이 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerMutationServiceResponse first = appMarkerService.update(updateRequest());
    replaceStoredRequestHash(LEGACY_UPDATE_REQUEST_HASH);

    // when: 같은 키와 본문을 재전송한다.
    MarkerMutationServiceResponse repeated = appMarkerService.update(updateRequest());

    // then: 저장된 응답을 반환하고 수정 내용·버전·이벤트를 그대로 유지한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("좌표가 포함된 과거 수정 요청을 재전송하면, 기존 응답을 반환하고 좌표·이벤트를 중복 변경하지 않는다")
  void updateMarker_legacyRequestWithLocation_returnsStoredResponseWithoutDuplicates() {
    // given: 좌표만 수정한 요청의 과거 해시와 응답이 남아 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder().location(createMarkerLocation()).memo(null).build();
    MarkerMutationServiceResponse first = appMarkerService.update(request);
    replaceStoredRequestHash("7e867860b0c2c34bd7ad90e263ecc88d13d92d1a60e1c42d9c032ab2f79a85d6");

    // when: 같은 좌표가 담긴 요청을 다시 보낸다.
    MarkerMutationServiceResponse repeated = appMarkerService.update(request);

    // then: 과거 좌표 표현을 인식하고 마커·이벤트는 한 번만 변경된다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getLocation().getX()).isEqualTo(126.9134);
    assertThat(saved.getLocation().getY()).isEqualTo(35.1631);
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertCompletedRequest();
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("처리한 수정 요청 키에 다른 메모를 보내면, 기존 마커와 이벤트를 유지하고 거부한다")
  void updateMarker_sameKeyWithDifferentMemo_rejectsWithoutChangingMarkerOrEvents(
      String storedHashFormat) {
    // given: 현재 또는 과거 방식으로 처리된 수정 요청이 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    appMarkerService.update(updateRequest());
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(LEGACY_UPDATE_REQUEST_HASH);
    }

    // when: 같은 키에 다른 메모를 담아 보낸다.
    assertThatThrownBy(
            () -> appMarkerService.update(updateRequest().toBuilder().memo("changed memo").build()))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 최초 수정 결과와 처리 기록이 유지된다.
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("같은 삭제 요청을 재전송하면, 삭제 상태여도 기존 응답을 반환하고 중복 처리하지 않는다")
  void deleteMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    // given: 삭제가 한 번 완료된 요청이다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerMutationServiceResponse first = appMarkerService.delete(deleteRequest());
    assertThat(readRequestBodyHash())
        .isEqualTo("16efc3f737390b1ec618b00b2c14abd9a6d8b07850127d7ff7c7fa53d04370ef");

    // when: 같은 키와 본문으로 다시 삭제를 요청한다.
    MarkerMutationServiceResponse repeated = appMarkerService.delete(deleteRequest());

    // then: 저장된 응답을 반환하고 삭제 이벤트는 하나만 남는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("과거 방식으로 기록된 삭제 요청을 재전송하면, 기존 응답을 반환하고 중복 삭제하지 않는다")
  void deleteMarker_legacyRequestHash_returnsStoredResponseWithoutDuplicates() {
    // given: 이전 서버가 삭제를 처리한 해시와 응답이 남아 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerMutationServiceResponse first = appMarkerService.delete(deleteRequest());
    replaceStoredRequestHash(LEGACY_DELETE_REQUEST_HASH);

    // when: 같은 삭제 요청을 재전송한다.
    MarkerMutationServiceResponse repeated = appMarkerService.delete(deleteRequest());

    // then: 삭제 상태·버전·이벤트를 추가로 변경하지 않는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    assertCompletedRequest();
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("처리한 삭제 요청 키에 다른 사유를 보내면, 기존 마커와 이벤트를 유지하고 거부한다")
  void deleteMarker_sameKeyWithDifferentReason_rejectsWithoutChangingMarkerOrEvents(
      String storedHashFormat) {
    // given: 현재 또는 과거 방식으로 처리된 삭제 요청이 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    appMarkerService.delete(deleteRequest());
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(LEGACY_DELETE_REQUEST_HASH);
    }

    // when: 같은 키에 다른 삭제 사유를 담아 보낸다.
    assertThatThrownBy(
            () ->
                appMarkerService.delete(
                    deleteRequest().toBuilder().reason("changed reason").build()))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 최초 삭제 결과와 처리 기록이 유지된다.
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    assertCompletedRequest();
  }

  @Test
  @DisplayName("이벤트 저장에 실패하면, 마커 수정과 요청 처리 기록도 함께 롤백한다")
  void updateMarker_eventStorageFails_rollsBackMarkerAndRequestRecord() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 테스트 DB에서 이 마커의 수정 이벤트만 저장할 수 없게 한다.
    jdbcTemplate.execute(
        """
        ALTER TABLE event_dispatch_job ADD CONSTRAINT test_marker_update_event_failure
        CHECK (source_entity_id <> '55555555-5555-5555-5555-555555550072'::uuid
               OR event_type <> 'MARKER_UPDATED') NOT VALID
        """);
    try {
      // when & then: 마커 SQL 다음의 이벤트 SQL이 실패해도 일부 변경만 남지 않는다.
      assertThatThrownBy(() -> appMarkerService.update(updateRequest()))
          .isInstanceOf(DataAccessException.class);
      assertUnchangedMarker();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                  Integer.class,
                  IDEMPOTENCY_KEY))
          .isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE event_dispatch_job DROP CONSTRAINT test_marker_update_event_failure");
    }
    // then: 실패 원인이 사라지면 같은 키로 정상 처리할 수 있다.
    assertResponse(appMarkerService.update(updateRequest()), "UPDATED");
  }

  @Test
  @DisplayName("유형과 좌표가 모두 잘못되었으면, 기존과 같이 유형 오류를 먼저 반환한다")
  void updateMarker_invalidTypeAndLocation_rejectsTypeBeforeGeometry() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 존재하지 않는 유형과 Point가 아닌 좌표를 함께 보낸다.
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder()
            .type("UNKNOWN")
            .location(
                new GeoJsonPoint(
                    "LineString", List.of(new BigDecimal("126.9"), new BigDecimal("35.1"))))
            .build();

    // when & then: 좌표 오류보다 앞서 유형의 write_conflict 오류를 반환한다.
    assertThatThrownBy(() -> appMarkerService.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("앱 서비스에 웹 인증으로 수정·삭제를 요청하면, 저장된 응답이 있어도 거부한다")
  void changeMarker_webAuthentication_rejectsBeforeReusingStoredResponse() {
    // given: 앱에서 처리한 수정 응답이 있고, 같은 요청 키를 가진 웹 인증이 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    appMarkerService.update(updateRequest());
    SuriMapAuthentication webAuthentication = createAuthentication("WEB", null);

    // when & then: 저장된 응답을 반환하기 전에 서비스의 채널 경계를 검사한다.
    assertThatThrownBy(
            () ->
                appMarkerService.update(
                    updateRequest().toBuilder()
                        .authentication(webAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("channel_not_allowed");
    assertThatThrownBy(
            () ->
                appMarkerService.delete(
                    deleteRequest().toBuilder()
                        .authentication(webAuthentication)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("channel_not_allowed");
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
  }

  private void replaceStoredRequestHash(String requestBodyHash) {
    jdbcTemplate.update(
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ?",
        requestBodyHash,
        IDEMPOTENCY_KEY);
  }

  private String readRequestBodyHash() {
    return jdbcTemplate
        .queryForObject(
            "SELECT request_body_hash FROM idempotency_record WHERE idempotency_key = ?",
            String.class,
            IDEMPOTENCY_KEY)
        .trim();
  }

  private void assertCompletedRequest() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .containsExactly("COMPLETED");
  }

  private SuriMapAuthentication createAuthentication(String channel, UUID policePhoneId) {
    return new SuriMapAuthentication(PRECINCT_TEAM_ID, channel, policePhoneId);
  }

  private MarkerUpdateServiceRequest updateRequest() {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .memo("updated clue memo")
        .authentication(authentication)
        .idempotencyKey(IDEMPOTENCY_KEY)
        .build();
  }

  private MarkerDeleteServiceRequest deleteRequest() {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .reason("wrong marker")
        .authentication(authentication)
        .idempotencyKey(IDEMPOTENCY_KEY)
        .build();
  }

  private void insertMarker(MarkerSource source, UUID policePhoneId) {
    markerMapper.insertSeed(
        Marker.builder()
            .id(MUTATION_MARKER_ID)
            .incidentId(INCIDENT_ID)
            .operationalPeriodId(OP1_ID)
            .markerType(MarkerType.CLUE)
            .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
            .memo("initial clue")
            .occurredAt(CLIENT_TS)
            .createdByAccountId(PRECINCT_TEAM_ID)
            .policePhoneId(policePhoneId)
            .markerSource(source)
            .status(MarkerStatus.ACTIVE)
            .version(1L)
            .build());
  }

  private void assertResponse(MarkerMutationServiceResponse response, String status) {
    assertThat(response.getId()).isEqualTo(MUTATION_MARKER_ID);
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getVersion()).isEqualTo(2L);
  }

  private void assertUnchangedMarker() {
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(saved.getMemo()).isEqualTo("initial clue");
    assertThat(readEventTypes()).isEmpty();
  }

  private void assertEvent(JsonNode event, String status, UUID policePhoneId) {
    assertThat(event.path("id").asText()).isEqualTo(MUTATION_MARKER_ID.toString());
    assertThat(event.path("incidentId").asText()).isEqualTo(INCIDENT_ID.toString());
    assertThat(event.path("opId").asText()).isEqualTo(OP1_ID.toString());
    assertThat(event.path("version").asLong()).isEqualTo(2L);
    assertThat(event.path("status").asText()).isEqualTo(status);
    if (policePhoneId == null) {
      assertThat(event.path("policePhoneId").isNull()).isTrue();
    } else {
      assertThat(event.path("policePhoneId").asText()).isEqualTo(policePhoneId.toString());
    }
  }

  private JsonNode readMutationEventPayload() throws Exception {
    return objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "SELECT payload::text FROM event_dispatch_job WHERE incident_id = ?",
            String.class,
            INCIDENT_ID));
  }
}
