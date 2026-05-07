package com.surimap.summary.mock;

import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SearchHistorySummaryPort.GenerationStatus;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;

/**
 * In-memory mock adapter for {@link SearchHistorySummaryPort}. Used in Phase 3 RED tests. Coder
 * must implement the real {@link com.surimap.client.openai.OpenAiSearchHistorySummaryAdapter}.
 *
 * <p>Mock behavior is configurable per test using the factory methods below.
 */
public class MockSearchHistorySummaryAdapter implements SearchHistorySummaryPort {

  private final Behavior behavior;
  private final String fixedSummaryText;

  private MockSearchHistorySummaryAdapter(Behavior behavior, String fixedSummaryText) {
    this.behavior = behavior;
    this.fixedSummaryText = fixedSummaryText;
  }

  /** Returns a mock that succeeds with the given summary text. */
  public static MockSearchHistorySummaryAdapter success(String summaryText) {
    return new MockSearchHistorySummaryAdapter(Behavior.SUCCESS, summaryText);
  }

  /** Returns a mock that simulates an OpenAI timeout (throws RuntimeException). */
  public static MockSearchHistorySummaryAdapter timeout() {
    return new MockSearchHistorySummaryAdapter(Behavior.TIMEOUT, null);
  }

  /** Returns a mock that returns a schema-invalid response (empty text). */
  public static MockSearchHistorySummaryAdapter schemaInvalid() {
    return new MockSearchHistorySummaryAdapter(Behavior.SCHEMA_INVALID, null);
  }

  /**
   * Returns a mock that returns a schema-valid response containing a forbidden phrase. Used to
   * verify the ForbiddenSummaryGuard blocks the content before persistence.
   */
  public static MockSearchHistorySummaryAdapter forbiddenPhrase() {
    // Forbidden phrase text is from S8.json §mock_search_history_summary_adapter.forbiddenPhraseResponse
    return new MockSearchHistorySummaryAdapter(
        Behavior.SUCCESS, "다음 구역 추천: 북쪽으로 이동. 위험도 높음.");
  }

  @Override
  public SummaryResult generate(SummaryRequest request) {
    return switch (behavior) {
      case SUCCESS -> SummaryResult.ready(fixedSummaryText);
      case TIMEOUT -> throw new RuntimeException("OpenAI request timed out");
      case SCHEMA_INVALID -> SummaryResult.failed();
    };
  }

  private enum Behavior {
    SUCCESS,
    TIMEOUT,
    SCHEMA_INVALID
  }
}
