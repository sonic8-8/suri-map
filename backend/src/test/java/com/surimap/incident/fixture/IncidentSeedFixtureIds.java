package com.surimap.incident.fixture;

import java.util.List;

/** L1-T07 SC-01/02/12 대표 사건 fixture ID 모음. */
public record IncidentSeedFixtureIds(
    String sourceIncidentId,
    String incidentId,
    String opId,
    List<String> markerIds,
    String pathVehicleId,
    String pathFootId,
    String memoId,
    List<String> accountIds,
    List<String> policePhoneIds,
    List<String> beforeHandoverAssignmentIds,
    List<String> afterHandoverAssignmentIds,
    List<String> afterSupportAssignmentIds,
    List<SeedMarker> seedMarkers) {

  public IncidentSeedFixtureIds {
    markerIds = List.copyOf(markerIds);
    accountIds = List.copyOf(accountIds);
    policePhoneIds = List.copyOf(policePhoneIds);
    beforeHandoverAssignmentIds = List.copyOf(beforeHandoverAssignmentIds);
    afterHandoverAssignmentIds = List.copyOf(afterHandoverAssignmentIds);
    afterSupportAssignmentIds = List.copyOf(afterSupportAssignmentIds);
    seedMarkers = List.copyOf(seedMarkers);
  }

  public record SeedMarker(String type, String source, String memo, double lon, double lat) {}
}
