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

  /** 상세 read model. Active와 Terminal을 타입으로 분리해 잘못된 필드 조합을 만들 수 없게 한다. */
  public sealed interface Detail permits Detail.Active, Detail.Terminal {
    UUID id();

    UUID incidentId();

    String status();

    long version();

    static Detail active(
        UUID id,
        UUID incidentId,
        String status,
        long version,
        MissingPerson missingPerson,
        List<Assignment> assignments) {
      return new Active(id, incidentId, status, version, missingPerson, assignments);
    }

    static Detail terminal(
        UUID id, UUID incidentId, String status, long version, Instant closedAt) {
      return new Terminal(id, incidentId, status, version, closedAt);
    }

    /** OPEN 상세는 missing_person과 active assignments만 포함한다. */
    record Active(
        UUID id,
        UUID incidentId,
        String status,
        long version,
        MissingPerson missingPerson,
        List<Assignment> assignments)
        implements Detail {
      public Active {
        assignments = List.copyOf(assignments);
      }
    }

    /** CLOSED 상세는 sanitized terminal 상태만 포함하고 active 개인정보 필드를 가질 수 없다. */
    record Terminal(UUID id, UUID incidentId, String status, long version, Instant closedAt)
        implements Detail {
      public String writeDisabledReason() {
        return "incident_closed";
      }

      public TerminalSnapshot terminalSnapshot() {
        return new TerminalSnapshot(
            id, incidentId, status, version, closedAt, writeDisabledReason());
      }
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
