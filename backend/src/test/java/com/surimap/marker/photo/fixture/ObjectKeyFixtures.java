package com.surimap.marker.photo.fixture;

import java.util.UUID;

/**
 * Object key 규칙 검증용 fixture.
 * ObjectKeyGenerator가 MinIO-compatible 형식을 올바르게 생성하는지 테스트.
 */
public final class ObjectKeyFixtures {

    /** 고정 UUID로 예상 key 패턴을 검증 */
    public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID MARKER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID PHOTO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    public static final String EXPECTED_JPEG_KEY =
            "markers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/cccccccc-cccc-cccc-cccc-cccccccccccc.jpg";

    public static final String EXPECTED_PNG_KEY =
            "markers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/cccccccc-cccc-cccc-cccc-cccccccccccc.png";

    public static final String EXPECTED_WEBP_KEY =
            "markers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/cccccccc-cccc-cccc-cccc-cccccccccccc.webp";

    private ObjectKeyFixtures() {}
}
