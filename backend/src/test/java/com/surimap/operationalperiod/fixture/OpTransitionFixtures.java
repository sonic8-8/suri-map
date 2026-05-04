package com.surimap.operationalperiod.fixture;

import java.util.List;
import java.util.UUID;

/**
 * S8 SC-10 OP 전환 수렴 fixture.
 */
public final class OpTransitionFixtures {

    /** OP 전환 시 S4로 발행해야 하는 이벤트 UUID. */
    public static final UUID OP_TRANSITIONED_EVENT_ID =
            UUID.fromString("66666666-6666-6666-6666-666666660001");

    /** SC-10 상황판 수렴을 확인할 때 관찰해야 하는 슬롯 목록과 선행 상태. */
    public static final List<String> SC10_BOARD_PROBE_SLOTS = List.of(
            "op_toggle",
            "op_history",
            "handover_status"
    );
    public static final String SC10_HANDOVER_STATUS = "NEEDS_MEMO";
    public static final String PRE_CONVERGENCE_STATE = "PENDING_PROJECTION";

    private OpTransitionFixtures() {}

    /** OP 전환 REST 응답 기대값. */
    public static OpTransitionRestResponse opTransitionRestResponse() {
        return new OpTransitionRestResponse(
                OperationalPeriodFixtures.NEW_OP_ID,
                OperationalPeriodFixtures.NEW_OP_STATUS,
                OperationalPeriodFixtures.NEW_OP_VERSION,
                OperationalPeriodFixtures.NEW_OP_SEQUENCE_NO
        );
    }

    /** OP 전환 시 S4로 발행해야 하는 이벤트 기대값. */
    public static OperationalPeriodFixtures.OpTransitionedEvent opTransitionedEvent() {
        return new OperationalPeriodFixtures.OpTransitionedEvent(
                OP_TRANSITIONED_EVENT_ID,
                "OP_TRANSITIONED",
                OperationalPeriodFixtures.NEW_OP_ID,
                OperationalPeriodFixtures.INCIDENT_ID,
                OperationalPeriodFixtures.NEW_OP_ID,
                OperationalPeriodFixtures.NEW_OP_STATUS,
                OperationalPeriodFixtures.NEW_OP_VERSION,
                OperationalPeriodFixtures.NEW_OP_SEQUENCE_NO,
                OperationalPeriodFixtures.PREVIOUS_OP_ID,
                OperationalPeriodFixtures.NEW_OP_ID
        );
    }

    /** SC-10 상황판 슬롯들이 새 OP 상태로 수렴했는지 확인하는 데이터. */
    public static OpTransitionBoardProbe opTransitionBoardProbe() {
        return new OpTransitionBoardProbe(
                SC10_BOARD_PROBE_SLOTS,
                OperationalPeriodFixtures.NEW_OP_ID,
                OperationalPeriodFixtures.NEW_OP_STATUS,
                OperationalPeriodFixtures.NEW_OP_VERSION,
                OperationalPeriodFixtures.NEW_OP_ID,
                SC10_HANDOVER_STATUS,
                PRE_CONVERGENCE_STATE
        );
    }

    /** OP 전환 REST 응답 비교 모델. */
    public record OpTransitionRestResponse(
            UUID id,
            String status,
            long version,
            int sequenceNo
    ) {}

    /** SC-10 상황판 수렴 상태 비교 모델. */
    public record OpTransitionBoardProbe(
            List<String> slots,
            UUID id,
            String status,
            long version,
            UUID opId,
            String handoverStatus,
            String preConvergenceState
    ) {}
}
