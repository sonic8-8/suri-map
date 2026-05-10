package com.surimap.incident.adapter;

import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

/** S1-1 import의 간단한 mock 112 marker seed를 S5 ReferenceMarkerSeed payload로 변환한다. */
@Component
public class ReferenceMarkerSeedAdapter implements ReferenceMarkerSeed {

  private static final int SRID = 4326;
  private static final int OP1_SEQUENCE = 1;
  private static final UUID PRECINCT_FIRST_INCIDENT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID PRECINCT_FIRST_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final UUID PRECINCT_FIRST_CREATED_BY_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110003");
  private static final Instant PRECINCT_FIRST_MARKER_OCCURRED_AT =
      Instant.parse("2026-04-28T00:05:00Z");
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  private final com.surimap.marker.seed.ReferenceMarkerSeed delegate;
  private final OperationalPeriodMapper operationalPeriodMapper;

  public ReferenceMarkerSeedAdapter(
      com.surimap.marker.seed.ReferenceMarkerSeed delegate,
      OperationalPeriodMapper operationalPeriodMapper) {
    this.delegate = delegate;
    this.operationalPeriodMapper = operationalPeriodMapper;
  }

  @Override
  public void createForIncident(UUID incidentId, List<ReferenceMarkerSeed.SeedMarker> seedMarkers) {
    OperationalPeriod op =
        operationalPeriodMapper
            .findByIncidentAndSequence(incidentId, OP1_SEQUENCE)
            .orElseThrow(() -> new IllegalStateException("op1_not_found_for_reference_marker"));

    List<com.surimap.marker.seed.SeedMarker> converted = new ArrayList<>();
    for (int index = 0; index < seedMarkers.size(); index++) {
      ReferenceMarkerSeed.SeedMarker seed = seedMarkers.get(index);
      converted.add(toSeedMarker(incidentId, op.getId(), seed, index));
    }
    delegate.createForIncident(incidentId, converted);
  }

  private com.surimap.marker.seed.SeedMarker toSeedMarker(
      UUID incidentId, UUID opId, ReferenceMarkerSeed.SeedMarker seed, int index) {
    return new com.surimap.marker.seed.SeedMarker(
        markerIdFor(incidentId, seed, index),
        opId,
        null,
        MarkerType.valueOf(seed.type()),
        null,
        point(seed.lon(), seed.lat()),
        seed.memo(),
        occurredAtFor(incidentId),
        PRECINCT_FIRST_CREATED_BY_ACCOUNT_ID,
        null);
  }

  private static UUID markerIdFor(UUID incidentId, ReferenceMarkerSeed.SeedMarker seed, int index) {
    if (PRECINCT_FIRST_INCIDENT_ID.equals(incidentId) && index == 0) {
      return PRECINCT_FIRST_MARKER_ID;
    }
    String seedValue =
        "reference-marker:"
            + incidentId
            + ":"
            + index
            + ":"
            + seed.type()
            + ":"
            + seed.lon()
            + ":"
            + seed.lat();
    return UUID.nameUUIDFromBytes(seedValue.getBytes(StandardCharsets.UTF_8));
  }

  private static Instant occurredAtFor(UUID incidentId) {
    if (PRECINCT_FIRST_INCIDENT_ID.equals(incidentId)) {
      return PRECINCT_FIRST_MARKER_OCCURRED_AT;
    }
    return Instant.now();
  }

  private static Point point(double lon, double lat) {
    Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    point.setSRID(SRID);
    return point;
  }
}
