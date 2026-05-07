package com.surimap.marker.domain;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 마커 생성 시 OP 연결 검증 red test.
 *
 * <p>SC-06 harness red test 기준: - active OP 없으면 409 op_required - request.opId != currentOp.id 이면
 * 409 op_mismatch
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
@DisplayName("마커 OP 연결 검증 red test")
class MarkerOpBindingRedTest {

  /** 정상 stub: OP1 반환 */
  private final OperationalPeriodQueryPort activeOpQuery = incidentId -> Optional.of(OP_ID);

  /** 실패 stub: OP 없음 */
  private final OperationalPeriodQueryPort emptyOpQuery = incidentId -> Optional.empty();

  private final MarkerOpBindingValidator validator = new MarkerOpBindingValidator(activeOpQuery);

  // ══════════════════════════════════════════════════════
  // 정상 케이스
  // ══════════════════════════════════════════════════════

  @Nested
  @DisplayName("OP 연결 성공")
  class ValidBinding {

    @Test
    @DisplayName("request.opId == currentOp.id → 바인딩 성공")
    void matchingOpId_binds() {
      assertThat(validator.validate(INCIDENT_ID, OP_ID)).isEqualTo(OP_ID);
    }
  }

  // ══════════════════════════════════════════════════════
  // 실패 케이스
  // ══════════════════════════════════════════════════════

  @Nested
  @DisplayName("OP 연결 실패")
  class InvalidBinding {

    @Test
    @DisplayName("active OP 없으면 → op_required")
    void noActiveOp_rejected() {
      MarkerOpBindingValidator noActiveOpValidator = new MarkerOpBindingValidator(emptyOpQuery);

      assertThatThrownBy(() -> noActiveOpValidator.validate(INCIDENT_ID, OP_ID))
          .isInstanceOf(OpRequiredException.class)
          .extracting("errorCode")
          .isEqualTo("op_required");
    }

    @Test
    @DisplayName("request.opId != currentOp.id → op_mismatch")
    void mismatchedOpId_rejected() {
      UUID wrongOpId = UUID.fromString("00000000-0000-0000-0000-999999999999");

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, wrongOpId))
          .isInstanceOf(OpMismatchException.class)
          .extracting("errorCode")
          .isEqualTo("op_mismatch");
    }
  }
}
