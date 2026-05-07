package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodQueryFixtures;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** L3-T05A OperationalPeriodQuery mock test double. */
public final class OperationalPeriodQueryMock implements OperationalPeriodQuery {

  @Override
  public Optional<CurrentOpResult> current(UUID incidentId) {
    if (OperationalPeriodFixtures.INCIDENT_ID.equals(incidentId)) {
      return Optional.of(OperationalPeriodFixtures.currentOpResult(incidentId));
    }
    return Optional.empty();
  }

  @Override
  public List<OperationalPeriodRow> list(UUID incidentId) {
    if (OperationalPeriodFixtures.INCIDENT_ID.equals(incidentId)) {
      return List.of(OperationalPeriodQueryFixtures.currentOp1());
    }
    return List.of();
  }
}
