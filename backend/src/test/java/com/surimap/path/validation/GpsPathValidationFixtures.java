package com.surimap.path.validation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

final class GpsPathValidationFixtures {

    static final String NORMAL_PATH_ID = "path-precinct-mixed-001";
    static final String NORMAL_DEVICE_ID = "dev-precinct-car-01";
    static final String VEHICLE_SEGMENT_ID = "seg-precinct-vehicle-001";
    static final String FOOT_SEGMENT_ID = "seg-precinct-foot-001";

    static final List<GpsPointFixture> NORMAL_POINTS = List.of(
            point("gps-precinct-001", "2026-04-28T09:00:00+09:00", "126.956000", "37.570000", 13.5, 5),
            point("gps-precinct-002", "2026-04-28T09:00:05+09:00", "126.956650", "37.570180", 12.8, 5),
            point("gps-precinct-003", "2026-04-28T09:00:10+09:00", "126.957300", "37.570360", 11.9, 5),
            point("gps-precinct-004", "2026-04-28T09:00:15+09:00", "126.957850", "37.570540", 9.8, 5),
            point("gps-precinct-005", "2026-04-28T09:00:20+09:00", "126.958000", "37.570700", 1.6, 5),
            point("gps-precinct-006", "2026-04-28T09:00:25+09:00", "126.958080", "37.570880", 1.3, 5),
            point("gps-precinct-007", "2026-04-28T09:00:30+09:00", "126.958160", "37.571050", 1.1, 5),
            point("gps-precinct-008", "2026-04-28T09:00:35+09:00", "126.958250", "37.571220", 1.4, 5)
    );

    static final List<PathSegmentFixture> NORMAL_SEGMENTS = List.of(
            new PathSegmentFixture(VEHICLE_SEGMENT_ID, "VEHICLE", 0, 3, "gps-precinct-001", "gps-precinct-004"),
            new PathSegmentFixture(FOOT_SEGMENT_ID, "FOOT", 4, 7, "gps-precinct-005", "gps-precinct-008")
    );

    static final StructuralFailureFixture BATCH_LIMIT_EXCEEDED = new StructuralFailureFixture(
            "gps-batch-over-limit-121",
            IntStream.rangeClosed(1, 121)
                    .mapToObj(index -> point(
                            "gps-over-limit-%03d".formatted(index),
                            "2026-04-28T09:%02d:%02d+09:00".formatted((index - 1) / 12, ((index - 1) % 12) * 5),
                            "126.956000",
                            "37.570000",
                            2.0,
                            5
                    ))
                    .toList(),
            "invalid_geometry",
            "points maxItems=120"
    );

    static final StructuralFailureFixture COORDINATE_OUTSIDE_ENVELOPE = new StructuralFailureFixture(
            "coord-outside-envelope",
            List.of(
                    point("gps-outside-001", "2026-04-28T09:05:00+09:00", "127.200000", "37.571200", 3.0, 5),
                    point("gps-outside-002", "2026-04-28T09:05:05+09:00", "127.200100", "37.571250", 3.0, 5)
            ),
            "invalid_geometry",
            "point outside harness envelope"
    );

    static final StructuralFailureFixture COORDINATE_LAT_LON_SWAPPED = new StructuralFailureFixture(
            "coord-latlon-swapped",
            List.of(
                    point("gps-swapped-001", "2026-04-28T09:06:00+09:00", "37.571200", "126.956500", 3.0, 5),
                    point("gps-swapped-002", "2026-04-28T09:06:05+09:00", "37.571300", "126.956600", 3.0, 5)
            ),
            "invalid_geometry",
            "lon/lat order must be EPSG:4326"
    );

    static final StructuralFailureFixture NON_MONOTONIC_CLIENT_TS = new StructuralFailureFixture(
            "gps-client-ts-non-monotonic",
            List.of(
                    point("gps-ts-001", "2026-04-28T09:07:00+09:00", "126.956000", "37.570000", 3.0, 5),
                    point("gps-ts-002", "2026-04-28T09:06:55+09:00", "126.956100", "37.570100", 3.0, 5)
            ),
            "invalid_geometry",
            "clientTs strict monotonic"
    );

