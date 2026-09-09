package com.surimap.domain.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkerTest {

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
        .markerSource(MarkerSource.APP)
        .status(MarkerStatus.ACTIVE)
        .version(1L)
        .build();
  }
}
