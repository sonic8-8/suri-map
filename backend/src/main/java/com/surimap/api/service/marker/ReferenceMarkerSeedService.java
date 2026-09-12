package com.surimap.api.service.marker;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사건 가져오기의 원천 마커에 식별자와 최초 수색 차수를 부여해 기준 마커로 저장한다. */
@Service
public class ReferenceMarkerSeedService implements ReferenceMarkerSeed {

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

  private final MarkerMapper markerMapper;
  private final OperationalPeriodMapper operationalPeriodMapper;

  public ReferenceMarkerSeedService(
      MarkerMapper markerMapper, OperationalPeriodMapper operationalPeriodMapper) {
    this.markerMapper = markerMapper;
    this.operationalPeriodMapper = operationalPeriodMapper;
  }

  @Override
  @Transactional
  public void createForIncident(UUID incidentId, List<ReferenceMarkerSeed.SeedMarker> seedMarkers) {
    OperationalPeriod op =
        operationalPeriodMapper
            .findByIncidentAndSequence(incidentId, OP1_SEQUENCE)
            .orElseThrow(() -> new IllegalStateException("op1_not_found_for_reference_marker"));

    List<Marker> markers = new ArrayList<>();
    for (int index = 0; index < seedMarkers.size(); index++) {
      ReferenceMarkerSeed.SeedMarker seed = seedMarkers.get(index);
      markers.add(createReferenceMarker(incidentId, op.getId(), seed, index));
    }
    // 한 좌표라도 잘못되면 일부 마커만 저장되지 않도록 전체 묶음을 먼저 검사한다.
    markers.forEach(marker -> Marker.validateLocation(marker.getLocation()));
    markers.forEach(markerMapper::insertSeed);
  }

  private Marker createReferenceMarker(
      UUID incidentId, UUID opId, ReferenceMarkerSeed.SeedMarker seed, int index) {
    return Marker.builder()
        .id(createMarkerId(incidentId, seed, index))
        .incidentId(incidentId)
        .operationalPeriodId(opId)
        .markerType(MarkerType.valueOf(seed.type()))
        .location(createPoint(seed.lon(), seed.lat()))
        .memo(seed.memo())
        .occurredAt(resolveOccurredAt(incidentId))
        .createdByAccountId(PRECINCT_FIRST_CREATED_BY_ACCOUNT_ID)
        .markerSource(MarkerSource.MOCK_SEED)
        .status(MarkerStatus.ACTIVE)
        .version(1L)
        .build();
  }

  private static UUID createMarkerId(
      UUID incidentId, ReferenceMarkerSeed.SeedMarker seed, int index) {
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

  private static Instant resolveOccurredAt(UUID incidentId) {
    if (PRECINCT_FIRST_INCIDENT_ID.equals(incidentId)) {
      return PRECINCT_FIRST_MARKER_OCCURRED_AT;
    }
    return Instant.now();
  }

  private static Point createPoint(double lon, double lat) {
    Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    point.setSRID(SRID);
    return point;
  }
}
