package com.surimap.maparea.assignment;

import java.util.List;
import java.util.UUID;

/**
 * SearchAreaAssignmentService.assign 응답 record (S8, Phase 2).
 *
 * <p>기준 문서: docs/spec/specs/S8.json §api_contracts POST
 * /search-areas/{searchAreaId}/assignments response.
 */
public record SearchAreaAssignmentResponse(
    UUID searchAreaId,
    UUID opId,
    List<UUID> assignmentIds,
    long version) {}
