package com.surimap.maparea.assignment;

import java.util.List;
import java.util.UUID;

/**
 * SearchAreaAssignmentCommand 포트 입력 record (S8, Phase 2).
 *
 * <p>기준 문서: docs/spec/specs/S8.json §api_contracts POST
 * /search-areas/{searchAreaId}/assignments.
 */
public record SearchAreaAssignmentRequest(
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    List<UUID> assigneeAccountIds,
    UUID assignedByAccountId,
    String memo,
    String idempotencyKey) {}
