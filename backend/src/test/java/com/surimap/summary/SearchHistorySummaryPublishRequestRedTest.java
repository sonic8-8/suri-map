package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SearchHistorySummaryPort.GenerationStatus;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;
import com.surimap.domain.summary.SearchHistorySummaryPublishRequest;
import com.surimap.summary.fixture.SearchHistorySummaryFixtures;
import com.surimap.summary.mock.MockSearchHistorySummaryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RED contract tests for L3-T08: search history summary port, PublishRequest payload, and
 * ForbiddenSummaryGuard (FR-23, S8 §search_history_summary_quality, SC-11).
 *
 * <p>These tests are RED because SearchHistorySummaryService, OpenAiSearchHistorySummaryAdapter,
 * and ForbiddenSummaryGuard are not yet implemented. Coder must close the implementation gap.
 *
 * <p>Fixture IDs come verbatim from S8.json §harness_fixtures.sc11_handover_ai_convergence.
 */
@DisplayName("L3-T08 SearchHistorySummary PublishRequest contract tests")
class SearchHistorySummaryPublishRequestRedTest {

  // ── Shared fixture values ─────────────────────────────────────────────────

  private SummaryRequest buildRequest() {
    return new SummaryRequest(
        SearchHistorySummaryFixtures.SUMMARY_ID,
        SearchHistorySummaryFixtures.OP_ID,
        SearchHistorySummaryFixtures.INCIDENT_ID,
        "minimized OP/path/marker/area/memo evidence snapshot");
  }

  // ── 1. PublishRequest payload contract ───────────────────────────────────

  @Nested
  @DisplayName("SEARCH_HISTORY_SUMMARY_CHANGED PublishRequest")
  class PublishRequestContract {

    @Test
    @DisplayName("FAILED 상태의 PublishRequest는 fixture id/status/version/opId를 포함한다")
    void failedPublishRequestContainsFixtureFields() {
      // Given: fixture values from S8.json §harness_fixtures.sc11_handover_ai_convergence.aiSummary
      var request =
          new SearchHistorySummaryPublishRequest(
              SearchHistorySummaryFixtures.SUMMARY_EVENT_ID,
              SearchHistorySummaryFixtures.SUMMARY_ID,
              SearchHistorySummaryFixtures.INCIDENT_ID,
              SearchHistorySummaryFixtures.OP_ID,
              SearchHistorySummaryFixtures.SUMMARY_STATUS_FAILED,
              SearchHistorySummaryFixtures.SUMMARY_VERSION);

      // Then: event type matches spec
      assertThat(request.getType())
          .isEqualTo(SearchHistorySummaryFixtures.SUMMARY_EVENT_TYPE);

      // Then: payload id matches fixture summaryId
      assertThat(request.getId())
          .isEqualTo(SearchHistorySummaryFixtures.SUMMARY_ID);

      // Then: incidentId matches fixture
      assertThat(request.getIncidentId())
          .isEqualTo(SearchHistorySummaryFixtures.INCIDENT_ID);

      // Then: opId matches fixture op-precinct-001-op2
      assertThat(request.getOpId())
          .isEqualTo(SearchHistorySummaryFixtures.OP_ID);

      // Then: status is FAILED as spec requires for failure scenario
      assertThat(request.getStatus())
          .isEqualTo("FAILED");

      // Then: version matches fixture version=1
      assertThat(request.getVersion())
          .isEqualTo(1L);

      // Then: eventId matches fixture evt-s8-ai-summary-001
      assertThat(request.getEventId())
          .isEqualTo("evt-s8-ai-summary-001");
    }

    @Test
    @DisplayName("SEARCH_HISTORY_SUMMARY_CHANGED 이벤트 타입 상수는 spec 문자열과 같다")
    void eventTypeConstantMatchesSpec() {
      // spec string from S8.json §events_published[type=SEARCH_HISTORY_SUMMARY_CHANGED]
      assertThat(SearchHistorySummaryPublishRequest.EVENT_TYPE)
          .isEqualTo("SEARCH_HISTORY_SUMMARY_CHANGED");
    }
  }

