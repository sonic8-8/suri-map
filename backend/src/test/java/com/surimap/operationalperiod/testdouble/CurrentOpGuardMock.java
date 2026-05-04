package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.fixture.CurrentOpGuardFixtures;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import java.util.Optional;
import java.util.UUID;

/**
 * L3-T05A @RequireCurrentOp guard mock.
 *
 * <p>S3-1/S5 field write contract test가 current OP 필수/불일치 실패를 재현할 수 있게 한다.</p>
 */
public final class CurrentOpGuardMock {

    private final OperationalPeriodQuery opQuery;

    public CurrentOpGuardMock(OperationalPeriodQuery opQuery) {
        this.opQuery = opQuery;
    }

    public CurrentOpGuardFixtures.CurrentOpGuardDecision requireCurrentOp(UUID incidentId, UUID requestedOpId) {
        Optional<CurrentOpResult> currentOp = opQuery.current(incidentId);
        if (currentOp.isEmpty()) {
            return CurrentOpGuardFixtures.currentOpRejected(CurrentOpGuardFixtures.opRequiredGuard());
        }

        UUID currentOpId = currentOp.get().opId();
        if (!currentOpId.equals(requestedOpId)) {
            return CurrentOpGuardFixtures.currentOpRejected(CurrentOpGuardFixtures.opMismatchGuard());
        }

        return CurrentOpGuardFixtures.currentOpAllowed(currentOpId);
    }
}
