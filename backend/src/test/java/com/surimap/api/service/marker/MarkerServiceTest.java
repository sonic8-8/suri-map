package com.surimap.api.service.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.api.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.api.service.marker.response.MarkerListServiceResponse;
import com.surimap.api.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MarkerServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa5701");
  private static final UUID OP_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb5701");
  private static final UUID MARKER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc5701");
  private static final UUID ACCOUNT_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd5701");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeee5701");
  private static final UUID PHOTO_ID = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffff5701");
  private static final Instant OCCURRED_AT = Instant.parse("2026-05-14T00:00:01Z");
  private static final Instant ATTACHED_AT = Instant.parse("2026-05-14T00:00:02Z");
  private static final String PHOTO_OBJECT_KEY =
      "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + PHOTO_ID + ".jpg";
  private static final String PHOTO_URL = "https://photo.example/marker-photo.jpg";

  @Autowired private MarkerService markerService;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private ObjectMapper objectMapper;
  private MarkerRequestContext mutationContext;
  private static final UUID MUTATION_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final UUID MUTATION_INCIDENT_ID = MarkerGeometryFixtures.INCIDENT_ID;
  private static final UUID MUTATION_OP_ID = MarkerGeometryFixtures.OP1_ID;
  private static final String MUTATION_IDEMPOTENCY_KEY = "idem-marker-update-delete-001";
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  @MockitoBean private MockObjectStorageAdapter objectStorage;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE photo, marker");
    jdbcTemplate.update(
        """
        INSERT INTO marker (
            id, incident_id, operational_period_id, marker_type, location, memo,
            occurred_at, created_by_account_id, police_phone_id, marker_source, status, version
        )
        VALUES (?, ?, ?, 'CLUE', ST_SetSRID(ST_MakePoint(126.9134, 35.1631), 4326),
                ?, ?, ?, ?, 'APP', 'ACTIVE', 7)
        """,
        MARKER_ID,
        INCIDENT_ID,
        OP_ID,
        "등산로 입구 제보",
        Timestamp.from(OCCURRED_AT),
        ACCOUNT_ID,
        POLICE_PHONE_ID);
    jdbcTemplate.update(
        """
        INSERT INTO photo (
            id, marker_id, object_key, status, attached_at, content_type, size_bytes, version
        )
        VALUES (?, ?, ?, 'ATTACHED', ?, 'image/jpeg', 1024, 3)
        """,
        PHOTO_ID,
        MARKER_ID,
        PHOTO_OBJECT_KEY,
        Timestamp.from(ATTACHED_AT));
    when(objectStorage.generatePresignedViewUrl(
            PHOTO_OBJECT_KEY, ObjectStoragePort.DEFAULT_VIEW_TTL))
        .thenReturn(Optional.of(PHOTO_URL));
  }

  @Test
  @DisplayName("사건·수색 차수·유형·상태를 지정하면, 조건에 맞는 마커와 첨부 사진을 조회한다")
  void listMarkers_matchingFilters_returnsMarkerAndAttachedPhoto() {
    // given: 사진이 첨부된 활성 마커가 실제 DB에 저장되어 있다.
    // when: 소문자 유형을 포함한 조회 조건으로 마커를 조회한다.
    MarkerListServiceResponse response =
        markerService.list(INCIDENT_ID, OP_ID, " clue ", " active ");

    // then: 저장된 마커와 사진 정보, 사진 조회 주소를 반환한다.
    assertThat(response.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(response.getMarkers()).hasSize(1);
    MarkerView marker = response.getMarkers().get(0);
    assertThat(marker.id()).isEqualTo(MARKER_ID);
    assertThat(marker.type().name()).isEqualTo("CLUE");
    assertThat(marker.status().name()).isEqualTo("ACTIVE");
    assertThat(marker.location().getSRID()).isEqualTo(4326);
    assertThat(marker.location().getX()).isEqualTo(126.9134);
    assertThat(marker.location().getY()).isEqualTo(35.1631);
    assertThat(marker.photoSummary())
        .extracting(MarkerPhotoSummary::photoId)
        .containsExactly(PHOTO_ID);
    assertThat(marker.photoSummary())
        .extracting(MarkerPhotoSummary::photoUrl)
        .containsExactly(PHOTO_URL);
  }

  @ParameterizedTest
  @CsvSource({"bad-type,", ",bad-status"})
  @DisplayName("알 수 없는 마커 유형이나 상태를 지정하면, invalid_marker_filter 오류로 거부한다")
  void listMarkers_unknownTypeOrStatus_rejectsInvalidFilter(String type, String status) {
    // given: 지원하지 않는 마커 유형 또는 상태를 조회 조건으로 지정한다.
    // when & then: 조회 요청을 기존 오류 코드로 거부한다.
    assertThatThrownBy(() -> markerService.list(INCIDENT_ID, OP_ID, type, status))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("invalid_marker_filter");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createNonMatchingFilters")
  @DisplayName("사건·수색 차수·유형·상태 중 하나라도 일치하지 않으면, 해당 마커를 반환하지 않는다")
  void listMarkers_nonMatchingFilter_returnsEmpty(
      String condition, UUID incidentId, UUID opId, String type, String status) {
    // given: 저장된 마커와 한 가지 조건이 다른 조회 요청이다.
    // when: 해당 조건으로 마커 목록을 조회한다.
    MarkerListServiceResponse response = markerService.list(incidentId, opId, type, status);

    // then: 조회한 사건 ID와 빈 목록을 반환한다.
    assertThat(response.getIncidentId()).isEqualTo(incidentId);
    assertThat(response.getMarkers()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"ACTIVE,1", "UPDATED,1", "DELETED,0"})
  @DisplayName("조회 조건을 생략하면, 활성·수정 상태의 마커를 반환하고 삭제한 마커는 제외한다")
  void listMarkers_withoutOptionalFilters_excludesDeletedMarkers(String status, int expectedCount) {
    // given: 저장된 마커의 상태를 활성·수정·삭제 중 하나로 지정한다.
    jdbcTemplate.update("UPDATE marker SET status = ? WHERE id = ?", status, MARKER_ID);

    // when: 사건 외에 수색 차수·유형·상태 조건은 지정하지 않는다.
    MarkerListServiceResponse response = markerService.list(INCIDENT_ID, null, null, null);

    // then: 기본 조회에는 활성·수정 상태만 포함한다.
    assertThat(response.getMarkers()).hasSize(expectedCount);
  }

  private static Stream<Arguments> createNonMatchingFilters() {
    return Stream.of(
        Arguments.of(
            "다른 사건",
            UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb7108"),
            OP_ID,
            "CLUE",
            "ACTIVE"),
        Arguments.of(
            "다른 수색 차수",
            INCIDENT_ID,
            UUID.fromString("22222222-2222-4222-8222-222222227108"),
            "CLUE",
            "ACTIVE"),
        Arguments.of("다른 마커 유형", INCIDENT_ID, OP_ID, "NOTE", "ACTIVE"),
        Arguments.of("다른 마커 상태", INCIDENT_ID, OP_ID, "CLUE", "UPDATED"));
  }

  @ParameterizedTest
  @EnumSource(
      value = MarkerSource.class,
      names = {"MOCK_SEED", "SYSTEM"})
  @DisplayName("업무폰 정보가 없는 기준 마커를 웹에서 수정하면, 이벤트에도 업무폰 없이 기록한다")
  void updateMarker_webReferenceWithoutPhone_savesEventWithoutPhone(MarkerSource source)
      throws Exception {
    prepareMarkerMutation();
    // given: 업무폰 정보가 없는 기준 마커를 웹에서 수정한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MUTATION_MARKER_ID);
    insertMarker(source, null);
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder().context(context("WEB", null)).type("NOTE").build();

    // when: 메모와 유형을 수정한다.
    MarkerMutationServiceResponse response = markerService.update(request);

    // then: 업무폰을 만들어 넣지 않고 수정 내용과 이벤트를 저장한다.
    assertResponse(response, "UPDATED");
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getPolicePhoneId()).isNull();
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getMarkerType()).isEqualTo("NOTE");
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertEvent(readEventPayload(), "UPDATED", null);
  }

  @Test
  @DisplayName("업무폰 정보가 없는 기준 마커를 웹에서 삭제하면, 삭제 이벤트에도 업무폰 없이 기록한다")
  void deleteMarker_webReferenceWithoutPhone_savesEventWithoutPhone() throws Exception {
    prepareMarkerMutation();
    // given: 업무폰 정보가 없는 사전 등록 마커를 준비한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MUTATION_MARKER_ID);
    insertMarker(MarkerSource.MOCK_SEED, null);
    MarkerDeleteServiceRequest request =
        deleteRequest().toBuilder().context(context("WEB", null)).build();

    // when: 웹에서 삭제를 요청한다.
    MarkerMutationServiceResponse response = markerService.delete(request);

    // then: 행은 남고, 삭제 결과와 이벤트에도 업무폰 정보가 없다.
    assertResponse(response, "DELETED");
    Marker saved = markerMapper.findById(MUTATION_MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getPolicePhoneId()).isNull();
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    assertEvent(readEventPayload(), "DELETED", null);
  }

  @Test
  @DisplayName("채널에서 변경할 수 없는 마커이면, 수정과 삭제를 모두 거부한다")
  void changeMarker_disallowedSource_preservesMarkerAndEvents() {
    prepareMarkerMutation();
    // given: 웹에서 현장 마커를 변경하도록 요청한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MUTATION_MARKER_ID);
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
    MarkerRequestContext requestContext = context("WEB", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 실제 DB의 마커 출처로 수정·삭제 권한을 판단한다.
    assertThatThrownBy(
            () -> markerService.update(updateRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(
            () -> markerService.delete(deleteRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("사건이 종료되었으면, 마커 수정과 삭제를 거부한다")
  void changeMarker_closedIncident_preservesMarkerAndEvents() {
    prepareMarkerMutation();
    // given: 마커가 속한 사건이 종료되어 있다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?",
        MUTATION_INCIDENT_ID);

    // when & then: 수정·삭제 모두 사건 종료 오류를 반환한다.
    assertThatThrownBy(() -> markerService.update(updateRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertThatThrownBy(() -> markerService.delete(deleteRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("같은 수정 요청을 재전송하면, 저장된 응답을 반환하고 중복 변경하지 않는다")
  void updateMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    prepareMarkerMutation();
    // given: 수정이 한 번 완료된 요청이다.
    MarkerMutationServiceResponse first = markerService.update(updateRequest());

    // when: 같은 키와 본문으로 다시 요청한다.
    MarkerMutationServiceResponse repeated = markerService.update(updateRequest());

    // then: DB 버전과 이벤트는 한 번만 증가한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertThatThrownBy(
            () -> markerService.update(updateRequest().toBuilder().memo("다른 메모").build()))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  @DisplayName("같은 삭제 요청을 재전송하면, 삭제 상태여도 기존 응답을 반환하고 중복 처리하지 않는다")
  void deleteMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    prepareMarkerMutation();
    // given: 삭제가 한 번 완료된 요청이다.
    MarkerMutationServiceResponse first = markerService.delete(deleteRequest());

    // when: 같은 키와 본문으로 다시 삭제를 요청한다.
    MarkerMutationServiceResponse repeated = markerService.delete(deleteRequest());

    // then: 저장된 응답을 반환하고 삭제 이벤트는 하나만 남는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
  }

  @Test
  @DisplayName("이벤트 저장에 실패하면, 마커 수정과 요청 처리 기록도 함께 롤백한다")
  void updateMarker_eventStorageFails_rollsBackMarkerAndRequestRecord() {
    prepareMarkerMutation();
    // given: 테스트 DB에서 이 마커의 수정 이벤트만 저장할 수 없게 한다.
    jdbcTemplate.execute(
        """
        ALTER TABLE event_dispatch_job ADD CONSTRAINT test_marker_update_event_failure
        CHECK (source_entity_id <> '55555555-5555-5555-5555-555555550072'::uuid
               OR event_type <> 'MARKER_UPDATED') NOT VALID
        """);
    try {
      // when & then: 마커 SQL 다음의 이벤트 SQL이 실패해도 일부 변경만 남지 않는다.
      assertThatThrownBy(() -> markerService.update(updateRequest()))
          .isInstanceOf(DataAccessException.class);
      assertUnchangedMarker();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?",
                  Integer.class,
                  MUTATION_IDEMPOTENCY_KEY))
          .isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE event_dispatch_job DROP CONSTRAINT test_marker_update_event_failure");
    }
    // then: 실패 원인이 사라지면 같은 키로 정상 처리할 수 있다.
    assertResponse(markerService.update(updateRequest()), "UPDATED");
  }

  @Test
  @DisplayName("웹 서비스에 앱 인증으로 수정·삭제를 요청하면, 저장된 응답이 있어도 거부한다")
  void changeMarker_appContext_rejectsBeforeReusingStoredResponse() {
    // given: 웹에서 처리한 수정 응답이 있고, 같은 요청 키를 가진 앱 인증이 있다.
    prepareMarkerMutation();
    markerService.update(updateRequest());
    MarkerRequestContext appContext = context("APP", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 저장된 응답을 반환하기 전에 서비스의 채널 경계를 검사한다.
    assertThatThrownBy(
            () -> markerService.update(updateRequest().toBuilder().context(appContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("channel_not_allowed");
    assertThatThrownBy(
            () -> markerService.delete(deleteRequest().toBuilder().context(appContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("channel_not_allowed");
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
  }

  private void prepareMarkerMutation() {
    // Testcontainers의 테스트 DB에서 이 사건과 요청 키로 만든 데이터만 정리한다.
    jdbcTemplate.update(
        "DELETE FROM event_dispatch_job WHERE incident_id = ?", MUTATION_INCIDENT_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", MUTATION_INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?", MUTATION_IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", MUTATION_OP_ID);
    jdbcTemplate.update(
        "DELETE FROM incident_assignment WHERE incident_id = ?", MUTATION_INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident (id, source_incident_id, title, status, opened_at, version, created_at, updated_at)
        VALUES (?, ?, 'Marker mutation fixture', 'OPEN', NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'OPEN', closed_at = NULL, closed_by_account_id = NULL
        """,
        MUTATION_INCIDENT_ID,
        MUTATION_INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at)
        VALUES (?, ?, ?, 'MEMBER', NOW(), NOW(), NOW())
        """,
        UUID.randomUUID(),
        MUTATION_INCIDENT_ID,
        PRECINCT_TEAM_ID);
    mutationContext = context("WEB", null);
    insertMarker(MarkerSource.MOCK_SEED, null);
  }

  private MarkerRequestContext context(String channel, UUID policePhoneId) {
    return new MarkerRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, channel, policePhoneId),
        MUTATION_IDEMPOTENCY_KEY);
  }

  private MarkerUpdateServiceRequest updateRequest() {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .memo("updated clue memo")
        .context(mutationContext)
        .build();
  }

  private MarkerDeleteServiceRequest deleteRequest() {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .reason("wrong marker")
        .context(mutationContext)
        .build();
  }

  private void insertMarker(MarkerSource source, UUID policePhoneId) {
    markerMapper.insertSeed(
        Marker.builder()
            .id(MUTATION_MARKER_ID)
            .incidentId(MUTATION_INCIDENT_ID)
            .operationalPeriodId(MUTATION_OP_ID)
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
    assertThat(event.path("incidentId").asText()).isEqualTo(MUTATION_INCIDENT_ID.toString());
    assertThat(event.path("opId").asText()).isEqualTo(MUTATION_OP_ID.toString());
    assertThat(event.path("version").asLong()).isEqualTo(2L);
    assertThat(event.path("status").asText()).isEqualTo(status);
    if (policePhoneId == null) {
      assertThat(event.path("policePhoneId").isNull()).isTrue();
    } else {
      assertThat(event.path("policePhoneId").asText()).isEqualTo(policePhoneId.toString());
    }
  }

  private List<String> readEventTypes() {
    return jdbcTemplate.queryForList(
        "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
        String.class,
        MUTATION_INCIDENT_ID);
  }

  private JsonNode readEventPayload() throws Exception {
    return objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "SELECT payload::text FROM event_dispatch_job WHERE incident_id = ?",
            String.class,
            MUTATION_INCIDENT_ID));
  }
}
