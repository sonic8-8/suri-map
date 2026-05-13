package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.summary.fixture.SearchHistorySummaryFixtures;
import com.surimap.summary.mock.MockSearchHistorySummaryAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S8 search_history_summary durable generation completion")
class SearchHistorySummaryGenerationCompletionTest {

  private static final String SOURCE_HASH = "hash-s8-source-snapshot-op2-001";
  private static final String EVIDENCE =
      "OP2 path, marker, completed-area, and handover memo evidence snapshot";
  private static final Instant NOW = Instant.parse("2026-05-13T00:00:00Z");
  private static final SummaryRequest REQUEST =
      new SummaryRequest(
          SearchHistorySummaryFixtures.SUMMARY_ID,
          SearchHistorySummaryFixtures.OP_ID,
          SearchHistorySummaryFixtures.INCIDENT_ID,
          EVIDENCE);

  @Test
  @DisplayName("provider success saves READY content and emits SEARCH_HISTORY_SUMMARY_CHANGED")
  void providerSuccessSavesReadyContentAndEmitsChangedEvent() {
    CompletionHarness harness =
        CompletionHarness.withProvider(
            MockSearchHistorySummaryAdapter.success(SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT));

    harness.runGeneration();

    harness.mapper.assertPersisted(
        "READY", SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT, "READY", NOW);
    harness.mapper.assertStatusPersistedBeforeEvent("READY");
    harness.eventHub.assertPublishedChanged("READY");
  }

  @Test
  @DisplayName("provider failure timeout and schema failure save FAILED without fallback content")
  void providerFailureTimeoutAndSchemaFailureSaveFailedWithoutFallbackContent() {
    List<ProviderCase> cases =
        List.of(
            new ProviderCase("provider-failed", request -> SummaryResult.failed()),
            new ProviderCase("provider-timeout", MockSearchHistorySummaryAdapter.timeout()),
            new ProviderCase("schema-failed", MockSearchHistorySummaryAdapter.schemaInvalid()));

    for (ProviderCase providerCase : cases) {
      CompletionHarness harness = CompletionHarness.withProvider(providerCase.provider());

      harness.runGeneration();

      harness.mapper.assertPersisted("FAILED", null, "READY", null);
      harness.mapper.assertNoStoredContent(
          providerCase.name(),
          "summary_unavailable",
          "UNAVAILABLE",
          SearchHistorySummaryFixtures.SUCCESS_SUMMARY_TEXT);
      harness.mapper.assertStatusPersistedBeforeEvent("FAILED", providerCase.name());
      harness.eventHub.assertPublishedChanged("FAILED", providerCase.name());
    }
  }

  @Test
  @DisplayName("ForbiddenSummaryGuard blocks disallowed phrases and persists FAILED with no content")
  void forbiddenSummaryGuardBlocksDisallowedPhrasesAndPersistsFailedWithNoContent() {
    CompletionHarness harness =
        CompletionHarness.withProvider(MockSearchHistorySummaryAdapter.forbiddenPhrase());

    harness.runGeneration();

    harness.mapper.assertPersisted("FAILED", null, "READY", null);
    harness.mapper.assertNoStoredContent(
        "forbidden-phrase",
        SearchHistorySummaryFixtures.FORBIDDEN_PHRASE_SUMMARY_TEXT,
        SearchHistorySummaryFixtures.FORBIDDEN_NEXT_AREA,
        SearchHistorySummaryFixtures.FORBIDDEN_HIGH_RISK);
    harness.mapper.assertStatusPersistedBeforeEvent("FAILED");
    harness.eventHub.assertPublishedChanged("FAILED");
  }

  private record ProviderCase(String name, SearchHistorySummaryPort provider) {}

  private static final class CompletionHarness {
    private final RecordingSummaryMapper mapper;
    private final RecordingEventHub eventHub;
    private final SearchHistorySummaryService service;

    private CompletionHarness(
        RecordingSummaryMapper mapper,
        RecordingEventHub eventHub,
        SearchHistorySummaryService service) {
      this.mapper = mapper;
      this.eventHub = eventHub;
      this.service = service;
    }

    static CompletionHarness withProvider(SearchHistorySummaryPort provider) {
      EventOrder order = new EventOrder();
      RecordingSummaryMapper mapper = new RecordingSummaryMapper(order);
      RecordingEventHub eventHub = new RecordingEventHub(order);
      SearchHistorySummaryService service =
          new SearchHistorySummaryService(
              mapper,
              provider,
              new ForbiddenSummaryGuard(),
              eventHub,
              Clock.fixed(NOW, ZoneOffset.UTC));
      return new CompletionHarness(mapper, eventHub, service);
    }

    void runGeneration() {
      service.generate(REQUEST);
    }
  }

  private static final class RecordingSummaryMapper implements SearchHistorySummaryMapper {
    private final EventOrder order;
    private final List<GenerationUpdate> updates = new ArrayList<>();

    private RecordingSummaryMapper(EventOrder order) {
      this.order = order;
    }

