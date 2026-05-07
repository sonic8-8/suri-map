package com.surimap.maparea.query;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SearchAreaQuery.overallOf 응답 shape (S2.json §service_contracts).
 *
 * <p>status는 항상 ACTIVE다. not_found이면 Optional.empty()가 반환된다.
 */
public record OverallSearchAreaResult(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    GeoJsonPolygon geometry,
    List<BigDecimal> bbox,
    Instant updatedAt) {}