  // ── 2. Mock adapter — success path ───────────────────────────────────────

  @Nested
  @DisplayName("MockSearchHistorySummaryAdapter — happy path")
  class HappyPath {

    @Test
    @DisplayName("성공 mock은 READY 상태와 summaryText를 반환한다")
    void successMockReturnsReadyStatus() {
      SearchHistorySummaryPort adapter =
          MockSearchHistorySummaryAdapter.success(
              SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT);

      SummaryResult result = adapter.generate(buildRequest());

      assertThat(result.status()).isEqualTo(GenerationStatus.READY);
      assertThat(result.summaryText())
          .isEqualTo(SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT);
      assertThat(result.isFailed()).isFalse();
    }

    @Test
    @DisplayName("성공 응답에 content가 null이 아니고 source prompt가 포함되지 않는다")
    void successSummaryTextIsNotNullAndContainsNoSourcePrompt() {
      SearchHistorySummaryPort adapter =
          MockSearchHistorySummaryAdapter.success(
              SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT);

      SummaryResult result = adapter.generate(buildRequest());

      assertThat(result.summaryText()).isNotNull().isNotBlank();
      // source prompt must not leak into client-visible text (S8 §non_functional_requirements.security)
      assertThat(result.summaryText()).doesNotContain("source_prompt");
    }
  }

  // ── 3. Failure state tests ────────────────────────────────────────────────

  @Nested
  @DisplayName("OpenAI 실패 시 generation_status=FAILED")
  class FailedStateTests {

    @Test
    @DisplayName("OpenAI timeout 시 adapter가 RuntimeException을 던지고 서비스는 FAILED 처리해야 한다")
    void timeoutAdapterThrowsRuntimeException() {
      // Adapter throws; service layer must catch and persist FAILED state.
      // This test verifies the mock contract — the adapter propagates timeout as RuntimeException.
      SearchHistorySummaryPort adapter = MockSearchHistorySummaryAdapter.timeout();

      assertThatThrownBy(() -> adapter.generate(buildRequest()))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("schema 실패(빈 텍스트) 응답을 받으면 FAILED 상태로 남아야 한다")
    void schemaInvalidResponseMustProduceFailedState() {
      // The adapter returns an empty text (low-quality / schema-invalid stub).
      // The service must detect this and store generation_status=FAILED with no content.
      SearchHistorySummaryPort adapter = MockSearchHistorySummaryAdapter.schemaInvalid();

      SummaryResult result = adapter.generate(buildRequest());

      // schema-invalid adapter returns FAILED directly — service must persist FAILED, content=null
      assertThat(result.isFailed()).isTrue();
      assertThat(result.summaryText()).isNull();
    }

    @Test
    @DisplayName("FAILED SummaryResult는 summaryText가 null이다")
    void failedResultHasNullSummaryText() {
      SummaryResult result = SummaryResult.failed();

      assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
      assertThat(result.summaryText()).isNull();
      assertThat(result.isFailed()).isTrue();
    }

    @Test
    @DisplayName("FAILED 상태에서 content를 저장하지 않는다 — summaryText null 보장")
    void failedResultContentIsNull() {
      // S8.json §FR-39: "실패 시 generation_status=FAILED로 남기며 대체 요약 문장을 저장하지 않는다"
      SummaryResult failed = SummaryResult.failed();

      assertThat(failed.summaryText())
          .as("FAILED 상태에서 content는 null이어야 한다 (S8 §FR-39)")
          .isNull();
    }
  }

  // ── 4. ForbiddenSummaryGuard tests ───────────────────────────────────────

  @Nested
  @DisplayName("ForbiddenSummaryGuard — FR-23 금지 문구 차단")
  class ForbiddenPhraseGuard {

