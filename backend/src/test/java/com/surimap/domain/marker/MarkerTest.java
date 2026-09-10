package com.surimap.domain.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

class MarkerTest {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  @ParameterizedTest
  @CsvSource({"APP,true", "WEB,false", "MOCK_SEED,false", "SYSTEM,false"})
  @DisplayName("앱에서 기록한 마커이고 작성 계정이 같으면, 해당 계정의 현장 마커로 판단한다")
  void isFieldMarkerCreatedBy_sourceAndAuthorMatch_identifiesOwnFieldMarker(
      MarkerSource source, boolean expected) {
    // given: 같은 작성 계정이 기록한 마커를 출처별로 준비한다.
    Marker marker = createMarker(source);

    // when: 작성 계정의 현장 마커인지 판단한다.
    boolean ownFieldMarker = marker.isFieldMarkerCreatedBy(marker.getCreatedByAccountId());

    // then: 앱에서 기록한 마커만 해당하며, 다른 계정이나 계정 누락은 해당하지 않는다.
    assertThat(ownFieldMarker).isEqualTo(expected);
    assertThat(marker.isFieldMarkerCreatedBy(PRECINCT_TEAM_ID)).isFalse();
    assertThat(marker.isFieldMarkerCreatedBy(null)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"APP,false", "WEB,false", "MOCK_SEED,true", "SYSTEM,true"})
  @DisplayName("출처가 MOCK_SEED 또는 SYSTEM이면, 웹에서 보정할 수 있는 기준 마커로 판단한다")
  void isReferenceMarker_seedOrSystemSource_identifiesReferenceMarker(
      MarkerSource source, boolean expected) {
    // given: 현장 기록·웹 기록·초기 기준점·시스템 마커를 준비한다.
    Marker marker = createMarker(source);

    // when: 웹 보정 대상인 기준 마커인지 판단한다.
    boolean referenceMarker = marker.isReferenceMarker();

    // then: 초기 기준점과 시스템 마커만 기준 마커로 분류한다.
    assertThat(referenceMarker).isEqualTo(expected);
  }

