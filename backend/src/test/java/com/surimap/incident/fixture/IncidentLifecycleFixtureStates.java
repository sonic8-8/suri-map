package com.surimap.incident.fixture;

import java.util.List;

/** SC-12 종료 후 write 차단과 Outbox 재시도 거부를 검증하는 terminal fixture 상태. */
public record IncidentLifecycleFixtureStates(
    String closedIncidentId,
    String preCloseOperationId,
    String postCloseOperationId,
    String expectedPreClose,
    String expectedPostClose,
    String expectedUserCategory,
    List<FailureCategoryRow> failureCategoryRows) {

  public IncidentLifecycleFixtureStates {
    failureCategoryRows = List.copyOf(failureCategoryRows);
  }

  public FailureCategoryRow closedFailureRow() {
    return failureCategoryRows.stream()
        .filter(row -> "op-fail-closed-001".equals(row.operationId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("op-fail-closed-001 fixture가 없습니다"));
  }

  public record FailureCategoryRow(
      String operationId, String lastError, String userSafeFailureCategory, boolean retryable) {}
}
