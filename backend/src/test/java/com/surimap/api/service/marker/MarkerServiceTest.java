package com.surimap.api.service.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.api.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.api.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse.MarkerPhotoServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse.MarkerServiceResponse;
import com.surimap.client.storage.MockObjectStorageAdapter;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerSupportRequestType;
import com.surimap.domain.marker.MarkerType;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.global.geometry.GeoJsonPoint;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
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
  private SuriMapAuthentication mutationAuthentication;
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
    MarkersServiceResponse response = markerService.list(INCIDENT_ID, OP_ID, " clue ", " active ");

    // then: 저장된 마커와 사진 정보, 사진 조회 주소를 반환한다.
    assertThat(response.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(response.getMarkers()).hasSize(1);
    MarkerServiceResponse marker = response.getMarkers().get(0);
    assertThat(marker.getId()).isEqualTo(MARKER_ID);
    assertThat(marker.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(marker.getOpId()).isEqualTo(OP_ID);
    assertThat(marker.getDutyShiftId()).isNull();
    assertThat(marker.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(marker.getPolicePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(marker.getType().name()).isEqualTo("CLUE");
    assertThat(marker.getSupportRequestType()).isNull();
    assertThat(marker.getSource()).isEqualTo(MarkerSource.APP);
    assertThat(marker.getStatus().name()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(7L);
    assertThat(marker.getMemo()).isEqualTo("등산로 입구 제보");
    assertThat(marker.getOccurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(marker.getLocation().getSRID()).isEqualTo(4326);
    assertThat(marker.getLocation().getX()).isEqualTo(126.9134);
    assertThat(marker.getLocation().getY()).isEqualTo(35.1631);
    assertThat(marker.getPhotoSummary())
        .extracting(MarkerPhotoServiceResponse::getPhotoId)
        .containsExactly(PHOTO_ID);
    assertThat(marker.getPhotoSummary())
        .extracting(MarkerPhotoServiceResponse::getPhotoUrl)
        .containsExactly(PHOTO_URL);
    MarkerPhotoServiceResponse photo = marker.getPhotoSummary().get(0);
    assertThat(photo.getThumbnailUrl()).isEqualTo(PHOTO_URL);
    assertThat(photo.getStatus()).isEqualTo("ATTACHED");
    assertThat(photo.getVersion()).isEqualTo(3L);
    assertThat(photo.getContentType()).isEqualTo("image/jpeg");
    assertThat(photo.getSizeBytes()).isEqualTo(1024L);
    assertThat(photo.getAttachedAt()).isEqualTo(ATTACHED_AT);
  }

  @Test
  @DisplayName("지원 요청 마커에 근무 교대와 요청 유형이 있으면, 조회 결과에도 해당 값을 유지한다")
  void listMarkers_supportRequestWithDutyShift_preservesContext() {
    // given: 근무 교대와 드론 지원 요청이 기록된 마커다.
    UUID dutyShiftId = UUID.randomUUID();
    jdbcTemplate.update(
        "UPDATE marker SET duty_shift_id = ?, marker_type = 'SUPPORT_REQUEST',"
            + " support_request_type = 'DRONE' WHERE id = ?",
        dutyShiftId,
        MARKER_ID);

    // when: 마커를 조회한다.
    MarkerServiceResponse marker =
        markerService.list(INCIDENT_ID, OP_ID, "SUPPORT_REQUEST", null).getMarkers().get(0);

    // then: 선택 항목도 응답 변환에서 빠지지 않는다.
    assertThat(marker.getType()).isEqualTo(MarkerType.SUPPORT_REQUEST);
    assertThat(marker.getSupportRequestType()).isEqualTo(MarkerSupportRequestType.DRONE);
    assertThat(marker.getDutyShiftId()).isEqualTo(dutyShiftId);
  }

  @Test
  @DisplayName("사진 조회 URL이 없으면, 마커와 첨부 사진 정보는 반환하고 URL만 비워 둔다")
  void listMarkers_photoViewUrlUnavailable_preservesMarkerAndPhoto() {
    // given: 저장소에서 첨부 사진의 조회 URL을 발급하지 못한다.
    when(objectStorage.generatePresignedViewUrl(
            PHOTO_OBJECT_KEY, ObjectStoragePort.DEFAULT_VIEW_TTL))
        .thenReturn(Optional.empty());

    // when: 마커와 첨부 사진을 조회한다.
    MarkersServiceResponse response = markerService.list(INCIDENT_ID, null, null, null);

    // then: 조회 결과는 유지하고 사진 URL만 null로 반환한다.
    assertThat(response.getMarkers())
        .extracting(MarkerServiceResponse::getId)
        .containsExactly(MARKER_ID);
    assertThat(response.getMarkers().get(0).getPhotoSummary())
        .singleElement()
        .satisfies(
            photo -> {
              assertThat(photo.getPhotoId()).isEqualTo(PHOTO_ID);
              assertThat(photo.getPhotoUrl()).isNull();
              assertThat(photo.getThumbnailUrl()).isNull();
            });
  }

  @Test
  @DisplayName("사진 URL 발급 중 예외가 발생해도, 마커와 첨부 사진 조회는 실패하지 않는다")
  void listMarkers_photoViewUrlThrows_preservesMarkerAndPhoto() {
    // given: 외부 저장소 연동에서 예외가 발생한다.
    when(objectStorage.generatePresignedViewUrl(
            PHOTO_OBJECT_KEY, ObjectStoragePort.DEFAULT_VIEW_TTL))
        .thenThrow(new IllegalStateException("storage unavailable"));

    // when: 마커와 첨부 사진을 조회한다.
    MarkersServiceResponse response = markerService.list(INCIDENT_ID, null, null, null);

    // then: 기존 동작대로 마커·사진 정보는 반환하고 URL만 비워 둔다.
    assertThat(response.getMarkers())
        .extracting(MarkerServiceResponse::getId)
        .containsExactly(MARKER_ID);
    assertThat(response.getMarkers().get(0).getPhotoSummary())
        .singleElement()
        .satisfies(
            photo -> {
              assertThat(photo.getPhotoId()).isEqualTo(PHOTO_ID);
              assertThat(photo.getPhotoUrl()).isNull();
              assertThat(photo.getThumbnailUrl()).isNull();
            });
  }

  @Test
  @DisplayName("첨부가 끝나지 않은 사진만 있으면, 마커의 사진 목록은 비우고 URL도 발급하지 않는다")
  void listMarkers_withoutAttachedPhotos_returnsMarkerWithEmptyPhotos() {
    // given: 사진 업로드가 아직 끝나지 않은 마커다.
    jdbcTemplate.update(
        "UPDATE photo SET status = 'PENDING_UPLOAD', attached_at = NULL WHERE id = ?", PHOTO_ID);

    // when: 마커를 조회한다.
    MarkersServiceResponse response = markerService.list(INCIDENT_ID, null, null, null);

    // then: 사진이 없어도 마커를 반환하며 외부 저장소는 호출하지 않는다.
    assertThat(response.getMarkers())
        .singleElement()
        .satisfies(
            marker -> {
              assertThat(marker.getId()).isEqualTo(MARKER_ID);
              assertThat(marker.getPhotoSummary()).isEmpty();
            });
    verifyNoInteractions(objectStorage);
  }

  @ParameterizedTest
  @CsvSource({"bad-type,", ",bad-status"})
  @DisplayName("알 수 없는 마커 유형이나 상태를 지정하면, invalid_marker_filter 오류로 거부한다")
  void listMarkers_unknownTypeOrStatus_rejectsInvalidFilter(String type, String status) {
    // given: 지원하지 않는 마커 유형 또는 상태를 조회 조건으로 지정한다.
    // when & then: 조회 요청을 기존 오류 코드로 거부한다.
    assertThatThrownBy(() -> markerService.list(INCIDENT_ID, OP_ID, type, status))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("invalid_marker_filter");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createNonMatchingFilters")
  @DisplayName("사건·수색 차수·유형·상태 중 하나라도 일치하지 않으면, 해당 마커를 반환하지 않는다")
  void listMarkers_nonMatchingFilter_returnsEmpty(
      String condition, UUID incidentId, UUID opId, String type, String status) {
    // given: 저장된 마커와 한 가지 조건이 다른 조회 요청이다.
    // when: 해당 조건으로 마커 목록을 조회한다.
    MarkersServiceResponse response = markerService.list(incidentId, opId, type, status);

    // then: 조회한 사건 ID와 빈 목록을 반환한다.
    assertThat(response.getIncidentId()).isEqualTo(incidentId);
    assertThat(response.getMarkers()).isEmpty();
    verifyNoInteractions(objectStorage);
  }

  @ParameterizedTest
  @CsvSource({"ACTIVE,1", "UPDATED,1", "DELETED,0"})
  @DisplayName("조회 조건을 생략하면, 활성·수정 상태의 마커를 반환하고 삭제한 마커는 제외한다")
  void listMarkers_withoutOptionalFilters_excludesDeletedMarkers(String status, int expectedCount) {
    // given: 저장된 마커의 상태를 활성·수정·삭제 중 하나로 지정한다.
    jdbcTemplate.update("UPDATE marker SET status = ? WHERE id = ?", status, MARKER_ID);

    // when: 사건 외에 수색 차수·유형·상태 조건은 지정하지 않는다.
    MarkersServiceResponse response = markerService.list(INCIDENT_ID, null, null, null);

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

  @Test
  @DisplayName("웹의 수정·삭제 요청을 직렬화하면, 인증 정보와 멱등키는 본문에서 제외한다")
  void serializeMarkerRequests_webWrites_excludesAuthenticationAndIdempotencyKey() {
    // given: 인증 정보와 멱등키가 있는 웹의 서비스 요청을 준비한다.
    mutationAuthentication = createAuthentication("WEB", null);
    List<Object> requests = List.of(updateRequest(), deleteRequest());

    for (Object request : requests) {
      // when: 멱등성 비교에 사용하는 요청 본문을 JSON으로 변환한다.
      JsonNode body = objectMapper.valueToTree(request);

      // then: 요청 본문은 남기고, 헤더에서 받은 인증 정보와 멱등키는 제외한다.
      assertThat(body.isEmpty()).isFalse();
      assertThat(body.has("authentication")).isFalse();
      assertThat(body.has("idempotencyKey")).isFalse();
    }
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
        updateRequest().toBuilder()
            .authentication(createAuthentication("WEB", null))
            .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
            .type("NOTE")
            .build();

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
        deleteRequest().toBuilder()
            .authentication(createAuthentication("WEB", null))
            .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
            .build();

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
    SuriMapAuthentication requestAuthentication =
        createAuthentication("WEB", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 실제 DB의 마커 출처로 수정·삭제 권한을 판단한다.
    assertThatThrownBy(
            () ->
                markerService.update(
                    updateRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertThatThrownBy(
            () ->
                markerService.delete(
                    deleteRequest().toBuilder()
                        .authentication(requestAuthentication)
                        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("incident_access_denied");
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("이미 삭제된 기준 마커에 새 요청을 보내면, 웹에서 수정·삭제를 거부하고 기록을 유지한다")
  void changeMarker_deletedMarker_rejectsNewRequestWithoutChangingRecords() {
    // given: 삭제 상태의 기준 마커에 새로운 요청 키로 변경을 요청한다.
    prepareMarkerMutation();
    jdbcTemplate.update("UPDATE marker SET status = 'DELETED' WHERE id = ?", MUTATION_MARKER_ID);

    // when & then: 버전 충돌 검사에 앞서 기존 권한 오류로 수정·삭제를 거부한다.
    assertThatThrownBy(() -> markerService.update(updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_access_denied", HttpStatus.FORBIDDEN);
    assertThatThrownBy(() -> markerService.delete(deleteRequest()))
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
                MUTATION_IDEMPOTENCY_KEY))
        .isEmpty();
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
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INCIDENT_CLOSED);
    assertThatThrownBy(() -> markerService.delete(deleteRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INCIDENT_CLOSED);
    assertUnchangedMarker();
  }

  @Test
  @DisplayName("웹에서 경위도 범위를 벗어난 좌표로 수정하면, 오류를 반환하고 기존 마커를 유지한다")
  void updateMarker_invalidLocation_preservesMarkerAndEvents() {
    // given: 수정 권한이 있는 기준 마커에 위도 범위를 벗어난 좌표를 지정한다.
    prepareMarkerMutation();
    MarkerUpdateServiceRequest request =
        updateRequest().toBuilder()
            .location(
                new GeoJsonPoint("Point", List.of(new BigDecimal("126.9"), new BigDecimal("91"))))
            .build();

    // when: 실제 서비스에서 좌표 검증에 실패한다.
    assertThatThrownBy(() -> markerService.update(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);

    // then: 위치·메모·버전을 바꾸거나 수정 이벤트를 남기지 않는다.
    assertUnchangedMarker();
    assertThat(markerMapper.findById(MUTATION_MARKER_ID).orElseThrow().getLocation())
        .isEqualTo(MarkerGeometryFixtures.VALID_MARKER_POINT);
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
  void changeMarker_appAuthentication_rejectsBeforeReusingStoredResponse() {
    // given: 웹에서 처리한 수정 응답이 있고, 같은 요청 키를 가진 앱 인증이 있다.
    prepareMarkerMutation();
    markerService.update(updateRequest());
    SuriMapAuthentication appAuthentication = createAuthentication("APP", ASSIGNED_POLICE_PHONE_ID);

    // when & then: 저장된 응답을 반환하기 전에 서비스의 채널 경계를 검사한다.
    assertThatThrownBy(
            () ->
                markerService.update(
                    updateRequest().toBuilder()
                        .authentication(appAuthentication)
                        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
        .isEqualTo("channel_not_allowed");
    assertThatThrownBy(
            () ->
                markerService.delete(
                    deleteRequest().toBuilder()
                        .authentication(appAuthentication)
                        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error")
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
    mutationAuthentication = createAuthentication("WEB", null);
    insertMarker(MarkerSource.MOCK_SEED, null);
  }

  private SuriMapAuthentication createAuthentication(String channel, UUID policePhoneId) {
    return new SuriMapAuthentication(PRECINCT_TEAM_ID, channel, policePhoneId);
  }

  private MarkerUpdateServiceRequest updateRequest() {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .memo("updated clue memo")
        .authentication(mutationAuthentication)
        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
        .build();
  }

  private MarkerDeleteServiceRequest deleteRequest() {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MUTATION_MARKER_ID)
        .version(1L)
        .reason("wrong marker")
        .authentication(mutationAuthentication)
        .idempotencyKey(MUTATION_IDEMPOTENCY_KEY)
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