    @Override
    public List<SearchHistorySummaryRow> findByOp(
        UUID opId, UUID incidentId, String scopeType, UUID scopeId, UUID dutyShiftId, String status) {
      return List.of(
          new SearchHistorySummaryRow(
              SearchHistorySummaryFixtures.SUMMARY_ID,
              SearchHistorySummaryFixtures.INCIDENT_ID,
              SearchHistorySummaryFixtures.OP_ID,
              null,
              "GENERATING",
              null,
              SOURCE_HASH,
              "READY",
              null,
              SearchHistorySummaryFixtures.SUMMARY_VERSION));
    }

    @Override
    public String sourceFingerprintForScope(UUID opId, UUID dutyShiftId) {
      return EVIDENCE;
    }

    @Override
    public String sourceEvidenceForScope(UUID opId, UUID dutyShiftId) {
      return EVIDENCE;
    }

    @Override
    public List<SearchHistorySummaryRow> findReadyOrFailedByScopeWithDifferentHash(
        UUID opId, UUID dutyShiftId, String sourceDataHash) {
      return List.of();
    }

    @Override
    public int insertGenerationRequest(
        UUID summaryId,
        UUID opId,
        UUID dutyShiftId,
        String generationStatus,
        String content,
        String sourceDataHash,
        String sourceReadiness,
        UUID requestedByAccountId,
        Instant generatedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
      return 1;
    }

    @Override
    public void updateGenerationResult(
        UUID summaryId,
        String generationStatus,
        String content,
        String sourceReadiness,
        Instant generatedAt,
        Instant updatedAt) {
      updates.add(
          new GenerationUpdate(
              summaryId, generationStatus, content, sourceReadiness, generatedAt, updatedAt));
      order.record("mapper:" + generationStatus);
    }

    @Override
    public void markStaleByIds(List<UUID> summaryIds, Instant updatedAt) {}

    void assertPersisted(
        String status, String content, String sourceReadiness, Instant generatedAt) {
      assertThat(updates).hasSize(1);
      GenerationUpdate update = updates.get(0);
      assertThat(update.summaryId()).isEqualTo(SearchHistorySummaryFixtures.SUMMARY_ID);
      assertThat(update.generationStatus()).isEqualTo(status);
      assertThat(update.content()).isEqualTo(content);
      assertThat(update.sourceReadiness()).isEqualTo(sourceReadiness);
      assertThat(update.generatedAt()).isEqualTo(generatedAt);
      assertThat(update.updatedAt()).isEqualTo(NOW);
    }

    void assertNoStoredContent(String label, String... disallowedContent) {
      assertThat(updates).as(label).hasSize(1);
      String storedContent = updates.get(0).content();
      if (storedContent == null) {
        return;
      }
      for (String disallowed : disallowedContent) {
        assertThat(storedContent).as(label).doesNotContain(Objects.toString(disallowed, ""));
      }
    }

    void assertStatusPersistedBeforeEvent(String status) {
      assertStatusPersistedBeforeEvent(status, status);
    }

    void assertStatusPersistedBeforeEvent(String status, String label) {
      assertThat(order.indexOf("mapper:" + status))
          .as("%s should store %s before publishing SEARCH_HISTORY_SUMMARY_CHANGED", label, status)
          .isLessThan(order.indexOf("event:" + status));
    }

    private record GenerationUpdate(
        UUID summaryId,
        String generationStatus,
        String content,
        String sourceReadiness,
        Instant generatedAt,
        Instant updatedAt) {}
  }

  private static final class RecordingEventHub implements EventHub {
    private final EventOrder order;
    private final List<PublishRequest> published = new ArrayList<>();

    private RecordingEventHub(EventOrder order) {
      this.order = order;
    }

    @Override
    public void publish(PublishRequest request) {
      published.add(request);
      Object status = request.payload().get("status");
      if (status != null) {
        order.record("event:" + status);
      }
    }

    void assertPublishedChanged(String expectedStatus) {
      assertPublishedChanged(expectedStatus, expectedStatus);
    }

    void assertPublishedChanged(String expectedStatus, String label) {
      assertThat(published)
          .as("%s should publish one SEARCH_HISTORY_SUMMARY_CHANGED event", label)
          .hasSize(1);

      PublishRequest request = published.get(0);
      assertThat(request.type()).isEqualTo(SearchHistorySummaryFixtures.SUMMARY_EVENT_TYPE);
      assertThat(request.incidentId()).isEqualTo(SearchHistorySummaryFixtures.INCIDENT_ID);
      assertThat(request.payload())
          .containsEntry("status", expectedStatus)
          .containsEntry("sourceReadiness", "READY")
          .containsEntry("sourceHash", SOURCE_HASH)
          .containsEntry("version", SearchHistorySummaryFixtures.SUMMARY_VERSION);
      assertPayloadValue(request.payload(), "id", SearchHistorySummaryFixtures.SUMMARY_ID);
      assertPayloadValue(request.payload(), "opId", SearchHistorySummaryFixtures.OP_ID);
    }

    private void assertPayloadValue(Map<String, Object> payload, String key, UUID expected) {
      assertThat(payload).containsKey(key);
      assertThat(Objects.toString(payload.get(key))).isEqualTo(expected.toString());
    }
  }

  private static final class EventOrder {
    private final List<String> events = new ArrayList<>();

    void record(String event) {
      events.add(event);
    }

    int indexOf(String event) {
      int index = events.indexOf(event);
      assertThat(index).as("expected event order marker %s in %s", event, events).isNotEqualTo(-1);
      return index;
    }
  }
}
