package com.surimap.incident.testdouble;

/** L1 사건 흐름 하네스가 사용하는 S4 PublishRequest capture 모델. */
public record IncidentPublishRequest(
    String eventId, String type, String incidentId, String payloadId, String status, long version) {

  public static IncidentPublishRequest incidentCreated(
      String incidentId, String status, long version) {
    return new IncidentPublishRequest(
        eventId("INCIDENT_CREATED", incidentId, version),
        "INCIDENT_CREATED",
        incidentId,
        incidentId,
        status,
        version);
  }

  public static IncidentPublishRequest assignmentChanged(
      String incidentId, String status, long version) {
    return new IncidentPublishRequest(
        eventId("INCIDENT_ASSIGNMENT_CHANGED", incidentId, version),
        "INCIDENT_ASSIGNMENT_CHANGED",
        incidentId,
        incidentId,
        status,
        version);
  }

  public static IncidentPublishRequest incidentClosed(
      String incidentId, String status, long version) {
    return new IncidentPublishRequest(
        eventId("INCIDENT_CLOSED", incidentId, version),
        "INCIDENT_CLOSED",
        incidentId,
        incidentId,
        status,
        version);
  }

  private static String eventId(String type, String incidentId, long version) {
    return type + ":" + incidentId + ":" + version;
  }
}
