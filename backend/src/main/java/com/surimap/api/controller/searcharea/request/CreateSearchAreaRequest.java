package com.surimap.api.controller.searcharea.request;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSearchAreaRequest(
    @NotNull UUID incidentId,
    UUID opId,
    @NotNull String areaLevel,
    @NotNull GeoJsonPolygon geometry,
    String memo,
    @NotNull OffsetDateTime clientTs) {}
