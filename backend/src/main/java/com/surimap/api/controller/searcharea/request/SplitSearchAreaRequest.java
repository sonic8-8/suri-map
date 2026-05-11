package com.surimap.api.controller.searcharea.request;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SplitSearchAreaRequest(
    @NotNull UUID opId,
    @NotNull @Size(min = 2) List<GeoJsonPolygon> children,
    String memo,
    @NotNull Long expectedVersion,
    @NotNull OffsetDateTime clientTs) {}
