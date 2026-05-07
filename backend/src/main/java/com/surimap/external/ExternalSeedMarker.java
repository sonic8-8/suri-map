package com.surimap.external;

/** mock 112 초기 마커 DTO. 내부 marker ID가 없는 S5 ReferenceMarkerSeed 입력값이다. */
public record ExternalSeedMarker(String type, String source, String memo, double lon, double lat) {}
