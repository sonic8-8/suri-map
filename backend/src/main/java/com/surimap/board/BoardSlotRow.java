package com.surimap.board;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record BoardSlotRow(
    String id,
    String boardRowId,
    String status,
    long version,
    long sequence,
    String sourceSpec,
    String sourceHash,
    String latestEventId,
    Map<String, Object> payload)
    implements BoardRowCursor {

  private static final List<String> INCIDENT_TERMINAL_PAYLOAD_KEYS =
      List.of(
          "incidentId",
          "terminalStatus",
          "closedStatus",
          "closedAt",
          "writeDisabledReason",
          "localPurgeState");
  private static final Set<String> TERMINAL_STATUSES = Set.of("CLOSED", "PURGE_PENDING", "PURGED");
  private static final Set<String> TERMINAL_STATUS_VALUES =
      Set.of("OPEN", "CLOSED", "PURGE_PENDING", "PURGED");
  private static final Set<String> CLOSED_STATUS_VALUES =
      Set.of("not_closed", "closed", "purge_pending", "purged");
  private static final Set<String> WRITE_DISABLED_REASON_VALUES =
      Set.of("none", "incident_closed", "purged");
  private static final Set<String> LOCAL_PURGE_STATE_VALUES =
      Set.of("not_started", "queued", "in_progress", "completed", "failed_retryable");

  public BoardSlotRow {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(boardRowId, "boardRowId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(sourceHash, "sourceHash must not be null");
    Objects.requireNonNull(latestEventId, "latestEventId must not be null");
    payload =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(payload, "payload must not be null")));
  }

  static BoardSlotRow from(BoardSourceRow row) {
    return new BoardSlotRow(
        row.sourceResponseId(),
        row.boardRowId(),
        row.status(),
        row.version(),
        row.sequence(),
        row.sourceSpec(),
        row.sourceHash(),
        row.latestEventId(),
        responsePayload(row));
  }

  static boolean isTerminalIncidentRow(BoardSourceRow row) {
    if (!row.slot().equals("incident_terminal")) {
      return false;
    }
    return TERMINAL_STATUSES.contains(terminalStatus(row));
  }

  Map<String, Object> responseFields() {
    Map<String, Object> fields = new LinkedHashMap<>(payload);
    fields.put("id", id);
    fields.put("status", status);
    fields.put("version", version);
    fields.put("sequence", sequence);
    fields.put("sourceSpec", sourceSpec);
    fields.put("sourceHash", sourceHash);
    fields.put("latestEventId", latestEventId);
    return Collections.unmodifiableMap(fields);
  }

  private static Map<String, Object> responsePayload(BoardSourceRow row) {
    if (!row.slot().equals("incident_terminal")) {
      return row.payload();
    }
    return incidentTerminalPayload(row);
  }

  private static Map<String, Object> incidentTerminalPayload(BoardSourceRow row) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    boolean closedTerminal = requiresClosedAt(row);
    for (String key : INCIDENT_TERMINAL_PAYLOAD_KEYS) {
      if (!row.payload().containsKey(key)) {
        if (key.equals("closedAt") && !closedTerminal) {
          continue;
        }
        throw new IllegalArgumentException(
            "incident_terminal payload missing required field: " + key);
      }
      Object value = row.payload().get(key);
      if (value == null) {
        if (key.equals("closedAt")) {
          if (closedTerminal) {
            throw new IllegalArgumentException(
                "incident_terminal closedAt is required for terminal rows");
          }
        } else {
          throw new IllegalArgumentException(
              "incident_terminal payload missing required field: " + key);
        }
      }
      validateTerminalPayloadValue(key, value);
      sanitized.put(key, value);
    }
    return sanitized;
  }

  private static boolean requiresClosedAt(BoardSourceRow row) {
    return TERMINAL_STATUSES.contains(terminalStatus(row));
  }

  private static String terminalStatus(BoardSourceRow row) {
    Object value = row.payload().get("terminalStatus");
    if (value == null) {
      throw new IllegalArgumentException(
          "incident_terminal payload missing required field: terminalStatus");
    }
    String terminalStatus = String.valueOf(value);
    validateEnum("terminalStatus", terminalStatus, TERMINAL_STATUS_VALUES);
    return terminalStatus;
  }

  private static void validateTerminalPayloadValue(String key, Object value) {
    if (value == null) {
      return;
    }
    switch (key) {
      case "terminalStatus" -> validateEnum(key, String.valueOf(value), TERMINAL_STATUS_VALUES);
      case "closedStatus" -> validateEnum(key, String.valueOf(value), CLOSED_STATUS_VALUES);
      case "writeDisabledReason" ->
          validateEnum(key, String.valueOf(value), WRITE_DISABLED_REASON_VALUES);
      case "localPurgeState" -> validateEnum(key, String.valueOf(value), LOCAL_PURGE_STATE_VALUES);
      default -> {
        return;
      }
    }
  }

  private static void validateEnum(String key, String value, Set<String> allowedValues) {
    if (!allowedValues.contains(value)) {
      throw new IllegalArgumentException("invalid incident_terminal " + key + ": " + value);
    }
  }
}
