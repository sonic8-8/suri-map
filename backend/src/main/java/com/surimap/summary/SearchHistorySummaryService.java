package com.surimap.summary;

import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SearchHistorySummaryPort.GenerationStatus;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchHistorySummaryService {

  static final String SUMMARY_CHANGED = "SEARCH_HISTORY_SUMMARY_CHANGED";
  private static final String SOURCE_ENTITY_TYPE = "search_history_summary";
  private static final String READY = "READY";
  private static final String FAILED = "FAILED";
  private static final String SOURCE_READY = "READY";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final SearchHistorySummaryMapper mapper;
  private final SearchHistorySummaryPort summaryPort;
  private final ForbiddenSummaryGuard forbiddenSummaryGuard;
  private final EventHub eventHub;
  private final Clock clock;

  public SearchHistorySummaryService(
      SearchHistorySummaryMapper mapper,
      SearchHistorySummaryPort summaryPort,
      ForbiddenSummaryGuard forbiddenSummaryGuard,
      EventHub eventHub,
      Clock clock) {
    this.mapper = mapper;
    this.summaryPort = summaryPort;
    this.forbiddenSummaryGuard = forbiddenSummaryGuard;
    this.eventHub = eventHub;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void generate(SummaryRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    SearchHistorySummaryRow row = requireGenerationRow(request);
    GenerationDecision decision = generateSafely(request);
    Instant now = clock.instant();
    mapper.updateGenerationResult(
        row.summaryId(),
        decision.status(),
        decision.content(),
        SOURCE_READY,
        READY.equals(decision.status()) ? now : null,
        now);
    publishChanged(row, decision.status(), SOURCE_READY, now);
  }

  void publishStale(SearchHistorySummaryRow row) {
    publishChanged(row, row.status(), "STALE", clock.instant());
  }

  private SearchHistorySummaryRow requireGenerationRow(SummaryRequest request) {
    return mapper
        .findByOp(request.operationalPeriodId(), request.incidentId(), null, null, null, null)
        .stream()
        .filter(row -> row.summaryId().equals(request.summaryId()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "search_history_summary row is missing: " + request.summaryId()));
  }

  private GenerationDecision generateSafely(SummaryRequest request) {
    SummaryResult result;
    try {
      result = summaryPort.generate(request);
    } catch (RuntimeException e) {
      return GenerationDecision.failed();
    }
    if (result == null || result.isFailed() || result.status() != GenerationStatus.READY) {
      return GenerationDecision.failed();
    }
    String summaryText = result.summaryText();
    if (summaryText == null
        || summaryText.isBlank()
        || forbiddenSummaryGuard.containsForbiddenPhrase(summaryText)) {
      return GenerationDecision.failed();
    }
    return new GenerationDecision(READY, summaryText);
  }

  private void publishChanged(
      SearchHistorySummaryRow row, String status, String sourceReadiness, Instant occurredAt) {
    eventHub.publish(
        new PublishRequest(
            eventIdFor(row, status, sourceReadiness),
            row.incidentId(),
            SUMMARY_CHANGED,
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            row.summaryId(),
            occurredAt,
            payloadFor(row, status, sourceReadiness)));
  }

  private UUID eventIdFor(SearchHistorySummaryRow row, String status, String sourceReadiness) {
    String seed =
        "event:"
            + SUMMARY_CHANGED
            + ":"
            + row.summaryId()
            + ":v"
            + row.version()
            + ":"
            + status
            + ":"
            + sourceReadiness;
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, Object> payloadFor(
      SearchHistorySummaryRow row, String status, String sourceReadiness) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", row.summaryId().toString());
    payload.put("incidentId", row.incidentId().toString());
    payload.put("opId", row.opId().toString());
    payload.put("status", status);
    payload.put("sourceReadiness", sourceReadiness);
    payload.put("sourceHash", row.sourceHash());
    payload.put("version", row.version());
    if (row.dutyShiftId() != null) {
      payload.put("dutyShiftId", row.dutyShiftId().toString());
    }
    return payload;
  }

  private record GenerationDecision(String status, String content) {
    static GenerationDecision failed() {
      return new GenerationDecision(FAILED, null);
    }
  }
}
