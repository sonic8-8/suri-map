package com.surimap.marker.notification.service;

import java.util.Map;
import java.util.Objects;

public record BoardToastEvidence(
    String slot,
    String eventId,
    String type,
    String id,
    String markerId,
    String incidentId,
    String opId,
    String policePhoneId,
    String status,
    int version) {

  private static final String SLOT = "toast";

  public static BoardToastEvidence fromSupportRequest(String eventId, Map<String, Object> payload) {
    return fromNotificationPayload(eventId, payload);
  }

  public static BoardToastEvidence fromNotificationPayload(
      String eventId, Map<String, Object> payload) {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    return new BoardToastEvidence(
        SLOT,
        eventId,
        requiredString(payload, "type"),
        requiredString(payload, "id"),
        requiredString(payload, "markerId"),
        requiredString(payload, "incidentId"),
        requiredString(payload, "opId"),
        requiredString(payload, "policePhoneId"),
        requiredString(payload, "status"),
        requiredInt(payload, "version"));
  }

  private static String requiredString(Map<String, Object> payload, String field) {
    Object value = payload.get(field);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw new IllegalArgumentException("payload." + field + " must be a non-blank string");
    }
    return stringValue;
  }

  private static int requiredInt(Map<String, Object> payload, String field) {
    Object value = payload.get(field);
    if (value instanceof Number numberValue) {
      return numberValue.intValue();
    }
    if (value instanceof String stringValue) {
      try {
        return Integer.parseInt(stringValue);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("payload." + field + " must be an integer", exception);
      }
    }
    throw new IllegalArgumentException("payload." + field + " must be an integer");
  }
}
