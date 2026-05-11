package com.surimap.api.controller.summary.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surimap.summary.SearchHistorySummaryRow;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHistorySummaryItemResponse(
    UUID summaryId,
    UUID opId,
    String scopeType,
    UUID scopeId,
    UUID dutyShiftId,
    String status,
    String displayStatus,
    String content,
    String sourceReadiness,
    String sourceHash,
    Instant generatedAt,
    long version) {

  public static SearchHistorySummaryItemResponse from(SearchHistorySummaryRow row) {
    boolean dutyScoped = row.dutyShiftId() != null;
    return new SearchHistorySummaryItemResponse(
        row.summaryId(),
        row.opId(),
        dutyScoped ? "DUTY_SHIFT" : "OP",
        dutyScoped ? row.dutyShiftId() : row.opId(),
        row.dutyShiftId(),
        row.status(),
        displayStatus(row.status()),
        "READY".equals(row.status()) ? row.content() : null,
        row.sourceReadiness(),
        row.sourceHash(),
        row.generatedAt(),
        row.version());
  }

  private static String displayStatus(String status) {
    return switch (status) {
      case "READY" -> "READY";
      case "FAILED" -> "UNAVAILABLE";
      default -> "LOADING";
    };
  }
}
