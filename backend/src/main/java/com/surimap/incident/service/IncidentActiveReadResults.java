package com.surimap.incident.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Controller response DTO와 MyBatis row 사이에서 쓰는 사건 read model. */
public final class IncidentActiveReadResults {

  private IncidentActiveReadResults() {}

  /** GET /api/incidents service result. */
  public record ListResult(List<ListItem> items) {
    public ListResult {
      items = List.copyOf(items);
    }
  }

  /** 목록 item은 상세/종료 필드를 포함하지 않는다. */
  public record ListItem(
      UUID id, UUID incidentId, String title, String status, long version, Instant closedAt) {}

  /** 상세 read model. OPEN이면 active 필드, CLOSED이면 sanitized terminal 필드만 채운다. */
  public record Detail(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      Instant closedAt,
      TerminalSnapshot terminalSnapshot,
      String writeDisabledReason,
      MissingPerson missingPerson,
      List<Assignment> assignments) {
    public Detail {
      assignments = assignments == null ? null : List.copyOf(assignments);
    }

    public static Detail active(
        UUID id,
        UUID incidentId,
        String status,
        long version,
        MissingPerson missingPerson,
        List<Assignment> assignments) {
      return new Detail(
          id, incidentId, status, version, null, null, null, missingPerson, assignments);
    }

    public static Detail terminal(
        UUID id, UUID incidentId, String status, long version, Instant closedAt) {
      return new Detail(
          id,
          incidentId,
          status,
          version,
          closedAt,
          new TerminalSnapshot(id, incidentId, status, version, closedAt, "incident_closed"),
          "incident_closed",
          null,
          null);
    }
  }

  /** S3-2가 소비할 수 있는 종료 사건 sanitized snapshot. missing_person PII는 포함하지 않는다. */
  public record TerminalSnapshot(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      Instant closedAt,
      String writeDisabledReason) {}

  /** S1-1이 active detail에 노출해도 되는 missing_person 필드만 담는다. */
  public record MissingPerson(
      UUID incidentId,
      String displayName,
      String photoObjectKey,
      String appearanceText,
      String lastSeenLocationText,
      Instant lastSeenAt) {}

  /** 사건 접근과 표시용 incident_assignment 요약. */
  public record Assignment(String accountId, String incidentRole) {}
}
