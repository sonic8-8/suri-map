package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.command.InitialOperationalPeriodCreator;
import com.surimap.operationalperiod.command.InitialOperationalPeriodResult;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures.OpTransitionedEvent;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** L3-T05A OP1 자동 생성 mock test double. */
public final class InitialOperationalPeriodCreatorMock implements InitialOperationalPeriodCreator {

  private final Map<UUID, OperationalPeriodRow> createdRows = new LinkedHashMap<>();
  private final Map<UUID, RuntimeException> creationFailures = new LinkedHashMap<>();
  private final List<OpTransitionedEvent> publishedEvents = new ArrayList<>();

  @Override
  public InitialOperationalPeriodResult createOp1(UUID incidentId) {
    if (!OperationalPeriodFixtures.INCIDENT_ID.equals(incidentId)) {
      throw new IllegalArgumentException("unknown incidentId: " + incidentId);
    }
    RuntimeException failure = creationFailures.get(incidentId);
    if (failure != null) {
      throw failure;
    }
    OperationalPeriodRow row =
        createdRows.computeIfAbsent(
            incidentId,
            ignored -> {
              publishedEvents.add(
                  new OpTransitionedEvent(
                      UUID.randomUUID(),
                      "OP_TRANSITIONED",
                      OperationalPeriodFixtures.CURRENT_OP_ID,
                      OperationalPeriodFixtures.INCIDENT_ID,
                      OperationalPeriodFixtures.CURRENT_OP_ID,
                      OperationalPeriodFixtures.CURRENT_OP_STATUS,
                      OperationalPeriodFixtures.CURRENT_OP_VERSION,
                      OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
                      null,
                      OperationalPeriodFixtures.CURRENT_OP_ID));
              return OperationalPeriodFixtures.currentOpListRow();
            });
    return new InitialOperationalPeriodResult(row);
  }

  public void failOp1CreationFor(UUID incidentId, RuntimeException failure) {
    if (!OperationalPeriodFixtures.INCIDENT_ID.equals(incidentId)) {
      throw new IllegalArgumentException("unknown incidentId: " + incidentId);
    }
    creationFailures.put(incidentId, Objects.requireNonNull(failure, "failure must not be null"));
  }

  public List<OperationalPeriodRow> createdRows() {
    return List.copyOf(createdRows.values());
  }

  public List<OpTransitionedEvent> publishedEvents() {
    return List.copyOf(publishedEvents);
  }
}
