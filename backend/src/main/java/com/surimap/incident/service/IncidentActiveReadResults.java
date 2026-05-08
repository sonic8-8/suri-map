package com.surimap.incident.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Controller response DTO와 MyBatis row 사이에서 쓰는 active 사건 read model. */
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

  /** active 상세 read model. terminal/purge 표현은 intentionally 제외한다. */
  public record Detail(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      MissingPerson missingPerson,
      List<Assignment> assignments) {
    public Detail {
      assignments = List.copyOf(assignments);
    }
  }

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
