package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.fixture.OpBootstrapFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L3-T05A OP1 bootstrap handler mock.
 *
 * <p>L1은 INCIDENT_CREATED fixture를 넣어 OP1 completion과 current/list OP 응답을 소비한다.</p>
 */
public final class Op1BootstrapMock {

    private final Map<UUID, OpBootstrapFixtures.Op1BootstrapCompletion> completions = new LinkedHashMap<>();
    private int publishRequestCount;

    public OpBootstrapFixtures.Op1BootstrapCompletion handle(OpBootstrapFixtures.IncidentCreatedEvent event) {
        OpBootstrapFixtures.Op1BootstrapCompletion existing = completions.get(event.incidentId());
        if (existing != null) {
            return existing;
        }

        OpBootstrapFixtures.Op1BootstrapCompletion completion = new OpBootstrapFixtures.Op1BootstrapCompletion(
                event.incidentId(),
                OperationalPeriodFixtures.CURRENT_OP_ID,
                OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
                OperationalPeriodFixtures.CURRENT_OP_STATUS,
                OperationalPeriodFixtures.CURRENT_OP_VERSION,
                null,
                OperationalPeriodFixtures.CURRENT_OP_ID,
                OpBootstrapFixtures.op1BootstrappedEvent(event.incidentId()),
                OperationalPeriodFixtures.currentOpResult(event.incidentId()),
                List.of(OperationalPeriodFixtures.currentOpListRow(event.incidentId()))
        );
        completions.put(event.incidentId(), completion);
        publishRequestCount++;
        return completion;
    }

    public int publishRequestCount() {
        return publishRequestCount;
    }
}
