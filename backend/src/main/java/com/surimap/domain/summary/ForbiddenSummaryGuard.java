package com.surimap.domain.summary;

import java.util.List;
import java.util.stream.Stream;

/**
 * Guards the AI-generated summary text from forbidden wording before persistence. Forbidden phrases
 * are sourced from S8.json §search_history_summary_quality.disallowedPhrases. If any forbidden
 * phrase is detected, the summary must be treated as FAILED with no content stored.
 */
public class ForbiddenSummaryGuard {

  /**
   * Forbidden phrases from S8.json §search_history_summary_quality.disallowedPhrases. These strings
   * are used verbatim from the spec.
   */
  public static final List<String> FORBIDDEN_PHRASES =
      List.of("다음 구역 추천", "누락 확정", "위험도 높음", "자동 판단");

  /**
   * Additional provider guard terms shared with the OP comparison AI validator. Keep
   * FORBIDDEN_PHRASES verbatim for S8 spec assertions, and use this wider list for runtime
   * blocking.
   */
  public static final List<String> PROVIDER_FORBIDDEN_TERMS =
      List.of(
          "전략", "암묵지", "시사", "의미한다", "로 보인다", "~로 보인다", "효율", "잘못", "더 나음", "추천", "다음 차수", "미수색",
          "위험", "가능성 높음", "왜", "의도", "방침");

  public static final List<String> ALL_FORBIDDEN_TERMS =
      Stream.concat(FORBIDDEN_PHRASES.stream(), PROVIDER_FORBIDDEN_TERMS.stream())
          .distinct()
          .toList();

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
    return ALL_FORBIDDEN_TERMS.stream().anyMatch(summaryText::contains);
  }
}
