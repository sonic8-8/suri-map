package com.surimap.marker.domain.validation;

import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import java.util.Objects;
import java.util.UUID;

/**
 * S5 marker create의 current OP binding 검증 skeleton.
 *
 * <p>L5-T03A red test는 이 클래스가 아직 S8 current OP query 결과로 op_required/op_mismatch를 판정하지 않는 상태를 고정한다.
 */
public class MarkerOpBindingValidator {

  private final OperationalPeriodQueryPort operationalPeriodQueryPort;

  /**
   * MarkerOpBindingValidator를 생성한다.
   *
   * @param operationalPeriodQueryPort S8 current OP query port
   */
  public MarkerOpBindingValidator(OperationalPeriodQueryPort operationalPeriodQueryPort) {
    this.operationalPeriodQueryPort =
        Objects.requireNonNull(
            operationalPeriodQueryPort, "operationalPeriodQueryPort is required.");
  }

  /**
   * 요청 opId가 사건의 current OP와 일치하는지 검증한다.
   *
   * @param incidentId 사건 ID
   * @param requestOpId 요청 opId
   */
  public void validate(UUID incidentId, UUID requestOpId) {
    // RED skeleton: L5-T03B에서 current OP 없음과 request/current OP 불일치를 구현한다.
  }
}