  @Test
  @DisplayName("마커를 삭제하면, 생성·수정 상태와 구분해 삭제 상태로 판단한다")
  void isDeleted_afterDeletion_distinguishesActiveAndUpdatedStates() {
    // given: 새 마커와 수정한 마커는 삭제 상태가 아니다.
    Marker marker = createMarker();
    assertThat(marker.isDeleted()).isFalse();
    marker.markUpdated(1L);
    assertThat(marker.isDeleted()).isFalse();

    // when: 현재 버전으로 마커를 삭제한다.
    marker.delete(2L);

    // then: 마커가 자신의 삭제 상태를 판단한다.
    assertThat(marker.isDeleted()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "126.913400,35.163100", "126.904000,35.162000", "126.9134007,35.1631007",
    "127.200000,35.163100", "-180,-90", "180,90"
  })
  @DisplayName("경위도와 좌표계가 유효하면, 수색구역·소수점 자릿수에 관계없이 좌표를 허용한다")
  void validateLocation_validCoordinates_acceptsWithoutChangingPrecision(
      double longitude, double latitude) {
    // given: 유효한 좌표, 수색구역 밖 좌표, 소수점 7자리 및 경위도 극값을 준비한다.
    Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));

    // when & then: 사건·구역 조회 없이 좌표 자체를 검사하며 반올림하지 않는다.
    assertThatCode(() -> Marker.validateLocation(location)).doesNotThrowAnyException();
    assertThat(location.getX()).isEqualTo(longitude);
    assertThat(location.getY()).isEqualTo(latitude);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createInvalidLocations")
  @DisplayName("좌표가 없거나 좌표계·경위도가 유효하지 않으면, 공통 invalid_geometry 오류로 거부한다")
  void validateLocation_invalidCoordinates_usesCommonBusinessError(
      String condition, Point location) {
    // given: 누락·빈 좌표·좌표계 불일치·숫자 또는 범위 오류가 있는 좌표다.
    // when & then: 좌표 오류는 공통 비즈니스 예외와 기존 오류 코드로 전달한다.
    assertThatThrownBy(() -> Marker.validateLocation(location))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GEOMETRY);
  }

  private static Stream<Arguments> createInvalidLocations() {
    return Stream.of(
        Arguments.of("좌표 누락", null),
        Arguments.of("빈 Point", GEOMETRY_FACTORY.createPoint()),
        Arguments.of("좌표계 불일치", MarkerGeometryFixtures.SRID_MISMATCH_POINT),
        Arguments.of("NaN 좌표", MarkerGeometryFixtures.NAN_POINT),
        Arguments.of(
            "무한대 경도", GEOMETRY_FACTORY.createPoint(new Coordinate(Double.POSITIVE_INFINITY, 35))),
        Arguments.of(
            "무한대 위도", GEOMETRY_FACTORY.createPoint(new Coordinate(126, Double.NEGATIVE_INFINITY))),
        Arguments.of("경위도 교환으로 위도 범위 초과", MarkerGeometryFixtures.LAT_LON_SWAPPED),
        Arguments.of("경도 최솟값 미만", GEOMETRY_FACTORY.createPoint(new Coordinate(-181, 35))),
        Arguments.of("경도 최댓값 초과", GEOMETRY_FACTORY.createPoint(new Coordinate(181, 35))),
        Arguments.of("위도 최솟값 미만", GEOMETRY_FACTORY.createPoint(new Coordinate(126, -91))),
        Arguments.of("위도 최댓값 초과", GEOMETRY_FACTORY.createPoint(new Coordinate(126, 91))));
  }

  @Test
  @DisplayName("마커 변경을 기록하면, 기존 내용은 유지하고 수정 상태와 다음 버전을 기록한다")
  void markUpdated_currentVersion_preservesContentAndAdvancesVersion() {
    // given: 사진 첨부처럼 마커 내용은 유지하면서 변경을 기록해야 한다.
    Marker marker = createMarker();

    // when: 현재 버전의 마커를 수정 상태로 변경한다.
    marker.markUpdated(1L);

    // then: 메모·유형·위치는 유지하고 수정 상태와 버전만 변경한다.
    assertThat(marker.getMemo()).isEqualTo("기존 메모");
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getLocation()).isEqualTo(MarkerGeometryFixtures.VALID_MARKER_POINT);
    assertThat(marker.getStatus()).isEqualTo("UPDATED");
    assertThat(marker.getVersion()).isEqualTo(2L);

    // when & then: 변경을 한 번 더 기록하면 수정 상태를 유지하고 버전은 3이 된다.
    marker.markUpdated(2L);
    assertThat(marker.getStatus()).isEqualTo("UPDATED");
    assertThat(marker.getVersion()).isEqualTo(3L);
  }

  @Test
  @DisplayName("메모만 수정하면, 나머지 값은 유지하고 수정 상태와 다음 버전을 기록한다")
  void update_onlyMemoChanges_preservesOtherFieldsAndAdvancesVersion() {
    // given: 위치와 유형이 정해진 현장 마커를 준비한다.
    Marker marker = createMarker();

    // when: 현재 버전으로 메모만 수정한다.
    marker.update(1L, null, null, "수정한 메모");

    // then: 지정하지 않은 값은 유지하고 수정 상태와 버전을 변경한다.
    assertThat(marker.getMemo()).isEqualTo("수정한 메모");
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getLocation().getX()).isEqualTo(126.913400);
    assertThat(marker.getLocation().getY()).isEqualTo(35.163100);
    assertThat(marker.getStatus()).isEqualTo("UPDATED");
    assertThat(marker.getVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("마커를 삭제하면, 기존 내용은 남기고 삭제 상태와 다음 버전을 기록한다")
  void delete_currentVersion_preservesContentAndMarksDeleted() {
    // given: 현재 버전이 1인 현장 마커를 준비한다.
    Marker marker = createMarker();

    // when: 현재 버전으로 삭제한다.
    marker.delete(1L);

    // then: 내용을 지우지 않고 삭제 상태와 버전만 변경한다.
    assertThat(marker.getMemo()).isEqualTo("기존 메모");
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getLocation().getX()).isEqualTo(126.913400);
    assertThat(marker.getStatus()).isEqualTo("DELETED");
    assertThat(marker.getVersion()).isEqualTo(2L);

    // then: 삭제한 마커는 수정하거나 다시 삭제할 수 없다.
    assertThatThrownBy(() -> marker.update(2L, null, null, "삭제 후 수정"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThatThrownBy(() -> marker.delete(2L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThatThrownBy(() -> marker.markUpdated(2L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThat(marker.getVersion()).isEqualTo(2L);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 2L, 99L})
  @DisplayName("요청 버전이 현재 버전과 다르면, 수정과 삭제를 거부하고 기존 상태를 유지한다")
  void changeMarker_versionMismatch_preservesState(long requestedVersion) {
    // given: 버전이 1인 마커에 다른 버전의 요청을 보낸다.
    Marker marker = createMarker();

    // when & then: 수정과 삭제 모두 같은 버전 규칙으로 거부한다.
    assertThatThrownBy(() -> marker.update(requestedVersion, "NOTE", null, "새 메모"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThatThrownBy(() -> marker.delete(requestedVersion))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThatThrownBy(() -> marker.markUpdated(requestedVersion))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(marker.getMemo()).isEqualTo("기존 메모");
  }

  @Test
  @DisplayName("메모가 2,000자를 넘으면, 다른 수정 내용도 적용하지 않는다")
  void update_memoTooLong_rejectsAllChanges() {
    // given: 유형 변경과 허용 길이를 초과한 메모를 함께 요청한다.
    Marker marker = createMarker();

    // when & then: 메모 검증에 실패하면 일부 필드만 변경하지 않는다.
    assertThatThrownBy(() -> marker.update(1L, "NOTE", null, "m".repeat(2001)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getMemo()).isEqualTo("기존 메모");
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"note", "UNKNOWN"})
  @DisplayName("지원하지 않는 마커 유형으로 수정하면, 기존 값과 버전을 유지한다")
  void update_unknownType_preservesState(String type) {
    // given: API에서 허용하지 않는 유형을 수정 요청에 포함한다.
    Marker marker = createMarker();

    // when & then: 유형 검증에 실패하면 메모와 버전도 변경하지 않는다.
    assertThatThrownBy(() -> marker.update(1L, type, null, "새 메모"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WRITE_CONFLICT);
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getMemo()).isEqualTo("기존 메모");
    assertThat(marker.getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("유형은 공백이고 메모는 빈 문자열이면, 유형은 유지하고 메모만 비운다")
  void update_blankTypeAndEmptyMemo_preservesTypeAndClearsMemo() {
    // given: 값 생략과 빈 문자열의 의미를 구분하는 수정 요청이다.
    Marker marker = createMarker();

    // when: 유형을 공백으로, 메모를 빈 문자열로 지정한다.
    marker.update(1L, " ", null, "");

    // then: 공백 유형은 변경하지 않고 빈 메모는 그대로 반영한다.
    assertThat(marker.getMarkerType()).isEqualTo("CLUE");
    assertThat(marker.getMemo()).isEmpty();
    assertThat(marker.getVersion()).isEqualTo(2L);
  }

  private Marker createMarker() {
    return createMarker(MarkerSource.APP);
  }

  private Marker createMarker(MarkerSource source) {
    return Marker.builder()
        .id(UUID.fromString("55555555-5555-5555-5555-555555550072"))
        .incidentId(MarkerGeometryFixtures.INCIDENT_ID)
        .operationalPeriodId(MarkerGeometryFixtures.OP1_ID)
        .markerType(MarkerType.CLUE)
        .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
        .memo("기존 메모")
        .occurredAt(Instant.parse("2026-04-28T00:05:00Z"))
        .createdByAccountId(UUID.fromString("11111111-1111-1111-1111-111111110072"))
        .policePhoneId(UUID.fromString("22222222-2222-2222-2222-222222220072"))
        .markerSource(source)
        .status(MarkerStatus.ACTIVE)
        .version(1L)
        .build();
  }
}
