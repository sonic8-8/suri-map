package com.surimap.incident.domain;

import java.util.UUID;

/** mock-112 webhook idempotency row. */
public class Mock112WebhookEventRecord {

  private String eventId;
  private String eventType;
  private UUID sourceIncidentId;
  private String requestBodyHash;
  private String webhookStatus;
  private UUID incidentId;
  private String incidentStatus;
  private Long incidentVersion;
  private String responseBodyJson;

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public UUID getSourceIncidentId() {
    return sourceIncidentId;
  }

  public void setSourceIncidentId(UUID sourceIncidentId) {
    this.sourceIncidentId = sourceIncidentId;
  }

  public String getRequestBodyHash() {
    return requestBodyHash;
  }

  public void setRequestBodyHash(String requestBodyHash) {
    this.requestBodyHash = requestBodyHash;
  }

  public String getWebhookStatus() {
    return webhookStatus;
  }

  public void setWebhookStatus(String webhookStatus) {
    this.webhookStatus = webhookStatus;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public String getIncidentStatus() {
    return incidentStatus;
  }

  public void setIncidentStatus(String incidentStatus) {
    this.incidentStatus = incidentStatus;
  }

  public Long getIncidentVersion() {
    return incidentVersion;
  }

  public void setIncidentVersion(Long incidentVersion) {
    this.incidentVersion = incidentVersion;
  }

  public String getResponseBodyJson() {
    return responseBodyJson;
  }

  public void setResponseBodyJson(String responseBodyJson) {
    this.responseBodyJson = responseBodyJson;
  }
}
