package com.surimap.api.controller.searcharea.response;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchAreaResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID parentAreaId,
    String areaLevel,
    String colorToken,
    String status,
    long historyCount,
    long version,
    GeoJsonPolygon geometry,
    List<BigDecimal> bbox,
    Instant updatedAt)
    implements SearchAreaReadResponse {}
