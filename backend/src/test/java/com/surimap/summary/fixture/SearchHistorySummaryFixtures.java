package com.surimap.summary.fixture;

import java.util.UUID;

/**
 * Fixture IDs for search_history_summary tests. All IDs are sourced verbatim from S8.json
 * §harness_fixtures.sc11_handover_ai_convergence. Do not rename or invent new IDs.
 */
public final class SearchHistorySummaryFixtures {

  private SearchHistorySummaryFixtures() {}

  // ── S8 harness_fixtures.sc11_handover_ai_convergence ──────────────────────
  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  public static final String OP_ALIAS = "op-precinct-001-op2";
  public static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");

  // summary row
  public static final String SUMMARY_ALIAS = "summary-precinct-op2-001";
  public static final UUID SUMMARY_ID = UUID.fromString("44444444-4444-4444-4444-444444440001");
  public static final String SUMMARY_STATUS_FAILED = "FAILED";
  public static final String SUMMARY_DISPLAY_STATUS_UNAVAILABLE = "UNAVAILABLE";
  public static final long SUMMARY_VERSION = 1L;

  // SEARCH_HISTORY_SUMMARY_CHANGED event
  public static final String SUMMARY_EVENT_ID = "evt-s8-ai-summary-001";
  public static final String SUMMARY_EVENT_TYPE = "SEARCH_HISTORY_SUMMARY_CHANGED";

  // mock adapter success text from S8.json §mock_search_history_summary_adapter.success
  public static final String SUCCESS_SUMMARY_TEXT =
      "OP2 동안 수색한 경로, 주요 마커, 인수인계 메모를 시간순으로 요약했습니다.";

  // forbidden phrase text from S8.json §mock_search_history_summary_adapter.forbiddenPhraseResponse
  public static final String FORBIDDEN_PHRASE_SUMMARY_TEXT = "다음 구역 추천: 북쪽으로 이동. 위험도 높음.";

  // individual forbidden phrases from S8.json §search_history_summary_quality.disallowedPhrases
  public static final String FORBIDDEN_NEXT_AREA = "다음 구역 추천";
  public static final String FORBIDDEN_MISSING_CONFIRMED = "누락 확정";
  public static final String FORBIDDEN_HIGH_RISK = "위험도 높음";
  public static final String FORBIDDEN_AUTO_JUDGE = "자동 판단";

}
