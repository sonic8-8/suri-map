package com.surimap.incident.testdouble;

import java.util.ArrayList;
import java.util.List;

/** S1-3 purge handoff 가드 mock. L1이 purge orchestration을 직접 실행하지 않도록 잡는다. */
public final class MockIncidentPurgeHook {

  private final List<IncidentPublishRequest> closedEvents = new ArrayList<>();
  private int directPurgeRuns;

  public void captureIncidentClosed(IncidentPublishRequest request) {
    if (!"INCIDENT_CLOSED".equals(request.type())) {
      throw new IllegalArgumentException("INCIDENT_CLOSED 종류의 요청만 받습니다");
    }
    closedEvents.add(request);
  }

  public void runDirectPurge(String incidentId) {
    directPurgeRuns++;
    throw new AssertionError("S1-1은 S1-3 purge orchestration을 직접 실행하면 안 됩니다: " + incidentId);
  }

  public List<IncidentPublishRequest> closedEvents() {
    return List.copyOf(closedEvents);
  }

  public int directPurgeRuns() {
    return directPurgeRuns;
  }
}
