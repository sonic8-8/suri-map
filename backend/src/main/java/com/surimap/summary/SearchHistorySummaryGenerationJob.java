package com.surimap.summary;

import com.surimap.dutyshift.DutyShift;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.operationalperiod.OperationalPeriod;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SearchHistorySummaryGenerationJob {

  private static final String GENERATING = "GENERATING";
  private static final String READY = "READY";

  private final SearchHistorySummaryMapper mapper;
  private final SearchHistorySummaryService summaryService;
  private final Clock clock;

  public SearchHistorySummaryGenerationJob(
      SearchHistorySummaryMapper mapper, SearchHistorySummaryService summaryService, Clock clock) {
    this.mapper = mapper;
    this.summaryService = summaryService;
    this.clock = clock;
  }

  public void enqueueForDutyShiftEnd(DutyShift endedDutyShift, UUID requestedByAccountId) {
    Objects.requireNonNull(endedDutyShift, "endedDutyShift must not be null");
    enqueue(
        endedDutyShift.getIncidentId(),
        endedDutyShift.getOpId(),
        endedDutyShift.getId(),
        requestedByAccountId,
        "duty-shift-end:" + endedDutyShift.getId() + ":v" + endedDutyShift.getVersion());
  }

  public void enqueueForOperationalPeriodTransition(
      OperationalPeriod endedOperationalPeriod,
      OperationalPeriod openedOperationalPeriod,
      UUID requestedByAccountId) {
    Objects.requireNonNull(endedOperationalPeriod, "endedOperationalPeriod must not be null");
    Objects.requireNonNull(openedOperationalPeriod, "openedOperationalPeriod must not be null");
    enqueue(
        endedOperationalPeriod.getIncidentId(),
        endedOperationalPeriod.getId(),
        null,
        requestedByAccountId,
        "op-transition:"
            + endedOperationalPeriod.getId()
            + ":v"
            + endedOperationalPeriod.getVersion()
            + "->"
            + openedOperationalPeriod.getId());
  }

  private void enqueue(
      UUID incidentId,
      UUID operationalPeriodId,
      UUID dutyShiftId,
      UUID requestedByAccountId,
      String fallbackFingerprint) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(operationalPeriodId, "operationalPeriodId must not be null");
    Objects.requireNonNull(requestedByAccountId, "requestedByAccountId must not be null");

    String sourceFingerprint = mapper.sourceFingerprintForScope(operationalPeriodId, dutyShiftId);
    if (sourceFingerprint == null || sourceFingerprint.isBlank()) {
      sourceFingerprint = fallbackFingerprint;
    }
    String sourceHash = sha256(sourceFingerprint);
    Instant now = clock.instant();
    markStaleRows(operationalPeriodId, dutyShiftId, sourceHash, now);
    UUID summaryId = UUID.randomUUID();
    int inserted =
        mapper.insertGenerationRequest(
            summaryId,
            operationalPeriodId,
            dutyShiftId,
            GENERATING,
            null,
            sourceHash,
            READY,
            requestedByAccountId,
            null,
            1L,
            now,
            now);
    if (inserted > 0) {
      String evidence = mapper.sourceEvidenceForScope(operationalPeriodId, dutyShiftId);
      if (evidence == null || evidence.isBlank()) {
        evidence = sourceFingerprint;
      }
      SummaryRequest request =
          new SummaryRequest(summaryId, operationalPeriodId, incidentId, evidence);
      runAfterCommit(() -> summaryService.generate(request));
    }
  }

  private void markStaleRows(
      UUID operationalPeriodId, UUID dutyShiftId, String sourceHash, Instant updatedAt) {
    List<SearchHistorySummaryRow> staleRows =
        mapper.findReadyOrFailedByScopeWithDifferentHash(
            operationalPeriodId, dutyShiftId, sourceHash);
    if (staleRows.isEmpty()) {
      return;
    }
    mapper.markStaleByIds(
        staleRows.stream().map(SearchHistorySummaryRow::summaryId).toList(), updatedAt);
    staleRows.forEach(summaryService::publishStale);
  }

  private void runAfterCommit(Runnable runnable) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      runnable.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            runnable.run();
          }
        });
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
