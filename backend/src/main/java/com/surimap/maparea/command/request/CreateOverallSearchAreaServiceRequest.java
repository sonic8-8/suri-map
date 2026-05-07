package com.surimap.maparea.command.request;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.time.Instant;
import java.util.UUID;

/**
 * overall_search_area 생성 service 요청.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §acceptance_criteria AC-S2-01.
 */
public record CreateOverallSearchAreaServiceRequest(
    UUID incidentId,
    GeoJsonPolygon geometry,
    String memo,
    UUID commanderAccountId,
    Instant requestedAt) {}
