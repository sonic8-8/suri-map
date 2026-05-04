package com.surimap.operationalperiod.fixture;

import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.List;
import java.util.UUID;

/**
 * L3-T05A OP1 bootstrap fixture.
 */
public final class OpBootstrapFixtures {

    private OpBootstrapFixtures() {}

    /** OP1 bootstrap 시 S4로 발행해야 하는 이벤트 기대값. */
    public static OperationalPeriodFixtures.OpTransitionedEvent op1BootstrappedEvent() {
        return op1BootstrappedEvent(OperationalPeriodFixtures.INCIDENT_ID);
    }

    /** 지정 incident OP1 bootstrap 시 S4로 발행해야 하는 이벤트 기대값. */
    public static OperationalPeriodFixtures.OpTransitionedEvent op1BootstrappedEvent(UUID incidentId) {
        return new OperationalPeriodFixtures.OpTransitionedEvent(
                null,
                "OP_TRANSITIONED",
                OperationalPeriodFixtures.CURRENT_OP_ID,
                incidentId,
                OperationalPeriodFixtures.CURRENT_OP_ID,
                OperationalPeriodFixtures.CURRENT_OP_STATUS,
                OperationalPeriodFixtures.CURRENT_OP_VERSION,
                OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
                null,
                OperationalPeriodFixtures.CURRENT_OP_ID
        );
    }

    /** S1-1 INCIDENT_CREATED 소비 mock 입력 fixture. */
    public static IncidentCreatedEvent incidentCreatedEvent() {
        return new IncidentCreatedEvent("INCIDENT_CREATED", OperationalPeriodFixtures.INCIDENT_ID);
    }

    /** OP1 bootstrap mock completion 기대값. */
    public static Op1BootstrapCompletion op1BootstrapCompletion() {
        return new Op1BootstrapCompletion(
                OperationalPeriodFixtures.INCIDENT_ID,
                OperationalPeriodFixtures.CURRENT_OP_ID,
                OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
                OperationalPeriodFixtures.CURRENT_OP_STATUS,
                OperationalPeriodFixtures.CURRENT_OP_VERSION,
                null,
                OperationalPeriodFixtures.CURRENT_OP_ID,
                op1BootstrappedEvent(),
                OperationalPeriodFixtures.currentOpResult(),
                List.of(OperationalPeriodFixtures.currentOpListRow())
        );
    }

    /** S1-1 INCIDENT_CREATED 소비 mock 입력 비교 모델. */
    public record IncidentCreatedEvent(
            String type,
            UUID incidentId
    ) {}

    /** OP1 bootstrap completion mock 출력 비교 모델. */
    public record Op1BootstrapCompletion(
            UUID incidentId,
            UUID opId,
            int sequenceNo,
            String status,
            long version,
            UUID fromOpId,
            UUID toOpId,
            OperationalPeriodFixtures.OpTransitionedEvent event,
            CurrentOpResult currentOp,
            List<OperationalPeriodRow> listRows
    ) {}
}
