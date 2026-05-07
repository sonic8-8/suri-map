package com.surimap.maparea.assignment;

import java.util.List;
import java.util.UUID;

/**
 * SearchAreaAssignmentService use case 입력 record (S8, Phase 2).
 *
 * <p>Controller 계층이 SearchAreaAssignmentService를 호출할 때 사용하는 Service Request DTO.
 * 기준 문서: docs/spec/specs/S8.json §api_contracts POST
 * /search-areas/{searchAreaId}/assignments.
 */
public record SearchAreaAssignmentServiceRequest(
    UUID searchAreaId,
    UUID incidentId,
    UUID opId,
    List<UUID> assigneeAccountIds,
    UUID assignedByAccountId,
    String memo,
    String idempotencyKey) {}
