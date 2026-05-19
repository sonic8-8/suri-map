package com.surimap.domain.summary;

import java.util.UUID;

/**
 * Provider-facing summary evidence package. The source field must already be minimized and
 * sanitized before it is sent to an AI provider.
 */
public record SummaryEvidence(
    UUID summaryId, UUID operationalPeriodId, UUID incidentId, Object source) {}
