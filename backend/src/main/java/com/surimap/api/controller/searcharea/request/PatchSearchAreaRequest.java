package com.surimap.api.controller.searcharea.request;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatchSearchAreaRequest(
    UUID opId,
    GeoJsonPolygon geometry,
    String memo,
    Long expectedVersion,
    String nextStatus,
    @NotNull OffsetDateTime clientTs) {}
