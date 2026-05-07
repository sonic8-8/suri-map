package com.surimap.domain.summary;

import java.util.List;

/**
 * Guards the AI-generated summary text from forbidden wording before persistence. Forbidden phrases
 * are sourced from S8.json §search_history_summary_quality.disallowedPhrases. If any forbidden
 * phrase is detected, the summary must be treated as FAILED with no content stored.
 */
public class ForbiddenSummaryGuard {

  /**
   * Forbidden phrases from S8.json §search_history_summary_quality.disallowedPhrases. These
   * strings are used verbatim from the spec.
   */
  public static final List<String> FORBIDDEN_PHRASES =
      List.of("다음 구역 추천", "누락 확정", "위험도 높음", "자동 판단");

  /**
   * Returns true if the summary text contains at least one forbidden phrase.
   *
   * @param summaryText the text to check
   * @return true if forbidden phrase is detected
   */
  public boolean containsForbiddenPhrase(String summaryText) {
    if (summaryText == null) {
      return false;
    }
    return FORBIDDEN_PHRASES.stream().anyMatch(summaryText::contains);
  }
}