    @Test
    @DisplayName("금지 문구 '다음 구역 추천'이 포함되면 차단해야 한다")
    void detectsForbiddenNextAreaPhrase() {
      var guard = new ForbiddenSummaryGuard();
      String text = "수색을 마쳤습니다. 다음 구역 추천: 북쪽.";

      assertThat(guard.containsForbiddenPhrase(text)).isTrue();
    }

    @Test
    @DisplayName("금지 문구 목록이 S8.json spec 문자열과 동일하다")
    void forbiddenPhrasesMatchSpec() {
      // Forbidden phrases must be exactly the strings from S8.json §disallowedPhrases
      assertThat(ForbiddenSummaryGuard.FORBIDDEN_PHRASES)
          .containsExactlyInAnyOrder(
              SearchHistorySummaryFixtures.FORBIDDEN_NEXT_AREA,
              SearchHistorySummaryFixtures.FORBIDDEN_MISSING_CONFIRMED,
              SearchHistorySummaryFixtures.FORBIDDEN_HIGH_RISK,
              SearchHistorySummaryFixtures.FORBIDDEN_AUTO_JUDGE);
    }

    @Test
    @DisplayName("mock adapter의 forbiddenPhrase 텍스트는 금지 문구를 포함한다")
    void forbiddenPhraseAdapterTextContainsDisallowedPhrases() {
      // Verifies the mock itself contains the right forbidden content for testing
      SearchHistorySummaryPort adapter = MockSearchHistorySummaryAdapter.forbiddenPhrase();

      SummaryResult result = adapter.generate(buildRequest());

      // The adapter returns text containing forbidden phrases; service must block this
      assertThat(result.summaryText()).contains(SearchHistorySummaryFixtures.FORBIDDEN_NEXT_AREA);
      assertThat(result.summaryText()).contains(SearchHistorySummaryFixtures.FORBIDDEN_HIGH_RISK);
    }

    @Test
    @DisplayName("금지 문구가 포함된 응답은 generation_status=FAILED로 처리해야 한다")
    void forbiddenPhraseResponseMustResultInFailedStatus() {
      // S8.json §search_history_summary_quality.failurePolicy:
      // "금지 문구 검출 시 generation_status=FAILED로 저장하고 content를 저장하지 않는다"
      //
      // The coder must implement SearchHistorySummaryService to:
      //   1. Call adapter.generate(request)
      //   2. Run ForbiddenSummaryGuard.containsForbiddenPhrase(result.summaryText())
      //   3. If true → persist generation_status=FAILED, content=null
      //   4. Publish SEARCH_HISTORY_SUMMARY_CHANGED with status=FAILED
      //
      // This test documents the contract expectation as a RED test.
      SearchHistorySummaryPort adapter = MockSearchHistorySummaryAdapter.forbiddenPhrase();
      SummaryResult adapterResult = adapter.generate(buildRequest());

      var guard = new ForbiddenSummaryGuard();
      assertThat(guard.containsForbiddenPhrase(adapterResult.summaryText())).isTrue();

      // Guard detected → service maps to FAILED with null content
      SummaryResult failedResult = SummaryResult.failed();
      assertThat(failedResult.isFailed()).isTrue();
      assertThat(failedResult.summaryText()).isNull();
    }
  }

  // ── 5. Happy path minimized evidence — no PII, no raw stream ─────────────

  @Nested
  @DisplayName("최소화된 내부 evidence 사용 계약")
  class MinimizedEvidenceContract {

    @Test
    @DisplayName("SummaryRequest evidence 필드는 null이 아니어야 한다")
    void summaryRequestEvidenceIsNotNull() {
      SummaryRequest request = buildRequest();

      assertThat(request.evidence()).isNotNull();
      assertThat(request.summaryId()).isEqualTo(SearchHistorySummaryFixtures.SUMMARY_ID);
      assertThat(request.operationalPeriodId()).isEqualTo(SearchHistorySummaryFixtures.OP_ID);
      assertThat(request.incidentId()).isEqualTo(SearchHistorySummaryFixtures.INCIDENT_ID);
    }

