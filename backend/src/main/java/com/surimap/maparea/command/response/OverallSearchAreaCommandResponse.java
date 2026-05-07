package com.surimap.maparea.command.response;

import java.util.UUID;

/**
 * overall_search_area command 결과 응답.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §acceptance_criteria AC-S2-01, AC-S2-05. missing_area,
 * blindspot, recommended_area 필드는 포함하지 않는다 (FR-23 scope excluded).
 */
public record OverallSearchAreaCommandResponse(
    UUID id, UUID incidentId, String status, long version) {}
