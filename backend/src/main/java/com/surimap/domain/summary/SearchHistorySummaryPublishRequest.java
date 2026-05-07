package com.surimap.domain.summary;

import java.util.UUID;

/**
 * PublishRequest payload for SEARCH_HISTORY_SUMMARY_CHANGED event. Fields match S8.json
 * §events_published SEARCH_HISTORY_SUMMARY_CHANGED payload_schema.
 */
public class SearchHistorySummaryPublishRequest {

  public static final String EVENT_TYPE = "SEARCH_HISTORY_SUMMARY_CHANGED";

  private final String eventId;
  private final String type;
  private final UUID id;
  private final UUID incidentId;
  private final UUID opId;
  private final String status;
  private final long version;

  public SearchHistorySummaryPublishRequest(
      String eventId,
      UUID id,
      UUID incidentId,
      UUID opId,
      String status,
      long version) {
    this.eventId = eventId;
    this.type = EVENT_TYPE;
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.status = status;
    this.version = version;
  }

  public String getEventId() {
    return eventId;
  }

  public String getType() {
    return type;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public UUID getOpId() {
    return opId;
  }

  public String getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }
}
