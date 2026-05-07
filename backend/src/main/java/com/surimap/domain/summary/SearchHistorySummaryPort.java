package com.surimap.domain.summary;

import java.util.UUID;

/**
 * Port interface for AI-based search history summary generation. Implementations must use only
 * internal OP/path/marker/area/memo evidence and never include recommendation, missing-area
 * conclusion, risk judgment, or next-area instruction (FR-23, S8 §search_history_summary_quality).
 */
public interface SearchHistorySummaryPort {

  /**
   * Generate a search history summary for the given operational period.
   *
   * <p>On success, returns a {@link SummaryResult} with status READY and non-null summaryText. On
   * any failure (timeout, schema invalid, HTTP error), returns a {@link SummaryResult} with status
   * FAILED and null summaryText. The caller is responsible for persisting the FAILED state and
   * publishing SEARCH_HISTORY_SUMMARY_CHANGED.
   *
   * @param request minimized evidence from OP/path/marker/area/memo; must not contain raw location
   *     stream or PII beyond what is recorded in internal tables
   * @return SummaryResult with READY or FAILED status
   */
  SummaryResult generate(SummaryRequest request);

  record SummaryRequest(UUID summaryId, UUID operationalPeriodId, UUID incidentId, String evidence) {
  }

  record SummaryResult(GenerationStatus status, String summaryText) {
    public static SummaryResult ready(String summaryText) {
      return new SummaryResult(GenerationStatus.READY, summaryText);
    }

    public static SummaryResult failed() {
      return new SummaryResult(GenerationStatus.FAILED, null);
    }

    public boolean isFailed() {
      return status == GenerationStatus.FAILED;
    }
  }

  enum GenerationStatus {
    GENERATING,
    READY,
    FAILED
  }
}
