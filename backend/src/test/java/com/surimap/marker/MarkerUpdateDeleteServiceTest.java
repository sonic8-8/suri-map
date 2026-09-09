package com.surimap.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.marker.service.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;
import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class MarkerUpdateDeleteServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final String IDEMPOTENCY_KEY = "idem-marker-update-delete-001";

  @Autowired private MarkerUpdateDeleteService service;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private ObjectMapper objectMapper;

  private MarkerRequestContext appContext;

  @BeforeEach
  void setUp() {
    // Testcontainers의 테스트 DB에서 이 사건과 요청 키로 만든 데이터만 정리한다.
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?", IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident (id, source_incident_id, title, status, opened_at, version, created_at, updated_at)
        VALUES (?, ?, 'Marker mutation fixture', 'OPEN', NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'OPEN', closed_at = NULL, closed_by_account_id = NULL
        """,
        INCIDENT_ID,
        INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at)
        VALUES (?, ?, ?, 'MEMBER', NOW(), NOW(), NOW())
        """,
        UUID.randomUUID(),
        INCIDENT_ID,
        PRECINCT_TEAM_ID);
    appContext = context("APP", ASSIGNED_POLICE_PHONE_ID);
    insertMarker(MarkerSource.APP, ASSIGNED_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("마커를 수정하면, 변경된 내용과 증가한 버전 및 수정 이벤트를 함께 저장한다")
  void updateMarker_currentVersion_savesChangesAndUpdatedEvent() throws Exception {
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
    MarkerMutationServiceResponse response = service.update(request);
    Instant completedAt = Instant.now();

    // then: 응답·마커·이벤트에 같은 수정 내용과 버전이 남는다.
    assertResponse(response, "UPDATED");
    Marker saved = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(saved.getMarkerType()).isEqualTo("NOTE");
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getLocation().getSRID()).isEqualTo(4326);
    assertThat(saved.getLocation().getX()).isEqualTo(126.913701);
    assertThat(saved.getLocation().getY()).isEqualTo(35.163401);
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(saved.getStatus()).isEqualTo("UPDATED");
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    JsonNode event = readEventPayload();
    assertEvent(event, "UPDATED", ASSIGNED_POLICE_PHONE_ID);
    assertThat(event.path("type").asText()).isEqualTo("NOTE");
    assertThat(event.path("location").path("coordinates").get(0).decimalValue())
        .isEqualByComparingTo("126.913701");
    assertThat(event.path("location").path("coordinates").get(1).decimalValue())
        .isEqualByComparingTo("35.163401");
    assertThat(Instant.parse(event.path("serverTs").asText())).isBetween(startedAt, completedAt);
  }

  @ParameterizedTest
  @EnumSource(
      value = MarkerSource.class,
      names = {"MOCK_SEED", "SYSTEM"})
  @DisplayName("업무폰 정보가 없는 기준 마커를 웹에서 수정하면, 이벤트에도 업무폰 없이 기록한다")
  void updateMarker_webReferenceWithoutPhone_savesEventWithoutPhone(MarkerSource source)
      throws Exception {
    // given: 업무폰 정보가 없는 기준 마커를 웹에서 수정한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);
    insertMarker(source, null);
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder().context(context("WEB", null)).type("NOTE").build();

    // when: 메모와 유형을 수정한다.
    MarkerMutationServiceResponse response = service.update(request);

    // then: 업무폰을 만들어 넣지 않고 수정 내용과 이벤트를 저장한다.
    assertResponse(response, "UPDATED");
    Marker saved = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(saved.getPolicePhoneId()).isNull();
    assertThat(saved.getMemo()).isEqualTo("updated clue memo");
    assertThat(saved.getMarkerType()).isEqualTo("NOTE");
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertEvent(readEventPayload(), "UPDATED", null);
  }

  @Test
  @DisplayName("업무폰 정보가 없는 기준 마커를 웹에서 삭제하면, 삭제 이벤트에도 업무폰 없이 기록한다")
  void deleteMarker_webReferenceWithoutPhone_savesEventWithoutPhone() throws Exception {
    // given: 업무폰 정보가 없는 사전 등록 마커를 준비한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);
    insertMarker(MarkerSource.MOCK_SEED, null);
    MarkerDeleteServiceRequest request =
        deleteRequest().toBuilder().context(context("WEB", null)).build();

    // when: 웹에서 삭제를 요청한다.
    MarkerMutationServiceResponse response = service.delete(request);

    // then: 행은 남고, 삭제 결과와 이벤트에도 업무폰 정보가 없다.
    assertResponse(response, "DELETED");
    Marker saved = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getPolicePhoneId()).isNull();
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    assertEvent(readEventPayload(), "DELETED", null);
  }

  @Test
  @DisplayName("현장 마커를 삭제하면, 행은 남기고 삭제 상태·다음 버전·삭제 이벤트를 저장한다")
  void deleteMarker_currentVersion_savesDeletedStatusAndEvent() throws Exception {
    // given: 버전이 1인 현장 마커와 작성 계정의 요청이다.
    // when: 현재 버전으로 삭제한다.
    MarkerMutationServiceResponse response = service.delete(deleteRequest());

    // then: 기존 내용은 남기고 삭제 결과를 응답과 이벤트에 기록한다.
    assertResponse(response, "DELETED");
    Marker saved = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(saved.getMemo()).isEqualTo("initial clue");
    assertThat(saved.getStatus()).isEqualTo("DELETED");
    assertThat(saved.getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
    JsonNode event = readEventPayload();
    assertEvent(event, "DELETED", ASSIGNED_POLICE_PHONE_ID);
    assertThat(event.has("type")).isFalse();
    assertThat(event.has("location")).isFalse();
  }

  @Test
  @DisplayName("수정할 메모가 2,000자를 넘으면, 마커와 이벤트를 변경하지 않는다")
  void updateMarker_memoTooLong_preservesMarkerAndEvents() {
    // given: 유형 변경과 허용 길이를 넘는 메모를 요청한다.
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder().type("NOTE").memo("m".repeat(2001)).build();

    // when & then: 검증 오류로 전체 요청을 거부한다.
    assertThatThrownBy(() -> service.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("요청 버전이 저장된 버전과 다르면, 마커와 이벤트를 변경하지 않는다")
  void updateMarker_versionMismatch_preservesMarkerAndEvents() {
    // given: 저장된 버전은 1인데 요청 버전은 99이다.
    MarkerUpdateServiceRequest request = updateRequest().toBuilder().version(99L).build();

    // when & then: 다른 버전의 수정 요청을 거부한다.
    assertThatThrownBy(() -> service.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  @ParameterizedTest
  @CsvSource({"APP,MOCK_SEED", "WEB,APP"})
  @DisplayName("채널에서 변경할 수 없는 마커이면, 수정과 삭제를 모두 거부한다")
  void changeMarker_disallowedSource_preservesMarkerAndEvents(String channel, MarkerSource source) {
    // given: 앱에는 기준 마커를, 웹에는 현장 마커를 변경하도록 요청한다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);
    insertMarker(source, ASSIGNED_POLICE_PHONE_ID);
    MarkerRequestContext requestContext = context(channel, ASSIGNED_POLICE_PHONE_ID);

    // when & then: 실제 DB의 마커 출처로 수정·삭제 권한을 판단한다.
    assertThatThrownBy(
            () -> service.update(updateRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(
            () -> service.delete(deleteRequest().toBuilder().context(requestContext).build()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("다른 계정이 기록한 현장 마커이면, 앱에서 수정과 삭제를 거부한다")
  void changeMarker_differentAuthor_preservesMarkerAndEvents() {
    // given: 요청 계정과 다른 계정이 마커를 기록했다.
    jdbcTemplate.update(
        "UPDATE marker SET created_by_account_id = ? WHERE id = ?",
        com.surimap.account.AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
        MARKER_ID);

    // when & then: 같은 사건에 배정되어 있어도 작성자가 아니면 거부한다.
    assertThatThrownBy(() -> service.update(updateRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(() -> service.delete(deleteRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("사건이 종료되었으면, 마커 수정과 삭제를 거부한다")
  void changeMarker_closedIncident_preservesMarkerAndEvents() {
    // given: 마커가 속한 사건이 종료되어 있다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);

    // when & then: 수정·삭제 모두 사건 종료 오류를 반환한다.
    assertThatThrownBy(() -> service.update(updateRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertThatThrownBy(() -> service.delete(deleteRequest()))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("incident_closed");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("같은 수정 요청을 재전송하면, 저장된 응답을 반환하고 중복 변경하지 않는다")
  void updateMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    // given: 수정이 한 번 완료된 요청이다.
    MarkerMutationServiceResponse first = service.update(updateRequest());

    // when: 같은 키와 본문으로 다시 요청한다.
    MarkerMutationServiceResponse repeated = service.update(updateRequest());

    // then: DB 버전과 이벤트는 한 번만 증가한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_UPDATED");
    assertThatThrownBy(() -> service.update(updateRequest().toBuilder().memo("다른 메모").build()))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  @DisplayName("같은 삭제 요청을 재전송하면, 삭제 상태여도 기존 응답을 반환하고 중복 처리하지 않는다")
  void deleteMarker_sameRequest_returnsStoredResponseWithoutDuplicates() {
    // given: 삭제가 한 번 완료된 요청이다.
    MarkerMutationServiceResponse first = service.delete(deleteRequest());

    // when: 같은 키와 본문으로 다시 삭제를 요청한다.
    MarkerMutationServiceResponse repeated = service.delete(deleteRequest());

    // then: 저장된 응답을 반환하고 삭제 이벤트는 하나만 남는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(readEventTypes()).containsExactly("MARKER_DELETED");
  }

  @Test
  @DisplayName("이벤트 저장에 실패하면, 마커 수정과 요청 처리 기록도 함께 롤백한다")
  void updateMarker_eventStorageFails_rollsBackMarkerAndRequestRecord() {
    // given: 테스트 DB에서 이 마커의 수정 이벤트만 저장할 수 없게 한다.
    jdbcTemplate.execute(
        """
        ALTER TABLE event_dispatch_job ADD CONSTRAINT test_marker_update_event_failure
        CHECK (source_entity_id <> '55555555-5555-5555-5555-555555550072'::uuid
               OR event_type <> 'MARKER_UPDATED') NOT VALID
        """);
    try {
      // when & then: 마커 SQL 다음의 이벤트 SQL이 실패해도 일부 변경만 남지 않는다.
      assertThatThrownBy(() -> service.update(updateRequest()))
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
    assertResponse(service.update(updateRequest()), "UPDATED");
  }

  @Test
  @DisplayName("유형과 좌표가 모두 잘못되었으면, 기존과 같이 유형 오류를 먼저 반환한다")
  void updateMarker_invalidTypeAndLocation_rejectsTypeBeforeGeometry() {
    // given: 존재하지 않는 유형과 Point가 아닌 좌표를 함께 보낸다.
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder()
            .type("UNKNOWN")
            .location(
                new MarkerGeoJsonPoint(
                    "LineString", List.of(new BigDecimal("126.9"), new BigDecimal("35.1"))))
            .build();

    // when & then: 좌표 오류보다 앞서 유형의 write_conflict 오류를 반환한다.
    assertThatThrownBy(() -> service.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertUnchangedMarker();
  }

  private MarkerRequestContext context(String channel, UUID policePhoneId) {
    return new MarkerRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, channel, policePhoneId), IDEMPOTENCY_KEY);
  }

  private MarkerUpdateServiceRequest updateRequest() {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MARKER_ID)
        .version(1L)
        .memo("updated clue memo")
        .context(appContext)
        .build();
  }

  private MarkerDeleteServiceRequest deleteRequest() {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MARKER_ID)
        .version(1L)
        .reason("wrong marker")
        .context(appContext)
        .build();
  }

  private void insertMarker(MarkerSource source, UUID policePhoneId) {
    markerMapper.insertSeed(
        Marker.builder()
            .id(MARKER_ID)
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
    assertThat(response.getId()).isEqualTo(MARKER_ID);
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getVersion()).isEqualTo(2L);
  }

  private void assertUnchangedMarker() {
    Marker saved = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(saved.getMemo()).isEqualTo("initial clue");
    assertThat(readEventTypes()).isEmpty();
  }

  private void assertEvent(JsonNode event, String status, UUID policePhoneId) {
    assertThat(event.path("id").asText()).isEqualTo(MARKER_ID.toString());
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

  private List<String> readEventTypes() {
    return jdbcTemplate.queryForList(
        "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
        String.class,
        INCIDENT_ID);
  }

  private JsonNode readEventPayload() throws Exception {
    return objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "SELECT payload::text FROM event_dispatch_job WHERE incident_id = ?",
            String.class,
            INCIDENT_ID));
  }
}
