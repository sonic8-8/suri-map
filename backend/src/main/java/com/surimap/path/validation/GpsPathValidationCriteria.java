package com.surimap.path.validation;

public final class GpsPathValidationCriteria {

    public static final int MIN_POINTS_PER_BATCH = 2;
    public static final int MAX_POINTS_PER_BATCH = 120;
    public static final int MAX_HORIZONTAL_ACCURACY_METERS = 50;
    public static final int MAX_TIMESTAMP_SKEW_SECONDS = 30;
    public static final int MAX_SPEED_METERS_PER_SECOND = 45;
    public static final int MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS = 200;
    public static final int CANONICAL_COORDINATE_SCALE = 6;

    public static final GeoEnvelope HARNESS_ENVELOPE = new GeoEnvelope(
            126.900000,
            37.500000,
            127.080000,
            37.620000
    );

    private GpsPathValidationCriteria() {
    }

    public record GeoEnvelope(
            double minLon,
            double minLat,
            double maxLon,
            double maxLat
    ) {
    }
}
