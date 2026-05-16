package com.surimap.incident.service;

import com.surimap.incident.exception.IncidentApiException;
import org.springframework.http.HttpStatus;

public enum Mock112WebhookEventType {
  INCIDENT_READY,
  INCIDENT_ASSIGNMENT_CHANGED;

  static Mock112WebhookEventType parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IncidentApiException("mock112_event_invalid", HttpStatus.CONFLICT);
    }
    try {
      return Mock112WebhookEventType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IncidentApiException("mock112_event_invalid", HttpStatus.CONFLICT, exception);
    }
  }
}
