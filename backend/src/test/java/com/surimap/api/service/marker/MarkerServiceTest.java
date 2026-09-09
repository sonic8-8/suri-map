package com.surimap.api.service.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.surimap.api.service.marker.response.MarkerListServiceResponse;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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
}
