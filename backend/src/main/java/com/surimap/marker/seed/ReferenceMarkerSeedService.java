package com.surimap.marker.seed;

import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.repository.MarkerSeedRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

public class ReferenceMarkerSeedService implements ReferenceMarkerSeed {

  private static final int SRID = 4326;

  private final MarkerRepository markerRepository;

  public ReferenceMarkerSeedService(MarkerRepository markerRepository) {
    this.markerRepository = Objects.requireNonNull(markerRepository, "markerRepository");
  }

  @Override
  public ReferenceMarkerSeedResult createForIncident(
      UUID incidentId, List<SeedMarker> seedMarkers) {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(seedMarkers, "seedMarkers");

    List<MarkerSeedRecord> records =
        seedMarkers.stream()
            .peek(seed -> validateSeedLocation(seed.location()))
            .map(seed -> MarkerSeedRecord.from(incidentId, seed))
            .toList();
    if (records.isEmpty()) {
      return new ReferenceMarkerSeedResult(incidentId, List.of());
    }

    records.forEach(markerRepository::insertSeed);

    List<UUID> markerIds = records.stream().map(MarkerSeedRecord::id).toList();
    Map<UUID, MarkerRecord> persisted =
        markerRepository.findByIds(markerIds).stream()
            .collect(Collectors.toMap(MarkerRecord::getId, Function.identity()));
    List<MarkerView> markers =
        markerIds.stream()
            .map(persisted::get)
            .filter(Objects::nonNull)
            .map(record -> record.toView(incidentId))
            .toList();
    return new ReferenceMarkerSeedResult(incidentId, markers);
  }

  private static void validateSeedLocation(Point location) {
    if (location == null || location.isEmpty()) {
      throw new InvalidGeometryException("location is null or empty");
    }
    if (location.getSRID() != SRID) {
      throw new InvalidGeometryException("location SRID must be 4326");
    }
    Coordinate coord = location.getCoordinate();
    if (coord == null || !Double.isFinite(coord.x) || !Double.isFinite(coord.y)) {
      throw new InvalidGeometryException("coordinates must be finite numbers");
    }
    if (coord.x < -180.0 || coord.x > 180.0) {
      throw new InvalidGeometryException("longitude out of range: " + coord.x);
    }
    if (coord.y < -90.0 || coord.y > 90.0) {
      throw new InvalidGeometryException("latitude out of range: " + coord.y);
    }
  }
}
