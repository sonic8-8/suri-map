package com.surimap.maparea.query;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SearchAreaQuery.byIncident / byOp 응답 개별 area row (S2.json §service_contracts).
 *
 * <p>historyCount는 search_area_history에서 파생된 값이다.
 */
public record SearchAreaRow(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID parentAreaId,
    String name,
    String areaLevel,
    String status,
    long version,
    GeoJsonPolygon geometry,
    List<BigDecimal> bbox,
    Instant updatedAt,
    long historyCount,
    Instant completedAt,
    UUID completedByAccountId,
    String completionMemo) {}
