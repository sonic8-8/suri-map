package com.surimap.marker.domain.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * L5-T03A marker location/current OP fixture.
 *
 * <p>문자열 alias와 좌표는 docs/spec/harness-scenarios.md §6 및 docs/spec/specs/S5.json fixture를 따른다.
 */
public final class MarkerGeometryFixtures {

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  public static final String INCIDENT_ALIAS = BoundaryAreaFixtures.INCIDENT_ALIAS;
  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  public static final String OP1_ALIAS = BoundaryAreaFixtures.OP1_ALIAS;
  public static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;
  public static final UUID OP_ID = OP1_ID;
  public static final String OP2_ALIAS = BoundaryAreaFixtures.OP2_ALIAS;
  public static final UUID OP2_ID = BoundaryAreaFixtures.OP2_ID;

  public static final String POLICE_PHONE_ALIAS = "dev-precinct-phone-01";
  public static final String ACCOUNT_ALIAS = "acct-precinct-team";
  public static final String MARKER_ALIAS = "mk-precinct-clue-001";
  public static final String OP_MISMATCH_MARKER_ALIAS = "mk-precinct-op-mismatch-001";

  /** 기준 overall_search_area: 종로구 일대 */
  public static final Polygon HARNESS_OVERALL_SEARCH_AREA =
      GF.createPolygon(
          new Coordinate[] {
            new Coordinate(126.904000, 35.158000),
            new Coordinate(126.923000, 35.158000),
            new Coordinate(126.923000, 35.173000),
            new Coordinate(126.904000, 35.173000),
            new Coordinate(126.904000, 35.158000)
          });

  public static final Point VALID_MARKER_POINT = point(126.913400, 35.163100);
  public static final Point OUTSIDE_ENVELOPE = point(127.200000, 35.163100);
  public static final Point LAT_LON_SWAPPED = point(35.163100, 126.913400);
  public static final Point LATLON_SWAPPED = LAT_LON_SWAPPED;
  public static final Point PRECISION_OVER_6DP = point(126.9134007, 35.1631007);
  public static final Point NAN_POINT = point(Double.NaN, Double.NaN);
  public static final Point SRID_MISMATCH_POINT =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 3857)
          .createPoint(new Coordinate(126.913400, 35.163100));

  public static final double ENVELOPE_MIN_LON = 126.647507;
  public static final double ENVELOPE_MIN_LAT = 35.052595;
  public static final double ENVELOPE_MAX_LON = 127.017482;
  public static final double ENVELOPE_MAX_LAT = 35.256837;

  private MarkerGeometryFixtures() {}

  private static Point point(double lon, double lat) {
    return GF.createPoint(new Coordinate(lon, lat));
  }
}
