package com.surimap.domain.marker;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MarkerWriteAccessValidatorTest {

  private final MarkerWriteAccessValidator validator = new MarkerWriteAccessValidator();

  @Test
  @DisplayName("열린 사건에 배정되어 현재 수색 차수에서 근무 중이면, 마커 생성과 사진 기록을 허용한다")
  void validateAccess_openIncidentWithAssignmentAndDutyShift_allowsWrites() {
    // given: DB나 Spring 없이 권한 조건을 충족하는 조회 값을 준비한다.
    MarkerWriteAccessData accessData = createAllowedAccessData();

    // when & then: 같은 조회 값으로 생성·수정·사진과 생성 전 사진 업로드의 조건을 검사한다.
    assertThatCode(() -> validator.validateCreateAccess(accessData, OP1_ID))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateUploadBeforeMarkerCreationAccess(accessData, OP1_ID))
        .doesNotThrowAnyException();
    assertThatCode(() -> validator.validateIncidentAccess(accessData)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validatePhotoAccess(accessData, OP1_ID))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createDeniedAccessData")
  @DisplayName("마커 생성 조건을 충족하지 못하면, 마커 생성과 생성 전 사진 업로드를 해당 오류 코드로 거부한다")
  void validateAccess_unmetCreationCondition_rejectsWithExpectedError(
      String condition, MarkerWriteAccessData accessData, ErrorCode expectedError) {
    // given: 사건·수색 차수·배정·근무교대 중 한 조건을 충족하지 못한다.
    // when & then: DB 조회 없이 전달받은 값으로 거부 사유를 판단한다.
    assertThatThrownBy(() -> validator.validateCreateAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(expectedError);
    assertThatThrownBy(() -> validator.validateUploadBeforeMarkerCreationAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(expectedError);
  }

  @Test
  @DisplayName("사건 종료와 차수 부재가 겹치면, 마커 생성은 사건 종료를 먼저 반환하고 생성 전 사진 업로드는 차수 오류를 먼저 반환한다")
  void validateAccess_closedIncidentWithoutOp_preservesDifferentErrorOrder() {
    // given: 사건 종료·차수 부재·배정 부재가 겹친다.
    MarkerWriteAccessData accessData =
        MarkerWriteAccessData.builder().incidentStatus("CLOSED").build();

    // when & then: 마커 생성과 생성 전 사진 업로드의 서로 다른 검사 순서를 유지한다.
    assertThatThrownBy(() -> validator.validateCreateAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INCIDENT_CLOSED);
    assertThatThrownBy(() -> validator.validateUploadBeforeMarkerCreationAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_REQUIRED);
  }

  @Test
  @DisplayName("사건 종료와 차수 불일치가 겹치면, 생성 전 사진 업로드는 차수 불일치 오류를 먼저 반환한다")
  void validateUploadAccess_opMismatchOnClosedIncident_rejectsOpFirst() {
    // given: 사건이 종료되었고 현재 수색 차수도 요청과 다르다.
    MarkerWriteAccessData accessData =
        createAllowedAccessData().toBuilder().incidentStatus("CLOSED").currentOpId(OP2_ID).build();

    // when & then: 기존 생성 전 사진 업로드의 수색 차수 검사 순서를 유지한다.
    assertThatThrownBy(() -> validator.validateUploadBeforeMarkerCreationAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_MISMATCH);
  }

  @Test
  @DisplayName("요청한 수색 차수가 없으면, 현재 차수가 있어도 차수 필수 오류로 거부한다")
  void validateAccess_missingRequestedOp_rejectsWithOpRequired() {
    // given: 나머지 권한 조건은 유효하지만 요청의 수색 차수가 없다.
    MarkerWriteAccessData accessData = createAllowedAccessData();

    // when & then: 별도 조회 없이 기존 수색 차수 필수 조건을 검사한다.
    assertThatThrownBy(() -> validator.validateCreateAccess(accessData, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_REQUIRED);
    assertThatThrownBy(() -> validator.validateUploadBeforeMarkerCreationAccess(accessData, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_REQUIRED);
  }

  @Test
  @DisplayName("차수 불일치와 배정 부재가 겹치면, 마커 생성은 차수를 먼저 검사하고 기존 사진 기록은 배정을 먼저 검사한다")
  void validateAccess_opMismatchWithoutAssignment_preservesDifferentErrorOrder() {
    // given: 요청 차수가 현재 차수와 다르고 계정의 활성 배정도 없다.
    MarkerWriteAccessData accessData =
        createAllowedAccessData().toBuilder()
            .currentOpId(OP2_ID)
            .activeAssignmentCount(0)
            .activeIncidentAssignmentCount(0)
            .build();

    // when & then: 생성과 기존 마커의 사진 기록에서 각각 기존 오류 순서를 지킨다.
    assertThatThrownBy(() -> validator.validateCreateAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_MISMATCH);
    assertThatThrownBy(() -> validator.validatePhotoAccess(accessData, OP1_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TEAM_NOT_ASSIGNED);
  }

  @Test
  @DisplayName("열린 사건에 배정되어 있으면, 기존 마커의 수정·삭제 검사에는 활성 차수와 근무교대를 요구하지 않는다")
  void validateIncidentAccess_withoutOpOrDutyShift_allowsAssignedAccount() {
    // given: 사건 배정은 유효하지만 활성 차수와 근무교대는 없다.
    MarkerWriteAccessData accessData =
        createAllowedAccessData().toBuilder().currentOpId(null).activeDutyShiftId(null).build();

    // when & then: 생성에만 필요한 조건을 기존 마커의 수정·삭제 검사에 추가하지 않는다.
    assertThatCode(() -> validator.validateIncidentAccess(accessData)).doesNotThrowAnyException();
  }

  private static Stream<Arguments> createDeniedAccessData() {
    MarkerWriteAccessData allowed = createAllowedAccessData();
    return Stream.of(
        Arguments.of(
            "사건 부재",
            allowed.toBuilder().incidentStatus(null).build(),
            ErrorCode.INCIDENT_ACCESS_DENIED),
        Arguments.of(
            "준비 중인 사건",
            allowed.toBuilder().incidentStatus("BOOTSTRAPPING").build(),
            ErrorCode.INCIDENT_ACCESS_DENIED),
        Arguments.of(
            "종료된 사건",
            allowed.toBuilder().incidentStatus("CLOSED").build(),
            ErrorCode.INCIDENT_CLOSED),
        Arguments.of(
            "현재 수색 차수 부재", allowed.toBuilder().currentOpId(null).build(), ErrorCode.OP_REQUIRED),
        Arguments.of(
            "수색 차수 불일치", allowed.toBuilder().currentOpId(OP2_ID).build(), ErrorCode.OP_MISMATCH),
        Arguments.of(
            "모든 활성 배정 부재",
            allowed.toBuilder().activeAssignmentCount(0).activeIncidentAssignmentCount(0).build(),
            ErrorCode.TEAM_NOT_ASSIGNED),
        Arguments.of(
            "다른 사건에만 배정",
            allowed.toBuilder().activeIncidentAssignmentCount(0).build(),
            ErrorCode.INCIDENT_ACCESS_DENIED),
        Arguments.of(
            "활성 근무교대 부재",
            allowed.toBuilder().activeDutyShiftId(null).build(),
            ErrorCode.POLICE_PHONE_NOT_ASSIGNED));
  }

  private static MarkerWriteAccessData createAllowedAccessData() {
    return MarkerWriteAccessData.builder()
        .incidentStatus("OPEN")
        .currentOpId(OP1_ID)
        .activeAssignmentCount(1)
        .activeIncidentAssignmentCount(1)
        .activeDutyShiftId(UUID.fromString("33333333-3333-3333-3333-333333330001"))
        .build();
  }
}
