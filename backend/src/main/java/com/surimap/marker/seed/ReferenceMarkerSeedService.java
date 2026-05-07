package com.surimap.marker.seed;

import com.surimap.marker.domain.port.MarkerLocationValidator;
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

public class ReferenceMarkerSeedService implements ReferenceMarkerSeed {

  private final MarkerRepository markerRepository;
  private final MarkerLocationValidator markerLocationValidator;

  public ReferenceMarkerSeedService(
      MarkerRepository markerRepository, MarkerLocationValidator markerLocationValidator) {
    this.markerRepository = Objects.requireNonNull(markerRepository, "markerRepository");
    this.markerLocationValidator =
        Objects.requireNonNull(markerLocationValidator, "markerLocationValidator");
  }

  @Override
  public ReferenceMarkerSeedResult createForIncident(
      UUID incidentId, List<SeedMarker> seedMarkers) {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(seedMarkers, "seedMarkers");

    List<MarkerSeedRecord> records =
        seedMarkers.stream()
            .peek(seed -> markerLocationValidator.validate(incidentId, seed.location()))
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
}
