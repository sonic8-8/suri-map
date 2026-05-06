package com.surimap.external;

/** mock 112 초기 마커 DTO. import 시 S5 ReferenceMarkerSeed 입력용. */
public record ExternalSeedMarker(String type, String source, String memo, double lon, double lat) {}
