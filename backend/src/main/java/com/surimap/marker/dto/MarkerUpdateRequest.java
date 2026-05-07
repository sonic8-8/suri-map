package com.surimap.marker.dto;

public record MarkerUpdateRequest(
    Long version, MarkerGeoJsonPoint location, String memo, String type) {}