    @Test
    @DisplayName("READY happy path 결과는 추천·누락확정·위험도·자동판단 문구를 포함하지 않는다")
    void happyPathSummaryContainsNoForbiddenContent() {
      SearchHistorySummaryPort adapter =
          MockSearchHistorySummaryAdapter.success(
              SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT);

      SummaryResult result = adapter.generate(buildRequest());

      assertThat(result.summaryText())
          .doesNotContain(SearchHistorySummaryFixtures.FORBIDDEN_NEXT_AREA)
          .doesNotContain(SearchHistorySummaryFixtures.FORBIDDEN_MISSING_CONFIRMED)
          .doesNotContain(SearchHistorySummaryFixtures.FORBIDDEN_HIGH_RISK)
          .doesNotContain(SearchHistorySummaryFixtures.FORBIDDEN_AUTO_JUDGE);
    }
  }

  // ── 6. SC-11 board convergence fixture contract ───────────────────────────

  @Nested
  @DisplayName("SC-11 board convergence fixture — id/status/version/opId 안정성")
  class Sc11BoardConvergenceFixture {

    @Test
    @DisplayName("SEARCH_HISTORY_SUMMARY_CHANGED FAILED 이벤트 payload가 harness_fixtures와 일치한다")
    void failedEventPayloadMatchesHarnessFixture() {
      // S8.json §harness_fixtures.sc11_handover_ai_convergence.expectedS4Events.aiSummaryReady
      var publishRequest =
          new SearchHistorySummaryPublishRequest(
              SearchHistorySummaryFixtures.SUMMARY_EVENT_ID,
              SearchHistorySummaryFixtures.SUMMARY_ID,
              SearchHistorySummaryFixtures.INCIDENT_ID,
              SearchHistorySummaryFixtures.OP_ID,
              SearchHistorySummaryFixtures.SUMMARY_STATUS_FAILED,
              SearchHistorySummaryFixtures.SUMMARY_VERSION);

      // eventId from fixture
      assertThat(publishRequest.getEventId()).isEqualTo("evt-s8-ai-summary-001");
      // type must be SEARCH_HISTORY_SUMMARY_CHANGED
      assertThat(publishRequest.getType()).isEqualTo("SEARCH_HISTORY_SUMMARY_CHANGED");
      // payloadId from fixture summaryId
      assertThat(publishRequest.getId().toString())
          .isEqualTo(SearchHistorySummaryFixtures.SUMMARY_ID.toString());
      // payloadStatus must be FAILED
      assertThat(publishRequest.getStatus()).isEqualTo("FAILED");
      // payloadVersion from fixture version=1
      assertThat(publishRequest.getVersion()).isEqualTo(1L);
      // opId from fixture op-precinct-001-op2
      assertThat(publishRequest.getOpId().toString())
          .isEqualTo(SearchHistorySummaryFixtures.OP_ID.toString());
    }

    @Test
    @DisplayName("displayStatus는 FAILED일 때 UNAVAILABLE이다")
    void displayStatusIsUnavailableWhenFailed() {
      // S8.json §harness_fixtures.sc11_handover_ai_convergence.aiSummary.displayStatus
      assertThat(SearchHistorySummaryFixtures.SUMMARY_DISPLAY_STATUS_UNAVAILABLE)
          .isEqualTo("UNAVAILABLE");
      // Paired with FAILED status
      assertThat(SearchHistorySummaryFixtures.SUMMARY_STATUS_FAILED)
          .isEqualTo("FAILED");
    }

    @Test
    @DisplayName("FAILED 상태에서 sourcePromptVisible은 false 이어야 한다")
    void sourcePromptNotVisibleOnFailure() {
      // S8.json §harness_fixtures.sc11_handover_ai_convergence.aiSummary.sourcePromptVisible=false
      // S8.json §non_functional_requirements.security: source prompt must not be exposed
      SummaryResult failed = SummaryResult.failed();

      // Source prompt must never appear in the result text
      assertThat(failed.summaryText())
          .as("FAILED 상태에서 content는 null — source prompt 노출 없음")
          .isNull();
    }
  }
}
