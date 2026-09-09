package com.surimap.app.service.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_COMMANDER_ID;
import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.account.AccountIdentityCatalog.SUPPORT_TEAM_ID;
import static com.surimap.maparea.fixture.BoundaryAreaFixtures.OVERALL_AREA_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.HARNESS_OVERALL_SEARCH_AREA;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static com.surimap.marker.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.dto.MarkerCreatePhotoRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.repository.PhotoRepository;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;

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
  private static final String MARKER_MEMO = "field clue";
  // 이전 DTO 문자열 형식으로 계산해 둔 값이다. 운영 코드의 해시 함수를 기대값 생성에 사용하지 않는다.
  private static final String LEGACY_UPDATE_REQUEST_HASH =
      "5071d0522889dba1ae1cc2d04aaa4321bc3ec01a86b8cdc1b4eececcfb272a93";
  private static final String LEGACY_DELETE_REQUEST_HASH =
      "ceec70afabd12682afa1687e5c92946715fd369056003191189b1b2bcba3689a";

  @Autowired private AppMarkerService appMarkerService;
  @Autowired private MarkerRepository markerRepository;
  @Autowired private PhotoRepository photoRepository;
  @Autowired private MockObjectStorageAdapter objectStorage;
  @Autowired private ObjectMapper objectMapper;

  private MarkerRequestContext context;

  @BeforeEach
  void setUp() {
    // Testcontainers의 테스트 DB에서 이 사건과 요청 키로 만든 데이터만 정리한다.
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM marker_notification WHERE marker_id IN (SELECT id FROM marker WHERE incident_id = ?)",
        INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM photo WHERE id = ? OR marker_id IN (SELECT id FROM marker WHERE incident_id = ?)",
        PHOTO_ID,
        INCIDENT_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?", IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
    objectStorage.clear();

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
    context =
        new MarkerRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
            IDEMPOTENCY_KEY);
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

    Marker marker = markerRepository.findById(response.getId()).orElseThrow();
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
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9134007"), new BigDecimal("35.1631007"))))
            .memo("precision-over-6dp")
            .clientTs(CLIENT_TS)
            .clockOffsetMs(0L)
            .context(context)
            .build();

    // when: 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 저장된 좌표와 이벤트 좌표가 모두 소수점 아래 6자리로 반올림된다.
    Marker marker = markerRepository.findById(response.getId()).orElseThrow();
    assertThat(marker.getLocation().getX()).isEqualTo(126.913401);
    assertThat(marker.getLocation().getY()).isEqualTo(35.163101);
    JsonNode coordinates = readEventPayload("MARKER_CREATED").path("location").path("coordinates");
    assertThat(coordinates.path(0).asDouble()).isEqualTo(126.913401);
    assertThat(coordinates.path(1).asDouble()).isEqualTo(35.163101);
  }

  @Test
  @DisplayName("마커 생성 요청에 업로드된 사진 정보를 포함하면, 해당 사진을 마커에 첨부한다")
  void createMarker_uploadedPhoto_attachesPhoto() throws Exception {
    // given: 사진 업로드가 완료됐고, 첨부 기한이 남아 있는 사진을 생성 요청에 포함한다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();

    // when: 업로드된 사진과 함께 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 사진은 첨부 상태가 되고, 마커는 사진 첨부가 반영된 버전 2로 저장된다.
    assertThat(response.getId()).isEqualTo(MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("UPDATED");
    assertThat(response.getVersion()).isEqualTo(2L);
    assertThat(response.getPhotos())
        .singleElement()
        .satisfies(
            photo -> {
              assertThat(photo.getPhotoId()).isEqualTo(PHOTO_ID);
              assertThat(photo.getStatus()).isEqualTo("ATTACHED");
            });
    MarkerPhoto photo = photoRepository.findById(PHOTO_ID).orElseThrow();
    assertThat(photo.status()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(photo.version()).isEqualTo(2L);
    assertThat(photo.width()).isEqualTo(640);
    assertThat(photo.height()).isEqualTo(480);
    Marker marker = markerRepository.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("UPDATED");
    assertThat(marker.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", "MARKER_UPDATED");
    JsonNode photoDelta = readEventPayload("MARKER_UPDATED").path("photoDelta");
    assertThat(photoDelta.path("photoId").asText()).isEqualTo(PHOTO_ID.toString());
    assertThat(photoDelta.path("status").asText()).isEqualTo("ATTACHED");
    assertThat(photoDelta.path("version").asLong()).isEqualTo(2L);
  }

  @Test
  @DisplayName("웹 채널에서 마커 생성을 요청하면, 요청을 거부하고 마커·이벤트를 저장하지 않는다")
  void createMarker_webChannel_rejectsWithoutSavingMarkerOrEvent() {
    // given: 앱이 아닌 웹 채널에서 단서 마커 생성을 요청한다.
    MarkerRequestContext webContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "WEB", ASSIGNED_POLICE_PHONE_ID),
            IDEMPOTENCY_KEY);
    MarkerCreateServiceRequest request = createRequest("CLUE", null);

    // when: 웹 채널의 생성 요청을 처리한다.
    assertThatThrownBy(
            () -> appMarkerService.create(request.toBuilder().context(webContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("channel_not_allowed");

    // then: 마커와 생성 이벤트가 DB에 남지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
  }

  @Test
  @DisplayName("경위도 범위를 벗어난 좌표로 마커를 생성하면, 오류를 반환하고 마커·이벤트를 저장하지 않는다")
  void createMarker_invalidLocation_rejectsWithoutSavingMarkerOrEvent() {
    // given: 현재 수색 차수와 앱 권한은 유효하지만 위도 범위를 벗어난 좌표를 보낸다.
    MarkerCreateServiceRequest request =
        createRequest("CLUE", null).toBuilder()
            .location(
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9"), new BigDecimal("91"))))
            .build();

    // when: 실제 서비스에서 좌표 검증에 실패한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);

    // then: 마커와 생성 이벤트가 DB에 남지 않는다.
    assertThat(readMarkerIds()).isEmpty();
    assertThat(readEventTypes()).isEmpty();
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
            .context(context)
            .build();

    // when: 현재 OP와 다른 OP로 마커 생성을 요청한다.
    assertThatThrownBy(() -> appMarkerService.create(request))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("op_mismatch");

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
                "SELECT ST_Covers(geometry, ST_SetSRID(ST_MakePoint(127.2, 35.1631), 4326)) FROM search_area WHERE id = ?",
                Boolean.class,
                OVERALL_AREA_ID))
        .isFalse();
    MarkerCreateServiceRequest request =
        MarkerCreateServiceRequest.builder()
            .incidentId(INCIDENT_ID)
            .opId(OP1_ID)
            .type("CLUE")
            .location(
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("127.200000"), new BigDecimal("35.163100"))))
            .memo("outside overall search area")
            .clientTs(CLIENT_TS)
            .clockOffsetMs(0L)
            .context(context)
            .build();

    // when: 구역 밖의 좌표로 단서 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);

    // then: 구역 밖이라는 이유로 거부하지 않고 마커와 생성 이벤트를 저장한다.
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(readMarkerIds()).containsExactly(response.getId());
    Marker marker = markerRepository.findById(response.getId()).orElseThrow();
    assertThat(marker.getLocation().getX()).isEqualTo(127.2);
    assertThat(marker.getLocation().getY()).isEqualTo(35.1631);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    JsonNode coordinates = readEventPayload("MARKER_CREATED").path("location").path("coordinates");
    assertThat(coordinates.path(0).asDouble()).isEqualTo(127.2);
    assertThat(coordinates.path(1).asDouble()).isEqualTo(35.1631);
  }

  @Test
  @DisplayName("지원 요청 마커를 생성하면, 지휘 계정과 현장 지휘관 대상 알림을 저장한다")
  void createSupportRequestMarker_savesNotification() throws Exception {
    // given: 일반 대원·지휘 계정·현장 지휘관이 배정된 사건에서 드론 지원을 요청한다.
    MarkerCreateServiceRequest request = createRequest("SUPPORT_REQUEST", "DRONE");
    Instant startedAt = Instant.now();

    // when: 지원 요청 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);
    Instant completedAt = Instant.now();

    // then: 일반 대원은 제외하고 지휘 계정과 현장 지휘관의 계정·업무폰만 알림 대상으로 저장한다.
    Marker marker = markerRepository.findById(response.getId()).orElseThrow();
    assertThat(marker.getSupportRequestType()).isEqualTo("DRONE");
    assertNotificationStored(
        response,
        "SUPPORT_REQUEST",
        "SUPPORT_REQUEST_CREATED",
        "COMMANDERS_AND_FIELD_COMMANDERS",
        List.of(PRECINCT_COMMANDER_ID, SUPPORT_TEAM_ID),
        List.of(COMMANDER_PHONE_ID, FIELD_COMMANDER_PHONE_ID),
        startedAt,
        completedAt);
  }

  @Test
  @DisplayName("발견 마커를 생성하면, 사건에 배정된 계정·업무폰 대상 알림을 저장한다")
  void createPersonFoundMarker_savesNotification() throws Exception {
    // given: 일반 대원·지휘 계정·현장 지휘관이 배정된 사건에서 발견 마커 생성을 요청한다.
    MarkerCreateServiceRequest request = createRequest("PERSON_FOUND", null);
    Instant startedAt = Instant.now();

    // when: 발견 마커를 생성한다.
    MarkerCreateServiceResponse response = appMarkerService.create(request);
    Instant completedAt = Instant.now();

    // then: 역할에 관계없이 이 사건에 배정된 세 계정과 각 업무폰을 알림 대상으로 저장한다.
    assertNotificationStored(
        response,
        "PERSON_FOUND",
        "PERSON_FOUND",
        "ALL_INCIDENT_ASSIGNED",
        List.of(PRECINCT_TEAM_ID, PRECINCT_COMMANDER_ID, SUPPORT_TEAM_ID),
        List.of(ASSIGNED_POLICE_PHONE_ID, COMMANDER_PHONE_ID, FIELD_COMMANDER_PHONE_ID),
        startedAt,
        completedAt);
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
                    "SELECT request_body_hash FROM idempotency_record WHERE idempotency_key = ? AND request_path = '/api/markers' AND request_method = 'POST'",
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
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ? AND request_path = '/api/markers' AND request_method = 'POST'",
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
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ? AND request_path = '/api/markers' AND request_method = 'POST'",
        "05ce05736900922dd9ab918703177999876d4489f6ccc9c682531b54246864a2",
        IDEMPOTENCY_KEY);
    MarkerCreateServiceRequest changedRequest =
        createRequest("SUPPORT_REQUEST", "DRONE").toBuilder().memo("changed memo").build();

    // when: 같은 키로 다른 본문을 전송한다.
    assertThatThrownBy(() -> appMarkerService.create(changedRequest))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 기존 마커를 덮어쓰거나 이벤트·알림을 추가하지 않는다.
    assertThat(readMarkerIds()).containsExactly(firstResponse.getId());
    assertThat(markerRepository.findById(firstResponse.getId()).orElseThrow().getMemo())
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

  @Test
  @DisplayName("사진을 포함한 과거 요청을 재전송하면, 사진을 다시 첨부하지 않고 기존 응답을 반환한다")
  void createMarker_legacyRequestWithPhoto_returnsStoredAttachmentResponse() {
    // given: 사진 첨부까지 완료된 요청이 변경 전 해시로 기록되어 있다.
    MarkerCreateServiceRequest request = prepareMarkerRequestWithUploadedPhoto();
    MarkerCreateServiceResponse firstResponse = appMarkerService.create(request);
    jdbcTemplate.update(
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ? AND request_path = '/api/markers' AND request_method = 'POST'",
        "d11455f257e00d3b325694535c9593ff511458fe5f506c911d287fe76ba4f55d",
        IDEMPOTENCY_KEY);

    // when: 이미 첨부된 사진을 포함한 같은 요청을 재전송한다.
    MarkerCreateServiceResponse repeatedResponse =
        appMarkerService.create(request.toBuilder().build());

    // then: 사진·마커 버전과 생성·첨부 이벤트 수를 그대로 유지한다.
    assertThat(repeatedResponse).usingRecursiveComparison().isEqualTo(firstResponse);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(photoRepository.findById(PHOTO_ID).orElseThrow().version()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", "MARKER_UPDATED");
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
    Marker saved = markerRepository.findById(first.getId()).orElseThrow();
    assertThat(saved.getMarkerType()).isEqualTo("CLUE");
    assertThat(saved.getMemo()).isEqualTo(MARKER_MEMO);
    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(readEventTypes()).containsExactly("MARKER_CREATED");
    assertCompletedRequest();
  }

  private MarkerCreateServiceRequest prepareMarkerRequestWithUploadedPhoto() {
    String objectKey = "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + PHOTO_ID + ".jpg";
    objectStorage.generatePresignedUrl(
        objectKey, "image/jpeg", 1_048_576L, CHECKSUM_SHA256, Duration.ofMinutes(15));
    objectStorage.simulateUpload(objectKey);
    photoRepository.save(
        new MarkerPhoto(
            PHOTO_ID,
            MARKER_ID,
            objectKey,
            "image/jpeg",
            1_048_576L,
            CHECKSUM_SHA256,
            Instant.now().plus(Duration.ofMinutes(15))));
    return MarkerCreateServiceRequest.builder()
        .id(MARKER_ID)
        .incidentId(INCIDENT_ID)
        .opId(OP1_ID)
        .type("CLUE")
        .location(createMarkerLocation())
        .memo("photo evidence")
        .clientTs(CLIENT_TS)
        .clockOffsetMs(0L)
        .photos(
            List.of(
                MarkerCreatePhotoRequest.builder()
                    .photoId(PHOTO_ID)
                    .sizeBytes(1_048_576L)
                    .contentType("image/jpeg")
                    .width(640)
                    .height(480)
                    .checksumSha256(CHECKSUM_SHA256)
                    .build()))
        .context(context)
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
        .context(context)
        .build();
  }

  private static MarkerGeoJsonPoint createMarkerLocation() {
    return new MarkerGeoJsonPoint(
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
      Instant startedAt,
      Instant completedAt)
      throws Exception {
    UUID markerId = response.getId();
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(readMarkerIds()).containsExactly(markerId);
    assertThat(markerRepository.findById(markerId).orElseThrow().getMarkerType())
        .isEqualTo(markerType);

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
                "SELECT n.marker_id FROM marker_notification n JOIN marker m ON m.id = n.marker_id WHERE m.incident_id = ?",
                UUID.class,
                INCIDENT_ID))
        .containsExactly(markerId);
    assertThat(readEventTypes()).containsExactlyInAnyOrder("MARKER_CREATED", notificationEventType);

    JsonNode storedResponse =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT to_jsonb(r)::text FROM idempotency_record r WHERE idempotency_key = ? AND request_path = '/api/markers' AND request_method = 'POST'",
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
                new MarkerGeoJsonPoint(
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
  @DisplayName("현장 마커를 삭제하면, 행은 남기고 삭제 상태·다음 버전·삭제 이벤트를 저장한다")
  void deleteMarker_currentVersion_savesDeletedStatusAndEvent() throws Exception {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 버전이 1인 현장 마커와 작성 계정의 요청이다.
    // when: 현재 버전으로 삭제한다.
    MarkerMutationServiceResponse response = appMarkerService.delete(deleteRequest());

    // then: 기존 내용은 남기고 삭제 결과를 응답과 이벤트에 기록한다.
    assertResponse(response, "DELETED");
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
    MarkerRequestContext requestContext = context("APP", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 실제 DB의 마커 출처로 수정·삭제 권한을 판단한다.
    assertThatThrownBy(
            () ->
                appMarkerService.update(
                    updateRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(
            () ->
                appMarkerService.delete(
                    deleteRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
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
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(() -> appMarkerService.delete(deleteRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("사건이 종료되었으면, 마커 수정과 삭제를 거부한다")
  void changeMarker_closedIncident_preservesMarkerAndEvents() {
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    // given: 마커가 속한 사건이 종료되어 있다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);

    // when & then: 수정·삭제 모두 사건 종료 오류를 반환한다.
    assertThatThrownBy(() -> appMarkerService.update(updateRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertThatThrownBy(() -> appMarkerService.delete(deleteRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertUnchangedMarker();
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
    assertThat(markerRepository.findById(MUTATION_MARKER_ID).orElseThrow().getVersion())
        .isEqualTo(2L);
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
    assertThat(markerRepository.findById(MUTATION_MARKER_ID).orElseThrow().getVersion())
        .isEqualTo(2L);
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
                new MarkerGeoJsonPoint(
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
  void changeMarker_webContext_rejectsBeforeReusingStoredResponse() {
    // given: 앱에서 처리한 수정 응답이 있고, 같은 요청 키를 가진 웹 인증이 있다.
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    appMarkerService.update(updateRequest());
    MarkerRequestContext webContext = context("WEB", null);

    // when & then: 저장된 응답을 반환하기 전에 서비스의 채널 경계를 검사한다.
    assertThatThrownBy(
            () -> appMarkerService.update(updateRequest().toBuilder().context(webContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("channel_not_allowed");
    assertThatThrownBy(
            () -> appMarkerService.delete(deleteRequest().toBuilder().context(webContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("channel_not_allowed");
    assertThat(markerRepository.findById(MUTATION_MARKER_ID).orElseThrow().getVersion())
        .isEqualTo(2L);
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

  private MarkerRequestContext context(String channel, UUID policePhoneId) {
    return new MarkerRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, channel, policePhoneId), IDEMPOTENCY_KEY);
  }

  private MarkerUpdateServiceRequest updateRequest() {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .memo("updated clue memo")
        .context(context)
        .build();
  }

  private MarkerDeleteServiceRequest deleteRequest() {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .reason("wrong marker")
        .context(context)
        .build();
  }

  private void insertMarker(MarkerSource source, UUID policePhoneId) {
    markerRepository.insertSeed(
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
    Marker saved = markerRepository.findById(MUTATION_MARKER_ID).orElseThrow();
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
