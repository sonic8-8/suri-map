package com.surimap.marker.domain.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * L5 마커 검증 red test용 fixture. 좌표·ID는 harness-scenarios.md §6 기준값과 동일하다.
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/harness-scenarios.md
 */
public final class MarkerGeometryFixtures {

  private MarkerGeometryFixtures() {}

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  // ── 사건·OP fixture ID ──────────────────────────────────
  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID; // inc-precinct-first-001
  public static final UUID OP_ID = BoundaryAreaFixtures.OP1_ID; // op-precinct-001-op1

  // ── 정상 좌표 ──────────────────────────────────────────
  /** 기준 overall_search_area: 종로구 일대 */
  public static final Polygon HARNESS_OVERALL_SEARCH_AREA =
      GF.createPolygon(
          new Coordinate[] {
            new Coordinate(126.948000, 37.565000),
            new Coordinate(126.968000, 37.565000),
            new Coordinate(126.968000, 37.579000),
            new Coordinate(126.948000, 37.579000),
            new Coordinate(126.948000, 37.565000)
          });

  /** 기준 마커 위치: overall_search_area 내부 */
  public static final Point VALID_MARKER_POINT =
      GF.createPoint(new Coordinate(126.956500, 37.571200));

  // ── 실패 좌표 ──────────────────────────────────────────
  /** envelope 밖 좌표 */
  public static final Point OUTSIDE_ENVELOPE =
      GF.createPoint(new Coordinate(127.200000, 37.571200));

  /** lon/lat 뒤바뀐 좌표 */
  public static final Point LATLON_SWAPPED = GF.createPoint(new Coordinate(37.571200, 126.956500));

  /** precision 초과 좌표 (7자리) */
  public static final Point PRECISION_OVER_6DP =
      GF.createPoint(new Coordinate(126.9565007, 37.5712007));

  /** NaN 좌표 */
  public static final Point NAN_POINT = GF.createPoint(new Coordinate(Double.NaN, Double.NaN));

  /** EPSG:4326이 아닌 좌표계 */
  public static final Point SRID_MISMATCH_POINT =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 3857)
          .createPoint(new Coordinate(126.956500, 37.571200));

  // ── 하네스 기준 지도 envelope ─────────────────────────
  public static final double ENVELOPE_MIN_LON = 126.900000;
  public static final double ENVELOPE_MIN_LAT = 37.500000;
  public static final double ENVELOPE_MAX_LON = 127.080000;
  public static final double ENVELOPE_MAX_LAT = 37.620000;
}
