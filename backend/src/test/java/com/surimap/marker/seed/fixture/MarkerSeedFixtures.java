package com.surimap.marker.seed.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.seed.SeedMarker;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** L5-T05B initial reference marker seed fixture. */
public final class MarkerSeedFixtures {

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;
  public static final String INCIDENT_ALIAS = BoundaryAreaFixtures.INCIDENT_ALIAS;
  public static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;
  public static final String OP1_ALIAS = BoundaryAreaFixtures.OP1_ALIAS;

  public static final String MARKER_ALIAS = "mk-precinct-clue-001";
  public static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  public static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110003");
  public static final Instant OCCURRED_AT = Instant.parse("2026-04-28T00:05:00Z");
  public static final Point REFERENCE_POINT = point("126.913400", "35.163100");
  public static final String MEMO = "mock 112 initial reference clue";

  private MarkerSeedFixtures() {}

  public static SeedMarker referenceClueSeed() {
    return new SeedMarker(
        MARKER_ID,
        OP1_ID,
        null,
        MarkerType.CLUE,
        null,
        REFERENCE_POINT,
        MEMO,
        OCCURRED_AT,
        ACCOUNT_ID,
        null);
  }

  private static Point point(String lon, String lat) {
    Point point = GF.createPoint(new Coordinate(Double.parseDouble(lon), Double.parseDouble(lat)));
    point.setSRID(4326);
    return point;
  }
}
