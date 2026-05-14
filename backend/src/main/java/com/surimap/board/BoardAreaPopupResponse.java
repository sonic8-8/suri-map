package com.surimap.board;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BoardAreaPopupResponse(
    Common common,
    IncompleteSummary incompleteSummary,
    CompletedSummary completedSummary) {

  private static final String COMPLETED = "COMPLETED";
  private static final String ACTIVE = "ACTIVE";

  public BoardAreaPopupResponse {
    Objects.requireNonNull(common, "common must not be null");
  }

  public static BoardAreaPopupResponse from(
      SearchAreaRow area,
      OperationalPeriodRow op,
      List<SearchAreaAssignmentRow> assignments,
      List<PolicePhoneFreshnessRow> freshnessRows) {
    Objects.requireNonNull(area, "area must not be null");
    List<SearchAreaAssignmentRow> safeAssignments =
        assignments == null ? List.of() : List.copyOf(assignments);
    List<PolicePhoneFreshnessRow> safeFreshnessRows =
        freshnessRows == null ? List.of() : List.copyOf(freshnessRows);
    List<Assignment> assignmentResponses =
        safeAssignments.stream()
            .sorted(assignmentComparator())
            .map(assignment -> assignmentResponse(assignment, safeFreshnessRows))
            .toList();

    Common common =
        new Common(
            area.id(),
            displayAreaName(area),
            area.areaLevel(),
            area.status(),
            area.opId(),
            op == null ? null : op.sequenceNo(),
            op == null ? null : op.status(),
            op == null ? null : op.startedAt(),
            op == null ? null : op.endedAt(),
            assignmentStatus(safeAssignments),
            assignmentResponses,
            area.updatedAt(),
            area.version(),
            area.historyCount());

    if (COMPLETED.equals(area.status())) {
      return new BoardAreaPopupResponse(
          common,
          null,
          new CompletedSummary(
              area.completedAt() == null ? area.updatedAt() : area.completedAt(),
              area.completedByAccountId(),
              area.completionMemo(),
              area.historyCount()));
    }
    return new BoardAreaPopupResponse(
        common, new IncompleteSummary(area.updatedAt(), area.historyCount()), null);
  }

  private static String displayAreaName(SearchAreaRow area) {
    if (area.name() != null && !area.name().isBlank()) {
      return area.name();
    }
    return area.id().toString();
  }

  private static Assignment assignmentResponse(
      SearchAreaAssignmentRow assignment, List<PolicePhoneFreshnessRow> freshnessRows) {
    PolicePhoneFreshnessRow freshness = matchingFreshness(assignment.assignedAccountId(), freshnessRows);
    return new Assignment(
        assignment.id(),
        assignment.assignedAccountId(),
        assignment.assignedByAccountId(),
        assignment.assignedAt(),
        assignment.revokedAt(),
        assignment.status(),
        freshness == null ? null : freshness.accountType(),
        freshness == null ? null : freshness.organizationType(),
        freshness == null ? null : PolicePhone.from(freshness));
  }

  private static PolicePhoneFreshnessRow matchingFreshness(
      UUID assignedAccountId, List<PolicePhoneFreshnessRow> freshnessRows) {
    if (assignedAccountId == null) {
      return null;
    }
    String accountId = assignedAccountId.toString();
    return freshnessRows.stream()
        .filter(row -> accountId.equals(row.accountId()))
        .max(Comparator.comparingLong(PolicePhoneFreshnessRow::version))
        .orElse(null);
  }

  private static Comparator<SearchAreaAssignmentRow> assignmentComparator() {
    return Comparator.comparing(
            BoardAreaPopupResponse::activeAssignment, Comparator.reverseOrder())
        .thenComparing(SearchAreaAssignmentRow::assignedAt, Comparator.reverseOrder())
        .thenComparing(SearchAreaAssignmentRow::id);
  }

  private static boolean activeAssignment(SearchAreaAssignmentRow row) {
    return ACTIVE.equals(row.status()) && row.revokedAt() == null;
  }

  private static String assignmentStatus(List<SearchAreaAssignmentRow> assignments) {
    if (assignments.isEmpty()) {
      return "UNASSIGNED";
    }
    if (assignments.stream().anyMatch(BoardAreaPopupResponse::activeAssignment)) {
      return "ASSIGNED";
    }
    return "CANCELLED";
  }

  public record Common(
      UUID areaId,
      String areaName,
      String areaLevel,
      String areaStatus,
      UUID opId,
      Integer opSequence,
      String opStatus,
      Instant opStartedAt,
      Instant opEndedAt,
      String assignmentStatus,
      List<Assignment> assignments,
      Instant updatedAt,
      long version,
      long historyCount) {
    public Common {
      assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }
  }

  public record Assignment(
      UUID assignmentId,
      UUID assignedAccountId,
      UUID assignedByAccountId,
      Instant assignedAt,
      Instant revokedAt,
      String status,
      AccountType accountType,
      OrganizationType organizationType,
      PolicePhone policePhone) {}

  public record PolicePhone(
      UUID policePhoneId,
      String freshness,
      Instant lastHeartbeatAt,
      Instant lastSyncAt) {
    static PolicePhone from(PolicePhoneFreshnessRow row) {
      return new PolicePhone(
          row.policePhoneId(),
          row.derivedFreshness().name(),
          row.lastHeartbeatAt(),
          row.lastSyncAt());
    }
  }

  public record IncompleteSummary(Instant statusUpdatedAt, long historyCount) {}

  public record CompletedSummary(
      Instant completedAt,
      UUID completedByAccountId,
      String completionMemo,
      long historyCount) {}
}
