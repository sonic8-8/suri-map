package com.surimap.marker.domain.fixture;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.UUID;

/**
 * L5 마커 검증 red test용 fixture.
 * 좌표·ID는 harness-scenarios.md §6 기준값과 동일하다.
 *
 * @see docs/contracts/L5-05-geometry-spec.md §5
 */
public final class MarkerGeometryFixtures {

    private MarkerGeometryFixtures() {}

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    // ── 사건·OP fixture ID ──────────────────────────────────
    public static final UUID INCIDENT_ID =
            UUID.fromString("00000000-0000-0000-0000-696e63303031"); // inc-precinct-first-001
    public static final UUID OP_ID =
            UUID.fromString("00000000-0000-0000-0000-6f7030303031"); // op-precinct-001-op1

    // ── 정상 좌표 ──────────────────────────────────────────
    /** 기준 map_boundary: 종로구 일대 */
    public static final Polygon HARNESS_BOUNDARY = GF.createPolygon(new Coordinate[]{
            new Coordinate(126.948000, 37.565000),
            new Coordinate(126.968000, 37.565000),
            new Coordinate(126.968000, 37.579000),
            new Coordinate(126.948000, 37.579000),
            new Coordinate(126.948000, 37.565000)
    });

    /** 기준 마커 위치: boundary 내부 */
    public static final Point VALID_MARKER_POINT =
            GF.createPoint(new Coordinate(126.956500, 37.571200));

    // ── 실패 좌표 ──────────────────────────────────────────
    /** envelope 밖 좌표 */
    public static final Point OUTSIDE_ENVELOPE =
            GF.createPoint(new Coordinate(127.200000, 37.571200));

    /** lon/lat 뒤바뀐 좌표 */
    public static final Point LATLON_SWAPPED =
            GF.createPoint(new Coordinate(37.571200, 126.956500));

    /** precision 초과 좌표 (7자리) */
    public static final Point PRECISION_OVER_6DP =
            GF.createPoint(new Coordinate(126.9565007, 37.5712007));

    /** NaN 좌표 */
    public static final Point NAN_POINT =
            GF.createPoint(new Coordinate(Double.NaN, Double.NaN));

    // ── 하네스 기준 지도 envelope ─────────────────────────
    public static final double ENVELOPE_MIN_LON = 126.900000;
    public static final double ENVELOPE_MIN_LAT = 37.500000;
    public static final double ENVELOPE_MAX_LON = 127.080000;
    public static final double ENVELOPE_MAX_LAT = 37.620000;
}
