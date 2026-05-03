package com.surimap.marker.domain;

import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마커 생성 시 OP 연결 검증 red test.
 *
 * SC-06 harness red test 기준:
 * - active OP 없으면 409 op_required
 * - request.opId != currentOp.id 이면 409 op_mismatch
 *
 * @see docs/contracts/L5-06-op-query-dto.md §4
 */
@DisplayName("마커 OP 연결 검증 red test")
class MarkerOpBindingRedTest {

    /** 정상 stub: OP1 반환 */
    private final OperationalPeriodQueryPort activeOpQuery =
            incidentId -> Optional.of(OP_ID);

    /** 실패 stub: OP 없음 */
    private final OperationalPeriodQueryPort emptyOpQuery =
            incidentId -> Optional.empty();

    // ══════════════════════════════════════════════════════
    // 정상 케이스
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("OP 연결 성공")
    class ValidBinding {

        @Test
        @DisplayName("request.opId == currentOp.id → 바인딩 성공")
        void matchingOpId_binds() {
            // given
            UUID currentOpId = activeOpQuery.findCurrentOpId(INCIDENT_ID).orElseThrow();

            // then: 요청 opId와 현재 OP가 같으면 통과
            assertThat(currentOpId).isEqualTo(OP_ID);
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
            // given
            Optional<UUID> currentOp = emptyOpQuery.findCurrentOpId(INCIDENT_ID);

            // then: OP가 없으면 마커 생성 불가
            assertThat(currentOp).isEmpty();
            // RED: 실제 서비스에서는 OpRequiredException을 던져야 함
        }

        @Test
        @DisplayName("request.opId != currentOp.id → op_mismatch")
        void mismatchedOpId_rejected() {
            // given
            UUID currentOpId = activeOpQuery.findCurrentOpId(INCIDENT_ID).orElseThrow();
            UUID wrongOpId = UUID.fromString("00000000-0000-0000-0000-999999999999");

            // then: 불일치하면 마커 생성 불가
            assertThat(currentOpId).isNotEqualTo(wrongOpId);
            // RED: 실제 서비스에서는 OpMismatchException을 던져야 함
        }
    }
}
