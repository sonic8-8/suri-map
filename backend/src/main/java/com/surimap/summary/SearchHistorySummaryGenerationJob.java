package com.surimap.summary;

import com.surimap.dutyshift.DutyShift;
import com.surimap.operationalperiod.OperationalPeriod;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchHistorySummaryGenerationJob {

  private static final String GENERATING = "GENERATING";
  private static final String READY = "READY";

  private final SearchHistorySummaryMapper mapper;

  public SearchHistorySummaryGenerationJob(SearchHistorySummaryMapper mapper) {
    this.mapper = mapper;
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
    Instant now = Instant.now();
    mapper.insertGenerationRequest(
        UUID.randomUUID(),
        operationalPeriodId,
        dutyShiftId,
        GENERATING,
        null,
        sha256(sourceFingerprint),
        READY,
        requestedByAccountId,
        null,
        1L,
        now,
        now);
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
