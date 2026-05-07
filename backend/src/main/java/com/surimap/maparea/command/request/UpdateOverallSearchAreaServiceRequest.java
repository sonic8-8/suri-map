package com.surimap.maparea.command.request;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.time.Instant;
import java.util.UUID;

/**
 * overall_search_area 수정 service 요청.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §acceptance_criteria AC-S2-01.
 */
public record UpdateOverallSearchAreaServiceRequest(
    UUID overallSearchAreaId,
    UUID incidentId,
    GeoJsonPolygon geometry,
    String memo,
    long expectedVersion,
    UUID commanderAccountId,
    Instant requestedAt) {}
