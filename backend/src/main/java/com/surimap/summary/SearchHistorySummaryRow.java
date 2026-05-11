package com.surimap.summary;

import java.time.Instant;
import java.util.UUID;

public record SearchHistorySummaryRow(
    UUID summaryId,
    UUID incidentId,
    UUID opId,
    UUID dutyShiftId,
    String status,
    String content,
    String sourceHash,
    String sourceReadiness,
    Instant generatedAt,
    long version) {}
