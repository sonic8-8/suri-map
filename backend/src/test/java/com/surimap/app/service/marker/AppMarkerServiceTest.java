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
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"surimap.object-storage.provider=mock", "fcm.provider=mock"})
class AppMarkerServiceTest extends PostGisIntegrationTestSupport {

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
}
