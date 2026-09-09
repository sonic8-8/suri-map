package com.surimap.marker.seed;

import com.surimap.domain.marker.Marker;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.repository.MarkerRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReferenceMarkerSeedService implements ReferenceMarkerSeed {

  private final MarkerRepository markerRepository;

  public ReferenceMarkerSeedService(MarkerRepository markerRepository) {
    this.markerRepository = Objects.requireNonNull(markerRepository, "markerRepository");
  }

  @Override
  public ReferenceMarkerSeedResult createForIncident(
      UUID incidentId, List<SeedMarker> seedMarkers) {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(seedMarkers, "seedMarkers");

    List<Marker> records =
        seedMarkers.stream()
            .peek(seed -> Marker.validateLocation(seed.location()))
            .map(seed -> Marker.fromSeed(incidentId, seed))
            .toList();
    if (records.isEmpty()) {
      return new ReferenceMarkerSeedResult(incidentId, List.of());
    }

    records.forEach(markerRepository::insertSeed);

    List<UUID> markerIds = records.stream().map(Marker::getId).toList();
    Map<UUID, Marker> persisted =
        markerRepository.findByIds(markerIds).stream()
            .collect(Collectors.toMap(Marker::getId, Function.identity()));
    List<MarkerView> markers =
        markerIds.stream()
            .map(persisted::get)
            .filter(Objects::nonNull)
            .map(marker -> marker.toView(List.of()))
            .toList();
    return new ReferenceMarkerSeedResult(incidentId, markers);
  }
}