    static final StructuralFailureFixture NULL_COORDINATE = new StructuralFailureFixture(
            "point-null-nan",
            List.of(
                    point("gps-null-001", "2026-04-28T09:08:00+09:00", null, "37.570000", 3.0, 5),
                    point("gps-null-002", "2026-04-28T09:08:05+09:00", "126.956100", "37.570100", 3.0, 5)
            ),
            "invalid_geometry",
            "null or NaN coordinate is a structural failure"
    );

    static final StructuralFailureFixture PRECISION_OVER_SIX_DP = new StructuralFailureFixture(
            "precision-over-6dp",
            List.of(
                    point("gps-precision-001", "2026-04-28T09:09:00+09:00", "126.9565007", "37.5712007", 3.0, 5),
                    point("gps-precision-002", "2026-04-28T09:09:05+09:00", "126.9566007", "37.5713007", 3.0, 5)
            ),
            "invalid_geometry",
            "precision exceeds 6 decimal places"
    );

    static final QualityFailureFixture LOW_ACCURACY = new QualityFailureFixture(
            "gps-low-quality-accuracy-001",
            point("gps-quality-accuracy-001", "2026-04-28T09:10:00+09:00", "126.956000", "37.570000", 3.0, 51),
            "horizontalAccuracyM > 50",
            BigDecimal.valueOf(51),
            BigDecimal.valueOf(GpsPathValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS)
    );

    static final QualityFailureFixture TIMESTAMP_SKEW = new QualityFailureFixture(
            "gps-low-quality-skew-001",
            point("gps-quality-skew-001", "2026-04-28T09:10:35+09:00", "126.956100", "37.570100", 3.0, 5),
            "timestampSkewSec > 30",
            BigDecimal.valueOf(35),
            BigDecimal.valueOf(GpsPathValidationCriteria.MAX_TIMESTAMP_SKEW_SECONDS)
    );

    static final QualityFailureFixture INVALID_SPEED = new QualityFailureFixture(
            "gps-low-quality-speed-001",
            point("gps-quality-speed-001", "2026-04-28T09:10:10+09:00", "126.956200", "37.570200", 46.0, 5),
            "speedMps < 0 or > 45",
            BigDecimal.valueOf(46),
            BigDecimal.valueOf(GpsPathValidationCriteria.MAX_SPEED_METERS_PER_SECOND)
    );

    static final QualityFailureFixture DISTANCE_JUMP = new QualityFailureFixture(
            "gps-low-quality-jump-001",
            point("gps-quality-jump-001", "2026-04-28T09:10:15+09:00", "126.960500", "37.575500", 3.0, 5),
            "distance > 200m within 5 seconds",
            BigDecimal.valueOf(230),
            BigDecimal.valueOf(GpsPathValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS)
    );

    static final List<StructuralFailureFixture> STRUCTURAL_FAILURE_FIXTURES = List.of(
            BATCH_LIMIT_EXCEEDED,
            COORDINATE_OUTSIDE_ENVELOPE,
            COORDINATE_LAT_LON_SWAPPED,
            NON_MONOTONIC_CLIENT_TS,
            NULL_COORDINATE,
            PRECISION_OVER_SIX_DP
    );

    static final List<QualityFailureFixture> QUALITY_FAILURE_FIXTURES = List.of(
            LOW_ACCURACY,
            TIMESTAMP_SKEW,
            INVALID_SPEED,
            DISTANCE_JUMP
    );

    private GpsPathValidationFixtures() {
    }

    private static GpsPointFixture point(
            String pointId,
            String clientTs,
            String lon,
            String lat,
            double speedMps,
            Integer horizontalAccuracyM
    ) {
        return new GpsPointFixture(
                pointId,
                OffsetDateTime.parse(clientTs),
                decimalOrNull(lon),
                decimalOrNull(lat),
                BigDecimal.valueOf(speedMps),
                horizontalAccuracyM
        );
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    record GpsPointFixture(
            String pointId,
            OffsetDateTime clientTs,
            BigDecimal lon,
            BigDecimal lat,
            BigDecimal speedMps,
            Integer horizontalAccuracyM
    ) {
    }

    record PathSegmentFixture(
            String segmentId,
            String type,
            int startIndex,
            int endIndex,
            String startPointId,
            String endPointId
    ) {
    }

    record StructuralFailureFixture(
            String name,
            List<GpsPointFixture> points,
            String publicError,
            String rule
    ) {
    }

    record QualityFailureFixture(
            String name,
            GpsPointFixture point,
            String rule,
            BigDecimal measuredValue,
            BigDecimal threshold
    ) {
    }
}
